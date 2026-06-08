package handlers

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"mime/multipart"
	"net/http"
	"strconv"
	"strings"
	"time"

	"clipbridge/backend/internal/shares"
)

const publicShareAccessTokenTTL = 15 * time.Minute

type publicShareAccessClaims struct {
	ShareID     string `json:"sid"`
	PublicToken string `json:"ptk"`
	IssuedAt    int64  `json:"iat"`
	ExpiresAt   int64  `json:"exp"`
}

type publicShareAccessHeader struct {
	Algorithm string `json:"alg"`
	Type      string `json:"typ"`
}

type publicShareAccessManager struct {
	secret []byte
	ttl    time.Duration
}

func newPublicShareAccessManager(secret string) *publicShareAccessManager {
	normalizedSecret := strings.TrimSpace(secret)
	if normalizedSecret == "" {
		normalizedSecret = "clipbridge-public-share"
	}
	return &publicShareAccessManager{
		secret: []byte(normalizedSecret),
		ttl:    publicShareAccessTokenTTL,
	}
}

func (m *publicShareAccessManager) Generate(item shares.Item) (string, time.Time, error) {
	if m == nil {
		return "", time.Time{}, errors.New("public share access manager is nil")
	}
	if strings.TrimSpace(item.ID) == "" {
		return "", time.Time{}, errors.New("share id is required")
	}
	if strings.TrimSpace(item.PublicToken) == "" {
		return "", time.Time{}, errors.New("share token is required")
	}

	now := time.Now().UTC()
	expiresAt := now.Add(m.ttl)
	claims := publicShareAccessClaims{
		ShareID:     item.ID,
		PublicToken: item.PublicToken,
		IssuedAt:    now.Unix(),
		ExpiresAt:   expiresAt.Unix(),
	}

	token, err := encodePublicShareAccessToken(m.secret, claims)
	if err != nil {
		return "", time.Time{}, err
	}
	return token, expiresAt, nil
}

func (m *publicShareAccessManager) Validate(token string, share shares.Item) error {
	if m == nil {
		return errors.New("public share access manager is nil")
	}
	claims, err := decodePublicShareAccessToken(m.secret, strings.TrimSpace(token))
	if err != nil {
		return err
	}
	if claims.ShareID != strings.TrimSpace(share.ID) {
		return errors.New("share id mismatch")
	}
	if claims.PublicToken != strings.TrimSpace(share.PublicToken) {
		return errors.New("share token mismatch")
	}
	return nil
}

func encodePublicShareAccessToken(secret []byte, claims publicShareAccessClaims) (string, error) {
	headerJSON, err := json.Marshal(publicShareAccessHeader{
		Algorithm: "HS256",
		Type:      "JWT",
	})
	if err != nil {
		return "", fmt.Errorf("marshal public share access token header failed: %w", err)
	}

	payloadJSON, err := json.Marshal(claims)
	if err != nil {
		return "", fmt.Errorf("marshal public share access token payload failed: %w", err)
	}

	encodedHeader := encodePublicShareAccessSegment(headerJSON)
	encodedPayload := encodePublicShareAccessSegment(payloadJSON)
	signingInput := encodedHeader + "." + encodedPayload
	signature := signPublicShareAccessToken(secret, signingInput)

	return signingInput + "." + encodePublicShareAccessSegment(signature), nil
}

func decodePublicShareAccessToken(secret []byte, token string) (*publicShareAccessClaims, error) {
	if strings.TrimSpace(token) == "" {
		return nil, errors.New("public share access token is required")
	}

	parts := strings.Split(token, ".")
	if len(parts) != 3 {
		return nil, errors.New("public share access token format is invalid")
	}

	headerBytes, err := decodePublicShareAccessSegment(parts[0])
	if err != nil {
		return nil, errors.New("public share access token header is invalid")
	}

	var header publicShareAccessHeader
	if err := json.Unmarshal(headerBytes, &header); err != nil {
		return nil, errors.New("public share access token header json is invalid")
	}
	if header.Algorithm != "HS256" || header.Type != "JWT" {
		return nil, errors.New("public share access token header is not supported")
	}

	signingInput := parts[0] + "." + parts[1]
	expectedSignature := signPublicShareAccessToken(secret, signingInput)

	actualSignature, err := decodePublicShareAccessSegment(parts[2])
	if err != nil {
		return nil, errors.New("public share access token signature is invalid")
	}
	if !hmac.Equal(expectedSignature, actualSignature) {
		return nil, errors.New("public share access token signature mismatch")
	}

	payloadBytes, err := decodePublicShareAccessSegment(parts[1])
	if err != nil {
		return nil, errors.New("public share access token payload is invalid")
	}

	var claims publicShareAccessClaims
	if err := json.Unmarshal(payloadBytes, &claims); err != nil {
		return nil, errors.New("public share access token payload json is invalid")
	}

	nowUnix := time.Now().Unix()
	if claims.ExpiresAt <= 0 || claims.ExpiresAt <= nowUnix {
		return nil, errors.New("public share access token has expired")
	}
	return &claims, nil
}

