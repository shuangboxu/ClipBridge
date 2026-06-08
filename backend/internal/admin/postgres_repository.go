package admin

import (
	"context"
	"errors"
	"fmt"
	"strings"

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

func (r *PostgresRepository) EnsureSystemSettings(ctx context.Context, seed SeedSettings) (SystemSettings, error) {
	seed = normalizeSeedSettings(seed)

	_, err := r.db.Exec(ctx, `
		INSERT INTO system_settings(
			id,
			max_user_count,
			default_storage_quota_bytes,
			default_upload_bandwidth_kbps,
			default_download_bandwidth_kbps,
			max_user_upload_bandwidth_kbps,
			max_user_download_bandwidth_kbps,
			max_upload_file_bytes,
			allow_registration
		)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
		ON CONFLICT (id) DO NOTHING
	`, true, seed.MaxUserCount, seed.DefaultStorageQuotaBytes, seed.DefaultUploadBandwidthKbps,
		seed.DefaultDownloadBandwidthKbps, seed.MaxUserUploadBandwidthKbps, seed.MaxUserDownloadBandwidthKbps,
		seed.MaxUploadFileBytes, seed.AllowRegistration)
	if err != nil {
		return SystemSettings{}, fmt.Errorf("ensure system settings failed: %w", err)
	}

	return r.GetSystemSettings(ctx)
}

func (r *PostgresRepository) GetSystemSettings(ctx context.Context) (SystemSettings, error) {
	settings, err := scanSystemSettings(r.db.QueryRow(ctx, `
		SELECT
			max_user_count,
			default_storage_quota_bytes,
			default_upload_bandwidth_kbps,
			default_download_bandwidth_kbps,
			max_user_upload_bandwidth_kbps,
			max_user_download_bandwidth_kbps,
			max_upload_file_bytes,
			allow_registration,
			updated_at
		FROM system_settings
		WHERE id = true
	`))
	if errors.Is(err, pgx.ErrNoRows) || errors.Is(err, ErrNotFound) {
		return SystemSettings{}, ErrNotFound
	}
	if err != nil {
		return SystemSettings{}, fmt.Errorf("get system settings failed: %w", err)
	}
	return settings, nil
}

func (r *PostgresRepository) CountUsers(ctx context.Context) (int, error) {
	var count int
	if err := r.db.QueryRow(ctx, `SELECT COUNT(*)::int FROM users`).Scan(&count); err != nil {
		return 0, fmt.Errorf("count users failed: %w", err)
	}
	return count, nil
}

func (r *PostgresRepository) UpdateSystemSettings(ctx context.Context, input UpdateSettingsInput) (SystemSettings, error) {
	current, err := r.GetSystemSettings(ctx)
	if err != nil {
		return SystemSettings{}, err
	}

	next := current
	if input.MaxUserCount != nil {
		next.MaxUserCount = *input.MaxUserCount
	}
	if input.DefaultStorageQuotaBytes != nil {
		next.DefaultStorageQuotaBytes = *input.DefaultStorageQuotaBytes
	}
	if input.DefaultUploadBandwidthKbps != nil {
		next.DefaultUploadBandwidthKbps = *input.DefaultUploadBandwidthKbps
	}
	if input.DefaultDownloadBandwidthKbps != nil {
		next.DefaultDownloadBandwidthKbps = *input.DefaultDownloadBandwidthKbps
	}
	if input.MaxUserUploadBandwidthKbps != nil {
		next.MaxUserUploadBandwidthKbps = *input.MaxUserUploadBandwidthKbps
	}
	if input.MaxUserDownloadBandwidthKbps != nil {
		next.MaxUserDownloadBandwidthKbps = *input.MaxUserDownloadBandwidthKbps
	}
	if input.MaxUploadFileBytes != nil {
		next.MaxUploadFileBytes = *input.MaxUploadFileBytes
	}
	if input.AllowRegistration != nil {
		next.AllowRegistration = *input.AllowRegistration
	}

	if err := validateSystemSettings(next); err != nil {
		return SystemSettings{}, err
	}

	updated, err := scanSystemSettings(r.db.QueryRow(ctx, `
		UPDATE system_settings
		SET
			max_user_count = $2,
			default_storage_quota_bytes = $3,
			default_upload_bandwidth_kbps = $4,
			default_download_bandwidth_kbps = $5,
			max_user_upload_bandwidth_kbps = $6,
			max_user_download_bandwidth_kbps = $7,
			max_upload_file_bytes = $8,
			allow_registration = $9,
			updated_at = now()
		WHERE id = $1
		RETURNING
			max_user_count,
			default_storage_quota_bytes,
			default_upload_bandwidth_kbps,
			default_download_bandwidth_kbps,
			max_user_upload_bandwidth_kbps,
			max_user_download_bandwidth_kbps,
			max_upload_file_bytes,
			allow_registration,
			updated_at
	`, true, next.MaxUserCount, next.DefaultStorageQuotaBytes, next.DefaultUploadBandwidthKbps,
		next.DefaultDownloadBandwidthKbps, next.MaxUserUploadBandwidthKbps, next.MaxUserDownloadBandwidthKbps,
		next.MaxUploadFileBytes, next.AllowRegistration))
	if errors.Is(err, pgx.ErrNoRows) {
		return SystemSettings{}, ErrNotFound
	}
	if err != nil {
		return SystemSettings{}, fmt.Errorf("update system settings failed: %w", err)
	}
	return updated, nil
}

func (r *PostgresRepository) GetAccountOverview(ctx context.Context, userID string) (AccountOverview, error) {
	var storageQuotaBytes int64
	var storageUsedBytes int64
	var overview AccountOverview

	err := r.db.QueryRow(ctx, `
		SELECT
			u.storage_quota_bytes,
			COALESCE(files.total_bytes, 0) + COALESCE(shares.total_bytes, 0) AS storage_used_bytes,
			ss.max_user_count,
			ss.default_storage_quota_bytes,
			ss.default_upload_bandwidth_kbps,
			ss.default_download_bandwidth_kbps,
			ss.max_user_upload_bandwidth_kbps,
			ss.max_user_download_bandwidth_kbps,
			ss.max_upload_file_bytes,
			ss.allow_registration
		FROM users u
		CROSS JOIN system_settings ss
		LEFT JOIN LATERAL (
			SELECT COALESCE(SUM(size_bytes), 0)::bigint AS total_bytes
			FROM file_assets
			WHERE user_id = u.id
		) files ON true
		LEFT JOIN LATERAL (
			SELECT COALESCE(SUM(file_size_bytes), 0)::bigint AS total_bytes
			FROM share_items
			WHERE user_id = u.id
			  AND content_kind = 'file'
			  AND file_stored_path <> ''
		) shares ON true
		WHERE u.id = $1
	`, userID).Scan(
		&storageQuotaBytes,
		&storageUsedBytes,
		&overview.Limits.MaxUserCount,
		&overview.Limits.DefaultStorageQuotaBytes,
		&overview.Limits.DefaultUploadBandwidthKbps,
		&overview.Limits.DefaultDownloadBandwidthKbps,
		&overview.Limits.MaxUserUploadBandwidthKbps,
		&overview.Limits.MaxUserDownloadBandwidthKbps,
		&overview.Limits.MaxUploadFileBytes,
		&overview.Limits.AllowRegistration,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return AccountOverview{}, ErrNotFound
	}
	if err != nil {
		return AccountOverview{}, fmt.Errorf("get account overview failed: %w", err)
	}

	overview.StorageUsedBytes = storageUsedBytes
	overview.StorageFreeBytes = safeFreeBytes(storageQuotaBytes, storageUsedBytes)
	return overview, nil
}

func (r *PostgresRepository) GetUserTransferPolicy(ctx context.Context, userID string) (UserTransferPolicy, error) {
	var policy UserTransferPolicy
	err := r.db.QueryRow(ctx, `
		SELECT
			u.upload_bandwidth_kbps,
			u.download_bandwidth_kbps,
			ss.max_upload_file_bytes
		FROM users u
		CROSS JOIN system_settings ss
		WHERE u.id = $1
	`, userID).Scan(
		&policy.UploadBandwidthKbps,
		&policy.DownloadBandwidthKbps,
		&policy.MaxUploadFileBytes,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return UserTransferPolicy{}, ErrNotFound
	}
	if err != nil {
		return UserTransferPolicy{}, fmt.Errorf("get user transfer policy failed: %w", err)
	}
	return policy, nil
}

func (r *PostgresRepository) CreateQuotaRequest(ctx context.Context, userID string, requestedQuotaBytes int64, reason string) (QuotaRequest, error) {
	var currentQuotaBytes int64
	err := r.db.QueryRow(ctx, `SELECT storage_quota_bytes FROM users WHERE id = $1`, userID).Scan(&currentQuotaBytes)
	if errors.Is(err, pgx.ErrNoRows) {
		return QuotaRequest{}, ErrNotFound
	}
	if err != nil {
		return QuotaRequest{}, fmt.Errorf("query current quota failed: %w", err)
	}
	if requestedQuotaBytes <= currentQuotaBytes {
		return QuotaRequest{}, ErrInvalidArgument
	}

	requestID, err := id.NewUUID()
	if err != nil {
		return QuotaRequest{}, err
	}

	_, err = r.db.Exec(ctx, `
		INSERT INTO quota_requests(
			id,
			user_id,
			requested_quota_bytes,
			current_quota_bytes,
			reason
		)
		VALUES ($1, $2, $3, $4, $5)
	`, requestID, userID, requestedQuotaBytes, currentQuotaBytes, reason)
	if isConstraintViolation(err, "uq_quota_requests_pending_per_user") {
		return QuotaRequest{}, ErrPendingQuotaRequestExists
	}
	if isForeignKeyViolation(err) {
		return QuotaRequest{}, ErrNotFound
	}
	if err != nil {
		return QuotaRequest{}, fmt.Errorf("create quota request failed: %w", err)
	}

	return r.getQuotaRequestByID(ctx, r.db, requestID)
}

func (r *PostgresRepository) ListQuotaRequestsByUser(ctx context.Context, userID, status string) ([]QuotaRequest, error) {
	return listQuotaRequests(ctx, r.db, "qr.user_id = $1", []any{userID}, status)
}

func (r *PostgresRepository) CreateBandwidthRequest(ctx context.Context, userID string, requestedUploadKbps, requestedDownloadKbps int, reason string) (BandwidthRequest, error) {
	var currentUploadKbps int
	var currentDownloadKbps int
	var maxUploadKbps int
	var maxDownloadKbps int

	err := r.db.QueryRow(ctx, `
		SELECT
			u.upload_bandwidth_kbps,
			u.download_bandwidth_kbps,
			ss.max_user_upload_bandwidth_kbps,
			ss.max_user_download_bandwidth_kbps
		FROM users u
		CROSS JOIN system_settings ss
		WHERE u.id = $1
	`, userID).Scan(&currentUploadKbps, &currentDownloadKbps, &maxUploadKbps, &maxDownloadKbps)
	if errors.Is(err, pgx.ErrNoRows) {
		return BandwidthRequest{}, ErrNotFound
	}
	if err != nil {
		return BandwidthRequest{}, fmt.Errorf("query current bandwidth failed: %w", err)
	}
	if requestedUploadKbps <= 0 || requestedDownloadKbps <= 0 {
		return BandwidthRequest{}, ErrInvalidArgument
	}
	if requestedUploadKbps <= currentUploadKbps && requestedDownloadKbps <= currentDownloadKbps {
		return BandwidthRequest{}, ErrInvalidArgument
	}
	if requestedUploadKbps > maxUploadKbps || requestedDownloadKbps > maxDownloadKbps {
		return BandwidthRequest{}, ErrInvalidArgument
	}

	requestID, err := id.NewUUID()
	if err != nil {
		return BandwidthRequest{}, err
	}

	_, err = r.db.Exec(ctx, `
		INSERT INTO bandwidth_requests(
			id,
			user_id,
			requested_upload_kbps,
			requested_download_kbps,
			current_upload_kbps,
			current_download_kbps,
			reason
		)
		VALUES ($1, $2, $3, $4, $5, $6, $7)
	`, requestID, userID, requestedUploadKbps, requestedDownloadKbps, currentUploadKbps, currentDownloadKbps, reason)
	if isConstraintViolation(err, "uq_bandwidth_requests_pending_per_user") {
		return BandwidthRequest{}, ErrPendingBandwidthRequestExists
	}
	if isForeignKeyViolation(err) {
		return BandwidthRequest{}, ErrNotFound
	}
	if err != nil {
		return BandwidthRequest{}, fmt.Errorf("create bandwidth request failed: %w", err)
	}

	return r.getBandwidthRequestByID(ctx, r.db, requestID)
}

func (r *PostgresRepository) ListBandwidthRequestsByUser(ctx context.Context, userID, status string) ([]BandwidthRequest, error) {
	return listBandwidthRequests(ctx, r.db, "br.user_id = $1", []any{userID}, status)
}

func (r *PostgresRepository) CreateAdminRequest(ctx context.Context, userID, reason string) (AdminRequest, error) {
	var isAdmin bool
	err := r.db.QueryRow(ctx, `SELECT is_admin FROM users WHERE id = $1`, userID).Scan(&isAdmin)
	if errors.Is(err, pgx.ErrNoRows) {
		return AdminRequest{}, ErrNotFound
	}
	if err != nil {
		return AdminRequest{}, fmt.Errorf("query admin flag failed: %w", err)
	}
	if isAdmin {
		return AdminRequest{}, ErrAlreadyAdmin
	}

	requestID, err := id.NewUUID()
	if err != nil {
		return AdminRequest{}, err
	}

	_, err = r.db.Exec(ctx, `
		INSERT INTO admin_requests(id, user_id, reason)
		VALUES ($1, $2, $3)
	`, requestID, userID, reason)
	if isConstraintViolation(err, "uq_admin_requests_pending_per_user") {
		return AdminRequest{}, ErrPendingAdminRequestExists
	}
	if isForeignKeyViolation(err) {
		return AdminRequest{}, ErrNotFound
	}
	if err != nil {
		return AdminRequest{}, fmt.Errorf("create admin request failed: %w", err)
	}

	return r.getAdminRequestByID(ctx, r.db, requestID)
}

func (r *PostgresRepository) ListAdminRequestsByUser(ctx context.Context, userID, status string) ([]AdminRequest, error) {
	return listAdminRequests(ctx, r.db, "ar.user_id = $1", []any{userID}, status)
}

func (r *PostgresRepository) ListUsers(ctx context.Context) ([]UserSummary, error) {
	rows, err := r.db.Query(ctx, `
		SELECT
			u.id,
			u.username,
			u.is_admin,
			u.storage_quota_bytes,
			COALESCE(files.total_bytes, 0) + COALESCE(shares.total_bytes, 0) AS storage_used_bytes,
			u.upload_bandwidth_kbps,
			u.download_bandwidth_kbps,
			EXISTS(SELECT 1 FROM quota_requests qr WHERE qr.user_id = u.id AND qr.status = 'pending') AS has_pending_quota_request,
			EXISTS(SELECT 1 FROM bandwidth_requests br WHERE br.user_id = u.id AND br.status = 'pending') AS has_pending_bandwidth_request,
			EXISTS(SELECT 1 FROM admin_requests ar WHERE ar.user_id = u.id AND ar.status = 'pending') AS has_pending_admin_request,
			devices.last_active_at,
			u.created_at,
			u.updated_at
		FROM users u
		LEFT JOIN LATERAL (
			SELECT COALESCE(SUM(size_bytes), 0)::bigint AS total_bytes
			FROM file_assets
			WHERE user_id = u.id
		) files ON true
		LEFT JOIN LATERAL (
			SELECT COALESCE(SUM(file_size_bytes), 0)::bigint AS total_bytes
			FROM share_items
			WHERE user_id = u.id
			  AND content_kind = 'file'
			  AND file_stored_path <> ''
		) shares ON true
		LEFT JOIN LATERAL (
			SELECT MAX(last_seen_at) AS last_active_at
			FROM devices
			WHERE user_id = u.id
		) devices ON true
		ORDER BY u.created_at DESC, u.id DESC
	`)
	if err != nil {
		return nil, fmt.Errorf("list admin users failed: %w", err)
	}
	defer rows.Close()

	items := make([]UserSummary, 0)
	for rows.Next() {
		item, err := scanUserSummary(rows)
		if err != nil {
			return nil, fmt.Errorf("scan admin user failed: %w", err)
		}
		items = append(items, item)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate admin users failed: %w", err)
	}
	return items, nil
}

func (r *PostgresRepository) UpdateUser(ctx context.Context, userID string, input UpdateUserInput) (UserSummary, error) {
	if strings.TrimSpace(userID) == "" {
		return UserSummary{}, ErrInvalidArgument
	}

	tx, err := r.db.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return UserSummary{}, fmt.Errorf("begin user update failed: %w", err)
	}
	defer func() {
		_ = tx.Rollback(ctx)
	}()

	var currentIsAdmin bool
	var currentStorageQuota int64
	var currentUploadKbps int
	var currentDownloadKbps int
	err = tx.QueryRow(ctx, `
		SELECT is_admin, storage_quota_bytes, upload_bandwidth_kbps, download_bandwidth_kbps
		FROM users
		WHERE id = $1
		FOR UPDATE
	`, userID).Scan(&currentIsAdmin, &currentStorageQuota, &currentUploadKbps, &currentDownloadKbps)
	if errors.Is(err, pgx.ErrNoRows) {
		return UserSummary{}, ErrNotFound
	}
	if err != nil {
		return UserSummary{}, fmt.Errorf("lock target user failed: %w", err)
	}

	nextStorageQuota := currentStorageQuota
	if input.StorageQuotaBytes != nil {
		nextStorageQuota = *input.StorageQuotaBytes
	}
	nextUploadKbps := currentUploadKbps
	if input.UploadBandwidthKbps != nil {
		nextUploadKbps = *input.UploadBandwidthKbps
	}
	nextDownloadKbps := currentDownloadKbps
	if input.DownloadBandwidthKbps != nil {
		nextDownloadKbps = *input.DownloadBandwidthKbps
	}
	nextIsAdmin := currentIsAdmin
	if input.IsAdmin != nil {
		nextIsAdmin = *input.IsAdmin
	}

	if nextStorageQuota <= 0 || nextUploadKbps <= 0 || nextDownloadKbps <= 0 {
		return UserSummary{}, ErrInvalidArgument
	}

	settings, err := getSystemSettingsWithQueryer(ctx, tx)
	if err != nil {
		return UserSummary{}, err
	}
	if nextUploadKbps > settings.MaxUserUploadBandwidthKbps || nextDownloadKbps > settings.MaxUserDownloadBandwidthKbps {
		return UserSummary{}, ErrInvalidArgument
	}

	if currentIsAdmin && !nextIsAdmin {
		if err := ensureNotLastAdmin(ctx, tx, userID); err != nil {
			return UserSummary{}, err
		}
	}

	_, err = tx.Exec(ctx, `
		UPDATE users
		SET
			is_admin = $2,
			storage_quota_bytes = $3,
			upload_bandwidth_kbps = $4,
			download_bandwidth_kbps = $5,
			updated_at = now()
		WHERE id = $1
	`, userID, nextIsAdmin, nextStorageQuota, nextUploadKbps, nextDownloadKbps)
	if err != nil {
		return UserSummary{}, fmt.Errorf("update user config failed: %w", err)
	}

	item, err := queryUserSummaryByID(ctx, tx, userID)
	if err != nil {
		return UserSummary{}, err
	}
	if err := tx.Commit(ctx); err != nil {
		return UserSummary{}, fmt.Errorf("commit user update failed: %w", err)
	}
	return item, nil
}

