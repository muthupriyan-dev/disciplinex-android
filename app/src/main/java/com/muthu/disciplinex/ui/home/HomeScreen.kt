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
import com.muthu.disciplinex.data.PermissionChecks
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
        "streak", tr("Your streak \uD83D\uDD25", "உங்கள் ஸ்ட்ரீக் \uD83D\uDD25"),
        tr("Finish every challenge on time and this number grows. Miss one and it goes back to 0.", "ஒவ்வொரு சேலஞ்சையும் நேரத்துக்கு முடித்தால் இந்த எண் உயரும். ஒன்றைத் தவறவிட்டால் 0-க்கு திரும்பும்.")
    ),
    TourStep(
        "status", tr("Status", "நிலை"),
        tr("Shows if your app lock is ready, and what is waiting after an alarm rings. You get the time limit you set on the alarm to finish after it rings.", "ஆப் லாக் தயாரா என்றும், அலாரம் அடித்த பின் என்ன காத்திருக்கிறது என்றும் காட்டும். அலாரம் அடித்த பின் முடிக்க நீங்கள் வைத்த நேர வரம்பு உண்டு.")
    ),
    TourStep(
        "alarm", tr("An alarm", "ஒரு அலாரம்"),
        tr("Each card is one alarm. Tap it to change the time, repeat days, exercise and target. Add as many as you like \u2014 morning, evening, any time.", "ஒவ்வொரு கார்டும் ஒரு அலாரம். நேரம், நாட்கள், பயிற்சி, இலக்கு மாற்ற தட்டுங்கள். காலை, மாலை, எப்போது வேண்டுமானாலும் எத்தனை வேண்டுமானாலும் சேர்க்கலாம்.")
    ),
    TourStep(
        "switch", tr("On / off", "ஆன் / ஆஃப்"),
        tr("Switch an alarm on or off without deleting it.", "அலாரத்தை நீக்காமல் ஆன் அல்லது ஆஃப் செய்யலாம்.")
    ),
    TourStep(
        "fab", tr("Add an alarm", "அலாரம் சேர்"),
        tr("Tap + to add a new alarm. Pick the time, the days, the exercise (push-ups, squats, jumping jacks, walking\u2026) and how many reps or minutes.", "புதிய அலாரம் சேர்க்க + தட்டுங்கள். நேரம், நாட்கள், பயிற்சி (புஷ்-அப்ஸ், ஸ்குவாட்ஸ், நடைப்பயிற்சி\u2026) மற்றும் எத்தனை முறை / நிமிடங்கள் என தேர்ந்தெடுங்கள்.")
    ),
    TourStep(
        "settings", tr("Settings", "அமைப்புகள்"),
        tr("Choose which apps stay open while your phone is locked. Phone and Camera are always open.", "போன் பூட்டியிருக்கும்போது எந்த ஆப்கள் திறந்திருக்க வேண்டும் என்பதை தேர்ந்தெடுங்கள். போன், கேமரா எப்போதும் திறந்திருக்கும்.")
    ),
    TourStep(
        "help", tr("Need this again?", "மீண்டும் வேண்டுமா?"),
        tr("Tap this button any time to see this guide again.", "இந்த வழிகாட்டியை மீண்டும் காண எப்போது வேண்டுமானாலும் இந்த பட்டனை தட்டுங்கள்.")
    )
)

