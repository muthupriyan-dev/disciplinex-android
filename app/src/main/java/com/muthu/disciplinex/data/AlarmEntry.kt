package com.muthu.disciplinex.data

import org.json.JSONObject

/**
 * One alarm in the alarm list (like a normal clock app).
 *
 * @param daysMask bit0 = Monday ... bit6 = Sunday. 0 means "once" (rings at the
 *                 next occurrence of the time, then switches itself off).
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
    val enabled: Boolean
) {
    val isTimed: Boolean get() = ExerciseKinds.isTimed(exercise)
    val summary: String get() = if (isTimed) "$minutes min" else "$reps reps"

    val hour12: Int get() = ((hour24 + 11) % 12) + 1
    val isAm: Boolean get() = hour24 < 12
    val timeText: String get() = "$hour12:${minute.toString().padStart(2, '0')}"
    val periodText: String get() = if (isAm) "AM" else "PM"

    val repeatText: String
        get() = when (daysMask) {
            0 -> "Once"
            ALL_DAYS -> "Every day"
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

    companion object {
        const val ALL_DAYS = 127
        val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        fun fromJson(o: JSONObject): AlarmEntry = AlarmEntry(
            id = o.optInt("id"),
            hour24 = o.optInt("h", 5),
            minute = o.optInt("m", 0),
            daysMask = o.optInt("d", ALL_DAYS),
            label = o.optString("l", ""),
            exercise = o.optString("e", "Push-ups"),
            reps = o.optInt("r", 10),
            minutes = o.optInt("n", 15),
            enabled = o.optBoolean("on", true)
        )

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
