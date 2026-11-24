package com.muthu.disciplinex.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.muthu.disciplinex.alarm.AlarmTest
import com.muthu.disciplinex.data.AppSettings
import com.muthu.disciplinex.data.ChallengeSession
import com.muthu.disciplinex.data.PermissionChecks
import com.muthu.disciplinex.data.tr
import com.muthu.disciplinex.ui.components.GuideButton
import com.muthu.disciplinex.ui.components.TourOverlay
import com.muthu.disciplinex.ui.components.TourStep
import com.muthu.disciplinex.ui.components.rememberTourState
import com.muthu.disciplinex.ui.components.tourTarget
import com.muthu.disciplinex.ui.theme.Background
import com.muthu.disciplinex.ui.theme.ChipBackground
import com.muthu.disciplinex.ui.theme.OrangeEnd
import com.muthu.disciplinex.ui.theme.Success
import com.muthu.disciplinex.ui.theme.Surface
import com.muthu.disciplinex.ui.theme.TextMuted
import com.muthu.disciplinex.ui.theme.TextPrimary

/**
 * App settings, grouped into Appearance, Alarm preferences, Lock & Protection,
 * Progress & History, Data & Privacy and About. Theme, language and sound can
 * be changed any time; the allowed-apps list is read-only while the phone is
 * locked. All actions call the same settings / permission / backup code as before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenAllowedApps: () -> Unit,
    onOpenHistory: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val locked = remember { ChallengeSession.isLockActive(context) }

    // Re-check the permissions every time the user comes back from a system screen.
    var refreshTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            AlarmTest.stop() // never leave a test sound running when leaving this screen
        }
    }

    var ringtoneTitle by remember { mutableStateOf(ringtoneTitleOf(context)) }
    var vibrate by remember { mutableStateOf(AppSettings.vibrate(context)) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                AppSettings.setRingtoneUri(context, uri.toString())
                ringtoneTitle = ringtoneTitleOf(context)
            }
        }
    }

    val permissions = remember(refreshTick) {
        PermissionChecks.Kind.values().map { it to PermissionChecks.isGranted(context, it) }
    }
    // App lock (Accessibility) first, then the rest in their usual order.
    val orderedPermissions = remember(permissions) {
        permissions.sortedBy { it.first != PermissionChecks.Kind.ACCESSIBILITY }
    }
    val missingCount = permissions.count { !it.second }

    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    val tour = rememberTourState()
    var showTour by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val steps = remember(showTour, AppSettings.language) { settingsTourSteps().filter { tour.bounds.containsKey(it.key) } }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Background,
            topBar = {
                TopAppBar(
                    title = { Text(tr("Settings", "அமைப்புகள்"), fontWeight = FontWeight.Bold) },
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
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .fillMaxSize()
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    tr("Personalize your DisciplineX experience.", "உங்கள் DisciplineX அனுபவத்தை உங்களுக்கேற்ப அமையுங்கள்."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp)
                )

                // ---- Appearance ----
                SettingsSection(tr("Appearance", "காட்சி அமைப்பு")) {
                    SettingsGroup {
                        Column(modifier = Modifier.tourTarget(tour, "theme")) {
                            RowLayout(
                                icon = Icons.Filled.Palette,
                                title = tr("Theme", "தோற்றம்")
                            )
                            Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                                SegmentedSelector(
                                    options = listOf(
                                        AppSettings.THEME_SYSTEM to tr("System", "போன் படி"),
                                        AppSettings.THEME_LIGHT to tr("Light", "ஒளி"),
                                        AppSettings.THEME_DARK to tr("Dark", "இருள்")
                                    ),
                                    selected = AppSettings.themeMode,
                                    onSelect = { AppSettings.setTheme(context, it) }
                                )
                            }
                        }
                        GroupDivider()
                        SettingsRow(
                            icon = Icons.Filled.Language,
                            title = tr("Language", "மொழி"),
                            subtitle = if (AppSettings.language == AppSettings.LANG_TA) "தமிழ்" else "English",
                            onClick = { showLanguageDialog = true },
                            showChevron = true,
                            modifier = Modifier.tourTarget(tour, "language")
                        )
                    }
                }

                // ---- Alarm preferences ----
                SettingsSection(tr("Alarm preferences", "அலாரம் விருப்பங்கள்")) {
                    SettingsGroup(Modifier.tourTarget(tour, "sound")) {
                        SettingsRow(
                            icon = Icons.Filled.MusicNote,
                            title = tr("Alarm ringtone", "அலாரம் ஒலி"),
                            subtitle = ringtoneTitle,
                            onClick = {
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, tr("Alarm sound", "அலாரம் ஒலி"))
                                    AppSettings.ringtoneUri(context)?.let {
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it))
                                    }
                                }
                                ringtoneLauncher.launch(intent)
                            },
                            trailing = {
                                Text(
                                    tr("Change", "மாற்று"),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = AccentText
                                )
                            }
                        )
                        GroupDivider()
                        SettingsSwitchRow(
                            icon = Icons.Filled.Vibration,
                            title = tr("Vibration", "அதிர்வு"),
                            subtitle = tr("Vibrate along with the alarm sound.", "அலாரம் ஒலியுடன் சேர்ந்து அதிரும்."),
                            checked = vibrate,
                            onCheckedChange = {
                                vibrate = it
                                AppSettings.setVibrate(context, it)
                            }
                        )
                        GroupDivider()
                        SettingsRow(
                            icon = if (AlarmTest.isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            title = if (AlarmTest.isPlaying) tr("Stop test", "சோதனையை நிறுத்து")
                            else tr("Test alarm sound", "அலாரம் ஒலியை சோதி"),
                            subtitle = tr(
                                "Plays for a few seconds so you can check the volume. It doesn't start a challenge or lock anything.",
                                "ஒலி அளவை சரிபார்க்க சில வினாடிகள் ஒலிக்கும். இது சவாலை தொடங்காது, எதையும் பூட்டாது."
                            ),
                            onClick = { if (AlarmTest.isPlaying) AlarmTest.stop() else AlarmTest.start(context) }
                        )
                    }
                }

                // ---- Lock & Protection ----
                SettingsSection(tr("Lock & Protection", "பூட்டு & பாதுகாப்பு")) {
                    SettingsGroup(Modifier.tourTarget(tour, "apps")) {
                        SettingsRow(
                            icon = Icons.Filled.Apps,
                            title = tr("Allowed apps", "அனுமதிக்கப்பட்ட ஆப்கள்"),
                            subtitle = if (locked) {
                                tr(
                                    "Locked until your challenge ends, so the list can't be changed right now.",
                                    "சவால் முடியும் வரை பூட்டப்பட்டுள்ளது, அதனால் இந்த பட்டியலை இப்போது மாற்ற முடியாது."
                                )
                            } else {
                                tr(
                                    "Phone and Camera are always open. Pick any other apps you still need, like WhatsApp.",
                                    "போன், கேமரா எப்போதும் திறந்திருக்கும். WhatsApp போல, உங்களுக்கு இன்னும் தேவையான மற்ற ஆப்களை தேர்ந்தெடுங்கள்."
                                )
                            },
                            enabled = !locked,
                            onClick = onOpenAllowedApps,
                            showChevron = true
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    SettingsSubLabel(tr("Permissions", "அனுமதிகள்"))
                    SettingsGroup(Modifier.tourTarget(tour, "permissions")) {
                        PermissionSummaryRow(missingCount)
                        orderedPermissions.forEach { (kind, granted) ->
                            GroupDivider()
                            PermissionRow(
                                kind = kind,
                                granted = granted,
                                onFix = { openPermissionScreen(context, kind) }
                            )
                        }
                    }
                }

                // ---- Progress & History ----
                SettingsSection(tr("Progress & History", "முன்னேற்றம் & வரலாறு")) {
                    SettingsGroup(Modifier.tourTarget(tour, "history")) {
                        SettingsRow(
                            icon = Icons.Filled.CalendarMonth,
                            title = tr("Streak history", "தொடர் சாதனை வரலாறு"),
                            subtitle = tr(
                                "See which days you finished and which you missed.",
                                "எந்த நாட்களில் முடித்தீர்கள், எந்த நாட்களில் தவறவிட்டீர்கள் என்று பாருங்கள்."
                            ),
                            onClick = onOpenHistory,
                            showChevron = true
                        )
                    }
                }

                // ---- Data & Privacy ----
                SettingsSection(tr("Data & Privacy", "தரவு & தனியுரிமை")) {
                    BackupSection(Modifier.tourTarget(tour, "backup"))
                    Spacer(modifier = Modifier.height(12.dp))
                    PrivacyRowGroup(Modifier.tourTarget(tour, "privacy"))
                }

                // ---- About ----
                SettingsSection(tr("About DisciplineX", "DisciplineX பற்றி")) {
                    SettingsGroup(Modifier.tourTarget(tour, "about")) {
                        SettingsRow(
                            icon = Icons.Filled.Info,
                            title = "DisciplineX",
                            subtitle = if (version.isNotBlank()) tr("Version $version", "பதிப்பு $version") else null
                        )
                        GroupDivider()
                        Text(
                            tr(
                                "Wake up, finish your challenge, earn your phone back. No account needed, and your data stays on this phone.",
                                "எழுந்திருங்கள், சவாலை முடியுங்கள், உங்கள் போனை திரும்ப பெறுங்கள். கணக்கு தேவையில்லை, உங்கள் தகவல்கள் இந்த போனிலேயே இருக்கும்."
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        if (showTour) {
            TourOverlay(steps, tour, onFinish = { showTour = false }, scrollState = scroll)
        }
    }

    if (showLanguageDialog) {
        LanguageDialog(
            selected = AppSettings.language,
            onSelect = {
                AppSettings.setLanguage(context, it)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
}

// ---------------------------------------------------------------------------
// Permissions
// ---------------------------------------------------------------------------

/** Top row of the permissions group: one line saying whether anything needs attention. */
@Composable
private fun PermissionSummaryRow(missingCount: Int) {
    if (missingCount == 0) {
        SettingsRow(
            icon = Icons.Filled.CheckCircle,
            iconTint = Success,
            title = tr("Everything is on.", "எல்லாம் இயக்கத்தில் உள்ளது.")
        )
    } else {
        SettingsRow(
            icon = Icons.Filled.Warning,
            iconTint = DangerText,
            title = tr("$missingCount need attention", "$missingCount கவனம் தேவை"),
            subtitle = tr(
                "Something is off. The alarm or the app lock may not work properly until it is fixed.",
                "ஏதோ ஒன்று இயக்கத்தில் இல்லை. சரிசெய்யும் வரை அலாரம் அல்லது ஆப் பூட்டு சரியாக வேலை செய்யாமல் போகலாம்."
            )
        )
    }
}

