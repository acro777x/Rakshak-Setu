package com.rakshaksetu.voip.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakshaksetu.voip.ai.ScamPhraseTrie
import com.rakshaksetu.voip.ui.theme.CrimsonThreat
import com.rakshaksetu.voip.ui.theme.TextMuted
import com.rakshaksetu.voip.ui.theme.TextPrimary

/**
 * Live streaming transcript viewer that highlights threat keywords with crimson badges.
 */
@Composable
fun TranscriptView(
    transcriptText: String,
    matchedSpans: List<ScamPhraseTrie.MatchedSpan>,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Auto-scroll to bottom when new words arrive
    LaunchedEffect(transcriptText) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    GlassmorphicCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        backgroundColor = Color(0x12FFFFFF)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 90.dp, max = 150.dp)
                .verticalScroll(scrollState)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "LIVE SPEECH INTERCEPT (USERSPEACE RAM)",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                if (matchedSpans.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(CrimsonThreat.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${matchedSpans.size} SCAM TRIGGER(S)",
                            color = CrimsonThreat,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val annotatedString = buildAnnotatedString {
                if (transcriptText.isBlank()) {
                    withStyle(SpanStyle(color = TextMuted, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                        append("Listening to encrypted audio stream...")
                    }
                } else if (matchedSpans.isEmpty()) {
                    withStyle(SpanStyle(color = TextPrimary, fontSize = 14.sp)) {
                        append(transcriptText)
                    }
                } else {
                    // Highlight scam words in transcript
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
                                    color = CrimsonThreat,
                                    background = CrimsonThreat.copy(alpha = 0.25f),
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
                lineHeight = 20.sp
            )
        }
    }
}
