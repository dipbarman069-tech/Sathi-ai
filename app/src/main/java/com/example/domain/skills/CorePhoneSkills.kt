package com.example.domain.skills

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.data.local.SaathiRepository
import com.example.domain.model.AssistantAction
import com.example.domain.model.ParsedIntent
import com.example.domain.model.SkillResult
import com.example.services.SaathiAccessibilityService
import com.example.services.SaathiNotificationListenerService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppLaunchSkill(private val repository: SaathiRepository) : AssistantSkill {
  override val skillId = "app_launch"
  override val supportedActions = setOf(AssistantAction.OPEN_APP)

  override suspend fun execute(context: Context, intent: ParsedIntent): SkillResult {
    val query = intent.target.ifBlank { intent.parameters["appName"].orEmpty() }.trim()
    if (query.isBlank()) {
      return SkillResult.Failed("Kaun sa app kholna hai, naam batayein.")
    }

    val pm = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolveInfos = pm.queryIntentActivities(launcherIntent, 0)
    val match =
      resolveInfos.firstOrNull { info ->
        val label = info.loadLabel(pm).toString()
        label.equals(query, ignoreCase = true) ||
          label.contains(query, ignoreCase = true) ||
          info.activityInfo.packageName.contains(query, ignoreCase = true)
      }
        ?: return SkillResult.Failed("\"$query\" app device par nahi mila.")

    val pkg = match.activityInfo.packageName
    if (repository.isPackageBlocked(pkg)) {
      val label = match.loadLabel(pm).toString()
      return SkillResult.BlockedBySafety(
        reason = "$label blocklist mein hai (Banking/UPI/Password safety guard). Security tab se manually unlock karein.",
        packageName = pkg,
      )
    }

    val launchIntent =
      pm.getLaunchIntentForPackage(pkg)?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        ?: return SkillResult.Failed("Launch intent unavailable for $pkg")

    context.startActivity(launchIntent)
    val label = match.loadLabel(pm).toString()
    return SkillResult.Executed(reply = "$label khol diya hai.", detail = "Package: $pkg")
  }
}

class CommunicationSkill : AssistantSkill {
  override val skillId = "communication"
  override val supportedActions = setOf(AssistantAction.CALL_CONTACT, AssistantAction.SEND_MESSAGE)

  override suspend fun execute(context: Context, intent: ParsedIntent): SkillResult {
    return when (intent.action) {
      AssistantAction.CALL_CONTACT -> handleCall(context, intent)
      AssistantAction.SEND_MESSAGE -> handleMessage(context, intent)
      else -> SkillResult.Failed("Unsupported action")
    }
  }

  private fun handleCall(context: Context, intent: ParsedIntent): SkillResult {
    val target = intent.target.ifBlank { intent.parameters["contact"].orEmpty() }.trim()
    if (target.isBlank()) return SkillResult.Failed("Kise call karna hai? Naam ya number batayein.")

    val digits = target.filter { it.isDigit() || it == '+' }
    val resolvedNumber =
      if (digits.length >= 6) {
        digits
      } else {
        lookupPhoneNumber(context, target)
          ?: return SkillResult.NeedsPermission(
            permissionName = Manifest.permission.READ_CONTACTS,
            explanation = "Contact \"$target\" ka number dekhne ke liye Contacts permission chahiye, ya seedha phone number bolein.",
          )
      }

    val hasCallPerm =
      ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
        PackageManager.PERMISSION_GRANTED

    val callIntent =
      if (hasCallPerm) {
        Intent(Intent.ACTION_CALL, Uri.parse("tel:$resolvedNumber"))
      } else {
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$resolvedNumber"))
      }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    context.startActivity(callIntent)
    val modeNote = if (hasCallPerm) "Direct call started" else "Dialer opened (CALL_PHONE not granted)"
    return SkillResult.Executed(
      reply = "$target ($resolvedNumber) par call start kar diya.",
      detail = modeNote,
    )
  }

