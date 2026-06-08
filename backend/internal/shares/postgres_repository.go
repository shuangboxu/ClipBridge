package shares

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"time"

	"clipbridge/backend/internal/admin"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
	"golang.org/x/crypto/bcrypt"

	"clipbridge/backend/internal/id"
)

type PostgresRepository struct {
	db *pgxpool.Pool
}

const shareSelectColumns = `
	id,
	user_id,
	public_token,
	content_kind,
	text_content,
	text_preview,
	encrypted_payload,
	is_encrypted,
	password_hash,
	encryption_version,
	encryption_kdf,
	encryption_iterations,
	encryption_salt,
	encryption_nonce,
	encryption_cipher,
	text_encryption_version,
	text_encryption_kdf,
	text_encryption_iterations,
	text_encryption_salt,
	text_encryption_nonce,
	text_encryption_cipher,
	file_original_name,
	file_stored_path,
	file_content_type,
	file_size_bytes,
	file_sha256,
	allow_copy_content,
	burn_mode,
	burn_after_seconds,
	expires_at,
	first_opened_at,
	burn_deadline,
	consumed_at,
	revoked_at,
	open_count,
	created_at,
	updated_at
`

const shareFileSelectColumns = `
	id,
	share_id,
	sort_order,
	original_name,
	stored_path,
	content_type,
	size_bytes,
	sha256,
	encryption_version,
	encryption_kdf,
	encryption_iterations,
	encryption_salt,
	encryption_nonce,
	encryption_cipher
`

const createTextShareSQL = `
	INSERT INTO share_items(
		id,
		user_id,
		public_token,
		content_kind,
		text_content,
		text_preview,
		encrypted_payload,
		is_encrypted,
		password_hash,
		encryption_version,
		encryption_kdf,
		encryption_iterations,
		encryption_salt,
		encryption_nonce,
		encryption_cipher,
		text_encryption_version,
		text_encryption_kdf,
		text_encryption_iterations,
		text_encryption_salt,
		text_encryption_nonce,
		text_encryption_cipher,
		allow_copy_content,
		burn_mode,
		burn_after_seconds,
		expires_at
	)
	VALUES (
		$1, $2, $3, $4, $5, $6, $7, $8, $9,
		$10, $11, $12, $13, $14, $15,
		$16, $17, $18, $19, $20, $21,
		$22, $23, $24, $25
	)
	RETURNING ` + shareSelectColumns + `
`

const createShareFileSQL = `
	INSERT INTO share_files(
		id,
		share_id,
		sort_order,
		original_name,
		stored_path,
		content_type,
		size_bytes,
		sha256,
		encryption_version,
		encryption_kdf,
		encryption_iterations,
		encryption_salt,
		encryption_nonce,
		encryption_cipher
	)
	VALUES (
		$1, $2, $3, $4, $5, $6, $7,
		$8, $9, $10, $11, $12, $13, $14
	)
`

const createFileShareSQL = `
	INSERT INTO share_items(
		id,
		user_id,
		public_token,
		content_kind,
		text_content,
		text_preview,
		encrypted_payload,
		is_encrypted,
		password_hash,
		encryption_version,
		encryption_kdf,
		encryption_iterations,
		encryption_salt,
		encryption_nonce,
		encryption_cipher,
		text_encryption_version,
		text_encryption_kdf,
		text_encryption_iterations,
		text_encryption_salt,
		text_encryption_nonce,
		text_encryption_cipher,
		file_original_name,
		file_stored_path,
		file_content_type,
		file_size_bytes,
		file_sha256,
		allow_copy_content,
		burn_mode,
		burn_after_seconds,
		expires_at
	)
	VALUES (
		$1, $2, $3, $4, $5, $6, $7, $8, $9,
		$10, $11, $12, $13, $14, $15,
		$16, $17, $18, $19, $20, $21,
		$22, $23, $24, $25, $26, $27,
		$28, $29, $30
	)
	RETURNING ` + shareSelectColumns + `
`

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

func (r *PostgresRepository) CreateTextShare(ctx context.Context, params CreateTextShareParams) (Item, error) {
	shareID, err := id.NewUUID()
	if err != nil {
		return Item{}, err
	}

	row := r.db.QueryRow(ctx, createTextShareSQL, shareID, params.UserID, params.PublicToken, ContentKindText, params.TextContent, params.TextPreview, params.EncryptedPayload,
		params.IsEncrypted, params.PasswordHash, params.Encryption.Version, params.Encryption.KDF, params.Encryption.Iterations,
		params.Encryption.Salt, params.Encryption.Nonce, params.Encryption.Cipher,
		params.TextEncryption.Version, params.TextEncryption.KDF, params.TextEncryption.Iterations,
		params.TextEncryption.Salt, params.TextEncryption.Nonce, params.TextEncryption.Cipher,
		params.AllowCopyContent, params.BurnMode, params.BurnAfterSeconds, nullableTime(params.ExpiresAt))

	item, err := scanShareItem(row)
	if isForeignKeyViolation(err) {
		return Item{}, ErrNotFound
	}
	if err != nil {
		return Item{}, fmt.Errorf("create text share failed: %w", err)
	}
	return item, nil
}

