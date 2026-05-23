package handlers

import (
	"errors"
	"net/http"

	"clipbridge/backend/internal/app"
	"clipbridge/backend/internal/auth"
	"clipbridge/backend/internal/httpapi/authcontext"
	"clipbridge/backend/internal/httpapi/response"
)

type AccountHandler struct {
	authService *auth.Service
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