  private fun handleMessage(context: Context, intent: ParsedIntent): SkillResult {
    val channel = intent.parameters["channel"] ?: "sms"
    val recipient = intent.parameters["recipient"].orEmpty().ifBlank { intent.target }
    val body = intent.parameters["body"].orEmpty()

    if (channel.equals("whatsapp", ignoreCase = true)) {
      val digits = recipient.filter { it.isDigit() }
      val uri =
        if (digits.length >= 8) {
          Uri.parse("https://wa.me/$digits?text=${Uri.encode(body)}")
        } else {
          Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode("$recipient: $body".trim())}")
        }
      val waIntent =
        Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
      return try {
        context.startActivity(waIntent)
        SkillResult.Executed("WhatsApp draft khol diya hai.", "Recipient: $recipient")
      } catch (_: Throwable) {
        SkillResult.Failed("WhatsApp is device par available nahi hai.")
      }
    }

    val phone =
      recipient.filter { it.isDigit() || it == '+' }.takeIf { it.length >= 6 }
        ?: lookupPhoneNumber(context, recipient).orEmpty()
    val smsIntent =
      Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(phone)}")).apply {
        putExtra("sms_body", body)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    return try {
      context.startActivity(smsIntent)
      SkillResult.Executed("SMS composer khol diya hai.", "To: ${phone.ifBlank { recipient }}")
    } catch (_: Throwable) {
      SkillResult.Failed("SMS app unavailable.")
    }
  }

  private fun lookupPhoneNumber(context: Context, nameQuery: String): String? {
    if (
      ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
      return null
    }
    val cursor =
      context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
          ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
          ContactsContract.CommonDataKinds.Phone.NUMBER,
        ),
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
        arrayOf("%$nameQuery%"),
        null,
      )
    cursor?.use {
      if (it.moveToFirst()) {
        return it.getString(1)
      }
    }
    return null
  }
}

class ClockAlarmSkill : AssistantSkill {
  override val skillId = "clock_alarm"
  override val supportedActions = setOf(AssistantAction.SET_ALARM, AssistantAction.SET_TIMER)

  override suspend fun execute(context: Context, intent: ParsedIntent): SkillResult {
    return when (intent.action) {
      AssistantAction.SET_ALARM -> {
        val hour = intent.parameters["hour"]?.toIntOrNull() ?: 7
        val minute = intent.parameters["minute"]?.toIntOrNull() ?: 0
        val alarmIntent =
          Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Saathi Alarm")
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        return try {
          context.startActivity(alarmIntent)
          SkillResult.Executed(
            reply = String.format(Locale.US, "%02d:%02d ka alarm set kar diya.", hour, minute),
            detail = "AlarmClock intent dispatched",
          )
        } catch (_: Throwable) {
          SkillResult.Failed("System Clock app alarm support nahi mila.")
        }
      }
      AssistantAction.SET_TIMER -> {
        val seconds = intent.parameters["seconds"]?.toIntOrNull() ?: 300
        val timerIntent =
          Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Saathi Timer")
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        return try {
          context.startActivity(timerIntent)
          SkillResult.Executed(
            reply = "$seconds second ka timer chalu kar diya.",
            detail = "Timer length: ${seconds}s",
          )
        } catch (_: Throwable) {
          SkillResult.Failed("System Clock app timer support nahi mila.")
        }
      }
      else -> SkillResult.Failed("Unsupported clock action")
    }
  }
}

class DeviceControlSkill : AssistantSkill {
  override val skillId = "device_control"
  override val supportedActions =
    setOf(
      AssistantAction.TOGGLE_TORCH,
      AssistantAction.ADJUST_VOLUME,
      AssistantAction.ADJUST_BRIGHTNESS,
      AssistantAction.TOGGLE_WIFI,
      AssistantAction.TOGGLE_BLUETOOTH,
      AssistantAction.SEARCH_SETTINGS,
    )

  override suspend fun execute(context: Context, intent: ParsedIntent): SkillResult {
    return when (intent.action) {
      AssistantAction.TOGGLE_TORCH -> toggleTorch(context, intent.target != "off")
      AssistantAction.ADJUST_VOLUME -> adjustVolume(context, intent.target)
      AssistantAction.ADJUST_BRIGHTNESS -> adjustBrightness(context, intent.target)
      AssistantAction.TOGGLE_WIFI -> openWifiControl(context)
      AssistantAction.TOGGLE_BLUETOOTH -> openBluetoothControl(context)
      AssistantAction.SEARCH_SETTINGS -> openSettingsSearch(context, intent.parameters["query"].orEmpty())
      else -> SkillResult.Failed("Unsupported device control")
    }
  }

