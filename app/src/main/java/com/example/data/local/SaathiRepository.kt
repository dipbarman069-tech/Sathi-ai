package com.example.data.local

import android.content.Context
import com.example.data.security.LocalCryptoManager
import com.example.domain.model.ActionExecutionStatus
import com.example.domain.model.ActionLogUi
import com.example.domain.model.BlocklistedAppUi
import com.example.domain.model.ChatMessageUi
import com.example.domain.model.ChatRole
import com.example.domain.model.ParsedIntent
import com.example.domain.model.RiskLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SaathiRepository(
  private val dao: SaathiDao,
  val preferences: SaathiPreferences,
) {
  companion object {
    @Volatile private var INSTANCE: SaathiRepository? = null

    fun getInstance(context: Context): SaathiRepository {
      return INSTANCE
        ?: synchronized(this) {
          INSTANCE
            ?: SaathiRepository(
                dao = SaathiDatabase.getInstance(context).saathiDao(),
                preferences = SaathiPreferences(context.applicationContext),
              )
              .also { INSTANCE = it }
        }
    }
  }

  val messagesFlow: Flow<List<ChatMessageUi>> =
    dao.observeMessages().map { list ->
      list.map { entity ->
        ChatMessageUi(
          id = entity.id,
          role = runCatching { ChatRole.valueOf(entity.role) }.getOrDefault(ChatRole.ASSISTANT),
          text = LocalCryptoManager.decrypt(entity.encryptedText),
          timestampMs = entity.timestampMs,
          parsedJson = LocalCryptoManager.decrypt(entity.encryptedParsedJson),
          actionName = entity.actionName,
          riskLevel = runCatching { RiskLevel.valueOf(entity.riskLevel) }.getOrDefault(RiskLevel.SAFE),
        )
      }
    }

  val actionLogsFlow: Flow<List<ActionLogUi>> =
    dao.observeActionLogs().map { list ->
      list.map { entity ->
        ActionLogUi(
          id = entity.id,
          timestampMs = entity.timestampMs,
          rawCommand = LocalCryptoManager.decrypt(entity.encryptedRawCommand),
          action = entity.action,
          target = LocalCryptoManager.decrypt(entity.encryptedTarget),
          parametersJson = LocalCryptoManager.decrypt(entity.encryptedParametersJson),
          riskLevel = runCatching { RiskLevel.valueOf(entity.riskLevel) }.getOrDefault(RiskLevel.SAFE),
          status =
            runCatching { ActionExecutionStatus.valueOf(entity.status) }
              .getOrDefault(ActionExecutionStatus.EXECUTED),
          outcomeNote = LocalCryptoManager.decrypt(entity.encryptedOutcomeNote),
        )
      }
    }

  val blocklistedAppsFlow: Flow<List<BlocklistedAppUi>> =
    dao.observeBlocklistedApps().map { list ->
      list.map { BlocklistedAppUi(it.packageName, it.appName, it.category, it.isLocked) }
    }

  suspend fun seedDefaultsIfNeeded() {
    dao.insertAllBlocklistedIgnore(SaathiDatabase.DEFAULT_SENSITIVE_APPS)
  }

  suspend fun addMessage(
    role: ChatRole,
    text: String,
    parsedJson: String = "",
    actionName: String = "",
    riskLevel: RiskLevel = RiskLevel.SAFE,
  ): Long {
    return dao.insertMessage(
      ChatMessageEntity(
        role = role.name,
        encryptedText = LocalCryptoManager.encrypt(text),
        encryptedParsedJson = LocalCryptoManager.encrypt(parsedJson),
        actionName = actionName,
        riskLevel = riskLevel.name,
      )
    )
  }

  suspend fun logAction(
    intent: ParsedIntent,
    status: ActionExecutionStatus,
    outcomeNote: String,
  ): Long {
    val paramsString =
      intent.parameters.entries.joinToString(", ") { "${it.key}=${it.value}" }
    return dao.insertActionLog(
      ActionLogEntity(
        encryptedRawCommand = LocalCryptoManager.encrypt(intent.rawUtterance),
        action = intent.action.name,
        encryptedTarget = LocalCryptoManager.encrypt(intent.target),
        encryptedParametersJson = LocalCryptoManager.encrypt(paramsString),
        riskLevel = intent.riskLevel.name,
        status = status.name,
        encryptedOutcomeNote = LocalCryptoManager.encrypt(outcomeNote),
      )
    )
  }

  suspend fun clearMessages() = dao.clearMessages()

  suspend fun clearActionLogs() = dao.clearActionLogs()

  suspend fun isPackageBlocked(packageName: String): Boolean {
    val apps = dao.getBlocklistedAppsOnce()
    return apps.any { it.packageName.equals(packageName, ignoreCase = true) && it.isLocked }
  }

  suspend fun findBlockedMatchByName(query: String): BlocklistedAppUi? {
    if (query.isBlank()) return null
    val q = query.lowercase().trim()
    return dao
      .getBlocklistedAppsOnce()
      .firstOrNull { app ->
        app.isLocked &&
          (app.appName.lowercase().contains(q) ||
            q.contains(app.appName.lowercase().substringBefore(" ")) ||
            app.packageName.lowercase().contains(q) ||
            (q.contains("gpay") && app.packageName.contains("paisa")))
      }
      ?.let { BlocklistedAppUi(it.packageName, it.appName, it.category, it.isLocked) }
  }

  suspend fun setBlocklistedAppLocked(packageName: String, locked: Boolean) {
    dao.setAppLocked(packageName, locked)
  }

  suspend fun lockAllSensitiveApps() {
    dao.lockAllBlocklistedApps()
  }

  suspend fun addAppToBlocklist(packageName: String, appName: String, category: String = "User Guarded") {
    dao.upsertBlocklistedApp(BlocklistedAppEntity(packageName, appName, category, true))
  }

  suspend fun removeFromBlocklist(packageName: String) {
    dao.removeBlocklistedApp(packageName)
  }
}