func (r *PostgresRepository) DeleteUser(ctx context.Context, userID string) (DeleteUserResult, error) {
	if strings.TrimSpace(userID) == "" {
		return DeleteUserResult{}, ErrInvalidArgument
	}

	tx, err := r.db.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return DeleteUserResult{}, fmt.Errorf("begin user delete failed: %w", err)
	}
	defer func() {
		_ = tx.Rollback(ctx)
	}()

	var isAdmin bool
	err = tx.QueryRow(ctx, `
		SELECT is_admin
		FROM users
		WHERE id = $1
		FOR UPDATE
	`, userID).Scan(&isAdmin)
	if errors.Is(err, pgx.ErrNoRows) {
		return DeleteUserResult{}, ErrNotFound
	}
	if err != nil {
		return DeleteUserResult{}, fmt.Errorf("lock delete user failed: %w", err)
	}

	if isAdmin {
		if err := ensureNotLastAdmin(ctx, tx, userID); err != nil {
			return DeleteUserResult{}, err
		}
	}

	rows, err := tx.Query(ctx, `
		SELECT stored_path
		FROM file_assets
		WHERE user_id = $1
		UNION ALL
		SELECT file_stored_path
		FROM share_items
		WHERE user_id = $1
		  AND content_kind = 'file'
		  AND file_stored_path <> ''
	`, userID)
	if err != nil {
		return DeleteUserResult{}, fmt.Errorf("query stored paths before delete failed: %w", err)
	}

	storedPaths := make([]string, 0)
	for rows.Next() {
		var storedPath string
		if err := rows.Scan(&storedPath); err != nil {
			rows.Close()
			return DeleteUserResult{}, fmt.Errorf("scan stored path failed: %w", err)
		}
		storedPaths = append(storedPaths, storedPath)
	}
	if err := rows.Err(); err != nil {
		rows.Close()
		return DeleteUserResult{}, fmt.Errorf("iterate stored paths failed: %w", err)
	}
	rows.Close()

	commandTag, err := tx.Exec(ctx, `DELETE FROM users WHERE id = $1`, userID)
	if err != nil {
		return DeleteUserResult{}, fmt.Errorf("delete user failed: %w", err)
	}
	if commandTag.RowsAffected() == 0 {
		return DeleteUserResult{}, ErrNotFound
	}

	if err := tx.Commit(ctx); err != nil {
		return DeleteUserResult{}, fmt.Errorf("commit user delete failed: %w", err)
	}
	return DeleteUserResult{
		DeletedUserID: userID,
		StoredPaths:   storedPaths,
	}, nil
}

