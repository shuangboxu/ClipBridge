package com.xushuangbo.clipbridge.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xushuangbo.clipbridge.core.network.AuthApiClient
import com.xushuangbo.clipbridge.core.network.AuthApiException
import com.xushuangbo.clipbridge.core.network.DeviceRecord
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
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class DeviceDialogMode {
    Details,
    Edit,
}

data class DeviceSummary(
    val total: Int = 0,
    val online: Int = 0,
    val latestLastSeenText: String = "-",
)

data class DeviceUiState(
    val devices: List<DeviceRecord> = emptyList(),
    val summary: DeviceSummary = DeviceSummary(),
    val isLoadingDevices: Boolean = false,
    val isSavingName: Boolean = false,
    val isForcingOffline: Boolean = false,
    val errorMessage: String? = null,
    val dialogMode: DeviceDialogMode? = null,
    val selectedDeviceId: String = "",
    val renameDraft: String = "",
)

class DeviceViewModel(
    private val sessionStore: SessionStore,
    private val authApiClient: AuthApiClient,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DeviceUiState())
    val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()

    private val _sessionExitEvents = MutableSharedFlow<String>()
    val sessionExitEvents: SharedFlow<String> = _sessionExitEvents.asSharedFlow()
    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private var hasLoadedOnce = false

    fun ensureLoaded() {
        if (hasLoadedOnce || _uiState.value.isLoadingDevices) {
            return
        }
        loadDevices()
    }

    fun refreshDevices() {
        loadDevices()
    }

    fun openDeviceDetails(deviceId: String) {
        val device = findDevice(deviceId)
        if (device == null) {
            _uiState.update { it.copy(errorMessage = "设备不存在或已被移除。") }
            return
        }

        _uiState.update {
            it.copy(
                dialogMode = DeviceDialogMode.Details,
                selectedDeviceId = device.id,
                renameDraft = device.deviceName,
                errorMessage = null,
            )
        }
    }

    fun openDeviceEditor(deviceId: String) {
        val device = findDevice(deviceId)
        if (device == null) {
            _uiState.update { it.copy(errorMessage = "设备不存在或已被移除。") }
            return
        }

        _uiState.update {
            it.copy(
                dialogMode = DeviceDialogMode.Edit,
                selectedDeviceId = device.id,
                renameDraft = device.deviceName,
                errorMessage = null,
            )
        }
    }

    fun dismissDialog() {
        _uiState.update {
            it.copy(
                dialogMode = null,
                selectedDeviceId = "",
                renameDraft = "",
            )
        }
    }

    fun updateRenameDraft(value: String) {
        _uiState.update { it.copy(renameDraft = value) }
    }

    fun saveDeviceName() {
        val currentState = _uiState.value
        val selectedDevice = findSelectedDevice()
        val normalizedDeviceName = currentState.renameDraft.trim()

        if (selectedDevice == null) {
            _uiState.update { it.copy(errorMessage = "设备不存在或已被移除。") }
            return
        }
        if (normalizedDeviceName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "设备名称不能为空") }
            return
        }
        if (normalizedDeviceName.length > 128) {
            _uiState.update { it.copy(errorMessage = "设备名称最多 128 个字符") }
            return
        }

        val currentSession = sessionStore.readSession()
        if (!currentSession.hasCompleteAuth()) {
            sessionStore.clearAuth()
            emitSessionExit("登录已失效，请重新登录")
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSavingName = true,
                    errorMessage = null,
                )
            }

            try {
                val result = authApiClient.updateDeviceName(
                    session = currentSession,
                    deviceId = selectedDevice.id,
                    deviceName = normalizedDeviceName,
                    onRefreshing = null,
                )

                result.tokens?.let(sessionStore::updateTokens)
                if (result.device.id == currentSession.currentDeviceId) {
                    sessionStore.saveDeviceName(result.device.deviceName)
                }

                val updatedDevices = _uiState.value.devices.map { device ->
                    if (device.id == result.device.id) {
                        result.device
                    } else {
                        device
                    }
                }

                _uiState.update {
                    it.copy(
                        devices = updatedDevices,
                        summary = buildSummary(updatedDevices),
                        isSavingName = false,
                        dialogMode = null,
                        selectedDeviceId = "",
                        renameDraft = "",
                    )
                }
                _toastEvents.emit("设备名称已更新")
            } catch (error: AuthApiException) {
                handleRequestError(error)
            } catch (error: IOException) {
                _uiState.update {
                    it.copy(
                        isSavingName = false,
                        errorMessage = error.message ?: "网络异常，请稍后重试",
                    )
                }
            }
        }
    }

    fun forceOfflineSelectedDevice() {
        val selectedDevice = findSelectedDevice()
        if (selectedDevice == null) {
            _uiState.update { it.copy(errorMessage = "设备不存在或已被移除。") }
            return
        }

        val currentSession = sessionStore.readSession()
        if (!currentSession.hasCompleteAuth()) {
            sessionStore.clearAuth()
            emitSessionExit("登录已失效，请重新登录")
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isForcingOffline = true,
                    errorMessage = null,
                )
            }

            try {
                val result = authApiClient.forceOfflineDevice(
                    session = currentSession,
                    deviceId = selectedDevice.id,
                    onRefreshing = null,
                )

                result.tokens?.let(sessionStore::updateTokens)

                if (result.currentDeviceForcedOffline) {
                    sessionStore.clearAuth()
                    _uiState.value = DeviceUiState()
                    emitSessionExit("当前设备已被强制下线，请重新登录")
                    return@launch
                }

                val remainingDevices = _uiState.value.devices.filterNot { device ->
                    device.id == result.device.id
                }

                _uiState.update {
                    it.copy(
                        devices = remainingDevices,
                        summary = buildSummary(remainingDevices),
                        isForcingOffline = false,
                        dialogMode = null,
                        selectedDeviceId = "",
                        renameDraft = "",
                    )
                }
                _toastEvents.emit("设备已强制下线")
            } catch (error: AuthApiException) {
                handleRequestError(error)
            } catch (error: IOException) {
                _uiState.update {
                    it.copy(
                        isForcingOffline = false,
                        errorMessage = error.message ?: "网络异常，请稍后重试",
                    )
                }
            }
        }
    }

    private fun loadDevices() {
        val currentSession = sessionStore.readSession()
        if (!currentSession.hasCompleteAuth()) {
            sessionStore.clearAuth()
            emitSessionExit("登录已失效，请重新登录")
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingDevices = true,
                    errorMessage = null,
                )
            }

            try {
                val result = authApiClient.listDevices(
                    session = currentSession,
                    onRefreshing = null,
                )

                result.tokens?.let(sessionStore::updateTokens)
                hasLoadedOnce = true
                _uiState.update {
                    it.copy(
                        devices = result.devices,
                        summary = buildSummary(result.devices),
                        isLoadingDevices = false,
                        errorMessage = null,
                    )
                }
            } catch (error: AuthApiException) {
                handleRequestError(error)
            } catch (error: IOException) {
                _uiState.update {
                    it.copy(
                        isLoadingDevices = false,
                        errorMessage = error.message ?: "网络异常，请稍后重试",
                    )
                }
            }
        }
    }

    private fun handleRequestError(error: AuthApiException) {
        if (error.httpCode == 401) {
            sessionStore.clearAuth()
            _uiState.value = DeviceUiState()
            emitSessionExit("登录已失效，请重新登录")
            return
        }

        _uiState.update {
            it.copy(
                isLoadingDevices = false,
                isSavingName = false,
                isForcingOffline = false,
                errorMessage = error.message ?: "服务暂时不可用，请稍后重试",
            )
        }
    }

    private fun findDevice(deviceId: String): DeviceRecord? {
        return _uiState.value.devices.find { device -> device.id == deviceId }
    }

    private fun findSelectedDevice(): DeviceRecord? {
        return findDevice(_uiState.value.selectedDeviceId)
    }

    private fun emitSessionExit(message: String) {
        viewModelScope.launch {
            _sessionExitEvents.emit(message)
        }
    }

    private fun buildSummary(devices: List<DeviceRecord>): DeviceSummary {
        val total = devices.size
        val online = devices.count { device -> device.isActive }
        val latestLastSeen = devices
            .maxByOrNull { device -> parseTimeToEpoch(device.lastSeenAt) }
            ?.lastSeenAt

        return DeviceSummary(
            total = total,
            online = online,
            latestLastSeenText = formatTimeForSummary(latestLastSeen),
        )
    }

    private fun parseTimeToEpoch(value: String): Long {
        if (value.isBlank()) {
            return Long.MIN_VALUE
        }

        return try {
            OffsetDateTime.parse(value).toInstant().toEpochMilli()
        } catch (_: Exception) {
            Long.MIN_VALUE
        }
    }

    private fun formatTimeForSummary(value: String?): String {
        if (value.isNullOrBlank()) {
            return "-"
        }

        return try {
            val localDateTime = OffsetDateTime.parse(value)
                .atZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime()
            DEVICE_SUMMARY_TIME_FORMATTER.format(localDateTime)
        } catch (_: Exception) {
            value
        }
    }

    companion object {
        private val DEVICE_SUMMARY_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

        fun factory(
            sessionStore: SessionStore,
            authApiClient: AuthApiClient,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DeviceViewModel(
                        sessionStore = sessionStore,
                        authApiClient = authApiClient,
                    ) as T
                }
            }
        }
    }
}
