package com.xushuangbo.clipbridge.feature.shell

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xushuangbo.clipbridge.feature.sync.SyncRuntimeEffect
import com.xushuangbo.clipbridge.feature.sync.SyncServiceController

@Composable
fun MainShellRoute(
    settingsViewModel: SettingsViewModel = viewModel(),
    deviceViewModel: DeviceViewModel = viewModel(),
    historyViewModel: HistoryViewModel = viewModel(),
    onRequireAuth: (String) -> Unit,
) {
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val deviceUiState by deviceViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appContext = context.applicationContext

    // 这里仍然保留在入口层收集事件，避免把 ViewModel 和 Toast 逻辑塞进壳层组件里。
    LaunchedEffect(settingsViewModel) {
        settingsViewModel.sessionExitEvents.collect { message ->
            SyncServiceController.stopAll(appContext)
            onRequireAuth(message)
        }
    }
    LaunchedEffect(deviceViewModel) {
        deviceViewModel.sessionExitEvents.collect { message ->
            SyncServiceController.stopAll(appContext)
            onRequireAuth(message)
        }
    }
    LaunchedEffect(settingsViewModel, context) {
        settingsViewModel.toastEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(deviceViewModel, context) {
        deviceViewModel.toastEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    SyncRuntimeEffect(
        syncEnabled = settingsUiState.syncEnabled,
        hasSession = settingsUiState.currentDeviceId.isNotBlank() && settingsUiState.serviceAddress.isNotBlank(),
    )

    ShellScaffold(
        settingsUiState = settingsUiState,
        deviceUiState = deviceUiState,
        historyViewModel = historyViewModel,
        onRequireAuth = onRequireAuth,
        onToggleSync = settingsViewModel::toggleSyncEnabled,
        onServiceAddressChange = settingsViewModel::updateServiceAddress,
        onSaveServiceAddress = settingsViewModel::saveServiceAddress,
        onLogout = settingsViewModel::logout,
        onLoadDevices = deviceViewModel::ensureLoaded,
        onRefreshDevices = deviceViewModel::refreshDevices,
        onOpenDeviceDetails = deviceViewModel::openDeviceDetails,
        onOpenDeviceEditor = deviceViewModel::openDeviceEditor,
        onDismissDeviceDialog = deviceViewModel::dismissDialog,
        onDeviceNameDraftChange = deviceViewModel::updateRenameDraft,
        onSaveDeviceName = deviceViewModel::saveDeviceName,
        onForceOfflineDevice = deviceViewModel::forceOfflineSelectedDevice,
    )
}

@Composable
fun MainShell(
    settingsUiState: SettingsUiState,
    deviceUiState: DeviceUiState,
    historyViewModel: HistoryViewModel,
    onRequireAuth: (String) -> Unit,
    onToggleSync: () -> Unit,
    onServiceAddressChange: (String) -> Unit,
    onSaveServiceAddress: () -> Unit,
    onLogout: () -> Unit,
    onLoadDevices: () -> Unit,
    onRefreshDevices: () -> Unit,
    onOpenDeviceDetails: (String) -> Unit,
    onOpenDeviceEditor: (String) -> Unit,
    onDismissDeviceDialog: () -> Unit,
    onDeviceNameDraftChange: (String) -> Unit,
    onSaveDeviceName: () -> Unit,
    onForceOfflineDevice: () -> Unit,
) {
    ShellScaffold(
        settingsUiState = settingsUiState,
        deviceUiState = deviceUiState,
        historyViewModel = historyViewModel,
        onRequireAuth = onRequireAuth,
        onToggleSync = onToggleSync,
        onServiceAddressChange = onServiceAddressChange,
        onSaveServiceAddress = onSaveServiceAddress,
        onLogout = onLogout,
        onLoadDevices = onLoadDevices,
        onRefreshDevices = onRefreshDevices,
        onOpenDeviceDetails = onOpenDeviceDetails,
        onOpenDeviceEditor = onOpenDeviceEditor,
        onDismissDeviceDialog = onDismissDeviceDialog,
        onDeviceNameDraftChange = onDeviceNameDraftChange,
        onSaveDeviceName = onSaveDeviceName,
        onForceOfflineDevice = onForceOfflineDevice,
    )
}
