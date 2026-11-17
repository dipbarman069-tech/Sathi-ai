package com.example.domain.nlu

import com.example.domain.model.AssistantAction
import com.example.domain.model.AssistantLanguage
import com.example.domain.model.ParsedIntent
import com.example.domain.model.RiskLevel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Locale

/**
 * Offline-first multilingual NLU Intent Parser (Hindi, Hinglish, English).
 * Converts natural speech/text into structured JSON actions validated against
 * the strict AssistantAction whitelist before execution.
 */
object IntentParser {
  private val jsonFormatter = Json { prettyPrint = true; ignoreUnknownKeys = true }

  fun parse(rawInput: String, preferredLanguage: AssistantLanguage): ParsedIntent {
    val clean = rawInput.trim()
    val lower = clean.lowercase(Locale.getDefault())
    val detectedLang = detectLanguage(clean, preferredLanguage)

    // 1. Check if input is already structured JSON from a local LLM (Gemma/Qwen)
    if (clean.startsWith("{") && clean.endsWith("}")) {
      parseStructuredJson(clean, clean, detectedLang)?.let { return it }
    }

    // 2. Deterministic offline multilingual intent extraction
    val draft =
      when {
        // Screenshot
        containsAny(lower, "screenshot", "screen shot", "स्क्रीनशॉट", "screen capture") ->
          buildIntent(
            action = AssistantAction.TAKE_SCREENSHOT,
            raw = clean,
            risk = RiskLevel.SAFE,
            lang = detectedLang,
            reply = replyFor(detectedLang, "Screenshot le rahi hoon.", "स्क्रीनशॉट ले रही हूँ।", "Taking a screenshot."),
          )

        // Scroll screen
        containsAny(lower, "scroll", "स्क्रॉल", "neeche jao", "upar jao", "swipe") -> {
          val dir =
            when {
              containsAny(lower, "up", "upar", "ऊपर") -> "up"
              containsAny(lower, "left", "baaye", "बाएं") -> "left"
              containsAny(lower, "right", "daaye", "दाएं") -> "right"
              else -> "down"
            }
          buildIntent(
            action = AssistantAction.SCROLL_SCREEN,
            target = dir,
            params = mapOf("direction" to dir),
            raw = clean,
            risk = RiskLevel.SAFE,
            lang = detectedLang,
            reply = replyFor(detectedLang, "Screen $dir scroll kar rahi hoon.", "स्क्रीन $dir स्क्रॉल कर रही हूँ।", "Scrolling $dir."),
          )
        }

        // Tap element on screen
        Regex("^(tap|click|press|daba|दबाओ|टैप)\\b").containsMatchIn(lower) ||
          containsAny(lower, " pe tap", " par tap", " pe click", " par click") -> {
          val target =
            clean
              .replace(Regex("(?i)\\b(tap|click|press|karo|kar|do|pe|par|on|button|दबाओ|टैप|करो)\\b"), " ")
              .trim()
          buildIntent(
            action = AssistantAction.TAP_ELEMENT,
            target = target,
            params = mapOf("label" to target),
            raw = clean,
            risk = RiskLevel.MODERATE,
            lang = detectedLang,
            confirmPrompt = "Screen par \"$target\" tap karoon?",
            reply = replyFor(detectedLang, "\"$target\" par tap kar rahi hoon.", "\"$target\" पर टैप कर रही हूँ।", "Tapping \"$target\"."),
          )
        }

        // Type text on screen
        Regex("^(type|likho|write|लिखो)\\b").containsMatchIn(lower) -> {
          val textToType =
            clean
              .replace(Regex("(?i)^(type|likho|write|लिखो)\\s*"), "")
              .trim()
          buildIntent(
            action = AssistantAction.TYPE_TEXT,
            target = textToType,
            params = mapOf("text" to textToType),
            raw = clean,
            risk = RiskLevel.MODERATE,
            lang = detectedLang,
            confirmPrompt = "Screen field mein \"$textToType\" type karoon?",
            reply = replyFor(detectedLang, "Type kar rahi hoon.", "लिख रही हूँ।", "Typing your text."),
          )
        }

        // Read notifications
        containsAny(lower, "notification", "notifications", "notifs", "नोटिफिकेशन", "sandesh padho") ->
          buildIntent(
            action = AssistantAction.READ_NOTIFICATIONS,
            raw = clean,
            risk = RiskLevel.SAFE,
            lang = detectedLang,
            reply = replyFor(detectedLang, "Notifications check kar rahi hoon.", "नोटिफिकेशन पढ़ रही हूँ।", "Checking your notifications."),
          )

        // Torch / Flashlight
        containsAny(lower, "torch", "flashlight", "flash light", "टॉर्च", "फ्लैश") -> {
          val enable = !containsAny(lower, "off", "band", "बंद", "disable", "close")
          buildIntent(
            action = AssistantAction.TOGGLE_TORCH,
            target = if (enable) "on" else "off",
            params = mapOf("state" to if (enable) "on" else "off"),
            raw = clean,
            risk = RiskLevel.SAFE,
            lang = detectedLang,
            reply =
              if (enable) replyFor(detectedLang, "Torch on kar rahi hoon.", "टॉर्च चालू कर रही हूँ।", "Turning flashlight on.")
              else replyFor(detectedLang, "Torch band kar rahi hoon.", "टॉर्च बंद कर रही हूँ।", "Turning flashlight off."),
          )
        }

        // Volume
        containsAny(lower, "volume", "awaz", "awaaz", "वॉल्यूम", "आवाज़", "mute", "unmute") -> {
          val mode =
            when {
              containsAny(lower, "mute", "silent", "band", "चुप") -> "mute"
              containsAny(lower, "down", "kam", "low", "decrease", "कम", "ghatao") -> "down"
              else -> "up"
            }
          buildIntent(
            action = AssistantAction.ADJUST_VOLUME,
            target = mode,
            params = mapOf("direction" to mode),
            raw = clean,
            risk = RiskLevel.SAFE,
            lang = detectedLang,
            reply = replyFor(detectedLang, "Volume $mode kar rahi hoon.", "वॉल्यूम सेट कर रही हूँ।", "Adjusting volume $mode."),
          )
        }

        // Brightness
        containsAny(lower, "brightness", "roshni", "ब्राइटनेस", "चमक") -> {
          val dir =
            when {
              containsAny(lower, "down", "kam", "low", "dim", "कम") -> "down"
              containsAny(lower, "max", "full") -> "max"
              else -> "up"
            }
          buildIntent(
            action = AssistantAction.ADJUST_BRIGHTNESS,
            target = dir,
            params = mapOf("direction" to dir),
            raw = clean,
            risk = RiskLevel.MODERATE,
            lang = detectedLang,
            confirmPrompt = "Screen brightness $dir change karoon?",
            reply = replyFor(detectedLang, "Brightness adjust kar rahi hoon.", "ब्राइटनेस बदल रही हूँ।", "Adjusting brightness."),
          )
        }

        // WiFi
        containsAny(lower, "wifi", "wi-fi", "वाईफाई") -> {
          val state = if (containsAny(lower, "off", "band", "बंद", "disable")) "off" else "on"
          buildIntent(
            action = AssistantAction.TOGGLE_WIFI,
            target = state,
            params = mapOf("state" to state),
            raw = clean,
            risk = RiskLevel.MODERATE,
            lang = detectedLang,
            confirmPrompt = "WiFi settings ($state) open/change karoon?",
            reply = replyFor(detectedLang, "WiFi panel khol rahi hoon.", "वाईफाई सेटिंग्स खोल रही हूँ।", "Opening Wi-Fi controls."),
          )
        }

        // Bluetooth
        containsAny(lower, "bluetooth", "ब्लूटूथ") -> {
          val state = if (containsAny(lower, "off", "band", "बंद", "disable")) "off" else "on"
          buildIntent(
            action = AssistantAction.TOGGLE_BLUETOOTH,
            target = state,
            params = mapOf("state" to state),
            raw = clean,
            risk = RiskLevel.MODERATE,
            lang = detectedLang,
            confirmPrompt = "Bluetooth ($state) settings change karoon?",
            reply = replyFor(detectedLang, "Bluetooth settings khol rahi hoon.", "ब्लूटूथ सेटिंग्स खोल रही हूँ।", "Opening Bluetooth controls."),
          )
        }

        // Set Timer
        containsAny(lower, "timer", "टाइमर") -> {
          val num = Regex("(\\d+)").find(lower)?.value?.toIntOrNull() ?: 5
          val isSeconds = containsAny(lower, "sec", "second", "सेकंड")
          val seconds = if (isSeconds) num else num * 60
          buildIntent(
            action = AssistantAction.SET_TIMER,
            target = "$seconds",
            params = mapOf("seconds" to seconds.toString(), "label" to "Saathi Timer"),
            raw = clean,
            risk = RiskLevel.SAFE,
            lang = detectedLang,
            reply =
              if (isSeconds) replyFor(detectedLang, "$num second ka timer laga rahi hoon.", "$num सेकंड का टाइमर लगा रही हूँ।", "Setting a $num-second timer.")
              else replyFor(detectedLang, "$num minute ka timer laga rahi hoon.", "$num मिनट का टाइमर लगा रही हूँ।", "Setting a $num-minute timer."),
          )
        }

        // Set Alarm
        containsAny(lower, "alarm", "अलार्म", "wake me") -> {
          val (hour, minute) = extractTime(lower)
          val formatted = String.format(Locale.US, "%02d:%02d", hour, minute)
          buildIntent(
            action = AssistantAction.SET_ALARM,
            target = formatted,
            params = mapOf("hour" to hour.toString(), "minute" to minute.toString()),
            raw = clean,
            risk = RiskLevel.SAFE,
            lang = detectedLang,
            reply = replyFor(detectedLang, "$formatted ka alarm laga rahi hoon.", "$formatted का अलार्म सेट कर रही हूँ।", "Setting an alarm for $formatted."),
          )
        }

        // Send SMS or WhatsApp Message (HIGH RISK - Confirmation Required)
        containsAny(lower, "whatsapp", "sms", "message", "msg", "मैसेज", "व्हाट्सएप", "bhejo", "भेजो") &&
          !containsAny(lower, "open whatsapp", "whatsapp kholo") -> {
          val via = if (containsAny(lower, "whatsapp", "व्हाट्सएप")) "whatsapp" else "sms"
          val (recipient, body) = extractMessageParts(clean)
          buildIntent(
            action = AssistantAction.SEND_MESSAGE,
            target = recipient.ifBlank { "Selected contact" },
            params = mapOf("channel" to via, "recipient" to recipient, "body" to body),
            raw = clean,
            risk = RiskLevel.HIGH,
            lang = detectedLang,
            confirmPrompt =
              "${via.uppercase()} message to \"${recipient.ifBlank { "contact" }}\": \"${body.ifBlank { "(empty)" }}\" — Confirm karein?",
            reply = replyFor(detectedLang, "Message bhejne ke liye confirm karein.", "मैसेज भेजने से पहले कन्फर्म करें।", "Please confirm before sending this message."),
          )
        }

        // Call contact / number (HIGH RISK - Confirmation + Auth)
        containsAny(lower, "call", "dial", "phone", "कॉल", "फ़ोन", "फोन") -> {
          val target =
            clean
              .replace(Regex("(?i)\\b(call|dial|phone|karo|kar|do|ko|please|pls|कॉल|करो|को|फ़ोन|फोन)\\b"), " ")
              .trim()
          buildIntent(
            action = AssistantAction.CALL_CONTACT,
            target = target,
            params = mapOf("contact" to target),
            raw = clean,
            risk = RiskLevel.HIGH,
            lang = detectedLang,
            confirmPrompt = "\"$target\" ko call milane ke liye confirm karein?",
            reply = replyFor(detectedLang, "\"$target\" ko call karne se pehle confirm karein.", "\"$target\" को कॉल करने से पहले कन्फर्म करें।", "Please confirm before calling $target."),
          )
        }

        // Search Settings
        containsAny(lower, "setting", "settings", "सेटिंग", "सेटिंग्स") -> {
          val query =
            clean
              .replace(Regex("(?i)\\b(search|open|kholo|settings|setting|mein|in|सेटिंग|सेटिंग्स|खोलो)\\b"), " ")
              .trim()
          buildIntent(
            action = AssistantAction.SEARCH_SETTINGS,
            target = query.ifBlank { "main" },
            params = mapOf("query" to query),
            raw = clean,
            risk = RiskLevel.MODERATE,
            lang = detectedLang,
            confirmPrompt = "System Settings (${query.ifBlank { "main" }}) kholun?",
            reply = replyFor(detectedLang, "Settings khol rahi hoon.", "सेटिंग्स खोल रही हूँ।", "Opening device settings."),
          )
        }

        // Time / Date
        containsAny(lower, "time", "samay", "kitne baje", "date", "tareekh", "समय", "तारीख", "aaj ka din") ->
          buildIntent(
            action = AssistantAction.TELL_TIME_DATE,
            target = if (containsAny(lower, "date", "tareekh", "तारीख", "din")) "date" else "time",
            raw = clean,
            risk = RiskLevel.SAFE,
            lang = detectedLang,
            reply = "",
          )

        // Open App
        containsAny(lower, "open", "kholo", "khol", "launch", "start", "chalu", "खोलो", "चालू") -> {
          val appName =
            clean
              .replace(Regex("(?i)\\b(open|launch|start|kholo|khol|karo|do|kar|chalu|app|please|खोलो|चालू|करो)\\b"), " ")
              .trim()
          val isHighRiskKeyword =
            containsAny(appName.lowercase(), "pay", "bank", "upi", "gpay", "phonepe", "paytm", "bhim", "password", "bitwarden", "1password", "play store", "delete", "uninstall")
          buildIntent(
            action = AssistantAction.OPEN_APP,
            target = appName,
            params = mapOf("appName" to appName),
            raw = clean,
            risk = if (isHighRiskKeyword) RiskLevel.HIGH else RiskLevel.SAFE,
            lang = detectedLang,
            confirmPrompt = if (isHighRiskKeyword) "\"$appName\" sensitive/protected app hai. Unlock karke kholun?" else "",
            reply = replyFor(detectedLang, "$appName khol rahi hoon.", "$appName खोल रही हूँ।", "Opening $appName."),
          )
        }

        // Help / Capabilities
        containsAny(lower, "help", "madad", "kya kar sakti", "features", "commands", "मदद", "क्या कर सकती") ->
          buildIntent(
            action = AssistantAction.HELP_CAPABILITIES,
            raw = clean,
            risk = RiskLevel.SAFE,
            lang = detectedLang,
            reply = "",
          )

        else ->
          buildIntent(
            action = AssistantAction.UNKNOWN,
            target = clean,
            raw = clean,
            risk = RiskLevel.SAFE,
            lang = detectedLang,
            reply =
              replyFor(
                detectedLang,
                "Mujhe clear command dein — jaise 'WhatsApp kholo', '5 min timer lagao', 'Torch on karo', ya 'Mom ko call karo'.",
                "कृपया स्पष्ट कमांड दें — जैसे 'टॉर्च चालू करो', '5 मिनट का टाइमर लगाओ', या 'सेटिंग्स खोलो'।",
                "Please give a clear command like 'Open YouTube', 'Set a 5 minute timer', 'Turn on torch', or 'Read notifications'.",
              ),
          )
      }

    return draft
  }

