package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import com.example.model.NotchGlowStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

internal const val NOTCH_TRACK_SAMPLES = 96
internal const val LED_TRAIL_STEPS = 44
internal const val COMET_TRAIL_STEPS = 32

private val PRISM_RAINBOW_ANCHORS =
  arrayOf(
    Color(0xFFFF3366),
    Color(0xFFFFB703),
    Color(0xFF06D6A0),
    Color(0xFF00F0FF),
    Color(0xFF4361EE),
    Color(0xFFB5179E),
  )

internal fun samplePrismColor(normalizedPhase: Float): Color {
  val wrapped = ((normalizedPhase % 1f) + 1f) % 1f
  val scaled = wrapped * PRISM_RAINBOW_ANCHORS.size
  val idx0 = scaled.toInt() % PRISM_RAINBOW_ANCHORS.size
  val idx1 = (idx0 + 1) % PRISM_RAINBOW_ANCHORS.size
  val frac = scaled - scaled.toInt()
  return lerp(PRISM_RAINBOW_ANCHORS[idx0], PRISM_RAINBOW_ANCHORS[idx1], frac)
}

internal class GlowStrokeWidths(
  val outerMinPx: Float,
  val outerMaxPx: Float,
  val midMinPx: Float,
  val midMaxPx: Float,
  val coreMinPx: Float,
  val coreMaxPx: Float,
  val headOuterHaloPx: Float,
  val headMidHaloPx: Float,
  val headCorePx: Float,
  val prismBaseColors: MutableList<Color> = MutableList(7) { Color.Transparent },
  val prismCoreColors: MutableList<Color> = MutableList(7) { Color.Transparent },
)

internal fun DrawScope.drawPerimeterGlow(
  style: NotchGlowStyle,
  progress: Float,
  cornerPx: Float,
  ptsX: FloatArray?,
  ptsY: FloatArray?,
  primaryColor: Color,
  secondaryColor: Color,
  widths: GlowStrokeWidths,
  ambientProgress: Float = 0f,
  isAmbientActive: Boolean = false,
) {
  val isNotifActive = progress > 0f && progress < 1f

  if (isNotifActive) {
    val envelope =
      when {
        progress < 0.06f -> progress / 0.06f
        progress > 0.84f -> (1f - progress) / 0.16f
        else -> 1f
      }.coerceIn(0f, 1f)

    if (envelope <= 0.005f) return

    when (style) {
      NotchGlowStyle.LED_RACE -> {
        if (ptsX != null && ptsY != null) {
          drawCometTrail(
            headFloat = (progress * 2f * NOTCH_TRACK_SAMPLES) % NOTCH_TRACK_SAMPLES,
            trailSteps = LED_TRAIL_STEPS,
            envelope = envelope,
            ptsX = ptsX,
            ptsY = ptsY,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            widths = widths,
          )
        }
      }

      NotchGlowStyle.DUAL_COMET -> {
        if (ptsX != null && ptsY != null) {
          val headA = (progress * 2f * NOTCH_TRACK_SAMPLES) % NOTCH_TRACK_SAMPLES
          val headB = (headA + NOTCH_TRACK_SAMPLES / 2f) % NOTCH_TRACK_SAMPLES
          drawCometTrail(
            headFloat = headA,
            trailSteps = COMET_TRAIL_STEPS,
            envelope = envelope,
            ptsX = ptsX,
            ptsY = ptsY,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            widths = widths,
          )
          drawCometTrail(
            headFloat = headB,
            trailSteps = COMET_TRAIL_STEPS,
            envelope = envelope,
            ptsX = ptsX,
            ptsY = ptsY,
            primaryColor = secondaryColor,
            secondaryColor = primaryColor,
            widths = widths,
          )
        }
      }

      NotchGlowStyle.BREATHING_AURA -> {
        val pulse = (0.45f + 0.55f * (0.5f - 0.5f * cos(progress * 6f * PI).toFloat())).coerceIn(0f, 1f)
        drawUniformPerimeterAura(
          cornerPx = cornerPx,
          intensity = envelope * pulse,
          primaryColor = primaryColor,
          secondaryColor = secondaryColor,
          widths = widths,
        )
      }

      NotchGlowStyle.PRISM_WAVE -> {
        drawPrismPerimeterWave(
          cornerPx = cornerPx,
          phase = progress * 2.5f,
          envelope = envelope,
          widths = widths,
        )
      }
    }
  } else if (isAmbientActive) {
    when (style) {
      NotchGlowStyle.BREATHING_AURA -> {
        val breath = 0.20f + 0.22f * (0.5f + 0.5f * sin(ambientProgress * 2f * PI).toFloat())
        drawUniformPerimeterAura(
          cornerPx = cornerPx,
          intensity = breath,
          primaryColor = primaryColor,
          secondaryColor = secondaryColor,
          widths = widths,
        )
      }

      NotchGlowStyle.PRISM_WAVE -> {
        drawPrismPerimeterWave(
          cornerPx = cornerPx,
          phase = ambientProgress,
          envelope = 0.34f,
          widths = widths,
        )
      }

      else -> Unit
    }
  }
}

