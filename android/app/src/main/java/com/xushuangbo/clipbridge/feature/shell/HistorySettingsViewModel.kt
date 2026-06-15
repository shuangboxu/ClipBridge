package com.xushuangbo.clipbridge.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xushuangbo.clipbridge.core.network.AuthApiException
import com.xushuangbo.clipbridge.core.session.SessionStore
import com.xushuangbo.clipbridge.core.sync.ClipboardSyncCoordinator
import com.xushuangbo.clipbridge.core.sync.HistoryUpdateBus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class HistorySettingsUiState(
    val retentionDaysInput: String = "0",
    val historyLimitInput: String = "1000",
    val deletedCount: Int = 0,
    val isLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isClearing: Boolean = false,
    val isCleaningUp: Boolean = false,
    val errorMessage: String? = null,
)

class HistorySettingsViewModel(
    private val sessionStore: SessionStore,
    private val clipboardSyncCoordinator: ClipboardSyncCoordinator,
    private val historyUpdateBus: HistoryUpdateBus,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HistorySettingsUiState())
    val uiState: StateFlow<HistorySettingsUiState> = _uiState.asStateFlow()

    private val _sessionExitEvents = MutableSharedFlow<String>()
    val sessionExitEvents: SharedFlow<String> = _sessionExitEvents.asSharedFlow()
    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    init {
        observeHistoryChanges()
    }

    fun ensureLoaded() {
        if (_uiState.value.isLoaded || _uiState.value.isLoading) {
            return
        }
        loadSettings()
    }

    fun loadSettings() {
        val currentSession = requireSession() ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                )
            }

            try {
                val result = clipboardSyncCoordinator.getHistorySettings(currentSession)
                _uiState.update {
                    it.copy(
                        retentionDaysInput = result.settings.retentionDays.toString(),
                        historyLimitInput = result.settings.historyLimit.toString(),
                        isLoaded = true,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
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

    fun updateRetentionDaysInput(value: String) {
        _uiState.update {
            it.copy(
                retentionDaysInput = value.filter { char -> char.isDigit() },
                errorMessage = null,
            )
        }
    }

    fun updateHistoryLimitInput(value: String) {
        _uiState.update {
            it.copy(
                historyLimitInput = value.filter { char -> char.isDigit() },
                errorMessage = null,
            )
        }
    }

    fun saveSettings() {
        val currentSession = requireSession() ?: return
        val retentionDays = _uiState.value.retentionDaysInput.toIntOrNull()
        val historyLimit = _uiState.value.historyLimitInput.toIntOrNull()
        val validationMessage = validateInputs(retentionDays, historyLimit)
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
                val result = clipboardSyncCoordinator.updateHistorySettings(
                    session = currentSession,
                    retentionDays = retentionDays ?: 0,
                    historyLimit = historyLimit ?: 1000,
                )
                _uiState.update {
                    it.copy(
                        retentionDaysInput = result.settings.retentionDays.toString(),
                        historyLimitInput = result.settings.historyLimit.toString(),
                        deletedCount = result.deletedCount,
                        isLoaded = true,
                        isSaving = false,
                        errorMessage = null,
                    )
                }
                if (result.deletedCount > 0) {
                    historyUpdateBus.notifyHistoryChanged()
                }
                _toastEvents.emit(
                    if (result.deletedCount > 0) {
                        "历史设置已保存，并清理了 ${result.deletedCount} 条记录"
                    } else {
                        "历史设置已保存"
                    },
                )
            } catch (error: AuthApiException) {
                handleRequestError(error)
            } catch (error: IOException) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.message ?: "网络异常，请稍后重试",
                    )
                }
            }
        }
    }

    fun clearHistory() {
        val currentSession = requireSession() ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isClearing = true,
                    errorMessage = null,
                )
            }

            try {
                val result = clipboardSyncCoordinator.clearHistory(currentSession)
                _uiState.update {
                    it.copy(
                        deletedCount = result.deletedCount,
                        isLoaded = true,
                        isClearing = false,
                        errorMessage = null,
                    )
                }
                historyUpdateBus.notifyHistoryChanged()
                _toastEvents.emit("已清空 ${result.deletedCount} 条历史记录")
            } catch (error: AuthApiException) {
                handleRequestError(error)
            } catch (error: IOException) {
                _uiState.update {
                    it.copy(
                        isClearing = false,
                        errorMessage = error.message ?: "网络异常，请稍后重试",
                    )
                }
            }
        }
    }

    fun cleanupOlderThanDays() {
        val currentSession = requireSession() ?: return
        val retentionDays = _uiState.value.retentionDaysInput.toIntOrNull()
        if (retentionDays == null || retentionDays <= 0) {
            _uiState.update { it.copy(errorMessage = "请输入大于 0 的保留天数") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCleaningUp = true,
                    errorMessage = null,
                )
            }

            try {
                val result = clipboardSyncCoordinator.cleanupHistoryOlderThan(
                    session = currentSession,
                    days = retentionDays,
                )
                _uiState.update {
                    it.copy(
                        deletedCount = result.deletedCount,
                        isLoaded = true,
                        isCleaningUp = false,
                        errorMessage = null,
                    )
                }
                historyUpdateBus.notifyHistoryChanged()
                _toastEvents.emit("已清理 ${result.deletedCount} 条超期历史记录")
            } catch (error: AuthApiException) {
                handleRequestError(error)
            } catch (error: IOException) {
                _uiState.update {
                    it.copy(
                        isCleaningUp = false,
                        errorMessage = error.message ?: "网络异常，请稍后重试",
                    )
                }
            }
        }
    }

    private fun observeHistoryChanges() {
        viewModelScope.launch {
            historyUpdateBus.events.collect {
                if (_uiState.value.isLoaded && !_uiState.value.isLoading) {
                    loadSettings()
                }
            }
        }
    }

    private fun validateInputs(retentionDays: Int?, historyLimit: Int?): String? {
        return when {
            retentionDays == null -> "保留天数必须是数字"
            retentionDays < 0 -> "保留天数不能小于 0"
            historyLimit == null -> "最大记录数必须是数字"
            historyLimit <= 0 -> "最大记录数必须大于 0"
            else -> null
        }
    }

    private fun requireSession() = sessionStore.readSession().takeIf { session ->
        if (session.hasCompleteAuth()) {
            true
        } else {
            sessionStore.clearClipboardSyncState()
            sessionStore.clearAuth()
            emitSessionExit("登录已失效，请重新登录")
            false
        }
    }

    private fun handleRequestError(error: AuthApiException) {
        if (error.httpCode == 401) {
            sessionStore.clearClipboardSyncState()
            sessionStore.clearAuth()
            _uiState.value = HistorySettingsUiState()
            emitSessionExit("登录已失效，请重新登录")
            return
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                isSaving = false,
                isClearing = false,
                isCleaningUp = false,
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
            clipboardSyncCoordinator: ClipboardSyncCoordinator,
            historyUpdateBus: HistoryUpdateBus,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HistorySettingsViewModel(
                        sessionStore = sessionStore,
                        clipboardSyncCoordinator = clipboardSyncCoordinator,
                        historyUpdateBus = historyUpdateBus,
                    ) as T
                }
            }
        }
    }
}