@Composable
private fun PermissionRow(
    kind: PermissionChecks.Kind,
    granted: Boolean,
    onFix: () -> Unit
) {
    val notRequired = granted && isNotRequiredOnThisAndroid(kind)
    SettingsRow(
        icon = permissionIcon(kind),
        iconTint = if (granted) OrangeEnd else DangerText,
        title = PermissionChecks.title(kind),
        subtitle = permissionReason(kind),
        onClick = if (granted) null else onFix,
        actionLabel = if (granted) null else tr("Enable", "இயக்கு"),
        trailing = {
            when {
                notRequired -> StatusPill(tr("Not needed", "தேவையில்லை"), PillTone.NEUTRAL)
                granted -> StatusPill(tr("On", "இயக்கத்தில்"), PillTone.ON)
                else -> StatusPill(tr("Off", "இயக்கத்தில் இல்லை"), PillTone.OFF)
            }
        }
    )
}

/** Android versions that never ask for this permission report it as granted. */
private fun isNotRequiredOnThisAndroid(kind: PermissionChecks.Kind): Boolean = when (kind) {
    PermissionChecks.Kind.NOTIFICATIONS -> Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    PermissionChecks.Kind.FULL_SCREEN -> Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    else -> false
}

private fun permissionIcon(kind: PermissionChecks.Kind): ImageVector = when (kind) {
    PermissionChecks.Kind.NOTIFICATIONS -> Icons.Filled.Notifications
    PermissionChecks.Kind.FULL_SCREEN -> Icons.Filled.ScreenLockPortrait
    PermissionChecks.Kind.CAMERA -> Icons.Filled.CameraAlt
    PermissionChecks.Kind.ACTIVITY -> Icons.Filled.DirectionsWalk
    PermissionChecks.Kind.BATTERY -> Icons.Filled.BatteryChargingFull
    PermissionChecks.Kind.ACCESSIBILITY -> Icons.Filled.Accessibility
}

