package com.muthu.disciplinex.ui.home

import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import com.muthu.disciplinex.data.tr
import com.muthu.disciplinex.data.AppSettings
import com.muthu.disciplinex.data.ExerciseKinds
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import kotlinx.coroutines.launch
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
import com.muthu.disciplinex.data.PermissionChecks
import com.muthu.disciplinex.data.ChallengeSession
import com.muthu.disciplinex.data.HistoryStore
import com.muthu.disciplinex.data.StreakStore
import com.muthu.disciplinex.domain.Achievements
import com.muthu.disciplinex.domain.StatsCalculator
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
import com.muthu.disciplinex.ui.theme.SuccessText
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

private fun homeTourSteps(): List<TourStep> = listOf(
    TourStep(
        "streak", tr("Your streak \uD83D\uDD25", "உங்கள் தொடர்ச்சியான சாதனை \uD83D\uDD25"),
        tr("Finish every challenge on time and this number grows. Miss one and it goes back to 0.", "ஒவ்வொரு சவாலையும் நேரத்துக்குள் முடித்தால் இந்த எண் கூடும். ஒன்றை தவறவிட்டால் மீண்டும் 0 ஆகிவிடும்.")
    ),
    TourStep(
        "status", tr("Status", "நிலை"),
        tr("Shows if your app lock is ready, and what is waiting after an alarm rings. You get the time limit you set on the alarm to finish after it rings.", "ஆப் பூட்டு தயாராக உள்ளதா என்பதையும், அலாரம் அடித்த பிறகு என்ன காத்திருக்கிறது என்பதையும் காட்டும். அலாரம் அடித்த பிறகு முடிக்க, நீங்கள் அந்த அலாரத்தில் வைத்த நேர வரம்பு உங்களுக்கு கிடைக்கும்.")
    ),
    TourStep(
        "alarm", tr("An alarm", "ஒரு அலாரம்"),
        tr("Each card is one alarm. Tap it to change the time, repeat days, exercise and target. Add as many as you like \u2014 morning, evening, any time.", "ஒவ்வொரு அட்டையும் ஒரு அலாரம். நேரம், மீண்டும் அடிக்கும் நாட்கள், பயிற்சி, இலக்கு ஆகியவற்றை மாற்ற அதை தட்டுங்கள். காலை, மாலை, எப்போது வேண்டுமானாலும் \u2014 எத்தனை வேண்டுமானாலும் சேர்க்கலாம்.")
    ),
    TourStep(
        "switch", tr("On / off", "இயக்கு / அணை"),
        tr("Switch an alarm on or off without deleting it.", "அலாரத்தை நீக்காமலேயே இயக்கவோ அணைக்கவோ செய்யலாம்.")
    ),
    TourStep(
        "fab", tr("Add an alarm", "அலாரம் சேர்"),
        tr("Tap + to add a new alarm. Pick the time, the days, the exercise (push-ups, squats, jumping jacks, walking\u2026) and the target: reps, minutes or distance.", "புதிய அலாரம் சேர்க்க + ஐ தட்டுங்கள். நேரம், நாட்கள், பயிற்சி (புஷ்-அப்ஸ், ஸ்குவாட்ஸ், ஜம்பிங் ஜாக்ஸ், நடைப்பயிற்சி\u2026), இலக்கு (முறை, நிமிடங்கள் அல்லது தூரம்) ஆகியவற்றை தேர்ந்தெடுங்கள்.")
    ),
    TourStep(
        "stats", tr("Statistics", "புள்ளிவிவரங்கள்"),
        tr("See your totals, the last 7 days, results for each exercise and your achievements.", "உங்கள் மொத்த எண்கள், கடந்த 7 நாட்கள், ஒவ்வொரு பயிற்சியின் முடிவுகள், உங்கள் சாதனைகளை பாருங்கள்.")
    ),
    TourStep(
        "settings", tr("Settings", "அமைப்புகள்"),
        tr("Choose which apps stay open while locked, change theme and language, save or restore a backup, and read the privacy notes.", "பூட்டியிருக்கும்போது திறந்திருக்க வேண்டிய ஆப்களை தேர்ந்தெடுங்கள், தோற்றம் மற்றும் மொழியை மாற்றுங்கள், பேக்கப்பை சேமியுங்கள் அல்லது மீட்டெடுங்கள், தனியுரிமை குறிப்புகளை படியுங்கள்.")
    ),
    TourStep(
        "help", tr("Need this again?", "இது மீண்டும் வேண்டுமா?"),
        tr("Tap this button any time to see this guide again.", "இந்த வழிகாட்டியை மீண்டும் பார்க்க, எப்போது வேண்டுமானாலும் இந்த பொத்தானை தட்டுங்கள்.")
    )
)

