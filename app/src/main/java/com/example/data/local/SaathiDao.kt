package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SaathiDao {
  @Query("SELECT * FROM chat_messages ORDER BY timestampMs ASC, id ASC")
  fun observeMessages(): Flow<List<ChatMessageEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMessage(entity: ChatMessageEntity): Long

  @Query("DELETE FROM chat_messages")
  suspend fun clearMessages()

  @Query("SELECT * FROM action_logs ORDER BY timestampMs DESC, id DESC")
  fun observeActionLogs(): Flow<List<ActionLogEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertActionLog(entity: ActionLogEntity): Long

  @Query("DELETE FROM action_logs")
  suspend fun clearActionLogs()

  @Query("SELECT * FROM blocklisted_apps ORDER BY appName ASC")
  fun observeBlocklistedApps(): Flow<List<BlocklistedAppEntity>>

  @Query("SELECT * FROM blocklisted_apps")
  suspend fun getBlocklistedAppsOnce(): List<BlocklistedAppEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertBlocklistedApp(app: BlocklistedAppEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertAllBlocklistedIgnore(apps: List<BlocklistedAppEntity>)

  @Query("UPDATE blocklisted_apps SET isLocked = :locked WHERE packageName = :packageName")
  suspend fun setAppLocked(packageName: String, locked: Boolean)

  @Query("UPDATE blocklisted_apps SET isLocked = 1")
  suspend fun lockAllBlocklistedApps()

  @Query("DELETE FROM blocklisted_apps WHERE packageName = :packageName")
  suspend fun removeBlocklistedApp(packageName: String)
}
