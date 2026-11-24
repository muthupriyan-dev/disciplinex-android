package com.muthu.disciplinex.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muthu.disciplinex.data.BackupManager
import com.muthu.disciplinex.data.tr
import com.muthu.disciplinex.ui.theme.Surface
import com.muthu.disciplinex.ui.theme.TextMuted
import com.muthu.disciplinex.ui.theme.TextPrimary
import java.time.LocalDate

/**
 * Settings > Data & Privacy: save / restore a local JSON file.
 * [groupModifier] is applied to the rounded group (used to tag it for the guide).
 * Backup and restore logic is unchanged; only the presentation is new.
 */
@Composable
fun BackupSection(groupModifier: Modifier = Modifier) {
    val context = LocalContext.current
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val ok = BackupManager.export(context, uri)
            Toast.makeText(
                context,
                if (ok) tr("Backup saved", "காப்பு நகல் சேமிக்கப்பட்டது")
                else tr("Could not save the backup", "காப்பு நகலை சேமிக்க முடியவில்லை"),
                Toast.LENGTH_LONG
            ).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pendingRestore = uri }

    Column {
        SettingsGroup(groupModifier) {
            SettingsRow(
                icon = Icons.Filled.Backup,
                title = tr("Save backup", "காப்பு நகலை சேமி"),
                subtitle = tr(
                    "Save your alarms, streak, history and allowed apps to a file on your phone.",
                    "உங்கள் அலாரங்கள், தொடர் சாதனை, வரலாறு, அனுமதிக்கப்பட்ட ஆப்களை போனில் ஒரு கோப்பாக சேமியுங்கள்."
                ),
                onClick = { exportLauncher.launch("disciplinex-backup-${LocalDate.now()}.json") },
                showChevron = true
            )
            GroupDivider()
            SettingsRow(
                icon = Icons.Filled.Restore,
                title = tr("Restore from backup", "காப்பு நகலிலிருந்து மீட்டெடு"),
                subtitle = tr(
                    "Works only on a fresh start, before any day is recorded.",
                    "எந்த நாளும் பதிவாகும் முன், புதிய தொடக்கத்தில் மட்டுமே வேலை செய்யும்."
                ),
                onClick = {
                    val why = BackupManager.restoreBlockReason(context)
                    if (why != null) {
                        Toast.makeText(context, restoreMessage(why), Toast.LENGTH_LONG).show()
                    } else {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
                    }
                },
                showChevron = true
            )
        }
        GroupFooter(
            tr(
                "The file stays wherever you put it; nothing is uploaded. " +
                    "Restore only works before any day is recorded (for example on a new phone), so a backup can never undo a missed day. " +
                    "It also cannot run while a challenge is active or an alarm is about to ring.",
                "அந்த கோப்பு நீங்கள் வைக்கும் இடத்திலேயே இருக்கும்; எதுவும் இணையத்தில் பதிவேற்றப்படாது. " +
                    "எந்த நாளும் பதிவாகும் முன் மட்டுமே மீட்டெடுக்க முடியும் (எ.கா. புதிய போனில்), அதனால் காப்பு நகலால் தவறிய நாளை அழிக்க முடியாது. " +
                    "சவால் நடந்துகொண்டிருக்கும்போதோ, அலாரம் அடிக்கப்போகும்போதோ இதை செய்ய முடியாது."
            )
        )
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            containerColor = Surface,
            titleContentColor = TextPrimary,
            textContentColor = TextPrimary,
            title = { Text(tr("Restore this backup?", "இந்த காப்பு நகலை மீட்டெடுக்கவா?")) },
            text = {
                Text(
                    tr(
                        "Your current alarms and allowed apps will be replaced by the ones in the file.",
                        "தற்போதைய அலாரங்களும் அனுமதிக்கப்பட்ட ஆப்களும் கோப்பில் உள்ளவற்றால் மாற்றப்படும்."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val result = BackupManager.restore(context, uri)
                    pendingRestore = null
                    val message = restoreMessage(result)
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }) { Text(tr("Restore", "மீட்டெடு"), color = AccentText, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) {
                    Text(tr("Cancel", "ரத்து"), color = TextMuted)
                }
            }
        )
    }
}

