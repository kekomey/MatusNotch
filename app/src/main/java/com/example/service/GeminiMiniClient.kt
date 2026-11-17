package com.example.service

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed interface GeminiMiniResult {
  data class Success(val replyText: String) : GeminiMiniResult
  data class Error(val message: String) : GeminiMiniResult
}

object GeminiMiniClient {

  internal const val PRIMARY_MODEL = "gemini-3-flash-preview"
  internal const val FALLBACK_MODEL = "gemini-3.1-flash-lite-preview"

  private const val BASE_SYSTEM_PROMPT =
    "You are Matus, a crisp voice assistant inside a small Android dynamic notch overlay. " +
      "Reply clearly and directly in 1–2 complete sentences in plain text without markdown or trailing off."

  internal fun formatLiveDeviceDateTime(
    date: Date = Date(),
    timeZone: TimeZone = TimeZone.getDefault(),
  ): String =
    SimpleDateFormat("EEEE, MMMM d, yyyy 'at' HH:mm z", Locale.ENGLISH)
      .apply { this.timeZone = timeZone }
      .format(date)

  internal fun buildSystemInstruction(liveDateTimeString: String): String =
    "$BASE_SYSTEM_PROMPT Current live device date, day, and time: $liveDateTimeString. " +
      "Always treat this exact timestamp as the present moment."

  internal fun buildContextualUserPrompt(userPrompt: String, liveDateTimeString: String): String =
    "[Current Device Context: Today is $liveDateTimeString. Always use this exact date, day, and time for any temporal questions like today, tomorrow, yesterday, or current year.] User question: $userPrompt"

  internal fun buildModelEndpoint(modelId: String): String =
    "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent"

  internal fun shouldRetryWithFallback(httpCode: Int, errorMessage: String?): Boolean {
    if (httpCode == 404 || httpCode == 503) return true
    val msg = errorMessage?.lowercase().orEmpty()
    if (msg.isEmpty()) return false
    return msg.contains("not found") ||
      msg.contains("not supported") ||
      msg.contains("unavailable") ||
      msg.contains("overloaded") ||
      msg.contains("does not exist") ||
      msg.contains("capacity")
  }

  private sealed interface AttemptOutcome {
    data class Ok(val replyText: String) : AttemptOutcome
    data class HttpFailure(val code: Int, val rawApiMessage: String?, val friendlyMessage: String) :
      AttemptOutcome
    data class NetworkFailure(val message: String) : AttemptOutcome
  }

  suspend fun generateShortReply(apiKey: String, prompt: String): GeminiMiniResult =
    withContext(Dispatchers.IO) {
      val cleanKey = apiKey.trim()
      val cleanPrompt = prompt.trim()
      if (cleanKey.isEmpty()) {
        return@withContext GeminiMiniResult.Error(
          "Add your free Gemini API key in Settings for AI answers"
        )
      }
      if (cleanPrompt.isEmpty()) {
        return@withContext GeminiMiniResult.Error("No spoken prompt to answer")
      }

      val liveDateTimeString = formatLiveDeviceDateTime()

      when (val primary = executeRequest(cleanKey, cleanPrompt, liveDateTimeString, PRIMARY_MODEL)) {
        is AttemptOutcome.Ok -> GeminiMiniResult.Success(primary.replyText)
        is AttemptOutcome.HttpFailure -> {
          if (shouldRetryWithFallback(primary.code, primary.rawApiMessage)) {
            when (
              val fallback =
                executeRequest(cleanKey, cleanPrompt, liveDateTimeString, FALLBACK_MODEL)
            ) {
              is AttemptOutcome.Ok -> GeminiMiniResult.Success(fallback.replyText)
              is AttemptOutcome.HttpFailure -> GeminiMiniResult.Error(fallback.friendlyMessage)
              is AttemptOutcome.NetworkFailure -> GeminiMiniResult.Error(fallback.message)
            }
          } else {
            GeminiMiniResult.Error(primary.friendlyMessage)
          }
        }
        is AttemptOutcome.NetworkFailure -> GeminiMiniResult.Error(primary.message)
      }
    }

