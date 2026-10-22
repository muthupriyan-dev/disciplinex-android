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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muthu.disciplinex.MainActivity
import com.muthu.disciplinex.data.ChallengeSession
import com.muthu.disciplinex.ui.theme.OrangeEnd
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Shown when an alarm fires. Displays over the lock screen with ONE big button
 * and a 10-minute countdown: start the challenge in time, or it counts as missed.
 *
 * The sound/vibration itself is owned by [AlarmService] (a foreground
 * service), NOT this Activity — some OEM ROMs (Vivo/OriginOS included) kill
 * backgrounded activities to save battery, which was cutting the alarm sound.
 */
class AlarmRingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        safely("showOverLockScreen") { showOverLockScreen() }

        setContent {
            AlarmRingScreen(
                onStart = {
                    AlarmService.stop(this)
                    val challengeIntent = Intent(this, MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_START_CHALLENGE, true)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(challengeIntent)
                    finish()
                },
                onClose = {
                    AlarmService.stop(this)
                    finish()
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
        // Both the modern API and the legacy window flags: on some OEM skins
        // (Vivo/OriginOS included) the newer calls alone are unreliable.
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
        // Swallow back button — the alarm can only be answered with the button.
    }
}

@Composable
private fun AlarmRingScreen(onStart: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var phase by remember { mutableStateOf(ChallengeSession.phase(context)) }
    var msLeft by remember { mutableStateOf(ChallengeSession.msLeftToStart(context)) }

    // Tick every second so the countdown (and the "missed" state) stay live.
    LaunchedEffect(Unit) {
        while (true) {
            phase = ChallengeSession.phase(context)
            msLeft = ChallengeSession.msLeftToStart(context)
            delay(1000)
        }
    }

    val exercise = ChallengeSession.exercise(context)
    val summary = ChallengeSession.summary(context)
    val label = ChallengeSession.label(context)

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
            when (phase) {
                ChallengeSession.Phase.WAITING -> {
                    Text(
                        text = if (label.isBlank()) "Time to move!" else label,
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "$exercise \u00b7 $summary",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(36.dp))
                    val sec = (msLeft / 1000).toInt()
                    Text(
                        text = String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60),
                        color = Color.White,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "left to start \u2014 after that the challenge is missed and your streak resets",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(40.dp))
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = OrangeEnd
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text("Start challenge", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onClose,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.5.dp, Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text("Dismiss", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Dismiss only stops the sound. Your challenge is still due and your apps stay locked.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }

                ChallengeSession.Phase.IN_PROGRESS -> {
                    Text("Challenge in progress", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = OrangeEnd),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) { Text("Continue challenge", fontWeight = FontWeight.Bold) }
                }

                ChallengeSession.Phase.MISSED -> {
                    val unlockAt = DateFormat.getTimeInstance(DateFormat.SHORT)
                        .format(Date(ChallengeSession.lockEndsAtMillis(context)))
                    Text("Challenge missed", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "You didn't start within 10 minutes. Your streak is reset and your apps stay locked until $unlockAt.",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = OrangeEnd),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) { Text("OK", fontWeight = FontWeight.Bold) }
                }

                ChallengeSession.Phase.IDLE -> {
                    Text("Nothing pending", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = OrangeEnd),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) { Text("Close", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
