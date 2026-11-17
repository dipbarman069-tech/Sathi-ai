package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.data.local.SaathiSettingsState
import com.example.domain.model.ChatMessageUi
import com.example.domain.model.ChatRole
import com.example.domain.model.PendingConfirmation
import com.example.domain.model.RiskLevel
import com.example.ui.components.ConfirmationActionCard
import com.example.ui.theme.AssistantBubbleBg
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CrimsonSurface
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.EmeraldSurface
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.SonicCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UserBubbleBg
import com.example.ui.theme.WarmAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
  messages: List<ChatMessageUi>,
  settings: SaathiSettingsState,
  pendingConfirmation: PendingConfirmation?,
  isListening: Boolean,
  partialTranscript: String,
  onSendText: (String) -> Unit,
  onMicClick: () -> Unit,
  onOpenLiveVoice: () -> Unit,
  onConfirmAction: () -> Unit,
  onCancelAction: () -> Unit,
  onKillSwitch: () -> Unit,
  onResumeFromKillSwitch: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var inputText by remember { mutableStateOf("") }
  val listState = rememberLazyListState()

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.lastIndex)
    }
  }

  Column(
    modifier = modifier.fillMaxSize().background(ObsidianVoid).imePadding(),
  ) {
    // Top Header Bar
    Surface(
      color = MidnightSurface,
      border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.5f)),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "Saathi",
              style = MaterialTheme.typography.titleLarge,
              color = TextPrimary,
            )
            Spacer(Modifier.width(8.dp))
            Surface(
              color = if (settings.onlineModeEnabled) WarmAmber.copy(alpha = 0.16f) else EmeraldSurface.copy(alpha = 0.7f),
              shape = RoundedCornerShape(99.dp),
              border = BorderStroke(1.dp, if (settings.onlineModeEnabled) WarmAmber else EmeraldSafe),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  Icons.Rounded.Shield,
                  contentDescription = null,
                  tint = if (settings.onlineModeEnabled) WarmAmber else EmeraldSafe,
                  modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                  text = if (settings.onlineModeEnabled) "ONLINE" else "ON-DEVICE",
                  style = MaterialTheme.typography.labelSmall,
                  color = if (settings.onlineModeEnabled) WarmAmber else EmeraldSafe,
                )
              }
            }
          }
          Text(
            text = "Acts ONLY on your command • Encrypted locally",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
          FilledIconButton(
            onClick = onOpenLiveVoice,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = SlateElevated, contentColor = SonicCyan),
            modifier = Modifier.size(48.dp).testTag("open_live_voice_button"),
          ) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = "Open Live Voice Mode")
          }

          FilledIconButton(
            onClick = onKillSwitch,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = CrimsonSurface, contentColor = CrimsonAlert),
            modifier = Modifier.size(48.dp).testTag("header_kill_switch_button"),
          ) {
            Icon(Icons.Rounded.StopCircle, contentDescription = "Stop Everything Kill Switch")
          }
        }
      }
    }

    // Kill Switch Active Banner
    AnimatedVisibility(visible = settings.killSwitchActive) {
      Surface(
        color = CrimsonSurface,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "Kill Switch Active — All listening & automation halted.",
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
          )
          Spacer(Modifier.width(8.dp))
          Button(
            onClick = onResumeFromKillSwitch,
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSafe, contentColor = ObsidianVoid),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.heightIn(min = 40.dp).testTag("resume_saathi_button"),
          ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Resume", style = MaterialTheme.typography.labelLarge)
          }
        }
      }
    }

    // Chat Message Feed
    LazyColumn(
      state = listState,
      modifier = Modifier.weight(1f).fillMaxWidth(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      items(messages, key = { it.id }) { msg ->
        ChatBubbleItem(message = msg)
      }

      if (isListening && partialTranscript.isNotBlank()) {
        item(key = "live_partial") {
          Surface(
            color = UserBubbleBg.copy(alpha = 0.55f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, SonicCyan.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "Listening: \"$partialTranscript\"...",
              style = MaterialTheme.typography.bodyMedium,
              color = SonicCyan,
              modifier = Modifier.padding(12.dp),
            )
          }
        }
      }
    }

    // Pending Risky Action Confirmation Card
    AnimatedVisibility(visible = pendingConfirmation != null) {
      pendingConfirmation?.let { pending ->
        ConfirmationActionCard(
          pending = pending,
          onConfirm = onConfirmAction,
          onCancel = onCancelAction,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
      }
    }

    // Quick Hinglish & English Command Chips
    val suggestions = remember {
      listOf(
        "Torch on karo",
        "5 min timer lagao",
        "Samay kya hua",
        "GPay kholo",
        "Notifications padho",
        "Screenshot lo",
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      suggestions.forEach { cmd ->
        AssistChip(
          onClick = { onSendText(cmd) },
          label = { Text(cmd, style = MaterialTheme.typography.bodySmall) },
          colors = AssistChipDefaults.assistChipColors(containerColor = SlateElevated, labelColor = TextSecondary),
          border = BorderStroke(1.dp, SlateBorder),
          modifier = Modifier.heightIn(min = 36.dp),
        )
      }
    }

    // Bottom ChatGPT-style Composer + Big Luminous Mic Button
    Surface(
      color = MidnightSurface,
      border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.7f)),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedTextField(
          value = inputText,
          onValueChange = { inputText = it },
          placeholder = {
            Text(
              "Hindi, Hinglish, or English command...",
              style = MaterialTheme.typography.bodyMedium,
              color = TextMuted,
            )
          },
          singleLine = false,
          maxLines = 3,
          shape = RoundedCornerShape(24.dp),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedContainerColor = SlateElevated,
              unfocusedContainerColor = SlateElevated,
              focusedBorderColor = SonicCyan,
              unfocusedBorderColor = SlateBorder,
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary,
            ),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions =
            KeyboardActions(
              onSend = {
                if (inputText.isNotBlank()) {
                  onSendText(inputText)
                  inputText = ""
                }
              }
            ),
          trailingIcon = {
            if (inputText.isNotBlank()) {
              IconButton(
                onClick = {
                  onSendText(inputText)
                  inputText = ""
                },
                modifier = Modifier.testTag("send_command_button"),
              ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send command", tint = SonicCyan)
              }
            }
          },
          modifier = Modifier.weight(1f).testTag("command_text_input"),
        )

        // Big Mic Button
        Box(
          modifier =
            Modifier.size(56.dp)
              .clip(CircleShape)
              .background(
                Brush.linearGradient(
                  if (isListening) listOf(CrimsonAlert, WarmAmber)
                  else listOf(SonicCyan, EmeraldSafe)
                )
              )
              .clickable(onClick = onMicClick)
              .testTag("big_mic_button"),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
            contentDescription = if (isListening) "Stop Listening" else "Tap to Speak Command",
            tint = ObsidianVoid,
            modifier = Modifier.size(28.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun ChatBubbleItem(message: ChatMessageUi) {
  var showJson by remember { mutableStateOf(false) }
  val timeStr = remember(message.timestampMs) {
    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestampMs))
  }

  when (message.role) {
    ChatRole.USER -> {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
          color = UserBubbleBg,
          shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
          border = BorderStroke(1.dp, SonicCyan.copy(alpha = 0.35f)),
          modifier = Modifier.widthIn(max = 310.dp),
        ) {
          Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(message.text, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(timeStr, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
          }
        }
      }
    }

    ChatRole.SYSTEM_NOTICE -> {
      Surface(
        color = SlateElevated.copy(alpha = 0.7f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, WarmAmber.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = message.text,
          style = MaterialTheme.typography.bodySmall,
          color = WarmAmber,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
      }
    }

    ChatRole.ASSISTANT -> {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
          color = AssistantBubbleBg,
          shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
          border = BorderStroke(1.dp, SlateBorder),
          modifier = Modifier.widthIn(max = 330.dp),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Box(
                modifier =
                  Modifier.size(22.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(SonicCyan, WarmAmber))),
                contentAlignment = Alignment.Center,
              ) {
                Text("S", style = MaterialTheme.typography.labelSmall, color = ObsidianVoid, fontWeight = FontWeight.Bold)
              }
              Text("Saathi", style = MaterialTheme.typography.labelLarge, color = SonicCyan)
              if (message.actionName.isNotBlank() && message.actionName != "READY") {
                val riskColor =
                  when (message.riskLevel) {
                    RiskLevel.HIGH -> CrimsonAlert
                    RiskLevel.MODERATE -> WarmAmber
                    RiskLevel.SAFE -> EmeraldSafe
                  }
                Surface(
                  color = riskColor.copy(alpha = 0.14f),
                  shape = RoundedCornerShape(6.dp),
                ) {
                  Text(
                    text = message.actionName,
                    style = MaterialTheme.typography.labelSmall,
                    color = riskColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  )
                }
              }
            }

            Text(message.text, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)

            if (message.parsedJson.isNotBlank()) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(timeStr, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Row(
                  modifier =
                    Modifier.clip(RoundedCornerShape(8.dp))
                      .clickable { showJson = !showJson }
                      .padding(horizontal = 6.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(Icons.Rounded.Code, contentDescription = "Toggle parsed JSON", tint = TextSecondary, modifier = Modifier.size(14.dp))
                  Spacer(Modifier.width(4.dp))
                  Text(
                    text = if (showJson) "Hide JSON" else "View JSON",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                  )
                }
              }

              AnimatedVisibility(visible = showJson) {
                Surface(color = ObsidianVoid, shape = RoundedCornerShape(10.dp)) {
                  Text(
                    text = message.parsedJson,
                    style = MaterialTheme.typography.labelSmall,
                    color = SonicCyan,
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                  )
                }
              }
            } else {
              Text(timeStr, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
          }
        }
      }
    }
  }
}
