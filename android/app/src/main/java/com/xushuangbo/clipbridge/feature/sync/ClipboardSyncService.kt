package com.xushuangbo.clipbridge.feature.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.xushuangbo.clipbridge.MainActivity
import com.xushuangbo.clipbridge.R
import com.xushuangbo.clipbridge.app.AppContainer
import com.xushuangbo.clipbridge.core.network.AuthApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

class ClipboardSyncService : Service() {
    private val appContainer by lazy { AppContainer.create(applicationContext) }
    private val sessionStore get() = appContainer.sessionStore
    private val syncCoordinator get() = appContainer.clipboardSyncCoordinator
    private val historyUpdateBus get() = appContainer.historyUpdateBus

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // 上传、手动同步、轮询补拉都共用一把锁，避免并发修改剪贴板状态。
    private val syncMutex = Mutex()

    private lateinit var clipboardManager: ClipboardManager
    private lateinit var notificationManager: NotificationManager

    private var syncJob: Job? = null
    private var suppressNextClipboardCallback = false
    private var lastUploadedText: String = ""

    // 系统剪贴板回调进入后统一串行处理，避免快速复制时出现重入。
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        serviceScope.launch {
            syncMutex.withLock {
                handleLocalClipboardChanged()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService<ClipboardManager>() ?: error("ClipboardManager unavailable")
        notificationManager = getSystemService<NotificationManager>() ?: error("NotificationManager unavailable")
        ensureNotificationChannel()
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_STOP -> {
                stopServiceInternal("同步已停止")
                return START_NOT_STICKY
            }

            ACTION_SYNC_NOW -> {
                startForegroundCompat(buildNotification("正在手动同步"))
                serviceScope.launch {
                    syncMutex.withLock {
                        handleManualSync(intent ?: Intent())
                    }
                }
                return START_STICKY
            }

            else -> {
                startForegroundCompat(buildNotification("正在启动同步"))
                serviceScope.launch {
                    syncMutex.withLock {
                        ensureRunning()
                    }
                }
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        syncJob?.cancel()
        syncJob = null
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureRunning() {
        val session = sessionStore.readSession()
        if (!session.hasCompleteAuth()) {
            stopServiceInternal("登录已失效，请重新登录")
            return
        }
        if (!sessionStore.isSyncEnabled()) {
            stopServiceInternal("同步开关已关闭")
            return
        }

        lastUploadedText = ""
        updateStatus("同步已开启，正在监听剪贴板")
        if (syncJob?.isActive == true) {
            return
        }

        syncJob = serviceScope.launch {
            // 常驻轮询只负责“补拉远端”，本地复制由系统剪贴板监听主动触发。
            while (isActive) {
                try {
                    syncMutex.withLock {
                        pullRemoteOnce(showWhenNoData = false)
                    }
                } catch (error: AuthApiException) {
                    if (error.httpCode == 401) {
                        handleAuthFailure()
                        return@launch
                    }
                    updateStatus(error.message ?: "拉取失败")
                } catch (error: IOException) {
                    updateStatus(error.message ?: "网络异常，请稍后重试")
                } catch (error: Exception) {
                    updateStatus(error.message ?: "同步异常")
                }

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun handleLocalClipboardChanged() {
        if (!sessionStore.isSyncEnabled()) {
            return
        }

        if (suppressNextClipboardCallback) {
            suppressNextClipboardCallback = false
            return
        }

        val snapshot = readClipboardSnapshot() ?: return
        // 历史页复制和远端写回都会打内部标签，避免再次上传形成回环。
        if (isInternalClipboardLabel(snapshot.label)) {
            return
        }

        val text = snapshot.text.trim()
        if (text.isBlank()) {
            return
        }
        // 简单文本去重，避免同一段文本因为重复复制被连续上传。
        if (text == lastUploadedText) {
            return
        }

        uploadTextAndAck(text, source = "本地剪贴板")
    }

    private suspend fun handleManualSync(intent: Intent) {
        val session = sessionStore.readSession()
        if (!session.hasCompleteAuth()) {
            stopServiceInternal("登录已失效，请重新登录")
            return
        }
        if (!sessionStore.isSyncEnabled()) {
            stopServiceInternal("同步开关已关闭")
            return
        }

        val source = intent.getStringExtra(EXTRA_SYNC_SOURCE).orEmpty().ifBlank { "手动同步" }
        val preferredText = intent.getStringExtra(EXTRA_SYNC_TEXT)?.trim().orEmpty()
        val preferredLabel = intent.getStringExtra(EXTRA_SYNC_LABEL).orEmpty()

        val snapshot = if (preferredText.isBlank()) {
            readClipboardSnapshot()
        } else {
            null
        }

        // 悬浮球/通知栏触发时，先识别内部标签，再决定是否上传当前剪贴板。
        val sourceLabel = when {
            preferredLabel.isNotBlank() -> preferredLabel
            else -> snapshot?.label.orEmpty()
        }
        val textToUpload = if (isInternalClipboardLabel(sourceLabel)) {
            ""
        } else {
            when {
                preferredText.isNotBlank() -> preferredText
                else -> snapshot?.text?.trim().orEmpty()
            }
        }

        if (textToUpload.isNotBlank()) {
            uploadTextAndAck(textToUpload, source = source)
        }

        pullRemoteOnce(showWhenNoData = true)
    }

    private suspend fun uploadTextAndAck(text: String, source: String) {
        val session = sessionStore.readSession()
        if (!session.hasCompleteAuth()) {
            stopServiceInternal("登录已失效，请重新登录")
            return
        }

        try {
            val uploadResult = syncCoordinator.uploadText(
                session = session,
                text = text,
            )
            lastUploadedText = text
            if (uploadResult.deduplicated) {
                updateStatus("去重命中：$source")
            } else {
                updateStatus("上传成功：$source")
            }

            val latestSession = syncCoordinator.readCurrentSession()
            if (uploadResult.item.seq > 0L) {
                syncCoordinator.ackSync(
                    session = latestSession,
                    seq = uploadResult.item.seq,
                )
            }

            // 上传成功后通知历史页刷新，让列表表现更接近聊天消息流。
            historyUpdateBus.notifyHistoryUpdated()
        } catch (error: AuthApiException) {
            if (error.httpCode == 401) {
                handleAuthFailure()
                return
            }
            updateStatus(error.message ?: "上传失败")
        } catch (error: IOException) {
            updateStatus(error.message ?: "网络异常，请稍后重试")
        } catch (error: Exception) {
            updateStatus(error.message ?: "上传失败")
        }
    }

    private suspend fun pullRemoteOnce(showWhenNoData: Boolean) {
        val session = sessionStore.readSession()
        if (!session.hasCompleteAuth()) {
            stopServiceInternal("登录已失效，请重新登录")
            return
        }

        val sinceSeq = sessionStore.readLastAckSeq()
        val pullResult = syncCoordinator.pullSync(
            session = session,
            sinceSeq = sinceSeq,
            limit = DEFAULT_PULL_LIMIT,
        )

        if (pullResult.items.isEmpty()) {
            if (showWhenNoData) {
                updateStatus("没有新的远端内容")
            }
            return
        }

        var appliedCount = 0
        val latestRemoteItem = pullResult.items.asReversed().firstOrNull { item ->
            item.originDeviceId != session.currentDeviceId &&
                item.contentType == "text" &&
                item.textContent.isNotBlank()
        }

        if (latestRemoteItem != null) {
            applyRemoteClipboardText(latestRemoteItem.textContent)
            appliedCount = 1
        }

        val ackSeq = pullResult.nextSinceSeq ?: pullResult.items.maxOfOrNull { it.seq } ?: sinceSeq
        if (ackSeq > sinceSeq) {
            val latestSession = syncCoordinator.readCurrentSession()
            syncCoordinator.ackSync(
                session = latestSession,
                seq = ackSeq,
            )
        }

        // 只要补拉到了新历史，就通知历史页决定“自动刷新”还是“提示有新内容”。
        historyUpdateBus.notifyHistoryUpdated(itemCount = pullResult.items.size)
        updateStatus("已补拉 ${pullResult.items.size} 条，写入 $appliedCount 条")
    }

    private fun applyRemoteClipboardText(text: String) {
        // 远端写回前先抑制下一次系统回调，避免“写回即上传”。
        suppressNextClipboardCallback = true
        clipboardManager.setPrimaryClip(
            buildClipboardClipData(CLIPBRIDGE_REMOTE_LABEL, text),
        )
    }

    private fun readClipboardSnapshot(): ClipboardSnapshot? {
        return try {
            val clip = clipboardManager.primaryClip ?: return null
            if (clip.itemCount <= 0) return null
            val item = clip.getItemAt(0)
            ClipboardSnapshot(
                label = clipboardManager.primaryClipDescription?.label?.toString().orEmpty(),
                text = item.coerceToText(this)?.toString().orEmpty(),
            )
        } catch (error: SecurityException) {
            updateStatus("系统限制：无法读取剪贴板内容")
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun stopServiceInternal(status: String) {
        syncJob?.cancel()
        syncJob = null
        updateStatus(status)
        lastUploadedText = ""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun handleAuthFailure() {
        sessionStore.clearClipboardSyncState()
        sessionStore.clearAuth()
        stopServiceInternal("登录已失效，请重新登录")
    }

    private fun updateStatus(message: String) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "ClipBridge 剪贴板同步常驻通知"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(status: String): Notification {
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val openAppPendingIntent = PendingIntent.getActivity(this, 1001, openAppIntent, pendingFlags)

        // 通知栏“立即同步”也走前台触发页，避免后台直接读剪贴板被系统拦截。
        val syncNowIntent = SyncTriggerActivity.createLaunchIntent(this, source = "通知栏")
        val syncNowPendingIntent = PendingIntent.getActivity(this, 1002, syncNowIntent, pendingFlags)

        val stopIntent = Intent(this, ClipboardSyncService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(this, 1003, stopIntent, pendingFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("ClipBridge 同步中")
            .setContentText(status)
            .setStyle(NotificationCompat.BigTextStyle().bigText(status))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(0, "立即同步", syncNowPendingIntent)
            .addAction(0, "停止同步", stopPendingIntent)
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private data class ClipboardSnapshot(
        val label: String,
        val text: String,
    )

    companion object {
        const val ACTION_START = "com.xushuangbo.clipbridge.action.START_CLIPBOARD_SYNC"
        const val ACTION_STOP = "com.xushuangbo.clipbridge.action.STOP_CLIPBOARD_SYNC"
        const val ACTION_SYNC_NOW = "com.xushuangbo.clipbridge.action.SYNC_CLIPBOARD_NOW"
        const val EXTRA_SYNC_SOURCE = "extra_sync_source"
        const val EXTRA_SYNC_TEXT = "extra_sync_text"
        const val EXTRA_SYNC_LABEL = "extra_sync_label"

        private const val CHANNEL_ID = "clipbridge_sync_channel"
        private const val CHANNEL_NAME = "ClipBridge 剪贴板同步"
        private const val NOTIFICATION_ID = 11001
        private const val DEFAULT_PULL_LIMIT = 50
        private const val POLL_INTERVAL_MS = 2_500L

        fun start(context: Context) {
            val intent = Intent(context, ClipboardSyncService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ClipboardSyncService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun syncNow(
            context: Context,
            source: String,
            preferredText: String? = null,
            preferredLabel: String? = null,
        ) {
            SyncServiceController.syncClipboardNow(
                context = context,
                source = source,
                preferredText = preferredText,
                preferredLabel = preferredLabel,
            )
        }
    }
}
