package com.example.service

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.content.ContextCompat
import com.example.billing.ProBillingManager
import com.example.data.NotchSettingsRepository
import com.example.model.ActiveCallInfo
import com.example.model.ActiveModeAlert
import com.example.model.ActiveNotificationBanner
import com.example.model.CalculatorState
import com.example.model.CompactHudAlert
import com.example.model.EntitlementResolver
import com.example.model.FocusTimerState
import com.example.model.MascotState
import com.example.model.MediaPlaybackState
import com.example.model.ModeAlertKind
import com.example.model.NotchConfig
import com.example.model.NotchRuntimeState
import com.example.model.NotchWidgetType
import com.example.model.VoiceListeningPhase
import com.example.model.VoiceListeningState
import java.math.BigDecimal
import java.math.RoundingMode
import com.example.system.MediaAction
import com.example.system.SystemBridges
import com.example.system.ToggleResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotchRuntimeCoordinator(
  private val appContext: Context,
  val settingsRepository: NotchSettingsRepository,
  val billingManager: ProBillingManager,
) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private val _runtimeState = MutableStateFlow(NotchRuntimeState())
  val runtimeState: StateFlow<NotchRuntimeState> = _runtimeState.asStateFlow()

  private val _isScreenInteractive = MutableStateFlow(isDeviceScreenInteractive())
  val isScreenInteractive: StateFlow<Boolean> = _isScreenInteractive.asStateFlow()

  private var wakeJob: Job? = null
  private var sleepCountdownJob: Job? = null
  private var autoCollapseJob: Job? = null
  private var timerJob: Job? = null
  private var toastJob: Job? = null
  private var notificationJob: Job? = null
  private var modeAlertJob: Job? = null
  private var compactHudJob: Job? = null
  private var voiceCommandJob: Job? = null

  private var modeAlertCounter = 0L
  private var compactHudCounter = 0L
  private var mascotTapCounter = 0L
  private var voiceSessionCounter = 0L
  private var lastKnownDndOn: Boolean? = null
  private var lastKnownRingerMode: Int? = null
  private var lastKnownCharging: Boolean? = null
  private var lastKnownBatteryPercent: Int = -1
  private var notifiedLowBattery = false
  private var lastKnownVolumePercent: Int? = null
  private var lastKnownBrightnessPercent: Int? = null
  private val knownAudioDeviceIds = mutableSetOf<Int>()
  private val activeTorchCameras = mutableSetOf<String>()

  @Volatile
  private var latestConfigSnapshot: NotchConfig = NotchConfig()
  private var autoSleepSeconds: Int = 15
  private var autoCollapseSeconds: Int = 10

  private val voiceEngine =
    HeyMatusVoiceEngine(
      context = appContext,
      callbacks =
        object : HeyMatusVoiceCallbacks {
          override fun onActiveAudioLevelChanged(level: Float) {
            _runtimeState.update { state ->
              if (state.voiceListening.phase != VoiceListeningPhase.LISTENING) return@update state
              state.copy(voiceListening = state.voiceListening.copy(audioLevel = level))
            }
          }

          override fun onActivePartialTranscript(text: String) {
            _runtimeState.update { state ->
              if (state.voiceListening.phase != VoiceListeningPhase.LISTENING) return@update state
              state.copy(
                voiceListening =
                  state.voiceListening.copy(
                    transcript = text,
                    errorMessage = null,
                  )
              )
            }
          }

          override fun onActiveFinalTranscript(text: String) {
            val state = _runtimeState.value
            if (state.voiceListening.phase != VoiceListeningPhase.LISTENING) return
            val spoken = text.ifBlank { state.voiceListening.transcript }.trim()
            handleFinalVoiceTranscript(spoken)
          }

          override fun onVoiceError(message: String) {
            _runtimeState.update { state ->
              val settledMascot =
                if (state.media.isPlaying) MascotState.LISTENING_MUSIC else MascotState.IDLE
              state.copy(
                isExpanded = true,
                mascotState = settledMascot,
                voiceListening =
                  state.voiceListening.copy(
                    phase = VoiceListeningPhase.FINISHED,
                    audioLevel = 0f,
                    errorMessage = message,
                  ),
              )
            }
          }
        },
    )

  private val systemEventsReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
          NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED,
          AudioManager.RINGER_MODE_CHANGED_ACTION -> syncDndAndSilentState()
          Intent.ACTION_POWER_CONNECTED,
          Intent.ACTION_POWER_DISCONNECTED,
          Intent.ACTION_BATTERY_CHANGED -> syncBatteryAndChargingAlerts(intent)
          "android.media.VOLUME_CHANGED_ACTION" -> {
            val stream =
              intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", AudioManager.STREAM_MUSIC)
            syncVolumeHud(stream)
          }
          WifiManager.WIFI_STATE_CHANGED_ACTION -> syncWifiState()
          BluetoothAdapter.ACTION_STATE_CHANGED -> syncBluetoothState()
          Intent.ACTION_SCREEN_OFF -> onScreenInteractiveChanged(false)
          Intent.ACTION_SCREEN_ON -> onScreenInteractiveChanged(true)
        }
      }
    }

  private val mainHandler = Handler(Looper.getMainLooper())

  private val brightnessObserver =
    object : ContentObserver(mainHandler) {
      override fun onChange(selfChange: Boolean) {
        syncBrightnessHud()
      }
    }

  private val autoRotateObserver =
    object : ContentObserver(mainHandler) {
      override fun onChange(selfChange: Boolean) {
        syncAutoRotateState()
      }
    }

  private val displayListener =
    object : DisplayManager.DisplayListener {
      override fun onDisplayAdded(displayId: Int) = syncVirtualDisplayRecordingState()
      override fun onDisplayRemoved(displayId: Int) = syncVirtualDisplayRecordingState()
      override fun onDisplayChanged(displayId: Int) = Unit
    }

  private val audioDeviceCallback =
    object : AudioDeviceCallback() {
      override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
        val newlyConnected =
          addedDevices.orEmpty().firstOrNull { info ->
            isInterestingBluetoothOrHeadset(info) && knownAudioDeviceIds.add(info.id)
          } ?: return

        val name =
          newlyConnected.productName?.toString()?.trim()?.takeIf { it.isNotBlank() }
            ?: "Bluetooth Audio"
        showCompactHud(
          CompactHudAlert.BluetoothConnected(
            id = ++compactHudCounter,
            deviceName = name,
          ),
          durationMs = 3500L,
        )
      }

      override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
        removedDevices.orEmpty().forEach { knownAudioDeviceIds.remove(it.id) }
      }
    }

  private val torchCallback =
    object : CameraManager.TorchCallback() {
      override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
        if (enabled) {
          activeTorchCameras.add(cameraId)
        } else {
          activeTorchCameras.remove(cameraId)
        }
        val torchEnabled = activeTorchCameras.isNotEmpty()
        SystemBridges.updateObservedTorchState(torchEnabled)
        _runtimeState.update { state ->
          if (state.telemetry.isTorchOn == torchEnabled) state
          else state.copy(telemetry = state.telemetry.copy(isTorchOn = torchEnabled))
        }
      }

      override fun onTorchModeUnavailable(cameraId: String) {
        if (activeTorchCameras.remove(cameraId)) {
          val torchEnabled = activeTorchCameras.isNotEmpty()
          SystemBridges.updateObservedTorchState(torchEnabled)
          _runtimeState.update { state ->
            if (state.telemetry.isTorchOn == torchEnabled) state
            else state.copy(telemetry = state.telemetry.copy(isTorchOn = torchEnabled))
          }
        }
      }
    }

  init {
    syncDndAndSilentState()
    initBaselineHardwareSnapshots()
    registerSystemWatchers()
    refreshSystemTelemetry()

    scope.launch {
      combine(settingsRepository.configFlow, billingManager.entitlementState) { cfg, ent ->
        EntitlementResolver.effectiveConfig(cfg, ent.isProUnlocked())
      }.collect { cfg ->
        latestConfigSnapshot = cfg
        autoSleepSeconds = cfg.autoSleepSeconds
        val prevCollapse = autoCollapseSeconds
        autoCollapseSeconds = cfg.autoCollapseSeconds
        if (prevCollapse != cfg.autoCollapseSeconds) {
          val cur = _runtimeState.value
          if (cur.isExpanded && cur.activeNotification == null && cur.voiceListening.phase == VoiceListeningPhase.INACTIVE) {
            scheduleAutoCollapseIfExpanded()
          }
        }
      }
    }

    scope.launch(Dispatchers.IO) {
      runCatching { SystemBridges.queryLaunchableApps(appContext) }
    }
  }

  private fun isDeviceScreenInteractive(): Boolean {
    val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    return runCatching { pm?.isInteractive ?: true }.getOrDefault(true)
  }

  fun onScreenInteractiveChanged(interactive: Boolean) {
    if (_isScreenInteractive.value == interactive) return
    _isScreenInteractive.value = interactive
    if (!interactive) {
      sleepCountdownJob?.cancel()
      autoCollapseJob?.cancel()
    } else {
      val cur = _runtimeState.value
      if (
        cur.isExpanded &&
        cur.activeNotification == null &&
        cur.voiceListening.phase == VoiceListeningPhase.INACTIVE
      ) {
        scheduleAutoCollapseIfExpanded()
      } else {
        scheduleAutoSleepIfInactive()
      }
    }
  }

  private fun initBaselineHardwareSnapshots() {
    val (batteryPct, charging) = SystemBridges.readBatteryTelemetry(appContext)
    lastKnownBatteryPercent = batteryPct
    lastKnownCharging = charging
    val (_, volPct, _) = SystemBridges.readVolumeHud(appContext, AudioManager.STREAM_MUSIC)
    lastKnownVolumePercent = volPct
    val (_, brightPct) = SystemBridges.readBrightnessHud(appContext)
    lastKnownBrightnessPercent = brightPct

    val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    runCatching {
      am?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.forEach { info ->
        knownAudioDeviceIds.add(info.id)
      }
    }
  }

  private fun registerSystemWatchers() {
    runCatching {
      val filter =
        IntentFilter().apply {
          addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
          addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
          addAction(Intent.ACTION_POWER_CONNECTED)
          addAction(Intent.ACTION_POWER_DISCONNECTED)
          addAction(Intent.ACTION_BATTERY_CHANGED)
          addAction("android.media.VOLUME_CHANGED_ACTION")
          addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
          addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
          addAction(Intent.ACTION_SCREEN_OFF)
          addAction(Intent.ACTION_SCREEN_ON)
        }
      ContextCompat.registerReceiver(
        appContext,
        systemEventsReceiver,
        filter,
        ContextCompat.RECEIVER_EXPORTED,
      )
    }

    runCatching {
      Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS)?.let { uri ->
        appContext.contentResolver.registerContentObserver(uri, false, brightnessObserver)
      }
      Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION)?.let { uri ->
        appContext.contentResolver.registerContentObserver(uri, false, autoRotateObserver)
      }
    }

    runCatching {
      val dm = appContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
      dm?.registerDisplayListener(displayListener, mainHandler)
    }

    runCatching {
      val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
      am?.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
    }

    runCatching {
      val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
      cm?.registerTorchCallback(torchCallback, mainHandler)
    }
  }

  private fun isInterestingBluetoothOrHeadset(info: AudioDeviceInfo): Boolean =
    info.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
      info.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
      info.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
      info.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
      info.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
      (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        (info.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
          info.type == AudioDeviceInfo.TYPE_BLE_SPEAKER))

  fun shouldPopupNotification(packageName: String): Boolean {
    val effective =
      EntitlementResolver.effectiveConfig(latestConfigSnapshot, billingManager.isProUnlockedNow())
    return effective.shouldPopupNotification(packageName)
  }

  fun refreshSystemTelemetry() {
    syncDndAndSilentState()
    syncBatteryAndChargingAlerts()

    val (battery, charging) = SystemBridges.readBatteryTelemetry(appContext)
    val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    val systemMusicActive = audioManager?.isMusicActive == true
    val listenerGranted = SystemBridges.isNotificationListenerGranted(appContext)
    val activeController = NotchMediaListenerService.activeController
    val virtualDisplayRec = SystemBridges.isVirtualScreenCaptureDisplayActive(appContext)

    val dndOn = SystemBridges.isDndEnabled(appContext)
    val silentOn = SystemBridges.isSilentModeEnabled(appContext)
    val wifiOn = SystemBridges.isWifiEnabled(appContext)
    val bluetoothOn = SystemBridges.isBluetoothEnabled(appContext)
    val autoRotateOn = SystemBridges.isAutoRotateEnabled(appContext)
    val torchOn = SystemBridges.isTorchEnabled()
    val hasOverlayPerm = SystemBridges.isOverlayPermissionGranted(appContext)
    val hasDndPerm = SystemBridges.isDndPolicyAccessGranted(appContext)
    val hasBtPerm = SystemBridges.isBluetoothConnectPermissionGranted(appContext)
    val hasLockscreen = SystemBridges.isLockscreenOverlayEnabled(appContext)
    val hasWriteSettings = SystemBridges.canWriteSettings(appContext)
    val hasRecordAudio = SystemBridges.isRecordAudioPermissionGranted(appContext)
    val speechAvail = SystemBridges.isSpeechRecognitionAvailable(appContext)
    val overlayRunning = SystemBridges.isOverlayServiceRunning(appContext)

    _runtimeState.update { state ->
      val authoritativePlaying =
        if (listenerGranted && activeController != null) {
          activeController.playbackState?.state ==
            android.media.session.PlaybackState.STATE_PLAYING
        } else {
          systemMusicActive
        }

      val updatedMedia =
        if (!listenerGranted || activeController == null) {
          state.media.copy(
            isPlaying = authoritativePlaying,
            trackTitle = if (authoritativePlaying) "System Audio" else "No active audio",
            artistAndWriters =
              if (authoritativePlaying) "Enable Notification Access for title & cover"
              else "Play music or tap Play",
            albumArt = if (authoritativePlaying) state.media.albumArt else null,
            hasListenerAccess = listenerGranted,
          )
        } else {
          state.media.copy(
            isPlaying = authoritativePlaying,
            hasListenerAccess = true,
          )
        }

      val nextMascot =
        when {
          state.voiceListening.phase == VoiceListeningPhase.LISTENING -> MascotState.LISTENING_VOICE
          state.mascotState == MascotState.WAKING_UP -> MascotState.WAKING_UP
          authoritativePlaying -> MascotState.LISTENING_MUSIC
          state.mascotState == MascotState.LISTENING_MUSIC -> MascotState.IDLE
          else -> state.mascotState
        }

      val recNow =
        if (virtualDisplayRec) true
        else if (!listenerGranted) false
        else state.ongoingStatus.isScreenRecording

      val updatedOngoing = state.ongoingStatus.copy(isScreenRecording = recNow)
      val updatedTelemetry =
        state.telemetry.copy(
          batteryPercent = battery,
          isCharging = charging,
          isDndOn = dndOn,
          isSilentOn = silentOn,
          isWifiOn = wifiOn,
          isBluetoothOn = bluetoothOn,
          isAutoRotateOn = autoRotateOn,
          isTorchOn = torchOn,
          hasOverlayPermission = hasOverlayPerm,
          hasNotificationListenerPermission = listenerGranted,
          hasDndPolicyPermission = hasDndPerm,
          hasBluetoothPermission = hasBtPerm,
          hasLockscreenAccess = hasLockscreen,
          hasWriteSettingsPermission = hasWriteSettings,
          hasRecordAudioPermission = hasRecordAudio,
          isSpeechRecognitionAvailable = speechAvail,
          isOverlayServiceRunning = overlayRunning,
        )

      if (
        nextMascot == state.mascotState &&
        updatedMedia == state.media &&
        updatedOngoing == state.ongoingStatus &&
        updatedTelemetry == state.telemetry
      ) {
        return@update state
      }

      state.copy(
        mascotState = nextMascot,
        media = updatedMedia,
        ongoingStatus = updatedOngoing,
        telemetry = updatedTelemetry,
      )
    }
  }

  private fun syncWifiState() {
    val wifiOn = SystemBridges.isWifiEnabled(appContext)
    _runtimeState.update { state ->
      if (state.telemetry.isWifiOn == wifiOn) state
      else state.copy(telemetry = state.telemetry.copy(isWifiOn = wifiOn))
    }
  }

  private fun syncBluetoothState() {
    val btOn = SystemBridges.isBluetoothEnabled(appContext)
    val btPerm = SystemBridges.isBluetoothConnectPermissionGranted(appContext)
    _runtimeState.update { state ->
      if (state.telemetry.isBluetoothOn == btOn && state.telemetry.hasBluetoothPermission == btPerm) {
        state
      } else {
        state.copy(
          telemetry =
            state.telemetry.copy(
              isBluetoothOn = btOn,
              hasBluetoothPermission = btPerm,
            )
        )
      }
    }
  }

  private fun syncAutoRotateState() {
    val autoRotateOn = SystemBridges.isAutoRotateEnabled(appContext)
    _runtimeState.update { state ->
      if (state.telemetry.isAutoRotateOn == autoRotateOn) state
      else state.copy(telemetry = state.telemetry.copy(isAutoRotateOn = autoRotateOn))
    }
  }

  private fun syncVirtualDisplayRecordingState() {
    val virtualDisplayRec = SystemBridges.isVirtualScreenCaptureDisplayActive(appContext)
    if (virtualDisplayRec) {
      updateScreenRecording(active = true)
    } else if (!SystemBridges.isNotificationListenerGranted(appContext)) {
      updateScreenRecording(active = false)
    }
  }

  fun syncBatteryAndChargingAlerts(batteryIntent: Intent? = null) {
    val (percent, charging) = SystemBridges.readBatteryTelemetry(appContext, batteryIntent)
    if (
      percent == lastKnownBatteryPercent &&
      charging == lastKnownCharging &&
      _runtimeState.value.telemetry.batteryPercent == percent &&
      _runtimeState.value.telemetry.isCharging == charging
    ) {
      return
    }
    lastKnownBatteryPercent = percent

    _runtimeState.update { state ->
      if (state.telemetry.batteryPercent == percent && state.telemetry.isCharging == charging) {
        state
      } else {
        state.copy(
          telemetry = state.telemetry.copy(batteryPercent = percent, isCharging = charging)
        )
      }
    }

    val prevCharging = lastKnownCharging
    lastKnownCharging = charging

    if (prevCharging != null && charging != prevCharging) {
      if (charging) {
        notifiedLowBattery = false
        showCompactHud(
          CompactHudAlert.Charging(
            id = ++compactHudCounter,
            percent = percent,
            isCharging = true,
            isLowBattery = false,
          ),
          durationMs = 3000L,
        )
      } else {
        showCompactHud(
          CompactHudAlert.Charging(
            id = ++compactHudCounter,
            percent = percent,
            isCharging = false,
            isLowBattery = percent <= 15,
          ),
          durationMs = 2400L,
        )
      }
    } else if (!charging && percent <= 15 && !notifiedLowBattery) {
      notifiedLowBattery = true
      showCompactHud(
        CompactHudAlert.Charging(
          id = ++compactHudCounter,
          percent = percent,
          isCharging = false,
          isLowBattery = true,
        ),
        durationMs = 3200L,
      )
    }
  }

  fun syncVolumeHud(streamType: Int = AudioManager.STREAM_MUSIC) {
    val (fraction, percent, isMuted) = SystemBridges.readVolumeHud(appContext, streamType)
    val prev = lastKnownVolumePercent
    lastKnownVolumePercent = percent
    if (prev != null && prev != percent) {
      showCompactHud(
        CompactHudAlert.VolumeHud(
          id = ++compactHudCounter,
          fraction = fraction,
          percent = percent,
          isMuted = isMuted,
        ),
        durationMs = 2200L,
      )
    }
  }

  fun syncBrightnessHud() {
    val (fraction, percent) = SystemBridges.readBrightnessHud(appContext)
    val prev = lastKnownBrightnessPercent
    lastKnownBrightnessPercent = percent
    if (prev != null && prev != percent) {
      showCompactHud(
        CompactHudAlert.BrightnessHud(
          id = ++compactHudCounter,
          fraction = fraction,
          percent = percent,
        ),
        durationMs = 2200L,
      )
    }
  }

  fun showCompactHud(alert: CompactHudAlert, durationMs: Long = 2600L) {
    compactHudJob?.cancel()
    _runtimeState.update { it.copy(activeCompactHud = alert) }
    compactHudJob =
      scope.launch {
        delay(durationMs)
        _runtimeState.update { state ->
          if (state.activeCompactHud?.id == alert.id) state.copy(activeCompactHud = null)
          else state
        }
      }
  }

  fun updateOngoingCall(callInfo: ActiveCallInfo?) {
    _runtimeState.update { state ->
      if (state.ongoingStatus.activeCall == callInfo) state
      else state.copy(ongoingStatus = state.ongoingStatus.copy(activeCall = callInfo))
    }
  }

  fun openActiveCall() {
    val call = _runtimeState.value.ongoingStatus.activeCall ?: return
    collapse()
    SystemBridges.openNotificationTarget(
      context = appContext,
      pendingIntent = call.contentIntent,
      fallbackPackageName = call.packageName,
    )
  }

  fun updateScreenRecording(active: Boolean, startMillis: Long = 0L) {
    _runtimeState.update { state ->
      val current = state.ongoingStatus
      val effectiveStart =
        if (!active) 0L
        else if (startMillis > 0L) startMillis
        else if (current.isScreenRecording && current.recordingStartMillis > 0L) {
          current.recordingStartMillis
        } else {
          System.currentTimeMillis()
        }
      val nextOngoing =
        current.copy(
          isScreenRecording = active,
          recordingStartMillis = effectiveStart,
        )
      if (nextOngoing == current) state else state.copy(ongoingStatus = nextOngoing)
    }
  }

  fun setLandscapeHidden(hidden: Boolean) {
    val shouldStopVoice = hidden && latestConfigSnapshot.autoHideInLandscape
    if (shouldStopVoice) {
      voiceCommandJob?.cancel()
      voiceEngine.stopActiveCapture()
    }
    _runtimeState.update { state ->
      val shouldCollapse = hidden && latestConfigSnapshot.autoHideInLandscape
      state.copy(
        isLandscapeHidden = hidden,
        isExpanded = if (shouldCollapse) false else state.isExpanded,
        activeNotification = if (shouldCollapse) null else state.activeNotification,
        voiceListening = if (shouldCollapse) VoiceListeningState() else state.voiceListening,
      )
    }
  }

  fun syncDndAndSilentState() {
    val dndNow = SystemBridges.isDndEnabled(appContext)
    val ringerNow = SystemBridges.getRingerMode(appContext)

    val prevDnd = lastKnownDndOn
    val prevRinger = lastKnownRingerMode
    lastKnownDndOn = dndNow
    lastKnownRingerMode = ringerNow

    if (prevDnd != null && dndNow != prevDnd) {
      triggerModeAlert(if (dndNow) ModeAlertKind.DND_ON else ModeAlertKind.DND_OFF)
    }

    if (prevRinger != null && ringerNow != prevRinger) {
      when {
        ringerNow == AudioManager.RINGER_MODE_SILENT &&
          prevRinger != AudioManager.RINGER_MODE_SILENT ->
          triggerModeAlert(ModeAlertKind.SILENT_ON)
        ringerNow == AudioManager.RINGER_MODE_VIBRATE &&
          prevRinger == AudioManager.RINGER_MODE_NORMAL ->
          triggerModeAlert(ModeAlertKind.SILENT_ON)
        ringerNow == AudioManager.RINGER_MODE_NORMAL &&
          prevRinger != AudioManager.RINGER_MODE_NORMAL ->
          triggerModeAlert(ModeAlertKind.SILENT_OFF)
      }
    }

    val silentNow = ringerNow != AudioManager.RINGER_MODE_NORMAL
    _runtimeState.update { state ->
      if (state.telemetry.isDndOn == dndNow && state.telemetry.isSilentOn == silentNow) {
        return@update state
      }
      state.copy(
        telemetry =
          state.telemetry.copy(
            isDndOn = dndNow,
            isSilentOn = silentNow,
          )
      )
    }
  }

  fun triggerModeAlert(kind: ModeAlertKind) {
    modeAlertJob?.cancel()
    val nextId = ++modeAlertCounter
    _runtimeState.update {
      it.copy(activeModeAlert = ActiveModeAlert(id = nextId, kind = kind))
    }
    modeAlertJob =
      scope.launch {
        delay(3000L)
        _runtimeState.update { state ->
          if (state.activeModeAlert?.id == nextId) state.copy(activeModeAlert = null) else state
        }
      }
  }

  fun triggerScreenshot() {
    onUserInteraction()
    if (!NotchAccessibilityService.isConnected) {
      SystemBridges.openAccessibilitySettings(appContext)
      showStatusMessage("Enable Lockscreen & Device Actions bridge in Settings")
      return
    }
    collapse()
    scope.launch {
      delay(220L)
      if (!NotchAccessibilityService.performScreenshot()) {
        showStatusMessage("Screenshot requires Android 9+ Accessibility active")
      }
    }
  }

  fun triggerLockScreen() {
    onUserInteraction()
    if (!NotchAccessibilityService.isConnected) {
      SystemBridges.openAccessibilitySettings(appContext)
      showStatusMessage("Enable Lockscreen & Device Actions bridge in Settings")
      return
    }
    collapse()
    if (!NotchAccessibilityService.performLockScreen()) {
      showStatusMessage("Lock Screen requires Android 9+ Accessibility active")
    }
  }

  fun toggleAutoRotate() {
    onUserInteraction()
    val result = SystemBridges.toggleAutoRotate(appContext)
    if (result != null) {
      _runtimeState.update {
        it.copy(
          telemetry =
            it.telemetry.copy(
              isAutoRotateOn = result,
              hasWriteSettingsPermission = true,
            )
        )
      }
    } else {
      showStatusMessage("Grant Modify System Settings for Auto-Rotate")
    }
  }

  fun onNotificationPosted(banner: ActiveNotificationBanner) {
    val voicePhase = _runtimeState.value.voiceListening.phase
    if (voicePhase == VoiceListeningPhase.LISTENING || voicePhase == VoiceListeningPhase.THINKING) {
      return
    }
    notificationJob?.cancel()
    sleepCountdownJob?.cancel()
    autoCollapseJob?.cancel()
    wakeJob?.cancel()

    val current = _runtimeState.value
    val restoreExpandedAfter = current.isExpanded && current.activeNotification == null
    val nextPulse = current.notificationPulseId + 1L

    _runtimeState.update { state ->
      val settledMascot =
        if (state.media.isPlaying) MascotState.LISTENING_MUSIC else MascotState.WAKING_UP
      state.copy(
        isExpanded = true,
        activeNotification = banner.copy(id = nextPulse),
        notificationPulseId = nextPulse,
        mascotState = settledMascot,
      )
    }

    if (!_runtimeState.value.media.isPlaying) {
      wakeJob = scope.launch {
        delay(2350L)
        _runtimeState.update { s ->
          s.copy(
            mascotState = if (s.media.isPlaying) MascotState.LISTENING_MUSIC else MascotState.IDLE
          )
        }
      }
    }

    notificationJob = scope.launch {
      delay(4500L)
      _runtimeState.update { state ->
        state.copy(
          activeNotification = null,
          isExpanded = restoreExpandedAfter,
          mascotState =
            if (state.media.isPlaying) MascotState.LISTENING_MUSIC else MascotState.IDLE,
        )
      }
      if (restoreExpandedAfter) {
        scheduleAutoCollapseIfExpanded()
      } else {
        scheduleAutoSleepIfInactive()
      }
    }
  }

  fun onNotificationBannerClicked() {
    val banner = _runtimeState.value.activeNotification ?: return
    notificationJob?.cancel()
    autoCollapseJob?.cancel()
    _runtimeState.update { state ->
      state.copy(
        activeNotification = null,
        isExpanded = false,
        mascotState = if (state.media.isPlaying) MascotState.LISTENING_MUSIC else MascotState.IDLE,
      )
    }
    SystemBridges.openNotificationTarget(
      context = appContext,
      pendingIntent = banner.contentIntent,
      fallbackPackageName = banner.packageName,
    )
    scheduleAutoSleepIfInactive()
  }

  fun dismissNotificationBanner() {
    notificationJob?.cancel()
    autoCollapseJob?.cancel()
    _runtimeState.update { state ->
      state.copy(
        activeNotification = null,
        isExpanded = false,
        mascotState = if (state.media.isPlaying) MascotState.LISTENING_MUSIC else MascotState.IDLE,
      )
    }
    scheduleAutoSleepIfInactive()
  }

  fun triggerNotificationPulse() {
    val icon = SystemBridges.loadCachedScaledAppIcon(appContext, appContext.packageName, maxDim = 96)
    onNotificationPosted(
      ActiveNotificationBanner(
        packageName = appContext.packageName,
        appName = "MatusNotch",
        appIcon = icon,
        title = "New Notification",
        body = "Tap anywhere on this notch alert within 4.5s to open the app directly.",
        timestampMillis = System.currentTimeMillis(),
        contentIntent = null,
      )
    )
  }

  fun toggleExpand() {
    if (_runtimeState.value.isExpanded) collapse() else expand()
  }

  fun expand() {
    notificationJob?.cancel()
    sleepCountdownJob?.cancel()
    autoCollapseJob?.cancel()
    wakeJob?.cancel()

    val wasSleeping = _runtimeState.value.mascotState == MascotState.SLEEPING
    val shouldPlayWake = wasSleeping || _runtimeState.value.mascotState == MascotState.IDLE

    _runtimeState.update {
      it.copy(
        isExpanded = true,
        activeNotification = null,
        mascotState = if (shouldPlayWake) MascotState.WAKING_UP else it.mascotState,
      )
    }

    scope.launch {
      refreshSystemTelemetry()
    }

    scheduleAutoCollapseIfExpanded()

    if (shouldPlayWake) {
      wakeJob = scope.launch {
        delay(2350L)
        _runtimeState.update { current ->
          val settled =
            if (current.media.isPlaying) MascotState.LISTENING_MUSIC else MascotState.IDLE
          current.copy(mascotState = settled)
        }
        scheduleAutoSleepIfInactive()
      }
    } else {
      scheduleAutoSleepIfInactive()
    }
  }

  fun startVoiceListening(initialTranscript: String = "") {
    notificationJob?.cancel()
    sleepCountdownJob?.cancel()
    autoCollapseJob?.cancel()
    wakeJob?.cancel()
    voiceCommandJob?.cancel()

    val nextSessionId = ++voiceSessionCounter
    if (!billingManager.isProUnlockedNow()) {
      _runtimeState.update { state ->
        state.copy(
          isExpanded = true,
          activeNotification = null,
          mascotState = if (state.media.isPlaying) MascotState.LISTENING_MUSIC else MascotState.IDLE,
          voiceListening =
            VoiceListeningState(
              phase = VoiceListeningPhase.FINISHED,
              transcript = "",
              audioLevel = 0f,
              requiresProUpgrade = true,
              errorMessage = "Matus Pro unlocks hands-free voice commands & Gemini AI",
              sessionId = nextSessionId,
            ),
        )
      }
      return
    }

    val hasMicPerm = SystemBridges.isRecordAudioPermissionGranted(appContext)
    if (!hasMicPerm) {
      _runtimeState.update { state ->
        state.copy(
          isExpanded = true,
          activeNotification = null,
          mascotState = if (state.media.isPlaying) MascotState.LISTENING_MUSIC else MascotState.IDLE,
          voiceListening =
            VoiceListeningState(
              phase = VoiceListeningPhase.FINISHED,
              transcript = "",
              audioLevel = 0f,
              errorMessage = "Grant Microphone Access in Settings to talk to Matus",
              sessionId = nextSessionId,
            ),
        )
      }
      return
    }

    _runtimeState.update { state ->
      state.copy(
        isExpanded = true,
        activeNotification = null,
        mascotState = MascotState.LISTENING_VOICE,
        voiceListening =
          VoiceListeningState(
            phase = VoiceListeningPhase.LISTENING,
            transcript = initialTranscript,
            audioLevel = 0.16f,
            errorMessage = null,
            sessionId = nextSessionId,
          ),
      )
    }
    voiceEngine.startActiveCapture(initialTranscript = initialTranscript)
  }

  fun handleFinalVoiceTranscript(rawTranscript: String) {
    voiceCommandJob?.cancel()
    val spoken = rawTranscript.trim()
    val currentState = _runtimeState.value
    val sessionId =
      currentState.voiceListening.sessionId.takeIf { it > 0L } ?: ++voiceSessionCounter
    val settledMascot =
      if (currentState.media.isPlaying) MascotState.LISTENING_MUSIC else MascotState.IDLE

    if (spoken.isEmpty()) {
      _runtimeState.update { state ->
        state.copy(
          isExpanded = true,
          mascotState = settledMascot,
          voiceListening =
            VoiceListeningState(
              phase = VoiceListeningPhase.FINISHED,
              transcript = "",
              audioLevel = 0f,
              sessionId = sessionId,
            ),
        )
      }
      return
    }

    // Tier 1: Local on-device English commands (instant, no API key required)
    when (val commandResult = VoiceCommandProcessor.process(appContext, this, spoken)) {
      is VoiceCommandResult.Executed -> {
        val delayMs = commandResult.autoCloseDelayMs
        if (delayMs == null) {
          closeVoiceListening()
        } else {
          _runtimeState.update { state ->
            state.copy(
              isExpanded = true,
              mascotState = settledMascot,
              voiceListening =
                VoiceListeningState(
                  phase = VoiceListeningPhase.FINISHED,
                  transcript = spoken,
                  audioLevel = 0f,
                  confirmationBadge = commandResult.confirmationLabel,
                  sessionId = sessionId,
                ),
            )
          }
          voiceCommandJob =
            scope.launch {
              delay(delayMs)
              if (_runtimeState.value.voiceListening.sessionId == sessionId) {
                closeVoiceListening()
              }
            }
        }
        return
      }

      is VoiceCommandResult.InfoAnswer -> {
        _runtimeState.update { state ->
          state.copy(
            isExpanded = true,
            mascotState = settledMascot,
            voiceListening =
              VoiceListeningState(
                phase = VoiceListeningPhase.FINISHED,
                transcript = spoken,
                audioLevel = 0f,
                localInfoTitle = commandResult.title,
                localInfoReply = commandResult.answerText,
                sessionId = sessionId,
              ),
          )
        }
        return
      }

      is VoiceCommandResult.Failed -> {
        _runtimeState.update { state ->
          state.copy(
            isExpanded = true,
            mascotState = settledMascot,
            voiceListening =
              VoiceListeningState(
                phase = VoiceListeningPhase.FINISHED,
                transcript = spoken,
                audioLevel = 0f,
                errorMessage = commandResult.errorMessage,
                sessionId = sessionId,
              ),
          )
        }
        return
      }

      VoiceCommandResult.NotMatched -> Unit
    }

    // Tier 2: Optional BYOK Mini Gemini AI Fallback
    val apiKey = latestConfigSnapshot.userGeminiApiKey.trim()
    if (apiKey.isEmpty()) {
      _runtimeState.update { state ->
        state.copy(
          isExpanded = true,
          mascotState = settledMascot,
          voiceListening =
            VoiceListeningState(
              phase = VoiceListeningPhase.FINISHED,
              transcript = spoken,
              audioLevel = 0f,
              needsApiKeyHint = true,
              sessionId = sessionId,
            ),
        )
      }
      return
    }

    _runtimeState.update { state ->
      state.copy(
        isExpanded = true,
        mascotState = settledMascot,
        voiceListening =
          VoiceListeningState(
            phase = VoiceListeningPhase.THINKING,
            transcript = spoken,
            audioLevel = 0f,
            sessionId = sessionId,
          ),
      )
    }

    voiceCommandJob =
      scope.launch {
        when (val aiResult = GeminiMiniClient.generateShortReply(apiKey, spoken)) {
          is GeminiMiniResult.Success -> {
            _runtimeState.update { state ->
              if (state.voiceListening.sessionId != sessionId) return@update state
              state.copy(
                isExpanded = true,
                voiceListening =
                  state.voiceListening.copy(
                    phase = VoiceListeningPhase.FINISHED,
                    aiReply = aiResult.replyText,
                    errorMessage = null,
                    needsApiKeyHint = false,
                  ),
              )
            }
          }

          is GeminiMiniResult.Error -> {
            _runtimeState.update { state ->
              if (state.voiceListening.sessionId != sessionId) return@update state
              state.copy(
                isExpanded = true,
                voiceListening =
                  state.voiceListening.copy(
                    phase = VoiceListeningPhase.FINISHED,
                    errorMessage = aiResult.message,
                    aiReply = null,
                    needsApiKeyHint = false,
                  ),
              )
            }
          }
        }
      }
  }

  fun copyVoiceReply(replyText: String) {
    val clean = replyText.trim()
    if (clean.isEmpty()) return
    runCatching {
      val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
      clipboard?.setPrimaryClip(ClipData.newPlainText("Matus AI Reply", clean))
    }
  }

  fun closeVoiceListening() {
    voiceCommandJob?.cancel()
    voiceEngine.stopActiveCapture()
    _runtimeState.update { current ->
      val collapsedMascot =
        if (current.media.isPlaying) MascotState.LISTENING_MUSIC else MascotState.IDLE
      current.copy(
        isExpanded = false,
        activeNotification = null,
        voiceListening = VoiceListeningState(),
        mascotState = collapsedMascot,
      )
    }
    scheduleAutoSleepIfInactive()
  }

  fun collapse() {
    notificationJob?.cancel()
    autoCollapseJob?.cancel()
    wakeJob?.cancel()
    voiceCommandJob?.cancel()
    if (_runtimeState.value.voiceListening.phase != VoiceListeningPhase.INACTIVE) {
      voiceEngine.stopActiveCapture()
    }
    _runtimeState.update { current ->
      val collapsedMascot =
        if (current.media.isPlaying) MascotState.LISTENING_MUSIC else MascotState.IDLE
      current.copy(
        isExpanded = false,
        activeNotification = null,
        voiceListening = VoiceListeningState(),
        mascotState = collapsedMascot,
      )
    }
    scheduleAutoSleepIfInactive()
  }

  fun onMascotTapped() {
    val cur = _runtimeState.value
    if (
      !cur.isExpanded ||
      cur.activeNotification != null ||
      cur.voiceListening.phase != VoiceListeningPhase.INACTIVE
    ) {
      return
    }
    onUserInteraction()
    val nextTapId = ++mascotTapCounter
    _runtimeState.update { state ->
      val reactedMascot =
        if (state.media.isPlaying) MascotState.LISTENING_MUSIC else MascotState.WAKING_UP
      state.copy(
        mascotTapId = nextTapId,
        mascotState = reactedMascot,
      )
    }
    if (!_runtimeState.value.media.isPlaying) {
      wakeJob?.cancel()
      wakeJob = scope.launch {
        delay(1650L)
        _runtimeState.update { current ->
          val settled =
            if (current.media.isPlaying) MascotState.LISTENING_MUSIC else MascotState.IDLE
          current.copy(mascotState = settled)
        }
      }
    }
  }

  fun onWidgetPageChanged(page: Int) {
    _runtimeState.update { it.copy(activeWidgetPage = page) }
    onUserInteraction()
  }

  fun onUserInteraction() {
    val cur = _runtimeState.value
    if (
      cur.isExpanded &&
      cur.activeNotification == null &&
      cur.voiceListening.phase == VoiceListeningPhase.INACTIVE
    ) {
      scheduleAutoCollapseIfExpanded()
    }
    if (cur.mascotState == MascotState.SLEEPING) {
      wakeFromSleep()
    } else {
      scheduleAutoSleepIfInactive()
    }
  }

  private fun scheduleAutoCollapseIfExpanded() {
    autoCollapseJob?.cancel()
    val timeoutSec = autoCollapseSeconds
    if (timeoutSec <= 0) return
    if (_runtimeState.value.voiceListening.phase != VoiceListeningPhase.INACTIVE) return
    autoCollapseJob = scope.launch {
      delay(timeoutSec * 1000L)
      val current = _runtimeState.value
      if (
        current.isExpanded &&
        current.activeNotification == null &&
        current.voiceListening.phase == VoiceListeningPhase.INACTIVE
      ) {
        collapse()
      }
    }
  }

  private fun wakeFromSleep() {
    wakeJob?.cancel()
    _runtimeState.update { it.copy(mascotState = MascotState.WAKING_UP) }
    wakeJob = scope.launch {
      delay(2350L)
      _runtimeState.update { current ->
        val settled =
          if (current.media.isPlaying) MascotState.LISTENING_MUSIC else MascotState.IDLE
        current.copy(mascotState = settled)
      }
      scheduleAutoSleepIfInactive()
    }
  }

  private fun scheduleAutoSleepIfInactive() {
    sleepCountdownJob?.cancel()
    sleepCountdownJob = scope.launch {
      delay(autoSleepSeconds * 1000L)
      _runtimeState.update { current ->
        if (
          !current.media.isPlaying &&
          !current.timer.isRunning &&
          !current.isExpanded &&
          current.activeNotification == null &&
          current.voiceListening.phase == VoiceListeningPhase.INACTIVE &&
          current.ongoingStatus.activeCall == null
        ) {
          current.copy(mascotState = MascotState.SLEEPING)
        } else {
          current
        }
      }
    }
  }

  fun updateExternalMediaSession(
    isPlaying: Boolean,
    title: String,
    artistAndWriters: String,
    sourceApp: String,
    albumArt: ImageBitmap?,
    hasListenerAccess: Boolean,
  ) {
    val nextMedia =
      MediaPlaybackState(
        isPlaying = isPlaying,
        trackTitle = title.ifBlank { if (isPlaying) "Playing Media" else "No active audio" },
        artistAndWriters =
          artistAndWriters.ifBlank { sourceApp.ifBlank { "Connected Audio" } },
        sourceApp = sourceApp,
        albumArt = albumArt,
        hasListenerAccess = hasListenerAccess,
      )
    _runtimeState.update { current ->
      val nextMascot =
        when {
          current.voiceListening.phase == VoiceListeningPhase.LISTENING -> MascotState.LISTENING_VOICE
          isPlaying -> MascotState.LISTENING_MUSIC
          current.mascotState == MascotState.LISTENING_MUSIC -> MascotState.IDLE
          else -> current.mascotState
        }
      if (current.mascotState == nextMascot && current.media == nextMedia) {
        return@update current
      }
      current.copy(
        mascotState = nextMascot,
        media = nextMedia,
      )
    }
    if (!isPlaying) scheduleAutoSleepIfInactive()
  }

  fun togglePlayPause() {
    onUserInteraction()
    SystemBridges.dispatchMediaAction(appContext, MediaAction.PLAY_PAUSE)
    scope.launch {
      delay(250L)
      refreshSystemTelemetry()
    }
  }

  fun skipNext() {
    onUserInteraction()
    SystemBridges.dispatchMediaAction(appContext, MediaAction.NEXT)
    scope.launch {
      delay(250L)
      refreshSystemTelemetry()
    }
  }

  fun skipPrevious() {
    onUserInteraction()
    SystemBridges.dispatchMediaAction(appContext, MediaAction.PREVIOUS)
    scope.launch {
      delay(250L)
      refreshSystemTelemetry()
    }
  }

  fun toggleTorch() {
    onUserInteraction()
    val next = !_runtimeState.value.telemetry.isTorchOn
    val result = SystemBridges.setTorchEnabled(appContext, next)
    if (result != null) {
      _runtimeState.update { it.copy(telemetry = it.telemetry.copy(isTorchOn = result)) }
    } else {
      showStatusMessage("Camera flash unavailable")
    }
  }

  fun setTorchForVoice(targetEnabled: Boolean?): VoiceSwitchOutcome {
    val desired = targetEnabled ?: !_runtimeState.value.telemetry.isTorchOn
    val result = SystemBridges.setTorchEnabled(appContext, desired)
    return if (result != null) {
      _runtimeState.update { it.copy(telemetry = it.telemetry.copy(isTorchOn = result)) }
      VoiceSwitchOutcome.Changed(result)
    } else {
      VoiceSwitchOutcome.Unavailable
    }
  }

  fun toggleDnd() {
    onUserInteraction()
    val result = SystemBridges.toggleDnd(appContext)
    if (result != null) {
      lastKnownDndOn = result
      _runtimeState.update { it.copy(telemetry = it.telemetry.copy(isDndOn = result)) }
      triggerModeAlert(if (result) ModeAlertKind.DND_ON else ModeAlertKind.DND_OFF)
    } else {
      showStatusMessage("Grant DND access in Settings")
    }
  }

  fun setDndForVoice(targetEnabled: Boolean?): VoiceSwitchOutcome {
    if (!SystemBridges.isDndPolicyAccessGranted(appContext)) {
      return VoiceSwitchOutcome.PermissionRequired("Grant DND access in Settings")
    }
    val current = SystemBridges.isDndEnabled(appContext)
    val desired = targetEnabled ?: !current
    if (targetEnabled != null && current == desired) {
      _runtimeState.update { it.copy(telemetry = it.telemetry.copy(isDndOn = current)) }
      return VoiceSwitchOutcome.Already(current)
    }
    val result = SystemBridges.setDndEnabled(appContext, desired) ?: return VoiceSwitchOutcome.Unavailable
    lastKnownDndOn = result
    _runtimeState.update { it.copy(telemetry = it.telemetry.copy(isDndOn = result)) }
    triggerModeAlert(if (result) ModeAlertKind.DND_ON else ModeAlertKind.DND_OFF)
    return VoiceSwitchOutcome.Changed(result)
  }

  fun setAutoRotateForVoice(targetEnabled: Boolean?): VoiceSwitchOutcome {
    if (!SystemBridges.canWriteSettings(appContext)) {
      return VoiceSwitchOutcome.PermissionRequired("Grant Modify System Settings for Auto-Rotate")
    }
    val current = SystemBridges.isAutoRotateEnabled(appContext)
    val desired = targetEnabled ?: !current
    if (targetEnabled != null && current == desired) {
      _runtimeState.update {
        it.copy(
          telemetry =
            it.telemetry.copy(
              isAutoRotateOn = current,
              hasWriteSettingsPermission = true,
            )
        )
      }
      return VoiceSwitchOutcome.Already(current)
    }
    val result =
      SystemBridges.setAutoRotateEnabled(appContext, desired)
        ?: return VoiceSwitchOutcome.Unavailable
    _runtimeState.update {
      it.copy(
        telemetry =
          it.telemetry.copy(
            isAutoRotateOn = result,
            hasWriteSettingsPermission = true,
          )
      )
    }
    return VoiceSwitchOutcome.Changed(result)
  }

  fun setSilentModeForVoice(targetSilent: Boolean?): VoiceSwitchOutcome {
    if (!SystemBridges.isDndPolicyAccessGranted(appContext)) {
      return VoiceSwitchOutcome.PermissionRequired("Grant DND access in Settings for Silent mode")
    }
    val current = SystemBridges.isSilentModeEnabled(appContext)
    val desired = targetSilent ?: !current
    if (targetSilent != null && current == desired) {
      _runtimeState.update { it.copy(telemetry = it.telemetry.copy(isSilentOn = current)) }
      return VoiceSwitchOutcome.Already(current)
    }
    val result =
      SystemBridges.setSilentModeEnabled(appContext, desired)
        ?: return VoiceSwitchOutcome.Unavailable
    lastKnownRingerMode =
      if (result) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
    _runtimeState.update { it.copy(telemetry = it.telemetry.copy(isSilentOn = result)) }
    triggerModeAlert(if (result) ModeAlertKind.SILENT_ON else ModeAlertKind.SILENT_OFF)
    return VoiceSwitchOutcome.Changed(result)
  }

  fun setMediaPlaybackForVoice(play: Boolean): VoiceSwitchOutcome {
    val media = _runtimeState.value.media
    if (media.hasListenerAccess && media.isPlaying == play) {
      return VoiceSwitchOutcome.Already(play)
    }
    onUserInteraction()
    SystemBridges.dispatchMediaExplicit(appContext, play)
    scope.launch {
      delay(250L)
      refreshSystemTelemetry()
    }
    return VoiceSwitchOutcome.Changed(play)
  }

  fun toggleWifi() {
    onUserInteraction()
    when (val res = SystemBridges.toggleWifi(appContext)) {
      is ToggleResult.Updated -> {
        _runtimeState.update { it.copy(telemetry = it.telemetry.copy(isWifiOn = res.isEnabled)) }
      }
      ToggleResult.OpenedQuickPanel -> {
        collapse()
      }
      ToggleResult.Unavailable -> {
        showStatusMessage("Wi-Fi control unavailable")
      }
    }
  }

  fun setWifiForVoice(targetEnabled: Boolean?): VoiceSwitchOutcome {
    val current = SystemBridges.isWifiEnabled(appContext)
    if (targetEnabled != null && current == targetEnabled) {
      _runtimeState.update { it.copy(telemetry = it.telemetry.copy(isWifiOn = current)) }
      return VoiceSwitchOutcome.Already(current)
    }
    return when (val res = SystemBridges.toggleWifi(appContext)) {
      is ToggleResult.Updated -> {
        _runtimeState.update { it.copy(telemetry = it.telemetry.copy(isWifiOn = res.isEnabled)) }
        VoiceSwitchOutcome.Changed(res.isEnabled)
      }
      ToggleResult.OpenedQuickPanel -> {
        collapse()
        VoiceSwitchOutcome.OpenedQuickPanel
      }
      ToggleResult.Unavailable -> VoiceSwitchOutcome.Unavailable
    }
  }

  fun toggleBluetooth() {
    onUserInteraction()
    when (val res = SystemBridges.toggleBluetooth(appContext)) {
      is ToggleResult.Updated -> {
        _runtimeState.update {
          it.copy(telemetry = it.telemetry.copy(isBluetoothOn = res.isEnabled))
        }
      }
      ToggleResult.OpenedQuickPanel -> {
        collapse()
      }
      ToggleResult.Unavailable -> {
        showStatusMessage("Bluetooth unavailable")
      }
    }
  }

  fun setBluetoothForVoice(targetEnabled: Boolean?): VoiceSwitchOutcome {
    if (SystemBridges.isBluetoothConnectPermissionGranted(appContext) && targetEnabled != null) {
      val current = SystemBridges.isBluetoothEnabled(appContext)
      if (current == targetEnabled) {
        _runtimeState.update { it.copy(telemetry = it.telemetry.copy(isBluetoothOn = current)) }
        return VoiceSwitchOutcome.Already(current)
      }
    }
    return when (val res = SystemBridges.toggleBluetooth(appContext)) {
      is ToggleResult.Updated -> {
        _runtimeState.update {
          it.copy(telemetry = it.telemetry.copy(isBluetoothOn = res.isEnabled))
        }
        VoiceSwitchOutcome.Changed(res.isEnabled)
      }
      ToggleResult.OpenedQuickPanel -> {
        collapse()
        VoiceSwitchOutcome.OpenedQuickPanel
      }
      ToggleResult.Unavailable -> VoiceSwitchOutcome.Unavailable
    }
  }

  fun startPauseTimer() {
    onUserInteraction()
    val current = _runtimeState.value.timer
    if (current.isRunning) {
      timerJob?.cancel()
      _runtimeState.update { it.copy(timer = it.timer.copy(isRunning = false)) }
      scheduleAutoSleepIfInactive()
    } else {
      val startFrom =
        if (current.remainingSeconds <= 0) current.totalSeconds else current.remainingSeconds
      _runtimeState.update {
        it.copy(
          timer = current.copy(remainingSeconds = startFrom, isRunning = true),
          mascotState =
            if (_runtimeState.value.media.isPlaying) MascotState.LISTENING_MUSIC else MascotState.IDLE,
        )
      }
      timerJob?.cancel()
      timerJob = scope.launch {
        while (_runtimeState.value.timer.isRunning && _runtimeState.value.timer.remainingSeconds > 0) {
          delay(1000L)
          _runtimeState.update { state ->
            val nextRem = (state.timer.remainingSeconds - 1).coerceAtLeast(0)
            state.copy(
              timer =
                state.timer.copy(
                  remainingSeconds = nextRem,
                  isRunning = nextRem > 0,
                ),
              mascotState = if (nextRem == 0) MascotState.WAKING_UP else state.mascotState,
            )
          }
        }
        if (_runtimeState.value.timer.remainingSeconds == 0) {
          showStatusMessage("Focus session complete!")
          delay(1600L)
          _runtimeState.update {
            it.copy(
              mascotState =
                if (it.media.isPlaying) MascotState.LISTENING_MUSIC else MascotState.IDLE
            )
          }
        }
      }
    }
  }

  fun resetTimer() {
    onUserInteraction()
    timerJob?.cancel()
    _runtimeState.update { state ->
      state.copy(
        timer =
          FocusTimerState(
            totalSeconds = state.timer.totalSeconds,
            remainingSeconds = state.timer.totalSeconds,
            isRunning = false,
          )
      )
    }
  }

  fun setTimerMinutes(minutes: Int) {
    onUserInteraction()
    timerJob?.cancel()
    val secs = (minutes * 60).coerceAtLeast(60)
    _runtimeState.update { state ->
      state.copy(timer = FocusTimerState(totalSeconds = secs, remainingSeconds = secs, isRunning = false))
    }
  }

  fun startTimerForVoice(minutes: Int) {
    setTimerMinutes(minutes)
    startPauseTimer()
    val timerPageIdx =
      latestConfigSnapshot.enabledWidgets.indexOf(NotchWidgetType.FOCUS_TIMER).takeIf { it >= 0 }
    if (timerPageIdx != null) {
      _runtimeState.update { it.copy(activeWidgetPage = timerPageIdx) }
    }
  }

  fun addTimerMinute() {
    onUserInteraction()
    _runtimeState.update { state ->
      val nextTotal = state.timer.totalSeconds + 60
      val nextRem = state.timer.remainingSeconds + 60
      state.copy(timer = state.timer.copy(totalSeconds = nextTotal, remainingSeconds = nextRem))
    }
  }

  fun setOverlayRunningState(running: Boolean) {
    _runtimeState.update { state ->
      if (state.telemetry.isOverlayServiceRunning == running) state
      else state.copy(telemetry = state.telemetry.copy(isOverlayServiceRunning = running))
    }
  }

  fun launchPinnedApp(packageName: String) {
    if (SystemBridges.launchApp(appContext, packageName)) {
      collapse()
    } else {
      showStatusMessage("Unable to launch app")
    }
  }

  fun onCalculatorInput(rawKey: String) {
    onUserInteraction()
    val key =
      when (rawKey) {
        "*" -> "×"
        "/" -> "÷"
        "−" -> "-"
        else -> rawKey
      }

    _runtimeState.update { state ->
      val c = state.calculator
      val next =
        when {
          key == "AC" -> CalculatorState()

          key == "DEL" -> {
            if (c.hasError || c.clearOnNextDigit) {
              c.copy(display = "0", clearOnNextDigit = false, hasError = false)
            } else {
              val trimmed = if (c.display.length <= 1) "0" else c.display.dropLast(1)
              c.copy(display = if (trimmed == "-") "0" else trimmed)
            }
          }

          key == "%" -> {
            if (c.hasError) {
              c
            } else {
              val curVal = c.display.toDoubleOrNull() ?: 0.0
              val pctVal = curVal / 100.0
              c.copy(display = formatCalcNumber(pctVal), clearOnNextDigit = false)
            }
          }

          key in listOf("+", "-", "×", "÷") -> {
            if (c.hasError) {
              c
            } else {
              val curVal = c.display.toDoubleOrNull() ?: 0.0
              val baseValue =
                if (c.storedValue != null && c.pendingOp != null && !c.clearOnNextDigit) {
                  evaluateBinaryOp(c.storedValue, c.pendingOp, curVal)
                } else {
                  curVal
                }

              if (baseValue == null) {
                CalculatorState(
                  expression = "${formatCalcNumber(c.storedValue ?: 0.0)} ${c.pendingOp.orEmpty()} ${formatCalcNumber(curVal)}",
                  display = "Error",
                  hasError = true,
                )
              } else {
                val formatted = formatCalcNumber(baseValue)
                c.copy(
                  expression = "$formatted $key",
                  display = formatted,
                  storedValue = baseValue,
                  pendingOp = key,
                  clearOnNextDigit = true,
                )
              }
            }
          }

          key == "=" -> {
            if (c.hasError) {
              c
            } else if (c.storedValue != null && c.pendingOp != null) {
              val rhs = c.display.toDoubleOrNull() ?: c.storedValue
              val result = evaluateBinaryOp(c.storedValue, c.pendingOp, rhs)
              if (result == null) {
                CalculatorState(
                  expression = "${formatCalcNumber(c.storedValue)} ${c.pendingOp} ${formatCalcNumber(rhs)}",
                  display = "Error",
                  hasError = true,
                )
              } else {
                CalculatorState(
                  expression = "${formatCalcNumber(c.storedValue)} ${c.pendingOp} ${formatCalcNumber(rhs)} =",
                  display = formatCalcNumber(result),
                  pendingOp = null,
                  storedValue = null,
                  clearOnNextDigit = true,
                  hasError = false,
                )
              }
            } else {
              c.copy(clearOnNextDigit = true)
            }
          }

          key == "." -> {
            when {
              c.hasError -> CalculatorState(display = "0.")
              c.clearOnNextDigit ->
                c.copy(
                  expression = if (c.pendingOp == null) "" else c.expression,
                  display = "0.",
                  clearOnNextDigit = false,
                )
              c.display.contains(".") -> c
              else -> c.copy(display = "${c.display}.")
            }
          }

          key.length == 1 && key[0].isDigit() -> {
            when {
              c.hasError -> CalculatorState(display = key)
              c.clearOnNextDigit ->
                c.copy(
                  expression = if (c.pendingOp == null) "" else c.expression,
                  display = key,
                  clearOnNextDigit = false,
                )
              c.display == "0" -> c.copy(display = key)
              c.display.length >= 14 -> c
              else -> c.copy(display = c.display + key)
            }
          }

          else -> c
        }
      state.copy(calculator = next)
    }
  }

  fun copyCalculatorResult() {
    onUserInteraction()
    val value = _runtimeState.value.calculator.display
    if (value == "Error") return
    runCatching {
      val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
      clipboard?.setPrimaryClip(ClipData.newPlainText("MatusNotch Calculator", value))
      showStatusMessage("Copied $value")
    }
  }

  private fun evaluateBinaryOp(lhs: Double, op: String, rhs: Double): Double? {
    val result =
      when (op) {
        "+" -> lhs + rhs
        "-" -> lhs - rhs
        "×" -> lhs * rhs
        "÷" -> if (rhs == 0.0) Double.NaN else lhs / rhs
        else -> rhs
      }
    return if (result.isNaN() || result.isInfinite()) null else result
  }

  private fun formatCalcNumber(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "Error"
    val cleanZero = if (value == 0.0) 0.0 else value
    return runCatching {
      val bd = BigDecimal.valueOf(cleanZero).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros()
      val plain = bd.toPlainString()
      if (plain.length <= 14) plain else String.format(java.util.Locale.US, "%.6g", cleanZero)
    }.getOrElse { cleanZero.toString() }
  }

  fun showStatusMessage(message: String) {
    toastJob?.cancel()
    _runtimeState.update { it.copy(statusMessage = message) }
    toastJob = scope.launch {
      delay(2400L)
      _runtimeState.update { it.copy(statusMessage = null) }
    }
  }
}
