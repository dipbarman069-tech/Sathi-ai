package com.example.domain.skills

import android.content.Context
import com.example.data.local.SaathiRepository
import com.example.domain.model.ActionExecutionStatus
import com.example.domain.model.AssistantAction
import com.example.domain.model.ParsedIntent
import com.example.domain.model.RiskLevel
import com.example.domain.model.SkillResult

sealed class GateDecision {
  data class ReadyToExecute(val intent: ParsedIntent) : GateDecision()

  data class RequiresConfirmation(
    val intent: ParsedIntent,
    val needsBiometricOrPin: Boolean,
    val blockedPackageName: String? = null,
  ) : GateDecision()

  data class Rejected(val message: String) : GateDecision()
}

/**
 * Central Safety & Whitelist Gateway.
 * - Rejects non-whitelisted/UNKNOWN intents or active Kill Switch.
 * - Enforces App Blocklist (UPI/Banking/Password apps).
 * - Requires explicit on-screen + voice confirmation (and optional Biometric/PIN) before risky actions.
 * - Logs every outcome locally encrypted in Room.
 */
class SkillRegistry(private val repository: SaathiRepository) {
  private val skills: List<AssistantSkill> =
    listOf(
      AppLaunchSkill(repository),
      CommunicationSkill(),
      ClockAlarmSkill(),
      DeviceControlSkill(),
      AccessibilityControlSkill(),
      NotificationSkill(),
      SystemInfoSkill(),
    )

  suspend fun evaluateSafetyGate(
    intent: ParsedIntent,
    killSwitchActive: Boolean,
    requireAuthForRisky: Boolean,
  ): GateDecision {
    if (killSwitchActive) {
      return GateDecision.Rejected(
        "Kill Switch active hai ('Stop Everything'). Pehle Safety screen ya top bar se resume karein."
      )
    }

    if (intent.action == AssistantAction.UNKNOWN) {
      return GateDecision.Rejected(intent.spokenReply)
    }

    // Check if target matches a locked blocklisted banking/UPI/password app
    val blockedMatch =
      if (intent.action == AssistantAction.OPEN_APP) {
        repository.findBlockedMatchByName(intent.target)
      } else {
        null
      }

    if (blockedMatch != null) {
      return GateDecision.RequiresConfirmation(
        intent =
          intent.copy(
            riskLevel = RiskLevel.HIGH,
            spokenConfirmationPrompt =
              "${blockedMatch.appName} (${blockedMatch.category}) blocklist mein locked hai. Biometric/PIN se temporarily unlock karke kholein?",
          ),
        needsBiometricOrPin = true,
        blockedPackageName = blockedMatch.packageName,
      )
    }

    if (intent.riskLevel == RiskLevel.HIGH || intent.riskLevel == RiskLevel.MODERATE) {
      return GateDecision.RequiresConfirmation(
        intent = intent,
        needsBiometricOrPin = requireAuthForRisky && intent.riskLevel == RiskLevel.HIGH,
      )
    }

    return GateDecision.ReadyToExecute(intent)
  }

  suspend fun executeWhitelisted(context: Context, intent: ParsedIntent): SkillResult {
    val handler = skills.firstOrNull { intent.action in it.supportedActions }
    if (handler == null) {
      repository.logAction(intent, ActionExecutionStatus.FAILED, "No skill registered for ${intent.action}")
      return SkillResult.Failed("Action ${intent.action.name} is not whitelisted.")
    }

    val result = handler.execute(context, intent)
    val (status, note) =
      when (result) {
        is SkillResult.Executed -> ActionExecutionStatus.EXECUTED to result.detail.ifBlank { result.reply }
        is SkillResult.BlockedBySafety -> ActionExecutionStatus.BLOCKED to result.reason
        is SkillResult.NeedsPermission -> ActionExecutionStatus.FAILED to "Missing permission: ${result.permissionName}"
        is SkillResult.Failed -> ActionExecutionStatus.FAILED to result.errorMessage
      }
    repository.logAction(intent, status, note)
    return result
  }
}
