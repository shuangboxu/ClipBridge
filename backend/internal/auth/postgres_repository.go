package auth

import (
	"context"
	"errors"
	"fmt"
	"time"

	"clipbridge/backend/internal/id"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

type PostgresRepository struct {
	db *pgxpool.Pool
}

func NewPostgresRepository(db *pgxpool.Pool) *PostgresRepository {
	return &PostgresRepository{db: db}
}

func (r *PostgresRepository) CreateUser(ctx context.Context, username, passwordHash string) (User, error) {
	userID, err := id.NewUUID()
	if err != nil {
		return User{}, err
	}

	var user User
	err = r.db.QueryRow(ctx, `
		INSERT INTO users(id, username, password_hash)
		VALUES ($1, $2, $3)
		RETURNING id, username, password_hash, created_at, updated_at
	`, userID, username, passwordHash).Scan(
		&user.ID,
		&user.Username,
		&user.PasswordHash,
		&user.CreatedAt,
		&user.UpdatedAt,
	)
	if err != nil {
		if isUniqueViolation(err) {
			return User{}, ErrConflict
		}
		return User{}, fmt.Errorf("create user failed: %w", err)
	}
	return user, nil
}

func (r *PostgresRepository) GetUserByUsername(ctx context.Context, username string) (User, error) {
	var user User
	err := r.db.QueryRow(ctx, `
		SELECT id, username, password_hash, created_at, updated_at
		FROM users
		WHERE username = $1
	`, username).Scan(
		&user.ID,
		&user.Username,
		&user.PasswordHash,
		&user.CreatedAt,
		&user.UpdatedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return User{}, ErrNotFound
	}
	if err != nil {
		return User{}, fmt.Errorf("get user by username failed: %w", err)
	}
	return user, nil
}

func (r *PostgresRepository) GetUserByID(ctx context.Context, userID string) (User, error) {
	var user User
	err := r.db.QueryRow(ctx, `
		SELECT id, username, password_hash, created_at, updated_at
		FROM users
		WHERE id = $1
	`, userID).Scan(
		&user.ID,
		&user.Username,
		&user.PasswordHash,
		&user.CreatedAt,
		&user.UpdatedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return User{}, ErrNotFound
	}
	if err != nil {
		return User{}, fmt.Errorf("get user by id failed: %w", err)
	}
	return user, nil
}

func (r *PostgresRepository) UpdateUserPassword(ctx context.Context, userID, passwordHash string) (User, error) {
	var user User
	err := r.db.QueryRow(ctx, `
		UPDATE users
		SET password_hash = $2, updated_at = now()
		WHERE id = $1
		RETURNING id, username, password_hash, created_at, updated_at
	`, userID, passwordHash).Scan(
		&user.ID,
		&user.Username,
		&user.PasswordHash,
		&user.CreatedAt,
		&user.UpdatedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return User{}, ErrNotFound
	}
	if err != nil {
		return User{}, fmt.Errorf("update user password failed: %w", err)
	}
	return user, nil
}

func (r *PostgresRepository) CreateDevice(ctx context.Context, userID, platform, deviceName string) (Device, error) {
	deviceID, err := id.NewUUID()
	if err != nil {
		return Device{}, err
	}

	var device Device
	err = r.db.QueryRow(ctx, `
		INSERT INTO devices(id, user_id, platform, device_name)
		VALUES ($1, $2, $3, $4)
		RETURNING id, user_id, platform, device_name, last_seen_at, is_active, created_at
	`, deviceID, userID, platform, deviceName).Scan(
		&device.ID,
		&device.UserID,
		&device.Platform,
		&device.DeviceName,
		&device.LastSeenAt,
		&device.IsActive,
		&device.CreatedAt,
	)
	if err != nil {
		return Device{}, fmt.Errorf("create device failed: %w", err)
	}
	return device, nil
}

func (r *PostgresRepository) GetActiveUserDevice(ctx context.Context, userID, deviceID string) (User, Device, error) {
	var user User
	var device Device
	err := r.db.QueryRow(ctx, `
		SELECT
			u.id, u.username, u.password_hash, u.created_at, u.updated_at,
			d.id, d.user_id, d.platform, d.device_name, d.last_seen_at, d.is_active, d.created_at
		FROM users u
		INNER JOIN devices d ON d.user_id = u.id
		WHERE u.id = $1 AND d.id = $2 AND d.is_active = true
	`, userID, deviceID).Scan(
		&user.ID,
		&user.Username,
		&user.PasswordHash,
		&user.CreatedAt,
		&user.UpdatedAt,
		&device.ID,
		&device.UserID,
		&device.Platform,
		&device.DeviceName,
		&device.LastSeenAt,
		&device.IsActive,
		&device.CreatedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return User{}, Device{}, ErrNotFound
	}
	if err != nil {
		return User{}, Device{}, fmt.Errorf("get active user device failed: %w", err)
	}
	return user, device, nil
}

func (r *PostgresRepository) TouchDeviceLastSeen(ctx context.Context, userID, deviceID string) error {
	_, err := r.db.Exec(ctx, `
		UPDATE devices
		SET last_seen_at = now()
		WHERE user_id = $1 AND id = $2
	`, userID, deviceID)
	if err != nil {
		return fmt.Errorf("touch device last_seen_at failed: %w", err)
	}
	return nil
}

func (r *PostgresRepository) ListDevices(ctx context.Context, userID string) ([]Device, error) {
	rows, err := r.db.Query(ctx, `
		SELECT id, user_id, platform, device_name, last_seen_at, is_active, created_at
		FROM devices
		WHERE user_id = $1 AND is_active = true
		ORDER BY last_seen_at DESC, created_at DESC
	`, userID)
	if err != nil {
		return nil, fmt.Errorf("list devices failed: %w", err)
	}
	defer rows.Close()

	devices := make([]Device, 0)
	for rows.Next() {
		var device Device
		if err := rows.Scan(
			&device.ID,
			&device.UserID,
			&device.Platform,
			&device.DeviceName,
			&device.LastSeenAt,
			&device.IsActive,
			&device.CreatedAt,
		); err != nil {
			return nil, fmt.Errorf("scan device failed: %w", err)
		}
		devices = append(devices, device)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate devices failed: %w", err)
	}
	return devices, nil
}

func (r *PostgresRepository) UpdateDeviceName(ctx context.Context, userID, deviceID, deviceName string) (Device, error) {
	var device Device
	err := r.db.QueryRow(ctx, `
		UPDATE devices
		SET device_name = $3
		WHERE user_id = $1 AND id = $2
		RETURNING id, user_id, platform, device_name, last_seen_at, is_active, created_at
	`, userID, deviceID, deviceName).Scan(
		&device.ID,
		&device.UserID,
		&device.Platform,
		&device.DeviceName,
		&device.LastSeenAt,
		&device.IsActive,
		&device.CreatedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return Device{}, ErrNotFound
	}
	if err != nil {
		return Device{}, fmt.Errorf("update device name failed: %w", err)
	}
	return device, nil
}

func (r *PostgresRepository) DeleteDevice(ctx context.Context, userID, deviceID string) (Device, error) {
	tx, err := r.db.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return Device{}, fmt.Errorf("begin device delete failed: %w", err)
	}
	defer func() {
		_ = tx.Rollback(ctx)
	}()

	var device Device
	err = tx.QueryRow(ctx, `
		DELETE FROM devices
		WHERE user_id = $1 AND id = $2
		RETURNING id, user_id, platform, device_name, last_seen_at, is_active, created_at
	`, userID, deviceID).Scan(
		&device.ID,
		&device.UserID,
		&device.Platform,
		&device.DeviceName,
		&device.LastSeenAt,
		&device.IsActive,
		&device.CreatedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return Device{}, ErrNotFound
	}
	if err != nil {
		return Device{}, fmt.Errorf("delete device failed: %w", err)
	}

	if err := tx.Commit(ctx); err != nil {
		return Device{}, fmt.Errorf("commit device delete failed: %w", err)
	}

	// 返回值代表“被移除的那台设备”的快照，对客户端继续按离线状态展示更直观。
	device.IsActive = false
	return device, nil
}

