package com.xushuangbo.clipbridge.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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
import com.xushuangbo.clipbridge.feature.shell.MainShellRoute
import com.xushuangbo.clipbridge.feature.shell.SettingsViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ClipBridgeApp(
    appContainer: AppContainer? = null,
) {
    val context = LocalContext.current
    val resolvedContainer = appContainer ?: remember(context) {
        AppContainer.create(context)
    }
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppRoute.Splash,
    ) {
        composable(AppRoute.Splash) {
            val bootstrapViewModel: BootstrapViewModel = viewModel(
                factory = BootstrapViewModel.factory(
                    sessionStore = resolvedContainer.sessionStore,
                    authApiClient = resolvedContainer.authApiClient,
                ),
            )

            SplashRoute(
                viewModel = bootstrapViewModel,
                onRequireAuth = { message ->
                    navController.navigate(
                        AppRoute.auth(message),
                        navOptions {
                            popUpTo(AppRoute.Splash) {
                                inclusive = true
                            }
                        },
                    )
                },
                onReady = {
                    navController.navigate(
                        AppRoute.Main,
                        navOptions {
                            popUpTo(AppRoute.Splash) {
                                inclusive = true
                            }
                        },
                    )
                },
            )
        }

        composable(
            route = AppRoute.AuthPattern,
            arguments = listOf(
                navArgument("message") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val authViewModel: AuthViewModel = viewModel(
                factory = AuthViewModel.factory(
                    sessionStore = resolvedContainer.sessionStore,
                    authApiClient = resolvedContainer.authApiClient,
                    defaultDeviceName = resolvedContainer.defaultDeviceName,
                ),
            )

            AuthRoute(
                viewModel = authViewModel,
                incomingMessage = backStackEntry.arguments?.getString("message"),
                onAuthSuccess = {
                    navController.navigate(
                        AppRoute.Main,
                        navOptions {
                            popUpTo(AppRoute.AuthPattern) {
                                inclusive = true
                            }
                        },
                    )
                },
            )
        }

        composable(AppRoute.Main) {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(
                    sessionStore = resolvedContainer.sessionStore,
                    authApiClient = resolvedContainer.authApiClient,
                ),
            )
            val deviceViewModel: DeviceViewModel = viewModel(
                factory = DeviceViewModel.factory(
                    sessionStore = resolvedContainer.sessionStore,
                    authApiClient = resolvedContainer.authApiClient,
                ),
            )

            MainShellRoute(
                settingsViewModel = settingsViewModel,
                deviceViewModel = deviceViewModel,
                onRequireAuth = { message ->
                    navController.navigate(
                        AppRoute.auth(message),
                        navOptions {
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
