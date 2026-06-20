package com.xushuangbo.clipbridge.core.share

import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class PublicShareEncryption(
    val version: String = "",
    val kdf: String = "",
    val iterations: Int = 0,
    val salt: String = "",
    val nonce: String = "",
    val cipher: String = "",
)

object PublicShareCrypto {
    fun decryptText(
        encryptedPayload: String,
        encryption: PublicShareEncryption,
        password: String,
    ): String {
        val payloadBytes = decodeBase64(encryptedPayload)
        val saltBytes = decodeBase64(encryption.salt)
        val nonceBytes = decodeBase64(encryption.nonce)
        val iterationCount = encryption.iterations.coerceAtLeast(1)

        val passwordKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(
                PBEKeySpec(
                    password.toCharArray(),
                    saltBytes,
                    iterationCount,
                    256,
                ),
            )

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(passwordKey.encoded, "AES"),
            GCMParameterSpec(128, nonceBytes),
        )

        return cipher.doFinal(payloadBytes).toString(StandardCharsets.UTF_8)
    }

    private fun decodeBase64(value: String): ByteArray {
        return Base64.decode(value.trim(), Base64.DEFAULT)
    }
}
