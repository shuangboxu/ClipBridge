package handlers

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"mime/multipart"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"clipbridge/backend/internal/app"
	"clipbridge/backend/internal/httpapi/authcontext"
	"clipbridge/backend/internal/httpapi/response"
	"clipbridge/backend/internal/shares"
)

const (
	defaultShareExpireSeconds = int64(24 * 60 * 60)
	maxShareExpireSeconds     = int64(3650 * 24 * 60 * 60)
	shareMultipartMemory      = 32 << 20
)

type shareService interface {
	CreateTextShare(ctx context.Context, userID, deviceID string, input shares.CreateTextShareInput) (shares.Item, error)
	CreateFileShare(ctx context.Context, userID, deviceID string, input shares.CreateFileShareInput, sources []io.Reader) (shares.Item, error)
	List(ctx context.Context, userID, deviceID string, page, pageSize int, status string) (shares.ListResult, error)
	Revoke(ctx context.Context, userID, deviceID, shareID string) (shares.Item, error)
	GetPublicMeta(ctx context.Context, publicToken string) (shares.Item, error)
	OpenPublicShare(ctx context.Context, publicToken, password string) (shares.OpenShareResult, error)
	OpenPublicText(ctx context.Context, publicToken, password string) (shares.OpenTextResult, error)
	OpenPublicFile(ctx context.Context, publicToken, password string) (shares.OpenFileResult, error)
	GetPublicFile(ctx context.Context, publicToken, fileID string) (shares.OpenFileResult, error)
}

type ShareHandler struct {
	shareService shareService
	adminService shareDownloadPolicyService
	accessTokens *publicShareAccessManager
}

type shareDownloadPolicyService interface {
	PrepareDownloadWriter(ctx context.Context, userID string, dst io.Writer) (io.Writer, error)
}

type createTextShareRequest struct {
	TextContent      string                  `json:"text_content"`
	IsEncrypted      bool                    `json:"is_encrypted"`
	EncryptedPayload string                  `json:"encrypted_payload"`
	Encryption       *shareEncryptionRequest `json:"encryption"`
	Password         string                  `json:"password"`
	NeverExpires     bool                    `json:"never_expires"`
	ExpireSeconds    *int64                  `json:"expire_seconds"`
	BurnMode         string                  `json:"burn_mode"`
	BurnAfterSeconds int                     `json:"burn_after_seconds"`
	AllowCopyContent bool                    `json:"allow_copy_content"`
}

type openShareContentRequest struct {
	Password string `json:"password"`
	Part     string `json:"part"`
}

type shareFileUploadRequest struct {
	OriginalName        string                  `json:"original_name"`
	OriginalContentType string                  `json:"original_content_type"`
	Encryption          *shareEncryptionRequest `json:"encryption,omitempty"`
}

type shareEncryptionRequest struct {
	Version    string `json:"version"`
	KDF        string `json:"kdf"`
	Iterations int    `json:"iterations"`
	Salt       string `json:"salt"`
	Nonce      string `json:"nonce"`
	Cipher     string `json:"cipher"`
}

type shareEncryptionData struct {
	Version    string `json:"version"`
	KDF        string `json:"kdf"`
	Iterations int    `json:"iterations"`
	Salt       string `json:"salt"`
	Nonce      string `json:"nonce"`
	Cipher     string `json:"cipher"`
}

type shareFileData struct {
	ID           string `json:"id"`
	OriginalName string `json:"original_name"`
	ContentType  string `json:"content_type"`
	SizeBytes    int64  `json:"size_bytes"`
	IsImage      bool   `json:"is_image"`
	IsVideo      bool   `json:"is_video"`
}

type shareItemData struct {
	ID               string               `json:"id"`
	Token            string               `json:"token"`
	Status           string               `json:"status"`
	ContentKind      string               `json:"content_kind"`
	HasTextContent   bool                 `json:"has_text_content"`
	HasFileContent   bool                 `json:"has_file_content"`
	IsEncrypted      bool                 `json:"is_encrypted"`
	RequiresPassword bool                 `json:"requires_password"`
	TextPreview      string               `json:"text_preview"`
	File             *shareFileData       `json:"file,omitempty"`
	Files            []shareFileData      `json:"files,omitempty"`
	AllowCopyContent bool                 `json:"allow_copy_content"`
	BurnMode         string               `json:"burn_mode"`
	BurnAfterSeconds int                  `json:"burn_after_seconds"`
	RemainingSeconds int64                `json:"remaining_seconds"`
	ExpiresAt        string               `json:"expires_at"`
	FirstOpenedAt    string               `json:"first_opened_at"`
	BurnDeadline     string               `json:"burn_deadline"`
	ConsumedAt       string               `json:"consumed_at"`
	RevokedAt        string               `json:"revoked_at"`
	OpenCount        int64                `json:"open_count"`
	CreatedAt        string               `json:"created_at"`
	UpdatedAt        string               `json:"updated_at"`
	Encryption       *shareEncryptionData `json:"encryption,omitempty"`
}