  private fun toggleTorch(context: Context, enable: Boolean): SkillResult {
    return try {
      val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
      val cameraId =
        cm.cameraIdList.firstOrNull { id ->
          cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return SkillResult.Failed("Is device par camera flashlight available nahi hai.")
      cm.setTorchMode(cameraId, enable)
      SkillResult.Executed(
        reply = if (enable) "Torch on kar diya hai." else "Torch band kar diya hai.",
        detail = "CameraId: $cameraId, Torch=$enable",
      )
    } catch (t: Throwable) {
      SkillResult.Failed("Torch control failed: ${t.message}")
    }
  }

  private fun adjustVolume(context: Context, mode: String): SkillResult {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val direction =
      when (mode.lowercase()) {
        "down" -> AudioManager.ADJUST_LOWER
        "mute" -> AudioManager.ADJUST_MUTE
        else -> AudioManager.ADJUST_RAISE
      }
    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    return SkillResult.Executed(
      reply = "Media volume update kar diya ($current/$max).",
      detail = "Stream: MUSIC, level=$current/$max",
    )
  }

  private fun adjustBrightness(context: Context, direction: String): SkillResult {
    if (!Settings.System.canWrite(context)) {
      return SkillResult.NeedsPermission(
        permissionName = Settings.ACTION_MANAGE_WRITE_SETTINGS,
        explanation = "Screen brightness badalne ke liye 'Modify System Settings' permission zaroori hai.",
      )
    }
    val cr = context.contentResolver
    Settings.System.putInt(
      cr,
      Settings.System.SCREEN_BRIGHTNESS_MODE,
      Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
    )
    val current = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
    val next =
      when (direction.lowercase()) {
        "down" -> (current - 55).coerceAtLeast(15)
        "max" -> 255
        else -> (current + 55).coerceAtMost(255)
      }
    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, next)
    return SkillResult.Executed(
      reply = "Screen brightness ${(next * 100) / 255}% set kar di.",
      detail = "Brightness value: $next/255",
    )
  }

  private fun openWifiControl(context: Context): SkillResult {
    val panelIntent =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Intent(Settings.Panel.ACTION_WIFI)
      } else {
        Intent(Settings.ACTION_WIFI_SETTINGS)
      }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    context.startActivity(panelIntent)
    return SkillResult.Executed(
      reply = "Android 10+ security policy ke kaaran WiFi quick panel khol diya hai.",
      detail = "Settings.Panel.ACTION_WIFI opened",
    )
  }

  private fun openBluetoothControl(context: Context): SkillResult {
    val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val isEnabled = btManager?.adapter?.isEnabled == true
    val intent =
      Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    context.startActivity(intent)
    return SkillResult.Executed(
      reply = "Bluetooth abhi ${if (isEnabled) "ON" else "OFF"} hai. Bluetooth settings khol di hain.",
      detail = "ACTION_BLUETOOTH_SETTINGS opened",
    )
  }

  private fun openSettingsSearch(context: Context, query: String): SkillResult {
    val q = query.lowercase()
    val action =
      when {
        q.contains("display") || q.contains("screen") -> Settings.ACTION_DISPLAY_SETTINGS
        q.contains("sound") || q.contains("volume") -> Settings.ACTION_SOUND_SETTINGS
        q.contains("battery") -> Settings.ACTION_BATTERY_SAVER_SETTINGS
        q.contains("app") -> Settings.ACTION_APPLICATION_SETTINGS
        q.contains("privacy") || q.contains("security") -> Settings.ACTION_SECURITY_SETTINGS
        q.contains("location") -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
        else -> Settings.ACTION_SETTINGS
      }
    context.startActivity(Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    return SkillResult.Executed(
      reply = "Settings (${query.ifBlank { "General" }}) khol di hain.",
      detail = "Intent: $action",
    )
  }
}

