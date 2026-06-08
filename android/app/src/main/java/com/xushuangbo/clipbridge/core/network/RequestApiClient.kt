package com.xushuangbo.clipbridge.core.network

import com.xushuangbo.clipbridge.core.session.StoredSession
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class QuotaRequestRecord(
    val id: String,
    val userId: String,
    val username: String,
    val requestedQuotaBytes: Long,
    val currentQuotaBytes: Long,
    val reason: String,
    val status: String,
    val reviewedBy: String,
    val reviewedByUsername: String,
    val reviewNote: String,
    val createdAt: String,
    val reviewedAt: String,
)

data class BandwidthRequestRecord(
    val id: String,
    val userId: String,
    val username: String,
    val requestedUploadKbps: Int,
    val requestedDownloadKbps: Int,
    val currentUploadKbps: Int,
    val currentDownloadKbps: Int,
    val reason: String,
    val status: String,
    val reviewedBy: String,
    val reviewedByUsername: String,
    val reviewNote: String,
    val createdAt: String,
    val reviewedAt: String,
)

data class AdminPrivilegeRequestRecord(
    val id: String,
    val userId: String,
    val username: String,
    val reason: String,
    val status: String,
    val reviewedBy: String,
    val reviewedByUsername: String,
    val reviewNote: String,
    val createdAt: String,
    val reviewedAt: String,
)

data class RequestMutationResult<T>(
    val request: T,
    val tokens: TokenBundle? = null,
)

data class RequestListResult<T>(
    val requests: List<T>,
    val status: String,
    val tokens: TokenBundle? = null,
)

interface RequestApiClient {
    suspend fun createQuotaRequest(
        session: StoredSession,
        requestedQuotaMb: Long,
        reason: String,
        onRefreshing: (() -> Unit)? = null,
    ): RequestMutationResult<QuotaRequestRecord>

    suspend fun listQuotaRequests(
        session: StoredSession,
        status: String = "all",
        onRefreshing: (() -> Unit)? = null,
    ): RequestListResult<QuotaRequestRecord>

    suspend fun createBandwidthRequest(
        session: StoredSession,
        requestedUploadKbps: Int,
        requestedDownloadKbps: Int,
        reason: String,
        onRefreshing: (() -> Unit)? = null,
    ): RequestMutationResult<BandwidthRequestRecord>

    suspend fun listBandwidthRequests(
        session: StoredSession,
        status: String = "all",
        onRefreshing: (() -> Unit)? = null,
    ): RequestListResult<BandwidthRequestRecord>

    suspend fun createAdminRequest(
        session: StoredSession,
        reason: String,
        onRefreshing: (() -> Unit)? = null,
    ): RequestMutationResult<AdminPrivilegeRequestRecord>

    suspend fun listAdminRequests(
        session: StoredSession,
        status: String = "all",
        onRefreshing: (() -> Unit)? = null,
    ): RequestListResult<AdminPrivilegeRequestRecord>
}

