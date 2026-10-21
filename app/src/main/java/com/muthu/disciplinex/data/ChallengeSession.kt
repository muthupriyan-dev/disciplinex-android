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
 *  1. Alarm rings -> the lock turns ON and a 10-minute START window opens.
 *  2. Open the challenge inside that window -> the challenge is "in progress"
 *     (no time limit now) and finishing it unlocks the phone + counts for the streak.
 *  3. Don't open it in time -> the challenge is MISSED: the streak is broken and
 *     (if [MISSED_KEEPS_LOCK]) the phone stays locked until the next alarm.
 *  4. Whatever happens, the lock ends at the next alarm, or after 24 hours.
 */
object ChallengeSession {
    const val START_WINDOW_MS = 10 * 60 * 1000L
    const val MAX_LOCK_MS = 24 * 60 * 60 * 1000L

    /** true = missing the 10-minute window keeps the phone locked until the next alarm. */
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
        p.edit().putBoolean(K_MISSED, true).apply()
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
        val lockMs = untilNext.coerceIn(START_WINDOW_MS, MAX_LOCK_MS)

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

        // Lock window is over (next alarm reached, or the 24-hour cap).
        if (elapsed > p.getLong(K_LOCK_MS, MAX_LOCK_MS)) {
            recordMiss(context, p) // started but never finished, or never started
            p.edit().putBoolean(K_ACTIVE, false).apply()
            UserPrefs.resetProgress(context)
            return false
        }

        // The 10-minute start window ran out without the challenge being opened.
        if (!p.getBoolean(K_STARTED, false) && !p.getBoolean(K_MISSED, false) && elapsed > START_WINDOW_MS) {
            recordMiss(context, p)
            if (!MISSED_KEEPS_LOCK) {
                p.edit().putBoolean(K_ACTIVE, false).apply()
                return false
            }
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

    /** Milliseconds left to open the challenge (0 once the window is over). */
    fun msLeftToStart(context: Context): Long {
        if (phase(context) != Phase.WAITING) return 0L
        val p = prefs(context)
        return (START_WINDOW_MS - elapsedSinceRing(context, p)).coerceAtLeast(0L)
    }

    /**
     * The user opened the challenge screen. If we are still inside the
     * 10-minute window this marks the challenge as started.
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
        p.edit().putBoolean(K_ACTIVE, false).apply()
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
        if (ExerciseKinds.isTimed(exercise(context))) "${minutes(context)} min" else "${reps(context)} reps"

    /** Wall-clock time the lock is due to end (for display only). */
    fun lockEndsAtMillis(context: Context): Long {
        val p = prefs(context)
        return p.getLong(K_RING_WALL, 0L) + p.getLong(K_LOCK_MS, MAX_LOCK_MS)
    }
}
