package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.MascotState
import com.example.model.NotchConfig
import com.example.model.VoiceListeningPhase
import com.example.model.VoiceListeningState
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable
fun VoiceListeningNotchCard(
  voiceState: VoiceListeningState,
  config: NotchConfig,
  primaryColor: Color,
  secondaryColor: Color,
  onCopyReply: (String) -> Unit,
  onRetryListen: () -> Unit,
  onOpenProPaywall: (() -> Unit)? = null,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val haptic = LocalHapticFeedback.current
  val isListening = voiceState.phase == VoiceListeningPhase.LISTENING
  val isThinking = voiceState.phase == VoiceListeningPhase.THINKING
  var copiedRecently by remember(voiceState.sessionId, voiceState.aiReply, voiceState.localInfoReply) {
    mutableStateOf(false)
  }

  LaunchedEffect(copiedRecently) {
    if (copiedRecently) {
      delay(1500L)
      copiedRecently = false
    }
  }

  val smoothedLevel by
    animateFloatAsState(
      targetValue = if (isListening) voiceState.audioLevel.coerceIn(0.08f, 1f) else 0.06f,
      animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
      label = "voice_rms_level",
    )

  val wavePhase =
    if (isListening) {
      rememberInfiniteTransition(label = "voice_wave_transition").animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec =
          infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
          ),
        label = "voice_wave_phase",
      ).value
    } else {
      0f
    }

  val statusPulse =
    if (isListening || isThinking) {
      rememberInfiniteTransition(label = "voice_status_dot").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec =
          infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
          ),
        label = "voice_dot_alpha",
      ).value
    } else {
      1f
    }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .padding(horizontal = 14.dp, vertical = 10.dp)
        .testTag("voice_listening_notch_card"),
    verticalArrangement = Arrangement.SpaceBetween,
  ) {
    // Top Row: Mascot + Status + Close Button
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Box(
          modifier =
            Modifier.size(46.dp)
              .clip(CircleShape)
              .background(
                Brush.radialGradient(
                  listOf(
                    primaryColor.copy(
                      alpha =
                        when {
                          isListening -> 0.24f * smoothedLevel + 0.12f
                          isThinking -> 0.18f * statusPulse + 0.08f
                          else -> 0.10f
                        }
                    ),
                    Color.Transparent,
                  )
                )
              )
              .border(
                width = 1.25.dp,
                color =
                  when {
                    isListening || isThinking -> primaryColor.copy(alpha = 0.65f)
                    voiceState.confirmationBadge != null -> StatusSuccess.copy(alpha = 0.65f)
                    else -> Color.White.copy(alpha = 0.14f)
                  },
                shape = CircleShape,
              )
              .testTag("voice_mascot_badge"),
          contentAlignment = Alignment.Center,
        ) {
          MascotView(
            state = if (isListening) MascotState.LISTENING_VOICE else MascotState.IDLE,
            appearance = config.appearance,
            size = 40.dp,
          )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            when {
              isListening -> {
                Box(
                  modifier =
                    Modifier.size(8.dp)
                      .clip(CircleShape)
                      .background(primaryColor.copy(alpha = statusPulse))
                )
                Text(
                  text = "Listening...",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = primaryColor,
                )
              }

              isThinking -> {
                CircularProgressIndicator(
                  color = primaryColor,
                  strokeWidth = 2.dp,
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = "Thinking...",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = primaryColor,
                )
              }

              voiceState.confirmationBadge != null -> {
                Icon(
                  imageVector = Icons.Rounded.CheckCircle,
                  contentDescription = "Executed",
                  tint = StatusSuccess,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = "Command Executed",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = StatusSuccess,
                )
              }

              voiceState.localInfoReply != null -> {
                Icon(
                  imageVector = Icons.Rounded.CheckCircle,
                  contentDescription = voiceState.localInfoTitle ?: "Device Info",
                  tint = primaryColor,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = voiceState.localInfoTitle ?: "Device Info",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = primaryColor,
                )
              }

              voiceState.aiReply != null -> {
                Icon(
                  imageVector = Icons.Rounded.AutoAwesome,
                  contentDescription = "Gemini Reply",
                  tint = primaryColor,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = "Matus AI",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = primaryColor,
                )
              }

              voiceState.requiresProUpgrade -> {
                Icon(
                  imageVector = Icons.Rounded.WorkspacePremium,
                  contentDescription = "Matus Pro Required",
                  tint = primaryColor,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = "Matus Pro Feature",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = primaryColor,
                )
              }

              voiceState.errorMessage != null -> {
                Text(
                  text = "Voice Notice",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = StatusWarning,
                )
              }

              else -> {
                Icon(
                  imageVector = Icons.Rounded.CheckCircle,
                  contentDescription = "Heard",
                  tint = StatusSuccess,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = "Heard",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = StatusSuccess,
                )
              }
            }
          }
          Text(
            text =
              when {
                voiceState.requiresProUpgrade -> "Voice Commands & Gemini AI"
                voiceState.localInfoReply != null -> "On-device live info"
                voiceState.aiReply != null -> "Gemini 3 Flash"
                voiceState.confirmationBadge != null -> "On-device instant action"
                else -> "Matus Voice"
              },
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
          )
        }
      }

      IconButton(
        onClick = {
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          onClose()
        },
        modifier =
          Modifier.size(36.dp)
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .testTag("voice_close_btn"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Close,
          contentDescription = "Close voice listening",
          tint = TextPrimary,
          modifier = Modifier.size(18.dp),
        )
      }
    }

    // Center Content Area
    Column(
      modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
      when {
        isListening -> {
          Canvas(
            modifier =
              Modifier.fillMaxWidth()
                .height(26.dp)
                .testTag("voice_waveform_canvas"),
          ) {
            val barCount = 22
            val totalSlot = size.width / barCount
            val barWidth = (totalSlot * 0.46f).coerceAtLeast(3f)
            val centerIdx = (barCount - 1) / 2f

            for (i in 0 until barCount) {
              val edgeFalloff = 1f - (abs(i - centerIdx) / centerIdx).coerceIn(0f, 1f) * 0.55f
              val ripple = abs(sin(wavePhase + i * 0.52f))
              val barNorm = (0.14f + smoothedLevel * 0.86f * ripple * edgeFalloff).coerceIn(0.12f, 1f)
              val barH = size.height * barNorm
              val x = i * totalSlot + (totalSlot - barWidth) / 2f
              drawRoundRect(
                brush = Brush.verticalGradient(listOf(primaryColor, secondaryColor)),
                topLeft = Offset(x, (size.height - barH) / 2f),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                alpha = 0.92f,
              )
            }
          }

          Text(
            text = voiceState.transcript.takeIf { it.isNotBlank() }?.let { "\"$it\"" } ?: "Speak now...",
            style =
              MaterialTheme.typography.bodyMedium.copy(
                fontWeight =
                  if (voiceState.transcript.isNotBlank()) FontWeight.Medium else FontWeight.Normal
              ),
            color = if (voiceState.transcript.isNotBlank()) TextPrimary else TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().testTag("voice_transcript_text"),
          )
        }

        isThinking -> {
          if (voiceState.transcript.isNotBlank()) {
            Text(
              text = "\"${voiceState.transcript}\"",
              style = MaterialTheme.typography.labelMedium,
              color = TextSecondary,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.fillMaxWidth().testTag("voice_transcript_text"),
            )
          }
          Row(
            modifier =
              Modifier.clip(RoundedCornerShape(50))
                .background(primaryColor.copy(alpha = 0.14f))
                .border(1.dp, primaryColor.copy(alpha = 0.35f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            CircularProgressIndicator(
              color = primaryColor,
              strokeWidth = 2.dp,
              modifier = Modifier.size(13.dp),
            )
            Text(
              text = "Asking Gemini...",
              style = MaterialTheme.typography.labelMedium,
              color = primaryColor,
            )
          }
        }

        voiceState.confirmationBadge != null -> {
          if (voiceState.transcript.isNotBlank()) {
            Text(
              text = "\"${voiceState.transcript}\"",
              style = MaterialTheme.typography.labelSmall,
              color = TextMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.fillMaxWidth().testTag("voice_transcript_text"),
            )
          }
          Row(
            modifier =
              Modifier.clip(RoundedCornerShape(50))
                .background(StatusSuccess.copy(alpha = 0.16f))
                .border(1.dp, StatusSuccess.copy(alpha = 0.55f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("voice_confirmation_pill"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.CheckCircle,
              contentDescription = null,
              tint = StatusSuccess,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = voiceState.confirmationBadge,
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
              color = TextPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        (voiceState.localInfoReply ?: voiceState.aiReply) != null -> {
          val replyBody = voiceState.localInfoReply ?: voiceState.aiReply.orEmpty()
          if (voiceState.transcript.isNotBlank()) {
            Text(
              text = "\"${voiceState.transcript}\"",
              style = MaterialTheme.typography.labelSmall,
              color = TextMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.fillMaxWidth().testTag("voice_transcript_text"),
            )
          }
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .testTag("voice_ai_reply_text"),
          ) {
            Text(
              text = replyBody,
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
              color = TextPrimary,
            )
          }
        }

        voiceState.needsApiKeyHint -> {
          Text(
            text = "\"${voiceState.transcript}\"",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().testTag("voice_transcript_text"),
          )
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(primaryColor.copy(alpha = 0.12f))
                .border(1.dp, primaryColor.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("voice_api_key_hint"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Key,
              contentDescription = null,
              tint = primaryColor,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "Add your free Gemini API key in Settings for AI answers",
              style = MaterialTheme.typography.labelSmall,
              color = TextSecondary,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        voiceState.requiresProUpgrade -> {
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(primaryColor.copy(alpha = 0.14f))
                .border(1.dp, primaryColor.copy(alpha = 0.42f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("voice_pro_gate_card"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Lock,
              contentDescription = null,
              tint = primaryColor,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text =
                voiceState.errorMessage
                  ?: "Start a 4-day Play trial (\$1/mo or \$5/yr) for hands-free voice control & Gemini AI.",
              style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
              color = TextPrimary,
              modifier = Modifier.weight(1f),
            )
          }
        }

        voiceState.errorMessage != null -> {
          if (voiceState.transcript.isNotBlank()) {
            Text(
              text = "\"${voiceState.transcript}\"",
              style = MaterialTheme.typography.labelSmall,
              color = TextMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .testTag("voice_error_scroll"),
          ) {
            Text(
              text = voiceState.errorMessage,
              style = MaterialTheme.typography.bodyMedium,
              color = StatusWarning,
              modifier = Modifier.fillMaxWidth().testTag("voice_transcript_text"),
            )
          }
        }

        else -> {
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
          ) {
            Text(
              text =
                voiceState.transcript.takeIf { it.isNotBlank() }?.let { "\"$it\"" }
                  ?: "No speech captured. Tap Listen Again to retry.",
              style = MaterialTheme.typography.bodyMedium,
              color = if (voiceState.transcript.isNotBlank()) TextPrimary else TextSecondary,
              modifier = Modifier.fillMaxWidth().testTag("voice_transcript_text"),
            )
          }
        }
      }
    }

    // Bottom Controls
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (isListening || isThinking) {
        Text(
          text = if (isListening) "Waiting for speech..." else "Generating short reply...",
          style = MaterialTheme.typography.labelSmall,
          color = TextMuted,
          modifier = Modifier.weight(1f),
        )
      } else if (voiceState.requiresProUpgrade) {
        if (onOpenProPaywall != null) {
          Row(
            modifier =
              Modifier.clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(primaryColor, secondaryColor)))
                .clickable {
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onOpenProPaywall()
                }
                .testTag("voice_unlock_pro_btn")
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.WorkspacePremium,
              contentDescription = "Unlock Matus Pro",
              tint = Color.Black,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = "Unlock Matus Pro",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              color = Color.Black,
            )
          }
        }
      } else {
        val reply = voiceState.localInfoReply ?: voiceState.aiReply
        if (!reply.isNullOrBlank()) {
          Row(
            modifier =
              Modifier.clip(RoundedCornerShape(50))
                .background(
                  if (copiedRecently) StatusSuccess.copy(alpha = 0.18f)
                  else Color.White.copy(alpha = 0.08f)
                )
                .border(
                  width = 1.dp,
                  color =
                    if (copiedRecently) StatusSuccess.copy(alpha = 0.55f)
                    else Color.White.copy(alpha = 0.14f),
                  shape = RoundedCornerShape(50),
                )
                .clickable {
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onCopyReply(reply)
                  copiedRecently = true
                }
                .testTag("voice_copy_reply_btn")
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Icon(
              imageVector = if (copiedRecently) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
              contentDescription = if (copiedRecently) "Copied" else "Copy reply",
              tint = if (copiedRecently) StatusSuccess else TextPrimary,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = if (copiedRecently) "Copied" else "Copy",
              style = MaterialTheme.typography.labelMedium,
              color = if (copiedRecently) StatusSuccess else TextPrimary,
            )
          }
        }

        if (voiceState.confirmationBadge == null) {
          Row(
            modifier =
              Modifier.clip(RoundedCornerShape(50))
                .background(primaryColor.copy(alpha = 0.18f))
                .border(1.dp, primaryColor.copy(alpha = 0.55f), RoundedCornerShape(50))
                .clickable {
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onRetryListen()
                }
                .testTag("voice_retry_btn")
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Mic,
              contentDescription = "Listen again",
              tint = primaryColor,
              modifier = Modifier.size(15.dp),
            )
            Text(
              text = "Listen Again",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
              color = primaryColor,
            )
          }
        }
      }

      Box(
        modifier =
          Modifier.clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onClose()
            }
            .testTag("voice_done_btn")
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = if (isListening || isThinking) "Cancel" else "Done",
          style = MaterialTheme.typography.labelMedium,
          color = TextPrimary,
        )
      }
    }
  }
}