/**
 * Settings > Data & Privacy > Privacy: one row that opens the same plain
 * statements as before in a bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyRowGroup(groupModifier: Modifier = Modifier) {
    var showSheet by remember { mutableStateOf(false) }

    SettingsGroup(groupModifier) {
        SettingsRow(
            icon = Icons.Filled.PrivacyTip,
            title = tr("Privacy", "தனியுரிமை"),
            subtitle = tr(
                "How the camera, app lock and your data are used.",
                "கேமரா, ஆப் பூட்டு, உங்கள் தகவல்கள் எப்படி பயன்படுகின்றன என்பது."
            ),
            onClick = { showSheet = true },
            showChevron = true
        )
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
            ) {
                Text(
                    tr("Privacy", "தனியுரிமை"),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(16.dp))
                PrivacyItem(
                    icon = Icons.Filled.CameraAlt,
                    heading = tr("Camera", "கேமரா"),
                    body = tr(
                        "Exercise counting runs on your phone. Camera images are not saved and not sent anywhere.",
                        "பயிற்சிகளை எண்ணுவது உங்கள் போனிலேயே நடக்கும். கேமரா படங்கள் சேமிக்கப்படாது, எங்கும் அனுப்பப்படாது."
                    )
                )
                PrivacyItem(
                    icon = Icons.Filled.Accessibility,
                    heading = tr("Accessibility service", "அணுகல் வசதி சேவை"),
                    body = tr(
                        "Used only to see which app is open so it can be blocked during a challenge. Apps you choose as allowed stay open. Nothing it sees is stored or sent.",
                        "சவால் நேரத்தில் எந்த ஆப் திறந்துள்ளது என்பதை பார்த்து, அதை தடுக்க மட்டுமே பயன்படும். நீங்கள் அனுமதித்த ஆப்கள் திறந்தே இருக்கும். அது பார்ப்பது எதுவும் சேமிக்கப்படாது, அனுப்பப்படாது."
                    )
                )
                PrivacyItem(
                    icon = Icons.Filled.PhoneAndroid,
                    heading = tr("Your data", "உங்கள் தரவு"),
                    body = tr(
                        "Your alarms, streak and history are stored only on this phone. DisciplineX does not ask for the Internet permission and has no account or sign-in.",
                        "உங்கள் அலாரங்கள், தொடர் சாதனை, வரலாறு இந்த போனில் மட்டுமே சேமிக்கப்படும். DisciplineX இணைய அனுமதியை கேட்காது, கணக்கு அல்லது உள்நுழைவு இல்லை."
                    )
                )
                PrivacyItem(
                    icon = Icons.Filled.Info,
                    heading = tr("About the app lock", "ஆப் பூட்டு பற்றி"),
                    body = tr(
                        "The app lock uses an accessibility service, which is not the same as device-level security. A determined person can still get around it (for example by switching the service off in system settings).",
                        "ஆப் பூட்டு அணுகல் வசதி சேவையை பயன்படுத்துகிறது; இது போன் அளவிலான பாதுகாப்புக்கு சமமானது அல்ல. உறுதியாக முயன்றால் ஒருவர் இதை தவிர்க்க முடியும் (எ.கா. சிஸ்டம் அமைப்புகளில் அந்த சேவையை அணைத்து)."
                    ),
                    last = true
                )
            }
        }
    }
}

@Composable
private fun PrivacyItem(icon: ImageVector, heading: String, body: String, last: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (last) 0.dp else 18.dp),
        verticalAlignment = Alignment.Top
    ) {
        IconBadge(icon)
        Spacer(modifier = Modifier.padding(horizontal = 7.dp))
        Column {
            Text(
                heading,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
    }
}

private fun restoreMessage(result: BackupManager.RestoreResult): String = when (result) {
    BackupManager.RestoreResult.OK ->
        tr("Backup restored. Reopen the app to see everything.", "காப்பு நகல் மீட்டெடுக்கப்பட்டது. அனைத்தையும் பார்க்க ஆப்பை மீண்டும் திறந்து பாருங்கள்.")
    BackupManager.RestoreResult.INVALID_FILE ->
        tr("That file is not a valid DisciplineX backup.", "இது சரியான DisciplineX காப்பு நகல் கோப்பு அல்ல.")
    BackupManager.RestoreResult.BLOCKED_HISTORY ->
        tr(
            "This phone already has recorded days, so restore is blocked. Restore only works on a fresh install (for example a new phone), so a backup can never undo a missed day. Your backup file itself is fine.",
            "இந்த போனில் ஏற்கனவே நாட்கள் பதிவாகியுள்ளதால் இப்போது மீட்டெடுக்க முடியாது. புதிய நிறுவலில் (எ.கா. புதிய போனில்) மட்டுமே மீட்டெடுக்க முடியும், அதனால் காப்பு நகலால் தவறிய நாளை அழிக்க முடியாது. உங்கள் காப்பு நகல் கோப்பு சரியாகவே உள்ளது."
        )
    BackupManager.RestoreResult.BLOCKED_ACTIVE ->
        tr("A challenge is active right now. Finish it first, then try again.", "இப்போது ஒரு சவால் நடந்துகொண்டிருக்கிறது. முதலில் அதை முடித்துவிட்டு, மீண்டும் முயலுங்கள்.")
    BackupManager.RestoreResult.BLOCKED_ALARM_SOON ->
        tr("An alarm will ring within 2 hours, so restore is blocked. Try again later.", "2 மணி நேரத்துக்குள் ஒரு அலாரம் அடிக்கும், அதனால் இப்போது மீட்டெடுக்க முடியாது. பிறகு மீண்டும் முயலுங்கள்.")
    BackupManager.RestoreResult.IO_ERROR ->
        tr("Could not read the file.", "கோப்பை படிக்க முடியவில்லை.")
}
