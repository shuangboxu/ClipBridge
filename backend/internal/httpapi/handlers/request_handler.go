package handlers

import (
	"context"
	"errors"
	"net/http"
	"strings"

	"clipbridge/backend/internal/admin"
	"clipbridge/backend/internal/app"
	"clipbridge/backend/internal/httpapi/authcontext"
	"clipbridge/backend/internal/httpapi/response"
)

type RequestHandler struct {
	adminService requestAdminService
}

type requestAdminService interface {
	CreateQuotaRequest(ctx context.Context, userID string, requestedQuotaBytes int64, reason string) (admin.QuotaRequest, error)
	ListMyQuotaRequests(ctx context.Context, userID, status string) ([]admin.QuotaRequest, error)
	CreateBandwidthRequest(ctx context.Context, userID string, requestedUploadKbps, requestedDownloadKbps int, reason string) (admin.BandwidthRequest, error)
	ListMyBandwidthRequests(ctx context.Context, userID, status string) ([]admin.BandwidthRequest, error)
	CreateAdminRequest(ctx context.Context, userID, reason string) (admin.AdminRequest, error)
	ListMyAdminRequests(ctx context.Context, userID, status string) ([]admin.AdminRequest, error)
}

type createQuotaRequestBody struct {
	RequestedQuotaMB int64  `json:"requested_quota_mb"`
	Reason           string `json:"reason"`
}

type createBandwidthRequestBody struct {
	RequestedUploadKbps   int    `json:"requested_upload_kbps"`
	RequestedDownloadKbps int    `json:"requested_download_kbps"`
	Reason                string `json:"reason"`
}

type createAdminRequestBody struct {
	Reason string `json:"reason"`
}

func NewRequestHandler(application *app.App) *RequestHandler {
	if application == nil {
		return &RequestHandler{}
	}
	handler := &RequestHandler{}
	if application.AdminService != nil {
		handler.adminService = application.AdminService
	}
	return handler
}

func (h *RequestHandler) CreateQuotaRequest(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}
	if h.adminService == nil {
		response.Error(w, r, http.StatusInternalServerError, "admin service is not ready")
		return
	}

	var req createQuotaRequestBody
	if err := decodeJSONBody(r, &req); err != nil {
		response.Error(w, r, http.StatusBadRequest, "invalid request body")
		return
	}

	item, err := h.adminService.CreateQuotaRequest(r.Context(), identity.UserID, req.RequestedQuotaMB*1024*1024, req.Reason)
	if err != nil {
		h.writeRequestError(w, r, err, "create quota request")
		return
	}

	response.Created(w, r, map[string]any{
		"request": buildQuotaRequestData(item),
	})
}

func (h *RequestHandler) ListQuotaRequests(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}
	if h.adminService == nil {
		response.Error(w, r, http.StatusInternalServerError, "admin service is not ready")
		return
	}

	items, err := h.adminService.ListMyQuotaRequests(r.Context(), identity.UserID, r.URL.Query().Get("status"))
	if err != nil {
		h.writeRequestError(w, r, err, "list quota requests")
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

func (h *RequestHandler) CreateBandwidthRequest(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}
	if h.adminService == nil {
		response.Error(w, r, http.StatusInternalServerError, "admin service is not ready")
		return
	}

	var req createBandwidthRequestBody
	if err := decodeJSONBody(r, &req); err != nil {
		response.Error(w, r, http.StatusBadRequest, "invalid request body")
		return
	}

	item, err := h.adminService.CreateBandwidthRequest(r.Context(), identity.UserID, req.RequestedUploadKbps, req.RequestedDownloadKbps, req.Reason)
	if err != nil {
		h.writeRequestError(w, r, err, "create bandwidth request")
		return
	}

	response.Created(w, r, map[string]any{
		"request": buildBandwidthRequestData(item),
	})
}

func (h *RequestHandler) ListBandwidthRequests(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}
	if h.adminService == nil {
		response.Error(w, r, http.StatusInternalServerError, "admin service is not ready")
		return
	}

	items, err := h.adminService.ListMyBandwidthRequests(r.Context(), identity.UserID, r.URL.Query().Get("status"))
	if err != nil {
		h.writeRequestError(w, r, err, "list bandwidth requests")
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

func (h *RequestHandler) CreateAdminRequest(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}
	if h.adminService == nil {
		response.Error(w, r, http.StatusInternalServerError, "admin service is not ready")
		return
	}

	var req createAdminRequestBody
	if err := decodeJSONBody(r, &req); err != nil {
		response.Error(w, r, http.StatusBadRequest, "invalid request body")
		return
	}

	item, err := h.adminService.CreateAdminRequest(r.Context(), identity.UserID, req.Reason)
	if err != nil {
		h.writeRequestError(w, r, err, "create admin request")
		return
	}

	response.Created(w, r, map[string]any{
		"request": buildAdminRequestData(item),
	})
}

func (h *RequestHandler) ListAdminRequests(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}
	if h.adminService == nil {
		response.Error(w, r, http.StatusInternalServerError, "admin service is not ready")
		return
	}

	items, err := h.adminService.ListMyAdminRequests(r.Context(), identity.UserID, r.URL.Query().Get("status"))
	if err != nil {
		h.writeRequestError(w, r, err, "list admin requests")
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

func (h *RequestHandler) writeRequestError(w http.ResponseWriter, r *http.Request, err error, action string) {
	switch {
	case err == nil:
		return
	case errors.Is(err, admin.ErrNotFound):
		response.Error(w, r, http.StatusNotFound, "user not found")
	case errors.Is(err, admin.ErrInvalidArgument):
		response.Error(w, r, http.StatusBadRequest, "request payload is invalid")
	case errors.Is(err, admin.ErrPendingQuotaRequestExists):
		response.Error(w, r, http.StatusConflict, "you already have a pending quota request")
	case errors.Is(err, admin.ErrPendingBandwidthRequestExists):
		response.Error(w, r, http.StatusConflict, "you already have a pending bandwidth request")
	case errors.Is(err, admin.ErrPendingAdminRequestExists):
		response.Error(w, r, http.StatusConflict, "you already have a pending admin request")
	case errors.Is(err, admin.ErrAlreadyAdmin):
		response.Error(w, r, http.StatusConflict, "you are already an admin")
	default:
		response.Error(w, r, http.StatusInternalServerError, action+" failed")
	}
}

func normalizeRequestStatusLabel(value string) string {
	status := strings.ToLower(strings.TrimSpace(value))
	switch status {
	case admin.StatusPending:
		return admin.StatusPending
	case admin.StatusApproved:
		return admin.StatusApproved
	case admin.StatusRejected:
		return admin.StatusRejected
	default:
		return admin.StatusAll
	}
}
