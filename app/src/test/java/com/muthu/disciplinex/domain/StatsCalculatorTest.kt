package com.muthu.disciplinex.domain

import com.muthu.disciplinex.data.HistoryStore
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsCalculatorTest {
    private val today = LocalDate.of(2026, 11, 20)

    private fun entry(ex: String, timed: Boolean, target: Int) =
        HistoryStore.Entry(0L, today.toString(), ex, timed, target, 1000L)

    @Test fun emptyData_hasNoPercentAndZeroTotals() {
        val s = StatsCalculator.compute(emptyMap(), emptyList(), today)
        assertEquals(0, s.totalDone)
        assertNull(s.completionPercent)
        assertEquals(7, s.last7.size)
        assertEquals(today, s.last7.last().date)
    }

    @Test fun completionPercent_usesDoneOverDoneAndMissed() {
        val days = mapOf(today to (3 to 1))
        val s = StatsCalculator.compute(days, emptyList(), today)
        assertEquals(75, s.completionPercent)
    }

    @Test fun monthTotals_ignoreOtherMonths() {
        val days = mapOf(today to (1 to 0), LocalDate.of(2026, 10, 30) to (5 to 5))
        val s = StatsCalculator.compute(days, emptyList(), today)
        assertEquals(1, s.monthDone)
        assertEquals(0, s.monthMissed)
        assertEquals(6, s.totalDone)
    }

    @Test fun repsAndMinutes_areSeparated() {
        val h = listOf(entry("Push-ups", false, 20), entry("Squats", false, 30), entry("Reading", true, 15))
        val s = StatsCalculator.compute(emptyMap(), h, today)
        assertEquals(50, s.totalReps)
        assertEquals(15, s.totalMinutes)
        assertTrue(s.perExercise.any { it.exercise == "Squats" && it.total == 30 })
    }

    @Test fun achievements_unlockOnlyFromRealProgress() {
        val a = Achievements.evaluate(totalDone = 0, bestStreak = 0, totalReps = 0)
        assertTrue(a.none { it.unlocked })
        val b = Achievements.evaluate(totalDone = 1, bestStreak = 7, totalReps = 499)
        assertTrue(b.first { it.id == "first" }.unlocked)
        assertTrue(b.first { it.id == "streak7" }.unlocked)
        assertTrue(!b.first { it.id == "streak30" }.unlocked)
        assertTrue(!b.first { it.id == "reps500" }.unlocked)
    }
}
