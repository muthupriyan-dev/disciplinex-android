package com.muthu.disciplinex.lock

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muthu.disciplinex.data.UninstallGuard
import com.muthu.disciplinex.data.tr
import com.muthu.disciplinex.ui.components.PrimaryPillButton
import com.muthu.disciplinex.ui.theme.Background
import com.muthu.disciplinex.ui.theme.Danger
import com.muthu.disciplinex.ui.theme.DisciplineXTheme
import com.muthu.disciplinex.ui.theme.Surface
import com.muthu.disciplinex.ui.theme.TextMuted

/**
 * Opened by the app-lock when someone tries to uninstall DisciplineX.
 *  - Challenge pending / missed: "uninstall is blocked", nothing to type.
 *  - Otherwise: asks for the password that is shown in Settings. A correct password opens
 *    the real uninstall dialog; anything else sends the user home.
 */
class UninstallPasswordActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UninstallGuard.promptShowing = true

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = cancel()
        })

        setContent {
            DisciplineXTheme {
                UninstallPasswordScreen(
                    onCorrect = { openUninstallDialog() },
                    onCancel = { cancel() }
                )
            }
        }
    }

    override fun onDestroy() {
        UninstallGuard.promptShowing = false
        super.onDestroy()
    }

    private fun openUninstallDialog() {
        UninstallGuard.grantUninstall()
        try {
            startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Log.w("UninstallPassword", "Could not open the uninstall dialog", e)
        }
        finish()
    }

    private fun cancel() {
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Log.w("UninstallPassword", "Could not go home", e)
        }
        finish()
    }
}

@Composable
private fun UninstallPasswordScreen(onCorrect: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val blocked = remember { UninstallGuard.isProtected(context) }
    var code by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    Scaffold(containerColor = Background) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Surface)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (blocked) {
                    Text(
                        tr("Uninstall is blocked", "நிறுவல் நீக்கம் தடுக்கப்பட்டுள்ளது"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        tr(
                            "Finish your challenge first. While a challenge is pending or missed, DisciplineX can't be uninstalled.",
                            "முதலில் சவாலை முடியுங்கள். சவால் நிலுவையில் இருக்கும்போதோ தவறியிருக்கும்போதோ DisciplineX-ஐ நிறுவல் நீக்க முடியாது."
                        ),
                        color = TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    PrimaryPillButton(text = tr("OK", "சரி"), onClick = onCancel)
                } else {
                    Text(
                        tr("Enter the password to uninstall", "நிறுவல் நீக்க கடவுச்சொல்லை உள்ளிடுங்கள்"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        tr(
                            "You can see it in DisciplineX > Settings > Password. It changes every 30 seconds.",
                            "DisciplineX > அமைப்புகள் > கடவுச்சொல் என்பதில் பார்க்கலாம். இது 30 வினாடிக்கு ஒருமுறை மாறும்."
                        ),
                        color = TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = {
                            if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                code = it
                                wrong = false
                            }
                        },
                        singleLine = true,
                        isError = wrong,
                        label = { Text(tr("6-digit password", "6 இலக்க கடவுச்சொல்")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (wrong) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            tr("Wrong password. Try the one shown now.", "தவறான கடவுச்சொல். இப்போது காட்டப்படுவதை உள்ளிடுங்கள்."),
                            color = Danger,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    PrimaryPillButton(
                        text = tr("Confirm", "உறுதிசெய்"),
                        enabled = code.length == 6,
                        onClick = {
                            if (UninstallGuard.verify(context, code)) {
                                onCorrect()
                            } else {
                                wrong = true
                                code = ""
                            }
                        }
                    )
                    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text(tr("Cancel", "ரத்து செய்"), color = TextMuted)
                    }
                }
            }
        }
    }
}
