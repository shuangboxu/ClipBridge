package handlers

import (
	"context"
	"errors"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"strings"

	"clipbridge/backend/internal/app"
	"clipbridge/backend/internal/files"
	"clipbridge/backend/internal/httpapi/authcontext"
	"clipbridge/backend/internal/httpapi/response"
)

type fileService interface {
	Upload(ctx context.Context, userID, deviceID, originalName, contentType string, src io.Reader) (files.Item, error)
	List(ctx context.Context, userID, deviceID string, page, pageSize int) (files.ListResult, error)
	OpenDownload(ctx context.Context, userID, deviceID, fileID string) (files.DownloadResult, error)
	Rename(ctx context.Context, userID, deviceID, fileID, originalName string) (files.Item, error)
	Delete(ctx context.Context, userID, deviceID, fileID string) (files.DeleteResult, error)
}

type FileHandler struct {
	fileService fileService
}

type renameFileRequest struct {
	OriginalName string `json:"original_name"`
}

type fileItemData struct {
	ID               string `json:"id"`
	OriginalName     string `json:"original_name"`
	ContentType      string `json:"content_type"`
	SizeBytes        int64  `json:"size_bytes"`
	FileSHA256       string `json:"file_sha256"`
	OriginDeviceID   string `json:"origin_device_id"`
	OriginDeviceName string `json:"origin_device_name"`
	CreatedAt        string `json:"created_at"`
}

func NewFileHandler(application *app.App) *FileHandler {
	if application == nil {
		return &FileHandler{}
	}
	return &FileHandler{
		fileService: application.FileService,
	}
}

func (h *FileHandler) Upload(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}
	if h.fileService == nil {
		response.Error(w, r, http.StatusInternalServerError, "file service is not ready")
		return
	}

	reader, err := r.MultipartReader()
	if err != nil {
		response.Error(w, r, http.StatusBadRequest, "multipart/form-data is required")
		return
	}

	for {
		part, err := reader.NextPart()
		if err != nil {
			if errors.Is(err, io.EOF) {
				response.Error(w, r, http.StatusBadRequest, "file field is required")
				return
			}
			response.Error(w, r, http.StatusBadRequest, "invalid multipart body")
			return
		}

		if part.FormName() != "file" {
			_ = part.Close()
			continue
		}

		item, err := h.fileService.Upload(
			r.Context(),
			identity.UserID,
			identity.DeviceID,
			part.FileName(),
			part.Header.Get("Content-Type"),
			part,
		)
		_ = part.Close()
		if err != nil {
			h.writeFileError(w, r, err, "upload")
			return
		}

		response.Created(w, r, map[string]any{
			"file": buildFileItemData(item),
		})
		return
	}
}

func (h *FileHandler) List(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}
	if h.fileService == nil {
		response.Error(w, r, http.StatusInternalServerError, "file service is not ready")
		return
	}

	page, err := parseOptionalPositiveInt(r.URL.Query().Get("page"))
	if err != nil {
		response.Error(w, r, http.StatusBadRequest, "page must be a positive integer")
		return
	}
	pageSize, err := parseOptionalPositiveInt(r.URL.Query().Get("page_size"))
	if err != nil {
		response.Error(w, r, http.StatusBadRequest, "page_size must be a positive integer")
		return
	}

	result, err := h.fileService.List(r.Context(), identity.UserID, identity.DeviceID, page, pageSize)
	if err != nil {
		h.writeFileError(w, r, err, "list")
		return
	}

	items := make([]fileItemData, 0, len(result.Items))
	for _, item := range result.Items {
		items = append(items, buildFileItemData(item))
	}

	response.OK(w, r, map[string]any{
		"files": items,
		"pagination": map[string]any{
			"page":        result.Page,
			"page_size":   result.PageSize,
			"total":       result.TotalFiles,
			"total_pages": result.TotalPages,
		},
		"summary": map[string]any{
			"total_files":      result.Summary.TotalFiles,
			"total_bytes":      result.Summary.TotalBytes,
			"max_upload_bytes": result.Summary.MaxUploadBytes,
		},
	})
}