func (r *PostgresRepository) CreateRefreshToken(ctx context.Context, userID, deviceID, tokenHash string, expiresAt time.Time) (RefreshTokenRecord, error) {
	return r.insertRefreshToken(ctx, r.db, userID, deviceID, tokenHash, expiresAt)
}

func (r *PostgresRepository) FindActiveRefreshTokenByHash(ctx context.Context, tokenHash string) (RefreshTokenRecord, error) {
	var record RefreshTokenRecord
	err := r.db.QueryRow(ctx, `
		SELECT id, user_id, device_id, token_hash, expires_at, revoked_at, created_at
		FROM auth_refresh_tokens
		WHERE token_hash = $1 AND revoked_at IS NULL AND expires_at > now()
	`, tokenHash).Scan(
		&record.ID,
		&record.UserID,
		&record.DeviceID,
		&record.TokenHash,
		&record.ExpiresAt,
		&record.RevokedAt,
		&record.CreatedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return RefreshTokenRecord{}, ErrNotFound
	}
	if err != nil {
		return RefreshTokenRecord{}, fmt.Errorf("find active refresh token failed: %w", err)
	}
	return record, nil
}

func (r *PostgresRepository) RotateRefreshToken(ctx context.Context, oldTokenHash, userID, deviceID, newTokenHash string, expiresAt time.Time) (RefreshTokenRecord, error) {
	tx, err := r.db.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return RefreshTokenRecord{}, fmt.Errorf("begin refresh token rotation failed: %w", err)
	}
	defer func() {
		_ = tx.Rollback(ctx)
	}()

	// 旧 token 作废和新 token 入库必须放在同一个事务里，
	// 避免“旧 token 已废掉，但新 token 还没写进去”的半成功状态。
	commandTag, err := tx.Exec(ctx, `
		UPDATE auth_refresh_tokens
		SET revoked_at = now()
		WHERE token_hash = $1 AND revoked_at IS NULL AND expires_at > now()
	`, oldTokenHash)
	if err != nil {
		return RefreshTokenRecord{}, fmt.Errorf("revoke old refresh token failed: %w", err)
	}
	if commandTag.RowsAffected() == 0 {
		return RefreshTokenRecord{}, ErrNotFound
	}

	record, err := r.insertRefreshToken(ctx, tx, userID, deviceID, newTokenHash, expiresAt)
	if err != nil {
		return RefreshTokenRecord{}, err
	}

	if err := tx.Commit(ctx); err != nil {
		return RefreshTokenRecord{}, fmt.Errorf("commit refresh token rotation failed: %w", err)
	}
	return record, nil
}

