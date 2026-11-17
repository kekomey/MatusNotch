package com.example.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.speech.SpeechRecognizer
import android.util.LruCache
import android.view.Display
import android.view.KeyEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.model.LaunchableAppInfo
import com.example.service.NotchAccessibilityService
import com.example.service.NotchMediaListenerService
import com.example.service.NotchOverlayService

sealed interface ToggleResult {
  data class Updated(val isEnabled: Boolean) : ToggleResult
  data object OpenedQuickPanel : ToggleResult
  data object Unavailable : ToggleResult
}

object SystemBridges {

  @Volatile
  private var cachedSpeechAvailable: Boolean? = null
  @Volatile
  private var latestTorchState: Boolean = false
  @Volatile
  private var cachedLaunchableApps: List<LaunchableAppInfo>? = null
  @Volatile
  private var cachedLaunchableAppsTimestampMs: Long = 0L
  private const val LAUNCHABLE_APPS_TTL_MS = 60_000L
  private val appLabelCache = LruCache<String, String>(96)
  private val scaledAppIconCache = LruCache<String, ImageBitmap>(48)

  fun isTorchEnabled(): Boolean = latestTorchState

  fun updateObservedTorchState(enabled: Boolean) {
    latestTorchState = enabled
  }

  fun invalidateLaunchableAppsCache() {
    cachedLaunchableApps = null
    cachedLaunchableAppsTimestampMs = 0L
  }

  fun isOverlayPermissionGranted(context: Context): Boolean =
    Settings.canDrawOverlays(context)

  fun isNotificationListenerGranted(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

  fun isDndPolicyAccessGranted(context: Context): Boolean {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    return nm?.isNotificationPolicyAccessGranted == true
  }

  fun isBluetoothConnectPermissionGranted(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }

  fun isRecordAudioPermissionGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED

  fun isSpeechRecognitionAvailable(context: Context): Boolean {
    cachedSpeechAvailable?.let { return it }
    val available = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)
    cachedSpeechAvailable = available
    return available
  }

  fun isLockscreenOverlayEnabled(context: Context): Boolean {
    if (NotchAccessibilityService.isConnected) return true
    val enabledServices =
      runCatching {
        Settings.Secure.getString(
          context.contentResolver,
          Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
      }.getOrNull().orEmpty()
    val component = ComponentName(context, NotchAccessibilityService::class.java).flattenToString()
    val shortComponent = "${context.packageName}/${NotchAccessibilityService::class.java.simpleName}"
    return enabledServices.contains(component) || enabledServices.contains(shortComponent)
  }

  fun isDndEnabled(context: Context): Boolean {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    val filter = nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
    return filter != NotificationManager.INTERRUPTION_FILTER_ALL
  }

  fun getRingerMode(context: Context): Int {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return AudioManager.RINGER_MODE_NORMAL
    return runCatching { am.ringerMode }.getOrDefault(AudioManager.RINGER_MODE_NORMAL)
  }

  fun isSilentModeEnabled(context: Context): Boolean =
    getRingerMode(context) != AudioManager.RINGER_MODE_NORMAL

  fun isWifiEnabled(context: Context): Boolean {
    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    return runCatching { wm?.isWifiEnabled == true }.getOrDefault(false)
  }

  @SuppressLint("MissingPermission")
  fun isBluetoothEnabled(context: Context): Boolean {
    if (!isBluetoothConnectPermissionGranted(context)) return false
    val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = bm?.adapter ?: return false
    return runCatching { adapter.isEnabled }.getOrDefault(false)
  }

  fun isOverlayServiceRunning(context: Context): Boolean =
    NotchAccessibilityService.isConnected || NotchOverlayService.isServiceRunning

  fun openOverlayPermissionSettings(context: Context) {
    val intent =
      Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
      ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
  }

  fun openNotificationListenerSettings(context: Context) {
    val intent =
      Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
  }

  fun openDndPolicySettings(context: Context) {
    val intent =
      Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
  }

  fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
  }

  fun canWriteSettings(context: Context): Boolean =
    Settings.System.canWrite(context)

