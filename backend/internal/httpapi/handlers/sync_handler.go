package handlers

import (
	"errors"
	"net/http"
	"strconv"
	"strings"

	"clipbridge/backend/internal/app"
	"clipbridge/backend/internal/clipboard"
	"clipbridge/backend/internal/httpapi/authcontext"
	"clipbridge/backend/internal/httpapi/response"
)

type SyncHandler struct {
	clipboardService *clipboard.Service
}

type ackRequest struct {
	Seq int64 `json:"seq"`
}

func NewSyncHandler(application *app.App) *SyncHandler {
	if application == nil {
		return &SyncHandler{}
	}
	return &SyncHandler{
		clipboardService: application.ClipboardService,
	}
}

func (h *SyncHandler) Pull(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}

	sinceSeq, err := parseOptionalNonNegativeInt64(r.URL.Query().Get("since_seq"))
	if err != nil {
		response.Error(w, r, http.StatusBadRequest, "since_seq must be a non-negative integer")
		return
	}

	limit, err := parseOptionalPositiveInt(r.URL.Query().Get("limit"))
	if err != nil {
		response.Error(w, r, http.StatusBadRequest, "limit must be a positive integer")
		return
	}

	result, err := h.clipboardService.Pull(r.Context(), identity.UserID, identity.DeviceID, sinceSeq, limit)
	if err != nil {
		h.writeSyncError(w, r, err, "pull")
		return
	}

	items := make([]clipboardItemData, 0, len(result.Items))
	for _, item := range result.Items {
		items = append(items, buildClipboardItemData(item, identity.DeviceID))
	}

	response.OK(w, r, map[string]any{
		"items":                  items,
		"since_seq":              result.SinceSeq,
		"next_since_seq":         result.NextSinceSeq,
		"has_more":               result.HasMore,
		"latest_seq":             result.LatestSeq,
		"current_device_ack_seq": result.CurrentDeviceAckSeq,
	})
}

func (h *SyncHandler) Ack(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}

	var req ackRequest
	if err := decodeJSONBody(r, &req); err != nil {
		response.Error(w, r, http.StatusBadRequest, "invalid request body")
		return
	}

	result, err := h.clipboardService.Ack(r.Context(), identity.UserID, identity.DeviceID, req.Seq)
	if err != nil {
		h.writeSyncError(w, r, err, "ack")
		return
	}

	response.OK(w, r, map[string]any{
		"seq":                    result.Seq,
		"latest_seq":             result.LatestSeq,
		"current_device_ack_seq": result.CurrentDeviceAckSeq,
	})
}

func (h *SyncHandler) writeSyncError(w http.ResponseWriter, r *http.Request, err error, action string) {
	switch {
	case err == nil:
		return
	case errors.Is(err, clipboard.ErrNotFound):
		response.Error(w, r, http.StatusNotFound, "device or user not found")
	case isClipboardValidationError(err):
		response.Error(w, r, http.StatusBadRequest, err.Error())
	default:
		response.Error(w, r, http.StatusInternalServerError, action+" sync failed")
	}
}

func parseOptionalNonNegativeInt64(value string) (int64, error) {
	value = strings.TrimSpace(value)
	if value == "" {
		return 0, nil
	}

	number, err := strconv.ParseInt(value, 10, 64)
	if err != nil || number < 0 {
		return 0, errors.New("invalid non-negative integer")
	}
	return number, nil
}
