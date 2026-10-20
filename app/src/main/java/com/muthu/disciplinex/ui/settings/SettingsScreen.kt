package com.muthu.disciplinex.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muthu.disciplinex.data.ExerciseKinds
import com.muthu.disciplinex.data.UserPrefs
import com.muthu.disciplinex.ui.components.PrimaryPillButton
import com.muthu.disciplinex.ui.components.TargetStepper
import com.muthu.disciplinex.ui.components.WheelTimePicker
import com.muthu.disciplinex.ui.onboarding.ExerciseList
import com.muthu.disciplinex.ui.onboarding.ExerciseOption
import com.muthu.disciplinex.ui.onboarding.exerciseOptions
import com.muthu.disciplinex.ui.theme.OrangeEnd
import com.muthu.disciplinex.ui.theme.Surface
import com.muthu.disciplinex.ui.theme.TextMuted

/**
 * Change the wake-up time, exercise and target after onboarding (no more
 * reinstalling). Read-only while the app lock is active, so the challenge
 * can't be made easier mid-lock.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onSave: (wakeTime: String, exercise: String, targetReps: Int, durationMinutes: Int) -> Unit,
    onOpenAllowedApps: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val locked = remember { UserPrefs.isLockActive(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        }
    ) { padding ->
        if (locked) {
            Column(modifier = Modifier.padding(padding).padding(20.dp)) {
                Text(
                    "Settings are locked until you finish today's challenge.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            return@Scaffold
        }

        val (initHour, initMinute, initIsAm) = remember { parseWakeTime(UserPrefs.getWakeTime(context)) }
        var hour by remember { mutableStateOf(initHour) }
        var minute by remember { mutableStateOf(initMinute) }
        var isAm by remember { mutableStateOf(initIsAm) }

        var selected by remember {
            val saved = UserPrefs.getExercise(context)
            mutableStateOf(exerciseOptions.firstOrNull { it.name == saved } ?: exerciseOptions[0])
        }
        var reps by remember { mutableStateOf(UserPrefs.getTargetReps(context)) }
        var minutes by remember { mutableStateOf(UserPrefs.getDurationMinutes(context)) }
        val isTimed = ExerciseKinds.isTimed(selected.name)

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text("Wake-up time", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            WheelTimePicker(
                initialHour = initHour,
                initialMinute = initMinute,
                initialIsAm = initIsAm,
                onTimeChanged = { h, m, am ->
                    hour = h
                    minute = m
                    isAm = am
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text("Exercise", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            ExerciseList(selected = selected, onSelect = { selected = it })

            Spacer(modifier = Modifier.height(24.dp))
            if (isTimed) {
                Text("How many minutes?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                TargetStepper(value = minutes, unit = "minutes", min = 1, max = 180, onValueChange = { minutes = it })
            } else {
                Text("How many reps?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                TargetStepper(value = reps, unit = "reps", min = 1, max = 200, onValueChange = { reps = it })
            }

            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(
                onClick = onOpenAllowedApps,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Choose allowed apps", color = OrangeEnd) }

            Spacer(modifier = Modifier.height(24.dp))
            PrimaryPillButton(
                text = "Save",
                onClick = {
                    val minuteText = minute.toString().padStart(2, '0')
                    val period = if (isAm) "AM" else "PM"
                    onSave("$hour:$minuteText $period", selected.name, reps, minutes)
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** "5:30 AM" -> (5, 30, true). Falls back to 5:00 AM if the text is malformed. */
private fun parseWakeTime(text: String): Triple<Int, Int, Boolean> {
    val parts = text.trim().split(" ")
    val hm = parts.getOrNull(0)?.split(":")
    val hour = hm?.getOrNull(0)?.toIntOrNull()?.coerceIn(1, 12) ?: 5
    val minute = hm?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    val isAm = parts.getOrNull(1)?.uppercase() != "PM"
    return Triple(hour, minute, isAm)
}
