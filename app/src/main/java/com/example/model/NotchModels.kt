package com.example.model

import android.app.PendingIntent
import androidx.compose.ui.graphics.ImageBitmap

enum class MascotState {
  WAKING_UP,
  IDLE,
  LISTENING_MUSIC,
  LISTENING_VOICE,
  SLEEPING,
}

enum class ModeAlertKind {
  DND_ON,
  DND_OFF,
  SILENT_ON,
  SILENT_OFF,
}

data class ActiveModeAlert(
  val id: Long = 0L,
  val kind: ModeAlertKind = ModeAlertKind.DND_ON,
)

sealed interface CompactHudAlert {
  val id: Long

  data class Charging(
    override val id: Long,
    val percent: Int,
    val isCharging: Boolean,
    val isLowBattery: Boolean = false,
  ) : CompactHudAlert

  data class BluetoothConnected(
    override val id: Long,
    val deviceName: String,
    val batteryPercent: Int? = null,
  ) : CompactHudAlert

  data class VolumeHud(
    override val id: Long,
    val fraction: Float,
    val percent: Int,
    val isMuted: Boolean,
  ) : CompactHudAlert

  data class BrightnessHud(
    override val id: Long,
    val fraction: Float,
    val percent: Int,
  ) : CompactHudAlert
}

data class ActiveCallInfo(
  val key: String = "",
  val callerName: String = "",
  val subtitle: String = "Ongoing call",
  val startTimeMillis: Long = 0L,
  val contentIntent: PendingIntent? = null,
  val packageName: String = "",
)

data class OngoingStatusState(
  val activeCall: ActiveCallInfo? = null,
  val isScreenRecording: Boolean = false,
  val recordingStartMillis: Long = 0L,
)

enum class NotificationFilterMode(val label: String) {
  ALL_APPS("All Apps"),
  ONLY_SELECTED("Only Selected"),
  MUTE_SELECTED("Mute Selected"),
}

enum class NotchBorderStyle(val label: String) {
  HAIRLINE("Hairline"),
  NEON_RING("Neon Rim"),
  CYBER_DOUBLE("Cyber Frame"),
  STEALTH("Borderless"),
}

enum class NotchGlowStyle(val label: String) {
  LED_RACE("LED Strip"),
  DUAL_COMET("Dual Comet"),
  BREATHING_AURA("Breathing Halo"),
  PRISM_WAVE("Prism Wave"),
}

enum class NotchWidgetType(
  val title: String,
  val subtitle: String,
) {
  MEDIA("Now Playing", "Album art, credits & media controls"),
  QUICK_TOGGLES("Quick Toggles", "Wi-Fi, Bluetooth, Flashlight & DND"),
  DEVICE_ACTIONS("Device Actions", "Quick screenshot, lock screen & auto-rotate"),
  CALCULATOR("Calculator", "Quick split keypad & copyable math in notch"),
  FOCUS_TIMER("Focus Timer", "Countdown ring & Pomodoro quick presets"),
  APP_SHORTCUTS("Apps & Power", "Live battery telemetry & pinned app launchers"),
}

enum class AccentPreset(
  val label: String,
  val primaryHex: Long,
  val secondaryHex: Long,
) {
  CYAN("Electric Cyan", 0xFF00E5FF, 0xFF38BDF8),
  VIOLET("Neon Violet", 0xFFA855F7, 0xFFEC4899),
  MINT("Cyber Mint", 0xFF00F5A0, 0xFF06B6D4),
  AMBER("Solar Amber", 0xFFFFB703, 0xFFFB7185),
  CRIMSON("Crimson Pulse", 0xFFFF2A6D, 0xFFA855F7),
  CUSTOM("Color Wheel", 0xFF00E5FF, 0xFF38BDF8),
}

data class NotchGeometry(
  val compactWidthDp: Float = 156f,
  val compactHeightDp: Float = 38f,
  val cornerRadiusDp: Float = 26f,
  val offsetXDp: Float = 0f,
  val offsetYDp: Float = 10f,
)