private fun DrawScope.drawCometTrail(
  headFloat: Float,
  trailSteps: Int,
  envelope: Float,
  ptsX: FloatArray,
  ptsY: FloatArray,
  primaryColor: Color,
  secondaryColor: Color,
  widths: GlowStrokeWidths,
) {
  val headIdx = headFloat.toInt() % NOTCH_TRACK_SAMPLES
  val tailStartIdx = headIdx - trailSteps

  for (step in 0 until trailSteps) {
    val t = step.toFloat() / (trailSteps - 1).coerceAtLeast(1)
    val falloff = t * t * (0.35f + 0.65f * t)
    if (falloff <= 0.005f) continue

    val i0 = ((tailStartIdx + step) % NOTCH_TRACK_SAMPLES + NOTCH_TRACK_SAMPLES) % NOTCH_TRACK_SAMPLES
    val i1 = (i0 + 1) % NOTCH_TRACK_SAMPLES
    val p0 = Offset(ptsX[i0], ptsY[i0])
    val p1 = Offset(ptsX[i1], ptsY[i1])
    val stripColor = lerp(secondaryColor, primaryColor, t)

    drawLine(
      color = stripColor,
      start = p0,
      end = p1,
      strokeWidth = widths.outerMinPx + (widths.outerMaxPx - widths.outerMinPx) * falloff,
      cap = StrokeCap.Round,
      alpha = envelope * falloff * 0.24f,
    )
    drawLine(
      color = stripColor,
      start = p0,
      end = p1,
      strokeWidth = widths.midMinPx + (widths.midMaxPx - widths.midMinPx) * falloff,
      cap = StrokeCap.Round,
      alpha = envelope * falloff * 0.85f,
    )
    if (t > 0.35f) {
      val coreT = ((t - 0.35f) / 0.65f).pow(1.45f)
      val coreColor = lerp(primaryColor, Color.White, coreT * 0.85f)
      drawLine(
        color = coreColor,
        start = p0,
        end = p1,
        strokeWidth = widths.coreMinPx + (widths.coreMaxPx - widths.coreMinPx) * coreT,
        cap = StrokeCap.Round,
        alpha = envelope * coreT * 0.95f,
      )
    }
  }

  val nextIdx = (headIdx + 1) % NOTCH_TRACK_SAMPLES
  val frac = headFloat - headFloat.toInt()
  val headCenter =
    Offset(
      x = ptsX[headIdx] + (ptsX[nextIdx] - ptsX[headIdx]) * frac,
      y = ptsY[headIdx] + (ptsY[nextIdx] - ptsY[headIdx]) * frac,
    )
  drawCircle(
    color = primaryColor,
    radius = widths.headOuterHaloPx,
    center = headCenter,
    alpha = envelope * 0.28f,
  )
  drawCircle(
    color = primaryColor,
    radius = widths.headMidHaloPx,
    center = headCenter,
    alpha = envelope * 0.62f,
  )
  drawCircle(
    color = Color.White,
    radius = widths.headCorePx,
    center = headCenter,
    alpha = envelope * 0.96f,
  )
}

private fun DrawScope.drawUniformPerimeterAura(
  cornerPx: Float,
  intensity: Float,
  primaryColor: Color,
  secondaryColor: Color,
  widths: GlowStrokeWidths,
) {
  val center = Offset(size.width / 2f, size.height / 2f)
  val cornerRadius = CornerRadius(cornerPx, cornerPx)
  val outerBrush =
    Brush.sweepGradient(
      colors = listOf(primaryColor, secondaryColor, primaryColor, secondaryColor, primaryColor),
      center = center,
    )
  val corePrimary = lerp(primaryColor, Color.White, 0.45f)
  val coreSecondary = lerp(secondaryColor, Color.White, 0.45f)
  val coreBrush =
    Brush.sweepGradient(
      colors = listOf(corePrimary, coreSecondary, corePrimary, coreSecondary, corePrimary),
      center = center,
    )

  drawRoundRect(
    brush = outerBrush,
    cornerRadius = cornerRadius,
    alpha = intensity * 0.26f,
    style = Stroke(width = widths.outerMaxPx * (0.75f + 0.25f * intensity), cap = StrokeCap.Round),
  )
  drawRoundRect(
    brush = outerBrush,
    cornerRadius = cornerRadius,
    alpha = intensity * 0.72f,
    style = Stroke(width = widths.midMaxPx * 0.8f, cap = StrokeCap.Round),
  )
  drawRoundRect(
    brush = coreBrush,
    cornerRadius = cornerRadius,
    alpha = intensity * 0.65f,
    style = Stroke(width = widths.coreMinPx * 1.3f, cap = StrokeCap.Round),
  )
}

