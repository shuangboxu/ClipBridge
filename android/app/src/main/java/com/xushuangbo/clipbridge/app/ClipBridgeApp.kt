package com.xushuangbo.clipbridge.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navOptions
import com.xushuangbo.clipbridge.feature.auth.AuthRoute
import com.xushuangbo.clipbridge.feature.auth.AuthViewModel
import com.xushuangbo.clipbridge.feature.bootstrap.BootstrapViewModel
import com.xushuangbo.clipbridge.feature.bootstrap.SplashRoute
import com.xushuangbo.clipbridge.feature.shell.DeviceViewModel
import com.xushuangbo.clipbridge.feature.shell.FilesViewModel
import com.xushuangbo.clipbridge.feature.shell.HistoryViewModel
import com.xushuangbo.clipbridge.feature.shell.MainShellRoute
import com.xushuangbo.clipbridge.feature.shell.SettingsViewModel

/**
 * ClipBridgeApp 是整个 Android App 的 Compose 根节点。
 * 这个函数主要负责三件事：
 * 1. 创建全局依赖容器 AppContainer
 * 2. 创建导航控制器 navController
 * 3. 定义 Splash、Auth、Main 三个主路由之间的跳转关系
 */
@Composable
fun ClipBridgeApp(
    // 默认可以不传。测试时可以传入假的 AppContainer，方便做单元测试或 UI 测试。
    appContainer: AppContainer? = null,
) {
    // 获取当前 Android Context。
    // AppContainer.create(context) 需要用它来创建 SessionStore、网络客户端等对象。
    val context = LocalContext.current

    // resolvedContainer 是最终真正使用的依赖容器。
    //
    // 如果外部传入了 appContainer，就使用外部传入的。
    // 如果没有传入，就在这里创建一个真实的 AppContainer。
    //
    // remember(context) 的作用：
    // Compose 会因为状态变化多次重组 UI，但我们不希望每次重组都重新创建 AppContainer。
    // 所以用 remember 把它记住。
    val resolvedContainer = appContainer ?: remember(context) {
        AppContainer.create(context)
    }

    // 创建导航控制器。
    // 它负责页面跳转、返回栈管理、路由参数传递。
    val navController = rememberNavController()

    // NavHost 是整个 App 的导航容器。
    // startDestination 表示 App 启动后第一个进入 Splash 页面。
    NavHost(
        navController = navController,
        startDestination = AppRoute.Splash,
    ) {
        /**
         * 路由一：Splash 启动页
         *
         * 作用：
         * 1. 读取本地 Session
         * 2. 检查用户是否已经登录
         * 3. 决定进入 Auth 登录页，还是 Main 主界面
         */
        composable(AppRoute.Splash) {
            // 创建 Splash 页面使用的 ViewModel。
            //
            // 这里不能直接 new BootstrapViewModel，
            // 因为 ViewModel 需要交给 Android 生命周期系统管理。
            //
            // factory 用来把 SessionStore 和 AuthApiClient 传进去。
            val bootstrapViewModel: BootstrapViewModel = viewModel(
                factory = BootstrapViewModel.factory(
                    sessionStore = resolvedContainer.sessionStore,
                    authApiClient = resolvedContainer.authApiClient,
                ),
            )

            SplashRoute(
                viewModel = bootstrapViewModel,

                // 启动校验发现用户未登录、token 失效、或需要重新认证时调用。
                onRequireAuth = { message ->
                    navController.navigate(
                        // 跳转到 Auth 登录页，并把提示信息 message 传过去。
                        AppRoute.auth(message),
                        navOptions {
                            // 跳转后把 Splash 从返回栈移除。
                            // 否则用户在登录页按返回键，可能又回到 Splash。
                            popUpTo(AppRoute.Splash) {
                                inclusive = true
                            }
                        },
                    )
                },

                // 启动校验通过，说明用户可以直接进入主界面。
                onReady = {
                    navController.navigate(
                        AppRoute.Main,
                        navOptions {
                            // 跳转到 Main 后移除 Splash。
                            // 这样主界面按返回键不会回到启动页。
                            popUpTo(AppRoute.Splash) {
                                inclusive = true
                            }
                        },
                    )
                },
            )
        }

        /**
         * 路由二：Auth 登录 / 注册页
         *
         * AppRoute.AuthPattern 支持带 message 参数。
         * 例如：
         * token 失效后跳转登录页，可以显示“登录已失效，请重新登录”。
         */
        composable(
            route = AppRoute.AuthPattern,
            arguments = listOf(
                navArgument("message") {
                    // message 是字符串类型。
                    type = NavType.StringType

                    // 允许为空。
                    nullable = true

                    // 默认没有提示信息。
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            // 创建登录页 ViewModel。
            //
            // AuthViewModel 需要：
            // 1. sessionStore：保存 token、服务地址等本地会话信息
            // 2. authApiClient：调用登录、注册、测试连接等接口
            // 3. defaultDeviceName：注册 / 登录设备时使用的默认设备名
            val authViewModel: AuthViewModel = viewModel(
                factory = AuthViewModel.factory(
                    sessionStore = resolvedContainer.sessionStore,
                    authApiClient = resolvedContainer.authApiClient,
                    defaultDeviceName = resolvedContainer.defaultDeviceName,
                ),
            )

            AuthRoute(
                viewModel = authViewModel,

                // 从导航参数中取出 message，交给 Auth 页面显示。
                incomingMessage = backStackEntry.arguments?.getString("message"),

                // 登录或注册成功后进入主界面。
                onAuthSuccess = {
                    navController.navigate(
                        AppRoute.Main,
                        navOptions {
                            // 登录成功后移除 Auth 页面。
                            // 否则用户进入主界面后按返回键，可能又回到登录页。
                            popUpTo(AppRoute.AuthPattern) {
                                inclusive = true
                            }
                        },
                    )
                },
            )
        }

        /**
         * 路由三：Main 主界面
         *
         * MainShellRoute 通常包含底部导航：
         * 首页 / 历史 / AI / 设置
         *
         * 这里同时创建 SettingsViewModel 和 DeviceViewModel，
         * 因为设置页和设备页属于主界面内部的功能。
         */
        composable(AppRoute.Main) {
            // 设置页 ViewModel。
            // 负责服务地址、退出登录、设置项等相关逻辑。
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(
                    sessionStore = resolvedContainer.sessionStore,
                    authApiClient = resolvedContainer.authApiClient,
                ),
            )

            // 设备页 ViewModel。
            // 负责设备列表、修改设备名、强制下线设备等逻辑。
            val deviceViewModel: DeviceViewModel = viewModel(
                factory = DeviceViewModel.factory(
                    sessionStore = resolvedContainer.sessionStore,
                    authApiClient = resolvedContainer.authApiClient,
                ),
            )

            // 历史页 ViewModel。
            // 负责第一轮文本闭环：历史查询、手动上传、手动补拉和 ACK。
            val historyViewModel: HistoryViewModel = viewModel(
                factory = HistoryViewModel.factory(
                    sessionStore = resolvedContainer.sessionStore,
                    clipboardSyncCoordinator = resolvedContainer.clipboardSyncCoordinator,
                    historyUpdateBus = resolvedContainer.historyUpdateBus,
                ),
            )

            // 文件页 ViewModel。
            // 负责文件上传、分页查询、下载、重命名和删除。
            val filesViewModel: FilesViewModel = viewModel(
                factory = FilesViewModel.factory(
                    sessionStore = resolvedContainer.sessionStore,
                    fileTransferCoordinator = resolvedContainer.fileTransferCoordinator,
                ),
            )

            MainShellRoute(
                settingsViewModel = settingsViewModel,
                deviceViewModel = deviceViewModel,
                historyViewModel = historyViewModel,
                filesViewModel = filesViewModel,

                // 主界面里如果遇到 401、token 失效、退出登录等情况，
                // 就跳转回 Auth 登录页。
                onRequireAuth = { message ->
                    navController.navigate(
                        AppRoute.auth(message),
                        navOptions {
                            // 移除 Main 页面。
                            // 防止用户未登录时按返回键又回到主界面。
                            popUpTo(AppRoute.Main) {
                                inclusive = true
                            }
                        },
                    )
                },
            )
        }
    }
}
