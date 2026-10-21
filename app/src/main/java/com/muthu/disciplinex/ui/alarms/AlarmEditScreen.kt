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

/**
 * Add or edit one alarm: time, repeat days, label, exercise and target.
 * [alarmId] = -1 means "new alarm".
 */
@OptIn(ExperimentalMaterial3Api::class)
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
        var label by remember { mutableStateOf(existing?.label ?: "") }
        var selected by remember {
            mutableStateOf(exerciseOptions.firstOrNull { it.name == existing?.exercise } ?: exerciseOptions[0])
        }
        var reps by remember { mutableStateOf(existing?.reps ?: 10) }
        var minutes by remember { mutableStateOf(existing?.minutes ?: 15) }
        val isTimed = ExerciseKinds.isTimed(selected.name)

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
                    if (daysMask == 0) "Once \u2014 rings the next time this clock time comes, then switches itself off."
                    else "Tap the days you want. Selected: ${repeatSummary(daysMask)}.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AlarmEntry.DAY_NAMES.forEachIndexed { i, name ->
                        val on = (daysMask shr i) and 1 == 1
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (on) OrangeEnd else Color(0xFFF1F1F5))
                                .clickable { daysMask = daysMask xor (1 shl i) },
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
                    AssistChip(onClick = { daysMask = AlarmEntry.ALL_DAYS }, label = { Text("Every day") })
                    AssistChip(onClick = { daysMask = 0 }, label = { Text("Once") })
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

            Spacer(modifier = Modifier.height(20.dp))

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
                        enabled = true
                    )
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
