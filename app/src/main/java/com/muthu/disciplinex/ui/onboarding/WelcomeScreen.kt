package com.muthu.disciplinex.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muthu.disciplinex.ui.components.GradientHeroCard
import com.muthu.disciplinex.ui.components.PrimaryPillButton
import com.muthu.disciplinex.ui.theme.TextMuted

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Discipline",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineLarge,
                    maxLines = 1
                )
                Text(
                    text = "X",
                    fontWeight = FontWeight.Bold,
                    color = com.muthu.disciplinex.ui.theme.OrangeEnd,
                    style = MaterialTheme.typography.headlineLarge,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Show up before the world wakes up. Miss it, and the apps that distract you stay locked.",
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(28.dp))
            GradientHeroCard(
                title = "Morning Challenge",
                subtitle = "Wake up, complete your set, earn your phone back."
            ) {
                Text(
                    "5:00 AM  →  20 push-ups  →  unlocked",
                    color = com.muthu.disciplinex.ui.theme.Surface,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
            PrimaryPillButton(text = "Get started", onClick = onGetStarted)
        }
    }
}
