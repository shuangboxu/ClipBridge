package com.xushuangbo.clipbridge.core.share

import android.net.Uri
import com.xushuangbo.clipbridge.core.files.DocumentFileGateway
import com.xushuangbo.clipbridge.core.files.PickedLocalFile
import com.xushuangbo.clipbridge.core.network.ShareApiClient
import com.xushuangbo.clipbridge.core.network.ShareListResult
import com.xushuangbo.clipbridge.core.network.ShareMutationResult
import com.xushuangbo.clipbridge.core.network.ShareTextCreateRequest
import com.xushuangbo.clipbridge.core.network.ShareFileCreateRequest
import com.xushuangbo.clipbridge.core.network.TokenBundle
import com.xushuangbo.clipbridge.core.session.SessionStore
import com.xushuangbo.clipbridge.core.session.StoredSession
import java.io.IOException
import kotlin.math.ln
import kotlin.math.pow

class ShareCoordinator(
    private val sessionStore: SessionStore,
    private val shareApiClient: ShareApiClient,
    private val documentFileGateway: DocumentFileGateway,
) {
    suspend fun listShares(
        session: StoredSession,
        page: Int,
        pageSize: Int,
        statusFilter: ShareStatusFilter,
        onRefreshing: (() -> Unit)? = null,
    ): ShareListResult {
        val result = shareApiClient.listShares(
            session = session,
            page = page,
            pageSize = pageSize,
            statusFilter = statusFilter,
            onRefreshing = onRefreshing,
        )
        applyRotatedTokens(session, result.tokens)
        return result
    }

    fun inspectLocalFile(uri: Uri): PickedLocalFile {
        return documentFileGateway.inspect(uri)
    }

    suspend fun createTextShare(
        session: StoredSession,
        textContent: String,
        policy: SharePolicyPayload,
        onRefreshing: (() -> Unit)? = null,
    ): ShareMutationResult {
        val result = shareApiClient.createTextShare(
            session = session,
            request = ShareTextCreateRequest(
                textContent = textContent,
                policy = policy,
            ),
            onRefreshing = onRefreshing,
        )
        applyRotatedTokens(session, result.tokens)
        return result
    }

    suspend fun createFileShare(
        session: StoredSession,
        localFile: PickedLocalFile,
        policy: SharePolicyPayload,
        maxUploadBytes: Long,
        onRefreshing: (() -> Unit)? = null,
    ): ShareMutationResult {
        validatePickedFile(localFile, maxUploadBytes)

        val result = shareApiClient.createFileShare(
            session = session,
            request = ShareFileCreateRequest(
                fileName = localFile.displayName,
                contentType = localFile.contentType,
                sizeBytes = localFile.sizeBytes,
                policy = policy,
                openInputStream = { documentFileGateway.openInputStream(localFile.uri) },
            ),
            onRefreshing = onRefreshing,
        )
        applyRotatedTokens(session, result.tokens)
        return result
    }

    suspend fun revokeShare(
        session: StoredSession,
        shareId: String,
        onRefreshing: (() -> Unit)? = null,
    ): ShareMutationResult {
        val result = shareApiClient.revokeShare(
            session = session,
            shareId = shareId,
            onRefreshing = onRefreshing,
        )
        applyRotatedTokens(session, result.tokens)
        return result
    }

    private fun validatePickedFile(
        pickedFile: PickedLocalFile,
        maxUploadBytes: Long,
    ) {
        if (pickedFile.displayName.isBlank()) {
            throw IOException("无法识别文件名，请重新选择文件")
        }

        // 服务端也会再次校验大小，这里先在本地拦一下，
        // 可以减少用户在超大文件上白等上传时间。
        if (maxUploadBytes > 0L && pickedFile.sizeBytes != null && pickedFile.sizeBytes > maxUploadBytes) {
            throw IOException("文件超过上传上限，当前限制为 ${formatBytes(maxUploadBytes)}")
        }
    }

    private fun applyRotatedTokens(
        session: StoredSession,
        tokens: TokenBundle?,
    ): StoredSession {
        if (tokens == null) {
            return session
        }

        sessionStore.updateTokens(tokens)
        return session.copy(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
        )
    }

    private fun formatBytes(value: Long): String {
        if (value <= 0L) {
            return "0 B"
        }
        if (value < 1024L) {
            return "$value B"
        }

        val units = arrayOf("KB", "MB", "GB", "TB")
        val digitGroup = (ln(value.toDouble()) / ln(1024.0)).toInt().coerceAtMost(units.size)
        val scaledValue = value / 1024.0.pow(digitGroup.toDouble())
        return String.format("%.1f %s", scaledValue, units[digitGroup - 1])
    }
}
