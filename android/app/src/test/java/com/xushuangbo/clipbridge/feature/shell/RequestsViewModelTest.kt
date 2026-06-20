package com.xushuangbo.clipbridge.feature.shell

import com.xushuangbo.clipbridge.MainDispatcherRule
import com.xushuangbo.clipbridge.core.network.AccountProfileResult
import com.xushuangbo.clipbridge.core.network.AdminPrivilegeRequestRecord
import com.xushuangbo.clipbridge.core.network.AuthApiClient
import com.xushuangbo.clipbridge.core.network.AuthApiException
import com.xushuangbo.clipbridge.core.network.AuthResult
import com.xushuangbo.clipbridge.core.network.BandwidthRequestRecord
import com.xushuangbo.clipbridge.core.network.ChangePasswordResult
import com.xushuangbo.clipbridge.core.network.DeviceListResult
import com.xushuangbo.clipbridge.core.network.DeviceMutationResult
import com.xushuangbo.clipbridge.core.network.ForceOfflineResult
import com.xushuangbo.clipbridge.core.network.QuotaRequestRecord
import com.xushuangbo.clipbridge.core.network.RequestApiClient
import com.xushuangbo.clipbridge.core.network.RequestListResult
import com.xushuangbo.clipbridge.core.network.RequestMutationResult
import com.xushuangbo.clipbridge.core.network.SystemLimitsRecord
import com.xushuangbo.clipbridge.core.network.TokenBundle
import com.xushuangbo.clipbridge.core.session.SessionStore
import com.xushuangbo.clipbridge.core.session.StoredSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RequestsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun ensureLoaded_readsProfileAndRequestLists() = runTest {
        val viewModel = RequestsViewModel(
            sessionStore = FakeRequestsSessionStore(),
            authApiClient = FakeRequestsAuthApiClient(),
            requestApiClient = FakeRequestsApiClient(
                quotaList = listOf(fakeQuotaRequest()),
                bandwidthList = listOf(fakeBandwidthRequest()),
                adminList = listOf(fakeAdminRequest()),
            ),
        )

        viewModel.ensureLoaded()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.quotaRequests.size)
        assertEquals(1, viewModel.uiState.value.bandwidthRequests.size)
        assertEquals(1, viewModel.uiState.value.adminRequests.size)
        assertEquals(50L * MB, viewModel.uiState.value.maxUploadFileBytes)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun submitAdminRequest_isBlockedWhenCurrentUserIsAdmin() = runTest {
        val viewModel = RequestsViewModel(
            sessionStore = FakeRequestsSessionStore(
                storedSession = StoredSession(
                    baseUrl = "http://127.0.0.1:18080",
                    accessToken = "access-1",
                    refreshToken = "refresh-1",
                    currentDeviceId = "device-1",
                    username = "alice",
                    deviceName = "android-test",
                    isAdmin = true,
                ),
            ),
            authApiClient = FakeRequestsAuthApiClient(
                profile = fakeAccountProfile(isAdmin = true),
            ),
            requestApiClient = FakeRequestsApiClient(),
        )

        viewModel.submitAdminRequest()

        assertEquals("当前账号已经是管理员，无需再次申请", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun submitQuotaRequest_showsConflictMessage() = runTest {
        val viewModel = RequestsViewModel(
            sessionStore = FakeRequestsSessionStore(),
            authApiClient = FakeRequestsAuthApiClient(),
            requestApiClient = FakeRequestsApiClient(
                createQuotaError = AuthApiException(
                    httpCode = 409,
                    message = "you already have a pending quota request",
                ),
            ),
        )

        viewModel.updateQuotaDraftMb("256")
        viewModel.updateQuotaReasonDraft("我要上传更多文件")
        viewModel.submitQuotaRequest()
        advanceUntilIdle()

        assertEquals("you already have a pending quota request", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isSubmittingQuota)
    }

    @Test
    fun submitBandwidthRequest_withUnauthorizedErrorClearsSession() = runTest {
        val sessionStore = FakeRequestsSessionStore()
        val viewModel = RequestsViewModel(
            sessionStore = sessionStore,
            authApiClient = FakeRequestsAuthApiClient(),
            requestApiClient = FakeRequestsApiClient(
                createBandwidthError = AuthApiException(
                    httpCode = 401,
                    message = "expired",
                ),
            ),
        )

        viewModel.updateBandwidthUploadDraft("4")
        viewModel.updateBandwidthDownloadDraft("8")
        viewModel.submitBandwidthRequest()
        advanceUntilIdle()

        assertTrue(sessionStore.readSession().accessToken.isBlank())
        assertEquals("", sessionStore.readSession().username)
    }
}

private class FakeRequestsSessionStore(
    private var storedSession: StoredSession = StoredSession(
        baseUrl = "http://127.0.0.1:18080",
        accessToken = "access-1",
        refreshToken = "refresh-1",
        currentDeviceId = "device-1",
        username = "alice",
        deviceName = "android-test",
    ),
    private var lastAckSeq: Long = 0L,
) : SessionStore {
    override fun readSession(): StoredSession = storedSession

    override fun isSyncEnabled(): Boolean = false

    override fun readLastAckSeq(): Long = lastAckSeq

    override fun saveBaseUrl(baseUrl: String) {
        storedSession = storedSession.copy(baseUrl = baseUrl)
    }

    override fun saveDeviceName(deviceName: String) {
        storedSession = storedSession.copy(deviceName = deviceName)
    }

    override fun saveSyncEnabled(enabled: Boolean) = Unit

    override fun saveLastAckSeq(seq: Long) {
        lastAckSeq = seq
    }

    override fun saveAuthBundle(
        baseUrl: String,
        username: String,
        deviceName: String,
        currentDeviceId: String,
        tokens: TokenBundle,
        isAdmin: Boolean,
        storageQuotaBytes: Long,
        uploadBandwidthKbps: Int,
        downloadBandwidthKbps: Int,
    ) {
        storedSession = storedSession.copy(
            baseUrl = baseUrl,
            username = username,
            deviceName = deviceName,
            currentDeviceId = currentDeviceId,
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            isAdmin = isAdmin,
            storageQuotaBytes = storageQuotaBytes,
            uploadBandwidthKbps = uploadBandwidthKbps,
            downloadBandwidthKbps = downloadBandwidthKbps,
        )
    }

    override fun updateTokens(tokens: TokenBundle) {
        storedSession = storedSession.copy(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
        )
    }

    override fun clearClipboardSyncState() {
        lastAckSeq = 0L
    }

    override fun clearAuth() {
        storedSession = storedSession.copy(
            accessToken = "",
            refreshToken = "",
            currentDeviceId = "",
            username = "",
            isAdmin = false,
            storageQuotaBytes = 0L,
            uploadBandwidthKbps = 0,
            downloadBandwidthKbps = 0,
        )
    }
}

private class FakeRequestsAuthApiClient(
    private val profile: AccountProfileResult = fakeAccountProfile(),
) : AuthApiClient {
    override suspend fun testConnection(baseUrl: String) = Unit

    override suspend fun register(
        baseUrl: String,
        username: String,
        password: String,
        deviceName: String,
    ): AuthResult = error("unused")

    override suspend fun login(
        baseUrl: String,
        username: String,
        password: String,
        deviceName: String,
    ): AuthResult = error("unused")

    override suspend fun refresh(baseUrl: String, refreshToken: String): TokenBundle = error("unused")

    override suspend fun getCurrentAccount(
        session: StoredSession,
        onRefreshing: (() -> Unit)?,
    ): AccountProfileResult = profile

    override suspend fun listDevices(
        session: StoredSession,
        onRefreshing: (() -> Unit)?,
    ): DeviceListResult = error("unused")

    override suspend fun updateDeviceName(
        session: StoredSession,
        deviceId: String,
        deviceName: String,
        onRefreshing: (() -> Unit)?,
    ): DeviceMutationResult = error("unused")

    override suspend fun forceOfflineDevice(
        session: StoredSession,
        deviceId: String,
        onRefreshing: (() -> Unit)?,
    ): ForceOfflineResult = error("unused")

    override suspend fun changePassword(
        session: StoredSession,
        currentPassword: String,
        newPassword: String,
        onRefreshing: (() -> Unit)?,
    ): ChangePasswordResult = error("unused")

    override suspend fun logout(session: StoredSession) = Unit
}

private class FakeRequestsApiClient(
    private val quotaList: List<QuotaRequestRecord> = emptyList(),
    private val bandwidthList: List<BandwidthRequestRecord> = emptyList(),
    private val adminList: List<AdminPrivilegeRequestRecord> = emptyList(),
    private val createQuotaError: Exception? = null,
    private val createBandwidthError: Exception? = null,
) : RequestApiClient {
    override suspend fun createQuotaRequest(
        session: StoredSession,
        requestedQuotaMb: Long,
        reason: String,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<QuotaRequestRecord> {
        createQuotaError?.let { throw it }
        return RequestMutationResult(fakeQuotaRequest())
    }

    override suspend fun listQuotaRequests(
        session: StoredSession,
        status: String,
        onRefreshing: (() -> Unit)?,
    ): RequestListResult<QuotaRequestRecord> = RequestListResult(quotaList, status)

    override suspend fun createBandwidthRequest(
        session: StoredSession,
        requestedUploadKbps: Int,
        requestedDownloadKbps: Int,
        reason: String,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<BandwidthRequestRecord> {
        createBandwidthError?.let { throw it }
        return RequestMutationResult(fakeBandwidthRequest())
    }

    override suspend fun listBandwidthRequests(
        session: StoredSession,
        status: String,
        onRefreshing: (() -> Unit)?,
    ): RequestListResult<BandwidthRequestRecord> = RequestListResult(bandwidthList, status)

    override suspend fun createAdminRequest(
        session: StoredSession,
        reason: String,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<AdminPrivilegeRequestRecord> = RequestMutationResult(fakeAdminRequest())

    override suspend fun listAdminRequests(
        session: StoredSession,
        status: String,
        onRefreshing: (() -> Unit)?,
    ): RequestListResult<AdminPrivilegeRequestRecord> = RequestListResult(adminList, status)
}

private fun fakeAccountProfile(isAdmin: Boolean = false): AccountProfileResult {
    return AccountProfileResult(
        userId = "user-1",
        username = "alice",
        currentDeviceId = "device-1",
        isAdmin = isAdmin,
        storageQuotaBytes = 100L * MB,
        uploadBandwidthKbps = 2048,
        downloadBandwidthKbps = 4096,
        storageUsedBytes = 5L * MB,
        storageFreeBytes = 95L * MB,
        limits = SystemLimitsRecord(
            maxUserCount = 200,
            defaultStorageQuotaBytes = 100L * MB,
            defaultUploadBandwidthKbps = 2048,
            defaultDownloadBandwidthKbps = 4096,
            maxUserUploadBandwidthKbps = 10240,
            maxUserDownloadBandwidthKbps = 20480,
            maxUploadFileBytes = 50L * MB,
            allowRegistration = false,
        ),
    )
}

private fun fakeQuotaRequest(): QuotaRequestRecord {
    return QuotaRequestRecord(
        id = "quota-1",
        userId = "user-1",
        username = "alice",
        requestedQuotaBytes = 200L * MB,
        currentQuotaBytes = 100L * MB,
        reason = "需要更多空间",
        status = "pending",
        reviewedBy = "",
        reviewedByUsername = "",
        reviewNote = "",
        createdAt = "2026-06-01T10:00:00Z",
        reviewedAt = "",
    )
}

private fun fakeBandwidthRequest(): BandwidthRequestRecord {
    return BandwidthRequestRecord(
        id = "bandwidth-1",
        userId = "user-1",
        username = "alice",
        requestedUploadKbps = 4096,
        requestedDownloadKbps = 8192,
        currentUploadKbps = 2048,
        currentDownloadKbps = 4096,
        reason = "需要更快速度",
        status = "pending",
        reviewedBy = "",
        reviewedByUsername = "",
        reviewNote = "",
        createdAt = "2026-06-01T10:00:00Z",
        reviewedAt = "",
    )
}

private fun fakeAdminRequest(): AdminPrivilegeRequestRecord {
    return AdminPrivilegeRequestRecord(
        id = "admin-1",
        userId = "user-1",
        username = "alice",
        reason = "需要审批权限",
        status = "pending",
        reviewedBy = "",
        reviewedByUsername = "",
        reviewNote = "",
        createdAt = "2026-06-01T10:00:00Z",
        reviewedAt = "",
    )
}

private const val MB = 1024L * 1024L
