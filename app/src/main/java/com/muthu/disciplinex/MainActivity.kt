package com.muthu.disciplinex

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        enableEdgeToEdge()
        val startChallenge = intent?.getBooleanExtra(EXTRA_START_CHALLENGE, false) ?: false
        setContent {
            DisciplineXTheme {
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
