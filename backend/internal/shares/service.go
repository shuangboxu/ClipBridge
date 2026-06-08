package shares

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"errors"
	"fmt"
	"io"
	"math"
	"os"
	"path/filepath"
	"strings"
	"time"

	"clipbridge/backend/internal/filestore"
	"clipbridge/backend/internal/id"
	"golang.org/x/crypto/bcrypt"
)

const (
	defaultListPage        = 1
	defaultListPageSize    = 20
	maxListPageSize        = 100
	maxTextShareLength     = 65535
	maxFileNameLength      = 255
	minSharePasswordLength = 4
	maxSharePasswordLength = 128
	maxShareExpireSeconds  = int64(3650 * 24 * 60 * 60)
)

type Storage interface {
	Save(ctx context.Context, userID, originalName string, src io.Reader, maxBytes int64) (filestore.SaveResult, error)
	Open(storedPath string) (*os.File, int64, error)
	Delete(storedPath string) error
}

type PolicyProvider interface {
	PrepareUploadReader(ctx context.Context, userID string, src io.Reader) (io.Reader, int64, error)
	CurrentMaxUploadBytes(ctx context.Context, userID string) (int64, error)
}

type Service struct {
	repo           Repository
	store          Storage
	policyProvider PolicyProvider
}

func NewService(repo Repository, store Storage, policyProvider PolicyProvider) *Service {
	return &Service{
		repo:           repo,
		store:          store,
		policyProvider: policyProvider,
	}
}

func (s *Service) CreateTextShare(ctx context.Context, userID, deviceID string, input CreateTextShareInput) (Item, error) {
	if s == nil || s.repo == nil {
		return Item{}, fmt.Errorf("share service is not ready")
	}
	if err := s.repo.TouchDeviceLastSeen(ctx, userID, deviceID); err != nil {
		return Item{}, err
	}

	normalizedInput, passwordHash, err := normalizeTextShareInput(input)
	if err != nil {
		return Item{}, err
	}

	publicToken, err := newPublicToken()
	if err != nil {
		return Item{}, err
	}

	item, err := s.repo.CreateTextShare(ctx, CreateTextShareParams{
		UserID:           userID,
		PublicToken:      publicToken,
		TextContent:      normalizedInput.TextContent,
		TextPreview:      buildTextPreview(normalizedInput.TextContent, 80),
		EncryptedPayload: normalizedInput.EncryptedPayload,
		IsEncrypted:      normalizedInput.IsEncrypted,
		PasswordHash:     passwordHash,
		Encryption:       normalizedInput.Encryption,
		// 纯文本分享会把文本加密元数据同时写入兼容字段和文本字段，
		// 这样旧数据、旧逻辑和新的“文件+文字”模型都能统一读取。
		TextEncryption:   normalizedInput.Encryption,
		AllowCopyContent: normalizedInput.AllowCopyContent,
		ExpiresAt:        normalizedInput.ExpiresAt,
		BurnMode:         normalizedInput.BurnMode,
		BurnAfterSeconds: normalizedInput.BurnAfterSeconds,
	})
	if err != nil {
		return Item{}, err
	}
	decorateShareItem(&item, time.Now().UTC())
	return item, nil
}

