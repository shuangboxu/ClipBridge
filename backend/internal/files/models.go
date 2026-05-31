package files

import "time"

type Item struct {
	ID               string
	UserID           string
	OriginalName     string
	StoredPath       string
	ContentType      string
	SizeBytes        int64
	FileSHA256       string
	OriginDeviceID   string
	OriginDeviceName string
	CreatedAt        time.Time
}

type Summary struct {
	TotalFiles     int
	TotalBytes     int64
	MaxUploadBytes int64
}

type CreateFileParams struct {
	UserID           string
	OriginalName     string
	StoredPath       string
	ContentType      string
	SizeBytes        int64
	FileSHA256       string
	OriginDeviceID   string
	OriginDeviceName string
}

type ListOptions struct {
	Page     int
	PageSize int
}

type ListResult struct {
	Items      []Item
	Page       int
	PageSize   int
	TotalFiles int
	TotalBytes int64
	TotalPages int
	Summary    Summary
}

type DownloadResult struct {
	Item      Item
	File      FileBody
	SizeBytes int64
}

type DeleteResult struct {
	Item        Item
	DiskRemoved bool
}
