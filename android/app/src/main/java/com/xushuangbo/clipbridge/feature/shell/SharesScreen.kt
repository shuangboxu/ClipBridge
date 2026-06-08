package com.xushuangbo.clipbridge.feature.shell

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xushuangbo.clipbridge.app.AppTestTags
import com.xushuangbo.clipbridge.core.files.PickedLocalFile
import com.xushuangbo.clipbridge.core.network.ShareItemRecord
import com.xushuangbo.clipbridge.core.share.PublicShareLinkBuilder
import com.xushuangbo.clipbridge.core.share.QrCodeBitmapFactory
import com.xushuangbo.clipbridge.core.share.ShareComposeMode
import com.xushuangbo.clipbridge.core.share.ShareCountdownPreset
import com.xushuangbo.clipbridge.core.share.ShareExpirePreset
import com.xushuangbo.clipbridge.core.share.ShareStatusFilter
import com.xushuangbo.clipbridge.core.share.ShareStrategyKey
import com.xushuangbo.clipbridge.ui.components.PageErrorBanner
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

@Composable
fun ShareScreenRoute(
    innerPadding: PaddingValues,
    viewModel: SharesViewModel = viewModel(),
    onOpenShareRules: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.selectLocalFile(uri)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.ensureLoaded()
    }

    ShareScreen(
        innerPadding = innerPadding,
        uiState = uiState,
        onComposeModeSelected = viewModel::selectComposeMode,
        onStrategySelected = viewModel::selectStrategy,
        onTextDraftChange = viewModel::updateTextDraft,
        onChooseFile = { pickFileLauncher.launch(arrayOf("*/*")) },
        onClearSelectedFile = viewModel::clearSelectedFile,
        onCreateShare = viewModel::createShare,
        onStatusFilterSelected = viewModel::selectStatusFilter,
        onRefreshShares = viewModel::refreshShares,
        onPreviousPage = viewModel::loadPreviousPage,
        onNextPage = viewModel::loadNextPage,
        onRevokeShare = viewModel::revokeShare,
        onCopyLink = { link ->
            if (link.isBlank()) {
                viewModel.showUiError("分享链接无效，无法复制")
            } else {
                copyTextToClipboard(context, link)
                viewModel.notifyLinkCopied()
            }
        },
        onOpenLink = { link ->
            if (link.isBlank()) {
                viewModel.showUiError("分享链接无效，无法打开")
            } else {
                runCatching { uriHandler.openUri(link) }
                    .onFailure { viewModel.showUiError("无法打开浏览器，请检查系统设置") }
            }
        },
        onOpenShareRules = onOpenShareRules,
    )
}

