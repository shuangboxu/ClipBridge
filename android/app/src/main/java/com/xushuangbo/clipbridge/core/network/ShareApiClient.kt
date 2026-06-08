package com.xushuangbo.clipbridge.core.network

import com.xushuangbo.clipbridge.BuildConfig
import com.xushuangbo.clipbridge.core.share.SharePolicyPayload
import com.xushuangbo.clipbridge.core.share.ShareStatusFilter
import com.xushuangbo.clipbridge.core.session.StoredSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class ShareFileRecord(
    val originalName: String,
    val contentType: String,
    val sizeBytes: Long,
    val isImage: Boolean,
)

data class ShareItemRecord(
    val id: String,
    val token: String,
    val status: String,
    val contentKind: String,
    val hasTextContent: Boolean,
    val hasFileContent: Boolean,
    val isEncrypted: Boolean,
    val requiresPassword: Boolean,
    val textPreview: String,
    val file: ShareFileRecord?,
    val allowCopyContent: Boolean,
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
    val updatedAt: String,
) {
    fun isActive(): Boolean = status.equals("active", ignoreCase = true)
}

data class SharePagination(
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val totalPages: Int,
    val status: String,
)

data class ShareListSummary(
    val maxUploadBytes: Long,
)

data class ShareListResult(
    val shares: List<ShareItemRecord>,
    val pagination: SharePagination,
    val summary: ShareListSummary,
    val tokens: TokenBundle? = null,
)

data class ShareMutationResult(
    val share: ShareItemRecord,
    val tokens: TokenBundle? = null,
)

data class ShareTextCreateRequest(
    val textContent: String,
    val policy: SharePolicyPayload,
)

data class ShareFileCreateRequest(
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long?,
    val policy: SharePolicyPayload,
    val openInputStream: () -> InputStream,
)

interface ShareApiClient {
    suspend fun listShares(
        session: StoredSession,
        page: Int,
        pageSize: Int,
        statusFilter: ShareStatusFilter,
        onRefreshing: (() -> Unit)? = null,
    ): ShareListResult

    suspend fun createTextShare(
        session: StoredSession,
        request: ShareTextCreateRequest,
        onRefreshing: (() -> Unit)? = null,
    ): ShareMutationResult

    suspend fun createFileShare(
        session: StoredSession,
        request: ShareFileCreateRequest,
        onRefreshing: (() -> Unit)? = null,
    ): ShareMutationResult

    suspend fun revokeShare(
        session: StoredSession,
        shareId: String,
        onRefreshing: (() -> Unit)? = null,
    ): ShareMutationResult
}

