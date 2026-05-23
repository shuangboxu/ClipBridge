package middleware

import (
	"errors"
	"net/http"
	"strings"

	"clipbridge/backend/internal/auth"
	"clipbridge/backend/internal/httpapi/authcontext"
	"clipbridge/backend/internal/httpapi/response"
)

func Auth(authService *auth.Service) Middleware {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if authService == nil {
				response.Error(w, r, http.StatusInternalServerError, "auth service is not ready")
				return
			}

			authHeader := strings.TrimSpace(r.Header.Get("Authorization"))
			token, ok := parseBearerToken(authHeader)
			if !ok {
				response.Error(w, r, http.StatusUnauthorized, "missing or invalid Authorization header")
				return
			}

			user, device, err := authService.AuthenticateAccessToken(r.Context(), token)
			if err != nil {
				if errors.Is(err, auth.ErrUnauthorized) {
					response.Error(w, r, http.StatusUnauthorized, "invalid access token")
					return
				}
				response.Error(w, r, http.StatusInternalServerError, "auth validation failed")
				return
			}

			identity := authcontext.Identity{
				UserID:   user.ID,
				DeviceID: device.ID,
			}
			r = r.WithContext(authcontext.WithIdentity(r.Context(), identity))

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
