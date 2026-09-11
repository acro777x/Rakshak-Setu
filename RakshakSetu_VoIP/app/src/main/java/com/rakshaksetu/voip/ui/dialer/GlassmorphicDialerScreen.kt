package com.rakshaksetu.voip.ui.dialer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rakshaksetu.voip.ui.screens.DialerScreen

/**
 * iOS-grade round glassmorphic dialer keypad with tactile haptics,
 * sovereign encrypted status indicator, and instant 1930 Cyber Helpline speed dial.
 */
@Composable
fun GlassmorphicDialerScreen(
    onInitiateCall: (destination: String) -> Unit,
    onSpeedDial1930: () -> Unit,
    modifier: Modifier = Modifier
) {
    DialerScreen(
        onInitiateCall = onInitiateCall,
        onSpeedDial1930 = onSpeedDial1930,
        modifier = modifier
    )
}
