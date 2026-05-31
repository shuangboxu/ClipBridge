package files

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"clipbridge/backend/internal/filestore"
)

const (
	defaultListPage     = 1
	defaultListPageSize = 20
	maxListPageSize     = 100
	maxFileNameLength   = 255
)

type Storage interface {
	Save(ctx context.Context, userID, originalName string, src io.Reader, maxBytes int64) (filestore.SaveResult, error)
	Open(storedPath string) (*os.File, int64, error)
	Delete(storedPath string) error
}

type FileBody interface {
	io.ReadCloser
}

type Service struct {
	repo           Repository
	store          Storage
	maxUploadBytes int64
}

func NewService(repo Repository, store Storage, maxUploadBytes int64) *Service {
	return &Service{
		repo:           repo,
		store:          store,
		maxUploadBytes: maxUploadBytes,
	}
}

func (s *Service) Upload(ctx context.Context, userID, deviceID, originalName, contentType string, src io.Reader) (Item, error) {
	if s == nil || s.repo == nil || s.store == nil {
		return Item{}, fmt.Errorf("file service is not ready")
	}
	if src == nil {
		return Item{}, fmt.Errorf("file is required")
	}

	normalizedName, err := validateFileName(originalName)
	if err != nil {
		return Item{}, err
	}
	contentType = normalizeContentType(contentType)

	if err := s.repo.TouchDeviceLastSeen(ctx, userID, deviceID); err != nil {
		return Item{}, err
	}

	originDeviceName, err := s.repo.GetDeviceSnapshot(ctx, userID, deviceID)
	if err != nil {
		return Item{}, err
	}

	saved, err := s.store.Save(ctx, userID, normalizedName, src, s.maxUploadBytes)
	if err != nil {
		switch {
		case errors.Is(err, filestore.ErrFileTooLarge):
			return Item{}, ErrFileTooLarge
		default:
			return Item{}, fmt.Errorf("save file body failed: %w", err)
		}
	}

	// 先把文件体写到磁盘，再写数据库。
	// 如果元数据写失败，立刻把磁盘文件删掉，避免留下孤儿文件。
	item, err := s.repo.CreateFile(ctx, CreateFileParams{
		UserID:           userID,
		OriginalName:     normalizedName,
		StoredPath:       saved.StoredPath,
		ContentType:      contentType,
		SizeBytes:        saved.SizeBytes,
		FileSHA256:       saved.SHA256,
		OriginDeviceID:   deviceID,
		OriginDeviceName: originDeviceName,
	})
	if err != nil {
		_ = s.store.Delete(saved.StoredPath)
		return Item{}, err
	}
	return item, nil
}

func (s *Service) List(ctx context.Context, userID, deviceID string, page, pageSize int) (ListResult, error) {
	if s == nil || s.repo == nil {
		return ListResult{}, fmt.Errorf("file service is not ready")
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

	options := ListOptions{
		Page:     normalizePage(page),
		PageSize: normalizePageSize(pageSize),
	}
	items, totalFiles, totalBytes, err := s.repo.ListFiles(ctx, userID, options)
	if err != nil {
		return ListResult{}, err
	}

	totalPages := 0
	if totalFiles > 0 {
		totalPages = (totalFiles + options.PageSize - 1) / options.PageSize
	}

	return ListResult{
		Items:      items,
		Page:       options.Page,
		PageSize:   options.PageSize,
		TotalFiles: totalFiles,
		TotalBytes: totalBytes,
		TotalPages: totalPages,
		Summary: Summary{
			TotalFiles:     totalFiles,
			TotalBytes:     totalBytes,
			MaxUploadBytes: s.maxUploadBytes,
		},
	}, nil
}

func (s *Service) OpenDownload(ctx context.Context, userID, deviceID, fileID string) (DownloadResult, error) {
	if s == nil || s.repo == nil || s.store == nil {
		return DownloadResult{}, fmt.Errorf("file service is not ready")
	}
	if strings.TrimSpace(fileID) == "" {
		return DownloadResult{}, fmt.Errorf("file id is required")
	}

	if err := s.repo.TouchDeviceLastSeen(ctx, userID, deviceID); err != nil {
		return DownloadResult{}, err
	}

	item, err := s.repo.GetFile(ctx, userID, strings.TrimSpace(fileID))
	if err != nil {
		return DownloadResult{}, err
	}

	file, sizeBytes, err := s.store.Open(item.StoredPath)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return DownloadResult{}, ErrFileBodyMissing
		}
		return DownloadResult{}, fmt.Errorf("open file body failed: %w", err)
	}

	return DownloadResult{
		Item:      item,
		File:      file,
		SizeBytes: sizeBytes,
	}, nil
}

func (s *Service) Rename(ctx context.Context, userID, deviceID, fileID, originalName string) (Item, error) {
	if s == nil || s.repo == nil {
		return Item{}, fmt.Errorf("file service is not ready")
	}
	if strings.TrimSpace(fileID) == "" {
		return Item{}, fmt.Errorf("file id is required")
	}

	normalizedName, err := validateFileName(originalName)
	if err != nil {
		return Item{}, err
	}
	if err := s.repo.TouchDeviceLastSeen(ctx, userID, deviceID); err != nil {
		return Item{}, err
	}

	return s.repo.RenameFile(ctx, userID, strings.TrimSpace(fileID), normalizedName)
}

func (s *Service) Delete(ctx context.Context, userID, deviceID, fileID string) (DeleteResult, error) {
	if s == nil || s.repo == nil || s.store == nil {
		return DeleteResult{}, fmt.Errorf("file service is not ready")
	}
	if strings.TrimSpace(fileID) == "" {
		return DeleteResult{}, fmt.Errorf("file id is required")
	}
	if err := s.repo.TouchDeviceLastSeen(ctx, userID, deviceID); err != nil {
		return DeleteResult{}, err
	}

	item, deleted, err := s.repo.DeleteFile(ctx, userID, strings.TrimSpace(fileID))
	if err != nil {
		return DeleteResult{}, err
	}
	if !deleted {
		return DeleteResult{}, ErrNotFound
	}

	diskRemoved := true
	if err := s.store.Delete(item.StoredPath); err != nil {
		diskRemoved = false
	}

	return DeleteResult{
		Item:        item,
		DiskRemoved: diskRemoved,
	}, nil
}

func (s *Service) MaxUploadBytes() int64 {
	if s == nil {
		return 0
	}
	return s.maxUploadBytes
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

func normalizeContentType(contentType string) string {
	contentType = strings.TrimSpace(contentType)
	if contentType == "" {
		return "application/octet-stream"
	}
	return contentType
}

func validateFileName(name string) (string, error) {
	name = strings.TrimSpace(name)
	name = filepath.Base(strings.ReplaceAll(name, "\\", "/"))

	if name == "" || name == "." {
		return "", fmt.Errorf("file name is required")
	}
	if len(name) > maxFileNameLength {
		return "", fmt.Errorf("file name must be at most %d characters", maxFileNameLength)
	}
	if strings.Contains(name, "/") || strings.Contains(name, "\\") {
		return "", fmt.Errorf("file name cannot contain path separators")
	}
	for _, r := range name {
		if r < 32 || r == 127 {
			return "", fmt.Errorf("file name cannot contain control characters")
		}
	}
	return name, nil
}
