package com.muthu.disciplinex.ui.challenge

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.muthu.disciplinex.data.ExerciseKinds
import com.muthu.disciplinex.data.UserPrefs
import com.muthu.disciplinex.pose.RepUpdate
import com.muthu.disciplinex.pose.PoseCameraView
import com.muthu.disciplinex.ui.components.PrimaryPillButton
import com.muthu.disciplinex.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Two kinds of challenge:
 *  - Timed (Reading / Meditation / Walking): a countdown that only runs while
 *    this screen is in front. No button adds progress; it completes only when
 *    the full time has been spent here.
 *  - Rep-based (Push-ups / Squats via camera, Jumping Jacks manual): finish
 *    the number of reps you set.
 */
@Composable
fun ChallengeScreen(
    exerciseName: String,
    targetReps: Int,
    durationMinutes: Int,
    onComplete: () -> Unit
) {
    if (ExerciseKinds.isTimed(exerciseName)) {
        TimedChallenge(exerciseName, durationMinutes, onComplete)
    } else {
        RepChallenge(exerciseName, targetReps, onComplete)
    }
}

@Composable
private fun TimedChallenge(exerciseName: String, durationMinutes: Int, onComplete: () -> Unit) {
    val context = LocalContext.current
    val totalMs = durationMinutes.coerceAtLeast(1) * 60_000L

    // Saved progress survives the app being closed or killed mid-challenge.
    var elapsedMs by remember {
        mutableStateOf(UserPrefs.getTimedProgressMs(context).coerceIn(0L, totalMs))
    }
    val isComplete = elapsedMs >= totalMs

    // Keep the screen awake while the timer is on screen.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Time only counts while this screen is actually in front.
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isResumed = true
                Lifecycle.Event.ON_PAUSE -> isResumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isResumed, isComplete) {
        if (!isResumed || isComplete) return@LaunchedEffect
        var last = SystemClock.elapsedRealtime()
        var sinceSave = 0L
        try {
            while (true) {
                delay(250)
                val now = SystemClock.elapsedRealtime()
                val step = now - last
                last = now
                elapsedMs = (elapsedMs + step).coerceAtMost(totalMs)
                sinceSave += step
                if (sinceSave >= 5_000L) {
                    UserPrefs.setTimedProgressMs(context, elapsedMs)
                    sinceSave = 0L
                }
                if (elapsedMs >= totalMs) break
            }
        } finally {
            // Runs on pause (leaving the app), completion, and screen close.
            UserPrefs.setTimedProgressMs(context, elapsedMs)
        }
    }

    val remainingSec = ((totalMs - elapsedMs + 999) / 1000).coerceAtLeast(0)
    val timeText = String.format(Locale.US, "%02d:%02d", remainingSec / 60, remainingSec % 60)
    val progress by animateFloatAsState(
        targetValue = (elapsedMs.toFloat() / totalMs).coerceIn(0f, 1f),
        animationSpec = tween(250),
        label = "timedProgress"
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
                text = "$durationMinutes min \u00b7 stay on this screen until the timer ends",
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.weight(1f))

            ProgressRing(
                progress = progress,
                centerText = timeText,
                subText = if (isComplete) "done" else "remaining",
                isComplete = isComplete
            )

            Spacer(modifier = Modifier.weight(1f))

            if (isComplete) {
                Text(
                    text = "Challenge complete!",
                    color = Success,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                PrimaryPillButton(text = "Finish & Unlock Phone", onClick = onComplete)
            } else {
                Text(
                    text = if (isResumed) "Timer running \u2014 leaving the app pauses it, and there's no skipping."
                    else "Paused \u2014 come back to this screen to continue.",
                    color = if (isResumed) TextMuted else OrangeEnd,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RepChallenge(exerciseName: String, target: Int, onComplete: () -> Unit) {
    val context = LocalContext.current
    val usesCamera = ExerciseKinds.isCamera(exerciseName)

    // Progress is saved, so leaving the app (or the system killing it) mid-set
    // doesn't throw away the reps already done.
    var reps by remember(exerciseName) {
        mutableStateOf(UserPrefs.getRepProgress(context).coerceIn(0, target))
    }
    // The camera counter starts from 0 every time it is (re)created; track its
    // last value so only NEW reps are added on top of the saved total.
    var lastCounterReps by remember(exerciseName) { mutableStateOf(0) }
    var liveProgress by remember(exerciseName) { mutableStateOf(0f) }
    var isTracking by remember(exerciseName) { mutableStateOf(false) }
    val isComplete = reps >= target

    // Phone is propped on the floor during push-ups: don't let the screen time out.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val displayProgress = if (usesCamera && !isComplete) liveProgress else (reps.toFloat() / target)
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
                text = "$target reps \u00b7 complete to unlock your phone",
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (usesCamera && !isComplete) {
                CameraRepTracker(
                    exerciseName = exerciseName,
                    onUpdate = { update ->
                        val newReps = update.reps - lastCounterReps
                        lastCounterReps = update.reps
                        if (newReps > 0) {
                            reps = (reps + newReps).coerceAtMost(target)
                            UserPrefs.setRepProgress(context, reps)
                        }
                        liveProgress = update.liveProgress
                        isTracking = update.isTracking
                    }
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            ProgressRing(
                progress = progress,
                centerText = "$reps",
                subText = "of $target reps",
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
                        text = if (isTracking) "Keep going \u2014 full range of motion counts."
                        else "Get your whole body in frame to start counting.",
                        color = if (isTracking) TextMuted else OrangeEnd,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                else -> {
                    Text(
                        text = "Motion-sensor tracking for this exercise arrives next \u2014 tap to count a rep for now.",
                        color = TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    PrimaryPillButton(
                        text = "+1 Rep",
                        onClick = {
                            reps = (reps + 1).coerceAtMost(target)
                            UserPrefs.setRepProgress(context, reps)
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = {
                        reps = 0
                        UserPrefs.setRepProgress(context, 0)
                    }) {
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
    centerText: String,
    subText: String,
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
                text = centerText,
                fontWeight = FontWeight.Bold,
                fontSize = 52.sp,
                color = TextPrimary
            )
            Text(
                text = subText,
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
