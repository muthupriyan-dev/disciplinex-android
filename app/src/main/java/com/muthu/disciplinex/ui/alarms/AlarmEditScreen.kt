package com.muthu.disciplinex.ui.alarms

import com.muthu.disciplinex.data.AppSettings
import com.muthu.disciplinex.ui.components.tourTarget
import com.muthu.disciplinex.ui.components.rememberTourState
import com.muthu.disciplinex.ui.components.TourStep
import com.muthu.disciplinex.ui.components.TourOverlay
import com.muthu.disciplinex.ui.components.GuideButton
import com.muthu.disciplinex.data.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muthu.disciplinex.alarm.AlarmScheduler
import com.muthu.disciplinex.data.AlarmEntry
import com.muthu.disciplinex.data.AlarmStore
import com.muthu.disciplinex.data.ChallengeSession
import com.muthu.disciplinex.data.ExerciseKinds
import com.muthu.disciplinex.ui.components.TargetStepper
import com.muthu.disciplinex.ui.components.WheelTimePicker
import com.muthu.disciplinex.ui.onboarding.ExerciseList
import com.muthu.disciplinex.ui.onboarding.exerciseOptions
import com.muthu.disciplinex.ui.theme.Danger
import com.muthu.disciplinex.ui.theme.OrangeEnd
import com.muthu.disciplinex.ui.theme.Surface
import com.muthu.disciplinex.ui.theme.TextMuted
import com.muthu.disciplinex.ui.theme.TextPrimary
import com.muthu.disciplinex.ui.theme.ChipBackground
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Add or edit one alarm: time, repeat days, label, exercise and target.
 * [alarmId] = -1 means "new alarm".
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlarmEditScreen(alarmId: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val existing = remember { if (alarmId >= 0) AlarmStore.get(context, alarmId) else null }
    val locked = remember { ChallengeSession.isLockActive(context) }

    // Defaults for a new alarm: 6:00 AM, every day, push-ups x10.
    var hour24 by remember { mutableStateOf(existing?.hour24 ?: 6) }
    var minute by remember { mutableStateOf(existing?.minute ?: 0) }
    var daysMask by remember { mutableStateOf(existing?.daysMask ?: AlarmEntry.ALL_DAYS) }
    // "Custom" repeat: specific dates (epoch days). Not empty = custom mode.
    var customDates by remember { mutableStateOf(existing?.dates ?: emptyList<Long>()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var selected by remember {
        mutableStateOf(exerciseOptions.firstOrNull { it.name == existing?.exercise } ?: exerciseOptions[0])
    }
    var reps by remember { mutableStateOf(existing?.reps ?: 10) }
    var minutes by remember { mutableStateOf(existing?.minutes ?: 15) }
    val isTimed = ExerciseKinds.isTimed(selected.name)
    var windowMin by remember { mutableStateOf(existing?.windowMinutes ?: AlarmEntry.DEFAULT_WINDOW_MINUTES) }
    // A timed exercise (e.g. 2 hours of reading) needs at least that long to finish it.
    val minWindow = if (isTimed) minutes else 1
    LaunchedEffect(minWindow) { if (windowMin < minWindow) windowMin = minWindow }

    // Fixed at first composition: the wheel keeps its own state after that.
    val initHour12 = remember { ((hour24 + 11) % 12) + 1 }
    val initIsAm = remember { hour24 < 12 }


    // Used by the tick in the top bar and by the Save button at the bottom.
    val saveAlarm: () -> Unit = save@{
        val alarm = AlarmEntry(
            id = existing?.id ?: AlarmStore.nextId(context),
            hour24 = hour24,
            minute = minute,
            daysMask = daysMask,
            label = label.trim(),
            exercise = selected.name,
            reps = reps,
            minutes = minutes,
            enabled = true,
            dates = customDates.sorted(),
            windowMinutes = windowMin.coerceAtLeast(minWindow)
        )
        // A custom alarm whose chosen dates/time are all in the past would never ring.
        if (alarm.isCustom && AlarmScheduler.nextTriggerMillis(alarm, System.currentTimeMillis()) == null) {
            saveError = tr("That time has already passed on the date(s) you picked. Pick a later date or time.", "நீங்கள் தேர்ந்தெடுத்த தேதியில் அந்த நேரம் ஏற்கனவே கடந்துவிட்டது. பிந்தைய தேதி அல்லது நேரத்தை தேர்ந்தெடுங்கள்.")
            return@save
        }
        AlarmStore.save(context, alarm)
        AlarmScheduler.schedule(context, alarm)
        onClose()
    }

    val tour = rememberTourState()
    var showTour by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val steps = remember(showTour, AppSettings.language) { alarmEditTourSteps().filter { tour.bounds.containsKey(it.key) } }
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (existing == null) tr("New alarm", "புதிய அலாரம்") else tr("Edit alarm", "அலாரத்தை திருத்து"),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = tr("Close without saving", "சேமிக்காமல் மூடு"))
                    }
                },
                actions = {
                    GuideButton(onClick = { showTour = true })
                    if (!locked) {
                        IconButton(
                            onClick = saveAlarm,
                            modifier = Modifier.tourTarget(tour, "save")
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = tr("Save alarm", "அலாரத்தை சேமி"),
                                tint = OrangeEnd
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        }
    ) { padding ->
        if (locked) {
            Column(modifier = Modifier.padding(padding).padding(20.dp)) {
                Text(
                    tr("Alarms can't be changed while your phone is locked.", "போன் பூட்டப்பட்டிருக்கும்போது அலாரங்களை மாற்ற முடியாது."),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp)
                .fillMaxSize()
                .verticalScroll(scroll)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ---- Time ----
            Box(modifier = Modifier.tourTarget(tour, "time")) {
            WheelTimePicker(
                initialHour = initHour12,
                initialMinute = minute,
                initialIsAm = initIsAm,
                onTimeChanged = { h, m, am ->
                    hour24 = (h % 12) + (if (am) 0 else 12)
                    minute = m
                }
            )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ---- Repeat ----
            SectionCard(Modifier.tourTarget(tour, "repeat")) {
                Text(tr("Repeat", "மீண்டும்"), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    when {
                        customDates.isNotEmpty() -> tr("Custom \u2014 rings only on the dates you pick below.", "தனிப்பயன் \u2014 கீழே தேர்ந்தெடுக்கும் தேதிகளில் மட்டும் அடிக்கும்.")
                        daysMask == 0 -> tr("Once \u2014 rings the next time this clock time comes, then switches itself off.", "ஒருமுறை \u2014 இந்த நேரம் அடுத்து வரும்போது அடித்து, தானாக அணைந்துவிடும்.")
                        else -> tr("Tap the days you want. Selected: ${repeatSummary(daysMask)}.", "வேண்டிய நாட்களை தட்டுங்கள். தேர்வு: ${repeatSummary(daysMask)}.")
                    },
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AlarmEntry.DAY_LETTERS.forEachIndexed { i, letter ->
                        val on = customDates.isEmpty() && (daysMask shr i) and 1 == 1
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (on) OrangeEnd else ChipBackground)
                                .clickable {
                                    // Picking weekdays leaves "Custom" mode.
                                    if (customDates.isNotEmpty()) {
                                        customDates = emptyList()
                                        daysMask = 1 shl i
                                    } else {
                                        daysMask = daysMask xor (1 shl i)
                                    }
                                    saveError = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                letter,
                                color = if (on) Surface else TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                val chipColors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = OrangeEnd,
                    selectedLabelColor = Color.White
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        colors = chipColors,
                        selected = customDates.isEmpty() && daysMask == AlarmEntry.ALL_DAYS,
                        onClick = { customDates = emptyList(); daysMask = AlarmEntry.ALL_DAYS; saveError = null },
                        label = { Text(tr("Every day", "தினமும்")) }
                    )
                    FilterChip(
                        colors = chipColors,
                        selected = customDates.isEmpty() && daysMask == 0,
                        onClick = { customDates = emptyList(); daysMask = 0; saveError = null },
                        label = { Text(tr("Once", "ஒருமுறை")) }
                    )
                    FilterChip(
                        colors = chipColors,
                        selected = customDates.isNotEmpty(),
                        onClick = { showDatePicker = true },
                        label = { Text(tr("Custom", "தனிப்பயன்")) }
                    )
                }

                if (customDates.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        customDates.forEach { d ->
                            InputChip(
                                selected = false,
                                onClick = { customDates = customDates - d; saveError = null },
                                label = { Text(AlarmEntry.dateText(d)) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = tr("Remove this date", "இந்த தேதியை நீக்கு"),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                        AssistChip(
                            onClick = { showDatePicker = true },
                            label = { Text(tr("+ Add date", "+ தேதி சேர்")) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ---- Label ----
            SectionCard(Modifier.tourTarget(tour, "label")) {
                Text(tr("Label", "பெயர்"), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { if (it.length <= 30) label = it },
                    placeholder = { Text(tr("e.g. Morning workout", "எ.கா. காலை உடற்பயிற்சி")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ---- Exercise + target ----
            SectionCard(Modifier.tourTarget(tour, "exercise")) {
                Text(tr("Exercise", "பயிற்சி"), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    tr("Push-ups and squats are counted by the camera. Walking, meditation and reading run on a timer you can't skip.", "புஷ்-அப்ஸ், ஸ்குவாட்ஸ் கேமரா எண்ணும். நடைப்பயிற்சி, தியானம், வாசிப்பு தவிர்க்க முடியாத டைமரில் ஓடும்."),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                ExerciseList(selected = selected, onSelect = { selected = it })

                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    if (isTimed) tr("How many minutes?", "எத்தனை நிமிடங்கள்?") else tr("How many reps?", "எத்தனை முறை?"),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (isTimed) {
                    TargetStepper(value = minutes, unit = tr("minutes", "நிமிடங்கள்"), min = 1, max = 180, onValueChange = { minutes = it })
                } else {
                    TargetStepper(value = reps, unit = tr("reps", "முறை"), min = 1, max = 200, onValueChange = { reps = it })
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ---- Time limit ----
            SectionCard(Modifier.tourTarget(tour, "limit")) {
                Text(tr("Time limit", "நேர வரம்பு"), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    tr("How long after the alarm rings you have to finish. If you don't finish in time the day counts as missed, your streak resets and every app stays locked for 24 hours.", "அலாரம் அடித்த பிறகு முடிக்க உங்களுக்கு எவ்வளவு நேரம் உண்டு. நேரத்துக்குள் முடிக்காவிட்டால் அன்றைய நாள் தவறியதாக கணக்கு, ஸ்ட்ரீக் மீட்டமையும், எல்லா ஆப்களும் 24 மணி நேரம் பூட்டப்படும்."),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                TargetStepper(
                    value = windowMin.coerceAtLeast(minWindow),
                    unit = if (windowMin >= 60) tr("minutes \u00b7 ${windowMin / 60} h ${windowMin % 60} min", "நிமிடங்கள் \u00b7 ${windowMin / 60} மணி ${windowMin % 60} நிமி") else tr("minutes", "நிமிடங்கள்"),
                    min = minWindow,
                    max = 600,
                    bigStep = 15,
                    onValueChange = { windowMin = it }
                )
                if (isTimed) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        tr("Can't be shorter than your $minutes-minute ${selected.name.lowercase()} target.", "உங்கள் $minutes நிமிட இலக்கை விட குறைவாக இருக்க முடியாது."),
                        color = TextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            saveError?.let {
                Text(it, color = Danger, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = saveAlarm,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OrangeEnd)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(tr("Save alarm", "அலாரத்தை சேமி"), fontWeight = FontWeight.SemiBold)
            }

            if (existing != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        AlarmScheduler.cancel(context, existing.id)
                        AlarmStore.delete(context, existing.id)
                        onClose()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(tr("Delete this alarm", "இந்த அலாரத்தை நீக்கு"), color = Danger) }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        if (showDatePicker) {
            val todayUtcMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val pickerState = rememberDatePickerState(
                selectableDates = object : SelectableDates {
                    // No dates in the past.
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                        utcTimeMillis >= todayUtcMillis
                }
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        enabled = pickerState.selectedDateMillis != null,
                        onClick = {
                            pickerState.selectedDateMillis?.let { millis ->
                                // Picker gives UTC midnight of the chosen day.
                                val day = millis / 86_400_000L
                                if (day !in customDates) customDates = (customDates + day).sorted()
                                saveError = null
                            }
                            showDatePicker = false
                        }
                    ) { Text(tr("Add", "சேர்"), color = OrangeEnd, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text(tr("Cancel", "ரத்து"), color = TextMuted) }
                }
            ) {
                DatePicker(state = pickerState)
            }
        }
    }
        if (showTour) {
            TourOverlay(steps, tour, onFinish = { showTour = false }, scrollState = scroll)
        }
    }
}

@Composable
private fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .padding(18.dp),
        content = content
    )
}

private fun repeatSummary(mask: Int): String =
    AlarmEntry(0, 0, 0, mask, "", "", 0, 0, true).repeatText

private fun alarmEditTourSteps(): List<TourStep> = listOf(
    TourStep(
        "time", tr("Time", "நேரம்"),
        tr("Scroll the hour, minute and AM / PM wheels to choose when the alarm rings.",
            "அலாரம் அடிக்க வேண்டிய நேரத்தை மணி, நிமிடம், AM / PM சக்கரங்களை உருட்டி தேர்ந்தெடுங்கள்.")
    ),
    TourStep(
        "repeat", tr("Repeat", "மீண்டும்"),
        tr("Every day, only once, or tap the days you want. Custom lets you pick exact dates from a calendar.",
            "தினமும், ஒருமுறை, அல்லது வேண்டிய நாட்களை தட்டுங்கள். தனிப்பயன் மூலம் காலெண்டரில் குறிப்பிட்ட தேதிகளை தேர்ந்தெடுக்கலாம்.")
    ),
    TourStep(
        "label", tr("Label", "பெயர்"),
        tr("An optional name for this alarm, like Morning workout. It shows on the alarm.",
            "இந்த அலாரத்துக்கு விருப்பமான பெயர், உதாரணம் காலை உடற்பயிற்சி. அலாரத்தில் தெரியும்.")
    ),
    TourStep(
        "exercise", tr("Exercise and target", "பயிற்சி மற்றும் இலக்கு"),
        tr("Pick what you must do to unlock your phone, then set how many reps or minutes. Push-ups and squats are counted by the camera. Walking, meditation and reading run on a timer you can't skip.",
            "போனை திறக்க என்ன செய்ய வேண்டும் என தேர்ந்தெடுத்து, எத்தனை முறை அல்லது நிமிடங்கள் என அமையுங்கள். புஷ்-அப்ஸ், ஸ்குவாட்ஸ் கேமரா எண்ணும். நடைப்பயிற்சி, தியானம், வாசிப்பு தவிர்க்க முடியாத டைமரில் ஓடும்.")
    ),
    TourStep(
        "limit", tr("Time limit", "நேர வரம்பு"),
        tr("How long after the alarm rings you have to finish. Miss it and the day counts as missed, your streak resets and every app stays locked for 24 hours.",
            "அலாரம் அடித்த பிறகு முடிக்க எவ்வளவு நேரம் உண்டு. தவறினால் அந்த நாள் தவறியதாக கணக்கு, ஸ்ட்ரீக் மீட்டமையும், எல்லா ஆப்களும் 24 மணி நேரம் பூட்டப்படும்.")
    ),
    TourStep(
        "save", tr("Save", "சேமி"),
        tr("Tap the tick at the top any time to save. The alarm is set straight away.",
            "சேமிக்க மேலே உள்ள டிக்கை எப்போது வேண்டுமானாலும் தட்டுங்கள். அலாரம் உடனே அமைக்கப்படும்.")
    )
)
