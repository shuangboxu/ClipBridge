package clipboard

import "context"

type Repository interface {
	TouchDeviceLastSeen(ctx context.Context, userID, deviceID string) error
	CreateTextItem(ctx context.Context, params CreateTextItemParams) (CreateTextItemResult, error)
	ListHistory(ctx context.Context, userID string, options ListHistoryOptions) ([]Item, bool, error)
	PullItems(ctx context.Context, userID string, sinceSeq int64, limit int) ([]Item, bool, error)
	AckDevice(ctx context.Context, userID, deviceID string, seq int64) (int64, error)
	GetSyncSnapshot(ctx context.Context, userID, deviceID string) (SyncSnapshot, error)
	DeleteHistoryItem(ctx context.Context, userID, itemID string) (Item, bool, error)
	ClearHistory(ctx context.Context, userID string) (int, error)
	CleanupHistoryOlderThan(ctx context.Context, userID string, days int) (int, error)
	ApplyHistoryLimit(ctx context.Context, userID string, limit int) (int, error)
	GetHistorySettings(ctx context.Context, userID string) (HistorySettings, error)
	UpdateHistorySettings(ctx context.Context, userID string, input HistorySettingsInput) (HistorySettings, error)
}
