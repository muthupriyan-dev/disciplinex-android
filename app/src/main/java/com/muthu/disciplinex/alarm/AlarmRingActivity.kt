package com.muthu.disciplinex.alarm

import com.muthu.disciplinex.data.tr
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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
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
import com.muthu.disciplinex.data.AppSettings
import com.muthu.disciplinex.data.ExerciseKinds
import com.muthu.disciplinex.ui.theme.OrangeDeep
import com.muthu.disciplinex.ui.theme.OrangeEnd
import com.muthu.disciplinex.ui.theme.SuccessButton
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Shown when an alarm fires. Displays over the lock screen with ONE big button
 * and a countdown to the alarm's time limit: finish the challenge in time, or it counts as missed.
 *
 * The sound/vibration itself is owned by [AlarmService] (a foreground
 * service), NOT this Activity — some OEM ROMs (Vivo/OriginOS included) kill
 * backgrounded activities to save battery, which was cutting the alarm sound.
 */
class AlarmRingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.load(this)

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
    var msLeft by remember { mutableStateOf(ChallengeSession.msLeftToFinish(context)) }
    val windowMs = remember { ChallengeSession.windowMs(context).coerceAtLeast(1L) }

    // Tick every second so the countdown (and the "missed" state) stay live.
    LaunchedEffect(Unit) {
        while (true) {
            phase = ChallengeSession.phase(context)
            msLeft = ChallengeSession.msLeftToFinish(context)
            delay(1000)
        }
    }

    val exercise = ExerciseKinds.display(ChallengeSession.exercise(context))
    val summary = ChallengeSession.summary(context)
    val label = ChallengeSession.label(context)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFFFF1ED))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // ---- Top: what is happening (scrolls on small screens / long Tamil text) ----
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (phase) {
                    ChallengeSession.Phase.WAITING -> {
                        Text(
                            text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()),
                            color = OrangeDeep,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PulsingBadge(Icons.Filled.Alarm, pulse = true)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (label.isBlank()) tr("Time to move!", "உடலை அசைக்க வேண்டிய நேரம்!") else label,
                            color = OrangeEnd,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        InfoPill("$exercise \u00b7 $summary")
                        Spacer(modifier = Modifier.height(24.dp))
                        CountdownRing(msLeft = msLeft, windowMs = windowMs)
                        Spacer(modifier = Modifier.height(22.dp))
                        WarningCard(
                            tr(
                                "Miss it and your streak resets. Your apps stay locked for 24 hours.",
                                "தவறினால் தொடர்ச்சியான சாதனை 0 ஆகும். ஆப்கள் 24 மணி நேரம் பூட்டியே இருக்கும்."
                            )
                        )
                    }

                    ChallengeSession.Phase.IN_PROGRESS -> {
                        PulsingBadge(Icons.Filled.Alarm, pulse = false)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            tr("Challenge in progress", "சவால் நடந்துகொண்டிருக்கிறது"),
                            color = OrangeEnd,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        InfoPill("$exercise \u00b7 $summary")
                        Spacer(modifier = Modifier.height(24.dp))
                        CountdownRing(msLeft = msLeft, windowMs = windowMs)
                    }

                    ChallengeSession.Phase.MISSED -> {
                        val unlockAt = DateFormat.getTimeInstance(DateFormat.SHORT)
                            .format(Date(ChallengeSession.lockEndsAtMillis(context)))
                        PulsingBadge(Icons.Filled.Lock, pulse = false)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            tr("Challenge missed", "சவால் தவறிவிட்டது"),
                            color = OrangeEnd,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = tr(
                                "You didn't finish in time. Your streak is reset and your apps stay locked until $unlockAt.",
                                "நீங்கள் நேரத்துக்குள் முடிக்கவில்லை. தொடர்ச்சியான சாதனை 0 ஆகிவிட்டது; $unlockAt வரை ஆப்கள் பூட்டியே இருக்கும்."
                            ),
                            color = OrangeDeep,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    ChallengeSession.Phase.IDLE -> {
                        PulsingBadge(Icons.Filled.CheckCircle, pulse = false)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            tr("Nothing pending", "காத்திருப்பது எதுவும் இல்லை"),
                            color = OrangeEnd,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ---- Bottom: the buttons stay in the same place, easy to reach with a thumb ----
            Spacer(modifier = Modifier.height(12.dp))
            when (phase) {
                ChallengeSession.Phase.WAITING -> {
                    BigButton(tr("Start challenge", "சவாலை தொடங்கு"), SuccessButton, 62.dp, onStart)
                    Spacer(modifier = Modifier.height(10.dp))
                    BigButton(tr("Dismiss", "நிறுத்து"), OrangeEnd, 50.dp, onClose)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = tr(
                            "Dismiss only stops the sound. The time limit keeps running and your apps stay locked.",
                            "நிறுத்து என்பது ஒலியை மட்டுமே நிறுத்தும். நேர வரம்பு தொடர்ந்து ஓடும், ஆப்கள் பூட்டியே இருக்கும்."
                        ),
                        color = OrangeDeep,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ChallengeSession.Phase.IN_PROGRESS ->
                    BigButton(tr("Continue challenge", "சவாலை தொடர்"), SuccessButton, 62.dp, onStart)
                ChallengeSession.Phase.MISSED ->
                    BigButton(tr("OK", "சரி"), OrangeEnd, 56.dp, onClose)
                ChallengeSession.Phase.IDLE ->
                    BigButton(tr("Close", "மூடு"), OrangeEnd, 56.dp, onClose)
            }
        }
    }
}

/** A round red badge with an icon. While the alarm rings it gently pulses. */
@Composable
private fun PulsingBadge(icon: ImageVector, pulse: Boolean) {
    val transition = rememberInfiniteTransition(label = "badgePulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "badgeScale"
    )
    val glow by transition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.04f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "badgeGlow"
    )
    Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
        if (pulse) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(CircleShape)
                    .background(OrangeEnd.copy(alpha = glow))
            )
        }
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(OrangeEnd),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
        }
    }
}

/** Soft red pill, e.g. "Squats - 10 reps". */
@Composable
private fun InfoPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(OrangeEnd.copy(alpha = 0.10f))
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(text, color = OrangeDeep, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** A ring that empties as the time limit runs out, with the time in the middle. */
@Composable
private fun CountdownRing(msLeft: Long, windowMs: Long) {
    val progress = (msLeft.toFloat() / windowMs).coerceIn(0f, 1f)
    val sec = (msLeft / 1000).toInt()
    val timeText = if (sec >= 3600) {
        String.format(Locale.US, "%d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)
    } else {
        String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)
    }
    Box(modifier = Modifier.size(230.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            drawArc(
                color = OrangeEnd.copy(alpha = 0.12f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = OrangeEnd,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(timeText, color = OrangeEnd, fontSize = 50.sp, fontWeight = FontWeight.Bold)
            Text(tr("left to finish", "முடிக்க மீதம்"), color = OrangeDeep, fontSize = 14.sp)
        }
    }
}

/** What happens if the challenge is missed, in a small card with a lock icon. */
@Composable
private fun WarningCard(text: String) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(1.dp, OrangeEnd.copy(alpha = 0.25f), shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = OrangeEnd, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, color = OrangeDeep, fontSize = 13.sp)
    }
}

@Composable
private fun BigButton(text: String, color: Color, height: Dp, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}