func (r *PostgresRepository) CreateFileShare(ctx context.Context, params CreateFileShareParams) (Item, error) {
	shareID, err := id.NewUUID()
	if err != nil {
		return Item{}, err
	}

	tx, err := r.db.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return Item{}, fmt.Errorf("begin create file share failed: %w", err)
	}
	defer func() {
		_ = tx.Rollback(ctx)
	}()

	if err := admin.CheckStorageQuotaTx(ctx, tx, params.UserID, sumShareFileParamsBytes(params.Files)); err != nil {
		switch {
		case errors.Is(err, admin.ErrNotFound):
			return Item{}, ErrNotFound
		case errors.Is(err, admin.ErrStorageQuotaExceeded):
			return Item{}, ErrStorageQuotaExceeded
		default:
			return Item{}, err
		}
	}

	// 文件分享这里既要写文件元数据，也要兼容附带文字说明。
	// 占位符数量必须和上面的列严格一一对应，否则会在运行时直接报 500。
	row := tx.QueryRow(ctx, createFileShareSQL, shareID, params.UserID, params.PublicToken, ContentKindFile, params.TextContent, params.TextPreview, params.TextEncryptedPayload,
		params.IsEncrypted, params.PasswordHash,
		params.Encryption.Version, params.Encryption.KDF, params.Encryption.Iterations,
		params.Encryption.Salt, params.Encryption.Nonce, params.Encryption.Cipher,
		params.TextEncryption.Version, params.TextEncryption.KDF, params.TextEncryption.Iterations,
		params.TextEncryption.Salt, params.TextEncryption.Nonce, params.TextEncryption.Cipher,
		params.FileOriginalName, params.FileStoredPath, params.FileContentType, params.FileSizeBytes, params.FileSHA256,
		params.AllowCopyContent, params.BurnMode, params.BurnAfterSeconds, nullableTime(params.ExpiresAt))

	item, err := scanShareItem(row)
	if isForeignKeyViolation(err) {
		return Item{}, ErrNotFound
	}
	if err != nil {
		return Item{}, fmt.Errorf("create file share failed: %w", err)
	}

	item.Files = make([]ShareFile, 0, len(params.Files))
	for _, file := range params.Files {
		if _, err := tx.Exec(ctx, createShareFileSQL,
			file.ID,
			shareID,
			file.SortOrder,
			file.OriginalName,
			file.StoredPath,
			file.ContentType,
			file.SizeBytes,
			file.SHA256,
			file.Encryption.Version,
			file.Encryption.KDF,
			file.Encryption.Iterations,
			file.Encryption.Salt,
			file.Encryption.Nonce,
			file.Encryption.Cipher,
		); err != nil {
			return Item{}, fmt.Errorf("create share file failed: %w", err)
		}

		item.Files = append(item.Files, ShareFile{
			ID:           file.ID,
			ShareID:      shareID,
			SortOrder:    file.SortOrder,
			OriginalName: file.OriginalName,
			StoredPath:   file.StoredPath,
			ContentType:  file.ContentType,
			SizeBytes:    file.SizeBytes,
			SHA256:       file.SHA256,
			Encryption:   file.Encryption,
		})
	}

	if err := tx.Commit(ctx); err != nil {
		return Item{}, fmt.Errorf("commit create file share failed: %w", err)
	}
	return item, nil
}

func (r *PostgresRepository) ListShares(ctx context.Context, userID string, options ListOptions) ([]Item, int, error) {
	whereSQL, args, err := buildStatusFilterSQL(userID, options.Status, options.Now)
	if err != nil {
		return nil, 0, err
	}

	var total int
	countSQL := `SELECT COUNT(*)::int FROM share_items WHERE ` + whereSQL
	if err := r.db.QueryRow(ctx, countSQL, args...).Scan(&total); err != nil {
		return nil, 0, fmt.Errorf("count shares failed: %w", err)
	}

	offset := (options.Page - 1) * options.PageSize
	queryArgs := append(append([]any{}, args...), options.PageSize, offset)
	rows, err := r.db.Query(ctx, `SELECT `+shareSelectColumns+`
		FROM share_items
		WHERE `+whereSQL+`
		ORDER BY created_at DESC, id DESC
		LIMIT $`+fmt.Sprintf("%d", len(args)+1)+` OFFSET $`+fmt.Sprintf("%d", len(args)+2), queryArgs...)
	if err != nil {
		return nil, 0, fmt.Errorf("list shares failed: %w", err)
	}
	defer rows.Close()

	items := make([]Item, 0, options.PageSize)
	for rows.Next() {
		item, err := scanShareItem(rows)
		if err != nil {
			return nil, 0, fmt.Errorf("scan share failed: %w", err)
		}
		items = append(items, item)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, fmt.Errorf("iterate shares failed: %w", err)
	}

	return items, total, nil
}

