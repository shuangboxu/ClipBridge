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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.xushuangbo.clipbridge.core.network.AdminUserRecord
import com.xushuangbo.clipbridge.ui.components.PageErrorBanner

@Composable
fun UserManagementScreenRoute(
    innerPadding: PaddingValues,
    viewModel: AdminUsersViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.ensureLoaded()
    }

    val selectedUser = uiState.users.find { it.id == uiState.selectedUserId }

    UserManagementScreen(
        innerPadding = innerPadding,
        uiState = uiState,
        onRefresh = viewModel::refreshUsers,
        onOpenEditDialog = viewModel::openEditDialog,
        onOpenDeleteDialog = viewModel::openDeleteDialog,
        onDismissDialog = viewModel::dismissDialog,
        onStorageQuotaDraftChange = viewModel::updateStorageQuotaMbDraft,
        onUploadBandwidthDraftChange = viewModel::updateUploadBandwidthDraft,
        onDownloadBandwidthDraftChange = viewModel::updateDownloadBandwidthDraft,
        onIsAdminDraftChange = viewModel::updateIsAdminDraft,
        onSaveUser = viewModel::saveSelectedUser,
        onDeleteUser = viewModel::deleteSelectedUser,
        selectedUser = selectedUser,
    )
}

@Composable
fun UserManagementScreen(
    innerPadding: PaddingValues,
    uiState: AdminUsersUiState,
    onRefresh: () -> Unit,
    onOpenEditDialog: (String) -> Unit,
    onOpenDeleteDialog: (String) -> Unit,
    onDismissDialog: () -> Unit,
    onStorageQuotaDraftChange: (String) -> Unit,
    onUploadBandwidthDraftChange: (String) -> Unit,
    onDownloadBandwidthDraftChange: (String) -> Unit,
    onIsAdminDraftChange: (Boolean) -> Unit,
    onSaveUser: () -> Unit,
    onDeleteUser: () -> Unit,
    selectedUser: AdminUserRecord?,
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
                                text = "用户管理",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(
                                text = "这里可以调整用户额度、带宽和管理员权限，也可以删除账号。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = onRefresh, enabled = !uiState.isLoading) {
                            Text(if (uiState.isLoading) "刷新中..." else "刷新")
                        }
                    }
                }
            }
        }

        if (uiState.users.isEmpty() && !uiState.isLoading) {
            item {
                EmptyRequestHint(message = "当前没有可管理的用户记录")
            }
        } else {
            items(
                items = uiState.users,
                key = { user -> user.id },
            ) { user ->
                AdminUserCard(
                    user = user,
                    onOpenEditDialog = { onOpenEditDialog(user.id) },
                    onOpenDeleteDialog = { onOpenDeleteDialog(user.id) },
                )
            }
        }
    }

    if (uiState.dialogMode == AdminUserDialogMode.Edit && selectedUser != null) {
        AlertDialog(
            onDismissRequest = onDismissDialog,
            title = { Text("编辑用户") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = selectedUser.username.ifBlank { selectedUser.id },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = uiState.storageQuotaMbDraft,
                        onValueChange = onStorageQuotaDraftChange,
                        label = { Text("存储配额（MB）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = uiState.uploadBandwidthKbpsDraft,
                        onValueChange = onUploadBandwidthDraftChange,
                        label = { Text("上传带宽（Kbps）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = uiState.downloadBandwidthKbpsDraft,
                        onValueChange = onDownloadBandwidthDraftChange,
                        label = { Text("下载带宽（Kbps）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = uiState.isAdminDraft,
                            onCheckedChange = onIsAdminDraftChange,
                        )
                        Text("设为管理员")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onSaveUser,
                    enabled = !uiState.isSaving,
                ) {
                    Text(if (uiState.isSaving) "保存中..." else "保存")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDialog) {
                    Text("取消")
                }
            },
        )
    }

    if (uiState.dialogMode == AdminUserDialogMode.Delete && selectedUser != null) {
        AlertDialog(
            onDismissRequest = onDismissDialog,
            title = { Text("删除用户") },
            text = {
                Text("确认删除用户“${selectedUser.username.ifBlank { selectedUser.id }}”吗？该用户的文件、分享和申请记录也会一起清理。")
            },
            confirmButton = {
                Button(
                    onClick = onDeleteUser,
                    enabled = !uiState.isDeleting,
                ) {
                    Text(if (uiState.isDeleting) "删除中..." else "确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDialog) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun AdminUserCard(
    user: AdminUserRecord,
    onOpenEditDialog: () -> Unit,
    onOpenDeleteDialog: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.username.ifBlank { user.id },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = if (user.isAdmin) "管理员" else "普通用户",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = if (user.isAdmin) "管理员" else "普通用户",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.size(12.dp))
            SummaryRow("存储配额", formatShellBytes(user.storageQuotaBytes))
            SummaryRow("已用空间", formatShellBytes(user.storageUsedBytes))
            SummaryRow("剩余空间", formatShellBytes(user.storageFreeBytes))
            SummaryRow("上传带宽", formatShellKbps(user.uploadBandwidthKbps))
            SummaryRow("下载带宽", formatShellKbps(user.downloadBandwidthKbps))
            SummaryRow("最近活跃", formatShellLocalDateTime(user.lastActiveAt))

            val pendingTags = buildList {
                if (user.hasPendingQuotaRequest) add("配额申请")
                if (user.hasPendingBandwidthRequest) add("带宽申请")
                if (user.hasPendingAdminRequest) add("管理员申请")
            }
            if (pendingTags.isNotEmpty()) {
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = "待处理申请：${pendingTags.joinToString("、")}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.size(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onOpenEditDialog,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("编辑")
                }
                Button(
                    onClick = onOpenDeleteDialog,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("删除")
                }
            }
        }
    }
}
