package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoNotDisturbOff
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ActiveModeAlert
import com.example.model.ActiveNotificationBanner
import com.example.model.CompactHudAlert
import com.example.model.ModeAlertKind
import com.example.model.NotchBorderStyle
import com.example.model.NotchConfig
import com.example.model.NotchGlowStyle
import com.example.model.NotchRuntimeState
import com.example.model.VoiceListeningPhase
import com.example.service.NotchRuntimeCoordinator
import com.example.ui.theme.NotchPitchBlack
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

private const val FIXED_EXPANDED_HEIGHT_DP = 216f
private const val FIXED_NOTIFICATION_HEIGHT_DP = 132f

@Composable
fun DynamicNotchSurface(
  config: NotchConfig,
  runtime: NotchRuntimeState,
  coordinator: NotchRuntimeCoordinator,
  expandedWidthDp: Float,
  isInStudioPreview: Boolean,
  onNavigateToWidgetsTab: (() -> Unit)? = null,
  onOpenProPaywall: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val haptic = LocalHapticFeedback.current
  val primaryColor = Color(config.appearance.resolvePrimaryHex())
  val secondaryColor = Color(config.appearance.resolveSecondaryHex())
  val enabledWidgets = config.enabledWidgets

  val activeBanner = runtime.activeNotification
  var retainedBanner by remember { mutableStateOf<ActiveNotificationBanner?>(null) }
  LaunchedEffect(activeBanner) {
    if (activeBanner != null) retainedBanner = activeBanner
  }

  val hasNotification = activeBanner != null
  val isVoiceActive = runtime.voiceListening.phase != VoiceListeningPhase.INACTIVE
  val isExpandedAny = runtime.isExpanded || hasNotification || isVoiceActive
  val showVoiceCard = isVoiceActive
  val showExpandedWidgets = runtime.isExpanded && !hasNotification && !isVoiceActive
  val showCompact = !isExpandedAny

  // Silky smooth non-shaking expand/minimize curve (zero overshoot, zero bounce)
  val morphEasing = remember { CubicBezierEasing(0.22f, 1f, 0.36f, 1f) }
  val morphTweenDp =
    remember {
      tween<androidx.compose.ui.unit.Dp>(
        durationMillis = 270,
        easing = morphEasing,
      )
    }

  val animatedWidth by
    animateDpAsState(
      targetValue =
        if (isExpandedAny) expandedWidthDp.dp
        else config.geometry.compactWidthDp.dp,
      animationSpec = morphTweenDp,
      label = "notch_width",
    )

  val animatedHeight by
    animateDpAsState(
      targetValue =
        when {
          hasNotification -> FIXED_NOTIFICATION_HEIGHT_DP.dp
          isVoiceActive -> FIXED_EXPANDED_HEIGHT_DP.dp
          runtime.isExpanded -> FIXED_EXPANDED_HEIGHT_DP.dp
          else -> config.geometry.compactHeightDp.dp
        },
      animationSpec = morphTweenDp,
      label = "notch_height",
    )

  val animatedCorner by
    animateDpAsState(
      targetValue =
        when {
          hasNotification -> 26.dp
          isExpandedAny -> 28.dp
          else -> config.geometry.cornerRadiusDp.dp
        },
      animationSpec = morphTweenDp,
      label = "notch_corner",
    )

  val notificationAlpha by
    animateFloatAsState(
      targetValue = if (hasNotification) 1f else 0f,
      animationSpec =
        tween(
          durationMillis = if (hasNotification) 170 else 100,
          delayMillis = if (hasNotification) 50 else 0,
          easing = FastOutSlowInEasing,
        ),
      label = "notification_alpha",
    )

  val voiceAlpha by
    animateFloatAsState(
      targetValue = if (showVoiceCard) 1f else 0f,
      animationSpec =
        tween(
          durationMillis = if (showVoiceCard) 180 else 100,
          delayMillis = if (showVoiceCard) 50 else 0,
          easing = FastOutSlowInEasing,
        ),
      label = "voice_alpha",
    )

  val expandedWidgetsAlpha by
    animateFloatAsState(
      targetValue = if (showExpandedWidgets) 1f else 0f,
      animationSpec =
        tween(
          durationMillis = if (showExpandedWidgets) 180 else 100,
          delayMillis = if (showExpandedWidgets) 60 else 0,
          easing = FastOutSlowInEasing,
        ),
      label = "expanded_alpha",
    )

  val compactContentAlpha by
    animateFloatAsState(
      targetValue = if (showCompact) 1f else 0f,
      animationSpec =
        tween(
          durationMillis = if (showCompact) 150 else 85,
          delayMillis = if (showCompact) 95 else 0,
          easing = FastOutSlowInEasing,
        ),
      label = "compact_alpha",
    )

  // Perimeter glow progress (2.4s notification trigger + gentle ambient phase for Aura/Prism styles when expanded)
  val notificationLapProgress = remember { Animatable(0f) }
  LaunchedEffect(runtime.notificationPulseId) {
    if (runtime.notificationPulseId > 0L) {
      notificationLapProgress.snapTo(0f)
      notificationLapProgress.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 2400, easing = LinearEasing),
      )
    }
  }

  val isAmbientGlowStyle =
    isExpandedAny &&
      (config.appearance.glowStyle == NotchGlowStyle.BREATHING_AURA ||
        config.appearance.glowStyle == NotchGlowStyle.PRISM_WAVE)
  val ambientPhase =
    if (isAmbientGlowStyle) {
      rememberInfiniteTransition(label = "ambient_glow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
          infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
          ),
        label = "ambient_phase",
      ).value
    } else {
      0f
    }

  val shape = RoundedCornerShape(animatedCorner)
  val expandCd = stringResource(R.string.cd_expand_notch)
  val collapseCd = stringResource(R.string.cd_collapse_notch)

  val compactExtraOffsetX by
    animateDpAsState(
      targetValue =
        if (!isInStudioPreview && showCompact) {
          val clampedExpandedX = config.geometry.offsetXDp.coerceIn(-10f, 10f)
          (config.geometry.offsetXDp - clampedExpandedX).dp
        } else {
          0.dp
        },
      animationSpec = morphTweenDp,
      label = "compact_extra_x",
    )

  val surfaceOffsetModifier =
    if (isInStudioPreview) {
      val xOff =
        if (isExpandedAny) config.geometry.offsetXDp.coerceIn(-10f, 10f).dp
        else config.geometry.offsetXDp.dp
      val yOff =
        if (isExpandedAny) (config.geometry.offsetYDp * 0.35f).dp
        else config.geometry.offsetYDp.dp
      Modifier.offset(x = xOff, y = yOff)
    } else {
      Modifier.offset(x = compactExtraOffsetX)
    }

  Box(
    modifier = modifier.fillMaxWidth().then(surfaceOffsetModifier),
    contentAlignment = Alignment.TopCenter,
  ) {
    Box(
      modifier =
        Modifier.padding(
            start = 10.dp,
            end = 10.dp,
            top = 4.dp,
            bottom = 8.dp,
          )
          .then(
            if (showCompact) {
              Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = expandCd,
              ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                coordinator.expand()
              }
            } else {
              Modifier
            }
          ),
      contentAlignment = Alignment.Center,
    ) {
      // Pill outer frame: draws continuous diffused COB-style LED strip trail synced to live notch size & corner radius
      Box(
        modifier =
          Modifier.width(animatedWidth)
            .height(animatedHeight)
            .drawWithCache {
              val cornerPx = animatedCorner.toPx()
              val trackPath =
                Path().apply {
                  addRoundRect(
                    RoundRect(
                      left = 0f,
                      top = 0f,
                      right = size.width,
                      bottom = size.height,
                      cornerRadius = CornerRadius(cornerPx, cornerPx),
                    )
                  )
                }
              val pathMeasure = PathMeasure().apply { setPath(trackPath, forceClosed = true) }
              val totalLen = pathMeasure.length
              val ptsX = FloatArray(NOTCH_TRACK_SAMPLES)
              val ptsY = FloatArray(NOTCH_TRACK_SAMPLES)
              if (totalLen > 0f) {
                for (i in 0 until NOTCH_TRACK_SAMPLES) {
                  val pos = pathMeasure.getPosition((i.toFloat() / NOTCH_TRACK_SAMPLES) * totalLen)
                  ptsX[i] = pos.x
                  ptsY[i] = pos.y
                }
              }

              val widths =
                GlowStrokeWidths(
                  outerMinPx = 3.dp.toPx(),
                  outerMaxPx = 11.dp.toPx(),
                  midMinPx = 1.4.dp.toPx(),
                  midMaxPx = 4.2.dp.toPx(),
                  coreMinPx = 1.dp.toPx(),
                  coreMaxPx = 2.6.dp.toPx(),
                  headOuterHaloPx = 11.dp.toPx(),
                  headMidHaloPx = 5.5.dp.toPx(),
                  headCorePx = 2.2.dp.toPx(),
                )

              onDrawWithContent {
                drawContent()
                if (totalLen > 0f) {
                  val notifProgress =
                    if (runtime.notificationPulseId > 0L) notificationLapProgress.value else 0f
                  drawPerimeterGlow(
                    style = config.appearance.glowStyle,
                    progress = notifProgress,
                    ptsX = ptsX,
                    ptsY = ptsY,
                    primaryColor = primaryColor,
                    secondaryColor = secondaryColor,
                    widths = widths,
                    ambientProgress = ambientPhase,
                    isAmbientActive = isAmbientGlowStyle,
                  )
                }
              }
            }
            .semantics {
              contentDescription = if (isExpandedAny) collapseCd else expandCd
            }
            .testTag("dynamic_notch_pill"),
      ) {
        val borderModifier =
          when (config.appearance.borderStyle) {
            NotchBorderStyle.HAIRLINE ->
              Modifier.border(0.75.dp, Color.White.copy(alpha = 0.08f), shape)
            NotchBorderStyle.NEON_RING ->
              Modifier.border(
                width = 1.5.dp,
                brush =
                  Brush.linearGradient(
                    listOf(
                      primaryColor.copy(alpha = 0.85f),
                      secondaryColor.copy(alpha = 0.65f),
                      primaryColor.copy(alpha = 0.85f),
                    )
                  ),
                shape = shape,
              )
            NotchBorderStyle.CYBER_DOUBLE ->
              Modifier.border(
                width = 1.5.dp,
                color = primaryColor.copy(alpha = 0.80f),
                shape = shape,
              ).drawWithCache {
                val insetPx = 2.5.dp.toPx()
                val innerRadius = (animatedCorner.toPx() - insetPx).coerceAtLeast(4f)
                onDrawWithContent {
                  drawContent()
                  drawRoundRect(
                    color = Color.White.copy(alpha = 0.24f),
                    topLeft = androidx.compose.ui.geometry.Offset(insetPx, insetPx),
                    size =
                      androidx.compose.ui.geometry.Size(
                        width = (size.width - insetPx * 2f).coerceAtLeast(0f),
                        height = (size.height - insetPx * 2f).coerceAtLeast(0f),
                      ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(innerRadius, innerRadius),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.8.dp.toPx()),
                  )
                }
              }
            NotchBorderStyle.STEALTH -> Modifier
          }

        // Clipped pitch-black OLED pill & content
        Box(
          modifier =
            Modifier.fillMaxSize()
              .clip(shape)
              .background(NotchPitchBlack.copy(alpha = config.appearance.pillOpacity))
              .then(borderModifier),
          contentAlignment = Alignment.TopCenter,
        ) {
          if (compactContentAlpha > 0.01f) {
            Box(
              modifier =
                Modifier.fillMaxSize().graphicsLayer { alpha = compactContentAlpha },
            ) {
              CompactNotchContent(
                config = config,
                runtime = runtime,
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
              )
            }
          }

          val displayBanner = activeBanner ?: retainedBanner
          if (notificationAlpha > 0.01f && displayBanner != null) {
            Box(
              modifier =
                Modifier.requiredWidth(expandedWidthDp.dp)
                  .requiredHeight(FIXED_NOTIFICATION_HEIGHT_DP.dp)
                  .graphicsLayer { alpha = notificationAlpha },
            ) {
              NotificationNotchBanner(
                banner = displayBanner,
                config = config,
                runtime = runtime,
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                onOpenApp = {
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  coordinator.onNotificationBannerClicked()
                },
                onDismiss = {
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  coordinator.dismissNotificationBanner()
                },
              )
            }
          }

          if (voiceAlpha > 0.01f) {
            Box(
              modifier =
                Modifier.requiredWidth(expandedWidthDp.dp)
                  .requiredHeight(FIXED_EXPANDED_HEIGHT_DP.dp)
                  .graphicsLayer { alpha = voiceAlpha },
            ) {
              VoiceListeningNotchCard(
                voiceState = runtime.voiceListening,
                config = config,
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                onCopyReply = { reply -> coordinator.copyVoiceReply(reply) },
                onRetryListen = { coordinator.startVoiceListening() },
                onOpenProPaywall = onOpenProPaywall,
                onClose = { coordinator.closeVoiceListening() },
              )
            }
          }

          if (expandedWidgetsAlpha > 0.01f) {
            Box(
              modifier =
                Modifier.requiredWidth(expandedWidthDp.dp)
                  .requiredHeight(FIXED_EXPANDED_HEIGHT_DP.dp)
                  .graphicsLayer { alpha = expandedWidgetsAlpha },
            ) {
              ExpandedNotchContent(
                config = config,
                runtime = runtime,
                enabledWidgets = enabledWidgets,
                coordinator = coordinator,
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                onNavigateToWidgetsTab = onNavigateToWidgetsTab,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun NotificationNotchBanner(
  banner: ActiveNotificationBanner,
  config: NotchConfig,
  runtime: NotchRuntimeState,
  primaryColor: Color,
  secondaryColor: Color,
  onOpenApp: () -> Unit,
  onDismiss: () -> Unit,
) {
  val countdownFraction = remember { Animatable(1f) }
  LaunchedEffect(banner.id) {
    countdownFraction.snapTo(1f)
    countdownFraction.animateTo(
      targetValue = 0f,
      animationSpec = tween(durationMillis = 4500, easing = LinearEasing),
    )
  }

  val timeLabel = remember(banner.id, banner.timestampMillis) {
    formatNotificationArrivalTime(banner.timestampMillis)
  }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .clickable(
          onClickLabel = "Open ${banner.appName}",
          onClick = onOpenApp,
        )
        .testTag("notification_notch_banner")
        .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.SpaceBetween,
  ) {
    // Top meta row: Mascot/Action Logo on left + App Name & Arrival Time + Dismiss button
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        NotchLeftSlot(
          config = config,
          runtime = runtime,
          primaryColor = primaryColor,
          size = 26.dp,
        )
        Text(
          text = banner.appName.ifBlank { "Notification" },
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
          color = primaryColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
        Text(
          text = "• $timeLabel",
          style = MaterialTheme.typography.labelSmall,
          color = TextMuted,
          maxLines = 1,
        )
      }

      IconButton(
        onClick = onDismiss,
        modifier =
          Modifier.size(30.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .testTag("notification_dismiss_btn"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Close,
          contentDescription = "Dismiss notification",
          tint = TextSecondary,
          modifier = Modifier.size(15.dp),
        )
      }
    }

    // Main notification card row: App logo + Title + Message Preview + Open App cue
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (banner.appIcon != null) {
        Image(
          bitmap = banner.appIcon,
          contentDescription = banner.appName,
          contentScale = ContentScale.Crop,
          modifier =
            Modifier.size(44.dp)
              .clip(RoundedCornerShape(12.dp))
              .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
              .testTag("notification_app_logo"),
        )
      } else {
        Box(
          modifier =
            Modifier.size(44.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(
                Brush.linearGradient(
                  listOf(primaryColor.copy(alpha = 0.28f), secondaryColor.copy(alpha = 0.22f))
                )
              )
              .border(1.dp, primaryColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Rounded.Notifications,
            contentDescription = banner.appName,
            tint = primaryColor,
            modifier = Modifier.size(22.dp),
          )
        }
      }

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          text = banner.title.ifBlank { banner.appName },
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = TextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = banner.body.ifBlank { "Tap to open ${banner.appName}" },
          style = MaterialTheme.typography.bodySmall,
          color = TextSecondary,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Box(
        modifier =
          Modifier.clip(RoundedCornerShape(50))
            .background(primaryColor.copy(alpha = 0.14f))
            .border(0.75.dp, primaryColor.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
          contentDescription = "Open app",
          tint = primaryColor,
          modifier = Modifier.size(14.dp),
        )
      }
    }

    // Subtle 4.5s auto-collapse countdown progress strip
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(2.5.dp)
          .clip(CircleShape)
          .background(Color.White.copy(alpha = 0.1f)),
    ) {
      Box(
        modifier =
          Modifier.fillMaxWidth(countdownFraction.value.coerceIn(0f, 1f))
            .fillMaxHeight()
            .clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(primaryColor, secondaryColor))),
      )
    }
  }
}

private fun formatNotificationArrivalTime(timestampMillis: Long): String {
  if (timestampMillis <= 0L) return "Just now"
  val elapsedSec = ((System.currentTimeMillis() - timestampMillis) / 1000L).coerceAtLeast(0L)
  return when {
    elapsedSec < 45L -> "Just now"
    elapsedSec < 3600L -> "${(elapsedSec / 60L).coerceAtLeast(1L)}m ago"
    else -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMillis))
  }
}

