package com.muthu.disciplinex.ui.onboarding

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.muthu.disciplinex.ui.components.PrimaryPillButton
import com.muthu.disciplinex.ui.theme.Divider
import com.muthu.disciplinex.ui.theme.OrangeEnd
import com.muthu.disciplinex.ui.theme.Surface
import com.muthu.disciplinex.ui.theme.TextMuted

/**
 * One row in the permissions list. [isGranted] is re-evaluated on every
 * recomposition (and on every resume, see below) so the checkmark stays
 * accurate even after the user comes back from a Settings screen.
 */
private data class PermissionRow(
    val title: String,
    val reason: String,
    val isGranted: Boolean,
    val actionable: Boolean, // false = informational only (not implemented yet)
    val onRequest: () -> Unit
)

private fun hasExactAlarmPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return alarmManager.canScheduleExactAlarms()
}

private fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

private fun hasCameraPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onFinish: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current

    // Bump this to force a re-check of all permission states (e.g. after
    // returning from the exact-alarm Settings screen, or after a runtime
    // permission dialog result).
    var refreshTick by remember { mutableIntStateOf(0) }

    // Re-check permissions every time the screen resumes — covers the case
    // where the user grants "Alarms & reminders" in system Settings and
    // then presses back to return here.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshTick++ }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshTick++ }

    val exactAlarmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshTick++ }

    val exactAlarmGranted = remember(refreshTick) { hasExactAlarmPermission(context) }
    val notificationGranted = remember(refreshTick) { hasNotificationPermission(context) }
    val cameraGranted = remember(refreshTick) { hasCameraPermission(context) }

    val permissions = listOf(
        PermissionRow(
            title = "Alarm & exact alarm",
            reason = "So your wake-up alarm fires precisely, even in Doze mode.",
            isGranted = exactAlarmGranted,
            actionable = true,
            onRequest = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    exactAlarmLauncher.launch(intent)
                }
            }
        ),
        PermissionRow(
            title = "Notifications",
            reason = "For challenge reminders, streak alerts, and weekly reports.",
            isGranted = notificationGranted,
            actionable = true,
            onRequest = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        ),
        PermissionRow(
            title = "Camera",
            reason = "Only used live for pose detection during camera-based exercises — nothing is recorded or uploaded.",
            isGranted = cameraGranted,
            actionable = true,
            onRequest = { cameraLauncher.launch(Manifest.permission.CAMERA) }
        ),
        PermissionRow(
            title = "Accessibility service",
            reason = "Only used to detect when a blocked app opens, so it can show the lock screen. Never reads message or app content. You'll set this up from Home once app-locking is ready.",
            isGranted = true, // not built yet — informational row only for now
            actionable = false,
            onRequest = {}
        )
    )

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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(p.reason, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            when {
                                p.isGranted -> Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Granted",
                                    tint = OrangeEnd
                                )
                                p.actionable -> TextButton(onClick = p.onRequest) {
                                    Text("Allow", color = OrangeEnd, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Divider)
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            PrimaryPillButton(text = "Continue", onClick = onFinish)
        }
    }
}
