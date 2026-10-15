package com.muthu.disciplinex.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.muthu.disciplinex.data.UserPrefs
import com.muthu.disciplinex.lock.LockAccessibilityService
import com.muthu.disciplinex.ui.components.GradientHeroCard
import com.muthu.disciplinex.ui.theme.OrangeEnd
import com.muthu.disciplinex.ui.theme.Success
import com.muthu.disciplinex.ui.theme.Surface
import com.muthu.disciplinex.ui.theme.TextMuted
import com.muthu.disciplinex.ui.theme.Warning

@Composable
fun HomeScreen(wakeTime: String, duration: String, exerciseName: String) {
    val context = LocalContext.current

    // Re-read lock/accessibility state whenever the user returns to this
    // screen (e.g. after switching the service on in system Settings).
    var refreshTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val serviceEnabled = remember(refreshTick) { LockAccessibilityService.isEnabled(context) }
    val lockActive = remember(refreshTick) { UserPrefs.isLockActive(context) }
    var testArmed by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text("Today", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))

            GradientHeroCard(
                title = "$wakeTime challenge",
                subtitle = "$exerciseName \u00b7 $duration to complete"
            ) {
                Text(
                    "Your alarm will open this challenge automatically \u2014 dismiss it from the alarm screen to start.",
                    color = Surface.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ---- App lock card ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Surface)
                    .padding(18.dp)
            ) {
                Text("App lock", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))

                when {
                    !serviceEnabled -> {
                        Text(
                            "Off \u2014 turn on the DisciplineX App Lock accessibility service so distracting apps stay locked until you finish your challenge.",
                            color = Warning,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OrangeEnd)
                        ) { Text("Enable app lock") }
                    }
                    lockActive -> {
                        Text(
                            "Locked right now \u2014 finish your challenge to unlock Instagram, YouTube and the rest.",
                            color = OrangeEnd,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    else -> {
                        Text(
                            "Ready \u2014 blocked apps lock when your alarm rings and open again once you finish the challenge.",
                            color = Success,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (serviceEnabled && !lockActive) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            UserPrefs.startLock(context)
                            testArmed = true
                            refreshTick++
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    ) { Text("Test app lock now", color = OrangeEnd) }
                }

                if (testArmed && lockActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Test lock armed \u2014 open Instagram or YouTube. You should land on the challenge screen.",
                        color = TextMuted,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Streaks and the leaderboard are next.",
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