@Composable
fun ShareScreen(
    innerPadding: PaddingValues,
    uiState: SharesUiState,
    onComposeModeSelected: (ShareComposeMode) -> Unit,
    onStrategySelected: (ShareStrategyKey) -> Unit,
    onTextDraftChange: (String) -> Unit,
    onChooseFile: () -> Unit,
    onClearSelectedFile: () -> Unit,
    onCreateShare: () -> Unit,
    onStatusFilterSelected: (ShareStatusFilter) -> Unit,
    onRefreshShares: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onRevokeShare: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenShareRules: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .testTag(AppTestTags.ShareScreen),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (uiState.errorMessage != null) {
            item {
                PageErrorBanner(message = uiState.errorMessage)
            }
        }

        item {
            ShareStrategyPanel(
                composeMode = uiState.composeMode,
                selectedStrategy = uiState.strategyKey,
                strategySummaryTitle = uiState.strategySummary.title,
                strategySummaryDescription = uiState.strategySummary.description,
                strategySummaryCopyLabel = uiState.strategySummary.copyLabel,
                onComposeModeSelected = onComposeModeSelected,
                onStrategySelected = onStrategySelected,
                onOpenShareRules = onOpenShareRules,
            )
        }

        item {
            ShareCreatePanel(
                composeMode = uiState.composeMode,
                textDraft = uiState.textDraft,
                selectedFile = uiState.selectedFile,
                maxUploadBytes = uiState.maxUploadBytes,
                isCreating = uiState.isCreating,
                onTextDraftChange = onTextDraftChange,
                onChooseFile = onChooseFile,
                onClearSelectedFile = onClearSelectedFile,
                onCreateShare = onCreateShare,
            )
        }

        if (uiState.latestShareLink.isNotBlank()) {
            item {
                LatestShareResultCard(
                    shareLink = uiState.latestShareLink,
                    onCopyLink = { onCopyLink(uiState.latestShareLink) },
                    onOpenLink = { onOpenLink(uiState.latestShareLink) },
                )
            }
        }

        item {
            ShareListHeader(
                selectedFilter = uiState.statusFilter,
                onFilterSelected = onStatusFilterSelected,
                onRefreshShares = onRefreshShares,
            )
        }

        if (uiState.shares.isEmpty() && !uiState.isLoading) {
            item {
                EmptyShareState()
            }
        } else {
            items(
                items = uiState.shares,
                key = { share -> share.id },
            ) { share ->
                val publicLink = PublicShareLinkBuilder.build(uiState.serviceAddress, share.token).orEmpty()
                ShareItemCard(
                    share = share,
                    publicLink = publicLink,
                    isRevoking = uiState.revokingShareId == share.id,
                    onCopyLink = { onCopyLink(publicLink) },
                    onOpenLink = { onOpenLink(publicLink) },
                    onRevokeShare = { onRevokeShare(share.id) },
                )
            }
        }

        item {
            SharePagerBar(
                currentPage = uiState.currentPage,
                totalPages = uiState.totalPages,
                totalShares = uiState.totalShares,
                isLoading = uiState.isLoading,
                onPreviousPage = onPreviousPage,
                onNextPage = onNextPage,
            )
        }
    }
}

@Composable
fun ShareRulesScreenRoute(
    innerPadding: PaddingValues,
    viewModel: SharesViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ShareRulesScreen(
        innerPadding = innerPadding,
        uiState = uiState,
        onNeverAllowCopyChange = viewModel::updateNeverAllowCopy,
        onExpirePresetChange = viewModel::updateExpirePreset,
        onExpireAllowCopyChange = viewModel::updateExpireAllowCopy,
        onOnceShowCountdownChange = viewModel::updateOnceShowCountdown,
        onOnceCountdownPresetChange = viewModel::updateOnceCountdownPreset,
        onOnceAllowCopyChange = viewModel::updateOnceAllowCopy,
    )
}

