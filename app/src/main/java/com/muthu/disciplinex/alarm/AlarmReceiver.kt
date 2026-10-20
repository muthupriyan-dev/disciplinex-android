package com.muthu.disciplinex.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.muthu.disciplinex.data.UserPrefs

/**
 * Fires when the daily wake alarm (or a snooze alarm) goes off. Starts
 * [AlarmService], which plays the sound/vibration as a foreground service and
 * shows [AlarmRingActivity]. If the service can't start for any reason, a
 * backup high-priority notification is posted instead so the alarm is never
 * silently lost.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val isSnooze = intent.getBooleanExtra(EXTRA_IS_SNOOZE, false)

        if (isSnooze) {
            // The challenge was finished or skipped while snoozing — nothing to ring for.
            if (!UserPrefs.isLockActive(context)) return
        } else {
            // Re-arm for tomorrow first, in case starting the service is delayed.
            AlarmScheduler.scheduleWakeAlarm(context)
            // Apps stay locked until the challenge is completed.
            UserPrefs.startLock(context)
        }

        try {
            AlarmService.start(context)
        } catch (e: Exception) {
            Log.e(TAG, "Could not start alarm service, using backup notification", e)
            AlarmFallback.show(context)
        }
    }

    companion object {
        const val EXTRA_IS_SNOOZE = "is_snooze"
        private const val TAG = "AlarmReceiver"
    }
}
