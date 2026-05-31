package com.xushuangbo.clipbridge.core.files

import android.net.Uri
import com.xushuangbo.clipbridge.core.network.FileApiClient
import com.xushuangbo.clipbridge.core.network.FileDeleteResult
import com.xushuangbo.clipbridge.core.network.FileListResult
import com.xushuangbo.clipbridge.core.network.FileMutationResult
import com.xushuangbo.clipbridge.core.network.TokenBundle
import com.xushuangbo.clipbridge.core.session.SessionStore
import com.xushuangbo.clipbridge.core.session.StoredSession
import java.io.IOException
import kotlin.math.ln
import kotlin.math.pow

/**
 * 文件中转协调层。
 *
 * 这里负责把三类重复逻辑收拢在一起：
 * 1. 统一处理 token 轮换并写回本地
 * 2. 统一处理 Android 文档 URI 的读取 / 写入
 * 3. 在真正发请求前补上本地可提前判断的校验
 */
class FileTransferCoordinator(
    private val sessionStore: SessionStore,
    private val fileApiClient: FileApiClient,
    private val documentFileGateway: DocumentFileGateway,
) {
    suspend fun listFiles(
        session: StoredSession,
        page: Int,
        pageSize: Int,
        onRefreshing: (() -> Unit)? = null,
    ): FileListResult {
        val result = fileApiClient.listFiles(
            session = session,
            page = page,
            pageSize = pageSize,
            onRefreshing = onRefreshing,
        )
        applyRotatedTokens(session, result.tokens)
        return result
    }

    suspend fun uploadPickedFile(
        session: StoredSession,
        uri: Uri,
        maxUploadBytes: Long,
        onRefreshing: (() -> Unit)? = null,
    ): FileMutationResult {
        val pickedFile = documentFileGateway.inspect(uri)
        validatePickedFile(pickedFile, maxUploadBytes)

        val result = fileApiClient.uploadFile(
            session = session,
            fileName = pickedFile.displayName,
            contentType = pickedFile.contentType,
            sizeBytes = pickedFile.sizeBytes,
            openInputStream = { documentFileGateway.openInputStream(pickedFile.uri) },
            onRefreshing = onRefreshing,
        )
        applyRotatedTokens(session, result.tokens)
        return result
    }

    suspend fun renameFile(
        session: StoredSession,
        fileId: String,
        originalName: String,
        onRefreshing: (() -> Unit)? = null,
    ): FileMutationResult {
        val result = fileApiClient.renameFile(
            session = session,
            fileId = fileId,
            originalName = originalName,
            onRefreshing = onRefreshing,
        )
        applyRotatedTokens(session, result.tokens)
        return result
    }

    suspend fun deleteFile(
        session: StoredSession,
        fileId: String,
        onRefreshing: (() -> Unit)? = null,
    ): FileDeleteResult {
        val result = fileApiClient.deleteFile(
            session = session,
            fileId = fileId,
            onRefreshing = onRefreshing,
        )
        applyRotatedTokens(session, result.tokens)
        return result
    }

    suspend fun downloadFileToUri(
        session: StoredSession,
        fileId: String,
        targetUri: Uri,
        onRefreshing: (() -> Unit)? = null,
    ): TokenBundle? {
        documentFileGateway.openOutputStream(targetUri).use { outputStream ->
            val tokens = fileApiClient.downloadFile(
                session = session,
                fileId = fileId,
                outputStream = outputStream,
                onRefreshing = onRefreshing,
            )
            applyRotatedTokens(session, tokens)
            return tokens
        }
    }

    private fun validatePickedFile(
        pickedFile: PickedLocalFile,
        maxUploadBytes: Long,
    ) {
        if (pickedFile.displayName.isBlank()) {
            throw IOException("无法识别文件名，请重新选择文件")
        }

        // 服务端也会再次校验大小，这里先做一次本地拦截，
        // 可以避免用户白等上传完成后才看到 413 错误。
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