  fun openWriteSettings(context: Context) {
    val intent =
      Intent(
        Settings.ACTION_MANAGE_WRITE_SETTINGS,
        Uri.parse("package:${context.packageName}"),
      ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
  }

  fun isAutoRotateEnabled(context: Context): Boolean =
    runCatching {
      Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
    }.getOrDefault(false)

  fun toggleAutoRotate(context: Context): Boolean? {
    if (!canWriteSettings(context)) {
      openWriteSettings(context)
      return null
    }
    return setAutoRotateEnabled(context, !isAutoRotateEnabled(context))
  }

  fun setAutoRotateEnabled(context: Context, enable: Boolean): Boolean? {
    if (!canWriteSettings(context)) return null
    return runCatching {
      Settings.System.putInt(
        context.contentResolver,
        Settings.System.ACCELEROMETER_ROTATION,
        if (enable) 1 else 0,
      )
      enable
    }.getOrNull()
  }

  fun readVolumeHud(context: Context, streamType: Int = AudioManager.STREAM_MUSIC): Triple<Float, Int, Boolean> {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
      ?: return Triple(0f, 0, false)
    val max = runCatching { am.getStreamMaxVolume(streamType) }.getOrDefault(15).coerceAtLeast(1)
    val current = runCatching { am.getStreamVolume(streamType) }.getOrDefault(0).coerceIn(0, max)
    val fraction = (current.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    return Triple(fraction, (fraction * 100f).toInt(), current == 0)
  }

  fun readBrightnessHud(context: Context): Pair<Float, Int> {
    val raw =
      runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
      }.getOrDefault(128)
    val fraction = (raw.coerceIn(0, 255) / 255f).coerceIn(0f, 1f)
    return fraction to (fraction * 100f).toInt()
  }

  fun findConnectedBluetoothAudioName(context: Context): String? {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
    val btDevice =
      runCatching {
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { info ->
          info.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            info.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
              (info.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                info.type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
        }
      }.getOrNull() ?: return null
    return btDevice.productName?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: "Bluetooth Audio"
  }

  fun isVirtualScreenCaptureDisplayActive(context: Context): Boolean {
    val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return false
    return runCatching {
      dm.displays.any { display ->
        if (display.displayId == Display.DEFAULT_DISPLAY) return@any false
        val name = display.name.orEmpty().lowercase()
        name.contains("record") || name.contains("screen") || name.contains("virtual")
      }
    }.getOrDefault(false)
  }

  @Suppress("DEPRECATION")
  fun toggleWifi(context: Context): ToggleResult {
    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    if (wm != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      val next = !wm.isWifiEnabled
      val success = runCatching { wm.setWifiEnabled(next) }.getOrDefault(false)
      if (success) return ToggleResult.Updated(next)
    }

    val panelIntent =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
      } else {
        Intent(Settings.ACTION_WIFI_SETTINGS)
      }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    return if (runCatching { context.startActivity(panelIntent) }.isSuccess) {
      ToggleResult.OpenedQuickPanel
    } else {
      ToggleResult.Unavailable
    }
  }

  @SuppressLint("MissingPermission")
  @Suppress("DEPRECATION")
  fun toggleBluetooth(context: Context): ToggleResult {
    val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = bm?.adapter ?: return ToggleResult.Unavailable

    if (!isBluetoothConnectPermissionGranted(context)) {
      val enableIntent =
        Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      return if (runCatching { context.startActivity(enableIntent) }.isSuccess) {
        ToggleResult.OpenedQuickPanel
      } else {
        openBluetoothQuickFallback(context)
      }
    }

    val currentEnabled = runCatching { adapter.isEnabled }.getOrDefault(false)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      val directSuccess =
        runCatching { if (currentEnabled) adapter.disable() else adapter.enable() }.getOrDefault(false)
      if (directSuccess) return ToggleResult.Updated(!currentEnabled)
    }

    if (!currentEnabled) {
      val reqIntent =
        Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      if (runCatching { context.startActivity(reqIntent) }.isSuccess) {
        return ToggleResult.OpenedQuickPanel
      }
    }
    return openBluetoothQuickFallback(context)
  }

  private fun openBluetoothQuickFallback(context: Context): ToggleResult {
    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return if (runCatching { context.startActivity(intent) }.isSuccess) {
      ToggleResult.OpenedQuickPanel
    } else {
      ToggleResult.Unavailable
    }
  }

