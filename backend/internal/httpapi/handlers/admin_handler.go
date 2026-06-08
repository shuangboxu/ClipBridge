package handlers

import (
	"context"
	"errors"
	"net/http"

	"clipbridge/backend/internal/admin"
	"clipbridge/backend/internal/app"
	"clipbridge/backend/internal/httpapi/authcontext"
	"clipbridge/backend/internal/httpapi/response"
)

type AdminHandler struct {
	adminService adminHandlerService
}

type adminHandlerService interface {
	GetSettings(ctx context.Context) (admin.SystemSettings, error)
	CountUsers(ctx context.Context) (int, error)
	UpdateSettings(ctx context.Context, input admin.UpdateSettingsInput) (admin.SystemSettings, error)
	ListUsers(ctx context.Context) ([]admin.UserSummary, error)
	UpdateUser(ctx context.Context, userID string, input admin.UpdateUserInput) (admin.UserSummary, error)
	DeleteUser(ctx context.Context, userID string) (admin.DeleteUserResult, error)
	ListQuotaRequestsForAdmin(ctx context.Context, status string) ([]admin.QuotaRequest, error)
	ApproveQuotaRequest(ctx context.Context, requestID, reviewerID string, input admin.ApproveQuotaRequestInput) (admin.QuotaRequest, error)
	RejectQuotaRequest(ctx context.Context, requestID, reviewerID string, input admin.RejectRequestInput) (admin.QuotaRequest, error)
	ListBandwidthRequestsForAdmin(ctx context.Context, status string) ([]admin.BandwidthRequest, error)
	ApproveBandwidthRequest(ctx context.Context, requestID, reviewerID string, input admin.ApproveBandwidthRequestInput) (admin.BandwidthRequest, error)
	RejectBandwidthRequest(ctx context.Context, requestID, reviewerID string, input admin.RejectRequestInput) (admin.BandwidthRequest, error)
	ListAdminRequestsForAdmin(ctx context.Context, status string) ([]admin.AdminRequest, error)
	ApproveAdminRequest(ctx context.Context, requestID, reviewerID string, input admin.ApproveAdminRequestInput) (admin.AdminRequest, error)
	RejectAdminRequest(ctx context.Context, requestID, reviewerID string, input admin.RejectRequestInput) (admin.AdminRequest, error)
}

type updateAdminSettingsRequest struct {
	MaxUserCount                 *int   `json:"max_user_count"`
	DefaultStorageQuotaMB        *int64 `json:"default_storage_quota_mb"`
	DefaultUploadBandwidthKbps   *int   `json:"default_upload_bandwidth_kbps"`
	DefaultDownloadBandwidthKbps *int   `json:"default_download_bandwidth_kbps"`
	MaxUserUploadBandwidthKbps   *int   `json:"max_user_upload_bandwidth_kbps"`
	MaxUserDownloadBandwidthKbps *int   `json:"max_user_download_bandwidth_kbps"`
	MaxUploadFileMB              *int64 `json:"max_upload_file_mb"`
	AllowRegistration            *bool  `json:"allow_registration"`
}

type updateAdminUserRequest struct {
	StorageQuotaMB        *int64 `json:"storage_quota_mb"`
	UploadBandwidthKbps   *int   `json:"upload_bandwidth_kbps"`
	DownloadBandwidthKbps *int   `json:"download_bandwidth_kbps"`
	IsAdmin               *bool  `json:"is_admin"`
}

type approveQuotaRequestBody struct {
	ApprovedQuotaMB *int64 `json:"approved_quota_mb"`
	ReviewNote      string `json:"review_note"`
}

type approveBandwidthRequestBody struct {
	ApprovedUploadKbps   *int   `json:"approved_upload_kbps"`
	ApprovedDownloadKbps *int   `json:"approved_download_kbps"`
	ReviewNote           string `json:"review_note"`
}

type reviewOnlyRequestBody struct {
	ReviewNote string `json:"review_note"`
}

