package com.muthu.disciplinex.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.muthu.disciplinex.MainActivity
import com.muthu.disciplinex.data.AlarmEntry
import com.muthu.disciplinex.data.AlarmStore
import java.time.Instant
import java.time.ZoneId

/**
 * Schedules every alarm in the list. Each alarm gets its own system alarm
 * (request code = REQUEST_BASE + alarm id).
 *
 * Uses [AlarmManager.setAlarmClock] — the API real clock apps use. It needs NO
 * special permission, is not delayed by Doze, and lets the receiver start the
 * alarm foreground service from the background.
 */
object AlarmScheduler {

    private const val REQUEST_BASE = 2000
    private const val REQUEST_SHOW_APP = 1002

    /** (Re)arms every enabled alarm and cancels the disabled ones. */
    fun scheduleAll(context: Context) {
        AlarmStore.getAll(context).forEach { alarm ->
            if (alarm.enabled) schedule(context, alarm) else cancel(context, alarm.id)
        }
    }

    /** @param after only trigger times later than this are used (defaults to now). */
    fun schedule(context: Context, alarm: AlarmEntry, after: Long = System.currentTimeMillis()) {
        val triggerAtMillis = nextTriggerMillis(alarm, after) ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Shown when the user taps the alarm icon in the status bar / clock.
        val showIntent = PendingIntent.getActivity(
            context,
            REQUEST_SHOW_APP,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
            alarmPendingIntent(context, alarm.id)
        )
    }

    fun cancel(context: Context, alarmId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(alarmPendingIntent(context, alarmId))
    }

    /** The soonest time any enabled alarm will ring (null if none). */
    fun nextAlarmMillis(context: Context): Long? =
        AlarmStore.getAll(context)
            .filter { it.enabled }
            .mapNotNull { nextTriggerMillis(it, System.currentTimeMillis()) }
            .minOrNull()

    private fun alarmPendingIntent(context: Context, alarmId: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_BASE + alarmId, intent, flags)
    }

    /** The next time [alarm] should ring that is strictly after [after], or null. */
    fun nextTriggerMillis(alarm: AlarmEntry, after: Long): Long? {
        val zone = ZoneId.systemDefault()
        val nowDt = Instant.ofEpochMilli(after).atZone(zone).toLocalDateTime()

        // "Custom" alarm: the earliest chosen date whose time is still ahead.
        if (alarm.dates.isNotEmpty()) {
            return alarm.dates.sorted()
                .map { java.time.LocalDate.ofEpochDay(it).atTime(alarm.hour24, alarm.minute) }
                .firstOrNull { it.isAfter(nowDt) }
                ?.atZone(zone)?.toInstant()?.toEpochMilli()
        }

        for (offset in 0..7) {
            val date = nowDt.toLocalDate().plusDays(offset.toLong())
            val dayBit = (alarm.daysMask shr (date.dayOfWeek.value - 1)) and 1
            if (alarm.daysMask != 0 && dayBit == 0) continue
            val dt = date.atTime(alarm.hour24, alarm.minute)
            if (dt.isAfter(nowDt)) return dt.atZone(zone).toInstant().toEpochMilli()
        }
        return null
    }
}
