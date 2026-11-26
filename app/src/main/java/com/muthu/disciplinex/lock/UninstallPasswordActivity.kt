package com.muthu.disciplinex.lock

import android.content.Context
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

    private var purpose = UninstallGuard.Purpose.UNINSTALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        purpose = readPurpose(intent)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = cancel()
        })

        setContent {
            DisciplineXTheme {
                UninstallPasswordScreen(
                    purpose = purpose,
                    onCorrect = { onPasswordCorrect() },
                    onCancel = { cancel() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        UninstallGuard.promptShowing = true
    }

    /** Not showing any more (Home pressed, screen off...): the uninstall dialog is guarded again. */
    override fun onPause() {
        UninstallGuard.promptShowing = false
        super.onPause()
    }

    /**
     * Leaving this screen cancels it. Without this the screen would stay alive in the
     * background after Home was pressed and a later uninstall attempt could slip through.
     */
    override fun onStop() {
        if (!isFinishing) finish()
        super.onStop()
    }

    override fun onDestroy() {
        UninstallGuard.promptShowing = false
        super.onDestroy()
    }

    private fun onPasswordCorrect() {
        UninstallGuard.grantUninstall()
        when (purpose) {
            UninstallGuard.Purpose.UNINSTALL -> {
                // The device admin must be off or Android refuses to uninstall.
                UninstallGuard.removeOwnAdmin(this)
                try {
                    startActivity(
                        Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: Exception) {
                    Log.w("UninstallPassword", "Could not open the uninstall dialog", e)
                }
            }
            UninstallGuard.Purpose.DEACTIVATE -> UninstallGuard.removeOwnAdmin(this)
            UninstallGuard.Purpose.PROTECT -> Unit // the grant lets the user repeat what they were doing
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

    companion object {
        private const val EXTRA_PURPOSE = "purpose"

        fun intent(context: Context, purpose: UninstallGuard.Purpose): Intent =
            Intent(context, UninstallPasswordActivity::class.java)
                .putExtra(EXTRA_PURPOSE, purpose.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        private fun readPurpose(intent: Intent?): UninstallGuard.Purpose = try {
            UninstallGuard.Purpose.valueOf(intent?.getStringExtra(EXTRA_PURPOSE) ?: "UNINSTALL")
        } catch (e: Exception) {
            UninstallGuard.Purpose.UNINSTALL
        }
    }
}

@Composable
private fun UninstallPasswordScreen(
    purpose: UninstallGuard.Purpose,
    onCorrect: () -> Unit,
    onCancel: () -> Unit
) {
    val forUninstall = purpose == UninstallGuard.Purpose.UNINSTALL
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
                        if (forUninstall) tr("Uninstall is blocked", "நிறுவல் நீக்கம் தடுக்கப்பட்டுள்ளது")
                        else tr("This is blocked", "இது தடுக்கப்பட்டுள்ளது"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (forUninstall) tr(
                            "Finish your challenge first. While a challenge is pending or missed, DisciplineX can't be uninstalled.",
                            "முதலில் சவாலை முடியுங்கள். சவால் நிலுவையில் இருக்கும்போதோ தவறியிருக்கும்போதோ DisciplineX-ஐ நிறுவல் நீக்க முடியாது."
                        ) else tr(
                            "Finish your challenge first. While a challenge is pending or missed, DisciplineX's protection can't be switched off.",
                            "முதலில் சவாலை முடியுங்கள். சவால் நிலுவையில் இருக்கும்போதோ தவறியிருக்கும்போதோ DisciplineX பாதுகாப்பை அணைக்க முடியாது."
                        ),
                        color = TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    PrimaryPillButton(text = tr("OK", "சரி"), onClick = onCancel)
                } else {
                    Text(
                        if (forUninstall) tr("Enter the password to uninstall", "நிறுவல் நீக்க கடவுச்சொல்லை உள்ளிடுங்கள்")
                        else tr("Enter the password to continue", "தொடர கடவுச்சொல்லை உள்ளிடுங்கள்"),
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