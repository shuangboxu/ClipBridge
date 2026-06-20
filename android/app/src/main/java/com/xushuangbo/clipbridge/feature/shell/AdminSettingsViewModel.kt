package com.xushuangbo.clipbridge.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xushuangbo.clipbridge.core.network.AccountProfileResult
import com.xushuangbo.clipbridge.core.network.AdminApiClient
import com.xushuangbo.clipbridge.core.network.AdminSettingsRecord
import com.xushuangbo.clipbridge.core.network.AuthApiClient
import com.xushuangbo.clipbridge.core.network.AuthApiException
import com.xushuangbo.clipbridge.core.network.TokenBundle
import com.xushuangbo.clipbridge.core.network.UpdateAdminSettingsInput
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

data class AdminSettingsUiState(
    val maxUserCountDraft: String = "",
    val defaultStorageQuotaMbDraft: String = "",
    val defaultUploadBandwidthKbpsDraft: String = "",
    val defaultDownloadBandwidthKbpsDraft: String = "",
    val maxUserUploadBandwidthKbpsDraft: String = "",
    val maxUserDownloadBandwidthKbpsDraft: String = "",
    val maxUploadFileMbDraft: String = "",
    val allowRegistration: Boolean = false,
    val currentUserCount: Int = 0,
    val updatedAt: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

class AdminSettingsViewModel(
    private val sessionStore: SessionStore,
    private val authApiClient: AuthApiClient,
    private val adminApiClient: AdminApiClient,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminSettingsUiState())
    val uiState: StateFlow<AdminSettingsUiState> = _uiState.asStateFlow()

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
                loadSettings(currentSession)
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

    fun updateMaxUserCountDraft(value: String) {
        _uiState.update { it.copy(maxUserCountDraft = value, errorMessage = null) }
    }

    fun updateDefaultStorageQuotaMbDraft(value: String) {
        _uiState.update { it.copy(defaultStorageQuotaMbDraft = value, errorMessage = null) }
    }

    fun updateDefaultUploadBandwidthDraft(value: String) {
        _uiState.update { it.copy(defaultUploadBandwidthKbpsDraft = value, errorMessage = null) }
    }

    fun updateDefaultDownloadBandwidthDraft(value: String) {
        _uiState.update { it.copy(defaultDownloadBandwidthKbpsDraft = value, errorMessage = null) }
    }

    fun updateMaxUserUploadBandwidthDraft(value: String) {
        _uiState.update { it.copy(maxUserUploadBandwidthKbpsDraft = value, errorMessage = null) }
    }

    fun updateMaxUserDownloadBandwidthDraft(value: String) {
        _uiState.update { it.copy(maxUserDownloadBandwidthKbpsDraft = value, errorMessage = null) }
    }

    fun updateMaxUploadFileMbDraft(value: String) {
        _uiState.update { it.copy(maxUploadFileMbDraft = value, errorMessage = null) }
    }

    fun updateAllowRegistration(value: Boolean) {
        _uiState.update { it.copy(allowRegistration = value, errorMessage = null) }
    }

    fun saveSettings() {
        val currentSession = requireSession() ?: return
        val currentState = _uiState.value

        val validationMessage = validateDrafts(currentState)
        if (validationMessage != null) {
            _uiState.update { it.copy(errorMessage = validationMessage) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    errorMessage = null,
                )
            }

            try {
                val defaultUploadBandwidth = bandwidthMbDraftToKbpsOrNull(currentState.defaultUploadBandwidthKbpsDraft)
                val defaultDownloadBandwidth = bandwidthMbDraftToKbpsOrNull(currentState.defaultDownloadBandwidthKbpsDraft)
                val maxUserUploadBandwidth = bandwidthMbDraftToKbpsOrNull(currentState.maxUserUploadBandwidthKbpsDraft)
                val maxUserDownloadBandwidth = bandwidthMbDraftToKbpsOrNull(currentState.maxUserDownloadBandwidthKbpsDraft)

                if (defaultUploadBandwidth == null) {
                    _uiState.update { it.copy(isSaving = false, errorMessage = "默认上传带宽必须是大于 0 的数字 MB/s") }
                    return@launch
                }
                if (defaultDownloadBandwidth == null) {
                    _uiState.update { it.copy(isSaving = false, errorMessage = "默认下载带宽必须是大于 0 的数字 MB/s") }
                    return@launch
                }
                if (maxUserUploadBandwidth == null) {
                    _uiState.update { it.copy(isSaving = false, errorMessage = "用户上传上限必须是大于 0 的数字 MB/s") }
                    return@launch
                }
                if (maxUserDownloadBandwidth == null) {
                    _uiState.update { it.copy(isSaving = false, errorMessage = "用户下载上限必须是大于 0 的数字 MB/s") }
                    return@launch
                }

                val result = adminApiClient.updateSettings(
                    session = currentSession,
                    input = UpdateAdminSettingsInput(
                        maxUserCount = currentState.maxUserCountDraft.trim().toInt(),
                        defaultStorageQuotaMb = currentState.defaultStorageQuotaMbDraft.trim().toLong(),
                        defaultUploadBandwidthKbps = defaultUploadBandwidth,
                        defaultDownloadBandwidthKbps = defaultDownloadBandwidth,
                        maxUserUploadBandwidthKbps = maxUserUploadBandwidth,
                        maxUserDownloadBandwidthKbps = maxUserDownloadBandwidth,
                        maxUploadFileMb = currentState.maxUploadFileMbDraft.trim().toLong(),
                        allowRegistration = currentState.allowRegistration,
                    ),
                    onRefreshing = null,
                )
                result.tokens?.let(sessionStore::updateTokens)
                refreshCurrentAccountSnapshot()
                applySettings(result.settings, result.currentUserCount)
                _uiState.update { it.copy(isSaving = false) }
                _toastEvents.emit("管理员设置已更新")
            } catch (error: AuthApiException) {
                handleRequestError(error)
            } catch (error: IOException) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.message ?: "保存失败，请稍后重试",
                    )
                }
            }
        }
    }

    private suspend fun loadSettings(session: StoredSession) {
        val result = adminApiClient.getSettings(
            session = session,
            onRefreshing = null,
        )
        result.tokens?.let(sessionStore::updateTokens)
        hasLoadedOnce = true
        applySettings(result.settings, result.currentUserCount)
    }

    private suspend fun refreshCurrentAccountSnapshot() {
        val currentSession = sessionStore.readSession()
        val profile = authApiClient.getCurrentAccount(
            session = currentSession,
            onRefreshing = null,
        )
        saveCurrentAccountSnapshot(profile)
    }

    private fun saveCurrentAccountSnapshot(profile: AccountProfileResult) {
        val currentSession = sessionStore.readSession()
        val tokens = profile.tokens ?: TokenBundle(
            accessToken = currentSession.accessToken,
            refreshToken = currentSession.refreshToken,
        )

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

    private fun applySettings(
        settings: AdminSettingsRecord,
        currentUserCount: Int,
    ) {
        _uiState.update {
            it.copy(
                maxUserCountDraft = settings.maxUserCount.toString(),
                defaultStorageQuotaMbDraft = (settings.defaultStorageQuotaBytes / MB).toString(),
                defaultUploadBandwidthKbpsDraft = bandwidthKbpsToMbDraft(settings.defaultUploadBandwidthKbps),
                defaultDownloadBandwidthKbpsDraft = bandwidthKbpsToMbDraft(settings.defaultDownloadBandwidthKbps),
                maxUserUploadBandwidthKbpsDraft = bandwidthKbpsToMbDraft(settings.maxUserUploadBandwidthKbps),
                maxUserDownloadBandwidthKbpsDraft = bandwidthKbpsToMbDraft(settings.maxUserDownloadBandwidthKbps),
                maxUploadFileMbDraft = (settings.maxUploadFileBytes / MB).toString(),
                allowRegistration = settings.allowRegistration,
                currentUserCount = currentUserCount,
                updatedAt = settings.updatedAt,
                isLoading = false,
                errorMessage = null,
            )
        }
    }

    private fun validateDrafts(state: AdminSettingsUiState): String? {
        val requiredPositiveIntFields = listOf(
            "最大用户数" to state.maxUserCountDraft.trim().toIntOrNull(),
        )
        requiredPositiveIntFields.forEach { entry ->
            val value = entry.second
            if (value == null || value <= 0) {
                return "${entry.first}必须是大于 0 的整数"
            }
        }

        val requiredBandwidthFields = listOf(
            "默认上传带宽" to bandwidthMbDraftToKbpsOrNull(state.defaultUploadBandwidthKbpsDraft),
            "默认下载带宽" to bandwidthMbDraftToKbpsOrNull(state.defaultDownloadBandwidthKbpsDraft),
            "用户上传上限" to bandwidthMbDraftToKbpsOrNull(state.maxUserUploadBandwidthKbpsDraft),
            "用户下载上限" to bandwidthMbDraftToKbpsOrNull(state.maxUserDownloadBandwidthKbpsDraft),
        )
        requiredBandwidthFields.forEach { entry ->
            if (entry.second == null) {
                return "${entry.first}必须是大于 0 的数字 MB/s"
            }
        }

        val requiredPositiveLongFields = listOf(
            "默认存储配额" to state.defaultStorageQuotaMbDraft.trim().toLongOrNull(),
            "单文件上传上限" to state.maxUploadFileMbDraft.trim().toLongOrNull(),
        )
        requiredPositiveLongFields.forEach { entry ->
            val value = entry.second
            if (value == null || value <= 0L) {
                return "${entry.first}必须是大于 0 的整数 MB"
            }
        }

        return null
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
            _uiState.value = AdminSettingsUiState()
            emitSessionExit("登录已失效，请重新登录")
            return
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                isSaving = false,
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
        private const val MB = 1024L * 1024L

        fun factory(
            sessionStore: SessionStore,
            authApiClient: AuthApiClient,
            adminApiClient: AdminApiClient,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AdminSettingsViewModel(
                        sessionStore = sessionStore,
                        authApiClient = authApiClient,
                        adminApiClient = adminApiClient,
                    ) as T
                }
            }
        }
    }
}
