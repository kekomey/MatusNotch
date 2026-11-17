package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.NotchConfig
import com.example.model.NotchRuntimeState
import com.example.service.NotchRuntimeCoordinator
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun StudioCard(
  title: String? = null,
  subtitle: String? = null,
  modifier: Modifier = Modifier,
  headerTrailing: (@Composable () -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(22.dp))
        .background(ObsidianSurface)
        .border(1.dp, ObsidianCardBorder, RoundedCornerShape(22.dp))
        .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (title != null) {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
          )
          headerTrailing?.invoke()
        }
        if (subtitle != null) {
          Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
      }
    }
    content()
  }
}

@Composable
fun LabeledSlider(
  label: String,
  value: Float,
  valueRange: ClosedFloatingPointRange<Float>,
  unitSuffix: String,
  testTag: String,
  onValueChange: (Float) -> Unit,
) {
  val primary = MaterialTheme.colorScheme.primary
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
      Text(
        text = "${value.roundToInt()}$unitSuffix",
        style = MaterialTheme.typography.labelMedium,
        color = primary,
      )
    }
    Slider(
      value = value,
      onValueChange = onValueChange,
      valueRange = valueRange,
      colors =
        SliderDefaults.colors(
          thumbColor = primary,
          activeTrackColor = primary,
          inactiveTrackColor = Color.White.copy(alpha = 0.14f),
        ),
      modifier = Modifier.testTag(testTag),
    )
  }
}

@Composable
fun StudioLiveNotchStage(
  config: NotchConfig,
  runtime: NotchRuntimeState,
  coordinator: NotchRuntimeCoordinator,
  onToggleOverlay: () -> Unit,
  onNavigateToWidgetsTab: () -> Unit,
  onOpenProPaywall: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val primaryColor = Color(config.appearance.resolvePrimaryHex())
  val secondaryColor = Color(config.appearance.resolveSecondaryHex())
  val isRunning = runtime.telemetry.isOverlayServiceRunning

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(26.dp))
        .background(
          Brush.verticalGradient(
            listOf(
              primaryColor.copy(alpha = 0.12f),
              ObsidianSurface,
            )
          )
        )
        .border(1.dp, ObsidianCardBorder, RoundedCornerShape(26.dp))
        .padding(horizontal = 14.dp, vertical = 12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    // Top studio header & non-clipping overlay Launch/Active pill
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        modifier = Modifier.weight(1f).padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Box(
          modifier =
            Modifier.size(10.dp)
              .clip(CircleShape)
              .background(if (isRunning) StatusSuccess else TextMuted)
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "MatusNotch Studio",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text =
              if (isRunning) "Overlay live • Tap notch to test"
              else "Live Preview • Tap notch to expand",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      Row(
        modifier =
          Modifier.minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(50))
            .background(
              if (isRunning) StatusSuccess.copy(alpha = 0.18f)
              else primaryColor.copy(alpha = 0.18f)
            )
            .border(
              1.dp,
              if (isRunning) StatusSuccess.copy(alpha = 0.7f) else primaryColor.copy(alpha = 0.7f),
              RoundedCornerShape(50),
            )
            .clickable(onClick = onToggleOverlay)
            .testTag("studio_overlay_toggle")
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Icon(
          imageVector = if (isRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
          contentDescription = if (isRunning) "Stop floating notch" else "Launch floating notch",
          tint = if (isRunning) StatusSuccess else primaryColor,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = if (isRunning) "Active" else "Launch",
          style = MaterialTheme.typography.labelLarge,
          color = if (isRunning) StatusSuccess else primaryColor,
          maxLines = 1,
          softWrap = false,
        )
      }
    }

    // Simulated top bezel frame showcasing live DynamicNotchSurface with clean side border gap
    BoxWithConstraints(
      modifier =
        Modifier.fillMaxWidth()
          .heightIn(min = 104.dp)
          .clip(RoundedCornerShape(22.dp))
          .background(
            Brush.radialGradient(
              colors = listOf(secondaryColor.copy(alpha = 0.12f), Color(0xFF04060A))
            )
          )
          .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
          .padding(horizontal = 10.dp, vertical = 8.dp),
      contentAlignment = Alignment.TopCenter,
    ) {
      val previewExpandedWidthDp = (maxWidth.value - 12f).coerceIn(250f, 348f)

      // Simulated subtle phone status bar corners behind the compact notch
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "9:41",
          style = MaterialTheme.typography.labelMedium,
          color = TextMuted.copy(alpha = 0.75f),
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Layers,
            contentDescription = null,
            tint = TextMuted.copy(alpha = 0.7f),
            modifier = Modifier.size(13.dp),
          )
          Text(
            text = "${runtime.telemetry.batteryPercent}%",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted.copy(alpha = 0.75f),
          )
        }
      }

      DynamicNotchSurface(
        config = config,
        runtime = runtime,
        coordinator = coordinator,
        expandedWidthDp = previewExpandedWidthDp,
        isInStudioPreview = true,
        onNavigateToWidgetsTab = onNavigateToWidgetsTab,
        onOpenProPaywall = onOpenProPaywall,
      )
    }
  }
}
