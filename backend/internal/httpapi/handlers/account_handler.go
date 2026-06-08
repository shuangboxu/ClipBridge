package handlers

import (
	"context"
	"errors"
	"net/http"
	"strings"

	"clipbridge/backend/internal/admin"
	"clipbridge/backend/internal/app"
	"clipbridge/backend/internal/auth"
	"clipbridge/backend/internal/httpapi/authcontext"
	"clipbridge/backend/internal/httpapi/response"
)

type AccountHandler struct {
	authService  *auth.Service
	adminService accountAdminService
}

type accountAdminService interface {
	GetAccountOverview(ctx context.Context, userID string) (admin.AccountOverview, error)
}

type changePasswordRequest struct {
	CurrentPassword string `json:"current_password"`
	NewPassword     string `json:"new_password"`
}

func NewAccountHandler(application *app.App) *AccountHandler {
	if application == nil {
		return &AccountHandler{}
	}
	handler := &AccountHandler{
		authService: application.AuthService,
	}
	if application.AdminService != nil {
		handler.adminService = application.AdminService
	}
	return handler
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

	if h.adminService == nil {
		response.Error(w, r, http.StatusInternalServerError, "admin service is not ready")
		return
	}

	overview, err := h.adminService.GetAccountOverview(r.Context(), identity.UserID)
	if err != nil {
		if errors.Is(err, admin.ErrNotFound) {
			response.Error(w, r, http.StatusNotFound, "user not found")
			return
		}
		response.Error(w, r, http.StatusInternalServerError, "load current account failed")
		return
	}

	response.OK(w, r, map[string]any{
		"user":               buildUserData(profile.User),
		"current_device_id":  profile.CurrentDeviceID,
		"storage_used_bytes": overview.StorageUsedBytes,
		"storage_free_bytes": overview.StorageFreeBytes,
		"limits":             overview.Limits,
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
