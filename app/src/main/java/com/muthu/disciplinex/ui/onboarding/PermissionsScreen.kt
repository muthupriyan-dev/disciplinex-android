package com.muthu.disciplinex.ui.onboarding

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.muthu.disciplinex.lock.LockAccessibilityService
import com.muthu.disciplinex.ui.components.PrimaryPillButton
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
    val onRequest: () -> Unit
)

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

/** Android 14+ can switch off "full-screen" alarm screens for apps; older versions always allow it. */
private fun hasFullScreenIntentPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
    val manager = context.getSystemService(NotificationManager::class.java) ?: return true
    return manager.canUseFullScreenIntent()
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onFinish: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current

    // Bump this to force a re-check of all permission states (e.g. after
    // returning from a Settings screen, or after a runtime permission dialog).
    var refreshTick by remember { mutableIntStateOf(0) }

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

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshTick++ }

    val notificationGranted = remember(refreshTick) { hasNotificationPermission(context) }
    val fullScreenGranted = remember(refreshTick) { hasFullScreenIntentPermission(context) }
    val cameraGranted = remember(refreshTick) { hasCameraPermission(context) }
    val batteryGranted = remember(refreshTick) { isIgnoringBatteryOptimizations(context) }
    val accessibilityGranted = remember(refreshTick) { LockAccessibilityService.isEnabled(context) }

    val permissions = buildList {
        add(
            PermissionRow(
                title = "Notifications",
                reason = "Shows the wake-up alarm and keeps it running reliably.",
                isGranted = notificationGranted,
                onRequest = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(
                PermissionRow(
                    title = "Alarm over lock screen",
                    reason = "So the alarm screen can appear on top when your phone is locked.",
                    isGranted = fullScreenGranted,
                    onRequest = {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        settingsLauncher.launch(intent)
                    }
                )
            )
        }
        add(
            PermissionRow(
                title = "Camera",
                reason = "Only used live for pose detection during camera-based exercises — nothing is recorded or uploaded.",
                isGranted = cameraGranted,
                onRequest = { cameraLauncher.launch(Manifest.permission.CAMERA) }
            )
        )
        add(
            PermissionRow(
                title = "Run in background",
                reason = "Stops your phone's battery saver from killing the alarm or the app lock. Choose \"Allow\".",
                isGranted = batteryGranted,
                onRequest = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    settingsLauncher.launch(intent)
                }
            )
        )
        add(
            PermissionRow(
                title = "Accessibility service",
                reason = "Only used to detect when a blocked app opens, so it can send you back to your challenge. Never reads message or app content. Tap Allow, open \"DisciplineX App Lock\" and switch it on.",
                isGranted = accessibilityGranted,
                onRequest = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )
        )
    }

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
            // Scrolls on small phones so the Continue button below is never pushed off screen.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
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
                                if (p.isGranted) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Granted",
                                        tint = OrangeEnd
                                    )
                                } else {
                                    TextButton(onClick = p.onRequest) {
                                        Text("Allow", color = OrangeEnd, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                if (!accessibilityGranted) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "Heads up: until Accessibility is switched on, the app lock won't block anything. You can still turn it on later from the Home screen.",
                        color = OrangeEnd,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            PrimaryPillButton(text = "Continue", onClick = onFinish)
        }
    }
}
