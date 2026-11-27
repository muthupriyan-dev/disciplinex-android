package com.muthu.disciplinex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsTest {
    private val ids = listOf("first", "streak3", "streak7", "streak30", "done100", "reps500")

    @Test fun sixAchievements_inOrder_withTheirGoals() {
        val list = Achievements.evaluate(totalDone = 0, bestStreak = 0, totalReps = 0)
        assertEquals(ids, list.map { it.id })
        assertEquals(listOf(1, 3, 7, 30, 100, 500), list.map { it.goal })
    }

    @Test fun nothingUnlocked_withoutActivity() {
        assertTrue(Achievements.evaluate(0, 0, 0).none { it.unlocked })
    }

    @Test fun progressComesFromTheInputs() {
        val list = Achievements.evaluate(totalDone = 1, bestStreak = 1, totalReps = 50).associateBy { it.id }
        assertEquals(1, list.getValue("first").progress)
        assertEquals(1, list.getValue("streak3").progress)
        assertEquals(1, list.getValue("streak7").progress)
        assertEquals(1, list.getValue("streak30").progress)
        assertEquals(1, list.getValue("done100").progress)
        assertEquals(50, list.getValue("reps500").progress)
        assertTrue(list.getValue("first").unlocked)
        assertFalse(list.getValue("streak3").unlocked)
    }

    @Test fun unlocksExactlyAtTheGoal() {
        val below = Achievements.evaluate(totalDone = 99, bestStreak = 29, totalReps = 499)
        assertEquals(listOf("first", "streak3", "streak7"), below.filter { it.unlocked }.map { it.id })
        val at = Achievements.evaluate(totalDone = 100, bestStreak = 30, totalReps = 500)
        assertTrue(at.all { it.unlocked })
    }

    @Test fun fractionIsClampedBetweenZeroAndOne() {
        val list = Achievements.evaluate(totalDone = 1000, bestStreak = 500, totalReps = 9000)
        assertTrue(list.all { it.fraction == 1f })
        assertEquals(0f, Achievements.evaluate(0, 0, 0).first().fraction, 0f)
    }

    @Test fun everyBadgeHasADescription() {
        assertTrue(Achievements.evaluate(0, 0, 0).all { it.descEn.isNotBlank() && it.descTa.isNotBlank() })
    }
}
