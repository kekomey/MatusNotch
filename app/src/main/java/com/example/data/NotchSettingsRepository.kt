package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AccentPreset
import com.example.model.NotchAppearance
import com.example.model.NotchBorderStyle
import com.example.model.NotchConfig
import com.example.model.NotchGeometry
import com.example.model.NotchGlowStyle
import com.example.model.NotchWidgetType
import com.example.model.NotificationFilterMode
import com.example.model.PersistedProState
import com.example.model.WidgetSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.notchDataStore: DataStore<Preferences> by preferencesDataStore(name = "matusnotch_prefs")

class NotchSettingsRepository(private val context: Context) {

  private val configKey = stringPreferencesKey("notch_config_json_v1")
  private val debugProOverrideKey = booleanPreferencesKey("pro_debug_override_enabled")

  val configFlow: Flow<NotchConfig> =
    context.notchDataStore.data.map { prefs ->
      val raw = prefs[configKey]
      if (raw.isNullOrBlank()) NotchConfig() else decodeConfig(raw)
    }

  val proStateFlow: Flow<PersistedProState> =
    context.notchDataStore.data.map { prefs ->
      PersistedProState(
        debugProOverride = prefs[debugProOverrideKey] ?: false,
      )
    }

  suspend fun updateConfig(transform: (NotchConfig) -> NotchConfig) {
    context.notchDataStore.edit { prefs ->
      val current = prefs[configKey]?.let(::decodeConfig) ?: NotchConfig()
      val updated = transform(current)
      prefs[configKey] = encodeConfig(updated)
    }
  }

  suspend fun resetGeometry() {
    updateConfig { it.copy(geometry = NotchGeometry()) }
  }

  suspend fun setDebugProOverride(enabled: Boolean) {
    context.notchDataStore.edit { prefs ->
      prefs[debugProOverrideKey] = enabled
    }
  }

  private fun encodeConfig(config: NotchConfig): String {
    val root = JSONObject()
    val geo = JSONObject().apply {
      put("compactWidthDp", config.geometry.compactWidthDp.toDouble())
      put("compactHeightDp", config.geometry.compactHeightDp.toDouble())
      put("cornerRadiusDp", config.geometry.cornerRadiusDp.toDouble())
      put("offsetXDp", config.geometry.offsetXDp.toDouble())
      put("offsetYDp", config.geometry.offsetYDp.toDouble())
    }
    val app = JSONObject().apply {
      put("accentPreset", config.appearance.accentPreset.name)
      put("customAccentHex", config.appearance.customAccentHex)
      put("pillOpacity", config.appearance.pillOpacity.toDouble())
      put("borderStyle", config.appearance.borderStyle.name)
      put("glowStyle", config.appearance.glowStyle.name)
    }
    val widgetsArr = JSONArray().apply {
      config.widgetSlots.forEach { slot ->
        put(JSONObject().apply {
          put("type", slot.type.name)
          put("enabled", slot.enabled)
        })
      }
    }
    val pinnedArr = JSONArray().apply {
      config.pinnedPackages.forEach { put(it) }
    }
    val filteredArr = JSONArray().apply {
      config.filteredNotificationPackages.forEach { put(it) }
    }

    root.put("geometry", geo)
    root.put("appearance", app)
    root.put("widgets", widgetsArr)
    root.put("pinnedPackages", pinnedArr)
    root.put("autoSleepSeconds", config.autoSleepSeconds)
    root.put("autoCollapseSeconds", config.autoCollapseSeconds)
    root.put("autoHideInLandscape", config.autoHideInLandscape)
    root.put("userGeminiApiKey", config.userGeminiApiKey)
    root.put("notificationFilterMode", config.notificationFilterMode.name)
    root.put("filteredNotificationPackages", filteredArr)
    return root.toString()
  }

  private fun decodeConfig(raw: String): NotchConfig {
    return runCatching {
      val root = JSONObject(raw)
      val geoObj = root.optJSONObject("geometry")
      val geometry =
        if (geoObj != null) {
          NotchGeometry(
            compactWidthDp = geoObj.optDouble("compactWidthDp", 156.0).toFloat(),
            compactHeightDp = geoObj.optDouble("compactHeightDp", 38.0).toFloat(),
            cornerRadiusDp = geoObj.optDouble("cornerRadiusDp", 26.0).toFloat(),
            offsetXDp = geoObj.optDouble("offsetXDp", 0.0).toFloat(),
            offsetYDp = geoObj.optDouble("offsetYDp", 10.0).toFloat(),
          )
        } else NotchGeometry()

      val appObj = root.optJSONObject("appearance")
      val appearance =
        if (appObj != null) {
          NotchAppearance(
            accentPreset =
              runCatching { AccentPreset.valueOf(appObj.optString("accentPreset")) }
                .getOrDefault(AccentPreset.CYAN),
            customAccentHex = appObj.optLong("customAccentHex", 0xFF00E5FFL),
            pillOpacity = appObj.optDouble("pillOpacity", 0.98).toFloat(),
            borderStyle =
              runCatching { NotchBorderStyle.valueOf(appObj.optString("borderStyle")) }
                .getOrDefault(NotchBorderStyle.HAIRLINE),
            glowStyle =
              runCatching { NotchGlowStyle.valueOf(appObj.optString("glowStyle")) }
                .getOrDefault(NotchGlowStyle.LED_RACE),
          )
        } else NotchAppearance()

      val widgetsArr = root.optJSONArray("widgets")
      val parsedSlots = mutableListOf<WidgetSlot>()
      if (widgetsArr != null) {
        for (i in 0 until widgetsArr.length()) {
          val obj = widgetsArr.optJSONObject(i) ?: continue
          val type = runCatching { NotchWidgetType.valueOf(obj.optString("type")) }.getOrNull() ?: continue
          if (parsedSlots.none { it.type == type }) {
            parsedSlots.add(WidgetSlot(type = type, enabled = obj.optBoolean("enabled", true)))
          }
        }
      }
      NotchWidgetType.entries.forEach { type ->
        if (parsedSlots.none { it.type == type }) {
          parsedSlots.add(WidgetSlot(type = type, enabled = true))
        }
      }

      val pinnedArr = root.optJSONArray("pinnedPackages")
      val pinned = buildList {
        if (pinnedArr != null) {
          for (i in 0 until pinnedArr.length()) {
            pinnedArr.optString(i).takeIf { it.isNotBlank() }?.let(::add)
          }
        }
      }

      val filterMode =
        runCatching {
          NotificationFilterMode.valueOf(root.optString("notificationFilterMode"))
        }.getOrDefault(NotificationFilterMode.ALL_APPS)

      val filteredArr = root.optJSONArray("filteredNotificationPackages")
      val filteredSet = buildSet {
        if (filteredArr != null) {
          for (i in 0 until filteredArr.length()) {
            filteredArr.optString(i).takeIf { it.isNotBlank() }?.let(::add)
          }
        }
      }

      NotchConfig(
        geometry = geometry,
        appearance = appearance,
        widgetSlots = parsedSlots,
        pinnedPackages = pinned,
        autoSleepSeconds = root.optInt("autoSleepSeconds", 15).coerceIn(5, 120),
        autoCollapseSeconds = root.optInt("autoCollapseSeconds", 10).coerceIn(0, 60),
        autoHideInLandscape = root.optBoolean("autoHideInLandscape", true),
        userGeminiApiKey = root.optString("userGeminiApiKey", ""),
        notificationFilterMode = filterMode,
        filteredNotificationPackages = filteredSet,
      )
    }.getOrDefault(NotchConfig())
  }
}
