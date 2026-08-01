package com.muthu.disciplinex.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.muthu.disciplinex.MainActivity

/**
 * Fires when the daily wake alarm goes off.
 *
 * For now this launches [MainActivity] with a flag telling it to jump straight
 * into the challenge flow (the live challenge/timer screen itself is the next
 * piece to build). It also immediately re-schedules tomorrow's alarm, since
 * exact alarms in Android are one-shot.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Toast.makeText(context, "DEBUG: AlarmReceiver fired!", Toast.LENGTH_LONG).show()

        // Re-arm for tomorrow first, in case launching the activity is delayed.
        AlarmScheduler.scheduleWakeAlarm(context)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(EXTRA_LAUNCH_CHALLENGE, true)
        }
        context.startActivity(launchIntent)
    }

    companion object {
        const val EXTRA_LAUNCH_CHALLENGE = "launch_challenge"
    }
}
