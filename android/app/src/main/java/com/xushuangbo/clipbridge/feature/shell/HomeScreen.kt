package com.xushuangbo.clipbridge.feature.shell

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xushuangbo.clipbridge.R
import com.xushuangbo.clipbridge.app.AppTestTags

@Composable
internal fun HomeScreen(
    syncEnabled: Boolean,
    innerPadding: PaddingValues,
    onToggleSync: () -> Unit,
    shortcuts: List<HomeShortcut>,
    onShortcutClick: (HomeShortcut) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .testTag(AppTestTags.HomeScreen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SyncCenterPanel(enabled = syncEnabled, onToggleSync = onToggleSync)
        Spacer(modifier = Modifier.height(24.dp))
        ShortcutSection(title = "快捷入口", entries = shortcuts, onEntryClick = onShortcutClick)
    }
}

@Composable
internal fun AiScreen(
    innerPadding: PaddingValues,
    onPendingFeatureClick: (String) -> Unit,
) {
    val aiEntries = listOf(
        ActionEntry("文本整理", Icons.Outlined.AutoAwesome),
        ActionEntry("内容总结", Icons.Outlined.Description),
        ActionEntry("分享文案", Icons.Outlined.Share),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        ActionGroup(title = "AI 工具", entries = aiEntries, onEntryClick = onPendingFeatureClick)
    }
}

@Composable
private fun SyncCenterPanel(
    enabled: Boolean,
    onToggleSync: () -> Unit,
) {
    val panelBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
            MaterialTheme.colorScheme.surface,
        ),
    )

    Surface(
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(panelBrush)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                shadowElevation = 18.dp,
                modifier = Modifier
                    .size(230.dp)
                    .clickable(onClick = onToggleSync),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Image(
                        painter = painterResource(id = if (enabled) R.drawable.sync_toggle_on else R.drawable.sync_toggle_off),
                        contentDescription = if (enabled) "同步已开启" else "同步已关闭",
                        modifier = Modifier.size(164.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = if (enabled) "同步能力预留已开启" else "同步能力预留已关闭",
                style = MaterialTheme.typography.headlineMedium,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (enabled) {
                    "这里只保留第一轮入口状态，不会启动后台服务或静默同步。"
                } else {
                    "点击中间圆形按钮只会记录本地预留状态，方便后续接真实同步服务。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ShortcutSection(
    title: String,
    entries: List<HomeShortcut>,
    onEntryClick: (HomeShortcut) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(16.dp))

            entries.chunked(2).forEachIndexed { rowIndex, rowEntries ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowEntries.forEach { entry ->
                        ShortcutTile(entry = entry, modifier = Modifier.weight(1f), onClick = { onEntryClick(entry) })
                    }

                    if (rowEntries.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                if (rowIndex != entries.chunked(2).lastIndex) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun ShortcutTile(
    entry: HomeShortcut,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = entry.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
internal fun ActionGroup(
    title: String,
    entries: List<ActionEntry>,
    onEntryClick: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            )

            entries.forEachIndexed { index, entry ->
                ActionRow(entry = entry, onClick = { onEntryClick(entry.title) })
                if (index != entries.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    entry: ActionEntry,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = entry.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
