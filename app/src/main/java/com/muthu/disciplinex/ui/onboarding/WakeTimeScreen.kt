package com.muthu.disciplinex.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muthu.disciplinex.ui.components.PrimaryPillButton
import com.muthu.disciplinex.ui.theme.OrangeEnd
import com.muthu.disciplinex.ui.theme.Surface
import com.muthu.disciplinex.ui.theme.TextMuted

private val wakeTimes = listOf("4:30 AM", "5:00 AM", "5:30 AM", "6:00 AM", "6:30 AM")
private val durations = listOf("15 min", "20 min", "30 min", "45 min")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WakeTimeScreen(onNext: (wakeTime: String, duration: String) -> Unit, onBack: () -> Unit) {
    var selectedTime by remember { mutableStateOf(wakeTimes[1]) }
    var selectedDuration by remember { mutableStateOf(durations[1]) }

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

            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(wakeTimes) { time ->
                    val selected = time == selectedTime
                    FilterChip(
                        selected = selected,
                        onClick = { selectedTime = time },
                        label = { Text(time) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OrangeEnd,
                            selectedLabelColor = Surface
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            Text("Challenge duration", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text("How long you have to finish it before apps lock.", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(durations) { d ->
                    val selected = d == selectedDuration
                    FilterChip(
                        selected = selected,
                        onClick = { selectedDuration = d },
                        label = { Text(d) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OrangeEnd,
                            selectedLabelColor = Surface
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            PrimaryPillButton(text = "Continue", onClick = { onNext(selectedTime, selectedDuration) })
        }
    }
}
