package com.example.service

import android.content.Context
import com.example.model.LaunchableAppInfo
import com.example.system.SystemBridges
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

sealed interface VoiceCommandResult {
  data object NotMatched : VoiceCommandResult

  data class Executed(
    val confirmationLabel: String,
    val autoCloseDelayMs: Long? = 1600L,
  ) : VoiceCommandResult

  data class InfoAnswer(
    val title: String,
    val answerText: String,
  ) : VoiceCommandResult

  data class Failed(val errorMessage: String) : VoiceCommandResult
}

sealed interface VoiceSwitchOutcome {
  data class Changed(val enabled: Boolean) : VoiceSwitchOutcome
  data class Already(val enabled: Boolean) : VoiceSwitchOutcome
  data class PermissionRequired(val message: String) : VoiceSwitchOutcome
  data object OpenedQuickPanel : VoiceSwitchOutcome
  data object Unavailable : VoiceSwitchOutcome
}

object VoiceCommandProcessor {

  private enum class Polarity {
    ON,
    OFF,
    TOGGLE,
  }

  private enum class ToggleFeature(val displayName: String) {
    FLASHLIGHT("Flashlight"),
    DND("Do Not Disturb"),
    AUTO_ROTATE("Auto-Rotate"),
    SILENT("Silent Mode"),
    WIFI("Wi-Fi"),
    BLUETOOTH("Bluetooth"),
  }

  private val CONVERSATIONAL_PREFIXES =
    listOf(
      "hey matus",
      "ok matus",
      "okay matus",
      "matus",
      "can you please",
      "could you please",
      "would you please",
      "can you",
      "could you",
      "would you",
      "will you",
      "please",
      "go ahead and",
      "just",
      "i want to",
      "i need to",
      "i would like to",
      "id like to",
      "let me",
      "help me",
    )

  private val CONVERSATIONAL_SUFFIXES =
    listOf(
      "please",
      "for me",
      "right now",
      "now",
      "thanks",
      "thank you",
      "on my phone",
      "on the phone",
      "my phone",
      "the phone",
    )

  private val ON_VERBS =
    listOf(
      "turn on",
      "switch on",
      "power on",
      "put on",
      "activate",
      "enable",
      "start",
      "open",
      "shine",
    )

  private val OFF_VERBS =
    listOf(
      "turn off",
      "switch off",
      "power off",
      "shut off",
      "deactivate",
      "disable",
      "kill",
      "stop",
      "close",
      "end",
    )

  private val TOGGLE_VERBS = listOf("toggle", "flip", "switch")

  private val OPEN_APP_REGEX =
    Regex("^(?:open|launch|start|run|switch to|go to)\\s+(.+)$")

  private val TIMER_PATTERNS =
    listOf(
      Regex("^(?:set|start|create|make|add|begin)?\\s*(?:a\\s+)?timer\\s+(?:for\\s+)?(.+?)\\s+(minutes?|mins?|hours?|hrs?)$"),
      Regex("^(?:set|start|create|make|begin)\\s+(?:a\\s+)?(.+?)[\\s-]+(minutes?|mins?|hours?|hrs?)\\s+timer$"),
      Regex("^(.+?)[\\s-]+(minutes?|mins?|hours?|hrs?)\\s+timer$"),
      Regex("^(?:count\\s*down|countdown)\\s+(?:from\\s+|for\\s+)?(.+?)\\s+(minutes?|mins?|hours?|hrs?)$"),
      Regex("^(.+?)[\\s-]+(minutes?|mins?|hours?|hrs?)\\s+countdown$"),
      Regex("^(?:remind me in|focus for|take a|break for)\\s+(.+?)\\s+(minutes?|mins?|hours?|hrs?)(?:\\s+break)?$"),
    )

