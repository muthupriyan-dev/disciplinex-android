package com.muthu.disciplinex.data

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import java.time.LocalDate
import java.time.temporal.IsoFields

object UserPrefs {
    private const val PREFS_NAME = "disciplinex_prefs"

    // --- Onboarding / challenge settings ---
    private const val KEY_WAKE_TIME = "wake_time"
    private const val KEY_DURATION = "duration"
    private const val KEY_EXERCISE = "exercise"
    private const val KEY_ONBOARDED = "onboarded"
    private const val KEY_TARGET_REPS = "target_reps"
    private const val KEY_DURATION_MIN = "duration_min"

    // --- Challenge progress (survives the app being closed/killed) ---
    private const val KEY_TIMED_PROGRESS_MS = "timed_progress_ms"
    private const val KEY_REP_PROGRESS = "rep_progress"

    // --- App-lock state ---
    private const val KEY_LOCK_ACTIVE = "lock_active"
    private const val KEY_LOCK_IS_TEST = "lock_is_test"
    private const val KEY_LOCK_STARTED_AT = "lock_started_at"
    private const val KEY_LOCK_STARTED_ELAPSED = "lock_started_elapsed"
    private const val KEY_LOCK_BOOT_COUNT = "lock_boot_count"
    private const val KEY_LAST_COMPLETED_DATE = "last_completed_date"
    private const val KEY_ALLOWED_APPS = "allowed_apps"

    // --- Snooze / skip ---
    private const val KEY_SNOOZE_COUNT = "snooze_count"
    private const val KEY_SKIP_WEEK = "skip_week"
    private const val KEY_SKIP_COUNT = "skip_count"

    /**
     * Fail-safe: even if the challenge can never be completed (camera broken,
     * ML model failed to load, etc.) the lock expires by itself after this
     * long, so a bug can never keep the phone locked forever.
     */
    private const val MAX_LOCK_MS = 3 * 60 * 60 * 1000L // 3 hours

    const val MAX_SNOOZES = 2
    const val SNOOZE_MINUTES = 10
    const val MAX_SKIPS_PER_WEEK = 2

    /**
     * While the lock is active EVERY app with a launcher icon is blocked,
     * except these (plus Phone, Camera, keyboards, the home screen and system
     * screens, which the service detects by itself). The user can change this
     * list from Settings > Allowed apps.
     */
    val DEFAULT_ALLOWED_APPS: Set<String> = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b" // WhatsApp Business
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun SharedPreferences.Editor.resetProgress(): SharedPreferences.Editor =
        putLong(KEY_TIMED_PROGRESS_MS, 0L).putInt(KEY_REP_PROGRESS, 0)

    // ------------------------------------------------------------------
    // Onboarding + challenge target
    // ------------------------------------------------------------------

    fun saveOnboarding(
        context: Context,
        wakeTime: String,
        exercise: String,
        targetReps: Int,
        durationMinutes: Int
    ) {
        prefs(context).edit()
            .putString(KEY_WAKE_TIME, wakeTime)
            .putString(KEY_EXERCISE, exercise)
            .putInt(KEY_TARGET_REPS, targetReps)
            .putInt(KEY_DURATION_MIN, durationMinutes)
            .putString(KEY_DURATION, "$durationMinutes min")
            .putBoolean(KEY_ONBOARDED, true)
            .apply()
    }

    fun getWakeTime(context: Context): String = prefs(context).getString(KEY_WAKE_TIME, "5:00 AM") ?: "5:00 AM"
    fun getDuration(context: Context): String = prefs(context).getString(KEY_DURATION, "20 min") ?: "20 min"
    fun getExercise(context: Context): String = prefs(context).getString(KEY_EXERCISE, "Push-ups") ?: "Push-ups"
    fun isOnboarded(context: Context): Boolean = prefs(context).getBoolean(KEY_ONBOARDED, false)

    fun getTargetReps(context: Context): Int = prefs(context).getInt(KEY_TARGET_REPS, 10)

    fun setTargetReps(context: Context, reps: Int) {
        prefs(context).edit().putInt(KEY_TARGET_REPS, reps).apply()
    }

    fun getDurationMinutes(context: Context): Int {
        val p = prefs(context)
        if (p.contains(KEY_DURATION_MIN)) return p.getInt(KEY_DURATION_MIN, 15)
        // Older installs saved a string like "20 min".
        return getDuration(context).filter { it.isDigit() }.toIntOrNull() ?: 15
    }

    fun setDurationMinutes(context: Context, minutes: Int) {
        prefs(context).edit()
            .putInt(KEY_DURATION_MIN, minutes)
            .putString(KEY_DURATION, "$minutes min")
            .apply()
    }

    /** "10 reps" or "15 min", depending on the chosen exercise. */
    fun getChallengeSummary(context: Context): String =
        if (ExerciseKinds.isTimed(getExercise(context))) "${getDurationMinutes(context)} min"
        else "${getTargetReps(context)} reps"

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

    // ------------------------------------------------------------------
    // App-lock
    // ------------------------------------------------------------------

