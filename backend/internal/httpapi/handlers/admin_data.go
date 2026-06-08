package handlers

import (
	"time"

	"clipbridge/backend/internal/admin"
)

type limitsData struct {
	MaxUserCount                 int   `json:"max_user_count"`
	DefaultStorageQuotaBytes     int64 `json:"default_storage_quota_bytes"`
	DefaultUploadBandwidthKbps   int   `json:"default_upload_bandwidth_kbps"`
	DefaultDownloadBandwidthKbps int   `json:"default_download_bandwidth_kbps"`
	MaxUserUploadBandwidthKbps   int   `json:"max_user_upload_bandwidth_kbps"`
	MaxUserDownloadBandwidthKbps int   `json:"max_user_download_bandwidth_kbps"`
	MaxUploadFileBytes           int64 `json:"max_upload_file_bytes"`
	AllowRegistration            bool  `json:"allow_registration"`
}

type adminSettingsData struct {
	MaxUserCount                 int    `json:"max_user_count"`
	DefaultStorageQuotaBytes     int64  `json:"default_storage_quota_bytes"`
	DefaultUploadBandwidthKbps   int    `json:"default_upload_bandwidth_kbps"`
	DefaultDownloadBandwidthKbps int    `json:"default_download_bandwidth_kbps"`
	MaxUserUploadBandwidthKbps   int    `json:"max_user_upload_bandwidth_kbps"`
	MaxUserDownloadBandwidthKbps int    `json:"max_user_download_bandwidth_kbps"`
	MaxUploadFileBytes           int64  `json:"max_upload_file_bytes"`
	AllowRegistration            bool   `json:"allow_registration"`
	UpdatedAt                    string `json:"updated_at"`
}

type adminUserSummaryData struct {
	ID                         string `json:"id"`
	Username                   string `json:"username"`
	IsAdmin                    bool   `json:"is_admin"`
	StorageQuotaBytes          int64  `json:"storage_quota_bytes"`
	StorageUsedBytes           int64  `json:"storage_used_bytes"`
	StorageFreeBytes           int64  `json:"storage_free_bytes"`
	UploadBandwidthKbps        int    `json:"upload_bandwidth_kbps"`
	DownloadBandwidthKbps      int    `json:"download_bandwidth_kbps"`
	HasPendingQuotaRequest     bool   `json:"has_pending_quota_request"`
	HasPendingBandwidthRequest bool   `json:"has_pending_bandwidth_request"`
	HasPendingAdminRequest     bool   `json:"has_pending_admin_request"`
	LastActiveAt               string `json:"last_active_at"`
	CreatedAt                  string `json:"created_at"`
	UpdatedAt                  string `json:"updated_at"`
}

type quotaRequestData struct {
	ID                  string `json:"id"`
	UserID              string `json:"user_id"`
	Username            string `json:"username"`
	RequestedQuotaBytes int64  `json:"requested_quota_bytes"`
	CurrentQuotaBytes   int64  `json:"current_quota_bytes"`
	Reason              string `json:"reason"`
	Status              string `json:"status"`
	ReviewedBy          string `json:"reviewed_by"`
	ReviewedByUsername  string `json:"reviewed_by_username"`
	ReviewNote          string `json:"review_note"`
	CreatedAt           string `json:"created_at"`
	ReviewedAt          string `json:"reviewed_at"`
}

type bandwidthRequestData struct {
	ID                    string `json:"id"`
	UserID                string `json:"user_id"`
	Username              string `json:"username"`
	RequestedUploadKbps   int    `json:"requested_upload_kbps"`
	RequestedDownloadKbps int    `json:"requested_download_kbps"`
	CurrentUploadKbps     int    `json:"current_upload_kbps"`
	CurrentDownloadKbps   int    `json:"current_download_kbps"`
	Reason                string `json:"reason"`
	Status                string `json:"status"`
	ReviewedBy            string `json:"reviewed_by"`
	ReviewedByUsername    string `json:"reviewed_by_username"`
	ReviewNote            string `json:"review_note"`
	CreatedAt             string `json:"created_at"`
	ReviewedAt            string `json:"reviewed_at"`
}

type adminRequestData struct {
	ID                 string `json:"id"`
	UserID             string `json:"user_id"`
	Username           string `json:"username"`
	Reason             string `json:"reason"`
	Status             string `json:"status"`
	ReviewedBy         string `json:"reviewed_by"`
	ReviewedByUsername string `json:"reviewed_by_username"`
	ReviewNote         string `json:"review_note"`
	CreatedAt          string `json:"created_at"`
	ReviewedAt         string `json:"reviewed_at"`
}

