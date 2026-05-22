package auth

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"
)

// Claims 是当前阶段最小可用的 Access Token 载荷。
// 这里只放“用户 ID”和“设备 ID”，因为下一阶段的登录、设备校验都会依赖这两个字段。
type Claims struct {
	UserID    string `json:"uid"`
	DeviceID  string `json:"did"`
	Subject   string `json:"sub"`
	IssuedAt  int64  `json:"iat"`
	ExpiresAt int64  `json:"exp"`
}

type Manager struct {
	secret         []byte
	accessTokenTTL time.Duration
}

func NewManager(secret string, accessTokenTTL time.Duration) *Manager {
	return &Manager{
		secret:         []byte(secret),
		accessTokenTTL: accessTokenTTL,
	}
}

func (m *Manager) GenerateAccessToken(userID, deviceID string) (string, time.Time, error) {
	if m == nil {
		return "", time.Time{}, errors.New("token manager is nil")
	}
	if userID == "" {
		return "", time.Time{}, errors.New("userID is required")
	}
	if deviceID == "" {
		return "", time.Time{}, errors.New("deviceID is required")
	}

	now := time.Now()
	expiresAt := now.Add(m.accessTokenTTL)
	claims := Claims{
		UserID:    userID,
		DeviceID:  deviceID,
		Subject:   userID,
		IssuedAt:  now.Unix(),
		ExpiresAt: expiresAt.Unix(),
	}

	token, err := encodeToken(m.secret, claims)
	if err != nil {
		return "", time.Time{}, err
	}
	return token, expiresAt, nil
}

func (m *Manager) ParseAccessToken(tokenString string) (*Claims, error) {
	if m == nil {
		return nil, errors.New("token manager is nil")
	}
	if tokenString == "" {
		return nil, errors.New("token is empty")
	}

	claims, err := decodeToken(m.secret, tokenString)
	if err != nil {
		return nil, err
	}
	if claims.UserID == "" || claims.DeviceID == "" {
		return nil, errors.New("token claims are incomplete")
	}
	return claims, nil
}

type tokenHeader struct {
	Algorithm string `json:"alg"`
	Type      string `json:"typ"`
}

func encodeToken(secret []byte, claims Claims) (string, error) {
	headerJSON, err := json.Marshal(tokenHeader{
		Algorithm: "HS256",
		Type:      "JWT",
	})
	if err != nil {
		return "", fmt.Errorf("marshal token header failed: %w", err)
	}

	payloadJSON, err := json.Marshal(claims)
	if err != nil {
		return "", fmt.Errorf("marshal token claims failed: %w", err)
	}

	encodedHeader := encodeSegment(headerJSON)
	encodedPayload := encodeSegment(payloadJSON)
	signingInput := encodedHeader + "." + encodedPayload

	signature := sign(secret, signingInput)
	return signingInput + "." + encodeSegment(signature), nil
}

func decodeToken(secret []byte, tokenString string) (*Claims, error) {
	parts := strings.Split(tokenString, ".")
	if len(parts) != 3 {
		return nil, errors.New("token format is invalid")
	}

	headerBytes, err := decodeSegment(parts[0])
	if err != nil {
		return nil, errors.New("token header is invalid")
	}

	var header tokenHeader
	if err := json.Unmarshal(headerBytes, &header); err != nil {
		return nil, errors.New("token header json is invalid")
	}
	if header.Algorithm != "HS256" || header.Type != "JWT" {
		return nil, errors.New("token header is not supported")
	}

	signingInput := parts[0] + "." + parts[1]
	expectedSignature := sign(secret, signingInput)

	actualSignature, err := decodeSegment(parts[2])
	if err != nil {
		return nil, errors.New("token signature is invalid")
	}
	if !hmac.Equal(expectedSignature, actualSignature) {
		return nil, errors.New("token signature mismatch")
	}

	payloadBytes, err := decodeSegment(parts[1])
	if err != nil {
		return nil, errors.New("token payload is invalid")
	}

	var claims Claims
	if err := json.Unmarshal(payloadBytes, &claims); err != nil {
		return nil, errors.New("token payload json is invalid")
	}

	nowUnix := time.Now().Unix()
	if claims.ExpiresAt <= 0 || claims.ExpiresAt <= nowUnix {
		return nil, errors.New("token has expired")
	}
	return &claims, nil
}

func sign(secret []byte, signingInput string) []byte {
	mac := hmac.New(sha256.New, secret)
	_, _ = mac.Write([]byte(signingInput))
	return mac.Sum(nil)
}

func encodeSegment(raw []byte) string {
	return base64.RawURLEncoding.EncodeToString(raw)
}

func decodeSegment(value string) ([]byte, error) {
	return base64.RawURLEncoding.DecodeString(value)
}
