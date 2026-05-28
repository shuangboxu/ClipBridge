package handlers

import (
	"errors"
	"net/http"
	"strings"

	"clipbridge/backend/internal/app"
	"clipbridge/backend/internal/auth"
	"clipbridge/backend/internal/httpapi/authcontext"
	"clipbridge/backend/internal/httpapi/response"
)

type AccountHandler struct {
	authService *auth.Service
}

type changePasswordRequest struct {
	CurrentPassword string `json:"current_password"`
	NewPassword     string `json:"new_password"`
}

func NewAccountHandler(application *app.App) *AccountHandler {
	if application == nil {
		return &AccountHandler{}
	}
	return &AccountHandler{
		authService: application.AuthService,
	}
}

func (h *AccountHandler) GetMe(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}

	profile, err := h.authService.GetCurrentAccount(r.Context(), identity.UserID, identity.DeviceID)
	if err != nil {
		if errors.Is(err, auth.ErrNotFound) {
			response.Error(w, r, http.StatusNotFound, "user not found")
			return
		}
		response.Error(w, r, http.StatusInternalServerError, "load current account failed")
		return
	}

	response.OK(w, r, map[string]any{
		"user":              buildUserData(profile.User),
		"current_device_id": profile.CurrentDeviceID,
	})
}

func (h *AccountHandler) ChangePassword(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}

	var req changePasswordRequest
	if err := decodeJSONBody(r, &req); err != nil {
		response.Error(w, r, http.StatusBadRequest, "invalid request body")
		return
	}

	user, err := h.authService.ChangePassword(
		r.Context(),
		identity.UserID,
		identity.DeviceID,
		req.CurrentPassword,
		req.NewPassword,
	)
	if err != nil {
		switch {
		case errors.Is(err, auth.ErrInvalidCredentials):
			response.Error(w, r, http.StatusUnauthorized, "current password is incorrect")
		case errors.Is(err, auth.ErrNotFound):
			response.Error(w, r, http.StatusNotFound, "user not found")
		case isAccountValidationError(err):
			response.Error(w, r, http.StatusBadRequest, err.Error())
		default:
			response.Error(w, r, http.StatusInternalServerError, "change password failed")
		}
		return
	}

	response.OK(w, r, map[string]any{
		"success": true,
		"user":    buildUserData(user),
	})
}

func isAccountValidationError(err error) bool {
	if err == nil {
		return false
	}

	message := err.Error()
	return strings.Contains(message, "is required") ||
		strings.Contains(message, "at least") ||
		strings.Contains(message, "at most") ||
		strings.Contains(message, "different from")
}
