package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.model.NotchAppearance

@Composable
fun MatusNotchTheme(
  appearance: NotchAppearance = NotchAppearance(),
  content: @Composable () -> Unit,
) {
  val primary = Color(appearance.resolvePrimaryHex())
  val secondary = Color(appearance.resolveSecondaryHex())
  val colorScheme =
    darkColorScheme(
      primary = primary,
      onPrimary = NotchPitchBlack,
      primaryContainer = primary.copy(alpha = 0.16f),
      onPrimaryContainer = primary,
      secondary = secondary,
      onSecondary = TextPrimary,
      secondaryContainer = secondary.copy(alpha = 0.18f),
      onSecondaryContainer = secondary,
      tertiary = CyberMint,
      background = ObsidianVoid,
      onBackground = TextPrimary,
      surface = ObsidianSurface,
      onSurface = TextPrimary,
      surfaceVariant = ObsidianElevated,
      onSurfaceVariant = TextSecondary,
      outline = ObsidianCardBorder,
      outlineVariant = Color(0x14FFFFFF),
      error = StatusError,
    )

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
