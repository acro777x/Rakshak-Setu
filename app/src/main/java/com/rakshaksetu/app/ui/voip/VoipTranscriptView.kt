package com.rakshaksetu.app.ui.voip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakshaksetu.app.ai.ScamPhraseTrie
import com.rakshaksetu.app.ui.theme.*

/**
 * Live streaming transcript card adhering to RakshakSetuTheme.
 * Renders real-time ASR text and highlights detected scam phrases in high-contrast crimson.
 */
@Composable
fun VoipTranscriptView(
    transcriptText: String,
    matchedSpans: List<ScamPhraseTrie.MatchedSpan>,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Auto-scroll to bottom as new speech frames arrive
    LaunchedEffect(transcriptText) {
        if (transcriptText.isNotBlank()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (matchedSpans.isNotEmpty()) BlockedRedContainer.copy(alpha = 0.5f) else BorderColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Status title and scam trigger badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (matchedSpans.isNotEmpty()) BlockedRed else RakshakSetuBlue)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE SPEECH INTERCEPT (USERSPEACE RAM)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.5.sp
                    )
                }

                if (matchedSpans.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(BlockedRedLight, RoundedCornerShape(6.dp))
                            .border(1.dp, BlockedRed.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = BlockedRed,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${matchedSpans.size} SCAM TRIGGER(S)",
                            color = BlockedRed,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Scrollable text area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp, max = 130.dp)
                    .background(BackgroundLight, RoundedCornerShape(10.dp))
                    .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                    .padding(10.dp)
                    .verticalScroll(scrollState)
            ) {
                val annotatedString = buildAnnotatedString {
                    if (transcriptText.isBlank()) {
                        withStyle(SpanStyle(color = TextSecondary.copy(alpha = 0.7f), fontStyle = FontStyle.Italic, fontSize = 13.sp)) {
                            append("Listening to encrypted audio stream...")
                        }
                    } else if (matchedSpans.isEmpty()) {
                        withStyle(SpanStyle(color = TextPrimary, fontSize = 14.sp)) {
                            append(transcriptText)
                        }
                    } else {
                        // Highlight detected scam keywords with crimson background
                        var cursor = 0
                        val sortedSpans = matchedSpans.sortedBy { it.start }

                        for (span in sortedSpans) {
                            val validStart = span.start.coerceIn(0, transcriptText.length)
                            val validEnd = span.end.coerceIn(validStart, transcriptText.length)

                            if (cursor < validStart) {
                                withStyle(SpanStyle(color = TextPrimary, fontSize = 14.sp)) {
                                    append(transcriptText.substring(cursor, validStart))
                                }
                                cursor = validStart
                            }

                            val actualStart = maxOf(validStart, cursor)
                            if (actualStart < validEnd) {
                                withStyle(
                                    SpanStyle(
                                        color = BlockedRed,
                                        background = BlockedRedLight,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                ) {
                                    append(transcriptText.substring(actualStart, validEnd))
                                }
                                cursor = validEnd
                            }
                        }

                        if (cursor < transcriptText.length) {
                            withStyle(SpanStyle(color = TextPrimary, fontSize = 14.sp)) {
                                append(transcriptText.substring(cursor))
                            }
                        }
                    }
                }

                Text(
                    text = annotatedString,
                    lineHeight = 20.sp,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

// Canonical typealias for Prototype 2 compatibility
@Composable
fun TranscriptView(
    transcriptText: String,
    matchedSpans: List<ScamPhraseTrie.MatchedSpan>,
    modifier: Modifier = Modifier
) = VoipTranscriptView(transcriptText = transcriptText, matchedSpans = matchedSpans, modifier = modifier)