func NewAdminHandler(application *app.App) *AdminHandler {
	if application == nil {
		return &AdminHandler{}
	}
	handler := &AdminHandler{}
	if application.AdminService != nil {
		handler.adminService = application.AdminService
	}
	return handler
}

func (h *AdminHandler) GetSettings(w http.ResponseWriter, r *http.Request) {
	if h.adminService == nil {
		response.Error(w, r, http.StatusInternalServerError, "admin service is not ready")
		return
	}

	settings, err := h.adminService.GetSettings(r.Context())
	if err != nil {
		h.writeAdminError(w, r, err, "load settings")
		return
	}
	userCount, err := h.adminService.CountUsers(r.Context())
	if err != nil {
		response.Error(w, r, http.StatusInternalServerError, "count users failed")
		return
	}

	response.OK(w, r, map[string]any{
		"settings":           buildAdminSettingsData(settings),
		"current_user_count": userCount,
	})
}

func (h *AdminHandler) UpdateSettings(w http.ResponseWriter, r *http.Request) {
	if h.adminService == nil {
		response.Error(w, r, http.StatusInternalServerError, "admin service is not ready")
		return
	}

	var req updateAdminSettingsRequest
	if err := decodeJSONBody(r, &req); err != nil {
		response.Error(w, r, http.StatusBadRequest, "invalid request body")
		return
	}

	input := admin.UpdateSettingsInput{
		MaxUserCount:                 req.MaxUserCount,
		DefaultUploadBandwidthKbps:   req.DefaultUploadBandwidthKbps,
		DefaultDownloadBandwidthKbps: req.DefaultDownloadBandwidthKbps,
		MaxUserUploadBandwidthKbps:   req.MaxUserUploadBandwidthKbps,
		MaxUserDownloadBandwidthKbps: req.MaxUserDownloadBandwidthKbps,
		AllowRegistration:            req.AllowRegistration,
	}
	if req.DefaultStorageQuotaMB != nil {
		value := *req.DefaultStorageQuotaMB * 1024 * 1024
		input.DefaultStorageQuotaBytes = &value
	}
	if req.MaxUploadFileMB != nil {
		value := *req.MaxUploadFileMB * 1024 * 1024
		input.MaxUploadFileBytes = &value
	}

	settings, err := h.adminService.UpdateSettings(r.Context(), input)
	if err != nil {
		h.writeAdminError(w, r, err, "update settings")
		return
	}
	userCount, err := h.adminService.CountUsers(r.Context())
	if err != nil {
		response.Error(w, r, http.StatusInternalServerError, "count users failed")
		return
	}

	response.OK(w, r, map[string]any{
		"settings":           buildAdminSettingsData(settings),
		"current_user_count": userCount,
	})
}

func (h *AdminHandler) ListUsers(w http.ResponseWriter, r *http.Request) {
	if h.adminService == nil {
		response.Error(w, r, http.StatusInternalServerError, "admin service is not ready")
		return
	}

	items, err := h.adminService.ListUsers(r.Context())
	if err != nil {
		h.writeAdminError(w, r, err, "list users")
		return
	}

	data := make([]adminUserSummaryData, 0, len(items))
	for _, item := range items {
		data = append(data, buildAdminUserSummaryData(item))
	}

	response.OK(w, r, map[string]any{
		"users": data,
	})
}

func (h *AdminHandler) UpdateUser(w http.ResponseWriter, r *http.Request) {
	if h.adminService == nil {
		response.Error(w, r, http.StatusInternalServerError, "admin service is not ready")
		return
	}

	var req updateAdminUserRequest
	if err := decodeJSONBody(r, &req); err != nil {
		response.Error(w, r, http.StatusBadRequest, "invalid request body")
		return
	}

	input := admin.UpdateUserInput{
		UploadBandwidthKbps:   req.UploadBandwidthKbps,
		DownloadBandwidthKbps: req.DownloadBandwidthKbps,
		IsAdmin:               req.IsAdmin,
	}
	if req.StorageQuotaMB != nil {
		value := *req.StorageQuotaMB * 1024 * 1024
		input.StorageQuotaBytes = &value
	}

	item, err := h.adminService.UpdateUser(r.Context(), r.PathValue("id"), input)
	if err != nil {
		h.writeAdminError(w, r, err, "update user")
		return
	}

	response.OK(w, r, map[string]any{
		"user": buildAdminUserSummaryData(item),
	})
}

