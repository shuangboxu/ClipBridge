package com.xushuangbo.clipbridge.feature.shell

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xushuangbo.clipbridge.core.network.PublicShareFileRecord
import com.xushuangbo.clipbridge.core.network.PublicShareRecord
import com.xushuangbo.clipbridge.core.share.QrCodeBitmapFactory
import com.xushuangbo.clipbridge.core.share.QrCodeDecoder
import com.xushuangbo.clipbridge.ui.components.PageErrorBanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun ScanScreenRoute(
    innerPadding: PaddingValues,
    viewModel: ScanShareViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var hasCameraPermission by rememberSaveable { mutableStateOf(context.hasCameraPermission()) }
    var hasRequestedCameraPermission by rememberSaveable { mutableStateOf(false) }

    val requestCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        hasRequestedCameraPermission = true
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            val decodedText = withContext(Dispatchers.IO) {
                runCatching { QrCodeDecoder.decodeFromUri(context, uri) }.getOrNull()
            }

            if (decodedText.isNullOrBlank()) {
                viewModel.showUiError("未在所选图片里识别到二维码")
            } else {
                viewModel.resolveScannedText(decodedText)
            }
        }
    }

    LaunchedEffect(hasCameraPermission, hasRequestedCameraPermission) {
        if (!hasCameraPermission && !hasRequestedCameraPermission) {
            hasRequestedCameraPermission = true
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    ScanScreen(
        innerPadding = innerPadding,
        uiState = uiState,
        hasCameraPermission = hasCameraPermission,
        onRequestCameraPermission = {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        },
        onPickImage = {
            pickImageLauncher.launch("image/*")
        },
        onQrCodeDetected = viewModel::resolveScannedText,
        onPasswordChange = viewModel::updatePassword,
        onOpenShare = viewModel::openShare,
        onReset = viewModel::clearResult,
        onCopyLink = { link ->
            copyTextToClipboard(context, link)
            Toast.makeText(context, "分享链接已复制", Toast.LENGTH_SHORT).show()
        },
        onOpenLink = { link ->
            if (link.isBlank()) {
                viewModel.showUiError("分享链接无效，无法打开")
            } else {
                runCatching { uriHandler.openUri(link) }
                    .onFailure { viewModel.showUiError("无法打开浏览器，请检查系统设置") }
            }
        },
    )
}

@Composable
private fun ScanScreen(
    innerPadding: PaddingValues,
    uiState: ScanShareUiState,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    onPickImage: () -> Unit,
    onQrCodeDetected: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onOpenShare: () -> Unit,
    onReset: () -> Unit,
    onCopyLink: (String) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val isScanLocked = uiState.shareToken.isNotBlank() || uiState.isResolving || uiState.isOpening

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            if (uiState.errorMessage != null) {
                PageErrorBanner(message = uiState.errorMessage)
            }
        }

        item {
            ScanCaptureCard(
                hasCameraPermission = hasCameraPermission,
                scanLocked = isScanLocked,
                isResolving = uiState.isResolving,
                isOpening = uiState.isOpening,
                onRequestCameraPermission = onRequestCameraPermission,
                onPickImage = onPickImage,
                onReset = onReset,
                onQrCodeDetected = onQrCodeDetected,
            )
        }

        if (uiState.rawScannedText.isNotBlank()) {
            item {
                ScanValueCard(
                    rawValue = uiState.rawScannedText,
                    shareToken = uiState.shareToken,
                )
            }
        }

        if (uiState.share != null) {
            item {
                PublicShareCard(
                    share = uiState.share,
                    publicLink = uiState.publicLink,
                    password = uiState.password,
                    contentOpen = uiState.contentOpen,
                    textContent = uiState.textContent,
                    isOpening = uiState.isOpening,
                    onPasswordChange = onPasswordChange,
                    onOpenShare = onOpenShare,
                    onCopyLink = onCopyLink,
                    onOpenLink = onOpenLink,
                )
            }
        }
    }
}

