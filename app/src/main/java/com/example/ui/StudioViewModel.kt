package com.example.ui

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MatusNotchApplication
import com.example.model.AccentPreset
import com.example.model.EntitlementResolver
import com.example.model.LaunchableAppInfo
import com.example.model.NotchBorderStyle
import com.example.model.NotchConfig
import com.example.model.NotchGeometry
import com.example.model.NotchGlowStyle
import com.example.model.NotchRuntimeState
import com.example.model.NotchWidgetType
import com.example.model.NotificationFilterMode
import com.example.model.ProEntitlementState
import com.example.model.ProPlanTier
import com.example.model.ProPreviewSelection
import com.example.system.SystemBridges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StudioViewModel(application: Application) : AndroidViewModel(application) {

  private val app = application as MatusNotchApplication
  private val repository = app.settingsRepository
  private val billingManager = app.billingManager
  val coordinator = app.coordinator

  val entitlementState: StateFlow<ProEntitlementState> = billingManager.entitlementState

  private val _previewSelection = MutableStateFlow(ProPreviewSelection())
  val previewSelection: StateFlow<ProPreviewSelection> = _previewSelection.asStateFlow()

  val persistedConfig: StateFlow<NotchConfig> =
    repository.configFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = NotchConfig(),
    )

  /**
   * Studio UI & live Studio preview config:
   * 1) Computes EntitlementResolver.effectiveConfig(persisted, isPro)
   * 2) Overlays temporary Try-Before-You-Buy preview selections when Free
   */
  val config: StateFlow<NotchConfig> =
    combine(repository.configFlow, entitlementState, _previewSelection) {
      persisted,
      entitlement,
      preview ->
      val isPro = entitlement.isProUnlocked()
      val effective = EntitlementResolver.effectiveConfig(persisted, isPro)
      EntitlementResolver.applyStudioPreview(effective, preview, isPro)
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = NotchConfig(),
    )

  val runtime: StateFlow<NotchRuntimeState> = coordinator.runtimeState

  private val _installedApps = MutableStateFlow<List<LaunchableAppInfo>>(emptyList())
  val installedApps: StateFlow<List<LaunchableAppInfo>> = _installedApps.asStateFlow()

  private val _isLoadingApps = MutableStateFlow(false)
  val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

  init {
    viewModelScope.launch {
      entitlementState
        .combine(_previewSelection) { entitlement, preview ->
          entitlement.isProUnlocked() to preview
        }
        .distinctUntilChanged()
        .collect { (isPro, preview) ->
          if (isPro && preview.hasAnyPreview) {
            promoteActivePreview()
          }
        }
    }
  }

  fun isProUnlocked(): Boolean = entitlementState.value.isProUnlocked()

  fun refreshTelemetry() {
    coordinator.refreshSystemTelemetry()
    billingManager.refresh()
  }

  fun loadInstalledApps() {
    if (_isLoadingApps.value) return
    viewModelScope.launch {
      _isLoadingApps.value = true
      val apps = withContext(Dispatchers.IO) { SystemBridges.queryLaunchableApps(app) }
      _installedApps.value = apps
      _isLoadingApps.value = false
    }
  }

  fun updateGeometry(transform: (NotchGeometry) -> NotchGeometry) {
    viewModelScope.launch {
      repository.updateConfig { it.copy(geometry = transform(it.geometry)) }
    }
  }

  fun resetGeometry() {
    viewModelScope.launch { repository.resetGeometry() }
  }

  fun setAccentPreset(preset: AccentPreset) {
    if (isProUnlocked() || !EntitlementResolver.isProAccent(preset)) {
      _previewSelection.update { it.copy(accentPreset = null, customAccentHex = null) }
      viewModelScope.launch {
        repository.updateConfig { it.copy(appearance = it.appearance.copy(accentPreset = preset)) }
      }
    } else {
      _previewSelection.update {
        it.copy(
          accentPreset = AccentPreset.CUSTOM,
          customAccentHex = it.customAccentHex ?: persistedConfig.value.appearance.customAccentHex,
        )
      }
    }
  }

  fun setCustomAccentHex(argbHex: Long) {
    val opaqueHex = argbHex or 0xFF000000L
    if (isProUnlocked()) {
      _previewSelection.update { it.copy(accentPreset = null, customAccentHex = null) }
      viewModelScope.launch {
        repository.updateConfig {
          it.copy(
            appearance =
              it.appearance.copy(
                accentPreset = AccentPreset.CUSTOM,
                customAccentHex = opaqueHex,
              )
          )
        }
      }
    } else {
      _previewSelection.update {
        it.copy(
          accentPreset = AccentPreset.CUSTOM,
          customAccentHex = opaqueHex,
        )
      }
    }
  }

  fun setPillOpacity(value: Float) {
    viewModelScope.launch {
      repository.updateConfig {
        it.copy(appearance = it.appearance.copy(pillOpacity = value.coerceIn(0.70f, 1f)))
      }
    }
  }

  fun setBorderStyle(style: NotchBorderStyle) {
    if (isProUnlocked() || !EntitlementResolver.isProBorder(style)) {
      _previewSelection.update { it.copy(borderStyle = null) }
      viewModelScope.launch {
        repository.updateConfig { it.copy(appearance = it.appearance.copy(borderStyle = style)) }
      }
    } else {
      _previewSelection.update { it.copy(borderStyle = style) }
    }
  }

  fun setGlowStyle(style: NotchGlowStyle) {
    if (isProUnlocked() || !EntitlementResolver.isProGlow(style)) {
      _previewSelection.update { it.copy(glowStyle = null) }
      viewModelScope.launch {
        repository.updateConfig { it.copy(appearance = it.appearance.copy(glowStyle = style)) }
      }
    } else {
      _previewSelection.update { it.copy(glowStyle = style) }
    }
  }

  fun setAutoSleepSeconds(seconds: Int) {
    viewModelScope.launch {
      repository.updateConfig { it.copy(autoSleepSeconds = seconds) }
    }
  }

  fun setAutoCollapseSeconds(seconds: Int) {
    viewModelScope.launch {
      repository.updateConfig { it.copy(autoCollapseSeconds = seconds.coerceIn(0, 60)) }
    }
  }

  fun toggleWidgetEnabled(type: NotchWidgetType) {
    if (!isProUnlocked() && EntitlementResolver.isProWidget(type)) {
      val currentlyPreviewing = _previewSelection.value.previewWidgetType == type
      val nextPreview = if (currentlyPreviewing) null else type
      _previewSelection.update { it.copy(previewWidgetType = nextPreview) }
      if (nextPreview != null) {
        val previewConfig =
          EntitlementResolver.applyStudioPreview(
            EntitlementResolver.effectiveConfig(persistedConfig.value, isPro = false),
            _previewSelection.value,
            isPro = false,
          )
        val idx = previewConfig.enabledWidgets.indexOf(nextPreview).coerceAtLeast(0)
        coordinator.onWidgetPageChanged(idx)
        coordinator.expand()
      }
      return
    }

    viewModelScope.launch {
      repository.updateConfig { cfg ->
        val enabledCount = cfg.widgetSlots.count { it.enabled }
        val updated =
          cfg.widgetSlots.map { slot ->
            if (slot.type == type) {
              if (slot.enabled && enabledCount <= 1) slot else slot.copy(enabled = !slot.enabled)
            } else {
              slot
            }
          }
        cfg.copy(widgetSlots = updated)
      }
    }
  }

  fun moveWidget(type: NotchWidgetType, delta: Int) {
    viewModelScope.launch {
      repository.updateConfig { cfg ->
        val list = cfg.widgetSlots.toMutableList()
        val index = list.indexOfFirst { it.type == type }
        val target = index + delta
        if (index >= 0 && target in list.indices) {
          val item = list.removeAt(index)
          list.add(target, item)
        }
        cfg.copy(widgetSlots = list)
      }
    }
  }

  fun setAutoHideInLandscape(enabled: Boolean) {
    viewModelScope.launch {
      repository.updateConfig { it.copy(autoHideInLandscape = enabled) }
    }
  }

  fun setUserGeminiApiKey(apiKey: String) {
    viewModelScope.launch {
      repository.updateConfig { it.copy(userGeminiApiKey = apiKey.trim()) }
    }
  }

  fun setNotificationFilterMode(mode: NotificationFilterMode) {
    if (isProUnlocked() || !EntitlementResolver.isProFilterMode(mode)) {
      _previewSelection.update { it.copy(notificationFilterMode = null) }
      viewModelScope.launch {
        repository.updateConfig { it.copy(notificationFilterMode = mode) }
      }
    } else {
      _previewSelection.update { it.copy(notificationFilterMode = mode) }
    }
  }

  fun toggleFilteredNotificationPackage(packageName: String) {
    viewModelScope.launch {
      repository.updateConfig { cfg ->
        val next = cfg.filteredNotificationPackages.toMutableSet()
        if (!next.add(packageName)) {
          next.remove(packageName)
        }
        cfg.copy(filteredNotificationPackages = next)
      }
    }
  }

  fun clearFilteredNotificationPackages() {
    viewModelScope.launch {
      repository.updateConfig { it.copy(filteredNotificationPackages = emptySet()) }
    }
  }

  fun togglePinnedApp(packageName: String) {
    viewModelScope.launch {
      repository.updateConfig { cfg ->
        val current = cfg.pinnedPackages.toMutableList()
        if (current.contains(packageName)) {
          current.remove(packageName)
        } else if (current.size < 4) {
          current.add(packageName)
        } else {
          coordinator.showStatusMessage("Up to 4 quick-launch apps can be pinned")
        }
        cfg.copy(pinnedPackages = current)
      }
    }
  }

  fun clearProPreview() {
    _previewSelection.value = ProPreviewSelection()
  }

  fun selectProPlan(tier: ProPlanTier) {
    billingManager.selectPlan(tier)
  }

  fun launchProSubscription(
    activity: Activity,
    tier: ProPlanTier = entitlementState.value.selectedPlanTier,
  ) {
    billingManager.launchSubscriptionFlow(activity, tier)
  }

  fun restoreProPurchases() {
    billingManager.restorePurchases()
  }

  fun setDebugProOverride(enabled: Boolean) {
    billingManager.setDebugProOverride(enabled) {
      if (enabled) promoteActivePreview()
    }
  }

  fun clearBillingMessage() {
    billingManager.clearBillingMessage()
  }

  private fun promoteActivePreview() {
    val snapshot = _previewSelection.value
    _previewSelection.value = ProPreviewSelection()
    if (!snapshot.hasAnyPreview) return
    viewModelScope.launch {
      repository.updateConfig { persisted ->
        EntitlementResolver.mergePreviewIntoConfig(persisted, snapshot)
      }
    }
  }
}
