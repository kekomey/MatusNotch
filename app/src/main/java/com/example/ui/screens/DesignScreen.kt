package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.AccentPreset
import com.example.model.EntitlementResolver
import com.example.model.NotchBorderStyle
import com.example.model.NotchConfig
import com.example.model.NotchGlowStyle
import com.example.ui.StudioViewModel
import com.example.ui.components.ColorWheelPicker
import com.example.ui.components.LabeledSlider
import com.example.ui.components.ProLockBadge
import com.example.ui.components.StudioCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DesignScreen(
  config: NotchConfig,
  isProUnlocked: Boolean,
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier,
) {
  val activePrimary = Color(config.appearance.resolvePrimaryHex())
  val activeSecondary = Color(config.appearance.resolveSecondaryHex())

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // Accent Palette & Notification Pulse Preview
    StudioCard(
      title = "OLED Accent & Surface",
      subtitle = "Pick a built-in accent or unlock the Pro HSV color wheel & neon animations",
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        AccentPreset.entries.forEach { preset ->
          val selected = config.appearance.accentPreset == preset
          val isCustomWheel = preset == AccentPreset.CUSTOM
          val isLocked = EntitlementResolver.isProAccent(preset) && !isProUnlocked
          val pColor =
            if (isCustomWheel) Color(config.appearance.resolvePrimaryHex()) else Color(preset.primaryHex)
          val sColor =
            if (isCustomWheel) Color(config.appearance.resolveSecondaryHex()) else Color(preset.secondaryHex)

          val swatchBrush =
            if (isCustomWheel) {
              Brush.sweepGradient(
                listOf(
                  Color(0xFFFF3B30),
                  Color(0xFFFFCC00),
                  Color(0xFF34C759),
                  Color(0xFF00E5FF),
                  Color(0xFFA855F7),
                  Color(0xFFFF2A6D),
                  Color(0xFFFF3B30),
                )
              )
            } else {
              Brush.linearGradient(listOf(pColor, sColor))
            }

          Column(
            modifier =
              Modifier.clip(RoundedCornerShape(16.dp))
                .background(
                  if (selected) pColor.copy(alpha = 0.16f) else ObsidianElevated
                )
                .border(
                  width = if (selected) 1.5.dp else 1.dp,
                  color = if (selected) pColor else Color.White.copy(alpha = 0.08f),
                  shape = RoundedCornerShape(16.dp),
                )
                .clickable { viewModel.setAccentPreset(preset) }
                .testTag("accent_preset_${preset.name.lowercase()}")
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Box(
              modifier = Modifier.size(30.dp).clip(CircleShape).background(swatchBrush),
              contentAlignment = Alignment.Center,
            ) {
              when {
                selected -> {
                  Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "${preset.label} selected",
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp),
                  )
                }
                isLocked -> {
                  Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = "Matus Pro color wheel",
                    tint = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.size(14.dp),
                  )
                }
                isCustomWheel -> {
                  Icon(
                    imageVector = Icons.Rounded.WorkspacePremium,
                    contentDescription = "Matus Pro color wheel",
                    tint = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.size(14.dp),
                  )
                }
              }
            }
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Text(
                text = if (isCustomWheel) "Wheel" else preset.label.substringBefore(" "),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) pColor else TextSecondary,
              )
              if (isCustomWheel) {
                ProLockBadge(locked = isLocked)
              }
            }
          }
        }
      }

      AnimatedVisibility(
        visible = config.appearance.accentPreset == AccentPreset.CUSTOM,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
      ) {
        ColorWheelPicker(
          selectedHex = config.appearance.customAccentHex,
          onColorChanged = { hex -> viewModel.setCustomAccentHex(hex) },
        )
      }

      LabeledSlider(
        label = "Pill Opacity",
        value = config.appearance.pillOpacity * 100f,
        valueRange = 70f..100f,
        unitSuffix = "%",
        testTag = "slider_pill_opacity",
        onValueChange = { viewModel.setPillOpacity(it / 100f) },
      )

      Text(
        text = "Notch Border Style",
        style = MaterialTheme.typography.labelLarge,
        color = TextPrimary,
      )
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        NotchBorderStyle.entries.forEach { style ->
          val selected = config.appearance.borderStyle == style
          val isProStyle = EntitlementResolver.isProBorder(style)
          val locked = isProStyle && !isProUnlocked
          FilterChip(
            selected = selected,
            onClick = { viewModel.setBorderStyle(style) },
            label = {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
              ) {
                Text(style.label)
                if (isProStyle) {
                  ProLockBadge(locked = locked)
                }
              }
            },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = activePrimary.copy(alpha = 0.2f),
                selectedLabelColor = activePrimary,
              ),
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .testTag("border_style_${style.name.lowercase()}"),
          )
        }
      }

      Text(
        text = "Glow Effect Style",
        style = MaterialTheme.typography.labelLarge,
        color = TextPrimary,
      )
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        NotchGlowStyle.entries.forEach { style ->
          val selected = config.appearance.glowStyle == style
          val isProStyle = EntitlementResolver.isProGlow(style)
          val locked = isProStyle && !isProUnlocked
          FilterChip(
            selected = selected,
            onClick = { viewModel.setGlowStyle(style) },
            label = {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
              ) {
                Text(style.label)
                if (isProStyle) {
                  ProLockBadge(locked = locked)
                }
              }
            },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = activePrimary.copy(alpha = 0.2f),
                selectedLabelColor = activePrimary,
              ),
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .testTag("glow_style_${style.name.lowercase()}"),
          )
        }
      }

      OutlinedButton(
        onClick = { viewModel.coordinator.triggerNotificationPulse() },
        modifier =
          Modifier.fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("btn_preview_notification_spin"),
      ) {
        Icon(
          imageVector = Icons.Rounded.AutoAwesome,
          contentDescription = null,
          tint = activeSecondary,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text("Test Notification & Glow")
      }
    }

    // Compact Geometry & Cutout Alignment (Expanded notch width/height auto-fits screen with side gap)
    StudioCard(
      title = "Compact Cutout Alignment",
      subtitle = "Align the compact pill over your camera cutout; expanded size auto-fits your screen cleanly",
    ) {
      LabeledSlider(
        label = "Compact Width",
        value = config.geometry.compactWidthDp,
        valueRange = 110f..240f,
        unitSuffix = "dp",
        testTag = "slider_compact_width",
        onValueChange = { w -> viewModel.updateGeometry { it.copy(compactWidthDp = w) } },
      )
      LabeledSlider(
        label = "Compact Height",
        value = config.geometry.compactHeightDp,
        valueRange = 28f..52f,
        unitSuffix = "dp",
        testTag = "slider_compact_height",
        onValueChange = { h -> viewModel.updateGeometry { it.copy(compactHeightDp = h) } },
      )
      LabeledSlider(
        label = "Corner Radius",
        value = config.geometry.cornerRadiusDp,
        valueRange = 12f..32f,
        unitSuffix = "dp",
        testTag = "slider_corner_radius",
        onValueChange = { r -> viewModel.updateGeometry { it.copy(cornerRadiusDp = r) } },
      )
      LabeledSlider(
        label = "Horizontal Offset (X)",
        value = config.geometry.offsetXDp,
        valueRange = -80f..80f,
        unitSuffix = "dp",
        testTag = "slider_offset_x",
        onValueChange = { x -> viewModel.updateGeometry { it.copy(offsetXDp = x) } },
      )
      LabeledSlider(
        label = "Top Offset (Y)",
        value = config.geometry.offsetYDp,
        valueRange = 0f..56f,
        unitSuffix = "dp",
        testTag = "slider_offset_y",
        onValueChange = { y -> viewModel.updateGeometry { it.copy(offsetYDp = y) } },
      )

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(
          onClick = { viewModel.resetGeometry() },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("btn_reset_geometry"),
        ) {
          Icon(
            imageVector = Icons.Rounded.RestartAlt,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.size(6.dp))
          Text("Reset Geometry")
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))
  }
}