func (s *Service) CreateFileShare(ctx context.Context, userID, deviceID string, input CreateFileShareInput, sources []io.Reader) (Item, error) {
	if s == nil || s.repo == nil || s.store == nil {
		return Item{}, fmt.Errorf("share service is not ready")
	}
	if len(sources) == 0 {
		return Item{}, fmt.Errorf("file is required")
	}
	if err := s.repo.TouchDeviceLastSeen(ctx, userID, deviceID); err != nil {
		return Item{}, err
	}

	normalizedInput, passwordHash, err := normalizeFileShareInput(input)
	if err != nil {
		return Item{}, err
	}
	if len(sources) != len(normalizedInput.Files) {
		return Item{}, fmt.Errorf("file count does not match upload sources")
	}

	publicToken, err := newPublicToken()
	if err != nil {
		return Item{}, err
	}

	maxUploadBytes := int64(0)
	savedFiles := make([]ShareFileParams, 0, len(normalizedInput.Files))
	savedPaths := make([]string, 0, len(normalizedInput.Files))

	// 一个分享里现在可以同时带多个文件。
	// 这里按顺序逐个落盘，主要是为了让出错时更容易定位到是哪一个文件失败。
	for index, fileInput := range normalizedInput.Files {
		fileReader := sources[index]
		if fileReader == nil {
			for _, storedPath := range savedPaths {
				_ = s.store.Delete(storedPath)
			}
			return Item{}, fmt.Errorf("file %d is required", index+1)
		}

		if s.policyProvider != nil {
			preparedReader, preparedMaxBytes, err := s.policyProvider.PrepareUploadReader(ctx, userID, fileReader)
			if err != nil {
				for _, storedPath := range savedPaths {
					_ = s.store.Delete(storedPath)
				}
				return Item{}, err
			}
			fileReader = preparedReader
			maxUploadBytes = preparedMaxBytes
		}

		saved, err := s.store.Save(ctx, userID, fileInput.UploadName, fileReader, maxUploadBytes)
		if err != nil {
			for _, storedPath := range savedPaths {
				_ = s.store.Delete(storedPath)
			}

			switch {
			case errors.Is(err, filestore.ErrFileTooLarge):
				return Item{}, ErrFileTooLarge
			default:
				return Item{}, fmt.Errorf("save shared file failed: %w", err)
			}
		}

		fileID, err := newFileID()
		if err != nil {
			_ = s.store.Delete(saved.StoredPath)
			for _, storedPath := range savedPaths {
				_ = s.store.Delete(storedPath)
			}
			return Item{}, err
		}

		savedFiles = append(savedFiles, ShareFileParams{
			ID:           fileID,
			SortOrder:    index,
			OriginalName: fileInput.OriginalName,
			StoredPath:   saved.StoredPath,
			ContentType:  fileInput.ContentType,
			SizeBytes:    saved.SizeBytes,
			SHA256:       saved.SHA256,
			Encryption:   fileInput.Encryption,
		})
		savedPaths = append(savedPaths, saved.StoredPath)
	}

	primaryFile := savedFiles[0]
	item, err := s.repo.CreateFileShare(ctx, CreateFileShareParams{
		UserID:               userID,
		PublicToken:          publicToken,
		TextContent:          normalizedInput.TextContent,
		TextPreview:          buildTextPreview(normalizedInput.TextContent, 80),
		TextEncryptedPayload: normalizedInput.TextEncryptedPayload,
		TextEncryption:       normalizedInput.TextEncryption,
		Files:                savedFiles,
		FileOriginalName:     primaryFile.OriginalName,
		FileStoredPath:       primaryFile.StoredPath,
		FileContentType:      primaryFile.ContentType,
		FileSizeBytes:        primaryFile.SizeBytes,
		FileSHA256:           primaryFile.SHA256,
		IsEncrypted:          normalizedInput.IsEncrypted,
		PasswordHash:         passwordHash,
		Encryption:           primaryFile.Encryption,
		AllowCopyContent:     normalizedInput.AllowCopyContent && hasTextSharePayload(normalizedInput.TextContent, normalizedInput.TextEncryptedPayload),
		ExpiresAt:            normalizedInput.ExpiresAt,
		BurnMode:             normalizedInput.BurnMode,
		BurnAfterSeconds:     normalizedInput.BurnAfterSeconds,
	})
	if err != nil {
		for _, storedPath := range savedPaths {
			_ = s.store.Delete(storedPath)
		}
		return Item{}, err
	}
	item.Files = buildShareFilesFromParams(item.ID, savedFiles)
	decorateShareItem(&item, time.Now().UTC())
	return item, nil
}

