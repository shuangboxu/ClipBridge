package com.xushuangbo.clipbridge.app

import android.content.Context
import com.xushuangbo.clipbridge.core.network.AuthApiClient
import com.xushuangbo.clipbridge.core.network.ClipboardApiClient
import com.xushuangbo.clipbridge.core.network.HttpAuthApiClient
import com.xushuangbo.clipbridge.core.network.HttpClipboardApiClient
import com.xushuangbo.clipbridge.core.session.PreferenceSessionStore
import com.xushuangbo.clipbridge.core.session.SessionStore
import com.xushuangbo.clipbridge.core.sync.ClipboardSyncCoordinator
import com.xushuangbo.clipbridge.core.sync.HistoryUpdateBus
import com.xushuangbo.clipbridge.feature.auth.buildDefaultDeviceName

class AppContainer(
    val sessionStore: SessionStore,
    val authApiClient: AuthApiClient,
    val clipboardApiClient: ClipboardApiClient,
    val clipboardSyncCoordinator: ClipboardSyncCoordinator,
    val historyUpdateBus: HistoryUpdateBus,
    val defaultDeviceName: String,
) {
    companion object {
        @Volatile
        private var sharedHistoryUpdateBus: HistoryUpdateBus? = null

        fun create(context: Context): AppContainer {
            val appContext = context.applicationContext
            val preferences = appContext.getSharedPreferences(
                "clipbridge_session",
                Context.MODE_PRIVATE,
            )
            val authApiClient = HttpAuthApiClient()
            val sessionStore = PreferenceSessionStore(preferences)
            val clipboardApiClient = HttpClipboardApiClient(authApiClient = authApiClient)

            return AppContainer(
                sessionStore = sessionStore,
                authApiClient = authApiClient,
                clipboardApiClient = clipboardApiClient,
                clipboardSyncCoordinator = ClipboardSyncCoordinator(
                    sessionStore = sessionStore,
                    clipboardApiClient = clipboardApiClient,
                ),
                historyUpdateBus = obtainHistoryUpdateBus(),
                defaultDeviceName = buildDefaultDeviceName(),
            )
        }

        private fun obtainHistoryUpdateBus(): HistoryUpdateBus {
            return sharedHistoryUpdateBus ?: synchronized(this) {
                sharedHistoryUpdateBus ?: HistoryUpdateBus().also { sharedHistoryUpdateBus = it }
            }
        }
    }
}
