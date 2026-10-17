package com.muthu.disciplinex.lock

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.telecom.TelecomManager
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.muthu.disciplinex.MainActivity
import com.muthu.disciplinex.data.UserPrefs

/**
 * While the lock is active, every app the user can open from the launcher is
 * blocked EXCEPT: Phone, Camera, WhatsApp, the keyboard, the home screen and
 * system screens (so the phone can never be bricked). Blocked app opens ->
 * user is sent Home and the challenge screen opens.
 *
 * It only watches "which app's window came to the front"
 * (TYPE_WINDOW_STATE_CHANGED); it never reads screen content
 * (canRetrieveWindowContent = false), so it cannot see messages or typing.
 */
class LockAccessibilityService : AccessibilityService() {

    private var lastRedirectAt = 0L
    private var allowedCache: Set<String> = emptySet()
    private var allowedCachedAt = 0L
    private val launchableCache = HashMap<String, Boolean>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return                       // never block ourselves
        if (!UserPrefs.isLockActive(this)) return            // no challenge pending
        if (!shouldBlock(pkg)) return

        // Debounce: one app opening can fire several window events in a row.
        val now = SystemClock.elapsedRealtime()
        if (now - lastRedirectAt < REDIRECT_COOLDOWN_MS) return
        lastRedirectAt = now

        // Step 1 always works: kick the user out of the blocked app.
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
        private const val REDIRECT_COOLDOWN_MS = 1500L
        private const val ALLOWED_CACHE_MS = 60_000L

        /**
         * Always allowed. Settings stays open on purpose: it is how you switch
         * this service off in an emergency, and WhatsApp/camera permission
         * screens need it.
         */
        private val ALWAYS_ALLOWED = setOf(
            "com.android.settings",
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
