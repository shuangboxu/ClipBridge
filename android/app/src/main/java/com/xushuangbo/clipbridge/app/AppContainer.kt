package com.xushuangbo.clipbridge.app

import android.content.Context
import com.xushuangbo.clipbridge.core.network.AuthApiClient
import com.xushuangbo.clipbridge.core.network.ClipboardApiClient
import com.xushuangbo.clipbridge.core.network.HttpAuthApiClient
import com.xushuangbo.clipbridge.core.network.HttpClipboardApiClient
import com.xushuangbo.clipbridge.core.session.PreferenceSessionStore
import com.xushuangbo.clipbridge.core.session.SessionStore
import com.xushuangbo.clipbridge.feature.auth.buildDefaultDeviceName

class AppContainer(
    val sessionStore: SessionStore,
    val authApiClient: AuthApiClient,
    val clipboardApiClient: ClipboardApiClient,
    val defaultDeviceName: String,
) {
    companion object {
        fun create(context: Context): AppContainer {
            val appContext = context.applicationContext
            val preferences = appContext.getSharedPreferences(
                "clipbridge_session",
                Context.MODE_PRIVATE,
            )
            val authApiClient = HttpAuthApiClient()

            return AppContainer(
                sessionStore = PreferenceSessionStore(preferences),
                authApiClient = authApiClient,
                clipboardApiClient = HttpClipboardApiClient(authApiClient = authApiClient),
                defaultDeviceName = buildDefaultDeviceName(),
            )
        }
    }
}