func signPublicShareAccessToken(secret []byte, signingInput string) []byte {
	mac := hmac.New(sha256.New, secret)
	_, _ = mac.Write([]byte(signingInput))
	return mac.Sum(nil)
}

func encodePublicShareAccessSegment(raw []byte) string {
	return base64.RawURLEncoding.EncodeToString(raw)
}

func decodePublicShareAccessSegment(value string) ([]byte, error) {
	return base64.RawURLEncoding.DecodeString(value)
}

func collectShareUploadHeaders(form *multipart.Form) []*multipart.FileHeader {
	if form == nil || form.File == nil {
		return nil
	}

	headers := make([]*multipart.FileHeader, 0)
	// 新前端会使用 files 字段；这里同时兼容旧版单文件 file 字段。
	for _, fieldName := range []string{"files", "file"} {
		headers = append(headers, form.File[fieldName]...)
	}
	return headers
}

func parseShareFileUploadRequests(r *http.Request, headers []*multipart.FileHeader) ([]shareFileUploadRequest, error) {
	if len(headers) == 0 {
		return nil, nil
	}

	manifestRaw := strings.TrimSpace(r.FormValue("files_manifest"))
	if manifestRaw == "" {
		requests := make([]shareFileUploadRequest, 0, len(headers))
		for _, header := range headers {
			requests = append(requests, shareFileUploadRequest{
				OriginalName:        strings.TrimSpace(header.Filename),
				OriginalContentType: normalizeUploadedContentType(header.Header.Get("Content-Type")),
			})
		}
		return requests, nil
	}

	var requests []shareFileUploadRequest
	if err := json.Unmarshal([]byte(manifestRaw), &requests); err != nil {
		return nil, errors.New("files_manifest is invalid")
	}
	if len(requests) != len(headers) {
		return nil, errors.New("files_manifest count does not match uploaded files")
	}

	for index := range requests {
		if strings.TrimSpace(requests[index].OriginalName) == "" {
			requests[index].OriginalName = strings.TrimSpace(headers[index].Filename)
		}
		if strings.TrimSpace(requests[index].OriginalContentType) == "" {
			requests[index].OriginalContentType = normalizeUploadedContentType(headers[index].Header.Get("Content-Type"))
		}
	}
	return requests, nil
}

func buildShareFileInputs(requests []shareFileUploadRequest) []shares.ShareFileInput {
	inputs := make([]shares.ShareFileInput, 0, len(requests))
	for _, request := range requests {
		inputs = append(inputs, shares.ShareFileInput{
			UploadName:   strings.TrimSpace(request.OriginalName),
			OriginalName: strings.TrimSpace(request.OriginalName),
			ContentType:  normalizeUploadedContentType(request.OriginalContentType),
			Encryption:   buildShareEncryptionInput(request.Encryption),
		})
	}
	return inputs
}

func closeMultipartFiles(files []multipart.File) {
	for _, file := range files {
		if file != nil {
			_ = file.Close()
		}
	}
}

func parseDownloadIntent(raw string) bool {
	value := strings.ToLower(strings.TrimSpace(raw))
	switch value {
	case "1", "true", "yes", "on":
		return true
	default:
		return false
	}
}

func normalizeUploadedContentType(contentType string) string {
	contentType = strings.TrimSpace(contentType)
	if contentType == "" {
		return "application/octet-stream"
	}
	return contentType
}

func parseShareFileIDFromRequest(r *http.Request) string {
	if r == nil {
		return ""
	}
	if value := strings.TrimSpace(r.PathValue("file_id")); value != "" {
		return value
	}
	if value := strings.TrimSpace(r.URL.Query().Get("file_id")); value != "" {
		return value
	}
	return ""
}

func parseOptionalDownloadLimit(raw string) (int64, error) {
	if strings.TrimSpace(raw) == "" {
		return 0, nil
	}
	value, err := strconv.ParseInt(strings.TrimSpace(raw), 10, 64)
	if err != nil || value < 0 {
		return 0, errors.New("invalid non-negative integer")
	}
	return value, nil
}
