package com.xushuangbo.clipbridge.feature.shell

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.xushuangbo.clipbridge.core.network.DeviceRecord
import com.xushuangbo.clipbridge.ui.components.PageErrorBanner
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DeviceScreenRoute(
    innerPadding: PaddingValues,
    currentDeviceId: String,
    uiState: DeviceUiState,
    onLoadDevices: () -> Unit,
    onRefreshDevices: () -> Unit,
    onOpenDeviceDetails: (String) -> Unit,
    onOpenDeviceEditor: (String) -> Unit,
    onDismissDialog: () -> Unit,
    onRenameDraftChange: (String) -> Unit,
    onSaveDeviceName: () -> Unit,
    onForceOfflineDevice: () -> Unit,
) {
    val selectedDevice = remember(uiState.devices, uiState.selectedDeviceId) {
        uiState.devices.find { device -> device.id == uiState.selectedDeviceId }
    }

    LaunchedEffect(Unit) {
        onLoadDevices()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = innerPadding.calculateTopPadding() + 18.dp,
            bottom = innerPadding.calculateBottomPadding() + 18.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        val errorMessage = uiState.errorMessage
        if (!errorMessage.isNullOrBlank()) {
            item {
                PageErrorBanner(message = errorMessage)
            }
        }

        item {
            DeviceSummaryCard(summary = uiState.summary)
        }

        item {
            DeviceListCard(
                currentDeviceId = currentDeviceId,
                uiState = uiState,
                onRefreshDevices = onRefreshDevices,
                onOpenDeviceDetails = onOpenDeviceDetails,
                onOpenDeviceEditor = onOpenDeviceEditor,
            )
        }
    }

    when (uiState.dialogMode) {
        DeviceDialogMode.Details -> {
            if (selectedDevice != null) {
                DeviceDetailDialog(
                    device = selectedDevice,
                    onDismiss = onDismissDialog,
                )
            }
        }

        DeviceDialogMode.Edit -> {
            if (selectedDevice != null) {
                DeviceEditDialog(
                    device = selectedDevice,
                    draftName = uiState.renameDraft,
                    isSavingName = uiState.isSavingName,
                    isForcingOffline = uiState.isForcingOffline,
                    onDraftNameChange = onRenameDraftChange,
                    onDismiss = onDismissDialog,
                    onSave = onSaveDeviceName,
                    onForceOffline = onForceOfflineDevice,
                )
            }
        }

        null -> Unit
    }
}

