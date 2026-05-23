package auth

import (
	"context"
	"fmt"
	"testing"
	"time"
)

func TestRegisterCreatesUserDeviceAndTokens(t *testing.T) {
	repo := newFakeRepository()
	service := NewService(repo, NewManager("test-secret", time.Hour), 24*time.Hour)

	session, err := service.Register(context.Background(), " Alice ", "password123", "", "")
	if err != nil {
		t.Fatalf("Register returned error: %v", err)
	}

	if session.User.Username != "alice" {
		t.Fatalf("expected normalized username alice, got %q", session.User.Username)
	}
	if session.Device.Platform != "unknown" {
		t.Fatalf("expected default platform unknown, got %q", session.Device.Platform)
	}
	if session.Device.DeviceName != "unnamed-device" {
		t.Fatalf("expected default device name unnamed-device, got %q", session.Device.DeviceName)
	}
	if session.Tokens.AccessToken == "" || session.Tokens.RefreshToken == "" {
		t.Fatalf("expected token bundle to be populated")
	}
}

func TestLoginRejectsWrongPassword(t *testing.T) {
	repo := newFakeRepository()
	service := NewService(repo, NewManager("test-secret", time.Hour), 24*time.Hour)

	passwordHash, err := HashPassword("password123")
	if err != nil {
		t.Fatalf("HashPassword returned error: %v", err)
	}

	_, err = repo.CreateUser(context.Background(), "alice", passwordHash)
	if err != nil {
		t.Fatalf("CreateUser returned error: %v", err)
	}

	_, err = service.Login(context.Background(), "alice", "wrong-password", "android", "Pixel")
	if err != ErrInvalidCredentials {
		t.Fatalf("expected ErrInvalidCredentials, got %v", err)
	}
}

func TestRefreshRotatesToken(t *testing.T) {
	repo := newFakeRepository()
	service := NewService(repo, NewManager("test-secret", time.Hour), 24*time.Hour)

	session, err := service.Register(context.Background(), "alice", "password123", "android", "Pixel")
	if err != nil {
		t.Fatalf("Register returned error: %v", err)
	}

	oldRefreshHash := HashToken(session.Tokens.RefreshToken)
	refreshedTokens, err := service.Refresh(context.Background(), session.Tokens.RefreshToken)
	if err != nil {
		t.Fatalf("Refresh returned error: %v", err)
	}

	if refreshedTokens.RefreshToken == session.Tokens.RefreshToken {
		t.Fatalf("expected refresh token to rotate")
	}

	oldRecord := repo.refreshTokens[oldRefreshHash]
	if oldRecord.RevokedAt == nil {
		t.Fatalf("expected old refresh token to be revoked")
	}

	if repo.touchCount == 0 {
		t.Fatalf("expected refresh flow to touch current device last_seen_at")
	}
}

func TestAuthenticateAccessTokenRejectsMissingDevice(t *testing.T) {
	repo := newFakeRepository()
	service := NewService(repo, NewManager("test-secret", time.Hour), 24*time.Hour)

	accessToken, _, err := service.tokenManager.GenerateAccessToken("user-1", "device-1")
	if err != nil {
		t.Fatalf("GenerateAccessToken returned error: %v", err)
	}

	_, _, err = service.AuthenticateAccessToken(context.Background(), accessToken)
	if err != ErrUnauthorized {
		t.Fatalf("expected ErrUnauthorized, got %v", err)
	}
}

func TestUpdateDeviceNameUpdatesStoredDevice(t *testing.T) {
	repo := newFakeRepository()
	service := NewService(repo, NewManager("test-secret", time.Hour), 24*time.Hour)

	session, err := service.Register(context.Background(), "alice", "password123", "web", "Chrome")
	if err != nil {
		t.Fatalf("Register returned error: %v", err)
	}

	device, err := service.UpdateDeviceName(
		context.Background(),
		session.User.ID,
		session.Device.ID,
		session.Device.ID,
		"Office Chrome",
	)
	if err != nil {
		t.Fatalf("UpdateDeviceName returned error: %v", err)
	}

	if device.DeviceName != "Office Chrome" {
		t.Fatalf("expected updated device name, got %q", device.DeviceName)
	}
	if repo.devices[session.Device.ID].DeviceName != "Office Chrome" {
		t.Fatalf("expected repository device name to be updated")
	}
}

