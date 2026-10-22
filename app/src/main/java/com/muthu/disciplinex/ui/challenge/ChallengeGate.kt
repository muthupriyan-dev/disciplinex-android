package com.muthu.disciplinex.ui.challenge

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muthu.disciplinex.alarm.AlarmService
import com.muthu.disciplinex.data.ChallengeSession
import com.muthu.disciplinex.ui.theme.Background
import com.muthu.disciplinex.ui.theme.Danger
import com.muthu.disciplinex.ui.theme.OrangeEnd
import com.muthu.disciplinex.ui.theme.TextMuted
import com.muthu.disciplinex.ui.theme.TextPrimary
import java.text.DateFormat
import java.util.Date

/**
 * Decides what the challenge route shows, based on the running session:
 *  - opened inside the alarm's time limit -> the real challenge
 *  - window already over -> "missed" message
 *  - nothing pending -> a short note
 * Opening this screen inside the window is what marks the challenge as started.
 */
@Composable
fun ChallengeGate(onComplete: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    // Runs once when the route opens; marks the challenge as started if still in time.
    val phase = remember { ChallengeSession.enterChallenge(context) }

    // Opening the app by hand while the alarm is still ringing: silence it.
    LaunchedEffect(Unit) {
        if (phase == ChallengeSession.Phase.IN_PROGRESS) {
            try {
                AlarmService.stop(context)
            } catch (e: Exception) {
                // Alarm wasn't ringing — nothing to stop.
            }
        }
    }

    when (phase) {
        ChallengeSession.Phase.IN_PROGRESS -> {
            ChallengeScreen(
                exerciseName = ChallengeSession.exercise(context),
                targetReps = ChallengeSession.reps(context),
                durationMinutes = ChallengeSession.minutes(context),
                onComplete = {
                    ChallengeSession.complete(context)
                    onComplete()
                },
                onMissed = onClose
            )
        }

        ChallengeSession.Phase.MISSED -> {
            val unlockAt = DateFormat.getTimeInstance(DateFormat.SHORT)
                .format(Date(ChallengeSession.lockEndsAtMillis(context)))
            GateMessage(
                title = "Challenge missed",
                titleColor = Danger,
                body = "You didn't finish within the time limit, so today's challenge is over and your streak was reset.\n\nYour apps stay locked until $unlockAt.",
                buttonText = "Back to home",
                onClick = onClose
            )
        }

        else -> {
            GateMessage(
                title = "Nothing to do right now",
                titleColor = TextPrimary,
                body = "A challenge appears here when one of your alarms rings.",
                buttonText = "Back to home",
                onClick = onClose
            )
        }
    }
}

@Composable
private fun GateMessage(
    title: String,
    titleColor: androidx.compose.ui.graphics.Color,
    body: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Scaffold(containerColor = Background) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                title,
                color = titleColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(body, color = TextMuted, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OrangeEnd)
            ) { Text(buttonText, fontWeight = FontWeight.SemiBold) }
        }
    }
}
