package com.xushuangbo.clipbridge.feature.shell

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xushuangbo.clipbridge.core.files.PickedLocalFile
import com.xushuangbo.clipbridge.core.network.AuthApiException
import com.xushuangbo.clipbridge.core.network.ShareItemRecord
import com.xushuangbo.clipbridge.core.share.PublicShareLinkBuilder
import com.xushuangbo.clipbridge.core.share.ShareComposeMode
import com.xushuangbo.clipbridge.core.share.ShareCoordinator
import com.xushuangbo.clipbridge.core.share.ShareCountdownPreset
import com.xushuangbo.clipbridge.core.share.ShareExpirePreset
import com.xushuangbo.clipbridge.core.share.ShareRuleConfig
import com.xushuangbo.clipbridge.core.share.ShareRulesStore
import com.xushuangbo.clipbridge.core.share.ShareStatusFilter
import com.xushuangbo.clipbridge.core.share.ShareStrategyKey
import com.xushuangbo.clipbridge.core.share.ShareStrategySummary
import com.xushuangbo.clipbridge.core.share.buildDefaultShareRules
import com.xushuangbo.clipbridge.core.share.buildPolicyPayload
import com.xushuangbo.clipbridge.core.share.buildStrategySummary
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

data class SharesUiState(
    val serviceAddress: String = "",
    val rules: ShareRuleConfig = buildDefaultShareRules(),
    val composeMode: ShareComposeMode = ShareComposeMode.Text,
    val strategyKey: ShareStrategyKey = ShareStrategyKey.Expire,
    val strategySummary: ShareStrategySummary = buildDefaultShareRules().buildStrategySummary(ShareStrategyKey.Expire),
    val statusFilter: ShareStatusFilter = ShareStatusFilter.All,
    val shares: List<ShareItemRecord> = emptyList(),
    val textDraft: String = "",
    val selectedFile: PickedLocalFile? = null,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val totalShares: Int = 0,
    val pageSize: Int = 20,
    val maxUploadBytes: Long = 0L,
    val latestShareLink: String = "",
    val createSuccessVersion: Int = 0,
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val revokingShareId: String = "",
    val errorMessage: String? = null,
)

