package com.muthu.disciplinex.data

/**
 * How each exercise is completed:
 *  - CAMERA: reps are counted by pose detection (target = number of reps)
 *  - TIMED:  a countdown that only runs while the challenge screen is open
 *            (target = minutes). There is NO way to add progress by tapping.
 *  - anything else: rep-based with manual +1 (motion sensors arrive later)
 */
object ExerciseKinds {
    val CAMERA = setOf("Push-ups", "Squats")
    val TIMED = setOf("Reading", "Meditation", "Walking")

    /** The name shown to the user. The stored name stays English, it is only an id. */
    fun display(name: String): String = when (name) {
        "Push-ups" -> tr("Push-ups", "புஷ்-அப்ஸ்")
        "Squats" -> tr("Squats", "ஸ்குவாட்ஸ்")
        "Jumping Jacks" -> tr("Jumping Jacks", "ஜம்பிங் ஜாக்ஸ்")
        "Walking" -> tr("Walking", "நடைப்பயிற்சி")
        "Meditation" -> tr("Meditation", "தியானம்")
        "Reading" -> tr("Reading", "வாசிப்பு")
        else -> name
    }

    fun isCamera(name: String) = name in CAMERA
    fun isTimed(name: String) = name in TIMED
}
