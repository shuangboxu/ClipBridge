package com.xushuangbo.clipbridge.feature.shell

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.xushuangbo.clipbridge.app.AppTestTags

internal data class HomeShortcut(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val detailPage: DetailPage? = null,
    val targetTab: MainTab? = null,
    val historyAction: HistoryShortcutAction? = null,
)

internal data class ActionEntry(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

internal data class SettingEntry(
    val title: String,
    val detailPage: DetailPage,
)

internal enum class MainTab(
    val title: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Home("首页", "首页", Icons.Outlined.Home),
    History("历史", "历史", Icons.Outlined.Description),
    Ai("AI", "AI", Icons.Outlined.AutoAwesome),
    Settings("设置", "设置", Icons.Outlined.Settings),
}

internal enum class DetailPage(val title: String) {
    Files("文件"),
    Scan("扫一扫"),
    Share("分享"),
    AccountInfo("账号信息"),
    Device("设备"),
    Requests("申请"),
    GlobalSettings("全局设置"),
    Approvals("审批"),
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ShellScaffold(
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
    var currentTab by rememberSaveable { mutableStateOf(MainTab.Home) }
    var currentDetailPage by rememberSaveable { mutableStateOf<DetailPage?>(null) }
    var homeMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var historySearchQuery by rememberSaveable { mutableStateOf("") }
    var historyUploadDialogVisible by rememberSaveable { mutableStateOf(false) }
    var pendingHistoryShortcutAction by remember { mutableStateOf<HistoryShortcutAction?>(null) }
    val context = LocalContext.current

    // 主页面的快捷入口只负责把用户送到正确页面，具体业务仍然由对应页面处理。
    val homeShortcuts = listOf(
        HomeShortcut("上传文本", Icons.Outlined.CloudUpload, targetTab = MainTab.History, historyAction = HistoryShortcutAction.OpenUpload),
        HomeShortcut("拉取历史", Icons.Outlined.CloudDownload, targetTab = MainTab.History, historyAction = HistoryShortcutAction.PullRemote),
        HomeShortcut("设备", Icons.Outlined.Devices, detailPage = DetailPage.Device),
        HomeShortcut("AI", Icons.Outlined.AutoAwesome, targetTab = MainTab.Ai),
        HomeShortcut("文件", Icons.Outlined.Folder, detailPage = DetailPage.Files),
        HomeShortcut("分享", Icons.Outlined.Share, detailPage = DetailPage.Share),
    )

    val onPendingFeatureClick: (String) -> Unit = { title ->
        Toast.makeText(context, "$title 后续接入", Toast.LENGTH_SHORT).show()
    }

    val openDetailPage: (DetailPage) -> Unit = { page ->
        currentDetailPage = page
    }

    val onShortcutClick: (HomeShortcut) -> Unit = { entry ->
        when {
            entry.targetTab != null -> {
                currentTab = entry.targetTab
                currentDetailPage = null
                pendingHistoryShortcutAction = entry.historyAction
            }

            entry.detailPage != null -> openDetailPage(entry.detailPage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentDetailPage?.title ?: currentTab.title,
                    )
                },
                navigationIcon = {
                    if (currentDetailPage != null) {
                        IconButton(onClick = { currentDetailPage = null }) {
                            Text(text = "<")
                        }
                    }
                },
                actions = {
                    if (currentDetailPage == null && currentTab == MainTab.Home) {
                        Box {
                            IconButton(onClick = { homeMenuExpanded = true }) {
                                Text(text = "+")
                            }

                            DropdownMenu(
                                expanded = homeMenuExpanded,
                                onDismissRequest = { homeMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("扫一扫") },
                                    onClick = {
                                        homeMenuExpanded = false
                                        openDetailPage(DetailPage.Scan)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("分享") },
                                    onClick = {
                                        homeMenuExpanded = false
                                        openDetailPage(DetailPage.Share)
                                    },
                                )
                            }
                        }
                    } else if (currentDetailPage == null && currentTab == MainTab.History) {
                        IconButton(
                            onClick = { historyUploadDialogVisible = true },
                            modifier = Modifier.testTag(AppTestTags.HistoryUploadButton),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudUpload,
                                contentDescription = "手动上传文本",
                            )
                        }
                        IconButton(
                            onClick = { historyViewModel.refreshHistory() },
                            modifier = Modifier.testTag(AppTestTags.HistoryRefreshButton),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "刷新历史",
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentDetailPage == null && currentTab == tab,
                        onClick = {
                            currentTab = tab
                            currentDetailPage = null
                        },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding: PaddingValues ->
        when (currentDetailPage) {
            DetailPage.Files -> FilesScreen(innerPadding = innerPadding, onPendingFeatureClick = onPendingFeatureClick)
            DetailPage.Scan -> ScanScreen(innerPadding = innerPadding)
            DetailPage.Share -> ShareScreen(innerPadding = innerPadding, onPendingFeatureClick = onPendingFeatureClick)
            DetailPage.AccountInfo -> AccountInfoScreen(
                innerPadding = innerPadding,
                uiState = settingsUiState,
                onServiceAddressChange = onServiceAddressChange,
                onSaveServiceAddress = onSaveServiceAddress,
            )
            DetailPage.Device -> DeviceScreenRoute(
                innerPadding = innerPadding,
                currentDeviceId = settingsUiState.currentDeviceId,
                uiState = deviceUiState,
                onLoadDevices = onLoadDevices,
                onRefreshDevices = onRefreshDevices,
                onOpenDeviceDetails = onOpenDeviceDetails,
                onOpenDeviceEditor = onOpenDeviceEditor,
                onDismissDialog = onDismissDeviceDialog,
                onRenameDraftChange = onDeviceNameDraftChange,
                onSaveDeviceName = onSaveDeviceName,
                onForceOfflineDevice = onForceOfflineDevice,
            )
            DetailPage.Requests -> RequestScreen(innerPadding = innerPadding, onPendingFeatureClick = onPendingFeatureClick)
            DetailPage.GlobalSettings -> GlobalSettingsScreen(innerPadding = innerPadding, onPendingFeatureClick = onPendingFeatureClick)
            DetailPage.Approvals -> ApprovalsScreen(innerPadding = innerPadding, onPendingFeatureClick = onPendingFeatureClick)
            null -> when (currentTab) {
                MainTab.Home -> HomeScreen(
                    syncEnabled = settingsUiState.syncEnabled,
                    innerPadding = innerPadding,
                    onToggleSync = onToggleSync,
                    shortcuts = homeShortcuts,
                    onShortcutClick = onShortcutClick,
                )
                MainTab.History -> HistoryScreenRoute(
                    innerPadding = innerPadding,
                    viewModel = historyViewModel,
                    searchQuery = historySearchQuery,
                    onSearchQueryChange = { historySearchQuery = it },
                    pendingShortcutAction = pendingHistoryShortcutAction,
                    onShortcutActionConsumed = { pendingHistoryShortcutAction = null },
                    uploadDialogVisible = historyUploadDialogVisible,
                    onUploadDialogDismiss = { historyUploadDialogVisible = false },
                    onRequireAuth = onRequireAuth,
                )
                MainTab.Ai -> AiScreen(innerPadding = innerPadding, onPendingFeatureClick = onPendingFeatureClick)
                MainTab.Settings -> SettingsScreen(
                    innerPadding = innerPadding,
                    onOpenDetailPage = openDetailPage,
                    onLogout = onLogout,
                )
            }
        }
    }
}
