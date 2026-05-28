package handlers

import (
	"errors"
	"net/http"
	"strings"
	"time"

	"clipbridge/backend/internal/app"
	"clipbridge/backend/internal/auth"
	"clipbridge/backend/internal/httpapi/authcontext"
	"clipbridge/backend/internal/httpapi/response"
)

type AuthHandler struct {
	authService       *auth.Service
	allowRegistration bool
}

type registerRequest struct {
	Username   string `json:"username"`
	Password   string `json:"password"`
	Platform   string `json:"platform"`
	DeviceName string `json:"device_name"`
}

type loginRequest struct {
	Username   string `json:"username"`
	Password   string `json:"password"`
	Platform   string `json:"platform"`
	DeviceName string `json:"device_name"`
}

type refreshRequest struct {
	RefreshToken string `json:"refresh_token"`
}

type logoutRequest struct {
	RefreshToken string `json:"refresh_token"`
}

type authUserData struct {
	ID        string `json:"id"`
	Username  string `json:"username"`
	CreatedAt string `json:"created_at"`
	UpdatedAt string `json:"updated_at"`
}

type authDeviceData struct {
	ID         string `json:"id"`
	Platform   string `json:"platform"`
	DeviceName string `json:"device_name"`
	LastSeenAt string `json:"last_seen_at"`
	IsActive   bool   `json:"is_active"`
	CreatedAt  string `json:"created_at"`
}

type tokenData struct {
	AccessToken           string `json:"access_token"`
	AccessTokenExpiresAt  string `json:"access_token_expires_at"`
	RefreshToken          string `json:"refresh_token"`
	RefreshTokenExpiresAt string `json:"refresh_token_expires_at"`
}

func NewAuthHandler(application *app.App) *AuthHandler {
	if application == nil {
		return &AuthHandler{}
	}
	return &AuthHandler{
		authService:       application.AuthService,
		allowRegistration: application.Config.Auth.AllowRegistration,
	}
}

func (h *AuthHandler) Register(w http.ResponseWriter, r *http.Request) {
	// 当前项目默认不开放公开注册。
	// 只有显式打开配置后，才允许外部调用注册接口。
	if !h.allowRegistration {
		response.Error(w, r, http.StatusForbidden, "registration is disabled")
		return
	}

	var req registerRequest
	if err := decodeJSONBody(r, &req); err != nil {
		response.Error(w, r, http.StatusBadRequest, "invalid request body")
		return
	}

	session, err := h.authService.Register(
		r.Context(),
		req.Username,
		req.Password,
		req.Platform,
		req.DeviceName,
	)
	if err != nil {
		h.writeAuthError(w, r, err, true)
		return
	}

	response.Created(w, r, buildSessionResponse(session))
}

func (h *AuthHandler) Login(w http.ResponseWriter, r *http.Request) {
	var req loginRequest
	if err := decodeJSONBody(r, &req); err != nil {
		response.Error(w, r, http.StatusBadRequest, "invalid request body")
		return
	}

	session, err := h.authService.Login(
		r.Context(),
		req.Username,
		req.Password,
		req.Platform,
		req.DeviceName,
	)
	if err != nil {
		h.writeAuthError(w, r, err, false)
		return
	}

	response.OK(w, r, buildSessionResponse(session))
}

func (h *AuthHandler) Refresh(w http.ResponseWriter, r *http.Request) {
	var req refreshRequest
	if err := decodeJSONBody(r, &req); err != nil {
		response.Error(w, r, http.StatusBadRequest, "invalid request body")
		return
	}

	tokens, err := h.authService.Refresh(r.Context(), req.RefreshToken)
	if err != nil {
		switch {
		case errors.Is(err, auth.ErrInvalidRefreshToken):
			response.Error(w, r, http.StatusUnauthorized, "invalid refresh token")
		case strings.HasPrefix(err.Error(), "refresh_token is required"):
			response.Error(w, r, http.StatusBadRequest, err.Error())
		default:
			response.Error(w, r, http.StatusInternalServerError, "refresh token failed")
		}
		return
	}

	response.OK(w, r, map[string]any{
		"tokens": buildTokenData(tokens),
	})
}

func (h *AuthHandler) Logout(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}

	var req logoutRequest
	// 退出登录允许空 body。
	if err := decodeOptionalJSONBody(r, &req); err != nil {
		response.Error(w, r, http.StatusBadRequest, "invalid request body")
		return
	}

	if err := h.authService.Logout(r.Context(), identity.UserID, identity.DeviceID, req.RefreshToken); err != nil {
		response.Error(w, r, http.StatusInternalServerError, "logout failed")
		return
	}

	response.OK(w, r, map[string]any{
		"success": true,
	})
}

func (h *AuthHandler) writeAuthError(w http.ResponseWriter, r *http.Request, err error, isRegister bool) {
	switch {
	case err == nil:
		return
	case errors.Is(err, auth.ErrConflict):
		response.Error(w, r, http.StatusConflict, "username already exists")
	case errors.Is(err, auth.ErrInvalidCredentials):
		response.Error(w, r, http.StatusUnauthorized, "invalid username or password")
	case isValidationError(err):
		response.Error(w, r, http.StatusBadRequest, err.Error())
	default:
		action := "login"
		if isRegister {
			action = "register"
		}
		response.Error(w, r, http.StatusInternalServerError, action+" failed")
	}
}

func buildSessionResponse(session auth.Session) map[string]any {
	return map[string]any{
		"user":   buildUserData(session.User),
		"device": buildDeviceData(session.Device),
		"tokens": buildTokenData(session.Tokens),
	}
}

func buildUserData(user auth.User) authUserData {
	return authUserData{
		ID:        user.ID,
		Username:  user.Username,
		CreatedAt: formatTime(user.CreatedAt),
		UpdatedAt: formatTime(user.UpdatedAt),
	}
}

func buildDeviceData(device auth.Device) authDeviceData {
	return authDeviceData{
		ID:         device.ID,
		Platform:   device.Platform,
		DeviceName: device.DeviceName,
		LastSeenAt: formatTime(device.LastSeenAt),
		IsActive:   device.IsActive,
		CreatedAt:  formatTime(device.CreatedAt),
	}
}

func buildTokenData(tokens auth.TokenBundle) tokenData {
	return tokenData{
		AccessToken:           tokens.AccessToken,
		AccessTokenExpiresAt:  formatTime(tokens.AccessTokenExpiresAt),
		RefreshToken:          tokens.RefreshToken,
		RefreshTokenExpiresAt: formatTime(tokens.RefreshTokenExpiresAt),
	}
}

func formatTime(value time.Time) string {
	return value.UTC().Format(time.RFC3339)
}

func isValidationError(err error) bool {
	if err == nil {
		return false
	}

	message := err.Error()
	return strings.Contains(message, "is required") ||
		strings.Contains(message, "at least") ||
		strings.Contains(message, "at most")
}
