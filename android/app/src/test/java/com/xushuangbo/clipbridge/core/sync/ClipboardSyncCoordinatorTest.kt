package com.xushuangbo.clipbridge.core.sync

import com.xushuangbo.clipbridge.core.network.ClipboardApiClient
import com.xushuangbo.clipbridge.core.network.ClipboardHistoryResult
import com.xushuangbo.clipbridge.core.network.ClipboardItem
import com.xushuangbo.clipbridge.core.network.ClipboardUploadResult
import com.xushuangbo.clipbridge.core.network.SyncPullResult
import com.xushuangbo.clipbridge.core.network.TokenBundle
import com.xushuangbo.clipbridge.core.session.SessionStore
import com.xushuangbo.clipbridge.core.session.StoredSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ClipboardSyncCoordinatorTest {
    @Test
    fun uploadText_savesAckSeqAndRotatedTokens() = runTest {
        val sessionStore = FakeSyncSessionStore()
        val apiClient = FakeClipboardApiClient(
            uploadResult = ClipboardUploadResult(
                item = sampleItem(seq = 9L, text = "hello"),
                deduplicated = false,
                tokens = TokenBundle("upload-access", "upload-refresh"),
            ),
        )
        val coordinator = ClipboardSyncCoordinator(sessionStore, apiClient)

        val result = coordinator.uploadText(sessionStore.readSession(), "hello")

        assertEquals("hello", result.item.textContent)
        assertEquals(9L, sessionStore.readLastAckSeq())
        assertEquals("upload-access", sessionStore.readSession().accessToken)
        assertEquals("upload-refresh", sessionStore.readSession().refreshToken)
    }

    @Test
    fun listClipboardItems_writesCurrentDeviceAckSeq() = runTest {
        val sessionStore = FakeSyncSessionStore(lastAckSeq = 4L)
        val apiClient = FakeClipboardApiClient(
            historyResult = ClipboardHistoryResult(
                items = listOf(sampleItem(seq = 8L, text = "remote")),
                hasMore = false,
                latestSeq = 8L,
                currentDeviceAckSeq = 11L,
            ),
        )
        val coordinator = ClipboardSyncCoordinator(sessionStore, apiClient)

        val result = coordinator.listClipboardItems(sessionStore.readSession(), limit = 20)

        assertEquals(11L, sessionStore.readLastAckSeq())
        assertEquals("remote", result.items.first().textContent)
    }

    @Test
    fun ackSync_updatesStoredAckSeq() = runTest {
        val sessionStore = FakeSyncSessionStore()
        val apiClient = FakeClipboardApiClient()
        val coordinator = ClipboardSyncCoordinator(sessionStore, apiClient)

        coordinator.ackSync(sessionStore.readSession(), seq = 12L)

        assertEquals(listOf(12L), apiClient.ackedSeqs)
        assertEquals(12L, sessionStore.readLastAckSeq())
    }
}

private class FakeSyncSessionStore(
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

    override fun isSyncEnabled(): Boolean = true

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

private class FakeClipboardApiClient(
    private val historyResult: ClipboardHistoryResult = ClipboardHistoryResult(
        items = emptyList(),
        hasMore = false,
        latestSeq = 0L,
        currentDeviceAckSeq = 0L,
    ),
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
) : ClipboardApiClient {
    val ackedSeqs = mutableListOf<Long>()

    override suspend fun uploadText(
        session: StoredSession,
        text: String,
        onRefreshing: (() -> Unit)?,
    ): ClipboardUploadResult = uploadResult

    override suspend fun listClipboardItems(
        session: StoredSession,
        limit: Int,
        beforeSeq: Long?,
        onRefreshing: (() -> Unit)?,
    ): ClipboardHistoryResult = historyResult

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

private fun sampleItem(seq: Long, text: String): ClipboardItem {
    return ClipboardItem(
        id = "item-$seq",
        seq = seq,
        contentType = "text",
        textContent = text,
        originDeviceId = "device-1",
        isCurrentDeviceOrigin = false,
        createdAt = "2026-05-26T10:00:00Z",
    )
}
