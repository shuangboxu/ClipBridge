package shares

import (
	"strings"
	"time"
)

const (
	ContentKindText = "text"
	ContentKindFile = "file"

	BurnModeNone      = "none"
	BurnModeOnce      = "once"
	BurnModeCountdown = "countdown"

	StatusAll      = "all"
	StatusActive   = "active"
	StatusExpired  = "expired"
	StatusConsumed = "consumed"
	StatusRevoked  = "revoked"
)

type EncryptionMetadata struct {
	Version    string
	KDF        string
	Iterations int
	Salt       string
	Nonce      string
	Cipher     string
}

type ShareFile struct {
	ID           string
	ShareID      string
	SortOrder    int
	OriginalName string
	StoredPath   string
	ContentType  string
	SizeBytes    int64
	SHA256       string
	Encryption   EncryptionMetadata
}

type Item struct {
	ID               string
	UserID           string
	PublicToken      string
	ContentKind      string
	TextContent      string
	TextPreview      string
	EncryptedPayload string
	IsEncrypted      bool
	PasswordHash     string
	// Encryption 主要用于文件部分的加密元数据。
	// 兼容旧数据时，纯文本分享也可能还复用这一组字段。
	Encryption       EncryptionMetadata
	TextEncryption   EncryptionMetadata
	FileOriginalName string
	FileStoredPath   string
	FileContentType  string
	FileSizeBytes    int64
	FileSHA256       string
	Files            []ShareFile
	AllowCopyContent bool
	BurnMode         string
	BurnAfterSeconds int
	ExpiresAt        *time.Time
	FirstOpenedAt    *time.Time
	BurnDeadline     *time.Time
	ConsumedAt       *time.Time
	RevokedAt        *time.Time
	OpenCount        int64
	CreatedAt        time.Time
	UpdatedAt        time.Time
	Status           string
	RemainingSeconds int64
}

type CreateTextShareInput struct {
	TextContent      string
	IsEncrypted      bool
	EncryptedPayload string
	Encryption       EncryptionMetadata
	Password         string
	AllowCopyContent bool
	ExpiresAt        *time.Time
	BurnMode         string
	BurnAfterSeconds int
}

type CreateFileShareInput struct {
	Files                []ShareFileInput
	TextContent          string
	TextEncryptedPayload string
	TextEncryption       EncryptionMetadata
	IsEncrypted          bool
	Password             string
	AllowCopyContent     bool
	ExpiresAt            *time.Time
	BurnMode             string
	BurnAfterSeconds     int
}

type ShareFileInput struct {
	UploadName   string
	OriginalName string
	ContentType  string
	Encryption   EncryptionMetadata
}

type CreateTextShareParams struct {
	UserID           string
	PublicToken      string
	TextContent      string
	TextPreview      string
	EncryptedPayload string
	IsEncrypted      bool
	PasswordHash     string
	Encryption       EncryptionMetadata
	TextEncryption   EncryptionMetadata
	AllowCopyContent bool
	ExpiresAt        *time.Time
	BurnMode         string
	BurnAfterSeconds int
}

type CreateFileShareParams struct {
	UserID               string
	PublicToken          string
	TextContent          string
	TextPreview          string
	TextEncryptedPayload string
	TextEncryption       EncryptionMetadata
	Files                []ShareFileParams
	FileOriginalName     string
	FileStoredPath       string
	FileContentType      string
	FileSizeBytes        int64
	FileSHA256           string
	IsEncrypted          bool
	PasswordHash         string
	Encryption           EncryptionMetadata
	AllowCopyContent     bool
	ExpiresAt            *time.Time
	BurnMode             string
	BurnAfterSeconds     int
}

type ShareFileParams struct {
	ID           string
	SortOrder    int
	OriginalName string
	StoredPath   string
	ContentType  string
	SizeBytes    int64
	SHA256       string
	Encryption   EncryptionMetadata
}

type ListOptions struct {
	Page     int
	PageSize int
	Status   string
	Now      time.Time
}

type ListResult struct {
	Items          []Item
	Page           int
	PageSize       int
	Total          int
	TotalPages     int
	Status         string
	MaxUploadBytes int64
}

type OpenTextResult struct {
	Item Item
}

type OpenShareResult struct {
	Item Item
}

type OpenFileResult struct {
	Item      Item
	ShareFile ShareFile
	File      FileBody
	SizeBytes int64
}

type FileBody interface {
	Close() error
	Read(p []byte) (int, error)
}

func (i Item) RequiresPassword() bool {
	return i.IsEncrypted && strings.TrimSpace(i.PasswordHash) != ""
}

func (i Item) IsImageFile() bool {
	file, ok := i.PrimaryFile()
	return ok && file.IsImageFile()
}

func (i Item) IsVideoFile() bool {
	file, ok := i.PrimaryFile()
	return ok && file.IsVideoFile()
}

func (i Item) HasTextContent() bool {
	return strings.TrimSpace(i.TextContent) != "" ||
		strings.TrimSpace(i.TextPreview) != "" ||
		strings.TrimSpace(i.EncryptedPayload) != ""
}

func (i Item) HasFileContent() bool {
	if len(i.Files) > 0 {
		return true
	}
	return strings.TrimSpace(i.FileStoredPath) != "" ||
		strings.TrimSpace(i.FileOriginalName) != ""
}

func (i Item) PrimaryFile() (ShareFile, bool) {
	if len(i.Files) > 0 {
		return i.Files[0], true
	}
	return i.LegacyFile()
}

func (i Item) LegacyFile() (ShareFile, bool) {
	if strings.TrimSpace(i.FileStoredPath) == "" && strings.TrimSpace(i.FileOriginalName) == "" {
		return ShareFile{}, false
	}

	return ShareFile{
		ID:           LegacyShareFileID(i.ID),
		ShareID:      i.ID,
		SortOrder:    0,
		OriginalName: i.FileOriginalName,
		StoredPath:   i.FileStoredPath,
		ContentType:  i.FileContentType,
		SizeBytes:    i.FileSizeBytes,
		SHA256:       i.FileSHA256,
		Encryption:   i.Encryption,
	}, true
}

func (i Item) ResolveTextEncryption() EncryptionMetadata {
	if !i.TextEncryption.IsZero() {
		return i.TextEncryption
	}
	if i.ContentKind == ContentKindText {
		return i.Encryption
	}
	return EncryptionMetadata{}
}

func (m EncryptionMetadata) IsZero() bool {
	return strings.TrimSpace(m.Version) == "" &&
		strings.TrimSpace(m.KDF) == "" &&
		m.Iterations == 0 &&
		strings.TrimSpace(m.Salt) == "" &&
		strings.TrimSpace(m.Nonce) == "" &&
		strings.TrimSpace(m.Cipher) == ""
}

func (f ShareFile) HasStoredBody() bool {
	return strings.TrimSpace(f.StoredPath) != "" && strings.TrimSpace(f.OriginalName) != ""
}

func (f ShareFile) IsImageFile() bool {
	return strings.HasPrefix(strings.ToLower(strings.TrimSpace(f.ContentType)), "image/")
}

func (f ShareFile) IsVideoFile() bool {
	return strings.HasPrefix(strings.ToLower(strings.TrimSpace(f.ContentType)), "video/")
}

func LegacyShareFileID(shareID string) string {
	return strings.TrimSpace(shareID) + ":legacy-file"
}
