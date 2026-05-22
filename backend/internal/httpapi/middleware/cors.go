package middleware

import (
	"net/http"
	"strings"
)

func CORS(allowOrigins []string) Middleware {
	allowAll := len(allowOrigins) == 1 && allowOrigins[0] == "*"
	originSet := make(map[string]struct{}, len(allowOrigins))
	for _, origin := range allowOrigins {
		origin = strings.TrimSpace(origin)
		if origin != "" {
			originSet[origin] = struct{}{}
		}
	}

	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			origin := r.Header.Get("Origin")

			if allowAll {
				w.Header().Set("Access-Control-Allow-Origin", "*")
			} else if _, ok := originSet[origin]; ok {
				w.Header().Set("Access-Control-Allow-Origin", origin)
				w.Header().Set("Vary", "Origin")
				w.Header().Set("Access-Control-Allow-Credentials", "true")
			}

			w.Header().Set("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Request-ID")
			w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS")

			// 预检请求只需要返回“允许哪些规则”，不需要继续进入业务处理器。
			if r.Method == http.MethodOptions {
				w.WriteHeader(http.StatusNoContent)
				return
			}

			next.ServeHTTP(w, r)
		})
	}
}
