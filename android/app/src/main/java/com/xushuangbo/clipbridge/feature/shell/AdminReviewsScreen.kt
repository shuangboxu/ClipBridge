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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import com.xushuangbo.clipbridge.core.network.AdminPrivilegeRequestRecord
import com.xushuangbo.clipbridge.core.network.BandwidthRequestRecord
import com.xushuangbo.clipbridge.core.network.QuotaRequestRecord
import com.xushuangbo.clipbridge.ui.components.PageErrorBanner

@Composable
fun ApprovalsScreenRoute(
    innerPadding: PaddingValues,
    viewModel: AdminReviewsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.ensureLoaded()
    }

    ApprovalsScreen(
        innerPadding = innerPadding,
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onOpenApproveQuotaDialog = viewModel::openApproveQuotaDialog,
        onOpenApproveBandwidthDialog = viewModel::openApproveBandwidthDialog,
        onOpenApproveAdminDialog = viewModel::openApproveAdminDialog,
        onOpenRejectDialog = viewModel::openRejectDialog,
        onDismissDialog = viewModel::dismissDialog,
        onApprovedQuotaMbDraftChange = viewModel::updateApprovedQuotaMbDraft,
        onApprovedUploadDraftChange = viewModel::updateApprovedUploadKbpsDraft,
        onApprovedDownloadDraftChange = viewModel::updateApprovedDownloadKbpsDraft,
        onReviewNoteDraftChange = viewModel::updateReviewNoteDraft,
        onSubmitDialog = viewModel::submitDialog,
    )
}

@Composable
fun ApprovalsScreen(
    innerPadding: PaddingValues,
    uiState: AdminReviewsUiState,
    onRefresh: () -> Unit,
    onOpenApproveQuotaDialog: (String) -> Unit,
    onOpenApproveBandwidthDialog: (String) -> Unit,
    onOpenApproveAdminDialog: (String) -> Unit,
    onOpenRejectDialog: (AdminReviewType, String) -> Unit,
    onDismissDialog: () -> Unit,
    onApprovedQuotaMbDraftChange: (String) -> Unit,
    onApprovedUploadDraftChange: (String) -> Unit,
    onApprovedDownloadDraftChange: (String) -> Unit,
    onReviewNoteDraftChange: (String) -> Unit,
    onSubmitDialog: () -> Unit,
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
                                text = "待审批队列",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(
                                text = "这里只展示待处理申请，批准或拒绝后会自动从当前列表移除。",
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

        item {
            ReviewSectionCard(title = "存储配额申请") {
                if (uiState.quotaRequests.isEmpty()) {
                    EmptyRequestHint(message = "当前没有待审批的配额申请")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        uiState.quotaRequests.forEach { request ->
                            PendingQuotaReviewCard(
                                request = request,
                                onApprove = { onOpenApproveQuotaDialog(request.id) },
                                onReject = { onOpenRejectDialog(AdminReviewType.Quota, request.id) },
                            )
                        }
                    }
                }
            }
        }

        item {
            ReviewSectionCard(title = "带宽申请") {
                if (uiState.bandwidthRequests.isEmpty()) {
                    EmptyRequestHint(message = "当前没有待审批的带宽申请")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        uiState.bandwidthRequests.forEach { request ->
                            PendingBandwidthReviewCard(
                                request = request,
                                onApprove = { onOpenApproveBandwidthDialog(request.id) },
                                onReject = { onOpenRejectDialog(AdminReviewType.Bandwidth, request.id) },
                            )
                        }
                    }
                }
            }
        }

        item {
            ReviewSectionCard(title = "管理员申请") {
                if (uiState.adminRequests.isEmpty()) {
                    EmptyRequestHint(message = "当前没有待审批的管理员申请")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        uiState.adminRequests.forEach { request ->
                            PendingAdminReviewCard(
                                request = request,
                                onApprove = { onOpenApproveAdminDialog(request.id) },
                                onReject = { onOpenRejectDialog(AdminReviewType.Admin, request.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.dialogMode != null && uiState.selectedReviewType != null) {
        ApprovalDialog(
            uiState = uiState,
            onDismiss = onDismissDialog,
            onApprovedQuotaMbDraftChange = onApprovedQuotaMbDraftChange,
            onApprovedUploadDraftChange = onApprovedUploadDraftChange,
            onApprovedDownloadDraftChange = onApprovedDownloadDraftChange,
            onReviewNoteDraftChange = onReviewNoteDraftChange,
            onSubmit = onSubmitDialog,
        )
    }
}

@Composable
private fun ReviewSectionCard(
    title: String,
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
            content()
        }
    }
}

@Composable
private fun PendingQuotaReviewCard(
    request: QuotaRequestRecord,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    PendingReviewCard(
        title = request.username.ifBlank { request.userId },
        detailLines = listOf(
            "当前配额：${formatShellBytes(request.currentQuotaBytes)}",
            "申请配额：${formatShellBytes(request.requestedQuotaBytes)}",
            "申请时间：${formatShellLocalDateTime(request.createdAt)}",
            "申请说明：${request.reason.ifBlank { "-" }}",
        ),
        onApprove = onApprove,
        onReject = onReject,
    )
}

@Composable
private fun PendingBandwidthReviewCard(
    request: BandwidthRequestRecord,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    PendingReviewCard(
        title = request.username.ifBlank { request.userId },
        detailLines = listOf(
            "当前上传：${formatShellBandwidth(request.currentUploadKbps)}",
            "目标上传：${formatShellBandwidth(request.requestedUploadKbps)}",
            "当前下载：${formatShellBandwidth(request.currentDownloadKbps)}",
            "目标下载：${formatShellBandwidth(request.requestedDownloadKbps)}",
            "申请时间：${formatShellLocalDateTime(request.createdAt)}",
            "申请说明：${request.reason.ifBlank { "-" }}",
        ),
        onApprove = onApprove,
        onReject = onReject,
    )
}

@Composable
private fun PendingAdminReviewCard(
    request: AdminPrivilegeRequestRecord,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    PendingReviewCard(
        title = request.username.ifBlank { request.userId },
        detailLines = listOf(
            "申请时间：${formatShellLocalDateTime(request.createdAt)}",
            "申请说明：${request.reason.ifBlank { "-" }}",
        ),
        onApprove = onApprove,
        onReject = onReject,
    )
}

@Composable
private fun PendingReviewCard(
    title: String,
    detailLines: List<String>,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.size(8.dp))
            detailLines.forEach { line ->
                Text(
                    text = line,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.size(4.dp))
            }
            Spacer(modifier = Modifier.size(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("批准")
                }
                Button(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("拒绝")
                }
            }
        }
    }
}

