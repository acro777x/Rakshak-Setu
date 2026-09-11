package com.rakshaksetu.voip.ui.hud

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rakshaksetu.voip.ai.AiPipelineCoordinator.ThreatLevel
import com.rakshaksetu.voip.ui.components.ThreatAvatar

/**
 * Pulsing threat avatar that morphs based on Wald SPRT threat state.
 */
@Composable
fun PulsingThreatAvatar(
    threatLevel: ThreatLevel,
    modifier: Modifier = Modifier
) {
    ThreatAvatar(threatLevel = threatLevel, modifier = modifier)
}
