package clipboard

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"strings"
)

const (
	defaultHistoryLimit       = 20
	defaultPullLimit          = 50
	defaultRetentionDays      = 0
	defaultStoredHistoryLimit = 1000
	maxPageLimit              = 100
	maxStoredHistoryLimit     = 100000
	maxCleanupDays            = 36500
	maxTextBytes              = 64 * 1024
)

type Service struct {
	repo Repository
}

func NewService(repo Repository) *Service {
	return &Service{repo: repo}
}

func (s *Service) UploadText(ctx context.Context, userID, deviceID, textContent string) (CreateTextItemResult, error) {
	if s == nil || s.repo == nil {
		return CreateTextItemResult{}, fmt.Errorf("clipboard service is not ready")
	}

	if err := validateTextContent(textContent); err != nil {
		return CreateTextItemResult{}, err
	}
	if err := s.repo.TouchDeviceLastSeen(ctx, userID, deviceID); err != nil {
		return CreateTextItemResult{}, err
	}

	result, err := s.repo.CreateTextItem(ctx, CreateTextItemParams{
		UserID:         userID,
		OriginDeviceID: deviceID,
		TextContent:    textContent,
		ContentHash:    buildTextContentHash(textContent),
	})
	if err != nil {
		return CreateTextItemResult{}, err
	}

	// 新增文本后立即执行一次用户级保留策略，让历史数量不会无限增长。
	// 清理失败不影响本次上传结果，但必须把错误暴露出去，避免用户以为设置已经生效。
	if _, err := s.applyConfiguredRetention(ctx, userID); err != nil {
		return CreateTextItemResult{}, err
	}
	return result, nil
}

func (s *Service) ListHistory(ctx context.Context, userID, deviceID string, beforeSeq *int64, limit int) (HistoryResult, error) {
	if s == nil || s.repo == nil {
		return HistoryResult{}, fmt.Errorf("clipboard service is not ready")
	}

	if beforeSeq != nil && *beforeSeq <= 0 {
		return HistoryResult{}, fmt.Errorf("before_seq must be greater than 0")
	}
	if err := s.repo.TouchDeviceLastSeen(ctx, userID, deviceID); err != nil {
		return HistoryResult{}, err
	}

	items, hasMore, err := s.repo.ListHistory(ctx, userID, ListHistoryOptions{
		BeforeSeq: beforeSeq,
		Limit:     normalizeLimit(limit, defaultHistoryLimit),
	})
	if err != nil {
		return HistoryResult{}, err
	}

	snapshot, err := s.repo.GetSyncSnapshot(ctx, userID, deviceID)
	if err != nil {
		return HistoryResult{}, err
	}

	var nextBeforeSeq *int64
	if hasMore && len(items) > 0 {
		lastSeq := items[len(items)-1].Seq
		nextBeforeSeq = &lastSeq
	}

	return HistoryResult{
		Items:               items,
		HasMore:             hasMore,
		NextBeforeSeq:       nextBeforeSeq,
		LatestSeq:           snapshot.LatestSeq,
		CurrentDeviceAckSeq: snapshot.CurrentDeviceAckSeq,
	}, nil
}

func (s *Service) Pull(ctx context.Context, userID, deviceID string, sinceSeq int64, limit int) (PullResult, error) {
	if s == nil || s.repo == nil {
		return PullResult{}, fmt.Errorf("clipboard service is not ready")
	}
	if sinceSeq < 0 {
		return PullResult{}, fmt.Errorf("since_seq must be greater than or equal to 0")
	}
	if err := s.repo.TouchDeviceLastSeen(ctx, userID, deviceID); err != nil {
		return PullResult{}, err
	}

	items, hasMore, err := s.repo.PullItems(ctx, userID, sinceSeq, normalizeLimit(limit, defaultPullLimit))
	if err != nil {
		return PullResult{}, err
	}

	snapshot, err := s.repo.GetSyncSnapshot(ctx, userID, deviceID)
	if err != nil {
		return PullResult{}, err
	}

	nextSinceSeq := sinceSeq
	if len(items) > 0 {
		nextSinceSeq = items[len(items)-1].Seq
	}
	if len(items) == 0 && snapshot.LatestSeq > nextSinceSeq {
		// 有些 seq 可能已经被删除或按保留策略清理。
		// 当本次拉取没有可见记录时，允许客户端把游标推进到最新 seq，避免一直提示有待补拉事件。
		nextSinceSeq = snapshot.LatestSeq
	}
	if snapshot.CurrentDeviceAckSeq > nextSinceSeq {
		nextSinceSeq = snapshot.CurrentDeviceAckSeq
	}

	return PullResult{
		Items:               items,
		SinceSeq:            sinceSeq,
		NextSinceSeq:        nextSinceSeq,
		HasMore:             hasMore,
		LatestSeq:           snapshot.LatestSeq,
		CurrentDeviceAckSeq: snapshot.CurrentDeviceAckSeq,
	}, nil
}

