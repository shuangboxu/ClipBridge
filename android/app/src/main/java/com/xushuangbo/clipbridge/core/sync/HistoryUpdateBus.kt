package com.xushuangbo.clipbridge.core.sync

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 历史更新事件总线。
 *
 * 后台同步服务在发现历史有新增内容后，会往这里发一条通知；
 * 历史页 ViewModel 订阅这条通知后，就可以决定是自动刷新，还是只提示“有新内容”。
 */
class HistoryUpdateBus {
    private val _events = MutableSharedFlow<HistoryUpdateEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<HistoryUpdateEvent> = _events.asSharedFlow()

    fun notifyHistoryUpdated(itemCount: Int = 1) {
        _events.tryEmit(
            HistoryUpdateEvent(
                itemCount = itemCount.coerceAtLeast(1),
            ),
        )
    }
}

data class HistoryUpdateEvent(
    val itemCount: Int = 1,
)