type publicShareMetaData struct {
	Token            string          `json:"token"`
	Status           string          `json:"status"`
	ContentKind      string          `json:"content_kind"`
	HasTextContent   bool            `json:"has_text_content"`
	HasFileContent   bool            `json:"has_file_content"`
	IsEncrypted      bool            `json:"is_encrypted"`
	RequiresPassword bool            `json:"requires_password"`
	TextPreview      string          `json:"text_preview"`
	AllowCopyContent bool            `json:"allow_copy_content"`
	Files            []shareFileData `json:"files,omitempty"`
	BurnMode         string          `json:"burn_mode"`
	BurnAfterSeconds int             `json:"burn_after_seconds"`
	RemainingSeconds int64           `json:"remaining_seconds"`
	ExpiresAt        string          `json:"expires_at"`
	FirstOpenedAt    string          `json:"first_opened_at"`
	BurnDeadline     string          `json:"burn_deadline"`
	ConsumedAt       string          `json:"consumed_at"`
	RevokedAt        string          `json:"revoked_at"`
	OpenCount        int64           `json:"open_count"`
	CreatedAt        string          `json:"created_at"`
	File             *shareFileData  `json:"file,omitempty"`
}

type publicTextShareContentData struct {
	Token            string               `json:"token"`
	Status           string               `json:"status"`
	ContentKind      string               `json:"content_kind"`
	HasTextContent   bool                 `json:"has_text_content"`
	HasFileContent   bool                 `json:"has_file_content"`
	IsEncrypted      bool                 `json:"is_encrypted"`
	RequiresPassword bool                 `json:"requires_password"`
	AllowCopyContent bool                 `json:"allow_copy_content"`
	BurnMode         string               `json:"burn_mode"`
	BurnAfterSeconds int                  `json:"burn_after_seconds"`
	RemainingSeconds int64                `json:"remaining_seconds"`
	ExpiresAt        string               `json:"expires_at"`
	BurnDeadline     string               `json:"burn_deadline"`
	OpenCount        int64                `json:"open_count"`
	TextContent      string               `json:"text_content"`
	EncryptedPayload string               `json:"encrypted_payload"`
	Files            []shareFileData      `json:"files,omitempty"`
	Encryption       *shareEncryptionData `json:"encryption,omitempty"`
}

func NewShareHandler(application *app.App) *ShareHandler {
	if application == nil {
		return &ShareHandler{}
	}
	handler := &ShareHandler{
		shareService: application.ShareService,
		accessTokens: newPublicShareAccessManager(application.Config.Auth.JWTSecret),
	}
	if application.AdminService != nil {
		handler.adminService = application.AdminService
	}
	return handler
}

func (h *ShareHandler) CreateText(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}
	if h.shareService == nil {
		response.Error(w, r, http.StatusInternalServerError, "share service is not ready")
		return
	}

	var req createTextShareRequest
	if err := decodeJSONBody(r, &req); err != nil {
		response.Error(w, r, http.StatusBadRequest, "invalid request body")
		return
	}

	expiresAt, err := parseShareExpiresAt(req.NeverExpires, req.ExpireSeconds)
	if err != nil {
		response.Error(w, r, http.StatusBadRequest, err.Error())
		return
	}

	item, err := h.shareService.CreateTextShare(r.Context(), identity.UserID, identity.DeviceID, shares.CreateTextShareInput{
		TextContent:      req.TextContent,
		IsEncrypted:      req.IsEncrypted,
		EncryptedPayload: strings.TrimSpace(req.EncryptedPayload),
		Encryption:       buildShareEncryptionInput(req.Encryption),
		Password:         strings.TrimSpace(req.Password),
		AllowCopyContent: req.AllowCopyContent,
		ExpiresAt:        expiresAt,
		BurnMode:         req.BurnMode,
		BurnAfterSeconds: req.BurnAfterSeconds,
	})
	if err != nil {
		h.writeShareError(w, r, err, "create text share")
		return
	}

	response.Created(w, r, map[string]any{
		"share": buildShareItemData(item),
	})
}

