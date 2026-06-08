-- 第四步补上分享能力的最小表结构。
-- 这一版先把“文本/文件分享 + 公开取件 + 过期/撤销/焚毁 + 可选加密”落到一张表里，
-- 优先保证流程清晰，避免一开始拆太多表导致理解成本过高。

CREATE TABLE IF NOT EXISTS share_items (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    public_token text NOT NULL UNIQUE,
    content_kind text NOT NULL,

    text_content text NOT NULL DEFAULT '',
    text_preview text NOT NULL DEFAULT '',
    encrypted_payload text NOT NULL DEFAULT '',

    is_encrypted boolean NOT NULL DEFAULT false,
    password_hash text NOT NULL DEFAULT '',
    encryption_version text NOT NULL DEFAULT '',
    encryption_kdf text NOT NULL DEFAULT '',
    encryption_iterations integer NOT NULL DEFAULT 0,
    encryption_salt text NOT NULL DEFAULT '',
    encryption_nonce text NOT NULL DEFAULT '',
    encryption_cipher text NOT NULL DEFAULT '',

    file_original_name text NOT NULL DEFAULT '',
    file_stored_path text NOT NULL DEFAULT '',
    file_content_type text NOT NULL DEFAULT '',
    file_size_bytes bigint NOT NULL DEFAULT 0,
    file_sha256 text NOT NULL DEFAULT '',

    allow_copy_content boolean NOT NULL DEFAULT false,
    burn_mode text NOT NULL DEFAULT 'none',
    burn_after_seconds integer NOT NULL DEFAULT 0,
    expires_at timestamptz,
    first_opened_at timestamptz,
    burn_deadline timestamptz,
    consumed_at timestamptz,
    revoked_at timestamptz,
    open_count bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT share_items_content_kind_ck CHECK (content_kind IN ('text', 'file')),
    CONSTRAINT share_items_burn_mode_ck CHECK (burn_mode IN ('none', 'once', 'countdown')),
    CONSTRAINT share_items_burn_seconds_ck CHECK (
        (burn_mode = 'countdown' AND burn_after_seconds > 0)
        OR
        (burn_mode <> 'countdown' AND burn_after_seconds = 0)
    ),
    CONSTRAINT share_items_file_size_non_negative_ck CHECK (file_size_bytes >= 0),
    CONSTRAINT share_items_open_count_non_negative_ck CHECK (open_count >= 0),
    CONSTRAINT share_items_encryption_required_ck CHECK (
        (NOT is_encrypted)
        OR (
            password_hash <> ''
            AND encryption_version <> ''
            AND encryption_kdf <> ''
            AND encryption_iterations > 0
            AND encryption_salt <> ''
            AND encryption_nonce <> ''
            AND encryption_cipher <> ''
        )
    ),
    CONSTRAINT share_items_text_payload_ck CHECK (
        content_kind <> 'text'
        OR (
            (NOT is_encrypted AND text_content <> '')
            OR (is_encrypted AND encrypted_payload <> '')
        )
    ),
    CONSTRAINT share_items_file_payload_ck CHECK (
        content_kind <> 'file'
        OR (
            file_stored_path <> ''
            AND file_original_name <> ''
            AND file_content_type <> ''
            AND file_size_bytes > 0
            AND file_sha256 <> ''
        )
    )
);

CREATE INDEX IF NOT EXISTS idx_share_items_user_created_desc
    ON share_items(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_share_items_user_status
    ON share_items(user_id, revoked_at, consumed_at, expires_at, burn_deadline);

CREATE INDEX IF NOT EXISTS idx_share_items_expires_at
    ON share_items(expires_at)
    WHERE expires_at IS NOT NULL;
