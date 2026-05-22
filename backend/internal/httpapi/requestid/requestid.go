package requestid

import "context"

const (
	HeaderName = "X-Request-ID"
)

type contextKey string

const requestIDContextKey contextKey = "request_id"

func WithValue(ctx context.Context, requestID string) context.Context {
	return context.WithValue(ctx, requestIDContextKey, requestID)
}

func Get(ctx context.Context) string {
	value := ctx.Value(requestIDContextKey)
	if value == nil {
		return ""
	}

	requestID, ok := value.(string)
	if !ok {
		return ""
	}
	return requestID
}
