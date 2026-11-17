package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DoNotDisturbOff
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FocusTimerState
import com.example.model.MediaPlaybackState
import com.example.model.NotchConfig
import com.example.model.NotchRuntimeState
import com.example.model.NotchWidgetType
import com.example.model.SystemTelemetryState
import com.example.service.NotchRuntimeCoordinator
import com.example.system.SystemBridges
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun NotchWidgetPage(
  widgetType: NotchWidgetType,
  config: NotchConfig,
  runtime: NotchRuntimeState,
  coordinator: NotchRuntimeCoordinator,
  isVisiblePage: Boolean = true,
  onNavigateToWidgetsTab: (() -> Unit)? = null,
) {
  val primaryColor = Color(config.appearance.resolvePrimaryHex())
  val secondaryColor = Color(config.appearance.resolveSecondaryHex())

  when (widgetType) {
    NotchWidgetType.MEDIA ->
      MediaWidgetCard(
        media = runtime.media,
        primaryColor = primaryColor,
        secondaryColor = secondaryColor,
        isVisiblePage = isVisiblePage,
        onPlayPause = { coordinator.togglePlayPause() },
        onNext = { coordinator.skipNext() },
        onPrevious = { coordinator.skipPrevious() },
      )
    NotchWidgetType.QUICK_TOGGLES ->
      QuickTogglesWidgetCard(
        telemetry = runtime.telemetry,
        primaryColor = primaryColor,
        onToggleTorch = { coordinator.toggleTorch() },
        onToggleDnd = { coordinator.toggleDnd() },
        onToggleWifi = { coordinator.toggleWifi() },
        onToggleBluetooth = { coordinator.toggleBluetooth() },
      )
    NotchWidgetType.DEVICE_ACTIONS ->
      DeviceActionsWidgetCard(
        telemetry = runtime.telemetry,
        primaryColor = primaryColor,
        onScreenshot = { coordinator.triggerScreenshot() },
        onLockScreen = { coordinator.triggerLockScreen() },
        onToggleAutoRotate = { coordinator.toggleAutoRotate() },
      )
    NotchWidgetType.CALCULATOR ->
      CalculatorWidgetCard(
        calculator = runtime.calculator,
        primaryColor = primaryColor,
        secondaryColor = secondaryColor,
        onKey = { coordinator.onCalculatorInput(it) },
        onCopy = { coordinator.copyCalculatorResult() },
      )
    NotchWidgetType.FOCUS_TIMER ->
      FocusTimerWidgetCard(
        timer = runtime.timer,
        primaryColor = primaryColor,
        secondaryColor = secondaryColor,
        onStartPause = { coordinator.startPauseTimer() },
        onReset = { coordinator.resetTimer() },
        onSelectMinutes = { coordinator.setTimerMinutes(it) },
        onAddMinute = { coordinator.addTimerMinute() },
      )
    NotchWidgetType.APP_SHORTCUTS ->
      AppShortcutsWidgetCard(
        pinnedPackages = config.pinnedPackages,
        telemetry = runtime.telemetry,
        primaryColor = primaryColor,
        onLaunchApp = { coordinator.launchPinnedApp(it) },
        onOpenShortcutsSetup = onNavigateToWidgetsTab,
      )
  }
}

@Composable
private fun MediaWidgetCard(
  media: MediaPlaybackState,
  primaryColor: Color,
  secondaryColor: Color,
  isVisiblePage: Boolean = true,
  onPlayPause: () -> Unit,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
) {
  val haptic = LocalHapticFeedback.current
  val wavePhase =
    if (media.isPlaying && isVisiblePage) {
      val infiniteTransition = rememberInfiniteTransition(label = "media_wave")
      infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec =
          infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
          ),
        label = "wave_phase",
      ).value
    } else {
      0f
    }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .padding(horizontal = 6.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Album or Video Cover Art
      val art = media.albumArt
      if (art != null) {
        Image(
          bitmap = art,
          contentDescription = media.trackTitle,
          contentScale = ContentScale.Crop,
          modifier =
            Modifier.size(48.dp)
              .clip(RoundedCornerShape(12.dp))
              .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
              .testTag("widget_media_album_art"),
        )
      } else {
        Box(
          modifier =
            Modifier.size(48.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(primaryColor.copy(alpha = 0.16f))
              .border(1.dp, primaryColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = primaryColor,
            modifier = Modifier.size(22.dp),
          )
        }
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = media.trackTitle,
          style = MaterialTheme.typography.titleMedium,
          color = TextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = media.artistAndWriters,
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      // Live audio waveform equalizer
      Canvas(modifier = Modifier.width(46.dp).height(24.dp)) {
        val barCount = 7
        val spacing = size.width / barCount
        val barWidth = spacing * 0.56f
        for (i in 0 until barCount) {
          val norm =
            if (media.isPlaying) {
              0.24f + 0.74f * abs(sin(wavePhase + i * 0.7f))
            } else {
              0.18f
            }
          val barH = size.height * norm
          drawRoundRect(
            brush = Brush.verticalGradient(listOf(primaryColor, secondaryColor)),
            topLeft = Offset(i * spacing, (size.height - barH) / 2f),
            size = Size(barWidth, barH),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
          )
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
        onClick = {
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          onPrevious()
        },
        modifier = Modifier.minimumInteractiveComponentSize().testTag("widget_media_prev"),
      ) {
        Icon(
          imageVector = Icons.Rounded.SkipPrevious,
          contentDescription = "Previous track",
          tint = TextPrimary,
        )
      }

      Box(
        modifier =
          Modifier.size(46.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(primaryColor, secondaryColor)))
            .clickable {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onPlayPause()
            }
            .testTag("widget_media_play_pause"),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = if (media.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
          contentDescription = if (media.isPlaying) "Pause media" else "Play media",
          tint = Color.Black,
          modifier = Modifier.size(26.dp),
        )
      }

      IconButton(
        onClick = {
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          onNext()
        },
        modifier = Modifier.minimumInteractiveComponentSize().testTag("widget_media_next"),
      ) {
        Icon(
          imageVector = Icons.Rounded.SkipNext,
          contentDescription = "Next track",
          tint = TextPrimary,
        )
      }
    }
  }
}

