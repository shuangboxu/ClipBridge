package middleware

import "net/http"

// Middleware 表示一个标准的 HTTP 中间件。
// 它接收“下一个处理器”，并返回一个包装后的新处理器。
type Middleware func(http.Handler) http.Handler

func Chain(handler http.Handler, middlewares ...Middleware) http.Handler {
	wrapped := handler
	for index := len(middlewares) - 1; index >= 0; index-- {
		wrapped = middlewares[index](wrapped)
	}
	return wrapped
}