class SharesViewModel(
    private val sessionStore: SessionStore,
    private val shareCoordinator: ShareCoordinator,
    private val shareRulesStore: ShareRulesStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<SharesUiState> = _uiState.asStateFlow()

    private val _sessionExitEvents = MutableSharedFlow<String>()
    val sessionExitEvents: SharedFlow<String> = _sessionExitEvents.asSharedFlow()
    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private var hasLoadedOnce = false

    fun ensureLoaded() {
        if (hasLoadedOnce || _uiState.value.isLoading) {
            return
        }
        loadSharesPage(page = 1)
    }

    fun refreshShares() {
        loadSharesPage(page = _uiState.value.currentPage)
    }

    fun loadPreviousPage() {
        val previousPage = _uiState.value.currentPage - 1
        if (previousPage < 1 || _uiState.value.isLoading) {
            return
        }
        loadSharesPage(page = previousPage)
    }

    fun loadNextPage() {
        val nextPage = _uiState.value.currentPage + 1
        if (nextPage > _uiState.value.totalPages || _uiState.value.isLoading) {
            return
        }
        loadSharesPage(page = nextPage)
    }

    fun selectComposeMode(mode: ShareComposeMode) {
        _uiState.update {
            it.copy(
                composeMode = mode,
                errorMessage = null,
            )
        }
    }

    fun selectStrategy(strategyKey: ShareStrategyKey) {
        _uiState.update { currentState ->
            currentState.copy(
                strategyKey = strategyKey,
                strategySummary = currentState.rules.buildStrategySummary(strategyKey),
                errorMessage = null,
            )
        }
    }

    fun selectStatusFilter(statusFilter: ShareStatusFilter) {
        _uiState.update {
            it.copy(
                statusFilter = statusFilter,
                errorMessage = null,
            )
        }
        loadSharesPage(page = 1)
    }

    fun updateTextDraft(value: String) {
        _uiState.update {
            it.copy(
                textDraft = value,
                errorMessage = null,
            )
        }
    }

    fun selectLocalFile(uri: Uri) {
        try {
            val file = shareCoordinator.inspectLocalFile(uri)
            _uiState.update {
                it.copy(
                    selectedFile = file,
                    errorMessage = null,
                )
            }
        } catch (error: IOException) {
            _uiState.update {
                it.copy(errorMessage = error.message ?: "读取文件失败，请重新选择")
            }
        }
    }

    fun clearSelectedFile() {
        _uiState.update {
            it.copy(
                selectedFile = null,
                errorMessage = null,
            )
        }
    }

    fun createShare() {
        when (_uiState.value.composeMode) {
            ShareComposeMode.Text -> createTextShare()
            ShareComposeMode.File -> createFileShare()
        }
    }

    fun revokeShare(shareId: String) {
        if (shareId.isBlank()) {
            _uiState.update { it.copy(errorMessage = "分享不存在或已被移除") }
            return
        }

        val currentSession = requireSession() ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    revokingShareId = shareId,
                    errorMessage = null,
                )
            }

            try {
                shareCoordinator.revokeShare(
                    session = currentSession,
                    shareId = shareId,
                )

                // 撤销后统一重新拉当前页，避免自己手动改列表状态时漏掉分页或时间字段变化。
                reloadPageAfterMutation(
                    session = sessionStore.readSession(),
                    page = _uiState.value.currentPage,
                )
                _toastEvents.emit("分享已撤销")
            } catch (error: AuthApiException) {
                handleRequestError(error)
            } catch (error: IOException) {
                _uiState.update {
                    it.copy(
                        revokingShareId = "",
                        errorMessage = error.message ?: "撤销失败，请稍后重试",
                    )
                }
            }
        }
    }

    fun updateNeverAllowCopy(enabled: Boolean) {
        saveRules(_uiState.value.rules.copy(never = _uiState.value.rules.never.copy(allowCopyText = enabled)))
    }

    fun updateExpirePreset(preset: ShareExpirePreset) {
        saveRules(_uiState.value.rules.copy(expire = _uiState.value.rules.expire.copy(preset = preset)))
    }

    fun updateExpireAllowCopy(enabled: Boolean) {
        saveRules(_uiState.value.rules.copy(expire = _uiState.value.rules.expire.copy(allowCopyText = enabled)))
    }

    fun updateOnceShowCountdown(enabled: Boolean) {
        saveRules(_uiState.value.rules.copy(once = _uiState.value.rules.once.copy(showCountdown = enabled)))
    }

    fun updateOnceCountdownPreset(preset: ShareCountdownPreset) {
        saveRules(_uiState.value.rules.copy(once = _uiState.value.rules.once.copy(countdownPreset = preset)))
    }

    fun updateOnceAllowCopy(enabled: Boolean) {
        saveRules(_uiState.value.rules.copy(once = _uiState.value.rules.once.copy(allowCopyText = enabled)))
    }

    fun notifyLinkCopied() {
        viewModelScope.launch {
            _toastEvents.emit("分享链接已复制")
        }
    }

    fun showUiError(message: String) {
        if (message.isBlank()) {
            return
        }
        _uiState.update { it.copy(errorMessage = message) }
    }

    private fun createTextShare() {
        val normalizedText = _uiState.value.textDraft.trim()
        if (normalizedText.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入要分享的文本内容") }
            return
        }

        val currentSession = requireSession() ?: return
        val policy = _uiState.value.rules.buildPolicyPayload(
            strategyKey = _uiState.value.strategyKey,
            allowTextCopy = true,
        )

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCreating = true,
                    errorMessage = null,
                )
            }

            try {
                val result = shareCoordinator.createTextShare(
                    session = currentSession,
                    textContent = normalizedText,
                    policy = policy,
                )

                handleShareCreated(
                    session = sessionStore.readSession(),
                    share = result.share,
                    clearTextDraft = true,
                    clearSelectedFile = false,
                )
                _toastEvents.emit("文本分享已创建")
            } catch (error: AuthApiException) {
                handleRequestError(error)
            } catch (error: IOException) {
                _uiState.update {
                    it.copy(
                        isCreating = false,
                        errorMessage = error.message ?: "创建分享失败，请稍后重试",
                    )
                }
            }
        }
    }

    private fun createFileShare() {
        val selectedFile = _uiState.value.selectedFile
        if (selectedFile == null) {
            _uiState.update { it.copy(errorMessage = "请先选择要分享的文件") }
            return
        }

        val currentSession = requireSession() ?: return
        val policy = _uiState.value.rules.buildPolicyPayload(
            strategyKey = _uiState.value.strategyKey,
            allowTextCopy = false,
        )

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCreating = true,
                    errorMessage = null,
                )
            }

            try {
                val result = shareCoordinator.createFileShare(
                    session = currentSession,
                    localFile = selectedFile,
                    policy = policy,
                    maxUploadBytes = _uiState.value.maxUploadBytes,
                )

                handleShareCreated(
                    session = sessionStore.readSession(),
                    share = result.share,
                    clearTextDraft = false,
                    clearSelectedFile = true,
                )
                _toastEvents.emit("文件分享已创建")
            } catch (error: AuthApiException) {
                handleRequestError(error)
            } catch (error: IOException) {
                _uiState.update {
                    it.copy(
                        isCreating = false,
                        errorMessage = error.message ?: "创建分享失败，请稍后重试",
                    )
                }
            }
        }
    }

    private suspend fun handleShareCreated(
        session: StoredSession,
        share: ShareItemRecord,
        clearTextDraft: Boolean,
        clearSelectedFile: Boolean,
    ) {
        val latestShareLink = PublicShareLinkBuilder.build(session.baseUrl, share.token).orEmpty()

        reloadPageAfterMutation(
            session = session,
            page = 1,
        )

        _uiState.update {
            it.copy(
                textDraft = if (clearTextDraft) "" else it.textDraft,
                selectedFile = if (clearSelectedFile) null else it.selectedFile,
                latestShareLink = latestShareLink,
                createSuccessVersion = it.createSuccessVersion + 1,
                errorMessage = if (latestShareLink.isBlank()) {
                    "分享已创建，但当前服务地址无法生成公开链接"
                } else {
                    null
                },
            )
        }
    }

    private fun loadSharesPage(page: Int) {
        val currentSession = requireSession() ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                )
            }

            try {
                loadSharesPageInternal(
                    session = currentSession,
                    page = page,
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

    private suspend fun reloadPageAfterMutation(
        session: StoredSession,
        page: Int,
    ) {
        loadSharesPageInternal(
            session = session,
            page = page,
        )
    }

    private suspend fun loadSharesPageInternal(
        session: StoredSession,
        page: Int,
    ) {
        val result = shareCoordinator.listShares(
            session = session,
            page = page.coerceAtLeast(1),
            pageSize = DEFAULT_PAGE_SIZE,
            statusFilter = _uiState.value.statusFilter,
        )

        hasLoadedOnce = true
        val safeTotalPages = result.pagination.totalPages.coerceAtLeast(1)
        val safePage = result.pagination.page.coerceIn(1, safeTotalPages)

        _uiState.update { currentState ->
            currentState.copy(
                serviceAddress = sessionStore.readSession().baseUrl,
                shares = result.shares,
                currentPage = safePage,
                totalPages = safeTotalPages,
                totalShares = result.pagination.total,
                pageSize = result.pagination.pageSize.coerceAtLeast(DEFAULT_PAGE_SIZE),
                maxUploadBytes = result.summary.maxUploadBytes,
                isLoading = false,
                isCreating = false,
                revokingShareId = "",
                errorMessage = null,
            )
        }
    }

    private fun saveRules(rules: ShareRuleConfig) {
        shareRulesStore.saveRules(rules)
        _uiState.update { currentState ->
            currentState.copy(
                rules = rules,
                strategySummary = rules.buildStrategySummary(currentState.strategyKey),
                errorMessage = null,
            )
        }
    }

    private fun createInitialState(): SharesUiState {
        val storedSession = sessionStore.readSession()
        val rules = shareRulesStore.readRules()
        val strategyKey = ShareStrategyKey.Expire
        return SharesUiState(
            serviceAddress = storedSession.baseUrl,
            rules = rules,
            strategyKey = strategyKey,
            strategySummary = rules.buildStrategySummary(strategyKey),
        )
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
            _uiState.value = createInitialState()
            emitSessionExit("登录已失效，请重新登录")
            return
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                isCreating = false,
                revokingShareId = "",
                errorMessage = error.message ?: "服务暂时不可用，请稍后重试",
            )
        }
    }

    private fun emitSessionExit(message: String) {
        viewModelScope.launch {
            _sessionExitEvents.emit(message)
        }
    }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 20

        fun factory(
            sessionStore: SessionStore,
            shareCoordinator: ShareCoordinator,
            shareRulesStore: ShareRulesStore,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SharesViewModel(
                        sessionStore = sessionStore,
                        shareCoordinator = shareCoordinator,
                        shareRulesStore = shareRulesStore,
                    ) as T
                }
            }
        }
    }
}
