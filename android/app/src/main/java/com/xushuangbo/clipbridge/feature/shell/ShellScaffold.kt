package com.xushuangbo.clipbridge.feature.shell

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val visibleInBottomBar: Boolean = true,
) {
    Home("首页", "首页", Icons.Outlined.Home),
    History("历史", "历史", Icons.Outlined.Description),
    Ai("AI", "AI", Icons.Outlined.AutoAwesome, visibleInBottomBar = false),
    Settings("设置", "设置", Icons.Outlined.Settings),
}

internal enum class DetailPage(val title: String) {
    Files("文件"),
    Scan("扫一扫"),
    Share("分享"),
    ShareRules("分享规则"),
    HistorySettings("历史设置"),
    AccountInfo("账号信息"),
    Security("安全"),
    Device("设备"),
    Requests("申请"),
    GlobalSettings("全局设置"),
    UserManagement("用户管理"),
    Approvals("审批"),
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ShellScaffold(
    settingsUiState: SettingsUiState,
    deviceUiState: DeviceUiState,
    historyViewModel: HistoryViewModel,
    historySettingsViewModel: HistorySettingsViewModel,
    filesViewModel: FilesViewModel,
    sharesViewModel: SharesViewModel,
    scanShareViewModel: ScanShareViewModel,
    requestsViewModel: RequestsViewModel,
    adminSettingsViewModel: AdminSettingsViewModel,
    adminUsersViewModel: AdminUsersViewModel,
    adminReviewsViewModel: AdminReviewsViewModel,
    onRequireAuth: (String) -> Unit,
    onToggleSync: () -> Unit,
    onServiceAddressChange: (String) -> Unit,
    onSaveServiceAddress: () -> Unit,
    onRefreshSessionSnapshot: () -> Unit,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmNewPasswordChange: (String) -> Unit,
    onChangePassword: () -> Unit,
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
    var historySearchQuery by rememberSaveable { mutableStateOf("") }
    var historyUploadDialogVisible by rememberSaveable { mutableStateOf(false) }
    var filesUploadRequestVersion by rememberSaveable { mutableStateOf(0) }
    var shareCreateDialogVisible by rememberSaveable { mutableStateOf(false) }
    var pendingHistoryShortcutAction by remember { mutableStateOf<HistoryShortcutAction?>(null) }
    val sharesUiState by sharesViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val visibleMainTabs = remember { MainTab.entries.filter { it.visibleInBottomBar } }

    // AI 页暂时不对外展示。
    // 如果系统恢复了旧的底栏状态，导致 currentTab 还停留在 AI，
    // 这里统一把它拉回首页，避免用户进入一个已经隐藏但无法切换出来的页面。
    LaunchedEffect(currentTab, currentDetailPage) {
        if (currentDetailPage == null && !currentTab.visibleInBottomBar) {
            currentTab = MainTab.Home
        }
    }

    // 主页面的快捷入口只负责把用户送到正确页面，具体业务仍然由对应页面处理。
    val homeShortcuts = listOf(
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
                        IconButton(onClick = { openDetailPage(DetailPage.Scan) }) {
                            Icon(
                                imageVector = Icons.Outlined.QrCodeScanner,
                                contentDescription = "扫一扫",
                            )
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
                    } else if (currentDetailPage == DetailPage.Files) {
                        IconButton(
                            onClick = { filesUploadRequestVersion += 1 },
                            modifier = Modifier.testTag(AppTestTags.FilesUploadButton),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudUpload,
                                contentDescription = "上传文件",
                            )
                        }
                        IconButton(
                            onClick = { filesViewModel.refreshFiles() },
                            modifier = Modifier.testTag(AppTestTags.FilesRefreshButton),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "刷新文件列表",
                            )
                        }
                    } else if (currentDetailPage == DetailPage.Share) {
                        IconButton(
                            // 分享页正文只展示记录，右上角按钮负责打开创建弹窗。
                            onClick = { shareCreateDialogVisible = true },
                            enabled = !sharesUiState.isCreating,
                            modifier = Modifier.testTag(AppTestTags.ShareCreateButton),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = "创建分享",
                            )
                        }
                    } else if (currentDetailPage == DetailPage.Requests) {
                        IconButton(onClick = { requestsViewModel.refresh() }) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "刷新申请记录",
                            )
                        }
                    } else if (currentDetailPage == DetailPage.GlobalSettings) {
                        IconButton(onClick = { adminSettingsViewModel.refresh() }) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "刷新管理员设置",
                            )
                        }
                    } else if (currentDetailPage == DetailPage.UserManagement) {
                        IconButton(onClick = { adminUsersViewModel.refreshUsers() }) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "刷新用户列表",
                            )
                        }
                    } else if (currentDetailPage == DetailPage.Approvals) {
                        IconButton(onClick = { adminReviewsViewModel.refresh() }) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "刷新审批队列",
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                visibleMainTabs.forEach { tab ->
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
            DetailPage.Files -> FilesScreenRoute(
                innerPadding = innerPadding,
                viewModel = filesViewModel,
                uploadRequestVersion = filesUploadRequestVersion,
            )
            DetailPage.Scan -> ScanScreenRoute(
                innerPadding = innerPadding,
                viewModel = scanShareViewModel,
            )
            DetailPage.Share -> ShareScreenRoute(
                innerPadding = innerPadding,
                viewModel = sharesViewModel,
                createDialogVisible = shareCreateDialogVisible,
                onCreateDialogDismiss = { shareCreateDialogVisible = false },
            )
            DetailPage.ShareRules -> ShareRulesScreenRoute(
                innerPadding = innerPadding,
                viewModel = sharesViewModel,
            )
            DetailPage.HistorySettings -> HistorySettingsScreenRoute(
                innerPadding = innerPadding,
                viewModel = historySettingsViewModel,
                onRequireAuth = onRequireAuth,
            )
            DetailPage.AccountInfo -> AccountInfoScreen(
                innerPadding = innerPadding,
                uiState = settingsUiState,
                onServiceAddressChange = onServiceAddressChange,
                onSaveServiceAddress = onSaveServiceAddress,
            )
            DetailPage.Security -> SecurityScreen(
                innerPadding = innerPadding,
                uiState = settingsUiState,
                onCurrentPasswordChange = onCurrentPasswordChange,
                onNewPasswordChange = onNewPasswordChange,
                onConfirmNewPasswordChange = onConfirmNewPasswordChange,
                onChangePassword = onChangePassword,
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
            DetailPage.Requests -> RequestsScreenRoute(
                innerPadding = innerPadding,
                viewModel = requestsViewModel,
            )
            DetailPage.GlobalSettings -> AdminSettingsScreenRoute(
                innerPadding = innerPadding,
                viewModel = adminSettingsViewModel,
            )
            DetailPage.UserManagement -> UserManagementScreenRoute(
                innerPadding = innerPadding,
                viewModel = adminUsersViewModel,
            )
            DetailPage.Approvals -> ApprovalsScreenRoute(
                innerPadding = innerPadding,
                viewModel = adminReviewsViewModel,
            )
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
                    uiState = settingsUiState,
                    onOpenDetailPage = openDetailPage,
                    onRefreshSessionSnapshot = onRefreshSessionSnapshot,
                    onLogout = onLogout,
                )
            }
        }
    }
}
