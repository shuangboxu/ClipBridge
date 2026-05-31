package com.xushuangbo.clipbridge.feature.sync

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import com.xushuangbo.clipbridge.R
import kotlin.math.abs

class FloatingSyncService : Service() {
    private lateinit var windowManager: WindowManager
    private var floatingButton: ImageButton? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_STOP -> {
                removeFloatingButton()
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                if (!SyncServiceController.canDrawOverlays(this)) {
                    removeFloatingButton()
                    stopSelf()
                    return START_NOT_STICKY
                }

                showFloatingButtonIfNeeded()
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        removeFloatingButton()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showFloatingButtonIfNeeded() {
        if (floatingButton != null) {
            return
        }

        // 悬浮球保持最小尺寸和不抢焦点，避免影响用户正常操作。
        val params = WindowManager.LayoutParams(
            dpToPx(56),
            dpToPx(56),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(20)
            y = dpToPx(240)
        }

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        val button = ImageButton(this).apply {
            setImageResource(R.drawable.sync_toggle_on)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "ClipBridge 悬浮球"
            setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xEE3F7DFF.toInt())
            }
            alpha = 0.96f

            setOnTouchListener(object : View.OnTouchListener {
                private var downRawX = 0f
                private var downRawY = 0f
                private var startX = 0
                private var startY = 0
                private var moved = false

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    // 点击触发同步，拖动只改变悬浮球位置，两种操作共用同一个触摸监听。
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downRawX = event.rawX
                            downRawY = event.rawY
                            startX = params.x
                            startY = params.y
                            moved = false
                            return true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - downRawX).toInt()
                            val dy = (event.rawY - downRawY).toInt()
                            if (!moved && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                                moved = true
                            }
                            params.x = startX + dx
                            params.y = (startY + dy).coerceAtLeast(0)
                            windowManager.updateViewLayout(this@apply, params)
                            return true
                        }

                        MotionEvent.ACTION_UP -> {
                            if (!moved) {
                                triggerQuickSyncByOverlay()
                            }
                            return true
                        }
                    }
                    return false
                }
            })
        }

        windowManager.addView(button, params)
        floatingButton = button
    }

    private fun removeFloatingButton() {
        val button = floatingButton ?: return
        runCatching { windowManager.removeView(button) }
        floatingButton = null
    }

    private fun triggerQuickSyncByOverlay() {
        val intent = SyncTriggerActivity.createLaunchIntent(this, source = "悬浮球")
        runCatching { startActivity(intent) }
    }

    private fun dpToPx(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        const val ACTION_START = "com.xushuangbo.clipbridge.action.START_FLOATING_SYNC"
        const val ACTION_STOP = "com.xushuangbo.clipbridge.action.STOP_FLOATING_SYNC"

        fun start(context: Context) {
            context.startService(
                Intent(context, FloatingSyncService::class.java).apply {
                    action = ACTION_START
                },
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, FloatingSyncService::class.java).apply {
                    action = ACTION_STOP
                },
            )
        }
    }
}