func (h *FileHandler) Download(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}
	if h.fileService == nil {
		response.Error(w, r, http.StatusInternalServerError, "file service is not ready")
		return
	}

	fileID := strings.TrimSpace(r.PathValue("id"))
	result, err := h.fileService.OpenDownload(r.Context(), identity.UserID, identity.DeviceID, fileID)
	if err != nil {
		h.writeFileError(w, r, err, "download")
		return
	}
	defer result.File.Close()

	contentType := result.Item.ContentType
	if strings.TrimSpace(contentType) == "" {
		contentType = "application/octet-stream"
	}

	fileName := url.QueryEscape(result.Item.OriginalName)
	w.Header().Set("Content-Type", contentType)
	w.Header().Set("Content-Length", strconv.FormatInt(result.SizeBytes, 10))
	w.Header().Set("Content-Disposition", "attachment; filename*=UTF-8''"+fileName)
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(http.StatusOK)

	if _, err := io.Copy(w, result.File); err != nil {
		return
	}
}

func (h *FileHandler) Rename(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}
	if h.fileService == nil {
		response.Error(w, r, http.StatusInternalServerError, "file service is not ready")
		return
	}

	var req renameFileRequest
	if err := decodeJSONBody(r, &req); err != nil {
		response.Error(w, r, http.StatusBadRequest, "invalid request body")
		return
	}

	item, err := h.fileService.Rename(r.Context(), identity.UserID, identity.DeviceID, r.PathValue("id"), req.OriginalName)
	if err != nil {
		h.writeFileError(w, r, err, "rename")
		return
	}

	response.OK(w, r, map[string]any{
		"file": buildFileItemData(item),
	})
}

func (h *FileHandler) Delete(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}
	if h.fileService == nil {
		response.Error(w, r, http.StatusInternalServerError, "file service is not ready")
		return
	}

	result, err := h.fileService.Delete(r.Context(), identity.UserID, identity.DeviceID, r.PathValue("id"))
	if err != nil {
		h.writeFileError(w, r, err, "delete")
		return
	}

	response.OK(w, r, map[string]any{
		"file":         buildFileItemData(result.Item),
		"disk_removed": result.DiskRemoved,
	})
}

func (h *FileHandler) writeFileError(w http.ResponseWriter, r *http.Request, err error, action string) {
	switch {
	case err == nil:
		return
	case errors.Is(err, files.ErrNotFound):
		response.Error(w, r, http.StatusNotFound, "file not found")
	case errors.Is(err, files.ErrFileTooLarge):
		response.Error(w, r, http.StatusRequestEntityTooLarge, "file is too large")
	case errors.Is(err, files.ErrFileBodyMissing):
		response.Error(w, r, http.StatusNotFound, "file body not found")
	case isFileValidationError(err):
		response.Error(w, r, http.StatusBadRequest, err.Error())
	default:
		response.Error(w, r, http.StatusInternalServerError, action+" file failed")
	}
}

func buildFileItemData(item files.Item) fileItemData {
	return fileItemData{
		ID:               item.ID,
		OriginalName:     item.OriginalName,
		ContentType:      item.ContentType,
		SizeBytes:        item.SizeBytes,
		FileSHA256:       item.FileSHA256,
		OriginDeviceID:   item.OriginDeviceID,
		OriginDeviceName: item.OriginDeviceName,
		CreatedAt:        formatTime(item.CreatedAt),
	}
}

func isFileValidationError(err error) bool {
	if err == nil {
		return false
	}

	message := err.Error()
	return strings.Contains(message, "is required") ||
		strings.Contains(message, "at most") ||
		strings.Contains(message, "cannot contain") ||
		strings.Contains(message, "greater than or equal to 0")
}