  private val NUMBER_WORDS =
    mapOf(
      "a" to 1,
      "an" to 1,
      "one" to 1,
      "two" to 2,
      "three" to 3,
      "four" to 4,
      "five" to 5,
      "six" to 6,
      "seven" to 7,
      "eight" to 8,
      "nine" to 9,
      "ten" to 10,
      "eleven" to 11,
      "twelve" to 12,
      "thirteen" to 13,
      "fourteen" to 14,
      "fifteen" to 15,
      "twenty" to 20,
      "twenty-five" to 25,
      "twenty five" to 25,
      "thirty" to 30,
      "forty" to 40,
      "forty-five" to 45,
      "forty five" to 45,
      "fifty" to 50,
      "sixty" to 60,
      "ninety" to 90,
      "half" to 30,
      "half a" to 30,
      "half an" to 30,
    )

  private val PLAY_PHRASES =
    setOf(
      "play",
      "resume",
      "unpause",
      "play music",
      "play song",
      "play audio",
      "play track",
      "resume music",
      "resume song",
      "resume audio",
      "resume playback",
      "continue music",
      "continue playing",
      "start music",
      "start playing",
      "keep playing",
      "music on",
    )

  private val PAUSE_PHRASES =
    setOf(
      "pause",
      "pause music",
      "pause song",
      "pause audio",
      "pause track",
      "pause playback",
      "stop music",
      "stop song",
      "stop audio",
      "stop playing",
      "halt music",
      "music off",
    )

  private val NEXT_PHRASES =
    setOf(
      "next",
      "next song",
      "next track",
      "next music",
      "skip",
      "skip song",
      "skip track",
      "skip this",
      "skip this song",
      "skip ahead",
      "play next",
      "play next song",
      "forward track",
    )

  private val PREVIOUS_PHRASES =
    setOf(
      "previous",
      "previous song",
      "previous track",
      "go back",
      "back",
      "back track",
      "last song",
      "last track",
      "play previous",
      "restart song",
      "rewind",
      "rewind track",
    )

  private val SCREENSHOT_PHRASES =
    setOf(
      "screenshot",
      "take screenshot",
      "take a screenshot",
      "capture screen",
      "capture the screen",
      "screen capture",
      "snap screen",
      "snap the screen",
      "snap a screenshot",
      "grab screenshot",
      "grab a screenshot",
      "save screen",
      "print screen",
    )

  private val LOCK_SCREEN_PHRASES =
    setOf(
      "lock screen",
      "lock the screen",
      "lock phone",
      "lock my phone",
      "lock device",
      "lock it",
      "turn off screen",
      "turn off the screen",
      "screen off",
      "put phone to sleep",
      "sleep screen",
    )

  private val NON_CURRENT_INFO_MARKERS =
    listOf(
      "tomorrow",
      "yesterday",
      "next week",
      "last week",
      "next month",
      "last month",
      "next year",
      "last year",
      "until",
      "days left",
      "ago",
      "born",
      "died",
      "invented",
      "happened",
      "sunset",
      "sunrise",
      "timezone",
      "time zone",
      "why ",
      "how to ",
      "how do ",
      "was ",
      "were ",
      "did ",
      "will ",
      "would ",
    )

  private val BATTERY_PHRASES =
    setOf(
      "battery",
      "battery level",
      "battery percent",
      "battery percentage",
      "battery status",
      "charge level",
      "power level",
      "check battery",
      "check battery level",
      "show battery",
      "show battery level",
      "tell me battery",
      "tell me battery level",
      "what is battery",
      "what is battery level",
      "what is battery percent",
      "what is battery percentage",
      "what is battery status",
      "how much battery",
      "how much battery do i have",
      "how much battery is left",
      "how much battery left",
      "how much charge",
      "how much charge is left",
      "is phone charging",
      "is device charging",
      "is it charging",
      "am i charging",
      "charging status",
    )

  private val BATTERY_QUERY_REGEX =
    Regex("^(?:what is|tell me|check|show|give me)?\\s*(?:current\\s+)?battery(?:\\s+(?:level|percent|percentage|status))?$")