@Composable
private fun CompactNotchContent(
  config: NotchConfig,
  runtime: NotchRuntimeState,
  primaryColor: Color,
  secondaryColor: Color,
) {
  Crossfade(
    targetState = runtime.activeCompactHud,
    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
    label = "compact_hud_crossfade",
  ) { hud ->
    if (hud != null) {
      CompactHudOverlayRow(
        hud = hud,
        primaryColor = primaryColor,
        secondaryColor = secondaryColor,
      )
    } else {
      Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        // Left Side: Shaking DND/Silent action logo -> Active Call pulse -> Mascot
        NotchLeftSlot(
          config = config,
          runtime = runtime,
          primaryColor = primaryColor,
          size = (config.geometry.compactHeightDp - 4f).coerceIn(24f, 40f).dp,
        )

        // Right Side: Screen Recording -> Active Call -> Media Album Art -> Focus Timer
        CompactRightSlot(
          config = config,
          runtime = runtime,
          primaryColor = primaryColor,
        )
      }
    }
  }
}

@Composable
private fun CompactHudOverlayRow(
  hud: CompactHudAlert,
  primaryColor: Color,
  secondaryColor: Color,
) {
  Row(
    modifier =
      Modifier.fillMaxSize()
        .padding(horizontal = 10.dp)
        .testTag("compact_hud_overlay"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    when (hud) {
      is CompactHudAlert.Charging -> {
        val accent =
          when {
            hud.isCharging -> StatusSuccess
            hud.isLowBattery -> Color(0xFFFF4D6D)
            else -> TextSecondary
          }
        Icon(
          imageVector =
            if (hud.isLowBattery && !hud.isCharging) Icons.Rounded.BatteryAlert
            else Icons.Rounded.Bolt,
          contentDescription = if (hud.isCharging) "Charging" else "Battery level",
          tint = accent,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text =
            when {
              hud.isCharging -> "${hud.percent}% Charging"
              hud.isLowBattery -> "Low ${hud.percent}%"
              else -> "${hud.percent}%"
            },
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
          color = accent,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
      }

      is CompactHudAlert.BluetoothConnected -> {
        Icon(
          imageVector = Icons.Rounded.Headphones,
          contentDescription = "Audio connected",
          tint = primaryColor,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = hud.deviceName,
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
          color = TextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(StatusSuccess))
      }

      is CompactHudAlert.VolumeHud -> {
        val barFraction by animateFloatAsState(hud.fraction.coerceIn(0f, 1f), label = "vol_bar")
        Icon(
          imageVector =
            if (hud.isMuted) Icons.AutoMirrored.Rounded.VolumeOff
            else Icons.AutoMirrored.Rounded.VolumeUp,
          contentDescription = "Volume ${hud.percent}%",
          tint = if (hud.isMuted) Color(0xFFFF4D6D) else primaryColor,
          modifier = Modifier.size(17.dp),
        )
        Box(
          modifier =
            Modifier.weight(1f)
              .height(6.dp)
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.14f)),
        ) {
          Box(
            modifier =
              Modifier.fillMaxWidth(barFraction)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(primaryColor, secondaryColor))),
          )
        }
        Text(
          text = "${hud.percent}%",
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondary,
          maxLines = 1,
        )
      }

      is CompactHudAlert.BrightnessHud -> {
        val barFraction by animateFloatAsState(hud.fraction.coerceIn(0f, 1f), label = "bright_bar")
        Icon(
          imageVector = Icons.Rounded.BrightnessHigh,
          contentDescription = "Brightness ${hud.percent}%",
          tint = Color(0xFFFFB703),
          modifier = Modifier.size(17.dp),
        )
        Box(
          modifier =
            Modifier.weight(1f)
              .height(6.dp)
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.14f)),
        ) {
          Box(
            modifier =
              Modifier.fillMaxWidth(barFraction)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(Color(0xFFFFB703), primaryColor))),
          )
        }
        Text(
          text = "${hud.percent}%",
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondary,
          maxLines = 1,
        )
      }
    }
  }
}

