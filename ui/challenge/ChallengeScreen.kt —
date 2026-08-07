package com.muthu.disciplinex.ui.challenge

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muthu.disciplinex.ui.components.PrimaryPillButton
import com.muthu.disciplinex.ui.theme.*

/**
 * Milestone 2 challenge screen — DUMMY rep counting for now.
 *
 * The "+1 Rep" button stands in for ML Kit pose detection, which will
 * replace it in a later milestone. This screen exists to prove out the
 * navigation flow (alarm dismiss -> challenge -> home) and the UI shell
 * before wiring in the camera.
 */
private const val TARGET_REPS = 10

@Composable
fun ChallengeScreen(
    exerciseName: String,
    duration: String,
    onComplete: () -> Unit
) {
    var reps by remember { mutableStateOf(0) }
    val isComplete = reps >= TARGET_REPS

    val progress by animateFloatAsState(
        targetValue = (reps.toFloat() / TARGET_REPS).coerceIn(0f, 1f),
        animationSpec = tween(300),
        label = "progress"
    )

    Scaffold(containerColor = Background) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = exerciseName,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$duration challenge · complete to unlock your phone",
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.weight(1f))

            ProgressRing(
                progress = progress,
                reps = reps,
                target = TARGET_REPS,
                isComplete = isComplete
            )

            Spacer(modifier = Modifier.weight(1f))

            if (!isComplete) {
                Text(
                    text = "Camera-based rep counting arrives next — tap to simulate a rep for now.",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                PrimaryPillButton(
                    text = "+1 Rep",
                    onClick = { reps = (reps + 1).coerceAtMost(TARGET_REPS) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = { reps = 0 }) {
                    Text("Reset", color = TextMuted)
                }
            } else {
                Text(
                    text = "Challenge complete!",
                    color = Success,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                PrimaryPillButton(
                    text = "Finish & Unlock Phone",
                    onClick = onComplete
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProgressRing(
    progress: Float,
    reps: Int,
    target: Int,
    isComplete: Boolean
) {
    val ringColor = if (isComplete) Success else OrangeEnd

    Box(
        modifier = Modifier.size(220.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val stroke = 18.dp.toPx()
            // Track
            drawArc(
                color = Divider,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                size = Size(size.width - stroke, size.height - stroke),
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2)
            )
            // Progress
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                size = Size(size.width - stroke, size.height - stroke),
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$reps",
                fontWeight = FontWeight.Bold,
                fontSize = 56.sp,
                color = TextPrimary
            )
            Text(
                text = "of $target reps",
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
