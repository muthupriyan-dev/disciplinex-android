package com.muthu.disciplinex.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muthu.disciplinex.ui.components.PrimaryPillButton
import com.muthu.disciplinex.ui.theme.*

data class ExerciseOption(val name: String, val usesCamera: Boolean)

private val exerciseOptions = listOf(
    ExerciseOption("Push-ups", usesCamera = true),
    ExerciseOption("Squats", usesCamera = true),
    ExerciseOption("Jumping Jacks", usesCamera = false),
    ExerciseOption("Walking", usesCamera = false),
    ExerciseOption("Meditation", usesCamera = false),
    ExerciseOption("Reading", usesCamera = false)
)

@Composable
fun ExerciseTypeScreen(onNext: (ExerciseOption) -> Unit) {
    var selected by remember { mutableStateOf(exerciseOptions[0]) }

    Scaffold { padding ->
        Column(modifier = Modifier.padding(padding).padding(20.dp).fillMaxSize()) {
            Text("Pick your challenge", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Camera-tracked exercises use pose detection; others use motion sensors.", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(18.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                exerciseOptions.forEach { option ->
                    val isSelected = option == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) OrangeEnd else Surface)
                            .clickable { selected = option }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            option.name,
                            color = if (isSelected) Surface else TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (option.usesCamera) "Camera" else "Motion",
                            color = if (isSelected) Surface.copy(alpha = 0.85f) else TextMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            PrimaryPillButton(text = "Continue", onClick = { onNext(selected) })
        }
    }
}