func (s *Service) List(ctx context.Context, userID, deviceID string, page, pageSize int, status string) (ListResult, error) {
	if s == nil || s.repo == nil {
		return ListResult{}, fmt.Errorf("share service is not ready")
	}
	if page < 0 {
		return ListResult{}, fmt.Errorf("page must be greater than or equal to 0")
	}
	if pageSize < 0 {
		return ListResult{}, fmt.Errorf("page_size must be greater than or equal to 0")
	}
	if err := s.repo.TouchDeviceLastSeen(ctx, userID, deviceID); err != nil {
		return ListResult{}, err
	}

	normalizedStatus, err := normalizeStatusFilter(status)
	if err != nil {
		return ListResult{}, err
	}

	now := time.Now().UTC()
	options := ListOptions{
		Page:     normalizePage(page),
		PageSize: normalizePageSize(pageSize),
		Status:   normalizedStatus,
		Now:      now,
	}
	items, total, err := s.repo.ListShares(ctx, userID, options)
	if err != nil {
		return ListResult{}, err
	}

	maxUploadBytes := int64(0)
	if s.policyProvider != nil {
		maxUploadBytes, err = s.policyProvider.CurrentMaxUploadBytes(ctx, userID)
		if err != nil {
			return ListResult{}, err
		}
	}
	for index := range items {
		if err := s.hydrateShareFiles(ctx, &items[index]); err != nil {
			return ListResult{}, err
		}
		decorateShareItem(&items[index], now)
	}

	totalPages := 0
	if total > 0 {
		totalPages = (total + options.PageSize - 1) / options.PageSize
	}

	return ListResult{
		Items:          items,
		Page:           options.Page,
		PageSize:       options.PageSize,
		Total:          total,
		TotalPages:     totalPages,
		Status:         normalizedStatus,
		MaxUploadBytes: maxUploadBytes,
	}, nil
}

func (s *Service) Revoke(ctx context.Context, userID, deviceID, shareID string) (Item, error) {
	if s == nil || s.repo == nil {
		return Item{}, fmt.Errorf("share service is not ready")
	}
	if strings.TrimSpace(shareID) == "" {
		return Item{}, fmt.Errorf("share id is required")
	}
	if err := s.repo.TouchDeviceLastSeen(ctx, userID, deviceID); err != nil {
		return Item{}, err
	}

	item, err := s.repo.RevokeShare(ctx, userID, strings.TrimSpace(shareID))
	if err != nil {
		return Item{}, err
	}
	decorateShareItem(&item, time.Now().UTC())
	return item, nil
}

func (s *Service) GetPublicMeta(ctx context.Context, publicToken string) (Item, error) {
	if s == nil || s.repo == nil {
		return Item{}, fmt.Errorf("share service is not ready")
	}
	publicToken = strings.TrimSpace(publicToken)
	if publicToken == "" {
		return Item{}, fmt.Errorf("share token is required")
	}

	item, err := s.repo.GetShareByToken(ctx, publicToken)
	if err != nil {
		return Item{}, err
	}
	if err := s.hydrateShareFiles(ctx, &item); err != nil {
		return Item{}, err
	}
	decorateShareItem(&item, time.Now().UTC())
	return item, nil
}

func (s *Service) OpenPublicShare(ctx context.Context, publicToken, password string) (OpenShareResult, error) {
	if s == nil || s.repo == nil {
		return OpenShareResult{}, fmt.Errorf("share service is not ready")
	}
	publicToken = strings.TrimSpace(publicToken)
	if publicToken == "" {
		return OpenShareResult{}, fmt.Errorf("share token is required")
	}

	item, err := s.repo.OpenShareByToken(ctx, publicToken, strings.TrimSpace(password), time.Now().UTC())
	if err != nil {
		return OpenShareResult{}, err
	}
	if err := s.hydrateShareFiles(ctx, &item); err != nil {
		return OpenShareResult{}, err
	}
	decorateShareItem(&item, time.Now().UTC())
	return OpenShareResult{Item: item}, nil
}

func (s *Service) OpenPublicText(ctx context.Context, publicToken, password string) (OpenTextResult, error) {
	if s == nil || s.repo == nil {
		return OpenTextResult{}, fmt.Errorf("share service is not ready")
	}
	publicToken = strings.TrimSpace(publicToken)
	if publicToken == "" {
		return OpenTextResult{}, fmt.Errorf("share token is required")
	}

	previewItem, err := s.repo.GetShareByToken(ctx, publicToken)
	if err != nil {
		return OpenTextResult{}, err
	}
	if !previewItem.HasTextContent() {
		return OpenTextResult{}, ErrTextContentMissing
	}

	item, err := s.repo.OpenShareByToken(ctx, publicToken, strings.TrimSpace(password), time.Now().UTC())
	if err != nil {
		return OpenTextResult{}, err
	}
	if err := s.hydrateShareFiles(ctx, &item); err != nil {
		return OpenTextResult{}, err
	}
	decorateShareItem(&item, time.Now().UTC())
	return OpenTextResult{Item: item}, nil
}

