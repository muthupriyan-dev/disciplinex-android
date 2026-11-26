package com.muthu.disciplinex.data

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Base64
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import com.muthu.disciplinex.lock.DisciplineAdminReceiver

/**
 * The password that is needed to uninstall DisciplineX.
 *
 *  - It is a 6-digit code that changes every [STEP_SECONDS] seconds (like an authenticator
 *    app), made from a secret that never leaves this phone.
 *  - It can only be SEEN in Settings, and only while no challenge is pending or missed.
 *  - While a challenge is pending or missed ([isProtected]) no code is accepted at all.
 *
 * So after a missed task nobody can read the password, and a code seen earlier is useless
 * 30-60 seconds later.
 */
object UninstallGuard {
    private const val PREFS = "uninstall_guard"
    private const val KEY_SECRET = "secret"

    const val STEP_SECONDS = 30L

    /** After a correct password the protected screens are allowed for this long. */
    private const val GRANT_MS = 60 * 1000L

    /** Once the uninstall dialog has actually shown up, the grant shrinks to this. */
    private const val GRANT_AFTER_SEEN_MS = 30 * 1000L

    /** What the password screen was opened for. */
    enum class Purpose {
        /** The system "uninstall DisciplineX?" dialog. */
        UNINSTALL,
        /** The "deactivate device admin" screen. */
        DEACTIVATE,
        /** Another protected screen (for example switching the app-lock service off). */
        PROTECT
    }

    @Volatile
    private var grantedUntil = 0L

    /**
     * True only while the password screen is really on screen (set in onResume, cleared in
     * onPause). It must NOT stay true when the user presses Home, or the uninstall dialog
     * would no longer be guarded.
     */
    @Volatile
    var promptShowing = false

    fun isProtected(context: Context): Boolean = ChallengeSession.isUninstallProtected(context)

    /** The code that is valid right now (6 digits). */
    fun currentCode(context: Context, now: Long = System.currentTimeMillis()): String =
        codeFor(secret(context), now / 1000 / STEP_SECONDS)

    /** Seconds until the code changes (1..30). */
    fun secondsLeft(now: Long = System.currentTimeMillis()): Int =
        (STEP_SECONDS - (now / 1000) % STEP_SECONDS).toInt()

    /** The current code and the one before it are accepted (time to type). Never while protected. */
    fun verify(context: Context, input: String, now: Long = System.currentTimeMillis()): Boolean {
        if (isProtected(context)) return false
        val typed = input.filter { it.isDigit() }
        if (typed.length != 6) return false
        val step = now / 1000 / STEP_SECONDS
        val secret = secret(context)
        return typed == codeFor(secret, step) || typed == codeFor(secret, step - 1)
    }

    /** Called after a correct password: the next uninstall dialog is let through (60 s). */
    fun grantUninstall() {
        grantedUntil = SystemClock.elapsedRealtime() + GRANT_MS
    }

    /**
     * The uninstall dialog has been seen after the password: keep the grant only a little
     * longer so the user can tap OK, but it can't be reused minutes later.
     */
    fun shrinkGrantAfterDialogSeen() {
        val limit = SystemClock.elapsedRealtime() + GRANT_AFTER_SEEN_MS
        if (grantedUntil > limit) grantedUntil = limit
    }

    /** Ends the grant at once. */
    fun revokeGrant() {
        grantedUntil = 0L
    }

    // ------------------------------------------------------------------
    // Device admin (makes the plain "Uninstall" button impossible until it is switched off)
    // ------------------------------------------------------------------

    fun adminComponent(context: Context): ComponentName =
        ComponentName(context, DisciplineAdminReceiver::class.java)

    fun isAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return false
        return dpm.isAdminActive(adminComponent(context))
    }

    /** The system screen that asks the user to switch the device admin on. */
    fun addAdminIntent(context: Context, explanation: String): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(context))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, explanation)

    /**
     * Switches the device admin off. An app may always do this for itself. Only called
     * after a correct password, right before the real uninstall dialog.
     */
    fun removeOwnAdmin(context: Context) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            if (dpm != null && dpm.isAdminActive(adminComponent(context))) {
                dpm.removeActiveAdmin(adminComponent(context))
            }
        } catch (e: Exception) {
            // Nothing else to do: the system screen will ask the user instead.
        }
    }

    fun isUninstallGranted(context: Context): Boolean =
        !isProtected(context) && SystemClock.elapsedRealtime() < grantedUntil

    /** "483291" -> "483 291" */
    fun pretty(code: String): String =
        if (code.length == 6) code.substring(0, 3) + " " + code.substring(3) else code

    private fun secret(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_SECRET, null)?.let {
            return Base64.decode(it, Base64.NO_WRAP)
        }
        val fresh = ByteArray(20).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_SECRET, Base64.encodeToString(fresh, Base64.NO_WRAP)).commit()
        return fresh
    }

    /** The standard time-based one-time password (RFC 6238 / 4226) with SHA-1. */
    private fun codeFor(secret: ByteArray, step: Long): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret, "HmacSHA1"))
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array())
        val offset = hash[hash.size - 1].toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        return String.format(Locale.US, "%06d", binary % 1_000_000)
    }
}
