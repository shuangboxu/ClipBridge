-- 第一阶段先建立最核心的五张业务表。
-- 这样做的目的是让后续“认证与设备基础”“文本同步最小链路”可以直接在这套表上继续扩展。

CREATE TABLE IF NOT EXISTS users (
    id uuid PRIMARY KEY,
    username text NOT NULL UNIQUE,
    password_hash text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS devices (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform text NOT NULL,
    device_name text NOT NULL,
    last_seen_at timestamptz NOT NULL DEFAULT now(),
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS auth_refresh_tokens (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id uuid NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    token_hash text NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS user_sync_counters (
    user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    current_seq bigint NOT NULL
);

CREATE TABLE IF NOT EXISTS clipboard_items (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    seq bigint NOT NULL,
    content_type text NOT NULL,
    text_content text,
    image_path text,
    image_sha256 text,
    content_hash text NOT NULL,
    origin_device_id uuid NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT clipboard_content_type_ck CHECK (content_type IN ('text', 'image')),
    CONSTRAINT clipboard_user_seq_unique UNIQUE (user_id, seq)
);

CREATE TABLE IF NOT EXISTS device_sync_state (
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id uuid NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    last_ack_seq bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, device_id)
);

CREATE INDEX IF NOT EXISTS idx_devices_user_active
    ON devices(user_id, is_active);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_device_revoked
    ON auth_refresh_tokens(user_id, device_id, revoked_at);

CREATE INDEX IF NOT EXISTS idx_clipboard_items_user_seq_desc
    ON clipboard_items(user_id, seq DESC);

CREATE INDEX IF NOT EXISTS idx_clipboard_items_user_created_desc
    ON clipboard_items(user_id, created_at DESC);