func TestForceDeviceOfflineDisablesDeviceAndRevokesRefreshTokens(t *testing.T) {
	repo := newFakeRepository()
	service := NewService(repo, NewManager("test-secret", time.Hour), 24*time.Hour)

	session, err := service.Register(context.Background(), "alice", "password123", "web", "Chrome")
	if err != nil {
		t.Fatalf("Register returned error: %v", err)
	}

	device, err := service.ForceDeviceOffline(
		context.Background(),
		session.User.ID,
		session.Device.ID,
		session.Device.ID,
	)
	if err != nil {
		t.Fatalf("ForceDeviceOffline returned error: %v", err)
	}

	if device.IsActive {
		t.Fatalf("expected device to become inactive")
	}
	if repo.devices[session.Device.ID].IsActive {
		t.Fatalf("expected repository device to become inactive")
	}

	record := repo.refreshTokens[HashToken(session.Tokens.RefreshToken)]
	if record.RevokedAt == nil {
		t.Fatalf("expected refresh token to be revoked when device is forced offline")
	}
}

type fakeRepository struct {
	userSeq       int
	deviceSeq     int
	refreshSeq    int
	touchCount    int
	users         map[string]User
	usersByName   map[string]string
	devices       map[string]Device
	refreshTokens map[string]RefreshTokenRecord
}

func newFakeRepository() *fakeRepository {
	return &fakeRepository{
		users:         make(map[string]User),
		usersByName:   make(map[string]string),
		devices:       make(map[string]Device),
		refreshTokens: make(map[string]RefreshTokenRecord),
	}
}

func (r *fakeRepository) CreateUser(_ context.Context, username, passwordHash string) (User, error) {
	if _, exists := r.usersByName[username]; exists {
		return User{}, ErrConflict
	}

	r.userSeq++
	now := time.Now()
	user := User{
		ID:           makeFakeID("user", r.userSeq),
		Username:     username,
		PasswordHash: passwordHash,
		CreatedAt:    now,
		UpdatedAt:    now,
	}
	r.users[user.ID] = user
	r.usersByName[username] = user.ID
	return user, nil
}

func (r *fakeRepository) GetUserByUsername(_ context.Context, username string) (User, error) {
	userID, ok := r.usersByName[username]
	if !ok {
		return User{}, ErrNotFound
	}
	return r.users[userID], nil
}

func (r *fakeRepository) GetUserByID(_ context.Context, userID string) (User, error) {
	user, ok := r.users[userID]
	if !ok {
		return User{}, ErrNotFound
	}
	return user, nil
}

func (r *fakeRepository) CreateDevice(_ context.Context, userID, platform, deviceName string) (Device, error) {
	r.deviceSeq++
	now := time.Now()
	device := Device{
		ID:         makeFakeID("device", r.deviceSeq),
		UserID:     userID,
		Platform:   platform,
		DeviceName: deviceName,
		LastSeenAt: now,
		IsActive:   true,
		CreatedAt:  now,
	}
	r.devices[device.ID] = device
	return device, nil
}

func (r *fakeRepository) GetActiveUserDevice(_ context.Context, userID, deviceID string) (User, Device, error) {
	user, userOK := r.users[userID]
	device, deviceOK := r.devices[deviceID]
	if !userOK || !deviceOK || device.UserID != userID || !device.IsActive {
		return User{}, Device{}, ErrNotFound
	}
	return user, device, nil
}

