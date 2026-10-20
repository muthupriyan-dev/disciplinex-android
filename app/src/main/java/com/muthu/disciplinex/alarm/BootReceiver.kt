package com.muthu.disciplinex.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.muthu.disciplinex.data.UserPrefs

/**
 * Re-schedules the wake alarm whenever Android may have dropped or shifted it:
 * after a reboot, after the app is updated, and when the phone's time or
 * timezone changes. Only does anything once onboarding is done (i.e. there
 * is a saved wake time to restore).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                if (!UserPrefs.isOnboarded(context)) return
                AlarmScheduler.scheduleWakeAlarm(context)
            }
        }
    }
}
