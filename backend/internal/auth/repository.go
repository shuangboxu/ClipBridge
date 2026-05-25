package auth

import (
	"context"
	"time"
)

type Repository interface {
	CreateUser(ctx context.Context, username, passwordHash string) (User, error)
	GetUserByUsername(ctx context.Context, username string) (User, error)
	GetUserByID(ctx context.Context, userID string) (User, error)
	CreateDevice(ctx context.Context, userID, platform, deviceName string) (Device, error)
	GetActiveUserDevice(ctx context.Context, userID, deviceID string) (User, Device, error)
	TouchDeviceLastSeen(ctx context.Context, userID, deviceID string) error
	ListDevices(ctx context.Context, userID string) ([]Device, error)
	UpdateDeviceName(ctx context.Context, userID, deviceID, deviceName string) (Device, error)
	DeleteDevice(ctx context.Context, userID, deviceID string) (Device, error)
	CreateRefreshToken(ctx context.Context, userID, deviceID, tokenHash string, expiresAt time.Time) (RefreshTokenRecord, error)
	FindActiveRefreshTokenByHash(ctx context.Context, tokenHash string) (RefreshTokenRecord, error)
	RotateRefreshToken(ctx context.Context, oldTokenHash, userID, deviceID, newTokenHash string, expiresAt time.Time) (RefreshTokenRecord, error)
	RevokeRefreshTokenByHash(ctx context.Context, tokenHash string) error
	RevokeRefreshTokensByDevice(ctx context.Context, userID, deviceID string) error
}
