package com.muthu.disciplinex.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muthu.disciplinex.data.UninstallGuard
import com.muthu.disciplinex.data.tr
import com.muthu.disciplinex.ui.theme.Divider
import com.muthu.disciplinex.ui.theme.OrangeEnd
import com.muthu.disciplinex.ui.theme.Surface
import com.muthu.disciplinex.ui.theme.TextMuted
import com.muthu.disciplinex.ui.theme.TextPrimary
import kotlinx.coroutines.delay

/** Shows the uninstall password. It keeps updating, and closes itself if a challenge starts. */
@Composable
fun UninstallPasswordDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            if (UninstallGuard.isProtected(context)) {
                onDismiss()
                break
            }
            delay(500)
        }
    }

    val code = UninstallGuard.pretty(UninstallGuard.currentCode(context, now))
    val secondsLeft = UninstallGuard.secondsLeft(now)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(tr("Password", "கடவுச்சொல்"), fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = code,
                    color = TextPrimary,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 4.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { secondsLeft / UninstallGuard.STEP_SECONDS.toFloat() },
                    color = OrangeEnd,
                    trackColor = Divider,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    tr("Changes in $secondsLeft s", "$secondsLeft வினாடியில் மாறும்"),
                    color = TextMuted,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    tr(
                        "Enter this when you try to uninstall DisciplineX. It is hidden while a challenge is pending or missed, so the app can't be removed to skip it.",
                        "DisciplineX-ஐ நிறுவல் நீக்க முயலும்போது இதை உள்ளிடுங்கள். சவால் முடிக்கப்படாமல் இருக்கும்போதோ தவறியிருக்கும்போதோ இது மறைக்கப்படும், அதனால் சவாலை தவிர்க்க ஆப்பை நீக்க முடியாது."
                    ),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("Close", "மூடு"), color = OrangeEnd, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
