package com.xushuangbo.clipbridge.feature.shell

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xushuangbo.clipbridge.app.AppTestTags
import com.xushuangbo.clipbridge.core.network.FileAssetRecord
import com.xushuangbo.clipbridge.ui.components.PageErrorBanner
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FilesScreenRoute(
    innerPadding: PaddingValues,
    viewModel: FilesViewModel = viewModel(),
    uploadRequestVersion: Int = 0,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDownloadFile by remember { mutableStateOf<FileAssetRecord?>(null) }
    var handledUploadRequestVersion by remember { mutableStateOf(0) }

    LaunchedEffect(viewModel) {
        viewModel.ensureLoaded()
    }

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uploadSelectedFile(uri)
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*"),
    ) { uri: Uri? ->
        val targetFile = pendingDownloadFile
        pendingDownloadFile = null
        if (uri != null && targetFile != null) {
            viewModel.downloadFileToUri(targetFile.id, uri)
        }
    }

    LaunchedEffect(uploadRequestVersion) {
        if (uploadRequestVersion == 0 || uploadRequestVersion == handledUploadRequestVersion) {
            return@LaunchedEffect
        }

        handledUploadRequestVersion = uploadRequestVersion
        pickFileLauncher.launch(arrayOf("*/*"))
    }

    FilesScreen(
        innerPadding = innerPadding,
        uiState = uiState,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onPreviousPage = viewModel::loadPreviousPage,
        onNextPage = viewModel::loadNextPage,
        onDownloadClick = { file ->
            pendingDownloadFile = file
            createDocumentLauncher.launch(file.originalName.ifBlank { "download.bin" })
        },
        onRenameClick = viewModel::openRenameDialog,
        onDeleteClick = viewModel::openDeleteDialog,
        onRenameDraftChange = viewModel::updateRenameDraft,
        onRenameConfirm = viewModel::renameSelectedFile,
        onDeleteConfirm = viewModel::deleteSelectedFile,
        onDismissDialog = viewModel::dismissDialog,
    )
}

