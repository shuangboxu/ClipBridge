package com.xushuangbo.clipbridge.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import com.xushuangbo.clipbridge.app.AppTestTags
import com.xushuangbo.clipbridge.ui.components.PageErrorBanner

@Composable
fun AuthRoute(
    viewModel: AuthViewModel = viewModel(),
    incomingMessage: String?,
    onAuthSuccess: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(incomingMessage) {
        if (!incomingMessage.isNullOrBlank()) {
            viewModel.showErrorMessage(incomingMessage)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.authSuccessEvents.collect {
            onAuthSuccess()
        }
    }

    LaunchedEffect(viewModel, context) {
        viewModel.toastEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    AuthScreen(
        uiState = uiState,
        onModeChange = viewModel::switchMode,
        onServiceAddressChange = viewModel::updateServiceAddress,
        onUsernameChange = viewModel::updateUsername,
        onPasswordChange = viewModel::updatePassword,
        onConfirmPasswordChange = viewModel::updateConfirmPassword,
        onDeviceNameChange = viewModel::updateDeviceName,
        onTestConnection = viewModel::testConnection,
        onSubmit = viewModel::submit,
    )
}

@Composable
fun AuthScreen(
    uiState: AuthUiState,
    onModeChange: (AuthMode) -> Unit,
    onServiceAddressChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onSubmit: () -> Unit,
) {
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
            MaterialTheme.colorScheme.background,
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        HeroBadge()
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "ClipBridge",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "云桥把手机、桌面和网页端连成一条稳定的复制链路。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(28.dp))

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            shape = RoundedCornerShape(32.dp),
            shadowElevation = 18.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
            ) {
                AuthModeSwitch(
                    selectedMode = uiState.mode,
                    onModeChange = onModeChange,
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = if (uiState.mode == AuthMode.Login) {
                        "输入服务地址和账号信息，继续连接当前设备。"
                    } else {
                        "创建账号后会直接把当前设备登记到你的云桥里。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(18.dp))

                OutlinedTextField(
                    value = uiState.serviceAddress,
                    onValueChange = onServiceAddressChange,
                    label = { Text("服务地址") },
                    placeholder = { Text("http://127.0.0.1:18080") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        capitalization = KeyboardCapitalization.None,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(AppTestTags.AuthServiceAddressField),
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = uiState.username,
                    onValueChange = onUsernameChange,
                    label = { Text("用户名") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(AppTestTags.AuthUsernameField),
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = onPasswordChange,
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(AppTestTags.AuthPasswordField),
                )

                AnimatedVisibility(visible = uiState.mode == AuthMode.Register) {
                    Column {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = uiState.confirmPassword,
                            onValueChange = onConfirmPasswordChange,
                            label = { Text("确认密码") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(AppTestTags.AuthConfirmPasswordField),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = uiState.deviceName,
                    onValueChange = onDeviceNameChange,
                    label = { Text("设备名") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(AppTestTags.AuthDeviceNameField),
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "平台固定为 android。设备名留空时会自动回退到默认值。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                uiState.errorMessage?.let { errorMessage ->
                    Spacer(modifier = Modifier.height(18.dp))
                    PageErrorBanner(message = errorMessage)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(
                        onClick = onTestConnection,
                        enabled = !uiState.isSubmitting && !uiState.isTestingConnection,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                    ) {
                        Text(if (uiState.isTestingConnection) "测试中..." else "测试连接")
                    }

                    Button(
                        onClick = onSubmit,
                        enabled = !uiState.isSubmitting && !uiState.isTestingConnection,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag(AppTestTags.AuthSubmitButton),
                    ) {
                        Icon(
                            imageVector = if (uiState.mode == AuthMode.Login) {
                                Icons.AutoMirrored.Outlined.Login
                            } else {
                                Icons.Outlined.PersonAdd
                            },
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (uiState.isSubmitting) "提交中..." else uiState.submitButtonText)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun HeroBadge() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
        modifier = Modifier.wrapContentWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudQueue,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "连接你的多端云剪切板",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun AuthModeSwitch(
    selectedMode: AuthMode,
    onModeChange: (AuthMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AuthModeChip(
            title = "登录",
            icon = Icons.AutoMirrored.Outlined.Login,
            selected = selectedMode == AuthMode.Login,
            testTag = AppTestTags.AuthLoginToggle,
            onClick = { onModeChange(AuthMode.Login) },
            modifier = Modifier.weight(1f),
        )
        AuthModeChip(
            title = "注册",
            icon = Icons.Outlined.PersonAdd,
            selected = selectedMode == AuthMode.Register,
            testTag = AppTestTags.AuthRegisterToggle,
            onClick = { onModeChange(AuthMode.Register) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AuthModeChip(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
    }
}
