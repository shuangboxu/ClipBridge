package com.xushuangbo.clipbridge.core.network

import com.xushuangbo.clipbridge.BuildConfig
import com.xushuangbo.clipbridge.core.session.StoredSession
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
import java.util.concurrent.TimeUnit

data class ClipboardItem(
    val id: String,
    val seq: Long,
    val contentType: String,
    val textContent: String,
    val originDeviceId: String,
    val isCurrentDeviceOrigin: Boolean,
    val createdAt: String,
)

data class ClipboardHistoryResult(
    val items: List<ClipboardItem>,
    val hasMore: Boolean,
    val latestSeq: Long,
    val currentDeviceAckSeq: Long,
    val tokens: TokenBundle? = null,
)

data class ClipboardUploadResult(
    val item: ClipboardItem,
    val deduplicated: Boolean,
    val tokens: TokenBundle? = null,
)

data class SyncPullResult(
    val items: List<ClipboardItem>,
    val nextSinceSeq: Long?,
    val tokens: TokenBundle? = null,
)

interface ClipboardApiClient {
    suspend fun uploadText(
        session: StoredSession,
        text: String,
        onRefreshing: (() -> Unit)? = null,
    ): ClipboardUploadResult

    suspend fun listClipboardItems(
        session: StoredSession,
        limit: Int,
        beforeSeq: Long? = null,
        onRefreshing: (() -> Unit)? = null,
    ): ClipboardHistoryResult

    suspend fun pullSync(
        session: StoredSession,
        sinceSeq: Long,
        limit: Int,
        onRefreshing: (() -> Unit)? = null,
    ): SyncPullResult

    suspend fun ackSync(
        session: StoredSession,
        seq: Long,
        onRefreshing: (() -> Unit)? = null,
    ): TokenBundle?
}

class HttpClipboardApiClient(
    private val authApiClient: AuthApiClient,
    private val httpClient: OkHttpClient = buildHttpClient(),
) : ClipboardApiClient {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private data class AuthenticatedResponse(
        val data: JSONObject,
        val tokens: TokenBundle? = null,
    )

    override suspend fun uploadText(
        session: StoredSession,
        text: String,
        onRefreshing: (() -> Unit)?,
    ): ClipboardUploadResult = withContext(Dispatchers.IO) {
        val requestBody = JSONObject()
            .put("content_type", "text")
            .put("text_content", text)

        val response = requestAuthenticatedData(
            session = session,
            method = "POST",
            path = "/v1/clipboard/items",
            jsonBody = requestBody,
            onRefreshing = onRefreshing,
        )

        ClipboardUploadResult(
            item = parseClipboardItem(response.data.getJSONObject("item")),
            deduplicated = response.data.optBoolean("deduplicated"),
            tokens = response.tokens,
        )
    }

    override suspend fun listClipboardItems(
        session: StoredSession,
        limit: Int,
        beforeSeq: Long?,
        onRefreshing: (() -> Unit)?,
    ): ClipboardHistoryResult = withContext(Dispatchers.IO) {
        val query = buildString {
            append("?limit=")
            append(limit)
            if (beforeSeq != null) {
                append("&before_seq=")
                append(beforeSeq)
            }
        }

        val response = requestAuthenticatedData(
            session = session,
            method = "GET",
            path = "/v1/clipboard/items$query",
            onRefreshing = onRefreshing,
        )

        ClipboardHistoryResult(
            items = parseClipboardItems(response.data.optJSONArray("items")),
            hasMore = response.data.optBoolean("has_more"),
            latestSeq = response.data.optLong("latest_seq", 0L),
            currentDeviceAckSeq = response.data.optLong("current_device_ack_seq", 0L),
            tokens = response.tokens,
        )
    }

    override suspend fun pullSync(
        session: StoredSession,
        sinceSeq: Long,
        limit: Int,
        onRefreshing: (() -> Unit)?,
    ): SyncPullResult = withContext(Dispatchers.IO) {
        val response = requestAuthenticatedData(
            session = session,
            method = "GET",
            path = "/v1/sync/pull?since_seq=$sinceSeq&limit=$limit",
            onRefreshing = onRefreshing,
        )

        SyncPullResult(
            items = parseClipboardItems(response.data.optJSONArray("items")),
            nextSinceSeq = response.data.optLongOrNull("next_since_seq"),
            tokens = response.tokens,
        )
    }

    override suspend fun ackSync(
        session: StoredSession,
        seq: Long,
        onRefreshing: (() -> Unit)?,
    ): TokenBundle? = withContext(Dispatchers.IO) {
        val requestBody = JSONObject().put("seq", seq)
        val response = requestAuthenticatedData(
            session = session,
            method = "POST",
            path = "/v1/sync/ack",
            jsonBody = requestBody,
            onRefreshing = onRefreshing,
        )
        response.tokens
    }

    private suspend fun requestAuthenticatedData(
        session: StoredSession,
        method: String,
        path: String,
        jsonBody: JSONObject? = null,
        onRefreshing: (() -> Unit)? = null,
    ): AuthenticatedResponse {
        try {
            return AuthenticatedResponse(
                data = requestData(
                    baseUrl = session.baseUrl,
                    method = method,
                    path = path,
                    jsonBody = jsonBody,
                    accessToken = session.accessToken,
                ),
            )
        } catch (error: AuthApiException) {
            if (error.httpCode != 401) {
                throw error
            }

            // 业务接口遇到 401 时统一刷新一次，避免每个页面重复拼 refresh 逻辑。
            onRefreshing?.invoke()
            val refreshedTokens = authApiClient.refresh(session.baseUrl, session.refreshToken)
            val retriedData = requestData(
                baseUrl = session.baseUrl,
                method = method,
                path = path,
                jsonBody = jsonBody,
                accessToken = refreshedTokens.accessToken,
            )
            return AuthenticatedResponse(
                data = retriedData,
                tokens = refreshedTokens,
            )
        }
    }

    private fun parseClipboardItems(itemsData: JSONArray?): List<ClipboardItem> {
        if (itemsData == null) {
            return emptyList()
        }

        val items = mutableListOf<ClipboardItem>()
        for (index in 0 until itemsData.length()) {
            val item = itemsData.optJSONObject(index) ?: continue
            items += parseClipboardItem(item)
        }
        return items
    }

    private fun parseClipboardItem(itemData: JSONObject): ClipboardItem {
        return ClipboardItem(
            id = itemData.optString("id"),
            seq = itemData.optLong("seq"),
            contentType = itemData.optString("content_type"),
            textContent = itemData.optString("text_content"),
            originDeviceId = itemData.optString("origin_device_id"),
            isCurrentDeviceOrigin = itemData.optBoolean("is_current_device_origin"),
            createdAt = itemData.optString("created_at"),
        )
    }

    private fun requestData(
        baseUrl: String,
        method: String,
        path: String,
        jsonBody: JSONObject? = null,
        accessToken: String? = null,
    ): JSONObject {
        val requestBuilder = Request.Builder()
            .url(baseUrl + path)

        if (!accessToken.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $accessToken")
        }
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

    private fun JSONObject.optLongOrNull(name: String): Long? {
        if (!has(name) || isNull(name)) {
            return null
        }
        return optLong(name)
    }

    private companion object {
        fun buildHttpClient(): OkHttpClient {
            val loggingInterceptor = HttpLoggingInterceptor()
            loggingInterceptor.level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }

            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)
                .build()
        }
    }
}