/** Why the app needs this permission, in plain words. */
private fun permissionReason(kind: PermissionChecks.Kind): String = when (kind) {
    PermissionChecks.Kind.NOTIFICATIONS -> tr(
        "Shows your alarm and challenge reminders.",
        "அலாரம், சவால் நினைவூட்டல்களை காட்ட."
    )
    PermissionChecks.Kind.FULL_SCREEN -> tr(
        "Lets the alarm open over the lock screen.",
        "பூட்டுத் திரையின் மேல் அலாரம் திறக்க."
    )
    PermissionChecks.Kind.CAMERA -> tr(
        "Counts your push-ups and squats on the phone. Nothing is saved.",
        "புஷ்-அப், ஸ்குவாட் எண்ண. படங்கள் சேமிக்கப்படாது."
    )
    PermissionChecks.Kind.ACTIVITY -> tr(
        "Counts your steps for walking challenges.",
        "நடைப்பயிற்சி சவாலுக்கு உங்கள் அடிகளை எண்ண."
    )
    PermissionChecks.Kind.BATTERY -> tr(
        "Keeps the alarm reliable when the phone is idle.",
        "போன் ஓய்வில் இருக்கும்போதும் அலாரம் சரியாக அடிக்க."
    )
    PermissionChecks.Kind.ACCESSIBILITY -> tr(
        "Needed to block other apps during a challenge.",
        "சவால் நேரத்தில் மற்ற ஆப்களை தடுக்க தேவை."
    )
}

