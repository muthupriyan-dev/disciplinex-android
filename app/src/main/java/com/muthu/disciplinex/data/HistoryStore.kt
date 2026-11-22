package com.muthu.disciplinex.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Permanent record of every COMPLETED challenge (what, how much, how long).
 * [StreakStore] only keeps per-day done/missed counters; this adds the detail
 * that statistics and achievements need. Only real completions are written
 * (from [ChallengeSession.complete], which guards against double recording).
 *
 * Stored as JSON in SharedPreferences, like the other stores, so no migration
 * of existing data is needed. Older installs simply start with an empty list.
 */
object HistoryStore {
    private const val PREFS_NAME = "disciplinex_history"
    private const val KEY_ENTRIES = "entries"

    /** Keeps the JSON blob bounded; oldest entries are dropped first. */
    private const val MAX_ENTRIES = 5000

    data class Entry(
        val completedAtMillis: Long,
        /** ISO date (yyyy-MM-dd) of the alarm that started the challenge. */
        val date: String,
        val exercise: String,
        val timed: Boolean,
        /** Target reps, or target minutes for timed exercises. */
        val target: Int,
        /** Time from the alarm ringing to completion. */
        val durationMs: Long,
        /** True for the Walking challenge: [target] is then metres. */
        val distance: Boolean = false
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(context: Context): List<Entry> {
        val text = prefs(context).getString(KEY_ENTRIES, "[]") ?: "[]"
        val out = ArrayList<Entry>()
        try {
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out += Entry(
                    completedAtMillis = o.optLong("t"),
                    date = o.optString("d"),
                    exercise = o.optString("e"),
                    timed = o.optBoolean("tm"),
                    target = o.optInt("n"),
                    durationMs = o.optLong("ms"),
                    distance = o.optBoolean("ds")
                )
            }
        } catch (e: Exception) {
            // Corrupt data: behave as empty instead of crashing.
        }
        return out
    }

    fun add(context: Context, entry: Entry) {
        val list = (getAll(context) + entry).takeLast(MAX_ENTRIES)
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("t", it.completedAtMillis)
                    .put("d", it.date)
                    .put("e", it.exercise)
                    .put("tm", it.timed)
                    .put("n", it.target)
                    .put("ms", it.durationMs)
                    .put("ds", it.distance)
            )
        }
        prefs(context).edit().putString(KEY_ENTRIES, arr.toString()).commit()
    }
}
