-- 第五步把文件分享扩展成“文件 + 文字说明”。
-- 文字部分允许单独保存自己的加密元数据，避免和文件部分共用 nonce/salt。

ALTER TABLE share_items
    ADD COLUMN IF NOT EXISTS text_encryption_version text NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS text_encryption_kdf text NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS text_encryption_iterations integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS text_encryption_salt text NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS text_encryption_nonce text NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS text_encryption_cipher text NOT NULL DEFAULT '';

-- 兼容上一版已经生成的“纯文本加密分享”。
-- 旧数据的加密元数据原本放在通用字段里，这里复制到文本专用字段，方便后续统一读取。
UPDATE share_items
SET
    text_encryption_version = CASE
        WHEN text_encryption_version = '' THEN encryption_version
        ELSE text_encryption_version
    END,
    text_encryption_kdf = CASE
        WHEN text_encryption_kdf = '' THEN encryption_kdf
        ELSE text_encryption_kdf
    END,
    text_encryption_iterations = CASE
        WHEN text_encryption_iterations = 0 THEN encryption_iterations
        ELSE text_encryption_iterations
    END,
    text_encryption_salt = CASE
        WHEN text_encryption_salt = '' THEN encryption_salt
        ELSE text_encryption_salt
    END,
    text_encryption_nonce = CASE
        WHEN text_encryption_nonce = '' THEN encryption_nonce
        ELSE text_encryption_nonce
    END,
    text_encryption_cipher = CASE
        WHEN text_encryption_cipher = '' THEN encryption_cipher
        ELSE text_encryption_cipher
    END
WHERE content_kind = 'text'
  AND is_encrypted = true
  AND encrypted_payload <> '';
