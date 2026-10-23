package com.muthu.disciplinex.alarm

import com.muthu.disciplinex.data.tr
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Backup path used only if [AlarmService] could not be started. Posts a
 * high-priority alarm notification (with the system alarm sound and a
 * full-screen intent) so the user still gets woken up.
 */
object AlarmFallback {
    private const val TAG = "AlarmFallback"
    private const val CHANNEL_ID = "alarm_fallback_channel"
    private const val NOTIFICATION_ID = 1002

    fun show(context: Context) {
        try {
            com.muthu.disciplinex.data.AppSettings.load(context)
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID, "Alarm (backup)", NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Used only if the main alarm service can't start"
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }

            val ringIntent = Intent(context, AlarmRingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 2, ringIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(tr("Wake up!", "எழுந்திரு!"))
                .setContentText(tr("Tap to start your DisciplineX challenge.", "உங்கள் DisciplineX சேலஞ்சை தொடங்க தட்டுங்கள்."))
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)
                .build()

            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Backup alarm notification failed", e)
        }
    }
}
