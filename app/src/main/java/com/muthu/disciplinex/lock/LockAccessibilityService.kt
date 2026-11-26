package com.muthu.disciplinex.lock

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.telecom.TelecomManager
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.util.Locale
import com.muthu.disciplinex.data.UninstallGuard
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import com.muthu.disciplinex.MainActivity
import com.muthu.disciplinex.data.ChallengeSession
import com.muthu.disciplinex.data.UserPrefs

/**
 * While the lock is active, every app the user can open from the launcher is
 * blocked EXCEPT: Phone, Camera, WhatsApp, the keyboard, the home screen and
 * system screens (so the phone can never be bricked). Blocked app opens ->
 * user is sent Home and the challenge screen opens.
 *
 * It also checks every visible window (split-screen / floating "mini" windows),
 * so a blocked app can't be used in a small window next to the challenge.
 *
 * It looks at WHICH app owns a window (its package name). The only screens whose
 * text it reads are the system "uninstall app" dialogs (installer / Play Store), just
 * to see whether the question is about DisciplineX itself, so the app can't be removed
 * to dodge the lock (see [UninstallGuard]). It never reads messages, passwords or
 * anything you type, and nothing it sees is stored or sent anywhere.
 */
class LockAccessibilityService : AccessibilityService() {

    private var lastRedirectAt = 0L
    private var allowedCache: Set<String> = emptySet()
    private var allowedCachedAt = 0L
    private val launchableCache = HashMap<String, Boolean>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return

