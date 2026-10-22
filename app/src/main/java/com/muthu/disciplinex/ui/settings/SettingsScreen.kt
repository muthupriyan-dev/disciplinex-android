package com.muthu.disciplinex.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muthu.disciplinex.data.ChallengeSession
import com.muthu.disciplinex.ui.theme.OrangeEnd
import com.muthu.disciplinex.ui.theme.Surface
import com.muthu.disciplinex.ui.theme.TextMuted

/**
 * App settings. Alarms are added and edited from the Home list; this screen only
 * has the allowed-apps list (the guide is replayed from the (i) button on Home).
 * Read-only while the phone is locked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenAllowedApps: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val locked = remember { ChallengeSession.isLockActive(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
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
        ) {
            Text("Apps that stay open while locked", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Phone and Camera are always open. Pick any other apps you still need, like WhatsApp.",
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onOpenAllowedApps,
                enabled = !locked,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OrangeEnd, contentColor = Color.White)
            ) { Text("Choose allowed apps", fontWeight = FontWeight.SemiBold) }
            if (locked) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Locked until your challenge ends, so the list can't be changed right now.",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelMedium
                )
            }

        }
    }
}
