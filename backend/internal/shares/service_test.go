package shares

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

func TestServiceCreateTextShareRejectsMissingText(t *testing.T) {
	service := NewService(&fakeRepository{}, &fakeStorage{}, fakePolicyProvider{maxUploadBytes: 1024})

	_, err := service.CreateTextShare(context.Background(), "user-1", "device-1", CreateTextShareInput{})
	if err == nil || !strings.Contains(err.Error(), "text_content is required") {
		t.Fatalf("expected missing text error, got %v", err)
	}
}

func TestServiceCreateTextShareRequiresEncryptionMetadata(t *testing.T) {
	service := NewService(&fakeRepository{}, &fakeStorage{}, fakePolicyProvider{maxUploadBytes: 1024})

	_, err := service.CreateTextShare(context.Background(), "user-1", "device-1", CreateTextShareInput{
		IsEncrypted:      true,
		EncryptedPayload: "ciphertext",
		Password:         "1234",
	})
	if err == nil || !strings.Contains(err.Error(), "encryption.version is required") {
		t.Fatalf("expected encryption metadata error, got %v", err)
	}
}

func TestServiceListNormalizesPaginationAndStatus(t *testing.T) {
	repo := &fakeRepository{}
	service := NewService(repo, &fakeStorage{}, fakePolicyProvider{maxUploadBytes: 1024})

	result, err := service.List(context.Background(), "user-1", "device-1", 0, 999, "")
	if err != nil {
		t.Fatalf("list shares failed: %v", err)
	}
	if repo.lastListOptions.Page != 1 {
		t.Fatalf("expected normalized page 1, got %d", repo.lastListOptions.Page)
	}
	if repo.lastListOptions.PageSize != 100 {
		t.Fatalf("expected normalized page size 100, got %d", repo.lastListOptions.PageSize)
	}
	if repo.lastListOptions.Status != StatusAll {
		t.Fatalf("expected default status %q, got %q", StatusAll, repo.lastListOptions.Status)
	}
	if result.Page != 1 || result.PageSize != 100 || result.Status != StatusAll {
		t.Fatalf("unexpected pagination result: %+v", result)
	}
}

func TestServiceCreateFileShareRollsBackStoredFileWhenMetadataFails(t *testing.T) {
	repo := &fakeRepository{
		createFileErr: errors.New("insert failed"),
	}
	store := &fakeStorage{
		saveResult: filestore.SaveResult{
			StoredPath: "user-1/file.bin",
			SizeBytes:  5,
			SHA256:     "hash",
		},
	}
	service := NewService(repo, store, fakePolicyProvider{maxUploadBytes: 1024})

	_, err := service.CreateFileShare(context.Background(), "user-1", "device-1", CreateFileShareInput{
		Files: []ShareFileInput{
			{
				UploadName:   "share.bin",
				OriginalName: "hello.txt",
				ContentType:  "text/plain",
			},
		},
	}, []io.Reader{strings.NewReader("hello")})
	if err == nil {
		t.Fatalf("expected create file share error")
	}
	if store.deletedPath != "user-1/file.bin" {
		t.Fatalf("expected rollback delete, got %q", store.deletedPath)
	}
}

type fakeRepository struct {
	createFileErr   error
	lastListOptions ListOptions
}

func (r *fakeRepository) TouchDeviceLastSeen(context.Context, string, string) error {
	return nil
}

func (r *fakeRepository) CreateTextShare(_ context.Context, params CreateTextShareParams) (Item, error) {
	return Item{
		ID:               "share-1",
		UserID:           params.UserID,
		PublicToken:      params.PublicToken,
		ContentKind:      ContentKindText,
		TextContent:      params.TextContent,
		TextPreview:      params.TextPreview,
		IsEncrypted:      params.IsEncrypted,
		AllowCopyContent: params.AllowCopyContent,
		BurnMode:         params.BurnMode,
		BurnAfterSeconds: params.BurnAfterSeconds,
		CreatedAt:        time.Unix(0, 0).UTC(),
		UpdatedAt:        time.Unix(0, 0).UTC(),
	}, nil
}

func (r *fakeRepository) CreateFileShare(_ context.Context, params CreateFileShareParams) (Item, error) {
	if r.createFileErr != nil {
		return Item{}, r.createFileErr
	}
	return Item{
		ID:               "share-1",
		UserID:           params.UserID,
		PublicToken:      params.PublicToken,
		ContentKind:      ContentKindFile,
		FileOriginalName: params.FileOriginalName,
		FileStoredPath:   params.FileStoredPath,
		FileContentType:  params.FileContentType,
		FileSizeBytes:    params.FileSizeBytes,
		FileSHA256:       params.FileSHA256,
		CreatedAt:        time.Unix(0, 0).UTC(),
		UpdatedAt:        time.Unix(0, 0).UTC(),
	}, nil
}

func (r *fakeRepository) ListShares(_ context.Context, _ string, options ListOptions) ([]Item, int, error) {
	r.lastListOptions = options
	return nil, 0, nil
}

func (r *fakeRepository) ListShareFiles(context.Context, string) ([]ShareFile, error) {
	return nil, nil
}

func (r *fakeRepository) RevokeShare(context.Context, string, string) (Item, error) {
	return Item{}, nil
}

func (r *fakeRepository) GetShareByToken(context.Context, string) (Item, error) {
	return Item{}, nil
}

func (r *fakeRepository) OpenShareByToken(context.Context, string, string, time.Time) (Item, error) {
	return Item{}, nil
}

type fakeStorage struct {
	saveResult  filestore.SaveResult
	saveErr     error
	deletedPath string
}

type fakePolicyProvider struct {
	maxUploadBytes int64
}

func (p fakePolicyProvider) PrepareUploadReader(_ context.Context, _ string, src io.Reader) (io.Reader, int64, error) {
	return src, p.maxUploadBytes, nil
}

func (p fakePolicyProvider) CurrentMaxUploadBytes(context.Context, string) (int64, error) {
	return p.maxUploadBytes, nil
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