  /**
   * Parses structured JSON output produced by an on-device Gemma/Qwen LLM
   * and strictly validates `action` against the AssistantAction whitelist.
   */
  fun parseStructuredJson(jsonText: String, rawUtterance: String, language: AssistantLanguage): ParsedIntent? {
    return try {
      val obj: JsonObject = Json.parseToJsonElement(jsonText).jsonObject
      val actionStr = obj["action"]?.jsonPrimitive?.content?.uppercase(Locale.US) ?: return null
      val whitelistedAction = runCatching { AssistantAction.valueOf(actionStr) }.getOrNull() ?: return null
      val target = obj["target"]?.jsonPrimitive?.content.orEmpty()
      val params =
        obj["parameters"]?.jsonObject?.entries?.associate { it.key to it.value.jsonPrimitive.content }.orEmpty()
      val risk =
        when (whitelistedAction) {
          AssistantAction.CALL_CONTACT,
          AssistantAction.SEND_MESSAGE -> RiskLevel.HIGH
          AssistantAction.TOGGLE_WIFI,
          AssistantAction.TOGGLE_BLUETOOTH,
          AssistantAction.ADJUST_BRIGHTNESS,
          AssistantAction.TAP_ELEMENT,
          AssistantAction.TYPE_TEXT,
          AssistantAction.SEARCH_SETTINGS -> RiskLevel.MODERATE
          else -> RiskLevel.SAFE
        }
      buildIntent(
        action = whitelistedAction,
        target = target,
        params = params,
        raw = rawUtterance,
        risk = risk,
        lang = language,
        confirmPrompt = if (risk != RiskLevel.SAFE) "Confirm ${whitelistedAction.name} -> $target?" else "",
        reply = "JSON intent verified for ${whitelistedAction.name}.",
      )
    } catch (_: Throwable) {
      null
    }
  }

