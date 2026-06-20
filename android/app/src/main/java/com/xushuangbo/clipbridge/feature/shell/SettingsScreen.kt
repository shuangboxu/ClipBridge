package com.xushuangbo.clipbridge.feature.shell

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.xushuangbo.clipbridge.ui.components.PageErrorBanner

@Composable
internal fun SettingsScreen(
    innerPadding: PaddingValues,
    uiState: SettingsUiState,
    onOpenDetailPage: (DetailPage) -> Unit,
    onRefreshSessionSnapshot: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) {
        onRefreshSessionSnapshot()
    }

    val accountEntries = listOf(
        SettingEntry("账号信息", DetailPage.AccountInfo),
        SettingEntry("安全", DetailPage.Security),
        SettingEntry("设备", DetailPage.Device),
        SettingEntry("申请", DetailPage.Requests),
    )
    // 文件页和分享页已经能从主页进入，设置页只保留需要配置的分享规则入口。
    val commonEntries = listOf(
        SettingEntry("分享规则", DetailPage.ShareRules),
        SettingEntry("历史设置", DetailPage.HistorySettings),
    )
    val managementEntries = if (uiState.isAdmin) {
        listOf(
            SettingEntry("全局设置", DetailPage.GlobalSettings),
            SettingEntry("用户管理", DetailPage.UserManagement),
            SettingEntry("审批", DetailPage.Approvals),
        )
    } else {
        emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        SettingsSection(title = "账号", entries = accountEntries, onEntryClick = { entry -> onOpenDetailPage(entry.detailPage) })
        Spacer(modifier = Modifier.height(22.dp))
        SettingsSection(title = "通用", entries = commonEntries, onEntryClick = { entry -> onOpenDetailPage(entry.detailPage) })
        Spacer(modifier = Modifier.height(22.dp))
        if (managementEntries.isNotEmpty()) {
            SettingsSection(title = "管理", entries = managementEntries, onEntryClick = { entry -> onOpenDetailPage(entry.detailPage) })
            Spacer(modifier = Modifier.height(22.dp))
        }
        AboutSection(
            onOpenGithub = {
                runCatching { uriHandler.openUri("https://github.com/shuangboxu/ClipBridge") }
                    .onFailure {
                        Toast.makeText(context, "无法打开 GitHub 仓库", Toast.LENGTH_SHORT).show()
                    }
            },
        )
        Spacer(modifier = Modifier.height(22.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Icon(imageVector = Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("退出登录")
        }
    }
}

@Composable
internal fun AccountInfoScreen(
    innerPadding: PaddingValues,
    uiState: SettingsUiState,
    onServiceAddressChange: (String) -> Unit,
    onSaveServiceAddress: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        uiState.errorMessage?.let { errorMessage ->
            PageErrorBanner(message = errorMessage)
            Spacer(modifier = Modifier.height(18.dp))
        }

        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                InfoRow("用户名", uiState.username.ifBlank { "未读取到账号信息" })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                InfoRow("设备名称", uiState.deviceName.ifBlank { "android-phone" })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                InfoRow("账号角色", if (uiState.isAdmin) "管理员" else "普通用户")
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                InfoRow("存储配额", formatShellBytes(uiState.storageQuotaBytes))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                InfoRow("上传带宽", formatShellBandwidth(uiState.uploadBandwidthKbps))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                InfoRow("下载带宽", formatShellBandwidth(uiState.downloadBandwidthKbps))
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
                Text(text = "服务器地址", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                ShellFormTextField(
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
internal fun SecurityScreen(
    innerPadding: PaddingValues,
    uiState: SettingsUiState,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmNewPasswordChange: (String) -> Unit,
    onChangePassword: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        uiState.errorMessage?.let { errorMessage ->
            PageErrorBanner(message = errorMessage)
            Spacer(modifier = Modifier.height(18.dp))
        }

        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
                Text(text = "修改密码", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                ShellFormTextField(
                    value = uiState.currentPassword,
                    onValueChange = onCurrentPasswordChange,
                    singleLine = true,
                    label = { Text("当前密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                ShellFormTextField(
                    value = uiState.newPassword,
                    onValueChange = onNewPasswordChange,
                    singleLine = true,
                    label = { Text("新密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                ShellFormTextField(
                    value = uiState.confirmNewPassword,
                    onValueChange = onConfirmNewPasswordChange,
                    singleLine = true,
                    label = { Text("确认新密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onChangePassword,
                    enabled = !uiState.isChangingPassword,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (uiState.isChangingPassword) "修改中..." else "更新密码")
                }
            }
        }
    }
}

@Composable
private fun AboutSection(
    onOpenGithub: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "关于",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )

        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                AboutRow(
                    title = "GitHub 仓库",
                    value = "github.com/shuangboxu/ClipBridge",
                    onClick = onOpenGithub,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
                AboutRow(title = "Windows 下载", value = "即将开放")
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
                AboutRow(title = "Android 下载", value = "即将开放")
            }
        }
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

        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                entries.forEachIndexed { index, entry ->
                    SettingsRow(title = entry.title, onClick = { onEntryClick(entry) })
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
private fun AboutRow(
    title: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