  private val TIME_PHRASES =
    setOf(
      "time",
      "current time",
      "local time",
      "time now",
      "clock",
      "what time is it",
      "what time it is",
      "what is time",
      "what is current time",
      "what is local time",
      "tell me time",
      "give me time",
      "check time",
      "show time",
      "what time",
      "do you know time",
      "do you have time",
      "have you got time",
      "got time",
    )

  private val TIME_QUERY_REGEX =
    Regex("^(?:what is|tell me|check|show|give me)\\s+(?:current\\s+|local\\s+)?time$")

  private val DATE_DAY_YEAR_PHRASES =
    setOf(
      "date",
      "today date",
      "current date",
      "what is date",
      "what is today date",
      "what is current date",
      "tell me date",
      "check date",
      "show date",
      "give me date",
      "what date is it",
      "what date is today",
      "which date is it",
      "which date is today",
      "today",
      "day",
      "what day is it",
      "what day is today",
      "what day it is",
      "what day",
      "what is day",
      "what is today",
      "tell me day",
      "which day is it",
      "which day is today",
      "what day of week is it",
      "what day of week is today",
      "day of week",
      "what month is it",
      "what month is this",
      "which month is it",
      "which month is this",
      "current month",
      "what is month",
      "tell me month",
      "what year is it",
      "what year is this",
      "which year is it",
      "which year is this",
      "current year",
      "what is year",
      "tell me year",
      "date and time",
      "time and date",
      "what is date and time",
      "what is time and date",
      "tell me date and time",
      "what day and time is it",
    )

