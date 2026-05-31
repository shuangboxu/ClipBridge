package com.xushuangbo.clipbridge.core.sync

import com.xushuangbo.clipbridge.core.network.AuthApiException
import com.xushuangbo.clipbridge.core.network.ClipboardApiClient
import com.xushuangbo.clipbridge.core.network.ClipboardHistoryResult
import com.xushuangbo.clipbridge.core.network.ClipboardUploadResult
import com.xushuangbo.clipbridge.core.network.SyncPullResult
import com.xushuangbo.clipbridge.core.network.TokenBundle
import com.xushuangbo.clipbridge.core.session.SessionStore
import com.xushuangbo.clipbridge.core.session.StoredSession
import java.io.IOException

/**
 * 剪贴板同步协调层。
 *
 * 这里负责把“上传 / 拉取 / ACK / token 轮换”这些重复逻辑收拢起来，
 * 让服务和 ViewModel 都只关心自己的流程控制，不再各自拼网络细节。
 */
class ClipboardSyncCoordinator(
    private val sessionStore: SessionStore,
    private val clipboardApiClient: ClipboardApiClient,
) {
    suspend fun uploadText(
        session: StoredSession,
        text: String,
        onRefreshing: (() -> Unit)? = null,
    ): ClipboardUploadResult {
        val result = clipboardApiClient.uploadText(
            session = session,
            text = text.trim(),
            onRefreshing = onRefreshing,
        )
        applyRotatedTokens(session, result.tokens)
        if (result.item.seq > 0L) {
            sessionStore.saveLastAckSeq(maxOf(sessionStore.readLastAckSeq(), result.item.seq))
        }
        return result
    }

    suspend fun listClipboardItems(
        session: StoredSession,
        limit: Int,
        beforeSeq: Long? = null,
        onRefreshing: (() -> Unit)? = null,
    ): ClipboardHistoryResult {
        val result = clipboardApiClient.listClipboardItems(
            session = session,
            limit = limit,
            beforeSeq = beforeSeq,
            onRefreshing = onRefreshing,
        )
        applyRotatedTokens(session, result.tokens)
        if (result.currentDeviceAckSeq > 0L) {
            sessionStore.saveLastAckSeq(maxOf(sessionStore.readLastAckSeq(), result.currentDeviceAckSeq))
        }
        return result
    }

    suspend fun pullSync(
        session: StoredSession,
        sinceSeq: Long,
        limit: Int,
        onRefreshing: (() -> Unit)? = null,
    ): SyncPullResult {
        val result = clipboardApiClient.pullSync(
            session = session,
            sinceSeq = sinceSeq,
            limit = limit,
            onRefreshing = onRefreshing,
        )
        applyRotatedTokens(session, result.tokens)
        return result
    }

    suspend fun ackSync(
        session: StoredSession,
        seq: Long,
        onRefreshing: (() -> Unit)? = null,
    ): TokenBundle? {
        if (seq > sessionStore.readLastAckSeq()) {
            // ACK 先把本地游标推进，避免网络失败时反复重放同一批文本。
            sessionStore.saveLastAckSeq(seq)
        }

        return try {
            val tokens = clipboardApiClient.ackSync(
                session = session,
                seq = seq,
                onRefreshing = onRefreshing,
            )
            applyRotatedTokens(session, tokens)
            tokens
        } catch (error: AuthApiException) {
            if (error.httpCode == 401) {
                throw error
            }
            null
        } catch (_: IOException) {
            null
        }
    }

    fun readCurrentSession(): StoredSession {
        return sessionStore.readSession()
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
}
