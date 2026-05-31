package com.xushuangbo.clipbridge.feature.shell

import com.xushuangbo.clipbridge.MainDispatcherRule
import com.xushuangbo.clipbridge.core.network.AuthApiClient
import com.xushuangbo.clipbridge.core.network.AuthApiException
import com.xushuangbo.clipbridge.core.network.AuthResult
import com.xushuangbo.clipbridge.core.network.ChangePasswordResult
import com.xushuangbo.clipbridge.core.network.DeviceListResult
import com.xushuangbo.clipbridge.core.network.DeviceMutationResult
import com.xushuangbo.clipbridge.core.network.ForceOfflineResult
import com.xushuangbo.clipbridge.core.network.TokenBundle
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
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun saveServiceAddress_clearsAuthAndAckState() = runTest {
        val sessionStore = FakeSettingsSessionStore(lastAckSeq = 9L)
        val viewModel = SettingsViewModel(
            sessionStore = sessionStore,
            authApiClient = FakeSettingsAuthApiClient(),
        )

        viewModel.updateServiceAddress("http://127.0.0.1:28080")
        viewModel.saveServiceAddress()
        advanceUntilIdle()

        assertEquals("http://127.0.0.1:28080", sessionStore.readSession().baseUrl)
        assertEquals("", sessionStore.readSession().accessToken)
        assertEquals(0L, sessionStore.readLastAckSeq())
    }

    @Test
    fun logout_clearsAuthAndAckState() = runTest {
        val sessionStore = FakeSettingsSessionStore(lastAckSeq = 9L)
        val viewModel = SettingsViewModel(
            sessionStore = sessionStore,
            authApiClient = FakeSettingsAuthApiClient(),
        )

        viewModel.logout()
        advanceUntilIdle()

        assertEquals("", sessionStore.readSession().accessToken)
        assertEquals("", sessionStore.readSession().refreshToken)
        assertEquals(0L, sessionStore.readLastAckSeq())
    }

    @Test
    fun changePassword_successClearsFormAndUpdatesTokens() = runTest {
        val sessionStore = FakeSettingsSessionStore()
        val viewModel = SettingsViewModel(
            sessionStore = sessionStore,
            authApiClient = FakeSettingsAuthApiClient(
                changePasswordResult = ChangePasswordResult(
                    userId = "user-1",
                    username = "alice",
                    tokens = TokenBundle("access-2", "refresh-2"),
                ),
            ),
        )

        viewModel.updateCurrentPassword("password123")
        viewModel.updateNewPassword("new-password-456")
        viewModel.updateConfirmNewPassword("new-password-456")
        viewModel.changePassword()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.currentPassword)
        assertEquals("", viewModel.uiState.value.newPassword)
        assertEquals("", viewModel.uiState.value.confirmNewPassword)
        assertEquals("access-2", sessionStore.readSession().accessToken)
        assertEquals("refresh-2", sessionStore.readSession().refreshToken)
        assertFalse(viewModel.uiState.value.isChangingPassword)
    }

    @Test
    fun changePassword_withWrongCurrentPasswordShowsError() = runTest {
        val viewModel = SettingsViewModel(
            sessionStore = FakeSettingsSessionStore(),
            authApiClient = FakeSettingsAuthApiClient(
                changePasswordError = AuthApiException(
                    httpCode = 401,
                    message = "current password is incorrect",
                ),
            ),
        )

        viewModel.updateCurrentPassword("wrong-password")
        viewModel.updateNewPassword("new-password-456")
        viewModel.updateConfirmNewPassword("new-password-456")
        viewModel.changePassword()
        advanceUntilIdle()

        assertEquals("当前密码不正确", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isChangingPassword)
    }
}

private class FakeSettingsSessionStore(
    private var storedSession: StoredSession = StoredSession(
        baseUrl = "http://127.0.0.1:18080",
        accessToken = "access-1",
        refreshToken = "refresh-1",
        currentDeviceId = "device-1",
        username = "alice",
        deviceName = "android-test",
    ),
    private var syncEnabled: Boolean = false,
    private var lastAckSeq: Long = 0L,
) : SessionStore {
    override fun readSession(): StoredSession = storedSession

    override fun isSyncEnabled(): Boolean = syncEnabled

    override fun readLastAckSeq(): Long = lastAckSeq

    override fun saveBaseUrl(baseUrl: String) {
        storedSession = storedSession.copy(baseUrl = baseUrl)
    }

    override fun saveDeviceName(deviceName: String) {
        storedSession = storedSession.copy(deviceName = deviceName)
    }

    override fun saveSyncEnabled(enabled: Boolean) {
        syncEnabled = enabled
    }

    override fun saveLastAckSeq(seq: Long) {
        lastAckSeq = seq
    }

    override fun saveAuthBundle(
        baseUrl: String,
        username: String,
        deviceName: String,
        currentDeviceId: String,
        tokens: TokenBundle,
    ) {
        storedSession = StoredSession(
            baseUrl = baseUrl,
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            currentDeviceId = currentDeviceId,
            username = username,
            deviceName = deviceName,
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
        )
    }
}

private class FakeSettingsAuthApiClient(
    private val changePasswordResult: ChangePasswordResult = ChangePasswordResult(
        userId = "user-1",
        username = "alice",
        tokens = null,
    ),
    private val changePasswordError: Exception? = null,
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
    ): AuthResult = error("unused")

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
    ): ChangePasswordResult {
        changePasswordError?.let { throw it }
        return changePasswordResult
    }

    override suspend fun logout(session: StoredSession) = Unit
}
