package middleware

import (
	"log"
	"net/http"

	"clipbridge/backend/internal/httpapi/requestid"
	"clipbridge/backend/internal/httpapi/response"
)

func Recovery() Middleware {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			defer func() {
				if recovered := recover(); recovered != nil {
					log.Printf(
						"panic recovered: request_id=%s method=%s path=%s err=%v",
						requestid.Get(r.Context()),
						r.Method,
						r.URL.Path,
						recovered,
					)

					response.Error(w, r, http.StatusInternalServerError, "internal server error")
				}
			}()

			next.ServeHTTP(w, r)
		})
	}
}
