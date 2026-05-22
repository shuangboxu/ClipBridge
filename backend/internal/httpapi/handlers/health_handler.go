package handlers

import (
	"context"
	"net/http"
	"time"

	"clipbridge/backend/internal/app"
	"clipbridge/backend/internal/httpapi/response"
)

type HealthHandler struct {
	app *app.App
}

type healthData struct {
	Service  string `json:"service"`
	Status   string `json:"status"`
	Database string `json:"database"`
	Time     string `json:"time"`
}

func NewHealthHandler(application *app.App) *HealthHandler {
	return &HealthHandler{app: application}
}

func (h *HealthHandler) Get(w http.ResponseWriter, r *http.Request) {
	// 健康检查不只是“进程还活着”。
	// 这里顺手检查数据库是否能 ping 通，能更早发现数据库断连、凭据错误、端口不通等问题。
	pingCtx, cancel := context.WithTimeout(r.Context(), h.app.Config.Database.PingTimeout)
	defer cancel()

	if err := h.app.PingDatabase(pingCtx); err != nil {
		response.Error(w, r, http.StatusServiceUnavailable, "database is unavailable")
		return
	}

	response.OK(w, r, healthData{
		Service:  h.app.Config.Server.Name,
		Status:   "ok",
		Database: "up",
		Time:     time.Now().Format(time.RFC3339),
	})
}
