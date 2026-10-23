package com.muthu.disciplinex.ui.settings

import com.muthu.disciplinex.data.tr
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
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
import com.muthu.disciplinex.data.ChallengeSession
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

/** Drawable -> small bitmap (96 px is plenty for a 40 dp icon). */
private fun Drawable.toIconBitmap(sizePx: Int = 96): ImageBitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, sizePx, sizePx)
    draw(canvas)
    return bitmap.asImageBitmap()
}

/** Icons are decoded off the main thread, once per app, and reused while scrolling. */
private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

@Composable
private fun AppIcon(packageName: String, size: Dp) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(iconCache[packageName], packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                try {
                    context.packageManager.getApplicationIcon(packageName).toIconBitmap()
                        .also { iconCache[packageName] = it }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    val shape = RoundedCornerShape(10.dp)
    val bmp = icon
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = null, modifier = Modifier.size(size).clip(shape))
    } else {
        Spacer(modifier = Modifier.size(size))
    }
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
    val locked = remember { ChallengeSession.isLockActive(context) }
    // The app list is read on a background thread, so this screen opens instantly
    // (reading every app's name on the main thread is what made the button feel slow).
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.Default) { loadLaunchableApps(context) }
    }
    var allowed by remember { mutableStateOf(UserPrefs.getAllowedApps(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Allowed apps", "அனுமதிக்கப்பட்ட ஆப்கள்"), fontWeight = FontWeight.Bold) },
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
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                tr("Ticked apps stay usable while your apps are locked. Phone, Camera, keyboards and system screens are always allowed.", "டிக் செய்த ஆப்கள் பூட்டியிருக்கும்போதும் பயன்படுத்தலாம். போன், கேமரா, கீபோர்டு, சிஸ்டம் திரைகள் எப்போதும் அனுமதிக்கப்படும்."),
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
            if (locked) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    tr("Locked until you finish today's challenge.", "இன்றைய சேலஞ்சை முடிக்கும் வரை பூட்டப்பட்டுள்ளது."),
                    color = OrangeEnd,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            val list = apps
            if (list == null) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = OrangeEnd)
                }
            } else LazyColumn(modifier = Modifier.weight(1f)) {
                items(list, key = { it.packageName }) { app ->
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
                        AppIcon(app.packageName, 40.dp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            app.label,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
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
