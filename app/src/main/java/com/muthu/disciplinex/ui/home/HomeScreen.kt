package com.muthu.disciplinex.ui.home

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.muthu.disciplinex.alarm.AlarmScheduler
import com.muthu.disciplinex.data.AlarmEntry
import com.muthu.disciplinex.data.AlarmStore
import com.muthu.disciplinex.data.ChallengeSession
import com.muthu.disciplinex.data.StreakStore
import com.muthu.disciplinex.data.UserPrefs
import com.muthu.disciplinex.lock.LockAccessibilityService
import com.muthu.disciplinex.ui.components.GradientHeroCard
import com.muthu.disciplinex.ui.components.TourOverlay
import com.muthu.disciplinex.ui.components.TourStep
import com.muthu.disciplinex.ui.components.rememberTourState
import com.muthu.disciplinex.ui.components.tourTarget
import com.muthu.disciplinex.ui.theme.Danger
import com.muthu.disciplinex.ui.theme.OrangeEnd
import com.muthu.disciplinex.ui.theme.Success
import com.muthu.disciplinex.ui.theme.Surface
import com.muthu.disciplinex.ui.theme.TextMuted
import com.muthu.disciplinex.ui.theme.TextPrimary
import com.muthu.disciplinex.ui.theme.Warning
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

private val tourSteps = listOf(
    TourStep(
        "streak", "Your streak \uD83D\uDD25",
        "Finish every challenge on time and this number grows. Miss one and it goes back to 0."
    ),
    TourStep(
        "status", "Status",
        "Shows if your app lock is ready, and what is waiting after an alarm rings. You get the time limit you set on the alarm to finish after it rings."
    ),
    TourStep(
        "alarm", "An alarm",
        "Each card is one alarm. Tap it to change the time, repeat days, exercise and target. Add as many as you like \u2014 morning, evening, any time."
    ),
    TourStep(
        "switch", "On / off",
        "Switch an alarm on or off without deleting it."
    ),
    TourStep(
        "fab", "Add an alarm",
        "Tap + to add a new alarm. Pick the time, the days, the exercise (push-ups, squats, jumping jacks, walking\u2026) and how many reps or minutes."
    ),
    TourStep(
        "settings", "Settings",
        "Choose which apps stay open while your phone is locked. Phone and Camera are always open."
    ),
    TourStep(
        "help", "Need this again?",
        "Tap this button any time to see this guide again."
    )
)

@Composable
fun HomeScreen(
    onAddAlarm: () -> Unit,
    onEditAlarm: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onStartChallenge: () -> Unit
) {
    val context = LocalContext.current

    // Re-read everything whenever the user returns to this screen.
    var refreshTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val alarms = remember(refreshTick) { AlarmStore.getAll(context) }
    val phase = remember(refreshTick) { ChallengeSession.phase(context) }
    val locked = phase != ChallengeSession.Phase.IDLE
    val serviceEnabled = remember(refreshTick) { LockAccessibilityService.isEnabled(context) }
    val streak = remember(refreshTick) { StreakStore.currentStreak(context) }
    val best = remember(refreshTick) { StreakStore.bestStreak(context) }
    val week = remember(refreshTick) { StreakStore.lastSevenDays(context) }
    val nextText = remember(refreshTick) { nextAlarmText(context) }
    val msLeft = remember(refreshTick) { ChallengeSession.msLeftToFinish(context) }

    // While a challenge is waiting to be started, tick once a second so the countdown moves.
    LaunchedEffect(phase) {
        if (phase == ChallengeSession.Phase.WAITING || phase == ChallengeSession.Phase.IN_PROGRESS) {
            while (true) {
                delay(1000)
                refreshTick++
            }
        }
    }

    // First-time guide ("what does this button do?").
    val tour = rememberTourState()
    var showTour by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!UserPrefs.isTourSeen(context)) {
            delay(700)
            showTour = true
        }
    }
    val steps = remember(showTour) { tourSteps.filter { tour.bounds.containsKey(it.key) } }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            floatingActionButton = {
                if (!locked) {
                    FloatingActionButton(
                        onClick = onAddAlarm,
                        containerColor = OrangeEnd,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.tourTarget(tour, "fab")
                    ) { Icon(Icons.Default.Add, contentDescription = "Add alarm") }
                }
            },
            floatingActionButtonPosition = FabPosition.Center
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(horizontal = 20.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // ---- Top bar ----
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Discipline",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        "X",
                        fontWeight = FontWeight.Bold,
                        color = OrangeEnd,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { showTour = true },
                        modifier = Modifier.tourTarget(tour, "help")
                    ) { Icon(Icons.Default.Info, contentDescription = "Show guide", tint = TextMuted) }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.tourTarget(tour, "settings")
                    ) { Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextMuted) }
                }

                // ---- Streak ----
                GradientHeroCard(
                    title = "\uD83D\uDD25 $streak day streak",
                    subtitle = "Best: $best days",
                    modifier = Modifier.fillMaxWidth().tourTarget(tour, "streak")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        week.forEach { (date, status) -> DayDot(date, status) }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ---- Status ----
                StatusCard(
                    modifier = Modifier.tourTarget(tour, "status"),
                    phase = phase,
                    serviceEnabled = serviceEnabled,
                    hasAlarms = alarms.isNotEmpty(),
                    nextText = nextText,
                    msLeft = msLeft,
                    onStartChallenge = onStartChallenge,
                    onEnableLock = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))
                Text("Alarms", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                if (locked) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Alarms can't be changed while your phone is locked. Finish the challenge, or wait for the next alarm.",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                if (alarms.isEmpty()) {
                    Text(
                        "No alarms yet. Tap the + button to add your first one.",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }

                alarms.forEachIndexed { index, alarm ->
                    AlarmCard(
                        alarm = alarm,
                        locked = locked,
                        modifier = if (index == 0) Modifier.tourTarget(tour, "alarm") else Modifier,
                        switchModifier = if (index == 0) Modifier.tourTarget(tour, "switch") else Modifier,
                        onClick = { onEditAlarm(alarm.id) },
                        onToggle = { on ->
                            val updated = alarm.copy(enabled = on)
                            AlarmStore.save(context, updated)
                            if (on) AlarmScheduler.schedule(context, updated)
                            else AlarmScheduler.cancel(context, updated.id)
                            refreshTick++
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Room so the + button never covers the last card.
                Spacer(modifier = Modifier.height(96.dp))
            }
        }

        if (showTour) {
            TourOverlay(
                steps = steps,
                state = tour,
                onFinish = {
                    showTour = false
                    UserPrefs.setTourSeen(context, true)
                }
            )
        }
    }
}

