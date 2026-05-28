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
	"clipbridge/backend/internal/realtime"
)

type ClipboardHandler struct {
	clipboardService *clipboard.Service
	realtimeHub      *realtime.Hub
}

type createClipboardItemRequest struct {
	ContentType string `json:"content_type"`
	TextContent string `json:"text_content"`
}

type clipboardItemData struct {
	ID                    string `json:"id"`
	Seq                   int64  `json:"seq"`
	ContentType           string `json:"content_type"`
	TextContent           string `json:"text_content"`
	ContentHash           string `json:"content_hash"`
	OriginDeviceID        string `json:"origin_device_id"`
	IsCurrentDeviceOrigin bool   `json:"is_current_device_origin"`
	CreatedAt             string `json:"created_at"`
}

func NewClipboardHandler(application *app.App) *ClipboardHandler {
	if application == nil {
		return &ClipboardHandler{}
	}
	return &ClipboardHandler{
		clipboardService: application.ClipboardService,
		realtimeHub:      application.RealtimeHub,
	}
}

func (h *ClipboardHandler) CreateItem(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}

	var req createClipboardItemRequest
	if err := decodeJSONBody(r, &req); err != nil {
		response.Error(w, r, http.StatusBadRequest, "invalid request body")
		return
	}

	contentType := strings.TrimSpace(strings.ToLower(req.ContentType))
	if contentType == "" {
		contentType = clipboard.ContentTypeText
	}
	if contentType != clipboard.ContentTypeText {
		response.Error(w, r, http.StatusBadRequest, "only text clipboard items are supported")
		return
	}

	result, err := h.clipboardService.UploadText(r.Context(), identity.UserID, identity.DeviceID, req.TextContent)
	if err != nil {
		h.writeClipboardError(w, r, err, "create")
		return
	}

	data := map[string]any{
		"item":         buildClipboardItemData(result.Item, identity.DeviceID),
		"deduplicated": result.Deduplicated,
	}
	if result.Deduplicated {
		response.OK(w, r, data)
		return
	}

	if h.realtimeHub != nil {
		h.realtimeHub.BroadcastToUserExcept(identity.UserID, identity.DeviceID, map[string]any{
			"type": "clipboard.new",
			"item": buildClipboardItemData(result.Item, identity.DeviceID),
		})
	}
	response.Created(w, r, data)
}

func (h *ClipboardHandler) ListItems(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}

	limit, err := parseOptionalPositiveInt(r.URL.Query().Get("limit"))
	if err != nil {
		response.Error(w, r, http.StatusBadRequest, "limit must be a positive integer")
		return
	}

	beforeSeq, err := parseOptionalPositiveInt64(r.URL.Query().Get("before_seq"))
	if err != nil {
		response.Error(w, r, http.StatusBadRequest, "before_seq must be a positive integer")
		return
	}

	result, err := h.clipboardService.ListHistory(r.Context(), identity.UserID, identity.DeviceID, beforeSeq, limit)
	if err != nil {
		h.writeClipboardError(w, r, err, "list")
		return
	}

	items := make([]clipboardItemData, 0, len(result.Items))
	for _, item := range result.Items {
		items = append(items, buildClipboardItemData(item, identity.DeviceID))
	}

	response.OK(w, r, map[string]any{
		"items":                  items,
		"has_more":               result.HasMore,
		"next_before_seq":        result.NextBeforeSeq,
		"latest_seq":             result.LatestSeq,
		"current_device_ack_seq": result.CurrentDeviceAckSeq,
	})
}

func (h *ClipboardHandler) writeClipboardError(w http.ResponseWriter, r *http.Request, err error, action string) {
	switch {
	case err == nil:
		return
	case errors.Is(err, clipboard.ErrNotFound):
		response.Error(w, r, http.StatusNotFound, "device or user not found")
	case isClipboardValidationError(err):
		response.Error(w, r, http.StatusBadRequest, err.Error())
	default:
		response.Error(w, r, http.StatusInternalServerError, action+" clipboard item failed")
	}
}

func buildClipboardItemData(item clipboard.Item, currentDeviceID string) clipboardItemData {
	return clipboardItemData{
		ID:                    item.ID,
		Seq:                   item.Seq,
		ContentType:           item.ContentType,
		TextContent:           item.TextContent,
		ContentHash:           item.ContentHash,
		OriginDeviceID:        item.OriginDeviceID,
		IsCurrentDeviceOrigin: item.OriginDeviceID == currentDeviceID,
		CreatedAt:             formatTime(item.CreatedAt),
	}
}

func parseOptionalPositiveInt(value string) (int, error) {
	value = strings.TrimSpace(value)
	if value == "" {
		return 0, nil
	}

	number, err := strconv.Atoi(value)
	if err != nil || number <= 0 {
		return 0, errors.New("invalid positive integer")
	}
	return number, nil
}

func parseOptionalPositiveInt64(value string) (*int64, error) {
	value = strings.TrimSpace(value)
	if value == "" {
		return nil, nil
	}

	number, err := strconv.ParseInt(value, 10, 64)
	if err != nil || number <= 0 {
		return nil, errors.New("invalid positive integer")
	}
	return &number, nil
}

func isClipboardValidationError(err error) bool {
	if err == nil {
		return false
	}

	message := err.Error()
	return strings.Contains(message, "is required") ||
		strings.Contains(message, "must be at most") ||
		strings.Contains(message, "must be greater than") ||
		strings.Contains(message, "only text clipboard items are supported")
}
