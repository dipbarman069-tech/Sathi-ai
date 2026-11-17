package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val role: String,
  val encryptedText: String,
  val timestampMs: Long = System.currentTimeMillis(),
  val encryptedParsedJson: String = "",
  val actionName: String = "",
  val riskLevel: String = "SAFE",
)

@Entity(tableName = "action_logs")
data class ActionLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val timestampMs: Long = System.currentTimeMillis(),
  val encryptedRawCommand: String,
  val action: String,
  val encryptedTarget: String,
  val encryptedParametersJson: String,
  val riskLevel: String,
  val status: String,
  val encryptedOutcomeNote: String,
)

@Entity(tableName = "blocklisted_apps")
data class BlocklistedAppEntity(
  @PrimaryKey val packageName: String,
  val appName: String,
  val category: String,
  val isLocked: Boolean = true,
)