class AccessibilityControlSkill : AssistantSkill {
  override val skillId = "accessibility_control"
  override val supportedActions =
    setOf(
      AssistantAction.TAKE_SCREENSHOT,
      AssistantAction.SCROLL_SCREEN,
      AssistantAction.TAP_ELEMENT,
      AssistantAction.TYPE_TEXT,
    )

  override suspend fun execute(context: Context, intent: ParsedIntent): SkillResult {
    val service =
      SaathiAccessibilityService.instance
        ?: return SkillResult.NeedsPermission(
          permissionName = Settings.ACTION_ACCESSIBILITY_SETTINGS,
          explanation = "Screen scroll, tap, type aur screenshot ke liye Saathi Accessibility Service enable karein.",
        )

    val result =
      when (intent.action) {
        AssistantAction.TAKE_SCREENSHOT -> service.takeScreenshotOnCommand()
        AssistantAction.SCROLL_SCREEN -> service.scrollOnCommand(intent.parameters["direction"] ?: "down")
        AssistantAction.TAP_ELEMENT -> service.tapByTextOnCommand(intent.target)
        AssistantAction.TYPE_TEXT -> service.typeTextOnCommand(intent.target)
        else -> Result.failure(IllegalArgumentException("Unsupported accessibility action"))
      }

    return result.fold(
      onSuccess = { msg -> SkillResult.Executed(reply = msg, detail = "Executed via AccessibilityService") },
      onFailure = { err ->
        val message = err.message ?: "Accessibility action failed."
        if (message.contains("Blocked:", ignoreCase = true)) {
          SkillResult.BlockedBySafety(message)
        } else {
          SkillResult.Failed(message)
        }
      },
    )
  }
}

class NotificationSkill : AssistantSkill {
  override val skillId = "notification_reader"
  override val supportedActions = setOf(AssistantAction.READ_NOTIFICATIONS)

  override suspend fun execute(context: Context, intent: ParsedIntent): SkillResult {
    if (!SaathiNotificationListenerService.isConnected.value) {
      return SkillResult.NeedsPermission(
        permissionName = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
        explanation = "Notifications padhne ke liye Notification Access permission dein. Saathi koi notification save nahi karta.",
      )
    }
    val list = SaathiNotificationListenerService.getRecentNotifications()
    if (list.isEmpty()) {
      return SkillResult.Executed("Abhi koi nayi unread notification nahi hai.", "0 active notifications")
    }
    val summary =
      list.take(5).joinToString("\n") { n -> "• ${n.appLabel}: ${n.title} — ${n.text}" }
    return SkillResult.Executed(
      reply = "Aapke recent ${list.take(5).size} notifications:\n$summary",
      detail = "Read ${list.size} transient notifications",
    )
  }
}

class SystemInfoSkill : AssistantSkill {
  override val skillId = "system_info"
  override val supportedActions = setOf(AssistantAction.TELL_TIME_DATE, AssistantAction.HELP_CAPABILITIES)

  override suspend fun execute(context: Context, intent: ParsedIntent): SkillResult {
    return when (intent.action) {
      AssistantAction.TELL_TIME_DATE -> {
        val now = Date()
        val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)
        val date = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(now)
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        SkillResult.Executed(
          reply = "Abhi $time ho rahe hain. Aaj $date hai. Battery $battery% hai.",
          detail = "Local clock & BatteryManager",
        )
      }
      AssistantAction.HELP_CAPABILITIES ->
        SkillResult.Executed(
          reply =
            "Main Saathi hoon — aapki offline-first voice assistant:\n" +
              "• Apps kholna: 'YouTube kholo'\n" +
              "• Call / Message (Confirm ke saath): 'Mom ko call karo', 'Rahul ko WhatsApp bhejo hello'\n" +
              "• Alarm / Timer: 'Subah 7 baje ka alarm lagao', '5 min timer'\n" +
              "• Device Control: 'Torch on karo', 'Volume badhao', 'WiFi settings'\n" +
              "• Accessibility: 'Screenshot lo', 'Neeche scroll karo', 'Notifications padho'",
          detail = "Capabilities overview",
        )
      else -> SkillResult.Failed("Unknown info command")
    }
  }
}
