package files

import (
	"context"
	"errors"
	"fmt"

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

func (r *PostgresRepository) TouchDeviceLastSeen(ctx context.Context, userID, deviceID string) error {
	commandTag, err := r.db.Exec(ctx, `
		UPDATE devices
		SET last_seen_at = now()
		WHERE user_id = $1 AND id = $2 AND is_active = true
	`, userID, deviceID)
	if err != nil {
		return fmt.Errorf("touch device last_seen_at failed: %w", err)
	}
	if commandTag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return nil
}

func (r *PostgresRepository) GetDeviceSnapshot(ctx context.Context, userID, deviceID string) (string, error) {
	var deviceName string
	err := r.db.QueryRow(ctx, `
		SELECT device_name
		FROM devices
		WHERE user_id = $1 AND id = $2 AND is_active = true
	`, userID, deviceID).Scan(&deviceName)
	if errors.Is(err, pgx.ErrNoRows) {
		return "", ErrNotFound
	}
	if err != nil {
		return "", fmt.Errorf("get device snapshot failed: %w", err)
	}
	return deviceName, nil
}

func (r *PostgresRepository) CreateFile(ctx context.Context, params CreateFileParams) (Item, error) {
	fileID, err := id.NewUUID()
	if err != nil {
		return Item{}, err
	}

	var item Item
	err = r.db.QueryRow(ctx, `
		INSERT INTO file_assets(
			id,
			user_id,
			original_name,
			stored_path,
			content_type,
			size_bytes,
			file_sha256,
			origin_device_id,
			origin_device_name
		)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
		RETURNING
			id,
			user_id,
			original_name,
			stored_path,
			content_type,
			size_bytes,
			file_sha256,
			COALESCE(origin_device_id::text, ''),
			origin_device_name,
			created_at
	`, fileID, params.UserID, params.OriginalName, params.StoredPath, params.ContentType, params.SizeBytes, params.FileSHA256, nullableString(params.OriginDeviceID), params.OriginDeviceName).Scan(
		&item.ID,
		&item.UserID,
		&item.OriginalName,
		&item.StoredPath,
		&item.ContentType,
		&item.SizeBytes,
		&item.FileSHA256,
		&item.OriginDeviceID,
		&item.OriginDeviceName,
		&item.CreatedAt,
	)
	if isForeignKeyViolation(err) {
		return Item{}, ErrNotFound
	}
	if err != nil {
		return Item{}, fmt.Errorf("create file asset failed: %w", err)
	}
	return item, nil
}

func (r *PostgresRepository) ListFiles(ctx context.Context, userID string, options ListOptions) ([]Item, int, int64, error) {
	offset := (options.Page - 1) * options.PageSize

	var totalFiles int
	var totalBytes int64
	err := r.db.QueryRow(ctx, `
		SELECT COUNT(*)::int, COALESCE(SUM(size_bytes), 0)::bigint
		FROM file_assets
		WHERE user_id = $1
	`, userID).Scan(&totalFiles, &totalBytes)
	if err != nil {
		return nil, 0, 0, fmt.Errorf("count file assets failed: %w", err)
	}

	rows, err := r.db.Query(ctx, `
		SELECT
			id,
			user_id,
			original_name,
			stored_path,
			content_type,
			size_bytes,
			file_sha256,
			COALESCE(origin_device_id::text, ''),
			origin_device_name,
			created_at
		FROM file_assets
		WHERE user_id = $1
		ORDER BY created_at DESC, id DESC
		LIMIT $2 OFFSET $3
	`, userID, options.PageSize, offset)
	if err != nil {
		return nil, 0, 0, fmt.Errorf("list file assets failed: %w", err)
	}
	defer rows.Close()

	items := make([]Item, 0, options.PageSize)
	for rows.Next() {
		var item Item
		if err := rows.Scan(
			&item.ID,
			&item.UserID,
			&item.OriginalName,
			&item.StoredPath,
			&item.ContentType,
			&item.SizeBytes,
			&item.FileSHA256,
			&item.OriginDeviceID,
			&item.OriginDeviceName,
			&item.CreatedAt,
		); err != nil {
			return nil, 0, 0, fmt.Errorf("scan file asset failed: %w", err)
		}
		items = append(items, item)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, 0, fmt.Errorf("iterate file assets failed: %w", err)
	}

	return items, totalFiles, totalBytes, nil
}

func (r *PostgresRepository) GetFile(ctx context.Context, userID, fileID string) (Item, error) {
	var item Item
	err := r.db.QueryRow(ctx, `
		SELECT
			id,
			user_id,
			original_name,
			stored_path,
			content_type,
			size_bytes,
			file_sha256,
			COALESCE(origin_device_id::text, ''),
			origin_device_name,
			created_at
		FROM file_assets
		WHERE user_id = $1 AND id = $2
	`, userID, fileID).Scan(
		&item.ID,
		&item.UserID,
		&item.OriginalName,
		&item.StoredPath,
		&item.ContentType,
		&item.SizeBytes,
		&item.FileSHA256,
		&item.OriginDeviceID,
		&item.OriginDeviceName,
		&item.CreatedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return Item{}, ErrNotFound
	}
	if err != nil {
		return Item{}, fmt.Errorf("get file asset failed: %w", err)
	}
	return item, nil
}

func (r *PostgresRepository) RenameFile(ctx context.Context, userID, fileID, originalName string) (Item, error) {
	var item Item
	err := r.db.QueryRow(ctx, `
		UPDATE file_assets
		SET original_name = $3
		WHERE user_id = $1 AND id = $2
		RETURNING
			id,
			user_id,
			original_name,
			stored_path,
			content_type,
			size_bytes,
			file_sha256,
			COALESCE(origin_device_id::text, ''),
			origin_device_name,
			created_at
	`, userID, fileID, originalName).Scan(
		&item.ID,
		&item.UserID,
		&item.OriginalName,
		&item.StoredPath,
		&item.ContentType,
		&item.SizeBytes,
		&item.FileSHA256,
		&item.OriginDeviceID,
		&item.OriginDeviceName,
		&item.CreatedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return Item{}, ErrNotFound
	}
	if err != nil {
		return Item{}, fmt.Errorf("rename file asset failed: %w", err)
	}
	return item, nil
}

func (r *PostgresRepository) DeleteFile(ctx context.Context, userID, fileID string) (Item, bool, error) {
	var item Item
	err := r.db.QueryRow(ctx, `
		DELETE FROM file_assets
		WHERE user_id = $1 AND id = $2
		RETURNING
			id,
			user_id,
			original_name,
			stored_path,
			content_type,
			size_bytes,
			file_sha256,
			COALESCE(origin_device_id::text, ''),
			origin_device_name,
			created_at
	`, userID, fileID).Scan(
		&item.ID,
		&item.UserID,
		&item.OriginalName,
		&item.StoredPath,
		&item.ContentType,
		&item.SizeBytes,
		&item.FileSHA256,
		&item.OriginDeviceID,
		&item.OriginDeviceName,
		&item.CreatedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return Item{}, false, nil
	}
	if err != nil {
		return Item{}, false, fmt.Errorf("delete file asset failed: %w", err)
	}
	return item, true, nil
}

func isForeignKeyViolation(err error) bool {
	var pgErr *pgconn.PgError
	return errors.As(err, &pgErr) && pgErr.Code == "23503"
}

func nullableString(value string) any {
	if value == "" {
		return nil
	}
	return value
}
