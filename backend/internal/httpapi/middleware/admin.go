package middleware

import (
	"net/http"

	"clipbridge/backend/internal/httpapi/authcontext"
	"clipbridge/backend/internal/httpapi/response"
)

// RequireAdmin 只做一件事：确认当前登录态已经具备管理员标记。
// 这样各个管理接口就不需要重复写一遍相同的权限判断。
func RequireAdmin() Middleware {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			identity, ok := authcontext.Get(r.Context())
			if !ok {
				response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
				return
			}
			if !identity.IsAdmin {
				response.Error(w, r, http.StatusForbidden, "admin permission is required")
				return
			}

			next.ServeHTTP(w, r)
		})
	}
}
