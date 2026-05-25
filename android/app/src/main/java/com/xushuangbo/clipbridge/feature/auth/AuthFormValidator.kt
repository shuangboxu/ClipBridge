package com.xushuangbo.clipbridge.feature.auth

import com.xushuangbo.clipbridge.core.network.ServiceAddressFormatter

object AuthFormValidator {
    fun validate(uiState: AuthUiState): String? {
        val serviceAddressError = ServiceAddressFormatter.validate(uiState.serviceAddress)
        if (serviceAddressError != null) {
            return serviceAddressError
        }

        if (uiState.username.trim().length < 3) {
            return "用户名至少需要 3 个字符"
        }
        if (uiState.password.length < 8) {
            return "密码至少需要 8 位"
        }
        if (uiState.mode == AuthMode.Register && uiState.password != uiState.confirmPassword) {
            return "两次输入的密码不一致"
        }

        val trimmedDeviceName = uiState.deviceName.trim()
        if (trimmedDeviceName.length > 128) {
            return "设备名最多 128 个字符"
        }

        return null
    }
}
