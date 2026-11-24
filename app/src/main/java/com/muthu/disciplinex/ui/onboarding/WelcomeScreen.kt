package com.muthu.disciplinex.ui.onboarding

import com.muthu.disciplinex.data.tr
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
                text = tr("Show up before the world wakes up. Miss it, and the apps that distract you stay locked.", "எல்லோரும் எழுவதற்கு முன் நீங்கள் எழுந்து வாருங்கள். தவறினால், உங்கள் கவனத்தை இழுக்கும் ஆப்கள் பூட்டியே இருக்கும்."),
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(28.dp))
            GradientHeroCard(
                title = tr("Morning Challenge", "காலை சவால்"),
                subtitle = tr("Wake up, complete your set, earn your phone back.", "எழுந்திருங்கள், உங்கள் பயிற்சியை முடியுங்கள், உங்கள் போனை திரும்ப பெறுங்கள்.")
            ) {
                Text(
                    tr("5:00 AM  →  20 push-ups  →  unlocked", "காலை 5:00  →  20 புஷ்-அப்ஸ்  →  திறந்தது"),
                    color = com.muthu.disciplinex.ui.theme.Surface,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
            PrimaryPillButton(text = tr("Get started", "தொடங்குவோம்"), onClick = onGetStarted)
        }
    }
}