@Composable
private fun QuickTogglesWidgetCard(
  telemetry: SystemTelemetryState,
  primaryColor: Color,
  onToggleTorch: () -> Unit,
  onToggleDnd: () -> Unit,
  onToggleWifi: () -> Unit,
  onToggleBluetooth: () -> Unit,
) {
  val haptic = LocalHapticFeedback.current
  Row(
    modifier =
      Modifier.fillMaxSize()
        .padding(horizontal = 4.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    QuickToggleTile(
      label = if (telemetry.isWifiOn) "Wi-Fi On" else "Wi-Fi",
      icon = Icons.Rounded.Wifi,
      active = telemetry.isWifiOn,
      accent = primaryColor,
      testTag = "widget_toggle_wifi",
      modifier = Modifier.weight(1f),
      onClick = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onToggleWifi()
      },
    )
    QuickToggleTile(
      label = if (telemetry.isBluetoothOn) "BT On" else "Bluetooth",
      icon = Icons.Rounded.Bluetooth,
      active = telemetry.isBluetoothOn,
      accent = primaryColor,
      testTag = "widget_toggle_bluetooth",
      modifier = Modifier.weight(1f),
      onClick = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onToggleBluetooth()
      },
    )
    QuickToggleTile(
      label = if (telemetry.isTorchOn) "Torch On" else "Torch",
      icon = if (telemetry.isTorchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
      active = telemetry.isTorchOn,
      accent = primaryColor,
      testTag = "widget_toggle_torch",
      modifier = Modifier.weight(1f),
      onClick = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onToggleTorch()
      },
    )
    QuickToggleTile(
      label = if (telemetry.isDndOn) "DND On" else "Focus DND",
      icon = if (telemetry.isDndOn) Icons.Rounded.DoNotDisturbOn else Icons.Rounded.DoNotDisturbOff,
      active = telemetry.isDndOn,
      accent = primaryColor,
      testTag = "widget_toggle_dnd",
      modifier = Modifier.weight(1f),
      onClick = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onToggleDnd()
      },
    )
  }
}

@Composable
private fun DeviceActionsWidgetCard(
  telemetry: SystemTelemetryState,
  primaryColor: Color,
  onScreenshot: () -> Unit,
  onLockScreen: () -> Unit,
  onToggleAutoRotate: () -> Unit,
) {
  val haptic = LocalHapticFeedback.current
  Row(
    modifier =
      Modifier.fillMaxSize()
        .padding(horizontal = 4.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    QuickToggleTile(
      label = "Screenshot",
      icon = Icons.Rounded.PhotoCamera,
      active = false,
      accent = primaryColor,
      testTag = "widget_action_screenshot",
      modifier = Modifier.weight(1f),
      onClick = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onScreenshot()
      },
    )
    QuickToggleTile(
      label = "Lock Screen",
      icon = Icons.Rounded.Lock,
      active = false,
      accent = primaryColor,
      testTag = "widget_action_lock",
      modifier = Modifier.weight(1f),
      onClick = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onLockScreen()
      },
    )
    QuickToggleTile(
      label = if (telemetry.isAutoRotateOn) "Auto-Rotate" else "Portrait Lock",
      icon =
        if (telemetry.isAutoRotateOn) Icons.Rounded.ScreenRotation
        else Icons.Rounded.ScreenLockRotation,
      active = telemetry.isAutoRotateOn,
      accent = primaryColor,
      testTag = "widget_action_autorotate",
      modifier = Modifier.weight(1f),
      onClick = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onToggleAutoRotate()
      },
    )
  }
}

