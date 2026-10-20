package com.muthu.disciplinex.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.muthu.disciplinex.MainActivity
import com.muthu.disciplinex.data.UserPrefs
import java.util.Calendar
import java.util.Locale

/**
 * Schedules and cancels the daily wake-time alarm (and the short "snooze" alarm).
 *
 * Uses [AlarmManager.setAlarmClock] — the API real clock apps use. Unlike
 * setExact*(), it needs NO special permission (Android 14 denies
 * SCHEDULE_EXACT_ALARM by default), is not delayed by Doze, and lets the
 * receiver start the alarm foreground service from the background.
 *
 * Reads the saved wake time from [UserPrefs] (format: "5:00 AM" / "12:30 PM"),
 * computes the next trigger time (today if it hasn't passed yet, else tomorrow).
 * [AlarmReceiver] re-schedules the next day's alarm each time it fires.
 */
object AlarmScheduler {

    private const val REQUEST_CODE_WAKE_ALARM = 1001
    private const val REQUEST_CODE_SHOW_APP = 1002
    private const val REQUEST_CODE_SNOOZE = 1003

    fun scheduleWakeAlarm(context: Context) {
        val wakeTime = UserPrefs.getWakeTime(context)
        val triggerAtMillis = nextTriggerTimeMillis(wakeTime) ?: return
        setClockAlarm(context, triggerAtMillis, wakeAlarmPendingIntent(context))
    }

    /** One-off alarm [minutes] from now. Does not touch the daily alarm. */
    fun scheduleSnooze(context: Context, minutes: Int) {
        val triggerAtMillis = System.currentTimeMillis() + minutes * 60_000L
        setClockAlarm(context, triggerAtMillis, snoozePendingIntent(context))
    }

    fun cancelWakeAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(wakeAlarmPendingIntent(context))
    }

    private fun setClockAlarm(context: Context, triggerAtMillis: Long, operation: PendingIntent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Shown when the user taps the alarm icon in the status bar / clock.
        val showIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_SHOW_APP,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
            operation
        )
    }

    private fun wakeAlarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE_WAKE_ALARM, intent, flags)
    }

    private fun snoozePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_IS_SNOOZE, true)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE_SNOOZE, intent, flags)
    }

    /**
     * Parses a "h:mm a" style string (e.g. "5:00 AM") and returns the next
     * epoch-millis timestamp for that time, rolling over to tomorrow if the
     * time has already passed today.
     */
    private fun nextTriggerTimeMillis(wakeTime: String): Long? {
        val parts = wakeTime.trim().split(" ")
        if (parts.size != 2) return null

        val timeParts = parts[0].split(":")
        if (timeParts.size != 2) return null

        val hour12 = timeParts[0].toIntOrNull() ?: return null
        val minute = timeParts[1].toIntOrNull() ?: return null
        val isAm = parts[1].uppercase(Locale.ROOT) == "AM"

        val hour24 = when {
            isAm && hour12 == 12 -> 0
            !isAm && hour12 != 12 -> hour12 + 12
            else -> hour12
        }

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour24)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        return target.timeInMillis
    }
}
