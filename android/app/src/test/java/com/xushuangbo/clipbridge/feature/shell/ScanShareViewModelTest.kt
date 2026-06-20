package com.xushuangbo.clipbridge.feature.shell

import com.xushuangbo.clipbridge.MainDispatcherRule
import com.xushuangbo.clipbridge.core.network.PublicShareApiClient
import com.xushuangbo.clipbridge.core.network.PublicShareFileRecord
import com.xushuangbo.clipbridge.core.network.PublicShareOpenResult
import com.xushuangbo.clipbridge.core.network.PublicShareRecord
import com.xushuangbo.clipbridge.core.network.TokenBundle
import com.xushuangbo.clipbridge.core.session.SessionStore
import com.xushuangbo.clipbridge.core.session.StoredSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanShareViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    @Test
    fun resolveScannedText_withPublicLinkLoadsMetaAndAutoOpensContent() = runTest {
        val viewModel = ScanShareViewModel(
            sessionStore = FakeScanSessionStore(),
            publicShareApiClient = FakePublicShareApiClient(),
        )

        viewModel.resolveScannedText("https://clipbridge.example.com/#/public/share-token")
        advanceUntilIdle()

        assertEquals("share-token", viewModel.uiState.value.shareToken)
        assertTrue(viewModel.uiState.value.contentOpen)
        assertEquals("hello share", viewModel.uiState.value.textContent)
    }

    @Test
    fun resolveScannedText_withUnsupportedContentShowsError() = runTest {
        val viewModel = ScanShareViewModel(
            sessionStore = FakeScanSessionStore(),
            publicShareApiClient = FakePublicShareApiClient(),
        )

        viewModel.resolveScannedText("https://clipbridge.example.com/history")

        assertEquals("当前二维码不是 ClipBridge 分享链接", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun resolveScannedText_withCountdownShareHidesContentAfterCountdown() = runTest {
        val countdownApiClient = FakePublicShareApiClient(
            metaFactory = { token ->
                buildPublicShareRecord(
                    token = token,
                    burnMode = "countdown",
                    burnAfterSeconds = 5,
                    remainingSeconds = 5L,
                    burnDeadline = "",
                )
            },
            openFactory = { token ->
                PublicShareOpenResult(
                    share = buildPublicShareRecord(
                        token = token,
                        burnMode = "countdown",
                        burnAfterSeconds = 5,
                        remainingSeconds = 3L,
                        burnDeadline = "2026-06-15T10:00:03Z",
                    ),
                    textContent = "hello share",
                    encryptedPayload = "",
                    encryption = null,
                    accessToken = "public-access-token",
                    accessTokenExpiresAt = "2026-06-15T10:00:00Z",
                )
            },
        )
        val viewModel = ScanShareViewModel(
            sessionStore = FakeScanSessionStore(),
            publicShareApiClient = countdownApiClient,
        )

        viewModel.resolveScannedText("https://clipbridge.example.com/#/public/share-token")
        runCurrent()

        assertTrue(viewModel.uiState.value.contentOpen)
        assertEquals(5L, viewModel.uiState.value.share?.remainingSeconds)

        advanceTimeBy(4000)
        runCurrent()

        assertTrue(viewModel.uiState.value.contentOpen)
        assertEquals(1L, viewModel.uiState.value.share?.remainingSeconds)

        advanceTimeBy(1000)
        runCurrent()

        assertFalse(viewModel.uiState.value.contentOpen)
        assertEquals("", viewModel.uiState.value.textContent)
        assertEquals("consumed", viewModel.uiState.value.share?.status)
        assertEquals(0L, viewModel.uiState.value.share?.remainingSeconds)
        assertEquals("分享倒计时已结束，内容已自动隐藏。", viewModel.uiState.value.errorMessage)
    }
}

private class FakeScanSessionStore : SessionStore {
    private var storedSession = StoredSession(
        baseUrl = "https://clipbridge.example.com",
        accessToken = "access-1",
        refreshToken = "refresh-1",
        currentDeviceId = "device-1",
    )

    override fun readSession(): StoredSession = storedSession

    override fun isSyncEnabled(): Boolean = false

    override fun readLastAckSeq(): Long = 0L

    override fun saveBaseUrl(baseUrl: String) {
        storedSession = storedSession.copy(baseUrl = baseUrl)
    }

    override fun saveDeviceName(deviceName: String) = Unit

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
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            currentDeviceId = currentDeviceId,
        )
    }

    override fun updateTokens(tokens: TokenBundle) {
        storedSession = storedSession.copy(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
        )
    }

    override fun clearClipboardSyncState() = Unit

    override fun clearAuth() = Unit
}

private class FakePublicShareApiClient(
    private val metaFactory: (String) -> PublicShareRecord = { token -> buildPublicShareRecord(token = token) },
    private val openFactory: (String) -> PublicShareOpenResult = { token ->
        PublicShareOpenResult(
            share = buildPublicShareRecord(token = token),
            textContent = "hello share",
            encryptedPayload = "",
            encryption = null,
            accessToken = "public-access-token",
            accessTokenExpiresAt = "2026-06-15T10:00:00Z",
        )
    },
) : PublicShareApiClient {
    override suspend fun getMeta(
        baseUrl: String,
        token: String,
    ): PublicShareRecord {
        return metaFactory(token)
    }

    override suspend fun openShare(
        baseUrl: String,
        token: String,
        password: String,
    ): PublicShareOpenResult {
        return openFactory(token)
    }
}

private fun buildPublicShareRecord(
    token: String,
    status: String = "active",
    burnMode: String = "none",
    burnAfterSeconds: Int = 0,
    remainingSeconds: Long = 3600L,
    burnDeadline: String = "",
): PublicShareRecord {
    return PublicShareRecord(
        token = token,
        status = status,
        contentKind = "text",
        hasTextContent = true,
        hasFileContent = false,
        isEncrypted = false,
        requiresPassword = false,
        textPreview = "hello share",
        allowCopyContent = true,
        files = emptyList<PublicShareFileRecord>(),
        burnMode = burnMode,
        burnAfterSeconds = burnAfterSeconds,
        remainingSeconds = remainingSeconds,
        expiresAt = "2026-06-15T12:00:00Z",
        firstOpenedAt = "",
        burnDeadline = burnDeadline,
        consumedAt = "",
        revokedAt = "",
        openCount = 0,
        createdAt = "2026-06-15T09:00:00Z",
    )
}
