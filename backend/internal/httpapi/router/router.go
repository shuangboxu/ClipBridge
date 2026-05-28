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
	authHandler := handlers.NewAuthHandler(application)
	accountHandler := handlers.NewAccountHandler(application)
	deviceHandler := handlers.NewDeviceHandler(application)
	clipboardHandler := handlers.NewClipboardHandler(application)
	syncHandler := handlers.NewSyncHandler(application)
	wsHandler := handlers.NewWSHandler(application)

	router := newSimpleRouter()
	router.Handle(http.MethodGet, "/healthz", http.HandlerFunc(healthHandler.Get))

	router.Handle(http.MethodPost, "/v1/auth/register", http.HandlerFunc(authHandler.Register))
	router.Handle(http.MethodPost, "/v1/auth/login", http.HandlerFunc(authHandler.Login))
	router.Handle(http.MethodPost, "/v1/auth/refresh", http.HandlerFunc(authHandler.Refresh))

	protectedProfile := middleware.Auth(application.AuthService)(http.HandlerFunc(systemHandler.GetProfile))
	protectedLogout := middleware.Auth(application.AuthService)(http.HandlerFunc(authHandler.Logout))
	protectedMe := middleware.Auth(application.AuthService)(http.HandlerFunc(accountHandler.GetMe))
	protectedChangePassword := middleware.Auth(application.AuthService)(http.HandlerFunc(accountHandler.ChangePassword))
	protectedDevices := middleware.Auth(application.AuthService)(http.HandlerFunc(deviceHandler.List))
	protectedUpdateDevice := middleware.Auth(application.AuthService)(http.HandlerFunc(deviceHandler.Update))
	protectedForceOfflineDevice := middleware.Auth(application.AuthService)(http.HandlerFunc(deviceHandler.ForceOffline))
	protectedCreateClipboardItem := middleware.Auth(application.AuthService)(http.HandlerFunc(clipboardHandler.CreateItem))
	protectedListClipboardItems := middleware.Auth(application.AuthService)(http.HandlerFunc(clipboardHandler.ListItems))
	protectedPullSync := middleware.Auth(application.AuthService)(http.HandlerFunc(syncHandler.Pull))
	protectedAckSync := middleware.Auth(application.AuthService)(http.HandlerFunc(syncHandler.Ack))

	router.Handle(http.MethodGet, "/v1/system/profile", protectedProfile)
	router.Handle(http.MethodPost, "/v1/auth/logout", protectedLogout)
	router.Handle(http.MethodGet, "/v1/account/me", protectedMe)
	router.Handle(http.MethodPost, "/v1/account/password", protectedChangePassword)
	router.Handle(http.MethodGet, "/v1/devices", protectedDevices)
	router.Handle(http.MethodPatch, "/v1/devices", protectedUpdateDevice)
	router.Handle(http.MethodPost, "/v1/devices/offline", protectedForceOfflineDevice)
	router.Handle(http.MethodPost, "/v1/clipboard/items", protectedCreateClipboardItem)
	router.Handle(http.MethodGet, "/v1/clipboard/items", protectedListClipboardItems)
	router.Handle(http.MethodGet, "/v1/sync/pull", protectedPullSync)
	router.Handle(http.MethodPost, "/v1/sync/ack", protectedAckSync)
	router.Handle(http.MethodGet, "/v1/ws", http.HandlerFunc(wsHandler.Connect))

	// 中间件顺序很重要：
	// 1. 先生成 request_id，确保后面的日志和响应都能带上它；
	// 2. 再记录访问日志；
	// 3. 再做 panic 恢复；
	// 4. 最后处理跨域。
	return middleware.Chain(
		router,
		middleware.RequestID(),
		middleware.AccessLog(),
		middleware.Recovery(),
		middleware.CORS(application.Config.CORS.AllowOrigins),
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
