package com.muthu.disciplinex.ui.challenge

import android.widget.Toast
import com.muthu.disciplinex.data.tr
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
                    // Finishing this alarm does not lift the 24-hour lock of an EARLIER missed alarm.
                    if (ChallengeSession.isLockActive(context)) {
                        val until = DateFormat.getTimeInstance(DateFormat.SHORT)
                            .format(Date(ChallengeSession.lockEndsAtMillis(context)))
                        Toast.makeText(
                            context,
                            tr(
                                "Challenge done. Your phone stays locked until $until because an earlier alarm was missed.",
                                "சேலஞ்ச் முடிந்தது. முந்தைய அலாரம் தவறியதால் $until வரை போன் பூட்டியே இருக்கும்."
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    onComplete()
                },
                onMissed = onClose
            )
        }

        ChallengeSession.Phase.MISSED -> {
            val unlockAt = DateFormat.getTimeInstance(DateFormat.SHORT)
                .format(Date(ChallengeSession.lockEndsAtMillis(context)))
            GateMessage(
                title = tr("Challenge missed", "சேலஞ்ச் தவறிவிட்டது"),
                titleColor = Danger,
                body = tr("You didn't finish within the time limit, so today's challenge is over and your streak was reset.\n\nYour apps stay locked until $unlockAt.", "நேர வரம்புக்குள் நீங்கள் முடிக்கவில்லை, அதனால் இன்றைய சேலஞ்ச் முடிந்தது, ஸ்ட்ரீக் மீட்டமைக்கப்பட்டது.\n\n$unlockAt வரை உங்கள் ஆப்கள் பூட்டியே இருக்கும்."),
                buttonText = tr("Back to home", "முகப்புக்கு திரும்பு"),
                onClick = onClose
            )
        }

        else -> {
            GateMessage(
                title = tr("Nothing to do right now", "இப்போது எதுவும் இல்லை"),
                titleColor = TextPrimary,
                body = tr("A challenge appears here when one of your alarms rings.", "உங்கள் அலாரம் அடிக்கும்போது சேலஞ்ச் இங்கே தோன்றும்."),
                buttonText = tr("Back to home", "முகப்புக்கு திரும்பு"),
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
