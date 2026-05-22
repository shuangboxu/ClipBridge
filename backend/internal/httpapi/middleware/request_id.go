package middleware

import (
	"crypto/rand"
	"encoding/hex"
	"net/http"
	"time"

	"clipbridge/backend/internal/httpapi/requestid"
)

func RequestID() Middleware {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			id := r.Header.Get(requestid.HeaderName)
			if id == "" {
				id = newRequestID()
			}

			w.Header().Set(requestid.HeaderName, id)
			r = r.WithContext(requestid.WithValue(r.Context(), id))
			next.ServeHTTP(w, r)
		})
	}
}

func newRequestID() string {
	buffer := make([]byte, 8)
	if _, err := rand.Read(buffer); err != nil {
		// 极少数情况下如果随机数读取失败，就退化成时间戳字符串。
		// 虽然不如随机 ID 理想，但总比没有 request_id 更利于排查问题。
		return time.Now().Format("20060102150405.000000000")
	}
	return hex.EncodeToString(buffer)
}