@Composable
private fun DayDot(date: LocalDate, status: StreakStore.DayStatus) {
    val (symbol, bg) = when (status) {
        StreakStore.DayStatus.DONE -> "\u2713" to Success
        StreakStore.DayStatus.MISSED -> "\u2715" to Danger
        StreakStore.DayStatus.NONE -> "\u00B7" to Color.White.copy(alpha = 0.3f)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(bg),
            contentAlignment = Alignment.Center
        ) {
            Text(symbol, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun StatusCard(
    modifier: Modifier,
    phase: ChallengeSession.Phase,
    serviceEnabled: Boolean,
    hasAlarms: Boolean,
    nextText: String?,
    msLeft: Long,
    onStartChallenge: () -> Unit,
    onEnableLock: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .padding(18.dp)
    ) {
        when {
            !serviceEnabled -> {
                Text("App lock is OFF", fontWeight = FontWeight.SemiBold, color = Warning, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Turn on the DisciplineX App Lock accessibility service. Without it, apps stay open even if you skip the challenge.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onEnableLock,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeEnd)
                ) { Text("Turn on app lock") }
            }

            phase == ChallengeSession.Phase.WAITING -> {
                val sec = (msLeft / 1000).toInt()
                Text("Challenge waiting", fontWeight = FontWeight.SemiBold, color = OrangeEnd, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "${ChallengeSession.exercise(context)} \u00b7 ${ChallengeSession.summary(context)}",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    String.format(Locale.US, "Finish within %02d:%02d or it counts as missed.", sec / 60, sec % 60),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onStartChallenge,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeEnd)
                ) { Text("Start now") }
            }

            phase == ChallengeSession.Phase.IN_PROGRESS -> {
                Text("Challenge in progress", fontWeight = FontWeight.SemiBold, color = OrangeEnd, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "${ChallengeSession.exercise(context)} \u00b7 ${ChallengeSession.summary(context)}. Finish it to unlock your phone \u2014 ${msLeft / 60000} min left.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onStartChallenge,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeEnd)
                ) { Text("Continue challenge") }
            }

            phase == ChallengeSession.Phase.MISSED -> {
                val unlockAt = DateFormat.getTimeInstance(DateFormat.SHORT)
                    .format(Date(ChallengeSession.lockEndsAtMillis(context)))
                Text("Challenge missed", fontWeight = FontWeight.SemiBold, color = Danger, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "You didn't finish within the time limit, so your streak was reset. Apps stay locked until $unlockAt.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            else -> {
                Text("Ready", fontWeight = FontWeight.SemiBold, color = Success, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    when {
                        !hasAlarms -> "No alarms yet \u2014 tap + to add one."
                        nextText != null -> "Next alarm: $nextText. When it rings you have your time limit to finish, and every app except Phone, Camera and your allowed apps stays locked until you finish."
                        else -> "All alarms are off. Switch one on to get started."
                    },
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun AlarmCard(
    alarm: AlarmEntry,
    locked: Boolean,
    modifier: Modifier,
    switchModifier: Modifier,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val timeColor = if (alarm.enabled) TextPrimary else TextMuted
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Surface)
            .clickable(enabled = !locked, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(alarm.timeText, fontSize = 38.sp, fontWeight = FontWeight.Bold, color = timeColor)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    alarm.periodText,
                    fontSize = 16.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            val line = listOf(alarm.label, alarm.repeatText).filter { it.isNotBlank() }.joinToString(", ")
            Text(line, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "${alarm.exercise} \u00b7 ${alarm.summary}",
                color = if (alarm.enabled) OrangeEnd else TextMuted,
                style = MaterialTheme.typography.labelLarge
            )
        }
        Switch(
            checked = alarm.enabled,
            onCheckedChange = onToggle,
            enabled = !locked,
            modifier = switchModifier,
            colors = SwitchDefaults.colors(checkedTrackColor = OrangeEnd)
        )
    }
}

private fun nextAlarmText(context: Context): String? {
    val ms = AlarmScheduler.nextAlarmMillis(context) ?: return null
    val date = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    val day = when (date.toLocalDate()) {
        today -> "today"
        today.plusDays(1) -> "tomorrow"
        else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))
    return "$day at $time"
}
