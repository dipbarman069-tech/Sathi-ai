package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.domain.model.PermissionItemUi
import com.example.domain.model.VoiceOrbState
import com.example.ui.SaathiViewModel
import com.example.ui.components.CreatePinDialog
import com.example.ui.components.ModelSetupGuideDialog
import com.example.ui.components.PermissionExplanationDialog
import com.example.ui.components.PinChallengeDialog
import com.example.ui.navigation.ActivityLogRoute
import com.example.ui.navigation.ChatRoute
import com.example.ui.navigation.LiveVoiceRoute
import com.example.ui.navigation.SafetySettingsRoute
import com.example.ui.screens.ActionLogScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.LiveVoiceScreen
import com.example.ui.screens.SafetySettingsScreen
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SaathiTheme
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.SonicCyan
import com.example.ui.theme.TextSecondary

class MainActivity : FragmentActivity() {
  private val viewModel: SaathiViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      SaathiTheme {
        SaathiRootApp(
          activity = this,
          viewModel = viewModel,
        )
      }
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.refreshPermissions()
    viewModel.refreshModelStatus()
  }
}

private data class NavTab(
  val route: Any,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
)

@Composable
private fun SaathiRootApp(
  activity: FragmentActivity,
  viewModel: SaathiViewModel,
) {
  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentDest = backStackEntry?.destination

  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val messages by viewModel.messages.collectAsStateWithLifecycle()
  val actionLogs by viewModel.actionLogs.collectAsStateWithLifecycle()
  val blocklistedApps by viewModel.blocklistedApps.collectAsStateWithLifecycle()
  val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
  val permissions by viewModel.permissions.collectAsStateWithLifecycle()
  val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle()
  val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
  val orbState by viewModel.orbState.collectAsStateWithLifecycle()
  val rmsLevel by viewModel.speechManager.rmsLevel.collectAsStateWithLifecycle()
  val partialTranscript by viewModel.speechManager.partialTranscript.collectAsStateWithLifecycle()
  val isListening by viewModel.speechManager.isListening.collectAsStateWithLifecycle()
  val lastVoiceReply by viewModel.lastVoiceReply.collectAsStateWithLifecycle()
  val wakeWordRunning by viewModel.wakeWordServiceRunning.collectAsStateWithLifecycle()
  val hasVoiceProfile by viewModel.hasVoiceProfile.collectAsStateWithLifecycle()
  val isEnrollingVoice by viewModel.isEnrollingVoice.collectAsStateWithLifecycle()
  val voiceEnrollStatus by viewModel.voiceEnrollStatus.collectAsStateWithLifecycle()
  val pinChallengeActive by viewModel.pinChallengeActive.collectAsStateWithLifecycle()
  val pinError by viewModel.pinError.collectAsStateWithLifecycle()
  val permissionFixEvent by viewModel.permissionFixEvent.collectAsStateWithLifecycle()

  var explainingPermission by remember { mutableStateOf<PermissionItemUi?>(null) }
  var showCreatePinDialog by remember { mutableStateOf(false) }
  var showModelGuideDialog by remember { mutableStateOf(false) }

  // Individual single-permission launcher
  val singlePermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
      viewModel.refreshPermissions()
    }

  fun launchPermissionRequest(item: PermissionItemUi) {
    if (item.isSpecialSystemAccess) {
      val intent =
        when (item.id) {
          Settings.ACTION_MANAGE_WRITE_SETTINGS ->
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
              data = Uri.parse("package:${activity.packageName}")
            }
          else -> Intent(item.id)
        }
      activity.startActivity(intent)
    } else {
      singlePermissionLauncher.launch(item.id)
    }
  }

  // If a skill reported missing permission during command execution, map it to our single-permission card
  val matchedFixPermission =
    remember(permissionFixEvent, permissions) {
      val evt = permissionFixEvent ?: return@remember null
      permissions.firstOrNull { it.id == evt.permissionName }
        ?: PermissionItemUi(
          id = evt.permissionName,
          title = "Permission Required",
          titleHindi = "अनुमति आवश्यक",
          explanation = evt.explanation,
          isGranted = false,
          isSpecialSystemAccess = evt.permissionName.startsWith("android.settings."),
        )
    }

  val tabs =
    listOf(
      NavTab(ChatRoute, "Chat", Icons.Rounded.ChatBubble, Icons.Outlined.ChatBubbleOutline, "nav_chat"),
      NavTab(LiveVoiceRoute, "Voice Orb", Icons.Rounded.GraphicEq, Icons.Outlined.GraphicEq, "nav_voice"),
      NavTab(ActivityLogRoute, "Action Log", Icons.Rounded.History, Icons.Outlined.History, "nav_log"),
      NavTab(SafetySettingsRoute, "Safety", Icons.Rounded.Security, Icons.Outlined.Security, "nav_safety"),
    )

  val isLiveVoiceScreen = currentDest?.hasRoute<LiveVoiceRoute>() == true

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 600.dp

    Row(modifier = Modifier.fillMaxSize()) {
      if (useRail && !isLiveVoiceScreen) {
        NavigationRail(
          containerColor = MidnightSurface,
          modifier = Modifier.statusBarsPadding().navigationBarsPadding(),
        ) {
          tabs.forEach { tab ->
            val selected =
              when (tab.route) {
                is ChatRoute -> currentDest?.hasRoute<ChatRoute>() == true
                is LiveVoiceRoute -> currentDest?.hasRoute<LiveVoiceRoute>() == true
                is ActivityLogRoute -> currentDest?.hasRoute<ActivityLogRoute>() == true
                is SafetySettingsRoute -> currentDest?.hasRoute<SafetySettingsRoute>() == true
                else -> false
              }
            NavigationRailItem(
              selected = selected,
              onClick = {
                navController.navigate(tab.route) {
                  popUpTo(ChatRoute) { saveState = true }
                  launchSingleTop = true
                  restoreState = true
                }
              },
              icon = {
                Icon(
                  if (selected) tab.selectedIcon else tab.unselectedIcon,
                  contentDescription = tab.label,
                )
              },
              label = { Text(tab.label) },
              modifier = Modifier.testTag(tab.testTag),
            )
          }
        }
      }

      Scaffold(
        containerColor = ObsidianVoid,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
          if (!useRail && !isLiveVoiceScreen) {
            NavigationBar(
              containerColor = MidnightSurface,
              modifier = Modifier.navigationBarsPadding(),
            ) {
              tabs.forEach { tab ->
                val selected =
                  when (tab.route) {
                    is ChatRoute -> currentDest?.hasRoute<ChatRoute>() == true
                    is LiveVoiceRoute -> currentDest?.hasRoute<LiveVoiceRoute>() == true
                    is ActivityLogRoute -> currentDest?.hasRoute<ActivityLogRoute>() == true
                    is SafetySettingsRoute -> currentDest?.hasRoute<SafetySettingsRoute>() == true
                    else -> false
                  }
                NavigationBarItem(
                  selected = selected,
                  onClick = {
                    navController.navigate(tab.route) {
                      popUpTo(ChatRoute) { saveState = true }
                      launchSingleTop = true
                      restoreState = true
                    }
                  },
                  icon = {
                    Icon(
                      if (selected) tab.selectedIcon else tab.unselectedIcon,
                      contentDescription = tab.label,
                    )
                  },
                  label = { Text(tab.label) },
                  colors =
                    NavigationBarItemDefaults.colors(
                      selectedIconColor = SonicCyan,
                      selectedTextColor = SonicCyan,
                      indicatorColor = SlateElevated,
                      unselectedIconColor = TextSecondary,
                      unselectedTextColor = TextSecondary,
                    ),
                  modifier = Modifier.testTag(tab.testTag),
                )
              }
            }
          }
        },
      ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
          NavHost(navController = navController, startDestination = ChatRoute) {
            composable<ChatRoute> {
              ChatScreen(
                messages = messages,
                settings = settings,
                pendingConfirmation = pendingConfirmation,
                isListening = isListening,
                partialTranscript = partialTranscript,
                onSendText = { cmd ->
                  viewModel.handleUserCommand(cmd, speakResponse = true, activity = activity)
                },
                onMicClick = {
                  if (isListening) viewModel.speechManager.stopListening()
                  else viewModel.startVoiceCapture(speakResponse = true)
                },
                onOpenLiveVoice = {
                  navController.navigate(LiveVoiceRoute) { launchSingleTop = true }
                },
                onConfirmAction = { viewModel.confirmPendingAction(activity, speakResponse = true) },
                onCancelAction = { viewModel.cancelPendingAction(speakResponse = true) },
                onKillSwitch = { viewModel.triggerKillSwitch() },
                onResumeFromKillSwitch = { viewModel.resumeFromKillSwitch() },
              )
            }

            composable<LiveVoiceRoute> {
              LiveVoiceScreen(
                orbState = orbState,
                rmsLevel = rmsLevel,
                partialTranscript = partialTranscript,
                lastVoiceReply = lastVoiceReply,
                pendingConfirmation = pendingConfirmation,
                wakeWordEnabled = settings.wakeWordEnabled,
                onToggleListening = {
                  if (orbState == VoiceOrbState.LISTENING) {
                    viewModel.speechManager.stopListening()
                  } else {
                    viewModel.startVoiceCapture(speakResponse = true)
                  }
                },
                onConfirmAction = { viewModel.confirmPendingAction(activity, speakResponse = true) },
                onCancelAction = { viewModel.cancelPendingAction(speakResponse = true) },
                onKillSwitch = { viewModel.triggerKillSwitch() },
                onBackToChat = { navController.popBackStack() },
              )
            }

            composable<ActivityLogRoute> {
              ActionLogScreen(
                logs = actionLogs,
                killSwitchActive = settings.killSwitchActive,
                onTriggerKillSwitch = { viewModel.triggerKillSwitch() },
                onResumeFromKillSwitch = { viewModel.resumeFromKillSwitch() },
                onClearLogs = { viewModel.clearActionLogs() },
              )
            }

            composable<SafetySettingsRoute> {
              SafetySettingsScreen(
                settings = settings,
                permissions = permissions,
                blocklistedApps = blocklistedApps,
                installedApps = installedApps,
                modelStatus = modelStatus,
                wakeWordRunning = wakeWordRunning,
                hasVoiceProfile = hasVoiceProfile,
                isEnrollingVoice = isEnrollingVoice,
                voiceEnrollStatus = voiceEnrollStatus,
                onRequestSinglePermission = { perm -> explainingPermission = perm },
                onToggleBlocklistLock = { app -> viewModel.toggleBlocklistLock(app, activity) },
                onAddAppToBlocklist = { pkg, label -> viewModel.addAppToBlocklist(pkg, label) },
                onRemoveFromBlocklist = { pkg -> viewModel.removeAppFromBlocklist(pkg) },
                onToggleWakeWord = { enabled ->
                  if (enabled && permissions.firstOrNull { it.id == Manifest.permission.RECORD_AUDIO }?.isGranted != true) {
                    explainingPermission = permissions.firstOrNull { it.id == Manifest.permission.RECORD_AUDIO }
                  } else {
                    viewModel.setWakeWordEnabled(enabled)
                  }
                },
                onToggleOnlineMode = { viewModel.setOnlineMode(it) },
                onSelectLanguage = { viewModel.setPreferredLanguage(it) },
                onUpdateTts = { pitch, rate -> viewModel.setTtsVoiceParams(pitch, rate) },
                onTestFemaleVoice = {
                  viewModel.speechManager.speak(
                    "Namaste! Main Saathi hoon. Aapki aagya ke bina main koi bhi kaam nahi karti."
                  )
                },
                onToggleRequireAuthForRisky = { viewModel.setRequireAuthForRisky(it) },
                onToggleRequireVoiceMatch = { viewModel.setRequireVoiceProfileMatch(it) },
                onOpenCreatePin = { showCreatePinDialog = true },
                onEnrollVoiceProfile = { viewModel.enrollSpeakerVoiceProfile() },
                onClearVoiceProfile = { viewModel.clearVoiceProfile() },
                onOpenModelGuide = {
                  viewModel.refreshModelStatus()
                  showModelGuideDialog = true
                },
              )
            }
          }
        }
      }
    }
  }

  // Individual Permission Explanation Dialog
  val activePermissionDialog = explainingPermission ?: matchedFixPermission
  if (activePermissionDialog != null) {
    PermissionExplanationDialog(
      permission = activePermissionDialog,
      onProceed = {
        explainingPermission = null
        viewModel.dismissPermissionFixEvent()
        launchPermissionRequest(activePermissionDialog)
      },
      onDismiss = {
        explainingPermission = null
        viewModel.dismissPermissionFixEvent()
      },
    )
  }

  if (pinChallengeActive) {
    PinChallengeDialog(
      errorText = pinError,
      onSubmitPin = { viewModel.submitPinAttempt(it) },
      onDismiss = { viewModel.dismissPinChallenge() },
    )
  }

  if (showCreatePinDialog) {
    CreatePinDialog(
      onSavePin = { pin ->
        viewModel.saveSecurityPin(pin)
        showCreatePinDialog = false
      },
      onDismiss = { showCreatePinDialog = false },
    )
  }

  if (showModelGuideDialog) {
    ModelSetupGuideDialog(
      modelStatus = modelStatus,
      onRefresh = { viewModel.refreshModelStatus() },
      onDismiss = { showModelGuideDialog = false },
    )
  }
}
