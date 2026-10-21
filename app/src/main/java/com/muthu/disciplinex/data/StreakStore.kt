package com.muthu.disciplinex.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Daily results for the streak. Each day keeps two counters: challenges done
 * and challenges missed. A day is a SUCCESS only if it has at least one done
 * and zero missed. One miss (never started within 10 minutes, or never
 * finished) breaks the streak.
 */
object StreakStore {
    private const val PREFS_NAME = "disciplinex_streak"
    private const val KEY_DAYS = "days"

    enum class DayStatus { DONE, MISSED, NONE }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun load(context: Context): JSONObject = try {
        JSONObject(prefs(context).getString(KEY_DAYS, "{}") ?: "{}")
    } catch (e: Exception) {
        JSONObject()
    }

    fun record(context: Context, date: LocalDate, success: Boolean) {
        val obj = load(context)
        val key = date.toString()
        val arr = obj.optJSONArray(key)
        var done = arr?.optInt(0) ?: 0
        var missed = arr?.optInt(1) ?: 0
        if (success) done++ else missed++
        obj.put(key, JSONArray().put(done).put(missed))
        prefs(context).edit().putString(KEY_DAYS, obj.toString()).apply()
    }

    private fun statusOf(obj: JSONObject, date: LocalDate): DayStatus {
        val arr = obj.optJSONArray(date.toString()) ?: return DayStatus.NONE
        return when {
            arr.optInt(1) > 0 -> DayStatus.MISSED
            arr.optInt(0) > 0 -> DayStatus.DONE
            else -> DayStatus.NONE
        }
    }

    fun status(context: Context, date: LocalDate): DayStatus = statusOf(load(context), date)

    /** The last 7 days, oldest first, ending today. */
    fun lastSevenDays(context: Context): List<Pair<LocalDate, DayStatus>> {
        val obj = load(context)
        val today = LocalDate.now()
        return (6 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            d to statusOf(obj, d)
        }
    }

    /** A day with no result only breaks the streak if one of your repeating alarms was due that day. */
    private fun wasScheduled(alarms: List<AlarmEntry>, date: LocalDate): Boolean =
        alarms.any { it.enabled && it.daysMask != 0 && ((it.daysMask shr (date.dayOfWeek.value - 1)) and 1) == 1 }

    private fun earliest(obj: JSONObject): LocalDate? {
        var best: LocalDate? = null
        val keys = obj.keys()
        while (keys.hasNext()) {
            val d = try { LocalDate.parse(keys.next()) } catch (e: Exception) { continue }
            if (best == null || d.isBefore(best)) best = d
        }
        return best
    }

    fun currentStreak(context: Context): Int {
        val obj = load(context)
        val first = earliest(obj) ?: return 0
        val alarms = AlarmStore.getAll(context)
        val today = LocalDate.now()
        var streak = 0
        var i = 0
        while (true) {
            val d = today.minusDays(i.toLong())
            if (d.isBefore(first)) break
            when (statusOf(obj, d)) {
                DayStatus.MISSED -> return streak
                DayStatus.DONE -> streak++
                DayStatus.NONE -> if (i > 0 && wasScheduled(alarms, d)) return streak
            }
            i++
        }
        return streak
    }

    fun bestStreak(context: Context): Int {
        val obj = load(context)
        val first = earliest(obj) ?: return 0
        val alarms = AlarmStore.getAll(context)
        val today = LocalDate.now()
        var run = 0
        var best = 0
        var d = first
        while (!d.isAfter(today)) {
            when (statusOf(obj, d)) {
                DayStatus.DONE -> { run++; if (run > best) best = run }
                DayStatus.MISSED -> run = 0
                DayStatus.NONE -> if (d != today && wasScheduled(alarms, d)) run = 0
            }
            d = d.plusDays(1)
        }
        return best
    }
}
