package com.xushuangbo.clipbridge.feature.shell

import android.net.Uri
import android.net.createTestUri
import com.xushuangbo.clipbridge.MainDispatcherRule
import com.xushuangbo.clipbridge.core.files.DocumentFileGateway
import com.xushuangbo.clipbridge.core.files.PickedLocalFile
import com.xushuangbo.clipbridge.core.network.AuthApiException
import com.xushuangbo.clipbridge.core.network.ShareApiClient
import com.xushuangbo.clipbridge.core.network.ShareFileRecord
import com.xushuangbo.clipbridge.core.network.ShareFileCreateRequest
import com.xushuangbo.clipbridge.core.network.ShareItemRecord
import com.xushuangbo.clipbridge.core.network.ShareListResult
import com.xushuangbo.clipbridge.core.network.ShareListSummary
import com.xushuangbo.clipbridge.core.network.ShareMutationResult
import com.xushuangbo.clipbridge.core.network.SharePagination
import com.xushuangbo.clipbridge.core.network.ShareTextCreateRequest
import com.xushuangbo.clipbridge.core.network.TokenBundle
import com.xushuangbo.clipbridge.core.share.ShareCoordinator
import com.xushuangbo.clipbridge.core.share.ShareCountdownPreset
import com.xushuangbo.clipbridge.core.share.ShareExpirePreset
import com.xushuangbo.clipbridge.core.share.ShareRuleConfig
import com.xushuangbo.clipbridge.core.share.ShareRulesStore
import com.xushuangbo.clipbridge.core.share.ShareStatusFilter
import com.xushuangbo.clipbridge.core.share.ShareStrategyKey
import com.xushuangbo.clipbridge.core.session.SessionStore
import com.xushuangbo.clipbridge.core.session.StoredSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

@OptIn(ExperimentalCoroutinesApi::class)
class SharesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun ensureLoaded_populatesListAndSummary() = runTest {
        val sessionStore = FakeSharesSessionStore()
        val apiClient = FakeSharesApiClient(
            listResult = ShareListResult(
                shares = listOf(fakeShareItem()),
                pagination = SharePagination(
                    page = 1,
                    pageSize = 20,
                    total = 1,
                    totalPages = 1,
                    status = "all",
                ),
                summary = ShareListSummary(maxUploadBytes = 4096L),
            ),
        )
        val viewModel = createSharesViewModel(
            sessionStore = sessionStore,
            apiClient = apiClient,
        )

