package handlers

import (
	"net/http"

	"clipbridge/backend/internal/httpapi/authcontext"
	"clipbridge/backend/internal/httpapi/response"
)

type SystemHandler struct{}

type currentProfileData struct {
	UserID   string `json:"user_id"`
	DeviceID string `json:"device_id"`
	Note     string `json:"note"`
}

func NewSystemHandler() *SystemHandler {
	return &SystemHandler{}
}

func (h *SystemHandler) GetProfile(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}

	response.OK(w, r, currentProfileData{
		UserID:   identity.UserID,
		DeviceID: identity.DeviceID,
		Note:     "当前接口仅用于验证第一阶段的鉴权中间件骨架，后续会替换为真实账号接口",
	})
}