@Composable
private fun QuickToggleTile(
  label: String,
  icon: ImageVector,
  active: Boolean,
  accent: Color,
  testTag: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val bg = if (active) accent.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.05f)
  val border = if (active) accent.copy(alpha = 0.75f) else Color.Transparent
  val tint = if (active) accent else TextPrimary

  Column(
    modifier =
      modifier
        .height(82.dp)
        .minimumInteractiveComponentSize()
        .clip(RoundedCornerShape(14.dp))
        .background(bg)
        .then(
          if (active) Modifier.border(1.dp, border, RoundedCornerShape(14.dp)) else Modifier
        )
        .clickable(onClick = onClick)
        .testTag(testTag)
        .padding(horizontal = 4.dp, vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = if (active) accent else TextSecondary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun FocusTimerWidgetCard(
  timer: FocusTimerState,
  primaryColor: Color,
  secondaryColor: Color,
  onStartPause: () -> Unit,
  onReset: () -> Unit,
  onSelectMinutes: (Int) -> Unit,
  onAddMinute: () -> Unit,
) {
  val haptic = LocalHapticFeedback.current
  Row(
    modifier =
      Modifier.fillMaxSize()
        .padding(horizontal = 6.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        drawArc(
          color = Color.White.copy(alpha = 0.1f),
          startAngle = -90f,
          sweepAngle = 360f,
          useCenter = false,
          style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
        )
        drawArc(
          brush = Brush.sweepGradient(listOf(primaryColor, secondaryColor, primaryColor)),
          startAngle = -90f,
          sweepAngle = 360f * timer.progress,
          useCenter = false,
          style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
        )
      }
      Text(
        text = timer.formattedTime,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
        color = TextPrimary,
      )
    }

    Spacer(modifier = Modifier.width(10.dp))

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(5 to "5m", 15 to "15m", 25 to "25m").forEach { (mins, label) ->
          TimerChip(
            label = label,
            accent = primaryColor,
            testTag = "widget_timer_${mins}m",
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onSelectMinutes(mins)
            },
          )
        }
        TimerChip(
          label = "+1m",
          accent = secondaryColor,
          testTag = "widget_timer_plus1m",
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onAddMinute()
          },
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier =
            Modifier.weight(1f)
              .height(40.dp)
              .minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(12.dp))
              .background(Brush.horizontalGradient(listOf(primaryColor, secondaryColor)))
              .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onStartPause()
              }
              .testTag("widget_timer_start_pause"),
          contentAlignment = Alignment.Center,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
              imageVector = if (timer.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
              contentDescription = if (timer.isRunning) "Pause timer" else "Start timer",
              tint = Color.Black,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = if (timer.isRunning) "Pause" else "Start",
              style = MaterialTheme.typography.labelLarge,
              color = Color.Black,
            )
          }
        }

        IconButton(
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onReset()
          },
          modifier =
            Modifier.size(40.dp)
              .minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(12.dp))
              .background(Color.White.copy(alpha = 0.08f))
              .testTag("widget_timer_reset"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = "Reset timer",
            tint = TextPrimary,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun TimerChip(
  label: String,
  accent: Color,
  testTag: String,
  onClick: () -> Unit,
) {
  Box(
    modifier =
      Modifier.clip(RoundedCornerShape(8.dp))
        .background(accent.copy(alpha = 0.14f))
        .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
        .clickable(onClick = onClick)
        .testTag(testTag)
        .padding(horizontal = 8.dp, vertical = 4.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(text = label, style = MaterialTheme.typography.labelSmall, color = accent)
  }
}

@Composable
private fun AppShortcutsWidgetCard(
  pinnedPackages: List<String>,
  telemetry: SystemTelemetryState,
  primaryColor: Color,
  onLaunchApp: (String) -> Unit,
  onOpenShortcutsSetup: (() -> Unit)?,
) {
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  val displayApps = remember(pinnedPackages) { pinnedPackages.take(4) }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .padding(horizontal = 6.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
          imageVector =
            if (telemetry.isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
          contentDescription = "Battery telemetry",
          tint = primaryColor,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = "${telemetry.batteryPercent}%${if (telemetry.isCharging) " • Charging" else ""}",
          style = MaterialTheme.typography.labelMedium,
          color = TextPrimary,
        )
      }
      Text(
        text = "Quick Launch",
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted,
      )
    }

    if (displayApps.isEmpty()) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .clickable(enabled = onOpenShortcutsSetup != null) { onOpenShortcutsSetup?.invoke() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        Icon(
          imageVector = Icons.Rounded.TouchApp,
          contentDescription = null,
          tint = primaryColor,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Pin favorite apps in Widgets tab",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
        )
      }
    } else {
      Row(
        modifier = Modifier.fillMaxWidth().weight(1f),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        displayApps.forEach { pkg ->
          val label = remember(pkg) { SystemBridges.loadAppLabel(context, pkg) }
          val iconBitmap = remember(pkg) { SystemBridges.loadCachedScaledAppIcon(context, pkg, maxDim = 96) }
          Column(
            modifier =
              Modifier.weight(1f)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .clickable {
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onLaunchApp(pkg)
                }
                .testTag("widget_shortcut_$pkg")
                .padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            if (iconBitmap != null) {
              Image(
                bitmap = iconBitmap,
                contentDescription = label,
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)),
              )
            } else {
              Box(
                modifier =
                  Modifier.size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(primaryColor.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = label.take(1).uppercase(),
                  style = MaterialTheme.typography.labelLarge,
                  color = primaryColor,
                )
              }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = label,
              style = MaterialTheme.typography.labelSmall,
              color = TextSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}
