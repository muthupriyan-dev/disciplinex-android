#!/usr/bin/env python3
"""Phase 8 patch:
 1. Walking permission ("Physical activity"): asked in the first-run permissions screen, listed in
    Settings > Permissions (with a Fix button when it is off) and in the Home warning card.
    The Walking challenge also shows an Allow button if the permission is off.
 2. Restore-from-backup tells you the exact reason when it is blocked, BEFORE you pick a file.
Run from the repo root after phases 2-7:  python3 apply_phase8_patch.py   (safe to run twice)"""
import os, sys
M = "app/src/main/java/com/muthu/disciplinex/"
ok = True

def edit(path, pairs):
    global ok
    if not os.path.exists(path):
        print("MISSING FILE:", path); ok = False; return
    s = open(path, encoding="utf-8").read(); orig = s
    for pair in pairs:
        marker, old, new = pair[0], pair[1], pair[2]
        if marker in s: continue
        n = s.count(old)
        if n != 1:
            print("ANCHOR PROBLEM (%d matches) in %s -> %s" % (n, path, old[:70].replace("\n", " "))); ok = False; return
        s = s.replace(old, new)
    if s != orig:
        open(path, "w", encoding="utf-8").write(s); print("patched", path)
    else:
        print("already patched", path)

# ------------------------------------------------------------ PermissionChecks
edit(M + "data/PermissionChecks.kt", [
    ("CAMERA, ACTIVITY,",
     "enum class Kind { NOTIFICATIONS, FULL_SCREEN, CAMERA, BATTERY, ACCESSIBILITY }",
     "enum class Kind { NOTIFICATIONS, FULL_SCREEN, CAMERA, ACTIVITY, BATTERY, ACCESSIBILITY }"),
    ("Kind.ACTIVITY -> tr(",
     '        Kind.CAMERA -> tr("Camera", "கேமரா")\n',
     '        Kind.CAMERA -> tr("Camera", "கேமரா")\n        Kind.ACTIVITY -> tr("Physical activity (walking)", "உடல் செயல்பாடு (நடைப்பயிற்சி)")\n'),
    ("Kind.ACTIVITY -> WalkSteps.hasPermission(context)",
     "        Kind.BATTERY ->\n            (context.getSystemService(Context.POWER_SERVICE)",
     "        Kind.ACTIVITY -> WalkSteps.hasPermission(context)\n        Kind.BATTERY ->\n            (context.getSystemService(Context.POWER_SERVICE)"),
    ("Kind.ACTIVITY -> Intent(",
     "            Kind.CAMERA -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)\n",
     "            Kind.CAMERA -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)\n            Kind.ACTIVITY -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)\n"),
])

# ----------------------------------------------------- first-run permissions screen
edit(M + "ui/onboarding/PermissionsScreen.kt", [
    ("val activityLauncher",
     "    val notificationGranted = remember(refreshTick) { hasNotificationPermission(context) }\n",
     '''    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshTick++
        val activity = context.findActivity()
        if (!granted && activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.ACTIVITY_RECOGNITION
            )
        ) {
            openAppSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        }
    }

    val notificationGranted = remember(refreshTick) { hasNotificationPermission(context) }
'''),
    ("val activityGranted",
     "    val cameraGranted = remember(refreshTick) { hasCameraPermission(context) }\n",
     "    val cameraGranted = remember(refreshTick) { hasCameraPermission(context) }\n    val activityGranted = remember(refreshTick) { com.muthu.disciplinex.data.WalkSteps.hasPermission(context) }\n"),
    ('title = tr("Physical activity"',
     '        add(\n            PermissionRow(\n                title = tr("Run in background", "பின்னணியில் இயங்கு"),',
     '''        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(
                PermissionRow(
                    title = tr("Physical activity", "உடல் செயல்பாடு"),
                    reason = tr("Counts your steps for the Walking challenge. Nothing leaves your phone.", "நடைப்பயிற்சி சேலஞ்சுக்கு உங்கள் அடிகளை எண்ண. எதுவும் போனை விட்டு வெளியே போகாது."),
                    isGranted = activityGranted,
                    onRequest = { activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }
                )
            )
        }
        add(
            PermissionRow(
                title = tr("Run in background", "பின்னணியில் இயங்கு"),'''),
])

# --------------------------------------------------------------- walking screen
edit(M + "ui/challenge/ChallengeScreen.kt", [
    ("Physical activity is off",
     'tr("No step counter available \\u2014 a timer replaces the distance.", "அடி கவுண்டர் கிடைக்கவில்லை \\u2014 தூரத்துக்கு பதிலாக டைமர் ஓடும்.")',
     'if (WalkSteps.hasPermission(context)) {\n                        tr("No step counter available \\u2014 a timer replaces the distance.", "அடி கவுண்டர் கிடைக்கவில்லை \\u2014 தூரத்துக்கு பதிலாக டைமர் ஓடும்.")\n                    } else {\n                        tr("Physical activity is off \\u2014 a timer replaces the distance. Tap Allow to count steps.", "உடல் செயல்பாடு அனுமதி இல்லை \\u2014 தூரத்துக்கு பதிலாக டைமர் ஓடும். அடிகளை எண்ண Allow தட்டுங்கள்.")\n                    }'),
    ("Allow physical activity",
     "                style = MaterialTheme.typography.labelLarge\n            )\n\n            Spacer(modifier = Modifier.weight(1f))\n\n            ProgressRing(\n                progress = progress,\n                centerText = centerText,",
     '''                style = MaterialTheme.typography.labelLarge
            )
            if (!sensorReady && !WalkSteps.hasPermission(context)) {
                androidx.compose.material3.TextButton(
                    onClick = { stepPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION) }
                ) {
                    Text(tr("Allow physical activity", "உடல் செயல்பாட்டை அனுமதி"), color = OrangeEnd, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            ProgressRing(
                progress = progress,
                centerText = centerText,'''),
])

