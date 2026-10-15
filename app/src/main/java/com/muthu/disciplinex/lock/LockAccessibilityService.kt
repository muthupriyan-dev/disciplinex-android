package com.muthu.disciplinex.lock

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.muthu.disciplinex.MainActivity
import com.muthu.disciplinex.data.UserPrefs

/**
 * Watches ONLY for "a different app's window came to the front" events
 * (TYPE_WINDOW_STATE_CHANGED). It never reads screen content
 * (canRetrieveWindowContent = false in the XML config), so it cannot see
 * messages, OTPs or anything typed.
 *
 * While the lock is active and the foreground app is on the locked list, it
 * sends the user Home and opens the challenge screen.
 */
class LockAccessibilityService : AccessibilityService() {

    private var lastRedirectAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return                       // never block ourselves
        if (!UserPrefs.isLockActive(this)) return            // no challenge pending
        if (pkg !in UserPrefs.getLockedApps(this)) return    // not a blocked app

        // Debounce: one app opening can fire several window events in a row.
        val now = SystemClock.elapsedRealtime()
        if (now - lastRedirectAt < REDIRECT_COOLDOWN_MS) return
        lastRedirectAt = now

        // Step 1 always works: kick the user out of the blocked app.
        performGlobalAction(GLOBAL_ACTION_HOME)

        // Step 2: bring up the challenge. If the OEM blocks background launches
        // (Vivo/iQOO "pop-up while in background"), step 1 still protects.
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

    companion object {
        private const val TAG = "LockAccessibility"
        private const val REDIRECT_COOLDOWN_MS = 1500L

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
