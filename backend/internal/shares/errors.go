package shares

import "errors"

var (
	ErrNotFound             = errors.New("record not found")
	ErrShareUnavailable     = errors.New("share is not available")
	ErrInvalidPassword      = errors.New("invalid password")
	ErrFileTooLarge         = errors.New("file too large")
	ErrFileBodyMissing      = errors.New("file body missing")
	ErrTextContentMissing   = errors.New("share text content missing")
	ErrStorageQuotaExceeded = errors.New("storage quota exceeded")
)
