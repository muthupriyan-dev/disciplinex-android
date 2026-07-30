package com.muthu.disciplinex.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muthu.disciplinex.ui.components.PrimaryPillButton
import com.muthu.disciplinex.ui.theme.Divider
import com.muthu.disciplinex.ui.theme.Surface
import com.muthu.disciplinex.ui.theme.TextMuted

data class PermissionInfo(val title: String, val reason: String)

private val permissions = listOf(
    PermissionInfo("Alarm & exact alarm", "So your wake-up alarm fires precisely, even in Doze mode."),
    PermissionInfo("Notifications", "For challenge reminders, streak alerts, and weekly reports."),
    PermissionInfo("Camera", "Only used live for pose detection during camera-based exercises — nothing is recorded or uploaded."),
    PermissionInfo("Accessibility service", "Only used to detect when a blocked app opens, so it can show the lock screen. Never reads message or app content.")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onFinish: () -> Unit, onBack: () -> Unit) {
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
            Text("Why we ask for this", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "DisciplineX only uses these for the features below — never for reading your messages, OTPs, or personal content.",
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                permissions.forEach { p ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Surface)
                            .padding(14.dp)
                    ) {
                        Text(p.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(p.reason, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider(color = Divider)
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            PrimaryPillButton(text = "Allow & continue", onClick = onFinish)
        }
    }
}