func (h *ShareHandler) Create(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}
	if h.shareService == nil {
		response.Error(w, r, http.StatusInternalServerError, "share service is not ready")
		return
	}

	h.createMultipartShare(w, r, identity.UserID, identity.DeviceID, false)
}

func (h *ShareHandler) CreateFile(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}
	if h.shareService == nil {
		response.Error(w, r, http.StatusInternalServerError, "share service is not ready")
		return
	}

	h.createMultipartShare(w, r, identity.UserID, identity.DeviceID, true)
}

func (h *ShareHandler) List(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}
	if h.shareService == nil {
		response.Error(w, r, http.StatusInternalServerError, "share service is not ready")
		return
	}

	page, err := parseOptionalPositiveInt(r.URL.Query().Get("page"))
	if err != nil {
		response.Error(w, r, http.StatusBadRequest, "page must be a positive integer")
		return
	}
	pageSize, err := parseOptionalPositiveInt(r.URL.Query().Get("page_size"))
	if err != nil {
		response.Error(w, r, http.StatusBadRequest, "page_size must be a positive integer")
		return
	}

	result, err := h.shareService.List(r.Context(), identity.UserID, identity.DeviceID, page, pageSize, r.URL.Query().Get("status"))
	if err != nil {
		h.writeShareError(w, r, err, "list shares")
		return
	}

	items := make([]shareItemData, 0, len(result.Items))
	for _, item := range result.Items {
		items = append(items, buildShareItemData(item))
	}

	response.OK(w, r, map[string]any{
		"shares": items,
		"pagination": map[string]any{
			"page":        result.Page,
			"page_size":   result.PageSize,
			"total":       result.Total,
			"total_pages": result.TotalPages,
			"status":      result.Status,
		},
		"summary": map[string]any{
			"max_upload_bytes": result.MaxUploadBytes,
		},
	})
}

func (h *ShareHandler) Revoke(w http.ResponseWriter, r *http.Request) {
	identity, ok := authcontext.Get(r.Context())
	if !ok {
		response.Error(w, r, http.StatusInternalServerError, "auth identity is missing")
		return
	}
	if h.shareService == nil {
		response.Error(w, r, http.StatusInternalServerError, "share service is not ready")
		return
	}

	item, err := h.shareService.Revoke(r.Context(), identity.UserID, identity.DeviceID, r.PathValue("id"))
	if err != nil {
		h.writeShareError(w, r, err, "revoke share")
		return
	}

	response.OK(w, r, map[string]any{
		"share": buildShareItemData(item),
	})
}

func (h *ShareHandler) PublicMeta(w http.ResponseWriter, r *http.Request) {
	if h.shareService == nil {
		response.Error(w, r, http.StatusInternalServerError, "share service is not ready")
		return
	}

	item, err := h.shareService.GetPublicMeta(r.Context(), r.PathValue("token"))
	if err != nil {
		h.writeShareError(w, r, err, "query public share")
		return
	}

	response.OK(w, r, map[string]any{
		"share": buildPublicShareMetaData(item),
	})
}

func (h *ShareHandler) PublicContent(w http.ResponseWriter, r *http.Request) {
	if h.shareService == nil {
		response.Error(w, r, http.StatusInternalServerError, "share service is not ready")
		return
	}

	shareMeta, err := h.shareService.GetPublicMeta(r.Context(), r.PathValue("token"))
	if err != nil {
		h.writeShareError(w, r, err, "query public share")
		return
	}

	var req openShareContentRequest
	if r.Body != nil {
		if err := decodeJSONBody(r, &req); err != nil && !errors.Is(err, errEmptyJSONBody) {
			response.Error(w, r, http.StatusBadRequest, "invalid request body")
			return
		}
	}

	password := strings.TrimSpace(req.Password)
	part, err := resolvePublicSharePart(shareMeta, req.Part)
	if err != nil {
		response.Error(w, r, http.StatusBadRequest, err.Error())
		return
	}

	if part == "text" {
		result, err := h.shareService.OpenPublicText(r.Context(), shareMeta.PublicToken, password)
		if err != nil {
			h.writeShareError(w, r, err, "open public share")
			return
		}
		response.OK(w, r, map[string]any{
			"share": buildPublicTextShareContentData(result.Item),
		})
		return
	}

	result, err := h.shareService.OpenPublicFile(r.Context(), shareMeta.PublicToken, password)
	if err != nil {
		h.writeShareError(w, r, err, "open public share")
		return
	}
	defer result.File.Close()

	writer := io.Writer(w)
	if h.adminService != nil {
		limitedWriter, err := h.adminService.PrepareDownloadWriter(r.Context(), result.Item.UserID, w)
		if err != nil {
			response.Error(w, r, http.StatusInternalServerError, "prepare share download stream failed")
			return
		}
		writer = limitedWriter
	}

	writePublicSharedFile(w, writer, result, true)
}

