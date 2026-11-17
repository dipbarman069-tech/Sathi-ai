package com.example.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

data class CapturedNotification(
  val packageName: String,
  val appLabel: String,
  val title: String,
  val text: String,
  val postTime: Long,
)

/**
 * In-memory notification listener that exposes recent notifications ONLY when the user
 * explicitly commands Saathi to read them. Never persisted or transmitted.
 */
class SaathiNotificationListenerService : NotificationListenerService() {
  override fun onListenerConnected() {
    super.onListenerConnected()
    _isConnected.value = true
    refreshSnapshot()
  }

  override fun onListenerDisconnected() {
    _isConnected.value = false
    super.onListenerDisconnected()
  }

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    if (sbn == null) return
    if ((sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
    if (sbn.packageName == packageName) return
    refreshSnapshot()
  }

  override fun onNotificationRemoved(sbn: StatusBarNotification?) {
    refreshSnapshot()
  }

  private fun refreshSnapshot() {
    try {
      val active = activeNotifications ?: return
      val pm = packageManager
      val items =
        active
          .filter {
            (it.notification.flags and Notification.FLAG_ONGOING_EVENT) == 0 &&
              it.packageName != packageName
          }
          .sortedByDescending { it.postTime }
          .take(12)
          .mapNotNull { sbn ->
            val extras = sbn.notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            if (title.isBlank() && body.isBlank()) return@mapNotNull null
            val appLabel =
              runCatching {
                  pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
                }
                .getOrDefault(sbn.packageName.substringAfterLast('.'))
            CapturedNotification(sbn.packageName, appLabel, title, body, sbn.postTime)
          }
      recentNotifications.clear()
      recentNotifications.addAll(items)
    } catch (_: Throwable) {
      // Ignore transient listener disconnects
    }
  }

  companion object {
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val recentNotifications = CopyOnWriteArrayList<CapturedNotification>()

    fun getRecentNotifications(): List<CapturedNotification> = recentNotifications.toList()

    fun clearMemoryCache() {
      recentNotifications.clear()
    }
  }
}
