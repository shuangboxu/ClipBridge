package handlers

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"strings"
	"time"

	"clipbridge/backend/internal/app"
	"clipbridge/backend/internal/auth"
	"clipbridge/backend/internal/clipboard"
	"clipbridge/backend/internal/httpapi/response"
	"clipbridge/backend/internal/realtime"
	"github.com/gorilla/websocket"
)

const wsHeartbeatInterval = 20 * time.Second

type WSHandler struct {
	authService      *auth.Service
	clipboardService *clipboard.Service
	hub              *realtime.Hub
	upgrader         websocket.Upgrader
}

type wsInboundMessage struct {
	Type string `json:"type"`
	Seq  int64  `json:"seq"`
}

func NewWSHandler(application *app.App) *WSHandler {
	handler := &WSHandler{}
	if application == nil {
		return handler
	}

	handler.authService = application.AuthService
	handler.clipboardService = application.ClipboardService
	handler.hub = application.RealtimeHub
	handler.upgrader = websocket.Upgrader{
		ReadBufferSize:  1024,
		WriteBufferSize: 1024,
		CheckOrigin:     buildWSOriginChecker(application.Config.CORS.AllowOrigins),
	}
	return handler
}

func (h *WSHandler) Connect(w http.ResponseWriter, r *http.Request) {
	if h.authService == nil || h.clipboardService == nil || h.hub == nil {
		response.Error(w, r, http.StatusInternalServerError, "ws service is not ready")
		return
	}

	accessToken, ok := resolveWSAccessToken(r)
	if !ok {
		response.Error(w, r, http.StatusUnauthorized, "missing access token")
		return
	}

	user, device, err := h.authService.AuthenticateAccessToken(r.Context(), accessToken)
	if err != nil {
		if errors.Is(err, auth.ErrUnauthorized) {
			response.Error(w, r, http.StatusUnauthorized, "invalid access token")
			return
		}
		response.Error(w, r, http.StatusInternalServerError, "auth validation failed")
		return
	}

	snapshot, err := h.clipboardService.GetSyncSnapshot(r.Context(), user.ID, device.ID)
	if err != nil {
		if errors.Is(err, clipboard.ErrNotFound) {
			response.Error(w, r, http.StatusNotFound, "device or user not found")
			return
		}
		response.Error(w, r, http.StatusInternalServerError, "load sync snapshot failed")
		return
	}

	conn, err := h.upgrader.Upgrade(w, r, nil)
	if err != nil {
		return
	}
	defer conn.Close()

	client := realtime.NewClient(user.ID, device.ID)
	h.hub.Register(client)
	defer h.hub.Unregister(client)

	ctx, cancel := context.WithCancel(r.Context())
	defer cancel()

	go func() {
		h.writeLoop(ctx, conn, client, snapshot)
		cancel()
	}()

	h.readLoop(ctx, conn, client)
}

func (h *WSHandler) readLoop(ctx context.Context, conn *websocket.Conn, client *realtime.Client) {
	conn.SetReadLimit(64 * 1024)
	_ = conn.SetReadDeadline(time.Now().Add(wsHeartbeatInterval * 3))
	conn.SetPongHandler(func(string) error {
		return conn.SetReadDeadline(time.Now().Add(wsHeartbeatInterval * 3))
	})

	for {
		select {
		case <-ctx.Done():
			return
		default:
		}

		_, data, err := conn.ReadMessage()
		if err != nil {
			return
		}
		_ = conn.SetReadDeadline(time.Now().Add(wsHeartbeatInterval * 3))

		var msg wsInboundMessage
		if err := json.Unmarshal(data, &msg); err != nil {
			continue
		}

		switch strings.TrimSpace(strings.ToLower(msg.Type)) {
		case "sync.ping":
			h.hub.SendToDevice(client.UserID, client.DeviceID, map[string]any{
				"type": "sync.pong",
				"ts":   time.Now().Unix(),
			})
		case "sync.ack":
			result, err := h.clipboardService.Ack(ctx, client.UserID, client.DeviceID, msg.Seq)
			if err != nil {
				continue
			}
			h.hub.SendToDevice(client.UserID, client.DeviceID, map[string]any{
				"type":                   "sync.acknowledged",
				"seq":                    result.Seq,
				"latest_seq":             result.LatestSeq,
				"current_device_ack_seq": result.CurrentDeviceAckSeq,
			})
		}
	}
}

func (h *WSHandler) writeLoop(ctx context.Context, conn *websocket.Conn, client *realtime.Client, snapshot clipboard.SyncSnapshot) {
	heartbeatTicker := time.NewTicker(wsHeartbeatInterval)
	defer heartbeatTicker.Stop()

	if !writeWSJSON(conn, map[string]any{
		"type":                       "sync.hello",
		"device_id":                  client.DeviceID,
		"latest_seq":                 snapshot.LatestSeq,
		"current_device_ack_seq":     snapshot.CurrentDeviceAckSeq,
		"heartbeat_interval_seconds": int(wsHeartbeatInterval / time.Second),
	}) {
		return
	}

	for {
		select {
		case <-ctx.Done():
			_ = conn.WriteControl(
				websocket.CloseMessage,
				websocket.FormatCloseMessage(websocket.CloseNormalClosure, "bye"),
				time.Now().Add(2*time.Second),
			)
			return
		case payload, ok := <-client.Send:
			if !ok {
				return
			}
			_ = conn.SetWriteDeadline(time.Now().Add(10 * time.Second))
			if err := conn.WriteMessage(websocket.TextMessage, payload); err != nil {
				return
			}
		case <-heartbeatTicker.C:
			if !writeWSJSON(conn, map[string]any{
				"type": "sync.heartbeat",
				"ts":   time.Now().Unix(),
			}) {
				return
			}
		}
	}
}

func resolveWSAccessToken(r *http.Request) (string, bool) {
	// 浏览器原生 WebSocket 不能像 fetch 那样自定义 Authorization Header，
	// 所以这里兼容 query 参数，方便 Web 端直接接入实时链路。
	if token, ok := parseBearerToken(strings.TrimSpace(r.Header.Get("Authorization"))); ok {
		return token, true
	}

	token := strings.TrimSpace(r.URL.Query().Get("access_token"))
	if token == "" {
		return "", false
	}
	return token, true
}

func buildWSOriginChecker(allowOrigins []string) func(r *http.Request) bool {
	allowAll := len(allowOrigins) == 1 && strings.TrimSpace(allowOrigins[0]) == "*"
	originSet := make(map[string]struct{}, len(allowOrigins))
	for _, origin := range allowOrigins {
		origin = strings.TrimSpace(origin)
		if origin != "" {
			originSet[origin] = struct{}{}
		}
	}

	return func(r *http.Request) bool {
		if allowAll {
			return true
		}

		origin := strings.TrimSpace(r.Header.Get("Origin"))
		if origin == "" {
			return true
		}

		_, ok := originSet[origin]
		return ok
	}
}

func writeWSJSON(conn *websocket.Conn, payload any) bool {
	_ = conn.SetWriteDeadline(time.Now().Add(10 * time.Second))
	return conn.WriteJSON(payload) == nil
}

func parseBearerToken(authHeader string) (string, bool) {
	parts := strings.SplitN(authHeader, " ", 2)
	if len(parts) != 2 {
		return "", false
	}
	if !strings.EqualFold(parts[0], "Bearer") {
		return "", false
	}

	token := strings.TrimSpace(parts[1])
	if token == "" {
		return "", false
	}
	return token, true
}
