package shares

import (
	"context"
	"time"
)

type Repository interface {
	TouchDeviceLastSeen(ctx context.Context, userID, deviceID string) error
	CreateTextShare(ctx context.Context, params CreateTextShareParams) (Item, error)
	CreateFileShare(ctx context.Context, params CreateFileShareParams) (Item, error)
	ListShares(ctx context.Context, userID string, options ListOptions) ([]Item, int, error)
	ListShareFiles(ctx context.Context, shareID string) ([]ShareFile, error)
	RevokeShare(ctx context.Context, userID, shareID string) (Item, error)
	GetShareByToken(ctx context.Context, publicToken string) (Item, error)
	OpenShareByToken(ctx context.Context, publicToken, password string, now time.Time) (Item, error)
}