  fun process(
    context: Context,
    coordinator: NotchRuntimeCoordinator,
    rawTranscript: String,
  ): VoiceCommandResult {
    val cleaned = normalizeCommandText(rawTranscript)
    if (cleaned.isEmpty()) return VoiceCommandResult.NotMatched
    val compactPhrase = stripFillerArticles(cleaned)

    // 1. Focus Timer (check before general "start <app>" or polarity rules)
    when (compactPhrase) {
      "pause timer",
      "stop timer",
      "hold timer",
      "freeze timer" -> {
        return if (coordinator.runtimeState.value.timer.isRunning) {
          coordinator.startPauseTimer()
          VoiceCommandResult.Executed("Timer Paused")
        } else {
          VoiceCommandResult.Executed("Timer Already Paused")
        }
      }

      "resume timer",
      "continue timer",
      "unpause timer",
      "start timer" -> {
        return if (!coordinator.runtimeState.value.timer.isRunning) {
          coordinator.startPauseTimer()
          VoiceCommandResult.Executed("Timer Resumed")
        } else {
          VoiceCommandResult.Executed("Timer Already Running")
        }
      }

      "reset timer",
      "restart timer",
      "clear timer",
      "cancel timer" -> {
        coordinator.resetTimer()
        return VoiceCommandResult.Executed("Timer Reset")
      }

      "pomodoro",
      "start pomodoro",
      "set pomodoro",
      "start a pomodoro",
      "focus session",
      "start focus session" -> {
        coordinator.startTimerForVoice(25)
        return VoiceCommandResult.Executed("Timer: 25m Started")
      }
    }

    parseTimerMinutes(compactPhrase)?.let { minutes ->
      coordinator.startTimerForVoice(minutes)
      return VoiceCommandResult.Executed("Timer: ${minutes}m Started")
    }

    // 2. Media Controls (state-aware Play/Pause + Next/Previous)
    if (compactPhrase in PLAY_PHRASES) {
      return when (coordinator.setMediaPlaybackForVoice(play = true)) {
        is VoiceSwitchOutcome.Already -> VoiceCommandResult.Executed("Already Playing")
        else -> VoiceCommandResult.Executed("Media Playing")
      }
    }
    if (compactPhrase in PAUSE_PHRASES) {
      return when (coordinator.setMediaPlaybackForVoice(play = false)) {
        is VoiceSwitchOutcome.Already -> VoiceCommandResult.Executed("Already Paused")
        else -> VoiceCommandResult.Executed("Media Paused")
      }
    }
    if (compactPhrase in NEXT_PHRASES) {
      coordinator.skipNext()
      return VoiceCommandResult.Executed("Skipped Track")
    }
    if (compactPhrase in PREVIOUS_PHRASES) {
      coordinator.skipPrevious()
      return VoiceCommandResult.Executed("Previous Track")
    }

    // 3. Screenshot & Lock Screen
    if (compactPhrase in SCREENSHOT_PHRASES) {
      return if (NotchAccessibilityService.isConnected) {
        coordinator.triggerScreenshot()
        VoiceCommandResult.Executed("Taking Screenshot", autoCloseDelayMs = null)
      } else {
        VoiceCommandResult.Failed("Enable Lockscreen & Device Actions bridge in Settings")
      }
    }
    if (compactPhrase in LOCK_SCREEN_PHRASES) {
      return if (NotchAccessibilityService.isConnected) {
        coordinator.triggerLockScreen()
        VoiceCommandResult.Executed("Screen Locked", autoCloseDelayMs = null)
      } else {
        VoiceCommandResult.Failed("Enable Lockscreen & Device Actions bridge in Settings")
      }
    }

    // 4. Device Toggles & Natural Idioms (Flashlight, DND, Auto-Rotate, Silent, Wi-Fi, Bluetooth)
    parseToggleIntent(compactPhrase)?.let { (feature, polarity) ->
      return executeToggleFeature(coordinator, feature, polarity)
    }

    // 5. Launch Any Installed App ("open <app>", "launch <app>", "start <app>", "switch to <app>")
    val openMatch = OPEN_APP_REGEX.matchEntire(cleaned)
    if (openMatch != null) {
      val requestedRaw = openMatch.groupValues[1].trim()
      if (requestedRaw.isNotEmpty()) {
        val apps = SystemBridges.queryLaunchableApps(context)
        val matchedApp = findBestMatchingApp(apps, requestedRaw)
        if (matchedApp != null) {
          coordinator.collapse()
          val launched = SystemBridges.launchApp(context, matchedApp.packageName)
          return if (launched) {
            VoiceCommandResult.Executed("Opened ${matchedApp.label}", autoCloseDelayMs = null)
          } else {
            VoiceCommandResult.Failed("Unable to open ${matchedApp.label}")
          }
        }
        return VoiceCommandResult.Failed("App '$requestedRaw' not found")
      }
    }

    // 6. Instant Local System Info Queries (Time, Date, Day, Month, Year, Battery)
    matchSystemInfoQuery(context, compactPhrase)?.let { return it }

    return VoiceCommandResult.NotMatched
  }

  internal fun matchSystemInfoQuery(
    context: Context,
    phrase: String,
    now: Date = Date(),
    timeZone: TimeZone = TimeZone.getDefault(),
  ): VoiceCommandResult.InfoAnswer? {
    val compactPhrase = stripFillerArticles(phrase.trim())
    if (compactPhrase.isEmpty()) return null
    val normalizedQuery =
      compactPhrase
        .removePrefix("tell me ")
        .removePrefix("show me ")
        .removePrefix("give me ")
        .trim()
    if (NON_CURRENT_INFO_MARKERS.any { normalizedQuery.contains(it) }) return null

    if (
      compactPhrase in BATTERY_PHRASES ||
      normalizedQuery in BATTERY_PHRASES ||
      BATTERY_QUERY_REGEX.matches(normalizedQuery)
    ) {
      val (percent, charging) = SystemBridges.readBatteryTelemetry(context)
      val status = if (charging) "Charging" else "On battery"
      return VoiceCommandResult.InfoAnswer(
        title = "Battery Status",
        answerText = "Battery: $percent% ($status)",
      )
    }

    if (normalizedQuery.contains(" in ")) return null

    if (
      compactPhrase in TIME_PHRASES ||
      normalizedQuery in TIME_PHRASES ||
      TIME_QUERY_REGEX.matches(compactPhrase)
    ) {
      return VoiceCommandResult.InfoAnswer(
        title = "Local Time",
        answerText = formatLocalTimeAnswer(now = now, timeZone = timeZone),
      )
    }

    if (
      compactPhrase in DATE_DAY_YEAR_PHRASES ||
      normalizedQuery in DATE_DAY_YEAR_PHRASES ||
      normalizedQuery == "today date"
    ) {
      return VoiceCommandResult.InfoAnswer(
        title = "Today's Date",
        answerText = formatLocalDateAnswer(now = now, timeZone = timeZone),
      )
    }

    return null
  }

