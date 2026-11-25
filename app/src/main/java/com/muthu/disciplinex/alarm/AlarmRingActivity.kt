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
import com.muthu.disciplinex.ui.theme.OrangeStart
import java.text.SimpleDateFormat
import kotlin.math.sin
import kotlin.math.cos
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.animation.core.animateFloatAsState
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

// The ring screen always uses this light look (it must read well at 5 AM, whatever the app theme is).
private val RingInk = Color(0xFF1E1E24)
private val RingMuted = Color(0xFF6B6B75)
private val RingBg = Color(0xFFFAFAFC)
private val RingBorder = Color(0xFFEDEDF2)

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

    // Current time, in the phone's own 12/24-hour style.
    val now = Date()
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    val clock = SimpleDateFormat(if (is24) "HH:mm" else "h:mm", AppSettings.locale).format(now)
    val suffix = if (is24) "" else SimpleDateFormat("a", AppSettings.locale).format(now)

    // The content eases in once when the screen opens.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val appear by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(500),
        label = "ringAppear"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RingBg)
            .drawBehind {
                // A soft red glow at the top of the screen.
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(OrangeEnd.copy(alpha = 0.13f), Color.Transparent),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.width * 1.15f
                    )
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp)
                .graphicsLayer {
                    alpha = appear
                    translationY = (1f - appear) * 24.dp.toPx()
                }
        ) {
            // ---- What is happening (scrolls on small screens / long Tamil text) ----
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
                        LiveEyebrow(
                            text = if (label.isBlank()) tr("Time to move!", "உடலை அசைக்க வேண்டிய நேரம்!") else label,
                            live = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ClockHero(clock, suffix)
                        Spacer(modifier = Modifier.height(16.dp))
                        InfoPill("$exercise \u00b7 $summary")
                        Spacer(modifier = Modifier.height(32.dp))
                        CountdownRing(msLeft = msLeft, windowMs = windowMs, pulse = true)
                        Spacer(modifier = Modifier.height(24.dp))
                        LockCaption(
                            tr(
                                "Miss it and your streak resets. Your apps stay locked for 24 hours.",
                                "தவறினால் தொடர்ச்சியான சாதனை 0 ஆகும். ஆப்கள் 24 மணி நேரம் பூட்டியே இருக்கும்."
                            )
                        )
                    }

                    ChallengeSession.Phase.IN_PROGRESS -> {
                        LiveEyebrow(text = tr("Challenge in progress", "சவால் நடந்துகொண்டிருக்கிறது"), live = true)
                        Spacer(modifier = Modifier.height(16.dp))
                        InfoPill("$exercise \u00b7 $summary")
                        Spacer(modifier = Modifier.height(32.dp))
                        CountdownRing(msLeft = msLeft, windowMs = windowMs, pulse = false)
                    }

                    ChallengeSession.Phase.MISSED -> {
                        val unlockAt = DateFormat.getTimeInstance(DateFormat.SHORT)
                            .format(Date(ChallengeSession.lockEndsAtMillis(context)))
                        StatusBadge(Icons.Filled.Lock)
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            tr("Challenge missed", "சவால் தவறிவிட்டது"),
                            color = RingInk,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = tr(
                                "You didn't finish in time. Your streak is reset and your apps stay locked until $unlockAt.",
                                "நீங்கள் நேரத்துக்குள் முடிக்கவில்லை. தொடர்ச்சியான சாதனை 0 ஆகிவிட்டது; $unlockAt வரை ஆப்கள் பூட்டியே இருக்கும்."
                            ),
                            color = RingMuted,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    ChallengeSession.Phase.IDLE -> {
                        StatusBadge(Icons.Filled.CheckCircle)
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            tr("Nothing pending", "காத்திருப்பது எதுவும் இல்லை"),
                            color = RingInk,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ---- Buttons stay in the same place, easy to reach with a thumb ----
            Spacer(modifier = Modifier.height(16.dp))
            when (phase) {
                ChallengeSession.Phase.WAITING -> {
                    PrimaryRingButton(tr("Start challenge", "சவாலை தொடங்கு"), SuccessButton, onStart, Icons.Filled.PlayArrow)
                    Spacer(modifier = Modifier.height(12.dp))
                    TonalRingButton(tr("Dismiss", "நிறுத்து"), onClose)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = tr(
                            "Dismiss only stops the sound. The time limit keeps running and your apps stay locked.",
                            "நிறுத்து என்பது ஒலியை மட்டுமே நிறுத்தும். நேர வரம்பு தொடர்ந்து ஓடும், ஆப்கள் பூட்டியே இருக்கும்."
                        ),
                        color = RingMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ChallengeSession.Phase.IN_PROGRESS ->
                    PrimaryRingButton(tr("Continue challenge", "சவாலை தொடர்"), SuccessButton, onStart, Icons.Filled.PlayArrow)
                ChallengeSession.Phase.MISSED ->
                    PrimaryRingButton(tr("OK", "சரி"), OrangeEnd, onClose)
                ChallengeSession.Phase.IDLE ->
                    PrimaryRingButton(tr("Close", "மூடு"), OrangeEnd, onClose)
            }
        }
    }
}

/** Small red label with a blinking dot, like a "live" indicator. */
@Composable
private fun LiveEyebrow(text: String, live: Boolean) {
    val transition = rememberInfiniteTransition(label = "liveDot")
    val dotAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "liveDotAlpha"
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(OrangeEnd.copy(alpha = if (live) dotAlpha else 1f))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = OrangeEnd,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

/** The current time, big, like a real alarm clock. */
@Composable
private fun ClockHero(clock: String, suffix: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = clock,
            color = RingInk,
            fontSize = 68.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-1.5).sp
        )
        if (suffix.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = suffix,
                color = RingMuted,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 14.dp)
            )
        }
    }
}

