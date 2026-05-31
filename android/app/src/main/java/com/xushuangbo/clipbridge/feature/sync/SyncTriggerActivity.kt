package com.xushuangbo.clipbridge.feature.sync

import android.content.Context
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity

class SyncTriggerActivity : ComponentActivity() {
    // 避免透明触发页因为系统重建或重复投递而并发触发多次同步。
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyNoAnimationTransition()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handled = false
    }

    override fun onResume() {
        super.onResume()
        // 某些系统在 onResume 时还没真正给透明页焦点，这里延后一帧再读取剪贴板。
        window.decorView.post {
            triggerOnceIfNeeded()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            return
        }

        // 悬浮球拉起的透明页拿到窗口焦点后，再补一次触发机会，避免偶发丢读。
        window.decorView.post {
            triggerOnceIfNeeded()
        }
    }

    private fun triggerOnceIfNeeded() {
        if (handled) {
            return
        }
        handled = true

        val source = intent.getStringExtra(EXTRA_TRIGGER_SOURCE).orEmpty().ifBlank { "快捷触发" }
        val snapshot = readForegroundClipboardSnapshot()

        ClipboardSyncService.syncNow(
            context = this,
            source = source,
            preferredText = snapshot?.text,
            preferredLabel = snapshot?.label,
        )

        // 透明页留极短时间，确保前台读取和服务启动已经提交，再立刻退出。
        window.decorView.postDelayed(
            { closeTransientTask() },
            CLOSE_DELAY_MS,
        )
    }

    override fun finish() {
        super.finish()
        applyNoAnimationTransition()
    }

    private fun readForegroundClipboardSnapshot(): ClipboardSnapshot? {
        return try {
            val clipboardManager = getSystemService(ClipboardManager::class.java) ?: return null
            val clip = clipboardManager.primaryClip ?: return null
            if (clip.itemCount <= 0) {
                return null
            }

            ClipboardSnapshot(
                label = clipboardManager.primaryClipDescription?.label?.toString().orEmpty(),
                text = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty().trim(),
            )
        } catch (_: SecurityException) {
            // 某些 ROM 即使前台触发也可能拒绝读取，这时退回到“只补拉远端”兜底。
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun closeTransientTask() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask()
        } else {
            finish()
        }
        applyNoAnimationTransition()
    }

    @Suppress("DEPRECATION")
    private fun applyNoAnimationTransition() {
        overridePendingTransition(0, 0)
    }

    private data class ClipboardSnapshot(
        val label: String,
        val text: String,
    )

    companion object {
        const val EXTRA_TRIGGER_SOURCE = "extra_trigger_source"
        private const val CLOSE_DELAY_MS = 80L

        fun createLaunchIntent(
            context: Context,
            source: String,
        ): Intent {
            return Intent(context, SyncTriggerActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
                putExtra(EXTRA_TRIGGER_SOURCE, source)
            }
        }
    }
}
