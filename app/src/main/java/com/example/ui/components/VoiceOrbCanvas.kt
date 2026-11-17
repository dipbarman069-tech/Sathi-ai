package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.domain.model.VoiceOrbState
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.SonicCyan
import com.example.ui.theme.WarmAmber
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Custom Canvas animated sonic orb + concentric harmonic waveform rings reacting
 * smoothly to real-time microphone RMS energy and assistant state.
 */
@Composable
fun VoiceOrbCanvas(
  orbState: VoiceOrbState,
  rmsLevel: Float,
  modifier: Modifier = Modifier,
) {
  val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
  val phase by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = (2 * PI).toFloat(),
      animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
      label = "orb_phase",
    )
  val breathe by
    infiniteTransition.animateFloat(
      initialValue = 0.94f,
      targetValue = 1.06f,
      animationSpec =
        infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
      label = "orb_breathe",
    )

  val smoothRms by
    animateFloatAsState(
      targetValue =
        when (orbState) {
          VoiceOrbState.LISTENING -> rmsLevel.coerceIn(0.15f, 1f)
          VoiceOrbState.SPEAKING -> 0.55f
          VoiceOrbState.THINKING -> 0.35f
          VoiceOrbState.AWAITING_CONFIRMATION -> 0.25f
          VoiceOrbState.IDLE -> 0.08f
        },
      animationSpec = spring(dampingRatio = 0.7f, stiffness = 240f),
      label = "rms_spring",
    )

  val primaryColor by
    animateColorAsState(
      targetValue =
        when (orbState) {
          VoiceOrbState.LISTENING -> SonicCyan
          VoiceOrbState.SPEAKING -> WarmAmber
          VoiceOrbState.THINKING -> EmeraldSafe
          VoiceOrbState.AWAITING_CONFIRMATION -> WarmAmber
          VoiceOrbState.IDLE -> SonicCyan.copy(alpha = 0.75f)
        },
      label = "primary_orb_color",
    )

  val secondaryColor by
    animateColorAsState(
      targetValue =
        when (orbState) {
          VoiceOrbState.SPEAKING -> SonicCyan
          VoiceOrbState.LISTENING -> EmeraldSafe
          VoiceOrbState.THINKING -> SonicCyan
          else -> WarmAmber.copy(alpha = 0.65f)
        },
      label = "secondary_orb_color",
    )

  Box(modifier = modifier) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val center = Offset(size.width / 2f, size.height / 2f)
      val minDim = size.minDimension
      val baseRadius = minDim * 0.21f * breathe * (1f + smoothRms * 0.24f)

      // Outer atmospheric radial halo
      drawCircle(
        brush =
          Brush.radialGradient(
            colors = listOf(primaryColor.copy(alpha = 0.26f), Color.Transparent),
            center = center,
            radius = minDim * 0.48f,
          ),
        radius = minDim * 0.48f,
        center = center,
      )

      // Concentric harmonic waveform rings
      for (ringIndex in 1..3) {
        val ringRadius = baseRadius * (1.28f + ringIndex * 0.26f)
        val wavePath = Path()
        val points = 72
        val amplitude = (4f + smoothRms * 22f) / ringIndex
        for (i in 0..points) {
          val theta = (i.toFloat() / points) * (2f * PI.toFloat())
          val harmonic =
            sin(theta * (3 + ringIndex) + phase * (if (ringIndex % 2 == 0) 1f else -1.3f)) *
              amplitude
          val r = ringRadius + harmonic
          val x = center.x + r * cos(theta)
          val y = center.y + r * sin(theta)
          if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
        }
        wavePath.close()
        drawPath(
          path = wavePath,
          color = primaryColor.copy(alpha = (0.45f - ringIndex * 0.1f).coerceAtLeast(0.1f)),
          style = Stroke(width = (3.2f - ringIndex * 0.6f).dp.toPx(), cap = StrokeCap.Round),
        )
      }

      // Inner luminous orb gradient core
      drawCircle(
        brush =
          Brush.radialGradient(
            colors =
              listOf(
                Color.White.copy(alpha = 0.9f),
                primaryColor,
                secondaryColor.copy(alpha = 0.85f),
                Color(0xFF0A1424),
              ),
            center = Offset(center.x - baseRadius * 0.22f, center.y - baseRadius * 0.22f),
            radius = baseRadius * 1.15f,
          ),
        radius = baseRadius,
        center = center,
      )
    }
  }
}
