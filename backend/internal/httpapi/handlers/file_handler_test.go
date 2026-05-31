package handlers

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"clipbridge/backend/internal/files"
	"clipbridge/backend/internal/httpapi/authcontext"
)

func TestFileHandlerUploadRejectsBadMultipart(t *testing.T) {
	handler := &FileHandler{
		fileService: &stubFileService{},
	}

	request := httptest.NewRequest(http.MethodPost, "/v1/files", strings.NewReader("not-multipart"))
	request = request.WithContext(authcontext.WithIdentity(request.Context(), authcontext.Identity{
		UserID:   "user-1",
		DeviceID: "device-1",
	}))
	request.Header.Set("Content-Type", "application/json")

	recorder := httptest.NewRecorder()
	handler.Upload(recorder, request)

	if recorder.Code != http.StatusBadRequest {
		t.Fatalf("expected status 400, got %d", recorder.Code)
	}
}

func TestFileHandlerUploadRejectsMissingFileField(t *testing.T) {
	handler := &FileHandler{
		fileService: &stubFileService{},
	}

	body := &bytes.Buffer{}
	writer := multipart.NewWriter(body)
	_ = writer.WriteField("note", "hello")
	_ = writer.Close()

	request := httptest.NewRequest(http.MethodPost, "/v1/files", body)
	request = request.WithContext(authcontext.WithIdentity(request.Context(), authcontext.Identity{
		UserID:   "user-1",
		DeviceID: "device-1",
	}))
	request.Header.Set("Content-Type", writer.FormDataContentType())

	recorder := httptest.NewRecorder()
	handler.Upload(recorder, request)

	if recorder.Code != http.StatusBadRequest {
		t.Fatalf("expected status 400, got %d", recorder.Code)
	}
}

func TestFileHandlerRenameReturnsNotFound(t *testing.T) {
	handler := &FileHandler{
		fileService: &stubFileService{
			renameErr: files.ErrNotFound,
		},
	}

	request := httptest.NewRequest(http.MethodPatch, "/v1/files/file-1", strings.NewReader(`{"original_name":"new-name.txt"}`))
	request = request.WithContext(authcontext.WithIdentity(request.Context(), authcontext.Identity{
		UserID:   "user-1",
		DeviceID: "device-1",
	}))
	request.SetPathValue("id", "file-1")
	request.Header.Set("Content-Type", "application/json")

	recorder := httptest.NewRecorder()
	handler.Rename(recorder, request)

	if recorder.Code != http.StatusNotFound {
		t.Fatalf("expected status 404, got %d", recorder.Code)
	}
}

func TestFileHandlerDeleteReturnsNotFound(t *testing.T) {
	handler := &FileHandler{
		fileService: &stubFileService{
			deleteErr: files.ErrNotFound,
		},
	}

	request := httptest.NewRequest(http.MethodDelete, "/v1/files/file-1", nil)
	request = request.WithContext(authcontext.WithIdentity(request.Context(), authcontext.Identity{
		UserID:   "user-1",
		DeviceID: "device-1",
	}))
	request.SetPathValue("id", "file-1")

	recorder := httptest.NewRecorder()
	handler.Delete(recorder, request)

	if recorder.Code != http.StatusNotFound {
		t.Fatalf("expected status 404, got %d", recorder.Code)
	}
}

type stubFileService struct {
	renameErr error
	deleteErr error
}

func (s *stubFileService) Upload(context.Context, string, string, string, string, io.Reader) (files.Item, error) {
	return files.Item{}, nil
}

func (s *stubFileService) List(context.Context, string, string, int, int) (files.ListResult, error) {
	return files.ListResult{}, nil
}

func (s *stubFileService) OpenDownload(context.Context, string, string, string) (files.DownloadResult, error) {
	return files.DownloadResult{}, nil
}

func (s *stubFileService) Rename(context.Context, string, string, string, string) (files.Item, error) {
	if s.renameErr != nil {
		return files.Item{}, s.renameErr
	}
	return files.Item{
		ID:             "file-1",
		OriginalName:   "new-name.txt",
		ContentType:    "text/plain",
		SizeBytes:      5,
		FileSHA256:     "hash",
		OriginDeviceID: "device-1",
		CreatedAt:      time.Unix(0, 0).UTC(),
	}, nil
}

func (s *stubFileService) Delete(context.Context, string, string, string) (files.DeleteResult, error) {
	if s.deleteErr != nil {
		return files.DeleteResult{}, s.deleteErr
	}
	return files.DeleteResult{}, nil
}

func TestBuildFileItemDataFormatsTime(t *testing.T) {
	item := files.Item{
		ID:               "file-1",
		OriginalName:     "hello.txt",
		ContentType:      "text/plain",
		SizeBytes:        5,
		FileSHA256:       "hash",
		OriginDeviceID:   "device-1",
		OriginDeviceName: "Chrome",
		CreatedAt:        time.Date(2026, 5, 31, 1, 2, 3, 0, time.UTC),
	}

	data := buildFileItemData(item)
	if data.CreatedAt != "2026-05-31T01:02:03Z" {
		t.Fatalf("unexpected created_at: %s", data.CreatedAt)
	}
}

func TestFileHandlerListResponseShape(t *testing.T) {
	handler := &FileHandler{
		fileService: &stubFileServiceWithList{},
	}

	request := httptest.NewRequest(http.MethodGet, "/v1/files?page=1&page_size=20", nil)
	request = request.WithContext(authcontext.WithIdentity(request.Context(), authcontext.Identity{
		UserID:   "user-1",
		DeviceID: "device-1",
	}))

	recorder := httptest.NewRecorder()
	handler.List(recorder, request)

	if recorder.Code != http.StatusOK {
		t.Fatalf("expected status 200, got %d", recorder.Code)
	}

	var envelope map[string]any
	if err := json.Unmarshal(recorder.Body.Bytes(), &envelope); err != nil {
		t.Fatalf("unmarshal response failed: %v", err)
	}
	if envelope["code"].(float64) != 0 {
		t.Fatalf("expected code 0, got %v", envelope["code"])
	}
}

type stubFileServiceWithList struct{}

func (s *stubFileServiceWithList) Upload(context.Context, string, string, string, string, io.Reader) (files.Item, error) {
	return files.Item{}, nil
}

func (s *stubFileServiceWithList) List(context.Context, string, string, int, int) (files.ListResult, error) {
	return files.ListResult{
		Items: []files.Item{
			{
				ID:               "file-1",
				OriginalName:     "hello.txt",
				ContentType:      "text/plain",
				SizeBytes:        5,
				FileSHA256:       "hash",
				OriginDeviceID:   "device-1",
				OriginDeviceName: "Chrome",
				CreatedAt:        time.Unix(0, 0).UTC(),
			},
		},
		Page:       1,
		PageSize:   20,
		TotalFiles: 1,
		TotalPages: 1,
		Summary: files.Summary{
			TotalFiles:     1,
			TotalBytes:     5,
			MaxUploadBytes: 64,
		},
	}, nil
}

func (s *stubFileServiceWithList) OpenDownload(context.Context, string, string, string) (files.DownloadResult, error) {
	return files.DownloadResult{}, nil
}

func (s *stubFileServiceWithList) Rename(context.Context, string, string, string, string) (files.Item, error) {
	return files.Item{}, nil
}

func (s *stubFileServiceWithList) Delete(context.Context, string, string, string) (files.DeleteResult, error) {
	return files.DeleteResult{}, nil
}
