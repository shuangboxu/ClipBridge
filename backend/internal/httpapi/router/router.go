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
	requestHandler := handlers.NewRequestHandler(application)
	adminHandler := handlers.NewAdminHandler(application)
	deviceHandler := handlers.NewDeviceHandler(application)
	clipboardHandler := handlers.NewClipboardHandler(application)
	fileHandler := handlers.NewFileHandler(application)
	shareHandler := handlers.NewShareHandler(application)
	syncHandler := handlers.NewSyncHandler(application)
	wsHandler := handlers.NewWSHandler(application)

	router := newSimpleRouter()
	router.Handle(http.MethodGet, "/healthz", http.HandlerFunc(healthHandler.Get))

	router.Handle(http.MethodGet, "/v1/auth/registration-policy", http.HandlerFunc(authHandler.GetRegistrationPolicy))
	router.Handle(http.MethodPost, "/v1/auth/register", http.HandlerFunc(authHandler.Register))
	router.Handle(http.MethodPost, "/v1/auth/login", http.HandlerFunc(authHandler.Login))
	router.Handle(http.MethodPost, "/v1/auth/refresh", http.HandlerFunc(authHandler.Refresh))

	protectedProfile := middleware.Auth(application.AuthService)(http.HandlerFunc(systemHandler.GetProfile))
	protectedLogout := middleware.Auth(application.AuthService)(http.HandlerFunc(authHandler.Logout))
	protectedMe := middleware.Auth(application.AuthService)(http.HandlerFunc(accountHandler.GetMe))
	protectedChangePassword := middleware.Auth(application.AuthService)(http.HandlerFunc(accountHandler.ChangePassword))
	protectedCreateQuotaRequest := middleware.Auth(application.AuthService)(http.HandlerFunc(requestHandler.CreateQuotaRequest))
	protectedListQuotaRequests := middleware.Auth(application.AuthService)(http.HandlerFunc(requestHandler.ListQuotaRequests))
	protectedCreateBandwidthRequest := middleware.Auth(application.AuthService)(http.HandlerFunc(requestHandler.CreateBandwidthRequest))
	protectedListBandwidthRequests := middleware.Auth(application.AuthService)(http.HandlerFunc(requestHandler.ListBandwidthRequests))
	protectedCreateAdminRequest := middleware.Auth(application.AuthService)(http.HandlerFunc(requestHandler.CreateAdminRequest))
	protectedListAdminRequests := middleware.Auth(application.AuthService)(http.HandlerFunc(requestHandler.ListAdminRequests))
	protectedDevices := middleware.Auth(application.AuthService)(http.HandlerFunc(deviceHandler.List))
	protectedUpdateDevice := middleware.Auth(application.AuthService)(http.HandlerFunc(deviceHandler.Update))
	protectedForceOfflineDevice := middleware.Auth(application.AuthService)(http.HandlerFunc(deviceHandler.ForceOffline))
	protectedCreateClipboardItem := middleware.Auth(application.AuthService)(http.HandlerFunc(clipboardHandler.CreateItem))
	protectedListClipboardItems := middleware.Auth(application.AuthService)(http.HandlerFunc(clipboardHandler.ListItems))
	protectedDeleteClipboardItem := middleware.Auth(application.AuthService)(http.HandlerFunc(clipboardHandler.DeleteItem))
	protectedClearClipboardHistory := middleware.Auth(application.AuthService)(http.HandlerFunc(clipboardHandler.ClearHistory))
	protectedCleanupClipboardHistory := middleware.Auth(application.AuthService)(http.HandlerFunc(clipboardHandler.CleanupHistory))
	protectedGetClipboardHistorySettings := middleware.Auth(application.AuthService)(http.HandlerFunc(clipboardHandler.GetHistorySettings))
	protectedUpdateClipboardHistorySettings := middleware.Auth(application.AuthService)(http.HandlerFunc(clipboardHandler.UpdateHistorySettings))
	protectedUploadFile := middleware.Auth(application.AuthService)(http.HandlerFunc(fileHandler.Upload))
	protectedListFiles := middleware.Auth(application.AuthService)(http.HandlerFunc(fileHandler.List))
	protectedDownloadFile := middleware.Auth(application.AuthService)(http.HandlerFunc(fileHandler.Download))
	protectedRenameFile := middleware.Auth(application.AuthService)(http.HandlerFunc(fileHandler.Rename))
	protectedDeleteFile := middleware.Auth(application.AuthService)(http.HandlerFunc(fileHandler.Delete))
	protectedCreateShare := middleware.Auth(application.AuthService)(http.HandlerFunc(shareHandler.Create))
	protectedCreateTextShare := middleware.Auth(application.AuthService)(http.HandlerFunc(shareHandler.CreateText))
	protectedCreateFileShare := middleware.Auth(application.AuthService)(http.HandlerFunc(shareHandler.CreateFile))
	protectedListShares := middleware.Auth(application.AuthService)(http.HandlerFunc(shareHandler.List))
	protectedRevokeShare := middleware.Auth(application.AuthService)(http.HandlerFunc(shareHandler.Revoke))
	protectedPullSync := middleware.Auth(application.AuthService)(http.HandlerFunc(syncHandler.Pull))
	protectedAckSync := middleware.Auth(application.AuthService)(http.HandlerFunc(syncHandler.Ack))
	adminOnlyGetSettings := middleware.Chain(
		http.HandlerFunc(adminHandler.GetSettings),
		middleware.Auth(application.AuthService),
		middleware.RequireAdmin(),
	)
	adminOnlyUpdateSettings := middleware.Chain(
		http.HandlerFunc(adminHandler.UpdateSettings),
		middleware.Auth(application.AuthService),
		middleware.RequireAdmin(),
	)
	adminOnlyListUsers := middleware.Chain(
		http.HandlerFunc(adminHandler.ListUsers),
		middleware.Auth(application.AuthService),
		middleware.RequireAdmin(),
	)
	adminOnlyUpdateUser := middleware.Chain(
		http.HandlerFunc(adminHandler.UpdateUser),
		middleware.Auth(application.AuthService),
		middleware.RequireAdmin(),
	)
	adminOnlyDeleteUser := middleware.Chain(
		http.HandlerFunc(adminHandler.DeleteUser),
		middleware.Auth(application.AuthService),
		middleware.RequireAdmin(),
	)
	adminOnlyListQuotaRequests := middleware.Chain(
		http.HandlerFunc(adminHandler.ListQuotaRequests),
		middleware.Auth(application.AuthService),
		middleware.RequireAdmin(),
	)
	adminOnlyApproveQuotaRequest := middleware.Chain(
		http.HandlerFunc(adminHandler.ApproveQuotaRequest),
		middleware.Auth(application.AuthService),
		middleware.RequireAdmin(),
	)
	adminOnlyRejectQuotaRequest := middleware.Chain(
		http.HandlerFunc(adminHandler.RejectQuotaRequest),
		middleware.Auth(application.AuthService),
		middleware.RequireAdmin(),
	)
	adminOnlyListBandwidthRequests := middleware.Chain(
		http.HandlerFunc(adminHandler.ListBandwidthRequests),
		middleware.Auth(application.AuthService),
		middleware.RequireAdmin(),
	)
	adminOnlyApproveBandwidthRequest := middleware.Chain(
		http.HandlerFunc(adminHandler.ApproveBandwidthRequest),
		middleware.Auth(application.AuthService),
		middleware.RequireAdmin(),
	)
	adminOnlyRejectBandwidthRequest := middleware.Chain(
		http.HandlerFunc(adminHandler.RejectBandwidthRequest),
		middleware.Auth(application.AuthService),
		middleware.RequireAdmin(),
	)
	adminOnlyListAdminRequests := middleware.Chain(
		http.HandlerFunc(adminHandler.ListAdminRequests),
		middleware.Auth(application.AuthService),
		middleware.RequireAdmin(),
	)
	adminOnlyApproveAdminRequest := middleware.Chain(
		http.HandlerFunc(adminHandler.ApproveAdminRequest),
		middleware.Auth(application.AuthService),
		middleware.RequireAdmin(),
	)
	adminOnlyRejectAdminRequest := middleware.Chain(
		http.HandlerFunc(adminHandler.RejectAdminRequest),
		middleware.Auth(application.AuthService),
		middleware.RequireAdmin(),
	)

	router.Handle(http.MethodGet, "/v1/system/profile", protectedProfile)
	router.Handle(http.MethodPost, "/v1/auth/logout", protectedLogout)
	router.Handle(http.MethodGet, "/v1/account/me", protectedMe)
	router.Handle(http.MethodPost, "/v1/account/password", protectedChangePassword)
	router.Handle(http.MethodPost, "/v1/account/quota-requests", protectedCreateQuotaRequest)
	router.Handle(http.MethodGet, "/v1/account/quota-requests", protectedListQuotaRequests)
	router.Handle(http.MethodPost, "/v1/account/bandwidth-requests", protectedCreateBandwidthRequest)
	router.Handle(http.MethodGet, "/v1/account/bandwidth-requests", protectedListBandwidthRequests)
	router.Handle(http.MethodPost, "/v1/account/admin-requests", protectedCreateAdminRequest)
	router.Handle(http.MethodGet, "/v1/account/admin-requests", protectedListAdminRequests)
	router.Handle(http.MethodGet, "/v1/devices", protectedDevices)
	router.Handle(http.MethodPatch, "/v1/devices", protectedUpdateDevice)
	router.Handle(http.MethodPost, "/v1/devices/offline", protectedForceOfflineDevice)
	router.Handle(http.MethodPost, "/v1/clipboard/items", protectedCreateClipboardItem)
	router.Handle(http.MethodGet, "/v1/clipboard/items", protectedListClipboardItems)
	router.Handle(http.MethodDelete, "/v1/clipboard/items/:id", protectedDeleteClipboardItem)
	router.Handle(http.MethodPost, "/v1/clipboard/history/clear", protectedClearClipboardHistory)
	router.Handle(http.MethodPost, "/v1/clipboard/history/cleanup", protectedCleanupClipboardHistory)
	router.Handle(http.MethodGet, "/v1/clipboard/history/settings", protectedGetClipboardHistorySettings)
	router.Handle(http.MethodPut, "/v1/clipboard/history/settings", protectedUpdateClipboardHistorySettings)
	router.Handle(http.MethodPost, "/v1/files", protectedUploadFile)
	router.Handle(http.MethodGet, "/v1/files", protectedListFiles)
	router.Handle(http.MethodGet, "/v1/files/:id/download", protectedDownloadFile)
	router.Handle(http.MethodPatch, "/v1/files/:id", protectedRenameFile)
	router.Handle(http.MethodDelete, "/v1/files/:id", protectedDeleteFile)
	router.Handle(http.MethodPost, "/v1/shares", protectedCreateShare)
	router.Handle(http.MethodPost, "/v1/shares/text", protectedCreateTextShare)
	router.Handle(http.MethodPost, "/v1/shares/file", protectedCreateFileShare)
	router.Handle(http.MethodGet, "/v1/shares", protectedListShares)
	router.Handle(http.MethodPost, "/v1/shares/:id/revoke", protectedRevokeShare)
	router.Handle(http.MethodGet, "/v1/public/qrcode", http.HandlerFunc(shareHandler.PublicQRCode))
	router.Handle(http.MethodGet, "/v1/public/shares/:token/meta", http.HandlerFunc(shareHandler.PublicMeta))
	router.Handle(http.MethodPost, "/v1/public/shares/:token/open", http.HandlerFunc(shareHandler.PublicOpen))
	router.Handle(http.MethodPost, "/v1/public/shares/:token/content", http.HandlerFunc(shareHandler.PublicContent))
	router.Handle(http.MethodGet, "/v1/public/shares/:token/files/:file_id", http.HandlerFunc(shareHandler.PublicFile))
	router.Handle(http.MethodGet, "/v1/sync/pull", protectedPullSync)
	router.Handle(http.MethodPost, "/v1/sync/ack", protectedAckSync)
	router.Handle(http.MethodGet, "/v1/admin/settings", adminOnlyGetSettings)
	router.Handle(http.MethodPut, "/v1/admin/settings", adminOnlyUpdateSettings)
	router.Handle(http.MethodGet, "/v1/admin/users", adminOnlyListUsers)
	router.Handle(http.MethodPatch, "/v1/admin/users/:id", adminOnlyUpdateUser)
	router.Handle(http.MethodDelete, "/v1/admin/users/:id", adminOnlyDeleteUser)
	router.Handle(http.MethodGet, "/v1/admin/quota-requests", adminOnlyListQuotaRequests)
	router.Handle(http.MethodPost, "/v1/admin/quota-requests/:id/approve", adminOnlyApproveQuotaRequest)
	router.Handle(http.MethodPost, "/v1/admin/quota-requests/:id/reject", adminOnlyRejectQuotaRequest)
	router.Handle(http.MethodGet, "/v1/admin/bandwidth-requests", adminOnlyListBandwidthRequests)
	router.Handle(http.MethodPost, "/v1/admin/bandwidth-requests/:id/approve", adminOnlyApproveBandwidthRequest)
	router.Handle(http.MethodPost, "/v1/admin/bandwidth-requests/:id/reject", adminOnlyRejectBandwidthRequest)
	router.Handle(http.MethodGet, "/v1/admin/admin-requests", adminOnlyListAdminRequests)
	router.Handle(http.MethodPost, "/v1/admin/admin-requests/:id/approve", adminOnlyApproveAdminRequest)
	router.Handle(http.MethodPost, "/v1/admin/admin-requests/:id/reject", adminOnlyRejectAdminRequest)
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
