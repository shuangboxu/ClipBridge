package com.xushuangbo.clipbridge.feature.shell

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun RequestScreen(
    innerPadding: PaddingValues,
    onPendingFeatureClick: (String) -> Unit,
) {
    val requestEntries = listOf(
        ActionEntry("我的申请", Icons.Outlined.Description),
        ActionEntry("申请进度", Icons.Outlined.TaskAlt),
    )

    PageWithActionGroup(
        innerPadding = innerPadding,
        title = "申请",
        entries = requestEntries,
        onEntryClick = onPendingFeatureClick,
    )
}

@Composable
internal fun FilesScreen(
    innerPadding: PaddingValues,
    onPendingFeatureClick: (String) -> Unit,
) {
    val fileEntries = listOf(
        ActionEntry("上传文件", Icons.Outlined.Folder),
        ActionEntry("接收记录", Icons.Outlined.Description),
        ActionEntry("分享文件", Icons.Outlined.Share),
    )

    PageWithActionGroup(
        innerPadding = innerPadding,
        title = "文件中心",
        entries = fileEntries,
        onEntryClick = onPendingFeatureClick,
    )
}

@Composable
internal fun ShareScreen(
    innerPadding: PaddingValues,
    onPendingFeatureClick: (String) -> Unit,
) {
    val shareEntries = listOf(
        ActionEntry("文本分享", Icons.Outlined.Share),
        ActionEntry("文件分享", Icons.Outlined.Folder),
        ActionEntry("分享记录", Icons.Outlined.Description),
    )

    PageWithActionGroup(
        innerPadding = innerPadding,
        title = "分享",
        entries = shareEntries,
        onEntryClick = onPendingFeatureClick,
    )
}

@Composable
internal fun GlobalSettingsScreen(
    innerPadding: PaddingValues,
    onPendingFeatureClick: (String) -> Unit,
) {
    val settingsEntries = listOf(
        ActionEntry("同步策略", Icons.Outlined.TaskAlt),
        ActionEntry("显示偏好", Icons.Outlined.Description),
    )

    PageWithActionGroup(
        innerPadding = innerPadding,
        title = "全局设置",
        entries = settingsEntries,
        onEntryClick = onPendingFeatureClick,
    )
}

@Composable
internal fun ApprovalsScreen(
    innerPadding: PaddingValues,
    onPendingFeatureClick: (String) -> Unit,
) {
    val approvalEntries = listOf(
        ActionEntry("待审批", Icons.Outlined.TaskAlt),
        ActionEntry("审批记录", Icons.Outlined.Description),
    )

    PageWithActionGroup(
        innerPadding = innerPadding,
        title = "审批",
        entries = approvalEntries,
        onEntryClick = onPendingFeatureClick,
    )
}

@Composable
internal fun ScanScreen(innerPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(132.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "扫一扫",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "扫一扫入口先固定在这里",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "后面接真实扫码能力时，直接在这个页面继续扩展即可.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun PageWithActionGroup(
    innerPadding: PaddingValues,
    title: String,
    entries: List<ActionEntry>,
    onEntryClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        ActionGroup(
            title = title,
            entries = entries,
            onEntryClick = onEntryClick,
        )
    }
}