    /** Boot counter — lets the 3-hour fail-safe use a clock the user can't change. */
    private fun bootCount(context: Context): Int = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    } catch (e: Exception) {
        -1
    }

    /**
     * Called when the wake alarm fires, or from the Home "test" button
     * (isTest = true — a test lock can be ended from Home, a real one can't).
     */
    fun startLock(context: Context, isTest: Boolean = false) {
        prefs(context).edit()
            .putBoolean(KEY_LOCK_ACTIVE, true)
            .putBoolean(KEY_LOCK_IS_TEST, isTest)
            .putLong(KEY_LOCK_STARTED_AT, System.currentTimeMillis())
            .putLong(KEY_LOCK_STARTED_ELAPSED, SystemClock.elapsedRealtime())
            .putInt(KEY_LOCK_BOOT_COUNT, bootCount(context))
            .putInt(KEY_SNOOZE_COUNT, 0)
            .resetProgress()
            .apply()
    }

    fun isTestLock(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOCK_IS_TEST, false)

    /** Ends a TEST lock without recording a completed day. No-op for a real alarm lock. */
    fun cancelTestLock(context: Context) {
        if (!isTestLock(context)) return
        prefs(context).edit()
            .putBoolean(KEY_LOCK_ACTIVE, false)
            .resetProgress()
            .apply()
    }

    /** Called when the user finishes the challenge — unlocks and records the day. */
    fun completeChallenge(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_LOCK_ACTIVE, false)
            .putString(KEY_LAST_COMPLETED_DATE, LocalDate.now().toString())
            .resetProgress()
            .apply()
    }

    fun isLockActive(context: Context): Boolean {
        val p = prefs(context)
        if (!p.getBoolean(KEY_LOCK_ACTIVE, false)) return false

        val wallElapsed = System.currentTimeMillis() - p.getLong(KEY_LOCK_STARTED_AT, 0L)
        val sameBoot = bootCount(context) == p.getInt(KEY_LOCK_BOOT_COUNT, Int.MIN_VALUE)
        // Prefer the monotonic clock: changing the phone's date/time forward
        // can no longer make the fail-safe expire early. After a reboot the
        // monotonic clock restarts, so fall back to wall-clock time then.
        var elapsed = if (sameBoot) {
            SystemClock.elapsedRealtime() - p.getLong(KEY_LOCK_STARTED_ELAPSED, 0L)
        } else {
            wallElapsed
        }
        if (elapsed < 0) elapsed = wallElapsed

        if (elapsed > MAX_LOCK_MS) {
            p.edit().putBoolean(KEY_LOCK_ACTIVE, false).apply()
            return false
        }
        return true
    }

    fun getLastCompletedDate(context: Context): String? =
        prefs(context).getString(KEY_LAST_COMPLETED_DATE, null)

    // ------------------------------------------------------------------
    // Allowed apps (not blocked while the lock is on)
    // ------------------------------------------------------------------

    fun getAllowedApps(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_ALLOWED_APPS, null) ?: DEFAULT_ALLOWED_APPS

    fun setAllowedApps(context: Context, apps: Set<String>) {
        prefs(context).edit().putStringSet(KEY_ALLOWED_APPS, HashSet(apps)).apply()
    }

    // ------------------------------------------------------------------
    // Snooze (delays the alarm sound only — the lock stays on)
    // ------------------------------------------------------------------

    fun getSnoozesLeft(context: Context): Int =
        (MAX_SNOOZES - prefs(context).getInt(KEY_SNOOZE_COUNT, 0)).coerceAtLeast(0)

    fun useSnooze(context: Context): Boolean {
        if (getSnoozesLeft(context) <= 0) return false
        val p = prefs(context)
        p.edit().putInt(KEY_SNOOZE_COUNT, p.getInt(KEY_SNOOZE_COUNT, 0) + 1).apply()
        return true
    }

    // ------------------------------------------------------------------
    // Skip today (limited per week) — unlocks without doing the challenge
    // ------------------------------------------------------------------

    private fun currentWeekKey(): String {
        val d = LocalDate.now()
        return "${d.get(IsoFields.WEEK_BASED_YEAR)}-W${d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"
    }

    private fun skipsUsedThisWeek(p: SharedPreferences): Int =
        if (p.getString(KEY_SKIP_WEEK, null) == currentWeekKey()) p.getInt(KEY_SKIP_COUNT, 0) else 0

    fun getSkipsLeft(context: Context): Int =
        (MAX_SKIPS_PER_WEEK - skipsUsedThisWeek(prefs(context))).coerceAtLeast(0)

    /** Returns false (and does nothing) if no skips are left this week. */
    fun skipToday(context: Context): Boolean {
        val p = prefs(context)
        val used = skipsUsedThisWeek(p)
        if (used >= MAX_SKIPS_PER_WEEK) return false
        p.edit()
            .putString(KEY_SKIP_WEEK, currentWeekKey())
            .putInt(KEY_SKIP_COUNT, used + 1)
            .putBoolean(KEY_LOCK_ACTIVE, false)
            .resetProgress()
            .apply()
        return true
    }
}
