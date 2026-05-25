package com.xushuangbo.clipbridge.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = MistSurface,
    primaryContainer = Color(0xFFDCE7FF),
    onPrimaryContainer = BrandBlueDeep,
    secondary = BrandCyan,
    onSecondary = MistSurface,
    secondaryContainer = Color(0xFFDFF4FF),
    onSecondaryContainer = Color(0xFF0C4562),
    tertiary = BrandMint,
    onTertiary = MistSurface,
    tertiaryContainer = Color(0xFFD8F6EB),
    onTertiaryContainer = Color(0xFF114737),
    background = MistBackground,
    onBackground = MistTextPrimary,
    surface = MistSurface,
    onSurface = MistTextPrimary,
    surfaceVariant = MistSurfaceSoft,
    onSurfaceVariant = MistTextSecondary,
    outline = MistStroke,
    error = BrandDanger,
    onError = MistSurface,
    errorContainer = Color(0xFFFFE1DE),
    onErrorContainer = Color(0xFF6A1D19)
)

@Composable
fun ClipBridgeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
