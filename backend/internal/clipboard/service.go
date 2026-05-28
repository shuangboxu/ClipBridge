package clipboard

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"strings"
)

const (
	defaultHistoryLimit = 20
	defaultPullLimit    = 50
	maxPageLimit        = 100
	maxTextBytes        = 64 * 1024
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

	return s.repo.CreateTextItem(ctx, CreateTextItemParams{
		UserID:         userID,
		OriginDeviceID: deviceID,
		TextContent:    textContent,
		ContentHash:    buildTextContentHash(textContent),
	})
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
