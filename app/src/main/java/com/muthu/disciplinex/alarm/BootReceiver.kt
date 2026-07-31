package com.muthu.disciplinex.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.muthu.disciplinex.data.UserPrefs

/**
 * Re-schedules the wake alarm after a device reboot, since AlarmManager
 * alarms don't survive a restart. Only fires if onboarding is already done
 * (i.e. there's a saved wake time to restore).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!UserPrefs.isOnboarded(context)) return

        AlarmScheduler.scheduleWakeAlarm(context)
    }
}
