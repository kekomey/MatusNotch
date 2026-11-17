package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.model.EntitlementResolver
import com.example.model.NotchConfig
import com.example.model.NotchRuntimeState
import com.example.model.NotificationFilterMode
import com.example.model.ProEntitlementState
import com.example.model.ProPlanTier
import com.example.system.SystemBridges
import com.example.ui.StudioViewModel
import com.example.ui.components.ProLockBadge
import com.example.ui.components.StudioCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
  config: NotchConfig,
  runtime: NotchRuntimeState,
  entitlement: ProEntitlementState,
  viewModel: StudioViewModel,
  onToggleOverlay: () -> Unit,
  onOpenPaywall: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val primaryColor = MaterialTheme.colorScheme.primary
  val secondaryColor = MaterialTheme.colorScheme.secondary
  val telemetry = runtime.telemetry
  val isProUnlocked = entitlement.isProUnlocked()
  var showNotificationFilterPicker by remember { mutableStateOf(false) }

  val permissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission(),
      onResult = { viewModel.refreshTelemetry() },
    )

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // Dedicated Matus Pro Status & Upgrade Card
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .clip(RoundedCornerShape(22.dp))
          .background(
            Brush.linearGradient(
              listOf(
                primaryColor.copy(alpha = 0.20f),
                secondaryColor.copy(alpha = 0.10f),
                ObsidianSurface,
              )
            )
          )
          .border(1.dp, primaryColor.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
          .padding(16.dp)
          .testTag("settings_matus_pro_card"),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Box(
            modifier =
              Modifier.size(40.dp)
                .clip(CircleShape)
                .background(primaryColor.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Rounded.WorkspacePremium,
              contentDescription = null,
              tint = primaryColor,
              modifier = Modifier.size(22.dp),
            )
          }
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = "Matus Pro",
              style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
              color = TextPrimary,
            )
            Text(
              text =
                when {
                  entitlement.isPlaySubscribed ->
                    "Active ${entitlement.activePlanTier?.title ?: "Pro"} Google Play subscription"
                  entitlement.isDebugUnlocked -> "Unlocked via Debug Preview toggle"
                  else -> "Color Wheel, Neon Glows, Calc & Focus Timer, Filters & Voice AI"
                },
              style = MaterialTheme.typography.bodySmall,
              color = if (isProUnlocked) StatusSuccess else TextSecondary,
            )
          }
        }

        Box(
          modifier =
            Modifier.clip(RoundedCornerShape(50))
              .background(
                if (isProUnlocked) StatusSuccess.copy(alpha = 0.18f)
                else primaryColor.copy(alpha = 0.16f)
              )
              .border(
                1.dp,
                if (isProUnlocked) StatusSuccess.copy(alpha = 0.6f)
                else primaryColor.copy(alpha = 0.55f),
                RoundedCornerShape(50),
              )
              .padding(horizontal = 10.dp, vertical = 4.dp)
              .testTag("settings_pro_status_pill"),
        ) {
          Text(
            text =
              when {
                entitlement.isPlaySubscribed -> "PRO"
                entitlement.isDebugUnlocked -> "DEBUG"
                else -> "FREE"
              },
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (isProUnlocked) StatusSuccess else primaryColor,
          )
        }
      }

      if (!entitlement.billingMessage.isNullOrBlank()) {
        Text(
          text = entitlement.billingMessage,
          style = MaterialTheme.typography.bodySmall,
          color = primaryColor,
        )
      }

      if (!entitlement.isPlaySubscribed) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          val selectedTier = entitlement.selectedPlanTier
          FilterChip(
            selected = selectedTier == ProPlanTier.MONTHLY,
            onClick = { viewModel.selectProPlan(ProPlanTier.MONTHLY) },
            label = {
              Text(
                text = "Monthly ${entitlement.displayPriceWithPeriod(ProPlanTier.MONTHLY)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = primaryColor.copy(alpha = 0.22f),
                selectedLabelColor = primaryColor,
              ),
            modifier =
              Modifier.weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("settings_plan_monthly"),
          )
          FilterChip(
            selected = selectedTier == ProPlanTier.YEARLY,
            onClick = { viewModel.selectProPlan(ProPlanTier.YEARLY) },
            label = {
              Text(
                text = "Annual ${entitlement.displayPriceWithPeriod(ProPlanTier.YEARLY)} • Save 58%",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = primaryColor.copy(alpha = 0.22f),
                selectedLabelColor = primaryColor,
              ),
            modifier =
              Modifier.weight(1.25f)
                .minimumInteractiveComponentSize()
                .testTag("settings_plan_yearly"),
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = onOpenPaywall,
            modifier =
              Modifier.weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("settings_upgrade_pro_btn"),
          ) {
            Text(
              text = "View Plans",
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          Button(
            onClick = {
              (context as? Activity)?.let { activity ->
                viewModel.launchProSubscription(activity, entitlement.selectedPlanTier)
              }
            },
            enabled = !entitlement.isPurchasing,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = Color.Black,
              ),
            modifier =
              Modifier.weight(1.25f)
                .minimumInteractiveComponentSize()
                .testTag("settings_start_trial_btn"),
          ) {
            Text(
              text =
                if (entitlement.hasTrialOffer(entitlement.selectedPlanTier)) "4-Day Free Trial"
                else "Subscribe ${entitlement.displayPriceWithPeriod(entitlement.selectedPlanTier)}",
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "4-day trial via Google Play • \$1/mo or \$5/yr • Cancel anytime",
          style = MaterialTheme.typography.labelSmall,
          color = TextMuted,
          modifier = Modifier.weight(1f).padding(end = 6.dp),
        )
        TextButton(
          onClick = { viewModel.restoreProPurchases() },
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .testTag("settings_restore_purchase_btn"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Restore,
            contentDescription = null,
            tint = primaryColor,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.size(5.dp))
          Text("Restore", color = primaryColor)
        }
      }

      if (BuildConfig.DEBUG) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(ObsidianElevated)
              .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
              .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.BugReport,
            contentDescription = null,
            tint = StatusWarning,
            modifier = Modifier.size(18.dp),
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Debug Pro Preview Toggle",
              style = MaterialTheme.typography.labelLarge,
              color = TextPrimary,
            )
            Text(
              text = "Debug build only: test all Pro features without Play Store",
              style = MaterialTheme.typography.labelSmall,
              color = TextSecondary,
            )
          }
          Switch(
            checked = entitlement.isDebugUnlocked,
            onCheckedChange = { enabled -> viewModel.setDebugProOverride(enabled) },
            colors =
              SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = StatusWarning,
              ),
            modifier = Modifier.testTag("settings_debug_pro_switch"),
          )
        }
      }
    }
    StudioCard(
      title = "System Floating Overlay",
      subtitle = "Keep MatusNotch floating above all apps and around your camera cutout",
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Button(
          onClick = onToggleOverlay,
          colors =
            ButtonDefaults.buttonColors(
              containerColor =
                if (telemetry.isOverlayServiceRunning) StatusSuccess else primaryColor,
              contentColor = Color.Black,
            ),
          modifier =
            Modifier.weight(1f)
              .minimumInteractiveComponentSize()
              .testTag("settings_overlay_button"),
        ) {
          Text(
            if (telemetry.isOverlayServiceRunning) "Stop Floating Notch"
            else "Start Floating Notch"
          )
        }
      }
    }

    StudioCard(
      title = "Smart Notch Rules",
      subtitle = "Control fullscreen gaming visibility and which apps can expand the 4.5s notification banner",
    ) {
      // Auto-Hide in Landscape / Fullscreen Videos & Games
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianElevated)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Box(
          modifier =
            Modifier.size(38.dp)
              .clip(CircleShape)
              .background(primaryColor.copy(alpha = 0.16f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Rounded.SportsEsports,
            contentDescription = null,
            tint = primaryColor,
          )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = "Auto-Hide in Landscape",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
          )
          Text(
            text = "Automatically hides MatusNotch during landscape videos and fullscreen games",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
          )
        }
        Switch(
          checked = config.autoHideInLandscape,
          onCheckedChange = { viewModel.setAutoHideInLandscape(it) },
          colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = primaryColor),
          modifier = Modifier.testTag("switch_auto_hide_landscape"),
        )
      }

      // Expanded Inactivity Auto-Collapse
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianElevated)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Timer,
            contentDescription = null,
            tint = primaryColor,
          )
          Column {
            Text(
              text = "Expanded Auto-Collapse",
              style = MaterialTheme.typography.titleMedium,
              color = TextPrimary,
            )
            Text(
              text = "Automatically folds the expanded notch back to compact mode after inactivity",
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary,
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          listOf(0 to "Off", 10 to "10s", 15 to "15s", 30 to "30s").forEach { (sec, label) ->
            val selected = config.autoCollapseSeconds == sec
            FilterChip(
              selected = selected,
              onClick = { viewModel.setAutoCollapseSeconds(sec) },
              label = { Text(label) },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = primaryColor.copy(alpha = 0.2f),
                  selectedLabelColor = primaryColor,
                ),
              modifier =
                Modifier.weight(1f)
                  .minimumInteractiveComponentSize()
                  .testTag("autocollapse_${sec}s"),
            )
          }
        }
      }

      // Per-App 4.5s Notification Popup Filter
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianElevated)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.FilterList,
            contentDescription = null,
            tint = primaryColor,
          )
          Column(modifier = Modifier.weight(1f)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = "4.5s Notification Popup Filter",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
              )
              ProLockBadge(locked = !isProUnlocked)
            }
            Text(
              text = "Choose whether all apps, only selected apps, or non-muted apps pop open the notch",
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary,
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          NotificationFilterMode.entries.forEach { mode ->
            val selected = config.notificationFilterMode == mode
            val isProMode = EntitlementResolver.isProFilterMode(mode)
            val locked = isProMode && !isProUnlocked
            FilterChip(
              selected = selected,
              onClick = { viewModel.setNotificationFilterMode(mode) },
              label = {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                  Text(
                    text = mode.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  if (isProMode) {
                    ProLockBadge(locked = locked)
                  }
                }
              },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = primaryColor.copy(alpha = 0.2f),
                  selectedLabelColor = primaryColor,
                ),
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .testTag("filter_mode_${mode.name.lowercase()}"),
            )
          }
        }

        if (config.notificationFilterMode != NotificationFilterMode.ALL_APPS) {
          val actionLabel =
            when (config.notificationFilterMode) {
              NotificationFilterMode.ONLY_SELECTED ->
                "Allowed Apps (${config.filteredNotificationPackages.size})"
              NotificationFilterMode.MUTE_SELECTED ->
                "Muted Apps (${config.filteredNotificationPackages.size})"
              NotificationFilterMode.ALL_APPS -> ""
            }
          OutlinedButton(
            onClick = {
              viewModel.loadInstalledApps()
              showNotificationFilterPicker = true
            },
            modifier =
              Modifier.fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("btn_choose_filter_apps"),
          ) {
            Text(
              if (isProUnlocked) actionLabel
              else "$actionLabel (Pro Preview)"
            )
          }
        }
      }
    }

    StudioCard(
      title = "Voice Assistant & Gemini AI",
      subtitle = "Long-press the expanded mascot for hands-free commands & live Gemini AI answers",
      headerTrailing = { ProLockBadge(locked = !isProUnlocked) },
    ) {
      if (!isProUnlocked) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(primaryColor.copy(alpha = 0.14f))
              .border(1.dp, primaryColor.copy(alpha = 0.42f), RoundedCornerShape(14.dp))
              .clickable { onOpenPaywall() }
              .padding(horizontal = 12.dp, vertical = 10.dp)
              .testTag("settings_voice_pro_banner"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = primaryColor,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = "Matus Pro (\$1/mo or \$5/yr) — tap for plans & 4-day Play trial",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
            modifier = Modifier.weight(1f),
          )
          ProLockBadge(locked = true)
        }
      }
      var apiKeyDraft by remember(config.userGeminiApiKey) { mutableStateOf(config.userGeminiApiKey) }
      var showApiKey by remember { mutableStateOf(false) }
      val hasSavedKey = config.userGeminiApiKey.isNotBlank()
      val isDirty = apiKeyDraft.trim() != config.userGeminiApiKey.trim()

      OutlinedTextField(
        value = apiKeyDraft,
        onValueChange = { apiKeyDraft = it },
        label = { Text("Gemini API Key") },
        placeholder = { Text("Paste key from aistudio.google.com") },
        leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null, tint = primaryColor) },
        trailingIcon = {
          IconButton(onClick = { showApiKey = !showApiKey }) {
            Icon(
              imageVector = if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
              contentDescription = if (showApiKey) "Hide API key" else "Show API key",
            )
          }
        },
        visualTransformation =
          if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("input_gemini_api_key"),
      )

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = if (hasSavedKey) Icons.Rounded.CheckCircle else Icons.Rounded.Mic,
          contentDescription = null,
          tint = if (hasSavedKey) StatusSuccess else TextSecondary,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text =
            if (hasSavedKey) "Personal Gemini key saved locally on this device"
            else "No key saved — local commands (open apps, torch, media, timer, toggles) work offline",
          style = MaterialTheme.typography.bodyMedium,
          color = if (hasSavedKey) StatusSuccess else TextSecondary,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Button(
          onClick = { viewModel.setUserGeminiApiKey(apiKeyDraft.trim()) },
          enabled = isDirty && apiKeyDraft.trim().isNotEmpty(),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = primaryColor,
              contentColor = Color.Black,
            ),
          modifier =
            Modifier.weight(1f)
              .minimumInteractiveComponentSize()
              .testTag("btn_save_gemini_key"),
        ) {
          Text(if (hasSavedKey && !isDirty) "Saved" else "Save Key")
        }

        OutlinedButton(
          onClick = {
            apiKeyDraft = ""
            viewModel.setUserGeminiApiKey("")
          },
          enabled = hasSavedKey || apiKeyDraft.isNotEmpty(),
          modifier =
            Modifier.weight(1f)
              .minimumInteractiveComponentSize()
              .testTag("btn_clear_gemini_key"),
        ) {
          Text("Clear")
        }
      }
    }

    StudioCard(
      title = "Permissions & System Bridges",
      subtitle = "Enable bridges for floating overlay, lockscreen & screenshot actions, live media, and quick toggles",
    ) {
      PermissionBridgeRow(
        title = "Display Over Other Apps",
        description = "Required for the floating MatusNotch pill across all apps",
        icon = Icons.Rounded.Layers,
        granted = telemetry.hasOverlayPermission,
        actionLabel = "Grant",
        testTag = "perm_overlay_btn",
        onAction = { SystemBridges.openOverlayPermissionSettings(context) },
      )

      PermissionBridgeRow(
        title = "Lockscreen & Device Actions",
        description = "Shows MatusNotch on the lockscreen and powers 1-tap Screenshot and Screen Lock",
        icon = Icons.Rounded.Lock,
        granted = telemetry.hasLockscreenAccess,
        actionLabel = "Enable",
        testTag = "perm_lockscreen_btn",
        onAction = { SystemBridges.openAccessibilitySettings(context) },
      )

      PermissionBridgeRow(
        title = "Modify System Settings",
        description = "Allows 1-tap Auto-Rotate toggling from the Device Actions notch widget",
        icon = Icons.Rounded.ScreenRotation,
        granted = telemetry.hasWriteSettingsPermission,
        actionLabel = "Allow",
        testTag = "perm_write_settings_btn",
        onAction = { SystemBridges.openWriteSettings(context) },
      )

      PermissionBridgeRow(
        title = "Media & Notification Access",
        description = "Shows live album art, incoming calls, recording status, and 4.5s notification banners",
        icon = Icons.Rounded.MusicNote,
        granted = telemetry.hasNotificationListenerPermission,
        actionLabel = "Enable",
        testTag = "perm_media_listener_btn",
        onAction = { SystemBridges.openNotificationListenerSettings(context) },
      )

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PermissionBridgeRow(
          title = "Bluetooth Quick Control",
          description = "Reads live Bluetooth state and enables quick toggling from the notch widget",
          icon = Icons.Rounded.Bluetooth,
          granted = telemetry.hasBluetoothPermission,
          actionLabel = "Allow",
          testTag = "perm_bluetooth_btn",
          onAction = { permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
        )
      }

      PermissionBridgeRow(
        title = "Do Not Disturb Access",
        description = "Allows 1-tap Focus DND toggling from the Quick Toggles widget",
        icon = Icons.Rounded.DoNotDisturbOn,
        granted = telemetry.hasDndPolicyPermission,
        actionLabel = "Allow",
        testTag = "perm_dnd_btn",
        onAction = { SystemBridges.openDndPolicySettings(context) },
      )

      PermissionBridgeRow(
        title = "Microphone Access",
        description = "Enables live voice listening and waveform when long-pressing the expanded mascot",
        icon = Icons.Rounded.Mic,
        granted = telemetry.hasRecordAudioPermission,
        actionLabel = "Allow",
        testTag = "perm_record_audio_btn",
        onAction = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
      )
    }

    Spacer(modifier = Modifier.height(16.dp))
  }

  if (showNotificationFilterPicker) {
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingApps.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }

    val dialogTitle =
      if (config.notificationFilterMode == NotificationFilterMode.ONLY_SELECTED) {
        "Allow Notch Popups From"
      } else {
        "Mute Notch Popups From"
      }

    AlertDialog(
      onDismissRequest = { showNotificationFilterPicker = false },
      containerColor = ObsidianSurface,
      title = { Text(dialogTitle, color = TextPrimary) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search installed apps...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("search_filter_apps_input"),
          )
          if (isLoading) {
            Box(
              modifier = Modifier.fillMaxWidth().height(160.dp),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator(color = primaryColor)
            }
          } else {
            val filtered =
              remember(installedApps, search) {
                if (search.isBlank()) installedApps
                else installedApps.filter {
                  it.label.contains(search, ignoreCase = true) ||
                    it.packageName.contains(search, ignoreCase = true)
                }
              }
            LazyColumn(
              modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              items(filtered, key = { it.packageName }) { appInfo ->
                val selected = config.filteredNotificationPackages.contains(appInfo.packageName)
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .minimumInteractiveComponentSize()
                      .clip(RoundedCornerShape(12.dp))
                      .background(if (selected) primaryColor.copy(alpha = 0.16f) else ObsidianElevated)
                      .clickable { viewModel.toggleFilteredNotificationPackage(appInfo.packageName) }
                      .padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = appInfo.label,
                      style = MaterialTheme.typography.bodyLarge,
                      color = TextPrimary,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = appInfo.packageName,
                      style = MaterialTheme.typography.labelSmall,
                      color = TextMuted,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                  if (selected) {
                    Icon(
                      imageVector = Icons.Rounded.CheckCircle,
                      contentDescription = "Selected",
                      tint = primaryColor,
                      modifier = Modifier.clip(CircleShape),
                    )
                  }
                }
              }
            }
          }
        }
      },
      dismissButton = {
        TextButton(onClick = { viewModel.clearFilteredNotificationPackages() }) {
          Text("Clear", color = TextSecondary)
        }
      },
      confirmButton = {
        TextButton(onClick = { showNotificationFilterPicker = false }) {
          Text("Done", color = primaryColor)
        }
      },
    )
  }
}