func (r *PostgresRepository) ListQuotaRequestsForAdmin(ctx context.Context, status string) ([]QuotaRequest, error) {
	return listQuotaRequests(ctx, r.db, "1 = 1", nil, status)
}

func (r *PostgresRepository) ApproveQuotaRequest(ctx context.Context, requestID, reviewerID string, input ApproveQuotaRequestInput) (QuotaRequest, error) {
	tx, err := r.db.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return QuotaRequest{}, fmt.Errorf("begin quota approval failed: %w", err)
	}
	defer func() {
		_ = tx.Rollback(ctx)
	}()

	var userID string
	var requestedQuotaBytes int64
	var currentQuotaBytes int64
	var status string
	err = tx.QueryRow(ctx, `
		SELECT user_id, requested_quota_bytes, current_quota_bytes, status
		FROM quota_requests
		WHERE id = $1
		FOR UPDATE
	`, requestID).Scan(&userID, &requestedQuotaBytes, &currentQuotaBytes, &status)
	if errors.Is(err, pgx.ErrNoRows) {
		return QuotaRequest{}, ErrNotFound
	}
	if err != nil {
		return QuotaRequest{}, fmt.Errorf("lock quota request failed: %w", err)
	}
	if status != StatusPending {
		return QuotaRequest{}, ErrInvalidState
	}

	approvedQuotaBytes := requestedQuotaBytes
	if input.ApprovedQuotaBytes != nil {
		approvedQuotaBytes = *input.ApprovedQuotaBytes
	}
	if approvedQuotaBytes <= currentQuotaBytes {
		return QuotaRequest{}, ErrInvalidArgument
	}

	commandTag, err := tx.Exec(ctx, `
		UPDATE users
		SET storage_quota_bytes = $2, updated_at = now()
		WHERE id = $1
	`, userID, approvedQuotaBytes)
	if err != nil {
		return QuotaRequest{}, fmt.Errorf("update user quota failed: %w", err)
	}
	if commandTag.RowsAffected() == 0 {
		return QuotaRequest{}, ErrNotFound
	}

	_, err = tx.Exec(ctx, `
		UPDATE quota_requests
		SET
			status = $2,
			reviewed_by = $3,
			review_note = $4,
			reviewed_at = now()
		WHERE id = $1
	`, requestID, StatusApproved, reviewerID, input.ReviewNote)
	if err != nil {
		return QuotaRequest{}, fmt.Errorf("mark quota request approved failed: %w", err)
	}

	item, err := r.getQuotaRequestByID(ctx, tx, requestID)
	if err != nil {
		return QuotaRequest{}, err
	}
	if err := tx.Commit(ctx); err != nil {
		return QuotaRequest{}, fmt.Errorf("commit quota approval failed: %w", err)
	}
	return item, nil
}

