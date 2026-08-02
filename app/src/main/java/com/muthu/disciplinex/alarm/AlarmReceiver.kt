package com.muthu.disciplinex.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires when the daily wake alarm goes off. Launches [AlarmRingActivity]
 * (full-screen, sound + vibration, shown over the lock screen) and
 * immediately re-schedules tomorrow's alarm, since exact alarms in
 * Android are one-shot.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Re-arm for tomorrow first, in case launching the activity is delayed.
        AlarmScheduler.scheduleWakeAlarm(context)

        val launchIntent = Intent(context, AlarmRingActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        context.startActivity(launchIntent)
    }
}
