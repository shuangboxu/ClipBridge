package middleware

import (
	"net/http"
	"strings"

	"clipbridge/backend/internal/auth"
	"clipbridge/backend/internal/httpapi/authcontext"
	"clipbridge/backend/internal/httpapi/response"
)

func Auth(tokenManager *auth.Manager) Middleware {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if tokenManager == nil {
				response.Error(w, r, http.StatusInternalServerError, "auth service is not ready")
				return
			}

			authHeader := strings.TrimSpace(r.Header.Get("Authorization"))
			token, ok := parseBearerToken(authHeader)
			if !ok {
				response.Error(w, r, http.StatusUnauthorized, "missing or invalid Authorization header")
				return
			}

			claims, err := tokenManager.ParseAccessToken(token)
			if err != nil {
				response.Error(w, r, http.StatusUnauthorized, "invalid access token")
				return
			}

			identity := authcontext.Identity{
				UserID:   claims.UserID,
				DeviceID: claims.DeviceID,
			}
			r = r.WithContext(authcontext.WithIdentity(r.Context(), identity))

			// 当前阶段只先做 token 解析和身份注入。
			// 下一阶段接入真实登录后，会在这里继续补：
			// 1. 用户是否存在；
			// 2. 设备是否存在；
			// 3. 设备是否已被吊销。
			next.ServeHTTP(w, r)
		})
	}
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
