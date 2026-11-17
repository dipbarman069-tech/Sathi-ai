package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.ActionExecutionStatus
import com.example.domain.model.ActionLogUi
import com.example.domain.model.RiskLevel
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CrimsonSurface
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.SonicCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActionLogScreen(
  logs: List<ActionLogUi>,
  killSwitchActive: Boolean,
  onTriggerKillSwitch: () -> Unit,
  onResumeFromKillSwitch: () -> Unit,
  onClearLogs: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxSize().background(ObsidianVoid).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // One-Tap "Stop Everything" Kill Switch Hero Card
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = if (killSwitchActive) CrimsonSurface else MidnightSurface),
      border = BorderStroke(1.5.dp, CrimsonAlert.copy(alpha = 0.7f)),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Emergency Control",
          style = MaterialTheme.typography.titleMedium,
          color = TextPrimary,
        )
        Text(
          text =
            "Halts TTS voice output, stops microphone & wake-word service, clears transient notifications, cancels pending actions, and relocks all banking/UPI apps.",
          style = MaterialTheme.typography.bodySmall,
          color = TextSecondary,
        )
        if (killSwitchActive) {
          Button(
            onClick = onResumeFromKillSwitch,
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSafe, contentColor = ObsidianVoid),
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("log_resume_button"),
          ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Resume Saathi", style = MaterialTheme.typography.labelLarge)
          }
        } else {
          Button(
            onClick = onTriggerKillSwitch,
            colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert, contentColor = TextPrimary),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("stop_everything_kill_button"),
          ) {
            Icon(Icons.Rounded.StopCircle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("STOP EVERYTHING (Kill Switch)", style = MaterialTheme.typography.labelLarge)
          }
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column {
        Text("Encrypted Action Audit Log", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Text("Stored locally with Android Keystore AES-256-GCM", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
      }
      if (logs.isNotEmpty()) {
        IconButton(onClick = onClearLogs, modifier = Modifier.size(48.dp).testTag("clear_logs_button")) {
          Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear encrypted logs", tint = TextSecondary)
        }
      }
    }

    if (logs.isEmpty()) {
      Card(
        colors = CardDefaults.cardColors(containerColor = MidnightSurface),
        border = BorderStroke(1.dp, SlateBorder),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(28.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(Icons.Rounded.History, contentDescription = null, tint = SonicCyan, modifier = Modifier.size(36.dp))
          Text("No actions logged yet", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
          Text(
            "Every action Saathi evaluates or executes appears here with full transparency.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        items(logs, key = { it.id }) { log ->
          ActionLogRowCard(log = log)
        }
      }
    }
  }
}

@Composable
private fun ActionLogRowCard(log: ActionLogUi) {
  val dateText = remember(log.timestampMs) {
    SimpleDateFormat("dd MMM, hh:mm:ss a", Locale.getDefault()).format(Date(log.timestampMs))
  }
  val statusColor =
    when (log.status) {
      ActionExecutionStatus.EXECUTED -> EmeraldSafe
      ActionExecutionStatus.CANCELLED -> WarmAmber
      ActionExecutionStatus.BLOCKED,
      ActionExecutionStatus.FAILED -> CrimsonAlert
      ActionExecutionStatus.PENDING_CONFIRMATION -> SonicCyan
    }
  val riskColor =
    when (log.riskLevel) {
      RiskLevel.HIGH -> CrimsonAlert
      RiskLevel.MODERATE -> WarmAmber
      RiskLevel.SAFE -> EmeraldSafe
    }

  Card(
    colors = CardDefaults.cardColors(containerColor = SlateElevated),
    border = BorderStroke(1.dp, SlateBorder),
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(log.action, style = MaterialTheme.typography.labelLarge, color = SonicCyan)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Surface(color = riskColor.copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
            Text(
              log.riskLevel.name,
              style = MaterialTheme.typography.labelSmall,
              color = riskColor,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
          Surface(color = statusColor.copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
            Text(
              log.status.name,
              style = MaterialTheme.typography.labelSmall,
              color = statusColor,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
        }
      }
      Text("\"${log.rawCommand}\"", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
      if (log.target.isNotBlank() || log.parametersJson.isNotBlank()) {
        Text(
          "Target: ${log.target.ifBlank { "-" }}  •  Params: ${log.parametersJson.ifBlank { "{}" }}",
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondary,
        )
      }
      Text("${log.outcomeNote}  •  $dateText", style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
  }
}
