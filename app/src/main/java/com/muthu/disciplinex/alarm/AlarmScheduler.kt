package com.muthu.disciplinex.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.muthu.disciplinex.data.UserPrefs
import java.util.Calendar
import java.util.Locale

/**
 * Schedules and cancels the daily wake-time alarm.
 *
 * Reads the saved wake time from [UserPrefs] (format: "5:00 AM" / "12:30 PM"),
 * computes the next trigger time (today if it hasn't passed yet, else tomorrow),
 * and schedules an exact alarm via [AlarmManager]. [AlarmReceiver] re-schedules
 * the next day's alarm each time it fires, so this only needs to be called once
 * after onboarding (or whenever the wake time changes).
 */
object AlarmScheduler {

    private const val REQUEST_CODE_WAKE_ALARM = 1001

    fun scheduleWakeAlarm(context: Context) {
        val wakeTime = UserPrefs.getWakeTime(context) ?: return
        val triggerAtMillis = nextTriggerTimeMillis(wakeTime) ?: return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = wakeAlarmPendingIntent(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Caller should have already asked for SCHEDULE_EXACT_ALARM permission
            // on the PermissionsScreen. Fall back to an inexact alarm so the app
            // still fires (just not guaranteed to the minute).
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            return
        }

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    fun cancelWakeAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(wakeAlarmPendingIntent(context))
    }

    private fun wakeAlarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE_WAKE_ALARM, intent, flags)
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
