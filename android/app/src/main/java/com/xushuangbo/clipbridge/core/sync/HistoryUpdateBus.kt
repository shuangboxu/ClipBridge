package com.xushuangbo.clipbridge.core.sync

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 历史更新事件总线。
 *
 * 后台同步、单条删除、批量清理等动作都会往这里发通知；
 * 历史页和历史设置页订阅后，就可以统一决定是否刷新当前数据。
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
                itemCount = itemCount.coerceAtLeast(0),
            ),
        )
    }

    fun notifyHistoryChanged() {
        notifyHistoryUpdated(itemCount = 0)
    }
}

data class HistoryUpdateEvent(
    val itemCount: Int = 0,
)
