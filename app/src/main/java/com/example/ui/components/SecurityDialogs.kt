package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.domain.model.PendingConfirmation
import com.example.domain.model.PermissionItemUi
import com.example.domain.model.RiskLevel
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
fun ConfirmationActionCard(
  pending: PendingConfirmation,
  onConfirm: () -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val accentColor = if (pending.intent.riskLevel == RiskLevel.HIGH) CrimsonAlert else WarmAmber
  Card(
    modifier = modifier.fillMaxWidth().testTag("confirmation_action_card"),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = SlateElevated),
    border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.7f)),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector =
            if (pending.requiresBiometricOrPin) Icons.Rounded.Fingerprint
            else Icons.Rounded.WarningAmber,
          contentDescription = "Confirmation required",
          tint = accentColor,
          modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
          text =
            if (pending.blockedPackageName != null) "BLOCKLISTED APP UNLOCK"
            else "CONFIRMATION REQUIRED (${pending.intent.riskLevel.name})",
          style = MaterialTheme.typography.labelLarge,
          color = accentColor,
        )
      }

      Text(
        text = pending.intent.spokenConfirmationPrompt.ifBlank {
          "Execute ${pending.intent.action.name} on \"${pending.intent.target}\"?"
        },
        style = MaterialTheme.typography.bodyLarge,
        color = TextPrimary,
        fontWeight = FontWeight.Medium,
      )

      Surface(
        color = ObsidianVoid.copy(alpha = 0.65f),
        shape = RoundedCornerShape(10.dp),
      ) {
        Text(
          text = "Action: ${pending.intent.action.name}  •  Target: ${pending.intent.target.ifBlank { "default" }}",
          style = MaterialTheme.typography.labelSmall,
          color = SonicCyan,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onCancel,
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("cancel_risky_action_button"),
          border = BorderStroke(1.dp, SlateBorder),
        ) {
          Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Cancel")
        }

        Button(
          onClick = onConfirm,
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("confirm_risky_action_button"),
          colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = ObsidianVoid),
        ) {
          Icon(
            imageVector =
              if (pending.requiresBiometricOrPin) Icons.Rounded.Lock else Icons.Rounded.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(6.dp))
          Text(if (pending.requiresBiometricOrPin) "Verify & Run" else "Confirm")
        }
      }
    }
  }
}

