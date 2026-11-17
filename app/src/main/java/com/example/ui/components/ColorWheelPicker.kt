package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.model.deriveCompanionAccentHex
import com.example.model.hsvToArgbHex
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun ColorWheelPicker(
  selectedHex: Long,
  onColorChanged: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val initialHsv = remember(selectedHex) { argbHexToHsv(selectedHex) }
  var hue by remember(selectedHex) { mutableFloatStateOf(initialHsv[0]) }
  var saturation by remember(selectedHex) { mutableFloatStateOf(initialHsv[1]) }
  var brightness by remember(selectedHex) { mutableFloatStateOf(initialHsv[2].coerceIn(0.45f, 1f)) }
  var wheelSize by remember { mutableFloatStateOf(0f) }

  val primaryColor = Color(hsvToArgbHex(hue, saturation, brightness))
  val companionColor = Color(deriveCompanionAccentHex(hsvToArgbHex(hue, saturation, brightness)))
  val hexLabel = remember(hue, saturation, brightness) {
    val rgb = hsvToArgbHex(hue, saturation, brightness) and 0xFFFFFFL
    "#%06X".format(rgb)
  }

  val hueSweepColors = remember {
    listOf(
      Color(0xFFFF0000),
      Color(0xFFFFFF00),
      Color(0xFF00FF00),
      Color(0xFF00FFFF),
      Color(0xFF0000FF),
      Color(0xFFFF00FF),
      Color(0xFFFF0000),
    )
  }

  fun pickFromOffset(offset: Offset, size: IntSize) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = min(cx, cy).coerceAtLeast(1f)
    val dx = offset.x - cx
    val dy = offset.y - cy
    val dist = hypot(dx, dy).coerceAtMost(radius)
    val angleRad = atan2(dy, dx)
    var angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()
    if (angleDeg < 0f) angleDeg += 360f
    hue = angleDeg
    saturation = (dist / radius).coerceIn(0.12f, 1f)
    onColorChanged(hsvToArgbHex(hue, saturation, brightness))
  }

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(ObsidianElevated)
        .border(1.dp, primaryColor.copy(alpha = 0.32f), RoundedCornerShape(18.dp))
        .padding(14.dp)
        .testTag("custom_color_wheel_panel"),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Canvas(
        modifier =
          Modifier.size(144.dp)
            .onSizeChanged { sz -> wheelSize = min(sz.width, sz.height).toFloat() }
            .pointerInput(Unit) {
              detectTapGestures { pos -> pickFromOffset(pos, size) }
            }
            .pointerInput(Unit) {
              detectDragGestures { change, _ ->
                change.consume()
                pickFromOffset(change.position, size)
              }
            }
            .testTag("custom_color_wheel_canvas")
      ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f

        // Hue sweep ring & saturation gradient to luminous white center
        drawCircle(
          brush = Brush.sweepGradient(hueSweepColors, center = center),
          radius = radius,
          center = center,
        )
        drawCircle(
          brush =
            Brush.radialGradient(
              colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
              center = center,
              radius = radius,
            ),
          radius = radius,
          center = center,
        )
        // Brightness veil for accurate live visual feedback
        if (brightness < 0.99f) {
          drawCircle(
            color = Color.Black.copy(alpha = (1f - brightness).coerceIn(0f, 0.55f)),
            radius = radius,
            center = center,
          )
        }
        drawCircle(
          color = Color.White.copy(alpha = 0.16f),
          radius = radius,
          center = center,
          style = Stroke(width = 1.5.dp.toPx()),
        )

        // Draggable thumb
        val rad = hue * (PI.toFloat() / 180f)
        val thumbDist = saturation * radius
        val thumbPos =
          Offset(
            x = center.x + cos(rad) * thumbDist,
            y = center.y + sin(rad) * thumbDist,
          )
        drawCircle(
          color = Color.Black.copy(alpha = 0.55f),
          radius = 11.dp.toPx(),
          center = thumbPos,
        )
        drawCircle(
          color = primaryColor,
          radius = 9.dp.toPx(),
          center = thumbPos,
        )
        drawCircle(
          color = Color.White,
          radius = 9.dp.toPx(),
          center = thumbPos,
          style = Stroke(width = 2.dp.toPx()),
        )
      }

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = "Custom Color Wheel",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = TextPrimary,
        )
        Text(
          text = "Drag around the wheel for any custom OLED primary & companion glow",
          style = MaterialTheme.typography.bodySmall,
          color = TextSecondary,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Box(
            modifier =
              Modifier.size(28.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(primaryColor, companionColor)))
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
          )
          Text(
            text = hexLabel,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = primaryColor,
            modifier = Modifier.testTag("custom_color_hex_label"),
          )
        }
      }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(text = "Glow Brightness", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Text(
          text = "${(brightness * 100f).roundToInt()}%",
          style = MaterialTheme.typography.labelMedium,
          color = primaryColor,
        )
      }
      Slider(
        value = brightness,
        onValueChange = { next ->
          brightness = next.coerceIn(0.45f, 1f)
          onColorChanged(hsvToArgbHex(hue, saturation, brightness))
        },
        valueRange = 0.45f..1f,
        colors =
          SliderDefaults.colors(
            thumbColor = primaryColor,
            activeTrackColor = primaryColor,
            inactiveTrackColor = Color.White.copy(alpha = 0.14f),
          ),
        modifier = Modifier.testTag("custom_color_brightness_slider"),
      )
      Text(
        text = "Drag anywhere inside the wheel for instant hue & saturation",
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted,
      )
    }
  }
}

private fun argbHexToHsv(argbHex: Long): FloatArray {
  val r = ((argbHex shr 16) and 0xFF) / 255f
  val g = ((argbHex shr 8) and 0xFF) / 255f
  val b = (argbHex and 0xFF) / 255f

  val maxC = maxOf(r, g, b)
  val minC = minOf(r, g, b)
  val delta = maxC - minC

  var hue =
    when {
      delta <= 0.0001f -> 190f
      maxC == r -> 60f * (((g - b) / delta) % 6f)
      maxC == g -> 60f * (((b - r) / delta) + 2f)
      else -> 60f * (((r - g) / delta) + 4f)
    }
  if (hue < 0f) hue += 360f
  val sat = if (maxC <= 0.0001f) 0.85f else (delta / maxC).coerceIn(0.12f, 1f)
  val valC = maxC.coerceIn(0.45f, 1f)
  return floatArrayOf(hue, sat, valC)
}
