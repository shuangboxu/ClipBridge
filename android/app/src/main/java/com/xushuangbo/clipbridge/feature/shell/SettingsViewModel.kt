package com.xushuangbo.clipbridge.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xushuangbo.clipbridge.core.network.AuthApiClient
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

data class SettingsUiState(
    val serviceAddress: String = "",
    val username: String = "",
    val currentDeviceId: String = "",
    val deviceName: String = "",
    val syncEnabled: Boolean = false,
    val errorMessage: String? = null,
    val isSavingServiceAddress: Boolean = false,
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
