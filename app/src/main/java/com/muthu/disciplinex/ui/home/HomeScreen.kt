package com.muthu.disciplinex.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.muthu.disciplinex.alarm.AlarmService
import com.muthu.disciplinex.data.ExerciseKinds
import com.muthu.disciplinex.data.UserPrefs
import com.muthu.disciplinex.lock.LockAccessibilityService
import com.muthu.disciplinex.ui.components.GradientHeroCard
import com.muthu.disciplinex.ui.components.TargetStepper
import com.muthu.disciplinex.ui.theme.OrangeEnd
import com.muthu.disciplinex.ui.theme.Success
import com.muthu.disciplinex.ui.theme.Surface
import com.muthu.disciplinex.ui.theme.TextMuted
import com.muthu.disciplinex.ui.theme.Warning

@Composable
fun HomeScreen(wakeTime: String, exerciseName: String, onOpenSettings: () -> Unit) {
    val context = LocalContext.current

    // Re-read lock/accessibility state whenever the user returns to this
    // screen (e.g. after switching the service on in system Settings).
    var refreshTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val serviceEnabled = remember(refreshTick) { LockAccessibilityService.isEnabled(context) }
    val lockActive = remember(refreshTick) { UserPrefs.isLockActive(context) }
    val isTestLock = remember(refreshTick) { UserPrefs.isTestLock(context) }
    val skipsLeft = remember(refreshTick) { UserPrefs.getSkipsLeft(context) }
    var testArmed by remember { mutableStateOf(false) }
    var showSkipConfirm by remember { mutableStateOf(false) }

    // Challenge target: reps for rep exercises, minutes for timed ones.
    val isTimed = ExerciseKinds.isTimed(exerciseName)
    var reps by remember(refreshTick) { mutableStateOf(UserPrefs.getTargetReps(context)) }
    var minutes by remember(refreshTick) { mutableStateOf(UserPrefs.getDurationMinutes(context)) }
    val summary = if (isTimed) "$minutes min" else "$reps reps"

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text("Today", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))

            GradientHeroCard(
                title = "$wakeTime challenge",
                subtitle = "$exerciseName \u00b7 $summary to complete"
            ) {
                Text(
                    "Your alarm will open this challenge automatically \u2014 dismiss it from the alarm screen to start.",
                    color = Surface.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ---- Challenge target card ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Surface)
                    .padding(18.dp)
            ) {
                Text("Your target", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    if (isTimed) "How long you have to stay on the $exerciseName timer."
                    else "How many $exerciseName you have to do.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (isTimed) {
                    TargetStepper(
                        value = minutes,
                        unit = "minutes",
                        min = 1,
                        max = 180,
                        enabled = !lockActive,
                        onValueChange = {
                            minutes = it
                            UserPrefs.setDurationMinutes(context, it)
                        }
                    )
                } else {
                    TargetStepper(
                        value = reps,
                        unit = "reps",
                        min = 1,
                        max = 200,
                        enabled = !lockActive,
                        onValueChange = {
                            reps = it
                            UserPrefs.setTargetReps(context, it)
                        }
                    )
                }
                if (lockActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Locked until you finish today's challenge, so the target can't be changed right now.",
                        color = TextMuted,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ---- App lock card ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Surface)
                    .padding(18.dp)
            ) {
                Text("App lock", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))

                when {
                    !serviceEnabled -> {
                        Text(
                            "Off \u2014 turn on the DisciplineX App Lock accessibility service so every app except Phone, Camera and your allowed apps stays locked until you finish your challenge.",
                            color = Warning,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OrangeEnd)
                        ) { Text("Enable app lock") }
                    }
                    lockActive -> {
                        Text(
                            "Locked right now \u2014 only Phone, Camera and your allowed apps work. Finish your challenge to unlock everything else.",
                            color = OrangeEnd,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    else -> {
                        Text(
                            "Ready \u2014 when your alarm rings, every app except Phone, Camera and your allowed apps locks until you finish the challenge.",
                            color = Success,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // A real (alarm) lock can be skipped a limited number of times a week.
                if (lockActive && !isTestLock) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showSkipConfirm = true },
                        enabled = skipsLeft > 0,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Skip today ($skipsLeft left this week)", color = OrangeEnd)
                    }
                }

                if (serviceEnabled && !lockActive) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            UserPrefs.startLock(context, isTest = true)
                            testArmed = true
                            refreshTick++
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    ) { Text("Test app lock now", color = OrangeEnd) }
                }

                if (lockActive && isTestLock) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            UserPrefs.cancelTestLock(context)
                            testArmed = false
                            refreshTick++
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    ) { Text("End test lock", color = OrangeEnd) }
                }

                if (testArmed && lockActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Test lock armed \u2014 open any app other than Phone, Camera or an allowed app. You should land on the challenge screen.",
                        color = TextMuted,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) { Text("Alarm, exercise & allowed apps", color = OrangeEnd) }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Streaks and the leaderboard are next.",
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    if (showSkipConfirm) {
        AlertDialog(
            onDismissRequest = { showSkipConfirm = false },
            title = { Text("Skip today's challenge?") },
            text = {
                Text("Your apps unlock without doing the challenge. This uses 1 of your $skipsLeft skips left this week.")
            },
            confirmButton = {
                TextButton(onClick = {
                    if (UserPrefs.skipToday(context)) {
                        // Also silence the alarm if it is still ringing.
                        try {
                            AlarmService.stop(context)
                        } catch (e: Exception) {
                            // Not ringing / can't reach the service — nothing to stop.
                        }
                    }
                    showSkipConfirm = false
                    refreshTick++
                }) { Text("Skip today", color = OrangeEnd) }
            },
            dismissButton = {
                TextButton(onClick = { showSkipConfirm = false }) { Text("Cancel", color = TextMuted) }
            }
        )
    }
}
