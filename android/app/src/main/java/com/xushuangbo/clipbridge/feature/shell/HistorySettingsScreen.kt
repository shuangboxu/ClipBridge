package com.xushuangbo.clipbridge.feature.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xushuangbo.clipbridge.app.AppTestTags
import com.xushuangbo.clipbridge.ui.components.PageErrorBanner
import android.widget.Toast

@Composable
fun HistorySettingsScreenRoute(
    innerPadding: PaddingValues,
    viewModel: HistorySettingsViewModel = viewModel(),
    onRequireAuth: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.ensureLoaded()
    }

    LaunchedEffect(viewModel) {
        viewModel.sessionExitEvents.collect { message ->
            onRequireAuth(message)
        }
    }

    LaunchedEffect(viewModel, context) {
        viewModel.toastEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    HistorySettingsScreen(
        innerPadding = innerPadding,
        uiState = uiState,
        onRetentionDaysChange = viewModel::updateRetentionDaysInput,
        onHistoryLimitChange = viewModel::updateHistoryLimitInput,
        onSave = viewModel::saveSettings,
        onClearHistory = viewModel::clearHistory,
        onCleanupOlderThanDays = viewModel::cleanupOlderThanDays,
    )
}

@Composable
private fun HistorySettingsScreen(
    innerPadding: PaddingValues,
    uiState: HistorySettingsUiState,
    onRetentionDaysChange: (String) -> Unit,
    onHistoryLimitChange: (String) -> Unit,
    onSave: () -> Unit,
    onClearHistory: () -> Unit,
    onCleanupOlderThanDays: () -> Unit,
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    var showCleanupConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .testTag(AppTestTags.HistorySettingsScreen),
    ) {
        uiState.errorMessage?.let { errorMessage ->
            PageErrorBanner(message = errorMessage)
            Spacer(modifier = Modifier.height(18.dp))
        }

        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
                Text(
                    text = "历史保留策略",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "保留天数填 0 表示不限时间，默认最多保留 1000 条。所有端共用同一套设置。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(14.dp))
                ShellFormTextField(
                    value = uiState.retentionDaysInput,
                    onValueChange = onRetentionDaysChange,
                    singleLine = true,
                    label = { Text("保留天数") },
                    placeholder = { Text("0 表示不限时间") },
                    enabled = !uiState.isSaving && !uiState.isClearing && !uiState.isCleaningUp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                ShellFormTextField(
                    value = uiState.historyLimitInput,
                    onValueChange = onHistoryLimitChange,
                    singleLine = true,
                    label = { Text("最大记录数") },
                    enabled = !uiState.isSaving && !uiState.isClearing && !uiState.isCleaningUp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onSave,
                    enabled = !uiState.isLoading && !uiState.isSaving && !uiState.isClearing && !uiState.isCleaningUp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (uiState.isSaving) "保存中..." else "保存设置")
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
                Text(
                    text = "立即清理",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "如果你刚调整了规则，或者想立刻释放历史列表，可以直接执行清理。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (uiState.deletedCount > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "最近一次操作影响 ${uiState.deletedCount} 条记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showCleanupConfirm = true },
                    enabled = !uiState.isLoading && !uiState.isSaving && !uiState.isClearing && !uiState.isCleaningUp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (uiState.isCleaningUp) "清理中..." else "清理超出保留天数的记录")
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { showClearConfirm = true },
                    enabled = !uiState.isLoading && !uiState.isSaving && !uiState.isClearing && !uiState.isCleaningUp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (uiState.isClearing) "清空中..." else "清空全部历史")
                }
            }
        }
    }

    if (showCleanupConfirm) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isCleaningUp) {
                    showCleanupConfirm = false
                }
            },
            title = { Text("确认按天清理") },
            text = {
                Text(
                    text = "会删除早于当前“保留天数”的历史记录，建议先确认保留天数填写正确。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { showCleanupConfirm = false },
                    enabled = !uiState.isCleaningUp,
                ) {
                    Text("取消")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCleanupConfirm = false
                        onCleanupOlderThanDays()
                    },
                    enabled = !uiState.isCleaningUp,
                ) {
                    Text(if (uiState.isCleaningUp) "清理中..." else "确认清理")
                }
            },
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isClearing) {
                    showClearConfirm = false
                }
            },
            title = { Text("确认清空历史") },
            text = {
                Text(
                    text = "会移除当前账号下全部文本历史记录。这个动作会同步影响 Web、Android、Windows。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirm = false },
                    enabled = !uiState.isClearing,
                ) {
                    Text("取消")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        onClearHistory()
                    },
                    enabled = !uiState.isClearing,
                ) {
                    Text(if (uiState.isClearing) "清空中..." else "确认清空")
                }
            },
        )
    }
}
