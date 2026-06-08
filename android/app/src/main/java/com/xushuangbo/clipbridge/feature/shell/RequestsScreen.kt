package com.xushuangbo.clipbridge.feature.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.text.KeyboardOptions
import com.xushuangbo.clipbridge.app.AppTestTags
import com.xushuangbo.clipbridge.core.network.AdminPrivilegeRequestRecord
import com.xushuangbo.clipbridge.core.network.BandwidthRequestRecord
import com.xushuangbo.clipbridge.core.network.QuotaRequestRecord
import com.xushuangbo.clipbridge.ui.components.PageErrorBanner

@Composable
fun RequestsScreenRoute(
    innerPadding: PaddingValues,
    viewModel: RequestsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.ensureLoaded()
    }

    RequestsScreen(
        innerPadding = innerPadding,
        uiState = uiState,
        onQuotaDraftMbChange = viewModel::updateQuotaDraftMb,
        onQuotaReasonChange = viewModel::updateQuotaReasonDraft,
        onBandwidthUploadChange = viewModel::updateBandwidthUploadDraft,
        onBandwidthDownloadChange = viewModel::updateBandwidthDownloadDraft,
        onBandwidthReasonChange = viewModel::updateBandwidthReasonDraft,
        onAdminReasonChange = viewModel::updateAdminReasonDraft,
        onSubmitQuotaRequest = viewModel::submitQuotaRequest,
        onSubmitBandwidthRequest = viewModel::submitBandwidthRequest,
        onSubmitAdminRequest = viewModel::submitAdminRequest,
        onRefresh = viewModel::refresh,
    )
}

