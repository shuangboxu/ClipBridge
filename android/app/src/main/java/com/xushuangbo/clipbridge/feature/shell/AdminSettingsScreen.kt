package com.xushuangbo.clipbridge.feature.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xushuangbo.clipbridge.ui.components.PageErrorBanner

@Composable
fun AdminSettingsScreenRoute(
    innerPadding: PaddingValues,
    viewModel: AdminSettingsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.ensureLoaded()
    }

    AdminSettingsScreen(
        innerPadding = innerPadding,
        uiState = uiState,
        onMaxUserCountChange = viewModel::updateMaxUserCountDraft,
        onDefaultStorageQuotaMbChange = viewModel::updateDefaultStorageQuotaMbDraft,
        onDefaultUploadBandwidthChange = viewModel::updateDefaultUploadBandwidthDraft,
        onDefaultDownloadBandwidthChange = viewModel::updateDefaultDownloadBandwidthDraft,
        onMaxUserUploadBandwidthChange = viewModel::updateMaxUserUploadBandwidthDraft,
        onMaxUserDownloadBandwidthChange = viewModel::updateMaxUserDownloadBandwidthDraft,
        onMaxUploadFileMbChange = viewModel::updateMaxUploadFileMbDraft,
        onAllowRegistrationChange = viewModel::updateAllowRegistration,
        onSave = viewModel::saveSettings,
        onRefresh = viewModel::refresh,
    )
}

@Composable
fun AdminSettingsScreen(
    innerPadding: PaddingValues,
    uiState: AdminSettingsUiState,
    onMaxUserCountChange: (String) -> Unit,
    onDefaultStorageQuotaMbChange: (String) -> Unit,
    onDefaultUploadBandwidthChange: (String) -> Unit,
    onDefaultDownloadBandwidthChange: (String) -> Unit,
    onMaxUserUploadBandwidthChange: (String) -> Unit,
    onMaxUserDownloadBandwidthChange: (String) -> Unit,
    onMaxUploadFileMbChange: (String) -> Unit,
    onAllowRegistrationChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!uiState.errorMessage.isNullOrBlank()) {
            item {
                PageErrorBanner(message = uiState.errorMessage)
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "系统设置",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(
                                text = "这里的默认值会影响新账号和系统限制，修改后会立即生效。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = onRefresh, enabled = !uiState.isLoading) {
                            Text(if (uiState.isLoading) "刷新中..." else "刷新")
                        }
                    }

                    Spacer(modifier = Modifier.size(14.dp))
                    SummaryRow("当前用户数", uiState.currentUserCount.toString())
                    SummaryRow("上次更新时间", formatShellLocalDateTime(uiState.updatedAt))
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "可编辑项",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.size(12.dp))

                    AdminNumberField(
                        label = "最大用户数",
                        value = uiState.maxUserCountDraft,
                        onValueChange = onMaxUserCountChange,
                    )
                    AdminNumberField(
                        label = "默认存储配额（MB）",
                        value = uiState.defaultStorageQuotaMbDraft,
                        onValueChange = onDefaultStorageQuotaMbChange,
                    )
                    AdminNumberField(
                        label = "默认上传带宽（MB/s）",
                        value = uiState.defaultUploadBandwidthKbpsDraft,
                        onValueChange = onDefaultUploadBandwidthChange,
                        keyboardType = KeyboardType.Decimal,
                    )
                    AdminNumberField(
                        label = "默认下载带宽（MB/s）",
                        value = uiState.defaultDownloadBandwidthKbpsDraft,
                        onValueChange = onDefaultDownloadBandwidthChange,
                        keyboardType = KeyboardType.Decimal,
                    )
                    AdminNumberField(
                        label = "用户上传上限（MB/s）",
                        value = uiState.maxUserUploadBandwidthKbpsDraft,
                        onValueChange = onMaxUserUploadBandwidthChange,
                        keyboardType = KeyboardType.Decimal,
                    )
                    AdminNumberField(
                        label = "用户下载上限（MB/s）",
                        value = uiState.maxUserDownloadBandwidthKbpsDraft,
                        onValueChange = onMaxUserDownloadBandwidthChange,
                        keyboardType = KeyboardType.Decimal,
                    )
                    AdminNumberField(
                        label = "单文件上传上限（MB）",
                        value = uiState.maxUploadFileMbDraft,
                        onValueChange = onMaxUploadFileMbChange,
                    )

                    Spacer(modifier = Modifier.size(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = uiState.allowRegistration,
                            onCheckedChange = onAllowRegistrationChange,
                        )
                        Text(text = "允许公开注册")
                    }

                    Spacer(modifier = Modifier.size(12.dp))

                    Button(
                        onClick = onSave,
                        enabled = !uiState.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (uiState.isSaving) "保存中..." else "保存设置")
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Number,
) {
    ShellFormTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    )
}