  internal fun formatLocalTimeAnswer(
    now: Date = Date(),
    timeZone: TimeZone = TimeZone.getDefault(),
  ): String {
    val timePart =
      SimpleDateFormat("hh:mm a", Locale.ENGLISH)
        .apply { this.timeZone = timeZone }
        .format(now)
    val offsetMillis = timeZone.getOffset(now.time)
    val sign = if (offsetMillis >= 0) "+" else "-"
    val absMinutes = abs(offsetMillis) / 60_000
    val hours = absMinutes / 60
    val mins = absMinutes % 60
    val gmtOffset = String.format(Locale.ENGLISH, "GMT%s%02d:%02d", sign, hours, mins)
    return "$timePart ($gmtOffset)"
  }

  internal fun formatLocalDateAnswer(
    now: Date = Date(),
    timeZone: TimeZone = TimeZone.getDefault(),
  ): String =
    SimpleDateFormat("EEEE, MMMM d, yyyy '•' HH:mm", Locale.ENGLISH)
      .apply { this.timeZone = timeZone }
      .format(now)

  private fun executeToggleFeature(
    coordinator: NotchRuntimeCoordinator,
    feature: ToggleFeature,
    polarity: Polarity,
  ): VoiceCommandResult {
    val targetEnabled: Boolean? =
      when (polarity) {
        Polarity.ON -> true
        Polarity.OFF -> false
        Polarity.TOGGLE -> null
      }

    val outcome =
      when (feature) {
        ToggleFeature.FLASHLIGHT -> coordinator.setTorchForVoice(targetEnabled)
        ToggleFeature.DND -> coordinator.setDndForVoice(targetEnabled)
        ToggleFeature.AUTO_ROTATE -> coordinator.setAutoRotateForVoice(targetEnabled)
        ToggleFeature.SILENT -> coordinator.setSilentModeForVoice(targetEnabled)
        ToggleFeature.WIFI -> coordinator.setWifiForVoice(targetEnabled)
        ToggleFeature.BLUETOOTH -> coordinator.setBluetoothForVoice(targetEnabled)
      }

    return when (outcome) {
      is VoiceSwitchOutcome.Changed -> {
        val stateLabel = if (outcome.enabled) "On" else "Off"
        VoiceCommandResult.Executed("${feature.displayName} $stateLabel")
      }

      is VoiceSwitchOutcome.Already -> {
        val stateLabel = if (outcome.enabled) "Already On" else "Already Off"
        VoiceCommandResult.Executed("${feature.displayName} $stateLabel")
      }

      is VoiceSwitchOutcome.PermissionRequired -> VoiceCommandResult.Failed(outcome.message)

      VoiceSwitchOutcome.OpenedQuickPanel ->
        VoiceCommandResult.Executed("Opened ${feature.displayName}", autoCloseDelayMs = null)

      VoiceSwitchOutcome.Unavailable ->
        VoiceCommandResult.Failed(
          if (feature == ToggleFeature.FLASHLIGHT) "Camera flash unavailable"
          else "${feature.displayName} unavailable"
        )
    }
  }