func (s *Service) OpenPublicFile(ctx context.Context, publicToken, password string) (OpenFileResult, error) {
	if s == nil || s.repo == nil || s.store == nil {
		return OpenFileResult{}, fmt.Errorf("share service is not ready")
	}
	publicToken = strings.TrimSpace(publicToken)
	if publicToken == "" {
		return OpenFileResult{}, fmt.Errorf("share token is required")
	}

	previewItem, err := s.repo.GetShareByToken(ctx, publicToken)
	if err != nil {
		return OpenFileResult{}, err
	}
	if !previewItem.HasFileContent() {
		return OpenFileResult{}, ErrFileBodyMissing
	}

	if err := s.hydrateShareFiles(ctx, &previewItem); err != nil {
		return OpenFileResult{}, err
	}

	shareFile, err := selectShareFile(previewItem, "")
	if err != nil {
		return OpenFileResult{}, err
	}

	file, sizeBytes, err := s.store.Open(shareFile.StoredPath)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return OpenFileResult{}, ErrFileBodyMissing
		}
		return OpenFileResult{}, fmt.Errorf("open share file failed: %w", err)
	}

	item, err := s.repo.OpenShareByToken(ctx, publicToken, strings.TrimSpace(password), time.Now().UTC())
	if err != nil {
		_ = file.Close()
		return OpenFileResult{}, err
	}
	if err := s.hydrateShareFiles(ctx, &item); err != nil {
		_ = file.Close()
		return OpenFileResult{}, err
	}
	decorateShareItem(&item, time.Now().UTC())

	return OpenFileResult{
		Item:      item,
		ShareFile: shareFile,
		File:      file,
		SizeBytes: sizeBytes,
	}, nil
}

func (s *Service) GetPublicFile(ctx context.Context, publicToken, fileID string) (OpenFileResult, error) {
	if s == nil || s.repo == nil || s.store == nil {
		return OpenFileResult{}, fmt.Errorf("share service is not ready")
	}
	publicToken = strings.TrimSpace(publicToken)
	if publicToken == "" {
		return OpenFileResult{}, fmt.Errorf("share token is required")
	}

	item, err := s.repo.GetShareByToken(ctx, publicToken)
	if err != nil {
		return OpenFileResult{}, err
	}
	if err := s.hydrateShareFiles(ctx, &item); err != nil {
		return OpenFileResult{}, err
	}

	shareFile, err := selectShareFile(item, fileID)
	if err != nil {
		return OpenFileResult{}, err
	}

	file, sizeBytes, err := s.store.Open(shareFile.StoredPath)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return OpenFileResult{}, ErrFileBodyMissing
		}
		return OpenFileResult{}, fmt.Errorf("open share file failed: %w", err)
	}

	decorateShareItem(&item, time.Now().UTC())
	return OpenFileResult{
		Item:      item,
		ShareFile: shareFile,
		File:      file,
		SizeBytes: sizeBytes,
	}, nil
}

func (s *Service) MaxUploadBytes() int64 {
	return 0
}

func normalizePage(page int) int {
	if page <= 0 {
		return defaultListPage
	}
	return page
}

func normalizePageSize(pageSize int) int {
	switch {
	case pageSize <= 0:
		return defaultListPageSize
	case pageSize > maxListPageSize:
		return maxListPageSize
	default:
		return pageSize
	}
}

func normalizeStatusFilter(raw string) (string, error) {
	status := strings.ToLower(strings.TrimSpace(raw))
	if status == "" {
		return StatusAll, nil
	}

	switch status {
	case StatusAll, StatusActive, StatusExpired, StatusConsumed, StatusRevoked:
		return status, nil
	default:
		return "", fmt.Errorf("invalid status filter")
	}
}

