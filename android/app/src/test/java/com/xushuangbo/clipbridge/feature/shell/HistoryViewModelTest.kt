package com.xushuangbo.clipbridge.feature.shell

import com.xushuangbo.clipbridge.MainDispatcherRule
import com.xushuangbo.clipbridge.core.network.AuthApiException
import com.xushuangbo.clipbridge.core.network.ClipboardApiClient
import com.xushuangbo.clipbridge.core.network.ClipboardHistoryResult
import com.xushuangbo.clipbridge.core.network.ClipboardItem
import com.xushuangbo.clipbridge.core.network.ClipboardUploadResult
import com.xushuangbo.clipbridge.core.network.SyncPullResult
import com.xushuangbo.clipbridge.core.network.TokenBundle
import com.xushuangbo.clipbridge.core.session.SessionStore
import com.xushuangbo.clipbridge.core.session.StoredSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun ensureLoaded_loadsHistoryIntoUiState() = runTest {
        val sessionStore = FakeHistorySessionStore()
        val clipboardApiClient = FakeHistoryClipboardApiClient(
            historyResult = ClipboardHistoryResult(
                items = listOf(sampleItem(seq = 12L, text = "hello")),
                hasMore = false,
                latestSeq = 12L,
                currentDeviceAckSeq = 8L,
            ),
        )
        val viewModel = HistoryViewModel(sessionStore, clipboardApiClient)

        viewModel.ensureLoaded()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.items.size)
        assertEquals("hello", viewModel.uiState.value.items.first().textContent)
        assertEquals(12L, viewModel.uiState.value.latestSeq)
        assertEquals(8L, sessionStore.readLastAckSeq())
    }

    @Test
    fun uploadDraftText_successClearsDraftAndRefreshesHistory() = runTest {
        val sessionStore = FakeHistorySessionStore()
        val clipboardApiClient = FakeHistoryClipboardApiClient(
            uploadResult = ClipboardUploadResult(
                item = sampleItem(seq = 2L, text = "uploaded"),
                deduplicated = false,
            ),
            historyResults = ArrayDeque(
                listOf(
                    ClipboardHistoryResult(
                        items = listOf(sampleItem(seq = 2L, text = "uploaded")),
                        hasMore = false,
                        latestSeq = 2L,
                        currentDeviceAckSeq = 0L,
                    ),
                ),
            ),
        )
        val viewModel = HistoryViewModel(sessionStore, clipboardApiClient)

        viewModel.updateDraftText("uploaded")
        viewModel.uploadDraftText()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.draftText)
        assertEquals("uploaded", viewModel.uiState.value.items.first().textContent)
    }

    @Test
    fun uploadDraftText_failureKeepsDraftAndShowsError() = runTest {
        val sessionStore = FakeHistorySessionStore()
        val clipboardApiClient = FakeHistoryClipboardApiClient(
            uploadError = AuthApiException(message = "上传失败"),
        )
        val viewModel = HistoryViewModel(sessionStore, clipboardApiClient)

        viewModel.updateDraftText("keep me")
        viewModel.uploadDraftText()
        advanceUntilIdle()

        assertEquals("keep me", viewModel.uiState.value.draftText)
        assertEquals("上传失败", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun refreshHistory_writesRotatedTokensReturnedByApi() = runTest {
        val sessionStore = FakeHistorySessionStore()
        val clipboardApiClient = FakeHistoryClipboardApiClient(
            historyResult = ClipboardHistoryResult(
                items = emptyList(),
                hasMore = false,
                latestSeq = 0L,
                currentDeviceAckSeq = 0L,
                tokens = TokenBundle("new-access", "new-refresh"),
            ),
        )
        val viewModel = HistoryViewModel(sessionStore, clipboardApiClient)

        viewModel.refreshHistory()
        advanceUntilIdle()

        assertEquals("new-access", sessionStore.readSession().accessToken)
        assertEquals("new-refresh", sessionStore.readSession().refreshToken)
    }

    @Test
    fun pullRemoteHistory_acksAndUpdatesStoredAckSeq() = runTest {
        val sessionStore = FakeHistorySessionStore(lastAckSeq = 3L)
        val clipboardApiClient = FakeHistoryClipboardApiClient(
            pullResult = SyncPullResult(
                items = listOf(sampleItem(seq = 4L, text = "remote")),
                nextSinceSeq = 4L,
            ),
            historyResult = ClipboardHistoryResult(
                items = listOf(sampleItem(seq = 4L, text = "remote")),
                hasMore = false,
                latestSeq = 4L,
                currentDeviceAckSeq = 4L,
            ),
        )
        val viewModel = HistoryViewModel(sessionStore, clipboardApiClient)

        viewModel.pullRemoteHistory()
        advanceUntilIdle()

        assertEquals(listOf(4L), clipboardApiClient.ackedSeqs)
        assertEquals(4L, sessionStore.readLastAckSeq())
        assertEquals("remote", viewModel.uiState.value.items.first().textContent)
    }

    @Test
    fun pullRemoteHistory_withoutItemsDoesNotAck() = runTest {
        val sessionStore = FakeHistorySessionStore(lastAckSeq = 3L)
        val clipboardApiClient = FakeHistoryClipboardApiClient(
            pullResult = SyncPullResult(
                items = emptyList(),
                nextSinceSeq = 9L,
            ),
        )
        val viewModel = HistoryViewModel(sessionStore, clipboardApiClient)

        viewModel.pullRemoteHistory()
        advanceUntilIdle()

        assertTrue(clipboardApiClient.ackedSeqs.isEmpty())
        assertEquals(3L, sessionStore.readLastAckSeq())
    }

    private fun sampleItem(seq: Long, text: String): ClipboardItem {
        return ClipboardItem(
            id = "item-$seq",
            seq = seq,
            contentType = "text",
            textContent = text,
            originDeviceId = "device-1",
            isCurrentDeviceOrigin = true,
            createdAt = "2026-05-26T10:00:00Z",
        )
    }
}

private class FakeHistorySessionStore(
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

private class FakeHistoryClipboardApiClient(
    private val historyResult: ClipboardHistoryResult = ClipboardHistoryResult(
        items = emptyList(),
        hasMore = false,
        latestSeq = 0L,
        currentDeviceAckSeq = 0L,
    ),
    private val historyResults: ArrayDeque<ClipboardHistoryResult> = ArrayDeque(),
    private val uploadResult: ClipboardUploadResult = ClipboardUploadResult(
        item = ClipboardItem(
            id = "item-1",
            seq = 1L,
            contentType = "text",
            textContent = "text",
            originDeviceId = "device-1",
            isCurrentDeviceOrigin = true,
            createdAt = "2026-05-26T10:00:00Z",
        ),
        deduplicated = false,
    ),
    private val pullResult: SyncPullResult = SyncPullResult(
        items = emptyList(),
        nextSinceSeq = null,
    ),
    private val uploadError: Exception? = null,
) : ClipboardApiClient {
    val ackedSeqs = mutableListOf<Long>()

    override suspend fun uploadText(
        session: StoredSession,
        text: String,
        onRefreshing: (() -> Unit)?,
    ): ClipboardUploadResult {
        uploadError?.let { throw it }
        return uploadResult
    }

    override suspend fun listClipboardItems(
        session: StoredSession,
        limit: Int,
        beforeSeq: Long?,
        onRefreshing: (() -> Unit)?,
    ): ClipboardHistoryResult {
        return if (historyResults.isNotEmpty()) {
            historyResults.removeFirst()
        } else {
            historyResult
        }
    }

    override suspend fun pullSync(
        session: StoredSession,
        sinceSeq: Long,
        limit: Int,
        onRefreshing: (() -> Unit)?,
    ): SyncPullResult = pullResult

    override suspend fun ackSync(
        session: StoredSession,
        seq: Long,
        onRefreshing: (() -> Unit)?,
    ): TokenBundle? {
        ackedSeqs += seq
        return null
    }
}