data class NotchAppearance(
  val accentPreset: AccentPreset = AccentPreset.CYAN,
  val customAccentHex: Long = 0xFF00E5FF,
  val pillOpacity: Float = 0.98f,
  val borderStyle: NotchBorderStyle = NotchBorderStyle.HAIRLINE,
  val glowStyle: NotchGlowStyle = NotchGlowStyle.LED_RACE,
) {
  fun resolvePrimaryHex(): Long =
    if (accentPreset == AccentPreset.CUSTOM) {
      customAccentHex or 0xFF000000L
    } else {
      accentPreset.primaryHex
    }

  fun resolveSecondaryHex(): Long =
    if (accentPreset == AccentPreset.CUSTOM) {
      deriveCompanionAccentHex(resolvePrimaryHex())
    } else {
      accentPreset.secondaryHex
    }
}

fun deriveCompanionAccentHex(argbHex: Long): Long {
  val r = ((argbHex shr 16) and 0xFF) / 255f
  val g = ((argbHex shr 8) and 0xFF) / 255f
  val b = (argbHex and 0xFF) / 255f

  val maxC = maxOf(r, g, b)
  val minC = minOf(r, g, b)
  val delta = maxC - minC

  var hue =
    when {
      delta <= 0.0001f -> 195f
      maxC == r -> 60f * (((g - b) / delta) % 6f)
      maxC == g -> 60f * (((b - r) / delta) + 2f)
      else -> 60f * (((r - g) / delta) + 4f)
    }
  if (hue < 0f) hue += 360f
  val sat = if (maxC <= 0.0001f) 0.78f else (delta / maxC).coerceIn(0.45f, 1f)
  val value = maxC.coerceIn(0.65f, 1f)

  val shiftedHue = (hue + 28f) % 360f
  return hsvToArgbHex(shiftedHue, sat, value)
}

