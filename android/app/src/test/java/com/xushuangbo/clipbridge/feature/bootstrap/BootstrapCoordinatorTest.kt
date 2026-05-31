package com.xushuangbo.clipbridge.feature.bootstrap

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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BootstrapCoordinatorTest {
    @Test
    fun run_returnsMissingServerWhenBaseUrlIsEmpty() = runBlocking {
        val sessionStore = FakeSessionStore()
        val authApiClient = FakeAuthApiClient()
        val coordinator = BootstrapCoordinator(sessionStore, authApiClient)

        val states = mutableListOf<BootstrapState>()
        val result = coordinator.run { states += it }

        assertEquals(BootstrapState.MissingServer(), result)
        assertEquals(listOf(BootstrapState.LoadingLocal), states)
    }

    @Test
    fun run_reportsRefreshingAndSavesRotatedTokens() = runBlocking {
        val sessionStore = FakeSessionStore(
            storedSession = StoredSession(
                baseUrl = "http://127.0.0.1:18080",
                accessToken = "old-access",
                refreshToken = "old-refresh",
                currentDeviceId = "device-1",
                username = "alice",
                deviceName = "android-pixel",
            ),
            lastAckSeq = 12L,
        )
        val authApiClient = FakeAuthApiClient(
            currentAccountResult = AuthResult(
                userId = "user-1",
                username = "alice",
                currentDeviceId = "device-1",
                tokens = TokenBundle(
                    accessToken = "new-access",
                    refreshToken = "new-refresh",
                ),
            ),
            triggersRefreshing = true,
        )
        val coordinator = BootstrapCoordinator(sessionStore, authApiClient)

        val states = mutableListOf<BootstrapState>()
        val result = coordinator.run { states += it }

        assertEquals(BootstrapState.Ready(), result)
        assertEquals(
            listOf(
                BootstrapState.LoadingLocal,
                BootstrapState.CheckingAccess(),
                BootstrapState.Refreshing(),
            ),
            states,
        )
        assertEquals("new-access", sessionStore.readSession().accessToken)
        assertEquals("new-refresh", sessionStore.readSession().refreshToken)
    }

    @Test
    fun run_clearsSessionWhenRefreshIsUnauthorized() = runBlocking {
        val sessionStore = FakeSessionStore(
            storedSession = StoredSession(
                baseUrl = "http://127.0.0.1:18080",
                accessToken = "old-access",
                refreshToken = "old-refresh",
                currentDeviceId = "device-1",
                username = "alice",
                deviceName = "android-pixel",
            ),
        )
        val authApiClient = FakeAuthApiClient(
            currentAccountError = AuthApiException(
                httpCode = 401,
                message = "invalid refresh token",
            ),
        )
        val coordinator = BootstrapCoordinator(sessionStore, authApiClient)

        val result = coordinator.run { }

        assertEquals(BootstrapState.LoggedOut("登录已失效，请重新登录"), result)
        assertTrue(sessionStore.readSession().accessToken.isBlank())
        assertEquals("http://127.0.0.1:18080", sessionStore.readSession().baseUrl)
        assertEquals(0L, sessionStore.readLastAckSeq())
    }
}

private class FakeSessionStore(
    private var storedSession: StoredSession = StoredSession(),
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

private class FakeAuthApiClient(
    private val currentAccountResult: AuthResult = AuthResult(
        userId = "user-1",
        username = "alice",
        currentDeviceId = "device-1",
    ),
    private val currentAccountError: Exception? = null,
    private val triggersRefreshing: Boolean = false,
) : AuthApiClient {
    override suspend fun testConnection(baseUrl: String) = Unit

    override suspend fun register(
        baseUrl: String,
        username: String,
        password: String,
        deviceName: String,
    ): AuthResult {
        error("register should not be called in bootstrap tests")
    }

    override suspend fun login(
        baseUrl: String,
        username: String,
        password: String,
        deviceName: String,
    ): AuthResult {
        error("login should not be called in bootstrap tests")
    }

    override suspend fun refresh(baseUrl: String, refreshToken: String): TokenBundle {
        return TokenBundle("new-access", "new-refresh")
    }

    override suspend fun getCurrentAccount(
        session: StoredSession,
        onRefreshing: (() -> Unit)?,
    ): AuthResult {
        currentAccountError?.let { throw it }
        if (triggersRefreshing) {
            onRefreshing?.invoke()
        }
        return currentAccountResult
    }

    override suspend fun listDevices(
        session: StoredSession,
        onRefreshing: (() -> Unit)?,
    ): DeviceListResult {
        error("listDevices should not be called in bootstrap tests")
    }

    override suspend fun updateDeviceName(
        session: StoredSession,
        deviceId: String,
        deviceName: String,
        onRefreshing: (() -> Unit)?,
    ): DeviceMutationResult {
        error("updateDeviceName should not be called in bootstrap tests")
    }

    override suspend fun forceOfflineDevice(
        session: StoredSession,
        deviceId: String,
        onRefreshing: (() -> Unit)?,
    ): ForceOfflineResult {
        error("forceOfflineDevice should not be called in bootstrap tests")
    }

    override suspend fun changePassword(
        session: StoredSession,
        currentPassword: String,
        newPassword: String,
        onRefreshing: (() -> Unit)?,
    ): ChangePasswordResult {
        error("changePassword should not be called in bootstrap tests")
    }

    override suspend fun logout(session: StoredSession) = Unit
}
