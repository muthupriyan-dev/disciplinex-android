package com.muthu.disciplinex.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muthu.disciplinex.data.BackupManager
import com.muthu.disciplinex.data.tr
import com.muthu.disciplinex.ui.theme.OrangeEnd
import com.muthu.disciplinex.ui.theme.Surface
import com.muthu.disciplinex.ui.theme.TextMuted
import java.time.LocalDate

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
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

/** Settings > Data and backup: save / restore a local JSON file. */
@Composable
fun BackupCard() {
    val context = LocalContext.current
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val ok = BackupManager.export(context, uri)
            Toast.makeText(
                context,
                if (ok) tr("Backup saved", "பேக்கப் சேமிக்கப்பட்டது")
                else tr("Could not save the backup", "பேக்கப்பை சேமிக்க முடியவில்லை"),
                Toast.LENGTH_LONG
            ).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pendingRestore = uri }

    InfoCard(tr("Data and backup", "தரவு மற்றும் பேக்கப்")) {
        Text(
            tr(
                "Save your alarms, streak, history and allowed apps to a file on your phone. The file stays wherever you put it; nothing is uploaded.",
                "உங்கள் அலாரங்கள், ஸ்ட்ரீக், வரலாறு, அனுமதிக்கப்பட்ட ஆப்களை போனில் ஒரு கோப்பாக சேமியுங்கள். எதுவும் இணையத்தில் பதிவேற்றப்படாது."
            ),
            color = TextMuted,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = { exportLauncher.launch("disciplinex-backup-${LocalDate.now()}.json") },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = OrangeEnd, contentColor = Color.White)
        ) { Text(tr("Save backup", "பேக்கப் சேமி"), fontWeight = FontWeight.SemiBold) }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp)
        ) { Text(tr("Restore from backup", "பேக்கப்பிலிருந்து மீட்டெடு"), fontWeight = FontWeight.SemiBold) }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            tr(
                "Restore only works before any day is recorded (for example on a new phone), so a backup can never undo a missed day. It also cannot run while a challenge is active or an alarm is about to ring.",
                "எந்த நாளும் பதிவாகும் முன் மட்டுமே மீட்டெடுக்க முடியும் (எ.கா. புதிய போன்), அதனால் பேக்கப் தவறிய நாளை அழிக்க முடியாது. சேலஞ்ச் நடக்கும்போதோ அலாரம் அடிக்கப்போகும்போதோ முடியாது."
            ),
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(tr("Restore this backup?", "இந்த பேக்கப்பை மீட்டெடுக்கவா?")) },
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
                    val message = when (result) {
                        BackupManager.RestoreResult.OK ->
                            tr("Backup restored. Reopen the app to see everything.", "பேக்கப் மீட்டெடுக்கப்பட்டது. அனைத்தையும் காண ஆப்பை மீண்டும் திறக்கவும்.")
                        BackupManager.RestoreResult.INVALID_FILE ->
                            tr("That file is not a valid DisciplineX backup.", "இது சரியான DisciplineX பேக்கப் கோப்பு அல்ல.")
                        BackupManager.RestoreResult.NOT_ALLOWED ->
                            tr("Restore is not possible now (history exists, a challenge is active or an alarm is about to ring).", "இப்போது மீட்டெடுக்க முடியாது (வரலாறு உள்ளது, சேலஞ்ச் நடக்கிறது அல்லது அலாரம் அடிக்கப்போகிறது).")
                        BackupManager.RestoreResult.IO_ERROR ->
                            tr("Could not read the file.", "கோப்பை படிக்க முடியவில்லை.")
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }) { Text(tr("Restore", "மீட்டெடு"), color = OrangeEnd, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text(tr("Cancel", "ரத்து")) }
            }
        )
    }
}

/** Settings > Privacy: plain statements that match what the app actually does. */
@Composable
fun PrivacyCard() {
    InfoCard(tr("Privacy", "தனியுரிமை")) {
        val style = MaterialTheme.typography.bodyMedium
        Text(
            tr(
                "Camera: exercise counting runs on your phone. Camera images are not saved and not sent anywhere.",
                "கேமரா: உடற்பயிற்சி எண்ணுதல் உங்கள் போனிலேயே நடக்கும். கேமரா படங்கள் சேமிக்கப்படாது, எங்கும் அனுப்பப்படாது."
            ),
            color = TextMuted, style = style
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            tr(
                "Accessibility service: used only to see which app is open so it can be blocked during a challenge. Apps you choose as allowed stay open. Nothing it sees is stored or sent.",
                "அக்சசிபிலிட்டி சேவை: சேலஞ்ச் நேரத்தில் எந்த ஆப் திறந்துள்ளது என்று பார்த்து தடுக்க மட்டுமே. நீங்கள் அனுமதித்த ஆப்கள் திறந்தே இருக்கும். அது பார்ப்பது எதுவும் சேமிக்கப்படாது, அனுப்பப்படாது."
            ),
            color = TextMuted, style = style
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            tr(
                "Your alarms, streak and history are stored only on this phone. DisciplineX does not ask for the Internet permission and has no account or sign-in.",
                "உங்கள் அலாரங்கள், ஸ்ட்ரீக், வரலாறு இந்த போனில் மட்டுமே சேமிக்கப்படும். DisciplineX இணைய அனுமதி கேட்காது, கணக்கு அல்லது உள்நுழைவு இல்லை."
            ),
            color = TextMuted, style = style
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            tr(
                "The app lock uses an accessibility service, which is not the same as device-level security. A determined person can still get around it (for example by switching the service off in system settings).",
                "ஆப் லாக் அக்சசிபிலிட்டி சேவையை பயன்படுத்துகிறது; இது போன் அளவிலான பாதுகாப்புக்கு சமமல்ல. உறுதியாக முயன்றால் (எ.கா. சிஸ்டம் அமைப்புகளில் சேவையை அணைத்து) தவிர்க்க முடியும்."
            ),
            color = TextMuted, style = style
        )
    }
}
