package com.jagadaun.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SoftAccent,
    onSurfaceVariant = TextSecondary,
    primaryContainer = SoftAccent,
    onPrimaryContainer = Primary,
    error = Error,
    onError = Color.White,
    errorContainer = ErrorContainer,
    onErrorContainer = Error,
    outline = Outline,
    outlineVariant = Divider,
)

@Composable
fun JagaDaunTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = JagaDaunTypography,
        content = content,
    )
}
