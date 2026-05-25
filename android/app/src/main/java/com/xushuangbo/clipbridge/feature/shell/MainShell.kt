package com.xushuangbo.clipbridge.feature.shell

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xushuangbo.clipbridge.R
import com.xushuangbo.clipbridge.app.AppTestTags
import com.xushuangbo.clipbridge.ui.components.PageErrorBanner

private data class HomeShortcut(
    val title: String,
    val icon: ImageVector,
    val detailPage: DetailPage? = null,
    val targetTab: MainTab? = null,
)

private data class ActionEntry(
    val title: String,
    val icon: ImageVector,
)

private data class SettingEntry(
    val title: String,
    val detailPage: DetailPage,
)

private enum class MainTab(
    val title: String,
    val label: String,
    val icon: ImageVector,
) {
    Home("首页", "首页", Icons.Outlined.Home),
    History("历史", "历史", Icons.Outlined.Description),
    Ai("AI", "AI", Icons.Outlined.AutoAwesome),
    Settings("设置", "设置", Icons.Outlined.Settings),
}

private enum class DetailPage(val title: String) {
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
fun MainShellRoute(
    settingsViewModel: SettingsViewModel = viewModel(),
    deviceViewModel: DeviceViewModel = viewModel(),
    onRequireAuth: (String) -> Unit,
) {
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val deviceUiState by deviceViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(settingsViewModel) {
        settingsViewModel.sessionExitEvents.collect { message ->
            onRequireAuth(message)
        }
    }

    LaunchedEffect(deviceViewModel) {
        deviceViewModel.sessionExitEvents.collect { message ->
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

    MainShell(
        settingsUiState = settingsUiState,
        deviceUiState = deviceUiState,
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
@OptIn(ExperimentalMaterial3Api::class)
fun MainShell(
    settingsUiState: SettingsUiState,
    deviceUiState: DeviceUiState,
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
    val context = LocalContext.current

    val homeShortcuts = listOf(
        HomeShortcut("文件", Icons.Outlined.Folder, detailPage = DetailPage.Files),
        HomeShortcut("分享", Icons.Outlined.Share, detailPage = DetailPage.Share),
        HomeShortcut("AI", Icons.Outlined.AutoAwesome, targetTab = MainTab.Ai),
        HomeShortcut("申请", Icons.Outlined.TaskAlt, detailPage = DetailPage.Requests),
    )

    val onPendingFeatureClick: (String) -> Unit = { title ->
        Toast.makeText(context, "$title 后续接入", Toast.LENGTH_SHORT).show()
    }

    // 这里把“主 Tab”和“二级页面”拆开保存，
    // 这样底栏结构可以先稳定下来，后面逐步补真实页面时不用反复改导航骨架。
    val openDetailPage: (DetailPage) -> Unit = { page ->
        currentDetailPage = page
    }

    val onShortcutClick: (HomeShortcut) -> Unit = { entry ->
        when {
            entry.targetTab != null -> {
                currentTab = entry.targetTab
                currentDetailPage = null
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
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    if (currentDetailPage != null) {
                        IconButton(onClick = { currentDetailPage = null }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                    }
                },
                actions = {
                    if (currentDetailPage == null) {
                        when (currentTab) {
                            MainTab.Home -> {
                                Box {
                                    IconButton(onClick = { homeMenuExpanded = true }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Add,
                                            contentDescription = "更多动作",
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = homeMenuExpanded,
                                        onDismissRequest = { homeMenuExpanded = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("扫一扫") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Outlined.QrCodeScanner,
                                                    contentDescription = null,
                                                )
                                            },
                                            onClick = {
                                                homeMenuExpanded = false
                                                openDetailPage(DetailPage.Scan)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("分享") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Outlined.Share,
                                                    contentDescription = null,
                                                )
                                            },
                                            onClick = {
                                                homeMenuExpanded = false
                                                openDetailPage(DetailPage.Share)
                                            },
                                        )
                                    }
                                }
                            }

                            MainTab.History -> {
                                IconButton(
                                    onClick = { openDetailPage(DetailPage.Files) },
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Description,
                                        contentDescription = "文件",
                                    )
                                }
                            }

                            MainTab.Ai,
                            MainTab.Settings,
                            -> Unit
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
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                            )
                        },
                        label = {
                            Text(tab.label)
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        when (currentDetailPage) {
            DetailPage.Files -> FilesScreen(
                innerPadding = innerPadding,
                onPendingFeatureClick = onPendingFeatureClick,
            )

            DetailPage.Scan -> ScanScreen(innerPadding = innerPadding)

            DetailPage.Share -> ShareScreen(
                innerPadding = innerPadding,
                onPendingFeatureClick = onPendingFeatureClick,
            )

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

            DetailPage.Requests -> RequestScreen(
                innerPadding = innerPadding,
                onPendingFeatureClick = onPendingFeatureClick,
            )

            DetailPage.GlobalSettings -> GlobalSettingsScreen(
                innerPadding = innerPadding,
                onPendingFeatureClick = onPendingFeatureClick,
            )

            DetailPage.Approvals -> ApprovalsScreen(
                innerPadding = innerPadding,
                onPendingFeatureClick = onPendingFeatureClick,
            )

            null -> {
                when (currentTab) {
                    MainTab.Home -> HomeScreen(
                        syncEnabled = settingsUiState.syncEnabled,
                        innerPadding = innerPadding,
                        onToggleSync = onToggleSync,
                        shortcuts = homeShortcuts,
                        onShortcutClick = onShortcutClick,
                    )

                    MainTab.History -> HistoryScreen(
                        innerPadding = innerPadding,
                        searchQuery = historySearchQuery,
                        onSearchQueryChange = { historySearchQuery = it },
                    )

                    MainTab.Ai -> AiScreen(
                        innerPadding = innerPadding,
                        onPendingFeatureClick = onPendingFeatureClick,
                    )

                    MainTab.Settings -> SettingsScreen(
                        innerPadding = innerPadding,
                        onOpenDetailPage = openDetailPage,
                        onLogout = onLogout,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    syncEnabled: Boolean,
    innerPadding: PaddingValues,
    onToggleSync: () -> Unit,
    shortcuts: List<HomeShortcut>,
    onShortcutClick: (HomeShortcut) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .testTag(AppTestTags.HomeScreen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SyncCenterPanel(
            enabled = syncEnabled,
            onToggleSync = onToggleSync,
        )

        Spacer(modifier = Modifier.height(24.dp))

        ShortcutSection(
            title = "快捷入口",
            entries = shortcuts,
            onEntryClick = onShortcutClick,
        )
    }
}

@Composable
private fun HistoryScreen(
    innerPadding: PaddingValues,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            singleLine = true,
            placeholder = { Text("搜索历史") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                )
            },
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun AiScreen(
    innerPadding: PaddingValues,
    onPendingFeatureClick: (String) -> Unit,
) {
    val aiEntries = listOf(
        ActionEntry("文本整理", Icons.Outlined.AutoAwesome),
        ActionEntry("内容总结", Icons.Outlined.Description),
        ActionEntry("分享文案", Icons.Outlined.Share),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        ActionGroup(
            title = "AI 工具",
            entries = aiEntries,
            onEntryClick = onPendingFeatureClick,
        )
    }
}

@Composable
private fun SettingsScreen(
    innerPadding: PaddingValues,
    onOpenDetailPage: (DetailPage) -> Unit,
    onLogout: () -> Unit,
) {
    val accountEntries = listOf(
        SettingEntry("账号信息", DetailPage.AccountInfo),
        SettingEntry("设备", DetailPage.Device),
        SettingEntry("申请", DetailPage.Requests),
    )
    val commonEntries = listOf(
        SettingEntry("文件", DetailPage.Files),
        SettingEntry("分享", DetailPage.Share),
    )
    val managementEntries = listOf(
        SettingEntry("全局设置", DetailPage.GlobalSettings),
        SettingEntry("审批", DetailPage.Approvals),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        SettingsSection(
            title = "账号",
            entries = accountEntries,
            onEntryClick = { entry -> onOpenDetailPage(entry.detailPage) },
        )

        Spacer(modifier = Modifier.height(22.dp))

        SettingsSection(
            title = "通用",
            entries = commonEntries,
            onEntryClick = { entry -> onOpenDetailPage(entry.detailPage) },
        )

        Spacer(modifier = Modifier.height(22.dp))

        SettingsSection(
            title = "管理",
            entries = managementEntries,
            onEntryClick = { entry -> onOpenDetailPage(entry.detailPage) },
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Logout,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("退出登录")
        }
    }
}

@Composable
private fun AccountInfoScreen(
    innerPadding: PaddingValues,
    uiState: SettingsUiState,
    onServiceAddressChange: (String) -> Unit,
    onSaveServiceAddress: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        uiState.errorMessage?.let { errorMessage ->
            PageErrorBanner(message = errorMessage)
            Spacer(modifier = Modifier.height(18.dp))
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                InfoRow("用户名", uiState.username.ifBlank { "未读取到账号信息" })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                InfoRow("设备名称", uiState.deviceName.ifBlank { "android-phone" })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                InfoRow("设备 ID", uiState.currentDeviceId.ifBlank { "暂无设备 ID" })
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            ) {
                Text(
                    text = "服务器地址",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.serviceAddress,
                    onValueChange = onServiceAddressChange,
                    singleLine = true,
                    label = { Text("服务地址") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onSaveServiceAddress,
                    enabled = !uiState.isSavingServiceAddress,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (uiState.isSavingServiceAddress) "保存中..." else "保存并重新登录")
                }
            }
        }
    }
}

@Composable
private fun RequestScreen(
    innerPadding: PaddingValues,
    onPendingFeatureClick: (String) -> Unit,
) {
    val requestEntries = listOf(
        ActionEntry("我的申请", Icons.Outlined.Description),
        ActionEntry("申请进度", Icons.Outlined.TaskAlt),
    )

    PageWithActionGroup(
        innerPadding = innerPadding,
        title = "申请",
        entries = requestEntries,
        onEntryClick = onPendingFeatureClick,
    )
}

@Composable
private fun FilesScreen(
    innerPadding: PaddingValues,
    onPendingFeatureClick: (String) -> Unit,
) {
    val fileEntries = listOf(
        ActionEntry("上传文件", Icons.Outlined.Folder),
        ActionEntry("接收记录", Icons.Outlined.Description),
        ActionEntry("分享文件", Icons.Outlined.Share),
    )

    PageWithActionGroup(
        innerPadding = innerPadding,
        title = "文件中心",
        entries = fileEntries,
        onEntryClick = onPendingFeatureClick,
    )
}

@Composable
private fun ShareScreen(
    innerPadding: PaddingValues,
    onPendingFeatureClick: (String) -> Unit,
) {
    val shareEntries = listOf(
        ActionEntry("文本分享", Icons.Outlined.Share),
        ActionEntry("文件分享", Icons.Outlined.Folder),
        ActionEntry("分享记录", Icons.Outlined.Description),
    )

    PageWithActionGroup(
        innerPadding = innerPadding,
        title = "分享",
        entries = shareEntries,
        onEntryClick = onPendingFeatureClick,
    )
}

@Composable
private fun GlobalSettingsScreen(
    innerPadding: PaddingValues,
    onPendingFeatureClick: (String) -> Unit,
) {
    val settingsEntries = listOf(
        ActionEntry("同步策略", Icons.Outlined.Tune),
        ActionEntry("显示偏好", Icons.Outlined.Settings),
    )

    PageWithActionGroup(
        innerPadding = innerPadding,
        title = "全局设置",
        entries = settingsEntries,
        onEntryClick = onPendingFeatureClick,
    )
}

@Composable
private fun ApprovalsScreen(
    innerPadding: PaddingValues,
    onPendingFeatureClick: (String) -> Unit,
) {
    val approvalEntries = listOf(
        ActionEntry("待审批", Icons.Outlined.TaskAlt),
        ActionEntry("审批记录", Icons.Outlined.Description),
    )

    PageWithActionGroup(
        innerPadding = innerPadding,
        title = "审批",
        entries = approvalEntries,
        onEntryClick = onPendingFeatureClick,
    )
}

@Composable
private fun ScanScreen(innerPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(132.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "扫一扫入口先固定在这里",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "后面接真实扫码能力时，直接在这个页面继续扩展即可。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PageWithActionGroup(
    innerPadding: PaddingValues,
    title: String,
    entries: List<ActionEntry>,
    onEntryClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        ActionGroup(
            title = title,
            entries = entries,
            onEntryClick = onEntryClick,
        )
    }
}

@Composable
private fun SyncCenterPanel(
    enabled: Boolean,
    onToggleSync: () -> Unit,
) {
    val panelBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
            MaterialTheme.colorScheme.surface,
        ),
    )

    Surface(
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(panelBrush)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = if (enabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shadowElevation = 18.dp,
                modifier = Modifier
                    .size(230.dp)
                    .clickable(onClick = onToggleSync),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Image(
                        painter = painterResource(
                            id = if (enabled) {
                                R.drawable.sync_toggle_on
                            } else {
                                R.drawable.sync_toggle_off
                            },
                        ),
                        contentDescription = if (enabled) "同步已开启" else "同步已关闭",
                        modifier = Modifier.size(164.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = if (enabled) "同步已开启" else "同步已关闭",
                style = MaterialTheme.typography.headlineMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (enabled) {
                    "再次点击中间圆形按钮即可关闭同步。"
                } else {
                    "点击中间圆形按钮即可开启同步。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ShortcutSection(
    title: String,
    entries: List<HomeShortcut>,
    onEntryClick: (HomeShortcut) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(16.dp))

            entries.chunked(2).forEachIndexed { rowIndex, rowEntries ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowEntries.forEach { entry ->
                        ShortcutTile(
                            entry = entry,
                            modifier = Modifier.weight(1f),
                            onClick = { onEntryClick(entry) },
                        )
                    }

                    if (rowEntries.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                if (rowIndex != entries.chunked(2).lastIndex) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun ShortcutTile(
    entry: HomeShortcut,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ActionGroup(
    title: String,
    entries: List<ActionEntry>,
    onEntryClick: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            )

            entries.forEachIndexed { index, entry ->
                ActionRow(
                    entry = entry,
                    onClick = { onEntryClick(entry.title) },
                )

                if (index != entries.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    entry: ActionEntry,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = entry.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    entries: List<SettingEntry>,
    onEntryClick: (SettingEntry) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )

        Surface(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                entries.forEachIndexed { index, entry ->
                    SettingsRow(
                        title = entry.title,
                        onClick = { onEntryClick(entry) },
                    )

                    if (index != entries.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                            modifier = Modifier.padding(horizontal = 18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