  fun setTorchEnabled(context: Context, enable: Boolean): Boolean? {
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return null
    return runCatching {
      val flashCameraId =
        cameraManager.cameraIdList.firstOrNull { id ->
          val chars = cameraManager.getCameraCharacteristics(id)
          val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
          val facingBack =
            chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
          hasFlash && facingBack
        }
          ?: cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ==
              true
          }
      if (flashCameraId == null) return null
      cameraManager.setTorchMode(flashCameraId, enable)
      latestTorchState = enable
      enable
    }.getOrNull()
  }

  fun toggleDnd(context: Context): Boolean? {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return null
    if (!nm.isNotificationPolicyAccessGranted) {
      openDndPolicySettings(context)
      return null
    }
    val nextEnabled = nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
    return setDndEnabled(context, nextEnabled)
  }

  fun setDndEnabled(context: Context, enable: Boolean): Boolean? {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return null
    if (!nm.isNotificationPolicyAccessGranted) return null
    return runCatching {
      nm.setInterruptionFilter(
        if (enable) NotificationManager.INTERRUPTION_FILTER_PRIORITY
        else NotificationManager.INTERRUPTION_FILTER_ALL
      )
      enable
    }.getOrNull()
  }

  fun setSilentModeEnabled(context: Context, enable: Boolean): Boolean? {
    if (!isDndPolicyAccessGranted(context)) return null
    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
    return runCatching {
      am.ringerMode =
        if (enable) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
      enable
    }.getOrNull()
  }

  fun dispatchMediaAction(context: Context, action: MediaAction) {
    val controller = NotchMediaListenerService.activeController
    if (controller != null) {
      val controls = controller.transportControls
      when (action) {
        MediaAction.PLAY_PAUSE -> {
          val state = controller.playbackState?.state
          if (state == android.media.session.PlaybackState.STATE_PLAYING) controls.pause()
          else controls.play()
        }
        MediaAction.NEXT -> controls.skipToNext()
        MediaAction.PREVIOUS -> controls.skipToPrevious()
      }
      return
    }

    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    val keyCode =
      when (action) {
        MediaAction.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        MediaAction.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
        MediaAction.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
      }
    val now = SystemClock.uptimeMillis()
    audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
    audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
  }

  fun dispatchMediaExplicit(context: Context, play: Boolean) {
    val controller = NotchMediaListenerService.activeController
    if (controller != null) {
      val controls = controller.transportControls
      if (play) controls.play() else controls.pause()
      return
    }

    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    val keyCode = if (play) KeyEvent.KEYCODE_MEDIA_PLAY else KeyEvent.KEYCODE_MEDIA_PAUSE
    val now = SystemClock.uptimeMillis()
    audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
    audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
  }

  fun readBatteryTelemetry(context: Context, batteryIntent: Intent? = null): Pair<Int, Boolean> {
    val intent =
      if (batteryIntent?.action == Intent.ACTION_BATTERY_CHANGED) {
        batteryIntent
      } else {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
      }
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val percent = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else 100
    val charging =
      status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    return percent to charging
  }

  fun queryLaunchableApps(context: Context, forceRefresh: Boolean = false): List<LaunchableAppInfo> {
    val now = SystemClock.elapsedRealtime()
    val existing = cachedLaunchableApps
    if (!forceRefresh && existing != null && (now - cachedLaunchableAppsTimestampMs) < LAUNCHABLE_APPS_TTL_MS) {
      return existing
    }

    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved = pm.queryIntentActivities(intent, 0)
    val apps =
      resolved
        .asSequence()
        .mapNotNull { info ->
          val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
          if (pkg == context.packageName) return@mapNotNull null
          val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
          LaunchableAppInfo(packageName = pkg, label = label)
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
        .toList()
    cachedLaunchableApps = apps
    cachedLaunchableAppsTimestampMs = now
    return apps
  }

  fun loadAppIcon(context: Context, packageName: String): Drawable? =
    runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()

  fun loadCachedScaledAppIcon(
    context: Context,
    packageName: String,
    maxDim: Int = 96,
  ): ImageBitmap? {
    if (packageName.isBlank()) return null
    val cacheKey = "$packageName@$maxDim"
    scaledAppIconCache.get(cacheKey)?.let { return it }
    val drawable = loadAppIcon(context, packageName) ?: return null
    return drawableToScaledImageBitmap(drawable, maxDim = maxDim)?.also { bitmap ->
      scaledAppIconCache.put(cacheKey, bitmap)
    }
  }

  fun drawableToScaledImageBitmap(drawable: Drawable?, maxDim: Int = 96): ImageBitmap? {
    if (drawable == null) return null
    return runCatching {
      val raw =
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
          drawable.bitmap
        } else {
          val w = drawable.intrinsicWidth.coerceIn(48, maxDim)
          val h = drawable.intrinsicHeight.coerceIn(48, maxDim)
          Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
          }
        }
      val scaled =
        if (raw.width > maxDim || raw.height > maxDim) {
          val ratio = minOf(maxDim.toFloat() / raw.width, maxDim.toFloat() / raw.height)
          Bitmap.createScaledBitmap(
            raw,
            (raw.width * ratio).toInt().coerceAtLeast(1),
            (raw.height * ratio).toInt().coerceAtLeast(1),
            true,
          )
        } else {
          raw
        }
      scaled.asImageBitmap()
    }.getOrNull()
  }

  fun loadAppLabel(context: Context, packageName: String): String {
    if (packageName.isBlank()) return ""
    appLabelCache.get(packageName)?.let { return it }
    val resolved =
      runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
      }.getOrNull()
    if (!resolved.isNullOrBlank()) {
      appLabelCache.put(packageName, resolved)
      return resolved
    }
    return packageName.substringAfterLast('.')
  }

  fun launchApp(context: Context, packageName: String): Boolean {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching {
      context.startActivity(intent)
      true
    }.getOrDefault(false)
  }

  fun openNotificationTarget(
    context: Context,
    pendingIntent: PendingIntent?,
    fallbackPackageName: String,
  ): Boolean {
    if (pendingIntent != null) {
      val sent =
        runCatching {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val opts =
              ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                  ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                )
            pendingIntent.send(context, 0, null, null, null, null, opts.toBundle())
          } else {
            pendingIntent.send()
          }
          true
        }.getOrDefault(false)
      if (sent) return true
    }
    return if (fallbackPackageName.isNotBlank()) launchApp(context, fallbackPackageName) else false
  }

  fun startOverlayService(context: Context): Boolean {
    if (!isOverlayPermissionGranted(context)) return false
    val intent = Intent(context, NotchOverlayService::class.java)
    return runCatching {
      context.startService(intent)
      true
    }.getOrDefault(false)
  }

  fun stopOverlayService(context: Context) {
    context.stopService(Intent(context, NotchOverlayService::class.java))
  }
}

enum class MediaAction {
  PLAY_PAUSE,
  NEXT,
  PREVIOUS,
}
