package com.muthu.disciplinex.data

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The challenge that started when an alarm rang. It decides everything about
 * the app lock:
 *
 *  1. Alarm rings -> the lock turns ON and the alarm's own TIME LIMIT starts
 *     ("finish within N minutes", chosen when the alarm is set).
 *  2. Finish the challenge inside the time limit -> the phone unlocks and the day
 *     counts for the streak. The limit covers the WHOLE challenge, also while it
 *     is in progress.
 *  3. Time limit over without finishing -> MISSED: the streak is broken and the
 *     phone stays locked for 24 hours from the alarm.
 *  4. While the challenge is in progress you may leave the app for up to
 *     [AWAY_GRACE_MS] (an emergency). Come back later than that and the progress
 *     starts again from zero.
 */
object ChallengeSession {
    /** The alarm sound stops after this long (or at the time limit, if shorter). */
    const val MAX_RING_MS = 10 * 60 * 1000L
    const val MAX_LOCK_MS = 24 * 60 * 60 * 1000L

    /** How long you may be away from an in-progress challenge before it restarts. */
    const val AWAY_GRACE_MS = 5 * 60 * 1000L

    /** true = missing the time limit keeps the phone locked (for 24 hours). */
    const val MISSED_KEEPS_LOCK = true

    enum class Phase { IDLE, WAITING, IN_PROGRESS, MISSED }