func normalizeTextShareInput(input CreateTextShareInput) (CreateTextShareInput, string, error) {
	input.EncryptedPayload = strings.TrimSpace(input.EncryptedPayload)
	input.Password = strings.TrimSpace(input.Password)

	burnMode, burnAfterSeconds, err := normalizeBurnConfig(input.BurnMode, input.BurnAfterSeconds)
	if err != nil {
		return CreateTextShareInput{}, "", err
	}

	input.BurnMode = burnMode
	input.BurnAfterSeconds = burnAfterSeconds
	if err := validateExpiresAt(input.ExpiresAt); err != nil {
		return CreateTextShareInput{}, "", err
	}

	passwordHash, err := validateEncryptionAndHashPassword(
		input.IsEncrypted,
		input.Password,
		input.EncryptedPayload,
		input.Encryption,
	)
	if err != nil {
		return CreateTextShareInput{}, "", err
	}

	if !input.IsEncrypted {
		if input.TextContent == "" {
			return CreateTextShareInput{}, "", fmt.Errorf("text_content is required")
		}
		if len([]rune(input.TextContent)) > maxTextShareLength {
			return CreateTextShareInput{}, "", fmt.Errorf("text_content must be at most %d characters", maxTextShareLength)
		}
		input.EncryptedPayload = ""
		input.Encryption = EncryptionMetadata{}
		return input, "", nil
	}

	if input.EncryptedPayload == "" {
		return CreateTextShareInput{}, "", fmt.Errorf("encrypted_payload is required")
	}
	return input, passwordHash, nil
}

func normalizeFileShareInput(input CreateFileShareInput) (CreateFileShareInput, string, error) {
	input.TextEncryptedPayload = strings.TrimSpace(input.TextEncryptedPayload)
	input.Password = strings.TrimSpace(input.Password)

	if len(input.Files) == 0 {
		return CreateFileShareInput{}, "", fmt.Errorf("file is required")
	}

	normalizedFiles := make([]ShareFileInput, 0, len(input.Files))
	for index, file := range input.Files {
		file.UploadName = strings.TrimSpace(file.UploadName)
		file.OriginalName = strings.TrimSpace(file.OriginalName)
		file.ContentType = normalizeContentType(file.ContentType)
		if file.UploadName == "" {
			file.UploadName = file.OriginalName
		}

		uploadName, err := validateFileName(file.UploadName)
		if err != nil {
			return CreateFileShareInput{}, "", fmt.Errorf("file %d: %w", index+1, err)
		}
		originalName, err := validateFileName(file.OriginalName)
		if err != nil {
			return CreateFileShareInput{}, "", fmt.Errorf("file %d: %w", index+1, err)
		}

		file.UploadName = uploadName
		file.OriginalName = originalName
		normalizedFiles = append(normalizedFiles, file)
	}
	input.Files = normalizedFiles

	burnMode, burnAfterSeconds, err := normalizeBurnConfig(input.BurnMode, input.BurnAfterSeconds)
	if err != nil {
		return CreateFileShareInput{}, "", err
	}
	if err := validateExpiresAt(input.ExpiresAt); err != nil {
		return CreateFileShareInput{}, "", err
	}

	input.BurnMode = burnMode
	input.BurnAfterSeconds = burnAfterSeconds

	if !input.IsEncrypted {
		if strings.TrimSpace(input.Password) != "" {
			return CreateFileShareInput{}, "", fmt.Errorf("password requires is_encrypted=true")
		}
		if len([]rune(input.TextContent)) > maxTextShareLength {
			return CreateFileShareInput{}, "", fmt.Errorf("text_content must be at most %d characters", maxTextShareLength)
		}
		for index := range input.Files {
			input.Files[index].Encryption = EncryptionMetadata{}
		}
		input.TextEncryptedPayload = ""
		input.TextEncryption = EncryptionMetadata{}
		return input, "", nil
	}

	passwordHash, err := validateSharePasswordAndHash(input.Password)
	if err != nil {
		return CreateFileShareInput{}, "", err
	}

	// 分享级密码开启后，附带文本也必须跟文件一样先在浏览器侧加密，
	// 避免出现“文件是密文、文字却明文入库”的不一致状态。
	if input.TextContent != "" {
		return CreateFileShareInput{}, "", fmt.Errorf("text_content must be empty when is_encrypted=true")
	}
	if input.TextEncryptedPayload != "" {
		if err := validateEncryptionMetadata(input.TextEncryption); err != nil {
			return CreateFileShareInput{}, "", err
		}
	}

	for index, file := range input.Files {
		if err := validateEncryptionMetadata(file.Encryption); err != nil {
			return CreateFileShareInput{}, "", fmt.Errorf("file %d encryption metadata is invalid: %w", index+1, err)
		}
	}
	return input, passwordHash, nil
}