  private fun executeRequest(
    apiKey: String,
    prompt: String,
    liveDateTimeString: String,
    modelId: String,
  ): AttemptOutcome {
    var connection: HttpURLConnection? = null
    return try {
      val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
      val url = URL("${buildModelEndpoint(modelId)}?key=$encodedKey")
      connection =
        (url.openConnection() as HttpURLConnection).apply {
          requestMethod = "POST"
          connectTimeout = 10_000
          readTimeout = 15_000
          doOutput = true
          setRequestProperty("Content-Type", "application/json; charset=UTF-8")
          setRequestProperty("Accept", "application/json")
        }

      val payload = buildRequestPayload(prompt, liveDateTimeString)
      OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
        writer.write(payload)
        writer.flush()
      }

      val code = connection.responseCode
      val stream =
        if (code in 200..299) connection.inputStream
        else (connection.errorStream ?: connection.inputStream)
      val body =
        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
          reader.readText()
        }

      if (code in 200..299) {
        val reply = parseGeminiResponseBody(body)
        if (!reply.isNullOrBlank()) {
          AttemptOutcome.Ok(reply)
        } else {
          AttemptOutcome.HttpFailure(code, null, "Gemini returned an empty response")
        }
      } else {
        val apiMsg = parseGeminiErrorMessage(body)
        val friendly =
          when {
            code == 400 || code == 401 || code == 403 ->
              apiMsg?.takeIf { it.isNotBlank() } ?: "Invalid Gemini API key — check Settings"
            code == 429 -> "Gemini rate limit reached — try again shortly"
            else -> apiMsg?.takeIf { it.isNotBlank() } ?: "Gemini error (HTTP $code)"
          }
        AttemptOutcome.HttpFailure(code, apiMsg, friendly)
      }
    } catch (_: Exception) {
      AttemptOutcome.NetworkFailure("Could not reach Gemini. Check your internet connection.")
    } finally {
      connection?.disconnect()
    }
  }

  internal fun buildRequestPayload(
    prompt: String,
    liveDateTimeString: String = formatLiveDeviceDateTime(),
  ): String {
    val root =
      JSONObject().apply {
        put(
          "systemInstruction",
          JSONObject().put(
            "parts",
            JSONArray().put(
              JSONObject().put("text", buildSystemInstruction(liveDateTimeString))
            ),
          ),
        )
        put(
          "contents",
          JSONArray().put(
            JSONObject().apply {
              put("role", "user")
              put(
                "parts",
                JSONArray().put(
                  JSONObject().put("text", buildContextualUserPrompt(prompt, liveDateTimeString))
                ),
              )
            }
          ),
        )
        put(
          "generationConfig",
          JSONObject().apply {
            put("temperature", 0.4)
            put("maxOutputTokens", 512)
            put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
          },
        )
      }
    return root.toString()
  }

  internal fun parseGeminiResponseBody(jsonText: String): String? =
    runCatching {
      val root = JSONObject(jsonText)
      val candidates = root.optJSONArray("candidates") ?: return@runCatching null
      if (candidates.length() == 0) return@runCatching null
      val firstCandidate = candidates.optJSONObject(0) ?: return@runCatching null
      val content = firstCandidate.optJSONObject("content") ?: return@runCatching null
      val parts = content.optJSONArray("parts") ?: return@runCatching null
      val collected =
        buildList {
          for (i in 0 until parts.length()) {
            val partText = parts.optJSONObject(i)?.optString("text", "")?.trim().orEmpty()
            if (partText.isNotEmpty()) add(partText)
          }
        }
      collected.joinToString(" ").trim().takeIf { it.isNotEmpty() }
    }.getOrNull()

  internal fun parseGeminiErrorMessage(jsonText: String): String? =
    runCatching {
      val root = JSONObject(jsonText)
      root.optJSONObject("error")?.optString("message")?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()
}
