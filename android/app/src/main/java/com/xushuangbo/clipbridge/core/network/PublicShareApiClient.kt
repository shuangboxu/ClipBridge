package com.xushuangbo.clipbridge.core.network

import com.xushuangbo.clipbridge.BuildConfig
import com.xushuangbo.clipbridge.core.share.PublicShareEncryption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class PublicShareFileRecord(
    val id: String,
    val originalName: String,
    val contentType: String,
    val sizeBytes: Long,
    val isImage: Boolean,
    val isVideo: Boolean,
)

data class PublicShareRecord(
    val token: String,
    val status: String,
    val contentKind: String,
    val hasTextContent: Boolean,
    val hasFileContent: Boolean,
    val isEncrypted: Boolean,
    val requiresPassword: Boolean,
    val textPreview: String,
    val allowCopyContent: Boolean,
    val files: List<PublicShareFileRecord>,
    val burnMode: String,
    val burnAfterSeconds: Int,
    val remainingSeconds: Long,
    val expiresAt: String,
    val firstOpenedAt: String,
    val burnDeadline: String,
    val consumedAt: String,
    val revokedAt: String,
    val openCount: Long,
    val createdAt: String,
)

data class PublicShareOpenResult(
    val share: PublicShareRecord,
    val textContent: String,
    val encryptedPayload: String,
    val encryption: PublicShareEncryption?,
    val accessToken: String,
    val accessTokenExpiresAt: String,
)

interface PublicShareApiClient {
    suspend fun getMeta(
        baseUrl: String,
        token: String,
    ): PublicShareRecord

    suspend fun openShare(
        baseUrl: String,
        token: String,
        password: String,
    ): PublicShareOpenResult
}

class HttpPublicShareApiClient(
    private val httpClient: OkHttpClient = buildHttpClient(),
) : PublicShareApiClient {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun getMeta(
        baseUrl: String,
        token: String,
    ): PublicShareRecord = withContext(Dispatchers.IO) {
        val encodedToken = encodePathSegment(token.trim())
        val responseData = requestData(
            baseUrl = baseUrl,
            method = "GET",
            path = "/v1/public/shares/$encodedToken/meta",
        )
        parsePublicShareRecord(responseData.getJSONObject("share"))
    }

    override suspend fun openShare(
        baseUrl: String,
        token: String,
        password: String,
    ): PublicShareOpenResult = withContext(Dispatchers.IO) {
        val encodedToken = encodePathSegment(token.trim())
        val requestBody = JSONObject().apply {
            if (password.isNotBlank()) {
                put("password", password)
            }
        }

        val responseData = requestData(
            baseUrl = baseUrl,
            method = "POST",
            path = "/v1/public/shares/$encodedToken/open",
            jsonBody = requestBody,
        )

        PublicShareOpenResult(
            share = parsePublicShareRecord(responseData.getJSONObject("share")),
            textContent = responseData.optJSONObject("share")?.optString("text_content").orEmpty(),
            encryptedPayload = responseData.optJSONObject("share")?.optString("encrypted_payload").orEmpty(),
            encryption = responseData.optJSONObject("share")
                ?.optJSONObject("encryption")
                ?.let(::parsePublicShareEncryption),
            accessToken = responseData.optString("access_token"),
            accessTokenExpiresAt = responseData.optString("access_token_expires_at"),
        )
    }

    private fun requestData(
        baseUrl: String,
        method: String,
        path: String,
        jsonBody: JSONObject? = null,
    ): JSONObject {
        val requestBuilder = Request.Builder()
            .url(baseUrl + path)

        if (jsonBody != null) {
            requestBuilder.header("Content-Type", "application/json")
        }

        when (method) {
            "GET" -> requestBuilder.get()
            "POST" -> requestBuilder.post((jsonBody?.toString() ?: "{}").toRequestBody(jsonMediaType))
            else -> error("Unsupported method: $method")
        }

        try {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    throw IOException(extractErrorMessage(bodyText, response.code))
                }

                val responseJson = parseJson(bodyText)
                if (responseJson.optInt("code", -1) != 0) {
                    throw IOException(responseJson.optString("message", "请求失败"))
                }

                return responseJson.optJSONObject("data") ?: JSONObject()
            }
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("网络异常，请检查服务地址和网络连接", error)
        }
    }

    private fun parsePublicShareRecord(data: JSONObject): PublicShareRecord {
        return PublicShareRecord(
            token = data.optString("token"),
            status = data.optString("status"),
            contentKind = data.optString("content_kind"),
            hasTextContent = data.optBoolean("has_text_content", false),
            hasFileContent = data.optBoolean("has_file_content", false),
            isEncrypted = data.optBoolean("is_encrypted", false),
            requiresPassword = data.optBoolean("requires_password", false),
            textPreview = data.optString("text_preview"),
            allowCopyContent = data.optBoolean("allow_copy_content", false),
            files = parsePublicShareFiles(data.optJSONArray("files")),
            burnMode = data.optString("burn_mode"),
            burnAfterSeconds = data.optInt("burn_after_seconds", 0),
            remainingSeconds = data.optLong("remaining_seconds", 0L),
            expiresAt = data.optString("expires_at"),
            firstOpenedAt = data.optString("first_opened_at"),
            burnDeadline = data.optString("burn_deadline"),
            consumedAt = data.optString("consumed_at"),
            revokedAt = data.optString("revoked_at"),
            openCount = data.optLong("open_count", 0L),
            createdAt = data.optString("created_at"),
        )
    }

    private fun parsePublicShareFiles(items: JSONArray?): List<PublicShareFileRecord> {
        if (items == null) {
            return emptyList()
        }

        val files = mutableListOf<PublicShareFileRecord>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            files += PublicShareFileRecord(
                id = item.optString("id"),
                originalName = item.optString("original_name"),
                contentType = item.optString("content_type"),
                sizeBytes = item.optLong("size_bytes", 0L),
                isImage = item.optBoolean("is_image", false),
                isVideo = item.optBoolean("is_video", false),
            )
        }
        return files
    }

    private fun parsePublicShareEncryption(data: JSONObject): PublicShareEncryption {
        return PublicShareEncryption(
            version = data.optString("version"),
            kdf = data.optString("kdf"),
            iterations = data.optInt("iterations", 0),
            salt = data.optString("salt"),
            nonce = data.optString("nonce"),
            cipher = data.optString("cipher"),
        )
    }

    private fun parseJson(bodyText: String): JSONObject {
        if (bodyText.isBlank()) {
            return JSONObject()
        }

        return try {
            JSONObject(bodyText)
        } catch (error: Exception) {
            throw IOException("服务端返回了非 JSON 响应", error)
        }
    }

    private fun extractErrorMessage(
        bodyText: String,
        httpCode: Int,
    ): String {
        if (bodyText.isBlank()) {
            return "请求失败（HTTP $httpCode）"
        }

        return try {
            val responseJson = JSONObject(bodyText)
            responseJson.optString("message").ifBlank { "请求失败（HTTP $httpCode）" }
        } catch (_: Exception) {
            "请求失败（HTTP $httpCode）"
        }
    }

    companion object {
        private fun encodePathSegment(value: String): String {
            return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
        }

        fun buildHttpClient(): OkHttpClient {
            val loggingInterceptor = HttpLoggingInterceptor()
            loggingInterceptor.level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }

            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)
                .build()
        }
    }
}
