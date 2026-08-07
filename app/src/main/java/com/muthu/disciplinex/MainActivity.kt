package com.muthu.disciplinex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.muthu.disciplinex.ui.navigation.DisciplineXNavGraph
import com.muthu.disciplinex.ui.theme.DisciplineXTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_START_CHALLENGE = "start_challenge"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startChallenge = intent?.getBooleanExtra(EXTRA_START_CHALLENGE, false) ?: false
        setContent {
            DisciplineXTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DisciplineXNavGraph(startChallenge = startChallenge)
                }
            }
        }
    }
}
