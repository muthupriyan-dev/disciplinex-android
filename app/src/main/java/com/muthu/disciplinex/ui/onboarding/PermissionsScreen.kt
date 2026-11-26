package com.muthu.disciplinex.ui.onboarding

import com.muthu.disciplinex.data.AppSettings
import com.muthu.disciplinex.ui.components.tourTarget
import com.muthu.disciplinex.ui.components.rememberTourState
import com.muthu.disciplinex.ui.components.TourStep
import com.muthu.disciplinex.ui.components.TourOverlay
import com.muthu.disciplinex.ui.components.GuideButton
import com.muthu.disciplinex.data.tr
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
import com.muthu.disciplinex.data.UninstallGuard
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

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshTick++ }

    // If the user denied with "Don't ask again", Android never shows the pop-up again
    // and Continue would be stuck. In that case send them to this app's settings page.
    fun openAppSettings(action: String) {
        val intent = Intent(action).apply {
            if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                data = Uri.parse("package:${context.packageName}")
            }
        }
        settingsLauncher.launch(intent)
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshTick++
        val activity = context.findActivity()
        if (!granted && activity != null &&
            !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.POST_NOTIFICATIONS
            )
        ) {
            openAppSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshTick++
        val activity = context.findActivity()
        if (!granted && activity != null &&
            !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.CAMERA
            )
        ) {
            openAppSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        }
    }

    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshTick++
        val activity = context.findActivity()
        if (!granted && activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.ACTIVITY_RECOGNITION
            )
        ) {
            openAppSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        }
    }

    val notificationGranted = remember(refreshTick) { hasNotificationPermission(context) }
    val fullScreenGranted = remember(refreshTick) { hasFullScreenIntentPermission(context) }
    val cameraGranted = remember(refreshTick) { hasCameraPermission(context) }
    val activityGranted = remember(refreshTick) { com.muthu.disciplinex.data.WalkSteps.hasPermission(context) }
    val batteryGranted = remember(refreshTick) { isIgnoringBatteryOptimizations(context) }
    val accessibilityGranted = remember(refreshTick) { LockAccessibilityService.isEnabled(context) }
    // Phones without device-admin support (rare) must not get stuck on this screen.
    val adminGranted = remember(refreshTick) {
        !context.packageManager.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN) ||
            UninstallGuard.isAdminActive(context)
    }

    val permissions = buildList {
        add(
            PermissionRow(
                title = tr("Notifications", "அறிவிப்புகள்"),
                reason = tr("Shows the wake-up alarm and keeps it running reliably.", "எழுப்பும் அலாரத்தை காட்டி, அது தவறாமல் வேலை செய்ய உதவும்."),
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
                    title = tr("Alarm over lock screen", "பூட்டுத் திரையில் அலாரம்"),
                    reason = tr("So the alarm screen can appear on top when your phone is locked.", "போன் பூட்டியிருக்கும்போது, அலாரம் திரை மேலே தோன்றுவதற்காக."),
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
                title = tr("Camera", "கேமரா"),
                reason = tr("Only used live for pose detection during camera-based exercises — nothing is recorded or uploaded.", "கேமரா பயிற்சிகளின்போது உங்கள் உடல் நிலையை அறிய மட்டுமே நேரடியாக பயன்படும் — எதுவும் பதிவு செய்யப்படாது, பதிவேற்றப்படாது."),
                isGranted = cameraGranted,
                onRequest = { cameraLauncher.launch(Manifest.permission.CAMERA) }
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(
                PermissionRow(
                    title = tr("Physical activity", "உடல் செயல்பாடு"),
                    reason = tr("Counts your steps for the Walking challenge. Nothing leaves your phone.", "நடைப்பயிற்சிக்காக உங்கள் அடிகளை எண்ணும். எதுவும் உங்கள் போனை விட்டு வெளியே போகாது."),
                    isGranted = activityGranted,
                    onRequest = { activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }
                )
            )
        }
        add(
            PermissionRow(
                title = tr("Run in background", "பின்னணியில் இயங்க அனுமதி"),
                reason = tr("Stops your phone's battery saver from killing the alarm or the app lock. Choose \"Allow\".", "போனின் பேட்டரி சேமிப்பு அமைப்பு அலாரத்தையோ ஆப் பூட்டையோ நிறுத்திவிடாமல் தடுக்கும். \"Allow\" (அனுமதி) என்பதை தேர்ந்தெடுங்கள்."),
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
                title = tr("Accessibility service", "அணுகல் வசதி சேவை"),
                reason = tr("Only used to see which app is opening (including small floating windows), so it can send you back to your challenge. Never reads your messages or anything you type. Tap Allow, open \"DisciplineX App Lock\" and switch it on. If the switch is greyed out: Settings → Apps → DisciplineX → ⋮ → Allow restricted settings.", "எந்த ஆப் திறக்கிறது என்பதை (சிறிய மிதக்கும் திரைகள் உட்பட) பார்க்க மட்டுமே பயன்படும்; அதனால் உங்களை மீண்டும் சவாலுக்கு அனுப்ப முடியும். உங்கள் செய்திகளையோ, நீங்கள் தட்டச்சு செய்வதையோ ஒருபோதும் படிக்காது. அனுமதி என்பதை தட்டி, \"DisciplineX App Lock\" என்பதை திறந்து இயக்குங்கள். சுவிட்ச் சாம்பல் நிறத்தில் இருந்தால்: Settings → Apps → DisciplineX → ⋮ → Allow restricted settings."),
                isGranted = accessibilityGranted,
                onRequest = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )
        )
        add(
            PermissionRow(
                title = tr("Uninstall protection", "நிறுவல் நீக்கப் பாதுகாப்பு"),
                reason = tr(
                    "Stops DisciplineX from being uninstalled in one tap to dodge the lock. It asks for no control over your phone. Tap Allow, then \"Activate this device admin app\".",
                    "பூட்டை தவிர்க்க DisciplineX-ஐ ஒரே தட்டலில் நிறுவல் நீக்குவதை தடுக்கும். உங்கள் போனை கட்டுப்படுத்த அனுமதி கேட்காது. அனுமதி என்பதை தட்டி, \"Activate this device admin app\" என்பதை தேர்ந்தெடுங்கள்."
                ),
                isGranted = adminGranted,
                onRequest = {
                    try {
                        settingsLauncher.launch(
                            UninstallGuard.addAdminIntent(
                                context,
                                tr(
                                    "Makes DisciplineX impossible to uninstall in one tap. It asks for no permissions over your phone.",
                                    "DisciplineX-ஐ ஒரே தட்டலில் நிறுவல் நீக்க முடியாதபடி செய்யும். உங்கள் போனில் எந்த கட்டுப்பாட்டையும் கேட்காது."
                                )
                            )
                        )
                    } catch (e: Exception) {
                        // No device-admin screen on this phone: nothing to do.
                    }
                }
            )
        )
    }

    // Continue only unlocks once EVERY permission above is granted.
    val allGranted = permissions.all { it.isGranted }

    val tour = rememberTourState()
    var showTour by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val steps = remember(showTour, AppSettings.language) { permissionsTourSteps().filter { tour.bounds.containsKey(it.key) } }
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = tr("Back", "பின்செல்"))
                    }
                },
                actions = { GuideButton(onClick = { showTour = true }) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(20.dp).fillMaxSize()) {
            // Scrolls on small phones so the Continue button below is never pushed off screen.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scroll)
            ) {
                Text(tr("Why we ask for this", "இதை ஏன் கேட்கிறோம்"), modifier = Modifier.tourTarget(tour, "why"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    tr("DisciplineX only uses these for the features below — never for reading your messages, OTPs, or personal content.", "கீழே உள்ள வசதிகளுக்கு மட்டுமே DisciplineX இவற்றை பயன்படுத்தும் — உங்கள் செய்திகள், OTP, தனிப்பட்ட தகவல்களை ஒருபோதும் படிக்காது."),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                Column(modifier = Modifier.tourTarget(tour, "rows"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                        contentDescription = tr("Granted", "அனுமதிக்கப்பட்டது"),
                                        tint = OrangeEnd
                                    )
                                } else {
                                    TextButton(onClick = p.onRequest) {
                                        Text(tr("Allow", "அனுமதி"), color = OrangeEnd, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                if (!allGranted) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        tr("DisciplineX can't work without these. Allow every item above to continue.", "இவை இல்லாமல் DisciplineX வேலை செய்யாது. தொடர, மேலே உள்ள எல்லாவற்றையும் அனுமதியுங்கள்."),
                        color = OrangeEnd,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            PrimaryPillButton(text = tr("Continue", "தொடர்"), onClick = onFinish, enabled = allGranted, modifier = Modifier.tourTarget(tour, "continue"))
        }
    }
        if (showTour) {
            TourOverlay(steps, tour, onFinish = { showTour = false }, scrollState = scroll)
        }
    }
}

private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun permissionsTourSteps(): List<TourStep> = listOf(
    TourStep(
        "why", tr("Why these permissions", "இந்த அனுமதிகள் ஏன் தேவை"),
        tr("DisciplineX only uses these for the features listed. It never reads your messages, OTPs or personal content.",
            "பட்டியலிடப்பட்ட வசதிகளுக்கு மட்டுமே DisciplineX இவற்றை பயன்படுத்தும். உங்கள் செய்திகள், OTP, தனிப்பட்ட தகவல்களை ஒருபோதும் படிக்காது.")
    ),
    TourStep(
        "rows", tr("Allow each one", "ஒவ்வொன்றையும் அனுமதியுங்கள்"),
        tr("Tap Allow next to each item. A tick means it is done. The app can't work properly until every one is on.",
            "ஒவ்வொன்றின் அருகிலும் அனுமதி என்பதை தட்டுங்கள். சரிக்குறி என்றால் முடிந்தது. எல்லாம் இயக்கத்தில் இருக்கும் வரை ஆப் சரியாக வேலை செய்யாது.")
    ),
    TourStep(
        "continue", tr("Continue", "தொடர்"),
        tr("This unlocks only after every permission is allowed.",
            "எல்லா அனுமதிகளையும் கொடுத்த பிறகுதான் இது திறக்கும்.")
    )
)