    private const val PREFS_NAME = "disciplinex_session"
    private const val K_ACTIVE = "active"
    private const val K_STARTED = "started"
    private const val K_MISSED = "missed"
    private const val K_ALARM_ID = "alarm_id"
    private const val K_EXERCISE = "exercise"
    private const val K_REPS = "reps"
    private const val K_MINUTES = "minutes"
    private const val K_LABEL = "label"
    private const val K_RING_WALL = "ring_wall"
    private const val K_RING_ELAPSED = "ring_elapsed"
    private const val K_BOOT = "boot_count"
    private const val K_LOCK_MS = "lock_ms"
    private const val K_WINDOW_MS = "window_ms"
    private const val K_LEFT_ELAPSED = "left_elapsed"
    private const val K_LEFT_WALL = "left_wall"
    private const val K_LEFT_BOOT = "left_boot"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun bootCount(context: Context): Int = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    } catch (e: Exception) {
        -1
    }

    private fun elapsedSinceRing(context: Context, p: SharedPreferences): Long {
        val wall = System.currentTimeMillis() - p.getLong(K_RING_WALL, 0L)
        val sameBoot = bootCount(context) == p.getInt(K_BOOT, Int.MIN_VALUE)
        // Prefer the monotonic clock so changing the phone's date/time can't shorten the lock.
        var e = if (sameBoot) SystemClock.elapsedRealtime() - p.getLong(K_RING_ELAPSED, 0L) else wall
        if (e < 0) e = wall
        return e
    }

    private fun ringDate(p: SharedPreferences): LocalDate =
        Instant.ofEpochMilli(p.getLong(K_RING_WALL, System.currentTimeMillis()))
            .atZone(ZoneId.systemDefault()).toLocalDate()

    /** Records a failed day for this session (once) and remembers that it was missed. */
    private fun recordMiss(context: Context, p: SharedPreferences) {
        if (p.getBoolean(K_MISSED, false)) return
        StreakStore.record(context, ringDate(p), false)
        // A missed day locks the phone for the full 24 hours from the alarm.
        p.edit().putBoolean(K_MISSED, true).putLong(K_LOCK_MS, MAX_LOCK_MS).apply()
    }

    // ------------------------------------------------------------------
    // Called when an alarm rings
    // ------------------------------------------------------------------

    fun start(context: Context, alarm: AlarmEntry, nextAlarmMillis: Long?) {
        val p = prefs(context)
        // A previous challenge that was never finished counts as missed.
        if (p.getBoolean(K_ACTIVE, false)) recordMiss(context, p)

        val now = System.currentTimeMillis()
        val untilNext = (nextAlarmMillis ?: (now + MAX_LOCK_MS)) - now
        val windowMs = (alarm.windowMinutes.coerceAtLeast(if (alarm.isTimed) alarm.minutes else 1) * 60_000L)
            .coerceIn(60_000L, MAX_LOCK_MS)
        val lockMs = untilNext.coerceIn(windowMs, MAX_LOCK_MS)

        UserPrefs.resetProgress(context)
        p.edit()
            .putBoolean(K_ACTIVE, true)
            .putBoolean(K_STARTED, false)
            .putBoolean(K_MISSED, false)
            .putInt(K_ALARM_ID, alarm.id)
            .putString(K_EXERCISE, alarm.exercise)
            .putInt(K_REPS, alarm.reps)
            .putInt(K_MINUTES, alarm.minutes)
            .putString(K_LABEL, alarm.label)
            .putLong(K_RING_WALL, now)
            .putLong(K_RING_ELAPSED, SystemClock.elapsedRealtime())
            .putInt(K_BOOT, bootCount(context))
            .putLong(K_LOCK_MS, lockMs)
            .putLong(K_WINDOW_MS, windowMs)
            .remove(K_LEFT_ELAPSED).remove(K_LEFT_WALL).remove(K_LEFT_BOOT)
            .apply()
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    /** Also resolves timeouts, so every caller always sees up-to-date state. */
    fun isLockActive(context: Context): Boolean {
        val p = prefs(context)
        if (!p.getBoolean(K_ACTIVE, false)) return false
        val elapsed = elapsedSinceRing(context, p)

        // Time limit over without finishing (whether or not it was ever opened): missed.
        if (!p.getBoolean(K_MISSED, false) && elapsed > windowMs(p)) {
            recordMiss(context, p) // also extends the lock to 24 hours
            UserPrefs.resetProgress(context)
            if (!MISSED_KEEPS_LOCK) {
                p.edit().putBoolean(K_ACTIVE, false).apply()
                return false
            }
        }

        // Lock window is over (24 hours after a miss, or the next alarm for a finished-in-time day).
        if (elapsed > p.getLong(K_LOCK_MS, MAX_LOCK_MS)) {
            recordMiss(context, p) // started but never finished, or never started
            p.edit().putBoolean(K_ACTIVE, false).apply()
            UserPrefs.resetProgress(context)
            return false
        }
        return true
    }

    fun phase(context: Context): Phase {
        if (!isLockActive(context)) return Phase.IDLE
        val p = prefs(context)
        return when {
            p.getBoolean(K_MISSED, false) -> Phase.MISSED
            p.getBoolean(K_STARTED, false) -> Phase.IN_PROGRESS
            else -> Phase.WAITING
        }
    }

    private fun windowMs(p: SharedPreferences): Long = p.getLong(K_WINDOW_MS, 10 * 60 * 1000L)

    /** The alarm's time limit in milliseconds. */
    fun windowMs(context: Context): Long = windowMs(prefs(context))

    /** Milliseconds left to finish the challenge (0 once the time limit is over). */
    fun msLeftToFinish(context: Context): Long {
        val ph = phase(context)
        if (ph != Phase.WAITING && ph != Phase.IN_PROGRESS) return 0L
        val p = prefs(context)
        return (windowMs(p) - elapsedSinceRing(context, p)).coerceAtLeast(0L)
    }

    // ------------------------------------------------------------------
    // Leaving the app mid-challenge (5-minute emergency grace)
    // ------------------------------------------------------------------

    /** Call when the challenge screen goes to the background. */
    fun markLeft(context: Context) {
        if (phase(context) != Phase.IN_PROGRESS) return
        prefs(context).edit()
            .putLong(K_LEFT_ELAPSED, SystemClock.elapsedRealtime())
            .putLong(K_LEFT_WALL, System.currentTimeMillis())
            .putInt(K_LEFT_BOOT, bootCount(context))
            .apply()
    }

    /**
     * Call when the challenge screen comes back. Returns true if you were away
     * longer than [AWAY_GRACE_MS]; the saved progress has then been reset to zero.
     */
    fun returnedAfterGrace(context: Context): Boolean {
        val p = prefs(context)
        val leftElapsed = p.getLong(K_LEFT_ELAPSED, 0L)
        val leftWall = p.getLong(K_LEFT_WALL, 0L)
        if (leftElapsed == 0L && leftWall == 0L) return false

        val sameBoot = bootCount(context) == p.getInt(K_LEFT_BOOT, Int.MIN_VALUE)
        var away = if (sameBoot) SystemClock.elapsedRealtime() - leftElapsed else System.currentTimeMillis() - leftWall
        if (away < 0) away = System.currentTimeMillis() - leftWall
        p.edit().remove(K_LEFT_ELAPSED).remove(K_LEFT_WALL).remove(K_LEFT_BOOT).apply()

        if (away > AWAY_GRACE_MS && phase(context) == Phase.IN_PROGRESS) {
            UserPrefs.resetProgress(context)
            return true
        }
        return false
    }

    /**
     * The user opened the challenge screen. If we are still inside the
     * time limit this marks the challenge as started.
     */
    fun enterChallenge(context: Context): Phase {
        val ph = phase(context)
        if (ph == Phase.WAITING) {
            prefs(context).edit().putBoolean(K_STARTED, true).apply()
            return Phase.IN_PROGRESS
        }
        return ph
    }

    /** Challenge finished: unlock the phone and count the day. */
    fun complete(context: Context) {
        val p = prefs(context)
        if (p.getBoolean(K_ACTIVE, false) && !p.getBoolean(K_MISSED, false)) {
            StreakStore.record(context, ringDate(p), true)
        }
        p.edit().putBoolean(K_ACTIVE, false)
            .remove(K_LEFT_ELAPSED).remove(K_LEFT_WALL).remove(K_LEFT_BOOT)
            .apply()
        UserPrefs.resetProgress(context)
    }

    // ------------------------------------------------------------------
    // Details of the running challenge
    // ------------------------------------------------------------------

    fun exercise(context: Context): String = prefs(context).getString(K_EXERCISE, "Push-ups") ?: "Push-ups"
    fun reps(context: Context): Int = prefs(context).getInt(K_REPS, 10)
    fun minutes(context: Context): Int = prefs(context).getInt(K_MINUTES, 15)
    fun label(context: Context): String = prefs(context).getString(K_LABEL, "") ?: ""

    fun summary(context: Context): String =
        if (ExerciseKinds.isTimed(exercise(context))) tr("${minutes(context)} min", "${minutes(context)} நிமிடம்") else tr("${reps(context)} reps", "${reps(context)} முறை")

    /** Wall-clock time the lock is due to end (for display only). */
    fun lockEndsAtMillis(context: Context): Long {
        val p = prefs(context)
        return p.getLong(K_RING_WALL, 0L) + p.getLong(K_LOCK_MS, MAX_LOCK_MS)
    }
}