func (r *fakeRepository) TouchDeviceLastSeen(_ context.Context, userID, deviceID string) error {
	device, ok := r.devices[deviceID]
	if !ok || device.UserID != userID {
		return ErrNotFound
	}

	r.touchCount++
	device.LastSeenAt = time.Now()
	r.devices[deviceID] = device
	return nil
}

func (r *fakeRepository) ListDevices(_ context.Context, userID string) ([]Device, error) {
	devices := make([]Device, 0)
	for _, device := range r.devices {
		if device.UserID == userID {
			devices = append(devices, device)
		}
	}
	return devices, nil
}

func (r *fakeRepository) UpdateDeviceName(_ context.Context, userID, deviceID, deviceName string) (Device, error) {
	device, ok := r.devices[deviceID]
	if !ok || device.UserID != userID {
		return Device{}, ErrNotFound
	}

	device.DeviceName = deviceName
	r.devices[deviceID] = device
	return device, nil
}

func (r *fakeRepository) DeactivateDevice(_ context.Context, userID, deviceID string) (Device, error) {
	device, ok := r.devices[deviceID]
	if !ok || device.UserID != userID {
		return Device{}, ErrNotFound
	}

	device.IsActive = false
	r.devices[deviceID] = device

	now := time.Now()
	for tokenHash, record := range r.refreshTokens {
		if record.UserID == userID && record.DeviceID == deviceID && record.RevokedAt == nil {
			record.RevokedAt = &now
			r.refreshTokens[tokenHash] = record
		}
	}

	return device, nil
}

func (r *fakeRepository) CreateRefreshToken(_ context.Context, userID, deviceID, tokenHash string, expiresAt time.Time) (RefreshTokenRecord, error) {
	r.refreshSeq++
	record := RefreshTokenRecord{
		ID:        makeFakeID("refresh", r.refreshSeq),
		UserID:    userID,
		DeviceID:  deviceID,
		TokenHash: tokenHash,
		ExpiresAt: expiresAt,
		CreatedAt: time.Now(),
	}
	r.refreshTokens[tokenHash] = record
	return record, nil
}

func (r *fakeRepository) FindActiveRefreshTokenByHash(_ context.Context, tokenHash string) (RefreshTokenRecord, error) {
	record, ok := r.refreshTokens[tokenHash]
	if !ok || record.RevokedAt != nil || !record.ExpiresAt.After(time.Now()) {
		return RefreshTokenRecord{}, ErrNotFound
	}
	return record, nil
}

func (r *fakeRepository) RotateRefreshToken(_ context.Context, oldTokenHash, userID, deviceID, newTokenHash string, expiresAt time.Time) (RefreshTokenRecord, error) {
	record, ok := r.refreshTokens[oldTokenHash]
	if !ok || record.RevokedAt != nil || !record.ExpiresAt.After(time.Now()) {
		return RefreshTokenRecord{}, ErrNotFound
	}

	now := time.Now()
	record.RevokedAt = &now
	r.refreshTokens[oldTokenHash] = record
	return r.CreateRefreshToken(context.Background(), userID, deviceID, newTokenHash, expiresAt)
}

func (r *fakeRepository) RevokeRefreshTokenByHash(_ context.Context, tokenHash string) error {
	record, ok := r.refreshTokens[tokenHash]
	if !ok {
		return nil
	}

	now := time.Now()
	record.RevokedAt = &now
	r.refreshTokens[tokenHash] = record
	return nil
}

func (r *fakeRepository) RevokeRefreshTokensByDevice(_ context.Context, userID, deviceID string) error {
	now := time.Now()
	for tokenHash, record := range r.refreshTokens {
		if record.UserID == userID && record.DeviceID == deviceID && record.RevokedAt == nil {
			record.RevokedAt = &now
			r.refreshTokens[tokenHash] = record
		}
	}
	return nil
}

func makeFakeID(prefix string, seq int) string {
	return fmt.Sprintf("%s-%d", prefix, seq)
}
