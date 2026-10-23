package com.muthu.disciplinex.ui.settings

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

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(tr("Settings", "அமைப்புகள்"), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = tr("Back", "பின்"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // ---- Theme ----
            SettingsCard(tr("Theme", "தீம்")) {
                ChoiceRow(
                    options = listOf(
                        AppSettings.THEME_SYSTEM to tr("System", "சிஸ்டம்"),
                        AppSettings.THEME_LIGHT to tr("Light", "ஒளி"),
                        AppSettings.THEME_DARK to tr("Dark", "இருள்")
                    ),
                    selected = AppSettings.themeMode,
                    onSelect = { AppSettings.setTheme(context, it) }
                )
            }

            // ---- Language ----
            SettingsCard(tr("Language", "மொழி")) {
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
            SettingsCard(tr("Alarm sound", "அலாரம் ஒலி")) {
                Text(ringtoneTitle, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
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
                    modifier = Modifier.fillMaxWidth()
                ) { Text(tr("Change ringtone", "ரிங்டோனை மாற்று"), color = OrangeEnd) }

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
                Button(
                    onClick = { if (AlarmTest.isPlaying) AlarmTest.stop() else AlarmTest.start(context) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeEnd, contentColor = Color.White)
                ) {
                    Text(
                        if (AlarmTest.isPlaying) tr("Stop test", "சோதனையை நிறுத்து")
                        else tr("Test alarm sound", "அலாரம் ஒலியை சோதி"),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    tr(
                        "Plays for a few seconds so you can check the volume. It doesn't start a challenge or lock anything.",
                        "ஒலி அளவை சரிபார்க்க சில வினாடிகள் ஒலிக்கும். சேலஞ்ச் தொடங்காது, எதையும் பூட்டாது."
                    ),
                    color = TextMuted,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            // ---- Allowed apps ----
            SettingsCard(tr("Apps that stay open while locked", "பூட்டியிருக்கும்போது திறந்திருக்கும் ஆப்கள்")) {
                Text(
                    tr(
                        "Phone and Camera are always open. Pick any other apps you still need, like WhatsApp.",
                        "போன், கேமரா எப்போதும் திறந்திருக்கும். WhatsApp போல உங்களுக்கு தேவையான மற்ற ஆப்களை தேர்ந்தெடுங்கள்."
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
                            "சேலஞ்ச் முடியும் வரை பூட்டப்பட்டுள்ளது, அதனால் பட்டியலை இப்போது மாற்ற முடியாது."
                        ),
                        color = TextMuted,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // ---- Permissions ----
            SettingsCard(tr("Permissions", "அனுமதிகள்")) {
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
                            "ஏதோ அணைந்துள்ளது. சரிசெய்யும் வரை அலாரம் அல்லது ஆப் லாக் சரியாக வேலை செய்யாமல் போகலாம்."
                        ),
                        color = Danger,
                        style = MaterialTheme.typography.labelMedium
                    )
                } else {
                    Text(
                        tr("Everything is on.", "எல்லாம் ஆன் ஆக உள்ளது."),
                        color = TextMuted,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // ---- Streak history ----
            SettingsCard(tr("Streak history", "ஸ்ட்ரீக் வரலாறு")) {
                Text(
                    tr(
                        "See which days you finished and which you missed.",
                        "எந்த நாட்கள் முடித்தீர்கள், எவை தவறின என்று பாருங்கள்."
                    ),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Open calendar", "காலெண்டரை திற"), color = OrangeEnd)
                }
            }

            // ---- About ----
            SettingsCard(tr("About", "பற்றி")) {
                Text(
                    "DisciplineX" + if (version.isNotBlank()) "  v$version" else "",
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    tr(
                        "Wake up, finish your challenge, earn your phone back. No account needed, and your data stays on this phone.",
                        "எழுந்திருங்கள், சேலஞ்சை முடியுங்கள், போனை திரும்பப் பெறுங்கள். கணக்கு தேவையில்லை, உங்கள் தரவு இந்த போனிலேயே இருக்கும்."
                    ),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
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
