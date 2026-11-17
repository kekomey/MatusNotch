package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CalculatorState
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CalculatorWidgetCard(
  calculator: CalculatorState,
  primaryColor: Color,
  secondaryColor: Color,
  onKey: (String) -> Unit,
  onCopy: () -> Unit,
) {
  val haptic = LocalHapticFeedback.current

  Row(
    modifier =
      Modifier.fillMaxSize()
        .clip(RoundedCornerShape(18.dp))
        .background(ObsidianElevated.copy(alpha = 0.72f))
        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
        .padding(7.dp)
        .testTag("widget_calculator_card"),
    horizontalArrangement = Arrangement.spacedBy(7.dp),
  ) {
    // Left Readout & Utility Column
    Column(
      modifier = Modifier.weight(0.39f).fillMaxHeight(),
      verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Column(
        modifier =
          Modifier.weight(1f)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianSurface.copy(alpha = 0.85f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End,
      ) {
        Text(
          text = calculator.expression.ifBlank { "Ready" },
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          color = TextMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.End,
          modifier = Modifier.fillMaxWidth(),
        )
        Text(
          text = calculator.display,
          style =
            MaterialTheme.typography.titleMedium.copy(
              fontSize = if (calculator.display.length > 9) 14.sp else 18.sp,
              fontWeight = FontWeight.Bold,
              fontFeatureSettings = "tnum",
            ),
          color = if (calculator.hasError) Color(0xFFFF4D6D) else TextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.End,
          modifier = Modifier.fillMaxWidth().testTag("calc_display"),
        )
      }

      // Utility actions: Copy, AC, %, Backspace
      Row(
        modifier = Modifier.fillMaxWidth().weight(0.42f),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Box(
          modifier =
            Modifier.weight(1f)
              .fillMaxHeight()
              .clip(RoundedCornerShape(9.dp))
              .background(Color.White.copy(alpha = 0.07f))
              .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCopy()
              }
              .testTag("calc_key_copy"),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Rounded.ContentCopy,
            contentDescription = "Copy result",
            tint = TextSecondary,
            modifier = Modifier.size(13.dp),
          )
        }

        Box(
          modifier =
            Modifier.weight(1f)
              .fillMaxHeight()
              .clip(RoundedCornerShape(9.dp))
              .background(Color(0xFFFF4D6D).copy(alpha = 0.16f))
              .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onKey("AC")
              }
              .testTag("calc_key_ac"),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "AC",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFFFF6B86),
          )
        }

        Box(
          modifier =
            Modifier.weight(1f)
              .fillMaxHeight()
              .clip(RoundedCornerShape(9.dp))
              .background(primaryColor.copy(alpha = 0.14f))
              .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onKey("%")
              }
              .testTag("calc_key_pct"),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "%",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = primaryColor,
          )
        }

        Box(
          modifier =
            Modifier.weight(1f)
              .fillMaxHeight()
              .clip(RoundedCornerShape(9.dp))
              .background(Color.White.copy(alpha = 0.07f))
              .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onKey("DEL")
              }
              .testTag("calc_key_del"),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.Backspace,
            contentDescription = "Backspace",
            tint = TextPrimary,
            modifier = Modifier.size(13.dp),
          )
        }
      }
    }

    // Right 4x4 Numeric & Operator Keypad
    Column(
      modifier = Modifier.weight(0.61f).fillMaxHeight(),
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      val rows =
        listOf(
          listOf("7", "8", "9", "÷"),
          listOf("4", "5", "6", "×"),
          listOf("1", "2", "3", "-"),
          listOf("0", ".", "=", "+"),
        )
      rows.forEach { rowKeys ->
        Row(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          rowKeys.forEach { key ->
            val isOp = key in listOf("÷", "×", "-", "+")
            val isEquals = key == "="
            val tagKey =
              when (key) {
                "+" -> "plus"
                "-" -> "minus"
                "×" -> "mul"
                "÷" -> "div"
                "=" -> "eq"
                "." -> "dot"
                else -> key
              }
            Box(
              modifier =
                Modifier.weight(1f)
                  .fillMaxHeight()
                  .clip(RoundedCornerShape(8.dp))
                  .then(
                    when {
                      isEquals ->
                        Modifier.background(
                          Brush.linearGradient(listOf(primaryColor, secondaryColor))
                        )
                      isOp ->
                        Modifier.background(primaryColor.copy(alpha = 0.18f))
                          .border(0.75.dp, primaryColor.copy(alpha = 0.42f), RoundedCornerShape(8.dp))
                      else ->
                        Modifier.background(Color.White.copy(alpha = 0.08f))
                    }
                  )
                  .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onKey(key)
                  }
                  .testTag("calc_key_$tagKey"),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = if (key == "-") "−" else key,
                style =
                  MaterialTheme.typography.labelMedium.copy(
                    fontSize = if (isOp || isEquals) 14.sp else 13.sp,
                    fontWeight = if (isOp || isEquals) FontWeight.Bold else FontWeight.SemiBold,
                  ),
                color =
                  when {
                    isEquals -> Color.Black
                    isOp -> primaryColor
                    else -> TextPrimary
                  },
              )
            }
          }
        }
      }
    }
  }
}
