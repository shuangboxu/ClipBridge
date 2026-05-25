package com.xushuangbo.clipbridge.feature.auth

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

class AuthViewModel(
    private val sessionStore: SessionStore,
    private val authApiClient: AuthApiClient,
    private val defaultDeviceName: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _authSuccessEvents = MutableSharedFlow<Unit>()
    val authSuccessEvents: SharedFlow<Unit> = _authSuccessEvents.asSharedFlow()
    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    fun switchMode(mode: AuthMode) {
        _uiState.update { currentState ->
            currentState.copy(
                mode = mode,
                confirmPassword = "",
                errorMessage = null,
            )
        }
    }

    fun updateServiceAddress(value: String) {
        _uiState.update { it.copy(serviceAddress = value) }
    }

    fun updateUsername(value: String) {
        _uiState.update { it.copy(username = value) }
    }

    fun updatePassword(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun updateConfirmPassword(value: String) {
        _uiState.update { it.copy(confirmPassword = value) }
    }

    fun updateDeviceName(value: String) {
        _uiState.update { it.copy(deviceName = value) }
    }

    fun showErrorMessage(message: String) {
        if (message.isBlank()) {
            return
        }
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun testConnection() {
        val currentState = _uiState.value
        val serviceAddressError = ServiceAddressFormatter.validate(currentState.serviceAddress)
        if (serviceAddressError != null) {
            showErrorMessage(serviceAddressError)
            return
        }

        val normalizedAddress = ServiceAddressFormatter.normalize(currentState.serviceAddress)
        val deviceNameForSubmit = currentState.deviceName.trim().ifBlank { defaultDeviceName }

        sessionStore.saveBaseUrl(normalizedAddress)
        sessionStore.saveDeviceName(deviceNameForSubmit)

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTestingConnection = true,
                    errorMessage = null,
                )
            }

            try {
                authApiClient.testConnection(normalizedAddress)
                _uiState.update {
                    it.copy(
                        isTestingConnection = false,
                        errorMessage = null,
                    )
                }
                _toastEvents.emit("服务连接成功")
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isTestingConnection = false,
                        errorMessage = error.message ?: "服务连接失败",
                    )
                }
            }
        }
    }

    fun submit() {
        val currentState = _uiState.value
        if (currentState.isSubmitting || currentState.isTestingConnection) {
            return
        }

        val validationMessage = AuthFormValidator.validate(currentState)
        if (validationMessage != null) {
            showErrorMessage(validationMessage)
            return
        }

        val normalizedAddress = ServiceAddressFormatter.normalize(currentState.serviceAddress)
        val deviceNameForSubmit = currentState.deviceName.trim().ifBlank { defaultDeviceName }

        sessionStore.saveBaseUrl(normalizedAddress)
        sessionStore.saveDeviceName(deviceNameForSubmit)

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSubmitting = true,
                    errorMessage = null,
                )
            }

            try {
                val result = if (currentState.mode == AuthMode.Login) {
                    authApiClient.login(
                        baseUrl = normalizedAddress,
                        username = currentState.username.trim(),
                        password = currentState.password,
                        deviceName = deviceNameForSubmit,
                    )
                } else {
                    authApiClient.register(
                        baseUrl = normalizedAddress,
                        username = currentState.username.trim(),
                        password = currentState.password,
                        deviceName = deviceNameForSubmit,
                    )
                }

                val tokens = requireNotNull(result.tokens) {
                    "鉴权成功但未返回令牌"
                }

                // 登录/注册成功后，把当前环境和鉴权结果一起持久化，
                // 这样应用重启时就能直接走启动校验流程。
                sessionStore.saveAuthBundle(
                    baseUrl = normalizedAddress,
                    username = result.username,
                    deviceName = deviceNameForSubmit,
                    currentDeviceId = result.currentDeviceId,
                    tokens = tokens,
                )

                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        password = "",
                        confirmPassword = "",
                        errorMessage = null,
                    )
                }
                _authSuccessEvents.emit(Unit)
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = error.message ?: "鉴权失败",
                    )
                }
            }
        }
    }

    private fun createInitialState(): AuthUiState {
        val storedSession = sessionStore.readSession()
        return AuthUiState(
            serviceAddress = storedSession.baseUrl,
            username = storedSession.username,
            deviceName = storedSession.deviceName.ifBlank { defaultDeviceName },
        )
    }

    companion object {
        fun factory(
            sessionStore: SessionStore,
            authApiClient: AuthApiClient,
            defaultDeviceName: String,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AuthViewModel(
                        sessionStore = sessionStore,
                        authApiClient = authApiClient,
                        defaultDeviceName = defaultDeviceName,
                    ) as T
                }
            }
        }
    }
}
