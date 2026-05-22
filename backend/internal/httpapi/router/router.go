package router

import (
	"net/http"

	"clipbridge/backend/internal/app"
	"clipbridge/backend/internal/httpapi/handlers"
	"clipbridge/backend/internal/httpapi/middleware"
	"clipbridge/backend/internal/httpapi/response"
)

func New(application *app.App) http.Handler {
	healthHandler := handlers.NewHealthHandler(application)
	systemHandler := handlers.NewSystemHandler()

	router := newSimpleRouter()
	router.Handle(http.MethodGet, "/healthz", http.HandlerFunc(healthHandler.Get))

	// 这个示例接口的存在意义是：
	// 让第一阶段就能验证“鉴权中间件是否真的起作用了”。
	protectedProfile := middleware.Auth(application.TokenManager)(http.HandlerFunc(systemHandler.GetProfile))
	router.Handle(http.MethodGet, "/v1/system/profile", protectedProfile)

	// 中间件顺序很重要：
	// 1. 先生成 request_id，确保后面的日志和响应都能带上它；
	// 2. 再记录访问日志；
	// 3. 再做 panic 恢复；
	// 4. 最后处理跨域。
	return middleware.Chain(
		router,
		middleware.CORS(application.Config.CORS.AllowOrigins),
		middleware.Recovery(),
		middleware.AccessLog(),
		middleware.RequestID(),
	)
}

type simpleRouter struct {
	routes map[string]map[string]http.Handler
}

func newSimpleRouter() *simpleRouter {
	return &simpleRouter{
		routes: make(map[string]map[string]http.Handler),
	}
}

func (r *simpleRouter) Handle(method, path string, handler http.Handler) {
	if r.routes[path] == nil {
		r.routes[path] = make(map[string]http.Handler)
	}
	r.routes[path][method] = handler
}

func (r *simpleRouter) ServeHTTP(w http.ResponseWriter, req *http.Request) {
	methodMap, ok := r.routes[req.URL.Path]
	if !ok {
		response.Error(w, req, http.StatusNotFound, "route not found")
		return
	}

	handler, ok := methodMap[req.Method]
	if !ok {
		response.Error(w, req, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	handler.ServeHTTP(w, req)
}
