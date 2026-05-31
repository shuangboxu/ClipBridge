package com.xushuangbo.clipbridge.feature.sync

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

object SyncServiceController {
    // 统一封装所有同步相关服务入口，避免 UI 层散落 action 字符串。
    fun startClipboardSync(context: Context) {
        val intent = Intent(context, ClipboardSyncService::class.java).apply {
            action = ClipboardSyncService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopClipboardSync(context: Context) {
        val intent = Intent(context, ClipboardSyncService::class.java).apply {
            action = ClipboardSyncService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun syncClipboardNow(
        context: Context,
        source: String,
        preferredText: String? = null,
        preferredLabel: String? = null,
    ) {
        val intent = Intent(context, ClipboardSyncService::class.java).apply {
            action = ClipboardSyncService.ACTION_SYNC_NOW
            putExtra(ClipboardSyncService.EXTRA_SYNC_SOURCE, source)
            if (!preferredText.isNullOrBlank()) {
                putExtra(ClipboardSyncService.EXTRA_SYNC_TEXT, preferredText)
            }
            if (!preferredLabel.isNullOrBlank()) {
                putExtra(ClipboardSyncService.EXTRA_SYNC_LABEL, preferredLabel)
            }
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun startFloatingSync(context: Context) {
        context.startService(
            Intent(context, FloatingSyncService::class.java).apply {
                action = FloatingSyncService.ACTION_START
            },
        )
    }

    fun stopFloatingSync(context: Context) {
        context.startService(
            Intent(context, FloatingSyncService::class.java).apply {
                action = FloatingSyncService.ACTION_STOP
            },
        )
    }

    fun stopAll(context: Context) {
        stopFloatingSync(context)
        stopClipboardSync(context)
    }

    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun openOverlayPermissionSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || canDrawOverlays(context)) {
            return true
        }

        // 悬浮窗权限没有标准运行时弹窗，只能主动跳到系统授权页。
        val overlayIntent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (startActivitySafely(context, overlayIntent)) {
            return true
        }

        // 某些系统不支持专用入口时，退回到应用详情页让用户手动授权。
        val appDetailsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startActivitySafely(context, appDetailsIntent)
    }

    private fun startActivitySafely(
        context: Context,
        intent: Intent,
    ): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
