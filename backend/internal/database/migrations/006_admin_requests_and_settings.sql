-- 第六步补上“申请 / 额度 / 管理员”所需的数据库结构。
-- 这一版先把用户额度字段、系统设置表和三类申请表落稳，
-- 后续接口、审批和 Web 控制台都会围绕这些表展开。

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS is_admin boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS storage_quota_bytes bigint NOT NULL DEFAULT 104857600,
    ADD COLUMN IF NOT EXISTS upload_bandwidth_kbps integer NOT NULL DEFAULT 2048,
    ADD COLUMN IF NOT EXISTS download_bandwidth_kbps integer NOT NULL DEFAULT 4096;

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS users_storage_quota_positive_ck;
ALTER TABLE users
    ADD CONSTRAINT users_storage_quota_positive_ck CHECK (storage_quota_bytes > 0);

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS users_upload_bandwidth_positive_ck;
ALTER TABLE users
    ADD CONSTRAINT users_upload_bandwidth_positive_ck CHECK (upload_bandwidth_kbps > 0);

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS users_download_bandwidth_positive_ck;
ALTER TABLE users
    ADD CONSTRAINT users_download_bandwidth_positive_ck CHECK (download_bandwidth_kbps > 0);

CREATE TABLE IF NOT EXISTS system_settings (
    id boolean PRIMARY KEY DEFAULT true CHECK (id),
    max_user_count integer NOT NULL,
    default_storage_quota_bytes bigint NOT NULL,
    default_upload_bandwidth_kbps integer NOT NULL,
    default_download_bandwidth_kbps integer NOT NULL,
    max_user_upload_bandwidth_kbps integer NOT NULL,
    max_user_download_bandwidth_kbps integer NOT NULL,
    max_upload_file_bytes bigint NOT NULL,
    allow_registration boolean NOT NULL DEFAULT false,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT system_settings_max_user_count_positive_ck CHECK (max_user_count > 0),
    CONSTRAINT system_settings_default_storage_quota_positive_ck CHECK (default_storage_quota_bytes > 0),
    CONSTRAINT system_settings_default_upload_bandwidth_positive_ck CHECK (default_upload_bandwidth_kbps > 0),
    CONSTRAINT system_settings_default_download_bandwidth_positive_ck CHECK (default_download_bandwidth_kbps > 0),
    CONSTRAINT system_settings_max_user_upload_bandwidth_positive_ck CHECK (max_user_upload_bandwidth_kbps > 0),
    CONSTRAINT system_settings_max_user_download_bandwidth_positive_ck CHECK (max_user_download_bandwidth_kbps > 0),
    CONSTRAINT system_settings_max_upload_file_positive_ck CHECK (max_upload_file_bytes > 0)
);

CREATE TABLE IF NOT EXISTS quota_requests (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    requested_quota_bytes bigint NOT NULL,
    current_quota_bytes bigint NOT NULL,
    reason text NOT NULL DEFAULT '',
    status text NOT NULL DEFAULT 'pending',
    reviewed_by uuid REFERENCES users(id) ON DELETE SET NULL,
    review_note text NOT NULL DEFAULT '',
    created_at timestamptz NOT NULL DEFAULT now(),
    reviewed_at timestamptz,
    CONSTRAINT quota_requests_requested_positive_ck CHECK (requested_quota_bytes > 0),
    CONSTRAINT quota_requests_current_positive_ck CHECK (current_quota_bytes > 0),
    CONSTRAINT quota_requests_status_ck CHECK (status IN ('pending', 'approved', 'rejected'))
);

CREATE INDEX IF NOT EXISTS idx_quota_requests_user_created_desc
    ON quota_requests(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_quota_requests_status_created_desc
    ON quota_requests(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_quota_requests_reviewed_at
    ON quota_requests(reviewed_at)
    WHERE reviewed_at IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_quota_requests_pending_per_user
    ON quota_requests(user_id)
    WHERE status = 'pending';

CREATE TABLE IF NOT EXISTS bandwidth_requests (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    requested_upload_kbps integer NOT NULL,
    requested_download_kbps integer NOT NULL,
    current_upload_kbps integer NOT NULL,
    current_download_kbps integer NOT NULL,
    reason text NOT NULL DEFAULT '',
    status text NOT NULL DEFAULT 'pending',
    reviewed_by uuid REFERENCES users(id) ON DELETE SET NULL,
    review_note text NOT NULL DEFAULT '',
    created_at timestamptz NOT NULL DEFAULT now(),
    reviewed_at timestamptz,
    CONSTRAINT bandwidth_requests_requested_upload_positive_ck CHECK (requested_upload_kbps > 0),
    CONSTRAINT bandwidth_requests_requested_download_positive_ck CHECK (requested_download_kbps > 0),
    CONSTRAINT bandwidth_requests_current_upload_positive_ck CHECK (current_upload_kbps > 0),
    CONSTRAINT bandwidth_requests_current_download_positive_ck CHECK (current_download_kbps > 0),
    CONSTRAINT bandwidth_requests_status_ck CHECK (status IN ('pending', 'approved', 'rejected'))
);

CREATE INDEX IF NOT EXISTS idx_bandwidth_requests_user_created_desc
    ON bandwidth_requests(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_bandwidth_requests_status_created_desc
    ON bandwidth_requests(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_bandwidth_requests_reviewed_at
    ON bandwidth_requests(reviewed_at)
    WHERE reviewed_at IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_bandwidth_requests_pending_per_user
    ON bandwidth_requests(user_id)
    WHERE status = 'pending';

CREATE TABLE IF NOT EXISTS admin_requests (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason text NOT NULL DEFAULT '',
    status text NOT NULL DEFAULT 'pending',
    reviewed_by uuid REFERENCES users(id) ON DELETE SET NULL,
    review_note text NOT NULL DEFAULT '',
    created_at timestamptz NOT NULL DEFAULT now(),
    reviewed_at timestamptz,
    CONSTRAINT admin_requests_status_ck CHECK (status IN ('pending', 'approved', 'rejected'))
);

CREATE INDEX IF NOT EXISTS idx_admin_requests_user_created_desc
    ON admin_requests(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_admin_requests_status_created_desc
    ON admin_requests(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_admin_requests_reviewed_at
    ON admin_requests(reviewed_at)
    WHERE reviewed_at IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_admin_requests_pending_per_user
    ON admin_requests(user_id)
    WHERE status = 'pending';
