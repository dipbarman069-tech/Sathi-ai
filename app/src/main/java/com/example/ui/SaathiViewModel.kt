package com.example.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.SaathiRepository
import com.example.data.local.SaathiSettingsState
import com.example.data.security.PinAndBiometricManager
import com.example.data.security.VoiceProfileManager
import com.example.domain.model.ActionExecutionStatus
import com.example.domain.model.ActionLogUi
import com.example.domain.model.AssistantLanguage
import com.example.domain.model.BlocklistedAppUi
import com.example.domain.model.ChatMessageUi
import com.example.domain.model.ChatRole
import com.example.domain.model.ParsedIntent
import com.example.domain.model.PendingConfirmation
import com.example.domain.model.PermissionItemUi
import com.example.domain.model.RiskLevel
import com.example.domain.model.SkillResult
import com.example.domain.model.VoiceOrbState
import com.example.domain.nlu.IntentParser
import com.example.domain.nlu.LocalModelStatus
import com.example.domain.nlu.OnDeviceModelManager
import com.example.domain.skills.GateDecision
import com.example.domain.skills.SkillRegistry
import com.example.services.SaathiAccessibilityService
import com.example.services.SaathiNotificationListenerService
import com.example.services.SaathiSpeechManager
import com.example.services.SaathiWakeWordService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SaathiViewModel(application: Application) : AndroidViewModel(application) {
  private val appContext = application.applicationContext
  private val repository = SaathiRepository.getInstance(appContext)
  private val skillRegistry = SkillRegistry(repository)
  val speechManager = SaathiSpeechManager(appContext)

  val settings: StateFlow<SaathiSettingsState> =
    repository.preferences.settingsFlow.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      SaathiSettingsState(),
    )

  val messages: StateFlow<List<ChatMessageUi>> =
    repository.messagesFlow.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList(),
    )

  val actionLogs: StateFlow<List<ActionLogUi>> =
    repository.actionLogsFlow.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList(),
    )

  val blocklistedApps: StateFlow<List<BlocklistedAppUi>> =
    repository.blocklistedAppsFlow.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList(),
    )

  private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
  val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

  private val _permissions = MutableStateFlow<List<PermissionItemUi>>(emptyList())
  val permissions: StateFlow<List<PermissionItemUi>> = _permissions.asStateFlow()

  private val _modelStatus = MutableStateFlow(OnDeviceModelManager.inspectLocalModels(appContext))
  val modelStatus: StateFlow<LocalModelStatus> = _modelStatus.asStateFlow()

  private val _lastVoiceReply = MutableStateFlow("Tap the orb or mic button and speak your command.")
  val lastVoiceReply: StateFlow<String> = _lastVoiceReply.asStateFlow()

  private val _isThinking = MutableStateFlow(false)
  private val _pinChallengeActive = MutableStateFlow(false)
  val pinChallengeActive: StateFlow<Boolean> = _pinChallengeActive.asStateFlow()

  private val _pinError = MutableStateFlow<String?>(null)
  val pinError: StateFlow<String?> = _pinError.asStateFlow()

  private var pendingAuthenticatedCallback: (() -> Unit)? = null

  private val _hasVoiceProfile = MutableStateFlow(VoiceProfileManager.loadProfile(appContext) != null)
  val hasVoiceProfile: StateFlow<Boolean> = _hasVoiceProfile.asStateFlow()

  private val _isEnrollingVoice = MutableStateFlow(false)
  val isEnrollingVoice: StateFlow<Boolean> = _isEnrollingVoice.asStateFlow()

  private val _voiceEnrollStatus = MutableStateFlow("")
  val voiceEnrollStatus: StateFlow<String> = _voiceEnrollStatus.asStateFlow()

  private val _installedApps = MutableStateFlow<List<Pair<String, String>>>(emptyList())
  val installedApps: StateFlow<List<Pair<String, String>>> = _installedApps.asStateFlow()

  private val _permissionFixEvent = MutableStateFlow<SkillResult.NeedsPermission?>(null)
  val permissionFixEvent: StateFlow<SkillResult.NeedsPermission?> = _permissionFixEvent.asStateFlow()

  val wakeWordServiceRunning: StateFlow<Boolean> = SaathiWakeWordService.isRunning

  val orbState: StateFlow<VoiceOrbState> =
    combine(
        speechManager.isListening,
        speechManager.isSpeaking,
        _isThinking,
        _pendingConfirmation,
      ) { listening, speaking, thinking, pending ->
        when {
          listening -> VoiceOrbState.LISTENING
          speaking -> VoiceOrbState.SPEAKING
          thinking -> VoiceOrbState.THINKING
          pending != null -> VoiceOrbState.AWAITING_CONFIRMATION
          else -> VoiceOrbState.IDLE
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VoiceOrbState.IDLE)

  init {
    viewModelScope.launch {
      repository.seedDefaultsIfNeeded()
      refreshPermissions()
      loadInstalledLaunchableApps()
      if (repository.messagesFlow.first().isEmpty()) {
        repository.addMessage(
          role = ChatRole.ASSISTANT,
          text =
            "Namaste! Main Saathi hoon — aapki private, offline-first personal assistant.\n" +
              "Main sirf tab sunti aur kaam karti hoon jab aap command dein. Risky actions (Call, Message, Settings, Banking apps) hamesha aapki confirmation ke baad hi chalenge.",
          actionName = "READY",
          riskLevel = RiskLevel.SAFE,
        )
      }
    }

    viewModelScope.launch {
      settings.collect { s ->
        speechManager.applyVoiceConfig(s.preferredLanguage, s.ttsPitch, s.ttsSpeechRate)
      }
    }

    viewModelScope.launch {
      SaathiWakeWordService.wakeTriggered.collect {
        if (!settings.value.killSwitchActive) {
          startVoiceCapture(speakResponse = true)
        }
      }
    }
  }

  fun refreshPermissions() {
    val hasMic =
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    val hasNotifs =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      } else true
    val hasCall =
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.CALL_PHONE) ==
        PackageManager.PERMISSION_GRANTED
    val hasContacts =
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED
    val hasAccessibility = SaathiAccessibilityService.isConnected.value
    val hasNotifListener = SaathiNotificationListenerService.isConnected.value
    val hasWriteSettings = Settings.System.canWrite(appContext)

    _permissions.value =
      listOf(
        PermissionItemUi(
          id = Manifest.permission.RECORD_AUDIO,
          title = "Microphone (Push-to-Talk & Wake Word)",
          titleHindi = "माइक्रोफ़ोन अनुमति",
          explanation =
            "Used ONLY when you tap the mic button or explicitly enable Wake Word. Audio stays on-device and is never recorded to cloud.",
          isGranted = hasMic,
        ),
        PermissionItemUi(
          id = Manifest.permission.POST_NOTIFICATIONS,
          title = "Visible Wake-Word Notification",
          titleHindi = "नोटिफिकेशन अनुमति",
          explanation =
            "Required on Android 13+ so Saathi can always display a clear ongoing notification with a Stop Everything button when wake-word is ON.",
          isGranted = hasNotifs,
        ),
        PermissionItemUi(
          id = Manifest.permission.CALL_PHONE,
          title = "Direct Phone Calls",
          titleHindi = "फ़ोन कॉल अनुमति",
          explanation =
            "Lets Saathi place a call ONLY after you confirm on screen/voice. Without this, Saathi safely opens the phone dialer instead.",
          isGranted = hasCall,
        ),
        PermissionItemUi(
          id = Manifest.permission.READ_CONTACTS,
          title = "Contact Name Lookup",
          titleHindi = "संपर्क (Contacts) अनुमति",
          explanation =
            "Reads contact phone numbers locally when you say 'Mom ko call karo'. Contact lists never leave your phone.",
          isGranted = hasContacts,
        ),
        PermissionItemUi(
          id = Settings.ACTION_ACCESSIBILITY_SETTINGS,
          title = "Accessibility Phone Control",
          titleHindi = "एक्सेसिबिलिटी नियंत्रण",
          explanation =
            "Enables screen scroll, tap, type, and screenshots ONLY on your command. Automatically blocks banking, UPI, and password apps.",
          isGranted = hasAccessibility,
          isSpecialSystemAccess = true,
        ),
        PermissionItemUi(
          id = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
          title = "Notification Reader",
          titleHindi = "नोटिफिकेशन रीडर",
          explanation =
            "Reads unread notifications aloud ONLY when you ask 'mere notifications padho'. Nothing is logged or stored.",
          isGranted = hasNotifListener,
          isSpecialSystemAccess = true,
        ),
        PermissionItemUi(
          id = Settings.ACTION_MANAGE_WRITE_SETTINGS,
          title = "Modify System Brightness",
          titleHindi = "सिस्टम ब्राइटनेस",
          explanation =
            "Allows Saathi to raise or lower screen brightness when you command it.",
          isGranted = hasWriteSettings,
          isSpecialSystemAccess = true,
        ),
      )
  }

  private fun loadInstalledLaunchableApps() {
    viewModelScope.launch(Dispatchers.IO) {
      val pm = appContext.packageManager
      val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
      val apps =
        pm.queryIntentActivities(intent, 0)
          .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
          .distinctBy { it.first }
          .filter { it.first != appContext.packageName }
          .sortedBy { it.second.lowercase() }
      _installedApps.value = apps
    }
  }

  fun startVoiceCapture(speakResponse: Boolean = true) {
    if (settings.value.killSwitchActive) {
      viewModelScope.launch {
        repository.addMessage(
          ChatRole.SYSTEM_NOTICE,
          "Kill Switch active hai. Pehle 'Resume Saathi' dabakar listening allow karein.",
        )
      }
      return
    }
    if (
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
        PackageManager.PERMISSION_GRANTED
    ) {
      _permissionFixEvent.value =
        SkillResult.NeedsPermission(
          Manifest.permission.RECORD_AUDIO,
          "Voice command sunne ke liye Microphone permission chahiye.",
        )
      return
    }

    speechManager.startListening(
      language = settings.value.preferredLanguage,
      preferOffline = !settings.value.onlineModeEnabled,
      onResult = { transcript ->
        handleUserCommand(transcript, speakResponse = speakResponse, activity = null)
      },
      onError = { errMsg ->
        _lastVoiceReply.value = errMsg
        if (speakResponse) speechManager.speak(errMsg)
      },
    )
  }

  fun handleUserCommand(
    rawInput: String,
    speakResponse: Boolean = true,
    activity: FragmentActivity? = null,
  ) {
    val clean = rawInput.trim()
    if (clean.isBlank()) return

    // Check if user is replying by voice to an awaiting confirmation
    val pending = _pendingConfirmation.value
    if (pending != null) {
      val lower = clean.lowercase()
      when {
        lower in listOf("yes", "haan", "ha", "confirm", "ok", "theek hai", "karo", "kar do", "हाँ", "कन्फर्म") -> {
          viewModelScope.launch { repository.addMessage(ChatRole.USER, clean) }
          confirmPendingAction(activity, speakResponse)
          return
        }
        lower in listOf("no", "nahi", "cancel", "ruko", "stop", "mat karo", "नहीं", "रद्द") -> {
          viewModelScope.launch { repository.addMessage(ChatRole.USER, clean) }
          cancelPendingAction(speakResponse)
          return
        }
      }
    }

    viewModelScope.launch {
      _isThinking.value = true
      repository.addMessage(ChatRole.USER, clean)

      val parsed = IntentParser.parse(clean, settings.value.preferredLanguage)
      val decision =
        skillRegistry.evaluateSafetyGate(
          intent = parsed,
          killSwitchActive = settings.value.killSwitchActive,
          requireAuthForRisky = settings.value.requireAuthForRiskyActions,
        )

      _isThinking.value = false

      when (decision) {
        is GateDecision.Rejected -> {
          repository.addMessage(
            role = ChatRole.ASSISTANT,
            text = decision.message,
            parsedJson = parsed.jsonRepresentation,
            actionName = parsed.action.name,
            riskLevel = parsed.riskLevel,
          )
          _lastVoiceReply.value = decision.message
          if (speakResponse) speechManager.speak(decision.message)
        }

        is GateDecision.RequiresConfirmation -> {
          _pendingConfirmation.value =
            PendingConfirmation(
              id = System.currentTimeMillis(),
              intent = decision.intent,
              requiresBiometricOrPin = decision.needsBiometricOrPin,
              blockedPackageName = decision.blockedPackageName,
            )
          val prompt = decision.intent.spokenConfirmationPrompt.ifBlank { decision.intent.spokenReply }
          repository.addMessage(
            role = ChatRole.ASSISTANT,
            text = "$prompt\n(Neeche Confirm ya Cancel karein — ya 'Haan' / 'Nahi' bolein.)",
            parsedJson = decision.intent.jsonRepresentation,
            actionName = decision.intent.action.name,
            riskLevel = decision.intent.riskLevel,
          )
          repository.logAction(
            decision.intent,
            ActionExecutionStatus.PENDING_CONFIRMATION,
            "Waiting for user confirmation",
          )
          _lastVoiceReply.value = prompt
          if (speakResponse) speechManager.speak(prompt)
        }

        is GateDecision.ReadyToExecute -> {
          executeAndReport(decision.intent, speakResponse)
        }
      }
    }
  }

  fun confirmPendingAction(activity: FragmentActivity?, speakResponse: Boolean = true) {
    val pending = _pendingConfirmation.value ?: return
    val actionBlock: () -> Unit = {
      viewModelScope.launch {
        if (!pending.blockedPackageName.isNullOrBlank()) {
          repository.setBlocklistedAppLocked(pending.blockedPackageName, false)
        }
        _pendingConfirmation.value = null
        executeAndReport(pending.intent, speakResponse)
      }
    }

    if (pending.requiresBiometricOrPin) {
      requestAuthentication(
        activity = activity,
        title = "Saathi Security Lock",
        subtitle = "Confirm ${pending.intent.action.name} -> ${pending.intent.target}",
        onSuccess = actionBlock,
      )
    } else {
      actionBlock()
    }
  }

  fun cancelPendingAction(speakResponse: Boolean = true) {
    val pending = _pendingConfirmation.value ?: return
    _pendingConfirmation.value = null
    viewModelScope.launch {
      repository.logAction(pending.intent, ActionExecutionStatus.CANCELLED, "Cancelled by user")
      val msg = "Theek hai, ${pending.intent.action.name} cancel kar diya."
      repository.addMessage(ChatRole.SYSTEM_NOTICE, msg)
      _lastVoiceReply.value = msg
      if (speakResponse) speechManager.speak(msg)
    }
  }

  private suspend fun executeAndReport(intent: ParsedIntent, speakResponse: Boolean) {
    val result = skillRegistry.executeWhitelisted(appContext, intent)
    val replyText =
      when (result) {
        is SkillResult.Executed -> result.reply
        is SkillResult.BlockedBySafety -> result.reason
        is SkillResult.NeedsPermission -> {
          _permissionFixEvent.value = result
          result.explanation
        }
        is SkillResult.Failed -> result.errorMessage
      }

    repository.addMessage(
      role = ChatRole.ASSISTANT,
      text = replyText,
      parsedJson = intent.jsonRepresentation,
      actionName = intent.action.name,
      riskLevel = intent.riskLevel,
    )
    _lastVoiceReply.value = replyText
    if (speakResponse) speechManager.speak(replyText)
  }

  fun dismissPermissionFixEvent() {
    _permissionFixEvent.value = null
  }

  fun requestAuthentication(
    activity: FragmentActivity?,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
  ) {
    if (activity != null && PinAndBiometricManager.canAuthenticateBiometric(activity)) {
      PinAndBiometricManager.promptBiometric(
        activity = activity,
        title = title,
        subtitle = subtitle,
        onSuccess = onSuccess,
        onFallbackToPin = {
          if (settings.value.hasPinConfigured) {
            pendingAuthenticatedCallback = onSuccess
            _pinError.value = null
            _pinChallengeActive.value = true
          } else {
            onSuccess()
          }
        },
        onError = { err ->
          viewModelScope.launch {
            repository.addMessage(ChatRole.SYSTEM_NOTICE, "Authentication cancelled: $err")
          }
        },
      )
    } else if (settings.value.hasPinConfigured) {
      pendingAuthenticatedCallback = onSuccess
      _pinError.value = null
      _pinChallengeActive.value = true
    } else {
      onSuccess()
    }
  }

  fun submitPinAttempt(pin: String) {
    viewModelScope.launch {
      if (repository.preferences.verifyPin(pin)) {
        _pinChallengeActive.value = false
        _pinError.value = null
        val cb = pendingAuthenticatedCallback
        pendingAuthenticatedCallback = null
        cb?.invoke()
      } else {
        _pinError.value = "Galat PIN. Dobara koshish karein."
      }
    }
  }

  fun dismissPinChallenge() {
    _pinChallengeActive.value = false
    _pinError.value = null
    pendingAuthenticatedCallback = null
  }

  fun saveSecurityPin(pin: String) {
    viewModelScope.launch {
      repository.preferences.savePin(pin)
    }
  }

  fun toggleBlocklistLock(app: BlocklistedAppUi, activity: FragmentActivity?) {
    viewModelScope.launch {
      if (!app.isLocked) {
        repository.setBlocklistedAppLocked(app.packageName, true)
      } else {
        requestAuthentication(
          activity = activity,
          title = "Unlock ${app.appName}",
          subtitle = "Allow Saathi to open/interact with this protected app",
          onSuccess = {
            viewModelScope.launch { repository.setBlocklistedAppLocked(app.packageName, false) }
          },
        )
      }
    }
  }

  fun addAppToBlocklist(packageName: String, appName: String) {
    viewModelScope.launch { repository.addAppToBlocklist(packageName, appName) }
  }

  fun removeAppFromBlocklist(packageName: String) {
    viewModelScope.launch { repository.removeFromBlocklist(packageName) }
  }

  fun setWakeWordEnabled(enabled: Boolean) {
    viewModelScope.launch {
      if (enabled) {
        if (
          ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
          _permissionFixEvent.value =
            SkillResult.NeedsPermission(
              Manifest.permission.RECORD_AUDIO,
              "Wake-word service start karne ke liye Microphone permission zaroori hai.",
            )
          return@launch
        }
        repository.preferences.setKillSwitchActive(false)
        repository.preferences.setWakeWordEnabled(true)
        val svcIntent =
          Intent(appContext, SaathiWakeWordService::class.java).putExtra(
            SaathiWakeWordService.EXTRA_REQUIRE_VOICE_MATCH,
            settings.value.requireVoiceProfileMatch,
          )
        ContextCompat.startForegroundService(appContext, svcIntent)
      } else {
        repository.preferences.setWakeWordEnabled(false)
        appContext.startService(
          Intent(appContext, SaathiWakeWordService::class.java).setAction(SaathiWakeWordService.ACTION_STOP)
        )
      }
    }
  }

  fun setOnlineMode(enabled: Boolean) {
    viewModelScope.launch { repository.preferences.setOnlineMode(enabled) }
  }

  fun setPreferredLanguage(language: AssistantLanguage) {
    viewModelScope.launch { repository.preferences.setPreferredLanguage(language) }
  }

  fun setTtsVoiceParams(pitch: Float, rate: Float) {
    viewModelScope.launch { repository.preferences.setTtsConfig(pitch, rate) }
  }

  fun setRequireAuthForRisky(required: Boolean) {
    viewModelScope.launch { repository.preferences.setRequireAuthForRiskyActions(required) }
  }

  fun setRequireVoiceProfileMatch(required: Boolean) {
    viewModelScope.launch { repository.preferences.setRequireVoiceProfileMatch(required) }
  }

  fun enrollSpeakerVoiceProfile() {
    if (
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
        PackageManager.PERMISSION_GRANTED
    ) {
      _permissionFixEvent.value =
        SkillResult.NeedsPermission(
          Manifest.permission.RECORD_AUDIO,
          "Voice profile record karne ke liye Microphone permission chahiye.",
        )
      return
    }
    if (_isEnrollingVoice.value) return
    _isEnrollingVoice.value = true
    _voiceEnrollStatus.value = "Listening (6s)... 4 baar 'Hey Saathi' bolein."

    viewModelScope.launch {
      val saved =
        withContext(Dispatchers.IO) {
          var rec: AudioRecord? = null
          try {
            val min =
              AudioRecord.getMinBufferSize(
                VoiceProfileManager.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
              )
            rec =
              AudioRecord(
                MediaRecorder.AudioSource.MIC,
                VoiceProfileManager.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(min, 8192),
              )
            val buf = ShortArray(VoiceProfileManager.FRAME_SIZE)
            val acc = VoiceProfileManager.Accumulator()
            rec.startRecording()
            val end = SystemClock.elapsedRealtime() + 6000
            while (SystemClock.elapsedRealtime() < end) {
              val n = rec.read(buf, 0, buf.size)
              if (n < buf.size) continue
              if (VoiceProfileManager.computeRms(buf, n) > VoiceProfileManager.VOICED_RMS_THRESHOLD) {
                acc.add(VoiceProfileManager.extractFrameFeatures(buf))
              }
            }
            if (acc.frameCount >= 20) {
              VoiceProfileManager.saveProfile(appContext, acc.average())
              true
            } else {
              false
            }
          } catch (_: Throwable) {
            false
          } finally {
            try {
              rec?.stop()
            } catch (_: Throwable) {}
            try {
              rec?.release()
            } catch (_: Throwable) {}
          }
        }
      _isEnrollingVoice.value = false
      _hasVoiceProfile.value = VoiceProfileManager.loadProfile(appContext) != null
      _voiceEnrollStatus.value =
        if (saved) "Voiceprint saved securely on-device."
        else "Awaaz kam aayi — mic ke paas bolkar dobara try karein."
    }
  }

  fun clearVoiceProfile() {
    VoiceProfileManager.clearProfile(appContext)
    _hasVoiceProfile.value = false
    _voiceEnrollStatus.value = "Voiceprint deleted."
  }

  fun refreshModelStatus() {
    _modelStatus.value = OnDeviceModelManager.inspectLocalModels(appContext)
  }

  /**
   * Emergency "Stop Everything" Kill Switch:
   * - Stops TTS and SpeechRecognizer immediately
   * - Stops foreground Wake Word service
   * - Clears transient notifications from memory
   * - Relocks all sensitive banking/UPI/password apps
   * - Cancels pending risky actions
   */
  fun triggerKillSwitch() {
    speechManager.stopAllAudio()
    appContext.startService(
      Intent(appContext, SaathiWakeWordService::class.java).setAction(SaathiWakeWordService.ACTION_STOP)
    )
    SaathiNotificationListenerService.clearMemoryCache()
    val pending = _pendingConfirmation.value
    _pendingConfirmation.value = null

    viewModelScope.launch {
      repository.preferences.setKillSwitchActive(true)
      repository.lockAllSensitiveApps()
      if (pending != null) {
        repository.logAction(
          pending.intent,
          ActionExecutionStatus.CANCELLED,
          "Halted by Stop Everything kill switch",
        )
      }
      repository.addMessage(
        role = ChatRole.SYSTEM_NOTICE,
        text = "STOP EVERYTHING: Mic, TTS, Wake-Word service band aur sabhi protected apps relock kar diye gaye hain.",
      )
      _lastVoiceReply.value = "All assistant operations halted."
    }
  }

  fun resumeFromKillSwitch() {
    viewModelScope.launch {
      repository.preferences.setKillSwitchActive(false)
      repository.addMessage(ChatRole.SYSTEM_NOTICE, "Saathi resumed. Tap mic when you want to command.")
      _lastVoiceReply.value = "Saathi ready."
    }
  }

  fun clearActionLogs() {
    viewModelScope.launch { repository.clearActionLogs() }
  }

  fun clearChatHistory() {
    viewModelScope.launch { repository.clearMessages() }
  }

  override fun onCleared() {
    speechManager.release()
    super.onCleared()
  }
}