@Composable
fun RequestsScreen(
    innerPadding: PaddingValues,
    uiState: RequestsUiState,
    onQuotaDraftMbChange: (String) -> Unit,
    onQuotaReasonChange: (String) -> Unit,
    onBandwidthUploadChange: (String) -> Unit,
    onBandwidthDownloadChange: (String) -> Unit,
    onBandwidthReasonChange: (String) -> Unit,
    onAdminReasonChange: (String) -> Unit,
    onSubmitQuotaRequest: () -> Unit,
    onSubmitBandwidthRequest: () -> Unit,
    onSubmitAdminRequest: () -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 18.dp)
            .testTag(AppTestTags.RequestsScreen),
        contentPadding = PaddingValues(vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!uiState.errorMessage.isNullOrBlank()) {
            item {
                PageErrorBanner(message = uiState.errorMessage)
            }
        }

        item {
            RequestOverviewCard(
                uiState = uiState,
                onRefresh = onRefresh,
            )
        }

        item {
            RequestFormCard(
                title = "存储配额申请",
                description = "输入希望提升到的目标配额，单位是 MB。",
            ) {
                OutlinedTextField(
                    value = uiState.quotaDraftMb,
                    onValueChange = onQuotaDraftMbChange,
                    label = { Text("目标配额（MB）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.size(12.dp))
                OutlinedTextField(
                    value = uiState.quotaReasonDraft,
                    onValueChange = onQuotaReasonChange,
                    label = { Text("申请说明") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.size(12.dp))
                Button(
                    onClick = onSubmitQuotaRequest,
                    enabled = !uiState.isSubmittingQuota,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (uiState.isSubmittingQuota) "提交中..." else "提交配额申请")
                }
            }
        }

        item {
            RequestFormCard(
                title = "带宽申请",
                description = "上传和下载都按 Kbps 填写，至少填一个高于当前值的目标更合理。",
            ) {
                OutlinedTextField(
                    value = uiState.bandwidthUploadDraft,
                    onValueChange = onBandwidthUploadChange,
                    label = { Text("上传带宽（Kbps）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.size(12.dp))
                OutlinedTextField(
                    value = uiState.bandwidthDownloadDraft,
                    onValueChange = onBandwidthDownloadChange,
                    label = { Text("下载带宽（Kbps）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.size(12.dp))
                OutlinedTextField(
                    value = uiState.bandwidthReasonDraft,
                    onValueChange = onBandwidthReasonChange,
                    label = { Text("申请说明") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.size(12.dp))
                Button(
                    onClick = onSubmitBandwidthRequest,
                    enabled = !uiState.isSubmittingBandwidth,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (uiState.isSubmittingBandwidth) "提交中..." else "提交带宽申请")
                }
            }
        }

        item {
            RequestFormCard(
                title = "管理员申请",
                description = "说明为什么需要管理员权限，以及主要会做哪些管理操作。",
            ) {
                OutlinedTextField(
                    value = uiState.adminReasonDraft,
                    onValueChange = onAdminReasonChange,
                    label = { Text("申请说明") },
                    minLines = 4,
                    enabled = !uiState.isAdmin,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.size(12.dp))
                Button(
                    onClick = onSubmitAdminRequest,
                    enabled = !uiState.isAdmin && !uiState.isSubmittingAdmin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(AppTestTags.RequestsAdminSubmitButton),
                ) {
                    Text(
                        when {
                            uiState.isAdmin -> "当前账号已是管理员"
                            uiState.isSubmittingAdmin -> "提交中..."
                            else -> "提交管理员申请"
                        },
                    )
                }
            }
        }

        item {
            RequestRecordSectionCard(
                title = "存储配额申请记录",
                emptyMessage = "还没有提交过配额申请",
            ) {
                if (uiState.quotaRequests.isEmpty()) {
                    EmptyRequestHint(message = "还没有提交过配额申请")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        uiState.quotaRequests.forEach { request ->
                            QuotaRequestRecordCard(request = request)
                        }
                    }
                }
            }
        }

        item {
            RequestRecordSectionCard(
                title = "带宽申请记录",
                emptyMessage = "还没有提交过带宽申请",
            ) {
                if (uiState.bandwidthRequests.isEmpty()) {
                    EmptyRequestHint(message = "还没有提交过带宽申请")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        uiState.bandwidthRequests.forEach { request ->
                            BandwidthRequestRecordCard(request = request)
                        }
                    }
                }
            }
        }

        item {
            RequestRecordSectionCard(
                title = "管理员申请记录",
                emptyMessage = "还没有提交过管理员申请",
            ) {
                if (uiState.adminRequests.isEmpty()) {
                    EmptyRequestHint(message = "还没有提交过管理员申请")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        uiState.adminRequests.forEach { request ->
                            AdminRequestRecordCard(request = request)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestOverviewCard(
    uiState: RequestsUiState,
    onRefresh: () -> Unit,
) {
    val stats = listOf(
        "角色" to if (uiState.isAdmin) "管理员" else "普通用户",
        "存储配额" to formatShellBytes(uiState.storageQuotaBytes),
        "已用空间" to formatShellBytes(uiState.storageUsedBytes),
        "剩余空间" to formatShellBytes(uiState.storageFreeBytes),
        "上传带宽" to formatShellKbps(uiState.uploadBandwidthKbps),
        "下载带宽" to formatShellKbps(uiState.downloadBandwidthKbps),
        "单文件上限" to formatShellBytes(uiState.maxUploadFileBytes),
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "当前额度",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = "申请提交后会进入管理员审核队列，审核通过后会立即生效。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onRefresh, enabled = !uiState.isLoading) {
                    Text(if (uiState.isLoading) "刷新中..." else "刷新")
                }
            }

            Spacer(modifier = Modifier.size(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                stats.forEach { entry ->
                    SummaryRow(label = entry.first, value = entry.second)
                }
            }
        }
    }
}

@Composable
private fun RequestFormCard(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.size(12.dp))
            content()
        }
    }
}

@Composable
private fun RequestRecordSectionCard(
    title: String,
    emptyMessage: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.size(10.dp))
            if (emptyMessage.isNotBlank()) {
                content()
            }
        }
    }
}

@Composable
internal fun EmptyRequestHint(message: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuotaRequestRecordCard(request: QuotaRequestRecord) {
    RequestRecordCard(
        title = request.username.ifBlank { "未命名用户" },
        status = request.status,
    ) {
        SummaryRow("当前配额", formatShellBytes(request.currentQuotaBytes))
        SummaryRow("申请配额", formatShellBytes(request.requestedQuotaBytes))
        RequestNoteRow("申请说明", request.reason)
        SummaryRow("审核人", request.reviewedByUsername.ifBlank { "-" })
        RequestNoteRow("审核备注", request.reviewNote)
        SummaryRow("提交时间", formatShellLocalDateTime(request.createdAt))
        SummaryRow("审核时间", formatShellLocalDateTime(request.reviewedAt))
    }
}

@Composable
private fun BandwidthRequestRecordCard(request: BandwidthRequestRecord) {
    RequestRecordCard(
        title = request.username.ifBlank { "未命名用户" },
        status = request.status,
    ) {
        SummaryRow("当前上传", formatShellKbps(request.currentUploadKbps))
        SummaryRow("目标上传", formatShellKbps(request.requestedUploadKbps))
        SummaryRow("当前下载", formatShellKbps(request.currentDownloadKbps))
        SummaryRow("目标下载", formatShellKbps(request.requestedDownloadKbps))
        RequestNoteRow("申请说明", request.reason)
        SummaryRow("审核人", request.reviewedByUsername.ifBlank { "-" })
        RequestNoteRow("审核备注", request.reviewNote)
        SummaryRow("提交时间", formatShellLocalDateTime(request.createdAt))
        SummaryRow("审核时间", formatShellLocalDateTime(request.reviewedAt))
    }
}

@Composable
private fun AdminRequestRecordCard(request: AdminPrivilegeRequestRecord) {
    RequestRecordCard(
        title = request.username.ifBlank { "未命名用户" },
        status = request.status,
    ) {
        RequestNoteRow("申请说明", request.reason)
        SummaryRow("审核人", request.reviewedByUsername.ifBlank { "-" })
        RequestNoteRow("审核备注", request.reviewNote)
        SummaryRow("提交时间", formatShellLocalDateTime(request.createdAt))
        SummaryRow("审核时间", formatShellLocalDateTime(request.reviewedAt))
    }
}

@Composable
private fun RequestRecordCard(
    title: String,
    status: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                StatusChip(status = status)
            }
            Spacer(modifier = Modifier.size(10.dp))
            content()
        }
    }
}

@Composable
internal fun SummaryRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = value.ifBlank { "-" },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RequestNoteRow(
    label: String,
    value: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(text = value.ifBlank { "-" })
    }
    Spacer(modifier = Modifier.size(8.dp))
}

@Composable
internal fun StatusChip(status: String) {
    val normalizedStatus = status.trim().lowercase()
    val backgroundColor = when (normalizedStatus) {
        "approved" -> MaterialTheme.colorScheme.primaryContainer
        "rejected" -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val text = when (normalizedStatus) {
        "approved" -> "已批准"
        "rejected" -> "已拒绝"
        else -> "待处理"
    }

    Surface(
        shape = CircleShape,
        color = backgroundColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
