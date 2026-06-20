package com.xushuangbo.clipbridge.feature.shell

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

internal fun formatShellBytes(value: Long): String {
    if (value <= 0L) {
        return "0 B"
    }
    if (value < 1024L) {
        return "$value B"
    }

    val kilo = 1024.0
    val mega = kilo * 1024
    val giga = mega * 1024
    val tera = giga * 1024

    return when {
        value < mega -> String.format("%.1f KB", value / kilo)
        value < giga -> String.format("%.1f MB", value / mega)
        value < tera -> String.format("%.1f GB", value / giga)
        else -> String.format("%.1f TB", value / tera)
    }
}

private const val BANDWIDTH_BASE = 1024.0

private fun trimBandwidthText(raw: String): String {
    return raw.trimEnd('0').trimEnd('.')
}

private fun formatBandwidthText(value: Double): String {
    return trimBandwidthText(String.format(Locale.US, "%.3f", value))
}

internal fun formatShellBandwidth(value: Int): String {
    if (value <= 0) {
        return "0 MB/s"
    }

    return "${formatBandwidthText(value / BANDWIDTH_BASE)} MB/s"
}

internal fun bandwidthKbpsToMbDraft(value: Int): String {
    if (value <= 0) {
        return ""
    }

    return formatBandwidthText(value / BANDWIDTH_BASE)
}

internal fun bandwidthMbDraftToKbpsOrNull(value: String): Int? {
    val normalized = value.trim().toDoubleOrNull() ?: return null
    if (normalized <= 0.0) {
        return null
    }

    // 中文注释：接口字段名仍然是 kbps，但服务端历史上一直按 KB/s 解释。
    // 这里统一把页面输入的 MB/s 换算回旧字段，避免改动现有接口契约。
    return (normalized * BANDWIDTH_BASE).roundToInt().coerceAtLeast(1)
}

internal fun formatShellLocalDateTime(
    value: String,
    pattern: String = "yyyy-MM-dd HH:mm",
): String {
    if (value.isBlank()) {
        return "-"
    }

    return try {
        val localDateTime = OffsetDateTime.parse(value)
            .atZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime()
        DateTimeFormatter.ofPattern(pattern).format(localDateTime)
    } catch (_: Exception) {
        value
    }
}