private fun DrawScope.drawPrismPerimeterWave(
  cornerPx: Float,
  phase: Float,
  envelope: Float,
  widths: GlowStrokeWidths,
) {
  val center = Offset(size.width / 2f, size.height / 2f)
  val cornerRadius = CornerRadius(cornerPx, cornerPx)
  val baseColors = widths.prismBaseColors
  val coreColors = widths.prismCoreColors
  for (idx in 0 until 7) {
    val c = samplePrismColor(idx / 6f - phase)
    baseColors[idx] = c
    coreColors[idx] = lerp(c, Color.White, 0.35f)
  }
  val prismBrush = Brush.sweepGradient(colors = baseColors, center = center)
  val coreBrush = Brush.sweepGradient(colors = coreColors, center = center)

  drawRoundRect(
    brush = prismBrush,
    cornerRadius = cornerRadius,
    alpha = envelope * 0.24f,
    style = Stroke(width = widths.outerMaxPx * 0.85f, cap = StrokeCap.Round),
  )
  drawRoundRect(
    brush = prismBrush,
    cornerRadius = cornerRadius,
    alpha = envelope * 0.82f,
    style = Stroke(width = widths.midMaxPx * 0.88f, cap = StrokeCap.Round),
  )
  drawRoundRect(
    brush = coreBrush,
    cornerRadius = cornerRadius,
    alpha = envelope * 0.85f,
    style = Stroke(width = widths.coreMinPx * 1.4f, cap = StrokeCap.Round),
  )
}

private data class BurstParticleSpec(
  val angleRad: Float,
  val maxDistFactor: Float,
  val radiusFactor: Float,
  val isStar: Boolean,
  val useSecondary: Boolean,
)

@Composable
fun MascotParticleBurstOverlay(
  tapId: Long,
  primaryColor: Color,
  secondaryColor: Color,
  modifier: Modifier = Modifier,
) {
  val burstProgress = remember { Animatable(1f) }
  val reusableStarPath = remember { Path() }
  val particles = remember {
    listOf(
      BurstParticleSpec(-0.35f, 1.35f, 0.14f, isStar = true, useSecondary = false),
      BurstParticleSpec(0.28f, 1.45f, 0.12f, isStar = false, useSecondary = true),
      BurstParticleSpec(0.95f, 1.30f, 0.14f, isStar = true, useSecondary = false),
      BurstParticleSpec(1.62f, 1.25f, 0.11f, isStar = false, useSecondary = true),
      BurstParticleSpec(2.25f, 1.38f, 0.13f, isStar = true, useSecondary = true),
      BurstParticleSpec(2.90f, 1.28f, 0.11f, isStar = false, useSecondary = false),
      BurstParticleSpec(3.65f, 1.32f, 0.14f, isStar = true, useSecondary = false),
      BurstParticleSpec(4.45f, 1.25f, 0.11f, isStar = false, useSecondary = true),
      BurstParticleSpec(5.15f, 1.40f, 0.13f, isStar = true, useSecondary = true),
      BurstParticleSpec(5.75f, 1.22f, 0.10f, isStar = false, useSecondary = false),
    )
  }

  LaunchedEffect(tapId) {
    if (tapId > 0L) {
      burstProgress.snapTo(0f)
      burstProgress.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 680, easing = FastOutSlowInEasing),
      )
    }
  }

  if (tapId > 0L && burstProgress.value < 0.99f) {
    Canvas(
      modifier = modifier.graphicsLayer { clip = false },
    ) {
      val p = burstProgress.value
      val fade = (1f - p).pow(1.25f).coerceIn(0f, 1f)
      val baseRadius = size.minDimension * 0.52f
      val center = Offset(size.width / 2f, size.height / 2f)

      // Expanding soft neon shockwave ring
      drawCircle(
        color = primaryColor,
        radius = baseRadius * (0.65f + p * 0.95f),
        center = center,
        alpha = fade * 0.28f,
        style = Stroke(width = 2f * (1f - p * 0.5f)),
      )

      particles.forEach { spec ->
        val dist = baseRadius * (0.45f + p * spec.maxDistFactor)
        val cx = center.x + cos(spec.angleRad) * dist
        val cy = center.y + sin(spec.angleRad) * dist
        val particleColor = if (spec.useSecondary) secondaryColor else primaryColor
        val r = size.minDimension * spec.radiusFactor * (1f - p * 0.35f)

        if (spec.isStar) {
          reusableStarPath.apply {
            reset()
            moveTo(cx, cy - r * 1.45f)
            lineTo(cx + r * 0.42f, cy - r * 0.42f)
            lineTo(cx + r * 1.45f, cy)
            lineTo(cx + r * 0.42f, cy + r * 0.42f)
            lineTo(cx, cy + r * 1.45f)
            lineTo(cx - r * 0.42f, cy + r * 0.42f)
            lineTo(cx - r * 1.45f, cy)
            lineTo(cx - r * 0.42f, cy - r * 0.42f)
            close()
          }
          drawPath(
            path = reusableStarPath,
            color = lerp(particleColor, Color.White, 0.35f),
            alpha = fade * 0.95f,
          )
        } else {
          drawCircle(
            color = particleColor,
            radius = r * 0.9f,
            center = Offset(cx, cy),
            alpha = fade * 0.36f,
          )
          drawCircle(
            color = lerp(particleColor, Color.White, 0.5f),
            radius = r * 0.45f,
            center = Offset(cx, cy),
            alpha = fade * 0.95f,
          )
        }
      }
    }
  }
}
