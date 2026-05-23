package auth

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"time"
)

type RefreshToken struct {
	Raw       string
	Hash      string
	ExpiresAt time.Time
}

func GenerateRefreshToken(ttl time.Duration) (RefreshToken, error) {
	if ttl <= 0 {
		return RefreshToken{}, fmt.Errorf("refresh token ttl must be greater than 0")
	}

	var raw [32]byte
	if _, err := rand.Read(raw[:]); err != nil {
		return RefreshToken{}, fmt.Errorf("generate refresh token bytes failed: %w", err)
	}

	token := base64.RawURLEncoding.EncodeToString(raw[:])
	return RefreshToken{
		Raw:       token,
		Hash:      HashToken(token),
		ExpiresAt: time.Now().Add(ttl),
	}, nil
}

func HashToken(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}