@Composable
fun HomeScreen(
    onAddAlarm: () -> Unit,
    onEditAlarm: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onStartChallenge: () -> Unit,
    onOpenHistory: () -> Unit = {},
    onOpenStats: () -> Unit = {}
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
    // Other switched-off permissions (the app lock itself has its own card in the status box).
    val missingPermissions = remember(refreshTick) {
        PermissionChecks.missing(context).filter { it != PermissionChecks.Kind.ACCESSIBILITY }
    }
    val streak = remember(refreshTick) { StreakStore.currentStreak(context) }
    val best = remember(refreshTick) { StreakStore.bestStreak(context) }
    val week = remember(refreshTick) { StreakStore.lastSevenDays(context) }
    val nextText = remember(refreshTick) { nextAlarmText(context) }
    val msLeft = remember(refreshTick) { ChallengeSession.msLeftToFinish(context) }
    val emergencyOpenLeft = remember(refreshTick) { ChallengeSession.emergencyUnlockMsLeft(context) }

    // "Today: 1 of 2 done": repeating / custom alarms due today vs. challenges finished today.
    val today = remember(refreshTick) { LocalDate.now() }
    val doneToday = remember(refreshTick) { StreakStore.allDays(context)[today]?.first ?: 0 }
    val dueToday = remember(refreshTick) {
        alarms.count {
            it.enabled && (
                today.toEpochDay() in it.dates ||
                    (it.daysMask != 0 && ((it.daysMask shr (today.dayOfWeek.value - 1)) and 1) == 1)
                )
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // A new badge unlocked since the last time the user saw Home: tell them once.
    LaunchedEffect(streak, best, doneToday) {
        val stats = StatsCalculator.compute(StreakStore.allDays(context), HistoryStore.getAll(context), LocalDate.now())
        val unlocked = Achievements.evaluate(stats.totalDone, best, stats.totalReps).filter { it.unlocked }
        val ids = unlocked.map { it.id }.toSet()
        val seen = UserPrefs.getSeenAchievements(context)
        if (seen == null) {
            UserPrefs.setSeenAchievements(context, ids) // first run: don't announce old badges
        } else {
            val fresh = unlocked.filter { it.id !in seen }
            if (fresh.isNotEmpty()) {
                UserPrefs.setSeenAchievements(context, seen + ids)
                val names = fresh.joinToString(", ") { tr(it.titleEn, it.titleTa) }
                snackbarHostState.showSnackbar(
                    message = tr("\uD83C\uDFC6 Achievement unlocked: $names", "\uD83C\uDFC6 புதிய சாதனை: $names")
                )
            }
        }
    }

    // While a challenge is waiting to be started, tick once a second so the countdown moves.
    LaunchedEffect(phase, emergencyOpenLeft != null) {
        if (phase == ChallengeSession.Phase.WAITING || phase == ChallengeSession.Phase.IN_PROGRESS || emergencyOpenLeft != null) {
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
    val steps = remember(showTour, AppSettings.language) { homeTourSteps().filter { tour.bounds.containsKey(it.key) } }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                if (!locked) {
                    FloatingActionButton(
                        onClick = onAddAlarm,
                        containerColor = OrangeEnd,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.tourTarget(tour, "fab")
                    ) { Icon(Icons.Default.Add, contentDescription = tr("Add alarm", "அலாரம் சேர்")) }
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
                    ) { Icon(Icons.Default.Info, contentDescription = tr("Show guide", "வழிகாட்டியை காட்டு"), tint = TextMuted) }
                    IconButton(
                        onClick = onOpenStats,
                        modifier = Modifier.tourTarget(tour, "stats")
                    ) { Icon(Icons.Default.BarChart, contentDescription = tr("Statistics", "புள்ளிவிவரங்கள்"), tint = TextMuted) }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.tourTarget(tour, "settings")
                    ) { Icon(Icons.Default.Settings, contentDescription = tr("Settings", "அமைப்புகள்"), tint = TextMuted) }
                }

                // ---- Streak ----
                GradientHeroCard(
                    title = tr("\uD83D\uDD25 $streak day streak", "\uD83D\uDD25 $streak நாள் தொடர்ச்சியான சாதனை"),
                    subtitle = tr("Best: $best days", "அதிகபட்சம்: $best நாட்கள்"),
                    modifier = Modifier.fillMaxWidth().tourTarget(tour, "streak").clickable { onOpenHistory() }
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

                if (missingPermissions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    PermissionWarningCard(
                        names = missingPermissions.joinToString(", ") { PermissionChecks.title(it) },
                        onReview = onOpenSettings
                    )
                }

                if (phase == ChallengeSession.Phase.MISSED) {
                    Spacer(modifier = Modifier.height(12.dp))
                    EmergencyUnlockCard(onChanged = { refreshTick++ })
                }

                if (emergencyOpenLeft != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val sec = (emergencyOpenLeft / 1000).toInt()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Surface)
                            .padding(16.dp)
                    ) {
                        Text(tr("Emergency unlock is on", "அவசர காலத் திறப்பு இயக்கத்தில் உள்ளது"), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            tr("Your phone locks again in ${sec / 60}:${"%02d".format(sec % 60)}.",
                                "${sec / 60}:${"%02d".format(sec % 60)} கழித்து போன் மீண்டும் பூட்டிக்கொள்ளும்."),
                            color = TextMuted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(tr("Alarms", "அலாரங்கள்"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                if (dueToday > 0 || doneToday > 0) {
                    val total = maxOf(dueToday, doneToday)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        tr("Today: $doneToday of $total done", "இன்று: $total-ல் $doneToday முடிந்தது"),
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { doneToday.toFloat() / total },
                        color = if (doneToday >= total) Success else OrangeEnd,
                        trackColor = com.muthu.disciplinex.ui.theme.Divider,
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    )
                }
                if (locked) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        tr("Alarms can't be changed while your phone is locked. Finish the challenge, or wait for the next alarm.", "போன் பூட்டியிருக்கும்போது அலாரங்களை மாற்ற முடியாது. சவாலை முடியுங்கள், அல்லது அடுத்த அலாரம் வரை காத்திருங்கள்."),
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                if (alarms.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("\u23F0", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            tr("No alarms yet", "இன்னும் அலாரம் இல்லை"),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            tr("Set a wake-up time and a challenge. Miss it and your apps stay locked.", "எழுந்திருக்கும் நேரத்தையும் ஒரு சவாலையும் அமைத்துக்கொள்ளுங்கள். அதை தவறவிட்டால், உங்கள் ஆப்கள் பூட்டியே இருக்கும்."),
                            color = TextMuted,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onAddAlarm,
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OrangeEnd, contentColor = Color.White),
                            modifier = Modifier.height(48.dp)
                        ) { Text(tr("Add your first alarm", "முதல் அலாரத்தை சேர்"), fontWeight = FontWeight.SemiBold) }
                    }
                }

                alarms.forEachIndexed { index, alarm ->
                    key(alarm.id) {
                        val committed = AlarmScheduler.isCommitted(alarm)
                        SwipeableAlarmCard(
                            alarm = alarm,
                            locked = locked || committed,
                            committed = committed && !locked,
                            modifier = if (index == 0) Modifier.tourTarget(tour, "alarm") else Modifier,
                            switchModifier = if (index == 0) Modifier.tourTarget(tour, "switch") else Modifier,
                            onClick = { onEditAlarm(alarm.id) },
                            onToggle = { on ->
                                val updated = alarm.copy(enabled = on)
                                AlarmStore.save(context, updated)
                                if (on) AlarmScheduler.schedule(context, updated)
                                else AlarmScheduler.cancel(context, updated.id)
                                refreshTick++
                            },
                            onDelete = {
                                AlarmScheduler.cancel(context, alarm.id)
                                AlarmStore.delete(context, alarm.id)
                                refreshTick++
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = tr("Alarm deleted", "அலாரம் நீக்கப்பட்டது"),
                                        actionLabel = tr("Undo", "திரும்பப்பெறு"),
                                        duration = androidx.compose.material3.SnackbarDuration.Long
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        AlarmStore.save(context, alarm)
                                        if (alarm.enabled) AlarmScheduler.schedule(context, alarm)
                                        refreshTick++
                                    }
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
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
            date.dayOfWeek.getDisplayName(TextStyle.NARROW, AppSettings.locale),
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
                Text(tr("App lock is OFF", "ஆப் பூட்டு அணைந்துள்ளது"), fontWeight = FontWeight.SemiBold, color = Warning, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    tr("Turn on the DisciplineX App Lock accessibility service. Without it, apps stay open even if you skip the challenge.", "DisciplineX App Lock அணுகல் வசதி சேவையை இயக்குங்கள். இது இல்லையென்றால், சவாலை தவிர்த்தாலும் ஆப்கள் திறந்தே இருக்கும்."),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onEnableLock,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeEnd)
                ) { Text(tr("Turn on app lock", "ஆப் பூட்டை இயக்கு")) }
            }

            phase == ChallengeSession.Phase.WAITING -> {
                val sec = (msLeft / 1000).toInt()
                Text(tr("Challenge waiting", "சவால் காத்திருக்கிறது"), fontWeight = FontWeight.SemiBold, color = OrangeEnd, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "${ExerciseKinds.display(ChallengeSession.exercise(context))} \u00b7 ${ChallengeSession.summary(context)}",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    String.format(Locale.US, tr("Finish within %02d:%02d or it counts as missed.", "%02d:%02d-க்குள் முடியுங்கள், இல்லையென்றால் தவறியதாக எடுத்துக்கொள்ளப்படும்."), sec / 60, sec % 60),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                TimeLeftBar(msLeft = msLeft, windowMs = ChallengeSession.windowMs(context))
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onStartChallenge,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeEnd)
                ) { Text(tr("Start now", "இப்போது தொடங்கு")) }
            }

            phase == ChallengeSession.Phase.IN_PROGRESS -> {
                Text(tr("Challenge in progress", "சவால் நடந்துகொண்டிருக்கிறது"), fontWeight = FontWeight.SemiBold, color = OrangeEnd, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    tr("${ExerciseKinds.display(ChallengeSession.exercise(context))} \u00b7 ${ChallengeSession.summary(context)}. Finish it to unlock your phone \u2014 ${msLeft / 60000} min left.", "${ExerciseKinds.display(ChallengeSession.exercise(context))} \u00b7 ${ChallengeSession.summary(context)}. இதை முடித்தால் போன் திறக்கும் \u2014 ${msLeft / 60000} நிமிடம் மீதம்."),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                TimeLeftBar(msLeft = msLeft, windowMs = ChallengeSession.windowMs(context))
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onStartChallenge,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeEnd)
                ) { Text(tr("Continue challenge", "சவாலை தொடர்")) }
            }

            phase == ChallengeSession.Phase.MISSED -> {
                val unlockAt = DateFormat.getTimeInstance(DateFormat.SHORT)
                    .format(Date(ChallengeSession.lockEndsAtMillis(context)))
                Text(tr("Challenge missed", "சவால் தவறிவிட்டது"), fontWeight = FontWeight.SemiBold, color = Danger, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    tr("You didn't finish within the time limit, so your streak was reset. Apps stay locked until $unlockAt.", "நேர வரம்புக்குள் முடிக்கவில்லை, அதனால் தொடர்ச்சியான சாதனை 0 ஆகிவிட்டது. $unlockAt வரை ஆப்கள் பூட்டியே இருக்கும்."),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            else -> {
                Text(tr("Ready", "தயார்"), fontWeight = FontWeight.SemiBold, color = SuccessText, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    when {
                        !hasAlarms -> tr("No alarms yet \u2014 tap + to add one.", "இன்னும் அலாரம் இல்லை \u2014 சேர்க்க + ஐ தட்டுங்கள்.")
                        nextText != null -> tr("Next alarm: $nextText. When it rings you have your time limit to finish, and every app except Phone, Camera and your allowed apps stays locked until you finish.", "அடுத்த அலாரம்: $nextText. அது அடித்ததும் முடிக்க உங்கள் நேர வரம்பு கிடைக்கும்; முடிக்கும் வரை போன், கேமரா, நீங்கள் அனுமதித்த ஆப்கள் தவிர மற்ற எல்லா ஆப்களும் பூட்டியே இருக்கும்.")
                        else -> tr("All alarms are off. Switch one on to get started.", "எல்லா அலாரங்களும் அணைந்துள்ளன. தொடங்க ஒன்றை இயக்குங்கள்.")
                    },
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/** A thin bar that empties as the time limit runs out (turns red in the last minute). */
@Composable
private fun TimeLeftBar(msLeft: Long, windowMs: Long) {
    val sec = (msLeft / 1000).toInt()
    LinearProgressIndicator(
        progress = { (msLeft.toFloat() / windowMs.coerceAtLeast(1L)).coerceIn(0f, 1f) },
        color = if (sec < 60) Danger else OrangeEnd,
        trackColor = com.muthu.disciplinex.ui.theme.Divider,
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
    )
}

/**
 * An alarm card you can swipe left to delete (with an Undo in the snackbar).
 * A locked or committed alarm can't be deleted, so it is not swipeable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableAlarmCard(
    alarm: AlarmEntry,
    locked: Boolean,
    committed: Boolean,
    modifier: Modifier,
    switchModifier: Modifier,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    if (locked) {
        AlarmCard(alarm, locked, committed, modifier, switchModifier, onClick, onToggle)
        return
    }
    val currentOnDelete by rememberUpdatedState(onDelete)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                currentOnDelete()
                true
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        modifier = modifier,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Danger)
                    .padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = tr("Delete alarm", "அலாரத்தை நீக்கு"),
                    tint = Color.White
                )
            }
        }
    ) {
        AlarmCard(alarm, locked, committed, Modifier, switchModifier, onClick, onToggle)
    }
}

@Composable
private fun AlarmCard(
    alarm: AlarmEntry,
    locked: Boolean,
    committed: Boolean,
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
                "${ExerciseKinds.display(alarm.exercise)} \u00b7 ${alarm.summary}",
                color = if (alarm.enabled) OrangeEnd else TextMuted,
                style = MaterialTheme.typography.labelLarge
            )
            if (committed) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    tr("\uD83D\uDD12 Rings within 2 hours \u2014 it can't be changed or switched off now.",
                        "\uD83D\uDD12 2 மணி நேரத்துக்குள் அடிக்கும் \u2014 இப்போது இதை மாற்றவோ அணைக்கவோ முடியாது."),
                    color = TextMuted,
                    style = MaterialTheme.typography.labelMedium
                )
            }
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
        today -> tr("today", "இன்று")
        today.plusDays(1) -> tr("tomorrow", "நாளை")
        else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, AppSettings.locale)
    }
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))
    return tr("$day at $time", "$day, $time")
}

/** Shown on Home when a permission other than the app lock has been switched off. */
@Composable
private fun PermissionWarningCard(names: String, onReview: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .clickable { onReview() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tr("Some permissions are off", "சில அனுமதிகள் இயக்கப்படவில்லை"),
                fontWeight = FontWeight.Bold,
                color = Danger
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                tr(
                    "$names \u2014 the alarm may not work properly. Tap to fix.",
                    "$names \u2014 அலாரம் சரியாக வேலை செய்யாமல் போகலாம். சரிசெய்ய தட்டுங்கள்."
                ),
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Shown on Home after a MISSED challenge: a real emergency can unlock the phone
 * early, but only after a 10-minute wait. The day stays missed and the streak stays 0.
 */
@Composable
private fun EmergencyUnlockCard(onChanged: () -> Unit) {
    val context = LocalContext.current
    var msLeft by remember { mutableStateOf(ChallengeSession.emergencyMsLeft(context)) }
    var confirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            msLeft = ChallengeSession.emergencyMsLeft(context)
            delay(1000)
        }
    }

    val left = msLeft
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .padding(16.dp)
    ) {
        Text(tr("Real emergency?", "உண்மையிலேயே அவசரமா?"), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        when {
            left == null -> {
                Text(
                    tr("Wait 10 minutes inside this app (the time only counts while the app is open) and your phone unlocks for 30 minutes, then it locks again. Today stays missed and your streak stays at 0.",
                        "இந்த ஆப்புக்குள் 10 நிமிடம் காத்திருங்கள் (ஆப் திறந்திருக்கும்போது மட்டுமே நேரம் எண்ணப்படும்). பிறகு போன் 30 நிமிடம் திறந்திருக்கும், அதன் பிறகு மீண்டும் பூட்டிவிடும். இன்று தவறியதாகவே கணக்கிடப்படும், தொடர்ச்சியான சாதனை 0-லேயே இருக்கும்."),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = { confirm = true }) {
                    Text(tr("Start emergency unlock", "அவசரத் திறப்பை தொடங்கு"), color = OrangeEnd)
                }
            }
            left > 0L -> {
                val sec = (left / 1000).toInt()
                Text(
                    tr("Unlock available in ${sec / 60}:${"%02d".format(sec % 60)} \u2014 keep this app open, the timer pauses when you leave",
                        "${sec / 60}:${"%02d".format(sec % 60)} கழித்து திறக்கலாம் \u2014 இந்த ஆப்பை திறந்தே வையுங்கள், வெளியே சென்றால் நேரம் நின்றுவிடும்"),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = {
                    ChallengeSession.cancelEmergencyUnlock(context)
                    msLeft = null
                }) { Text(tr("Cancel", "ரத்து"), color = OrangeEnd) }
            }
            else -> {
                Button(
                    onClick = { if (ChallengeSession.finishEmergencyUnlock(context)) onChanged() },
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeEnd, contentColor = Color.White)
                ) { Text(tr("Unlock my phone now", "போனை இப்போது திற"), fontWeight = FontWeight.SemiBold) }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(tr("Start emergency unlock?", "அவசரத் திறப்பை தொடங்கவா?")) },
            text = {
                Text(
                    tr("You must keep this app open for 10 minutes. Then your apps unlock for 30 minutes and lock again by themselves. Today stays missed and your streak stays at 0. Only use this for a real emergency.",
                        "இந்த ஆப்பை 10 நிமிடம் திறந்து வைத்திருக்க வேண்டும். பிறகு ஆப்கள் 30 நிமிடம் திறந்திருக்கும், தானாகவே மீண்டும் பூட்டிக்கொள்ளும். இன்று தவறியதாகவே கணக்கிடப்படும், தொடர்ச்சியான சாதனை 0-லேயே இருக்கும். உண்மையான அவசரத்துக்கு மட்டும் பயன்படுத்துங்கள்.")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    ChallengeSession.startEmergencyUnlock(context)
                    msLeft = ChallengeSession.emergencyMsLeft(context)
                    confirm = false
                }) { Text(tr("Start", "தொடங்கு"), color = OrangeEnd, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(tr("Cancel", "ரத்து"), color = TextMuted) }
            }
        )
    }
}
