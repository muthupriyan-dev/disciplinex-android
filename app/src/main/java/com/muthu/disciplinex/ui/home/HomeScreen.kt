package com.muthu.disciplinex.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muthu.disciplinex.ui.components.GradientHeroCard
import com.muthu.disciplinex.ui.theme.Surface
import com.muthu.disciplinex.ui.theme.TextMuted

@Composable
fun HomeScreen(wakeTime: String, duration: String, exerciseName: String) {
    Scaffold { padding ->
        Column(modifier = Modifier.padding(padding).padding(20.dp).fillMaxSize()) {
            Text("Today", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))

            GradientHeroCard(
                title = "$wakeTime challenge",
                subtitle = "$exerciseName · $duration to complete"
            ) {
                Text(
                    "Alarm, camera tracking & app-locking arrive in the next build.",
                    color = Surface.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "This is the Milestone 1 skeleton — streaks, leaderboard, and the live challenge screen are next.",
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
