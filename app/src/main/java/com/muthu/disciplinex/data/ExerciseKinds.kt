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

    fun isCamera(name: String) = name in CAMERA
    fun isTimed(name: String) = name in TIMED
}
