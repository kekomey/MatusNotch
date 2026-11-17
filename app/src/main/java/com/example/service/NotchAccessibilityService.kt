package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.example.MatusNotchApplication

/**
 * Accessibility overlay host using TYPE_ACCESSIBILITY_OVERLAY so MatusNotch renders above the
 * Android Lockscreen (Keyguard) and system shade without reading any screen content, and exposes
 * Screenshot / Screen Lock global actions for the Device Actions notch widget.
 */
class NotchAccessibilityService : AccessibilityService() {

  private var windowHost: NotchOverlayWindowHost? = null

  override fun onServiceConnected() {
    super.onServiceConnected()
    activeInstance = this
    isConnected = true
    NotchOverlayService.onAccessibilityHostConnected(true)

    val app = applicationContext as? MatusNotchApplication ?: return
    app.coordinator.setOverlayRunningState(true)
    app.coordinator.setLandscapeHidden(
      resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    )
    app.coordinator.refreshSystemTelemetry()

    windowHost =
      NotchOverlayWindowHost(
        context = this,
        windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        app = app,
      ).also { it.attach() }
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    (applicationContext as? MatusNotchApplication)?.coordinator?.setLandscapeHidden(
      newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
    )
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    // No screen reading required; canRetrieveWindowContent=false
  }

  override fun onInterrupt() {
    // No ongoing accessibility feedback to interrupt
  }

  override fun onUnbind(intent: Intent?): Boolean {
    teardown()
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
    super.onDestroy()
    teardown()
  }

  private fun teardown() {
    activeInstance = null
    isConnected = false
    windowHost?.destroy()
    windowHost = null
    NotchOverlayService.onAccessibilityHostConnected(false)
    (applicationContext as? MatusNotchApplication)?.coordinator?.refreshSystemTelemetry()
  }

  companion object {
    @Volatile
    var isConnected: Boolean = false
      private set

    @Volatile
    private var activeInstance: NotchAccessibilityService? = null

    fun performScreenshot(): Boolean {
      val service = activeInstance ?: return false
      return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        service.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
      } else {
        false
      }
    }

    fun performLockScreen(): Boolean {
      val service = activeInstance ?: return false
      return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
      } else {
        false
      }
    }
  }
}
