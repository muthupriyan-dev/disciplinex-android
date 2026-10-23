package com.muthu.disciplinex.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.muthu.disciplinex.data.AppSettings

/**
 * "Test alarm sound" in Settings: plays the chosen ringtone (and vibration) for a
 * few seconds so volume, sound and vibration can be checked. It does NOT start
 * a challenge and does NOT lock anything.
 */
object AlarmTest {
    private const val TEST_MS = 6_000L

    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val stopRunnable = Runnable { stop() }

    var isPlaying by mutableStateOf(false)
        private set

    fun start(context: Context) {
        stop()
        val app = context.applicationContext
        try {
            val uri: Uri = AppSettings.ringtoneUri(app)?.let { Uri.parse(it) }
                ?: RingtoneManager.getActualDefaultRingtoneUri(app, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val rt = RingtoneManager.getRingtone(app, uri)
            if (rt != null) {
                rt.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                rt.play()
                ringtone = rt
            }
        } catch (e: Exception) {
            Log.e("AlarmTest", "sound failed", e)
        }

        try {
            if (AppSettings.vibrate(app)) {
                val v: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))
                vibrator = v
            }
        } catch (e: Exception) {
            Log.e("AlarmTest", "vibration failed", e)
        }

        isPlaying = true
        handler.postDelayed(stopRunnable, TEST_MS)
    }

    fun stop() {
        handler.removeCallbacks(stopRunnable)
        try { ringtone?.stop() } catch (e: Exception) { Log.e("AlarmTest", "stop ringtone", e) }
        try { vibrator?.cancel() } catch (e: Exception) { Log.e("AlarmTest", "stop vibration", e) }
        ringtone = null
        vibrator = null
        isPlaying = false
    }
}
