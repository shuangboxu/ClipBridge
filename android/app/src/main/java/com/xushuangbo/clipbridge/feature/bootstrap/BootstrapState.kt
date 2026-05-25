package com.xushuangbo.clipbridge.feature.bootstrap

sealed interface BootstrapState {
    val message: String

    data object LoadingLocal : BootstrapState {
        override val message: String = "正在读取本地配置"
    }

    data class MissingServer(
        override val message: String = "请先填写服务地址",
    ) : BootstrapState

    data class LoggedOut(
        override val message: String = "请登录后继续使用",
    ) : BootstrapState

    data class CheckingAccess(
        override val message: String = "正在校验登录态",
    ) : BootstrapState

    data class Refreshing(
        override val message: String = "正在刷新登录态",
    ) : BootstrapState

    data class Ready(
        override val message: String = "欢迎回来，正在进入工作台",
    ) : BootstrapState

    data class Error(
        override val message: String,
    ) : BootstrapState
}