@Composable
private fun PermissionBridgeRow(
  title: String,
  description: String,
  icon: ImageVector,
  granted: Boolean,
  actionLabel: String,
  testTag: String,
  onAction: () -> Unit,
) {
  val primary = MaterialTheme.colorScheme.primary
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(ObsidianElevated)
        .border(
          1.dp,
          if (granted) StatusSuccess.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
          RoundedCornerShape(16.dp),
        )
        .padding(12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      modifier =
        Modifier.size(40.dp)
          .clip(CircleShape)
          .background((if (granted) StatusSuccess else primary).copy(alpha = 0.16f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (granted) StatusSuccess else primary,
      )
    }

    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Icon(
          imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
          contentDescription = if (granted) "Granted" else "Setup needed",
          tint = if (granted) StatusSuccess else StatusWarning,
          modifier = Modifier.size(16.dp),
        )
      }
      Text(text = description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }

    if (!granted) {
      OutlinedButton(
        onClick = onAction,
        modifier = Modifier.minimumInteractiveComponentSize().testTag(testTag),
      ) {
        Text(actionLabel)
      }
    } else {
      Text(
        text = "Ready",
        style = MaterialTheme.typography.labelMedium,
        color = StatusSuccess,
        modifier = Modifier.padding(horizontal = 8.dp),
      )
    }
  }
}