func (h *AdminHandler) DeleteUser(w http.ResponseWriter, r *http.Request) {
	if h.adminService == nil {
		response.Error(w, r, http.StatusInternalServerError, "admin service is not ready")
		return
	}

	result, err := h.adminService.DeleteUser(r.Context(), r.PathValue("id"))
	if err != nil {
		h.writeAdminError(w, r, err, "delete user")
		return
	}

	response.OK(w, r, map[string]any{
		"success":         true,
		"deleted_user_id": result.DeletedUserID,
	})
}

func (h *AdminHandler) ListQuotaRequests(w http.ResponseWriter, r *http.Request) {
	if h.adminService == nil {
		response.Error(w, r, http.StatusInternalServerError, "admin service is not ready")
		return
	}
	items, err := h.adminService.ListQuotaRequestsForAdmin(r.Context(), r.URL.Query().Get("status"))
	if err != nil {
		h.writeAdminError(w, r, err, "list quota requests")
		return
	}

	data := make([]quotaRequestData, 0, len(items))
	for _, item := range items {
		data = append(data, buildQuotaRequestData(item))
	}
	response.OK(w, r, map[string]any{
		"requests": data,
		"status":   normalizeRequestStatusLabel(r.URL.Query().Get("status")),
	})
}

func (h *AdminHandler) ApproveQuotaRequest(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}

	var req approveQuotaRequestBody
	if err := decodeJSONBody(r, &req); err != nil {
		response.Error(w, r, http.StatusBadRequest, "invalid request body")
		return
	}

	input := admin.ApproveQuotaRequestInput{
		ApprovedQuotaBytes: nil,
		ReviewNote:         req.ReviewNote,
	}
	if req.ApprovedQuotaMB != nil {
		value := *req.ApprovedQuotaMB * 1024 * 1024
		input.ApprovedQuotaBytes = &value
	}

	item, err := h.adminService.ApproveQuotaRequest(r.Context(), r.PathValue("id"), identity.UserID, input)
	if err != nil {
		h.writeAdminError(w, r, err, "approve quota request")
		return
	}

	response.OK(w, r, map[string]any{
		"request": buildQuotaRequestData(item),
	})
}

func (h *AdminHandler) RejectQuotaRequest(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}

	var req reviewOnlyRequestBody
	if err := decodeJSONBody(r, &req); err != nil {
		response.Error(w, r, http.StatusBadRequest, "invalid request body")
		return
	}

	item, err := h.adminService.RejectQuotaRequest(r.Context(), r.PathValue("id"), identity.UserID, admin.RejectRequestInput{
		ReviewNote: req.ReviewNote,
	})
	if err != nil {
		h.writeAdminError(w, r, err, "reject quota request")
		return
	}

	response.OK(w, r, map[string]any{
		"request": buildQuotaRequestData(item),
	})
}

func (h *AdminHandler) ListBandwidthRequests(w http.ResponseWriter, r *http.Request) {
	if h.adminService == nil {
		response.Error(w, r, http.StatusInternalServerError, "admin service is not ready")
		return
	}
	items, err := h.adminService.ListBandwidthRequestsForAdmin(r.Context(), r.URL.Query().Get("status"))
	if err != nil {
		h.writeAdminError(w, r, err, "list bandwidth requests")
		return
	}

	data := make([]bandwidthRequestData, 0, len(items))
	for _, item := range items {
		data = append(data, buildBandwidthRequestData(item))
	}
	response.OK(w, r, map[string]any{
		"requests": data,
		"status":   normalizeRequestStatusLabel(r.URL.Query().Get("status")),
	})
}

