package com.rakshaksetu.voip.ui.hud

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rakshaksetu.voip.ai.ScamPhraseTrie
import com.rakshaksetu.voip.ui.components.TranscriptView

/**
 * Live streaming transcript viewer that highlights threat keywords with crimson badges.
 */
@Composable
fun StreamingTranscriptView(
    transcriptText: String,
    matchedSpans: List<ScamPhraseTrie.MatchedSpan>,
    modifier: Modifier = Modifier
) {
    TranscriptView(
        transcriptText = transcriptText,
        matchedSpans = matchedSpans,
        modifier = modifier
    )
}