func buildLimitsData(value admin.Limits) limitsData {
	return limitsData{
		MaxUserCount:                 value.MaxUserCount,
		DefaultStorageQuotaBytes:     value.DefaultStorageQuotaBytes,
		DefaultUploadBandwidthKbps:   value.DefaultUploadBandwidthKbps,
		DefaultDownloadBandwidthKbps: value.DefaultDownloadBandwidthKbps,
		MaxUserUploadBandwidthKbps:   value.MaxUserUploadBandwidthKbps,
		MaxUserDownloadBandwidthKbps: value.MaxUserDownloadBandwidthKbps,
		MaxUploadFileBytes:           value.MaxUploadFileBytes,
		AllowRegistration:            value.AllowRegistration,
	}
}

func buildAdminSettingsData(value admin.SystemSettings) adminSettingsData {
	return adminSettingsData{
		MaxUserCount:                 value.MaxUserCount,
		DefaultStorageQuotaBytes:     value.DefaultStorageQuotaBytes,
		DefaultUploadBandwidthKbps:   value.DefaultUploadBandwidthKbps,
		DefaultDownloadBandwidthKbps: value.DefaultDownloadBandwidthKbps,
		MaxUserUploadBandwidthKbps:   value.MaxUserUploadBandwidthKbps,
		MaxUserDownloadBandwidthKbps: value.MaxUserDownloadBandwidthKbps,
		MaxUploadFileBytes:           value.MaxUploadFileBytes,
		AllowRegistration:            value.AllowRegistration,
		UpdatedAt:                    formatTime(value.UpdatedAt),
	}
}

func buildAdminUserSummaryData(value admin.UserSummary) adminUserSummaryData {
	return adminUserSummaryData{
		ID:                         value.ID,
		Username:                   value.Username,
		IsAdmin:                    value.IsAdmin,
		StorageQuotaBytes:          value.StorageQuotaBytes,
		StorageUsedBytes:           value.StorageUsedBytes,
		StorageFreeBytes:           value.StorageFreeBytes,
		UploadBandwidthKbps:        value.UploadBandwidthKbps,
		DownloadBandwidthKbps:      value.DownloadBandwidthKbps,
		HasPendingQuotaRequest:     value.HasPendingQuotaRequest,
		HasPendingBandwidthRequest: value.HasPendingBandwidthRequest,
		HasPendingAdminRequest:     value.HasPendingAdminRequest,
		LastActiveAt:               formatOptionalTime(value.LastActiveAt),
		CreatedAt:                  formatTime(value.CreatedAt),
		UpdatedAt:                  formatTime(value.UpdatedAt),
	}
}

func buildQuotaRequestData(value admin.QuotaRequest) quotaRequestData {
	return quotaRequestData{
		ID:                  value.ID,
		UserID:              value.UserID,
		Username:            value.Username,
		RequestedQuotaBytes: value.RequestedQuotaBytes,
		CurrentQuotaBytes:   value.CurrentQuotaBytes,
		Reason:              value.Reason,
		Status:              value.Status,
		ReviewedBy:          value.ReviewedBy,
		ReviewedByUsername:  value.ReviewedByUsername,
		ReviewNote:          value.ReviewNote,
		CreatedAt:           formatTime(value.CreatedAt),
		ReviewedAt:          formatOptionalTime(value.ReviewedAt),
	}
}

func buildBandwidthRequestData(value admin.BandwidthRequest) bandwidthRequestData {
	return bandwidthRequestData{
		ID:                    value.ID,
		UserID:                value.UserID,
		Username:              value.Username,
		RequestedUploadKbps:   value.RequestedUploadKbps,
		RequestedDownloadKbps: value.RequestedDownloadKbps,
		CurrentUploadKbps:     value.CurrentUploadKbps,
		CurrentDownloadKbps:   value.CurrentDownloadKbps,
		Reason:                value.Reason,
		Status:                value.Status,
		ReviewedBy:            value.ReviewedBy,
		ReviewedByUsername:    value.ReviewedByUsername,
		ReviewNote:            value.ReviewNote,
		CreatedAt:             formatTime(value.CreatedAt),
		ReviewedAt:            formatOptionalTime(value.ReviewedAt),
	}
}

func buildAdminRequestData(value admin.AdminRequest) adminRequestData {
	return adminRequestData{
		ID:                 value.ID,
		UserID:             value.UserID,
		Username:           value.Username,
		Reason:             value.Reason,
		Status:             value.Status,
		ReviewedBy:         value.ReviewedBy,
		ReviewedByUsername: value.ReviewedByUsername,
		ReviewNote:         value.ReviewNote,
		CreatedAt:          formatTime(value.CreatedAt),
		ReviewedAt:         formatOptionalTime(value.ReviewedAt),
	}
}

func formatOptionalTime(value *time.Time) string {
	if value == nil {
		return ""
	}
	return formatTime(*value)
}
