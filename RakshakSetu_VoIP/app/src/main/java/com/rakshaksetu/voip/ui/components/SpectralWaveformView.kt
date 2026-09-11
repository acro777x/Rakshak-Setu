package com.rakshaksetu.voip.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.rakshaksetu.voip.ai.AiPipelineCoordinator.ThreatLevel
import com.rakshaksetu.voip.ui.theme.AmberWarn
import com.rakshaksetu.voip.ui.theme.CrimsonThreat
import com.rakshaksetu.voip.ui.theme.EmeraldSafe
import kotlin.math.PI
import kotlin.math.sin

/**
 * Real-time phase-shifted GPU spectral waveform rendered on a Compose Canvas.
 *
 * Animates multi-harmonic sine waves modulated by live PCM RMS audio energy
 * intercepted directly in userspace RAM.
 */
@Composable
fun SpectralWaveformView(
    threatLevel: ThreatLevel,
    audioRms: Float,
    modifier: Modifier = Modifier.fillMaxWidth().height(100.dp)
) {
    // Infinite transition for continuous smooth phase shift
    val infiniteTransition = rememberInfiniteTransition(label = "WavePhase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PhaseValue"
    )

    // Dynamic wave color tied to threat state
    val waveColor by animateColorAsState(
        targetValue = when (threatLevel) {
            ThreatLevel.SAFE -> EmeraldSafe
            ThreatLevel.EVALUATING -> AmberWarn
            ThreatLevel.CRITICAL_THREAT -> CrimsonThreat
        },
        animationSpec = tween(durationMillis = 400),
        label = "WaveColor"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val baseAmplitude = (12.dp.toPx() + (audioRms * 32.dp.toPx())).coerceAtMost(height * 0.45f)

        // Draw primary harmonic wave
        val primaryPath = Path()
        val secondaryPath = Path()
        val tertiaryPath = Path()

        val step = 4f
        var x = 0f
        var first = true

        while (x <= width) {
            val progress = x / width
            // Window envelope (tapers ends to zero for a clean aesthetic)
            val envelope = sin(progress * PI.toFloat())

            val y1 = centerY + baseAmplitude * envelope * sin((progress * 4f * PI.toFloat()) + phase)
            val y2 = centerY + (baseAmplitude * 0.65f) * envelope * sin((progress * 6f * PI.toFloat()) - (phase * 1.3f))
            val y3 = centerY + (baseAmplitude * 0.35f) * envelope * sin((progress * 8f * PI.toFloat()) + (phase * 0.7f))

            if (first) {
                primaryPath.moveTo(x, y1)
                secondaryPath.moveTo(x, y2)
                tertiaryPath.moveTo(x, y3)
                first = false
            } else {
                primaryPath.lineTo(x, y1)
                secondaryPath.lineTo(x, y2)
                tertiaryPath.lineTo(x, y3)
            }
            x += step
        }

        // Draw background faint harmonics
        drawPath(
            path = tertiaryPath,
            color = waveColor.copy(alpha = 0.25f),
            style = Stroke(width = 1.5.dp.toPx())
        )
        drawPath(
            path = secondaryPath,
            color = waveColor.copy(alpha = 0.50f),
            style = Stroke(width = 2.dp.toPx())
        )

        // Draw foreground glowing primary harmonic
        drawPath(
            path = primaryPath,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    waveColor.copy(alpha = 0.2f),
                    waveColor,
                    waveColor.copy(alpha = 0.2f)
                )
            ),
            style = Stroke(width = 3.dp.toPx())
        )
    }
}
