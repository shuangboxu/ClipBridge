package auth

import "errors"

var (
	ErrNotFound            = errors.New("record not found")
	ErrConflict            = errors.New("record conflict")
	ErrInvalidCredentials  = errors.New("invalid credentials")
	ErrInvalidRefreshToken = errors.New("invalid refresh token")
	ErrUnauthorized        = errors.New("unauthorized")
)