  private fun parseToggleIntent(cleaned: String): Pair<ToggleFeature, Polarity>? {
    // Explicit idiomatic phrases first
    when (cleaned) {
      "lights on",
      "light on",
      "i need light",
      "need flashlight",
      "need a flashlight",
      "beam on" -> return ToggleFeature.FLASHLIGHT to Polarity.ON

      "lights off",
      "light off",
      "kill light",
      "kill the light",
      "flashlight out",
      "torch out" -> return ToggleFeature.FLASHLIGHT to Polarity.OFF

      "mute",
      "mute phone",
      "mute ringer",
      "mute sound",
      "mute volume",
      "silence",
      "silence phone",
      "silence ringer",
      "put phone on silent",
      "put on silent",
      "set phone to silent",
      "be quiet",
      "quiet phone" -> return ToggleFeature.SILENT to Polarity.ON

      "unmute",
      "unmute phone",
      "unmute ringer",
      "unmute sound",
      "sound on",
      "ring on",
      "ringer on",
      "ringtone on",
      "normal mode",
      "loud mode",
      "restore sound" -> return ToggleFeature.SILENT to Polarity.OFF

      "unlock rotation",
      "unlock screen rotation",
      "allow rotation",
      "allow screen rotation",
      "rotate screen on" -> return ToggleFeature.AUTO_ROTATE to Polarity.ON

      "lock rotation",
      "lock screen rotation",
      "freeze rotation",
      "stop rotation",
      "portrait lock" -> return ToggleFeature.AUTO_ROTATE to Polarity.OFF

      "quiet mode",
      "quiet mode on",
      "focus mode",
      "focus mode on",
      "mute notifications",
      "block notifications",
      "hide notifications" -> return ToggleFeature.DND to Polarity.ON

      "quiet mode off",
      "focus mode off",
      "allow notifications",
      "unblock notifications" -> return ToggleFeature.DND to Polarity.OFF
    }

    var phrase = cleaned
    var polarity: Polarity? = null

    // Check trailing polarity first ("turn the flashlight on" -> "turn the flashlight" + ON)
    if (phrase.endsWith(" on") && phrase.length > 3) {
      polarity = Polarity.ON
      phrase = phrase.removeSuffix(" on").trim()
    } else if (phrase.endsWith(" off") && phrase.length > 4) {
      polarity = Polarity.OFF
      phrase = phrase.removeSuffix(" off").trim()
    }

    // Check leading polarity verbs ("activate flashlight", "turn off wifi", "disable bluetooth")
    for (verb in ON_VERBS) {
      if (phrase.startsWith("$verb ")) {
        if (polarity == null) polarity = Polarity.ON
        phrase = phrase.removePrefix("$verb ").trim()
        break
      }
    }
    if (polarity == null || phrase == cleaned) {
      for (verb in OFF_VERBS) {
        if (phrase.startsWith("$verb ")) {
          if (polarity == null) polarity = Polarity.OFF
          phrase = phrase.removePrefix("$verb ").trim()
          break
        }
      }
    }
    if (polarity == null) {
      for (verb in TOGGLE_VERBS) {
        if (phrase.startsWith("$verb ")) {
          polarity = Polarity.TOGGLE
          phrase = phrase.removePrefix("$verb ").trim()
          break
        }
      }
    }

    // Also strip neutral "turn" / "put" / "set" left behind after trailing on/off removal
    for (leftover in listOf("turn ", "switch ", "put ", "set ", "make ")) {
      if (phrase.startsWith(leftover)) {
        phrase = phrase.removePrefix(leftover).trim()
      }
    }
    for (article in listOf("the ", "my ", "a ")) {
      if (phrase.startsWith(article)) {
        phrase = phrase.removePrefix(article).trim()
      }
    }

    val feature = matchToggleSubject(phrase) ?: return null
    return feature to (polarity ?: Polarity.TOGGLE)
  }

