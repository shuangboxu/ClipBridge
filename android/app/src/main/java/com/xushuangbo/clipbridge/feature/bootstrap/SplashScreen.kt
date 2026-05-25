package com.xushuangbo.clipbridge.feature.bootstrap

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xushuangbo.clipbridge.R
import com.xushuangbo.clipbridge.app.AppTestTags

@Composable
fun SplashRoute(
    viewModel: BootstrapViewModel = viewModel(),
    onRequireAuth: (String) -> Unit,
    onReady: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        when (state) {
            is BootstrapState.LoggedOut -> onRequireAuth(state.message)
            is BootstrapState.MissingServer -> onRequireAuth(state.message)
            is BootstrapState.Ready -> onReady()
            else -> Unit
        }
    }

    SplashScreen(
        state = state,
        onRetry = viewModel::bootstrap,
    )
}

@Composable
fun SplashScreen(
    state: BootstrapState,
    onRetry: () -> Unit,
) {
    var revealContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        revealContent = true
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (revealContent) 1f else 0f,
        label = "splash_alpha",
    )
    val contentTopPadding by animateDpAsState(
        targetValue = if (revealContent) 8.dp else 32.dp,
        label = "splash_top_padding",
    )

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.background,
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(horizontal = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = contentTopPadding)
                .alpha(contentAlpha),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(modifier = Modifier.height(1.dp))

            Image(
                painter = painterResource(id = R.drawable.clipbridge_splash),
                contentDescription = "ClipBridge 启动画面",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(16.dp)
                    .weight(1f, fill = false),
            )

            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "ClipBridge",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag(AppTestTags.SplashStatus),
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    if (state is BootstrapState.Error) {
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("重试启动检查")
                        }
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