/** White pill with a soft shadow, e.g. "Squats - 10 reps". */
@Composable
private fun InfoPill(text: String) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .shadow(3.dp, shape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
            .background(Color.White, shape)
            .border(1.dp, RingBorder, shape)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(OrangeEnd)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = RingInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/**
 * The time left, as a thin gradient ring that shrinks with a glowing dot at its end.
 * The time turns red only in the last minute, so red means "hurry".
 */
@Composable
private fun CountdownRing(msLeft: Long, windowMs: Long, pulse: Boolean) {
    val progress = (msLeft.toFloat() / windowMs).coerceIn(0f, 1f)
    val sec = (msLeft / 1000).toInt()
    val timeText = if (sec >= 3600) {
        String.format(Locale.US, "%d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)
    } else {
        String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)
    }

    val transition = rememberInfiniteTransition(label = "ringGlow")
    val breathing by transition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.16f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "ringGlowAlpha"
    )
    val glow = if (pulse) breathing else 0f

    Box(modifier = Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val diameter = size.minDimension - stroke * 2
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            if (glow > 0f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(OrangeEnd.copy(alpha = glow), Color.Transparent),
                        center = center,
                        radius = size.minDimension * 0.62f
                    )
                )
            }
            // Track
            drawArc(
                color = OrangeEnd.copy(alpha = 0.10f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (progress > 0f) {
                // Progress: starts at the top and runs clockwise.
                rotate(degrees = -90f, pivot = center) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(OrangeStart, OrangeEnd, OrangeStart), center = center),
                        startAngle = 0f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                // Glowing dot at the end of the arc.
                val angle = Math.toRadians((-90f + 360f * progress).toDouble())
                val radius = diameter / 2f
                val head = Offset(
                    center.x + (radius * cos(angle)).toFloat(),
                    center.y + (radius * sin(angle)).toFloat()
                )
                drawCircle(color = Color.White, radius = stroke * 0.85f, center = head)
                drawCircle(color = OrangeEnd, radius = stroke * 0.45f, center = head)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = timeText,
                color = if (sec < 60) OrangeEnd else RingInk,
                fontSize = 48.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1).sp
            )
            Text(
                text = tr("left to finish", "முடிக்க மீதம்"),
                color = RingMuted,
                fontSize = 14.sp
            )
        }
    }
}

/** One quiet line under the ring: what happens if you miss it. */
@Composable
private fun LockCaption(text: String) {
    Row(
        modifier = Modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = RingMuted, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = RingMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

/** A big round icon for the "missed" and "nothing pending" screens. */
@Composable
private fun StatusBadge(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(OrangeEnd.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center
    ) {
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

/** The main action: tall, rounded, with a soft shadow. */
@Composable
private fun PrimaryRingButton(text: String, color: Color, onClick: () -> Unit, icon: ImageVector? = null) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(30.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

/** The second action: soft red background with red text, so it does not compete with the main one. */
@Composable
private fun TonalRingButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(containerColor = OrangeEnd.copy(alpha = 0.10f), contentColor = OrangeEnd),
        elevation = null,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
