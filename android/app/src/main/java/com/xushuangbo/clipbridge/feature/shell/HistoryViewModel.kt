package com.xushuangbo.clipbridge.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xushuangbo.clipbridge.core.network.AuthApiException
import com.xushuangbo.clipbridge.core.network.ClipboardApiClient
import com.xushuangbo.clipbridge.core.network.ClipboardItem
import com.xushuangbo.clipbridge.core.network.TokenBundle
import com.xushuangbo.clipbridge.core.session.SessionStore
import com.xushuangbo.clipbridge.core.session.StoredSession
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class HistoryUiState(
    val items: List<ClipboardItem> = emptyList(),
    val draftText: String = "",
    val latestSeq: Long = 0L,
    val currentPage: Int = 1,
    val hasMoreOlder: Boolean = false,
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val isPulling: Boolean = false,
    val errorMessage: String? = null,
)

enum class HistoryShortcutAction {
    OpenUpload,
    PullRemote,
}

class HistoryViewModel(
    private val sessionStore: SessionStore,
    private val clipboardApiClient: ClipboardApiClient,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val _sessionExitEvents = MutableSharedFlow<String>()
    val sessionExitEvents: SharedFlow<String> = _sessionExitEvents.asSharedFlow()
    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private var hasLoadedOnce = false
    private var historyPageCursors: MutableList<Long?> = mutableListOf(null)
    private var historyCurrentPageIndex = 0

    fun ensureLoaded() {
        if (hasLoadedOnce || _uiState.value.isLoading) {
            return
        }
        refreshHistory()
    }

    fun refreshHistory() {
        val currentSession = requireSession() ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                )
            }

            try {
                resetHistoryPagination()
                loadHistoryPage(
                    session = currentSession,
                    beforeSeq = null,
                    pageIndex = 0,
                    keepDraft = true,
                    emitLoading = false,
                )
            } catch (error: AuthApiException) {
                handleRequestError(error)
            } catch (error: IOException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "网络异常，请稍后重试",
                    )
                }
            }
        }
    }

    fun loadOlderHistoryPage() {
        val currentSession = requireSession() ?: return
        if (_uiState.value.isLoading || !_uiState.value.hasMoreOlder) {
            return
        }

        val beforeSeq = _uiState.value.items.lastOrNull()?.seq ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val nextPageIndex = historyCurrentPageIndex + 1
                if (historyPageCursors.size <= nextPageIndex) {
                    historyPageCursors.add(beforeSeq)
                } else {
                    historyPageCursors[nextPageIndex] = beforeSeq
                }

                loadHistoryPage(
                    session = currentSession,
                    beforeSeq = beforeSeq,
                    pageIndex = nextPageIndex,
                    keepDraft = true,
                    emitLoading = false,
                )
                historyCurrentPageIndex = nextPageIndex
            } catch (error: AuthApiException) {
                handleRequestError(error)
            } catch (error: IOException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "网络异常，请稍后重试",
                    )
                }
            }
        }
    }

    fun loadNewerHistoryPage() {
        val currentSession = requireSession() ?: return
        if (_uiState.value.isLoading || historyCurrentPageIndex <= 0) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val nextPageIndex = historyCurrentPageIndex - 1
                val beforeSeq = historyPageCursors.getOrNull(nextPageIndex)

                loadHistoryPage(
                    session = currentSession,
                    beforeSeq = beforeSeq,
                    pageIndex = nextPageIndex,
                    keepDraft = true,
                    emitLoading = false,
                )
                historyCurrentPageIndex = nextPageIndex
            } catch (error: AuthApiException) {
                handleRequestError(error)
            } catch (error: IOException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "网络异常，请稍后重试",
                    )
                }
            }
        }
    }

    fun updateDraftText(value: String) {
        _uiState.update {
            it.copy(
                draftText = value,
                errorMessage = null,
            )
        }
    }

    fun uploadDraftText() {
        val normalizedText = _uiState.value.draftText.trim()
        if (normalizedText.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入要上传的文本内容") }
            return
        }

        val currentSession = requireSession() ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUploading = true,
                    errorMessage = null,
                )
            }

            try {
                var session = currentSession
                val uploadResult = clipboardApiClient.uploadText(session, normalizedText)
                session = applyRotatedTokens(session, uploadResult.tokens)

                _uiState.update {
                    it.copy(
                        draftText = "",
                    )
                }

                // 手动上传成功后立刻刷新历史第一页，保证用户马上能看到当前结果。
                refreshAfterMutation(session)
                _uiState.update { it.copy(isUploading = false) }
                _toastEvents.emit(
                    if (uploadResult.deduplicated) {
                        "文本已存在，已复用最近记录"
                    } else {
                        "文本已上传"
                    },
                )
            } catch (error: AuthApiException) {
                handleRequestError(error)
            } catch (error: IOException) {
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        errorMessage = error.message ?: "网络异常，请稍后重试",
                    )
                }
            }
        }
    }

    fun pullRemoteHistory() {
        val currentSession = requireSession() ?: return
        val sinceSeq = sessionStore.readLastAckSeq()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isPulling = true,
                    errorMessage = null,
                )
            }

            try {
                var session = currentSession
                val pullResult = clipboardApiClient.pullSync(
                    session = session,
                    sinceSeq = sinceSeq,
                    limit = DEFAULT_PULL_LIMIT,
                )
                session = applyRotatedTokens(session, pullResult.tokens)

                // pull 返回的是“按 seq 升序的新数据”，
                // 本轮先在拿到 next_since_seq 后立即 ACK，再刷新第一页，避免客户端本地进度和服务端脱节。
                if (pullResult.items.isNotEmpty() && pullResult.nextSinceSeq != null) {
                    val ackTokens = clipboardApiClient.ackSync(session, pullResult.nextSinceSeq)
                    session = applyRotatedTokens(session, ackTokens)
                    sessionStore.saveLastAckSeq(pullResult.nextSinceSeq)
                }

                if (pullResult.items.isNotEmpty()) {
                    refreshAfterMutation(session)
                    _toastEvents.emit("已拉取最新远端历史")
                } else {
                    _toastEvents.emit("当前没有新的远端历史")
                }

                _uiState.update { it.copy(isPulling = false) }
            } catch (error: AuthApiException) {
                handleRequestError(error)
            } catch (error: IOException) {
                _uiState.update {
                    it.copy(
                        isPulling = false,
                        errorMessage = error.message ?: "网络异常，请稍后重试",
                    )
                }
            }
        }
    }

    fun handleShortcutAction(action: HistoryShortcutAction) {
        when (action) {
            HistoryShortcutAction.OpenUpload -> Unit
            HistoryShortcutAction.PullRemote -> pullRemoteHistory()
        }
    }

    private suspend fun loadHistoryPage(
        session: StoredSession,
        beforeSeq: Long?,
        pageIndex: Int,
        keepDraft: Boolean = true,
        emitLoading: Boolean = true,
    ) {
        val result = clipboardApiClient.listClipboardItems(
            session = session,
            limit = DEFAULT_HISTORY_LIMIT,
            beforeSeq = beforeSeq,
        )
        applyRotatedTokens(session, result.tokens)
        if (result.currentDeviceAckSeq > 0L) {
            val storedAck = sessionStore.readLastAckSeq()
            sessionStore.saveLastAckSeq(maxOf(storedAck, result.currentDeviceAckSeq))
        }

        hasLoadedOnce = true
        _uiState.update {
            it.copy(
                items = result.items,
                latestSeq = result.latestSeq,
                currentPage = pageIndex + 1,
                hasMoreOlder = result.hasMore,
                draftText = if (keepDraft) it.draftText else "",
                isLoading = false,
                isUploading = false,
                isPulling = false,
                errorMessage = null,
            )
        }
        if (!emitLoading) {
            _uiState.update { currentState ->
                currentState.copy(isLoading = false)
            }
        }
    }

    private suspend fun refreshAfterMutation(session: StoredSession) {
        resetHistoryPagination()
        loadHistoryPage(
            session = session,
            beforeSeq = null,
            pageIndex = 0,
            keepDraft = false,
            emitLoading = false,
        )
    }

    private fun resetHistoryPagination() {
        historyPageCursors = mutableListOf(null)
        historyCurrentPageIndex = 0
    }

    private fun requireSession(): StoredSession? {
        val currentSession = sessionStore.readSession()
        if (!currentSession.hasCompleteAuth()) {
            sessionStore.clearClipboardSyncState()
            sessionStore.clearAuth()
            emitSessionExit("登录已失效，请重新登录")
            return null
        }
        return currentSession
    }

    private fun handleRequestError(error: AuthApiException) {
        if (error.httpCode == 401) {
            sessionStore.clearClipboardSyncState()
            sessionStore.clearAuth()
            _uiState.value = HistoryUiState()
            resetHistoryPagination()
            emitSessionExit("登录已失效，请重新登录")
            return
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                isUploading = false,
                isPulling = false,
                errorMessage = error.message ?: "服务暂时不可用，请稍后重试",
            )
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

    private fun emitSessionExit(message: String) {
        viewModelScope.launch {
            _sessionExitEvents.emit(message)
        }
    }

    companion object {
        private const val DEFAULT_HISTORY_LIMIT = 20
        private const val DEFAULT_PULL_LIMIT = 50

        fun factory(
            sessionStore: SessionStore,
            clipboardApiClient: ClipboardApiClient,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HistoryViewModel(
                        sessionStore = sessionStore,
                        clipboardApiClient = clipboardApiClient,
                    ) as T
                }
            }
        }
    }
}
