package admin

import (
	"context"
	"errors"
	"fmt"

	"github.com/jackc/pgx/v5"
)

type storageQuotaQueryer interface {
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
}

// CheckStorageQuotaTx 会在事务内锁住用户行并统计当前已占用的持久化文件体积。
// 文件上传和文件分享上传都应该在真正写入元数据前调用它，
// 这样才能避免并发上传把同一份配额同时超卖。
func CheckStorageQuotaTx(ctx context.Context, q storageQuotaQueryer, userID string, appendBytes int64) error {
	if q == nil {
		return fmt.Errorf("storage quota queryer is nil")
	}
	if appendBytes < 0 {
		return ErrInvalidArgument
	}

	var storageQuotaBytes int64
	err := q.QueryRow(ctx, `
		SELECT storage_quota_bytes
		FROM users
		WHERE id = $1
		FOR UPDATE
	`, userID).Scan(&storageQuotaBytes)
	if errors.Is(err, pgx.ErrNoRows) {
		return ErrNotFound
	}
	if err != nil {
		return fmt.Errorf("query user storage quota failed: %w", err)
	}

	var storageUsedBytes int64
	err = q.QueryRow(ctx, `
		SELECT
			COALESCE((
				SELECT SUM(size_bytes)::bigint
				FROM file_assets
				WHERE user_id = $1
			), 0)
			+
			COALESCE((
				SELECT SUM(file_size_bytes)::bigint
				FROM share_items
				WHERE user_id = $1
				  AND content_kind = 'file'
				  AND file_stored_path <> ''
			), 0)
	`, userID).Scan(&storageUsedBytes)
	if err != nil {
		return fmt.Errorf("query user storage usage failed: %w", err)
	}

	if storageUsedBytes+appendBytes > storageQuotaBytes {
		return ErrStorageQuotaExceeded
	}
	return nil
}