func (h *ShareHandler) PublicOpen(w http.ResponseWriter, r *http.Request) {
	if h.shareService == nil {
		response.Error(w, r, http.StatusInternalServerError, "share service is not ready")
		return
	}
	if h.accessTokens == nil {
		response.Error(w, r, http.StatusInternalServerError, "public access token manager is not ready")
		return
	}

	var req openShareContentRequest
	if err := decodeJSONBody(r, &req); err != nil && !errors.Is(err, errEmptyJSONBody) {
		response.Error(w, r, http.StatusBadRequest, "invalid request body")
		return
	}

	result, err := h.shareService.OpenPublicShare(r.Context(), r.PathValue("token"), strings.TrimSpace(req.Password))
	if err != nil {
		h.writeShareError(w, r, err, "open public share")
		return
	}

	accessToken, accessTokenExpiresAt, err := h.accessTokens.Generate(result.Item)
	if err != nil {
		response.Error(w, r, http.StatusInternalServerError, "generate public access token failed")
		return
	}

	response.OK(w, r, map[string]any{
		"share":                   buildPublicTextShareContentData(result.Item),
		"access_token":            accessToken,
		"access_token_expires_at": formatTime(accessTokenExpiresAt),
	})
}

func (h *ShareHandler) PublicFile(w http.ResponseWriter, r *http.Request) {
	if h.shareService == nil {
		response.Error(w, r, http.StatusInternalServerError, "share service is not ready")
		return
	}
	if h.accessTokens == nil {
		response.Error(w, r, http.StatusInternalServerError, "public access token manager is not ready")
		return
	}

	shareMeta, err := h.shareService.GetPublicMeta(r.Context(), r.PathValue("token"))
	if err != nil {
		h.writeShareError(w, r, err, "query public share")
		return
	}

	accessToken := strings.TrimSpace(r.URL.Query().Get("access_token"))
	if accessToken == "" {
		response.Error(w, r, http.StatusUnauthorized, "public access token is required")
		return
	}
	if err := h.accessTokens.Validate(accessToken, shareMeta); err != nil {
		response.Error(w, r, http.StatusUnauthorized, "invalid public access token")
		return
	}

	// 这里对 revoked / expired 仍然直接拦截；
	// 只有“已焚毁但当前浏览器还持有短时访问令牌”的情况，会继续放行下载和预览。
	if shareMeta.Status == shares.StatusRevoked || shareMeta.Status == shares.StatusExpired {
		h.writeShareError(w, r, shares.ErrShareUnavailable, "open public share file")
		return
	}

	result, err := h.shareService.GetPublicFile(r.Context(), shareMeta.PublicToken, r.PathValue("file_id"))
	if err != nil {
		h.writeShareError(w, r, err, "open public share file")
		return
	}
	defer result.File.Close()

	writer := io.Writer(w)
	if h.adminService != nil {
		limitedWriter, err := h.adminService.PrepareDownloadWriter(r.Context(), result.Item.UserID, w)
		if err != nil {
			response.Error(w, r, http.StatusInternalServerError, "prepare share download stream failed")
			return
		}
		writer = limitedWriter
	}

	forceDownload := parseDownloadIntent(r.URL.Query().Get("download"))
	writePublicSharedFile(w, writer, result, forceDownload)
}

