package clipboard

import "context"

type Repository interface {
	TouchDeviceLastSeen(ctx context.Context, userID, deviceID string) error
	CreateTextItem(ctx context.Context, params CreateTextItemParams) (CreateTextItemResult, error)
	ListHistory(ctx context.Context, userID string, options ListHistoryOptions) ([]Item, bool, error)
	PullItems(ctx context.Context, userID string, sinceSeq int64, limit int) ([]Item, bool, error)
	AckDevice(ctx context.Context, userID, deviceID string, seq int64) (int64, error)
	GetSyncSnapshot(ctx context.Context, userID, deviceID string) (SyncSnapshot, error)
}
