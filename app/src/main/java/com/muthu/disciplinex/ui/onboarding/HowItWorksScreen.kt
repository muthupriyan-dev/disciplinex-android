package com.muthu.disciplinex.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muthu.disciplinex.data.tr
import com.muthu.disciplinex.ui.components.PrimaryPillButton
import com.muthu.disciplinex.ui.theme.Background
import com.muthu.disciplinex.ui.theme.OrangeEnd
import com.muthu.disciplinex.ui.theme.Surface
import com.muthu.disciplinex.ui.theme.TextMuted

/**
 * Shown once before the permission screen, so the permissions (especially the
 * accessibility one) make sense: how the app works in three steps.
 */
@Composable
fun HowItWorksScreen(onContinue: () -> Unit, onBack: () -> Unit) {
    Scaffold(containerColor = Background) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = tr("How it works", "இது எப்படி வேலை செய்கிறது"),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = tr("Three simple steps. Next we will ask for a few permissions that make this work.", "மூன்று எளிய படிகள். இது வேலை செய்ய தேவையான சில அனுமதிகளை அடுத்து கேட்போம்."),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(24.dp))
                Step(
                    number = "1",
                    title = tr("Your alarm rings", "உங்கள் அலாரம் அடிக்கும்"),
                    body = tr("You set a time and an exercise. When it rings, your distracting apps lock and a timer starts.", "நேரத்தையும் ஒரு பயிற்சியையும் அமைக்கிறீர்கள். அது அடிக்கும்போது, கவனத்தை சிதறடிக்கும் ஆப்கள் பூட்டப்பட்டு ஒரு டைமர் தொடங்கும்.")
                )
                Spacer(modifier = Modifier.height(14.dp))
                Step(
                    number = "2",
                    title = tr("Do your challenge", "சவாலை செய்யுங்கள்"),
                    body = tr("Push-ups and squats are counted by the camera. Other exercises use a timer or your steps.", "புஷ்-அப்ஸ், ஸ்குவாட்ஸ் கேமராவால் எண்ணப்படும். மற்ற பயிற்சிகளுக்கு டைமர் அல்லது உங்கள் அடிகள் பயன்படும்.")
                )
                Spacer(modifier = Modifier.height(14.dp))
                Step(
                    number = "3",
                    title = tr("Earn your phone back", "போனை திரும்ப பெறுங்கள்"),
                    body = tr("Finish in time and your apps unlock and your streak grows. Miss it and they stay locked for 24 hours.", "நேரத்துக்குள் முடித்தால் ஆப்கள் திறக்கும், தொடர் சாதனை கூடும். தவறினால் 24 மணி நேரம் பூட்டியே இருக்கும்.")
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = tr("Everything stays on your phone. Nothing is uploaded.", "எல்லாமே உங்கள் போனிலேயே இருக்கும். எதுவும் பதிவேற்றப்படாது."),
                    color = TextMuted,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            PrimaryPillButton(text = tr("Continue", "தொடர்"), onClick = onContinue)
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Back", "பின்செல்"), color = TextMuted)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Step(number: String, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(OrangeEnd),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(body, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
