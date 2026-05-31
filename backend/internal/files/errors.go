package files

import "errors"

var (
	ErrNotFound        = errors.New("record not found")
	ErrFileTooLarge    = errors.New("file too large")
	ErrFileBodyMissing = errors.New("file body missing")
)
