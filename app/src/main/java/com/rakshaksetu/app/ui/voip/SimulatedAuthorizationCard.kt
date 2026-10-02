package com.rakshaksetu.app.ui.voip

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakshaksetu.app.ai.AiPipelineCoordinator.ThreatLevel
import com.rakshaksetu.app.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Status lifecycle for the in-flight simulated financial approval countdown.
 */
enum class AuthorizationStatus {
    IDLE,
    COUNTING,
    FROZEN,
    APPROVED
}

/**
 * State container for the Simulated Financial Authorization Card.
 */
data class SimulatedAuthorizationState(
    val initialSeconds: Float = 10.0f,
    val remainingSeconds: Float = 10.0f,
    val status: AuthorizationStatus = AuthorizationStatus.COUNTING,
    val amount: String = "₹50,000",
    val beneficiary: String = "Escrow Beneficiary (Ref: RTGS-9821)",
    val alertBuzzTriggered: Boolean = false,
    val frozenAtSeconds: Float? = null
)

/**
 * Controller managing the 10.0s authorization countdown, freeze on threat breach,
 * audible alert buzz, and pre-emptive call termination.
 */
class SimulatedAuthorizationController(
    val initialSeconds: Float = 10.0f,
    private val coroutineScope: CoroutineScope? = null
) {
    companion object {
        private const val TAG = "SimAuthCtrl"
    }

    private val _remainingSecondsFlow = MutableStateFlow(initialSeconds)
    val remainingSeconds: StateFlow<Float> = _remainingSecondsFlow.asStateFlow()

    private val _statusFlow = MutableStateFlow(AuthorizationStatus.COUNTING)
    val status: StateFlow<AuthorizationStatus> = _statusFlow.asStateFlow()

    private val _stateFlow = MutableStateFlow(
        SimulatedAuthorizationState(
            initialSeconds = initialSeconds,
            remainingSeconds = initialSeconds,
            status = AuthorizationStatus.COUNTING
        )
    )
    val state: StateFlow<SimulatedAuthorizationState> = _stateFlow.asStateFlow()

    private var timerJob: Job? = null
    private var alertTriggered = false

    /**
     * Start the 10.0-second approval countdown.
     */
    fun start(
        scope: CoroutineScope,
        onApproved: () -> Unit = {},
        onSeverCall: () -> Unit = {}
    ) {
        timerJob?.cancel()
        _remainingSecondsFlow.value = initialSeconds
        _statusFlow.value = AuthorizationStatus.COUNTING
        alertTriggered = false

        _stateFlow.value = SimulatedAuthorizationState(
            initialSeconds = initialSeconds,
            remainingSeconds = initialSeconds,
            status = AuthorizationStatus.COUNTING
        )

        timerJob = scope.launch {
            val stepIntervalMs = 100L
            val stepDecrement = 0.1f

            while (isActive && _remainingSecondsFlow.value > 0.05f) {
                delay(stepIntervalMs)
                if (_statusFlow.value == AuthorizationStatus.FROZEN) {
                    break
                }
                val nextVal = (_remainingSecondsFlow.value - stepDecrement).coerceAtLeast(0.0f)
                _remainingSecondsFlow.value = nextVal
                _stateFlow.value = _stateFlow.value.copy(remainingSeconds = nextVal)
            }

            if (_statusFlow.value == AuthorizationStatus.COUNTING && _remainingSecondsFlow.value <= 0.05f) {
                _remainingSecondsFlow.value = 0.0f
                _statusFlow.value = AuthorizationStatus.APPROVED
                _stateFlow.value = _stateFlow.value.copy(
                    remainingSeconds = 0.0f,
                    status = AuthorizationStatus.APPROVED
                )
                onApproved()
            }
        }
    }

    /**
     * Triggered when Wald SPRT breaches A = +5.288 or risk score > 0.95.
     * IMMEDIATELY FREEZES the countdown, marks threat detected, and severs the call.
     */
    fun freezeOnThreat(
        context: Context? = null,
        onSeverCall: () -> Unit = {}
    ) {
        if (_statusFlow.value == AuthorizationStatus.FROZEN) return
        timerJob?.cancel()

        val frozenSec = _remainingSecondsFlow.value
        _statusFlow.value = AuthorizationStatus.FROZEN
        alertTriggered = true

        _stateFlow.value = _stateFlow.value.copy(
            status = AuthorizationStatus.FROZEN,
            remainingSeconds = frozenSec,
            frozenAtSeconds = frozenSec,
            alertBuzzTriggered = true
        )

        // Audible alert buzz
        triggerAudibleBuzz(context)

        // Automatically sever call before approval expires
        coroutineScope?.launch {
            delay(1200L) // Allow HUD to display frozen alert before tearing down
            onSeverCall()
        } ?: run {
            onSeverCall()
        }
    }

    /**
     * Resets the authorization state to initial 10.0s counting.
     */
    fun reset() {
        timerJob?.cancel()
        _remainingSecondsFlow.value = initialSeconds
        _statusFlow.value = AuthorizationStatus.COUNTING
        alertTriggered = false
        _stateFlow.value = SimulatedAuthorizationState(
            initialSeconds = initialSeconds,
            remainingSeconds = initialSeconds,
            status = AuthorizationStatus.COUNTING
        )
    }

    private fun triggerAudibleBuzz(context: Context?) {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            toneGen.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 800)
        } catch (e: Exception) {
            Log.w(TAG, "Audible alert buzz fallback: ${e.message}")
        }
    }
}

