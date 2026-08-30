package com.rakshaksetu.app.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = RakshakSetuBlue,
    onPrimary = SurfaceWhite,
    primaryContainer = RakshakSetuBlueLight,
    onPrimaryContainer = RakshakSetuBlueDark,
    secondary = SafeGreenContainer,
    onSecondary = SurfaceWhite,
    background = BackgroundLight,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = BorderColor,
    onSurfaceVariant = TextSecondary,
    error = BlockedRedContainer,
    onError = SurfaceWhite,
)

@Composable
fun RakshakSetuTheme(content: @Composable () -> Unit) {
    val colorScheme = LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = SurfaceWhite.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = RakshakSetuTypography,
        content = content
    )
}