  private fun buildIntent(
    action: AssistantAction,
    target: String = "",
    params: Map<String, String> = emptyMap(),
    raw: String,
    risk: RiskLevel,
    lang: AssistantLanguage,
    confirmPrompt: String = "",
    reply: String,
  ): ParsedIntent {
    val jsonObj = buildJsonObject {
      put("action", action.name)
      put("target", target)
      put(
        "parameters",
        buildJsonObject { params.forEach { (k, v) -> put(k, v) } },
      )
      put("risk_level", risk.name)
      put("whitelisted", action != AssistantAction.UNKNOWN)
    }
    return ParsedIntent(
      action = action,
      target = target,
      parameters = params,
      rawUtterance = raw,
      riskLevel = risk,
      spokenConfirmationPrompt = confirmPrompt,
      spokenReply = reply,
      language = lang,
      jsonRepresentation = jsonFormatter.encodeToString(JsonObject.serializer(), jsonObj),
    )
  }

  private fun extractTime(lower: String): Pair<Int, Int> {
    val colonMatch = Regex("(\\d{1,2}):(\\d{2})").find(lower)
    var hour = colonMatch?.groupValues?.get(1)?.toIntOrNull()
    var minute = colonMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0
    if (hour == null) {
      hour = Regex("(\\d{1,2})").find(lower)?.value?.toIntOrNull() ?: 7
    }
    val isPm = containsAny(lower, "pm", "sham", "shaam", "raat", "evening", "night", "शाम", "रात")
    val isAm = containsAny(lower, "am", "subah", "morning", "सुबह")
    if (isPm && hour < 12) hour += 12
    if (isAm && hour == 12) hour = 0
    return hour.coerceIn(0, 23) to minute.coerceIn(0, 59)
  }

