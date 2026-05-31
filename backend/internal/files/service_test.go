package files

import (
	"context"
	"errors"
	"io"
	"os"
	"strings"
	"testing"
	"time"

	"clipbridge/backend/internal/filestore"
)

func TestServiceUploadRejectsEmptyFileName(t *testing.T) {
	service := NewService(&fakeRepository{}, &fakeStorage{}, 1024)

	_, err := service.Upload(context.Background(), "user-1", "device-1", "", "text/plain", strings.NewReader("hello"))
	if err == nil || !strings.Contains(err.Error(), "file name is required") {
		t.Fatalf("expected empty file name error, got %v", err)
	}
}

func TestServiceRenameRejectsEmptyFileName(t *testing.T) {
	service := NewService(&fakeRepository{}, &fakeStorage{}, 1024)

	_, err := service.Rename(context.Background(), "user-1", "device-1", "file-1", "")
	if err == nil || !strings.Contains(err.Error(), "file name is required") {
		t.Fatalf("expected empty rename error, got %v", err)
	}
}

func TestServiceListNormalizesPagination(t *testing.T) {
	repo := &fakeRepository{}
	service := NewService(repo, &fakeStorage{}, 1024)

	result, err := service.List(context.Background(), "user-1", "device-1", 0, 999)
	if err != nil {
		t.Fatalf("list files failed: %v", err)
	}
	if repo.lastListOptions.Page != 1 {
		t.Fatalf("expected normalized page 1, got %d", repo.lastListOptions.Page)
	}
	if repo.lastListOptions.PageSize != 100 {
		t.Fatalf("expected normalized page size 100, got %d", repo.lastListOptions.PageSize)
	}
	if result.Page != 1 || result.PageSize != 100 {
		t.Fatalf("unexpected pagination result: %+v", result)
	}
}

func TestServiceUploadRollsBackStoredFileWhenMetadataFails(t *testing.T) {
	repo := &fakeRepository{
		createFileErr: errors.New("insert failed"),
		deviceName:    "My Device",
	}
	store := &fakeStorage{
		saveResult: filestore.SaveResult{
			StoredPath: "user-1/file.txt",
			SizeBytes:  5,
			SHA256:     "hash",
		},
	}
	service := NewService(repo, store, 1024)

	_, err := service.Upload(context.Background(), "user-1", "device-1", "file.txt", "text/plain", strings.NewReader("hello"))
	if err == nil {
		t.Fatalf("expected upload error")
	}
	if store.deletedPath != "user-1/file.txt" {
		t.Fatalf("expected rollback delete, got %q", store.deletedPath)
	}
}

func TestServiceUploadMapsTooLargeError(t *testing.T) {
	repo := &fakeRepository{deviceName: "My Device"}
	store := &fakeStorage{saveErr: filestore.ErrFileTooLarge}
	service := NewService(repo, store, 3)

	_, err := service.Upload(context.Background(), "user-1", "device-1", "file.txt", "text/plain", strings.NewReader("hello"))
	if !errors.Is(err, ErrFileTooLarge) {
		t.Fatalf("expected ErrFileTooLarge, got %v", err)
	}
}

type fakeRepository struct {
	deviceName      string
	createFileErr   error
	lastListOptions ListOptions
}

func (r *fakeRepository) TouchDeviceLastSeen(context.Context, string, string) error {
	return nil
}

func (r *fakeRepository) GetDeviceSnapshot(context.Context, string, string) (string, error) {
	return r.deviceName, nil
}

func (r *fakeRepository) CreateFile(_ context.Context, params CreateFileParams) (Item, error) {
	if r.createFileErr != nil {
		return Item{}, r.createFileErr
	}
	return Item{
		ID:               "file-1",
		UserID:           params.UserID,
		OriginalName:     params.OriginalName,
		StoredPath:       params.StoredPath,
		ContentType:      params.ContentType,
		SizeBytes:        params.SizeBytes,
		FileSHA256:       params.FileSHA256,
		OriginDeviceID:   params.OriginDeviceID,
		OriginDeviceName: params.OriginDeviceName,
		CreatedAt:        time.Unix(0, 0).UTC(),
	}, nil
}

func (r *fakeRepository) ListFiles(_ context.Context, _ string, options ListOptions) ([]Item, int, int64, error) {
	r.lastListOptions = options
	return nil, 0, 0, nil
}

func (r *fakeRepository) GetFile(context.Context, string, string) (Item, error) {
	return Item{}, nil
}

func (r *fakeRepository) RenameFile(context.Context, string, string, string) (Item, error) {
	return Item{}, nil
}

func (r *fakeRepository) DeleteFile(context.Context, string, string) (Item, bool, error) {
	return Item{}, false, nil
}

type fakeStorage struct {
	saveResult  filestore.SaveResult
	saveErr     error
	deletedPath string
}

func (s *fakeStorage) Save(context.Context, string, string, io.Reader, int64) (filestore.SaveResult, error) {
	if s.saveErr != nil {
		return filestore.SaveResult{}, s.saveErr
	}
	return s.saveResult, nil
}

func (s *fakeStorage) Open(string) (*os.File, int64, error) {
	return nil, 0, os.ErrNotExist
}

func (s *fakeStorage) Delete(storedPath string) error {
	s.deletedPath = storedPath
	return nil
}
