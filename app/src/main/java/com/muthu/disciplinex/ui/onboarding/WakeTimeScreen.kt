package com.muthu.disciplinex.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muthu.disciplinex.ui.components.PrimaryPillButton
import com.muthu.disciplinex.ui.components.WheelTimePicker
import com.muthu.disciplinex.ui.theme.Surface
import com.muthu.disciplinex.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WakeTimeScreen(onNext: (wakeTime: String) -> Unit, onBack: () -> Unit) {
    // Default wake time: 5:00 AM
    var selectedHour by remember { mutableStateOf(5) }
    var selectedMinute by remember { mutableStateOf(0) }
    var selectedIsAm by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(20.dp).fillMaxSize()) {
            Text("When do you want to wake up?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text("The alarm fires at this time, every day.", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))

            WheelTimePicker(
                initialHour = selectedHour,
                initialMinute = selectedMinute,
                initialIsAm = selectedIsAm,
                onTimeChanged = { hour, minute, isAm ->
                    selectedHour = hour
                    selectedMinute = minute
                    selectedIsAm = isAm
                }
            )

            Spacer(modifier = Modifier.weight(1f))
            PrimaryPillButton(
                text = "Continue",
                onClick = {
                    val minuteStr = selectedMinute.toString().padStart(2, '0')
                    val period = if (selectedIsAm) "AM" else "PM"
                    val wakeTime = "$selectedHour:$minuteStr $period"
                    onNext(wakeTime)
                }
            )
        }
    }
}
