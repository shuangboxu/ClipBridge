package com.xushuangbo.clipbridge.feature.shell

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

internal fun formatShellKbps(value: Int): String {
    if (value <= 0) {
        return "0 Kbps"
    }
    if (value < 1024) {
        return "$value Kbps"
    }

    val mega = 1024.0
    return String.format("%.1f Mbps", value / mega)
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