func (r *PostgresRepository) ListShareFiles(ctx context.Context, shareID string) ([]ShareFile, error) {
	rows, err := r.db.Query(ctx, `SELECT `+shareFileSelectColumns+`
		FROM share_files
		WHERE share_id = $1
		ORDER BY sort_order ASC, id ASC
	`, shareID)
	if err != nil {
		return nil, fmt.Errorf("list share files failed: %w", err)
	}
	defer rows.Close()

	files := make([]ShareFile, 0)
	for rows.Next() {
		file, err := scanShareFile(rows)
		if err != nil {
			return nil, fmt.Errorf("scan share file failed: %w", err)
		}
		files = append(files, file)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate share files failed: %w", err)
	}

	return files, nil
}

func (r *PostgresRepository) RevokeShare(ctx context.Context, userID, shareID string) (Item, error) {
	item, err := scanShareItem(r.db.QueryRow(ctx, `
		UPDATE share_items
		SET revoked_at = COALESCE(revoked_at, now()), updated_at = now()
		WHERE user_id = $1 AND id = $2
		RETURNING `+shareSelectColumns+`
	`, userID, shareID))
	if errors.Is(err, pgx.ErrNoRows) || errors.Is(err, ErrNotFound) {
		return Item{}, ErrNotFound
	}
	if err != nil {
		return Item{}, fmt.Errorf("revoke share failed: %w", err)
	}
	return item, nil
}

func (r *PostgresRepository) GetShareByToken(ctx context.Context, publicToken string) (Item, error) {
	item, err := scanShareItem(r.db.QueryRow(ctx, `
		SELECT `+shareSelectColumns+`
		FROM share_items
		WHERE public_token = $1
	`, publicToken))
	if errors.Is(err, pgx.ErrNoRows) || errors.Is(err, ErrNotFound) {
		return Item{}, ErrNotFound
	}
	if err != nil {
		return Item{}, fmt.Errorf("get share by token failed: %w", err)
	}
	return item, nil
}

func (r *PostgresRepository) OpenShareByToken(ctx context.Context, publicToken, password string, now time.Time) (Item, error) {
	// 公开取件要在事务里加行锁处理。
	// 这样“单次焚毁”或“首次打开后倒计时”不会被并发请求同时绕过。
	tx, err := r.db.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return Item{}, fmt.Errorf("begin open share failed: %w", err)
	}
	defer func() {
		_ = tx.Rollback(ctx)
	}()

	item, err := scanShareItem(tx.QueryRow(ctx, `
		SELECT `+shareSelectColumns+`
		FROM share_items
		WHERE public_token = $1
		FOR UPDATE
	`, publicToken))
	if errors.Is(err, pgx.ErrNoRows) || errors.Is(err, ErrNotFound) {
		return Item{}, ErrNotFound
	}
	if err != nil {
		return Item{}, fmt.Errorf("lock share failed: %w", err)
	}

	if computeShareStatus(item, now) != StatusActive {
		return Item{}, ErrShareUnavailable
	}

	if item.RequiresPassword() {
		if err := bcrypt.CompareHashAndPassword([]byte(item.PasswordHash), []byte(password)); err != nil {
			return Item{}, ErrInvalidPassword
		}
	}

	// 下面统一在一条 UPDATE 里推进 open_count / first_opened_at / 焚毁状态，
	// 避免业务状态散落在多条 SQL 里，后续排查时更容易对照数据库结果。
	openCount := item.OpenCount + 1
	firstOpenedAt := item.FirstOpenedAt
	if firstOpenedAt == nil {
		firstOpenedAt = &now
	}

	burnDeadline := item.BurnDeadline
	if item.BurnMode == BurnModeCountdown && burnDeadline == nil {
		deadline := now.Add(time.Duration(item.BurnAfterSeconds) * time.Second)
		burnDeadline = &deadline
	}

	consumedAt := item.ConsumedAt
	if item.BurnMode == BurnModeOnce {
		consumedAt = &now
	}

	updatedItem, err := scanShareItem(tx.QueryRow(ctx, `
		UPDATE share_items
		SET
			open_count = $2,
			first_opened_at = $3,
			burn_deadline = $4,
			consumed_at = $5,
			updated_at = now()
		WHERE id = $1
		RETURNING `+shareSelectColumns+`
	`, item.ID, openCount, nullableTime(firstOpenedAt), nullableTime(burnDeadline), nullableTime(consumedAt)))
	if err != nil {
		return Item{}, fmt.Errorf("update opened share failed: %w", err)
	}

	if err := tx.Commit(ctx); err != nil {
		return Item{}, fmt.Errorf("commit opened share failed: %w", err)
	}
	return updatedItem, nil
}

