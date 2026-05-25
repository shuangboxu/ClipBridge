package com.xushuangbo.clipbridge.app

import android.content.Context
import com.xushuangbo.clipbridge.core.network.AuthApiClient
import com.xushuangbo.clipbridge.core.network.HttpAuthApiClient
import com.xushuangbo.clipbridge.core.session.PreferenceSessionStore
import com.xushuangbo.clipbridge.core.session.SessionStore
import com.xushuangbo.clipbridge.feature.auth.buildDefaultDeviceName

class AppContainer(
    val sessionStore: SessionStore,
    val authApiClient: AuthApiClient,
    val defaultDeviceName: String,
) {
    companion object {
        fun create(context: Context): AppContainer {
            val appContext = context.applicationContext
            val preferences = appContext.getSharedPreferences(
                "clipbridge_session",
                Context.MODE_PRIVATE,
            )

            return AppContainer(
                sessionStore = PreferenceSessionStore(preferences),
                authApiClient = HttpAuthApiClient(),
                defaultDeviceName = buildDefaultDeviceName(),
            )
        }
    }
}