  private fun extractMessageParts(raw: String): Pair<String, String> {
    // Handles: "Rahul ko WhatsApp message bhejo hello bro" or "Send SMS to Mom saying I reached"
    val normalized = raw.trim()
    val toRegex = Regex("(?i)(?:to|ko)\\s+([A-Za-z0-9+ ]+?)(?:\\s+(?:saying|that|message|msg|whatsapp|sms|bhejo|likho|:)\\s+(.+))?$")
    val koBefore = Regex("(?i)^([A-Za-z0-9+ ]+?)\\s+ko\\s+(?:whatsapp|sms|message|msg)?\\s*(?:bhejo|send|likho)?\\s*(.+)?$")
    toRegex.find(normalized)?.let { m ->
      val who = m.groupValues[1].replace(Regex("(?i)\\b(whatsapp|sms|message|msg)\\b"), "").trim()
      val msg = m.groupValues[2].trim()
      if (who.isNotBlank()) return who to msg
    }
    koBefore.find(normalized)?.let { m ->
      val who = m.groupValues[1].trim()
      val msg = m.groupValues[2].trim()
      if (who.isNotBlank()) return who to msg
    }
    val cleaned =
      normalized.replace(Regex("(?i)\\b(send|bhejo|whatsapp|sms|message|msg|to|ko)\\b"), " ").trim()
    val parts = cleaned.split(" ", limit = 2)
    return (parts.getOrNull(0).orEmpty()) to (parts.getOrNull(1).orEmpty())
  }

  private fun detectLanguage(text: String, pref: AssistantLanguage): AssistantLanguage {
    if (pref != AssistantLanguage.AUTO) return pref
    val hasDevanagari = text.any { it in '\u0900'..'\u097F' }
    if (hasDevanagari) return AssistantLanguage.HINDI
    val hinglishWords = listOf("kholo", "karo", "bhejo", "lagao", "padho", "samay", "badhao", "kam", "band", "chalu")
    return if (hinglishWords.any { text.lowercase().contains(it) }) {
      AssistantLanguage.HINGLISH
    } else {
      AssistantLanguage.ENGLISH
    }
  }

  private fun replyFor(lang: AssistantLanguage, hinglish: String, hindi: String, english: String): String {
    return when (lang) {
      AssistantLanguage.HINDI -> hindi
      AssistantLanguage.ENGLISH -> english
      AssistantLanguage.HINGLISH,
      AssistantLanguage.AUTO -> hinglish
    }
  }

  private fun containsAny(text: String, vararg needles: String): Boolean {
    return needles.any { text.contains(it) }
  }
}
