package com.xushuangbo.clipbridge.core.share

import android.net.Uri
import android.net.createTestUri
import com.xushuangbo.clipbridge.core.files.DocumentFileGateway
import com.xushuangbo.clipbridge.core.files.PickedLocalFile
import com.xushuangbo.clipbridge.core.network.ShareApiClient
import com.xushuangbo.clipbridge.core.network.ShareFileRecord
import com.xushuangbo.clipbridge.core.network.ShareItemRecord
import com.xushuangbo.clipbridge.core.network.ShareListResult
import com.xushuangbo.clipbridge.core.network.ShareListSummary
import com.xushuangbo.clipbridge.core.network.ShareMutationResult
import com.xushuangbo.clipbridge.core.network.SharePagination
import com.xushuangbo.clipbridge.core.network.ShareTextCreateRequest
import com.xushuangbo.clipbridge.core.network.ShareFileCreateRequest
import com.xushuangbo.clipbridge.core.network.TokenBundle
import com.xushuangbo.clipbridge.core.session.SessionStore
import com.xushuangbo.clipbridge.core.session.StoredSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class ShareCoordinatorTest {
    @Test
    fun listShares_updatesRotatedTokensInSessionStore() = runTest {
        val sessionStore = FakeShareSessionStore()
        val shareApiClient = FakeShareApiClient(
            listResult = ShareListResult(
                shares = emptyList(),
                pagination = SharePagination(
                    page = 1,
                    pageSize = 20,
                    total = 0,
                    totalPages = 0,
                    status = "all",
                ),
                summary = ShareListSummary(maxUploadBytes = 128L),
                tokens = TokenBundle("new-access", "new-refresh"),
            ),
        )
        val coordinator = ShareCoordinator(
            sessionStore = sessionStore,
            shareApiClient = shareApiClient,
            documentFileGateway = FakeShareDocumentFileGateway(),
        )

        coordinator.listShares(
            session = sessionStore.readSession(),
            page = 1,
            pageSize = 20,
            statusFilter = ShareStatusFilter.All,
        )

        assertEquals("new-access", sessionStore.readSession().accessToken)
        assertEquals("new-refresh", sessionStore.readSession().refreshToken)
    }

    @Test
    fun createFileShare_rejectsOversizedFileBeforeRequest() = runTest {
        val sessionStore = FakeShareSessionStore()
        val shareApiClient = FakeShareApiClient()
        val coordinator = ShareCoordinator(
            sessionStore = sessionStore,
            shareApiClient = shareApiClient,
            documentFileGateway = FakeShareDocumentFileGateway(),
        )
        val file = PickedLocalFile(
            uri = createTestUri("content://share/big.bin"),
            displayName = "big.bin",
            contentType = "application/octet-stream",
            sizeBytes = 4096L,
        )

        val error = runCatching {
            coordinator.createFileShare(
                session = sessionStore.readSession(),
                localFile = file,
                policy = buildDefaultShareRules().buildPolicyPayload(
                    strategyKey = ShareStrategyKey.Expire,
                    allowTextCopy = false,
                ),
                maxUploadBytes = 1024L,
            )
        }.exceptionOrNull()

        assertTrue(error is java.io.IOException)
        assertEquals(0, shareApiClient.createFileCalls)
    }

    @Test
    fun createFileShare_passesFileMetadataAndUpdatesTokens() = runTest {
        val sessionStore = FakeShareSessionStore()
        val gateway = FakeShareDocumentFileGateway(
            openedBytes = "hello share".encodeToByteArray(),
        )
        val shareApiClient = FakeShareApiClient(
            createResult = ShareMutationResult(
                share = sampleShare(token = "file-token"),
                tokens = TokenBundle("upload-access", "upload-refresh"),
            ),
        )
        val coordinator = ShareCoordinator(
            sessionStore = sessionStore,
            shareApiClient = shareApiClient,
            documentFileGateway = gateway,
        )
        val file = PickedLocalFile(
            uri = createTestUri("content://share/file.txt"),
            displayName = "file.txt",
            contentType = "text/plain",
            sizeBytes = 11L,
        )

        coordinator.createFileShare(
            session = sessionStore.readSession(),
            localFile = file,
            policy = buildDefaultShareRules().buildPolicyPayload(
                strategyKey = ShareStrategyKey.Expire,
                allowTextCopy = false,
            ),
            maxUploadBytes = 2048L,
        )

        assertEquals("file.txt", shareApiClient.lastFileRequest?.fileName)
        assertEquals("text/plain", shareApiClient.lastFileRequest?.contentType)
        assertEquals(11L, shareApiClient.lastFileRequest?.sizeBytes)
        assertArrayEquals("hello share".encodeToByteArray(), shareApiClient.lastUploadedBytes)
        assertEquals("upload-access", sessionStore.readSession().accessToken)
        assertEquals("upload-refresh", sessionStore.readSession().refreshToken)
    }

    @Test
    fun revokeShare_updatesTokensAfterSuccess() = runTest {
        val sessionStore = FakeShareSessionStore()
        val shareApiClient = FakeShareApiClient(
            createResult = ShareMutationResult(
                share = sampleShare(status = "revoked"),
                tokens = TokenBundle("revoke-access", "revoke-refresh"),
            ),
        )
        val coordinator = ShareCoordinator(
            sessionStore = sessionStore,
            shareApiClient = shareApiClient,
            documentFileGateway = FakeShareDocumentFileGateway(),
        )

        coordinator.revokeShare(
            session = sessionStore.readSession(),
            shareId = "share-1",
        )

        assertEquals("share-1", shareApiClient.lastRevokedShareId)
        assertEquals("revoke-access", sessionStore.readSession().accessToken)
        assertEquals("revoke-refresh", sessionStore.readSession().refreshToken)
    }
}

