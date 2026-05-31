package com.xushuangbo.clipbridge.core.files

import com.xushuangbo.clipbridge.core.network.FileApiClient
import com.xushuangbo.clipbridge.core.network.FileAssetRecord
import com.xushuangbo.clipbridge.core.network.FileDeleteResult
import com.xushuangbo.clipbridge.core.network.FileListResult
import com.xushuangbo.clipbridge.core.network.FileListSummary
import com.xushuangbo.clipbridge.core.network.FileMutationResult
import com.xushuangbo.clipbridge.core.network.FilePagination
import com.xushuangbo.clipbridge.core.network.TokenBundle
import com.xushuangbo.clipbridge.core.session.SessionStore
import com.xushuangbo.clipbridge.core.session.StoredSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream

class FileTransferCoordinatorTest {
    @Test
    fun listFiles_updatesRotatedTokensInSessionStore() = runTest {
        val sessionStore = FakeSessionStore()
        val fileApiClient = FakeFileApiClient(
            listResult = FileListResult(
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
                    maxUploadBytes = 64L,
                ),
                tokens = TokenBundle(
                    accessToken = "new-access",
                    refreshToken = "new-refresh",
                ),
            ),
        )
        val coordinator = FileTransferCoordinator(
            sessionStore = sessionStore,
            fileApiClient = fileApiClient,
            documentFileGateway = FakeDocumentFileGateway(),
        )

        coordinator.listFiles(
            session = sessionStore.readSession(),
            page = 1,
            pageSize = 20,
        )

        assertEquals("new-access", sessionStore.readSession().accessToken)
        assertEquals("new-refresh", sessionStore.readSession().refreshToken)
    }

    @Test
    fun renameFile_updatesRotatedTokensAndReturnsServerResult() = runTest {
        val sessionStore = FakeSessionStore()
        val fileApiClient = FakeFileApiClient(
            renameResult = FileMutationResult(
                file = FileAssetRecord(
                    id = "file-1",
                    originalName = "renamed.txt",
                    contentType = "text/plain",
                    sizeBytes = 12L,
                    fileSha256 = "hash",
                    originDeviceId = "device-1",
                    originDeviceName = "Pixel",
                    createdAt = "2026-05-31T10:00:00Z",
                ),
                tokens = TokenBundle(
                    accessToken = "rename-access",
                    refreshToken = "rename-refresh",
                ),
            ),
        )
        val coordinator = FileTransferCoordinator(
            sessionStore = sessionStore,
            fileApiClient = fileApiClient,
            documentFileGateway = FakeDocumentFileGateway(),
        )

        val result = coordinator.renameFile(
            session = sessionStore.readSession(),
            fileId = "file-1",
            originalName = "renamed.txt",
        )

        assertEquals("file-1", fileApiClient.lastRenamedFileId)
        assertEquals("renamed.txt", fileApiClient.lastRenamedFileName)
        assertEquals("renamed.txt", result.file.originalName)
        assertEquals("rename-access", sessionStore.readSession().accessToken)
        assertEquals("rename-refresh", sessionStore.readSession().refreshToken)
    }

    private class FakeSessionStore : SessionStore {
        private var session = StoredSession(
            baseUrl = "https://example.com",
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
        ) {
            session = session.copy(
                baseUrl = baseUrl,
                username = username,
                deviceName = deviceName,
                currentDeviceId = currentDeviceId,
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
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

    private class FakeDocumentFileGateway : DocumentFileGateway {
        override fun inspect(uri: android.net.Uri): PickedLocalFile {
            throw AssertionError("unused in test")
        }

        override fun openInputStream(uri: android.net.Uri): InputStream {
            throw AssertionError("unused in test")
        }

        override fun openOutputStream(uri: android.net.Uri): OutputStream {
            throw AssertionError("unused in test")
        }
    }

    private class FakeFileApiClient(
        private val listResult: FileListResult = FileListResult(
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
                maxUploadBytes = 0L,
            ),
        ),
        private val renameResult: FileMutationResult = FileMutationResult(
            file = FileAssetRecord(
                id = "file-1",
                originalName = "demo.txt",
                contentType = "text/plain",
                sizeBytes = 1L,
                fileSha256 = "hash",
                originDeviceId = "device-1",
                originDeviceName = "Pixel",
                createdAt = "2026-05-31T10:00:00Z",
            ),
        ),
    ) : FileApiClient {
        var lastRenamedFileId: String = ""
        var lastRenamedFileName: String = ""

        override suspend fun listFiles(
            session: StoredSession,
            page: Int,
            pageSize: Int,
            onRefreshing: (() -> Unit)?,
        ): FileListResult = listResult

        override suspend fun uploadFile(
            session: StoredSession,
            fileName: String,
            contentType: String,
            sizeBytes: Long?,
            openInputStream: () -> InputStream,
            onRefreshing: (() -> Unit)?,
        ): FileMutationResult {
            throw AssertionError("unexpected upload call")
        }

        override suspend fun renameFile(
            session: StoredSession,
            fileId: String,
            originalName: String,
            onRefreshing: (() -> Unit)?,
        ): FileMutationResult {
            lastRenamedFileId = fileId
            lastRenamedFileName = originalName
            return renameResult
        }

        override suspend fun deleteFile(
            session: StoredSession,
            fileId: String,
            onRefreshing: (() -> Unit)?,
        ): FileDeleteResult {
            throw AssertionError("unused in test")
        }

        override suspend fun downloadFile(
            session: StoredSession,
            fileId: String,
            outputStream: OutputStream,
            onRefreshing: (() -> Unit)?,
        ): TokenBundle? {
            throw AssertionError("unused in test")
        }
    }
}