func (h *AdminHandler) ApproveBandwidthRequest(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}

	var req approveBandwidthRequestBody
	if err := decodeJSONBody(r, &req); err != nil {
		response.Error(w, r, http.StatusBadRequest, "invalid request body")
		return
	}

	item, err := h.adminService.ApproveBandwidthRequest(r.Context(), r.PathValue("id"), identity.UserID, admin.ApproveBandwidthRequestInput{
		ApprovedUploadKbps:   req.ApprovedUploadKbps,
		ApprovedDownloadKbps: req.ApprovedDownloadKbps,
		ReviewNote:           req.ReviewNote,
	})
	if err != nil {
		h.writeAdminError(w, r, err, "approve bandwidth request")
		return
	}

	response.OK(w, r, map[string]any{
		"request": buildBandwidthRequestData(item),
	})
}

func (h *AdminHandler) RejectBandwidthRequest(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}

	var req reviewOnlyRequestBody
	if err := decodeJSONBody(r, &req); err != nil {
		response.Error(w, r, http.StatusBadRequest, "invalid request body")
		return
	}

	item, err := h.adminService.RejectBandwidthRequest(r.Context(), r.PathValue("id"), identity.UserID, admin.RejectRequestInput{
		ReviewNote: req.ReviewNote,
	})
	if err != nil {
		h.writeAdminError(w, r, err, "reject bandwidth request")
		return
	}

	response.OK(w, r, map[string]any{
		"request": buildBandwidthRequestData(item),
	})
}

func (h *AdminHandler) ListAdminRequests(w http.ResponseWriter, r *http.Request) {
	if h.adminService == nil {
		response.Error(w, r, http.StatusInternalServerError, "admin service is not ready")
		return
	}
	items, err := h.adminService.ListAdminRequestsForAdmin(r.Context(), r.URL.Query().Get("status"))
	if err != nil {
		h.writeAdminError(w, r, err, "list admin requests")
		return
	}

	data := make([]adminRequestData, 0, len(items))
	for _, item := range items {
		data = append(data, buildAdminRequestData(item))
	}
	response.OK(w, r, map[string]any{
		"requests": data,
		"status":   normalizeRequestStatusLabel(r.URL.Query().Get("status")),
	})
}

func (h *AdminHandler) ApproveAdminRequest(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}

	var req reviewOnlyRequestBody
	if err := decodeJSONBody(r, &req); err != nil {
		response.Error(w, r, http.StatusBadRequest, "invalid request body")
		return
	}

	item, err := h.adminService.ApproveAdminRequest(r.Context(), r.PathValue("id"), identity.UserID, admin.ApproveAdminRequestInput{
		ReviewNote: req.ReviewNote,
	})
	if err != nil {
		h.writeAdminError(w, r, err, "approve admin request")
		return
	}

	response.OK(w, r, map[string]any{
		"request": buildAdminRequestData(item),
	})
}

func (h *AdminHandler) RejectAdminRequest(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}

	var req reviewOnlyRequestBody
	if err := decodeJSONBody(r, &req); err != nil {
		response.Error(w, r, http.StatusBadRequest, "invalid request body")
		return
	}

	item, err := h.adminService.RejectAdminRequest(r.Context(), r.PathValue("id"), identity.UserID, admin.RejectRequestInput{
		ReviewNote: req.ReviewNote,
	})
	if err != nil {
		h.writeAdminError(w, r, err, "reject admin request")
		return
	}

	response.OK(w, r, map[string]any{
		"request": buildAdminRequestData(item),
	})
}

func (h *AdminHandler) writeAdminError(w http.ResponseWriter, r *http.Request, err error, action string) {
	switch {
	case err == nil:
		return
	case errors.Is(err, admin.ErrNotFound):
		response.Error(w, r, http.StatusNotFound, "resource not found")
	case errors.Is(err, admin.ErrInvalidArgument):
		response.Error(w, r, http.StatusBadRequest, "request payload is invalid")
	case errors.Is(err, admin.ErrInvalidState):
		response.Error(w, r, http.StatusConflict, "request is not pending")
	case errors.Is(err, admin.ErrLastAdmin):
		response.Error(w, r, http.StatusConflict, "cannot remove the last admin")
	default:
		response.Error(w, r, http.StatusInternalServerError, action+" failed")
	}
}
