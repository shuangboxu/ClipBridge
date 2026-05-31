package com.xushuangbo.clipbridge

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.xushuangbo.clipbridge.app.AppContainer
import com.xushuangbo.clipbridge.app.AppTestTags
import com.xushuangbo.clipbridge.app.ClipBridgeApp
import com.xushuangbo.clipbridge.core.network.AuthApiClient
import com.xushuangbo.clipbridge.core.network.AuthResult
import com.xushuangbo.clipbridge.core.network.ClipboardApiClient
import com.xushuangbo.clipbridge.core.network.ClipboardHistoryResult
import com.xushuangbo.clipbridge.core.network.ClipboardItem
import com.xushuangbo.clipbridge.core.network.ClipboardUploadResult
import com.xushuangbo.clipbridge.core.network.DeviceListResult
import com.xushuangbo.clipbridge.core.network.DeviceMutationResult
import com.xushuangbo.clipbridge.core.network.ForceOfflineResult
import com.xushuangbo.clipbridge.core.network.SyncPullResult
import com.xushuangbo.clipbridge.core.network.TokenBundle
import com.xushuangbo.clipbridge.core.session.SessionStore
import com.xushuangbo.clipbridge.core.session.StoredSession
import com.xushuangbo.clipbridge.core.sync.ClipboardSyncCoordinator
import com.xushuangbo.clipbridge.core.sync.HistoryUpdateBus
import com.xushuangbo.clipbridge.feature.auth.AuthScreen
import com.xushuangbo.clipbridge.feature.auth.AuthUiState
import com.xushuangbo.clipbridge.ui.theme.ClipBridgeTheme
import org.junit.Rule
import org.junit.Test

class AuthFlowUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun switchToRegisterMode_showsConfirmPasswordField() {
        composeRule.setContent {
            var uiState by remember {
                mutableStateOf(
                    AuthUiState(
                        serviceAddress = "http://127.0.0.1:18080",
                        deviceName = "android-test",
                    ),
                )
            }

            ClipBridgeTheme {
                AuthScreen(
                    uiState = uiState,
                    onModeChange = { uiState = uiState.copy(mode = it) },
                    onServiceAddressChange = { uiState = uiState.copy(serviceAddress = it) },
                    onUsernameChange = { uiState = uiState.copy(username = it) },
                    onPasswordChange = { uiState = uiState.copy(password = it) },
                    onConfirmPasswordChange = { uiState = uiState.copy(confirmPassword = it) },
                    onDeviceNameChange = { uiState = uiState.copy(deviceName = it) },
                    onTestConnection = {},
                    onSubmit = {},
                )
            }
        }