func (h *ShareHandler) createMultipartShare(w http.ResponseWriter, r *http.Request, userID, deviceID string, requireFile bool) {
	if err := r.ParseMultipartForm(shareMultipartMemory); err != nil {
		response.Error(w, r, http.StatusBadRequest, "multipart/form-data is required")
		return
	}
	defer removeMultipartForm(r.MultipartForm)

	fileHeaders := collectShareUploadHeaders(r.MultipartForm)
	if requireFile && len(fileHeaders) == 0 {
		response.Error(w, r, http.StatusBadRequest, "file field is required")
		return
	}

	isEncrypted, err := parseFlexibleBool(r.FormValue("is_encrypted"), false)
	if err != nil {
		response.Error(w, r, http.StatusBadRequest, "invalid is_encrypted")
		return
	}
	allowCopyContent, err := parseFlexibleBool(r.FormValue("allow_copy_content"), false)
	if err != nil {
		response.Error(w, r, http.StatusBadRequest, "invalid allow_copy_content")
		return
	}
	neverExpires, err := parseFlexibleBool(r.FormValue("never_expires"), false)
	if err != nil {
		response.Error(w, r, http.StatusBadRequest, "invalid never_expires")
		return
	}

	expireSeconds, err := parseOptionalPositiveInt64(r.FormValue("expire_seconds"))
	if err != nil {
		response.Error(w, r, http.StatusBadRequest, "expire_seconds must be a positive integer")
		return
	}
	expiresAt, err := parseShareExpiresAt(neverExpires, expireSeconds)
	if err != nil {
		response.Error(w, r, http.StatusBadRequest, err.Error())
		return
	}

	burnAfterSeconds, err := parseOptionalPositiveInt(r.FormValue("burn_after_seconds"))
	if err != nil && strings.TrimSpace(r.FormValue("burn_after_seconds")) != "" {
		response.Error(w, r, http.StatusBadRequest, "burn_after_seconds must be a positive integer")
		return
	}

	textContent := r.FormValue("text_content")
	textEncryptedPayload := firstNonEmpty(
		strings.TrimSpace(r.FormValue("text_encrypted_payload")),
		strings.TrimSpace(r.FormValue("encrypted_payload")),
	)
	password := strings.TrimSpace(r.FormValue("password"))

	if len(fileHeaders) == 0 {
		if textContent == "" && textEncryptedPayload == "" {
			response.Error(w, r, http.StatusBadRequest, "text_content or file is required")
			return
		}

		textEncryption, err := parseShareEncryptionJSON(firstNonEmpty(r.FormValue("text_encryption"), r.FormValue("encryption")))
		if err != nil {
			response.Error(w, r, http.StatusBadRequest, "invalid text_encryption")
			return
		}

		item, err := h.shareService.CreateTextShare(r.Context(), userID, deviceID, shares.CreateTextShareInput{
			TextContent:      textContent,
			IsEncrypted:      isEncrypted,
			EncryptedPayload: textEncryptedPayload,
			Encryption:       buildShareEncryptionInput(textEncryption),
			Password:         password,
			AllowCopyContent: allowCopyContent,
			ExpiresAt:        expiresAt,
			BurnMode:         r.FormValue("burn_mode"),
			BurnAfterSeconds: burnAfterSeconds,
		})
		if err != nil {
			h.writeShareError(w, r, err, "create share")
			return
		}

		response.Created(w, r, map[string]any{
			"share": buildShareItemData(item),
		})
		return
	}

	shareFiles, err := parseShareFileUploadRequests(r, fileHeaders)
	if err != nil {
		response.Error(w, r, http.StatusBadRequest, err.Error())
		return
	}
	textEncryption, err := parseShareEncryptionJSON(r.FormValue("text_encryption"))
	if err != nil {
		response.Error(w, r, http.StatusBadRequest, "invalid text_encryption")
		return
	}

	sources := make([]io.Reader, 0, len(fileHeaders))
	openedFiles := make([]multipart.File, 0, len(fileHeaders))
	for _, header := range fileHeaders {
		file, err := header.Open()
		if err != nil {
			for _, openedFile := range openedFiles {
				_ = openedFile.Close()
			}
			response.Error(w, r, http.StatusBadRequest, "invalid file field")
			return
		}
		openedFiles = append(openedFiles, file)
		sources = append(sources, file)
	}
	defer closeMultipartFiles(openedFiles)

	item, err := h.shareService.CreateFileShare(r.Context(), userID, deviceID, shares.CreateFileShareInput{
		Files:                buildShareFileInputs(shareFiles),
		TextContent:          textContent,
		TextEncryptedPayload: textEncryptedPayload,
		TextEncryption:       buildShareEncryptionInput(textEncryption),
		IsEncrypted:          isEncrypted,
		Password:             password,
		AllowCopyContent:     allowCopyContent,
		ExpiresAt:            expiresAt,
		BurnMode:             r.FormValue("burn_mode"),
		BurnAfterSeconds:     burnAfterSeconds,
	}, sources)
	if err != nil {
		h.writeShareError(w, r, err, "create share")
		return
	}

	response.Created(w, r, map[string]any{
		"share": buildShareItemData(item),
	})
}

