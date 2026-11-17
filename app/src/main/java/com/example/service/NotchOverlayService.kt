package com.example.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import com.example.MatusNotchApplication

class NotchOverlayService : Service() {

  private var windowHost: NotchOverlayWindowHost? = null

  override fun onCreate() {
    super.onCreate()
    activeInstance = this
    clearLegacyOverlayNotification()

    val app = applicationContext as MatusNotchApplication
    app.coordinator.setOverlayRunningState(true)
    app.coordinator.setLandscapeHidden(
      resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    )
    windowHost =
      NotchOverlayWindowHost(
        context = this,
        windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        app = app,
      )
    if (!NotchAccessibilityService.isConnected) {
      windowHost?.attach()
    }
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    (applicationContext as? MatusNotchApplication)?.coordinator?.setLandscapeHidden(
      newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
    )
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val app = applicationContext as? MatusNotchApplication
    when (intent?.action) {
      ACTION_TOGGLE_EXPAND -> app?.coordinator?.toggleExpand()
      ACTION_STOP_OVERLAY -> {
        stopSelf()
        return START_NOT_STICKY
      }
    }
    return START_STICKY
  }

  override fun onDestroy() {
    super.onDestroy()
    activeInstance = null
    windowHost?.destroy()
    windowHost = null
    (applicationContext as? MatusNotchApplication)?.coordinator?.setOverlayRunningState(
      NotchAccessibilityService.isConnected
    )
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun syncWithAccessibilityHost(accessibilityActive: Boolean) {
    if (accessibilityActive) {
      windowHost?.detach()
    } else {
      windowHost?.attach()
    }
  }

  private fun clearLegacyOverlayNotification() {
    runCatching {
      val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
      nm.cancel(LEGACY_NOTIFICATION_ID)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        nm.deleteNotificationChannel(LEGACY_CHANNEL_ID)
      }
    }
  }

  companion object {
    private const val LEGACY_CHANNEL_ID = "matusnotch_overlay_channel"
    private const val LEGACY_NOTIFICATION_ID = 4091
    const val ACTION_TOGGLE_EXPAND = "com.example.action.TOGGLE_EXPAND"
    const val ACTION_STOP_OVERLAY = "com.example.action.STOP_OVERLAY"

    @Volatile
    private var activeInstance: NotchOverlayService? = null

    fun onAccessibilityHostConnected(connected: Boolean) {
      activeInstance?.syncWithAccessibilityHost(connected)
    }
  }
}
