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
 *  5. After a MISSED challenge an emergency unlock is possible: keep the app OPEN for
 *     [EMERGENCY_WAIT_MS] (the clock pauses whenever the app is not on screen), then the
 *     phone is unlocked for [EMERGENCY_UNLOCK_MS] and locks again by itself.
 */
object ChallengeSession {
    /** The alarm sound stops after this long (or at the time limit, if shorter). */
    const val MAX_RING_MS = 10 * 60 * 1000L
    const val MAX_LOCK_MS = 24 * 60 * 60 * 1000L

    /** How long you may be away from an in-progress challenge before it restarts. */
    const val AWAY_GRACE_MS = 5 * 60 * 1000L

    /**
     * After a MISSED challenge you can unlock early, but only after waiting this long.
     * The wait only counts while the app is OPEN (on screen).
     */
    const val EMERGENCY_WAIT_MS = 10 * 60 * 1000L

    /** An emergency unlock lasts this long, then the phone locks again by itself. */
    const val EMERGENCY_UNLOCK_MS = 30 * 60 * 1000L

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
    private const val K_METERS = "meters"
    private const val K_LABEL = "label"
    private const val K_RING_WALL = "ring_wall"
    private const val K_RING_ELAPSED = "ring_elapsed"
    private const val K_BOOT = "boot_count"
    private const val K_LOCK_MS = "lock_ms"
    private const val K_WINDOW_MS = "window_ms"
    private const val K_LEFT_ELAPSED = "left_elapsed"
    private const val K_LEFT_WALL = "left_wall"
    private const val K_LEFT_BOOT = "left_boot"
    private const val K_EM_ELAPSED = "em_elapsed"
    private const val K_EM_WALL = "em_wall"
    private const val K_EM_BOOT = "em_boot"
    private const val K_EM_STARTED = "em_started"
    private const val K_EM_ACCUM = "em_accum"
    private const val K_EM_RUN_SINCE = "em_run_since"
    private const val K_EM_RUN_BOOT = "em_run_boot"
    private const val K_EMU_ELAPSED = "emu_elapsed"
    private const val K_EMU_WALL = "emu_wall"
    private const val K_EMU_BOOT = "emu_boot"

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
        p.edit().putBoolean(K_MISSED, true).putLong(K_LOCK_MS, MAX_LOCK_MS).commit()
    }

    // ------------------------------------------------------------------
    // Called when an alarm rings
    // ------------------------------------------------------------------

    /** The same alarm firing again within this long is a duplicate trigger, not a new day. */
    const val DUPLICATE_TRIGGER_MS = 60_000L

    /**
     * @return false if this was a duplicate trigger of the session that is already
     * running (nothing was changed), true if a new session was started.
     */
    fun start(context: Context, alarm: AlarmEntry, nextAlarmMillis: Long?): Boolean {
        val p = prefs(context)
        if (p.getBoolean(K_ACTIVE, false)) {
            // Same alarm delivered twice (e.g. re-armed after a clock change): keep the
            // running session instead of counting it as a missed day and restarting.
            val sameAlarm = p.getInt(K_ALARM_ID, -1) == alarm.id
            val sameBoot = bootCount(context) == p.getInt(K_BOOT, Int.MIN_VALUE)
            val since = SystemClock.elapsedRealtime() - p.getLong(K_RING_ELAPSED, 0L)
            if (sameAlarm && sameBoot && since in 0..DUPLICATE_TRIGGER_MS) return false
            // A previous challenge that was never finished counts as missed.
            recordMiss(context, p)
        }

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
            .putInt(K_METERS, alarm.meters)
            .putString(K_LABEL, alarm.label)
            .putLong(K_RING_WALL, now)
            .putLong(K_RING_ELAPSED, SystemClock.elapsedRealtime())
            .putInt(K_BOOT, bootCount(context))
            .putLong(K_LOCK_MS, lockMs)
            .putLong(K_WINDOW_MS, windowMs)
            .remove(K_LEFT_ELAPSED).remove(K_LEFT_WALL).remove(K_LEFT_BOOT)
            .remove(K_EM_ELAPSED).remove(K_EM_WALL).remove(K_EM_BOOT)
            .remove(K_EM_STARTED).remove(K_EM_ACCUM).remove(K_EM_RUN_SINCE).remove(K_EM_RUN_BOOT)
            .remove(K_EMU_ELAPSED).remove(K_EMU_WALL).remove(K_EMU_BOOT)
            .commit()
        return true
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    /** Also resolves timeouts, so every caller always sees up-to-date state. */
    fun isLockActive(context: Context): Boolean {
        val p = prefs(context)
        if (!p.getBoolean(K_ACTIVE, false)) return false

        // Emergency unlock running: the phone is open for 30 minutes, then it locks again.
        val unlockLeft = emergencyUnlockLeft(context, p)
        if (unlockLeft != null && unlockLeft > 0L) return false

        val elapsed = elapsedSinceRing(context, p)

        // Time limit over without finishing (whether or not it was ever opened): missed.
        if (!p.getBoolean(K_MISSED, false) && elapsed > windowMs(p)) {
            recordMiss(context, p) // also extends the lock to 24 hours
            UserPrefs.resetProgress(context)
            if (!MISSED_KEEPS_LOCK) {
                p.edit().putBoolean(K_ACTIVE, false).commit()
                return false
            }
        }

        // Lock window is over (24 hours after a miss, or the next alarm for a finished-in-time day).
        if (elapsed > p.getLong(K_LOCK_MS, MAX_LOCK_MS)) {
            recordMiss(context, p) // started but never finished, or never started
            p.edit().putBoolean(K_ACTIVE, false).commit()
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

    /** Identifies the current alarm session (the moment the alarm rang). */
    fun sessionId(context: Context): Long = prefs(context).getLong(K_RING_WALL, 0L)

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

    // ------------------------------------------------------------------
    // Emergency unlock (only after a missed challenge)
    // ------------------------------------------------------------------

    /** Starts the wait. Only possible once the challenge has been MISSED. */
    fun startEmergencyUnlock(context: Context): Boolean {
        if (phase(context) != Phase.MISSED) return false
        prefs(context).edit()
            .putBoolean(K_EM_STARTED, true)
            .putLong(K_EM_ACCUM, 0L)
            // The user is looking at the app right now, so the clock starts running.
            .putLong(K_EM_RUN_SINCE, SystemClock.elapsedRealtime())
            .putInt(K_EM_RUN_BOOT, bootCount(context))
            .commit()
        return true
    }

    fun cancelEmergencyUnlock(context: Context) {
        prefs(context).edit().clearEmergencyWait().commit()
    }

    private fun SharedPreferences.Editor.clearEmergencyWait(): SharedPreferences.Editor =
        remove(K_EM_STARTED).remove(K_EM_ACCUM).remove(K_EM_RUN_SINCE).remove(K_EM_RUN_BOOT)
            .remove(K_EM_ELAPSED).remove(K_EM_WALL).remove(K_EM_BOOT)

    /** The app came on screen: the emergency wait (if any) runs again. */
    fun onAppForeground(context: Context) {
        val p = prefs(context)
        if (!p.getBoolean(K_EM_STARTED, false)) return
        // If a previous on-screen period was never closed (the app was killed), that time
        // is not counted - it is only counted when we saw the app go to the background.
        p.edit()
            .putLong(K_EM_RUN_SINCE, SystemClock.elapsedRealtime())
            .putInt(K_EM_RUN_BOOT, bootCount(context))
            .commit()
    }

    /** The app left the screen: the emergency wait pauses and keeps what it has counted. */
    fun onAppBackground(context: Context) {
        val p = prefs(context)
        if (!p.getBoolean(K_EM_STARTED, false)) return
        val since = p.getLong(K_EM_RUN_SINCE, 0L)
        if (since == 0L) return
        val sameBoot = bootCount(context) == p.getInt(K_EM_RUN_BOOT, Int.MIN_VALUE)
        val ran = if (sameBoot) (SystemClock.elapsedRealtime() - since).coerceAtLeast(0L) else 0L
        p.edit()
            .putLong(K_EM_ACCUM, p.getLong(K_EM_ACCUM, 0L) + ran)
            .putLong(K_EM_RUN_SINCE, 0L)
            .commit()
    }

    /** null = not started, 0 = the wait is over, otherwise milliseconds still to wait. */
    fun emergencyMsLeft(context: Context): Long? {
        val p = prefs(context)
        if (!p.getBoolean(K_EM_STARTED, false)) return null
        var passed = p.getLong(K_EM_ACCUM, 0L)
        val since = p.getLong(K_EM_RUN_SINCE, 0L)
        if (since != 0L && bootCount(context) == p.getInt(K_EM_RUN_BOOT, Int.MIN_VALUE)) {
            passed += (SystemClock.elapsedRealtime() - since).coerceAtLeast(0L)
        }
        return (EMERGENCY_WAIT_MS - passed).coerceAtLeast(0L)
    }

    /**
     * Unlocks the phone once the wait is over, for [EMERGENCY_UNLOCK_MS] only. The day
     * stays missed and the streak stays reset - this only lifts the lock for a while.
     */
    fun finishEmergencyUnlock(context: Context): Boolean {
        if (phase(context) != Phase.MISSED) return false
        val left = emergencyMsLeft(context) ?: return false
        if (left > 0L) return false
        prefs(context).edit()
            .clearEmergencyWait()
            .putLong(K_EMU_ELAPSED, SystemClock.elapsedRealtime())
            .putLong(K_EMU_WALL, System.currentTimeMillis())
            .putInt(K_EMU_BOOT, bootCount(context))
            .commit()
        return true
    }

    /**
     * True from the moment an alarm rings until the challenge is finished, and through the
     * whole missed-day lock - including the 30 minutes of an emergency unlock. While this is
     * true the app can't be uninstalled and the uninstall password stays hidden.
     */
    fun isUninstallProtected(context: Context): Boolean {
        if (isLockActive(context)) return true // also clears a lock that has run out
        return prefs(context).getBoolean(K_ACTIVE, false)
    }

    /** Milliseconds of emergency unlock still left; null if none is running. */
    private fun emergencyUnlockLeft(context: Context, p: SharedPreferences): Long? {
        val wall = p.getLong(K_EMU_WALL, 0L)
        val elapsed = p.getLong(K_EMU_ELAPSED, 0L)
        if (wall == 0L && elapsed == 0L) return null
        val now = System.currentTimeMillis()
        val sameBoot = bootCount(context) == p.getInt(K_EMU_BOOT, Int.MIN_VALUE)
        var passed = if (sameBoot) SystemClock.elapsedRealtime() - elapsed else now - wall
        if (passed < 0) passed = now - wall
        val left = (EMERGENCY_UNLOCK_MS - passed).coerceAtLeast(0L)
        if (left == 0L) {
            // The 30 minutes are over: the lock comes back.
            p.edit().remove(K_EMU_ELAPSED).remove(K_EMU_WALL).remove(K_EMU_BOOT).commit()
            return null
        }
        return left
    }

    /** For the Home banner: how long the phone stays unlocked (null = no emergency unlock running). */
    fun emergencyUnlockMsLeft(context: Context): Long? {
        val p = prefs(context)
        if (!p.getBoolean(K_ACTIVE, false)) return null
        return emergencyUnlockLeft(context, p)
    }

    /** Call when the challenge screen goes to the background. */
    fun markLeft(context: Context) {
        if (phase(context) != Phase.IN_PROGRESS) return
        prefs(context).edit()
            .putLong(K_LEFT_ELAPSED, SystemClock.elapsedRealtime())
            .putLong(K_LEFT_WALL, System.currentTimeMillis())
            .putInt(K_LEFT_BOOT, bootCount(context))
            .commit()
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
        p.edit().remove(K_LEFT_ELAPSED).remove(K_LEFT_WALL).remove(K_LEFT_BOOT).commit()

        if (away > AWAY_GRACE_MS && phase(context) == Phase.IN_PROGRESS) {
            UserPrefs.resetProgress(context)
            WalkSteps.reset(context)
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
            prefs(context).edit().putBoolean(K_STARTED, true).commit()
            return Phase.IN_PROGRESS
        }
        return ph
    }

    /** Challenge finished: unlock the phone and count the day. */
    fun complete(context: Context) {
        val p = prefs(context)
        if (p.getBoolean(K_ACTIVE, false) && !p.getBoolean(K_MISSED, false)) {
            StreakStore.record(context, ringDate(p), true)
            val ex = exercise(context)
            HistoryStore.add(
                context,
                HistoryStore.Entry(
                    completedAtMillis = System.currentTimeMillis(),
                    date = ringDate(p).toString(),
                    exercise = ex,
                    timed = ExerciseKinds.isTimed(ex),
                    target = when {
                        ExerciseKinds.isDistance(ex) -> meters(context)
                        ExerciseKinds.isTimed(ex) -> minutes(context)
                        else -> reps(context)
                    },
                    durationMs = elapsedSinceRing(context, p).coerceAtLeast(0L),
                    distance = ExerciseKinds.isDistance(ex)
                )
            )
        }
        p.edit().putBoolean(K_ACTIVE, false)
            .remove(K_LEFT_ELAPSED).remove(K_LEFT_WALL).remove(K_LEFT_BOOT)
            .remove(K_EMU_ELAPSED).remove(K_EMU_WALL).remove(K_EMU_BOOT)
            .commit()
        UserPrefs.resetProgress(context)
    }

    // ------------------------------------------------------------------
    // Details of the running challenge
    // ------------------------------------------------------------------

    fun exercise(context: Context): String = prefs(context).getString(K_EXERCISE, "Push-ups") ?: "Push-ups"
    fun reps(context: Context): Int = prefs(context).getInt(K_REPS, 10)
    fun minutes(context: Context): Int = prefs(context).getInt(K_MINUTES, 15)
    fun meters(context: Context): Int = prefs(context).getInt(K_METERS, AlarmEntry.DEFAULT_METERS)
    fun label(context: Context): String = prefs(context).getString(K_LABEL, "") ?: ""

    fun summary(context: Context): String =
        if (ExerciseKinds.isDistance(exercise(context))) ExerciseKinds.distanceText(meters(context))
        else if (ExerciseKinds.isTimed(exercise(context))) tr("${minutes(context)} min", "${minutes(context)} நிமிடம்") else tr("${reps(context)} reps", "${reps(context)} முறை")

    /** Wall-clock time the lock is due to end (for display only). */
    fun lockEndsAtMillis(context: Context): Long {
        val p = prefs(context)
        return p.getLong(K_RING_WALL, 0L) + p.getLong(K_LOCK_MS, MAX_LOCK_MS)
    }
}
