package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.RenderMode
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieClipSpec
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.example.R
import com.example.model.MascotState
import com.example.model.NotchAppearance

@Composable
fun MascotView(
  state: MascotState,
  appearance: NotchAppearance,
  size: Dp,
  modifier: Modifier = Modifier,
  isActive: Boolean = true,
) {
  val primaryColor = Color(appearance.resolvePrimaryHex())
  val cd = stringResource(R.string.cd_mascot_avatar)

  // Keep Lottie compositions pre-warmed in LottieCompositionFactory while mounting ONLY the visible layer(s)
  val idleComp by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.mascot_idle))
  val musicComp by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.mascot_music))
  val voiceComp by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.mascot_listening))
  val sleepComp by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.mascot_sleeping))
  val wakeComp by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.mascot_wake_wave))

  val sleepColorInt = remember(primaryColor) { primaryColor.copy(alpha = 0.96f).toArgb() }
  val dynamicProperties =
    rememberLottieDynamicProperties(
      rememberLottieDynamicProperty(
        property = LottieProperty.COLOR,
        value = sleepColorInt,
        keyPath = arrayOf("sleep_z_0", "**"),
      ),
      rememberLottieDynamicProperty(
        property = LottieProperty.COLOR,
        value = sleepColorInt,
        keyPath = arrayOf("sleep_z_1", "**"),
      ),
      rememberLottieDynamicProperty(
        property = LottieProperty.COLOR,
        value = sleepColorInt,
        keyPath = arrayOf("sleep_z_2", "**"),
      ),
      rememberLottieDynamicProperty(
        property = LottieProperty.STROKE_COLOR,
        value = sleepColorInt,
        keyPath = arrayOf("sleep_z_0", "**"),
      ),
      rememberLottieDynamicProperty(
        property = LottieProperty.STROKE_COLOR,
        value = sleepColorInt,
        keyPath = arrayOf("sleep_z_1", "**"),
      ),
      rememberLottieDynamicProperty(
        property = LottieProperty.STROKE_COLOR,
        value = sleepColorInt,
        keyPath = arrayOf("sleep_z_2", "**"),
      ),
      rememberLottieDynamicProperty(
        property = LottieProperty.COLOR,
        value = sleepColorInt,
        keyPath = arrayOf("sleep_z_0"),
      ),
      rememberLottieDynamicProperty(
        property = LottieProperty.COLOR,
        value = sleepColorInt,
        keyPath = arrayOf("sleep_z_1"),
      ),
      rememberLottieDynamicProperty(
        property = LottieProperty.COLOR,
        value = sleepColorInt,
        keyPath = arrayOf("sleep_z_2"),
      ),
    )

  // Solid-back crossfade: keep outgoing layer underneath at 100% alpha strictly during 120ms fade,
  // then unmount it so only 1 Lottie hardware layer is ever active at rest.
  var settlingPrevState by remember { mutableStateOf<MascotState?>(null) }
  var lastActiveState by remember { mutableStateOf(state) }
  val incomingAlpha = remember { Animatable(1f) }

  LaunchedEffect(state) {
    if (state != lastActiveState) {
      settlingPrevState = lastActiveState
      lastActiveState = state
      incomingAlpha.snapTo(0f)
      incomingAlpha.animateTo(1f, animationSpec = tween(durationMillis = 120))
      settlingPrevState = null
    }
  }

  val wakeClipSpec = remember { LottieClipSpec.Frame(min = 285, max = 515) }
  val sleepClipSpec = remember { LottieClipSpec.Frame(min = 0, max = 115) }

  val effectivePrevState =
    if (state != lastActiveState) lastActiveState else settlingPrevState?.takeIf { it != state }
  val effectiveIncomingAlpha = if (state != lastActiveState) 0f else incomingAlpha.value

  fun specFor(target: MascotState): Triple<LottieComposition?, LottieClipSpec?, Pair<Int, Float>> =
    when (target) {
      MascotState.IDLE -> Triple(idleComp, null, LottieConstants.IterateForever to 1f)
      MascotState.LISTENING_MUSIC -> Triple(musicComp, null, LottieConstants.IterateForever to 1f)
      MascotState.LISTENING_VOICE -> Triple(voiceComp, null, LottieConstants.IterateForever to 1f)
      MascotState.SLEEPING -> Triple(sleepComp, sleepClipSpec, 1 to 1f)
      MascotState.WAKING_UP -> Triple(wakeComp, wakeClipSpec, 1 to 1.55f)
    }

  Box(
    modifier = modifier.size(size).semantics { contentDescription = cd },
    contentAlignment = Alignment.Center,
  ) {
    if (effectivePrevState != null) {
      key(effectivePrevState) {
        val (comp, clip, iterAndSpeed) = specFor(effectivePrevState)
        MascotLayer(
          isCurrent = false,
          isPlaying = false,
          layerAlpha = 1f,
          layerZIndex = 1f,
          composition = comp,
          clipSpec = clip,
          iterations = iterAndSpeed.first,
          speed = iterAndSpeed.second,
          dynamicProperties = dynamicProperties,
        )
      }
    }

    key(state) {
      val (comp, clip, iterAndSpeed) = specFor(state)
      MascotLayer(
        isCurrent = true,
        isPlaying = isActive,
        layerAlpha = effectiveIncomingAlpha,
        layerZIndex = 2f,
        composition = comp,
        clipSpec = clip,
        iterations = iterAndSpeed.first,
        speed = iterAndSpeed.second,
        dynamicProperties = dynamicProperties,
      )
    }
  }
}

@Composable
private fun MascotLayer(
  isCurrent: Boolean,
  isPlaying: Boolean,
  layerAlpha: Float,
  layerZIndex: Float,
  composition: LottieComposition?,
  clipSpec: LottieClipSpec?,
  iterations: Int,
  speed: Float,
  dynamicProperties: com.airbnb.lottie.compose.LottieDynamicProperties,
) {
  val progress by
    animateLottieCompositionAsState(
      composition = composition,
      isPlaying = isCurrent && isPlaying,
      iterations = iterations,
      clipSpec = clipSpec,
      speed = speed,
      restartOnPlay = true,
    )

  LottieAnimation(
    composition = composition,
    progress = { progress },
    dynamicProperties = dynamicProperties,
    renderMode = RenderMode.AUTOMATIC,
    contentScale = ContentScale.Fit,
    modifier =
      Modifier.fillMaxSize()
        .zIndex(layerZIndex)
        .graphicsLayer {
          this.alpha = layerAlpha
        },
  )
}