@Composable
fun FilesScreen(
    innerPadding: PaddingValues,
    uiState: FilesUiState,
    onSearchQueryChange: (String) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onDownloadClick: (FileAssetRecord) -> Unit,
    onRenameClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    onRenameDraftChange: (String) -> Unit,
    onRenameConfirm: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDismissDialog: () -> Unit,
) {
    val filteredFiles = uiState.files.filter { file ->
        uiState.searchQuery.isBlank() ||
            file.originalName.contains(uiState.searchQuery, ignoreCase = true) ||
            file.contentType.contains(uiState.searchQuery, ignoreCase = true) ||
            file.originDeviceName.contains(uiState.searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .testTag(AppTestTags.FilesScreen),
    ) {
        if (uiState.errorMessage != null) {
            PageErrorBanner(message = uiState.errorMessage)
            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChange,
            singleLine = true,
            label = { Text("本地搜索") },
            placeholder = { Text("只过滤当前已加载列表") },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            if (filteredFiles.isEmpty() && !uiState.isLoading) {
                EmptyFilesState(searchQuery = uiState.searchQuery)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    items(
                        items = filteredFiles,
                        key = { file -> file.id },
                    ) { file ->
                        FileItemCard(
                            file = file,
                            currentDeviceId = uiState.currentDeviceId,
                            isDownloading = uiState.downloadingFileId == file.id,
                            onDownloadClick = { onDownloadClick(file) },
                            onRenameClick = { onRenameClick(file.id) },
                            onDeleteClick = { onDeleteClick(file.id) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            FilesPagerBar(
                currentPage = uiState.currentPage,
                totalPages = uiState.totalPages,
                isLoading = uiState.isLoading,
                onPreviousPage = onPreviousPage,
                onNextPage = onNextPage,
            )
        }
    }

    if (uiState.dialogMode == FileDialogMode.Rename) {
        AlertDialog(
            onDismissRequest = onDismissDialog,
            title = { Text("重命名文件") },
            text = {
                Column {
                    Text(
                        text = "这里只修改显示名称，不会移动服务端磁盘文件。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.renameDraft,
                        onValueChange = onRenameDraftChange,
                        singleLine = true,
                        label = { Text("文件名") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onRenameConfirm,
                    enabled = !uiState.isSavingName,
                ) {
                    Text(if (uiState.isSavingName) "保存中..." else "保存")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDialog) {
                    Text("取消")
                }
            },
        )
    }

    if (uiState.dialogMode == FileDialogMode.Delete) {
        AlertDialog(
            onDismissRequest = onDismissDialog,
            title = { Text("删除文件") },
            text = {
                Text(
                    text = "删除后会同时移除文件记录，并尝试清理服务端磁盘文件。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = onDeleteConfirm,
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
private fun EmptyFilesState(searchQuery: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = if (searchQuery.isBlank()) "还没有文件记录" else "当前页没有匹配结果",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (searchQuery.isBlank()) {
                "可以从右上角上传一个文件，后续分享能力也会复用这里的文件元数据。"
            } else {
                "换一个关键词试试，当前搜索只过滤已经加载到本页的数据。"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FileItemCard(
    file: FileAssetRecord,
    currentDeviceId: String,
    isDownloading: Boolean,
    onDownloadClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(42.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.originalName.ifBlank { "未命名文件" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${formatFileSize(file.sizeBytes)} · ${formatFileTime(file.createdAt)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetaPill(label = file.contentType.ifBlank { "application/octet-stream" })
                    MetaPill(
                        label = buildOriginDeviceLabel(
                            originDeviceName = file.originDeviceName,
                            originDeviceId = file.originDeviceId,
                            currentDeviceId = currentDeviceId,
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactFileAction(
                        title = "重命名",
                        icon = Icons.Outlined.DriveFileRenameOutline,
                        onClick = onRenameClick,
                    )

                    CompactFileAction(
                        title = "删除",
                        icon = Icons.Outlined.DeleteOutline,
                        onClick = onDeleteClick,
                    )
                }
            }

            IconButton(
                onClick = onDownloadClick,
                enabled = !isDownloading,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudDownload,
                    contentDescription = if (isDownloading) "保存中" else "下载文件",
                )
            }
        }
    }
}

@Composable
private fun CompactFileAction(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MetaPill(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f),
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FilesPagerBar(
    currentPage: Int,
    totalPages: Int,
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
            text = "第 $currentPage / $totalPages 页",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = onNextPage,
            enabled = !isLoading && currentPage < totalPages,
        ) {
            Text("下一页")
        }
    }
}

private fun buildOriginDeviceLabel(
    originDeviceName: String,
    originDeviceId: String,
    currentDeviceId: String,
): String {
    val normalizedName = originDeviceName.ifBlank { "未知设备" }
    return if (originDeviceId.isNotBlank() && originDeviceId == currentDeviceId) {
        "$normalizedName · 本机"
    } else {
        normalizedName
    }
}

private fun formatFileTime(value: String): String {
    if (value.isBlank()) {
        return "-"
    }

    return try {
        val localDateTime = OffsetDateTime.parse(value)
            .atZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime()
        FILE_TIME_FORMATTER.format(localDateTime)
    } catch (_: Exception) {
        value
    }
}

private fun formatFileSize(value: Long): String {
    if (value <= 0L) {
        return "0 B"
    }
    if (value < 1024L) {
        return "$value B"
    }

    val kilo = 1024.0
    val mega = kilo * 1024
    val giga = mega * 1024

    return when {
        value < mega -> String.format("%.1f KB", value / kilo)
        value < giga -> String.format("%.1f MB", value / mega)
        else -> String.format("%.1f GB", value / giga)
    }
}

private val FILE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