func (r *PostgresRepository) RejectQuotaRequest(ctx context.Context, requestID, reviewerID string, input RejectRequestInput) (QuotaRequest, error) {
	return r.rejectQuotaRequest(ctx, requestID, reviewerID, input)
}

func (r *PostgresRepository) ListBandwidthRequestsForAdmin(ctx context.Context, status string) ([]BandwidthRequest, error) {
	return listBandwidthRequests(ctx, r.db, "1 = 1", nil, status)
}

func (r *PostgresRepository) ApproveBandwidthRequest(ctx context.Context, requestID, reviewerID string, input ApproveBandwidthRequestInput) (BandwidthRequest, error) {
	tx, err := r.db.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return BandwidthRequest{}, fmt.Errorf("begin bandwidth approval failed: %w", err)
	}
	defer func() {
		_ = tx.Rollback(ctx)
	}()

	var userID string
	var requestedUploadKbps int
	var requestedDownloadKbps int
	var currentUploadKbps int
	var currentDownloadKbps int
	var status string
	err = tx.QueryRow(ctx, `
		SELECT
			user_id,
			requested_upload_kbps,
			requested_download_kbps,
			current_upload_kbps,
			current_download_kbps,
			status
		FROM bandwidth_requests
		WHERE id = $1
		FOR UPDATE
	`, requestID).Scan(&userID, &requestedUploadKbps, &requestedDownloadKbps, &currentUploadKbps, &currentDownloadKbps, &status)
	if errors.Is(err, pgx.ErrNoRows) {
		return BandwidthRequest{}, ErrNotFound
	}
	if err != nil {
		return BandwidthRequest{}, fmt.Errorf("lock bandwidth request failed: %w", err)
	}
	if status != StatusPending {
		return BandwidthRequest{}, ErrInvalidState
	}

	settings, err := getSystemSettingsWithQueryer(ctx, tx)
	if err != nil {
		return BandwidthRequest{}, err
	}

	approvedUploadKbps := requestedUploadKbps
	if input.ApprovedUploadKbps != nil {
		approvedUploadKbps = *input.ApprovedUploadKbps
	}
	approvedDownloadKbps := requestedDownloadKbps
	if input.ApprovedDownloadKbps != nil {
		approvedDownloadKbps = *input.ApprovedDownloadKbps
	}
	if approvedUploadKbps <= 0 || approvedDownloadKbps <= 0 {
		return BandwidthRequest{}, ErrInvalidArgument
	}
	if approvedUploadKbps <= currentUploadKbps && approvedDownloadKbps <= currentDownloadKbps {
		return BandwidthRequest{}, ErrInvalidArgument
	}
	if approvedUploadKbps > settings.MaxUserUploadBandwidthKbps || approvedDownloadKbps > settings.MaxUserDownloadBandwidthKbps {
		return BandwidthRequest{}, ErrInvalidArgument
	}

	commandTag, err := tx.Exec(ctx, `
		UPDATE users
		SET
			upload_bandwidth_kbps = $2,
			download_bandwidth_kbps = $3,
			updated_at = now()
		WHERE id = $1
	`, userID, approvedUploadKbps, approvedDownloadKbps)
	if err != nil {
		return BandwidthRequest{}, fmt.Errorf("update user bandwidth failed: %w", err)
	}
	if commandTag.RowsAffected() == 0 {
		return BandwidthRequest{}, ErrNotFound
	}

	_, err = tx.Exec(ctx, `
		UPDATE bandwidth_requests
		SET
			status = $2,
			reviewed_by = $3,
			review_note = $4,
			reviewed_at = now()
		WHERE id = $1
	`, requestID, StatusApproved, reviewerID, input.ReviewNote)
	if err != nil {
		return BandwidthRequest{}, fmt.Errorf("mark bandwidth request approved failed: %w", err)
	}

	item, err := r.getBandwidthRequestByID(ctx, tx, requestID)
	if err != nil {
		return BandwidthRequest{}, err
	}
	if err := tx.Commit(ctx); err != nil {
		return BandwidthRequest{}, fmt.Errorf("commit bandwidth approval failed: %w", err)
	}
	return item, nil
}

