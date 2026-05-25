package com.xushuangbo.clipbridge.feature.auth

data class AuthUiState(
    val mode: AuthMode = AuthMode.Login,
    val serviceAddress: String = "",
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val deviceName: String = "",
    val errorMessage: String? = null,
    val isSubmitting: Boolean = false,
    val isTestingConnection: Boolean = false,
) {
    val submitButtonText: String
        get() = if (mode == AuthMode.Login) "登录" else "注册"
}
