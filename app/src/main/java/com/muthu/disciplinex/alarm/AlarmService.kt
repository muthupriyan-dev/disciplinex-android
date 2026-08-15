package com.muthu.disciplinex.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Owns the alarm sound + vibration for as long as the alarm is ringing.
 *
 * This runs as a foreground service — deliberately NOT tied to
 * [AlarmRingActivity]'s lifecycle — so the sound keeps playing even if the
 * user leaves the app, opens another app, or locks the phone. Some OEM ROMs
 * (Vivo/OriginOS included) aggressively freeze or kill backgrounded
 * activities to save battery, which was silently cutting the alarm sound
 * whenever the Activity itself owned the MediaPlayer/Ringtone.
 */
class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private var originalAlarmVolume: Int? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var loopRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        launchRingActivity()
        safely("acquireWakeLock") { acquireWakeLock() }
        safely("startAlarmSound") { startAlarmSound() }
        safely("startVibration") { startVibration() }
        return START_STICKY
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "DisciplineX:AlarmWakeLock"
        ).apply {
            // Safety cap so it can never hold the CPU awake forever if something
            // goes wrong and stopEverything() is never reached.
            acquire(20 * 60 * 1000L)
        }
    }

    private inline fun safely(label: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.e("AlarmService", "$label failed", e)
        }
    }

    private fun launchRingActivity() {
        val launchIntent = Intent(this, AlarmRingActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        startActivity(launchIntent)
    }

    private fun buildNotification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Alarm", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "DisciplineX wake-up alarm"
                setSound(null, null) // we play the alarm sound ourselves, not via the notification
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, AlarmService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Wake up!")
            .setContentText("Your DisciplineX challenge is waiting.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(0, "Dismiss", stopPendingIntent)
            .build()
    }

    private fun startAlarmSound() {
        // Force the ALARM stream up — on many OEM ROMs there's a separate
        // "alarm volume" that can be 0 even when media/ringer volume is fine.
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val current = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (current <= 0) {
                originalAlarmVolume = current
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, (max * 0.7).toInt().coerceAtLeast(1), 0)
            }
        } catch (e: Exception) {
            Log.e("AlarmService", "raising alarm volume failed", e)
        }

        val alarmUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: run { startFallbackTone(); return }

        // Try 1: android.media.Ringtone — this is what the system's own alarm
        // clock app uses, and it's far more reliable across OEM ROMs than
        // MediaPlayer.setDataSource on a content:// URI.
        try {
            val rt = RingtoneManager.getRingtone(this, alarmUri)
            if (rt != null) {
                rt.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    rt.isLooping = true
                } else {
                    startRingtoneLoopWatcher(rt)
                }
                rt.play()
                ringtone = rt
                return
            }
        } catch (e: Exception) {
            Log.e("AlarmService", "Ringtone playback failed", e)
        }

        // Try 2: raw MediaPlayer against the same URI.
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, alarmUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("AlarmService", "MediaPlayer playback failed", e)
            mediaPlayer?.release()
            mediaPlayer = null
            startFallbackTone()
        }
    }

    /** Pre-API28 devices: [Ringtone] has no setLooping, so poll and restart it manually. */
    private fun startRingtoneLoopWatcher(rt: Ringtone) {
        val runnable = object : Runnable {
            override fun run() {
                if (ringtone === rt && !rt.isPlaying) {
                    try { rt.play() } catch (e: Exception) {
                        Log.e("AlarmService", "ringtone loop restart failed", e)
                    }
                }
                handler.postDelayed(this, 500)
            }
        }
        loopRunnable = runnable
        handler.postDelayed(runnable, 500)
    }

    private fun startFallbackTone() {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
            val runnable = object : Runnable {
                override fun run() {
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 700)
                    handler.postDelayed(this, 1200)
                }
            }
            loopRunnable = runnable
            handler.post(runnable)
        } catch (e: Exception) {
            Log.e("AlarmService", "startFallbackTone failed", e)
        }
    }

    private fun startVibration() {
        val pattern = longArrayOf(0, 500, 500)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun stopEverything() {
        loopRunnable?.let { handler.removeCallbacks(it) }
        loopRunnable = null

        safely("stop ringtone") { ringtone?.stop() }
        ringtone = null

        safely("stop mediaPlayer") {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null

        safely("release toneGenerator") { toneGenerator?.release() }
        toneGenerator = null

        vibrator?.cancel()

        originalAlarmVolume?.let { original ->
            safely("restore alarm volume") {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, original, 0)
            }
        }
        originalAlarmVolume = null

        safely("release wakeLock") {
            wakeLock?.let { if (it.isHeld) it.release() }
        }
        wakeLock = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.muthu.disciplinex.alarm.ACTION_STOP"
        private const val CHANNEL_ID = "alarm_channel"
        private const val NOTIFICATION_ID = 1001

        /** Called from AlarmReceiver (background context) — must use startForegroundService. */
        fun start(context: Context) {
            val intent = Intent(context, AlarmService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Called from AlarmRingActivity's Dismiss button (foreground/visible context). */
        fun stop(context: Context) {
            val intent = Intent(context, AlarmService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }
}
