-- 第二步在不改动表结构主干的前提下，补上文本同步最小链路常用索引。
-- 这样做可以让“最新一条去重判断”“按序补拉”“ACK 状态读取”都更稳定。

CREATE INDEX IF NOT EXISTS idx_clipboard_items_user_hash_seq_desc
    ON clipboard_items(user_id, content_hash, seq DESC);

CREATE INDEX IF NOT EXISTS idx_device_sync_state_user_device_ack
    ON device_sync_state(user_id, device_id, last_ack_seq);
