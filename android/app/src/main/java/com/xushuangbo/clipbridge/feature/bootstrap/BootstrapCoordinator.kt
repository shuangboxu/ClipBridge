package com.xushuangbo.clipbridge.feature.bootstrap

import com.xushuangbo.clipbridge.core.network.AuthApiClient
import com.xushuangbo.clipbridge.core.network.AuthApiException
import com.xushuangbo.clipbridge.core.network.TokenBundle
import com.xushuangbo.clipbridge.core.session.SessionStore
import java.io.IOException

class BootstrapCoordinator(
    private val sessionStore: SessionStore,
    private val authApiClient: AuthApiClient,
) {
    suspend fun run(reportState: (BootstrapState) -> Unit): BootstrapState {
        reportState(BootstrapState.LoadingLocal)
        val currentSession = sessionStore.readSession()

        if (!currentSession.hasServerAddress()) {
            return BootstrapState.MissingServer()
        }
        if (!currentSession.hasCompleteAuth()) {
            return BootstrapState.LoggedOut()
        }

        reportState(BootstrapState.CheckingAccess())

        return try {
            val account = authApiClient.getCurrentAccount(currentSession) {
                reportState(BootstrapState.Refreshing())
            }

            val tokens = account.tokens ?: TokenBundle(
                accessToken = currentSession.accessToken,
                refreshToken = currentSession.refreshToken,
            )

            // 启动校验通过后顺手把用户名和轮换后的 token 写回本地，
            // 保证主页和后续请求看到的是最新状态。
            sessionStore.saveAuthBundle(
                baseUrl = currentSession.baseUrl,
                username = account.username,
                deviceName = currentSession.deviceName,
                currentDeviceId = account.currentDeviceId,
                tokens = tokens,
            )

            BootstrapState.Ready()
        } catch (error: AuthApiException) {
            if (error.httpCode == 401) {
                sessionStore.clearAuth()
                BootstrapState.LoggedOut("登录已失效，请重新登录")
            } else {
                BootstrapState.Error(error.message ?: "服务暂时不可用，请稍后重试")
            }
        } catch (error: IOException) {
            BootstrapState.Error(error.message ?: "网络异常，请稍后重试")
        }
    }
}
