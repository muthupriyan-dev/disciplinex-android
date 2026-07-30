package com.muthu.disciplinex.data

import android.content.Context
import android.content.SharedPreferences

object UserPrefs {
    private const val PREFS_NAME = "disciplinex_prefs"
    private const val KEY_WAKE_TIME = "wake_time"
    private const val KEY_DURATION = "duration"
    private const val KEY_EXERCISE = "exercise"
    private const val KEY_ONBOARDED = "onboarded"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveOnboarding(context: Context, wakeTime: String, duration: String, exercise: String) {
        prefs(context).edit()
            .putString(KEY_WAKE_TIME, wakeTime)
            .putString(KEY_DURATION, duration)
            .putString(KEY_EXERCISE, exercise)
            .putBoolean(KEY_ONBOARDED, true)
            .apply()
    }

    fun getWakeTime(context: Context): String = prefs(context).getString(KEY_WAKE_TIME, "5:00 AM") ?: "5:00 AM"
    fun getDuration(context: Context): String = prefs(context).getString(KEY_DURATION, "20 min") ?: "20 min"
    fun getExercise(context: Context): String = prefs(context).getString(KEY_EXERCISE, "Push-ups") ?: "Push-ups"
    fun isOnboarded(context: Context): Boolean = prefs(context).getBoolean(KEY_ONBOARDED, false)
}