func (r *PostgresRepository) RejectBandwidthRequest(ctx context.Context, requestID, reviewerID string, input RejectRequestInput) (BandwidthRequest, error) {
	tx, err := r.db.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return BandwidthRequest{}, fmt.Errorf("begin bandwidth rejection failed: %w", err)
	}
	defer func() {
		_ = tx.Rollback(ctx)
	}()

	var status string
	err = tx.QueryRow(ctx, `
		SELECT status
		FROM bandwidth_requests
		WHERE id = $1
		FOR UPDATE
	`, requestID).Scan(&status)
	if errors.Is(err, pgx.ErrNoRows) {
		return BandwidthRequest{}, ErrNotFound
	}
	if err != nil {
		return BandwidthRequest{}, fmt.Errorf("lock bandwidth request failed: %w", err)
	}
	if status != StatusPending {
		return BandwidthRequest{}, ErrInvalidState
	}

	_, err = tx.Exec(ctx, `
		UPDATE bandwidth_requests
		SET
			status = $2,
			reviewed_by = $3,
			review_note = $4,
			reviewed_at = now()
		WHERE id = $1
	`, requestID, StatusRejected, reviewerID, input.ReviewNote)
	if err != nil {
		return BandwidthRequest{}, fmt.Errorf("mark bandwidth request rejected failed: %w", err)
	}

	item, err := r.getBandwidthRequestByID(ctx, tx, requestID)
	if err != nil {
		return BandwidthRequest{}, err
	}
	if err := tx.Commit(ctx); err != nil {
		return BandwidthRequest{}, fmt.Errorf("commit bandwidth rejection failed: %w", err)
	}
	return item, nil
}

func (r *PostgresRepository) ListAdminRequestsForAdmin(ctx context.Context, status string) ([]AdminRequest, error) {
	return listAdminRequests(ctx, r.db, "1 = 1", nil, status)
}

func (r *PostgresRepository) ApproveAdminRequest(ctx context.Context, requestID, reviewerID string, input ApproveAdminRequestInput) (AdminRequest, error) {
	tx, err := r.db.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return AdminRequest{}, fmt.Errorf("begin admin request approval failed: %w", err)
	}
	defer func() {
		_ = tx.Rollback(ctx)
	}()

	var userID string
	var status string
	err = tx.QueryRow(ctx, `
		SELECT user_id, status
		FROM admin_requests
		WHERE id = $1
		FOR UPDATE
	`, requestID).Scan(&userID, &status)
	if errors.Is(err, pgx.ErrNoRows) {
		return AdminRequest{}, ErrNotFound
	}
	if err != nil {
		return AdminRequest{}, fmt.Errorf("lock admin request failed: %w", err)
	}
	if status != StatusPending {
		return AdminRequest{}, ErrInvalidState
	}

	commandTag, err := tx.Exec(ctx, `
		UPDATE users
		SET is_admin = true, updated_at = now()
		WHERE id = $1
	`, userID)
	if err != nil {
		return AdminRequest{}, fmt.Errorf("grant admin role failed: %w", err)
	}
	if commandTag.RowsAffected() == 0 {
		return AdminRequest{}, ErrNotFound
	}

	_, err = tx.Exec(ctx, `
		UPDATE admin_requests
		SET
			status = $2,
			reviewed_by = $3,
			review_note = $4,
			reviewed_at = now()
		WHERE id = $1
	`, requestID, StatusApproved, reviewerID, input.ReviewNote)
	if err != nil {
		return AdminRequest{}, fmt.Errorf("mark admin request approved failed: %w", err)
	}

	item, err := r.getAdminRequestByID(ctx, tx, requestID)
	if err != nil {
		return AdminRequest{}, err
	}
	if err := tx.Commit(ctx); err != nil {
		return AdminRequest{}, fmt.Errorf("commit admin request approval failed: %w", err)
	}
	return item, nil
}

