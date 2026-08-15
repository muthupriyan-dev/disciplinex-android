package com.muthu.disciplinex.ui.challenge

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.muthu.disciplinex.pose.RepUpdate
import com.muthu.disciplinex.pose.PoseCameraView
import com.muthu.disciplinex.ui.components.PrimaryPillButton
import com.muthu.disciplinex.ui.theme.*

private const val TARGET_REPS = 10
private val CAMERA_EXERCISES = setOf("Push-ups", "Squats")

@Composable
fun ChallengeScreen(
    exerciseName: String,
    duration: String,
    onComplete: () -> Unit
) {
    val usesCamera = exerciseName in CAMERA_EXERCISES

    var reps by remember(exerciseName) { mutableStateOf(0) }
    var liveProgress by remember(exerciseName) { mutableStateOf(0f) }
    var isTracking by remember(exerciseName) { mutableStateOf(false) }
    val isComplete = reps >= TARGET_REPS

    val displayProgress = if (usesCamera && !isComplete) liveProgress else (reps.toFloat() / TARGET_REPS)
    val progress by animateFloatAsState(
        targetValue = displayProgress.coerceIn(0f, 1f),
        animationSpec = tween(200),
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

            Spacer(modifier = Modifier.height(20.dp))

            if (usesCamera && !isComplete) {
                CameraRepTracker(
                    exerciseName = exerciseName,
                    onUpdate = { update ->
                        reps = update.reps.coerceAtMost(TARGET_REPS)
                        liveProgress = update.liveProgress
                        isTracking = update.isTracking
                    }
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            ProgressRing(
                progress = progress,
                reps = reps,
                target = TARGET_REPS,
                isComplete = isComplete
            )

            Spacer(modifier = Modifier.weight(1f))

            when {
                isComplete -> {
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
                usesCamera -> {
                    Text(
                        text = if (isTracking) "Keep going — full range of motion counts."
                        else "Get your whole body in frame to start counting.",
                        color = if (isTracking) TextMuted else OrangeEnd,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                else -> {
                    Text(
                        text = "Motion-sensor tracking for this exercise arrives next — tap to simulate a rep for now.",
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
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Small live camera preview + permission gate. Falls back to a manual
 * "request camera access" prompt if permission was revoked after
 * onboarding, instead of silently showing a blank preview.
 */
@Composable
private fun CameraRepTracker(exerciseName: String, onUpdate: (RepUpdate) -> Unit) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (hasCameraPermission) {
            PoseCameraView(
                exerciseName = exerciseName,
                onUpdate = onUpdate,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Camera access is needed to count reps",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                PrimaryPillButton(
                    text = "Grant Camera Access",
                    onClick = { launcher.launch(Manifest.permission.CAMERA) }
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(20.dp))
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
