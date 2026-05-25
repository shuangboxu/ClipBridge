package com.xushuangbo.clipbridge.app

import android.net.Uri

object AppRoute {
    const val Splash = "splash"
    const val Main = "main"
    const val AuthPattern = "auth?message={message}"

    fun auth(message: String? = null): String {
        if (message.isNullOrBlank()) {
            return "auth"
        }
        return "auth?message=${Uri.encode(message)}"
    }
}
