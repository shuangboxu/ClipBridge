package com.xushuangbo.clipbridge.feature.shell

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xushuangbo.clipbridge.app.AppTestTags
import com.xushuangbo.clipbridge.core.network.ClipboardItem
import com.xushuangbo.clipbridge.feature.sync.CLIPBRIDGE_HISTORY_LABEL
import com.xushuangbo.clipbridge.feature.sync.buildClipboardClipData
import com.xushuangbo.clipbridge.ui.components.PageErrorBanner
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreenRoute(
    innerPadding: PaddingValues,
    viewModel: HistoryViewModel = viewModel(),
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    pendingShortcutAction: HistoryShortcutAction? = null,
    onShortcutActionConsumed: () -> Unit = {},
    uploadDialogVisible: Boolean = false,
    onUploadDialogDismiss: () -> Unit = {},
    onRequireAuth: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var latestScrollRequestId by remember { mutableStateOf(0) }
    var handledScrollRequestId by remember { mutableStateOf(0) }
    val isPinnedToTop by androidx.compose.runtime.remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset <= 8
        }
    }

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

    LaunchedEffect(viewModel) {
        viewModel.scrollToTopEvents.collect {
            latestScrollRequestId += 1
        }
    }

    LaunchedEffect(latestScrollRequestId, uiState.items.firstOrNull()?.id) {
        if (latestScrollRequestId == 0 || handledScrollRequestId == latestScrollRequestId) {
            return@LaunchedEffect
        }

        // LazyColumn 在顶部插入新数据时，会尽量保持当前阅读位置，
        // 这里等新列表完成一帧布局后再强制回到第 0 项，确保最新记录真正顶下来。
        withFrameNanos { }
        listState.scrollToItem(0)
        withFrameNanos { }
        listState.scrollToItem(0)
        handledScrollRequestId = latestScrollRequestId
    }

    LaunchedEffect(pendingShortcutAction) {
        val action = pendingShortcutAction ?: return@LaunchedEffect
        viewModel.handleShortcutAction(action)
        onShortcutActionConsumed()
    }

    LaunchedEffect(uiState.hasPendingLatestUpdates, uiState.currentPage, isPinnedToTop) {
        if (
            uiState.hasPendingLatestUpdates &&
            uiState.currentPage == 1 &&
            isPinnedToTop &&
            !uiState.isLoading &&
            !uiState.isUploading &&
            !uiState.isPulling
        ) {
            viewModel.showLatestHistory(scrollToTop = true)
        }
    }

    HistoryScreen(
        innerPadding = innerPadding,
        uiState = uiState,
        listState = listState,
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onDraftTextChange = viewModel::updateDraftText,
        onUploadClick = viewModel::uploadDraftText,
        uploadDialogVisible = uploadDialogVisible,
        onUploadDialogDismiss = onUploadDialogDismiss,
        onShowLatestUpdates = { viewModel.showLatestHistory(scrollToTop = true) },
        onPreviousPage = viewModel::loadNewerHistoryPage,
        onNextPage = viewModel::loadOlderHistoryPage,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HistoryScreen(
    innerPadding: PaddingValues,
    uiState: HistoryUiState,
    listState: LazyListState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onDraftTextChange: (String) -> Unit,
    onUploadClick: () -> Unit,
    uploadDialogVisible: Boolean,
    onUploadDialogDismiss: () -> Unit,
    onShowLatestUpdates: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
) {
    val context = LocalContext.current
    val filteredItems = uiState.items.filter { item ->
        searchQuery.isBlank() || item.textContent.contains(searchQuery, ignoreCase = true)
    }
    var selectedItem by remember { mutableStateOf<ClipboardItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .testTag(AppTestTags.HistoryScreen),
    ) {
        if (uiState.errorMessage != null) {
            PageErrorBanner(message = uiState.errorMessage)
            Spacer(modifier = Modifier.height(16.dp))
        }

        HistoryToolbar(
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.hasPendingLatestUpdates) {
            PendingLatestHistoryBanner(
                isViewingLatestPage = uiState.currentPage == 1,
                onClick = onShowLatestUpdates,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            if (filteredItems.isEmpty() && !uiState.isLoading) {
                EmptyHistoryState()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    state = listState,
                ) {
                    items(
                        items = filteredItems,
                        key = { item -> item.id },
                    ) { item ->
                        HistoryItemCard(
                            item = item,
                            onCopy = { copyTextToClipboard(item.textContent, context) },
                            onClick = { selectedItem = item },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HistoryPagerBar(
                currentPage = uiState.currentPage,
                hasMoreOlder = uiState.hasMoreOlder,
                isLoading = uiState.isLoading,
                onPreviousPage = onPreviousPage,
                onNextPage = onNextPage,
            )
        }
    }

    if (uploadDialogVisible) {
        AlertDialog(
            onDismissRequest = onUploadDialogDismiss,
            title = { Text("手动上传文本") },
            text = {
                Column {
                    Text(
                        text = "输入要上传的文本后点击上传。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.draftText,
                        onValueChange = onDraftTextChange,
                        minLines = 4,
                        label = { Text("要上传的文本") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUploadClick()
                        onUploadDialogDismiss()
                    },
                    enabled = !uiState.isUploading && !uiState.isPulling,
                ) {
                    Text(if (uiState.isUploading) "上传中..." else "上传")
                }
            },
            dismissButton = {
                Button(onClick = onUploadDialogDismiss) {
                    Text("取消")
                }
            },
        )
    }

    selectedItem?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedItem = null },
            title = { Text("完整内容") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "记录时间：${formatHistoryTime(item.createdAt)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = item.textContent.ifBlank { "(空文本)" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(onClick = { selectedItem = null }) {
                    Text("关闭")
                }
            },
        )
    }
}

@Composable
private fun HistoryToolbar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
) {
    Column {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            singleLine = true,
            label = { Text("本地搜索") },
            placeholder = { Text("只过滤当前已加载列表") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PendingLatestHistoryBanner(
    isViewingLatestPage: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (isViewingLatestPage) {
                    "有新的历史记录，点击立即刷新"
                } else {
                    "有新的历史记录，点击回到最新"
                },
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EmptyHistoryState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "暂无历史记录",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "可以先手动上传一段文本，或执行一次远端历史拉取。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryItemCard(
    item: ClipboardItem,
    onCopy: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Seq ${item.seq}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = item.textContent.ifBlank { "(空文本)" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
            }

            IconButton(
                onClick = onCopy,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "复制",
                )
            }
        }
    }
}

@Composable
private fun HistoryPagerBar(
    currentPage: Int,
    hasMoreOlder: Boolean,
    isLoading: Boolean,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onPreviousPage,
            enabled = !isLoading && currentPage > 1,
        ) {
            Text("上一页")
        }

        Text(
            text = "第 $currentPage 页",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = onNextPage,
            enabled = !isLoading && hasMoreOlder,
        ) {
            Text("下一页")
        }
    }
}

private fun copyTextToClipboard(text: String, context: Context) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(buildClipboardClipData(CLIPBRIDGE_HISTORY_LABEL, text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

private fun formatHistoryTime(value: String): String {
    if (value.isBlank()) {
        return "-"
    }

    return try {
        val localDateTime = OffsetDateTime.parse(value)
            .atZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime()
        HISTORY_TIME_FORMATTER.format(localDateTime)
    } catch (_: Exception) {
        value
    }
}

private val HISTORY_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