type rowScanner interface {
	Scan(dest ...any) error
}

func scanShareItem(row rowScanner) (Item, error) {
	var item Item
	err := row.Scan(
		&item.ID,
		&item.UserID,
		&item.PublicToken,
		&item.ContentKind,
		&item.TextContent,
		&item.TextPreview,
		&item.EncryptedPayload,
		&item.IsEncrypted,
		&item.PasswordHash,
		&item.Encryption.Version,
		&item.Encryption.KDF,
		&item.Encryption.Iterations,
		&item.Encryption.Salt,
		&item.Encryption.Nonce,
		&item.Encryption.Cipher,
		&item.TextEncryption.Version,
		&item.TextEncryption.KDF,
		&item.TextEncryption.Iterations,
		&item.TextEncryption.Salt,
		&item.TextEncryption.Nonce,
		&item.TextEncryption.Cipher,
		&item.FileOriginalName,
		&item.FileStoredPath,
		&item.FileContentType,
		&item.FileSizeBytes,
		&item.FileSHA256,
		&item.AllowCopyContent,
		&item.BurnMode,
		&item.BurnAfterSeconds,
		&item.ExpiresAt,
		&item.FirstOpenedAt,
		&item.BurnDeadline,
		&item.ConsumedAt,
		&item.RevokedAt,
		&item.OpenCount,
		&item.CreatedAt,
		&item.UpdatedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return Item{}, ErrNotFound
	}
	if err != nil {
		return Item{}, err
	}
	return item, nil
}

func scanShareFile(row rowScanner) (ShareFile, error) {
	var file ShareFile
	err := row.Scan(
		&file.ID,
		&file.ShareID,
		&file.SortOrder,
		&file.OriginalName,
		&file.StoredPath,
		&file.ContentType,
		&file.SizeBytes,
		&file.SHA256,
		&file.Encryption.Version,
		&file.Encryption.KDF,
		&file.Encryption.Iterations,
		&file.Encryption.Salt,
		&file.Encryption.Nonce,
		&file.Encryption.Cipher,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return ShareFile{}, ErrNotFound
	}
	if err != nil {
		return ShareFile{}, err
	}
	return file, nil
}

func sumShareFileParamsBytes(files []ShareFileParams) int64 {
	var total int64
	for _, file := range files {
		total += file.SizeBytes
	}
	return total
}

func buildStatusFilterSQL(userID, status string, now time.Time) (string, []any, error) {
	baseWhere := "user_id = $1"
	args := []any{userID}

	switch strings.ToLower(strings.TrimSpace(status)) {
	case "", StatusAll:
		return baseWhere, args, nil
	case StatusActive:
		return baseWhere + ` AND revoked_at IS NULL AND consumed_at IS NULL AND (expires_at IS NULL OR expires_at > $2) AND (burn_deadline IS NULL OR burn_deadline > $2)`, append(args, now), nil
	case StatusExpired:
		return baseWhere + ` AND revoked_at IS NULL AND consumed_at IS NULL AND expires_at IS NOT NULL AND expires_at <= $2`, append(args, now), nil
	case StatusConsumed:
		return baseWhere + ` AND revoked_at IS NULL AND (consumed_at IS NOT NULL OR (burn_deadline IS NOT NULL AND burn_deadline <= $2))`, append(args, now), nil
	case StatusRevoked:
		return baseWhere + ` AND revoked_at IS NOT NULL`, args, nil
	default:
		return "", nil, fmt.Errorf("invalid status filter")
	}
}

func computeShareStatus(item Item, now time.Time) string {
	switch {
	case item.RevokedAt != nil:
		return StatusRevoked
	case item.ConsumedAt != nil:
		return StatusConsumed
	case item.BurnDeadline != nil && !item.BurnDeadline.After(now):
		return StatusConsumed
	case item.ExpiresAt != nil && !item.ExpiresAt.After(now):
		return StatusExpired
	default:
		return StatusActive
	}
}

func isForeignKeyViolation(err error) bool {
	var pgErr *pgconn.PgError
	return errors.As(err, &pgErr) && pgErr.Code == "23503"
}

func nullableTime(value *time.Time) any {
	if value == nil {
		return nil
	}
	return value.UTC()
}
