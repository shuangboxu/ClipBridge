package clipboard

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

const duplicateWindow = 10 * time.Second

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

func (r *PostgresRepository) CreateTextItem(ctx context.Context, params CreateTextItemParams) (CreateTextItemResult, error) {
	tx, err := r.db.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return CreateTextItemResult{}, fmt.Errorf("begin create clipboard item failed: %w", err)
	}
	defer func() {
		_ = tx.Rollback(ctx)
	}()

	currentSeq, err := r.lockUserSyncCounter(ctx, tx, params.UserID)
	if err != nil {
		return CreateTextItemResult{}, err
	}

	latestItem, found, err := r.getLatestUserItem(ctx, tx, params.UserID)
	if err != nil {
		return CreateTextItemResult{}, err
	}
	if found &&
		latestItem.ContentHash == params.ContentHash &&
		time.Since(latestItem.CreatedAt) <= duplicateWindow {
		if err := tx.Commit(ctx); err != nil {
			return CreateTextItemResult{}, fmt.Errorf("commit duplicated clipboard item failed: %w", err)
		}
		return CreateTextItemResult{
			Item:         latestItem,
			Deduplicated: true,
		}, nil
	}

	itemID, err := id.NewUUID()
	if err != nil {
		return CreateTextItemResult{}, err
	}

	nextSeq := currentSeq + 1
	var item Item
	err = tx.QueryRow(ctx, `
		INSERT INTO clipboard_items(
			id, user_id, seq, content_type, text_content, content_hash, origin_device_id
		)
		VALUES ($1, $2, $3, $4, $5, $6, $7)
		RETURNING id, user_id, seq, content_type, COALESCE(text_content, ''), content_hash, origin_device_id, created_at
	`, itemID, params.UserID, nextSeq, ContentTypeText, params.TextContent, params.ContentHash, params.OriginDeviceID).Scan(
		&item.ID,
		&item.UserID,
		&item.Seq,
		&item.ContentType,
		&item.TextContent,
		&item.ContentHash,
		&item.OriginDeviceID,
		&item.CreatedAt,
	)
	if err != nil {
		if isForeignKeyViolation(err) {
			return CreateTextItemResult{}, ErrNotFound
		}
		return CreateTextItemResult{}, fmt.Errorf("insert clipboard item failed: %w", err)
	}

	if _, err := tx.Exec(ctx, `
		UPDATE user_sync_counters
		SET current_seq = $2
		WHERE user_id = $1
	`, params.UserID, nextSeq); err != nil {
		return CreateTextItemResult{}, fmt.Errorf("update user sync counter failed: %w", err)
	}

	if err := tx.Commit(ctx); err != nil {
		return CreateTextItemResult{}, fmt.Errorf("commit clipboard item failed: %w", err)
	}

	return CreateTextItemResult{
		Item:         item,
		Deduplicated: false,
	}, nil
}

func (r *PostgresRepository) ListHistory(ctx context.Context, userID string, options ListHistoryOptions) ([]Item, bool, error) {
	limitPlusOne := options.Limit + 1

	var rows pgx.Rows
	var err error
	if options.BeforeSeq != nil {
		rows, err = r.db.Query(ctx, `
			SELECT id, user_id, seq, content_type, COALESCE(text_content, ''), content_hash, origin_device_id, created_at
			FROM clipboard_items
			WHERE user_id = $1 AND seq < $2
			ORDER BY seq DESC
			LIMIT $3
		`, userID, *options.BeforeSeq, limitPlusOne)
	} else {
		rows, err = r.db.Query(ctx, `
			SELECT id, user_id, seq, content_type, COALESCE(text_content, ''), content_hash, origin_device_id, created_at
			FROM clipboard_items
			WHERE user_id = $1
			ORDER BY seq DESC
			LIMIT $2
		`, userID, limitPlusOne)
	}
	if err != nil {
		return nil, false, fmt.Errorf("list clipboard history failed: %w", err)
	}
	defer rows.Close()

	items, err := scanItems(rows)
	if err != nil {
		return nil, false, err
	}

	hasMore := len(items) > options.Limit
	if hasMore {
		items = items[:options.Limit]
	}
	return items, hasMore, nil
}

func (r *PostgresRepository) PullItems(ctx context.Context, userID string, sinceSeq int64, limit int) ([]Item, bool, error) {
	rows, err := r.db.Query(ctx, `
		SELECT id, user_id, seq, content_type, COALESCE(text_content, ''), content_hash, origin_device_id, created_at
		FROM clipboard_items
		WHERE user_id = $1 AND seq > $2
		ORDER BY seq ASC
		LIMIT $3
	`, userID, sinceSeq, limit+1)
	if err != nil {
		return nil, false, fmt.Errorf("pull clipboard items failed: %w", err)
	}
	defer rows.Close()

	items, err := scanItems(rows)
	if err != nil {
		return nil, false, err
	}

	hasMore := len(items) > limit
	if hasMore {
		items = items[:limit]
	}
	return items, hasMore, nil
}

