package com.xushuangbo.clipbridge.windows.crypto;

import com.xushuangbo.clipbridge.windows.api.ApiModels.EncryptionMeta;
import com.xushuangbo.clipbridge.windows.api.ApiModels.FileE2EEMeta;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public final class E2EECrypto {
    public static final String VERSION = "e2ee-v1";
    public static final String KDF = "PBKDF2-HMAC-SHA256";
    public static final String CIPHER = "AES-256-GCM";
    public static final int ITERATIONS = 210_000;

    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final int NONCE_BYTES = 12;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();

    private E2EECrypto() {}

    public record EncryptedClipboardPayload(String encryptedPayload, EncryptionMeta encryption) {}

    public record EncryptedFilePayload(byte[] cipherBytes, FileE2EEMeta meta) {}

    public record DecryptedFilePayload(String fileName, String mimeType, byte[] plainBytes) {}

    public record DecryptedFileMetadata(String fileName, String mimeType) {}

    public static EncryptedClipboardPayload encryptClipboardText(String text, String passphrase) {
        try {
            ObjectNode payload = MAPPER.createObjectNode();
            payload.put("kind", "text");
            payload.put("text", text == null ? "" : text);

            byte[] salt = randomBytes(SALT_BYTES);
            byte[] nonce = randomBytes(NONCE_BYTES);
            SecretKeySpec key = deriveKey(passphrase, salt, ITERATIONS);
            byte[] cipherBytes = aesGcmEncrypt(payload.toString().getBytes(StandardCharsets.UTF_8), key, nonce);

            EncryptionMeta encryption = new EncryptionMeta(
                VERSION,
                KDF,
                ITERATIONS,
                base64Encode(salt),
                base64Encode(nonce),
                CIPHER
            );
            return new EncryptedClipboardPayload(base64Encode(cipherBytes), encryption);
        } catch (Exception e) {
            throw new IllegalArgumentException("文本加密失败: " + e.getMessage(), e);
        }
    }

    public static String decryptClipboardText(String encryptedPayload, EncryptionMeta encryption, String passphrase) {
        try {
            requireEncryption(encryption);
            SecretKeySpec key = deriveKey(passphrase, base64Decode(encryption.salt()), encryption.iterations());
            byte[] plainBytes = aesGcmDecrypt(base64Decode(encryptedPayload), key, base64Decode(encryption.nonce()));
            JsonNode payload = MAPPER.readTree(plainBytes);
            return payload.path("text").asText("");
        } catch (Exception e) {
            throw new IllegalArgumentException("解密失败：口令错误或数据损坏", e);
        }
    }

    public static EncryptedFilePayload encryptFileBytes(byte[] plainBytes, String fileName, String mimeType, String passphrase) {
        try {
            byte[] safePlain = plainBytes == null ? new byte[0] : plainBytes;
            byte[] salt = randomBytes(SALT_BYTES);
            byte[] payloadNonce = randomBytes(NONCE_BYTES);
            byte[] metaNonce = randomBytes(NONCE_BYTES);
            SecretKeySpec key = deriveKey(passphrase, salt, ITERATIONS);

            byte[] cipherBytes = aesGcmEncrypt(safePlain, key, payloadNonce);

            // 中文注释：文件名和 MIME 也属于敏感信息，必须和文件体一样在客户端加密。
            ObjectNode meta = MAPPER.createObjectNode();
            meta.put("name", fileName == null ? "download.bin" : fileName);
            meta.put("mime", mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType);
            byte[] encryptedMetadata = aesGcmEncrypt(meta.toString().getBytes(StandardCharsets.UTF_8), key, metaNonce);

            FileE2EEMeta e2eeMeta = new FileE2EEMeta(
                VERSION,
                KDF,
                ITERATIONS,
                base64Encode(salt),
                base64Encode(payloadNonce),
                base64Encode(metaNonce),
                CIPHER,
                base64Encode(encryptedMetadata)
            );
            return new EncryptedFilePayload(cipherBytes, e2eeMeta);
        } catch (Exception e) {
            throw new IllegalArgumentException("文件加密失败: " + e.getMessage(), e);
        }
    }

    public static DecryptedFileMetadata decryptFileMetadata(FileE2EEMeta meta, String passphrase) {
        try {
            requireFileMeta(meta);
            SecretKeySpec key = deriveKey(passphrase, base64Decode(meta.salt()), meta.iterations());
            byte[] metaPlain = aesGcmDecrypt(base64Decode(meta.encryptedMetadata()), key, base64Decode(meta.metaNonce()));
            JsonNode obj = MAPPER.readTree(metaPlain);
            return new DecryptedFileMetadata(
                obj.path("name").asText("download.bin"),
                obj.path("mime").asText("application/octet-stream")
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("解密失败：口令错误或数据损坏", e);
        }
    }

    public static DecryptedFilePayload decryptFileBytes(byte[] cipherBytes, FileE2EEMeta meta, String passphrase) {
        try {
            DecryptedFileMetadata fileMeta = decryptFileMetadata(meta, passphrase);
            SecretKeySpec key = deriveKey(passphrase, base64Decode(meta.salt()), meta.iterations());
            byte[] plainBytes = aesGcmDecrypt(
                cipherBytes == null ? new byte[0] : cipherBytes,
                key,
                base64Decode(meta.payloadNonce())
            );
            return new DecryptedFilePayload(fileMeta.fileName(), fileMeta.mimeType(), plainBytes);
        } catch (Exception e) {
            throw new IllegalArgumentException("解密失败：口令错误或数据损坏", e);
        }
    }

    private static SecretKeySpec deriveKey(String passphrase, byte[] salt, int iterations) throws Exception {
        if (passphrase == null || passphrase.isBlank()) {
            throw new IllegalArgumentException("未设置端到端加密口令");
        }
        int safeIterations = iterations > 0 ? iterations : ITERATIONS;
        PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, safeIterations, KEY_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] key = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(key, "AES");
    }

    private static byte[] aesGcmEncrypt(byte[] plain, SecretKeySpec key, byte[] nonce) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        return cipher.doFinal(plain);
    }

    private static byte[] aesGcmDecrypt(byte[] cipherBytes, SecretKeySpec key, byte[] nonce) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        return cipher.doFinal(cipherBytes);
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static String base64Encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] base64Decode(String text) {
        return Base64.getDecoder().decode(text == null ? "" : text);
    }

    private static void requireEncryption(EncryptionMeta encryption) {
        if (encryption == null) {
            throw new IllegalArgumentException("加密参数缺失");
        }
        if (!VERSION.equals(encryption.version()) || !KDF.equals(encryption.kdf()) || !CIPHER.equals(encryption.cipher())) {
            throw new IllegalArgumentException("不支持的加密参数");
        }
        if (encryption.iterations() <= 0 || isBlank(encryption.salt()) || isBlank(encryption.nonce())) {
            throw new IllegalArgumentException("加密参数不完整");
        }
    }

    private static void requireFileMeta(FileE2EEMeta meta) {
        if (meta == null) {
            throw new IllegalArgumentException("文件加密元数据缺失");
        }
        if (!VERSION.equals(meta.version()) || !KDF.equals(meta.kdf()) || !CIPHER.equals(meta.cipher())) {
            throw new IllegalArgumentException("不支持的加密参数");
        }
        if (meta.iterations() <= 0 ||
            isBlank(meta.salt()) ||
            isBlank(meta.payloadNonce()) ||
            isBlank(meta.metaNonce()) ||
            isBlank(meta.encryptedMetadata())) {
            throw new IllegalArgumentException("文件加密元数据不完整");
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}

