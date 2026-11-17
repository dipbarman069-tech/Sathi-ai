package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Strict whitelist of supported phone actions.
 * Every parsed LLM or NLU JSON payload MUST match one of these actions before execution.
 */
@Serializable
enum class AssistantAction {
  OPEN_APP,
  CALL_CONTACT,
  SEND_MESSAGE,
  SET_ALARM,
  SET_TIMER,
  TOGGLE_WIFI,
  TOGGLE_BLUETOOTH,
  TOGGLE_TORCH,
  ADJUST_VOLUME,
  ADJUST_BRIGHTNESS,
  READ_NOTIFICATIONS,
  TAKE_SCREENSHOT,
  SCROLL_SCREEN,
  TAP_ELEMENT,
  TYPE_TEXT,
  SEARCH_SETTINGS,
  TELL_TIME_DATE,
  HELP_CAPABILITIES,
  UNKNOWN,
}

@Serializable
enum class RiskLevel {
  SAFE,
  MODERATE,
  HIGH,
}

@Serializable
enum class AssistantLanguage(val label: String, val localeTag: String) {
  AUTO("Auto (Hindi / Hinglish / English)", "hi-IN"),
  HINGLISH("Hinglish", "hi-IN"),
  HINDI("Hindi (Devanagari)", "hi-IN"),
  ENGLISH("English (India)", "en-IN"),
}

@Serializable
data class ParsedIntent(
  val action: AssistantAction,
  val target: String = "",
  val parameters: Map<String, String> = emptyMap(),
  val rawUtterance: String = "",
  val riskLevel: RiskLevel = RiskLevel.SAFE,
  val spokenConfirmationPrompt: String = "",
  val spokenReply: String = "",
  val language: AssistantLanguage = AssistantLanguage.HINGLISH,
  val jsonRepresentation: String = "",
)

sealed class SkillResult {
  data class Executed(val reply: String, val detail: String = "") : SkillResult()

  data class NeedsPermission(val permissionName: String, val explanation: String) : SkillResult()

  data class BlockedBySafety(val reason: String, val packageName: String? = null) : SkillResult()

  data class Failed(val errorMessage: String) : SkillResult()
}

enum class ChatRole {
  USER,
  ASSISTANT,
  SYSTEM_NOTICE,
}

data class ChatMessageUi(
  val id: Long,
  val role: ChatRole,
  val text: String,
  val timestampMs: Long,
  val parsedJson: String = "",
  val actionName: String = "",
  val riskLevel: RiskLevel = RiskLevel.SAFE,
)

enum class ActionExecutionStatus {
  EXECUTED,
  CANCELLED,
  BLOCKED,
  FAILED,
  PENDING_CONFIRMATION,
}

data class ActionLogUi(
  val id: Long,
  val timestampMs: Long,
  val rawCommand: String,
  val action: String,
  val target: String,
  val parametersJson: String,
  val riskLevel: RiskLevel,
  val status: ActionExecutionStatus,
  val outcomeNote: String,
)

data class BlocklistedAppUi(
  val packageName: String,
  val appName: String,
  val category: String,
  val isLocked: Boolean,
)

data class PermissionItemUi(
  val id: String,
  val title: String,
  val titleHindi: String,
  val explanation: String,
  val isGranted: Boolean,
  val isSpecialSystemAccess: Boolean = false,
)

enum class VoiceOrbState {
  IDLE,
  LISTENING,
  THINKING,
  SPEAKING,
  AWAITING_CONFIRMATION,
}

data class PendingConfirmation(
  val id: Long,
  val intent: ParsedIntent,
  val requiresBiometricOrPin: Boolean,
  val blockedPackageName: String? = null,
)
