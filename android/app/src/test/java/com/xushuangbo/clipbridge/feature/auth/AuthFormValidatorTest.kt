package com.xushuangbo.clipbridge.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthFormValidatorTest {
    @Test
    fun validate_returnsMismatchMessageForRegisterMode() {
        val uiState = AuthUiState(
            mode = AuthMode.Register,
            serviceAddress = "http://127.0.0.1:18080",
            username = "alice",
            password = "password123",
            confirmPassword = "password456",
            deviceName = "android-pixel",
        )

        assertEquals("两次输入的密码不一致", AuthFormValidator.validate(uiState))
    }

    @Test
    fun validate_acceptsValidLoginForm() {
        val uiState = AuthUiState(
            mode = AuthMode.Login,
            serviceAddress = "http://127.0.0.1:18080",
            username = "alice",
            password = "password123",
            deviceName = "android-pixel",
        )

        assertNull(AuthFormValidator.validate(uiState))
    }
}
