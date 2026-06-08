-- 第七步把分享文件拆成子表，支持一次分享多个文件。
-- 旧数据仍然保留在 share_items.file_* 字段里，代码会自动回退兼容，
-- 因此这里不做强制迁移，先保证线上平滑升级。

CREATE TABLE IF NOT EXISTS share_files (
    id uuid PRIMARY KEY,
    share_id uuid NOT NULL REFERENCES share_items(id) ON DELETE CASCADE,
    sort_order integer NOT NULL DEFAULT 0,
    original_name text NOT NULL,
    stored_path text NOT NULL,
    content_type text NOT NULL DEFAULT '',
    size_bytes bigint NOT NULL,
    sha256 text NOT NULL,
    encryption_version text NOT NULL DEFAULT '',
    encryption_kdf text NOT NULL DEFAULT '',
    encryption_iterations integer NOT NULL DEFAULT 0,
    encryption_salt text NOT NULL DEFAULT '',
    encryption_nonce text NOT NULL DEFAULT '',
    encryption_cipher text NOT NULL DEFAULT '',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT share_files_sort_order_non_negative_ck CHECK (sort_order >= 0),
    CONSTRAINT share_files_size_positive_ck CHECK (size_bytes > 0)
);

CREATE INDEX IF NOT EXISTS idx_share_files_share_sort
    ON share_files(share_id, sort_order, id);
