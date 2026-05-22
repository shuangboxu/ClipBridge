package middleware

import (
	"log"
	"net/http"
	"time"

	"clipbridge/backend/internal/httpapi/requestid"
)

func AccessLog() Middleware {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			start := time.Now()
			recorder := &responseRecorder{
				ResponseWriter: w,
				statusCode:     http.StatusOK,
			}

			next.ServeHTTP(recorder, r)

			log.Printf(
				"request finished: request_id=%s method=%s path=%s status=%d duration=%s",
				requestid.Get(r.Context()),
				r.Method,
				r.URL.Path,
				recorder.statusCode,
				time.Since(start).String(),
			)
		})
	}
}

type responseRecorder struct {
	http.ResponseWriter
	statusCode int
}

func (r *responseRecorder) WriteHeader(statusCode int) {
	r.statusCode = statusCode
	r.ResponseWriter.WriteHeader(statusCode)
}
