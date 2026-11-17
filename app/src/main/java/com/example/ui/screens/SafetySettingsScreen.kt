package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.SaathiSettingsState
import com.example.domain.model.AssistantLanguage
import com.example.domain.model.BlocklistedAppUi
import com.example.domain.model.PermissionItemUi
import com.example.domain.nlu.LocalModelStatus
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.SonicCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber

@Composable
fun SafetySettingsScreen(
  settings: SaathiSettingsState,
  permissions: List<PermissionItemUi>,
  blocklistedApps: List<BlocklistedAppUi>,
  installedApps: List<Pair<String, String>>,
  modelStatus: LocalModelStatus,
  wakeWordRunning: Boolean,
  hasVoiceProfile: Boolean,
  isEnrollingVoice: Boolean,
  voiceEnrollStatus: String,
  onRequestSinglePermission: (PermissionItemUi) -> Unit,
  onToggleBlocklistLock: (BlocklistedAppUi) -> Unit,
  onAddAppToBlocklist: (String, String) -> Unit,
  onRemoveFromBlocklist: (String) -> Unit,
  onToggleWakeWord: (Boolean) -> Unit,
  onToggleOnlineMode: (Boolean) -> Unit,
  onSelectLanguage: (AssistantLanguage) -> Unit,
  onUpdateTts: (Float, Float) -> Unit,
  onTestFemaleVoice: () -> Unit,
  onToggleRequireAuthForRisky: (Boolean) -> Unit,
  onToggleRequireVoiceMatch: (Boolean) -> Unit,
  onOpenCreatePin: () -> Unit,
  onEnrollVoiceProfile: () -> Unit,
  onClearVoiceProfile: () -> Unit,
  onOpenModelGuide: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showAddAppPicker by remember { mutableStateOf(false) }
  var languageMenuExpanded by remember { mutableStateOf(false) }

  LazyColumn(
    modifier = modifier.fillMaxSize().background(ObsidianVoid),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Section 1: Offline AI, Voice & Wake Word
    item {
      SectionHeader(
        title = "Offline AI, Voice & Trigger",
        subtitle = "Wake word or mic-button only. No hidden background calls.",
      )
    }

    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MidnightSurface),
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(16.dp),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Wake Word Foreground Service", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
              Text(
                if (wakeWordRunning) "Active with visible notification ('Hey Saathi')"
                else "OFF (Push-to-talk mic button only)",
                style = MaterialTheme.typography.bodySmall,
                color = if (wakeWordRunning) EmeraldSafe else TextSecondary,
              )
            }
            Switch(
              checked = settings.wakeWordEnabled,
              onCheckedChange = onToggleWakeWord,
              modifier = Modifier.testTag("wake_word_switch"),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Optional Online Mode", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
              Text(
                if (settings.onlineModeEnabled) "Enabled by you (allows online speech fallback)"
                else "OFF (Strictly On-Device Offline-First)",
                style = MaterialTheme.typography.bodySmall,
                color = if (settings.onlineModeEnabled) WarmAmber else EmeraldSafe,
              )
            }
            Switch(
              checked = settings.onlineModeEnabled,
              onCheckedChange = onToggleOnlineMode,
              modifier = Modifier.testTag("online_mode_switch"),
            )
          }

          // Female Voice Language & Pitch
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Female Voice Language", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
              Text(settings.preferredLanguage.label, style = MaterialTheme.typography.bodySmall, color = SonicCyan)
            }
            OutlinedButton(
              onClick = { languageMenuExpanded = true },
              modifier = Modifier.heightIn(min = 48.dp),
            ) {
              Text("Change")
              DropdownMenu(
                expanded = languageMenuExpanded,
                onDismissRequest = { languageMenuExpanded = false },
              ) {
                AssistantLanguage.entries.forEach { lang ->
                  DropdownMenuItem(
                    text = { Text(lang.label) },
                    onClick = {
                      languageMenuExpanded = false
                      onSelectLanguage(lang)
                    },
                  )
                }
              }
            }
          }

          Column {
            Text(
              "Female Voice Pitch (${String.format("%.2f", settings.ttsPitch)})",
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondary,
            )
            Slider(
              value = settings.ttsPitch,
              onValueChange = { onUpdateTts(it, settings.ttsSpeechRate) },
              valueRange = 0.85f..1.40f,
              colors = SliderDefaults.colors(thumbColor = SonicCyan, activeTrackColor = SonicCyan),
            )
            Text(
              "Speech Rate (${String.format("%.2f", settings.ttsSpeechRate)}x)",
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondary,
            )
            Slider(
              value = settings.ttsSpeechRate,
              onValueChange = { onUpdateTts(settings.ttsPitch, it) },
              valueRange = 0.75f..1.35f,
              colors = SliderDefaults.colors(thumbColor = WarmAmber, activeTrackColor = WarmAmber),
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
              onClick = onTestFemaleVoice,
              modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("test_female_voice_button"),
            ) {
              Icon(Icons.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Test Voice")
            }
            Button(
              onClick = onOpenModelGuide,
              modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("open_model_guide_button"),
            ) {
              Icon(Icons.Rounded.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text(if (modelStatus.llmModelFound) "Local LLM Ready" else "Model Setup")
            }
          }
        }
      }
    }

    // Section 2: Biometric, PIN & Speaker Voiceprint Lock
    item {
      SectionHeader(
        title = "Safety Locks & Voice Auth",
        subtitle = "Required before risky actions or unlocking sensitive apps.",
      )
    }

    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MidnightSurface),
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(16.dp),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Biometric / PIN for Risky Actions", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
              Text("Calls, SMS/WhatsApp, Settings, Blocklisted apps", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Switch(
              checked = settings.requireAuthForRiskyActions,
              onCheckedChange = onToggleRequireAuthForRisky,
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Backup Security PIN", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
              Text(
                if (settings.hasPinConfigured) "Encrypted PIN configured" else "Not set (uses device biometric/screen lock)",
                style = MaterialTheme.typography.bodySmall,
                color = if (settings.hasPinConfigured) EmeraldSafe else TextSecondary,
              )
            }
            OutlinedButton(
              onClick = onOpenCreatePin,
              modifier = Modifier.heightIn(min = 48.dp).testTag("set_pin_button"),
            ) {
              Icon(Icons.Rounded.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text(if (settings.hasPinConfigured) "Change PIN" else "Set PIN")
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Speaker Voiceprint Match", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
              Text(
                if (hasVoiceProfile) "Enrolled on-device (wake-word checks your voice)"
                else "Optional: verify only your voice triggers wake word",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
              )
            }
            Switch(
              checked = settings.requireVoiceProfileMatch,
              onCheckedChange = onToggleRequireVoiceMatch,
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
              onClick = onEnrollVoiceProfile,
              enabled = !isEnrollingVoice,
              modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("enroll_voiceprint_button"),
            ) {
              Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text(if (isEnrollingVoice) "Recording (6s)..." else "Enroll Voiceprint")
            }
            if (hasVoiceProfile) {
              OutlinedButton(
                onClick = onClearVoiceProfile,
                modifier = Modifier.heightIn(min = 48.dp),
              ) {
                Text("Clear")
              }
            }
          }
          if (voiceEnrollStatus.isNotBlank()) {
            Text(voiceEnrollStatus, style = MaterialTheme.typography.bodySmall, color = SonicCyan)
          }
        }
      }
    }

    // Section 3: Banking / UPI / Password App Blocklist
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        SectionHeader(
          title = "Protected App Blocklist",
          subtitle = "Blocked from launch/automation unless manually unlocked.",
          modifier = Modifier.weight(1f),
        )
        OutlinedButton(
          onClick = { showAddAppPicker = true },
          modifier = Modifier.heightIn(min = 48.dp).testTag("add_blocklist_app_button"),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(4.dp))
          Text("Add App")
        }
      }
    }

    items(blocklistedApps, key = { it.packageName }) { app ->
      Card(
        colors = CardDefaults.cardColors(containerColor = SlateElevated),
        border = BorderStroke(1.dp, if (app.isLocked) SlateBorder else WarmAmber),
        shape = RoundedCornerShape(14.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(app.appName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text("${app.category} • ${app.packageName}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
              onClick = { onToggleBlocklistLock(app) },
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = if (app.isLocked) MidnightSurface else WarmAmber,
                  contentColor = if (app.isLocked) EmeraldSafe else ObsidianVoid,
                ),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
              modifier = Modifier.heightIn(min = 44.dp),
            ) {
              Icon(
                if (app.isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(Modifier.width(4.dp))
              Text(if (app.isLocked) "Locked" else "Unlocked", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(
              onClick = { onRemoveFromBlocklist(app.packageName) },
              modifier = Modifier.size(44.dp),
            ) {
              Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove from blocklist", tint = TextSecondary)
            }
          }
        }
      }
    }

    // Section 4: Separate Permission Cards with Explanations
    item {
      SectionHeader(
        title = "Permissions (One-by-One)",
        subtitle = "Each permission is requested individually with a clear explanation.",
      )
    }

    items(permissions, key = { it.id }) { perm ->
      Card(
        colors = CardDefaults.cardColors(containerColor = MidnightSurface),
        border = BorderStroke(1.dp, if (perm.isGranted) EmeraldSafe.copy(alpha = 0.4f) else SlateBorder),
        shape = RoundedCornerShape(14.dp),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(perm.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
              Text(perm.titleHindi, style = MaterialTheme.typography.bodySmall, color = SonicCyan)
            }
            if (perm.isGranted) {
              Surface(color = EmeraldSafe.copy(alpha = 0.16f), shape = RoundedCornerShape(99.dp)) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = EmeraldSafe, modifier = Modifier.size(14.dp))
                  Spacer(Modifier.width(4.dp))
                  Text("Granted", style = MaterialTheme.typography.labelSmall, color = EmeraldSafe)
                }
              }
            } else {
              Button(
                onClick = { onRequestSinglePermission(perm) },
                modifier = Modifier.heightIn(min = 44.dp).testTag("grant_perm_${perm.id}"),
              ) {
                Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Grant")
              }
            }
          }
          Text(perm.explanation, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
      }
    }
  }

  if (showAddAppPicker) {
    AlertDialog(
      onDismissRequest = { showAddAppPicker = false },
      containerColor = MidnightSurface,
      title = { Text("Add Installed App to Blocklist", style = MaterialTheme.typography.titleMedium) },
      text = {
        if (installedApps.isEmpty()) {
          Text("No other launchable apps found.", color = TextSecondary)
        } else {
          LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
            items(installedApps) { (pkg, label) ->
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .clickable {
                      onAddAppToBlocklist(pkg, label)
                      showAddAppPicker = false
                    }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = SonicCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                  Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                  Text(pkg, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showAddAppPicker = false }) { Text("Close") }
      },
    )
  }
}

@Composable
private fun SectionHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
  Column(modifier = modifier) {
    Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
  }
}