func (r *PostgresRepository) RevokeRefreshTokenByHash(ctx context.Context, tokenHash string) error {
	_, err := r.db.Exec(ctx, `
		UPDATE auth_refresh_tokens
		SET revoked_at = now()
		WHERE token_hash = $1 AND revoked_at IS NULL
	`, tokenHash)
	if err != nil {
		return fmt.Errorf("revoke refresh token by hash failed: %w", err)
	}
	return nil
}

func (r *PostgresRepository) RevokeRefreshTokensByDevice(ctx context.Context, userID, deviceID string) error {
	_, err := r.db.Exec(ctx, `
		UPDATE auth_refresh_tokens
		SET revoked_at = now()
		WHERE user_id = $1 AND device_id = $2 AND revoked_at IS NULL
	`, userID, deviceID)
	if err != nil {
		return fmt.Errorf("revoke refresh tokens by device failed: %w", err)
	}
	return nil
}

func (r *PostgresRepository) RevokeRefreshTokensByUserExceptDevice(ctx context.Context, userID, keepDeviceID string) error {
	_, err := r.db.Exec(ctx, `
		UPDATE auth_refresh_tokens
		SET revoked_at = now()
		WHERE user_id = $1 AND device_id <> $2 AND revoked_at IS NULL
	`, userID, keepDeviceID)
	if err != nil {
		return fmt.Errorf("revoke refresh tokens by user except device failed: %w", err)
	}
	return nil
}

type queryRower interface {
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
}

func (r *PostgresRepository) insertRefreshToken(ctx context.Context, q queryRower, userID, deviceID, tokenHash string, expiresAt time.Time) (RefreshTokenRecord, error) {
	refreshTokenID, err := id.NewUUID()
	if err != nil {
		return RefreshTokenRecord{}, err
	}

	var record RefreshTokenRecord
	err = q.QueryRow(ctx, `
		INSERT INTO auth_refresh_tokens(id, user_id, device_id, token_hash, expires_at)
		VALUES ($1, $2, $3, $4, $5)
		RETURNING id, user_id, device_id, token_hash, expires_at, revoked_at, created_at
	`, refreshTokenID, userID, deviceID, tokenHash, expiresAt).Scan(
		&record.ID,
		&record.UserID,
		&record.DeviceID,
		&record.TokenHash,
		&record.ExpiresAt,
		&record.RevokedAt,
		&record.CreatedAt,
	)
	if err != nil {
		return RefreshTokenRecord{}, fmt.Errorf("insert refresh token failed: %w", err)
	}
	return record, nil
}

func isUniqueViolation(err error) bool {
	var pgErr *pgconn.PgError
	if !errors.As(err, &pgErr) {
		return false
	}
	return pgErr.Code == "23505"
}