@Composable
private fun DeviceSummaryCard(summary: DeviceSummary) {
    val summaryEntries = listOf(
        DeviceStatEntry("总设备", summary.total.toString()),
        DeviceStatEntry("在线", summary.online.toString()),
        DeviceStatEntry("最近活动", summary.latestLastSeenText),
    )

    Surface(
        shape = RoundedCornerShape(30.dp),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
        ) {
            Text(
                text = "设备概览",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                summaryEntries.forEach { entry ->
                    DeviceStatTile(
                        entry = entry,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceStatTile(
    entry: DeviceStatEntry,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = entry.value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DeviceListCard(
    currentDeviceId: String,
    uiState: DeviceUiState,
    onRefreshDevices: () -> Unit,
    onOpenDeviceDetails: (String) -> Unit,
    onOpenDeviceEditor: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(30.dp),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "设备列表",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(
                    onClick = onRefreshDevices,
                    enabled = !uiState.isLoadingDevices && !uiState.isSavingName && !uiState.isForcingOffline,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (uiState.isLoadingDevices) "正在刷新..." else "刷新")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "共 ${uiState.summary.total} 台设备",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(14.dp))

            when {
                uiState.isLoadingDevices && uiState.devices.isEmpty() -> {
                    LoadingDeviceState()
                }

                uiState.devices.isEmpty() -> {
                    EmptyDeviceState(onRefreshDevices = onRefreshDevices)
                }

                else -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        uiState.devices.forEach { device ->
                            DeviceListItem(
                                device = device,
                                currentDeviceId = currentDeviceId,
                                onOpenDetails = { onOpenDeviceDetails(device.id) },
                                onOpenEditor = { onOpenDeviceEditor(device.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingDeviceState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "正在加载设备列表",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyDeviceState(
    onRefreshDevices: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Devices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "暂无设备",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "当前账号下还没有可显示的设备记录。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRefreshDevices) {
            Text("刷新")
        }
    }
}

@Composable
private fun DeviceListItem(
    device: DeviceRecord,
    currentDeviceId: String,
    onOpenDetails: () -> Unit,
    onOpenEditor: () -> Unit,
) {
    val deviceRoleLabel = if (device.id == currentDeviceId) {
        "当前设备"
    } else {
        "已登记"
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = device.deviceName.ifBlank { "unnamed-device" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(isOnline = device.isActive)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (device.isActive) "在线" else "已下线",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = deviceRoleLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MiniIconAction(
                    icon = Icons.Outlined.Visibility,
                    contentDescription = "查看设备详情",
                    onClick = onOpenDetails,
                )
                MiniIconAction(
                    icon = Icons.Outlined.Edit,
                    contentDescription = "编辑设备",
                    onClick = onOpenEditor,
                )
            }
        }
    }
}

@Composable
private fun MiniIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DeviceDetailDialog(
    device: DeviceRecord,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = device.deviceName.ifBlank { "unnamed-device" },
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "设备详情",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DeviceInfoRow("设备 ID", device.id)
                DeviceInfoRow("平台", device.platform.ifBlank { "unknown" })
                DeviceInfoRow("状态", if (device.isActive) "在线" else "已下线")
                DeviceInfoRow("创建时间", formatDeviceTime(device.createdAt))
                DeviceInfoRow("最近在线", formatDeviceTime(device.lastSeenAt))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun DeviceEditDialog(
    device: DeviceRecord,
    draftName: String,
    isSavingName: Boolean,
    isForcingOffline: Boolean,
    onDraftNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onForceOffline: () -> Unit,
) {
    var showForceOfflineConfirm by rememberSaveable(device.id) { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                Text(
                    text = device.deviceName.ifBlank { "unnamed-device" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "编辑设备",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(18.dp))

                OutlinedTextField(
                    value = draftName,
                    onValueChange = onDraftNameChange,
                    singleLine = true,
                    label = { Text("设备名称") },
                    enabled = !isSavingName && !isForcingOffline,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(18.dp))

                DeviceInfoRow("设备 ID", device.id)
                Spacer(modifier = Modifier.height(10.dp))
                DeviceInfoRow("平台", device.platform.ifBlank { "unknown" })
                Spacer(modifier = Modifier.height(10.dp))
                DeviceInfoRow("状态", if (device.isActive) "在线" else "已下线")
                Spacer(modifier = Modifier.height(10.dp))
                DeviceInfoRow("最近在线", formatDeviceTime(device.lastSeenAt))

                Spacer(modifier = Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isSavingName && !isForcingOffline,
                    ) {
                        Text("取消")
                    }

                    TextButton(
                        onClick = { showForceOfflineConfirm = true },
                        enabled = !isSavingName && !isForcingOffline,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isForcingOffline) "正在下线..." else "强制下线",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = onSave,
                        enabled = !isSavingName && !isForcingOffline,
                    ) {
                        Text(if (isSavingName) "正在保存..." else "保存名称")
                    }
                }
            }
        }
    }

    if (showForceOfflineConfirm) {
        AlertDialog(
            onDismissRequest = { showForceOfflineConfirm = false },
            title = { Text("确认强制下线") },
            text = {
                Text("确认强制下线设备“${device.deviceName.ifBlank { "unnamed-device" }}”吗？")
            },
            dismissButton = {
                TextButton(onClick = { showForceOfflineConfirm = false }) {
                    Text("取消")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showForceOfflineConfirm = false
                        onForceOffline()
                    },
                    enabled = !isSavingName && !isForcingOffline,
                ) {
                    Text(
                        text = if (isForcingOffline) "正在下线..." else "确认下线",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
        )
    }
}

@Composable
private fun DeviceInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
        )
        Text(
            text = value.ifBlank { "-" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatusDot(isOnline: Boolean) {
    val color = if (isOnline) {
        Color(0xFF16AA7A)
    } else {
        Color(0xFFF5A524)
    }

    Box(
        modifier = Modifier
            .size(10.dp)
            .background(color = color, shape = CircleShape),
    )
}

private data class DeviceStatEntry(
    val label: String,
    val value: String,
)

private fun formatDeviceTime(value: String): String {
    if (value.isBlank()) {
        return "-"
    }

    return try {
        val dateTime = OffsetDateTime.parse(value)
            .atZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime()
        DEVICE_TIME_FORMATTER.format(dateTime)
    } catch (_: Exception) {
        value
    }
}

private val DEVICE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
