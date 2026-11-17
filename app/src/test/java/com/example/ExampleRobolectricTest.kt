package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.AccentPreset
import com.example.model.ActiveCallInfo
import com.example.model.ActiveNotificationBanner
import com.example.model.CompactHudAlert
import com.example.model.LaunchableAppInfo
import com.example.model.MascotState
import com.example.model.NotchBorderStyle
import com.example.model.NotchConfig
import com.example.model.NotchGlowStyle
import com.example.model.NotchWidgetType
import com.example.model.NotificationFilterMode
import com.example.model.VoiceListeningPhase
import com.example.service.GeminiMiniClient
import com.example.service.VoiceCommandProcessor
import com.example.service.VoiceCommandResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `app name matches MatusNotch`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("MatusNotch", appName)
  }

  @Test
  fun `runtime coordinator handles expand collapse notification banner and dismiss`() {
    val app = ApplicationProvider.getApplicationContext<MatusNotchApplication>()
    val coordinator = app.coordinator

    assertFalse(coordinator.runtimeState.value.isExpanded)

    coordinator.expand()
    assertTrue(coordinator.runtimeState.value.isExpanded)
    assertEquals(MascotState.WAKING_UP, coordinator.runtimeState.value.mascotState)

    val beforePulse = coordinator.runtimeState.value.notificationPulseId
    coordinator.onNotificationPosted(
      ActiveNotificationBanner(
        packageName = app.packageName,
        appName = "Messages",
        title = "Alex",
        body = "See you at 8pm!",
        timestampMillis = System.currentTimeMillis(),
      )
    )
    assertTrue(coordinator.runtimeState.value.isExpanded)
    assertTrue(coordinator.runtimeState.value.notificationPulseId > beforePulse)
    val banner = coordinator.runtimeState.value.activeNotification
    assertNotNull(banner)
    assertEquals("Messages", banner?.appName)
    assertEquals("Alex", banner?.title)
    assertEquals("See you at 8pm!", banner?.body)

    coordinator.dismissNotificationBanner()
    assertNull(coordinator.runtimeState.value.activeNotification)
    assertFalse(coordinator.runtimeState.value.isExpanded)
  }

  @Test
  fun `notch config defaults and widget fallback work cleanly`() {
    val config = NotchConfig()
    assertEquals(AccentPreset.CYAN, config.appearance.accentPreset)
    assertEquals(NotchBorderStyle.HAIRLINE, config.appearance.borderStyle)
    assertEquals(NotchGlowStyle.LED_RACE, config.appearance.glowStyle)
    assertEquals(10, config.autoCollapseSeconds)
    assertEquals(6, config.enabledWidgets.size)
    assertTrue(config.enabledWidgets.contains(NotchWidgetType.DEVICE_ACTIONS))
    assertTrue(config.enabledWidgets.contains(NotchWidgetType.CALCULATOR))

    val allDisabled =
      config.copy(widgetSlots = config.widgetSlots.map { it.copy(enabled = false) })
    assertEquals(listOf(NotchWidgetType.MEDIA), allDisabled.enabledWidgets)
  }

  @Test
  fun `all user mascot Lottie raw resources are bundled`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    listOf(
      R.raw.mascot_idle,
      R.raw.mascot_music,
      R.raw.mascot_sleeping,
      R.raw.mascot_wake_wave,
      R.raw.mascot_listening,
    ).forEach { resId ->
      val bytes = context.resources.openRawResource(resId).use { it.readBytes() }
      assertTrue("Raw resource $resId should not be empty", bytes.isNotEmpty())
    }
  }

  @Test
  fun `dnd and silent mode transitions trigger left action alert for activate and deactivate`() {
    val app = ApplicationProvider.getApplicationContext<MatusNotchApplication>()
    val coordinator = app.coordinator
    val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager

    audioManager.ringerMode = android.media.AudioManager.RINGER_MODE_NORMAL
    coordinator.syncDndAndSilentState()

    audioManager.ringerMode = android.media.AudioManager.RINGER_MODE_SILENT
    coordinator.syncDndAndSilentState()
    assertEquals(
      com.example.model.ModeAlertKind.SILENT_ON,
      coordinator.runtimeState.value.activeModeAlert?.kind,
    )

    audioManager.ringerMode = android.media.AudioManager.RINGER_MODE_NORMAL
    coordinator.syncDndAndSilentState()
    assertEquals(
      com.example.model.ModeAlertKind.SILENT_OFF,
      coordinator.runtimeState.value.activeModeAlert?.kind,
    )

    coordinator.triggerModeAlert(com.example.model.ModeAlertKind.DND_ON)
    assertEquals(
      com.example.model.ModeAlertKind.DND_ON,
      coordinator.runtimeState.value.activeModeAlert?.kind,
    )

    coordinator.triggerModeAlert(com.example.model.ModeAlertKind.DND_OFF)
    assertEquals(
      com.example.model.ModeAlertKind.DND_OFF,
      coordinator.runtimeState.value.activeModeAlert?.kind,
    )
  }

  @Test
  fun `per-app notification filter modes work as expected`() {
    val selected = setOf("com.whatsapp", "com.discord")

    val allAppsConfig =
      NotchConfig(
        notificationFilterMode = NotificationFilterMode.ALL_APPS,
        filteredNotificationPackages = selected,
      )
    assertTrue(allAppsConfig.shouldPopupNotification("com.whatsapp"))
    assertTrue(allAppsConfig.shouldPopupNotification("com.other.app"))

    val onlySelectedConfig =
      allAppsConfig.copy(notificationFilterMode = NotificationFilterMode.ONLY_SELECTED)
    assertTrue(onlySelectedConfig.shouldPopupNotification("com.whatsapp"))
    assertFalse(onlySelectedConfig.shouldPopupNotification("com.other.app"))

    val muteSelectedConfig =
      allAppsConfig.copy(notificationFilterMode = NotificationFilterMode.MUTE_SELECTED)
    assertFalse(muteSelectedConfig.shouldPopupNotification("com.whatsapp"))
    assertTrue(muteSelectedConfig.shouldPopupNotification("com.other.app"))
  }

  @Test
  fun `compact hud alerts, ongoing call, screen recording, and landscape hide update runtime state`() {
    val app = ApplicationProvider.getApplicationContext<MatusNotchApplication>()
    val coordinator = app.coordinator

    val chargingAlert = CompactHudAlert.Charging(id = 101L, percent = 82, isCharging = true)
    coordinator.showCompactHud(chargingAlert, durationMs = 5000L)
    assertEquals(chargingAlert, coordinator.runtimeState.value.activeCompactHud)

    val btAlert = CompactHudAlert.BluetoothConnected(id = 102L, deviceName = "Pixel Buds Pro")
    coordinator.showCompactHud(btAlert, durationMs = 5000L)
    assertEquals(btAlert, coordinator.runtimeState.value.activeCompactHud)

    val volAlert =
      CompactHudAlert.VolumeHud(id = 103L, fraction = 0.6f, percent = 60, isMuted = false)
    coordinator.showCompactHud(volAlert, durationMs = 5000L)
    assertEquals(volAlert, coordinator.runtimeState.value.activeCompactHud)

    val brightAlert = CompactHudAlert.BrightnessHud(id = 104L, fraction = 0.75f, percent = 75)
    coordinator.showCompactHud(brightAlert, durationMs = 5000L)
    assertEquals(brightAlert, coordinator.runtimeState.value.activeCompactHud)

    val call = ActiveCallInfo(key = "call-1", callerName = "Jordan", packageName = "com.dialer")
    coordinator.updateOngoingCall(call)
    assertEquals("Jordan", coordinator.runtimeState.value.ongoingStatus.activeCall?.callerName)
    coordinator.updateOngoingCall(null)
    assertNull(coordinator.runtimeState.value.ongoingStatus.activeCall)

    coordinator.updateScreenRecording(active = true, startMillis = 123456L)
    assertTrue(coordinator.runtimeState.value.ongoingStatus.isScreenRecording)
    assertEquals(123456L, coordinator.runtimeState.value.ongoingStatus.recordingStartMillis)
    coordinator.updateScreenRecording(active = false)
    assertFalse(coordinator.runtimeState.value.ongoingStatus.isScreenRecording)

    coordinator.expand()
    assertTrue(coordinator.runtimeState.value.isExpanded)
    coordinator.setLandscapeHidden(true)
    assertTrue(coordinator.runtimeState.value.isLandscapeHidden)
    assertFalse(coordinator.runtimeState.value.isExpanded)
    coordinator.setLandscapeHidden(false)
    assertFalse(coordinator.runtimeState.value.isLandscapeHidden)
  }

  @Test
  fun `mini calculator supports arithmetic chaining decimals backspace and error recovery`() {
    val app = ApplicationProvider.getApplicationContext<MatusNotchApplication>()
    val coordinator = app.coordinator

    coordinator.onCalculatorInput("AC")
    listOf("1", "2", "+", "8", "=").forEach { coordinator.onCalculatorInput(it) }
    assertEquals("20", coordinator.runtimeState.value.calculator.display)

    listOf("×", "2", ".", "5", "=").forEach { coordinator.onCalculatorInput(it) }
    assertEquals("50", coordinator.runtimeState.value.calculator.display)

    coordinator.onCalculatorInput("AC")
    listOf("9", "5", "DEL").forEach { coordinator.onCalculatorInput(it) }
    assertEquals("9", coordinator.runtimeState.value.calculator.display)

    listOf("÷", "0", "=").forEach { coordinator.onCalculatorInput(it) }
    assertTrue(coordinator.runtimeState.value.calculator.hasError)
    assertEquals("Error", coordinator.runtimeState.value.calculator.display)

    coordinator.onCalculatorInput("7")
    assertFalse(coordinator.runtimeState.value.calculator.hasError)
    assertEquals("7", coordinator.runtimeState.value.calculator.display)
  }

  @Test
  fun `mascot tap reaction triggers only when notch is expanded without active notification`() {
    val app = ApplicationProvider.getApplicationContext<MatusNotchApplication>()
    val coordinator = app.coordinator

    coordinator.collapse()
    val initialTapId = coordinator.runtimeState.value.mascotTapId
    coordinator.onMascotTapped()
    assertEquals(initialTapId, coordinator.runtimeState.value.mascotTapId)

    coordinator.onNotificationPosted(
      ActiveNotificationBanner(
        packageName = app.packageName,
        appName = "Chat",
        title = "Ping",
        body = "Notification active",
        timestampMillis = System.currentTimeMillis(),
      )
    )
    coordinator.onMascotTapped()
    assertEquals(initialTapId, coordinator.runtimeState.value.mascotTapId)

    coordinator.dismissNotificationBanner()
    coordinator.expand()
    coordinator.onMascotTapped()
    assertEquals(initialTapId + 1L, coordinator.runtimeState.value.mascotTapId)
    assertEquals(MascotState.WAKING_UP, coordinator.runtimeState.value.mascotState)
  }

  @Test
  fun `manual mascot long press voice listening opens persistent card and closes cleanly`() {
    val app = ApplicationProvider.getApplicationContext<MatusNotchApplication>()
    val coordinator = app.coordinator
    coordinator.collapse()

    // Without RECORD_AUDIO permission in Robolectric default state, startVoiceListening keeps
    // the expanded card open in FINISHED state with an instructive prompt until explicitly closed.
    coordinator.startVoiceListening()
    val state = coordinator.runtimeState.value
    assertTrue(state.isExpanded)
    assertEquals(VoiceListeningPhase.FINISHED, state.voiceListening.phase)
    assertNotNull(state.voiceListening.errorMessage)

    coordinator.closeVoiceListening()
    assertFalse(coordinator.runtimeState.value.isExpanded)
    assertEquals(VoiceListeningPhase.INACTIVE, coordinator.runtimeState.value.voiceListening.phase)
  }

  @Test
  fun `voice command processor parses timer durations matches apps and handles fallback hint`() {
    assertEquals(10, VoiceCommandProcessor.parseTimerMinutes("set timer for 10 minutes"))
    assertEquals(5, VoiceCommandProcessor.parseTimerMinutes("start five minute timer"))
    assertNull(VoiceCommandProcessor.parseTimerMinutes("what is the weather"))

    val apps =
      listOf(
        LaunchableAppInfo("com.instagram.android", "Instagram"),
        LaunchableAppInfo("com.google.android.youtube", "YouTube"),
        LaunchableAppInfo("com.android.settings", "Settings"),
      )
    assertEquals("Instagram", VoiceCommandProcessor.findBestMatchingApp(apps, "instagram")?.label)
    assertEquals("YouTube", VoiceCommandProcessor.findBestMatchingApp(apps, "you tube")?.label)
    assertNull(VoiceCommandProcessor.findBestMatchingApp(apps, "nonexistentapp"))

    val app = ApplicationProvider.getApplicationContext<MatusNotchApplication>()
    val coordinator = app.coordinator

    // Unknown app stays open with explicit inline message
    val unknownAppResult = VoiceCommandProcessor.process(app, coordinator, "open unknownapp")
    assertEquals(VoiceCommandResult.Failed("App 'unknownapp' not found"), unknownAppResult)

    // Focus timer voice command configures & starts timer
    coordinator.handleFinalVoiceTranscript("set timer for 7 minutes")
    val timerState = coordinator.runtimeState.value
    assertTrue(timerState.timer.isRunning)
    assertEquals(420, timerState.timer.totalSeconds)
    assertEquals("Timer: 7m Started", timerState.voiceListening.confirmationBadge)

    // Non-command prompt without user API key shows Settings hint cleanly
    coordinator.handleFinalVoiceTranscript("what is the capital of Japan")
    val fallbackState = coordinator.runtimeState.value
    assertEquals(VoiceListeningPhase.FINISHED, fallbackState.voiceListening.phase)
    assertTrue(fallbackState.voiceListening.needsApiKeyHint)
    assertEquals("what is the capital of Japan", fallbackState.voiceListening.transcript)

    coordinator.closeVoiceListening()
  }

  @Test
  fun `voice command processor handles natural human variations auto rotate silent mode and state awareness`() {
    assertEquals(
      "activate flashlight",
      VoiceCommandProcessor.normalizeCommandText("Hey Matus, could you please activate flashlight for me?"),
    )
    assertEquals(15, VoiceCommandProcessor.parseTimerMinutes("count down 15 minutes"))
    assertEquals(30, VoiceCommandProcessor.parseTimerMinutes("set a timer for half an hour"))
    assertEquals(60, VoiceCommandProcessor.parseTimerMinutes("1 hour timer"))

    val app = ApplicationProvider.getApplicationContext<MatusNotchApplication>()
    val coordinator = app.coordinator

    // Natural phrasing for Pomodoro, Pause/Resume, and Skip
    val pomodoroRes = VoiceCommandProcessor.process(app, coordinator, "Can you start a pomodoro please")
    assertEquals(VoiceCommandResult.Executed("Timer: 25m Started"), pomodoroRes)
    assertTrue(coordinator.runtimeState.value.timer.isRunning)

    val alreadyRunningRes = VoiceCommandProcessor.process(app, coordinator, "resume timer")
    assertEquals(VoiceCommandResult.Executed("Timer Already Running"), alreadyRunningRes)

    val pauseTimerRes = VoiceCommandProcessor.process(app, coordinator, "hold timer")
    assertEquals(VoiceCommandResult.Executed("Timer Paused"), pauseTimerRes)
    assertFalse(coordinator.runtimeState.value.timer.isRunning)

    // Natural app-launch variation with polite wrapper and "app" suffix
    val appRes = VoiceCommandProcessor.process(app, coordinator, "Could you switch to unknownapp app")
    assertEquals(VoiceCommandResult.Failed("App 'unknownapp app' not found"), appRes)

    // Media natural phrasing
    val skipRes = VoiceCommandProcessor.process(app, coordinator, "skip this song please")
    assertEquals(VoiceCommandResult.Executed("Skipped Track"), skipRes)

    val pauseMediaRes = VoiceCommandProcessor.process(app, coordinator, "please pause the music right now")
    assertEquals(VoiceCommandResult.Executed("Media Paused"), pauseMediaRes)

    // State-aware Auto-Rotate transitions (Robolectric permits System.Settings writes)
    val unlockRot = VoiceCommandProcessor.process(app, coordinator, "unlock rotation")
    assertEquals(VoiceCommandResult.Executed("Auto-Rotate On"), unlockRot)
    assertTrue(coordinator.runtimeState.value.telemetry.isAutoRotateOn)

    val alreadyRot = VoiceCommandProcessor.process(app, coordinator, "enable auto rotate please")
    assertEquals(VoiceCommandResult.Executed("Auto-Rotate Already On"), alreadyRot)

    val lockRot = VoiceCommandProcessor.process(app, coordinator, "lock rotation")
    assertEquals(VoiceCommandResult.Executed("Auto-Rotate Off"), lockRot)
    assertFalse(coordinator.runtimeState.value.telemetry.isAutoRotateOn)

    // Without DND policy access, Silent mode returns clear permission guidance
    val silentDenied = VoiceCommandProcessor.process(app, coordinator, "put my phone on silent please")
    assertEquals(
      VoiceCommandResult.Failed("Grant DND access in Settings for Silent mode"),
      silentDenied,
    )

    // With DND policy access granted, verify state-aware Silent mode and DND ON/Already On/OFF
    val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    org.robolectric.Shadows.shadowOf(nm).setNotificationPolicyAccessGranted(true)

    val silentOn = VoiceCommandProcessor.process(app, coordinator, "put my phone on silent please")
    assertEquals(VoiceCommandResult.Executed("Silent Mode On"), silentOn)
    assertTrue(coordinator.runtimeState.value.telemetry.isSilentOn)

    val silentAlready = VoiceCommandProcessor.process(app, coordinator, "silence phone")
    assertEquals(VoiceCommandResult.Executed("Silent Mode Already On"), silentAlready)

    val silentOff = VoiceCommandProcessor.process(app, coordinator, "unmute ringer")
    assertEquals(VoiceCommandResult.Executed("Silent Mode Off"), silentOff)
    assertFalse(coordinator.runtimeState.value.telemetry.isSilentOn)

    val dndOn = VoiceCommandProcessor.process(app, coordinator, "activate dnd please")
    assertEquals(VoiceCommandResult.Executed("Do Not Disturb On"), dndOn)

    val dndAlready = VoiceCommandProcessor.process(app, coordinator, "enable do not disturb")
    assertEquals(VoiceCommandResult.Executed("Do Not Disturb Already On"), dndAlready)

    val dndOff = VoiceCommandProcessor.process(app, coordinator, "disable dnd")
    assertEquals(VoiceCommandResult.Executed("Do Not Disturb Off"), dndOff)
  }

  @Test
  fun `voice command processor intercepts instant local time date day year battery and delegates complex temporal prompts`() {
    val app = ApplicationProvider.getApplicationContext<MatusNotchApplication>()
    val coordinator = app.coordinator

    val tz = java.util.TimeZone.getTimeZone("GMT+03:00")
    val cal =
      java.util.Calendar.getInstance(tz, java.util.Locale.ENGLISH).apply {
        set(2026, java.util.Calendar.NOVEMBER, 17, 2, 45, 0)
        set(java.util.Calendar.MILLISECOND, 0)
      }
    val fixedNow = cal.time

    assertEquals(
      "02:45 AM (GMT+03:00)",
      VoiceCommandProcessor.formatLocalTimeAnswer(now = fixedNow, timeZone = tz),
    )
    assertEquals(
      "Tuesday, November 17, 2026 • 02:45",
      VoiceCommandProcessor.formatLocalDateAnswer(now = fixedNow, timeZone = tz),
    )

    // Deterministic matching on time, date, day, month, year queries
    listOf("what time is it", "tell me the time", "tell me what time it is").forEach { phrase ->
      val normalized = VoiceCommandProcessor.normalizeCommandText(phrase)
      val matched = VoiceCommandProcessor.matchSystemInfoQuery(app, normalized, fixedNow, tz)
      assertEquals(
        VoiceCommandResult.InfoAnswer("Local Time", "02:45 AM (GMT+03:00)"),
        matched,
      )
    }

    listOf(
      "what day is it",
      "what day is today",
      "what is today's date",
      "what's the date",
      "what year is it",
      "what month is it",
    ).forEach { phrase ->
      val normalized =
        VoiceCommandProcessor.normalizeCommandText(phrase)
          .split(" ")
          .filterNot { it == "the" || it == "my" }
          .joinToString(" ")
      val matched = VoiceCommandProcessor.matchSystemInfoQuery(app, normalized, fixedNow, tz)
      assertEquals(
        VoiceCommandResult.InfoAnswer("Today's Date", "Tuesday, November 17, 2026 • 02:45"),
        matched,
      )
    }

    // Battery command executes locally and populates persistent localInfoReply
    coordinator.handleFinalVoiceTranscript("what's my battery level")
    val batteryState = coordinator.runtimeState.value
    assertTrue(batteryState.isExpanded)
    assertEquals(VoiceListeningPhase.FINISHED, batteryState.voiceListening.phase)
    assertEquals("Battery Status", batteryState.voiceListening.localInfoTitle)
    assertTrue(batteryState.voiceListening.localInfoReply?.startsWith("Battery: ") == true)
    assertNull(batteryState.voiceListening.aiReply)
    assertFalse(batteryState.voiceListening.needsApiKeyHint)

    // Complex temporal or foreign timezone questions must fall through to Tier 2 Gemini
    listOf(
      "what day is tomorrow",
      "how many days until New Year",
      "what time is it in Tokyo",
      "what year was Einstein born",
    ).forEach { complexQuestion ->
      assertEquals(
        VoiceCommandResult.NotMatched,
        VoiceCommandProcessor.process(app, coordinator, complexQuestion),
      )
    }

    coordinator.closeVoiceListening()
  }

  @Test
  fun `gemini mini client parses response text injects live timestamp context and handles api error json`() {
    assertEquals(
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent",
      GeminiMiniClient.buildModelEndpoint(GeminiMiniClient.PRIMARY_MODEL),
    )
    assertEquals(
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:generateContent",
      GeminiMiniClient.buildModelEndpoint(GeminiMiniClient.FALLBACK_MODEL),
    )
    assertTrue(GeminiMiniClient.shouldRetryWithFallback(404, "Model not found"))
    assertTrue(GeminiMiniClient.shouldRetryWithFallback(503, "Model is overloaded"))
    assertFalse(GeminiMiniClient.shouldRetryWithFallback(401, "API key not valid"))

    val tz = java.util.TimeZone.getTimeZone("GMT+03:00")
    val cal =
      java.util.Calendar.getInstance(tz, java.util.Locale.ENGLISH).apply {
        set(2026, java.util.Calendar.NOVEMBER, 17, 2, 45, 0)
        set(java.util.Calendar.MILLISECOND, 0)
      }
    val liveStamp = GeminiMiniClient.formatLiveDeviceDateTime(date = cal.time, timeZone = tz)
    assertEquals("Tuesday, November 17, 2026 at 02:45 GMT+03:00", liveStamp)

    val payloadJson =
      org.json.JSONObject(
        GeminiMiniClient.buildRequestPayload("what day is tomorrow?", liveStamp)
      )
    val sysText =
      payloadJson
        .getJSONObject("systemInstruction")
        .getJSONArray("parts")
        .getJSONObject(0)
        .getString("text")
    assertTrue(sysText.contains(liveStamp))

    val userText =
      payloadJson
        .getJSONArray("contents")
        .getJSONObject(0)
        .getJSONArray("parts")
        .getJSONObject(0)
        .getString("text")
    assertEquals(
      "[Current Device Context: Today is Tuesday, November 17, 2026 at 02:45 GMT+03:00. " +
        "Always use this exact date, day, and time for any temporal questions like today, tomorrow, yesterday, or current year.] " +
        "User question: what day is tomorrow?",
      userText,
    )

    val genCfg = payloadJson.getJSONObject("generationConfig")
    assertEquals(512, genCfg.getInt("maxOutputTokens"))
    assertEquals(0, genCfg.getJSONObject("thinkingConfig").getInt("thinkingBudget"))

    val okJson =
      """{"candidates":[{"content":{"parts":[{"text":"Tokyo is the capital of Japan."}]}}]}"""
    assertEquals("Tokyo is the capital of Japan.", GeminiMiniClient.parseGeminiResponseBody(okJson))

    val errJson = """{"error":{"code":400,"message":"API key not valid."}}"""
    assertEquals("API key not valid.", GeminiMiniClient.parseGeminiErrorMessage(errJson))
  }

  @Test
  fun `matus pro feature split keeps existing accents and device actions free while locking custom wheel and pro items`() {
    // Existing 5 accents remain FREE; only CUSTOM Color Wheel is Pro
    listOf(
      AccentPreset.CYAN,
      AccentPreset.VIOLET,
      AccentPreset.MINT,
      AccentPreset.AMBER,
      AccentPreset.CRIMSON,
    ).forEach { freePreset ->
      assertFalse(com.example.model.EntitlementResolver.isProAccent(freePreset))
    }
    assertTrue(com.example.model.EntitlementResolver.isProAccent(AccentPreset.CUSTOM))

    // Device Actions stays FREE per user override; Calculator & Focus Timer are Pro
    assertFalse(com.example.model.EntitlementResolver.isProWidget(NotchWidgetType.MEDIA))
    assertFalse(com.example.model.EntitlementResolver.isProWidget(NotchWidgetType.QUICK_TOGGLES))
    assertFalse(com.example.model.EntitlementResolver.isProWidget(NotchWidgetType.DEVICE_ACTIONS))
    assertFalse(com.example.model.EntitlementResolver.isProWidget(NotchWidgetType.APP_SHORTCUTS))
    assertTrue(com.example.model.EntitlementResolver.isProWidget(NotchWidgetType.CALCULATOR))
    assertTrue(com.example.model.EntitlementResolver.isProWidget(NotchWidgetType.FOCUS_TIMER))

    // Borders & Glows split
    assertFalse(com.example.model.EntitlementResolver.isProBorder(NotchBorderStyle.HAIRLINE))
    assertTrue(com.example.model.EntitlementResolver.isProBorder(NotchBorderStyle.NEON_RING))
    assertTrue(com.example.model.EntitlementResolver.isProBorder(NotchBorderStyle.CYBER_DOUBLE))
    assertTrue(com.example.model.EntitlementResolver.isProBorder(NotchBorderStyle.STEALTH))

    assertFalse(com.example.model.EntitlementResolver.isProGlow(NotchGlowStyle.LED_RACE))
    assertTrue(com.example.model.EntitlementResolver.isProGlow(NotchGlowStyle.DUAL_COMET))
    assertTrue(com.example.model.EntitlementResolver.isProGlow(NotchGlowStyle.BREATHING_AURA))
    assertTrue(com.example.model.EntitlementResolver.isProGlow(NotchGlowStyle.PRISM_WAVE))

    // Notification filter split
    assertFalse(com.example.model.EntitlementResolver.isProFilterMode(NotificationFilterMode.ALL_APPS))
    assertTrue(com.example.model.EntitlementResolver.isProFilterMode(NotificationFilterMode.ONLY_SELECTED))
    assertTrue(com.example.model.EntitlementResolver.isProFilterMode(NotificationFilterMode.MUTE_SELECTED))

    // Effective config gates Pro items back to Free defaults when isPro == false
    val fullPersisted =
      NotchConfig(
        appearance =
          com.example.model.NotchAppearance(
            accentPreset = AccentPreset.CUSTOM,
            customAccentHex = 0xFFFF5500L,
            borderStyle = NotchBorderStyle.NEON_RING,
            glowStyle = NotchGlowStyle.PRISM_WAVE,
          ),
        notificationFilterMode = NotificationFilterMode.ONLY_SELECTED,
        filteredNotificationPackages = setOf("com.whatsapp"),
      )

    val freeEffective = com.example.model.EntitlementResolver.effectiveConfig(fullPersisted, isPro = false)
    assertEquals(AccentPreset.CYAN, freeEffective.appearance.accentPreset)
    assertEquals(NotchBorderStyle.HAIRLINE, freeEffective.appearance.borderStyle)
    assertEquals(NotchGlowStyle.LED_RACE, freeEffective.appearance.glowStyle)
    assertEquals(NotificationFilterMode.ALL_APPS, freeEffective.notificationFilterMode)
    assertFalse(freeEffective.enabledWidgets.contains(NotchWidgetType.CALCULATOR))
    assertFalse(freeEffective.enabledWidgets.contains(NotchWidgetType.FOCUS_TIMER))
    assertTrue(freeEffective.enabledWidgets.contains(NotchWidgetType.DEVICE_ACTIONS))
    assertTrue(freeEffective.shouldPopupNotification("com.other.app"))

    // When Pro unlocked, persisted Color Wheel, Pro widgets, borders, glows & filter are active
    val proEffective = com.example.model.EntitlementResolver.effectiveConfig(fullPersisted, isPro = true)
    assertEquals(AccentPreset.CUSTOM, proEffective.appearance.accentPreset)
    assertEquals(0xFFFF5500L, proEffective.appearance.resolvePrimaryHex())
    assertTrue(proEffective.appearance.resolveSecondaryHex() != 0xFFFF5500L)
    assertEquals(NotchBorderStyle.NEON_RING, proEffective.appearance.borderStyle)
    assertEquals(NotchGlowStyle.PRISM_WAVE, proEffective.appearance.glowStyle)
    assertTrue(proEffective.enabledWidgets.contains(NotchWidgetType.CALCULATOR))
    assertTrue(proEffective.enabledWidgets.contains(NotchWidgetType.FOCUS_TIMER))
    assertFalse(proEffective.shouldPopupNotification("com.other.app"))
    assertTrue(proEffective.shouldPopupNotification("com.whatsapp"))
  }

  @Test
  fun `subscription plans play entitlement debug gating live preview promotion and voice pro gate behave accurately`() {
    val freshState = com.example.model.ProEntitlementState()
    assertFalse(freshState.isProUnlocked(isDebugBuild = true))
    assertFalse(freshState.isProUnlocked(isDebugBuild = false))
    assertEquals(com.example.model.ProPlanTier.YEARLY, freshState.selectedPlanTier)
    assertEquals("\$1.00", freshState.displayPrice(com.example.model.ProPlanTier.MONTHLY))
    assertEquals("\$5.00", freshState.displayPrice(com.example.model.ProPlanTier.YEARLY))
    assertEquals("\$1.00/mo", freshState.displayPriceWithPeriod(com.example.model.ProPlanTier.MONTHLY))
    assertEquals("\$5.00/yr", freshState.displayPriceWithPeriod(com.example.model.ProPlanTier.YEARLY))
    assertTrue(freshState.hasTrialOffer(com.example.model.ProPlanTier.MONTHLY))
    assertTrue(freshState.hasTrialOffer(com.example.model.ProPlanTier.YEARLY))
    assertEquals(4, com.example.model.EntitlementResolver.TRIAL_DAYS)

    // Active Google Play subscription unlocks Pro in both debug and release builds
    val subscribedMonthly =
      com.example.model.ProEntitlementState(
        isPlaySubscribed = true,
        activePlanTier = com.example.model.ProPlanTier.MONTHLY,
      )
    assertTrue(subscribedMonthly.isProUnlocked(isDebugBuild = false))
    assertTrue(subscribedMonthly.isProUnlocked(isDebugBuild = true))

    val subscribedYearly =
      com.example.model.ProEntitlementState(
        isPlaySubscribed = true,
        activePlanTier = com.example.model.ProPlanTier.YEARLY,
      )
    assertTrue(subscribedYearly.isProUnlocked(isDebugBuild = false))

    // Debug override unlocks ONLY in debug builds, never in release builds
    val debugState = com.example.model.ProEntitlementState(isDebugUnlocked = true)
    assertTrue(debugState.isProUnlocked(isDebugBuild = true))
    assertFalse(debugState.isProUnlocked(isDebugBuild = false))

    // Live preview selection promotes cleanly into persisted config on unlock
    val freeBase = NotchConfig()
    val preview =
      com.example.model.ProPreviewSelection(
        accentPreset = AccentPreset.CUSTOM,
        customAccentHex = 0xFF9D4EDDL,
        borderStyle = NotchBorderStyle.CYBER_DOUBLE,
        glowStyle = NotchGlowStyle.DUAL_COMET,
        previewWidgetType = NotchWidgetType.CALCULATOR,
        notificationFilterMode = NotificationFilterMode.MUTE_SELECTED,
      )
    val merged = com.example.model.EntitlementResolver.mergePreviewIntoConfig(freeBase, preview)
    assertEquals(AccentPreset.CUSTOM, merged.appearance.accentPreset)
    assertEquals(0xFF9D4EDDL, merged.appearance.customAccentHex)
    assertEquals(NotchBorderStyle.CYBER_DOUBLE, merged.appearance.borderStyle)
    assertEquals(NotchGlowStyle.DUAL_COMET, merged.appearance.glowStyle)
    assertEquals(NotificationFilterMode.MUTE_SELECTED, merged.notificationFilterMode)

    // Voice listening on Free user opens Voice Pro Gate card instead of starting mic
    val app = ApplicationProvider.getApplicationContext<MatusNotchApplication>()
    val coordinator = app.coordinator
    coordinator.closeVoiceListening()
    coordinator.startVoiceListening()
    val gatedVoiceState = coordinator.runtimeState.value.voiceListening
    assertEquals(VoiceListeningPhase.FINISHED, gatedVoiceState.phase)
    assertTrue(gatedVoiceState.requiresProUpgrade)
    coordinator.closeVoiceListening()
  }
}
