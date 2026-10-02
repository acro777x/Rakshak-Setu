package com.rakshaksetu.app.ui.voip

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
import com.rakshaksetu.app.ai.AiPipelineCoordinator.ThreatLevel
import com.rakshaksetu.app.ui.theme.BlockedRed
import com.rakshaksetu.app.ui.theme.RakshakSetuBlue
import com.rakshaksetu.app.ui.theme.SuspiciousAmber
import kotlin.math.PI
import kotlin.math.sin

/**
 * Live GPU spectral waveform gliding in Electric Blue / RakshakSetuBlue (#1565C0)
 * on normal speech and turning Crimson (BlockedRed #C62828) upon threat alert.
 *
 * Renders multi-harmonic sinusoidal waves modulated by live linear PCM audio energy
 * intercepted directly in userspace RAM.
 */
@Composable
fun VoipWaveformView(
    threatLevel: ThreatLevel,
    audioRms: Float,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(90.dp)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WavePhaseTransition")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )

    // Waveform color: RakshakSetuBlue (Safe) -> SuspiciousAmber (Evaluating) -> BlockedRed (Threat)
    val waveColor by animateColorAsState(
        targetValue = when (threatLevel) {
            ThreatLevel.SAFE -> RakshakSetuBlue
            ThreatLevel.EVALUATING -> SuspiciousAmber
            ThreatLevel.CRITICAL_THREAT -> BlockedRed
        },
        animationSpec = tween(durationMillis = 350),
        label = "WaveColorAnimation"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val clampedRms = audioRms.coerceIn(0.0f, 1.0f)
        val baseAmplitude = (12.dp.toPx() + (clampedRms * 36.dp.toPx())).coerceAtMost(height * 0.44f)

        val primaryPath = Path()
        val secondaryPath = Path()
        val tertiaryPath = Path()

        val step = 4f
        var x = 0f
        var isFirst = true

        while (x <= width) {
            val progress = x / width
            // Window envelope (tapers ends to zero for a clean card aesthetic)
            val envelope = sin(progress * PI.toFloat())

            val y1 = centerY + baseAmplitude * envelope * sin((progress * 4f * PI.toFloat()) + phase)
            val y2 = centerY + (baseAmplitude * 0.65f) * envelope * sin((progress * 6f * PI.toFloat()) - (phase * 1.3f))
            val y3 = centerY + (baseAmplitude * 0.35f) * envelope * sin((progress * 8f * PI.toFloat()) + (phase * 0.7f))

            if (isFirst) {
                primaryPath.moveTo(x, y1)
                secondaryPath.moveTo(x, y2)
                tertiaryPath.moveTo(x, y3)
                isFirst = false
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
            color = waveColor.copy(alpha = 0.20f),
            style = Stroke(width = 1.5.dp.toPx())
        )
        drawPath(
            path = secondaryPath,
            color = waveColor.copy(alpha = 0.45f),
            style = Stroke(width = 2.dp.toPx())
        )

        // Draw foreground glowing primary harmonic
        drawPath(
            path = primaryPath,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    waveColor.copy(alpha = 0.25f),
                    waveColor,
                    waveColor.copy(alpha = 0.25f)
                )
            ),
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

// Canonical typealias for Prototype 2 compatibility
@Composable
fun SpectralWaveformView(
    threatLevel: ThreatLevel,
    audioRms: Float,
    modifier: Modifier = Modifier.fillMaxWidth().height(90.dp)
) = VoipWaveformView(threatLevel = threatLevel, audioRms = audioRms, modifier = modifier)