@Composable
private fun ApprovalDialog(
    uiState: AdminReviewsUiState,
    onDismiss: () -> Unit,
    onApprovedQuotaMbDraftChange: (String) -> Unit,
    onApprovedUploadDraftChange: (String) -> Unit,
    onApprovedDownloadDraftChange: (String) -> Unit,
    onReviewNoteDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val reviewType = uiState.selectedReviewType ?: return
    val dialogMode = uiState.dialogMode ?: return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    dialogMode == AdminReviewDialogMode.Reject -> "拒绝申请"
                    reviewType == AdminReviewType.Quota -> "批准配额申请"
                    reviewType == AdminReviewType.Bandwidth -> "批准带宽申请"
                    else -> "批准管理员申请"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (dialogMode == AdminReviewDialogMode.Approve && reviewType == AdminReviewType.Quota) {
                    ShellFormTextField(
                        value = uiState.approvedQuotaMbDraft,
                        onValueChange = onApprovedQuotaMbDraftChange,
                        label = { Text("批准配额（MB，可调整）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (dialogMode == AdminReviewDialogMode.Approve && reviewType == AdminReviewType.Bandwidth) {
                    ShellFormTextField(
                        value = uiState.approvedUploadKbpsDraft,
                        onValueChange = onApprovedUploadDraftChange,
                        label = { Text("批准上传（MB/s，可调整）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ShellFormTextField(
                        value = uiState.approvedDownloadKbpsDraft,
                        onValueChange = onApprovedDownloadDraftChange,
                        label = { Text("批准下载（MB/s，可调整）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                ShellFormTextField(
                    value = uiState.reviewNoteDraft,
                    onValueChange = onReviewNoteDraftChange,
                    label = { Text("审核备注") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = !uiState.isSubmitting,
            ) {
                Text(
                    when {
                        uiState.isSubmitting -> "处理中..."
                        dialogMode == AdminReviewDialogMode.Approve -> "确认批准"
                        else -> "确认拒绝"
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
