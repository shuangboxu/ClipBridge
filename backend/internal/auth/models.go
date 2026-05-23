package auth

import "time"

type User struct {
	ID           string
	Username     string
	PasswordHash string
	CreatedAt    time.Time
	UpdatedAt    time.Time
}

type Device struct {
	ID         string
	UserID     string
	Platform   string
	DeviceName string
	LastSeenAt time.Time
	IsActive   bool
	CreatedAt  time.Time
}

type RefreshTokenRecord struct {
	ID        string
	UserID    string
	DeviceID  string
	TokenHash string
	ExpiresAt time.Time
	RevokedAt *time.Time
	CreatedAt time.Time
}

type TokenBundle struct {
	AccessToken           string
	AccessTokenExpiresAt  time.Time
	RefreshToken          string
	RefreshTokenExpiresAt time.Time
}

type Session struct {
	User   User
	Device Device
	Tokens TokenBundle
}

type AccountProfile struct {
	User            User
	CurrentDeviceID string
}
