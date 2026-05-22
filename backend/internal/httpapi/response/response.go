package response

import (
	"encoding/json"
	"net/http"

	"clipbridge/backend/internal/httpapi/requestid"
)

// Envelope 是整个后端统一的 HTTP 响应外层结构。
// 这样前端或移动端拿到任何接口响应时，都能先按同一套字段解析：
// 1. code 看是否成功；
// 2. message 看提示信息；
// 3. data 读业务数据；
// 4. request_id 用来排查日志。
type Envelope struct {
	Code      int    `json:"code"`
	Message   string `json:"message"`
	Data      any    `json:"data,omitempty"`
	RequestID string `json:"request_id"`
}

func OK(w http.ResponseWriter, r *http.Request, data any) {
	write(w, r, http.StatusOK, 0, "ok", data)
}

func Created(w http.ResponseWriter, r *http.Request, data any) {
	write(w, r, http.StatusCreated, 0, "ok", data)
}

func Error(w http.ResponseWriter, r *http.Request, status int, message string) {
	write(w, r, status, status, message, nil)
}

func write(w http.ResponseWriter, r *http.Request, status int, code int, message string, data any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)

	_ = json.NewEncoder(w).Encode(Envelope{
		Code:      code,
		Message:   message,
		Data:      data,
		RequestID: requestid.Get(r.Context()),
	})
}
