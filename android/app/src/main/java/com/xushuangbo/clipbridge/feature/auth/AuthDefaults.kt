package com.xushuangbo.clipbridge.feature.auth

import android.os.Build

fun buildDefaultDeviceName(
    manufacturer: String = Build.MANUFACTURER,
    model: String = Build.MODEL,
): String {
    val parts = listOf(manufacturer, model)
        .map { it.trim() }
        .filter { it.isNotBlank() }

    if (parts.isEmpty()) {
        return "android-phone"
    }

    return "android-${parts.joinToString(separator = "-")}".replace(" ", "-")
}
