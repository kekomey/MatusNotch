package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.EntitlementResolver
import com.example.model.NotchConfig
import com.example.model.NotchWidgetType
import com.example.system.SystemBridges
import com.example.ui.StudioViewModel
import com.example.ui.components.ProLockBadge
import com.example.ui.components.StudioCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun WidgetsScreen(
  config: NotchConfig,
  isProUnlocked: Boolean,
  previewWidgetType: NotchWidgetType?,
  onOpenPaywall: () -> Unit,
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val primaryColor = MaterialTheme.colorScheme.primary
  var showAppPicker by remember { mutableStateOf(false) }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    StudioCard(
      title = "Swipeable Widget Deck",
      subtitle = "Enable widgets and reorder how they appear when you swipe left or right",
    ) {
      config.widgetSlots.forEachIndexed { index, slot ->
        val isProWidget = EntitlementResolver.isProWidget(slot.type)
        val isLocked = isProWidget && !isProUnlocked
        val isPreviewing = isLocked && previewWidgetType == slot.type
        val displayActive = slot.enabled || isPreviewing

        val icon =
          when (slot.type) {
            NotchWidgetType.MEDIA -> Icons.Rounded.MusicNote
            NotchWidgetType.QUICK_TOGGLES -> Icons.Rounded.Tune
            NotchWidgetType.DEVICE_ACTIONS -> Icons.Rounded.Smartphone
            NotchWidgetType.CALCULATOR -> Icons.Rounded.Calculate
            NotchWidgetType.FOCUS_TIMER -> Icons.Rounded.Timer
            NotchWidgetType.APP_SHORTCUTS -> Icons.Rounded.Apps
          }
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(ObsidianElevated)
              .border(
                1.dp,
                if (displayActive) primaryColor.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f),
                RoundedCornerShape(16.dp),
              )
              .clickable(enabled = isLocked) { viewModel.toggleWidgetEnabled(slot.type) }
              .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Box(
            modifier =
              Modifier.size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(primaryColor.copy(alpha = if (displayActive) 0.16f else 0.06f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = if (displayActive) primaryColor else TextMuted,
            )
          }

          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = slot.type.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (displayActive) TextPrimary else TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
              )
              if (isProWidget) {
                ProLockBadge(
                  label = if (isPreviewing) "PREVIEW" else "PRO",
                  locked = isLocked,
                  modifier = if (isLocked) Modifier.clickable { onOpenPaywall() } else Modifier,
                )
              }
            }
            Text(
              text =
                if (isLocked) "Tap to preview in Studio notch • Unlocks with Matus Pro"
                else slot.type.subtitle,
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          IconButton(
            onClick = { viewModel.moveWidget(slot.type, -1) },
            enabled = index > 0,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("widget_move_up_${slot.type.name.lowercase()}"),
          ) {
            Icon(
              imageVector = Icons.Rounded.ArrowUpward,
              contentDescription = "Move ${slot.type.title} up",
              tint = if (index > 0) TextPrimary else TextMuted.copy(alpha = 0.4f),
            )
          }

          IconButton(
            onClick = { viewModel.moveWidget(slot.type, 1) },
            enabled = index < config.widgetSlots.lastIndex,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("widget_move_down_${slot.type.name.lowercase()}"),
          ) {
            Icon(
              imageVector = Icons.Rounded.ArrowDownward,
              contentDescription = "Move ${slot.type.title} down",
              tint =
                if (index < config.widgetSlots.lastIndex) TextPrimary
                else TextMuted.copy(alpha = 0.4f),
            )
          }

          Switch(
            checked = displayActive,
            onCheckedChange = { viewModel.toggleWidgetEnabled(slot.type) },
            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = primaryColor),
            modifier = Modifier.testTag("widget_switch_${slot.type.name.lowercase()}"),
          )
        }
      }
    }

    StudioCard(
      title = "Pinned App Shortcuts (${config.pinnedPackages.size}/4)",
      subtitle = "Choose up to 4 apps to launch straight from the Apps & Power widget",
    ) {
      if (config.pinnedPackages.isEmpty()) {
        Text(
          text = "No apps pinned yet. Tap below to pick your favorite apps.",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
        )
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          config.pinnedPackages.forEach { pkg ->
            val label = remember(pkg) { SystemBridges.loadAppLabel(context, pkg) }
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .background(ObsidianElevated)
                  .padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                  text = pkg,
                  style = MaterialTheme.typography.labelSmall,
                  color = TextMuted,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              IconButton(
                onClick = { viewModel.togglePinnedApp(pkg) },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("remove_pinned_$pkg"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Close,
                  contentDescription = "Unpin $label",
                  tint = TextSecondary,
                )
              }
            }
          }
        }
      }

      OutlinedButton(
        onClick = {
          viewModel.loadInstalledApps()
          showAppPicker = true
        },
        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("btn_pick_pinned_apps"),
      ) {
        Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
        Spacer(modifier = Modifier.size(8.dp))
        Text("Select Pinned Apps")
      }
    }

    StudioCard(
      title = "Mascot Auto-Sleep Delay",
      subtitle = "How soon the mascot drifts to sleep when idle",
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        listOf(10, 15, 30, 60).forEach { seconds ->
          val selected = config.autoSleepSeconds == seconds
          FilterChip(
            selected = selected,
            onClick = { viewModel.setAutoSleepSeconds(seconds) },
            label = { Text("${seconds}s") },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = primaryColor.copy(alpha = 0.2f),
                selectedLabelColor = primaryColor,
              ),
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("autosleep_${seconds}s"),
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))
  }

  if (showAppPicker) {
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingApps.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }

    AlertDialog(
      onDismissRequest = { showAppPicker = false },
      containerColor = ObsidianSurface,
      title = {
        Text("Pin Launcher Apps (${config.pinnedPackages.size}/4)", color = TextPrimary)
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search installed apps...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("search_apps_input"),
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
                val pinned = config.pinnedPackages.contains(appInfo.packageName)
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .minimumInteractiveComponentSize()
                      .clip(RoundedCornerShape(12.dp))
                      .background(if (pinned) primaryColor.copy(alpha = 0.16f) else ObsidianElevated)
                      .clickable { viewModel.togglePinnedApp(appInfo.packageName) }
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
                  if (pinned) {
                    Icon(
                      imageVector = Icons.Rounded.CheckCircle,
                      contentDescription = "Pinned",
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
      confirmButton = {
        TextButton(onClick = { showAppPicker = false }) { Text("Done", color = primaryColor) }
      },
    )
  }
}
