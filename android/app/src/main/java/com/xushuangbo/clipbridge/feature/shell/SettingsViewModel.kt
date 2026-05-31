package com.xushuangbo.clipbridge.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xushuangbo.clipbridge.core.network.AuthApiClient
import com.xushuangbo.clipbridge.core.network.AuthApiException
import com.xushuangbo.clipbridge.core.network.ServiceAddressFormatter
import com.xushuangbo.clipbridge.core.session.SessionStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class SettingsUiState(
    val serviceAddress: String = "",
    val username: String = "",
    val currentDeviceId: String = "",
    val deviceName: String = "",
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmNewPassword: String = "",
    val syncEnabled: Boolean = false,
    val errorMessage: String? = null,
    val isSavingServiceAddress: Boolean = false,
    val isChangingPassword: Boolean = false,
    val isLoggingOut: Boolean = false,
)

class SettingsViewModel(
    private val sessionStore: SessionStore,
    private val authApiClient: AuthApiClient,
) : ViewModel() {
    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _sessionExitEvents = MutableSharedFlow<String>()
    val sessionExitEvents: SharedFlow<String> = _sessionExitEvents.asSharedFlow()
    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    fun updateServiceAddress(value: String) {
        _uiState.update { it.copy(serviceAddress = value, errorMessage = null) }
    }

    fun updateCurrentPassword(value: String) {
        _uiState.update { it.copy(currentPassword = value, errorMessage = null) }
    }

    fun updateNewPassword(value: String) {
        _uiState.update { it.copy(newPassword = value, errorMessage = null) }
    }

    fun updateConfirmNewPassword(value: String) {
        _uiState.update { it.copy(confirmNewPassword = value, errorMessage = null) }
    }

    fun toggleSyncEnabled() {
        val nextValue = !_uiState.value.syncEnabled

        // 这里先把同步开关落到本地，界面层再据此启动或停止后台同步服务。
        sessionStore.saveSyncEnabled(nextValue)
        _uiState.update {
            it.copy(
                syncEnabled = nextValue,
            )
        }
    }

    fun saveServiceAddress() {
        val currentState = _uiState.value
        val validationMessage = ServiceAddressFormatter.validate(currentState.serviceAddress)
        if (validationMessage != null) {
            _uiState.update { it.copy(errorMessage = validationMessage) }
            return
        }

        val normalizedAddress = ServiceAddressFormatter.normalize(currentState.serviceAddress)
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSavingServiceAddress = true,
                    errorMessage = null,
                )
            }

            sessionStore.saveBaseUrl(normalizedAddress)
            sessionStore.clearClipboardSyncState()
            sessionStore.clearAuth()
            _uiState.update {
                it.copy(
                    isSavingServiceAddress = false,
                )
            }
            _toastEvents.emit("服务地址已更新")
            _sessionExitEvents.emit("")
        }
    }

    fun logout() {
        val currentSession = sessionStore.readSession()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoggingOut = true,
                    errorMessage = null,
                )
            }

            authApiClient.logout(currentSession)
            sessionStore.clearClipboardSyncState()
            sessionStore.clearAuth()

            _uiState.update {
                it.copy(
                    isLoggingOut = false,
                )
            }
            _toastEvents.emit("已退出登录")
            _sessionExitEvents.emit("")
        }
    }

    fun changePassword() {
        val currentState = _uiState.value
        val currentPassword = currentState.currentPassword
        val newPassword = currentState.newPassword
        val confirmNewPassword = currentState.confirmNewPassword

        val validationMessage = validatePasswordForm(
            currentPassword = currentPassword,
            newPassword = newPassword,
            confirmNewPassword = confirmNewPassword,
        )
        if (validationMessage != null) {
            _uiState.update { it.copy(errorMessage = validationMessage) }
            return
        }

        val currentSession = sessionStore.readSession()
        if (!currentSession.hasCompleteAuth()) {
            sessionStore.clearClipboardSyncState()
            sessionStore.clearAuth()
            emitSessionExit("登录已失效，请重新登录")
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isChangingPassword = true,
                    errorMessage = null,
                )
            }

            try {
                val result = authApiClient.changePassword(
                    session = currentSession,
                    currentPassword = currentPassword,
                    newPassword = newPassword,
                    onRefreshing = null,
                )

                // 改密码请求如果顺手触发了 refresh，这里要把新 token 落回本地。
                result.tokens?.let(sessionStore::updateTokens)

                _uiState.update {
                    it.copy(
                        username = result.username,
                        currentPassword = "",
                        newPassword = "",
                        confirmNewPassword = "",
                        isChangingPassword = false,
                    )
                }
                _toastEvents.emit("密码已更新，其他设备需要重新登录")
            } catch (error: AuthApiException) {
                handleChangePasswordError(error)
            } catch (error: IOException) {
                _uiState.update {
                    it.copy(
                        isChangingPassword = false,
                        errorMessage = error.message ?: "网络异常，请稍后重试",
                    )
                }
            }
        }
    }

    private fun createInitialState(): SettingsUiState {
        val storedSession = sessionStore.readSession()
        return SettingsUiState(
            serviceAddress = storedSession.baseUrl,
            username = storedSession.username,
            currentDeviceId = storedSession.currentDeviceId,
            deviceName = storedSession.deviceName,
            syncEnabled = sessionStore.isSyncEnabled(),
        )
    }

    private fun validatePasswordForm(
        currentPassword: String,
        newPassword: String,
        confirmNewPassword: String,
    ): String? {
        return when {
            currentPassword.isBlank() -> "当前密码不能为空"
            newPassword.length < 8 -> "新密码至少需要 8 位"
            newPassword.length > 128 -> "新密码不能超过 128 位"
            newPassword != confirmNewPassword -> "两次输入的新密码不一致"
            currentPassword == newPassword -> "新密码不能与当前密码相同"
            else -> null
        }
    }

    private fun handleChangePasswordError(error: AuthApiException) {
        // 改密码接口也会返回 401 表示“当前密码错误”，
        // 这个场景不能直接当成登录失效，否则用户只要输错一次就会被强制退回登录页。
        if (error.httpCode == 401 && error.message == "current password is incorrect") {
            _uiState.update {
                it.copy(
                    isChangingPassword = false,
                    errorMessage = "当前密码不正确",
                )
            }
            return
        }

        if (error.httpCode == 401) {
            sessionStore.clearClipboardSyncState()
            sessionStore.clearAuth()
            emitSessionExit("登录已失效，请重新登录")
            return
        }

        _uiState.update {
            it.copy(
                isChangingPassword = false,
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
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(
                        sessionStore = sessionStore,
                        authApiClient = authApiClient,
                    ) as T
                }
            }
        }
    }
}
