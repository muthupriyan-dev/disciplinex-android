package com.muthu.disciplinex.ui.alarms

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (existing == null) "New alarm" else "Edit alarm",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close without saving")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        }
    ) { padding ->
        if (locked) {
            Column(modifier = Modifier.padding(padding).padding(20.dp)) {
                Text(
                    "Alarms can't be changed while your phone is locked.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            return@Scaffold
        }

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

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ---- Time ----
            WheelTimePicker(
                initialHour = initHour12,
                initialMinute = minute,
                initialIsAm = initIsAm,
                onTimeChanged = { h, m, am ->
                    hour24 = (h % 12) + (if (am) 0 else 12)
                    minute = m
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Repeat ----
            SectionCard {
                Text("Repeat", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    when {
                        customDates.isNotEmpty() -> "Custom \u2014 rings only on the dates you pick below."
                        daysMask == 0 -> "Once \u2014 rings the next time this clock time comes, then switches itself off."
                        else -> "Tap the days you want. Selected: ${repeatSummary(daysMask)}."
                    },
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AlarmEntry.DAY_NAMES.forEachIndexed { i, name ->
                        val on = customDates.isEmpty() && (daysMask shr i) and 1 == 1
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (on) OrangeEnd else Color(0xFFF1F1F5))
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
                                name.take(1),
                                color = if (on) Surface else TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = customDates.isEmpty() && daysMask == AlarmEntry.ALL_DAYS,
                        onClick = { customDates = emptyList(); daysMask = AlarmEntry.ALL_DAYS; saveError = null },
                        label = { Text("Every day") }
                    )
                    FilterChip(
                        selected = customDates.isEmpty() && daysMask == 0,
                        onClick = { customDates = emptyList(); daysMask = 0; saveError = null },
                        label = { Text("Once") }
                    )
                    FilterChip(
                        selected = customDates.isNotEmpty(),
                        onClick = { showDatePicker = true },
                        label = { Text("Custom") }
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
                                        contentDescription = "Remove this date",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                        AssistChip(
                            onClick = { showDatePicker = true },
                            label = { Text("+ Add date") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ---- Label ----
            SectionCard {
                Text("Label", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { if (it.length <= 30) label = it },
                    placeholder = { Text("e.g. Morning workout") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ---- Exercise + target ----
            SectionCard {
                Text("Exercise", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Push-ups and squats are counted by the camera. Walking, meditation and reading run on a timer you can't skip.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                ExerciseList(selected = selected, onSelect = { selected = it })

                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    if (isTimed) "How many minutes?" else "How many reps?",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (isTimed) {
                    TargetStepper(value = minutes, unit = "minutes", min = 1, max = 180, onValueChange = { minutes = it })
                } else {
                    TargetStepper(value = reps, unit = "reps", min = 1, max = 200, onValueChange = { reps = it })
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ---- Time limit ----
            SectionCard {
                Text("Time limit", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "How long after the alarm rings you have to finish. If you don't finish in time the day counts as missed, your streak resets and every app stays locked for 24 hours.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                TargetStepper(
                    value = windowMin.coerceAtLeast(minWindow),
                    unit = if (windowMin >= 60) "minutes \u00b7 ${windowMin / 60} h ${windowMin % 60} min" else "minutes",
                    min = minWindow,
                    max = 600,
                    bigStep = 15,
                    onValueChange = { windowMin = it }
                )
                if (isTimed) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Can't be shorter than your $minutes-minute ${selected.name.lowercase()} target.",
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
                onClick = {
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
                        saveError = "That time has already passed on the date(s) you picked. Pick a later date or time."
                        return@Button
                    }
                    AlarmStore.save(context, alarm)
                    AlarmScheduler.schedule(context, alarm)
                    onClose()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OrangeEnd)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save alarm", fontWeight = FontWeight.SemiBold)
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
                ) { Text("Delete this alarm", color = Danger) }
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
                    ) { Text("Add", color = OrangeEnd, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = TextMuted) }
                }
            ) {
                DatePicker(state = pickerState)
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .padding(18.dp),
        content = content
    )
}

private fun repeatSummary(mask: Int): String =
    AlarmEntry(0, 0, 0, mask, "", "", 0, 0, true).repeatText
