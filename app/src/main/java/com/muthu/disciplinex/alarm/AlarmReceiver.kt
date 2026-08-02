package com.muthu.disciplinex.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires when the daily wake alarm goes off. Starts [AlarmService], which
 * plays the sound/vibration as a foreground service (so it keeps ringing
 * even if the user leaves the app or locks the phone) and shows
 * [AlarmRingActivity] for the dismiss UI. Also immediately re-schedules
 * tomorrow's alarm, since exact alarms in Android are one-shot.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Re-arm for tomorrow first, in case starting the service is delayed.
        AlarmScheduler.scheduleWakeAlarm(context)
        AlarmService.start(context)
    }
}
