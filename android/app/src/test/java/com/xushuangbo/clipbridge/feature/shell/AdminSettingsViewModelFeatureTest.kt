package com.xushuangbo.clipbridge.feature.shell

import com.xushuangbo.clipbridge.MainDispatcherRule
import com.xushuangbo.clipbridge.core.network.AccountProfileResult
import com.xushuangbo.clipbridge.core.network.AdminApiClient
import com.xushuangbo.clipbridge.core.network.AdminDeleteUserResult
import com.xushuangbo.clipbridge.core.network.AdminSettingsRecord
import com.xushuangbo.clipbridge.core.network.AdminSettingsResult
import com.xushuangbo.clipbridge.core.network.AdminUserRecord
import com.xushuangbo.clipbridge.core.network.AdminUsersResult
import com.xushuangbo.clipbridge.core.network.AdminPrivilegeRequestRecord
import com.xushuangbo.clipbridge.core.network.ApproveBandwidthRequestInput
import com.xushuangbo.clipbridge.core.network.ApproveQuotaRequestInput
import com.xushuangbo.clipbridge.core.network.AuthApiClient
import com.xushuangbo.clipbridge.core.network.AuthResult
import com.xushuangbo.clipbridge.core.network.BandwidthRequestRecord
import com.xushuangbo.clipbridge.core.network.ChangePasswordResult
import com.xushuangbo.clipbridge.core.network.DeviceListResult
import com.xushuangbo.clipbridge.core.network.DeviceMutationResult
import com.xushuangbo.clipbridge.core.network.ForceOfflineResult
import com.xushuangbo.clipbridge.core.network.QuotaRequestRecord
import com.xushuangbo.clipbridge.core.network.RequestListResult
import com.xushuangbo.clipbridge.core.network.RequestMutationResult
import com.xushuangbo.clipbridge.core.network.ReviewNoteInput
import com.xushuangbo.clipbridge.core.network.SystemLimitsRecord
import com.xushuangbo.clipbridge.core.network.TokenBundle
import com.xushuangbo.clipbridge.core.network.UpdateAdminSettingsInput
import com.xushuangbo.clipbridge.core.network.UpdateAdminUserInput
import com.xushuangbo.clipbridge.core.session.SessionStore
import com.xushuangbo.clipbridge.core.session.StoredSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminSettingsViewModelFeatureTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun ensureLoaded_readsExistingSettings() = runTest {
        val viewModel = AdminSettingsViewModel(
            sessionStore = FakeAdminSettingsSessionStore(),
            authApiClient = FakeAdminSettingsAuthApiClient(),
            adminApiClient = FakeAdminSettingsApiClient(),
        )

        viewModel.ensureLoaded()
        advanceUntilIdle()

        assertEquals("200", viewModel.uiState.value.maxUserCountDraft)
        assertEquals("100", viewModel.uiState.value.defaultStorageQuotaMbDraft)
        assertEquals(12, viewModel.uiState.value.currentUserCount)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun saveSettings_withInvalidNumberShowsError() = runTest {
        val viewModel = AdminSettingsViewModel(
            sessionStore = FakeAdminSettingsSessionStore(),
            authApiClient = FakeAdminSettingsAuthApiClient(),
            adminApiClient = FakeAdminSettingsApiClient(),
        )

        viewModel.updateMaxUserCountDraft("0")
        viewModel.saveSettings()

        assertEquals("最大用户数必须是大于 0 的整数", viewModel.uiState.value.errorMessage)
    }
}

private class FakeAdminSettingsSessionStore(
    private var storedSession: StoredSession = StoredSession(
        baseUrl = "http://127.0.0.1:18080",
        accessToken = "access-1",
        refreshToken = "refresh-1",
        currentDeviceId = "device-1",
        username = "alice",
        deviceName = "android-test",
        isAdmin = true,
    ),
) : SessionStore {
    override fun readSession(): StoredSession = storedSession

    override fun isSyncEnabled(): Boolean = false

    override fun readLastAckSeq(): Long = 0L

    override fun saveBaseUrl(baseUrl: String) {
        storedSession = storedSession.copy(baseUrl = baseUrl)
    }

    override fun saveDeviceName(deviceName: String) {
        storedSession = storedSession.copy(deviceName = deviceName)
    }

    override fun saveSyncEnabled(enabled: Boolean) = Unit

    override fun saveLastAckSeq(seq: Long) = Unit

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

    override fun clearClipboardSyncState() = Unit

    override fun clearAuth() {
        storedSession = storedSession.copy(
            accessToken = "",
            refreshToken = "",
            currentDeviceId = "",
            username = "",
        )
    }
}

