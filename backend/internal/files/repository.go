package files

import "context"

type Repository interface {
	TouchDeviceLastSeen(ctx context.Context, userID, deviceID string) error
	GetDeviceSnapshot(ctx context.Context, userID, deviceID string) (string, error)
	CreateFile(ctx context.Context, params CreateFileParams) (Item, error)
	ListFiles(ctx context.Context, userID string, options ListOptions) ([]Item, int, int64, error)
	GetFile(ctx context.Context, userID, fileID string) (Item, error)
	RenameFile(ctx context.Context, userID, fileID, originalName string) (Item, error)
	DeleteFile(ctx context.Context, userID, fileID string) (Item, bool, error)
}
