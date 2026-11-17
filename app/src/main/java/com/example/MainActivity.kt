package com.example

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.system.SystemBridges
import com.example.ui.StudioViewModel
import com.example.ui.components.MatusProPaywallSheet
import com.example.ui.components.ProPreviewCheckoutBar
import com.example.ui.components.StudioLiveNotchStage
import com.example.ui.screens.DesignScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WidgetsScreen
import com.example.ui.theme.MatusNotchTheme
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

  companion object {
    const val EXTRA_OPEN_PRO_PAYWALL = "com.example.OPEN_PRO_PAYWALL"
  }

  private val openPaywallSignal = mutableStateOf(false)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (intent?.getBooleanExtra(EXTRA_OPEN_PRO_PAYWALL, false) == true) {
      openPaywallSignal.value = true
      intent?.removeExtra(EXTRA_OPEN_PRO_PAYWALL)
    }

    enableEdgeToEdge()
    requestHighestDisplayRefreshRate()
    setContent {
      val viewModel: StudioViewModel = viewModel()
      val config by viewModel.config.collectAsStateWithLifecycle()
      val runtime by viewModel.runtime.collectAsStateWithLifecycle()
      val openPaywallRequested by openPaywallSignal

      MatusNotchTheme(appearance = config.appearance) {
        MatusNotchStudioApp(
          viewModel = viewModel,
          config = config,
          runtime = runtime,
          openPaywallFromIntent = openPaywallRequested,
          onConsumedOpenPaywallIntent = { openPaywallSignal.value = false },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (intent.getBooleanExtra(EXTRA_OPEN_PRO_PAYWALL, false)) {
      openPaywallSignal.value = true
      intent.removeExtra(EXTRA_OPEN_PRO_PAYWALL)
    }
  }

  private fun requestHighestDisplayRefreshRate() {
    runCatching {
      val activeDisplay =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          display
        } else {
          @Suppress("DEPRECATION")
          windowManager.defaultDisplay
        } ?: return

      val currentMode = activeDisplay.mode
      val bestMode =
        activeDisplay.supportedModes
          .filter {
            it.physicalWidth == currentMode.physicalWidth &&
              it.physicalHeight == currentMode.physicalHeight
          }
          .maxByOrNull { it.refreshRate }

      if (bestMode != null && bestMode.modeId != currentMode.modeId) {
        val attrs = window.attributes
        attrs.preferredDisplayModeId = bestMode.modeId
        window.attributes = attrs
      }
    }
  }
}

private enum class StudioTab(
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val tag: String,
) {
  DESIGN(R.string.tab_design, Icons.Rounded.Palette, Icons.Outlined.Palette, "nav_design"),
  WIDGETS(R.string.tab_widgets, Icons.Rounded.Widgets, Icons.Outlined.Widgets, "nav_widgets"),
  SETTINGS(R.string.tab_settings, Icons.Rounded.Settings, Icons.Outlined.Settings, "nav_settings"),
}

