package com.muthu.disciplinex.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BackupManagerTest {
    private val alarm = AlarmEntry(1, 6, 30, 31, "Gym", "Push-ups", 20, 15, true)
    private val streak = """{"2026-11-19":[1,0],"2026-11-20":[0,1]}"""
    private val history =
        """[{"t":1,"d":"2026-11-19","e":"Push-ups","tm":false,"n":20,"ms":60000}]"""

    private fun valid() = BackupManager.buildJson(listOf(alarm), streak, history, setOf("com.a", "com.b"), 123L)

    @Test fun roundTrip_keepsEverything() {
        val p = BackupManager.parse(valid())
        assertNotNull(p)
        assertEquals(listOf(alarm.id), p!!.alarms.map { it.id })
        assertEquals("Gym", p.alarms.first().label)
        assertEquals(setOf("com.a", "com.b"), p.allowedApps)
        assertEquals(1, org.json.JSONArray(p.historyJson).length())
        assertEquals(2, org.json.JSONObject(p.streakJson).length())
    }

    @Test fun rejectsGarbageAndWrongApp() {
        assertNull(BackupManager.parse("not json"))
        assertNull(BackupManager.parse("""{"app":"Other","format":1}"""))
    }

    @Test fun rejectsWrongFormatVersion() {
        assertNull(BackupManager.parse(valid().replace("\"format\": 1", "\"format\": 99")))
    }

    @Test fun rejectsBadStreakDate() {
        val bad = BackupManager.buildJson(listOf(alarm), """{"yesterday":[1,0]}""", history, emptySet(), 1L)
        assertNull(BackupManager.parse(bad))
    }

    @Test fun rejectsDuplicateAlarmIds() {
        val bad = BackupManager.buildJson(listOf(alarm, alarm), streak, history, emptySet(), 1L)
        assertNull(BackupManager.parse(bad))
    }
}