/**
 * Simulated Authorization Card on the in-call HUD.
 *
 * Requirements:
 * - 10.0-second approval countdown.
 * - Genuine speech: countdown completes safely from 10.0s down to 0.0s, showing APPROVED state.
 * - Cloned speech: when Wald SPRT breaches A = +5.288 (risk score > 0.95), the countdown
 *   IMMEDIATELY FREEZES (at ~7.7s remaining), triggers an audible alert buzz, displays
 *   "FROZEN - THREAT DETECTED", and automatically severs the call before approval expires.
 */
@Composable
fun SimulatedAuthorizationCard(
    threatLevel: ThreatLevel,
    sprtLambda: Float,
    cloneProbability: Float,
    onEmergencyCallSever: () -> Unit,
    modifier: Modifier = Modifier,
    controller: SimulatedAuthorizationController? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Local controller if not provided externally
    val activeController = remember(controller) {
        controller ?: SimulatedAuthorizationController(initialSeconds = 10.0f, coroutineScope = coroutineScope)
    }

    val authState by activeController.state.collectAsState()

    // Start timer on mount
    LaunchedEffect(Unit) {
        activeController.start(
            scope = coroutineScope,
            onApproved = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onSeverCall = onEmergencyCallSever
        )
    }

    // Monitor AI Threat Level: When Wald SPRT breaches A = +5.288 (risk > 0.95) or CRITICAL_THREAT
    LaunchedEffect(threatLevel, sprtLambda, cloneProbability) {
        val isThreatBreached = threatLevel == ThreatLevel.CRITICAL_THREAT ||
                sprtLambda >= 5.288f ||
                cloneProbability > 0.95f

        if (isThreatBreached && authState.status == AuthorizationStatus.COUNTING) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            activeController.freezeOnThreat(context) {
                onEmergencyCallSever()
            }
        }
    }

    // Card styling based on authorization status
    val cardBorderColor by animateColorAsState(
        targetValue = when (authState.status) {
            AuthorizationStatus.APPROVED -> SafeGreen
            AuthorizationStatus.FROZEN -> BlockedRed
            AuthorizationStatus.COUNTING, AuthorizationStatus.IDLE -> BorderColor
        },
        animationSpec = tween(300),
        label = "AuthBorderColor"
    )

    val cardBgColor by animateColorAsState(
        targetValue = when (authState.status) {
            AuthorizationStatus.APPROVED -> SafeGreenLight.copy(alpha = 0.5f)
            AuthorizationStatus.FROZEN -> BlockedRedLight.copy(alpha = 0.6f)
            AuthorizationStatus.COUNTING, AuthorizationStatus.IDLE -> SurfaceWhite
        },
        animationSpec = tween(300),
        label = "AuthBgColor"
    )

    val progressFraction by animateFloatAsState(
        targetValue = (authState.remainingSeconds / authState.initialSeconds).coerceIn(0.0f, 1.0f),
        label = "AuthProgress"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(if (authState.status != AuthorizationStatus.COUNTING) 2.dp else 1.dp, cardBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                when (authState.status) {
                                    AuthorizationStatus.APPROVED -> SafeGreenLight
                                    AuthorizationStatus.FROZEN -> BlockedRedLight
                                    else -> RakshakSetuBlueLight.copy(alpha = 0.2f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (authState.status) {
                                AuthorizationStatus.APPROVED -> Icons.Default.CheckCircle
                                AuthorizationStatus.FROZEN -> Icons.Default.Lock
                                else -> Icons.Default.AccountBalance
                            },
                            contentDescription = null,
                            tint = when (authState.status) {
                                AuthorizationStatus.APPROVED -> SafeGreen
                                AuthorizationStatus.FROZEN -> BlockedRed
                                else -> RakshakSetuBlue
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "SIMULATED AUTHORIZATION GATE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = authState.amount,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }

                // Status Badge
                Surface(
                    color = when (authState.status) {
                        AuthorizationStatus.APPROVED -> SafeGreen
                        AuthorizationStatus.FROZEN -> BlockedRed
                        else -> RakshakSetuBlue
                    },
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = when (authState.status) {
                            AuthorizationStatus.APPROVED -> "APPROVED"
                            AuthorizationStatus.FROZEN -> "FROZEN"
                            AuthorizationStatus.COUNTING -> "IN FLIGHT"
                            AuthorizationStatus.IDLE -> "IDLE"
                        },
                        color = SurfaceWhite,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Beneficiary / Transaction Context
            Text(
                text = authState.beneficiary,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Countdown & Status Banner
            when (authState.status) {
                AuthorizationStatus.COUNTING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "Approval countdown active:",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Text(
                            text = String.format(Locale.US, "%.1fs remaining", authState.remainingSeconds),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = RakshakSetuBlue
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = RakshakSetuBlue,
                        trackColor = BorderColor
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "🔒 Protected by Wald SPRT gate. Synthetic voice clones freeze transfer pre-emptively.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                AuthorizationStatus.FROZEN -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BlockedRedLight, RoundedCornerShape(8.dp))
                            .border(1.dp, BlockedRed.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.GppBad,
                            contentDescription = null,
                            tint = BlockedRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "FROZEN - THREAT DETECTED (%.1fs remaining)",
                                    authState.remainingSeconds
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = BlockedRed
                            )
                            Text(
                                text = "Wald SPRT breached A = +5.288 (Risk > 95%). Transfer blocked, severing call.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BlockedRed
                            )
                        }
                    }
                }

                AuthorizationStatus.APPROVED -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SafeGreenLight, RoundedCornerShape(8.dp))
                            .border(1.dp, SafeGreen.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = SafeGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "TRANSACTION APPROVED (10.0s Safely Elapsed)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = SafeGreen
                            )
                            Text(
                                text = "Genuine human voice verified throughout. Zero synthetic voice artifacts detected.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SafeGreen
                            )
                        }
                    }
                }

                AuthorizationStatus.IDLE -> {
                    Text(
                        text = "Simulated transfer in standby mode.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