# --------------------------------------------------------------- restore messages
edit(M + "data/BackupManager.kt", [
    ("BLOCKED_HISTORY, BLOCKED_ACTIVE",
     "enum class RestoreResult { OK, INVALID_FILE, NOT_ALLOWED, IO_ERROR }",
     "enum class RestoreResult { OK, INVALID_FILE, BLOCKED_HISTORY, BLOCKED_ACTIVE, BLOCKED_ALARM_SOON, IO_ERROR }"),
    ("fun restoreBlockReason",
     '''    /** True if a restore must be refused right now (see the class comment). */
    fun restoreBlocked(context: Context): Boolean {
        if (ChallengeSession.isLockActive(context)) return true
        if (AlarmStore.getAll(context).any { AlarmScheduler.isCommitted(it) }) return true
        if (StreakStore.allDays(context).isNotEmpty()) return true
        if (HistoryStore.getAll(context).isNotEmpty()) return true
        return false
    }
''',
     '''    /** Why a restore must be refused right now (see the class comment), or null if it is allowed. */
    fun restoreBlockReason(context: Context): RestoreResult? {
        if (ChallengeSession.isLockActive(context)) return RestoreResult.BLOCKED_ACTIVE
        if (AlarmStore.getAll(context).any { AlarmScheduler.isCommitted(it) }) return RestoreResult.BLOCKED_ALARM_SOON
        if (StreakStore.allDays(context).isNotEmpty() || HistoryStore.getAll(context).isNotEmpty()) {
            return RestoreResult.BLOCKED_HISTORY
        }
        return null
    }
'''),
    ("restoreBlockReason(context)?.let",
     "        if (restoreBlocked(context)) return RestoreResult.NOT_ALLOWED\n",
     "        restoreBlockReason(context)?.let { return it }\n"),
])

p = M + "ui/settings/BackupCard.kt"
if os.path.exists(p):
    s = open(p, encoding="utf-8").read(); orig = s
    if "private fun restoreMessage" not in s:
        a = s.find("                    val message = when (result) {")
        b = s.find("                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()")
        old_click = 'onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },'
        if a == -1 or b == -1 or b < a or s.count(old_click) != 1:
            print("ANCHOR PROBLEM in", p); ok = False
        else:
            s = s[:a] + "                    val message = restoreMessage(result)\n" + s[b:]
            s = s.replace(old_click, '''onClick = {
                val why = BackupManager.restoreBlockReason(context)
                if (why != null) {
                    Toast.makeText(context, restoreMessage(why), Toast.LENGTH_LONG).show()
                } else {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
                }
            },''')
            s = s.rstrip("\n") + '''

private fun restoreMessage(result: BackupManager.RestoreResult): String = when (result) {
    BackupManager.RestoreResult.OK ->
        tr("Backup restored. Reopen the app to see everything.", "பேக்கப் மீட்டெடுக்கப்பட்டது. அனைத்தையும் காண ஆப்பை மீண்டும் திறக்கவும்.")
    BackupManager.RestoreResult.INVALID_FILE ->
        tr("That file is not a valid DisciplineX backup.", "இது சரியான DisciplineX பேக்கப் கோப்பு அல்ல.")
    BackupManager.RestoreResult.BLOCKED_HISTORY ->
        tr(
            "This phone already has recorded days, so restore is blocked. Restore only works on a fresh install (for example a new phone), so a backup can never undo a missed day. Your backup file itself is fine.",
            "இந்த போனில் ஏற்கனவே நாட்கள் பதிவாகியுள்ளதால் மீட்டெடுப்பு தடுக்கப்பட்டது. புதிய இன்ஸ்டாலில் (எ.கா. புதிய போன்) மட்டுமே மீட்டெடுக்க முடியும், அதனால் பேக்கப் தவறிய நாளை அழிக்க முடியாது. உங்கள் பேக்கப் கோப்பு சரியாகவே உள்ளது."
        )
    BackupManager.RestoreResult.BLOCKED_ACTIVE ->
        tr("A challenge is active right now. Finish it first, then try again.", "இப்போது ஒரு சேலஞ்ச் நடந்துகொண்டிருக்கிறது. முதலில் அதை முடித்துவிட்டு மீண்டும் முயலுங்கள்.")
    BackupManager.RestoreResult.BLOCKED_ALARM_SOON ->
        tr("An alarm will ring within 2 hours, so restore is blocked. Try again later.", "2 மணி நேரத்துக்குள் அலாரம் அடிக்கும், அதனால் மீட்டெடுப்பு தடுக்கப்பட்டது. பிறகு முயலுங்கள்.")
    BackupManager.RestoreResult.IO_ERROR ->
        tr("Could not read the file.", "கோப்பை படிக்க முடியவில்லை.")
}
'''
            open(p, "w", encoding="utf-8").write(s); print("patched", p)
    else:
        print("already patched", p)
else:
    print("MISSING FILE:", p); ok = False
sys.exit(0 if ok else 1)