fun hsvToArgbHex(hue: Float, saturation: Float, value: Float): Long {
  val h = ((hue % 360f) + 360f) % 360f
  val s = saturation.coerceIn(0f, 1f)
  val v = value.coerceIn(0f, 1f)
  val c = v * s
  val x = c * (1f - kotlin.math.abs(((h / 60f) % 2f) - 1f))
  val m = v - c
  val (rp, gp, bp) =
    when {
      h < 60f -> Triple(c, x, 0f)
      h < 120f -> Triple(x, c, 0f)
      h < 180f -> Triple(0f, c, x)
      h < 240f -> Triple(0f, x, c)
      h < 300f -> Triple(x, 0f, c)
      else -> Triple(c, 0f, x)
    }
  val r = (((rp + m) * 255f).toInt()).coerceIn(0, 255)
  val g = (((gp + m) * 255f).toInt()).coerceIn(0, 255)
  val b = (((bp + m) * 255f).toInt()).coerceIn(0, 255)
  return (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
}

data class WidgetSlot(
  val type: NotchWidgetType,
  val enabled: Boolean = true,
)

data class NotchConfig(
  val geometry: NotchGeometry = NotchGeometry(),
  val appearance: NotchAppearance = NotchAppearance(),
  val widgetSlots: List<WidgetSlot> = NotchWidgetType.entries.map { WidgetSlot(it, enabled = true) },
  val pinnedPackages: List<String> = emptyList(),
  val autoSleepSeconds: Int = 15,
  val autoCollapseSeconds: Int = 10,
  val autoHideInLandscape: Boolean = true,
  val userGeminiApiKey: String = "",
  val notificationFilterMode: NotificationFilterMode = NotificationFilterMode.ALL_APPS,
  val filteredNotificationPackages: Set<String> = emptySet(),
) {
  val enabledWidgets: List<NotchWidgetType>
    get() {
      val active = widgetSlots.filter { it.enabled }.map { it.type }
      return active.ifEmpty { listOf(NotchWidgetType.MEDIA) }
    }

  fun shouldPopupNotification(packageName: String): Boolean =
    when (notificationFilterMode) {
      NotificationFilterMode.ALL_APPS -> true
      NotificationFilterMode.ONLY_SELECTED -> filteredNotificationPackages.contains(packageName)
      NotificationFilterMode.MUTE_SELECTED -> !filteredNotificationPackages.contains(packageName)
    }
}

data class MediaPlaybackState(
  val isPlaying: Boolean = false,
  val trackTitle: String = "No active audio",
  val artistAndWriters: String = "Play music or tap Play",
  val sourceApp: String = "",
  val albumArt: ImageBitmap? = null,
  val hasListenerAccess: Boolean = false,
)

data class ActiveNotificationBanner(
  val id: Long = 0L,
  val packageName: String = "",
  val appName: String = "",
  val appIcon: ImageBitmap? = null,
  val title: String = "",
  val body: String = "",
  val timestampMillis: Long = 0L,
  val contentIntent: PendingIntent? = null,
)

data class FocusTimerState(
  val totalSeconds: Int = 25 * 60,
  val remainingSeconds: Int = 25 * 60,
  val isRunning: Boolean = false,
) {
  val progress: Float
    get() = if (totalSeconds <= 0) 0f else (remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)

  val formattedTime: String
    get() {
      val mins = remainingSeconds / 60
      val secs = remainingSeconds % 60
      return "%02d:%02d".format(mins, secs)
    }
}

data class SystemTelemetryState(
  val isTorchOn: Boolean = false,
  val isDndOn: Boolean = false,
  val isSilentOn: Boolean = false,
  val isWifiOn: Boolean = false,
  val isBluetoothOn: Boolean = false,
  val isAutoRotateOn: Boolean = false,
  val batteryPercent: Int = 100,
  val isCharging: Boolean = false,
  val hasOverlayPermission: Boolean = false,
  val hasNotificationListenerPermission: Boolean = false,
  val hasDndPolicyPermission: Boolean = false,
  val hasBluetoothPermission: Boolean = true,
  val hasLockscreenAccess: Boolean = false,
  val hasWriteSettingsPermission: Boolean = false,
  val hasRecordAudioPermission: Boolean = false,
  val isSpeechRecognitionAvailable: Boolean = false,
  val isOverlayServiceRunning: Boolean = false,
)

enum class VoiceListeningPhase {
  INACTIVE,
  LISTENING,
  THINKING,
  FINISHED,
}

data class VoiceListeningState(
  val phase: VoiceListeningPhase = VoiceListeningPhase.INACTIVE,
  val transcript: String = "",
  val audioLevel: Float = 0f,
  val confirmationBadge: String? = null,
  val localInfoTitle: String? = null,
  val localInfoReply: String? = null,
  val aiReply: String? = null,
  val needsApiKeyHint: Boolean = false,
  val requiresProUpgrade: Boolean = false,
  val errorMessage: String? = null,
  val sessionId: Long = 0L,
)

data class CalculatorState(
  val expression: String = "",
  val display: String = "0",
  val pendingOp: String? = null,
  val storedValue: Double? = null,
  val clearOnNextDigit: Boolean = false,
  val hasError: Boolean = false,
)

data class NotchRuntimeState(
  val isExpanded: Boolean = false,
  val mascotState: MascotState = MascotState.IDLE,
  val mascotTapId: Long = 0L,
  val media: MediaPlaybackState = MediaPlaybackState(),
  val activeNotification: ActiveNotificationBanner? = null,
  val activeModeAlert: ActiveModeAlert? = null,
  val activeCompactHud: CompactHudAlert? = null,
  val ongoingStatus: OngoingStatusState = OngoingStatusState(),
  val isLandscapeHidden: Boolean = false,
  val voiceListening: VoiceListeningState = VoiceListeningState(),
  val timer: FocusTimerState = FocusTimerState(),
  val calculator: CalculatorState = CalculatorState(),
  val telemetry: SystemTelemetryState = SystemTelemetryState(),
  val activeWidgetPage: Int = 0,
  val notificationPulseId: Long = 0L,
  val statusMessage: String? = null,
)

data class LaunchableAppInfo(
  val packageName: String,
  val label: String,
)
