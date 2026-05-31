-- 第三步补上文件中转所需的最小表结构。
-- 文件体落在磁盘，数据库只保存元数据、来源设备快照和检索索引。

CREATE TABLE IF NOT EXISTS file_assets (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    original_name text NOT NULL,
    stored_path text NOT NULL,
    content_type text NOT NULL,
    size_bytes bigint NOT NULL,
    file_sha256 text NOT NULL,
    origin_device_id uuid REFERENCES devices(id) ON DELETE SET NULL,
    origin_device_name text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT file_assets_size_positive_ck CHECK (size_bytes > 0)
);

CREATE INDEX IF NOT EXISTS idx_file_assets_user_created_desc
    ON file_assets(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_file_assets_user_sha256
    ON file_assets(user_id, file_sha256);