        // Somebody is trying to uninstall DisciplineX: block it or ask for the password.
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && handleUninstallDialog(event)) return

        // An emergency unlock is running: make sure the lock comes back when it ends,
        // even if the user stays inside one app the whole time.
        scheduleRelockIfNeeded()

        if (!ChallengeSession.isLockActive(this)) return     // no challenge pending

        // 1) The app that just came to the front.
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (pkg != null && pkg != packageName && shouldBlock(pkg)) {
                redirectToChallenge()
                return
            }
        }

        // 2) Any other visible window (split-screen or floating mini window).
        if (hasBlockedWindowOnScreen()) redirectToChallenge()
    }

    private var lastUninstallPromptAt = 0L

    private enum class Watched { INSTALLER, STORE, SETTINGS, LAUNCHER }

    /** Result of looking at one window: [purpose] set = guard it, [recheck] = look again shortly. */
    private class Verdict(val purpose: UninstallGuard.Purpose? = null, val recheck: Boolean = false)

    private var pendingPkg: String? = null
    private var pendingClass: String = ""
    private var pendingKind: Watched? = null
    private val recheckRunnable = Runnable { recheckPending() }

    private fun watchedKind(pkg: String): Watched? = when {
        pkg.contains("packageinstaller", ignoreCase = true) -> Watched.INSTALLER
        pkg == PLAY_STORE -> Watched.STORE
        pkg == SETTINGS_PKG -> Watched.SETTINGS
        // Long-press on the icon -> "Uninstall": many launchers (Samsung, Xiaomi, Oppo, Vivo...)
        // show their OWN confirm dialog, which belongs to the launcher, not to the installer.
        pkg in homePackages() -> Watched.LAUNCHER
        else -> null
    }

    private var homeCache: Set<String> = emptySet()
    private var homeCachedAt = 0L

    private fun homePackages(): Set<String> {
        val now = SystemClock.elapsedRealtime()
        if (homeCache.isEmpty() || now - homeCachedAt > ALLOWED_CACHE_MS) {
            val set = HashSet<String>()
            try {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                    PackageManager.MATCH_ALL
                ).forEach { set += it.activityInfo.packageName }
            } catch (e: Exception) {
                Log.w(TAG, "home app lookup failed", e)
            }
            homeCache = set
            homeCachedAt = now
        }
        return homeCache
    }

    /**
     * The places where DisciplineX can be removed or its protection switched off:
     *  - the system "uninstall app" dialog (launcher, Settings and Play Store all end there),
     *  - the "deactivate device admin" screen and the "stop App Lock service?" dialog in Settings.
     * When one of them is about this app:
     *  - challenge pending / missed -> blocked,
     *  - otherwise -> the password screen opens first.
     * Returns true if the event was handled.
     */
    private fun handleUninstallDialog(event: AccessibilityEvent): Boolean {
        val pkg = event.packageName?.toString() ?: return false
        if (pkg == packageName) return false
        val kind = watchedKind(pkg) ?: return false
        if (UninstallGuard.promptShowing) return false

        val classLower = event.className?.toString().orEmpty().lowercase(Locale.ROOT)
        // In Settings only look at the device-admin screen and dialogs, never at normal pages.
        if (kind == Watched.SETTINGS && !(classLower.contains("deviceadmin") || classLower.contains("dialog"))) {
            return false
        }

        val blockedNow = UninstallGuard.isProtected(this)
        if (!blockedNow && UninstallGuard.isUninstallGranted(this)) {
            if (kind == Watched.INSTALLER && classLower.contains("uninstall")) {
                UninstallGuard.shrinkGrantAfterDialogSeen()
            }
            return false
        }

        val verdict = judge(kind, classLower, collectDialogText(event), blockedNow, finalPass = false)
        if (verdict.recheck) {
            // The dialog is there but its text isn't readable yet (or doesn't say which app).
            // Look again once it has finished drawing instead of letting it through.
            pendingPkg = pkg
            pendingClass = classLower
            pendingKind = kind
            handler.removeCallbacks(recheckRunnable)
            handler.postDelayed(recheckRunnable, RECHECK_DELAY_MS)
            return false
        }
        val purpose = verdict.purpose ?: return false
        guard(purpose)
        return true
    }

    private fun recheckPending() {
        val pkg = pendingPkg ?: return
        val kind = pendingKind ?: return
        val classLower = pendingClass
        pendingPkg = null
        pendingKind = null
        if (UninstallGuard.promptShowing) return
        val blockedNow = UninstallGuard.isProtected(this)
        if (!blockedNow && UninstallGuard.isUninstallGranted(this)) return

        val root = findWindowRoot(pkg)
        val text = collectTreeText(root)
        val verdict = judge(kind, classLower, text, blockedNow, finalPass = true)
        verdict.purpose?.let { guard(it) }
    }

    /**
     * Decides whether a watched window needs the password / the block.
     * Fails CLOSED for an uninstall question whose text can't be read at all.
     */
    private fun judge(
        kind: Watched,
        classLower: String,
        text: String,
        blockedNow: Boolean,
        finalPass: Boolean
    ): Verdict {
        val appLabel = try {
            applicationInfo.loadLabel(packageManager).toString().lowercase(Locale.ROOT)
        } catch (e: Exception) {
            "disciplinex"
        }
        val mentionsUs = text.contains(appLabel) || text.contains(packageName)
        val uninstallWords = UNINSTALL_WORDS.any { text.contains(it) }
        val uninstallClass = classLower.contains("uninstall")

        return when (kind) {
            // The Play Store has many screens: only act when it clearly asks about this app.
            Watched.STORE ->
                if (mentionsUs && uninstallWords) Verdict(UninstallGuard.Purpose.UNINSTALL) else Verdict()

            Watched.INSTALLER -> when {
                // While locked nobody uninstalls anything.
                blockedNow ->
                    if (uninstallClass || uninstallWords || mentionsUs) Verdict(UninstallGuard.Purpose.UNINSTALL) else Verdict()
                mentionsUs && (uninstallClass || uninstallWords) -> Verdict(UninstallGuard.Purpose.UNINSTALL)
                uninstallClass || uninstallWords -> when {
                    !finalPass -> Verdict(recheck = true)
                    // Still no readable text at all: assume it is about us.
                    text.isBlank() -> Verdict(UninstallGuard.Purpose.UNINSTALL)
                    // Readable and about another app: let it through.
                    else -> Verdict()
                }
                else -> Verdict()
            }

            Watched.LAUNCHER -> {
                val dialogLike = classLower.contains("dialog") || classLower.contains("alert") ||
                    classLower.contains("uninstall") || classLower.contains("confirm")
                // While locked (or missed / emergency unlock) any launcher window that asks to
                // uninstall this app is blocked. Otherwise only real dialogs are guarded, so a
                // normal home screen never triggers it.
                if (mentionsUs && uninstallWords && (blockedNow || dialogLike)) {
                    Verdict(UninstallGuard.Purpose.UNINSTALL)
                } else {
                    Verdict()
                }
            }

            Watched.SETTINGS -> when {
                !mentionsUs -> Verdict()
                classLower.contains("deviceadmin") -> Verdict(UninstallGuard.Purpose.DEACTIVATE)
                PROTECT_WORDS.any { text.contains(it) } -> Verdict(UninstallGuard.Purpose.PROTECT)
                else -> Verdict()
            }
        }
    }

    /** Closes the system screen, then shows our own password / "blocked" screen. */
    private fun guard(purpose: UninstallGuard.Purpose) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastUninstallPromptAt < 2000L) return
        lastUninstallPromptAt = now

        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({ openPasswordScreen(purpose) }, 250L)
    }

    private fun openPasswordScreen(purpose: UninstallGuard.Purpose) {
        val intent = UninstallPasswordActivity.intent(this, purpose)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not open the uninstall password screen", e)
        }
        // Some phones (Xiaomi, Oppo, Vivo...) silently refuse to start an activity from the
        // background. If the screen did not really appear, leave a notification that opens it
        // and send the user Home, so the dialog is never simply gone with nothing to type into.
        handler.postDelayed({
            if (!UninstallGuard.promptShowing) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                postPasswordNotification(intent)
            }
        }, 900L)
    }

    private fun postPasswordNotification(intent: Intent) {
        try {
            com.muthu.disciplinex.data.AppSettings.load(this)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                ?: return
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    android.app.NotificationChannel(
                        GUARD_CHANNEL,
                        com.muthu.disciplinex.data.tr("Uninstall protection", "நிறுவல் நீக்கப் பாதுகாப்பு"),
                        android.app.NotificationManager.IMPORTANCE_HIGH
                    )
                )
            }
            val pending = android.app.PendingIntent.getActivity(
                this, 7, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val n = androidx.core.app.NotificationCompat.Builder(this, GUARD_CHANNEL)
                .setContentTitle(com.muthu.disciplinex.data.tr("DisciplineX", "DisciplineX"))
                .setContentText(
                    com.muthu.disciplinex.data.tr(
                        "Tap to continue (password or blocked message).",
                        "தொடர தட்டுங்கள் (கடவுச்சொல் அல்லது தடை செய்தி)."
                    )
                )
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
            manager.notify(GUARD_NOTIFICATION_ID, n)
        } catch (e: Exception) {
            Log.w(TAG, "Could not post the password notification", e)
        }
    }

    /** All the text of a dialog, in lower case. Only used on the watched screens above. */
    private fun collectDialogText(event: AccessibilityEvent): String {
        val sb = StringBuilder()
        event.text?.forEach { sb.append(it).append(' ') }
        val root = try { event.source } catch (e: Exception) { null }
            ?: try { rootInActiveWindow } catch (e: Exception) { null }
        sb.append(collectTreeText(root))
        return sb.toString().lowercase(Locale.ROOT)
    }

    private fun collectTreeText(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""
        val sb = StringBuilder()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 150) {
            val node = queue.removeFirst()
            visited++
            node.text?.let { sb.append(it).append(' ') }
            node.contentDescription?.let { sb.append(it).append(' ') }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return sb.toString().lowercase(Locale.ROOT)
    }

    /** The content of the window that belongs to [pkg] (the active one first). */
    private fun findWindowRoot(pkg: String): AccessibilityNodeInfo? {
        try {
            val active = rootInActiveWindow
            if (active != null && active.packageName?.toString() == pkg) return active
        } catch (e: Exception) {
            // fall through
        }
        val list = try { windows } catch (e: Exception) { null } ?: return null
        for (w in list) {
            val root = try { w.root } catch (e: Exception) { null } ?: continue
            if (root.packageName?.toString() == pkg) return root
        }
        return null
    }

    private val handler = Handler(Looper.getMainLooper())
    private val relockRunnable = Runnable { relockNow() }

    private fun scheduleRelockIfNeeded() {
        val left = ChallengeSession.emergencyUnlockMsLeft(this) ?: return
        handler.removeCallbacks(relockRunnable)
        handler.postDelayed(relockRunnable, left + 500L)
    }

    /** The 30-minute emergency unlock is over: send the user back to the challenge screen. */
    private fun relockNow() {
        if (!ChallengeSession.isLockActive(this)) return
        val power = getSystemService(Context.POWER_SERVICE) as? PowerManager
        // Screen off: the next time the user opens an app the normal check blocks it.
        if (power != null && !power.isInteractive) return
        val front = try { rootInActiveWindow?.packageName?.toString() } catch (e: Exception) { null }
        if (front == packageName) return // already inside DisciplineX
        redirectToChallenge()
    }

    override fun onDestroy() {
        handler.removeCallbacks(relockRunnable)
        handler.removeCallbacks(recheckRunnable)
        super.onDestroy()
    }

    /** True if a window of a blocked app is visible anywhere on screen. */
    private fun hasBlockedWindowOnScreen(): Boolean {
        val list = try { windows } catch (e: Exception) { null } ?: return false
        for (w in list) {
            if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = try { w.root } catch (e: Exception) { null } ?: continue
            val pkg = root.packageName?.toString()
            root.recycle()
            if (pkg != null && pkg != packageName && shouldBlock(pkg)) return true
        }
        return false
    }

    private fun redirectToChallenge() {
        // Debounce: one app opening can fire several window events in a row.
        val now = SystemClock.elapsedRealtime()
        if (now - lastRedirectAt < REDIRECT_COOLDOWN_MS) return
        lastRedirectAt = now

        // Step 1 always works: kick the user out of the blocked app / mini window.
        performGlobalAction(GLOBAL_ACTION_HOME)

        // Step 2: bring up the challenge. If the OEM blocks background launches,
        // step 1 still protects.
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_START_CHALLENGE, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not open challenge from accessibility service", e)
        }
    }

    override fun onInterrupt() {
        // Nothing to clean up.
    }

    /**
     * Block only real apps (ones with a launcher icon) that are not allowed.
     * System overlays, permission dialogs, keyboards etc. have no launcher
     * icon, so they are never blocked. If we can't tell (package not visible),
     * we fail open and let it through.
     */
    private fun shouldBlock(pkg: String): Boolean {
        if (pkg in ALWAYS_BLOCKED) return true
        if (pkg in allowedPackages()) return false
        return launchableCache.getOrPut(pkg) {
            try {
                packageManager.getLaunchIntentForPackage(pkg) != null
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun allowedPackages(): Set<String> {
        val now = SystemClock.elapsedRealtime()
        if (allowedCache.isEmpty() || now - allowedCachedAt > ALLOWED_CACHE_MS) {
            allowedCache = buildAllowedPackages()
            launchableCache.clear()
            allowedCachedAt = now
        }
        return allowedCache
    }

    private fun buildAllowedPackages(): Set<String> {
        val set = HashSet<String>()
        set += UserPrefs.getAllowedApps(this)
        set += ALWAYS_ALLOWED

        // Default phone/dialer app (works on any brand).
        try {
            val telecom = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            telecom?.defaultDialerPackage?.let { set += it }
        } catch (e: Exception) {
            Log.w(TAG, "default dialer lookup failed", e)
        }

        // Dialer, camera and home-screen apps, found by what they can do.
        val intents = listOf(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            Intent(MediaStore.ACTION_VIDEO_CAPTURE),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        )
        for (intent in intents) {
            try {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                    .forEach { set += it.activityInfo.packageName }
            } catch (e: Exception) {
                Log.w(TAG, "query failed for ${intent.action}", e)
            }
        }

        // Every keyboard, so typing never gets blocked.
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.enabledInputMethodList?.forEach { set += it.packageName }
        } catch (e: Exception) {
            Log.w(TAG, "keyboard lookup failed", e)
        }

        return set
    }

    companion object {
        private const val TAG = "LockAccessibility"
        private const val PLAY_STORE = "com.android.vending"
        private const val SETTINGS_PKG = "com.android.settings"
        private const val GUARD_CHANNEL = "uninstall_guard_channel"
        private const val GUARD_NOTIFICATION_ID = 1003
        private const val RECHECK_DELAY_MS = 450L

        /**
         * Words that show up on an "uninstall this app?" question. The window's CLASS name
         * ("...UninstallerActivity") is checked first and works in every language; these
         * words are the backup. Languages other than English/Tamil are a best effort.
         */
        private val UNINSTALL_WORDS = listOf(
            "uninstall",
            "நிறுவல் நீக்க",
            "நீக்க வேண்டுமா",
            "अनइंस्टॉल",
            "अनइंस्टाल",
            "desinstalar",
            "désinstaller",
            "deinstallieren",
            "удалить",
            "卸载",
            "アンインストール",
            "إلغاء التثبيت",
            "copot"
        )

        /** Words on the "stop this accessibility service?" dialog in Settings (best effort). */
        private val PROTECT_WORDS = listOf(
            "stop",
            "turn off",
            "deactivate"
        )
        private const val REDIRECT_COOLDOWN_MS = 1500L
        private const val ALLOWED_CACHE_MS = 60_000L

        /**
         * Blocked even if they would otherwise pass. System Settings is blocked
         * while locked so the lock can't be switched off from the Accessibility
         * page or by opening this app's info page.
         */
        private val ALWAYS_BLOCKED = setOf(
            "com.android.settings"
        )

        /** Always allowed so the phone can never be bricked. */
        private val ALWAYS_ALLOWED = setOf(
            "com.android.systemui",
            "android",
            "com.android.emergency",
            // Phone / calls / contacts
            "com.android.dialer",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.android.incallui",
            "com.samsung.android.incallui",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.contacts",
            "com.google.android.contacts",
            // Camera
            "com.android.camera",
            "com.android.camera2",
            "com.google.android.GoogleCamera",
            "com.sec.android.app.camera",
            // Permission dialogs, installer, file picker (WhatsApp attachments)
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.android.documentsui",
            "com.google.android.documentsui"
        )

        /** True if the user has switched this service on in system Accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, LockAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            while (splitter.hasNext()) {
                val component = ComponentName.unflattenFromString(splitter.next())
                if (component == expected) return true
            }
            return false
        }
    }
}
