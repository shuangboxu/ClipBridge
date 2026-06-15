package com.xushuangbo.clipbridge

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.xushuangbo.clipbridge.app.AppContainer
import com.xushuangbo.clipbridge.app.AppTestTags
import com.xushuangbo.clipbridge.app.ClipBridgeApp
import com.xushuangbo.clipbridge.core.files.DocumentFileGateway
import com.xushuangbo.clipbridge.core.files.FileTransferCoordinator
import com.xushuangbo.clipbridge.core.files.PickedLocalFile
import com.xushuangbo.clipbridge.core.network.AccountProfileResult
import com.xushuangbo.clipbridge.core.network.AdminApiClient
import com.xushuangbo.clipbridge.core.network.AdminDeleteUserResult
import com.xushuangbo.clipbridge.core.network.AdminSettingsResult
import com.xushuangbo.clipbridge.core.network.AdminUserRecord
import com.xushuangbo.clipbridge.core.network.AdminUsersResult
import com.xushuangbo.clipbridge.core.network.ApproveBandwidthRequestInput
import com.xushuangbo.clipbridge.core.network.ApproveQuotaRequestInput
import com.xushuangbo.clipbridge.core.network.AuthApiClient
import com.xushuangbo.clipbridge.core.network.AuthResult
import com.xushuangbo.clipbridge.core.network.ChangePasswordResult
import com.xushuangbo.clipbridge.core.network.ClipboardApiClient
import com.xushuangbo.clipbridge.core.network.ClipboardHistoryResult
import com.xushuangbo.clipbridge.core.network.ClipboardItem
import com.xushuangbo.clipbridge.core.network.ClipboardUploadResult
import com.xushuangbo.clipbridge.core.network.DeviceListResult
import com.xushuangbo.clipbridge.core.network.DeviceMutationResult
import com.xushuangbo.clipbridge.core.network.FileApiClient
import com.xushuangbo.clipbridge.core.network.FileDeleteResult
import com.xushuangbo.clipbridge.core.network.FileListResult
import com.xushuangbo.clipbridge.core.network.FileListSummary
import com.xushuangbo.clipbridge.core.network.FileMutationResult
import com.xushuangbo.clipbridge.core.network.FilePagination
import com.xushuangbo.clipbridge.core.network.ForceOfflineResult
import com.xushuangbo.clipbridge.core.network.RequestApiClient
import com.xushuangbo.clipbridge.core.network.RequestListResult
import com.xushuangbo.clipbridge.core.network.RequestMutationResult
import com.xushuangbo.clipbridge.core.network.ReviewNoteInput
import com.xushuangbo.clipbridge.core.network.ShareApiClient
import com.xushuangbo.clipbridge.core.network.ShareFileCreateRequest
import com.xushuangbo.clipbridge.core.network.ShareFileRecord
import com.xushuangbo.clipbridge.core.network.ShareItemRecord
import com.xushuangbo.clipbridge.core.network.ShareListResult
import com.xushuangbo.clipbridge.core.network.ShareListSummary
import com.xushuangbo.clipbridge.core.network.ShareMutationResult
import com.xushuangbo.clipbridge.core.network.SharePagination
import com.xushuangbo.clipbridge.core.network.ShareTextCreateRequest
import com.xushuangbo.clipbridge.core.network.SystemLimitsRecord
import com.xushuangbo.clipbridge.core.network.SyncPullResult
import com.xushuangbo.clipbridge.core.network.TokenBundle
import com.xushuangbo.clipbridge.core.network.UpdateAdminSettingsInput
import com.xushuangbo.clipbridge.core.network.UpdateAdminUserInput
import com.xushuangbo.clipbridge.core.network.AdminSettingsRecord
import com.xushuangbo.clipbridge.core.network.QuotaRequestRecord
import com.xushuangbo.clipbridge.core.network.BandwidthRequestRecord
import com.xushuangbo.clipbridge.core.network.AdminPrivilegeRequestRecord
import com.xushuangbo.clipbridge.core.share.ShareCoordinator
import com.xushuangbo.clipbridge.core.share.ShareRuleConfig
import com.xushuangbo.clipbridge.core.share.ShareRulesStore
import com.xushuangbo.clipbridge.core.share.ShareStatusFilter
import com.xushuangbo.clipbridge.core.session.SessionStore
import com.xushuangbo.clipbridge.core.session.StoredSession
import com.xushuangbo.clipbridge.core.sync.ClipboardSyncCoordinator
import com.xushuangbo.clipbridge.core.sync.HistoryUpdateBus
import com.xushuangbo.clipbridge.feature.auth.AuthScreen
import com.xushuangbo.clipbridge.feature.auth.AuthUiState
import com.xushuangbo.clipbridge.ui.theme.ClipBridgeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream

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
            currentAccountResult = AccountProfileResult(
                userId = "user-1",
                username = "alice",
                currentDeviceId = "device-1",
                limits = SystemLimitsRecord(
                    maxUserCount = 200,
                    defaultStorageQuotaBytes = 100L * 1024L * 1024L,
                    defaultUploadBandwidthKbps = 2048,
                    defaultDownloadBandwidthKbps = 4096,
                    maxUserUploadBandwidthKbps = 10240,
                    maxUserDownloadBandwidthKbps = 20480,
                    maxUploadFileBytes = 50L * 1024L * 1024L,
                    allowRegistration = false,
                ),
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

    @Test
    fun shareScreen_showsStrategyButtonsAndRulesEntry() {
        val sessionStore = InMemorySessionStore(
            storedSession = createStoredSession(),
        )
        val authApiClient = FakeUiAuthApiClient(
            currentAccountResult = createCurrentAccountResult(),
        )

        composeRule.setContent {
            ClipBridgeTheme {
                ClipBridgeApp(
                    appContainer = createTestAppContainer(
                        sessionStore = sessionStore,
                        authApiClient = authApiClient,
                        clipboardApiClient = FakeUiClipboardApiClient(),
                    ),
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(AppTestTags.HomeScreen).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("分享").performClick()
        composeRule.onNodeWithTag(AppTestTags.ShareScreen).assertIsDisplayed()
        composeRule.onNodeWithTag(AppTestTags.ShareCreateButton).assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("不过期").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("过期").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("打开一次失效").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag(AppTestTags.ShareCreateButton).performClick()
        composeRule.onNodeWithTag(AppTestTags.ShareCreateDialog).assertIsDisplayed()
        composeRule.onNodeWithText("不过期").assertIsDisplayed()
        composeRule.onNodeWithText("过期").assertIsDisplayed()
        composeRule.onNodeWithText("打开一次失效").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("调整分享规则").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun nonAdminSettings_onlyShowsRequestEntry() {
        val sessionStore = InMemorySessionStore(
            storedSession = createStoredSession(isAdmin = false),
        )
        val authApiClient = FakeUiAuthApiClient(
            currentAccountResult = createCurrentAccountResult(isAdmin = false),
        )

        composeRule.setContent {
            ClipBridgeTheme {
                ClipBridgeApp(
                    appContainer = createTestAppContainer(
                        sessionStore = sessionStore,
                        authApiClient = authApiClient,
                        clipboardApiClient = FakeUiClipboardApiClient(),
                    ),
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(AppTestTags.HomeScreen).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("申请").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("全局设置").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("用户管理").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("审批").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun adminSettings_showsAdminEntries() {
        val sessionStore = InMemorySessionStore(
            storedSession = createStoredSession(isAdmin = true),
        )
        val authApiClient = FakeUiAuthApiClient(
            currentAccountResult = createCurrentAccountResult(isAdmin = true),
        )

        composeRule.setContent {
            ClipBridgeTheme {
                ClipBridgeApp(
                    appContainer = createTestAppContainer(
                        sessionStore = sessionStore,
                        authApiClient = authApiClient,
                        clipboardApiClient = FakeUiClipboardApiClient(),
                    ),
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(AppTestTags.HomeScreen).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("申请").assertIsDisplayed()
        composeRule.onNodeWithText("全局设置").assertIsDisplayed()
        composeRule.onNodeWithText("用户管理").assertIsDisplayed()
        composeRule.onNodeWithText("审批").assertIsDisplayed()
    }

    @Test
    fun adminRequestsScreen_disablesAdminRequestButton() {
        val sessionStore = InMemorySessionStore(
            storedSession = createStoredSession(isAdmin = true),
        )
        val authApiClient = FakeUiAuthApiClient(
            currentAccountResult = createCurrentAccountResult(isAdmin = true),
        )

        composeRule.setContent {
            ClipBridgeTheme {
                ClipBridgeApp(
                    appContainer = createTestAppContainer(
                        sessionStore = sessionStore,
                        authApiClient = authApiClient,
                        clipboardApiClient = FakeUiClipboardApiClient(),
                    ),
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(AppTestTags.HomeScreen).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("申请").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(AppTestTags.RequestsScreen).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(AppTestTags.RequestsAdminSubmitButton).assertIsNotEnabled()
    }
}

private fun createTestAppContainer(
    sessionStore: SessionStore,
    authApiClient: AuthApiClient,
    clipboardApiClient: ClipboardApiClient,
): AppContainer {
    val documentFileGateway = FakeUiDocumentFileGateway()
    val fileApiClient = FakeUiFileApiClient()
    val shareApiClient = FakeUiShareApiClient()
    val requestApiClient = FakeUiRequestApiClient()
    val adminApiClient = FakeUiAdminApiClient()

    return AppContainer(
        sessionStore = sessionStore,
        authApiClient = authApiClient,
        requestApiClient = requestApiClient,
        adminApiClient = adminApiClient,
        clipboardApiClient = clipboardApiClient,
        fileApiClient = fileApiClient,
        shareApiClient = shareApiClient,
        clipboardSyncCoordinator = ClipboardSyncCoordinator(
            sessionStore = sessionStore,
            clipboardApiClient = clipboardApiClient,
        ),
        fileTransferCoordinator = FileTransferCoordinator(
            sessionStore = sessionStore,
            fileApiClient = fileApiClient,
            documentFileGateway = documentFileGateway,
        ),
        shareCoordinator = ShareCoordinator(
            sessionStore = sessionStore,
            shareApiClient = shareApiClient,
            documentFileGateway = documentFileGateway,
        ),
        shareRulesStore = InMemoryUiShareRulesStore(),
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
        isAdmin: Boolean,
        storageQuotaBytes: Long,
        uploadBandwidthKbps: Int,
        downloadBandwidthKbps: Int,
    ) {
        storedSession = StoredSession(
            baseUrl = baseUrl,
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            currentDeviceId = currentDeviceId,
            username = username,
            deviceName = deviceName,
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
        )
    }
}

private class FakeUiAuthApiClient(
    private val currentAccountResult: AccountProfileResult? = null,
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
    ): AccountProfileResult {
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

    override suspend fun changePassword(
        session: StoredSession,
        currentPassword: String,
        newPassword: String,
        onRefreshing: (() -> Unit)?,
    ): ChangePasswordResult {
        error("changePassword should not be called in this test")
    }

    override suspend fun logout(session: StoredSession) = Unit

    private fun createResult(username: String, deviceName: String): AuthResult {
        return AuthResult(
            userId = "user-1",
            username = username,
            currentDeviceId = "device-1",
            deviceName = deviceName,
            isAdmin = false,
            storageQuotaBytes = 100L * 1024L * 1024L,
            uploadBandwidthKbps = 2048,
            downloadBandwidthKbps = 4096,
            tokens = TokenBundle(
                accessToken = "access-1",
                refreshToken = "refresh-1",
            ),
        )
    }
}

private class FakeUiRequestApiClient : RequestApiClient {
    override suspend fun createQuotaRequest(
        session: StoredSession,
        requestedQuotaMb: Long,
        reason: String,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<QuotaRequestRecord> = error("unused in ui test")

    override suspend fun listQuotaRequests(
        session: StoredSession,
        status: String,
        onRefreshing: (() -> Unit)?,
    ): RequestListResult<QuotaRequestRecord> = RequestListResult(emptyList(), status)

    override suspend fun createBandwidthRequest(
        session: StoredSession,
        requestedUploadKbps: Int,
        requestedDownloadKbps: Int,
        reason: String,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<BandwidthRequestRecord> = error("unused in ui test")

    override suspend fun listBandwidthRequests(
        session: StoredSession,
        status: String,
        onRefreshing: (() -> Unit)?,
    ): RequestListResult<BandwidthRequestRecord> = RequestListResult(emptyList(), status)

    override suspend fun createAdminRequest(
        session: StoredSession,
        reason: String,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<AdminPrivilegeRequestRecord> = error("unused in ui test")

    override suspend fun listAdminRequests(
        session: StoredSession,
        status: String,
        onRefreshing: (() -> Unit)?,
    ): RequestListResult<AdminPrivilegeRequestRecord> = RequestListResult(emptyList(), status)
}

private class FakeUiAdminApiClient : AdminApiClient {
    override suspend fun getSettings(
        session: StoredSession,
        onRefreshing: (() -> Unit)?,
    ): AdminSettingsResult {
        return AdminSettingsResult(
            settings = AdminSettingsRecord(
                maxUserCount = 200,
                defaultStorageQuotaBytes = 100L * 1024L * 1024L,
                defaultUploadBandwidthKbps = 2048,
                defaultDownloadBandwidthKbps = 4096,
                maxUserUploadBandwidthKbps = 10240,
                maxUserDownloadBandwidthKbps = 20480,
                maxUploadFileBytes = 50L * 1024L * 1024L,
                allowRegistration = false,
                updatedAt = "2026-06-01T10:00:00Z",
            ),
            currentUserCount = 1,
        )
    }

    override suspend fun updateSettings(
        session: StoredSession,
        input: UpdateAdminSettingsInput,
        onRefreshing: (() -> Unit)?,
    ): AdminSettingsResult = getSettings(session, onRefreshing)

    override suspend fun listUsers(
        session: StoredSession,
        onRefreshing: (() -> Unit)?,
    ): AdminUsersResult = AdminUsersResult(emptyList())

    override suspend fun updateUser(
        session: StoredSession,
        userId: String,
        input: UpdateAdminUserInput,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<AdminUserRecord> = error("unused in ui test")

    override suspend fun deleteUser(
        session: StoredSession,
        userId: String,
        onRefreshing: (() -> Unit)?,
    ): AdminDeleteUserResult = error("unused in ui test")

    override suspend fun listPendingQuotaRequests(
        session: StoredSession,
        onRefreshing: (() -> Unit)?,
    ): RequestListResult<QuotaRequestRecord> = RequestListResult(emptyList(), "pending")

    override suspend fun approveQuotaRequest(
        session: StoredSession,
        requestId: String,
        input: ApproveQuotaRequestInput,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<QuotaRequestRecord> = error("unused in ui test")

    override suspend fun rejectQuotaRequest(
        session: StoredSession,
        requestId: String,
        input: ReviewNoteInput,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<QuotaRequestRecord> = error("unused in ui test")

    override suspend fun listPendingBandwidthRequests(
        session: StoredSession,
        onRefreshing: (() -> Unit)?,
    ): RequestListResult<BandwidthRequestRecord> = RequestListResult(emptyList(), "pending")

    override suspend fun approveBandwidthRequest(
        session: StoredSession,
        requestId: String,
        input: ApproveBandwidthRequestInput,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<BandwidthRequestRecord> = error("unused in ui test")

    override suspend fun rejectBandwidthRequest(
        session: StoredSession,
        requestId: String,
        input: ReviewNoteInput,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<BandwidthRequestRecord> = error("unused in ui test")

    override suspend fun listPendingAdminRequests(
        session: StoredSession,
        onRefreshing: (() -> Unit)?,
    ): RequestListResult<AdminPrivilegeRequestRecord> = RequestListResult(emptyList(), "pending")

    override suspend fun approveAdminRequest(
        session: StoredSession,
        requestId: String,
        input: ReviewNoteInput,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<AdminPrivilegeRequestRecord> = error("unused in ui test")

    override suspend fun rejectAdminRequest(
        session: StoredSession,
        requestId: String,
        input: ReviewNoteInput,
        onRefreshing: (() -> Unit)?,
    ): RequestMutationResult<AdminPrivilegeRequestRecord> = error("unused in ui test")
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

private class FakeUiFileApiClient : FileApiClient {
    override suspend fun listFiles(
        session: StoredSession,
        page: Int,
        pageSize: Int,
        onRefreshing: (() -> Unit)?,
    ): FileListResult {
        return FileListResult(
            files = emptyList(),
            pagination = FilePagination(
                page = 1,
                pageSize = 20,
                total = 0,
                totalPages = 0,
            ),
            summary = FileListSummary(
                totalFiles = 0,
                totalBytes = 0L,
                maxUploadBytes = 2048L,
            ),
        )
    }

    override suspend fun uploadFile(
        session: StoredSession,
        fileName: String,
        contentType: String,
        sizeBytes: Long?,
        openInputStream: () -> InputStream,
        onRefreshing: (() -> Unit)?,
    ): FileMutationResult = error("unused in ui test")

    override suspend fun renameFile(
        session: StoredSession,
        fileId: String,
        originalName: String,
        onRefreshing: (() -> Unit)?,
    ): FileMutationResult = error("unused in ui test")

    override suspend fun deleteFile(
        session: StoredSession,
        fileId: String,
        onRefreshing: (() -> Unit)?,
    ): FileDeleteResult = error("unused in ui test")

    override suspend fun downloadFile(
        session: StoredSession,
        fileId: String,
        outputStream: OutputStream,
        onRefreshing: (() -> Unit)?,
    ): TokenBundle? = null
}

private class FakeUiShareApiClient : ShareApiClient {
    override suspend fun listShares(
        session: StoredSession,
        page: Int,
        pageSize: Int,
        statusFilter: ShareStatusFilter,
        onRefreshing: (() -> Unit)?,
    ): ShareListResult {
        return ShareListResult(
            shares = emptyList(),
            pagination = SharePagination(
                page = 1,
                pageSize = 20,
                total = 0,
                totalPages = 0,
                status = statusFilter.apiValue,
            ),
            summary = ShareListSummary(maxUploadBytes = 2048L),
        )
    }

    override suspend fun createTextShare(
        session: StoredSession,
        request: ShareTextCreateRequest,
        onRefreshing: (() -> Unit)?,
    ): ShareMutationResult {
        return ShareMutationResult(share = fakeUiShareItem())
    }

    override suspend fun createFileShare(
        session: StoredSession,
        request: ShareFileCreateRequest,
        onRefreshing: (() -> Unit)?,
    ): ShareMutationResult {
        return ShareMutationResult(share = fakeUiShareItem(contentKind = "file"))
    }

    override suspend fun revokeShare(
        session: StoredSession,
        shareId: String,
        onRefreshing: (() -> Unit)?,
    ): ShareMutationResult {
        return ShareMutationResult(share = fakeUiShareItem(status = "revoked"))
    }
}

private class FakeUiDocumentFileGateway : DocumentFileGateway {
    override fun inspect(uri: android.net.Uri): PickedLocalFile {
        return PickedLocalFile(
            uri = uri,
            displayName = "demo.txt",
            contentType = "text/plain",
            sizeBytes = 5L,
        )
    }

    override fun openInputStream(uri: android.net.Uri): InputStream {
        return ByteArrayInputStream("hello".encodeToByteArray())
    }

    override fun openOutputStream(uri: android.net.Uri): OutputStream {
        error("unused in ui test")
    }
}

private class InMemoryUiShareRulesStore(
    private var rules: ShareRuleConfig = ShareRuleConfig(),
) : ShareRulesStore {
    override fun readRules(): ShareRuleConfig = rules

    override fun saveRules(rules: ShareRuleConfig) {
        this.rules = rules
    }
}

private fun fakeUiShareItem(
    contentKind: String = "text",
    status: String = "active",
): ShareItemRecord {
    return ShareItemRecord(
        id = "share-1",
        token = "share-token",
        status = status,
        contentKind = contentKind,
        hasTextContent = contentKind == "text",
        hasFileContent = contentKind == "file",
        isEncrypted = false,
        requiresPassword = false,
        textPreview = "hello share",
        file = if (contentKind == "file") {
            ShareFileRecord(
                originalName = "demo.txt",
                contentType = "text/plain",
                sizeBytes = 5L,
                isImage = false,
            )
        } else {
            null
        },
        allowCopyContent = false,
        burnMode = "none",
        burnAfterSeconds = 0,
        remainingSeconds = 3600L,
        expiresAt = "2026-06-01T12:00:00Z",
        firstOpenedAt = "",
        burnDeadline = "",
        consumedAt = "",
        revokedAt = "",
        openCount = 0L,
        createdAt = "2026-06-01T10:00:00Z",
        updatedAt = "2026-06-01T10:00:00Z",
    )
}

private fun createStoredSession(isAdmin: Boolean = false): StoredSession {
    return StoredSession(
        baseUrl = "https://clipbridge.example.com",
        accessToken = "access-1",
        refreshToken = "refresh-1",
        currentDeviceId = "device-1",
        username = "alice",
        deviceName = "android-test",
        isAdmin = isAdmin,
        storageQuotaBytes = 100L * 1024L * 1024L,
        uploadBandwidthKbps = 2048,
        downloadBandwidthKbps = 4096,
    )
}

private fun createCurrentAccountResult(isAdmin: Boolean = false): AccountProfileResult {
    return AccountProfileResult(
        userId = "user-1",
        username = "alice",
        currentDeviceId = "device-1",
        isAdmin = isAdmin,
        storageQuotaBytes = 100L * 1024L * 1024L,
        uploadBandwidthKbps = 2048,
        downloadBandwidthKbps = 4096,
        storageUsedBytes = 20L * 1024L * 1024L,
        storageFreeBytes = 80L * 1024L * 1024L,
        limits = SystemLimitsRecord(
            maxUserCount = 200,
            defaultStorageQuotaBytes = 100L * 1024L * 1024L,
            defaultUploadBandwidthKbps = 2048,
            defaultDownloadBandwidthKbps = 4096,
            maxUserUploadBandwidthKbps = 10240,
            maxUserDownloadBandwidthKbps = 20480,
            maxUploadFileBytes = 50L * 1024L * 1024L,
            allowRegistration = false,
        ),
    )
}
