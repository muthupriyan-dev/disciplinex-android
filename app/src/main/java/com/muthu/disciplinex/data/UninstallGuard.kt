package com.muthu.disciplinex.data

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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

    /** After a correct password the uninstall dialog is allowed for this long. */
    private const val GRANT_MS = 2 * 60 * 1000L

    @Volatile
    private var grantedUntil = 0L

    /** True while the password screen is open, so the app-lock doesn't open it twice. */
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

    /** Called after a correct password: the next uninstall dialog is let through. */
    fun grantUninstall() {
        grantedUntil = SystemClock.elapsedRealtime() + GRANT_MS
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