func (h *ShareHandler) writeShareError(w http.ResponseWriter, r *http.Request, err error, action string) {
	switch {
	case err == nil:
		return
	case errors.Is(err, shares.ErrNotFound):
		response.Error(w, r, http.StatusNotFound, "share not found")
	case errors.Is(err, shares.ErrInvalidPassword):
		response.Error(w, r, http.StatusUnauthorized, "invalid password")
	case errors.Is(err, shares.ErrShareUnavailable):
		response.Error(w, r, http.StatusGone, "share is no longer available")
	case errors.Is(err, shares.ErrFileTooLarge):
		response.Error(w, r, http.StatusRequestEntityTooLarge, "file is too large")
	case errors.Is(err, shares.ErrStorageQuotaExceeded):
		response.Error(w, r, http.StatusRequestEntityTooLarge, "storage quota exceeded")
	case errors.Is(err, shares.ErrFileBodyMissing):
		response.Error(w, r, http.StatusNotFound, "share file not found")
	case errors.Is(err, shares.ErrTextContentMissing):
		response.Error(w, r, http.StatusNotFound, "share text not found")
	case isShareValidationError(err):
		response.Error(w, r, http.StatusBadRequest, err.Error())
	default:
		response.Error(w, r, http.StatusInternalServerError, action+" failed")
	}
}

func buildShareItemData(item shares.Item) shareItemData {
	data := shareItemData{
		ID:               item.ID,
		Token:            item.PublicToken,
		Status:           item.Status,
		ContentKind:      item.ContentKind,
		HasTextContent:   item.HasTextContent(),
		HasFileContent:   item.HasFileContent(),
		IsEncrypted:      item.IsEncrypted,
		RequiresPassword: item.RequiresPassword(),
		TextPreview:      item.TextPreview,
		AllowCopyContent: item.AllowCopyContent,
		BurnMode:         item.BurnMode,
		BurnAfterSeconds: item.BurnAfterSeconds,
		RemainingSeconds: item.RemainingSeconds,
		ExpiresAt:        formatOptionalTime(item.ExpiresAt),
		FirstOpenedAt:    formatOptionalTime(item.FirstOpenedAt),
		BurnDeadline:     formatOptionalTime(item.BurnDeadline),
		ConsumedAt:       formatOptionalTime(item.ConsumedAt),
		RevokedAt:        formatOptionalTime(item.RevokedAt),
		OpenCount:        item.OpenCount,
		CreatedAt:        formatTime(item.CreatedAt),
		UpdatedAt:        formatTime(item.UpdatedAt),
	}

	if item.IsEncrypted {
		if item.HasFileContent() {
			data.Encryption = buildShareEncryptionData(item.Encryption)
		} else {
			data.Encryption = buildShareEncryptionData(item.ResolveTextEncryption())
		}
	}
	if len(item.Files) > 0 {
		data.Files = buildShareFileDataList(item.Files)
		data.File = &data.Files[0]
	} else if primaryFile, ok := item.PrimaryFile(); ok {
		data.File = buildShareFileData(primaryFile)
		data.Files = []shareFileData{*data.File}
	}
	return data
}

func buildPublicShareMetaData(item shares.Item) publicShareMetaData {
	data := publicShareMetaData{
		Token:            item.PublicToken,
		Status:           item.Status,
		ContentKind:      item.ContentKind,
		HasTextContent:   item.HasTextContent(),
		HasFileContent:   item.HasFileContent(),
		IsEncrypted:      item.IsEncrypted,
		RequiresPassword: item.RequiresPassword(),
		TextPreview:      item.TextPreview,
		AllowCopyContent: item.AllowCopyContent,
		BurnMode:         item.BurnMode,
		BurnAfterSeconds: item.BurnAfterSeconds,
		RemainingSeconds: item.RemainingSeconds,
		ExpiresAt:        formatOptionalTime(item.ExpiresAt),
		FirstOpenedAt:    formatOptionalTime(item.FirstOpenedAt),
		BurnDeadline:     formatOptionalTime(item.BurnDeadline),
		ConsumedAt:       formatOptionalTime(item.ConsumedAt),
		RevokedAt:        formatOptionalTime(item.RevokedAt),
		OpenCount:        item.OpenCount,
		CreatedAt:        formatTime(item.CreatedAt),
	}
	if len(item.Files) > 0 {
		data.Files = buildShareFileDataList(item.Files)
		data.File = &data.Files[0]
	} else if primaryFile, ok := item.PrimaryFile(); ok {
		data.File = buildShareFileData(primaryFile)
		data.Files = []shareFileData{*data.File}
	}
	return data
}