func (r *PostgresRepository) RejectAdminRequest(ctx context.Context, requestID, reviewerID string, input RejectRequestInput) (AdminRequest, error) {
	tx, err := r.db.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return AdminRequest{}, fmt.Errorf("begin admin request rejection failed: %w", err)
	}
	defer func() {
		_ = tx.Rollback(ctx)
	}()

	var status string
	err = tx.QueryRow(ctx, `
		SELECT status
		FROM admin_requests
		WHERE id = $1
		FOR UPDATE
	`, requestID).Scan(&status)
	if errors.Is(err, pgx.ErrNoRows) {
		return AdminRequest{}, ErrNotFound
	}
	if err != nil {
		return AdminRequest{}, fmt.Errorf("lock admin request failed: %w", err)
	}
	if status != StatusPending {
		return AdminRequest{}, ErrInvalidState
	}

	_, err = tx.Exec(ctx, `
		UPDATE admin_requests
		SET
			status = $2,
			reviewed_by = $3,
			review_note = $4,
			reviewed_at = now()
		WHERE id = $1
	`, requestID, StatusRejected, reviewerID, input.ReviewNote)
	if err != nil {
		return AdminRequest{}, fmt.Errorf("mark admin request rejected failed: %w", err)
	}

	item, err := r.getAdminRequestByID(ctx, tx, requestID)
	if err != nil {
		return AdminRequest{}, err
	}
	if err := tx.Commit(ctx); err != nil {
		return AdminRequest{}, fmt.Errorf("commit admin request rejection failed: %w", err)
	}
	return item, nil
}

func (r *PostgresRepository) rejectQuotaRequest(ctx context.Context, requestID, reviewerID string, input RejectRequestInput) (QuotaRequest, error) {
	tx, err := r.db.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return QuotaRequest{}, fmt.Errorf("begin quota rejection failed: %w", err)
	}
	defer func() {
		_ = tx.Rollback(ctx)
	}()

	var status string
	err = tx.QueryRow(ctx, `
		SELECT status
		FROM quota_requests
		WHERE id = $1
		FOR UPDATE
	`, requestID).Scan(&status)
	if errors.Is(err, pgx.ErrNoRows) {
		return QuotaRequest{}, ErrNotFound
	}
	if err != nil {
		return QuotaRequest{}, fmt.Errorf("lock quota request failed: %w", err)
	}
	if status != StatusPending {
		return QuotaRequest{}, ErrInvalidState
	}

	_, err = tx.Exec(ctx, `
		UPDATE quota_requests
		SET
			status = $2,
			reviewed_by = $3,
			review_note = $4,
			reviewed_at = now()
		WHERE id = $1
	`, requestID, StatusRejected, reviewerID, input.ReviewNote)
	if err != nil {
		return QuotaRequest{}, fmt.Errorf("mark quota request rejected failed: %w", err)
	}

	item, err := r.getQuotaRequestByID(ctx, tx, requestID)
	if err != nil {
		return QuotaRequest{}, err
	}
	if err := tx.Commit(ctx); err != nil {
		return QuotaRequest{}, fmt.Errorf("commit quota rejection failed: %w", err)
	}
	return item, nil
}

type queryRower interface {
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
}

type queryRunner interface {
	queryRower
	Query(ctx context.Context, sql string, args ...any) (pgx.Rows, error)
}

func listQuotaRequests(ctx context.Context, q queryRunner, baseWhere string, baseArgs []any, status string) ([]QuotaRequest, error) {
	whereSQL, args := appendStatusWhere(baseWhere, baseArgs, "qr.status", status)
	rows, err := q.Query(ctx, `
		SELECT
			qr.id,
			qr.user_id,
			u.username,
			qr.requested_quota_bytes,
			qr.current_quota_bytes,
			qr.reason,
			qr.status,
			COALESCE(qr.reviewed_by::text, ''),
			COALESCE(reviewer.username, ''),
			qr.review_note,
			qr.created_at,
			qr.reviewed_at
		FROM quota_requests qr
		INNER JOIN users u ON u.id = qr.user_id
		LEFT JOIN users reviewer ON reviewer.id = qr.reviewed_by
		WHERE `+whereSQL+`
		ORDER BY qr.created_at DESC, qr.id DESC
	`, args...)
	if err != nil {
		return nil, fmt.Errorf("list quota requests failed: %w", err)
	}
	defer rows.Close()

	items := make([]QuotaRequest, 0)
	for rows.Next() {
		item, err := scanQuotaRequest(rows)
		if err != nil {
			return nil, fmt.Errorf("scan quota request failed: %w", err)
		}
		items = append(items, item)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate quota requests failed: %w", err)
	}
	return items, nil
}

func listBandwidthRequests(ctx context.Context, q queryRunner, baseWhere string, baseArgs []any, status string) ([]BandwidthRequest, error) {
	whereSQL, args := appendStatusWhere(baseWhere, baseArgs, "br.status", status)
	rows, err := q.Query(ctx, `
		SELECT
			br.id,
			br.user_id,
			u.username,
			br.requested_upload_kbps,
			br.requested_download_kbps,
			br.current_upload_kbps,
			br.current_download_kbps,
			br.reason,
			br.status,
			COALESCE(br.reviewed_by::text, ''),
			COALESCE(reviewer.username, ''),
			br.review_note,
			br.created_at,
			br.reviewed_at
		FROM bandwidth_requests br
		INNER JOIN users u ON u.id = br.user_id
		LEFT JOIN users reviewer ON reviewer.id = br.reviewed_by
		WHERE `+whereSQL+`
		ORDER BY br.created_at DESC, br.id DESC
	`, args...)
	if err != nil {
		return nil, fmt.Errorf("list bandwidth requests failed: %w", err)
	}
	defer rows.Close()

	items := make([]BandwidthRequest, 0)
	for rows.Next() {
		item, err := scanBandwidthRequest(rows)
		if err != nil {
			return nil, fmt.Errorf("scan bandwidth request failed: %w", err)
		}
		items = append(items, item)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate bandwidth requests failed: %w", err)
	}
	return items, nil
}

