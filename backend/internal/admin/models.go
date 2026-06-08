package admin

import (
	"io"
	"time"
)

const (
	DefaultMaxUserCount                       = 200
	DefaultStorageQuotaBytes            int64 = 100 * 1024 * 1024
	DefaultUploadBandwidthKbps                = 2048
	DefaultDownloadBandwidthKbps              = 4096
	DefaultMaxUserUploadBandwidthKbps         = 10240
	DefaultMaxUserDownloadBandwidthKbps       = 20480

	StatusAll      = "all"
	StatusPending  = "pending"
	StatusApproved = "approved"
	StatusRejected = "rejected"
)

type SeedSettings struct {
	MaxUserCount                 int
	DefaultStorageQuotaBytes     int64
	DefaultUploadBandwidthKbps   int
	DefaultDownloadBandwidthKbps int
	MaxUserUploadBandwidthKbps   int
	MaxUserDownloadBandwidthKbps int
	MaxUploadFileBytes           int64
	AllowRegistration            bool
}

type SystemSettings struct {
	MaxUserCount                 int
	DefaultStorageQuotaBytes     int64
	DefaultUploadBandwidthKbps   int
	DefaultDownloadBandwidthKbps int
	MaxUserUploadBandwidthKbps   int
	MaxUserDownloadBandwidthKbps int
	MaxUploadFileBytes           int64
	AllowRegistration            bool
	UpdatedAt                    time.Time
}

type Limits struct {
	MaxUserCount                 int   `json:"max_user_count"`
	DefaultStorageQuotaBytes     int64 `json:"default_storage_quota_bytes"`
	DefaultUploadBandwidthKbps   int   `json:"default_upload_bandwidth_kbps"`
	DefaultDownloadBandwidthKbps int   `json:"default_download_bandwidth_kbps"`
	MaxUserUploadBandwidthKbps   int   `json:"max_user_upload_bandwidth_kbps"`
	MaxUserDownloadBandwidthKbps int   `json:"max_user_download_bandwidth_kbps"`
	MaxUploadFileBytes           int64 `json:"max_upload_file_bytes"`
	AllowRegistration            bool  `json:"allow_registration"`
}

type AccountOverview struct {
	StorageUsedBytes int64
	StorageFreeBytes int64
	Limits           Limits
}

type UserTransferPolicy struct {
	UploadBandwidthKbps   int
	DownloadBandwidthKbps int
	MaxUploadFileBytes    int64
}

type UserSummary struct {
	ID                         string
	Username                   string
	IsAdmin                    bool
	StorageQuotaBytes          int64
	StorageUsedBytes           int64
	StorageFreeBytes           int64
	UploadBandwidthKbps        int
	DownloadBandwidthKbps      int
	HasPendingQuotaRequest     bool
	HasPendingBandwidthRequest bool
	HasPendingAdminRequest     bool
	LastActiveAt               *time.Time
	CreatedAt                  time.Time
	UpdatedAt                  time.Time
}

type QuotaRequest struct {
	ID                  string
	UserID              string
	Username            string
	RequestedQuotaBytes int64
	CurrentQuotaBytes   int64
	Reason              string
	Status              string
	ReviewedBy          string
	ReviewedByUsername  string
	ReviewNote          string
	CreatedAt           time.Time
	ReviewedAt          *time.Time
}

type BandwidthRequest struct {
	ID                    string
	UserID                string
	Username              string
	RequestedUploadKbps   int
	RequestedDownloadKbps int
	CurrentUploadKbps     int
	CurrentDownloadKbps   int
	Reason                string
	Status                string
	ReviewedBy            string
	ReviewedByUsername    string
	ReviewNote            string
	CreatedAt             time.Time
	ReviewedAt            *time.Time
}

type AdminRequest struct {
	ID                 string
	UserID             string
	Username           string
	Reason             string
	Status             string
	ReviewedBy         string
	ReviewedByUsername string
	ReviewNote         string
	CreatedAt          time.Time
	ReviewedAt         *time.Time
}

type UpdateSettingsInput struct {
	MaxUserCount                 *int
	DefaultStorageQuotaBytes     *int64
	DefaultUploadBandwidthKbps   *int
	DefaultDownloadBandwidthKbps *int
	MaxUserUploadBandwidthKbps   *int
	MaxUserDownloadBandwidthKbps *int
	MaxUploadFileBytes           *int64
	AllowRegistration            *bool
}

type UpdateUserInput struct {
	StorageQuotaBytes     *int64
	UploadBandwidthKbps   *int
	DownloadBandwidthKbps *int
	IsAdmin               *bool
}

type ApproveQuotaRequestInput struct {
	ApprovedQuotaBytes *int64
	ReviewNote         string
}

type RejectRequestInput struct {
	ReviewNote string
}

type ApproveBandwidthRequestInput struct {
	ApprovedUploadKbps   *int
	ApprovedDownloadKbps *int
	ReviewNote           string
}

type ApproveAdminRequestInput struct {
	ReviewNote string
}

type DeleteUserResult struct {
	DeletedUserID string
	StoredPaths   []string
}

type PathCleaner interface {
	Delete(storedPath string) error
}

type uploadReaderResult struct {
	Reader         io.Reader
	MaxUploadBytes int64
}
