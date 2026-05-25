package auth

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"time"
)

type Service struct {
	repo            Repository
	tokenManager    *Manager
	refreshTokenTTL time.Duration
}

func NewService(repo Repository, tokenManager *Manager, refreshTokenTTL time.Duration) *Service {
	return &Service{
		repo:            repo,
		tokenManager:    tokenManager,
		refreshTokenTTL: refreshTokenTTL,
	}
}

func (s *Service) Register(ctx context.Context, username, password, platform, deviceName string) (Session, error) {
	if s == nil || s.repo == nil || s.tokenManager == nil {
		return Session{}, fmt.Errorf("auth service is not ready")
	}

	username = normalizeUsername(username)
	if err := validateUsername(username); err != nil {
		return Session{}, err
	}
	if err := validatePassword(password); err != nil {
		return Session{}, err
	}

	passwordHash, err := HashPassword(password)
	if err != nil {
		return Session{}, err
	}

	user, err := s.repo.CreateUser(ctx, username, passwordHash)
	if err != nil {
		return Session{}, err
	}

	return s.createSession(ctx, user, platform, deviceName)
}

func (s *Service) Login(ctx context.Context, username, password, platform, deviceName string) (Session, error) {
	if s == nil || s.repo == nil || s.tokenManager == nil {
		return Session{}, fmt.Errorf("auth service is not ready")
	}

	username = normalizeUsername(username)
	if err := validateUsername(username); err != nil {
		return Session{}, err
	}
	if strings.TrimSpace(password) == "" {
		return Session{}, fmt.Errorf("password is required")
	}

	user, err := s.repo.GetUserByUsername(ctx, username)
	if err != nil {
		if errors.Is(err, ErrNotFound) {
			return Session{}, ErrInvalidCredentials
		}
		return Session{}, err
	}

	if err := CheckPassword(user.PasswordHash, password); err != nil {
		return Session{}, err
	}

	return s.createSession(ctx, user, platform, deviceName)
}

func (s *Service) Refresh(ctx context.Context, refreshToken string) (TokenBundle, error) {
	if s == nil || s.repo == nil || s.tokenManager == nil {
		return TokenBundle{}, fmt.Errorf("auth service is not ready")
	}

	refreshToken = strings.TrimSpace(refreshToken)
	if refreshToken == "" {
		return TokenBundle{}, fmt.Errorf("refresh_token is required")
	}

	tokenHash := HashToken(refreshToken)
	record, err := s.repo.FindActiveRefreshTokenByHash(ctx, tokenHash)
	if err != nil {
		if errors.Is(err, ErrNotFound) {
			return TokenBundle{}, ErrInvalidRefreshToken
		}
		return TokenBundle{}, err
	}

	if _, _, err := s.repo.GetActiveUserDevice(ctx, record.UserID, record.DeviceID); err != nil {
		if errors.Is(err, ErrNotFound) {
			return TokenBundle{}, ErrInvalidRefreshToken
		}
		return TokenBundle{}, err
	}

	tokens, err := s.buildTokenBundle(record.UserID, record.DeviceID)
	if err != nil {
		return TokenBundle{}, err
	}

	// Refresh Token 使用“轮换”策略：
	// 每刷新一次，就立刻废掉旧 token，并发给客户端一枚新的 token。
	// 这样即使旧 token 泄露，可被重放的时间窗口也会更短。
	if _, err := s.repo.RotateRefreshToken(
		ctx,
		tokenHash,
		record.UserID,
		record.DeviceID,
		HashToken(tokens.RefreshToken),
		tokens.RefreshTokenExpiresAt,
	); err != nil {
		if errors.Is(err, ErrNotFound) {
			return TokenBundle{}, ErrInvalidRefreshToken
		}
		return TokenBundle{}, err
	}

	if err := s.repo.TouchDeviceLastSeen(ctx, record.UserID, record.DeviceID); err != nil {
		return TokenBundle{}, err
	}

	return tokens, nil
}

func (s *Service) Logout(ctx context.Context, userID, deviceID, refreshToken string) error {
	if s == nil || s.repo == nil {
		return fmt.Errorf("auth service is not ready")
	}

	refreshToken = strings.TrimSpace(refreshToken)
	if refreshToken != "" {
		return s.repo.RevokeRefreshTokenByHash(ctx, HashToken(refreshToken))
	}
	return s.repo.RevokeRefreshTokensByDevice(ctx, userID, deviceID)
}

func (s *Service) AuthenticateAccessToken(ctx context.Context, accessToken string) (User, Device, error) {
	if s == nil || s.repo == nil || s.tokenManager == nil {
		return User{}, Device{}, fmt.Errorf("auth service is not ready")
	}

	claims, err := s.tokenManager.ParseAccessToken(accessToken)
	if err != nil {
		return User{}, Device{}, ErrUnauthorized
	}

	user, device, err := s.repo.GetActiveUserDevice(ctx, claims.UserID, claims.DeviceID)
	if err != nil {
		if errors.Is(err, ErrNotFound) {
			return User{}, Device{}, ErrUnauthorized
		}
		return User{}, Device{}, err
	}
	return user, device, nil
}

