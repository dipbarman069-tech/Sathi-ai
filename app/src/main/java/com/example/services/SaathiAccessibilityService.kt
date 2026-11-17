package com.example.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.local.SaathiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking

/**
 * AccessibilityService for explicit, user-commanded screen actions ONLY:
 * screenshot, scroll, tap by label, type text.
 * Enforces blocklist check on the currently visible app before any action executes.
 */
class SaathiAccessibilityService : AccessibilityService() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  @Volatile private var currentForegroundPackage: String = ""

  override fun onServiceConnected() {
    super.onServiceConnected()
    instance = this
    _isConnected.value = true
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    val pkg = event?.packageName?.toString()
    if (!pkg.isNullOrBlank()) {
      currentForegroundPackage = pkg
    }
  }

  override fun onInterrupt() {
    // No autonomous background state to retain
  }

  override fun onDestroy() {
    if (instance === this) instance = null
    _isConnected.value = false
    super.onDestroy()
  }

  private fun isCurrentAppBlocked(): Boolean {
    val pkg = currentForegroundPackage.ifBlank { rootInActiveWindow?.packageName?.toString().orEmpty() }
    if (pkg.isBlank()) return false
    return runBlocking { SaathiRepository.getInstance(applicationContext).isPackageBlocked(pkg) }
  }

  fun takeScreenshotOnCommand(): Result<String> {
    if (isCurrentAppBlocked()) {
      return Result.failure(IllegalStateException("Blocked: Active app is protected on your blocklist."))
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val ok = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
      if (ok) Result.success("Screenshot captured.")
      else Result.failure(IllegalStateException("System screenshot action returned false."))
    } else {
      Result.failure(UnsupportedOperationException("Global screenshot requires Android 9 or higher."))
    }
  }

  fun scrollOnCommand(direction: String): Result<String> {
    if (isCurrentAppBlocked()) {
      return Result.failure(IllegalStateException("Blocked: Active app is protected on your blocklist."))
    }
    val dm = resources.displayMetrics
    val cx = dm.widthPixels / 2f
    val startY: Float
    val endY: Float
    val startX: Float
    val endX: Float
    when (direction.lowercase()) {
      "up" -> {
        startX = cx; endX = cx
        startY = dm.heightPixels * 0.30f; endY = dm.heightPixels * 0.75f
      }
      "left" -> {
        startY = dm.heightPixels * 0.5f; endY = dm.heightPixels * 0.5f
        startX = dm.widthPixels * 0.8f; endX = dm.widthPixels * 0.2f
      }
      "right" -> {
        startY = dm.heightPixels * 0.5f; endY = dm.heightPixels * 0.5f
        startX = dm.widthPixels * 0.2f; endX = dm.widthPixels * 0.8f
      }
      else -> {
        startX = cx; endX = cx
        startY = dm.heightPixels * 0.75f; endY = dm.heightPixels * 0.28f
      }
    }
    val path = Path().apply {
      moveTo(startX, startY)
      lineTo(endX, endY)
    }
    val stroke = GestureDescription.StrokeDescription(path, 0, 280)
    val gesture = GestureDescription.Builder().addStroke(stroke).build()
    val dispatched = dispatchGesture(gesture, null, null)
    return if (dispatched) Result.success("Scrolled $direction.")
    else Result.failure(IllegalStateException("Unable to dispatch scroll gesture."))
  }

  fun tapByTextOnCommand(targetText: String): Result<String> {
    if (isCurrentAppBlocked()) {
      return Result.failure(IllegalStateException("Blocked: Active app is protected on your blocklist."))
    }
    val root = rootInActiveWindow ?: return Result.failure(IllegalStateException("No active screen window."))
    val match = findNodeMatchingText(root, targetText.trim())
      ?: return Result.failure(IllegalStateException("Could not find \"$targetText\" on screen."))

    var clickable: AccessibilityNodeInfo? = match
    while (clickable != null && !clickable.isClickable) {
      clickable = clickable.parent
    }
    if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
      return Result.success("Tapped \"$targetText\".")
    }

    val rect = Rect()
    match.getBoundsInScreen(rect)
    val path = Path().apply { moveTo(rect.exactCenterX(), rect.exactCenterY()) }
    val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 60)).build()
    return if (dispatchGesture(gesture, null, null)) Result.success("Tapped \"$targetText\".")
    else Result.failure(IllegalStateException("Failed to tap \"$targetText\"."))
  }

  fun typeTextOnCommand(textToType: String): Result<String> {
    if (isCurrentAppBlocked()) {
      return Result.failure(IllegalStateException("Blocked: Active app is protected on your blocklist."))
    }
    val root = rootInActiveWindow ?: return Result.failure(IllegalStateException("No active screen window."))
    val editable = findFocusedOrFirstEditable(root)
      ?: return Result.failure(IllegalStateException("No editable text field found on screen."))
    val args = Bundle().apply {
      putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
    }
    return if (editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
      Result.success("Typed text into focused field.")
    } else {
      Result.failure(IllegalStateException("Could not insert text into field."))
    }
  }

  private fun findNodeMatchingText(node: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
    val label = node.text?.toString().orEmpty()
    val desc = node.contentDescription?.toString().orEmpty()
    if (label.contains(query, ignoreCase = true) || desc.contains(query, ignoreCase = true)) {
      return node
    }
    for (i in 0 until node.childCount) {
      val child = node.getChild(i) ?: continue
      val hit = findNodeMatchingText(child, query)
      if (hit != null) return hit
    }
    return null
  }

  private fun findFocusedOrFirstEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
    if (node.isEditable && node.isFocused) return node
    for (i in 0 until node.childCount) {
      val child = node.getChild(i) ?: continue
      val focused = findFocusedOrFirstEditable(child)
      if (focused != null) return focused
    }
    if (node.isEditable) return node
    return null
  }

  companion object {
    @Volatile var instance: SaathiAccessibilityService? = null
      private set

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
  }
}
