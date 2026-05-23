package handlers

import (
	"errors"
	"net/http"

	"clipbridge/backend/internal/app"
	"clipbridge/backend/internal/auth"
	"clipbridge/backend/internal/httpapi/authcontext"
	"clipbridge/backend/internal/httpapi/response"
)

type DeviceHandler struct {
	authService *auth.Service
}

type updateDeviceRequest struct {
	DeviceID   string `json:"device_id"`
	DeviceName string `json:"device_name"`
}

type deactivateDeviceRequest struct {
	DeviceID string `json:"device_id"`
}

func NewDeviceHandler(application *app.App) *DeviceHandler {
	if application == nil {
		return &DeviceHandler{}
	}
	return &DeviceHandler{
		authService: application.AuthService,
	}
}

func (h *DeviceHandler) List(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}

	devices, err := h.authService.ListDevices(r.Context(), identity.UserID, identity.DeviceID)
	if err != nil {
		response.Error(w, r, http.StatusInternalServerError, "list devices failed")
		return
	}

	items := make([]authDeviceData, 0, len(devices))
	for _, device := range devices {
		items = append(items, buildDeviceData(device))
	}

	response.OK(w, r, map[string]any{
		"devices": items,
	})
}

func (h *DeviceHandler) Update(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}

	var req updateDeviceRequest
	if err := decodeJSONBody(r, &req); err != nil {
		response.Error(w, r, http.StatusBadRequest, "invalid request body")
		return
	}

	device, err := h.authService.UpdateDeviceName(
		r.Context(),
		identity.UserID,
		identity.DeviceID,
		req.DeviceID,
		req.DeviceName,
	)
	if err != nil {
		h.writeDeviceError(w, r, err, "update")
		return
	}

	response.OK(w, r, map[string]any{
		"device": buildDeviceData(device),
	})
}

func (h *DeviceHandler) ForceOffline(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}

	var req deactivateDeviceRequest
	if err := decodeJSONBody(r, &req); err != nil {
		response.Error(w, r, http.StatusBadRequest, "invalid request body")
		return
	}

	device, err := h.authService.ForceDeviceOffline(
		r.Context(),
		identity.UserID,
		identity.DeviceID,
		req.DeviceID,
	)
	if err != nil {
		h.writeDeviceError(w, r, err, "force offline")
		return
	}

	response.OK(w, r, map[string]any{
		"device":                        buildDeviceData(device),
		"current_device_forced_offline": device.ID == identity.DeviceID,
	})
}

func (h *DeviceHandler) writeDeviceError(w http.ResponseWriter, r *http.Request, err error, action string) {
	switch {
	case err == nil:
		return
	case errors.Is(err, auth.ErrNotFound):
		response.Error(w, r, http.StatusNotFound, "device not found")
	case isValidationError(err):
		response.Error(w, r, http.StatusBadRequest, err.Error())
	default:
		response.Error(w, r, http.StatusInternalServerError, action+" device failed")
	}
}

func decodeOptionalJSONBody(r *http.Request, dst any) error {
	if r.Body == nil {
		return nil
	}
	return ignoreEmptyBodyError(decodeJSONBody(r, dst))
}

func ignoreEmptyBodyError(err error) error {
	if err == nil || err == errEmptyJSONBody {
		return nil
	}
	return err
}
