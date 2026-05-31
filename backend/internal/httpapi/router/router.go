package router

import (
	"net/http"
	"strings"

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
	fileHandler := handlers.NewFileHandler(application)
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
	protectedUploadFile := middleware.Auth(application.AuthService)(http.HandlerFunc(fileHandler.Upload))
	protectedListFiles := middleware.Auth(application.AuthService)(http.HandlerFunc(fileHandler.List))
	protectedDownloadFile := middleware.Auth(application.AuthService)(http.HandlerFunc(fileHandler.Download))
	protectedRenameFile := middleware.Auth(application.AuthService)(http.HandlerFunc(fileHandler.Rename))
	protectedDeleteFile := middleware.Auth(application.AuthService)(http.HandlerFunc(fileHandler.Delete))
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
	router.Handle(http.MethodPost, "/v1/files", protectedUploadFile)
	router.Handle(http.MethodGet, "/v1/files", protectedListFiles)
	router.Handle(http.MethodGet, "/v1/files/:id/download", protectedDownloadFile)
	router.Handle(http.MethodPatch, "/v1/files/:id", protectedRenameFile)
	router.Handle(http.MethodDelete, "/v1/files/:id", protectedDeleteFile)
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
	routes []route
}

func newSimpleRouter() *simpleRouter {
	return &simpleRouter{
		routes: make([]route, 0),
	}
}

func (r *simpleRouter) Handle(method, path string, handler http.Handler) {
	r.routes = append(r.routes, route{
		method:   method,
		path:     path,
		segments: splitPath(path),
		handler:  handler,
	})
}

func (r *simpleRouter) ServeHTTP(w http.ResponseWriter, req *http.Request) {
	requestSegments := splitPath(req.URL.Path)
	var methodNotAllowed bool

	for _, route := range r.routes {
		pathValues, matched := matchPath(route.segments, requestSegments)
		if !matched {
			continue
		}

		if route.method != req.Method {
			methodNotAllowed = true
			continue
		}

		for key, value := range pathValues {
			req.SetPathValue(key, value)
		}
		route.handler.ServeHTTP(w, req)
		return
	}

	if methodNotAllowed {
		response.Error(w, req, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	response.Error(w, req, http.StatusNotFound, "route not found")
}

type route struct {
	method   string
	path     string
	segments []string
	handler  http.Handler
}

func splitPath(path string) []string {
	trimmed := strings.Trim(path, "/")
	if trimmed == "" {
		return nil
	}
	return strings.Split(trimmed, "/")
}

func matchPath(routeSegments, requestSegments []string) (map[string]string, bool) {
	if len(routeSegments) != len(requestSegments) {
		return nil, false
	}

	pathValues := make(map[string]string)
	for index, segment := range routeSegments {
		requestSegment := requestSegments[index]
		if strings.HasPrefix(segment, ":") {
			name := strings.TrimPrefix(segment, ":")
			if name == "" || requestSegment == "" {
				return nil, false
			}
			pathValues[name] = requestSegment
			continue
		}
		if segment != requestSegment {
			return nil, false
		}
	}
	return pathValues, true
}
