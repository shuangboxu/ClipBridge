package com.xushuangbo.clipbridge.core.network

import com.xushuangbo.clipbridge.core.session.StoredSession
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class AdminSettingsRecord(
    val maxUserCount: Int,
    val defaultStorageQuotaBytes: Long,
    val defaultUploadBandwidthKbps: Int,
    val defaultDownloadBandwidthKbps: Int,
    val maxUserUploadBandwidthKbps: Int,
    val maxUserDownloadBandwidthKbps: Int,
    val maxUploadFileBytes: Long,
    val allowRegistration: Boolean,
    val updatedAt: String,
)

data class AdminUserRecord(
    val id: String,
    val username: String,
    val isAdmin: Boolean,
    val storageQuotaBytes: Long,
    val storageUsedBytes: Long,
    val storageFreeBytes: Long,
    val uploadBandwidthKbps: Int,
    val downloadBandwidthKbps: Int,
    val hasPendingQuotaRequest: Boolean,
    val hasPendingBandwidthRequest: Boolean,
    val hasPendingAdminRequest: Boolean,
    val lastActiveAt: String,
    val createdAt: String,
    val updatedAt: String,
)

data class AdminSettingsResult(
    val settings: AdminSettingsRecord,
    val currentUserCount: Int,
    val tokens: TokenBundle? = null,
)

data class AdminUsersResult(
    val users: List<AdminUserRecord>,
    val tokens: TokenBundle? = null,
)

data class AdminDeleteUserResult(
    val success: Boolean,
    val deletedUserId: String,
    val tokens: TokenBundle? = null,
)

data class UpdateAdminSettingsInput(
    val maxUserCount: Int? = null,
    val defaultStorageQuotaMb: Long? = null,
    val defaultUploadBandwidthKbps: Int? = null,
    val defaultDownloadBandwidthKbps: Int? = null,
    val maxUserUploadBandwidthKbps: Int? = null,
    val maxUserDownloadBandwidthKbps: Int? = null,
    val maxUploadFileMb: Long? = null,
    val allowRegistration: Boolean? = null,
)

data class UpdateAdminUserInput(
    val storageQuotaMb: Long? = null,
    val uploadBandwidthKbps: Int? = null,
    val downloadBandwidthKbps: Int? = null,
    val isAdmin: Boolean? = null,
)

data class ApproveQuotaRequestInput(
    val approvedQuotaMb: Long? = null,
    val reviewNote: String = "",
)

data class ApproveBandwidthRequestInput(
    val approvedUploadKbps: Int? = null,
    val approvedDownloadKbps: Int? = null,
    val reviewNote: String = "",
)

data class ReviewNoteInput(
    val reviewNote: String = "",
)

interface AdminApiClient {
    suspend fun getSettings(
        session: StoredSession,
        onRefreshing: (() -> Unit)? = null,
    ): AdminSettingsResult

    suspend fun updateSettings(
        session: StoredSession,
        input: UpdateAdminSettingsInput,
        onRefreshing: (() -> Unit)? = null,
    ): AdminSettingsResult

    suspend fun listUsers(
        session: StoredSession,
        onRefreshing: (() -> Unit)? = null,
    ): AdminUsersResult

    suspend fun updateUser(
        session: StoredSession,
        userId: String,
        input: UpdateAdminUserInput,
        onRefreshing: (() -> Unit)? = null,
    ): RequestMutationResult<AdminUserRecord>

    suspend fun deleteUser(
        session: StoredSession,
        userId: String,
        onRefreshing: (() -> Unit)? = null,
    ): AdminDeleteUserResult

    suspend fun listPendingQuotaRequests(
        session: StoredSession,
        onRefreshing: (() -> Unit)? = null,
    ): RequestListResult<QuotaRequestRecord>

    suspend fun approveQuotaRequest(
        session: StoredSession,
        requestId: String,
        input: ApproveQuotaRequestInput,
        onRefreshing: (() -> Unit)? = null,
    ): RequestMutationResult<QuotaRequestRecord>

    suspend fun rejectQuotaRequest(
        session: StoredSession,
        requestId: String,
        input: ReviewNoteInput,
        onRefreshing: (() -> Unit)? = null,
    ): RequestMutationResult<QuotaRequestRecord>

    suspend fun listPendingBandwidthRequests(
        session: StoredSession,
        onRefreshing: (() -> Unit)? = null,
    ): RequestListResult<BandwidthRequestRecord>