func validateEncryptionAndHashPassword(isEncrypted bool, password, payloadMarker string, encryption EncryptionMetadata) (string, error) {
	if !isEncrypted {
		if strings.TrimSpace(password) != "" {
			return "", fmt.Errorf("password requires is_encrypted=true")
		}
		return "", nil
	}

	password = strings.TrimSpace(password)
	passwordHash, err := validateSharePasswordAndHash(password)
	if err != nil {
		return "", err
	}
	if strings.TrimSpace(payloadMarker) == "" {
		return "", fmt.Errorf("encrypted payload is required")
	}
	if err := validateEncryptionMetadata(encryption); err != nil {
		return "", err
	}
	return passwordHash, nil
}

func validateSharePasswordAndHash(password string) (string, error) {
	password = strings.TrimSpace(password)
	if password == "" {
		return "", fmt.Errorf("password is required when is_encrypted=true")
	}
	if len([]rune(password)) < minSharePasswordLength {
		return "", fmt.Errorf("password must be at least %d characters", minSharePasswordLength)
	}
	if len([]rune(password)) > maxSharePasswordLength {
		return "", fmt.Errorf("password must be at most %d characters", maxSharePasswordLength)
	}

	hashBytes, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return "", fmt.Errorf("hash share password failed: %w", err)
	}
	return string(hashBytes), nil
}

func validateEncryptionMetadata(encryption EncryptionMetadata) error {
	if strings.TrimSpace(encryption.Version) == "" {
		return fmt.Errorf("encryption.version is required")
	}
	if strings.TrimSpace(encryption.KDF) == "" {
		return fmt.Errorf("encryption.kdf is required")
	}
	if encryption.Iterations <= 0 {
		return fmt.Errorf("encryption.iterations must be greater than 0")
	}
	if strings.TrimSpace(encryption.Salt) == "" {
		return fmt.Errorf("encryption.salt is required")
	}
	if strings.TrimSpace(encryption.Nonce) == "" {
		return fmt.Errorf("encryption.nonce is required")
	}
	if strings.TrimSpace(encryption.Cipher) == "" {
		return fmt.Errorf("encryption.cipher is required")
	}
	return nil
}

func validateExpiresAt(expiresAt *time.Time) error {
	if expiresAt == nil {
		return nil
	}
	now := time.Now().UTC()
	if !expiresAt.After(now) {
		return fmt.Errorf("expires_at must be in the future")
	}
	if expiresAt.Sub(now) > time.Duration(maxShareExpireSeconds)*time.Second {
		return fmt.Errorf("expires_at is too far in the future")
	}
	return nil
}

func normalizeBurnConfig(rawMode string, burnAfterSeconds int) (string, int, error) {
	mode := strings.ToLower(strings.TrimSpace(rawMode))
	if mode == "" {
		mode = BurnModeNone
	}

	switch mode {
	case BurnModeNone:
		return BurnModeNone, 0, nil
	case BurnModeOnce:
		return BurnModeOnce, 0, nil
	case BurnModeCountdown:
		if burnAfterSeconds <= 0 {
			return "", 0, fmt.Errorf("burn_after_seconds must be greater than 0")
		}
		if int64(burnAfterSeconds) > maxShareExpireSeconds {
			return "", 0, fmt.Errorf("burn_after_seconds is too large")
		}
		return BurnModeCountdown, burnAfterSeconds, nil
	default:
		return "", 0, fmt.Errorf("invalid burn mode")
	}
}

