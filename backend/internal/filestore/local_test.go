package filestore

import (
	"context"
	"errors"
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestLocalStoreSaveOpenDelete(t *testing.T) {
	baseDir := t.TempDir()
	store := NewLocalStore(baseDir)

	saved, err := store.Save(context.Background(), "user-1", "hello.txt", strings.NewReader("clipbridge"), 1024)
	if err != nil {
		t.Fatalf("save file failed: %v", err)
	}
	if saved.SizeBytes != int64(len("clipbridge")) {
		t.Fatalf("unexpected saved size: %d", saved.SizeBytes)
	}
	if saved.StoredPath == "" {
		t.Fatalf("stored path should not be empty")
	}

	file, sizeBytes, err := store.Open(saved.StoredPath)
	if err != nil {
		t.Fatalf("open file failed: %v", err)
	}

	content, err := io.ReadAll(file)
	if err != nil {
		_ = file.Close()
		t.Fatalf("read file failed: %v", err)
	}
	if got := string(content); got != "clipbridge" {
		_ = file.Close()
		t.Fatalf("unexpected content: %q", got)
	}
	if sizeBytes != int64(len(content)) {
		_ = file.Close()
		t.Fatalf("unexpected file size: %d", sizeBytes)
	}
	if err := file.Close(); err != nil {
		t.Fatalf("close file failed: %v", err)
	}

	if err := store.Delete(saved.StoredPath); err != nil {
		t.Fatalf("delete file failed: %v", err)
	}
	if _, err := os.Stat(filepath.Join(baseDir, saved.StoredPath)); err == nil {
		t.Fatalf("deleted file should not still exist")
	}
}

func TestLocalStoreRejectsTooLargeFile(t *testing.T) {
	store := NewLocalStore(t.TempDir())

	_, err := store.Save(context.Background(), "user-1", "hello.txt", strings.NewReader("toolarge"), 3)
	if !errors.Is(err, ErrFileTooLarge) {
		t.Fatalf("expected ErrFileTooLarge, got %v", err)
	}
}

func TestLocalStoreRejectsPathTraversal(t *testing.T) {
	store := NewLocalStore(t.TempDir())

	_, _, err := store.Open("../outside.txt")
	if !errors.Is(err, ErrInvalidStoredPath) {
		t.Fatalf("expected ErrInvalidStoredPath for open, got %v", err)
	}

	err = store.Delete("../outside.txt")
	if !errors.Is(err, ErrInvalidStoredPath) {
		t.Fatalf("expected ErrInvalidStoredPath for delete, got %v", err)
	}
}