        composeRule.onNodeWithTag(AppTestTags.AuthRegisterToggle).performClick()
        composeRule.onNodeWithTag(AppTestTags.AuthConfirmPasswordField).assertIsDisplayed()
    }

    @Test
    fun successfulLogin_navigatesToHomeScreen() {
        val sessionStore = InMemorySessionStore()
        val authApiClient = FakeUiAuthApiClient()
        val clipboardApiClient = FakeUiClipboardApiClient()

        composeRule.setContent {
            ClipBridgeTheme {
                ClipBridgeApp(
                    appContainer = createTestAppContainer(
                        sessionStore = sessionStore,
                        authApiClient = authApiClient,
                        clipboardApiClient = clipboardApiClient,
                    ),
                )
            }
        }

        composeRule.onNodeWithTag(AppTestTags.AuthServiceAddressField).performTextInput("http://127.0.0.1:18080")
        composeRule.onNodeWithTag(AppTestTags.AuthUsernameField).performTextInput("alice")
        composeRule.onNodeWithTag(AppTestTags.AuthPasswordField).performTextInput("password123")
        composeRule.onNodeWithTag(AppTestTags.AuthSubmitButton).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(AppTestTags.HomeScreen).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(AppTestTags.HomeScreen).assertIsDisplayed()
    }

    @Test
    fun historyTab_showsManualUploadAndPullActions() {
        val sessionStore = InMemorySessionStore(
            storedSession = StoredSession(
                baseUrl = "http://127.0.0.1:18080",
                accessToken = "access-1",
                refreshToken = "refresh-1",
                currentDeviceId = "device-1",
                username = "alice",
                deviceName = "android-test",
            ),
        )
        val authApiClient = FakeUiAuthApiClient(
            currentAccountResult = AuthResult(
                userId = "user-1",
                username = "alice",
                currentDeviceId = "device-1",
            ),
        )
        val clipboardApiClient = FakeUiClipboardApiClient()

        composeRule.setContent {
            ClipBridgeTheme {
                ClipBridgeApp(
                    appContainer = createTestAppContainer(
                        sessionStore = sessionStore,
                        authApiClient = authApiClient,
                        clipboardApiClient = clipboardApiClient,
                    ),
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(AppTestTags.HomeScreen).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("历史").performClick()
        composeRule.onNodeWithTag(AppTestTags.HistoryUploadButton).assertIsDisplayed()
        composeRule.onNodeWithTag(AppTestTags.HistoryRefreshButton).assertIsDisplayed()
        composeRule.onNodeWithTag(AppTestTags.HistoryUploadButton).performClick()
        composeRule.onNodeWithText("手动上传文本").assertIsDisplayed()
    }
}

private fun createTestAppContainer(
    sessionStore: SessionStore,
    authApiClient: AuthApiClient,
    clipboardApiClient: ClipboardApiClient,
): AppContainer {
    return AppContainer(
        sessionStore = sessionStore,
        authApiClient = authApiClient,
        clipboardApiClient = clipboardApiClient,
        clipboardSyncCoordinator = ClipboardSyncCoordinator(
            sessionStore = sessionStore,
            clipboardApiClient = clipboardApiClient,
        ),
        historyUpdateBus = HistoryUpdateBus(),
        defaultDeviceName = "android-test",
    )
}

private class InMemorySessionStore(
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

private class FakeUiAuthApiClient(
    private val currentAccountResult: AuthResult? = null,
) : AuthApiClient {
    override suspend fun testConnection(baseUrl: String) = Unit

    override suspend fun register(
        baseUrl: String,
        username: String,
        password: String,
        deviceName: String,
    ): AuthResult {
        return createResult(username, deviceName)
    }

    override suspend fun login(
        baseUrl: String,
        username: String,
        password: String,
        deviceName: String,
    ): AuthResult {
        return createResult(username, deviceName)
    }

    override suspend fun refresh(baseUrl: String, refreshToken: String): TokenBundle {
        return TokenBundle("access-2", "refresh-2")
    }

    override suspend fun getCurrentAccount(
        session: StoredSession,
        onRefreshing: (() -> Unit)?,
    ): AuthResult {
        return currentAccountResult ?: error("getCurrentAccount should not be called in this test")
    }

    override suspend fun listDevices(
        session: StoredSession,
        onRefreshing: (() -> Unit)?,
    ): DeviceListResult {
        error("listDevices should not be called in this test")
    }

    override suspend fun updateDeviceName(
        session: StoredSession,
        deviceId: String,
        deviceName: String,
        onRefreshing: (() -> Unit)?,
    ): DeviceMutationResult {
        error("updateDeviceName should not be called in this test")
    }

    override suspend fun forceOfflineDevice(
        session: StoredSession,
        deviceId: String,
        onRefreshing: (() -> Unit)?,
    ): ForceOfflineResult {
        error("forceOfflineDevice should not be called in this test")
    }

    override suspend fun logout(session: StoredSession) = Unit

    private fun createResult(username: String, deviceName: String): AuthResult {
        return AuthResult(
            userId = "user-1",
            username = username,
            currentDeviceId = "device-1",
            deviceName = deviceName,
            tokens = TokenBundle(
                accessToken = "access-1",
                refreshToken = "refresh-1",
            ),
        )
    }
}

private class FakeUiClipboardApiClient : ClipboardApiClient {
    override suspend fun uploadText(
        session: StoredSession,
        text: String,
        onRefreshing: (() -> Unit)?,
    ): ClipboardUploadResult {
        return ClipboardUploadResult(
            item = ClipboardItem(
                id = "item-1",
                seq = 1L,
                contentType = "text",
                textContent = text,
                originDeviceId = session.currentDeviceId,
                isCurrentDeviceOrigin = true,
                createdAt = "2026-05-26T10:00:00Z",
            ),
            deduplicated = false,
        )
    }

    override suspend fun listClipboardItems(
        session: StoredSession,
        limit: Int,
        beforeSeq: Long?,
        onRefreshing: (() -> Unit)?,
    ): ClipboardHistoryResult {
        return ClipboardHistoryResult(
            items = emptyList(),
            hasMore = false,
            latestSeq = 0L,
            currentDeviceAckSeq = 0L,
        )
    }

    override suspend fun pullSync(
        session: StoredSession,
        sinceSeq: Long,
        limit: Int,
        onRefreshing: (() -> Unit)?,
    ): SyncPullResult {
        return SyncPullResult(
            items = emptyList(),
            nextSinceSeq = null,
        )
    }

    override suspend fun ackSync(
        session: StoredSession,
        seq: Long,
        onRefreshing: (() -> Unit)?,
    ): TokenBundle? = null
}