class HttpShareApiClient(
    private val authApiClient: AuthApiClient,
    private val httpClient: OkHttpClient = buildHttpClient(),
) : ShareApiClient {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private data class AuthenticatedJsonResponse(
        val data: JSONObject,
        val tokens: TokenBundle? = null,
    )

    override suspend fun listShares(
        session: StoredSession,
        page: Int,
        pageSize: Int,
        statusFilter: ShareStatusFilter,
        onRefreshing: (() -> Unit)?,
    ): ShareListResult = withContext(Dispatchers.IO) {
        val response = requestAuthenticatedJson(
            session = session,
            method = "GET",
            path = "/v1/shares?page=$page&page_size=$pageSize&status=${statusFilter.apiValue}",
            onRefreshing = onRefreshing,
        )

        val paginationData = response.data.optJSONObject("pagination") ?: JSONObject()
        val summaryData = response.data.optJSONObject("summary") ?: JSONObject()

        ShareListResult(
            shares = parseShareList(response.data.optJSONArray("shares")),
            pagination = SharePagination(
                page = paginationData.optInt("page", page.coerceAtLeast(1)),
                pageSize = paginationData.optInt("page_size", pageSize.coerceAtLeast(1)),
                total = paginationData.optInt("total", 0),
                totalPages = paginationData.optInt("total_pages", 0),
                status = paginationData.optString("status", statusFilter.apiValue),
            ),
            summary = ShareListSummary(
                maxUploadBytes = summaryData.optLong("max_upload_bytes", 0L),
            ),
            tokens = response.tokens,
        )
    }

    override suspend fun createTextShare(
        session: StoredSession,
        request: ShareTextCreateRequest,
        onRefreshing: (() -> Unit)?,
    ): ShareMutationResult = withContext(Dispatchers.IO) {
        val requestBody = JSONObject()
            .put("text_content", request.textContent)
            .put("never_expires", request.policy.neverExpires)
            .put("burn_mode", request.policy.burnMode)
            .put("allow_copy_content", request.policy.allowCopyContent)
            .put("is_encrypted", false)
            .apply {
                if (!request.policy.neverExpires) {
                    put("expire_seconds", request.policy.expireSeconds)
                }
                if (request.policy.burnMode == "countdown") {
                    put("burn_after_seconds", request.policy.burnAfterSeconds)
                }
            }
            .toString()
            .toRequestBody(jsonMediaType)

        val response = requestAuthenticatedJson(
            session = session,
            method = "POST",
            path = "/v1/shares/text",
            requestBody = requestBody,
            onRefreshing = onRefreshing,
        )

        ShareMutationResult(
            share = parseShareRecord(response.data.getJSONObject("share")),
            tokens = response.tokens,
        )
    }

    override suspend fun createFileShare(
        session: StoredSession,
        request: ShareFileCreateRequest,
        onRefreshing: (() -> Unit)?,
    ): ShareMutationResult = withContext(Dispatchers.IO) {
        val fileBody = StreamRequestBody(
            mediaTypeValue = request.contentType.ifBlank { "application/octet-stream" },
            sizeBytes = request.sizeBytes,
            openInputStream = request.openInputStream,
        )
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", request.fileName, fileBody)
            .addFormDataPart("never_expires", request.policy.neverExpires.toString())
            .addFormDataPart("burn_mode", request.policy.burnMode)
            .addFormDataPart("allow_copy_content", request.policy.allowCopyContent.toString())
            .addFormDataPart("is_encrypted", "false")
            .apply {
                if (!request.policy.neverExpires) {
                    addFormDataPart("expire_seconds", request.policy.expireSeconds.toString())
                }
                if (request.policy.burnMode == "countdown") {
                    addFormDataPart("burn_after_seconds", request.policy.burnAfterSeconds.toString())
                }
            }
            .build()

        val response = requestAuthenticatedJson(
            session = session,
            method = "POST",
            path = "/v1/shares/file",
            requestBody = multipartBody,
            onRefreshing = onRefreshing,
        )

        ShareMutationResult(
            share = parseShareRecord(response.data.getJSONObject("share")),
            tokens = response.tokens,
        )
    }

    override suspend fun revokeShare(
        session: StoredSession,
        shareId: String,
        onRefreshing: (() -> Unit)?,
    ): ShareMutationResult = withContext(Dispatchers.IO) {
        val response = requestAuthenticatedJson(
            session = session,
            method = "POST",
            path = "/v1/shares/${shareId.trim()}/revoke",
            onRefreshing = onRefreshing,
        )

        ShareMutationResult(
            share = parseShareRecord(response.data.getJSONObject("share")),
            tokens = response.tokens,
        )
    }

    private suspend fun requestAuthenticatedJson(
        session: StoredSession,
        method: String,
        path: String,
        requestBody: RequestBody? = null,
        onRefreshing: (() -> Unit)? = null,
    ): AuthenticatedJsonResponse {
        try {
            return AuthenticatedJsonResponse(
                data = requestJson(
                    baseUrl = session.baseUrl,
                    method = method,
                    path = path,
                    accessToken = session.accessToken,
                    requestBody = requestBody,
                ),
            )
        } catch (error: AuthApiException) {
            if (error.httpCode != 401) {
                throw error
            }

            // 分享接口也复用统一的 refresh 逻辑，
            // 这样页面层只需要处理“这次操作成功还是失败”。
            onRefreshing?.invoke()
            val refreshedTokens = authApiClient.refresh(session.baseUrl, session.refreshToken)
            val retriedData = requestJson(
                baseUrl = session.baseUrl,
                method = method,
                path = path,
                accessToken = refreshedTokens.accessToken,
                requestBody = requestBody,
            )
            return AuthenticatedJsonResponse(
                data = retriedData,
                tokens = refreshedTokens,
            )
        }
    }

    private fun requestJson(
        baseUrl: String,
        method: String,
        path: String,
        accessToken: String,
        requestBody: RequestBody? = null,
    ): JSONObject {
        val request = buildRequest(
            baseUrl = baseUrl,
            method = method,
            path = path,
            accessToken = accessToken,
            requestBody = requestBody,
        )

        try {
            httpClient.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    throw AuthApiException(
                        httpCode = response.code,
                        message = extractErrorMessage(bodyText, response.code),
                    )
                }

                val responseJson = parseJson(bodyText)
                if (responseJson.optInt("code", -1) != 0) {
                    throw AuthApiException(
                        httpCode = responseJson.optInt("code"),
                        message = responseJson.optString("message", "请求失败"),
                    )
                }

                return responseJson.optJSONObject("data") ?: JSONObject()
            }
        } catch (error: AuthApiException) {
            throw error
        } catch (error: IOException) {
            throw AuthApiException(message = "网络异常，请检查服务地址和网络连接", cause = error)
        }
    }

    private fun buildRequest(
        baseUrl: String,
        method: String,
        path: String,
        accessToken: String,
        requestBody: RequestBody? = null,
    ): Request {
        val builder = Request.Builder()
            .url(baseUrl + path)
            .header("Authorization", "Bearer $accessToken")

        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(requestBody ?: EMPTY_JSON_REQUEST_BODY)
            else -> error("Unsupported method: $method")
        }

        return builder.build()
    }

    private fun parseShareList(itemsData: JSONArray?): List<ShareItemRecord> {
        if (itemsData == null) {
            return emptyList()
        }

        val items = mutableListOf<ShareItemRecord>()
        for (index in 0 until itemsData.length()) {
            val item = itemsData.optJSONObject(index) ?: continue
            items += parseShareRecord(item)
        }
        return items
    }

    private fun parseShareRecord(itemData: JSONObject): ShareItemRecord {
        return ShareItemRecord(
            id = itemData.optString("id"),
            token = itemData.optString("token"),
            status = itemData.optString("status"),
            contentKind = itemData.optString("content_kind"),
            hasTextContent = itemData.optBoolean("has_text_content", false),
            hasFileContent = itemData.optBoolean("has_file_content", false),
            isEncrypted = itemData.optBoolean("is_encrypted", false),
            requiresPassword = itemData.optBoolean("requires_password", false),
            textPreview = itemData.optString("text_preview"),
            file = itemData.optJSONObject("file")?.let(::parseShareFileRecord),
            allowCopyContent = itemData.optBoolean("allow_copy_content", false),
            burnMode = itemData.optString("burn_mode"),
            burnAfterSeconds = itemData.optInt("burn_after_seconds", 0),
            remainingSeconds = itemData.optLong("remaining_seconds", 0L),
            expiresAt = itemData.optString("expires_at"),
            firstOpenedAt = itemData.optString("first_opened_at"),
            burnDeadline = itemData.optString("burn_deadline"),
            consumedAt = itemData.optString("consumed_at"),
            revokedAt = itemData.optString("revoked_at"),
            openCount = itemData.optLong("open_count", 0L),
            createdAt = itemData.optString("created_at"),
            updatedAt = itemData.optString("updated_at"),
        )
    }

    private fun parseShareFileRecord(fileData: JSONObject): ShareFileRecord {
        return ShareFileRecord(
            originalName = fileData.optString("original_name"),
            contentType = fileData.optString("content_type"),
            sizeBytes = fileData.optLong("size_bytes", 0L),
            isImage = fileData.optBoolean("is_image", false),
        )
    }

    private fun parseJson(bodyText: String): JSONObject {
        if (bodyText.isBlank()) {
            return JSONObject()
        }

        return try {
            JSONObject(bodyText)
        } catch (error: Exception) {
            throw AuthApiException(message = "服务端返回了非 JSON 响应", cause = error)
        }
    }

    private fun extractErrorMessage(bodyText: String, httpCode: Int): String {
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

    private class StreamRequestBody(
        private val mediaTypeValue: String,
        private val sizeBytes: Long?,
        private val openInputStream: () -> InputStream,
    ) : RequestBody() {
        override fun contentType() = mediaTypeValue.toMediaTypeOrNull()

        override fun contentLength(): Long {
            return sizeBytes ?: -1L
        }

        override fun writeTo(sink: BufferedSink) {
            openInputStream().use { inputStream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val readCount = inputStream.read(buffer)
                    if (readCount <= 0) {
                        break
                    }
                    sink.write(buffer, 0, readCount)
                }
            }
        }
    }

    private companion object {
        val EMPTY_JSON_REQUEST_BODY: RequestBody = "{}".toRequestBody("application/json; charset=utf-8".toMediaType())

        fun buildHttpClient(): OkHttpClient {
            val loggingInterceptor = HttpLoggingInterceptor()
            loggingInterceptor.level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }

            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)
                .build()
        }
    }
}
