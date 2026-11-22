package com.muthu.disciplinex.data

import android.content.Context
import android.net.Uri
import com.muthu.disciplinex.alarm.AlarmScheduler
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local backup file (JSON) of alarms, streak days, completed-challenge history and the
 * allowed-apps list. The file only ever goes where the user saves it; nothing is uploaded.
 *
 * Restore is deliberately limited so it cannot be used to cheat the app:
 *  - not while a challenge/lock is active, or an alarm is inside its 2-hour commit window
 *  - only if no streak days or history are recorded yet (a fresh install / new phone),
 *    so an old backup can never undo a missed day.
 * Live session state (ChallengeSession) and UI settings are never part of a backup.
 */
object BackupManager {
    const val FORMAT_VERSION = 1
    const val MAX_BYTES = 5_000_000

    // Same names as StreakStore / HistoryStore (those keep them private); keep in sync.
    private const val STREAK_PREFS = "disciplinex_streak"
    private const val STREAK_KEY = "days"
    private const val HISTORY_PREFS = "disciplinex_history"
    private const val HISTORY_KEY = "entries"

    enum class RestoreResult { OK, INVALID_FILE, BLOCKED_HISTORY, BLOCKED_ACTIVE, BLOCKED_ALARM_SOON, IO_ERROR }

    class Parsed(
        val alarms: List<AlarmEntry>,
        val streakJson: String,
        val historyJson: String,
        /** null = the file had no list, leave the current one alone. */
        val allowedApps: Set<String>?
    )

    // ---------------------------------------------------------- pure (JVM-testable)

    fun buildJson(
        alarms: List<AlarmEntry>,
        streakJson: String,
        historyJson: String,
        allowedApps: Set<String>,
        exportedAt: Long
    ): String {
        val alarmArr = JSONArray()
        alarms.forEach { alarmArr.put(it.toJson()) }
        val apps = JSONArray()
        allowedApps.sorted().forEach { apps.put(it) }
        return JSONObject()
            .put("app", "DisciplineX")
            .put("format", FORMAT_VERSION)
            .put("exportedAt", exportedAt)
            .put("alarms", alarmArr)
            .put("streak", objectOrEmpty(streakJson))
            .put("history", arrayOrEmpty(historyJson))
            .put("allowedApps", apps)
            .toString(2)
    }

    /** Returns null for anything that is not a complete, valid DisciplineX backup. */
    fun parse(text: String): Parsed? {
        try {
            val root = JSONObject(text)
            if (root.optString("app") != "DisciplineX") return null
            if (root.optInt("format", 0) != FORMAT_VERSION) return null

            val alarmArr = root.getJSONArray("alarms")
            val alarms = ArrayList<AlarmEntry>()
            for (i in 0 until alarmArr.length()) {
                alarms += AlarmEntry.fromJson(alarmArr.getJSONObject(i))
            }
            if (alarms.map { it.id }.toSet().size != alarms.size) return null

            val streak = root.getJSONObject("streak")
            val keys = streak.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                LocalDate.parse(key)
                val counters = streak.getJSONArray(key)
                if (counters.length() < 2) return null
                counters.getInt(0)
                counters.getInt(1)
            }

            val history = root.getJSONArray("history")
            for (i in 0 until history.length()) history.getJSONObject(i)

            val appsArr = root.optJSONArray("allowedApps")
            val apps = if (appsArr == null) null
            else (0 until appsArr.length()).map { appsArr.getString(it) }.toSet()

            return Parsed(alarms, streak.toString(), history.toString(), apps)
        } catch (e: Exception) {
            return null
        }
    }

    private fun objectOrEmpty(s: String): JSONObject =
        try { JSONObject(s) } catch (e: Exception) { JSONObject() }

    private fun arrayOrEmpty(s: String): JSONArray =
        try { JSONArray(s) } catch (e: Exception) { JSONArray() }

    // ------------------------------------------------------------------ Android I/O

    fun export(context: Context, uri: Uri): Boolean = try {
        val streak = context.getSharedPreferences(STREAK_PREFS, Context.MODE_PRIVATE)
            .getString(STREAK_KEY, "{}") ?: "{}"
        val history = context.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE)
            .getString(HISTORY_KEY, "[]") ?: "[]"
        val json = buildJson(
            AlarmStore.getAll(context), streak, history,
            UserPrefs.getAllowedApps(context), System.currentTimeMillis()
        )
        val out = context.contentResolver.openOutputStream(uri, "wt")
        if (out == null) false else out.use { it.write(json.toByteArray(Charsets.UTF_8)); true }
    } catch (e: Exception) {
        false
    }

    /** Why a restore must be refused right now (see the class comment), or null if it is allowed. */
    fun restoreBlockReason(context: Context): RestoreResult? {
        if (ChallengeSession.isLockActive(context)) return RestoreResult.BLOCKED_ACTIVE
        if (AlarmStore.getAll(context).any { AlarmScheduler.isCommitted(it) }) return RestoreResult.BLOCKED_ALARM_SOON
        if (StreakStore.allDays(context).isNotEmpty() || HistoryStore.getAll(context).isNotEmpty()) {
            return RestoreResult.BLOCKED_HISTORY
        }
        return null
    }

    fun restore(context: Context, uri: Uri): RestoreResult {
        restoreBlockReason(context)?.let { return it }
        val text = try {
            readLimited(context, uri) ?: return RestoreResult.INVALID_FILE
        } catch (e: Exception) {
            return RestoreResult.IO_ERROR
        }
        // Everything is validated BEFORE anything is changed.
        val parsed = parse(text) ?: return RestoreResult.INVALID_FILE
        return try {
            AlarmStore.getAll(context).forEach {
                AlarmScheduler.cancel(context, it.id)
                AlarmStore.delete(context, it.id)
            }
            parsed.alarms.forEach { AlarmStore.save(context, it) }
            context.getSharedPreferences(STREAK_PREFS, Context.MODE_PRIVATE)
                .edit().putString(STREAK_KEY, parsed.streakJson).apply()
            context.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE)
                .edit().putString(HISTORY_KEY, parsed.historyJson).apply()
            parsed.allowedApps?.let { UserPrefs.setAllowedApps(context, it) }
            AlarmScheduler.scheduleAll(context)
            RestoreResult.OK
        } catch (e: Exception) {
            RestoreResult.IO_ERROR
        }
    }

    /** Reads the file as UTF-8 text; null if it is larger than [MAX_BYTES]. */
    private fun readLimited(context: Context, uri: Uri): String? {
        val input = context.contentResolver.openInputStream(uri) ?: throw java.io.IOException("no stream")
        return input.use { stream ->
            val buffer = ByteArray(8192)
            val out = ByteArrayOutputStream()
            var tooBig = false
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > MAX_BYTES) {
                    tooBig = true
                    break
                }
            }
            if (tooBig) null else out.toString("UTF-8")
        }
    }
}