func listAdminRequests(ctx context.Context, q queryRunner, baseWhere string, baseArgs []any, status string) ([]AdminRequest, error) {
	whereSQL, args := appendStatusWhere(baseWhere, baseArgs, "ar.status", status)
	rows, err := q.Query(ctx, `
		SELECT
			ar.id,
			ar.user_id,
			u.username,
			ar.reason,
			ar.status,
			COALESCE(ar.reviewed_by::text, ''),
			COALESCE(reviewer.username, ''),
			ar.review_note,
			ar.created_at,
			ar.reviewed_at
		FROM admin_requests ar
		INNER JOIN users u ON u.id = ar.user_id
		LEFT JOIN users reviewer ON reviewer.id = ar.reviewed_by
		WHERE `+whereSQL+`
		ORDER BY ar.created_at DESC, ar.id DESC
	`, args...)
	if err != nil {
		return nil, fmt.Errorf("list admin requests failed: %w", err)
	}
	defer rows.Close()

	items := make([]AdminRequest, 0)
	for rows.Next() {
		item, err := scanAdminRequest(rows)
		if err != nil {
			return nil, fmt.Errorf("scan admin request failed: %w", err)
		}
		items = append(items, item)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate admin requests failed: %w", err)
	}
	return items, nil
}

func queryUserSummaryByID(ctx context.Context, q queryRower, userID string) (UserSummary, error) {
	item, err := scanUserSummary(q.QueryRow(ctx, `
		SELECT
			u.id,
			u.username,
			u.is_admin,
			u.storage_quota_bytes,
			COALESCE(files.total_bytes, 0) + COALESCE(shares.total_bytes, 0) AS storage_used_bytes,
			u.upload_bandwidth_kbps,
			u.download_bandwidth_kbps,
			EXISTS(SELECT 1 FROM quota_requests qr WHERE qr.user_id = u.id AND qr.status = 'pending') AS has_pending_quota_request,
			EXISTS(SELECT 1 FROM bandwidth_requests br WHERE br.user_id = u.id AND br.status = 'pending') AS has_pending_bandwidth_request,
			EXISTS(SELECT 1 FROM admin_requests ar WHERE ar.user_id = u.id AND ar.status = 'pending') AS has_pending_admin_request,
			devices.last_active_at,
			u.created_at,
			u.updated_at
		FROM users u
		LEFT JOIN LATERAL (
			SELECT COALESCE(SUM(size_bytes), 0)::bigint AS total_bytes
			FROM file_assets
			WHERE user_id = u.id
		) files ON true
		LEFT JOIN LATERAL (
			SELECT COALESCE(SUM(file_size_bytes), 0)::bigint AS total_bytes
			FROM share_items
			WHERE user_id = u.id
			  AND content_kind = 'file'
			  AND file_stored_path <> ''
		) shares ON true
		LEFT JOIN LATERAL (
			SELECT MAX(last_seen_at) AS last_active_at
			FROM devices
			WHERE user_id = u.id
		) devices ON true
		WHERE u.id = $1
	`, userID))
	if errors.Is(err, pgx.ErrNoRows) || errors.Is(err, ErrNotFound) {
		return UserSummary{}, ErrNotFound
	}
	if err != nil {
		return UserSummary{}, fmt.Errorf("query user summary failed: %w", err)
	}
	return item, nil
}

func (r *PostgresRepository) getQuotaRequestByID(ctx context.Context, q queryRower, requestID string) (QuotaRequest, error) {
	item, err := scanQuotaRequest(q.QueryRow(ctx, `
		SELECT
			qr.id,
			qr.user_id,
			u.username,
			qr.requested_quota_bytes,
			qr.current_quota_bytes,
			qr.reason,
			qr.status,
			COALESCE(qr.reviewed_by::text, ''),
			COALESCE(reviewer.username, ''),
			qr.review_note,
			qr.created_at,
			qr.reviewed_at
		FROM quota_requests qr
		INNER JOIN users u ON u.id = qr.user_id
		LEFT JOIN users reviewer ON reviewer.id = qr.reviewed_by
		WHERE qr.id = $1
	`, requestID))
	if errors.Is(err, pgx.ErrNoRows) || errors.Is(err, ErrNotFound) {
		return QuotaRequest{}, ErrNotFound
	}
	if err != nil {
		return QuotaRequest{}, fmt.Errorf("get quota request failed: %w", err)
	}
	return item, nil
}

func (r *PostgresRepository) getBandwidthRequestByID(ctx context.Context, q queryRower, requestID string) (BandwidthRequest, error) {
	item, err := scanBandwidthRequest(q.QueryRow(ctx, `
		SELECT
			br.id,
			br.user_id,
			u.username,
			br.requested_upload_kbps,
			br.requested_download_kbps,
			br.current_upload_kbps,
			br.current_download_kbps,
			br.reason,
			br.status,
			COALESCE(br.reviewed_by::text, ''),
			COALESCE(reviewer.username, ''),
			br.review_note,
			br.created_at,
			br.reviewed_at
		FROM bandwidth_requests br
		INNER JOIN users u ON u.id = br.user_id
		LEFT JOIN users reviewer ON reviewer.id = br.reviewed_by
		WHERE br.id = $1
	`, requestID))
	if errors.Is(err, pgx.ErrNoRows) || errors.Is(err, ErrNotFound) {
		return BandwidthRequest{}, ErrNotFound
	}
	if err != nil {
		return BandwidthRequest{}, fmt.Errorf("get bandwidth request failed: %w", err)
	}
	return item, nil
}

func (r *PostgresRepository) getAdminRequestByID(ctx context.Context, q queryRower, requestID string) (AdminRequest, error) {
	item, err := scanAdminRequest(q.QueryRow(ctx, `
		SELECT
			ar.id,
			ar.user_id,
			u.username,
			ar.reason,
			ar.status,
			COALESCE(ar.reviewed_by::text, ''),
			COALESCE(reviewer.username, ''),
			ar.review_note,
			ar.created_at,
			ar.reviewed_at
		FROM admin_requests ar
		INNER JOIN users u ON u.id = ar.user_id
		LEFT JOIN users reviewer ON reviewer.id = ar.reviewed_by
		WHERE ar.id = $1
	`, requestID))
	if errors.Is(err, pgx.ErrNoRows) || errors.Is(err, ErrNotFound) {
		return AdminRequest{}, ErrNotFound
	}
	if err != nil {
		return AdminRequest{}, fmt.Errorf("get admin request failed: %w", err)
	}
	return item, nil
}

func appendStatusWhere(baseWhere string, baseArgs []any, column, status string) (string, []any) {
	whereSQL := strings.TrimSpace(baseWhere)
	if whereSQL == "" {
		whereSQL = "1 = 1"
	}
	args := append([]any{}, baseArgs...)
	if status == StatusPending || status == StatusApproved || status == StatusRejected {
		args = append(args, status)
		whereSQL += fmt.Sprintf(" AND %s = $%d", column, len(args))
	}
	return whereSQL, args
}

