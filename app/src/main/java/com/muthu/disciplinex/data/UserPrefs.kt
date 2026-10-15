package com.muthu.disciplinex.data

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

object UserPrefs {
    private const val PREFS_NAME = "disciplinex_prefs"
    private const val KEY_WAKE_TIME = "wake_time"
    private const val KEY_DURATION = "duration"
    private const val KEY_EXERCISE = "exercise"
    private const val KEY_ONBOARDED = "onboarded"

    // --- App-lock state (Milestone 3) ---
    private const val KEY_LOCK_ACTIVE = "lock_active"
    private const val KEY_LOCK_STARTED_AT = "lock_started_at"
    private const val KEY_LAST_COMPLETED_DATE = "last_completed_date"
    private const val KEY_LOCKED_APPS = "locked_apps"

    /**
     * Fail-safe: even if the challenge can never be completed (camera broken,
     * ML model failed to load, etc.) the lock expires by itself after this
     * long, so a bug can never keep the phone locked forever.
     */
    private const val MAX_LOCK_MS = 3 * 60 * 60 * 1000L // 3 hours

    /**
     * Default distracting apps. This is an explicit allow-list of what to
     * block — the phone dialer, Settings, SMS, WhatsApp etc. are never in it,
     * so emergencies can't be blocked. A user-editable picker comes later.
     */
    val DEFAULT_LOCKED_APPS: Set<String> = setOf(
        "com.instagram.android",
        "com.instagram.lite",
        "com.google.android.youtube",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.snapchat.android",
        "com.twitter.android",
        "com.reddit.frontpage",
        "com.netflix.mediaclient",
        "in.startv.hotstar",
        "in.mohalla.sharechat",
        "in.mohalla.video"
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveOnboarding(context: Context, wakeTime: String, duration: String, exercise: String) {
        prefs(context).edit()
            .putString(KEY_WAKE_TIME, wakeTime)
            .putString(KEY_DURATION, duration)
            .putString(KEY_EXERCISE, exercise)
            .putBoolean(KEY_ONBOARDED, true)
            .apply()
    }

    fun getWakeTime(context: Context): String = prefs(context).getString(KEY_WAKE_TIME, "5:00 AM") ?: "5:00 AM"
    fun getDuration(context: Context): String = prefs(context).getString(KEY_DURATION, "20 min") ?: "20 min"
    fun getExercise(context: Context): String = prefs(context).getString(KEY_EXERCISE, "Push-ups") ?: "Push-ups"
    fun isOnboarded(context: Context): Boolean = prefs(context).getBoolean(KEY_ONBOARDED, false)

    // ------------------------------------------------------------------
    // App-lock
    // ------------------------------------------------------------------

    /** Called when the wake alarm fires (or from the Home "test" button). */
    fun startLock(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_LOCK_ACTIVE, true)
            .putLong(KEY_LOCK_STARTED_AT, System.currentTimeMillis())
            .apply()
    }

    /** Called when the user finishes the challenge — unlocks and records the day. */
    fun completeChallenge(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_LOCK_ACTIVE, false)
            .putString(KEY_LAST_COMPLETED_DATE, LocalDate.now().toString())
            .apply()
    }

    fun isLockActive(context: Context): Boolean {
        val p = prefs(context)
        if (!p.getBoolean(KEY_LOCK_ACTIVE, false)) return false
        val startedAt = p.getLong(KEY_LOCK_STARTED_AT, 0L)
        val elapsed = System.currentTimeMillis() - startedAt
        if (elapsed > MAX_LOCK_MS) {
            p.edit().putBoolean(KEY_LOCK_ACTIVE, false).apply()
            return false
        }
        return true
    }

    fun getLastCompletedDate(context: Context): String? =
        prefs(context).getString(KEY_LAST_COMPLETED_DATE, null)

    fun getLockedApps(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_LOCKED_APPS, null) ?: DEFAULT_LOCKED_APPS
}
