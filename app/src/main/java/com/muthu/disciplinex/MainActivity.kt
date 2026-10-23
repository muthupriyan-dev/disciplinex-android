package com.muthu.disciplinex

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import com.muthu.disciplinex.data.AppSettings
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.muthu.disciplinex.ui.navigation.DisciplineXNavGraph
import com.muthu.disciplinex.ui.theme.DisciplineXTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_START_CHALLENGE = "start_challenge"
    }

    // Bumped every time the activity is re-launched with "open the challenge"
    // while it is already running (alarm dismiss, or the app-lock redirect).
    // MainActivity is singleTop, so the running instance — and any rep
    // progress on the challenge screen — is kept instead of being recreated.
    private var challengeRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.load(this)
        AppSettings.systemDark =
            (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val startChallenge = intent?.getBooleanExtra(EXTRA_START_CHALLENGE, false) ?: false
        setContent {
            DisciplineXTheme {
                // Status / navigation bar icons follow the app's theme, not just the phone's.
                val dark = AppSettings.isDark
                DisposableEffect(dark) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(
                            android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT
                        ) { dark },
                        navigationBarStyle = SystemBarStyle.auto(
                            android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT
                        ) { dark }
                    )
                    onDispose { }
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    DisciplineXNavGraph(
                        startChallenge = startChallenge,
                        challengeRequest = challengeRequest
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_START_CHALLENGE, false)) {
            challengeRequest++
        }
    }
}
