package com.rakshaksetu.voip.ui.hud

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rakshaksetu.voip.ai.AiPipelineCoordinator.ThreatLevel
import com.rakshaksetu.voip.ui.components.SpectralWaveformView

/**
 * Real-time phase-shifted GPU spectral waveform.
 */
@Composable
fun GpuSpectralWaveform(
    threatLevel: ThreatLevel,
    audioRms: Float,
    modifier: Modifier = Modifier.fillMaxWidth().height(100.dp)
) {
    SpectralWaveformView(
        threatLevel = threatLevel,
        audioRms = audioRms,
        modifier = modifier
    )
}
