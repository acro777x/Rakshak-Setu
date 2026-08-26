package com.rakshaksetu.app.elder

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Elder Mode visual system (M3): maximum-contrast dark palette, oversized type
 * scale (+30%), and heavier weights — engineered for low-vision and tremor
 * accessibility on budget Android handsets.
 */
object ElderTheme {

    const val TYPE_SCALE_FACTOR = 1.3f

    val background = Color(0xFF000000)
    val surface = Color(0xFF0A0A0A)
    val surfaceVariant = Color(0xFF161616)
    val primary = Color(0xFFFFD600)      // high-luminance amber
    val onPrimary = Color(0xFF000000)
    val safe = Color(0xFF00E676)         // pure green
    val danger = Color(0xFFFF1744)       // pure red
    val onSurface = Color(0xFFFFFFFF)

    fun colorScheme(): ColorScheme = darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        secondary = safe,
        onSecondary = Color.Black,
        background = background,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = Color(0xFFF5F5F5),
        error = danger,
        onError = Color.White
    )

    fun typography(): Typography {
        val base = Typography()
        return base.copy(
            displaySmall = scaled(base.displaySmall, FontWeight.Bold),
            headlineLarge = scaled(base.headlineLarge, FontWeight.Bold),
            headlineMedium = scaled(base.headlineMedium, FontWeight.Bold),
            titleLarge = scaled(base.titleLarge, FontWeight.Bold),
            titleMedium = scaled(base.titleMedium, FontWeight.SemiBold),
            bodyLarge = scaled(base.bodyLarge, FontWeight.Medium),
            bodyMedium = scaled(base.bodyMedium, FontWeight.Medium),
            bodySmall = scaled(base.bodySmall, FontWeight.Normal),
            labelLarge = scaled(base.labelLarge, FontWeight.Bold),
            labelMedium = scaled(base.labelMedium, FontWeight.SemiBold)
        )
    }

    private fun scaled(style: TextStyle, weight: FontWeight): TextStyle =
        style.copy(
            fontSize = style.fontSize * TYPE_SCALE_FACTOR,
            lineHeight = style.lineHeight * TYPE_SCALE_FACTOR * 1.05f,
            fontWeight = weight
        )
}

/**
 * Applies the Elder Mode theme when enabled; falls back to the standard dark theme.
 */
@Composable
fun RakshakAppTheme(elderModeEnabled: Boolean, content: @Composable () -> Unit) {
    if (elderModeEnabled) {
        MaterialTheme(
            colorScheme = ElderTheme.colorScheme(),
            typography = ElderTheme.typography(),
            content = content
        )
    } else {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFF2E7D32),
                secondary = Color(0xFFFFB300),
                background = Color(0xFF121212),
                surface = Color(0xFF1E1E1E),
                onSurface = Color.White
            ),
            content = content
        )
    }
}