        viewModel.ensureLoaded()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.shares.size)
        assertEquals(4096L, viewModel.uiState.value.maxUploadBytes)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun createTextShare_successClearsDraftAndBuildsLatestLink() = runTest {
        val apiClient = FakeSharesApiClient(
            listResult = ShareListResult(
                shares = listOf(fakeShareItem(token = "text-token")),
                pagination = SharePagination(
                    page = 1,
                    pageSize = 20,
                    total = 1,
                    totalPages = 1,
                    status = "all",
                ),
                summary = ShareListSummary(maxUploadBytes = 2048L),
            ),
            createResult = ShareMutationResult(
                share = fakeShareItem(token = "text-token"),
            ),
        )
        val viewModel = createSharesViewModel(apiClient = apiClient)

        viewModel.ensureLoaded()
        advanceUntilIdle()
        viewModel.updateTextDraft("hello share")
        viewModel.createShare()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.textDraft)
        assertEquals("https://clipbridge.example.com/#/public/text-token", viewModel.uiState.value.latestShareLink)
        assertEquals("hello share", apiClient.lastTextRequest?.textContent)
        assertFalse(viewModel.uiState.value.isCreating)
    }

    @Test
    fun createFileShare_withoutSelectionShowsError() = runTest {
        val viewModel = createSharesViewModel()

        viewModel.selectComposeMode(com.xushuangbo.clipbridge.core.share.ShareComposeMode.File)
        viewModel.createShare()
        advanceUntilIdle()

        assertEquals("请先选择要分享的文件", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun revokeShare_successCallsApiAndKeepsCurrentPageFresh() = runTest {
        val apiClient = FakeSharesApiClient(
            listResult = ShareListResult(
                shares = listOf(fakeShareItem(status = "revoked")),
                pagination = SharePagination(
                    page = 1,
                    pageSize = 20,
                    total = 1,
                    totalPages = 1,
                    status = "all",
                ),
                summary = ShareListSummary(maxUploadBytes = 2048L),
            ),
        )
        val viewModel = createSharesViewModel(apiClient = apiClient)

        viewModel.ensureLoaded()
        advanceUntilIdle()
        viewModel.revokeShare("share-1")
        advanceUntilIdle()

        assertEquals("share-1", apiClient.lastRevokedShareId)
        assertEquals("revoked", viewModel.uiState.value.shares.first().status)
        assertEquals("", viewModel.uiState.value.revokingShareId)
    }

    @Test
    fun refreshShares_when401ClearsSessionAndEmitsExitEvent() = runTest {
        val sessionStore = FakeSharesSessionStore()
        val apiClient = FakeSharesApiClient(
            listError = AuthApiException(
                httpCode = 401,
                message = "expired",
            ),
        )
        val viewModel = createSharesViewModel(
            sessionStore = sessionStore,
            apiClient = apiClient,
        )
        var exitMessage = ""
        val job = launch {
            viewModel.sessionExitEvents.collect { message ->
                exitMessage = message
            }
        }
        advanceUntilIdle()

        viewModel.refreshShares()
        advanceUntilIdle()

        assertEquals("", sessionStore.readSession().accessToken)
        assertEquals("登录已失效，请重新登录", exitMessage)
        job.cancel()
    }

    @Test
    fun updateExpirePreset_refreshesStrategySummaryImmediately() = runTest {
        val viewModel = createSharesViewModel()

        viewModel.updateExpirePreset(ShareExpirePreset.SevenDays)

        assertTrue(viewModel.uiState.value.strategySummary.description.contains("7 天"))
    }
}

private fun createSharesViewModel(
    sessionStore: SessionStore = FakeSharesSessionStore(),
    apiClient: FakeSharesApiClient = FakeSharesApiClient(),
    rulesStore: ShareRulesStore = InMemoryShareRulesStore(),
    documentFileGateway: DocumentFileGateway = FakeSharesDocumentFileGateway(),
): SharesViewModel {
    return SharesViewModel(
        sessionStore = sessionStore,
        shareCoordinator = ShareCoordinator(
            sessionStore = sessionStore,
            shareApiClient = apiClient,
            documentFileGateway = documentFileGateway,
        ),
        shareRulesStore = rulesStore,
    )
}

private class FakeSharesSessionStore(
    private var storedSession: StoredSession = StoredSession(
        baseUrl = "https://clipbridge.example.com",
        accessToken = "access-1",
        refreshToken = "refresh-1",
        currentDeviceId = "device-1",
        username = "alice",
        deviceName = "android-test",
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

private class InMemoryShareRulesStore(
    private var rules: ShareRuleConfig = ShareRuleConfig(),
) : ShareRulesStore {
    override fun readRules(): ShareRuleConfig = rules

    override fun saveRules(rules: ShareRuleConfig) {
        this.rules = rules
    }
}

private class FakeSharesDocumentFileGateway(
    private val pickedFile: PickedLocalFile = PickedLocalFile(
        uri = createTestUri("content://share/demo.txt"),
        displayName = "demo.txt",
        contentType = "text/plain",
        sizeBytes = 5L,
    ),
) : DocumentFileGateway {
    override fun inspect(uri: Uri): PickedLocalFile {
        return pickedFile.copy(uri = uri)
    }

    override fun openInputStream(uri: Uri): InputStream {
        return ByteArrayInputStream("hello".encodeToByteArray())
    }

    override fun openOutputStream(uri: Uri) = error("unused in test")
}

private class FakeSharesApiClient(
    private val listResult: ShareListResult = ShareListResult(
        shares = emptyList(),
        pagination = SharePagination(
            page = 1,
            pageSize = 20,
            total = 0,
            totalPages = 0,
            status = "all",
        ),
        summary = ShareListSummary(maxUploadBytes = 2048L),
    ),
    private val createResult: ShareMutationResult = ShareMutationResult(
        share = fakeShareItem(),
    ),
    private val listError: Exception? = null,
) : ShareApiClient {
    var lastTextRequest: ShareTextCreateRequest? = null
    var lastFileRequest: ShareFileCreateRequest? = null
    var lastRevokedShareId: String = ""

    override suspend fun listShares(
        session: StoredSession,
        page: Int,
        pageSize: Int,
        statusFilter: ShareStatusFilter,
        onRefreshing: (() -> Unit)?,
    ): ShareListResult {
        listError?.let { throw it }
        return listResult
    }

    override suspend fun createTextShare(
        session: StoredSession,
        request: ShareTextCreateRequest,
        onRefreshing: (() -> Unit)?,
    ): ShareMutationResult {
        lastTextRequest = request
        return createResult
    }

    override suspend fun createFileShare(
        session: StoredSession,
        request: ShareFileCreateRequest,
        onRefreshing: (() -> Unit)?,
    ): ShareMutationResult {
        lastFileRequest = request
        return createResult
    }

    override suspend fun revokeShare(
        session: StoredSession,
        shareId: String,
        onRefreshing: (() -> Unit)?,
    ): ShareMutationResult {
        lastRevokedShareId = shareId
        return createResult.copy(
            share = createResult.share.copy(status = "revoked"),
        )
    }
}

private fun fakeShareItem(
    token: String = "share-token",
    status: String = "active",
): ShareItemRecord {
    return ShareItemRecord(
        id = "share-1",
        token = token,
        status = status,
        contentKind = "text",
        hasTextContent = true,
        hasFileContent = false,
        isEncrypted = false,
        requiresPassword = false,
        textPreview = "hello share",
        file = ShareFileRecord(
            originalName = "",
            contentType = "",
            sizeBytes = 0L,
            isImage = false,
        ),
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
