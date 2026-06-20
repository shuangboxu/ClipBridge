package com.xushuangbo.clipbridge.app

import android.content.Context
import com.xushuangbo.clipbridge.core.files.AndroidDocumentFileGateway
import com.xushuangbo.clipbridge.core.files.FileTransferCoordinator
import com.xushuangbo.clipbridge.core.network.AdminApiClient
import com.xushuangbo.clipbridge.core.network.AuthApiClient
import com.xushuangbo.clipbridge.core.network.ClipboardApiClient
import com.xushuangbo.clipbridge.core.network.FileApiClient
import com.xushuangbo.clipbridge.core.network.HttpAdminApiClient
import com.xushuangbo.clipbridge.core.network.HttpAuthApiClient
import com.xushuangbo.clipbridge.core.network.HttpClipboardApiClient
import com.xushuangbo.clipbridge.core.network.HttpFileApiClient
import com.xushuangbo.clipbridge.core.network.HttpPublicShareApiClient
import com.xushuangbo.clipbridge.core.network.HttpRequestApiClient
import com.xushuangbo.clipbridge.core.network.HttpShareApiClient
import com.xushuangbo.clipbridge.core.network.PublicShareApiClient
import com.xushuangbo.clipbridge.core.network.RequestApiClient
import com.xushuangbo.clipbridge.core.network.ShareApiClient
import com.xushuangbo.clipbridge.core.share.PreferenceShareRulesStore
import com.xushuangbo.clipbridge.core.share.ShareCoordinator
import com.xushuangbo.clipbridge.core.share.ShareRulesStore
import com.xushuangbo.clipbridge.core.session.PreferenceSessionStore
import com.xushuangbo.clipbridge.core.session.SessionStore
import com.xushuangbo.clipbridge.core.sync.ClipboardSyncCoordinator
import com.xushuangbo.clipbridge.core.sync.HistoryUpdateBus
import com.xushuangbo.clipbridge.feature.auth.buildDefaultDeviceName

class AppContainer(
    val sessionStore: SessionStore,
    val authApiClient: AuthApiClient,
    val requestApiClient: RequestApiClient,
    val adminApiClient: AdminApiClient,
    val clipboardApiClient: ClipboardApiClient,
    val fileApiClient: FileApiClient,
    val shareApiClient: ShareApiClient,
    val publicShareApiClient: PublicShareApiClient,
    val clipboardSyncCoordinator: ClipboardSyncCoordinator,
    val fileTransferCoordinator: FileTransferCoordinator,
    val shareCoordinator: ShareCoordinator,
    val shareRulesStore: ShareRulesStore,
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
            val requestApiClient = HttpRequestApiClient(authApiClient = authApiClient)
            val adminApiClient = HttpAdminApiClient(authApiClient = authApiClient)
            val clipboardApiClient = HttpClipboardApiClient(authApiClient = authApiClient)
            val fileApiClient = HttpFileApiClient(authApiClient = authApiClient)
            val shareApiClient = HttpShareApiClient(authApiClient = authApiClient)
            val publicShareApiClient = HttpPublicShareApiClient()
            val documentFileGateway = AndroidDocumentFileGateway(appContext)

            return AppContainer(
                sessionStore = sessionStore,
                authApiClient = authApiClient,
                requestApiClient = requestApiClient,
                adminApiClient = adminApiClient,
                clipboardApiClient = clipboardApiClient,
                fileApiClient = fileApiClient,
                shareApiClient = shareApiClient,
                publicShareApiClient = publicShareApiClient,
                clipboardSyncCoordinator = ClipboardSyncCoordinator(
                    sessionStore = sessionStore,
                    clipboardApiClient = clipboardApiClient,
                ),
                fileTransferCoordinator = FileTransferCoordinator(
                    sessionStore = sessionStore,
                    fileApiClient = fileApiClient,
                    documentFileGateway = documentFileGateway,
                ),
                shareCoordinator = ShareCoordinator(
                    sessionStore = sessionStore,
                    shareApiClient = shareApiClient,
                    documentFileGateway = documentFileGateway,
                ),
                shareRulesStore = PreferenceShareRulesStore(preferences),
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