func (s *Service) Ack(ctx context.Context, userID, deviceID string, seq int64) (AckResult, error) {
	if s == nil || s.repo == nil {
		return AckResult{}, fmt.Errorf("clipboard service is not ready")
	}
	if seq < 0 {
		return AckResult{}, fmt.Errorf("seq must be greater than or equal to 0")
	}
	if err := s.repo.TouchDeviceLastSeen(ctx, userID, deviceID); err != nil {
		return AckResult{}, err
	}

	if _, err := s.repo.AckDevice(ctx, userID, deviceID, seq); err != nil {
		return AckResult{}, err
	}

	snapshot, err := s.repo.GetSyncSnapshot(ctx, userID, deviceID)
	if err != nil {
		return AckResult{}, err
	}

	return AckResult{
		Seq:                 seq,
		LatestSeq:           snapshot.LatestSeq,
		CurrentDeviceAckSeq: snapshot.CurrentDeviceAckSeq,
	}, nil
}

func (s *Service) GetSyncSnapshot(ctx context.Context, userID, deviceID string) (SyncSnapshot, error) {
	if s == nil || s.repo == nil {
		return SyncSnapshot{}, fmt.Errorf("clipboard service is not ready")
	}
	if err := s.repo.TouchDeviceLastSeen(ctx, userID, deviceID); err != nil {
		return SyncSnapshot{}, err
	}
	return s.repo.GetSyncSnapshot(ctx, userID, deviceID)
}

func (s *Service) DeleteHistoryItem(ctx context.Context, userID, deviceID, itemID string) (HistoryDeleteResult, error) {
	if s == nil || s.repo == nil {
		return HistoryDeleteResult{}, fmt.Errorf("clipboard service is not ready")
	}

	itemID = strings.TrimSpace(itemID)
	if itemID == "" {
		return HistoryDeleteResult{}, fmt.Errorf("item_id is required")
	}
	if err := s.repo.TouchDeviceLastSeen(ctx, userID, deviceID); err != nil {
		return HistoryDeleteResult{}, err
	}

	item, deleted, err := s.repo.DeleteHistoryItem(ctx, userID, itemID)
	if err != nil {
		return HistoryDeleteResult{}, err
	}
	if !deleted {
		return HistoryDeleteResult{}, ErrNotFound
	}

	snapshot, err := s.repo.GetSyncSnapshot(ctx, userID, deviceID)
	if err != nil {
		return HistoryDeleteResult{}, err
	}
	return HistoryDeleteResult{
		Item:                item,
		Deleted:             true,
		DeletedActiveCount:  1,
		LatestSeq:           snapshot.LatestSeq,
		CurrentDeviceAckSeq: snapshot.CurrentDeviceAckSeq,
	}, nil
}

func (s *Service) ClearHistory(ctx context.Context, userID, deviceID string) (HistoryCleanupResult, error) {
	if s == nil || s.repo == nil {
		return HistoryCleanupResult{}, fmt.Errorf("clipboard service is not ready")
	}
	if err := s.repo.TouchDeviceLastSeen(ctx, userID, deviceID); err != nil {
		return HistoryCleanupResult{}, err
	}

	deletedCount, err := s.repo.ClearHistory(ctx, userID)
	if err != nil {
		return HistoryCleanupResult{}, err
	}
	return s.buildCleanupResult(ctx, userID, deviceID, deletedCount)
}

func (s *Service) CleanupHistoryOlderThan(ctx context.Context, userID, deviceID string, days int) (HistoryCleanupResult, error) {
	if s == nil || s.repo == nil {
		return HistoryCleanupResult{}, fmt.Errorf("clipboard service is not ready")
	}
	if days <= 0 {
		return HistoryCleanupResult{}, fmt.Errorf("days must be greater than 0")
	}
	if days > maxCleanupDays {
		return HistoryCleanupResult{}, fmt.Errorf("days must be at most %d", maxCleanupDays)
	}
	if err := s.repo.TouchDeviceLastSeen(ctx, userID, deviceID); err != nil {
		return HistoryCleanupResult{}, err
	}

	deletedCount, err := s.repo.CleanupHistoryOlderThan(ctx, userID, days)
	if err != nil {
		return HistoryCleanupResult{}, err
	}
	return s.buildCleanupResult(ctx, userID, deviceID, deletedCount)
}

func (s *Service) GetHistorySettings(ctx context.Context, userID, deviceID string) (HistorySettings, error) {
	if s == nil || s.repo == nil {
		return HistorySettings{}, fmt.Errorf("clipboard service is not ready")
	}
	if err := s.repo.TouchDeviceLastSeen(ctx, userID, deviceID); err != nil {
		return HistorySettings{}, err
	}
	return s.repo.GetHistorySettings(ctx, userID)
}