@Composable
private fun CompactRightSlot(
  config: NotchConfig,
  runtime: NotchRuntimeState,
  primaryColor: Color,
) {
  val ongoing = runtime.ongoingStatus
  when {
    ongoing.isScreenRecording -> {
      val pulse =
        rememberInfiniteTransition(label = "rec_pulse").animateFloat(
          initialValue = 0.35f,
          targetValue = 1f,
          animationSpec =
            infiniteRepeatable(
              animation = tween(650, easing = FastOutSlowInEasing),
              repeatMode = RepeatMode.Reverse,
            ),
          label = "rec_alpha",
        )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
          Modifier.clip(RoundedCornerShape(50))
            .background(Color(0xFFFF2A6D).copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .testTag("compact_recording_badge"),
      ) {
        Box(
          modifier =
            Modifier.size(7.dp)
              .clip(CircleShape)
              .background(Color(0xFFFF2A6D).copy(alpha = pulse.value)),
        )
        Text(
          text = "REC",
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = Color(0xFFFF4D6D),
        )
      }
    }

    ongoing.activeCall != null -> {
      Text(
        text = ongoing.activeCall.callerName.take(10),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = StatusSuccess,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag("compact_call_label"),
      )
    }

    runtime.media.isPlaying -> {
      val art = runtime.media.albumArt
      if (art != null) {
        Image(
          bitmap = art,
          contentDescription = runtime.media.trackTitle,
          contentScale = ContentScale.Crop,
          modifier =
            Modifier.size((config.geometry.compactHeightDp - 8f).coerceIn(20f, 32f).dp)
              .clip(RoundedCornerShape(8.dp))
              .border(0.75.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
              .testTag("compact_album_art"),
        )
      } else {
        Box(
          modifier =
            Modifier.size((config.geometry.compactHeightDp - 8f).coerceIn(20f, 32f).dp)
              .clip(RoundedCornerShape(8.dp))
              .background(primaryColor.copy(alpha = 0.22f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = runtime.media.trackTitle,
            tint = primaryColor,
            modifier = Modifier.size(14.dp),
          )
        }
      }
    }

    runtime.timer.isRunning -> {
      Text(
        text = runtime.timer.formattedTime,
        style = MaterialTheme.typography.labelSmall,
        color = primaryColor,
      )
    }

    else -> {
      Spacer(modifier = Modifier.width(4.dp))
    }
  }
}

@Composable
private fun ExpandedNotchContent(
  config: NotchConfig,
  runtime: NotchRuntimeState,
  enabledWidgets: List<com.example.model.NotchWidgetType>,
  coordinator: NotchRuntimeCoordinator,
  primaryColor: Color,
  secondaryColor: Color,
  onNavigateToWidgetsTab: (() -> Unit)?,
) {
  val haptic = LocalHapticFeedback.current
  val pagerState =
    rememberPagerState(
      initialPage = runtime.activeWidgetPage.coerceIn(0, (enabledWidgets.size - 1).coerceAtLeast(0)),
      pageCount = { enabledWidgets.size },
    )

  LaunchedEffect(runtime.activeWidgetPage, enabledWidgets.size) {
    val safeTarget =
      runtime.activeWidgetPage.coerceIn(0, (enabledWidgets.size - 1).coerceAtLeast(0))
    if (pagerState.currentPage != safeTarget) {
      pagerState.scrollToPage(safeTarget)
    }
  }

  LaunchedEffect(pagerState.currentPage) {
    coordinator.onWidgetPageChanged(pagerState.currentPage)
  }

  Column(
    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.SpaceBetween,
  ) {
    // Header: Left-Side Mascot/Action Logo + Active Widget Title + Optional Call Chip + Collapse button
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
        NotchLeftSlot(
          config = config,
          runtime = runtime,
          primaryColor = primaryColor,
          secondaryColor = secondaryColor,
          size = 36.dp,
          onMascotTapped = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            coordinator.onMascotTapped()
          },
          onMascotLongPressed = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            coordinator.startVoiceListening()
          },
        )
        val currentWidget = enabledWidgets.getOrNull(pagerState.currentPage)
        Text(
          text = currentWidget?.title ?: "MatusNotch",
          style = MaterialTheme.typography.titleMedium,
          color = TextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (runtime.ongoingStatus.activeCall != null) {
          Row(
            modifier =
              Modifier.clip(RoundedCornerShape(50))
                .background(StatusSuccess.copy(alpha = 0.18f))
                .border(0.75.dp, StatusSuccess.copy(alpha = 0.65f), RoundedCornerShape(50))
                .clickable { coordinator.openActiveCall() }
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .testTag("expanded_call_chip"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Call,
              contentDescription = "Open active call",
              tint = StatusSuccess,
              modifier = Modifier.size(12.dp),
            )
            Text(
              text = "Call",
              style = MaterialTheme.typography.labelSmall,
              color = StatusSuccess,
            )
          }
        }
      }

      IconButton(
        onClick = {
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          coordinator.collapse()
        },
        modifier =
          Modifier.size(36.dp)
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .testTag("notch_collapse_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.KeyboardArrowUp,
          contentDescription = stringResource(R.string.cd_collapse_notch),
          tint = TextPrimary,
        )
      }
    }

    AnimatedVisibility(visible = runtime.statusMessage != null) {
      Text(
        text = runtime.statusMessage.orEmpty(),
        style = MaterialTheme.typography.labelSmall,
        color = secondaryColor,
        modifier = Modifier.padding(vertical = 2.dp),
      )
    }

    HorizontalPager(
      state = pagerState,
      pageSpacing = 10.dp,
      modifier =
        Modifier.fillMaxWidth()
          .weight(1f)
          .padding(vertical = 6.dp)
          .testTag("notch_widget_pager"),
    ) { page ->
      val widgetType = enabledWidgets[page]
      NotchWidgetPage(
        widgetType = widgetType,
        config = config,
        runtime = runtime,
        coordinator = coordinator,
        onNavigateToWidgetsTab = onNavigateToWidgetsTab,
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      enabledWidgets.forEachIndexed { index, _ ->
        val selected = index == pagerState.currentPage
        Box(
          modifier =
            Modifier.padding(horizontal = 3.dp)
              .height(5.dp)
              .width(if (selected) 18.dp else 5.dp)
              .clip(CircleShape)
              .background(if (selected) primaryColor else Color.White.copy(alpha = 0.22f))
        )
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotchLeftSlot(
  config: NotchConfig,
  runtime: NotchRuntimeState,
  primaryColor: Color,
  secondaryColor: Color = primaryColor,
  size: Dp,
  onMascotTapped: (() -> Unit)? = null,
  onMascotLongPressed: (() -> Unit)? = null,
) {
  val slotMode: Any? =
    when {
      runtime.activeModeAlert != null -> runtime.activeModeAlert
      runtime.ongoingStatus.activeCall != null -> "CALL"
      else -> null
    }

  Crossfade(
    targetState = slotMode,
    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
    label = "notch_left_slot",
  ) { state ->
    when (state) {
      is ActiveModeAlert ->
        ActionModeLogoBadge(
          alert = state,
          primaryColor = primaryColor,
          size = size,
        )
      "CALL" ->
        ActiveCallLogoBadge(size = size)
      else -> {
        if (onMascotTapped != null) {
          val bounceScale = remember { Animatable(1f) }
          LaunchedEffect(runtime.mascotTapId) {
            if (runtime.mascotTapId > 0L) {
              bounceScale.snapTo(0.80f)
              bounceScale.animateTo(
                targetValue = 1.16f,
                animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
              )
              bounceScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
              )
            }
          }
          Box(
            modifier =
              Modifier.size(size)
                .combinedClickable(
                  interactionSource = remember { MutableInteractionSource() },
                  indication = null,
                  onClickLabel = "Pet mascot",
                  onClick = onMascotTapped,
                  onLongClickLabel = "Listen with Matus voice",
                  onLongClick = onMascotLongPressed,
                )
                .testTag("expanded_mascot_tap_target"),
            contentAlignment = Alignment.Center,
          ) {
            MascotView(
              state = runtime.mascotState,
              appearance = config.appearance,
              size = size,
              modifier =
                Modifier.graphicsLayer {
                  scaleX = bounceScale.value
                  scaleY = bounceScale.value
                },
            )
            MascotParticleBurstOverlay(
              tapId = runtime.mascotTapId,
              primaryColor = primaryColor,
              secondaryColor = secondaryColor,
              modifier = Modifier.matchParentSize(),
            )
          }
        } else {
          MascotView(
            state = runtime.mascotState,
            appearance = config.appearance,
            size = size,
          )
        }
      }
    }
  }
}

@Composable
private fun ActiveCallLogoBadge(size: Dp) {
  val pulse =
    rememberInfiniteTransition(label = "call_pulse").animateFloat(
      initialValue = 0.72f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(750, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "call_scale",
    )
  Box(
    modifier =
      Modifier.size(size)
        .graphicsLayer {
          scaleX = pulse.value
          scaleY = pulse.value
        }
        .clip(CircleShape)
        .background(StatusSuccess.copy(alpha = 0.22f))
        .testTag("notch_active_call_badge"),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = Icons.Rounded.Call,
      contentDescription = "Active call",
      tint = StatusSuccess,
      modifier = Modifier.size((size.value * 0.58f).dp),
    )
  }
}

@Composable
private fun ActionModeLogoBadge(
  alert: ActiveModeAlert,
  primaryColor: Color,
  size: Dp,
) {
  val progress = remember(alert.id) { Animatable(0f) }
  LaunchedEffect(alert.id) {
    progress.snapTo(0f)
    progress.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 3000, easing = LinearEasing),
    )
  }

  val p = progress.value
  val burstEnvelope = sin(p * 3f * PI).toFloat().let { it * it } * (1f - p * 0.25f)
  val shakeWave = sin(p * 28f * PI).toFloat()
  val rotationDeg = shakeWave * burstEnvelope * 21f
  val nudgeX = shakeWave * burstEnvelope * 1.6f
  val alphaScale =
    when {
      p < 0.07f -> (p / 0.07f).coerceIn(0f, 1f)
      p > 0.90f -> ((1f - p) / 0.10f).coerceIn(0f, 1f)
      else -> 1f
    }

  val (icon, tint, label) =
    when (alert.kind) {
      ModeAlertKind.DND_ON ->
        Triple(Icons.Rounded.DoNotDisturbOn, Color(0xFFA855F7), "Do Not Disturb enabled")
      ModeAlertKind.DND_OFF ->
        Triple(Icons.Rounded.DoNotDisturbOff, StatusSuccess, "Do Not Disturb disabled")
      ModeAlertKind.SILENT_ON ->
        Triple(Icons.Rounded.NotificationsOff, Color(0xFFFF4D6D), "Silent mode enabled")
      ModeAlertKind.SILENT_OFF ->
        Triple(Icons.Rounded.NotificationsActive, primaryColor, "Silent mode disabled")
    }

  Box(
    modifier =
      Modifier.size(size)
        .graphicsLayer {
          alpha = alphaScale
          val popScale = 0.78f + 0.22f * alphaScale
          scaleX = popScale
          scaleY = popScale
          rotationZ = rotationDeg
          translationX = nudgeX.dp.toPx()
        }
        .clip(CircleShape)
        .background(tint.copy(alpha = 0.20f))
        .testTag("notch_mode_alert_badge"),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = tint,
      modifier = Modifier.size((size.value * 0.64f).dp),
    )
  }
}