@Composable
fun ShareRulesScreen(
    innerPadding: PaddingValues,
    uiState: SharesUiState,
    onNeverAllowCopyChange: (Boolean) -> Unit,
    onExpirePresetChange: (ShareExpirePreset) -> Unit,
    onExpireAllowCopyChange: (Boolean) -> Unit,
    onOnceShowCountdownChange: (Boolean) -> Unit,
    onOnceCountdownPresetChange: (ShareCountdownPreset) -> Unit,
    onOnceAllowCopyChange: (Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .testTag(AppTestTags.ShareRulesScreen),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (uiState.errorMessage != null) {
            item {
                PageErrorBanner(message = uiState.errorMessage)
            }
        }

        item {
            ShareRuleCard(
                title = "不过期",
                description = "公开链接不会自动失效，需要你主动撤销。",
                allowCopy = uiState.rules.never.allowCopyText,
                onAllowCopyChange = onNeverAllowCopyChange,
            )
        }

        item {
            ShareRuleCard(
                title = "过期",
                description = "这组规则会给文字分享加自动过期时间。",
                allowCopy = uiState.rules.expire.allowCopyText,
                onAllowCopyChange = onExpireAllowCopyChange,
                extraContent = {
                    PresetChipGroup(
                        entries = ShareExpirePreset.entries.map { preset -> preset to preset.label },
                        selectedValue = uiState.rules.expire.preset,
                        onSelected = onExpirePresetChange,
                    )
                },
            )
        }

        item {
            ShareRuleCard(
                title = "打开一次失效",
                description = "可以直接一次焚毁，也可以在首次打开后再走倒计时焚毁。",
                allowCopy = uiState.rules.once.allowCopyText,
                onAllowCopyChange = onOnceAllowCopyChange,
                extraContent = {
                    ToggleRow(
                        title = "使用倒计时",
                        value = uiState.rules.once.showCountdown,
                        onValueChange = onOnceShowCountdownChange,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PresetChipGroup(
                        entries = ShareCountdownPreset.entries.map { preset -> preset to preset.label },
                        selectedValue = uiState.rules.once.countdownPreset,
                        enabled = uiState.rules.once.showCountdown,
                        onSelected = onOnceCountdownPresetChange,
                    )
                },
            )
        }
    }
}

@Composable
private fun ShareStrategyPanel(
    composeMode: ShareComposeMode,
    selectedStrategy: ShareStrategyKey,
    strategySummaryTitle: String,
    strategySummaryDescription: String,
    strategySummaryCopyLabel: String,
    onComposeModeSelected: (ShareComposeMode) -> Unit,
    onStrategySelected: (ShareStrategyKey) -> Unit,
    onOpenShareRules: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "创建分享",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(14.dp))

            ChipRow(
                entries = ShareComposeMode.entries.map { mode -> mode to mode.label },
                selectedValue = composeMode,
                onSelected = onComposeModeSelected,
            )

            Spacer(modifier = Modifier.height(16.dp))

            ChipRow(
                entries = ShareStrategyKey.entries.map { strategy -> strategy to strategy.label },
                selectedValue = selectedStrategy,
                onSelected = onStrategySelected,
            )

            Spacer(modifier = Modifier.height(16.dp))

            val summaryBrush = Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
                    MaterialTheme.colorScheme.surface,
                ),
            )
            Surface(
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .background(summaryBrush)
                        .fillMaxWidth()
                        .padding(18.dp),
                ) {
                    Text(
                        text = strategySummaryTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = strategySummaryDescription,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    MetaPill(label = strategySummaryCopyLabel)
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(onClick = onOpenShareRules) {
                        Icon(imageVector = Icons.Outlined.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("调整分享规则")
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareCreatePanel(
    composeMode: ShareComposeMode,
    textDraft: String,
    selectedFile: PickedLocalFile?,
    maxUploadBytes: Long,
    isCreating: Boolean,
    onTextDraftChange: (String) -> Unit,
    onChooseFile: () -> Unit,
    onClearSelectedFile: () -> Unit,
    onCreateShare: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            if (composeMode == ShareComposeMode.Text) {
                OutlinedTextField(
                    value = textDraft,
                    onValueChange = onTextDraftChange,
                    label = { Text("分享文本") },
                    placeholder = { Text("输入要公开分享的文字内容") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    maxLines = 8,
                )
            } else {
                FileSelectCard(
                    selectedFile = selectedFile,
                    maxUploadBytes = maxUploadBytes,
                    onChooseFile = onChooseFile,
                    onClearSelectedFile = onClearSelectedFile,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onCreateShare,
                enabled = !isCreating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = if (composeMode == ShareComposeMode.Text) {
                        Icons.Outlined.Share
                    } else {
                        Icons.Outlined.CloudUpload
                    },
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isCreating) {
                        "正在生成..."
                    } else {
                        if (composeMode == ShareComposeMode.Text) "生成文本分享" else "生成文件分享"
                    },
                )
            }
        }
    }
}

@Composable
private fun FileSelectCard(
    selectedFile: PickedLocalFile?,
    maxUploadBytes: Long,
    onChooseFile: () -> Unit,
    onClearSelectedFile: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "文件内容",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "当前文件分享会重新上传文件体，不直接复用文件中心里的现有记录。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (selectedFile == null) {
                OutlinedButton(
                    onClick = onChooseFile,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(imageVector = Icons.Outlined.Folder, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("选择文件")
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = selectedFile.displayName.ifBlank { "未命名文件" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = buildString {
                                append(selectedFile.contentType.ifBlank { "application/octet-stream" })
                                if (selectedFile.sizeBytes != null) {
                                    append(" · ${formatBytes(selectedFile.sizeBytes)}")
                                }
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onChooseFile) {
                                Text("重新选择")
                            }
                            OutlinedButton(onClick = onClearSelectedFile) {
                                Text("移除文件")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (maxUploadBytes > 0L) {
                    "单文件上限 ${formatBytes(maxUploadBytes)}"
                } else {
                    "上传上限以服务端当前配置为准"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LatestShareResultCard(
    shareLink: String,
    onCopyLink: () -> Unit,
    onOpenLink: () -> Unit,
) {
    val qrBitmap = remember(shareLink) {
        QrCodeBitmapFactory.createBitmapOrNull(
            content = shareLink,
            sizePx = 480,
        )
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "最新分享链接",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = shareLink,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCopyLink) {
                    Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("复制链接")
                }
                OutlinedButton(onClick = onOpenLink) {
                    Icon(imageVector = Icons.Outlined.OpenInBrowser, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("浏览器打开")
                }
            }

            if (qrBitmap != null) {
                Spacer(modifier = Modifier.height(18.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "分享二维码",
                        modifier = Modifier
                            .size(220.dp)
                            .padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareListHeader(
    selectedFilter: ShareStatusFilter,
    onFilterSelected: (ShareStatusFilter) -> Unit,
    onRefreshShares: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "分享记录",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = onRefreshShares) {
                Icon(imageVector = Icons.Outlined.Refresh, contentDescription = "刷新分享列表")
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        ChipRow(
            entries = ShareStatusFilter.entries.map { filter -> filter to filter.label },
            selectedValue = selectedFilter,
            onSelected = onFilterSelected,
        )
    }
}

@Composable
private fun ShareItemCard(
    share: ShareItemRecord,
    publicLink: String,
    isRevoking: Boolean,
    onCopyLink: () -> Unit,
    onOpenLink: () -> Unit,
    onRevokeShare: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                            imageVector = if (share.contentKind.equals("file", ignoreCase = true)) {
                                Icons.Outlined.Folder
                            } else {
                                Icons.Outlined.Description
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = buildShareTitle(share),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = buildShareSubtitle(share),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetaPill(label = shareStatusLabel(share.status))
                MetaPill(label = if (share.isEncrypted) "加密分享" else "明文分享")
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "打开次数 ${share.openCount} · 剩余 ${formatRemainingSeconds(share.remainingSeconds)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "创建时间 ${formatDateTime(share.createdAt)} · 到期时间 ${formatDateTime(share.expiresAt)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (publicLink.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = publicLink,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionTextButton(
                    title = "复制链接",
                    icon = Icons.Outlined.Link,
                    enabled = publicLink.isNotBlank(),
                    onClick = onCopyLink,
                )
                ActionTextButton(
                    title = "浏览器打开",
                    icon = Icons.Outlined.OpenInBrowser,
                    enabled = publicLink.isNotBlank(),
                    onClick = onOpenLink,
                )
                if (share.isActive()) {
                    ActionTextButton(
                        title = if (isRevoking) "撤销中..." else "撤销",
                        icon = Icons.Outlined.DeleteOutline,
                        enabled = !isRevoking,
                        onClick = onRevokeShare,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionTextButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun SharePagerBar(
    currentPage: Int,
    totalPages: Int,
    totalShares: Int,
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
            text = "第 $currentPage / $totalPages 页 · 共 $totalShares 条",
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

@Composable
private fun EmptyShareState() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "还没有分享记录",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "先创建一个文本分享或文件分享，列表会显示在这里。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ShareRuleCard(
    title: String,
    description: String,
    allowCopy: Boolean,
    onAllowCopyChange: (Boolean) -> Unit,
    extraContent: @Composable (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (extraContent != null) {
                Spacer(modifier = Modifier.height(14.dp))
                extraContent()
            }

            Spacer(modifier = Modifier.height(14.dp))
            ToggleRow(
                title = "允许取件页复制文字",
                value = allowCopy,
                onValueChange = onAllowCopyChange,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onValueChange(!value) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title)
        FilterChip(
            selected = value,
            onClick = { onValueChange(!value) },
            label = { Text(if (value) "已开启" else "已关闭") },
        )
    }
}

@Composable
private fun <T> ChipRow(
    entries: List<Pair<T, String>>,
    selectedValue: T,
    onSelected: (T) -> Unit,
) {
    SelectableChipGrid(
        entries = entries,
        selectedValue = selectedValue,
        maxItemsPerRow = 3,
        onSelected = onSelected,
    )
}

@Composable
private fun <T> PresetChipGroup(
    entries: List<Pair<T, String>>,
    selectedValue: T,
    enabled: Boolean = true,
    onSelected: (T) -> Unit,
) {
    Column {
        Text(
            text = "预设选项",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        SelectableChipGrid(
            entries = entries,
            selectedValue = selectedValue,
            enabled = enabled,
            maxItemsPerRow = 2,
            onSelected = onSelected,
        )
    }
}

@Composable
private fun <T> SelectableChipGrid(
    entries: List<Pair<T, String>>,
    selectedValue: T,
    enabled: Boolean = true,
    maxItemsPerRow: Int,
    onSelected: (T) -> Unit,
) {
    // 这里不再依赖 FlowRow，避免不同 Compose 运行时版本下的方法签名不一致导致闪退。
    val safeMaxItemsPerRow = maxItemsPerRow.coerceAtLeast(1)
    val rows = entries.chunked(safeMaxItemsPerRow)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { rowEntries ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowEntries.forEach { (value, label) ->
                    FilterChip(
                        selected = value == selectedValue,
                        onClick = { onSelected(value) },
                        enabled = enabled,
                        label = { Text(label) },
                        modifier = Modifier.weight(1f),
                    )
                }

                repeat(safeMaxItemsPerRow - rowEntries.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
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
        )
    }
}

private fun buildShareTitle(share: ShareItemRecord): String {
    if (share.contentKind.equals("file", ignoreCase = true)) {
        return share.file?.originalName?.ifBlank { "文件分享" } ?: "文件分享"
    }

    val preview = share.textPreview.trim()
    return when {
        preview.isNotBlank() -> preview
        share.isEncrypted -> "加密文本分享"
        else -> "文本分享"
    }
}

private fun buildShareSubtitle(share: ShareItemRecord): String {
    if (share.contentKind.equals("file", ignoreCase = true)) {
        val file = share.file
        return buildString {
            append(file?.contentType?.ifBlank { "application/octet-stream" } ?: "application/octet-stream")
            append(" · ${formatBytes(file?.sizeBytes ?: 0L)}")
        }
    }

    return when {
        share.isEncrypted -> "加密文本会在公开页按密码解锁"
        else -> "公开文本分享"
    }
}

private fun shareStatusLabel(status: String): String {
    return when (status.lowercase()) {
        "active" -> "可访问"
        "expired" -> "已过期"
        "consumed" -> "已焚毁"
        "revoked" -> "已撤销"
        else -> status.ifBlank { "未知状态" }
    }
}

private fun formatDateTime(value: String): String {
    if (value.isBlank()) {
        return "-"
    }

    return try {
        val localDateTime = OffsetDateTime.parse(value)
            .atZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime()
        SHARE_TIME_FORMATTER.format(localDateTime)
    } catch (_: Exception) {
        value
    }
}

private fun formatRemainingSeconds(value: Long): String {
    if (value <= 0L) {
        return "-"
    }

    return when {
        value < 60L -> "${value} 秒"
        value < 3600L -> "${value / 60} 分钟"
        else -> "${value / 3600} 小时"
    }
}

private fun formatBytes(value: Long): String {
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

private val SHARE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

private fun copyTextToClipboard(
    context: Context,
    text: String,
) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return
    clipboardManager.setPrimaryClip(ClipData.newPlainText("share-link", text))
}
