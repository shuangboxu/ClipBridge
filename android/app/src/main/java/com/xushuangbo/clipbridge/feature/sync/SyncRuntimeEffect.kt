package com.xushuangbo.clipbridge.feature.sync

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun SyncRuntimeEffect(
    syncEnabled: Boolean,
    hasSession: Boolean,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    var overlayHintShown by rememberSaveable { mutableStateOf(false) }
    val latestSyncEnabled by rememberUpdatedState(syncEnabled)
    val latestHasSession by rememberUpdatedState(hasSession)

    fun updateSyncServices(
        showOverlayHint: Boolean,
        syncEnabledValue: Boolean = latestSyncEnabled,
        hasSessionValue: Boolean = latestHasSession,
    ) {
        val shouldRunSync = syncEnabledValue && hasSessionValue
        if (!shouldRunSync) {
            overlayHintShown = false
            SyncServiceController.stopAll(appContext)
            return
        }

        SyncServiceController.startClipboardSync(appContext)
        if (SyncServiceController.canDrawOverlays(appContext)) {
            overlayHintShown = false
            SyncServiceController.startFloatingSync(appContext)
            return
        }

        SyncServiceController.stopFloatingSync(appContext)
        if (showOverlayHint && !overlayHintShown) {
            overlayHintShown = true
            Toast.makeText(
                context,
                "已开启同步，请在系统页面允许悬浮窗权限",
                Toast.LENGTH_SHORT,
            ).show()

            if (!SyncServiceController.openOverlayPermissionSettings(context)) {
                Toast.makeText(
                    context,
                    "无法打开悬浮窗设置页，请手动到系统设置中授权",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    LaunchedEffect(syncEnabled, hasSession) {
        updateSyncServices(
            showOverlayHint = true,
            syncEnabledValue = syncEnabled,
            hasSessionValue = hasSession,
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // 从权限页或系统设置返回时，重新对齐服务状态和悬浮球状态。
                updateSyncServices(showOverlayHint = false)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            SyncServiceController.stopAll(appContext)
        }
    }
}