@Composable
fun HomeScreen(
    onAddAlarm: () -> Unit,
    onEditAlarm: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onStartChallenge: () -> Unit,
    onOpenHistory: () -> Unit = {}
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
    val steps = remember(showTour, AppSettings.language) { homeTourSteps().filter { tour.bounds.containsKey(it.key) } }

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
                        onClick = onOpenSettings,
                        modifier = Modifier.tourTarget(tour, "settings")
                    ) { Icon(Icons.Default.Settings, contentDescription = tr("Settings", "அமைப்புகள்"), tint = TextMuted) }
                }

                // ---- Streak ----
                GradientHeroCard(
                    title = tr("\uD83D\uDD25 $streak day streak", "\uD83D\uDD25 $streak நாள் ஸ்ட்ரீக்"),
                    subtitle = tr("Best: $best days", "சிறந்தது: $best நாட்கள்"),
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

                Spacer(modifier = Modifier.height(20.dp))
                Text(tr("Alarms", "அலாரங்கள்"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                if (locked) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        tr("Alarms can't be changed while your phone is locked. Finish the challenge, or wait for the next alarm.", "போன் பூட்டியிருக்கும்போது அலாரங்களை மாற்ற முடியாது. சேலஞ்சை முடியுங்கள், அல்லது அடுத்த அலாரம் வரை காத்திருங்கள்."),
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
                            tr("Set a wake-up time and a challenge. Miss it and your apps stay locked.", "எழுந்திருக்கும் நேரத்தையும் சேலஞ்சையும் அமையுங்கள். தவறினால் ஆப்கள் பூட்டியிருக்கும்."),
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
                    val committed = AlarmScheduler.isCommitted(alarm)
                    AlarmCard(
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
                Text(tr("App lock is OFF", "ஆப் லாக் அணைந்துள்ளது"), fontWeight = FontWeight.SemiBold, color = Warning, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    tr("Turn on the DisciplineX App Lock accessibility service. Without it, apps stay open even if you skip the challenge.", "DisciplineX App Lock அக்சசிபிலிட்டி சேவையை ஆன் செய்யுங்கள். இல்லையெனில் சேலஞ்சை தவிர்த்தாலும் ஆப்கள் திறந்தே இருக்கும்."),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onEnableLock,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeEnd)
                ) { Text(tr("Turn on app lock", "ஆப் லாக் ஆன் செய்")) }
            }

            phase == ChallengeSession.Phase.WAITING -> {
                val sec = (msLeft / 1000).toInt()
                Text(tr("Challenge waiting", "சேலஞ்ச் காத்திருக்கிறது"), fontWeight = FontWeight.SemiBold, color = OrangeEnd, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "${ExerciseKinds.display(ChallengeSession.exercise(context))} \u00b7 ${ChallengeSession.summary(context)}",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    String.format(Locale.US, tr("Finish within %02d:%02d or it counts as missed.", "%02d:%02d-க்குள் முடிக்கவும், இல்லையெனில் தவறியதாக கணக்கு."), sec / 60, sec % 60),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onStartChallenge,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeEnd)
                ) { Text(tr("Start now", "இப்போது தொடங்கு")) }
            }

            phase == ChallengeSession.Phase.IN_PROGRESS -> {
                Text(tr("Challenge in progress", "சேலஞ்ச் நடக்கிறது"), fontWeight = FontWeight.SemiBold, color = OrangeEnd, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    tr("${ExerciseKinds.display(ChallengeSession.exercise(context))} \u00b7 ${ChallengeSession.summary(context)}. Finish it to unlock your phone \u2014 ${msLeft / 60000} min left.", "${ExerciseKinds.display(ChallengeSession.exercise(context))} \u00b7 ${ChallengeSession.summary(context)}. முடித்தால் போன் திறக்கும் \u2014 ${msLeft / 60000} நிமிடம் மீதம்."),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onStartChallenge,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeEnd)
                ) { Text(tr("Continue challenge", "சேலஞ்சை தொடர்")) }
            }

            phase == ChallengeSession.Phase.MISSED -> {
                val unlockAt = DateFormat.getTimeInstance(DateFormat.SHORT)
                    .format(Date(ChallengeSession.lockEndsAtMillis(context)))
                Text(tr("Challenge missed", "சேலஞ்ச் தவறிவிட்டது"), fontWeight = FontWeight.SemiBold, color = Danger, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    tr("You didn't finish within the time limit, so your streak was reset. Apps stay locked until $unlockAt.", "நேர வரம்புக்குள் முடிக்கவில்லை, அதனால் ஸ்ட்ரீக் மீட்டமைக்கப்பட்டது. $unlockAt வரை ஆப்கள் பூட்டியிருக்கும்."),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            else -> {
                Text(tr("Ready", "தயார்"), fontWeight = FontWeight.SemiBold, color = SuccessText, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    when {
                        !hasAlarms -> tr("No alarms yet \u2014 tap + to add one.", "இன்னும் அலாரம் இல்லை \u2014 சேர்க்க + தட்டுங்கள்.")
                        nextText != null -> tr("Next alarm: $nextText. When it rings you have your time limit to finish, and every app except Phone, Camera and your allowed apps stays locked until you finish.", "அடுத்த அலாரம்: $nextText. அடித்ததும் முடிக்க உங்கள் நேர வரம்பு உண்டு; முடிக்கும் வரை போன், கேமரா, அனுமதித்த ஆப்கள் தவிர எல்லாம் பூட்டியிருக்கும்.")
                        else -> tr("All alarms are off. Switch one on to get started.", "எல்லா அலாரங்களும் அணைந்துள்ளன. தொடங்க ஒன்றை ஆன் செய்யுங்கள்.")
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
                        "\uD83D\uDD12 2 மணி நேரத்துக்குள் அடிக்கும் \u2014 இப்போது மாற்றவோ அணைக்கவோ முடியாது."),
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
                tr("Some permissions are off", "சில அனுமதிகள் அணைந்துள்ளன"),
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
        Text(tr("Real emergency?", "உண்மையான அவசரமா?"), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        when {
            left == null -> {
                Text(
                    tr("You can unlock early after a 10-minute wait. Today stays missed and your streak stays at 0.",
                        "10 நிமிடம் காத்திருந்தால் முன்கூட்டியே திறக்கலாம். இன்று தவறியதாகவே இருக்கும், ஸ்ட்ரீக் 0-லேயே இருக்கும்."),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = { confirm = true }) {
                    Text(tr("Start emergency unlock", "அவசர அன்லாக் தொடங்கு"), color = OrangeEnd)
                }
            }
            left > 0L -> {
                val sec = (left / 1000).toInt()
                Text(
                    tr("Unlock available in ${sec / 60}:${"%02d".format(sec % 60)}",
                        "${sec / 60}:${"%02d".format(sec % 60)} கழித்து திறக்கலாம்"),
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
            title = { Text(tr("Start emergency unlock?", "அவசர அன்லாக் தொடங்கவா?")) },
            text = {
                Text(
                    tr("Your apps unlock after 10 minutes. Today stays missed and your streak stays at 0. Only use this for a real emergency.",
                        "10 நிமிடத்துக்கு பிறகு ஆப்கள் திறக்கும். இன்று தவறியதாகவே இருக்கும், ஸ்ட்ரீக் 0-லேயே இருக்கும். உண்மையான அவசரத்துக்கு மட்டும் பயன்படுத்துங்கள்.")
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
