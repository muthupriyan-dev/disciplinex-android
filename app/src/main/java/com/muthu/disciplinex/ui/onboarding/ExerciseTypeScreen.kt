package com.muthu.disciplinex.ui.onboarding

import com.muthu.disciplinex.data.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muthu.disciplinex.data.ExerciseKinds
import com.muthu.disciplinex.ui.components.PrimaryPillButton
import com.muthu.disciplinex.ui.components.TargetStepper
import com.muthu.disciplinex.ui.theme.*

data class ExerciseOption(val name: String, val usesCamera: Boolean)

internal val exerciseOptions = listOf(
    ExerciseOption("Push-ups", usesCamera = true),
    ExerciseOption("Squats", usesCamera = true),
    ExerciseOption("Jumping Jacks", usesCamera = true),
    ExerciseOption("Walking", usesCamera = false),
    ExerciseOption("Meditation", usesCamera = false),
    ExerciseOption("Reading", usesCamera = false)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseTypeScreen(
    onNext: (option: ExerciseOption, targetReps: Int, durationMinutes: Int) -> Unit,
    onBack: () -> Unit
) {
    var selected by remember { mutableStateOf(exerciseOptions[0]) }
    var reps by remember { mutableStateOf(10) }
    var minutes by remember { mutableStateOf(15) }
    val isTimed = ExerciseKinds.isTimed(selected.name)

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
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text("Pick your challenge", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Push-ups and squats are camera-counted. Walking, meditation and reading run on a timer you can't skip.",
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(18.dp))

            ExerciseList(selected = selected, onSelect = { selected = it })

            Spacer(modifier = Modifier.height(24.dp))

            if (isTimed) {
                Text("How many minutes?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "The timer only runs while this app is open. You have to stay for the full time.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                TargetStepper(value = minutes, unit = "minutes", min = 1, max = 180, onValueChange = { minutes = it })
            } else {
                Text("How many reps?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "You have to finish all of them to unlock your phone.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                TargetStepper(value = reps, unit = "reps", min = 1, max = 200, onValueChange = { reps = it })
            }

            Spacer(modifier = Modifier.height(28.dp))
            PrimaryPillButton(text = "Continue", onClick = { onNext(selected, reps, minutes) })
        }
    }
}

/** The tappable list of exercises — shared by onboarding and Settings. */
@Composable
internal fun ExerciseList(selected: ExerciseOption, onSelect: (ExerciseOption) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        exerciseOptions.forEach { option ->
            val isSelected = option == selected
            val tag = when {
                option.usesCamera -> tr("Camera", "கேமரா")
                ExerciseKinds.isTimed(option.name) -> tr("Timer", "டைமர்")
                else -> tr("Reps", "முறை")
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) OrangeEnd else Surface)
                    .clickable { onSelect(option) }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    ExerciseKinds.display(option.name),
                    color = if (isSelected) Surface else TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    tag,
                    color = if (isSelected) Surface.copy(alpha = 0.85f) else TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
