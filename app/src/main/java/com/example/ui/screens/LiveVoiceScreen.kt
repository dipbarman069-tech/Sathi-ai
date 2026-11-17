package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.model.PendingConfirmation
import com.example.domain.model.VoiceOrbState
import com.example.ui.components.ConfirmationActionCard
import com.example.ui.components.VoiceOrbCanvas
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CrimsonSurface
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
fun LiveVoiceScreen(
  orbState: VoiceOrbState,
  rmsLevel: Float,
  partialTranscript: String,
  lastVoiceReply: String,
  pendingConfirmation: PendingConfirmation?,
  wakeWordEnabled: Boolean,
  onToggleListening: () -> Unit,
  onConfirmAction: () -> Unit,
  onCancelAction: () -> Unit,
  onKillSwitch: () -> Unit,
  onBackToChat: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            listOf(ObsidianVoid, MidnightSurface, ObsidianVoid)
          )
        )
        .padding(horizontal = 20.dp, vertical = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.SpaceBetween,
  ) {
    // Top bar
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        color = SlateElevated,
        shape = RoundedCornerShape(99.dp),
        border = BorderStroke(1.dp, SlateBorder),
      ) {
        Text(
          text = if (wakeWordEnabled) "Wake Word: ON (Hey Saathi)" else "Manual Trigger Only",
          style = MaterialTheme.typography.labelSmall,
          color = if (wakeWordEnabled) EmeraldSafe else SonicCyan,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
      }

      IconButton(
        onClick = onBackToChat,
        modifier = Modifier.size(48.dp).testTag("exit_live_voice_button"),
      ) {
        Icon(Icons.Rounded.Close, contentDescription = "Back to Chat", tint = TextPrimary)
      }
    }

    // Center Animated Sonic Orb
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
      modifier = Modifier.weight(1f),
    ) {
      Spacer(Modifier.height(8.dp))
      val statusLabel =
        when (orbState) {
          VoiceOrbState.LISTENING -> "Sun rahi hoon..."
          VoiceOrbState.SPEAKING -> "Saathi bol rahi hai..."
          VoiceOrbState.THINKING -> "Command samajh rahi hoon..."
          VoiceOrbState.AWAITING_CONFIRMATION -> "Confirmation chahiye"
          VoiceOrbState.IDLE -> "Tap the orb to speak"
        }
      Text(
        text = statusLabel,
        style = MaterialTheme.typography.headlineMedium,
        color =
          when (orbState) {
            VoiceOrbState.SPEAKING,
            VoiceOrbState.AWAITING_CONFIRMATION -> WarmAmber
            else -> SonicCyan
          },
      )

      Box(
        modifier =
          Modifier.widthIn(max = 290.dp)
            .fillMaxWidth(0.75f)
            .aspectRatio(1f)
            .clip(CircleShape)
            .clickable(onClick = onToggleListening)
            .testTag("live_voice_orb_button"),
        contentAlignment = Alignment.Center,
      ) {
        VoiceOrbCanvas(
          orbState = orbState,
          rmsLevel = rmsLevel,
          modifier = Modifier.fillMaxSize(),
        )
      }

      // Live Transcript / Spoken Reply
      Surface(
        color = SlateElevated.copy(alpha = 0.78f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, SlateBorder),
        modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
      ) {
        Text(
          text =
            when {
              orbState == VoiceOrbState.LISTENING && partialTranscript.isNotBlank() -> "\"$partialTranscript\""
              else -> lastVoiceReply
            },
          style = MaterialTheme.typography.bodyLarge,
          color = TextPrimary,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(16.dp),
        )
      }

      AnimatedVisibility(visible = pendingConfirmation != null) {
        pendingConfirmation?.let { pending ->
          ConfirmationActionCard(
            pending = pending,
            onConfirm = onConfirmAction,
            onCancel = onCancelAction,
            modifier = Modifier.widthIn(max = 520.dp),
          )
        }
      }
    }

    // Bottom Controls
    Row(
      modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Button(
        onClick = onKillSwitch,
        colors = ButtonDefaults.buttonColors(containerColor = CrimsonSurface, contentColor = CrimsonAlert),
        shape = RoundedCornerShape(99.dp),
        modifier = Modifier.heightIn(min = 54.dp).testTag("live_voice_kill_switch"),
      ) {
        Icon(Icons.Rounded.StopCircle, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Stop Everything", style = MaterialTheme.typography.labelLarge)
      }

      Button(
        onClick = onToggleListening,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = if (orbState == VoiceOrbState.LISTENING) WarmAmber else SonicCyan,
            contentColor = ObsidianVoid,
          ),
        shape = RoundedCornerShape(99.dp),
        modifier = Modifier.heightIn(min = 54.dp).testTag("live_voice_mic_button"),
      ) {
        Icon(
          if (orbState == VoiceOrbState.LISTENING) Icons.Rounded.Stop else Icons.Rounded.Mic,
          contentDescription = null,
        )
        Spacer(Modifier.width(8.dp))
        Text(
          if (orbState == VoiceOrbState.LISTENING) "Stop Mic" else "Speak Now",
          style = MaterialTheme.typography.labelLarge,
        )
      }
    }
  }
}