private fun openPermissionScreen(context: Context, kind: PermissionChecks.Kind) {
    try {
        context.startActivity(PermissionChecks.fixIntent(context, kind))
    } catch (e: Exception) {
        // No such settings screen on this phone: open the app's own page.
        try {
            context.startActivity(
                Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                )
            )
        } catch (_: Exception) {
        }
    }
}

// ---------------------------------------------------------------------------
// Controls
// ---------------------------------------------------------------------------

/** Pill-shaped segmented control (used for the theme). */
@Composable
private fun SegmentedSelector(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ChipBackground)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val background by animateColorAsState(
                targetValue = if (isSelected) OrangeEnd else Color.Transparent,
                label = "segment"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(background)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(value) })
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (isSelected) Color.White else TextPrimary,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Picks the app language. Choosing an option applies it straight away. */
@Composable
private fun LanguageDialog(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary,
        title = { Text(tr("Language", "மொழி")) },
        text = {
            Column {
                listOf(
                    AppSettings.LANG_EN to "English",
                    AppSettings.LANG_TA to "தமிழ்"
                ).forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = value == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(value) }
                            )
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = OrangeEnd)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("Close", "மூடு"), color = AccentText, fontWeight = FontWeight.Bold)
            }
        }
    )
}

private fun ringtoneTitleOf(context: Context): String {
    val fallback = tr("Phone's default alarm sound", "போனின் இயல்புநிலை அலாரம் ஒலி")
    val saved = AppSettings.ringtoneUri(context) ?: return fallback
    return try {
        RingtoneManager.getRingtone(context, Uri.parse(saved))?.getTitle(context) ?: fallback
    } catch (e: Exception) {
        fallback
    }
}

