package com.example.service

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.MatusNotchApplication
import com.example.R
import com.example.model.EntitlementResolver
import com.example.model.NotchConfig
import com.example.model.NotchGeometry
import com.example.ui.components.DynamicNotchSurface
import com.example.ui.theme.MatusNotchTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Shared WindowManager Compose host used by both NotchOverlayService (TYPE_APPLICATION_OVERLAY)
 * and NotchAccessibilityService (TYPE_ACCESSIBILITY_OVERLAY for lockscreen visibility).
 *
 * Uses a fixed-width visual window (pass-through when compact so top-bar notification & control
 * center swipes on the sides are never blocked and width never resizes/shakes) paired with a
 * pill-sized compact touch hitbox positioned directly over the compact notch.
 */
class NotchOverlayWindowHost(
  private val context: Context,
  private val windowType: Int,
  private val app: MatusNotchApplication,
) {

  private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private var visualView: ComposeView? = null
  private var visualParams: WindowManager.LayoutParams? = null
  private var touchHitboxView: View? = null
  private var hitboxParams: WindowManager.LayoutParams? = null
  private var lifecycleOwner: OverlayLifecycleOwner? = null
  private var collectorJob: Job? = null
  private var isAttached = false
  private var lastVisualLayout: AppliedWindowLayout? = null
  private var lastHitboxLayout: AppliedWindowLayout? = null

  fun attach() {
    if (isAttached) return
    isAttached = true
    scope.launch {
      val initialCfg =
        runCatching { app.settingsRepository.configFlow.first() }.getOrDefault(NotchConfig())
      if (!isAttached) return@launch
      attachInternal(initialCfg)
    }
  }

  fun detach() {
    isAttached = false
    collectorJob?.cancel()
    collectorJob = null
    touchHitboxView?.let { view ->
      runCatching { windowManager.removeView(view) }
    }
    touchHitboxView = null
    hitboxParams = null
    visualView?.let { view ->
      runCatching { windowManager.removeView(view) }
    }
    visualView = null
    visualParams = null
    lastVisualLayout = null
    lastHitboxLayout = null
    lifecycleOwner?.destroy()
    lifecycleOwner = null
  }

  fun destroy() {
    detach()
    scope.cancel()
  }

  private fun fixedExpandedWidthDp(): Float {
    val screenWidthDp =
      context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density
    return (screenWidthDp - 32f).coerceIn(280f, 368f)
  }

  private fun attachInternal(initialConfig: NotchConfig) {
    val owner = OverlayLifecycleOwner().also {
      it.create()
      it.resume()
    }
    lifecycleOwner = owner

    val fixedWidthDp = fixedExpandedWidthDp()
    val expandedX = dpToPx(initialConfig.geometry.offsetXDp.coerceIn(-10f, 10f))
    val targetY = dpToPx(initialConfig.geometry.offsetYDp)

    @Suppress("DEPRECATION")
    val baseFlags =
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

    // Keep visual window bounds permanently fixed so SurfaceFlinger never reallocates buffers
    // or resizes across expand/collapse. When compact, FLAG_NOT_TOUCHABLE passes all touches through.
    val fixedVisualWidth = dpToPx(fixedWidthDp + 24f)
    val fixedVisualHeight = dpToPx(244f)
    val initialVisualFlags = baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    val initialVisualParams =
      WindowManager.LayoutParams(
        fixedVisualWidth,
        fixedVisualHeight,
        windowType,
        initialVisualFlags,
        PixelFormat.TRANSLUCENT,
      ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        x = expandedX
        y = targetY
        windowAnimations = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          fitInsetsTypes = 0
        }
      }
    visualParams = initialVisualParams
    lastVisualLayout =
      AppliedWindowLayout(
        width = fixedVisualWidth,
        height = fixedVisualHeight,
        x = expandedX,
        y = targetY,
        flags = initialVisualFlags,
        visibility = View.VISIBLE,
      )

    val composeView =
      ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setViewTreeLifecycleOwner(owner)
        setViewTreeViewModelStoreOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)

        setOnTouchListener { _, event ->
          if (event.action == MotionEvent.ACTION_OUTSIDE &&
            app.coordinator.runtimeState.value.isExpanded
          ) {
            app.coordinator.collapse()
          }
          false
        }

        setContent {
          val rawConfig by app.settingsRepository.configFlow.collectAsStateWithLifecycle(initialConfig)
          val entitlement by app.billingManager.entitlementState.collectAsStateWithLifecycle()
          val effectiveConfig =
            EntitlementResolver.effectiveConfig(rawConfig, entitlement.isProUnlocked())
          val runtime by app.coordinator.runtimeState.collectAsStateWithLifecycle()
          val screenInteractive by app.coordinator.isScreenInteractive.collectAsStateWithLifecycle()

          MatusNotchTheme(appearance = effectiveConfig.appearance) {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.TopCenter,
            ) {
              DynamicNotchSurface(
                config = effectiveConfig,
                runtime = runtime,
                coordinator = app.coordinator,
                expandedWidthDp = fixedWidthDp,
                isInStudioPreview = false,
                isScreenInteractive = screenInteractive,
                onOpenProPaywall = {
                  app.coordinator.closeVoiceListening()
                  runCatching {
                    val paywallIntent =
                      Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra(MainActivity.EXTRA_OPEN_PRO_PAYWALL, true)
                      }
                    context.startActivity(paywallIntent)
                  }
                },
              )
            }
          }
        }
      }

    // Separate pill-sized touch hitbox aligned pixel-for-pixel onto the compact notch pill
    val initialHitboxWidth = dpToPx(initialConfig.geometry.compactWidthDp)
    val initialHitboxHeight = dpToPx(initialConfig.geometry.compactHeightDp)
    val initialHitboxX = dpToPx(initialConfig.geometry.offsetXDp)
    val initialHitboxY = targetY + dpToPx(4f)
    val initialHitboxParams =
      WindowManager.LayoutParams(
        initialHitboxWidth,
        initialHitboxHeight,
        windowType,
        baseFlags,
        PixelFormat.TRANSLUCENT,
      ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        x = initialHitboxX
        y = initialHitboxY
        windowAnimations = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          fitInsetsTypes = 0
        }
      }
    hitboxParams = initialHitboxParams
    lastHitboxLayout =
      AppliedWindowLayout(
        width = initialHitboxWidth,
        height = initialHitboxHeight,
        x = initialHitboxX,
        y = initialHitboxY,
        flags = baseFlags,
        visibility = View.VISIBLE,
      )

    val hitboxView =
      View(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        contentDescription = context.getString(R.string.cd_expand_notch)
        isClickable = true
        setOnClickListener { view ->
          view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
          app.coordinator.expand()
        }
      }

    visualView = composeView
    touchHitboxView = hitboxView

    runCatching { windowManager.addView(composeView, initialVisualParams) }
    runCatching { windowManager.addView(hitboxView, initialHitboxParams) }

    collectorJob?.cancel()
    collectorJob =
      scope.launch {
        combine(
          app.settingsRepository.configFlow,
          app.coordinator.runtimeState,
          app.coordinator.isScreenInteractive,
        ) { cfg, rt, interactive ->
          OverlayWindowState(
            geometry = cfg.geometry,
            expanded =
              rt.isExpanded ||
                rt.voiceListening.phase != com.example.model.VoiceListeningPhase.INACTIVE,
            hasNotification = rt.activeNotification != null,
            hiddenInLandscape = cfg.autoHideInLandscape && rt.isLandscapeHidden,
            screenInteractive = interactive,
          )
        }
          .distinctUntilChanged()
          .collect { state ->
            updateWindowLayout(state)
          }
      }
  }

  private fun updateWindowLayout(state: OverlayWindowState) {
    val vView = visualView ?: return
    val vParams = visualParams ?: return
    val hView = touchHitboxView ?: return
    val hParams = hitboxParams ?: return

    if (state.hiddenInLandscape || !state.screenInteractive) {
      applyHitboxLayoutIfChanged(
        hView = hView,
        hParams = hParams,
        width = hParams.width,
        height = hParams.height,
        x = hParams.x,
        y = hParams.y,
        flags = hParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        visibility = View.GONE,
      )
      applyVisualLayoutIfChanged(
        vView = vView,
        vParams = vParams,
        width = vParams.width,
        height = vParams.height,
        x = vParams.x,
        y = vParams.y,
        flags = vParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        visibility = View.GONE,
      )
      return
    }

    val fixedWidthDp = fixedExpandedWidthDp()
    val fixedVisualWidth = dpToPx(fixedWidthDp + 24f)
    val fixedVisualHeight = dpToPx(244f)
    val expandedX = dpToPx(state.geometry.offsetXDp.coerceIn(-10f, 10f))
    val targetY = dpToPx(state.geometry.offsetYDp)

    val compactX = dpToPx(state.geometry.offsetXDp)
    val compactHitboxY = targetY + dpToPx(4f)
    val compactWidth = dpToPx(state.geometry.compactWidthDp)
    val compactHeight = dpToPx(state.geometry.compactHeightDp)

    if (state.expanded || state.hasNotification) {
      // Disable compact hitbox so touches inside expanded notch go directly to widgets / banner
      applyHitboxLayoutIfChanged(
        hView = hView,
        hParams = hParams,
        width = hParams.width,
        height = hParams.height,
        x = hParams.x,
        y = hParams.y,
        flags = hParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        visibility = View.GONE,
      )

      // Keep visual bounds fixed; only clear FLAG_NOT_TOUCHABLE to receive touches
      applyVisualLayoutIfChanged(
        vView = vView,
        vParams = vParams,
        width = fixedVisualWidth,
        height = fixedVisualHeight,
        x = expandedX,
        y = targetY,
        flags = vParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv(),
        visibility = View.VISIBLE,
      )
    } else {
      // Immediately pass touches through visual window & activate exact compact pill hitbox
      applyHitboxLayoutIfChanged(
        hView = hView,
        hParams = hParams,
        width = compactWidth,
        height = compactHeight,
        x = compactX,
        y = compactHitboxY,
        flags = hParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv(),
        visibility = View.VISIBLE,
      )

      applyVisualLayoutIfChanged(
        vView = vView,
        vParams = vParams,
        width = fixedVisualWidth,
        height = fixedVisualHeight,
        x = expandedX,
        y = targetY,
        flags = vParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        visibility = View.VISIBLE,
      )
    }
  }

  private fun applyVisualLayoutIfChanged(
    vView: View,
    vParams: WindowManager.LayoutParams,
    width: Int,
    height: Int,
    x: Int,
    y: Int,
    flags: Int,
    visibility: Int,
  ) {
    val target = AppliedWindowLayout(width, height, x, y, flags, visibility)
    if (target == lastVisualLayout) return
    val layoutParamsChanged =
      lastVisualLayout?.let {
        it.width != width || it.height != height || it.x != x || it.y != y || it.flags != flags
      } ?: true
    if (vView.visibility != visibility) {
      vView.visibility = visibility
    }
    if (layoutParamsChanged) {
      vParams.width = width
      vParams.height = height
      vParams.x = x
      vParams.y = y
      vParams.flags = flags
      runCatching { windowManager.updateViewLayout(vView, vParams) }
    }
    lastVisualLayout = target
  }

  private fun applyHitboxLayoutIfChanged(
    hView: View,
    hParams: WindowManager.LayoutParams,
    width: Int,
    height: Int,
    x: Int,
    y: Int,
    flags: Int,
    visibility: Int,
  ) {
    val target = AppliedWindowLayout(width, height, x, y, flags, visibility)
    if (target == lastHitboxLayout) return
    val layoutParamsChanged =
      lastHitboxLayout?.let {
        it.width != width || it.height != height || it.x != x || it.y != y || it.flags != flags
      } ?: true
    if (hView.visibility != visibility) {
      hView.visibility = visibility
    }
    if (layoutParamsChanged) {
      hParams.width = width
      hParams.height = height
      hParams.x = x
      hParams.y = y
      hParams.flags = flags
      runCatching { windowManager.updateViewLayout(hView, hParams) }
    }
    lastHitboxLayout = target
  }

  private fun dpToPx(dp: Float): Int =
    (dp * context.resources.displayMetrics.density).roundToInt()

  private data class AppliedWindowLayout(
    val width: Int,
    val height: Int,
    val x: Int,
    val y: Int,
    val flags: Int,
    val visibility: Int,
  )

  private data class OverlayWindowState(
    val geometry: NotchGeometry,
    val expanded: Boolean,
    val hasNotification: Boolean,
    val hiddenInLandscape: Boolean,
    val screenInteractive: Boolean,
  )

  private class OverlayLifecycleOwner :
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
      get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
      get() = store

    override val savedStateRegistry: SavedStateRegistry
      get() = savedStateController.savedStateRegistry

    fun create() {
      savedStateController.performRestore(null)
      lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun resume() {
      lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
      lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
      lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
      lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
      lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
      store.clear()
    }
  }
}
