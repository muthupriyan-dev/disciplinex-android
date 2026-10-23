package com.muthu.disciplinex.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * One alarm in the alarm list (like a normal clock app).
 *
 * @param daysMask bit0 = Monday ... bit6 = Sunday. 0 means "once" (rings at the
 *                 next occurrence of the time, then switches itself off).
 * @param dates    "Custom" repeat: specific calendar dates (as epoch days) the alarm
 *                 rings on. When not empty it replaces [daysMask]; after the last
 *                 date has rung the alarm switches itself off.
 * @param reps     target for rep exercises (push-ups, squats, jumping jacks)
 * @param minutes  target for timed exercises (walking, meditation, reading)
 */
data class AlarmEntry(
    val id: Int,
    val hour24: Int,
    val minute: Int,
    val daysMask: Int,
    val label: String,
    val exercise: String,
    val reps: Int,
    val minutes: Int,
    val enabled: Boolean,
    val dates: List<Long> = emptyList(),
    /** "Finish within" time limit, counted from the moment the alarm rings. */
    val windowMinutes: Int = DEFAULT_WINDOW_MINUTES
) {
    val isCustom: Boolean get() = dates.isNotEmpty()
    val isOnce: Boolean get() = daysMask == 0 && dates.isEmpty()

    val isTimed: Boolean get() = ExerciseKinds.isTimed(exercise)
    val summary: String get() = if (isTimed) tr("$minutes min", "$minutes நிமிடம்") else tr("$reps reps", "$reps முறை")

    val hour12: Int get() = ((hour24 + 11) % 12) + 1
    val isAm: Boolean get() = hour24 < 12
    val timeText: String get() = "$hour12:${minute.toString().padStart(2, '0')}"
    val periodText: String get() = if (isAm) "AM" else "PM"

    val repeatText: String
        get() = when {
            dates.isNotEmpty() -> datesText(dates)
            else -> weekdayText
        }

    private val weekdayText: String
        get() = when (daysMask) {
            0 -> tr("Once", "ஒருமுறை")
            ALL_DAYS -> tr("Every day", "தினமும்")
            else -> DAY_NAMES.filterIndexed { i, _ -> (daysMask shr i) and 1 == 1 }.joinToString(", ")
        }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("h", hour24)
        .put("m", minute)
        .put("d", daysMask)
        .put("l", label)
        .put("e", exercise)
        .put("r", reps)
        .put("n", minutes)
        .put("on", enabled)
        .put("w", windowMinutes)
        .put("dt", JSONArray().also { arr -> dates.forEach { arr.put(it) } })

    companion object {
        const val ALL_DAYS = 127
        val DAY_NAMES: List<String>
            get() = listOf(
                tr("Mon", "திங்"), tr("Tue", "செவ்"), tr("Wed", "புதன்"), tr("Thu", "வியா"),
                tr("Fri", "வெள்"), tr("Sat", "சனி"), tr("Sun", "ஞாயிறு")
            )

        /** One short label per weekday for the round day buttons. */
        val DAY_LETTERS: List<String>
            get() = listOf(
                tr("M", "தி"), tr("T", "செ"), tr("W", "பு"), tr("T", "வி"),
                tr("F", "வெ"), tr("S", "ச"), tr("S", "ஞா")
            )

        const val DEFAULT_WINDOW_MINUTES = 15

        fun fromJson(o: JSONObject): AlarmEntry {
            val exercise = o.optString("e", "Push-ups")
            val minutes = o.optInt("n", 15)
            // Alarms saved before the time limit existed: a sensible limit for their exercise.
            val savedWindow = o.optInt("w", -1)
            val window = when {
                savedWindow > 0 -> savedWindow
                ExerciseKinds.isTimed(exercise) -> minutes + 10
                else -> DEFAULT_WINDOW_MINUTES
            }
            return AlarmEntry(
                id = o.optInt("id"),
                hour24 = o.optInt("h", 5),
                minute = o.optInt("m", 0),
                daysMask = o.optInt("d", ALL_DAYS),
                label = o.optString("l", ""),
                exercise = exercise,
                reps = o.optInt("r", 10),
                minutes = minutes,
                enabled = o.optBoolean("on", true),
                dates = o.optJSONArray("dt")?.let { arr ->
                    (0 until arr.length()).map { arr.optLong(it) }.sorted()
                } ?: emptyList(),
                windowMinutes = window
            )
        }

        fun dateText(epochDay: Long): String =
            LocalDate.ofEpochDay(epochDay)
                .format(DateTimeFormatter.ofPattern("EEE, d MMM", AppSettings.locale))

        /** "Fri, 24 Oct" for one date, "3 dates" for several. */
        fun datesText(dates: List<Long>): String =
            if (dates.size == 1) dateText(dates[0]) else tr("${dates.size} dates", "${dates.size} தேதிகள்")

        /** "5:30 AM" -> hour24 and minute. Returns null if malformed. */
        fun parseClock(text: String): Pair<Int, Int>? {
            val parts = text.trim().split(" ")
            if (parts.size != 2) return null
            val hm = parts[0].split(":")
            if (hm.size != 2) return null
            val h12 = hm[0].toIntOrNull() ?: return null
            val m = hm[1].toIntOrNull() ?: return null
            val isAm = parts[1].uppercase() == "AM"
            val h24 = when {
                isAm && h12 == 12 -> 0
                !isAm && h12 != 12 -> h12 + 12
                else -> h12
            }
            return h24 to m
        }
    }
}