@Composable
private fun ScanCaptureCard(
    hasCameraPermission: Boolean,
    scanLocked: Boolean,
    isResolving: Boolean,
    isOpening: Boolean,
    onRequestCameraPermission: () -> Unit,
    onPickImage: () -> Unit,
    onReset: () -> Unit,
    onQrCodeDetected: (String) -> Unit,
) {
    val panelBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f),
            MaterialTheme.colorScheme.surface,
        ),
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(panelBrush)
                .padding(18.dp),
        ) {
            Text(
                text = "扫一扫",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (scanLocked) {
                    "二维码已识别，当前先暂停取景，避免重复触发。"
                } else {
                    "把分享二维码放到取景框里，识别后会自动读取公开分享内容。"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (hasCameraPermission) {
                CameraPreviewCard(
                    scanEnabled = !scanLocked,
                    onQrCodeDetected = onQrCodeDetected,
                )
            } else {
                CameraPermissionCard(onRequestCameraPermission = onRequestCameraPermission)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onPickImage) {
                    Icon(imageVector = Icons.Outlined.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("相册识别")
                }

                if (scanLocked) {
                    Button(onClick = onReset) {
                        Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("重新扫码")
                    }
                } else if (!hasCameraPermission) {
                    Button(onClick = onRequestCameraPermission) {
                        Icon(imageVector = Icons.Outlined.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("打开相机")
                    }
                }
            }

            if (isResolving || isOpening) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (isResolving) "正在读取分享信息..." else "正在打开分享内容...",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun CameraPreviewCard(
    scanEnabled: Boolean,
    onQrCodeDetected: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val detectionLocked = remember { AtomicBoolean(false) }

    LaunchedEffect(scanEnabled) {
        if (scanEnabled) {
            detectionLocked.set(false)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzerExecutor.shutdown()
        }
    }

    DisposableEffect(scanEnabled, lifecycleOwner, previewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var cameraProvider: ProcessCameraProvider? = null

        val preview = Preview.Builder().build().also { builtPreview ->
            builtPreview.surfaceProvider = previewView.surfaceProvider
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(analyzerExecutor) { imageProxy ->
            try {
                if (!scanEnabled || detectionLocked.get()) {
                    return@setAnalyzer
                }

                val decodedText = QrCodeDecoder.decodeFromImageProxy(imageProxy)
                if (!decodedText.isNullOrBlank() && detectionLocked.compareAndSet(false, true)) {
                    onQrCodeDetected(decodedText)
                }
            } finally {
                imageProxy.close()
            }
        }

        cameraProviderFuture.addListener(
            {
                cameraProvider = cameraProviderFuture.get().also { provider ->
                    provider.unbindAll()
                    if (scanEnabled) {
                        // 这里只绑定一个预览和一个分析器，
                        // 目的是把相机链路保持得尽量简单，方便后续排查扫码问题。
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis,
                        )
                    }
                }
            },
            mainExecutor,
        )

        onDispose {
            imageAnalysis.clearAnalyzer()
            cameraProvider?.unbindAll()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        if (!scanEnabled) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.QrCodeScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "已暂停取景")
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionCard(
    onRequestCameraPermission: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.QrCodeScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "需要相机权限才能实时扫码",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "不授权也可以继续使用相册识别。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRequestCameraPermission) {
                Text("授予相机权限")
            }
        }
    }
}

@Composable
private fun ScanValueCard(
    rawValue: String,
    shareToken: String,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "识别结果",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (shareToken.isNotBlank()) {
                Text(
                    text = "分享 token：$shareToken",
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            SelectionContainer {
                Text(
                    text = rawValue,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PublicShareCard(
    share: PublicShareRecord,
    publicLink: String,
    password: String,
    contentOpen: Boolean,
    textContent: String,
    isOpening: Boolean,
    onPasswordChange: (String) -> Unit,
    onOpenShare: () -> Unit,
    onCopyLink: (String) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "分享内容",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = buildShareSummary(share),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (publicLink.isNotBlank()) {
                    IconButton(onClick = { onCopyLink(publicLink) }) {
                        Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = "复制链接")
                    }
                    IconButton(onClick = { onOpenLink(publicLink) }) {
                        Icon(imageVector = Icons.Outlined.OpenInBrowser, contentDescription = "浏览器打开")
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShareMetaPill(label = scanShareStatusLabel(share.status))
                ShareMetaPill(label = if (share.isEncrypted) "加密分享" else "普通分享")
                if (share.hasFileContent) {
                    ShareMetaPill(label = "${share.files.size} 个文件")
                }
            }

            shareStatusMessage(share)?.let { message ->
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
            }

            if (share.requiresPassword && !contentOpen && share.status.equals("active", ignoreCase = true)) {
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("分享密码") },
                    placeholder = { Text("请输入分享密码") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (share.status.equals("active", ignoreCase = true) && !contentOpen && (share.hasTextContent || share.requiresPassword)) {
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onOpenShare,
                    enabled = !isOpening,
                ) {
                    Text(
                        if (isOpening) {
                            "正在打开..."
                        } else if (share.requiresPassword) {
                            "解锁分享内容"
                        } else {
                            "打开文本内容"
                        },
                    )
                }
            }

            if (share.textPreview.isNotBlank() && !contentOpen) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "预览",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = share.textPreview)
                    }
                }
            }

            if (contentOpen && textContent.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "分享文字",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SelectionContainer {
                            Text(text = textContent)
                        }
                    }
                }
            }

            if (share.files.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "文件列表",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    share.files.forEach { file ->
                        ShareFileRow(file = file)
                    }
                }
            }

            if (share.createdAt.isNotBlank() || share.expiresAt.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = buildTimeSummary(share),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ShareFileRow(file: PublicShareFileRecord) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = file.originalName.ifBlank { "未命名文件" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = buildString {
                    append(file.contentType.ifBlank { "application/octet-stream" })
                    append(" · ${formatBytes(file.sizeBytes)}")
                    if (file.isImage) {
                        append(" · 图片")
                    } else if (file.isVideo) {
                        append(" · 视频")
                    }
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ShareMetaPill(label: String) {
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

private fun buildShareSummary(share: PublicShareRecord): String {
    return when {
        share.hasTextContent && share.hasFileContent -> "文字 + 文件分享"
        share.hasFileContent -> "文件分享"
        else -> "文字分享"
    }
}

private fun shareStatusMessage(share: PublicShareRecord): String? {
    return when (share.status.lowercase()) {
        "expired" -> "这个分享已经过期。"
        "revoked" -> "分享已被创建者撤销。"
        "consumed" -> "这个分享已经失效，不能再次打开。"
        else -> {
            if (share.requiresPassword) {
                "输入密码后可以读取完整内容。"
            } else {
                null
            }
        }
    }
}

private fun scanShareStatusLabel(status: String): String {
    return when (status.lowercase()) {
        "active" -> "可访问"
        "expired" -> "已过期"
        "consumed" -> "已焚毁"
        "revoked" -> "已撤销"
        else -> "未知状态"
    }
}

private fun buildTimeSummary(share: PublicShareRecord): String {
    return buildString {
        append("创建时间 ${formatDateTime(share.createdAt)}")
        if (share.expiresAt.isNotBlank()) {
            append(" · 到期时间 ${formatDateTime(share.expiresAt)}")
        }
        if (share.remainingSeconds > 0L) {
            append(" · 剩余 ${formatRemainingSeconds(share.remainingSeconds)}")
        }
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
        SCAN_TIME_FORMATTER.format(localDateTime)
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

private fun copyTextToClipboard(
    context: Context,
    text: String,
) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return
    clipboardManager.setPrimaryClip(ClipData.newPlainText("share-link", text))
}

private fun Context.hasCameraPermission(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}

private val SCAN_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
