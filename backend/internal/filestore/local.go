package filestore

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"time"

	"clipbridge/backend/internal/id"
)

var (
	ErrFileTooLarge      = errors.New("file too large")
	ErrInvalidStoredPath = errors.New("invalid stored path")
)

const copyBufferSize = 32 * 1024

type SaveResult struct {
	StoredPath string
	SizeBytes  int64
	SHA256     string
}

type LocalStore struct {
	baseDir string
}

func NewLocalStore(baseDir string) *LocalStore {
	cleanBase := filepath.Clean(strings.TrimSpace(baseDir))
	if cleanBase == "" || cleanBase == "." {
		cleanBase = "data/uploads"
	}
	return &LocalStore{baseDir: cleanBase}
}

func (s *LocalStore) EnsureBaseDir() error {
	return os.MkdirAll(s.baseDir, 0o755)
}

func (s *LocalStore) Save(ctx context.Context, userID, originalName string, src io.Reader, maxBytes int64) (SaveResult, error) {
	if strings.TrimSpace(userID) == "" || src == nil {
		return SaveResult{}, ErrInvalidStoredPath
	}

	safeName := sanitizeFileName(originalName)
	now := time.Now().UTC()
	relDir := filepath.Join(userID, now.Format("2006"), now.Format("01"), now.Format("02"))
	absDir := filepath.Join(s.baseDir, relDir)
	if err := os.MkdirAll(absDir, 0o755); err != nil {
		return SaveResult{}, err
	}

	fileToken, err := id.NewUUID()
	if err != nil {
		return SaveResult{}, err
	}
	tmpName := fileToken + ".part"
	finalName := fileToken + "_" + safeName
	absTmp := filepath.Join(absDir, tmpName)
	absFinal := filepath.Join(absDir, finalName)

	file, err := os.OpenFile(absTmp, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0o644)
	if err != nil {
		return SaveResult{}, err
	}

	hasher := sha256.New()
	buffer := make([]byte, copyBufferSize)
	var total int64

	copyErr := func() error {
		defer file.Close()

		for {
			if err := ctx.Err(); err != nil {
				return err
			}

			n, readErr := src.Read(buffer)
			if n > 0 {
				total += int64(n)
				if maxBytes > 0 && total > maxBytes {
					return ErrFileTooLarge
				}

				chunk := buffer[:n]
				if _, err := hasher.Write(chunk); err != nil {
					return err
				}
				if _, err := file.Write(chunk); err != nil {
					return err
				}
			}

			if readErr != nil {
				if errors.Is(readErr, io.EOF) {
					break
				}
				return readErr
			}
		}

		if total <= 0 {
			return ErrInvalidStoredPath
		}
		return nil
	}()
	if copyErr != nil {
		_ = os.Remove(absTmp)
		return SaveResult{}, copyErr
	}

	if err := os.Rename(absTmp, absFinal); err != nil {
		_ = os.Remove(absTmp)
		return SaveResult{}, err
	}

	return SaveResult{
		StoredPath: filepath.ToSlash(filepath.Join(relDir, finalName)),
		SizeBytes:  total,
		SHA256:     hex.EncodeToString(hasher.Sum(nil)),
	}, nil
}

func (s *LocalStore) Open(storedPath string) (*os.File, int64, error) {
	absPath, err := s.resolve(storedPath)
	if err != nil {
		return nil, 0, err
	}

	file, err := os.Open(absPath)
	if err != nil {
		return nil, 0, err
	}

	info, err := file.Stat()
	if err != nil {
		_ = file.Close()
		return nil, 0, err
	}
	if info.Size() <= 0 {
		_ = file.Close()
		return nil, 0, os.ErrNotExist
	}

	return file, info.Size(), nil
}

func (s *LocalStore) Delete(storedPath string) error {
	absPath, err := s.resolve(storedPath)
	if err != nil {
		return err
	}

	err = os.Remove(absPath)
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}
	return err
}

func (s *LocalStore) resolve(storedPath string) (string, error) {
	cleanRel := filepath.Clean(strings.TrimSpace(storedPath))
	if cleanRel == "" || cleanRel == "." || filepath.IsAbs(cleanRel) || strings.HasPrefix(cleanRel, "..") {
		return "", ErrInvalidStoredPath
	}

	absPath := filepath.Clean(filepath.Join(s.baseDir, cleanRel))
	baseAbs, err := filepath.Abs(s.baseDir)
	if err != nil {
		return "", fmt.Errorf("resolve base dir failed: %w", err)
	}
	fileAbs, err := filepath.Abs(absPath)
	if err != nil {
		return "", fmt.Errorf("resolve file path failed: %w", err)
	}
	if fileAbs != baseAbs && !strings.HasPrefix(fileAbs, baseAbs+string(os.PathSeparator)) {
		return "", ErrInvalidStoredPath
	}
	return fileAbs, nil
}

func sanitizeFileName(name string) string {
	trimmed := strings.TrimSpace(name)
	if trimmed == "" {
		return "file.bin"
	}

	trimmed = strings.ReplaceAll(trimmed, "\\", "_")
	trimmed = strings.ReplaceAll(trimmed, "/", "_")

	builder := strings.Builder{}
	for _, r := range trimmed {
		switch {
		case r >= 'a' && r <= 'z':
			builder.WriteRune(r)
		case r >= 'A' && r <= 'Z':
			builder.WriteRune(r)
		case r >= '0' && r <= '9':
			builder.WriteRune(r)
		case r == '.', r == '-', r == '_', r == ' ':
			builder.WriteRune(r)
		default:
			builder.WriteRune('_')
		}
	}

	result := strings.TrimSpace(builder.String())
	result = strings.Trim(result, ".")
	if result == "" {
		result = "file.bin"
	}
	if len(result) > 120 {
		result = result[:120]
	}
	return result
}
