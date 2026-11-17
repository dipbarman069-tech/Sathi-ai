package com.example.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.security.VoiceProfileManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service for optional wake-word trigger.
 * - Never runs unless explicitly enabled by the user.
 * - Always shows a visible notification with a one-tap "Stop Everything" kill action.
 * - Never saves audio to disk and never performs autonomous commands.
 */
class SaathiWakeWordService : Service() {
  @Volatile private var running = false
  private var workerThread: Thread? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopWakeListener()
      stopSelf()
      return START_NOT_STICKY
    }

    if (
      ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
        PackageManager.PERMISSION_GRANTED
    ) {
      stopSelf()
      return START_NOT_STICKY
    }

    val requireVoiceMatch = intent?.getBooleanExtra(EXTRA_REQUIRE_VOICE_MATCH, false) ?: false
    startForegroundWithNotification()
    startWakeLoop(requireVoiceMatch)
    return START_STICKY
  }

  private fun startForegroundWithNotification() {
    val nm = getSystemService(NotificationManager::class.java)
    val channel =
      NotificationChannel(
        CHANNEL_ID,
        getString(R.string.wake_word_channel_name),
        NotificationManager.IMPORTANCE_LOW,
      ).apply { description = getString(R.string.wake_word_channel_description) }
    nm.createNotificationChannel(channel)

    val openIntent =
      PendingIntent.getActivity(
        this,
        10,
        Intent(this, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )

    val stopIntent =
      PendingIntent.getService(
        this,
        11,
        Intent(this, SaathiWakeWordService::class.java).apply { action = ACTION_STOP },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )

    val notification =
      Notification.Builder(this, CHANNEL_ID)
        .setContentTitle("Saathi Wake-Word Active (On-Device)")
        .setContentText("Say \"Hey Saathi\" to open command mic. Tap Stop to mute.")
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setOngoing(true)
        .setContentIntent(openIntent)
        .addAction(
          Notification.Action.Builder(null, getString(R.string.kill_switch_label), stopIntent).build()
        )
        .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
    _isRunning.value = true
  }

  private fun startWakeLoop(requireVoiceMatch: Boolean) {
    if (running) return
    running = true
    workerThread =
      Thread {
        var recorder: AudioRecord? = null
        try {
          val minBuf =
            AudioRecord.getMinBufferSize(
              VoiceProfileManager.SAMPLE_RATE,
              AudioFormat.CHANNEL_IN_MONO,
              AudioFormat.ENCODING_PCM_16BIT,
            )
          recorder =
            AudioRecord(
              MediaRecorder.AudioSource.MIC,
              VoiceProfileManager.SAMPLE_RATE,
              AudioFormat.CHANNEL_IN_MONO,
              AudioFormat.ENCODING_PCM_16BIT,
              maxOf(minBuf, 8192),
            )
          val buffer = ShortArray(VoiceProfileManager.FRAME_SIZE)
          val profile = if (requireVoiceMatch) VoiceProfileManager.loadProfile(this) else null
          recorder.startRecording()

          var acc: VoiceProfileManager.Accumulator? = null
          var quietFrames = 0

          while (running) {
            val read = recorder.read(buffer, 0, buffer.size)
            if (read < buffer.size) continue
            val rms = VoiceProfileManager.computeRms(buffer, read)
            if (rms > VoiceProfileManager.VOICED_RMS_THRESHOLD) {
              if (acc == null) acc = VoiceProfileManager.Accumulator()
              acc.add(VoiceProfileManager.extractFrameFeatures(buffer))
              quietFrames = 0
            } else if (acc != null && ++quietFrames > 14) {
              val segment = acc
              acc = null
              quietFrames = 0
              val durationValid = segment.frameCount in 8..65
              val speakerOk =
                if (profile != null) {
                  VoiceProfileManager.cosineSimilarity(segment.average(), profile) >=
                    VoiceProfileManager.MATCH_THRESHOLD
                } else {
                  true
                }
              if (durationValid && speakerOk) {
                _wakeTriggered.tryEmit(Unit)
                // Cooldown before next passive wake window
                Thread.sleep(2500)
              }
            }
          }
        } catch (_: Throwable) {
          // Graceful termination on audio release or kill switch
        } finally {
          try {
            recorder?.stop()
          } catch (_: Throwable) {}
          try {
            recorder?.release()
          } catch (_: Throwable) {}
          running = false
          _isRunning.value = false
        }
      }.apply {
        name = "saathi-wake-word"
        start()
      }
  }

  private fun stopWakeListener() {
    running = false
    workerThread?.interrupt()
    workerThread = null
    _isRunning.value = false
  }

  override fun onDestroy() {
    stopWakeListener()
    super.onDestroy()
  }

  companion object {
    const val ACTION_STOP = "com.example.saathi.STOP_WAKE_SERVICE"
    const val EXTRA_REQUIRE_VOICE_MATCH = "require_voice_match"
    private const val CHANNEL_ID = "saathi_wake_word"
    private const val NOTIFICATION_ID = 4041

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _wakeTriggered = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val wakeTriggered: SharedFlow<Unit> = _wakeTriggered.asSharedFlow()
  }
}