private fun settingsTourSteps(): List<TourStep> = listOf(
    TourStep(
        "theme", tr("Theme", "தோற்றம்"),
        tr("Pick Light, Dark, or System to follow your phone. It changes the whole app straight away.",
            "ஒளி, இருள் அல்லது போனை பின்பற்ற போன் படி என்பதை தேர்ந்தெடுங்கள். முழு ஆப்பும் உடனே மாறும்.")
    ),
    TourStep(
        "language", tr("Language", "மொழி"),
        tr("Switch the whole app between English and Tamil.",
            "முழு ஆப்பையும் ஆங்கிலம் அல்லது தமிழுக்கு மாற்றுங்கள்.")
    ),
    TourStep(
        "sound", tr("Alarm preferences", "அலாரம் விருப்பங்கள்"),
        tr("Choose the ringtone, turn vibration on or off, and tap Test alarm sound to hear it for a few seconds. Testing never starts a challenge or locks anything.",
            "அலாரம் ஒலியை தேர்ந்தெடுங்கள், அதிர்வை இயக்கவோ அணைக்கவோ செய்யுங்கள், சில வினாடிகள் கேட்க அலாரம் ஒலியை சோதி என்பதை தட்டுங்கள். சோதனை சவாலை தொடங்காது, எதையும் பூட்டாது.")
    ),
    TourStep(
        "apps", tr("Allowed apps", "அனுமதிக்கப்பட்ட ஆப்கள்"),
        tr("Choose which apps still work while your phone is locked, like WhatsApp. Phone and Camera always work. This list can't be changed while a challenge is pending.",
            "போன் பூட்டியிருக்கும்போதும் WhatsApp போன்ற எந்த ஆப்கள் வேலை செய்ய வேண்டும் என்பதை தேர்ந்தெடுங்கள். போன், கேமரா எப்போதும் வேலை செய்யும். சவால் முடியாமல் காத்திருக்கும்போது இந்த பட்டியலை மாற்ற முடியாது.")
    ),
    TourStep(
        "permissions", tr("Permissions", "அனுமதிகள்"),
        tr("A green On means it is on. A red Off means it is off, and the alarm or app lock may not work. Tap that row to open the right screen.",
            "பச்சை நிற இயக்கத்தில் என்றால் இயக்கத்தில் உள்ளது. சிவப்பு நிற இயக்கத்தில் இல்லை என்றால் அலாரம் அல்லது ஆப் பூட்டு வேலை செய்யாமல் போகலாம். சரியான திரையை திறக்க அந்த வரிசையை தட்டுங்கள்.")
    ),
    TourStep(
        "history", tr("Streak history", "தொடர் சாதனை வரலாறு"),
        tr("Open a calendar of the days you finished and the days you missed.",
            "நீங்கள் முடித்த நாட்கள், தவறவிட்ட நாட்களின் நாட்காட்டியை திறந்து பாருங்கள்.")
    ),
    TourStep(
        "backup", tr("Data and backup", "தரவு மற்றும் காப்பு நகல்"),
        tr("Save your alarms, streak, history and allowed apps to a file. Restore works only on a fresh start (no recorded days yet), so a backup can never undo a missed day.",
            "அலாரங்கள், தொடர் சாதனை, வரலாறு, அனுமதிக்கப்பட்ட ஆப்களை ஒரு கோப்பாக சேமிக்கலாம். இன்னும் எந்த நாளும் பதிவாகாத புதிய தொடக்கத்தில் மட்டுமே மீட்டெடுக்க முடியும், அதனால் காப்பு நகலால் தவறிய நாளை அழிக்க முடியாது.")
    ),
    TourStep(
        "privacy", tr("Privacy", "தனியுரிமை"),
        tr("What the camera, the accessibility service and your data are used for. Nothing leaves your phone.",
            "கேமரா, அணுகல் வசதி சேவை, உங்கள் தகவல்கள் எதற்காக பயன்படுத்தப்படுகின்றன என்பது. எதுவும் உங்கள் போனை விட்டு வெளியே போகாது.")
    ),
    TourStep(
        "about", tr("About", "பற்றி"),
        tr("Shows the app version. Everything stays on your phone.",
            "ஆப் பதிப்பை காட்டும். எல்லாம் உங்கள் போனிலேயே இருக்கும்.")
    )
)