func (r *PostgresRepository) AckDevice(ctx context.Context, userID, deviceID string, seq int64) (int64, error) {
	var lastAckSeq int64
	err := r.db.QueryRow(ctx, `
		INSERT INTO device_sync_state(user_id, device_id, last_ack_seq, updated_at)
		VALUES ($1, $2, $3, now())
		ON CONFLICT (user_id, device_id)
		DO UPDATE SET
			last_ack_seq = GREATEST(device_sync_state.last_ack_seq, EXCLUDED.last_ack_seq),
			updated_at = now()
		RETURNING last_ack_seq
	`, userID, deviceID, seq).Scan(&lastAckSeq)
	if err != nil {
		if isForeignKeyViolation(err) {
			return 0, ErrNotFound
		}
		return 0, fmt.Errorf("ack device sync state failed: %w", err)
	}
	return lastAckSeq, nil
}

func (r *PostgresRepository) GetSyncSnapshot(ctx context.Context, userID, deviceID string) (SyncSnapshot, error) {
	var snapshot SyncSnapshot
	err := r.db.QueryRow(ctx, `
		SELECT
			COALESCE((SELECT current_seq FROM user_sync_counters WHERE user_id = $1), 0),
			COALESCE((SELECT last_ack_seq FROM device_sync_state WHERE user_id = $1 AND device_id = $2), 0)
	`, userID, deviceID).Scan(
		&snapshot.LatestSeq,
		&snapshot.CurrentDeviceAckSeq,
	)
	if err != nil {
		return SyncSnapshot{}, fmt.Errorf("load sync snapshot failed: %w", err)
	}
	return snapshot, nil
}

func (r *PostgresRepository) lockUserSyncCounter(ctx context.Context, tx pgx.Tx, userID string) (int64, error) {
	var currentSeq int64
	err := tx.QueryRow(ctx, `
		SELECT current_seq
		FROM user_sync_counters
		WHERE user_id = $1
		FOR UPDATE
	`, userID).Scan(&currentSeq)
	if err == nil {
		return currentSeq, nil
	}
	if !errors.Is(err, pgx.ErrNoRows) {
		return 0, fmt.Errorf("lock user sync counter failed: %w", err)
	}

	if _, err := tx.Exec(ctx, `
		INSERT INTO user_sync_counters(user_id, current_seq)
		VALUES ($1, 0)
		ON CONFLICT (user_id) DO NOTHING
	`, userID); err != nil {
		if isForeignKeyViolation(err) {
			return 0, ErrNotFound
		}
		return 0, fmt.Errorf("init user sync counter failed: %w", err)
	}

	err = tx.QueryRow(ctx, `
		SELECT current_seq
		FROM user_sync_counters
		WHERE user_id = $1
		FOR UPDATE
	`, userID).Scan(&currentSeq)
	if errors.Is(err, pgx.ErrNoRows) {
		return 0, ErrNotFound
	}
	if err != nil {
		return 0, fmt.Errorf("reload user sync counter failed: %w", err)
	}
	return currentSeq, nil
}

func (r *PostgresRepository) getLatestUserItem(ctx context.Context, tx pgx.Tx, userID string) (Item, bool, error) {
	var item Item
	err := tx.QueryRow(ctx, `
		SELECT id, user_id, seq, content_type, COALESCE(text_content, ''), content_hash, origin_device_id, created_at
		FROM clipboard_items
		WHERE user_id = $1
		ORDER BY seq DESC
		LIMIT 1
	`, userID).Scan(
		&item.ID,
		&item.UserID,
		&item.Seq,
		&item.ContentType,
		&item.TextContent,
		&item.ContentHash,
		&item.OriginDeviceID,
		&item.CreatedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return Item{}, false, nil
	}
	if err != nil {
		return Item{}, false, fmt.Errorf("get latest clipboard item failed: %w", err)
	}
	return item, true, nil
}

func scanItems(rows pgx.Rows) ([]Item, error) {
	items := make([]Item, 0, 32)
	for rows.Next() {
		var item Item
		if err := rows.Scan(
			&item.ID,
			&item.UserID,
			&item.Seq,
			&item.ContentType,
			&item.TextContent,
			&item.ContentHash,
			&item.OriginDeviceID,
			&item.CreatedAt,
		); err != nil {
			return nil, fmt.Errorf("scan clipboard item failed: %w", err)
		}
		items = append(items, item)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate clipboard items failed: %w", err)
	}
	return items, nil
}

func isForeignKeyViolation(err error) bool {
	var pgErr *pgconn.PgError
	return errors.As(err, &pgErr) && pgErr.Code == "23503"
}
