package com.rakshaksetu.voip.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldSafe,
    secondary = SovereignCyan,
    tertiary = AmberWarn,
    background = BgDark,
    surface = BgDarkElevated,
    onPrimary = BgDark,
    onSecondary = BgDark,
    onTertiary = BgDark,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = CrimsonThreat,
    onError = TextPrimary
)

@Composable
fun RakshakVoipTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