func buildPublicTextShareContentData(item shares.Item) publicTextShareContentData {
	data := publicTextShareContentData{
		Token:            item.PublicToken,
		Status:           item.Status,
		ContentKind:      item.ContentKind,
		HasTextContent:   item.HasTextContent(),
		HasFileContent:   item.HasFileContent(),
		IsEncrypted:      item.IsEncrypted,
		RequiresPassword: item.RequiresPassword(),
		AllowCopyContent: item.AllowCopyContent,
		BurnMode:         item.BurnMode,
		BurnAfterSeconds: item.BurnAfterSeconds,
		RemainingSeconds: item.RemainingSeconds,
		ExpiresAt:        formatOptionalTime(item.ExpiresAt),
		BurnDeadline:     formatOptionalTime(item.BurnDeadline),
		OpenCount:        item.OpenCount,
		TextContent:      item.TextContent,
		EncryptedPayload: item.EncryptedPayload,
	}
	if len(item.Files) > 0 {
		data.Files = buildShareFileDataList(item.Files)
	} else if primaryFile, ok := item.PrimaryFile(); ok {
		data.Files = []shareFileData{*buildShareFileData(primaryFile)}
	}
	if item.IsEncrypted {
		data.Encryption = buildShareEncryptionData(item.ResolveTextEncryption())
	}
	return data
}

func buildShareFileDataList(files []shares.ShareFile) []shareFileData {
	items := make([]shareFileData, 0, len(files))
	for _, file := range files {
		if fileData := buildShareFileData(file); fileData != nil {
			items = append(items, *fileData)
		}
	}
	return items
}

func buildShareFileData(file shares.ShareFile) *shareFileData {
	if !file.HasStoredBody() {
		return nil
	}
	return &shareFileData{
		ID:           file.ID,
		OriginalName: file.OriginalName,
		ContentType:  file.ContentType,
		SizeBytes:    file.SizeBytes,
		IsImage:      file.IsImageFile(),
		IsVideo:      file.IsVideoFile(),
	}
}

func buildShareEncryptionInput(req *shareEncryptionRequest) shares.EncryptionMetadata {
	if req == nil {
		return shares.EncryptionMetadata{}
	}
	return shares.EncryptionMetadata{
		Version:    strings.TrimSpace(req.Version),
		KDF:        strings.TrimSpace(req.KDF),
		Iterations: req.Iterations,
		Salt:       strings.TrimSpace(req.Salt),
		Nonce:      strings.TrimSpace(req.Nonce),
		Cipher:     strings.TrimSpace(req.Cipher),
	}
}

func buildShareEncryptionData(value shares.EncryptionMetadata) *shareEncryptionData {
	if strings.TrimSpace(value.Version) == "" &&
		strings.TrimSpace(value.KDF) == "" &&
		value.Iterations == 0 &&
		strings.TrimSpace(value.Salt) == "" &&
		strings.TrimSpace(value.Nonce) == "" &&
		strings.TrimSpace(value.Cipher) == "" {
		return nil
	}
	return &shareEncryptionData{
		Version:    value.Version,
		KDF:        value.KDF,
		Iterations: value.Iterations,
		Salt:       value.Salt,
		Nonce:      value.Nonce,
		Cipher:     value.Cipher,
	}
}

func resolvePublicSharePart(item shares.Item, rawPart string) (string, error) {
	part := strings.ToLower(strings.TrimSpace(rawPart))
	switch part {
	case "":
		if item.HasFileContent() && !item.HasTextContent() {
			return "file", nil
		}
		return "text", nil
	case "text":
		return "text", nil
	case "file":
		return "file", nil
	default:
		return "", errors.New("part must be text or file")
	}
}