func getSystemSettingsWithQueryer(ctx context.Context, q queryRower) (SystemSettings, error) {
	settings, err := scanSystemSettings(q.QueryRow(ctx, `
		SELECT
			max_user_count,
			default_storage_quota_bytes,
			default_upload_bandwidth_kbps,
			default_download_bandwidth_kbps,
			max_user_upload_bandwidth_kbps,
			max_user_download_bandwidth_kbps,
			max_upload_file_bytes,
			allow_registration,
			updated_at
		FROM system_settings
		WHERE id = true
	`))
	if errors.Is(err, pgx.ErrNoRows) || errors.Is(err, ErrNotFound) {
		return SystemSettings{}, ErrNotFound
	}
	if err != nil {
		return SystemSettings{}, fmt.Errorf("query system settings failed: %w", err)
	}
	return settings, nil
}

func ensureNotLastAdmin(ctx context.Context, tx pgx.Tx, targetUserID string) error {
	rows, err := tx.Query(ctx, `
		SELECT id
		FROM users
		WHERE is_admin = true
		FOR UPDATE
	`)
	if err != nil {
		return fmt.Errorf("lock admin users failed: %w", err)
	}
	defer rows.Close()

	adminCount := 0
	hasTarget := false
	for rows.Next() {
		var adminUserID string
		if err := rows.Scan(&adminUserID); err != nil {
			return fmt.Errorf("scan admin user failed: %w", err)
		}
		adminCount++
		if adminUserID == targetUserID {
			hasTarget = true
		}
	}
	if err := rows.Err(); err != nil {
		return fmt.Errorf("iterate admin users failed: %w", err)
	}
	if hasTarget && adminCount <= 1 {
		return ErrLastAdmin
	}
	return nil
}

type rowScanner interface {
	Scan(dest ...any) error
}

func scanSystemSettings(row rowScanner) (SystemSettings, error) {
	var item SystemSettings
	err := row.Scan(
		&item.MaxUserCount,
		&item.DefaultStorageQuotaBytes,
		&item.DefaultUploadBandwidthKbps,
		&item.DefaultDownloadBandwidthKbps,
		&item.MaxUserUploadBandwidthKbps,
		&item.MaxUserDownloadBandwidthKbps,
		&item.MaxUploadFileBytes,
		&item.AllowRegistration,
		&item.UpdatedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return SystemSettings{}, ErrNotFound
	}
	if err != nil {
		return SystemSettings{}, err
	}
	return item, nil
}

func scanUserSummary(row rowScanner) (UserSummary, error) {
	var item UserSummary
	var storageUsedBytes int64
	err := row.Scan(
		&item.ID,
		&item.Username,
		&item.IsAdmin,
		&item.StorageQuotaBytes,
		&storageUsedBytes,
		&item.UploadBandwidthKbps,
		&item.DownloadBandwidthKbps,
		&item.HasPendingQuotaRequest,
		&item.HasPendingBandwidthRequest,
		&item.HasPendingAdminRequest,
		&item.LastActiveAt,
		&item.CreatedAt,
		&item.UpdatedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return UserSummary{}, ErrNotFound
	}
	if err != nil {
		return UserSummary{}, err
	}
	item.StorageUsedBytes = storageUsedBytes
	item.StorageFreeBytes = safeFreeBytes(item.StorageQuotaBytes, storageUsedBytes)
	return item, nil
}

func scanQuotaRequest(row rowScanner) (QuotaRequest, error) {
	var item QuotaRequest
	err := row.Scan(
		&item.ID,
		&item.UserID,
		&item.Username,
		&item.RequestedQuotaBytes,
		&item.CurrentQuotaBytes,
		&item.Reason,
		&item.Status,
		&item.ReviewedBy,
		&item.ReviewedByUsername,
		&item.ReviewNote,
		&item.CreatedAt,
		&item.ReviewedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return QuotaRequest{}, ErrNotFound
	}
	if err != nil {
		return QuotaRequest{}, err
	}
	return item, nil
}

func scanBandwidthRequest(row rowScanner) (BandwidthRequest, error) {
	var item BandwidthRequest
	err := row.Scan(
		&item.ID,
		&item.UserID,
		&item.Username,
		&item.RequestedUploadKbps,
		&item.RequestedDownloadKbps,
		&item.CurrentUploadKbps,
		&item.CurrentDownloadKbps,
		&item.Reason,
		&item.Status,
		&item.ReviewedBy,
		&item.ReviewedByUsername,
		&item.ReviewNote,
		&item.CreatedAt,
		&item.ReviewedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return BandwidthRequest{}, ErrNotFound
	}
	if err != nil {
		return BandwidthRequest{}, err
	}
	return item, nil
}

func scanAdminRequest(row rowScanner) (AdminRequest, error) {
	var item AdminRequest
	err := row.Scan(
		&item.ID,
		&item.UserID,
		&item.Username,
		&item.Reason,
		&item.Status,
		&item.ReviewedBy,
		&item.ReviewedByUsername,
		&item.ReviewNote,
		&item.CreatedAt,
		&item.ReviewedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return AdminRequest{}, ErrNotFound
	}
	if err != nil {
		return AdminRequest{}, err
	}
	return item, nil
}

func safeFreeBytes(quotaBytes, usedBytes int64) int64 {
	if quotaBytes <= usedBytes {
		return 0
	}
	return quotaBytes - usedBytes
}

func validateSystemSettings(settings SystemSettings) error {
	switch {
	case settings.MaxUserCount <= 0:
		return ErrInvalidArgument
	case settings.DefaultStorageQuotaBytes <= 0:
		return ErrInvalidArgument
	case settings.DefaultUploadBandwidthKbps <= 0:
		return ErrInvalidArgument
	case settings.DefaultDownloadBandwidthKbps <= 0:
		return ErrInvalidArgument
	case settings.MaxUserUploadBandwidthKbps <= 0:
		return ErrInvalidArgument
	case settings.MaxUserDownloadBandwidthKbps <= 0:
		return ErrInvalidArgument
	case settings.MaxUploadFileBytes <= 0:
		return ErrInvalidArgument
	case settings.DefaultUploadBandwidthKbps > settings.MaxUserUploadBandwidthKbps:
		return ErrInvalidArgument
	case settings.DefaultDownloadBandwidthKbps > settings.MaxUserDownloadBandwidthKbps:
		return ErrInvalidArgument
	default:
		return nil
	}
}

func isConstraintViolation(err error, name string) bool {
	var pgErr *pgconn.PgError
	if !errors.As(err, &pgErr) {
		return false
	}
	return pgErr.Code == "23505" && pgErr.ConstraintName == name
}

func isForeignKeyViolation(err error) bool {
	var pgErr *pgconn.PgError
	return errors.As(err, &pgErr) && pgErr.Code == "23503"
}
