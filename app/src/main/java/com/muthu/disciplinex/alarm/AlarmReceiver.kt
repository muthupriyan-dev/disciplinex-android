package com.muthu.disciplinex.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.muthu.disciplinex.data.AlarmStore
import com.muthu.disciplinex.data.ChallengeSession

/**
 * Fires when one of the alarms in the list goes off. Re-arms (or, for a
 * "once" alarm, switches off) that alarm, starts the challenge session (lock
 * ON + 10-minute start window) and starts [AlarmService]. If the service can't
 * start, a backup high-priority notification is posted instead so the alarm is
 * never silently lost.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        val alarm = AlarmStore.get(context, alarmId) ?: return
        if (!alarm.enabled) return // switched off after this system alarm was queued

        if (alarm.daysMask == 0) {
            // "Once" alarm: it has rung, so switch it off.
            AlarmStore.save(context, alarm.copy(enabled = false))
        } else {
            // Re-arm for the next matching day first, in case starting the service is delayed.
            AlarmScheduler.schedule(context, alarm, after = System.currentTimeMillis() + 60_000L)
        }

        // The lock lasts until the next alarm (max 24 h) unless the challenge is done.
        ChallengeSession.start(context, alarm, AlarmScheduler.nextAlarmMillis(context))

        try {
            AlarmService.start(context)
        } catch (e: Exception) {
            Log.e(TAG, "Could not start alarm service, using backup notification", e)
            AlarmFallback.show(context)
        }
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        private const val TAG = "AlarmReceiver"
    }
}
