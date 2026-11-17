package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
) {
  val primaryColor = Color(appearance.resolvePrimaryHex())
  val cd = stringResource(R.string.cd_mascot_avatar)

  // All 5 Lottie compositions hoisted simultaneously & permanently mounted (zero unmount blinks)
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

  // Solid-back crossfade: outgoing layer stays 100% opaque underneath while incoming fades 0 -> 1 on top.
  // This prevents the 75% combined-opacity dip ("blink") during WAKING_UP -> IDLE transitions.
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

  Box(
    modifier = modifier.size(size).semantics { contentDescription = cd },
    contentAlignment = Alignment.Center,
  ) {
    MascotLayer(
      layerState = MascotState.IDLE,
      currentState = state,
      settlingPrevState = settlingPrevState,
      incomingAlpha = incomingAlpha.value,
      composition = idleComp,
      clipSpec = null,
      iterations = LottieConstants.IterateForever,
      speed = 1f,
      dynamicProperties = dynamicProperties,
    )
    MascotLayer(
      layerState = MascotState.LISTENING_MUSIC,
      currentState = state,
      settlingPrevState = settlingPrevState,
      incomingAlpha = incomingAlpha.value,
      composition = musicComp,
      clipSpec = null,
      iterations = LottieConstants.IterateForever,
      speed = 1f,
      dynamicProperties = dynamicProperties,
    )
    MascotLayer(
      layerState = MascotState.LISTENING_VOICE,
      currentState = state,
      settlingPrevState = settlingPrevState,
      incomingAlpha = incomingAlpha.value,
      composition = voiceComp,
      clipSpec = null,
      iterations = LottieConstants.IterateForever,
      speed = 1f,
      dynamicProperties = dynamicProperties,
    )
    MascotLayer(
      layerState = MascotState.SLEEPING,
      currentState = state,
      settlingPrevState = settlingPrevState,
      incomingAlpha = incomingAlpha.value,
      composition = sleepComp,
      clipSpec = null,
      iterations = LottieConstants.IterateForever,
      speed = 1f,
      dynamicProperties = dynamicProperties,
    )
    MascotLayer(
      layerState = MascotState.WAKING_UP,
      currentState = state,
      settlingPrevState = settlingPrevState,
      incomingAlpha = incomingAlpha.value,
      composition = wakeComp,
      clipSpec = wakeClipSpec,
      iterations = 1,
      speed = 1.55f,
      dynamicProperties = dynamicProperties,
    )
  }
}

@Composable
private fun MascotLayer(
  layerState: MascotState,
  currentState: MascotState,
  settlingPrevState: MascotState?,
  incomingAlpha: Float,
  composition: LottieComposition?,
  clipSpec: LottieClipSpec?,
  iterations: Int,
  speed: Float,
  dynamicProperties: com.airbnb.lottie.compose.LottieDynamicProperties,
) {
  val isCurrent = layerState == currentState
  val isPrevSettling = layerState == settlingPrevState && !isCurrent

  val layerAlpha =
    when {
      isCurrent -> incomingAlpha
      isPrevSettling -> 1f
      else -> 0f
    }

  val layerZIndex =
    when {
      isCurrent -> 2f
      isPrevSettling -> 1f
      else -> 0f
    }

  val progress by
    animateLottieCompositionAsState(
      composition = composition,
      isPlaying = isCurrent,
      iterations = iterations,
      clipSpec = clipSpec,
      speed = speed,
      restartOnPlay = true,
    )

  // Keep LottieAnimation permanently mounted in composition so hardware textures stay warm (zero blink)
  LottieAnimation(
    composition = composition,
    progress = { progress },
    dynamicProperties = dynamicProperties,
    renderMode = RenderMode.HARDWARE,
    contentScale = ContentScale.Fit,
    modifier =
      Modifier.fillMaxSize()
        .zIndex(layerZIndex)
        .graphicsLayer {
          this.alpha = layerAlpha
        },
  )
}
