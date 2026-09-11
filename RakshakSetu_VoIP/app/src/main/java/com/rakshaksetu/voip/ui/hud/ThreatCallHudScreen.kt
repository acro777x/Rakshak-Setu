package com.rakshaksetu.voip.ui.hud

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rakshaksetu.voip.ai.AiPipelineCoordinator.PipelineUiState
import com.rakshaksetu.voip.ui.screens.ActiveCallHudScreen

/**
 * Dynamic in-call Threat HUD screen.
 */
@Composable
fun ThreatCallHudScreen(
    peerId: String,
    uiState: PipelineUiState,
    onDisconnectAndBlock: () -> Unit,
    onSealSection65B: () -> Unit,
    onSpeedDial1930: () -> Unit,
    onSimulateAttack: () -> Unit,
    onSimulateSafe: () -> Unit,
    modifier: Modifier = Modifier
) {
    ActiveCallHudScreen(
        peerId = peerId,
        uiState = uiState,
        onDisconnectAndBlock = onDisconnectAndBlock,
        onSealSection65B = onSealSection65B,
        onSpeedDial1930 = onSpeedDial1930,
        onSimulateAttack = onSimulateAttack,
        onSimulateSafe = onSimulateSafe,
        modifier = modifier
    )
}