class HttpRequestApiClient(
    authApiClient: AuthApiClient,
    httpClient: OkHttpClient = AuthenticatedJsonApiSupport.buildHttpClient(),
) : RequestApiClient {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val apiSupport = AuthenticatedJsonApiSupport(
        authApiClient = authApiClient,
        httpClient = httpClient,
    )

    override suspend fun createQuotaRequest(
        session: StoredSession,
        requestedQuotaMb: Long,
        reason: String,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<QuotaRequestRecord> {
        val requestBody = JSONObject()
            .put("requested_quota_mb", requestedQuotaMb)
            .put("reason", reason)
            .toString()
            .toRequestBody(jsonMediaType)

        val response = apiSupport.request(
            session = session,
            method = "POST",
            path = "/v1/account/quota-requests",
            requestBody = requestBody,
            onRefreshing = onRefreshing,
        )

        return RequestMutationResult(
            request = parseQuotaRequest(response.data.getJSONObject("request")),
            tokens = response.tokens,
        )
    }

    override suspend fun listQuotaRequests(
        session: StoredSession,
        status: String,
        onRefreshing: (() -> Unit)?,
    ): RequestListResult<QuotaRequestRecord> {
        val response = apiSupport.request(
            session = session,
            method = "GET",
            path = "/v1/account/quota-requests?status=${status.trim()}",
            onRefreshing = onRefreshing,
        )

        return RequestListResult(
            requests = parseQuotaRequests(response.data.optJSONArray("requests")),
            status = response.data.optString("status", status),
            tokens = response.tokens,
        )
    }

    override suspend fun createBandwidthRequest(
        session: StoredSession,
        requestedUploadKbps: Int,
        requestedDownloadKbps: Int,
        reason: String,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<BandwidthRequestRecord> {
        val requestBody = JSONObject()
            .put("requested_upload_kbps", requestedUploadKbps)
            .put("requested_download_kbps", requestedDownloadKbps)
            .put("reason", reason)
            .toString()
            .toRequestBody(jsonMediaType)

        val response = apiSupport.request(
            session = session,
            method = "POST",
            path = "/v1/account/bandwidth-requests",
            requestBody = requestBody,
            onRefreshing = onRefreshing,
        )

        return RequestMutationResult(
            request = parseBandwidthRequest(response.data.getJSONObject("request")),
            tokens = response.tokens,
        )
    }

    override suspend fun listBandwidthRequests(
        session: StoredSession,
        status: String,
        onRefreshing: (() -> Unit)?,
    ): RequestListResult<BandwidthRequestRecord> {
        val response = apiSupport.request(
            session = session,
            method = "GET",
            path = "/v1/account/bandwidth-requests?status=${status.trim()}",
            onRefreshing = onRefreshing,
        )

        return RequestListResult(
            requests = parseBandwidthRequests(response.data.optJSONArray("requests")),
            status = response.data.optString("status", status),
            tokens = response.tokens,
        )
    }

    override suspend fun createAdminRequest(
        session: StoredSession,
        reason: String,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<AdminPrivilegeRequestRecord> {
        val requestBody = JSONObject()
            .put("reason", reason)
            .toString()
            .toRequestBody(jsonMediaType)

        val response = apiSupport.request(
            session = session,
            method = "POST",
            path = "/v1/account/admin-requests",
            requestBody = requestBody,
            onRefreshing = onRefreshing,
        )

        return RequestMutationResult(
            request = parseAdminRequest(response.data.getJSONObject("request")),
            tokens = response.tokens,
        )
    }

    override suspend fun listAdminRequests(
        session: StoredSession,
        status: String,
        onRefreshing: (() -> Unit)?,
    ): RequestListResult<AdminPrivilegeRequestRecord> {
        val response = apiSupport.request(
            session = session,
            method = "GET",
            path = "/v1/account/admin-requests?status=${status.trim()}",
            onRefreshing = onRefreshing,
        )

        return RequestListResult(
            requests = parseAdminRequests(response.data.optJSONArray("requests")),
            status = response.data.optString("status", status),
            tokens = response.tokens,
        )
    }

    private fun parseQuotaRequests(items: JSONArray?): List<QuotaRequestRecord> {
        if (items == null) {
            return emptyList()
        }

        val result = mutableListOf<QuotaRequestRecord>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            result += parseQuotaRequest(item)
        }
        return result
    }

    private fun parseBandwidthRequests(items: JSONArray?): List<BandwidthRequestRecord> {
        if (items == null) {
            return emptyList()
        }

        val result = mutableListOf<BandwidthRequestRecord>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            result += parseBandwidthRequest(item)
        }
        return result
    }

    private fun parseAdminRequests(items: JSONArray?): List<AdminPrivilegeRequestRecord> {
        if (items == null) {
            return emptyList()
        }

        val result = mutableListOf<AdminPrivilegeRequestRecord>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            result += parseAdminRequest(item)
        }
        return result
    }

    private fun parseQuotaRequest(item: JSONObject): QuotaRequestRecord {
        return QuotaRequestRecord(
            id = item.optString("id"),
            userId = item.optString("user_id"),
            username = item.optString("username"),
            requestedQuotaBytes = item.optLong("requested_quota_bytes", 0L),
            currentQuotaBytes = item.optLong("current_quota_bytes", 0L),
            reason = item.optString("reason"),
            status = item.optString("status"),
            reviewedBy = item.optString("reviewed_by"),
            reviewedByUsername = item.optString("reviewed_by_username"),
            reviewNote = item.optString("review_note"),
            createdAt = item.optString("created_at"),
            reviewedAt = item.optString("reviewed_at"),
        )
    }

    private fun parseBandwidthRequest(item: JSONObject): BandwidthRequestRecord {
        return BandwidthRequestRecord(
            id = item.optString("id"),
            userId = item.optString("user_id"),
            username = item.optString("username"),
            requestedUploadKbps = item.optInt("requested_upload_kbps", 0),
            requestedDownloadKbps = item.optInt("requested_download_kbps", 0),
            currentUploadKbps = item.optInt("current_upload_kbps", 0),
            currentDownloadKbps = item.optInt("current_download_kbps", 0),
            reason = item.optString("reason"),
            status = item.optString("status"),
            reviewedBy = item.optString("reviewed_by"),
            reviewedByUsername = item.optString("reviewed_by_username"),
            reviewNote = item.optString("review_note"),
            createdAt = item.optString("created_at"),
            reviewedAt = item.optString("reviewed_at"),
        )
    }

    private fun parseAdminRequest(item: JSONObject): AdminPrivilegeRequestRecord {
        return AdminPrivilegeRequestRecord(
            id = item.optString("id"),
            userId = item.optString("user_id"),
            username = item.optString("username"),
            reason = item.optString("reason"),
            status = item.optString("status"),
            reviewedBy = item.optString("reviewed_by"),
            reviewedByUsername = item.optString("reviewed_by_username"),
            reviewNote = item.optString("review_note"),
            createdAt = item.optString("created_at"),
            reviewedAt = item.optString("reviewed_at"),
        )
    }
}
