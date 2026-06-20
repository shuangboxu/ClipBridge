package com.xushuangbo.clipbridge.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xushuangbo.clipbridge.core.network.AccountProfileResult
import com.xushuangbo.clipbridge.core.network.AdminApiClient
import com.xushuangbo.clipbridge.core.network.AdminUserRecord
import com.xushuangbo.clipbridge.core.network.AuthApiClient
import com.xushuangbo.clipbridge.core.network.AuthApiException
import com.xushuangbo.clipbridge.core.network.TokenBundle
import com.xushuangbo.clipbridge.core.network.UpdateAdminUserInput
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

enum class AdminUserDialogMode {
    Edit,
    Delete,
}

data class AdminUsersUiState(
    val users: List<AdminUserRecord> = emptyList(),
    val dialogMode: AdminUserDialogMode? = null,
    val selectedUserId: String = "",
    val storageQuotaMbDraft: String = "",
    val uploadBandwidthKbpsDraft: String = "",
    val downloadBandwidthKbpsDraft: String = "",
    val isAdminDraft: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
)

class AdminUsersViewModel(
    private val sessionStore: SessionStore,
    private val authApiClient: AuthApiClient,
    private val adminApiClient: AdminApiClient,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminUsersUiState())
    val uiState: StateFlow<AdminUsersUiState> = _uiState.asStateFlow()

    private val _sessionExitEvents = MutableSharedFlow<String>()
    val sessionExitEvents: SharedFlow<String> = _sessionExitEvents.asSharedFlow()
    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private var hasLoadedOnce = false

    fun ensureLoaded() {
        if (hasLoadedOnce || _uiState.value.isLoading) {
            return
        }
        refreshUsers()
    }

    fun refreshUsers() {
        val currentSession = requireSession() ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                )
            }

            try {
                loadUsers(currentSession)
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

    fun openEditDialog(userId: String) {
        val user = findUser(userId)
        if (user == null) {
            _uiState.update { it.copy(errorMessage = "用户不存在或已被移除") }
            return
        }

        _uiState.update {
            it.copy(
                dialogMode = AdminUserDialogMode.Edit,
                selectedUserId = user.id,
                storageQuotaMbDraft = (user.storageQuotaBytes / MB).toString(),
                uploadBandwidthKbpsDraft = bandwidthKbpsToMbDraft(user.uploadBandwidthKbps),
                downloadBandwidthKbpsDraft = bandwidthKbpsToMbDraft(user.downloadBandwidthKbps),
                isAdminDraft = user.isAdmin,
                errorMessage = null,
            )
        }
    }

    fun openDeleteDialog(userId: String) {
        val user = findUser(userId)
        if (user == null) {
            _uiState.update { it.copy(errorMessage = "用户不存在或已被移除") }
            return
        }

        _uiState.update {
            it.copy(
                dialogMode = AdminUserDialogMode.Delete,
                selectedUserId = user.id,
                storageQuotaMbDraft = (user.storageQuotaBytes / MB).toString(),
                uploadBandwidthKbpsDraft = bandwidthKbpsToMbDraft(user.uploadBandwidthKbps),
                downloadBandwidthKbpsDraft = bandwidthKbpsToMbDraft(user.downloadBandwidthKbps),
                isAdminDraft = user.isAdmin,
                errorMessage = null,
            )
        }
    }

    fun dismissDialog() {
        _uiState.update {
            it.copy(
                dialogMode = null,
                selectedUserId = "",
                storageQuotaMbDraft = "",
                uploadBandwidthKbpsDraft = "",
                downloadBandwidthKbpsDraft = "",
                isAdminDraft = false,
            )
        }
    }

    fun updateStorageQuotaMbDraft(value: String) {
        _uiState.update { it.copy(storageQuotaMbDraft = value, errorMessage = null) }
    }

    fun updateUploadBandwidthDraft(value: String) {
        _uiState.update { it.copy(uploadBandwidthKbpsDraft = value, errorMessage = null) }
    }

    fun updateDownloadBandwidthDraft(value: String) {
        _uiState.update { it.copy(downloadBandwidthKbpsDraft = value, errorMessage = null) }
    }

    fun updateIsAdminDraft(value: Boolean) {
        _uiState.update { it.copy(isAdminDraft = value, errorMessage = null) }
    }

    fun saveSelectedUser() {
        val currentSession = requireSession() ?: return
        val selectedUser = findSelectedUser()
        if (selectedUser == null) {
            _uiState.update { it.copy(errorMessage = "用户不存在或已被移除") }
            return
        }

        val storageQuotaMb = _uiState.value.storageQuotaMbDraft.trim().toLongOrNull()
        val uploadBandwidth = bandwidthMbDraftToKbpsOrNull(_uiState.value.uploadBandwidthKbpsDraft)
        val downloadBandwidth = bandwidthMbDraftToKbpsOrNull(_uiState.value.downloadBandwidthKbpsDraft)

        val validationMessage = when {
            storageQuotaMb == null || storageQuotaMb <= 0L -> "存储配额必须是大于 0 的整数 MB"
            uploadBandwidth == null -> "上传带宽必须是大于 0 的数字 MB/s"
            downloadBandwidth == null -> "下载带宽必须是大于 0 的数字 MB/s"
            else -> null
        }
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
                val result = adminApiClient.updateUser(
                    session = currentSession,
                    userId = selectedUser.id,
                    input = UpdateAdminUserInput(
                        storageQuotaMb = storageQuotaMb,
                        uploadBandwidthKbps = uploadBandwidth,
                        downloadBandwidthKbps = downloadBandwidth,
                        isAdmin = _uiState.value.isAdminDraft,
                    ),
                    onRefreshing = null,
                )
                result.tokens?.let(sessionStore::updateTokens)
                refreshCurrentAccountSnapshot()
                loadUsers(sessionStore.readSession())
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        dialogMode = null,
                        selectedUserId = "",
                    )
                }
                _toastEvents.emit("用户信息已更新")
            } catch (error: AuthApiException) {
                if (error.httpCode == 404) {
                    reloadUsersAfterMissingUser()
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = "用户不存在或已被移除",
                        )
                    }
                } else {
                    handleRequestError(error)
                }
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

    fun deleteSelectedUser() {
        val currentSession = requireSession() ?: return
        val selectedUser = findSelectedUser()
        if (selectedUser == null) {
            _uiState.update { it.copy(errorMessage = "用户不存在或已被移除") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDeleting = true,
                    errorMessage = null,
                )
            }

            try {
                val result = adminApiClient.deleteUser(
                    session = currentSession,
                    userId = selectedUser.id,
                    onRefreshing = null,
                )
                result.tokens?.let(sessionStore::updateTokens)
                refreshCurrentAccountSnapshot()
                loadUsers(sessionStore.readSession())
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        dialogMode = null,
                        selectedUserId = "",
                    )
                }
                _toastEvents.emit("用户已删除")
            } catch (error: AuthApiException) {
                if (error.httpCode == 404) {
                    reloadUsersAfterMissingUser()
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            errorMessage = "用户不存在或已被移除",
                        )
                    }
                } else {
                    handleRequestError(error)
                }
            } catch (error: IOException) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        errorMessage = error.message ?: "删除失败，请稍后重试",
                    )
                }
            }
        }
    }

    private suspend fun reloadUsersAfterMissingUser() {
        loadUsers(sessionStore.readSession())
        _uiState.update {
            it.copy(
                dialogMode = null,
                selectedUserId = "",
            )
        }
    }

    private suspend fun loadUsers(session: StoredSession) {
        val result = adminApiClient.listUsers(
            session = session,
            onRefreshing = null,
        )
        result.tokens?.let(sessionStore::updateTokens)
        hasLoadedOnce = true
        _uiState.update {
            it.copy(
                users = result.users,
                isLoading = false,
                isSaving = false,
                isDeleting = false,
                errorMessage = null,
            )
        }
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
            _uiState.value = AdminUsersUiState()
            emitSessionExit("登录已失效，请重新登录")
            return
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                isSaving = false,
                isDeleting = false,
                errorMessage = error.message ?: "服务暂时不可用，请稍后重试",
            )
        }
    }

    private fun findUser(userId: String): AdminUserRecord? {
        return _uiState.value.users.find { user -> user.id == userId }
    }

    private fun findSelectedUser(): AdminUserRecord? {
        return findUser(_uiState.value.selectedUserId)
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
                    return AdminUsersViewModel(
                        sessionStore = sessionStore,
                        authApiClient = authApiClient,
                        adminApiClient = adminApiClient,
                    ) as T
                }
            }
        }
    }
}
