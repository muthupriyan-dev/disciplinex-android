package com.muthu.disciplinex.ui.settings

import androidx.compose.foundation.layout.Box
import com.muthu.disciplinex.ui.components.tourTarget
import com.muthu.disciplinex.ui.components.rememberTourState
import com.muthu.disciplinex.ui.components.TourStep
import com.muthu.disciplinex.ui.components.TourOverlay
import com.muthu.disciplinex.ui.components.GuideButton
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.muthu.disciplinex.alarm.AlarmTest
import com.muthu.disciplinex.data.AppSettings
import com.muthu.disciplinex.data.ChallengeSession
import com.muthu.disciplinex.data.PermissionChecks
import com.muthu.disciplinex.data.tr
import com.muthu.disciplinex.ui.theme.Background
import com.muthu.disciplinex.ui.theme.OrangeEnd
import com.muthu.disciplinex.ui.theme.Success
import com.muthu.disciplinex.ui.theme.Danger
import com.muthu.disciplinex.ui.theme.Surface
import com.muthu.disciplinex.ui.theme.TextMuted

/**
 * App settings: theme, language, alarm sound and vibration, allowed apps,
 * permission status, streak history and about. Theme, language and sound can
 * be changed any time; the allowed-apps list is read-only while the phone is
 * locked.
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
                .padding(horizontal = 20.dp)
                .fillMaxSize()
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // ---- Theme ----
            SettingsCard(tr("Theme", "தோற்றம்"), Modifier.tourTarget(tour, "theme")) {
                ChoiceRow(
                    options = listOf(
                        AppSettings.THEME_SYSTEM to tr("System", "போன் படி"),
                        AppSettings.THEME_LIGHT to tr("Light", "ஒளி"),
                        AppSettings.THEME_DARK to tr("Dark", "இருள்")
                    ),
                    selected = AppSettings.themeMode,
                    onSelect = { AppSettings.setTheme(context, it) }
                )
            }

            // ---- Language ----
            SettingsCard(tr("Language", "மொழி"), Modifier.tourTarget(tour, "language")) {
                ChoiceRow(
                    options = listOf(
                        AppSettings.LANG_EN to "English",
                        AppSettings.LANG_TA to "தமிழ்"
                    ),
                    selected = AppSettings.language,
                    onSelect = { AppSettings.setLanguage(context, it) }
                )
            }

            // ---- Alarm sound ----
            SettingsCard(tr("Alarm sound", "அலாரம் ஒலி"), Modifier.tourTarget(tour, "sound")) {
                Text(ringtoneTitle, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
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
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeEnd, contentColor = Color.White)
                ) { Text(tr("Change ringtone", "ஒலியை மாற்று"), fontWeight = FontWeight.SemiBold) }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        tr("Vibration", "அதிர்வு"),
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Switch(
                        checked = vibrate,
                        onCheckedChange = {
                            vibrate = it
                            AppSettings.setVibrate(context, it)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = OrangeEnd)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { if (AlarmTest.isPlaying) AlarmTest.stop() else AlarmTest.start(context) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, OrangeEnd)
                ) {
                    Text(
                        if (AlarmTest.isPlaying) tr("Stop test", "சோதனையை நிறுத்து")
                        else tr("Test alarm sound", "அலாரம் ஒலியை சோதி"),
                        color = OrangeEnd,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    tr(
                        "Plays for a few seconds so you can check the volume. It doesn't start a challenge or lock anything.",
                        "ஒலி அளவை சரிபார்க்க சில வினாடிகள் ஒலிக்கும். இது சவாலை தொடங்காது, எதையும் பூட்டாது."
                    ),
                    color = TextMuted,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            // ---- Allowed apps ----
            SettingsCard(tr("Apps that stay open while locked", "பூட்டியிருக்கும்போது திறந்திருக்கும் ஆப்கள்"), Modifier.tourTarget(tour, "apps")) {
                Text(
                    tr(
                        "Phone and Camera are always open. Pick any other apps you still need, like WhatsApp.",
                        "போன், கேமரா எப்போதும் திறந்திருக்கும். WhatsApp போல, உங்களுக்கு இன்னும் தேவையான மற்ற ஆப்களை தேர்ந்தெடுங்கள்."
                    ),
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
                ) { Text(tr("Choose allowed apps", "அனுமதிக்கும் ஆப்களை தேர்ந்தெடு"), fontWeight = FontWeight.SemiBold) }
                if (locked) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        tr(
                            "Locked until your challenge ends, so the list can't be changed right now.",
                            "சவால் முடியும் வரை பூட்டப்பட்டுள்ளது, அதனால் இந்த பட்டியலை இப்போது மாற்ற முடியாது."
                        ),
                        color = TextMuted,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // ---- Permissions ----
            SettingsCard(tr("Permissions", "அனுமதிகள்"), Modifier.tourTarget(tour, "permissions")) {
                permissions.forEach { (kind, granted) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (granted) "\u2713" else "\u2715",
                            color = if (granted) Success else Danger,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 6.dp))
                        Text(PermissionChecks.title(kind), modifier = Modifier.weight(1f))
                        if (!granted) {
                            TextButton(onClick = {
                                try {
                                    context.startActivity(PermissionChecks.fixIntent(context, kind))
                                } catch (e: Exception) {
                                    // No such settings screen on this phone: open the app's own page.
                                    context.startActivity(
                                        Intent(
                                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }
                            }) { Text(tr("Fix", "சரிசெய்"), color = OrangeEnd, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                if (permissions.any { !it.second }) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        tr(
                            "Something is off. The alarm or the app lock may not work properly until it is fixed.",
                            "ஏதோ ஒன்று இயக்கத்தில் இல்லை. சரிசெய்யும் வரை அலாரம் அல்லது ஆப் பூட்டு சரியாக வேலை செய்யாமல் போகலாம்."
                        ),
                        color = Danger,
                        style = MaterialTheme.typography.labelMedium
                    )
                } else {
                    Text(
                        tr("Everything is on.", "எல்லாம் இயக்கத்தில் உள்ளது."),
                        color = TextMuted,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // ---- Streak history ----
            SettingsCard(tr("Streak history", "தொடர் சாதனை வரலாறு"), Modifier.tourTarget(tour, "history")) {
                Text(
                    tr(
                        "See which days you finished and which you missed.",
                        "எந்த நாட்களில் முடித்தீர்கள், எந்த நாட்களில் தவறவிட்டீர்கள் என்று பாருங்கள்."
                    ),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onOpenHistory,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeEnd, contentColor = Color.White)
                ) { Text(tr("Open calendar", "நாட்காட்டியை திற"), fontWeight = FontWeight.SemiBold) }
            }

            // ---- Data and backup / Privacy ----
            BackupCard(Modifier.tourTarget(tour, "backup"))

            PrivacyCard(Modifier.tourTarget(tour, "privacy"))

            // ---- About ----
            SettingsCard(tr("About", "பற்றி"), Modifier.tourTarget(tour, "about")) {
                Text(
                    "DisciplineX" + if (version.isNotBlank()) "  v$version" else "",
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    tr(
                        "Wake up, finish your challenge, earn your phone back. No account needed, and your data stays on this phone.",
                        "எழுந்திருங்கள், சவாலை முடியுங்கள், உங்கள் போனை திரும்ப பெறுங்கள். கணக்கு தேவையில்லை, உங்கள் தகவல்கள் இந்த போனிலேயே இருக்கும்."
                    ),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
        if (showTour) {
            TourOverlay(steps, tour, onFinish = { showTour = false }, scrollState = scroll)
        }
    }
}

@Composable
private fun SettingsCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .padding(18.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = OrangeEnd,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
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
        "sound", tr("Alarm sound", "அலாரம் ஒலி"),
        tr("Choose the ringtone, turn vibration on or off, and press Test to hear it for a few seconds. Testing never starts a challenge or locks anything.",
            "அலாரம் ஒலியை தேர்ந்தெடுங்கள், அதிர்வை இயக்கவோ அணைக்கவோ செய்யுங்கள், சில வினாடிகள் கேட்க சோதி என்பதை அழுத்துங்கள். சோதனை சவாலை தொடங்காது, எதையும் பூட்டாது.")
    ),
    TourStep(
        "apps", tr("Allowed apps", "அனுமதிக்கப்பட்ட ஆப்கள்"),
        tr("Choose which apps still work while your phone is locked, like WhatsApp. Phone and Camera always work. This list can't be changed while a challenge is pending.",
            "போன் பூட்டியிருக்கும்போதும் WhatsApp போன்ற எந்த ஆப்கள் வேலை செய்ய வேண்டும் என்பதை தேர்ந்தெடுங்கள். போன், கேமரா எப்போதும் வேலை செய்யும். சவால் முடியாமல் காத்திருக்கும்போது இந்த பட்டியலை மாற்ற முடியாது.")
    ),
    TourStep(
        "permissions", tr("Permissions", "அனுமதிகள்"),
        tr("A tick means it is on. A cross means it is off, and the alarm or app lock may not work. Tap Fix to open the right screen.",
            "சரிக்குறி என்றால் இயக்கத்தில் உள்ளது. தவறுக்குறி என்றால் இயக்கத்தில் இல்லை, அலாரம் அல்லது ஆப் பூட்டு வேலை செய்யாமல் போகலாம். சரியான திரையை திறக்க சரிசெய் என்பதை தட்டுங்கள்.")
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
