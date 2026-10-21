package com.muthu.disciplinex.data

import android.content.Context
import org.json.JSONArray

/**
 * Saves the user's alarms (any number of them) as JSON in SharedPreferences.
 * Older installs had exactly one alarm stored in [UserPrefs]; it is converted
 * into the first list entry the first time the list is read.
 */
object AlarmStore {
    private const val PREFS_NAME = "disciplinex_alarms"
    private const val KEY_ALARMS = "alarms"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(context: Context): List<AlarmEntry> {
        val p = prefs(context)
        if (!p.contains(KEY_ALARMS)) {
            val legacy = UserPrefs.legacyAlarmOrNull(context)
            writeAll(context, if (legacy != null) listOf(legacy) else emptyList())
        }
        val text = p.getString(KEY_ALARMS, "[]") ?: "[]"
        val list = ArrayList<AlarmEntry>()
        try {
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                list += AlarmEntry.fromJson(arr.getJSONObject(i))
            }
        } catch (e: Exception) {
            // Corrupt data: start from an empty list instead of crashing.
        }
        return list.sortedBy { it.hour24 * 60 + it.minute }
    }

    fun get(context: Context, id: Int): AlarmEntry? = getAll(context).firstOrNull { it.id == id }

    fun nextId(context: Context): Int = (getAll(context).maxOfOrNull { it.id } ?: 0) + 1

    /** Adds the alarm, or replaces the one with the same id. */
    fun save(context: Context, alarm: AlarmEntry) {
        val list = getAll(context).filter { it.id != alarm.id } + alarm
        writeAll(context, list)
    }

    fun delete(context: Context, id: Int) {
        writeAll(context, getAll(context).filter { it.id != id })
    }

    private fun writeAll(context: Context, list: List<AlarmEntry>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs(context).edit().putString(KEY_ALARMS, arr.toString()).apply()
    }
}