private class FakeShareSessionStore : SessionStore {
    private var session = StoredSession(
        baseUrl = "https://clipbridge.example.com",
        accessToken = "old-access",
        refreshToken = "old-refresh",
        currentDeviceId = "device-1",
        username = "alice",
        deviceName = "Pixel",
    )

    override fun readSession(): StoredSession = session

    override fun isSyncEnabled(): Boolean = false

    override fun readLastAckSeq(): Long = 0L

    override fun saveBaseUrl(baseUrl: String) {
        session = session.copy(baseUrl = baseUrl)
    }

    override fun saveDeviceName(deviceName: String) {
        session = session.copy(deviceName = deviceName)
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
        session = session.copy(
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
        session = session.copy(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
        )
    }

    override fun clearClipboardSyncState() = Unit

    override fun clearAuth() {
        session = session.copy(
            accessToken = "",
            refreshToken = "",
            currentDeviceId = "",
            username = "",
        )
    }
}

private class FakeShareDocumentFileGateway(
    private val pickedFile: PickedLocalFile = PickedLocalFile(
        uri = createTestUri("content://share/default.txt"),
        displayName = "default.txt",
        contentType = "text/plain",
        sizeBytes = 5L,
    ),
    private val openedBytes: ByteArray = "hello".encodeToByteArray(),
) : DocumentFileGateway {
    override fun inspect(uri: Uri): PickedLocalFile {
        return pickedFile.copy(uri = uri)
    }

    override fun openInputStream(uri: Uri): InputStream {
        return ByteArrayInputStream(openedBytes)
    }

    override fun openOutputStream(uri: Uri) = error("unused in test")
}

private class FakeShareApiClient(
    private val listResult: ShareListResult = ShareListResult(
        shares = emptyList(),
        pagination = SharePagination(
            page = 1,
            pageSize = 20,
            total = 0,
            totalPages = 0,
            status = "all",
        ),
        summary = ShareListSummary(maxUploadBytes = 0L),
    ),
    private val createResult: ShareMutationResult = ShareMutationResult(
        share = sampleShare(),
    ),
) : ShareApiClient {
    var lastTextRequest: ShareTextCreateRequest? = null
    var lastFileRequest: ShareFileCreateRequest? = null
    var lastRevokedShareId: String = ""
    var lastUploadedBytes: ByteArray = byteArrayOf()
    var createFileCalls: Int = 0

    override suspend fun listShares(
        session: StoredSession,
        page: Int,
        pageSize: Int,
        statusFilter: ShareStatusFilter,
        onRefreshing: (() -> Unit)?,
    ): ShareListResult = listResult

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
        createFileCalls += 1
        lastFileRequest = request
        lastUploadedBytes = request.openInputStream().readBytes()
        return createResult
    }

    override suspend fun revokeShare(
        session: StoredSession,
        shareId: String,
        onRefreshing: (() -> Unit)?,
    ): ShareMutationResult {
        lastRevokedShareId = shareId
        return createResult
    }
}

private fun sampleShare(
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
