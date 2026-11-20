package com.muthu.disciplinex.domain

import com.muthu.disciplinex.data.HistoryStore
import java.time.LocalDate

/** One day of the weekly chart. */
data class DayBar(val date: LocalDate, val done: Int, val missed: Int)

/** Totals for one exercise (from [HistoryStore], so only challenges completed since v1.2.1). */
data class ExerciseStat(
    val exercise: String,
    val timed: Boolean,
    val completed: Int,
    /** Sum of reps (rep exercises) or minutes (timed exercises). */
    val total: Int
)

data class Stats(
    val totalDone: Int,
    val totalMissed: Int,
    /** done / (done + missed) in percent, or null when there is no data yet. */
    val completionPercent: Int?,
    /** Reps/minutes only count challenges recorded by [HistoryStore]. */
    val totalReps: Int,
    val totalMinutes: Int,
    val last7: List<DayBar>,
    val monthDone: Int,
    val monthMissed: Int,
    val perExercise: List<ExerciseStat>
)

/**
 * Pure logic (no Android classes) so it can be unit-tested on the JVM.
 * Every number comes from stored activity; nothing is estimated or invented.
 *
 * @param days per-day (done, missed) counters from StreakStore
 */
object StatsCalculator {

    fun compute(
        days: Map<LocalDate, Pair<Int, Int>>,
        history: List<HistoryStore.Entry>,
        today: LocalDate
    ): Stats {
        var done = 0
        var missed = 0
        for ((_, v) in days) {
            done += v.first.coerceAtLeast(0)
            missed += v.second.coerceAtLeast(0)
        }
        val percent = if (done + missed == 0) null else (done * 100) / (done + missed)

        val last7 = (6 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val v = days[d]
            DayBar(d, v?.first ?: 0, v?.second ?: 0)
        }

        var monthDone = 0
        var monthMissed = 0
        for ((d, v) in days) {
            if (d.year == today.year && d.month == today.month) {
                monthDone += v.first.coerceAtLeast(0)
                monthMissed += v.second.coerceAtLeast(0)
            }
        }

        val rep = history.filter { !it.timed }
        val timed = history.filter { it.timed }

        val perExercise = history
            .groupBy { it.exercise }
            .map { (name, list) ->
                ExerciseStat(
                    exercise = name,
                    timed = list.first().timed,
                    completed = list.size,
                    total = list.sumOf { it.target.coerceAtLeast(0) }
                )
            }
            .sortedByDescending { it.completed }

        return Stats(
            totalDone = done,
            totalMissed = missed,
            completionPercent = percent,
            totalReps = rep.sumOf { it.target.coerceAtLeast(0) },
            totalMinutes = timed.sumOf { it.target.coerceAtLeast(0) },
            last7 = last7,
            monthDone = monthDone,
            monthMissed = monthMissed,
            perExercise = perExercise
        )
    }
}
