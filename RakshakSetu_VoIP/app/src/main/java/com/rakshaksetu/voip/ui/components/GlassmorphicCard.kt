package com.rakshaksetu.voip.ui.components

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rakshaksetu.voip.ui.theme.GlassBorder
import com.rakshaksetu.voip.ui.theme.GlassSurface

/**
 * iOS-grade glassmorphic container with translucent frosted background,
 * subtle gradient border reflection, and backwards-compatible fallback.
 */
@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    backgroundColor: Color = GlassSurface,
    borderColor: Color = GlassBorder,
    borderWidth: Dp = 1.dp,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    val gradientBorder = Brush.verticalGradient(
        colors = listOf(
            borderColor.copy(alpha = 0.45f),
            borderColor.copy(alpha = 0.15f)
        )
    )

    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        modifier.blur(0.5.dp)
    } else {
        modifier
    }

    Box(
        modifier = blurModifier
            .clip(shape)
            .background(backgroundColor)
            .border(BorderStroke(borderWidth, gradientBorder), shape)
            .padding(16.dp)
    ) {
        content()
    }
}