func (s *Service) GetCurrentAccount(ctx context.Context, userID, deviceID string) (AccountProfile, error) {
	if s == nil || s.repo == nil {
		return AccountProfile{}, fmt.Errorf("auth service is not ready")
	}

	if err := s.repo.TouchDeviceLastSeen(ctx, userID, deviceID); err != nil {
		return AccountProfile{}, err
	}

	user, err := s.repo.GetUserByID(ctx, userID)
	if err != nil {
		return AccountProfile{}, err
	}

	return AccountProfile{
		User:            user,
		CurrentDeviceID: deviceID,
	}, nil
}

func (s *Service) ListDevices(ctx context.Context, userID, currentDeviceID string) ([]Device, error) {
	if s == nil || s.repo == nil {
		return nil, fmt.Errorf("auth service is not ready")
	}

	if err := s.repo.TouchDeviceLastSeen(ctx, userID, currentDeviceID); err != nil {
		return nil, err
	}

	return s.repo.ListDevices(ctx, userID)
}

func (s *Service) UpdateDeviceName(ctx context.Context, userID, currentDeviceID, targetDeviceID, deviceName string) (Device, error) {
	if s == nil || s.repo == nil {
		return Device{}, fmt.Errorf("auth service is not ready")
	}

	if err := validateDeviceID(targetDeviceID); err != nil {
		return Device{}, err
	}
	if err := validateDeviceNameForUpdate(deviceName); err != nil {
		return Device{}, err
	}
	if err := s.repo.TouchDeviceLastSeen(ctx, userID, currentDeviceID); err != nil {
		return Device{}, err
	}

	return s.repo.UpdateDeviceName(ctx, userID, targetDeviceID, strings.TrimSpace(deviceName))
}

func (s *Service) ForceDeviceOffline(ctx context.Context, userID, currentDeviceID, targetDeviceID string) (Device, error) {
	if s == nil || s.repo == nil {
		return Device{}, fmt.Errorf("auth service is not ready")
	}

	if err := validateDeviceID(targetDeviceID); err != nil {
		return Device{}, err
	}
	if err := s.repo.TouchDeviceLastSeen(ctx, userID, currentDeviceID); err != nil {
		return Device{}, err
	}

	// 这里直接删除设备记录，而不是只做软下线。
	// 这样设备列表不会再保留这条记录，后续 refresh token 也会一起失效。
	return s.repo.DeleteDevice(ctx, userID, targetDeviceID)
}

func (s *Service) createSession(ctx context.Context, user User, platform, deviceName string) (Session, error) {
	// 注册和登录都会走到这里，保证“创建设备 + 颁发 token”的流程保持一致。
	device, err := s.repo.CreateDevice(ctx, user.ID, normalizePlatform(platform), normalizeDeviceName(deviceName))
	if err != nil {
		return Session{}, err
	}

	tokens, err := s.buildTokenBundle(user.ID, device.ID)
	if err != nil {
		return Session{}, err
	}

	if _, err := s.repo.CreateRefreshToken(
		ctx,
		user.ID,
		device.ID,
		HashToken(tokens.RefreshToken),
		tokens.RefreshTokenExpiresAt,
	); err != nil {
		return Session{}, err
	}

	return Session{
		User:   user,
		Device: device,
		Tokens: tokens,
	}, nil
}

func (s *Service) buildTokenBundle(userID, deviceID string) (TokenBundle, error) {
	accessToken, accessExpiresAt, err := s.tokenManager.GenerateAccessToken(userID, deviceID)
	if err != nil {
		return TokenBundle{}, err
	}

	refreshToken, err := GenerateRefreshToken(s.refreshTokenTTL)
	if err != nil {
		return TokenBundle{}, err
	}

	return TokenBundle{
		AccessToken:           accessToken,
		AccessTokenExpiresAt:  accessExpiresAt,
		RefreshToken:          refreshToken.Raw,
		RefreshTokenExpiresAt: refreshToken.ExpiresAt,
	}, nil
}

func normalizeUsername(username string) string {
	// 用户名统一转成小写，避免 Alice 和 alice 被当成两个账号。
	return strings.ToLower(strings.TrimSpace(username))
}

func normalizePlatform(platform string) string {
	platform = strings.ToLower(strings.TrimSpace(platform))
	if platform == "" {
		return "unknown"
	}
	if len(platform) > 32 {
		return platform[:32]
	}
	return platform
}

func normalizeDeviceName(deviceName string) string {
	deviceName = strings.TrimSpace(deviceName)
	if deviceName == "" {
		return "unnamed-device"
	}
	if len(deviceName) > 128 {
		return deviceName[:128]
	}
	return deviceName
}

func validateUsername(username string) error {
	switch {
	case username == "":
		return fmt.Errorf("username is required")
	case len(username) < 3:
		return fmt.Errorf("username must be at least 3 characters")
	case len(username) > 64:
		return fmt.Errorf("username must be at most 64 characters")
	default:
		return nil
	}
}

func validatePassword(password string) error {
	switch {
	case len(password) < 8:
		return fmt.Errorf("password must be at least 8 characters")
	case len(password) > 128:
		return fmt.Errorf("password must be at most 128 characters")
	default:
		return nil
	}
}

func validateDeviceID(deviceID string) error {
	if strings.TrimSpace(deviceID) == "" {
		return fmt.Errorf("device_id is required")
	}
	return nil
}

func validateDeviceNameForUpdate(deviceName string) error {
	deviceName = strings.TrimSpace(deviceName)
	switch {
	case deviceName == "":
		return fmt.Errorf("device_name is required")
	case len(deviceName) > 128:
		return fmt.Errorf("device_name must be at most 128 characters")
	default:
		return nil
	}
}