func (s *Service) UpdateHistorySettings(ctx context.Context, userID, deviceID string, input HistorySettingsInput) (HistoryCleanupResult, error) {
	if s == nil || s.repo == nil {
		return HistoryCleanupResult{}, fmt.Errorf("clipboard service is not ready")
	}
	if err := s.repo.TouchDeviceLastSeen(ctx, userID, deviceID); err != nil {
		return HistoryCleanupResult{}, err
	}

	normalizedInput, err := normalizeHistorySettingsInput(input)
	if err != nil {
		return HistoryCleanupResult{}, err
	}

	settings, err := s.repo.UpdateHistorySettings(ctx, userID, normalizedInput)
	if err != nil {
		return HistoryCleanupResult{}, err
	}

	deletedCount, err := s.applyRetentionSettings(ctx, userID, settings)
	if err != nil {
		return HistoryCleanupResult{}, err
	}
	snapshot, err := s.repo.GetSyncSnapshot(ctx, userID, deviceID)
	if err != nil {
		return HistoryCleanupResult{}, err
	}
	return HistoryCleanupResult{
		DeletedCount:        deletedCount,
		Settings:            settings,
		LatestSeq:           snapshot.LatestSeq,
		CurrentDeviceAckSeq: snapshot.CurrentDeviceAckSeq,
	}, nil
}

func normalizeLimit(limit, fallback int) int {
	switch {
	case limit <= 0:
		return fallback
	case limit > maxPageLimit:
		return maxPageLimit
	default:
		return limit
	}
}

func normalizeHistorySettingsInput(input HistorySettingsInput) (HistorySettingsInput, error) {
	normalized := HistorySettingsInput{
		RetentionDays: input.RetentionDays,
		HistoryLimit:  input.HistoryLimit,
	}
	if normalized.RetentionDays < 0 {
		return HistorySettingsInput{}, fmt.Errorf("retention_days must be greater than or equal to 0")
	}
	if normalized.RetentionDays > maxCleanupDays {
		return HistorySettingsInput{}, fmt.Errorf("retention_days must be at most %d", maxCleanupDays)
	}
	if normalized.HistoryLimit <= 0 {
		normalized.HistoryLimit = defaultStoredHistoryLimit
	}
	if normalized.HistoryLimit > maxStoredHistoryLimit {
		return HistorySettingsInput{}, fmt.Errorf("history_limit must be at most %d", maxStoredHistoryLimit)
	}
	return normalized, nil
}

func (s *Service) applyConfiguredRetention(ctx context.Context, userID string) (int, error) {
	settings, err := s.repo.GetHistorySettings(ctx, userID)
	if err != nil {
		return 0, err
	}
	return s.applyRetentionSettings(ctx, userID, settings)
}

func (s *Service) applyRetentionSettings(ctx context.Context, userID string, settings HistorySettings) (int, error) {
	deletedCount := 0
	if settings.RetentionDays > 0 {
		count, err := s.repo.CleanupHistoryOlderThan(ctx, userID, settings.RetentionDays)
		if err != nil {
			return 0, err
		}
		deletedCount += count
	}

	count, err := s.repo.ApplyHistoryLimit(ctx, userID, settings.HistoryLimit)
	if err != nil {
		return 0, err
	}
	deletedCount += count
	return deletedCount, nil
}

func (s *Service) buildCleanupResult(ctx context.Context, userID, deviceID string, deletedCount int) (HistoryCleanupResult, error) {
	settings, err := s.repo.GetHistorySettings(ctx, userID)
	if err != nil {
		return HistoryCleanupResult{}, err
	}
	snapshot, err := s.repo.GetSyncSnapshot(ctx, userID, deviceID)
	if err != nil {
		return HistoryCleanupResult{}, err
	}
	return HistoryCleanupResult{
		DeletedCount:        deletedCount,
		Settings:            settings,
		LatestSeq:           snapshot.LatestSeq,
		CurrentDeviceAckSeq: snapshot.CurrentDeviceAckSeq,
	}, nil
}

func validateTextContent(textContent string) error {
	// 这里用 trim 只做“是否为空”的校验，真正入库时仍保留原始文本，
	// 这样首尾空格和换行不会被服务端偷偷改写。
	if strings.TrimSpace(textContent) == "" {
		return fmt.Errorf("text_content is required")
	}
	if len(textContent) > maxTextBytes {
		return fmt.Errorf("text_content must be at most %d bytes", maxTextBytes)
	}
	return nil
}

func buildTextContentHash(textContent string) string {
	sum := sha256.Sum256([]byte(ContentTypeText + "\n" + textContent))
	return hex.EncodeToString(sum[:])
}
