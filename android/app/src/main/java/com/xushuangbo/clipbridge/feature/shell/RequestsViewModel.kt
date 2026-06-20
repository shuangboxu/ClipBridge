package com.xushuangbo.clipbridge.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xushuangbo.clipbridge.core.network.AccountProfileResult
import com.xushuangbo.clipbridge.core.network.AdminPrivilegeRequestRecord
import com.xushuangbo.clipbridge.core.network.AuthApiClient
import com.xushuangbo.clipbridge.core.network.AuthApiException
import com.xushuangbo.clipbridge.core.network.BandwidthRequestRecord
import com.xushuangbo.clipbridge.core.network.QuotaRequestRecord
import com.xushuangbo.clipbridge.core.network.RequestApiClient
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

data class RequestsUiState(
    val isAdmin: Boolean = false,
    val storageQuotaBytes: Long = 0L,
    val storageUsedBytes: Long = 0L,
    val storageFreeBytes: Long = 0L,
    val uploadBandwidthKbps: Int = 0,
    val downloadBandwidthKbps: Int = 0,
    val maxUploadFileBytes: Long = 0L,
    val quotaDraftMb: String = "",
    val quotaReasonDraft: String = "",
    val bandwidthUploadDraft: String = "",
    val bandwidthDownloadDraft: String = "",
    val bandwidthReasonDraft: String = "",
    val adminReasonDraft: String = "",
    val quotaRequests: List<QuotaRequestRecord> = emptyList(),
    val bandwidthRequests: List<BandwidthRequestRecord> = emptyList(),
    val adminRequests: List<AdminPrivilegeRequestRecord> = emptyList(),
    val isLoading: Boolean = false,
    val isSubmittingQuota: Boolean = false,
    val isSubmittingBandwidth: Boolean = false,
    val isSubmittingAdmin: Boolean = false,
    val errorMessage: String? = null,
)