func writePublicSharedFile(w http.ResponseWriter, writer io.Writer, result shares.OpenFileResult, forceDownload bool) {
	fileName := result.ShareFile.OriginalName
	if fileName == "" {
		fileName = "share.bin"
	}

	contentType := result.ShareFile.ContentType
	downloadName := fileName
	if result.Item.IsEncrypted {
		contentType = "application/octet-stream"
		downloadName = "encrypted.bin"
	}
	if strings.TrimSpace(contentType) == "" {
		contentType = "application/octet-stream"
	}

	escapedName := url.QueryEscape(downloadName)
	w.Header().Set("Content-Type", contentType)
	w.Header().Set("Content-Length", strconv.FormatInt(result.SizeBytes, 10))
	if forceDownload || (!result.ShareFile.IsImageFile() && !result.ShareFile.IsVideoFile()) {
		w.Header().Set("Content-Disposition", "attachment; filename*=UTF-8''"+escapedName)
	} else {
		w.Header().Set("Content-Disposition", "inline; filename*=UTF-8''"+escapedName)
	}
	w.Header().Set("Cache-Control", "no-store")
	w.Header().Set("X-Share-Content-Kind", shares.ContentKindFile)
	w.Header().Set("X-Share-Status", result.Item.Status)
	w.Header().Set("X-Share-Burn-Mode", result.Item.BurnMode)
	w.Header().Set("X-Share-Burn-Deadline", formatOptionalTime(result.Item.BurnDeadline))
	w.Header().Set("X-Share-Remaining-Seconds", strconv.FormatInt(result.Item.RemainingSeconds, 10))
	w.Header().Set("X-Share-Is-Encrypted", strconv.FormatBool(result.Item.IsEncrypted))
	w.Header().Set("X-Share-File-ID", result.ShareFile.ID)
	w.Header().Set("X-Share-File-Original-Name", url.QueryEscape(fileName))
	w.Header().Set("X-Share-File-Content-Type", result.ShareFile.ContentType)
	if result.Item.IsEncrypted {
		if encryption := buildShareEncryptionData(result.ShareFile.Encryption); encryption != nil {
			w.Header().Set("X-Share-Encryption-Version", encryption.Version)
			w.Header().Set("X-Share-Encryption-KDF", encryption.KDF)
			w.Header().Set("X-Share-Encryption-Iterations", strconv.Itoa(encryption.Iterations))
			w.Header().Set("X-Share-Encryption-Salt", encryption.Salt)
			w.Header().Set("X-Share-Encryption-Nonce", encryption.Nonce)
			w.Header().Set("X-Share-Encryption-Cipher", encryption.Cipher)
		}
	}
	w.WriteHeader(http.StatusOK)
	_, _ = io.Copy(writer, result.File)
}

func parseShareExpiresAt(neverExpires bool, expireSeconds *int64) (*time.Time, error) {
	if neverExpires {
		return nil, nil
	}

	seconds := defaultShareExpireSeconds
	if expireSeconds != nil {
		seconds = *expireSeconds
	}
	if seconds <= 0 {
		return nil, errors.New("expire_seconds must be greater than 0")
	}
	if seconds > maxShareExpireSeconds {
		return nil, errors.New("expire_seconds is too large")
	}

	expiresAt := time.Now().UTC().Add(time.Duration(seconds) * time.Second)
	return &expiresAt, nil
}

func parseFlexibleBool(raw string, fallback bool) (bool, error) {
	value := strings.ToLower(strings.TrimSpace(raw))
	if value == "" {
		return fallback, nil
	}

	switch value {
	case "1", "true", "yes", "on":
		return true, nil
	case "0", "false", "no", "off":
		return false, nil
	default:
		return false, errors.New("invalid bool")
	}
}

func parseShareEncryptionJSON(raw string) (*shareEncryptionRequest, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return nil, nil
	}

	var req shareEncryptionRequest
	if err := json.Unmarshal([]byte(raw), &req); err != nil {
		return nil, err
	}
	return &req, nil
}

func removeMultipartForm(form *multipart.Form) {
	if form == nil {
		return
	}
	_ = form.RemoveAll()
}

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if strings.TrimSpace(value) != "" {
			return value
		}
	}
	return ""
}

func isShareValidationError(err error) bool {
	if err == nil {
		return false
	}

	message := err.Error()
	return strings.Contains(message, "is required") ||
		strings.Contains(message, "at least") ||
		strings.Contains(message, "at most") ||
		strings.Contains(message, "must be empty") ||
		strings.Contains(message, "must be greater than") ||
		strings.Contains(message, "must be in the future") ||
		strings.Contains(message, "too far in the future") ||
		strings.Contains(message, "invalid status filter") ||
		strings.Contains(message, "invalid burn mode") ||
		strings.Contains(message, "requires is_encrypted=true")
}
