package com.muthu.disciplinex.domain

data class Achievement(
    val id: String,
    val titleEn: String,
    val titleTa: String,
    val goal: Int,
    /** Real progress towards [goal]; may exceed it. */
    val progress: Int,
    /** What the number means, e.g. "challenges", "days", "reps". */
    val unitEn: String,
    val unitTa: String,
    /** One short line telling the user how to earn the badge. */
    val descEn: String = "",
    val descTa: String = ""
) {
    val unlocked: Boolean get() = progress >= goal
    val fraction: Float get() = if (goal <= 0) 1f else (progress.toFloat() / goal).coerceIn(0f, 1f)
}

/**
 * Achievements are computed from stored activity every time, so they can only
 * be unlocked by real completed challenges. Nothing is granted manually.
 */
object Achievements {
    fun evaluate(totalDone: Int, bestStreak: Int, totalReps: Int): List<Achievement> = listOf(
        Achievement("first", "First Challenge", "முதல் சவால்", 1, totalDone, "challenges", "சவால்கள்", "Complete your first challenge", "உங்கள் முதல் சவாலை முடியுங்கள்"),
        Achievement("streak3", "3-Day Streak", "3 நாள் தொடர்ச்சியான சாதனை", 3, bestStreak, "days", "நாட்கள்", "Complete challenges for 3 days", "3 நாட்கள் தொடர்ந்து சவால்களை முடியுங்கள்"),
        Achievement("streak7", "7-Day Streak", "7 நாள் தொடர்ச்சியான சாதனை", 7, bestStreak, "days", "நாட்கள்", "Complete challenges for 7 days", "7 நாட்கள் தொடர்ந்து சவால்களை முடியுங்கள்"),
        Achievement("streak30", "30-Day Streak", "30 நாள் தொடர்ச்சியான சாதனை", 30, bestStreak, "days", "நாட்கள்", "Complete challenges for 30 days", "30 நாட்கள் தொடர்ந்து சவால்களை முடியுங்கள்"),
        Achievement("done100", "100 Challenges", "100 சவால்கள்", 100, totalDone, "challenges", "சவால்கள்", "Complete 100 challenges", "100 சவால்களை முடியுங்கள்"),
        Achievement("reps500", "500 Repetitions", "500 முறைகள்", 500, totalReps, "reps", "முறை", "Do 500 repetitions", "500 முறைகள் செய்யுங்கள்")
    )
}