private class FakeAdminSettingsAuthApiClient : AuthApiClient {
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
    ): AccountProfileResult {
        return AccountProfileResult(
            userId = "user-1",
            username = "alice",
            currentDeviceId = "device-1",
            isAdmin = true,
            storageQuotaBytes = 100L * MB,
            uploadBandwidthKbps = 2048,
            downloadBandwidthKbps = 4096,
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

private class FakeAdminSettingsApiClient : AdminApiClient {
    override suspend fun getSettings(
        session: StoredSession,
        onRefreshing: (() -> Unit)?,
    ): AdminSettingsResult {
        return AdminSettingsResult(
            settings = fakeAdminSettingsRecord(),
            currentUserCount = 12,
        )
    }

    override suspend fun updateSettings(
        session: StoredSession,
        input: UpdateAdminSettingsInput,
        onRefreshing: (() -> Unit)?,
    ): AdminSettingsResult {
        return AdminSettingsResult(
            settings = fakeAdminSettingsRecord(),
            currentUserCount = 12,
        )
    }

    override suspend fun listUsers(
        session: StoredSession,
        onRefreshing: (() -> Unit)?,
    ): AdminUsersResult = error("unused")

    override suspend fun updateUser(
        session: StoredSession,
        userId: String,
        input: UpdateAdminUserInput,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<AdminUserRecord> = error("unused")

    override suspend fun deleteUser(
        session: StoredSession,
        userId: String,
        onRefreshing: (() -> Unit)?,
    ): AdminDeleteUserResult = error("unused")

    override suspend fun listPendingQuotaRequests(
        session: StoredSession,
        onRefreshing: (() -> Unit)?,
    ): RequestListResult<QuotaRequestRecord> = error("unused")

    override suspend fun approveQuotaRequest(
        session: StoredSession,
        requestId: String,
        input: ApproveQuotaRequestInput,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<QuotaRequestRecord> = error("unused")

    override suspend fun rejectQuotaRequest(
        session: StoredSession,
        requestId: String,
        input: ReviewNoteInput,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<QuotaRequestRecord> = error("unused")

    override suspend fun listPendingBandwidthRequests(
        session: StoredSession,
        onRefreshing: (() -> Unit)?,
    ): RequestListResult<BandwidthRequestRecord> = error("unused")

    override suspend fun approveBandwidthRequest(
        session: StoredSession,
        requestId: String,
        input: ApproveBandwidthRequestInput,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<BandwidthRequestRecord> = error("unused")

    override suspend fun rejectBandwidthRequest(
        session: StoredSession,
        requestId: String,
        input: ReviewNoteInput,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<BandwidthRequestRecord> = error("unused")

    override suspend fun listPendingAdminRequests(
        session: StoredSession,
        onRefreshing: (() -> Unit)?,
    ): RequestListResult<AdminPrivilegeRequestRecord> = error("unused")

    override suspend fun approveAdminRequest(
        session: StoredSession,
        requestId: String,
        input: ReviewNoteInput,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<AdminPrivilegeRequestRecord> = error("unused")

    override suspend fun rejectAdminRequest(
        session: StoredSession,
        requestId: String,
        input: ReviewNoteInput,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<AdminPrivilegeRequestRecord> = error("unused")
}

private fun fakeAdminSettingsRecord(): AdminSettingsRecord {
    return AdminSettingsRecord(
        maxUserCount = 200,
        defaultStorageQuotaBytes = 100L * MB,
        defaultUploadBandwidthKbps = 2048,
        defaultDownloadBandwidthKbps = 4096,
        maxUserUploadBandwidthKbps = 10240,
        maxUserDownloadBandwidthKbps = 20480,
        maxUploadFileBytes = 50L * MB,
        allowRegistration = false,
        updatedAt = "2026-06-01T10:00:00Z",
    )
}

private const val MB = 1024L * 1024L