  private fun matchToggleSubject(subject: String): ToggleFeature? =
    when (subject) {
      "flashlight",
      "flash light",
      "torch",
      "flash",
      "camera light",
      "back light" -> ToggleFeature.FLASHLIGHT

      "do not disturb",
      "donot disturb",
      "dnd",
      "d n d",
      "disturb mode" -> ToggleFeature.DND

      "auto rotate",
      "autorotate",
      "auto-rotate",
      "auto rotation",
      "screen rotation",
      "rotation",
      "screen rotate",
      "rotate" -> ToggleFeature.AUTO_ROTATE

      "silent",
      "silent mode",
      "silence mode",
      "mute mode",
      "ringer mute" -> ToggleFeature.SILENT

      "wifi",
      "wi-fi",
      "wi fi",
      "wireless",
      "internet" -> ToggleFeature.WIFI

      "bluetooth",
      "blue tooth",
      "bt" -> ToggleFeature.BLUETOOTH

      else -> null
    }

  internal fun normalizeCommandText(raw: String): String {
    var text =
      raw
        .trim()
        .lowercase()
        .replace('\u2019', '\'')
        .replace("don't", "do not")
        .replace("can't", "cant")
        .replace("won't", "wont")
        .replace("let's", "lets")
        .replace("i'd", "id")
        .replace("i'm", "im")
        .replace("what's", "what is")
        .replace("it's", "it is")
        .replace("today's", "today")
        .replace(Regex("\\bwhats\\b"), "what is")
        .replace(Regex("\\btodays\\b"), "today")
        .replace(Regex("[^a-z0-9\\s-]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    if (text.isEmpty()) return ""

    var changed = true
    while (changed) {
      changed = false
      for (prefix in CONVERSATIONAL_PREFIXES) {
        if (text.startsWith("$prefix ")) {
          text = text.removePrefix("$prefix ").trim()
          changed = true
          break
        }
      }
      for (suffix in CONVERSATIONAL_SUFFIXES) {
        if (text.endsWith(" $suffix") && text.length > suffix.length + 1) {
          text = text.removeSuffix(" $suffix").trim()
          changed = true
          break
        }
      }
    }
    return text
  }

  private fun stripFillerArticles(text: String): String =
    text
      .split(" ")
      .filterNot { it == "the" || it == "my" }
      .joinToString(" ")

  internal fun parseTimerMinutes(normalized: String): Int? {
    if (normalized.contains("half an hour") || normalized.contains("half hour")) {
      return 30
    }
    for (regex in TIMER_PATTERNS) {
      val match = regex.matchEntire(normalized) ?: continue
      val qtyToken = match.groupValues[1].trim()
      val unitToken = match.groupValues[2].trim()
      val baseNumber = qtyToken.toIntOrNull() ?: NUMBER_WORDS[qtyToken] ?: continue
      val minutes =
        if (unitToken.startsWith("hour") || unitToken.startsWith("hr")) {
          baseNumber * 60
        } else {
          baseNumber
        }
      if (minutes in 1..240) return minutes
    }
    return null
  }

  internal fun findBestMatchingApp(
    apps: List<LaunchableAppInfo>,
    spokenTarget: String,
  ): LaunchableAppInfo? {
    val rawQuery = spokenTarget.trim().lowercase()
    if (rawQuery.isEmpty()) return null
    val candidates =
      listOf(rawQuery, rawQuery.removeSuffix(" app").trim()).filter { it.isNotEmpty() }.distinct()

    for (query in candidates) {
      val compactQuery = query.replace(" ", "")
      val matched =
        apps.firstOrNull { it.label.equals(query, ignoreCase = true) }
          ?: apps
            .filter { it.label.startsWith(query, ignoreCase = true) }
            .minByOrNull { it.label.length }
          ?: apps
            .filter { it.label.contains(query, ignoreCase = true) }
            .minByOrNull { it.label.length }
          ?: apps.firstOrNull { app ->
            app.label.lowercase().replace(" ", "").contains(compactQuery)
          }
      if (matched != null) return matched
    }
    return null
  }
}