@Composable
private fun MatusNotchStudioApp(
  viewModel: StudioViewModel,
  config: com.example.model.NotchConfig,
  runtime: com.example.model.NotchRuntimeState,
  openPaywallFromIntent: Boolean,
  onConsumedOpenPaywallIntent: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  var currentTab by rememberSaveable { mutableStateOf(StudioTab.DESIGN) }
  var showPaywallSheet by rememberSaveable { mutableStateOf(false) }

  val entitlement by viewModel.entitlementState.collectAsStateWithLifecycle()
  val previewSelection by viewModel.previewSelection.collectAsStateWithLifecycle()
  val isProUnlocked = entitlement.isProUnlocked()

  LaunchedEffect(openPaywallFromIntent) {
    if (openPaywallFromIntent) {
      showPaywallSheet = true
      onConsumedOpenPaywallIntent()
    }
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshTelemetry()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val handleOverlayToggle: () -> Unit = {
    if (runtime.telemetry.isOverlayServiceRunning) {
      SystemBridges.stopOverlayService(context)
      if (com.example.service.NotchAccessibilityService.isConnected) {
        SystemBridges.openAccessibilitySettings(context)
        scope.launch {
          snackbarHostState.showSnackbar(
            "Turn off MatusNotch in Accessibility Settings to stop lockscreen overlay"
          )
        }
      } else {
        viewModel.coordinator.setOverlayRunningState(false)
      }
    } else if (!SystemBridges.isOverlayPermissionGranted(context)) {
      currentTab = StudioTab.SETTINGS
      SystemBridges.openOverlayPermissionSettings(context)
      scope.launch {
        snackbarHostState.showSnackbar("Grant 'Display over other apps' to float MatusNotch")
      }
    } else {
      val started = SystemBridges.startOverlayService(context)
      viewModel.coordinator.setOverlayRunningState(started)
      if (started) {
        scope.launch {
          snackbarHostState.showSnackbar("MatusNotch floating overlay is now active!")
        }
      }
    }
  }

  BackHandler(enabled = currentTab != StudioTab.DESIGN) {
    currentTab = StudioTab.DESIGN
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize().background(ObsidianVoid)) {
    val useRail = maxWidth >= 680.dp
    val primaryColor = MaterialTheme.colorScheme.primary

    Scaffold(
      containerColor = ObsidianVoid,
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      bottomBar = {
        if (!useRail) {
          NavigationBar(
            containerColor = ObsidianSurface,
            modifier = Modifier.navigationBarsPadding(),
          ) {
            StudioTab.entries.forEach { tab ->
              val selected = currentTab == tab
              val label = stringResource(tab.labelRes)
              NavigationBarItem(
                selected = selected,
                onClick = { currentTab = tab },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = label,
                  )
                },
                label = { Text(label) },
                colors =
                  NavigationBarItemDefaults.colors(
                    selectedIconColor = primaryColor,
                    selectedTextColor = primaryColor,
                    indicatorColor = primaryColor.copy(alpha = 0.16f),
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted,
                  ),
                modifier = Modifier.testTag(tab.tag),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useRail) {
          NavigationRail(
            containerColor = ObsidianSurface,
            modifier = Modifier.fillMaxHeight(),
          ) {
            StudioTab.entries.forEach { tab ->
              val selected = currentTab == tab
              val label = stringResource(tab.labelRes)
              NavigationRailItem(
                selected = selected,
                onClick = { currentTab = tab },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = label,
                  )
                },
                label = { Text(label) },
                colors =
                  NavigationRailItemDefaults.colors(
                    selectedIconColor = primaryColor,
                    selectedTextColor = primaryColor,
                    indicatorColor = primaryColor.copy(alpha = 0.16f),
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted,
                  ),
                modifier = Modifier.testTag(tab.tag),
              )
            }
          }
        }

        Box(
          modifier = Modifier.weight(1f).fillMaxHeight(),
          contentAlignment = Alignment.TopCenter,
        ) {
          Column(
            modifier =
              Modifier.fillMaxWidth()
                .widthIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            StudioLiveNotchStage(
              config = config,
              runtime = runtime,
              coordinator = viewModel.coordinator,
              onToggleOverlay = handleOverlayToggle,
              onNavigateToWidgetsTab = { currentTab = StudioTab.WIDGETS },
              onOpenProPaywall = { showPaywallSheet = true },
            )

            ProPreviewCheckoutBar(
              visible = !isProUnlocked && previewSelection.hasAnyPreview,
              preview = previewSelection,
              entitlement = entitlement,
              onResetPreview = { viewModel.clearProPreview() },
              onStartPlayTrial = {
                (context as? Activity)?.let { activity ->
                  viewModel.launchProSubscription(activity, entitlement.selectedPlanTier)
                }
              },
              onOpenPaywall = { showPaywallSheet = true },
            )

            AnimatedContent(
              targetState = currentTab,
              transitionSpec = { fadeIn() togetherWith fadeOut() },
              label = "studio_tab_content",
            ) { tab ->
              when (tab) {
                StudioTab.DESIGN ->
                  DesignScreen(
                    config = config,
                    isProUnlocked = isProUnlocked,
                    viewModel = viewModel,
                  )
                StudioTab.WIDGETS ->
                  WidgetsScreen(
                    config = config,
                    isProUnlocked = isProUnlocked,
                    previewWidgetType = previewSelection.previewWidgetType,
                    onOpenPaywall = { showPaywallSheet = true },
                    viewModel = viewModel,
                  )
                StudioTab.SETTINGS ->
                  SettingsScreen(
                    config = config,
                    runtime = runtime,
                    entitlement = entitlement,
                    viewModel = viewModel,
                    onToggleOverlay = handleOverlayToggle,
                    onOpenPaywall = { showPaywallSheet = true },
                  )
              }
            }
          }
        }
      }
    }
  }

  if (showPaywallSheet) {
    MatusProPaywallSheet(
      config = config,
      entitlement = entitlement,
      onSelectPlan = { tier -> viewModel.selectProPlan(tier) },
      onLaunchSubscription = { tier ->
        (context as? Activity)?.let { activity ->
          viewModel.launchProSubscription(activity, tier)
        }
      },
      onRestorePurchase = { viewModel.restoreProPurchases() },
      onDismiss = {
        viewModel.clearBillingMessage()
        showPaywallSheet = false
      },
    )
  }
}