@Composable
fun PinChallengeDialog(
  errorText: String?,
  onSubmitPin: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var pin by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MidnightSurface,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Security, contentDescription = null, tint = SonicCyan)
        Spacer(Modifier.width(8.dp))
        Text("Enter Saathi Security PIN", style = MaterialTheme.typography.titleMedium)
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          "PIN verification is required before running sensitive actions or unlocking protected apps.",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
        )
        OutlinedTextField(
          value = pin,
          onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
          label = { Text("4–6 Digit PIN") },
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("pin_challenge_input"),
        )
        if (!errorText.isNullOrBlank()) {
          Text(errorText, style = MaterialTheme.typography.bodySmall, color = CrimsonAlert)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onSubmitPin(pin) },
        enabled = pin.length >= 4,
        modifier = Modifier.testTag("pin_challenge_submit"),
      ) {
        Text("Unlock")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
fun CreatePinDialog(
  onSavePin: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var pin by remember { mutableStateOf("") }
  var confirmPin by remember { mutableStateOf("") }
  val mismatch = confirmPin.isNotEmpty() && pin != confirmPin

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MidnightSurface,
    title = { Text("Set Backup Security PIN", style = MaterialTheme.typography.titleMedium) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          "Stored locally with a random salt + SHA-256 hash inside Android Keystore encrypted preferences.",
          style = MaterialTheme.typography.bodySmall,
          color = TextSecondary,
        )
        OutlinedTextField(
          value = pin,
          onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
          label = { Text("New PIN (4–6 digits)") },
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = confirmPin,
          onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) confirmPin = it },
          label = { Text("Confirm PIN") },
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          singleLine = true,
          isError = mismatch,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onSavePin(pin) },
        enabled = pin.length >= 4 && pin == confirmPin,
      ) {
        Text("Save PIN")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
fun PermissionExplanationDialog(
  permission: PermissionItemUi,
  onProceed: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MidnightSurface,
    title = {
      Column {
        Text(permission.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text(permission.titleHindi, style = MaterialTheme.typography.bodySmall, color = SonicCyan)
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(permission.explanation, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Surface(color = SlateElevated, shape = RoundedCornerShape(10.dp)) {
          Text(
            text = "Privacy guarantee: Saathi never uploads contacts, audio, or screen content.",
            style = MaterialTheme.typography.bodySmall,
            color = EmeraldSafe,
            modifier = Modifier.padding(10.dp),
          )
        }
      }
    },
    confirmButton = {
      Button(onClick = onProceed, modifier = Modifier.testTag("permission_dialog_proceed")) {
        Text(if (permission.isSpecialSystemAccess) "Open System Setting" else "Allow Permission")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Not Now") }
    },
  )
}

@Composable
fun ModelSetupGuideDialog(
  modelStatus: LocalModelStatus,
  onRefresh: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MidnightSurface,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Memory, contentDescription = null, tint = SonicCyan)
        Spacer(Modifier.width(8.dp))
        Text("On-Device Models & Build Guide", style = MaterialTheme.typography.titleMedium)
      }
    },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          "Saathi runs 100% offline right now using its built-in multilingual Hinglish/Hindi/English NLU + Android Offline Speech & Female TTS. To upgrade to local neural LLM / Whisper inference, place model files in:",
          style = MaterialTheme.typography.bodySmall,
          color = TextSecondary,
        )
        Surface(color = ObsidianVoid, shape = RoundedCornerShape(8.dp)) {
          Text(
            text = modelStatus.modelsDirPath,
            style = MaterialTheme.typography.labelSmall,
            color = SonicCyan,
            modifier = Modifier.padding(10.dp),
          )
        }

        Text("Detected Local Models", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
        Text(
          "• Local LLM (.task / .gguf): " +
            if (modelStatus.llmModelFound) "${modelStatus.llmFileName} (${modelStatus.llmSizeMb} MB)"
            else "Not installed (Built-in Hinglish NLU Active)",
          style = MaterialTheme.typography.bodySmall,
          color = if (modelStatus.llmModelFound) EmeraldSafe else WarmAmber,
        )
        Text(
          "• Local STT (Vosk / Whisper): " +
            if (modelStatus.sttModelFound) "${modelStatus.sttFolderName}"
            else "Not installed (Using System On-Device SpeechRecognizer)",
          style = MaterialTheme.typography.bodySmall,
          color = if (modelStatus.sttModelFound) EmeraldSafe else WarmAmber,
        )

        HorizontalDivider(color = SlateBorder)

        Text("Recommended Offline Models", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
        Text(
          "1. LLM Intent Parser: Gemma 2B IT INT4 (.task for MediaPipe LLM Inference) or Qwen2.5-1.5B-Instruct Q4_K_M (.gguf for llama.cpp).\n" +
            "2. Speech Recognition: vosk-model-small-hi-0.22 (Hindi) + vosk-model-small-en-in-0.4, or Whisper tiny/base multilingual.\n" +
            "3. Neural Female TTS: Piper Hindi/English ONNX or Android hi-IN neural female voice.",
          style = MaterialTheme.typography.bodySmall,
          color = TextSecondary,
        )

        HorizontalDivider(color = SlateBorder)

        Text("Phased Roadmap Implemented", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
        Text(
          "• Phase 1 (MVP): ChatGPT-style dark UI, Female Hindi/Hinglish/English TTS, Voice Orb, Open App / Call / Alarm.\n" +
            "• Phase 2 (Control): Torch, Volume, Brightness, WiFi/BT panels, Accessibility scroll/tap/type/screenshot, Notification Reader.\n" +
            "• Phase 3 (Safety): Android Keystore AES-256-GCM DB encryption, Biometric + PIN, UPI/Banking Blocklist, Action Log & One-Tap Kill Switch.",
          style = MaterialTheme.typography.bodySmall,
          color = TextSecondary,
        )
      }
    },
    confirmButton = {
      Button(onClick = onDismiss) { Text("Done") }
    },
    dismissButton = {
      TextButton(onClick = onRefresh) { Text("Rescan Folder") }
    },
  )
}
