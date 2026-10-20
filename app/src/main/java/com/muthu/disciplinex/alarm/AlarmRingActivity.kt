package com.muthu.disciplinex.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muthu.disciplinex.MainActivity
import com.muthu.disciplinex.data.UserPrefs
import com.muthu.disciplinex.ui.theme.OrangeEnd

/**
 * Shown when the wake alarm fires. Displays over the lock screen and lets
 * the user dismiss the alarm.
 *
 * The sound/vibration itself is owned by [AlarmService] (a foreground
 * service), NOT this Activity — that's deliberate. Some OEM ROMs (Vivo/
 * OriginOS included) aggressively freeze or kill backgrounded activities to
 * save battery, which was silently cutting the alarm sound the moment the
 * user left the app, opened another app, or locked the phone. The service
 * survives all of that; this Activity is UI-only.
 *
 * Lock-screen display flags are still wrapped in try/catch: on some OEM ROMs
 * these can behave unexpectedly, and a crash here must never take down the
 * whole screen — the ability to dismiss the alarm has to survive regardless.
 */
class AlarmRingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        safely("showOverLockScreen") { showOverLockScreen() }

        val exerciseName = UserPrefs.getExercise(this)
        val summary = UserPrefs.getChallengeSummary(this)
        val skipsLeft = UserPrefs.getSkipsLeft(this)
        val snoozesLeft = UserPrefs.getSnoozesLeft(this)

        setContent {
            AlarmRingScreen(
                exerciseName = exerciseName,
                duration = summary,
                skipsLeft = skipsLeft,
                snoozesLeft = snoozesLeft,
                onDismiss = {
                    AlarmService.stop(this)
                    val challengeIntent = Intent(this, MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_START_CHALLENGE, true)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(challengeIntent)
                    finish()
                },
                onSnooze = {
                    // Silences the alarm and rings again later. The app lock stays on.
                    if (UserPrefs.useSnooze(this)) {
                        AlarmService.stop(this)
                        AlarmScheduler.scheduleSnooze(this, UserPrefs.SNOOZE_MINUTES)
                        finish()
                    }
                },
                onSkip = {
                    // Limited per week; unlocks everything without doing the challenge.
                    if (UserPrefs.skipToday(this)) {
                        AlarmService.stop(this)
                        finish()
                    }
                }
            )
        }
    }

    private inline fun safely(label: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.e("AlarmRingActivity", "$label failed", e)
        }
    }

    private fun showOverLockScreen() {
        // Set both the modern API (27+) methods AND the legacy window flags
        // together — on some OEM skins (Vivo/OriginOS included) the newer
        // setShowWhenLocked()/setTurnScreenOn() alone are unreliable, and
        // adding the older flags on top improves the odds of actually
        // showing over the lock screen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        keyguardManager?.requestDismissKeyguard(this, null)
    }

    override fun onBackPressed() {
        // Swallow back button — alarm must be dismissed via the button, not skipped.
    }
}

@Composable
private fun AlarmRingScreen(
    exerciseName: String,
    duration: String,
    skipsLeft: Int,
    snoozesLeft: Int,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit
) {
    var showSkipConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OrangeEnd),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Wake Up!",
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "$exerciseName \u00b7 $duration to complete",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(64.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = OrangeEnd
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Dismiss & Start Challenge", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onSnooze, enabled = snoozesLeft > 0) {
                Text(
                    "Snooze ${UserPrefs.SNOOZE_MINUTES} min ($snoozesLeft left)",
                    color = Color.White.copy(alpha = if (snoozesLeft > 0) 1f else 0.5f)
                )
            }
            TextButton(onClick = { showSkipConfirm = true }, enabled = skipsLeft > 0) {
                Text(
                    "Skip today ($skipsLeft left this week)",
                    color = Color.White.copy(alpha = if (skipsLeft > 0) 1f else 0.5f)
                )
            }
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
                    showSkipConfirm = false
                    onSkip()
                }) { Text("Skip today", color = OrangeEnd) }
            },
            dismissButton = {
                TextButton(onClick = { showSkipConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
