-- 文本历史支持软删除和用户级保留策略。
-- deleted_at 不物理删除记录，方便后续排查误删、同步游标和历史问题。

ALTER TABLE clipboard_items
    ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

CREATE TABLE IF NOT EXISTS clipboard_history_settings (
    user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    retention_days int NOT NULL DEFAULT 0,
    history_limit int NOT NULL DEFAULT 1000,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT clipboard_history_settings_retention_days_ck CHECK (retention_days >= 0),
    CONSTRAINT clipboard_history_settings_history_limit_ck CHECK (history_limit > 0)
);

CREATE INDEX IF NOT EXISTS idx_clipboard_items_user_seq_desc_active
    ON clipboard_items(user_id, seq DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_clipboard_items_user_created_desc_active
    ON clipboard_items(user_id, created_at DESC)
    WHERE deleted_at IS NULL;
