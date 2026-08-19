package com.muthu.disciplinex.alarm

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.muthu.disciplinex.MainActivity

/**
 * Invisible trampoline for the notification's "Dismiss" action.
 *
 * Tapping "Dismiss" on the alarm notification used to just stop the sound
 * (via AlarmService.ACTION_STOP) with no further navigation — if the user
 * dismissed that way instead of pressing the on-screen "Dismiss & Start
 * Challenge" button, they'd land wherever they already were instead of the
 * challenge screen. This activity makes both dismiss paths behave the same:
 * stop the alarm AND open straight into the challenge.
 */
class DismissAlarmActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AlarmService.stop(this)

        val challengeIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_START_CHALLENGE, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(challengeIntent)
        finish()
    }
}
