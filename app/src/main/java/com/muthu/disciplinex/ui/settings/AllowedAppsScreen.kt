package com.muthu.disciplinex.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muthu.disciplinex.data.UserPrefs
import com.muthu.disciplinex.ui.theme.OrangeEnd
import com.muthu.disciplinex.ui.theme.Surface
import com.muthu.disciplinex.ui.theme.TextMuted

private data class AppEntry(val label: String, val packageName: String)

/** Every app with a launcher icon, except DisciplineX itself, sorted by name. */
private fun loadLaunchableApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION")
    val infos = pm.queryIntentActivities(intent, 0)
    return infos
        .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

/**
 * Pick which apps stay usable while the app lock is on. Phone, Camera,
 * keyboards, the home screen and system screens are always allowed and are
 * not listed here. Read-only while a lock is active.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllowedAppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val locked = remember { UserPrefs.isLockActive(context) }
    val apps = remember { loadLaunchableApps(context) }
    var allowed by remember { mutableStateOf(UserPrefs.getAllowedApps(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Allowed apps", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Ticked apps stay usable while your apps are locked. Phone, Camera, keyboards and system screens are always allowed.",
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
            if (locked) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Locked until you finish today's challenge.",
                    color = OrangeEnd,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(apps, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !locked) {
                                val updated = if (app.packageName in allowed) {
                                    allowed - app.packageName
                                } else {
                                    allowed + app.packageName
                                }
                                allowed = updated
                                UserPrefs.setAllowedApps(context, updated)
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                app.packageName,
                                color = TextMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Checkbox(
                            checked = app.packageName in allowed,
                            onCheckedChange = null,
                            enabled = !locked
                        )
                    }
                }
            }
        }
    }
}