func decorateShareItem(item *Item, now time.Time) {
	if item == nil {
		return
	}

	switch {
	case item.RevokedAt != nil:
		item.Status = StatusRevoked
	case item.ConsumedAt != nil:
		item.Status = StatusConsumed
	case item.BurnDeadline != nil && !item.BurnDeadline.After(now):
		item.Status = StatusConsumed
	case item.ExpiresAt != nil && !item.ExpiresAt.After(now):
		item.Status = StatusExpired
	default:
		item.Status = StatusActive
	}

	item.RemainingSeconds = calculateRemainingSeconds(*item, now)
}

func calculateRemainingSeconds(item Item, now time.Time) int64 {
	var deadline *time.Time
	if item.ExpiresAt != nil {
		deadline = item.ExpiresAt
	}
	if item.BurnDeadline != nil && (deadline == nil || item.BurnDeadline.Before(*deadline)) {
		deadline = item.BurnDeadline
	}
	if deadline == nil || !deadline.After(now) {
		return 0
	}
	return int64(math.Ceil(deadline.Sub(now).Seconds()))
}

func buildTextPreview(text string, limit int) string {
	cleaned := strings.Join(strings.Fields(strings.TrimSpace(text)), " ")
	if cleaned == "" || limit <= 0 {
		return ""
	}

	runes := []rune(cleaned)
	if len(runes) <= limit {
		return cleaned
	}
	return string(runes[:limit]) + "..."
}

func newPublicToken() (string, error) {
	raw := make([]byte, 24)
	if _, err := rand.Read(raw); err != nil {
		return "", fmt.Errorf("generate public token failed: %w", err)
	}
	return base64.RawURLEncoding.EncodeToString(raw), nil
}

func newFileID() (string, error) {
	fileID, err := id.NewUUID()
	if err != nil {
		return "", fmt.Errorf("generate share file id failed: %w", err)
	}
	return fileID, nil
}

func (s *Service) hydrateShareFiles(ctx context.Context, item *Item) error {
	if item == nil || s == nil || s.repo == nil {
		return nil
	}

	files, err := s.repo.ListShareFiles(ctx, item.ID)
	if err != nil {
		return err
	}
	if len(files) > 0 {
		item.Files = files
		return nil
	}

	// 多文件能力上线前，旧数据仍然直接把文件元数据放在 share_items 上。
	// 这里保留一个兼容分支，避免历史分享在升级后突然“没有文件”。
	if legacyFile, ok := item.LegacyFile(); ok {
		item.Files = []ShareFile{legacyFile}
	}
	return nil
}

func selectShareFile(item Item, fileID string) (ShareFile, error) {
	if len(item.Files) == 0 {
		return ShareFile{}, ErrFileBodyMissing
	}

	normalizedFileID := strings.TrimSpace(fileID)
	if normalizedFileID == "" {
		return item.Files[0], nil
	}

	for _, file := range item.Files {
		if file.ID == normalizedFileID {
			return file, nil
		}
	}
	return ShareFile{}, ErrFileBodyMissing
}

func buildShareFilesFromParams(shareID string, params []ShareFileParams) []ShareFile {
	files := make([]ShareFile, 0, len(params))
	for _, file := range params {
		files = append(files, ShareFile{
			ID:           file.ID,
			ShareID:      shareID,
			SortOrder:    file.SortOrder,
			OriginalName: file.OriginalName,
			StoredPath:   file.StoredPath,
			ContentType:  file.ContentType,
			SizeBytes:    file.SizeBytes,
			SHA256:       file.SHA256,
			Encryption:   file.Encryption,
		})
	}
	return files
}

func validateFileName(name string) (string, error) {
	name = strings.TrimSpace(name)
	name = filepath.Base(strings.ReplaceAll(name, "\\", "/"))

	if name == "" || name == "." {
		return "", fmt.Errorf("file name is required")
	}
	if len([]rune(name)) > maxFileNameLength {
		return "", fmt.Errorf("file name must be at most %d characters", maxFileNameLength)
	}
	return name, nil
}

func normalizeContentType(contentType string) string {
	contentType = strings.TrimSpace(contentType)
	if contentType == "" {
		return "application/octet-stream"
	}
	return contentType
}

func hasTextSharePayload(textContent, encryptedPayload string) bool {
	return textContent != "" || strings.TrimSpace(encryptedPayload) != ""
}