    suspend fun approveBandwidthRequest(
        session: StoredSession,
        requestId: String,
        input: ApproveBandwidthRequestInput,
        onRefreshing: (() -> Unit)? = null,
    ): RequestMutationResult<BandwidthRequestRecord>

    suspend fun rejectBandwidthRequest(
        session: StoredSession,
        requestId: String,
        input: ReviewNoteInput,
        onRefreshing: (() -> Unit)? = null,
    ): RequestMutationResult<BandwidthRequestRecord>

    suspend fun listPendingAdminRequests(
        session: StoredSession,
        onRefreshing: (() -> Unit)? = null,
    ): RequestListResult<AdminPrivilegeRequestRecord>

    suspend fun approveAdminRequest(
        session: StoredSession,
        requestId: String,
        input: ReviewNoteInput,
        onRefreshing: (() -> Unit)? = null,
    ): RequestMutationResult<AdminPrivilegeRequestRecord>

    suspend fun rejectAdminRequest(
        session: StoredSession,
        requestId: String,
        input: ReviewNoteInput,
        onRefreshing: (() -> Unit)? = null,
    ): RequestMutationResult<AdminPrivilegeRequestRecord>
}

class HttpAdminApiClient(
    authApiClient: AuthApiClient,
    httpClient: OkHttpClient = AuthenticatedJsonApiSupport.buildHttpClient(),
) : AdminApiClient {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val apiSupport = AuthenticatedJsonApiSupport(
        authApiClient = authApiClient,
        httpClient = httpClient,
    )

    override suspend fun getSettings(
        session: StoredSession,
        onRefreshing: (() -> Unit)?,
    ): AdminSettingsResult {
        val response = apiSupport.request(
            session = session,
            method = "GET",
            path = "/v1/admin/settings",
            onRefreshing = onRefreshing,
        )
        return parseSettingsResult(response)
    }

    override suspend fun updateSettings(
        session: StoredSession,
        input: UpdateAdminSettingsInput,
        onRefreshing: (() -> Unit)?,
    ): AdminSettingsResult {
        val requestBody = JSONObject().apply {
            input.maxUserCount?.let { put("max_user_count", it) }
            input.defaultStorageQuotaMb?.let { put("default_storage_quota_mb", it) }
            input.defaultUploadBandwidthKbps?.let { put("default_upload_bandwidth_kbps", it) }
            input.defaultDownloadBandwidthKbps?.let { put("default_download_bandwidth_kbps", it) }
            input.maxUserUploadBandwidthKbps?.let { put("max_user_upload_bandwidth_kbps", it) }
            input.maxUserDownloadBandwidthKbps?.let { put("max_user_download_bandwidth_kbps", it) }
            input.maxUploadFileMb?.let { put("max_upload_file_mb", it) }
            input.allowRegistration?.let { put("allow_registration", it) }
        }.toString().toRequestBody(jsonMediaType)

        val response = apiSupport.request(
            session = session,
            method = "PUT",
            path = "/v1/admin/settings",
            requestBody = requestBody,
            onRefreshing = onRefreshing,
        )
        return parseSettingsResult(response)
    }

    override suspend fun listUsers(
        session: StoredSession,
        onRefreshing: (() -> Unit)?,
    ): AdminUsersResult {
        val response = apiSupport.request(
            session = session,
            method = "GET",
            path = "/v1/admin/users",
            onRefreshing = onRefreshing,
        )
        return AdminUsersResult(
            users = parseAdminUsers(response.data.optJSONArray("users")),
            tokens = response.tokens,
        )
    }

    override suspend fun updateUser(
        session: StoredSession,
        userId: String,
        input: UpdateAdminUserInput,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<AdminUserRecord> {
        val requestBody = JSONObject().apply {
            input.storageQuotaMb?.let { put("storage_quota_mb", it) }
            input.uploadBandwidthKbps?.let { put("upload_bandwidth_kbps", it) }
            input.downloadBandwidthKbps?.let { put("download_bandwidth_kbps", it) }
            input.isAdmin?.let { put("is_admin", it) }
        }.toString().toRequestBody(jsonMediaType)

        val response = apiSupport.request(
            session = session,
            method = "PATCH",
            path = "/v1/admin/users/${userId.trim()}",
            requestBody = requestBody,
            onRefreshing = onRefreshing,
        )
        return RequestMutationResult(
            request = parseAdminUser(response.data.getJSONObject("user")),
            tokens = response.tokens,
        )
    }

    override suspend fun deleteUser(
        session: StoredSession,
        userId: String,
        onRefreshing: (() -> Unit)?,
    ): AdminDeleteUserResult {
        val response = apiSupport.request(
            session = session,
            method = "DELETE",
            path = "/v1/admin/users/${userId.trim()}",
            onRefreshing = onRefreshing,
        )
        return AdminDeleteUserResult(
            success = response.data.optBoolean("success", false),
            deletedUserId = response.data.optString("deleted_user_id"),
            tokens = response.tokens,
        )
    }

    override suspend fun listPendingQuotaRequests(
        session: StoredSession,
        onRefreshing: (() -> Unit)?,
    ): RequestListResult<QuotaRequestRecord> {
        val response = apiSupport.request(
            session = session,
            method = "GET",
            path = "/v1/admin/quota-requests?status=pending",
            onRefreshing = onRefreshing,
        )
        return RequestListResult(
            requests = parseQuotaRequests(response.data.optJSONArray("requests")),
            status = response.data.optString("status", "pending"),
            tokens = response.tokens,
        )
    }

    override suspend fun approveQuotaRequest(
        session: StoredSession,
        requestId: String,
        input: ApproveQuotaRequestInput,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<QuotaRequestRecord> {
        val requestBody = JSONObject().apply {
            input.approvedQuotaMb?.let { put("approved_quota_mb", it) }
            put("review_note", input.reviewNote)
        }.toString().toRequestBody(jsonMediaType)

        val response = apiSupport.request(
            session = session,
            method = "POST",
            path = "/v1/admin/quota-requests/${requestId.trim()}/approve",
            requestBody = requestBody,
            onRefreshing = onRefreshing,
        )
        return RequestMutationResult(
            request = parseQuotaRequest(response.data.getJSONObject("request")),
            tokens = response.tokens,
        )
    }

    override suspend fun rejectQuotaRequest(
        session: StoredSession,
        requestId: String,
        input: ReviewNoteInput,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<QuotaRequestRecord> {
        val requestBody = JSONObject()
            .put("review_note", input.reviewNote)
            .toString()
            .toRequestBody(jsonMediaType)

        val response = apiSupport.request(
            session = session,
            method = "POST",
            path = "/v1/admin/quota-requests/${requestId.trim()}/reject",
            requestBody = requestBody,
            onRefreshing = onRefreshing,
        )
        return RequestMutationResult(
            request = parseQuotaRequest(response.data.getJSONObject("request")),
            tokens = response.tokens,
        )
    }

    override suspend fun listPendingBandwidthRequests(
        session: StoredSession,
        onRefreshing: (() -> Unit)?,
    ): RequestListResult<BandwidthRequestRecord> {
        val response = apiSupport.request(
            session = session,
            method = "GET",
            path = "/v1/admin/bandwidth-requests?status=pending",
            onRefreshing = onRefreshing,
        )
        return RequestListResult(
            requests = parseBandwidthRequests(response.data.optJSONArray("requests")),
            status = response.data.optString("status", "pending"),
            tokens = response.tokens,
        )
    }

    override suspend fun approveBandwidthRequest(
        session: StoredSession,
        requestId: String,
        input: ApproveBandwidthRequestInput,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<BandwidthRequestRecord> {
        val requestBody = JSONObject().apply {
            input.approvedUploadKbps?.let { put("approved_upload_kbps", it) }
            input.approvedDownloadKbps?.let { put("approved_download_kbps", it) }
            put("review_note", input.reviewNote)
        }.toString().toRequestBody(jsonMediaType)

        val response = apiSupport.request(
            session = session,
            method = "POST",
            path = "/v1/admin/bandwidth-requests/${requestId.trim()}/approve",
            requestBody = requestBody,
            onRefreshing = onRefreshing,
        )
        return RequestMutationResult(
            request = parseBandwidthRequest(response.data.getJSONObject("request")),
            tokens = response.tokens,
        )
    }

    override suspend fun rejectBandwidthRequest(
        session: StoredSession,
        requestId: String,
        input: ReviewNoteInput,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<BandwidthRequestRecord> {
        val requestBody = JSONObject()
            .put("review_note", input.reviewNote)
            .toString()
            .toRequestBody(jsonMediaType)

        val response = apiSupport.request(
            session = session,
            method = "POST",
            path = "/v1/admin/bandwidth-requests/${requestId.trim()}/reject",
            requestBody = requestBody,
            onRefreshing = onRefreshing,
        )
        return RequestMutationResult(
            request = parseBandwidthRequest(response.data.getJSONObject("request")),
            tokens = response.tokens,
        )
    }

    override suspend fun listPendingAdminRequests(
        session: StoredSession,
        onRefreshing: (() -> Unit)?,
    ): RequestListResult<AdminPrivilegeRequestRecord> {
        val response = apiSupport.request(
            session = session,
            method = "GET",
            path = "/v1/admin/admin-requests?status=pending",
            onRefreshing = onRefreshing,
        )
        return RequestListResult(
            requests = parseAdminRequests(response.data.optJSONArray("requests")),
            status = response.data.optString("status", "pending"),
            tokens = response.tokens,
        )
    }

    override suspend fun approveAdminRequest(
        session: StoredSession,
        requestId: String,
        input: ReviewNoteInput,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<AdminPrivilegeRequestRecord> {
        val requestBody = JSONObject()
            .put("review_note", input.reviewNote)
            .toString()
            .toRequestBody(jsonMediaType)

        val response = apiSupport.request(
            session = session,
            method = "POST",
            path = "/v1/admin/admin-requests/${requestId.trim()}/approve",
            requestBody = requestBody,
            onRefreshing = onRefreshing,
        )
        return RequestMutationResult(
            request = parseAdminRequest(response.data.getJSONObject("request")),
            tokens = response.tokens,
        )
    }

    override suspend fun rejectAdminRequest(
        session: StoredSession,
        requestId: String,
        input: ReviewNoteInput,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<AdminPrivilegeRequestRecord> {
        val requestBody = JSONObject()
            .put("review_note", input.reviewNote)
            .toString()
            .toRequestBody(jsonMediaType)

        val response = apiSupport.request(
            session = session,
            method = "POST",
            path = "/v1/admin/admin-requests/${requestId.trim()}/reject",
            requestBody = requestBody,
            onRefreshing = onRefreshing,
        )
        return RequestMutationResult(
            request = parseAdminRequest(response.data.getJSONObject("request")),
            tokens = response.tokens,
        )
    }

    private fun parseSettingsResult(response: AuthenticatedJsonResponse): AdminSettingsResult {
        return AdminSettingsResult(
            settings = parseAdminSettings(response.data.getJSONObject("settings")),
            currentUserCount = response.data.optInt("current_user_count", 0),
            tokens = response.tokens,
        )
    }

    private fun parseAdminUsers(items: JSONArray?): List<AdminUserRecord> {
        if (items == null) {
            return emptyList()
        }

        val result = mutableListOf<AdminUserRecord>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            result += parseAdminUser(item)
        }
        return result
    }

    private fun parseAdminSettings(item: JSONObject): AdminSettingsRecord {
        return AdminSettingsRecord(
            maxUserCount = item.optInt("max_user_count", 0),
            defaultStorageQuotaBytes = item.optLong("default_storage_quota_bytes", 0L),
            defaultUploadBandwidthKbps = item.optInt("default_upload_bandwidth_kbps", 0),
            defaultDownloadBandwidthKbps = item.optInt("default_download_bandwidth_kbps", 0),
            maxUserUploadBandwidthKbps = item.optInt("max_user_upload_bandwidth_kbps", 0),
            maxUserDownloadBandwidthKbps = item.optInt("max_user_download_bandwidth_kbps", 0),
            maxUploadFileBytes = item.optLong("max_upload_file_bytes", 0L),
            allowRegistration = item.optBoolean("allow_registration", false),
            updatedAt = item.optString("updated_at"),
        )
    }

    private fun parseAdminUser(item: JSONObject): AdminUserRecord {
        return AdminUserRecord(
            id = item.optString("id"),
            username = item.optString("username"),
            isAdmin = item.optBoolean("is_admin", false),
            storageQuotaBytes = item.optLong("storage_quota_bytes", 0L),
            storageUsedBytes = item.optLong("storage_used_bytes", 0L),
            storageFreeBytes = item.optLong("storage_free_bytes", 0L),
            uploadBandwidthKbps = item.optInt("upload_bandwidth_kbps", 0),
            downloadBandwidthKbps = item.optInt("download_bandwidth_kbps", 0),
            hasPendingQuotaRequest = item.optBoolean("has_pending_quota_request", false),
            hasPendingBandwidthRequest = item.optBoolean("has_pending_bandwidth_request", false),
            hasPendingAdminRequest = item.optBoolean("has_pending_admin_request", false),
            lastActiveAt = item.optString("last_active_at"),
            createdAt = item.optString("created_at"),
            updatedAt = item.optString("updated_at"),
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
