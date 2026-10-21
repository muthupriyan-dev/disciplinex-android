package com.muthu.disciplinex.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Small app-wide settings. The alarms themselves live in [AlarmStore], and the
 * running challenge / lock lives in [ChallengeSession].
 */
object UserPrefs {
    private const val PREFS_NAME = "disciplinex_prefs"

    private const val KEY_ONBOARDED = "onboarded"
    private const val KEY_TOUR_SEEN = "tour_seen"
    private const val KEY_ALLOWED_APPS = "allowed_apps"

    // Challenge progress (survives the app being closed/killed)
    private const val KEY_TIMED_PROGRESS_MS = "timed_progress_ms"
    private const val KEY_REP_PROGRESS = "rep_progress"

    // Keys from the old single-alarm version, only read for migration.
    private const val LEGACY_WAKE_TIME = "wake_time"
    private const val LEGACY_EXERCISE = "exercise"
    private const val LEGACY_TARGET_REPS = "target_reps"
    private const val LEGACY_DURATION_MIN = "duration_min"

    /**
     * While the lock is on EVERY app with a launcher icon is blocked, except
     * these (plus Phone, Camera, keyboards, the home screen and system
     * screens, which the service detects by itself). Editable in Settings.
     */
    val DEFAULT_ALLOWED_APPS: Set<String> = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b" // WhatsApp Business
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ------------------------------------------------------------------
    // Onboarding + guide tour
    // ------------------------------------------------------------------

    fun isOnboarded(context: Context): Boolean = prefs(context).getBoolean(KEY_ONBOARDED, false)

    fun setOnboarded(context: Context) {
        prefs(context).edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    fun isTourSeen(context: Context): Boolean = prefs(context).getBoolean(KEY_TOUR_SEEN, false)

    fun setTourSeen(context: Context, seen: Boolean) {
        prefs(context).edit().putBoolean(KEY_TOUR_SEEN, seen).apply()
    }

    /** The single alarm of the old version, as a list entry (null if there was none). */
    fun legacyAlarmOrNull(context: Context): AlarmEntry? {
        val p = prefs(context)
        val wake = p.getString(LEGACY_WAKE_TIME, null) ?: return null
        val (h, m) = AlarmEntry.parseClock(wake) ?: return null
        return AlarmEntry(
            id = 1,
            hour24 = h,
            minute = m,
            daysMask = AlarmEntry.ALL_DAYS,
            label = "",
            exercise = p.getString(LEGACY_EXERCISE, "Push-ups") ?: "Push-ups",
            reps = p.getInt(LEGACY_TARGET_REPS, 10),
            minutes = p.getInt(LEGACY_DURATION_MIN, 15),
            enabled = true
        )
    }

    // ------------------------------------------------------------------
    // Challenge progress
    // ------------------------------------------------------------------

    fun getTimedProgressMs(context: Context): Long =
        prefs(context).getLong(KEY_TIMED_PROGRESS_MS, 0L)

    fun setTimedProgressMs(context: Context, ms: Long) {
        prefs(context).edit().putLong(KEY_TIMED_PROGRESS_MS, ms).apply()
    }

    fun getRepProgress(context: Context): Int =
        prefs(context).getInt(KEY_REP_PROGRESS, 0)

    fun setRepProgress(context: Context, reps: Int) {
        prefs(context).edit().putInt(KEY_REP_PROGRESS, reps).apply()
    }

    fun resetProgress(context: Context) {
        prefs(context).edit()
            .putLong(KEY_TIMED_PROGRESS_MS, 0L)
            .putInt(KEY_REP_PROGRESS, 0)
            .apply()
    }

    // ------------------------------------------------------------------
    // Allowed apps (not blocked while the lock is on)
    // ------------------------------------------------------------------

    fun getAllowedApps(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_ALLOWED_APPS, null) ?: DEFAULT_ALLOWED_APPS

    fun setAllowedApps(context: Context, apps: Set<String>) {
        prefs(context).edit().putStringSet(KEY_ALLOWED_APPS, HashSet(apps)).apply()
    }
}