class RequestsViewModel(
    private val sessionStore: SessionStore,
    private val authApiClient: AuthApiClient,
    private val requestApiClient: RequestApiClient,
) : ViewModel() {
    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<RequestsUiState> = _uiState.asStateFlow()

    private val _sessionExitEvents = MutableSharedFlow<String>()
    val sessionExitEvents: SharedFlow<String> = _sessionExitEvents.asSharedFlow()
    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private var hasLoadedOnce = false

    fun ensureLoaded() {
        if (hasLoadedOnce || _uiState.value.isLoading) {
            return
        }
        refresh()
    }

    fun refresh() {
        val currentSession = requireSession() ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                )
            }

            try {
                loadAllData(currentSession)
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

    fun updateQuotaDraftMb(value: String) {
        _uiState.update { it.copy(quotaDraftMb = value, errorMessage = null) }
    }

    fun updateQuotaReasonDraft(value: String) {
        _uiState.update { it.copy(quotaReasonDraft = value, errorMessage = null) }
    }

    fun updateBandwidthUploadDraft(value: String) {
        _uiState.update { it.copy(bandwidthUploadDraft = value, errorMessage = null) }
    }

    fun updateBandwidthDownloadDraft(value: String) {
        _uiState.update { it.copy(bandwidthDownloadDraft = value, errorMessage = null) }
    }

    fun updateBandwidthReasonDraft(value: String) {
        _uiState.update { it.copy(bandwidthReasonDraft = value, errorMessage = null) }
    }

    fun updateAdminReasonDraft(value: String) {
        _uiState.update { it.copy(adminReasonDraft = value, errorMessage = null) }
    }

    fun submitQuotaRequest() {
        val currentSession = requireSession() ?: return
        val currentState = _uiState.value
        val requestedQuotaMb = currentState.quotaDraftMb.trim().toLongOrNull()

        val validationMessage = when {
            requestedQuotaMb == null || requestedQuotaMb <= 0L -> "目标配额必须是大于 0 的整数 MB"
            currentState.quotaReasonDraft.length > 500 -> "申请说明不能超过 500 个字符"
            else -> null
        }
        if (validationMessage != null) {
            _uiState.update { it.copy(errorMessage = validationMessage) }
            return
        }
        val safeRequestedQuotaMb = requestedQuotaMb ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSubmittingQuota = true,
                    errorMessage = null,
                )
            }

            try {
                val result = requestApiClient.createQuotaRequest(
                    session = currentSession,
                    requestedQuotaMb = safeRequestedQuotaMb,
                    reason = currentState.quotaReasonDraft.trim(),
                    onRefreshing = null,
                )
                result.tokens?.let(sessionStore::updateTokens)
                reloadAfterMutation()
                _uiState.update {
                    it.copy(
                        quotaDraftMb = "",
                        quotaReasonDraft = "",
                    )
                }
                _toastEvents.emit("存储配额申请已提交")
            } catch (error: AuthApiException) {
                handleRequestError(error)
            } catch (error: IOException) {
                _uiState.update {
                    it.copy(
                        isSubmittingQuota = false,
                        errorMessage = error.message ?: "提交失败，请稍后重试",
                    )
                }
            }
        }
    }

    fun submitBandwidthRequest() {
        val currentSession = requireSession() ?: return
        val currentState = _uiState.value
        val requestedUpload = bandwidthMbDraftToKbpsOrNull(currentState.bandwidthUploadDraft)
        val requestedDownload = bandwidthMbDraftToKbpsOrNull(currentState.bandwidthDownloadDraft)

        val validationMessage = when {
            requestedUpload == null -> "上传带宽必须是大于 0 的数字 MB/s"
            requestedDownload == null -> "下载带宽必须是大于 0 的数字 MB/s"
            currentState.bandwidthReasonDraft.length > 500 -> "申请说明不能超过 500 个字符"
            else -> null
        }
        if (validationMessage != null) {
            _uiState.update { it.copy(errorMessage = validationMessage) }
            return
        }
        val safeRequestedUpload = requestedUpload ?: return
        val safeRequestedDownload = requestedDownload ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSubmittingBandwidth = true,
                    errorMessage = null,
                )
            }

            try {
                val result = requestApiClient.createBandwidthRequest(
                    session = currentSession,
                    requestedUploadKbps = safeRequestedUpload,
                    requestedDownloadKbps = safeRequestedDownload,
                    reason = currentState.bandwidthReasonDraft.trim(),
                    onRefreshing = null,
                )
                result.tokens?.let(sessionStore::updateTokens)
                reloadAfterMutation()
                _uiState.update {
                    it.copy(
                        bandwidthUploadDraft = "",
                        bandwidthDownloadDraft = "",
                        bandwidthReasonDraft = "",
                    )
                }
                _toastEvents.emit("带宽申请已提交")
            } catch (error: AuthApiException) {
                handleRequestError(error)
            } catch (error: IOException) {
                _uiState.update {
                    it.copy(
                        isSubmittingBandwidth = false,
                        errorMessage = error.message ?: "提交失败，请稍后重试",
                    )
                }
            }
        }
    }

    fun submitAdminRequest() {
        val currentSession = requireSession() ?: return
        val currentState = _uiState.value

        if (currentState.isAdmin) {
            _uiState.update { it.copy(errorMessage = "当前账号已经是管理员，无需再次申请") }
            return
        }
        if (currentState.adminReasonDraft.length > 500) {
            _uiState.update { it.copy(errorMessage = "申请说明不能超过 500 个字符") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSubmittingAdmin = true,
                    errorMessage = null,
                )
            }

            try {
                val result = requestApiClient.createAdminRequest(
                    session = currentSession,
                    reason = currentState.adminReasonDraft.trim(),
                    onRefreshing = null,
                )
                result.tokens?.let(sessionStore::updateTokens)
                reloadAfterMutation()
                _uiState.update { it.copy(adminReasonDraft = "") }
                _toastEvents.emit("管理员申请已提交")
            } catch (error: AuthApiException) {
                handleRequestError(error)
            } catch (error: IOException) {
                _uiState.update {
                    it.copy(
                        isSubmittingAdmin = false,
                        errorMessage = error.message ?: "提交失败，请稍后重试",
                    )
                }
            }
        }
    }

    private suspend fun reloadAfterMutation() {
        val refreshedSession = requireSession() ?: return
        loadAllData(refreshedSession)
    }

    private suspend fun loadAllData(session: StoredSession) {
        val profile = authApiClient.getCurrentAccount(
            session = session,
            onRefreshing = null,
        )
        saveCurrentAccountSnapshot(profile)

        val quotaResult = requestApiClient.listQuotaRequests(
            session = sessionStore.readSession(),
            status = "all",
            onRefreshing = null,
        )
        quotaResult.tokens?.let(sessionStore::updateTokens)

        val bandwidthResult = requestApiClient.listBandwidthRequests(
            session = sessionStore.readSession(),
            status = "all",
            onRefreshing = null,
        )
        bandwidthResult.tokens?.let(sessionStore::updateTokens)

        val adminResult = requestApiClient.listAdminRequests(
            session = sessionStore.readSession(),
            status = "all",
            onRefreshing = null,
        )
        adminResult.tokens?.let(sessionStore::updateTokens)

        hasLoadedOnce = true
        _uiState.update { currentState ->
            currentState.copy(
                isAdmin = profile.isAdmin,
                storageQuotaBytes = profile.storageQuotaBytes,
                storageUsedBytes = profile.storageUsedBytes,
                storageFreeBytes = profile.storageFreeBytes,
                uploadBandwidthKbps = profile.uploadBandwidthKbps,
                downloadBandwidthKbps = profile.downloadBandwidthKbps,
                maxUploadFileBytes = profile.limits.maxUploadFileBytes,
                quotaRequests = quotaResult.requests,
                bandwidthRequests = bandwidthResult.requests,
                adminRequests = adminResult.requests,
                isLoading = false,
                isSubmittingQuota = false,
                isSubmittingBandwidth = false,
                isSubmittingAdmin = false,
                errorMessage = null,
            )
        }
    }

    private fun saveCurrentAccountSnapshot(profile: AccountProfileResult) {
        val currentSession = sessionStore.readSession()
        val tokens = profile.tokens ?: TokenBundle(
            accessToken = currentSession.accessToken,
            refreshToken = currentSession.refreshToken,
        )

        // 每次重新拉当前账号后，都把最新角色和额度落回本地，
        // 这样设置页和其它入口不需要等到重新登录才能看到权限变化。
        sessionStore.saveAuthBundle(
            baseUrl = currentSession.baseUrl,
            username = profile.username,
            deviceName = currentSession.deviceName,
            currentDeviceId = profile.currentDeviceId,
            tokens = tokens,
            isAdmin = profile.isAdmin,
            storageQuotaBytes = profile.storageQuotaBytes,
            uploadBandwidthKbps = profile.uploadBandwidthKbps,
            downloadBandwidthKbps = profile.downloadBandwidthKbps,
        )
    }

    private fun createInitialState(): RequestsUiState {
        val storedSession = sessionStore.readSession()
        return RequestsUiState(
            isAdmin = storedSession.isAdmin,
            storageQuotaBytes = storedSession.storageQuotaBytes,
            uploadBandwidthKbps = storedSession.uploadBandwidthKbps,
            downloadBandwidthKbps = storedSession.downloadBandwidthKbps,
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
                isSubmittingQuota = false,
                isSubmittingBandwidth = false,
                isSubmittingAdmin = false,
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
        fun factory(
            sessionStore: SessionStore,
            authApiClient: AuthApiClient,
            requestApiClient: RequestApiClient,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RequestsViewModel(
                        sessionStore = sessionStore,
                        authApiClient = authApiClient,
                        requestApiClient = requestApiClient,
                    ) as T
                }
            }
        }
    }
}
