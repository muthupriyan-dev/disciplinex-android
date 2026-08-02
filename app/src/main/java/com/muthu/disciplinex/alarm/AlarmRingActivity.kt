package com.muthu.disciplinex.alarm

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.Ringtone
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muthu.disciplinex.data.UserPrefs
import com.muthu.disciplinex.ui.theme.OrangeEnd

/**
 * Shown when the wake alarm fires. Displays over the lock screen, turns the
 * screen on, plays a looping alarm sound + vibration until the user dismisses it.
 *
 * Every non-UI side effect (sound, vibration, lock-screen flags) is wrapped in
 * try/catch: on some OEM ROMs the default alarm ringtone URI or vibrator
 * service can behave unexpectedly, and a crash here must never take down the
 * whole screen — the UI (and the ability to dismiss) has to survive regardless.
 */
class AlarmRingActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var originalAlarmVolume: Int? = null
    private var toneGenerator: ToneGenerator? = null
    private val toneHandler = Handler(Looper.getMainLooper())
    private var toneRunnable: Runnable? = null
    private var ringtone: Ringtone? = null
    private var ringtoneLoopRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        safely("showOverLockScreen") { showOverLockScreen() }
        safely("startAlarmSound") { startAlarmSound() }
        safely("startVibration") { startVibration() }

        val exerciseName = UserPrefs.getExercise(this)
        val duration = UserPrefs.getDuration(this)

        setContent {
            AlarmRingScreen(
                exerciseName = exerciseName,
                duration = duration,
                onDismiss = { stopAlarmAndFinish() }
            )
        }
    }

    private inline fun safely(label: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.e("AlarmRingActivity", "$label failed", e)
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        keyguardManager?.requestDismissKeyguard(this, null)
    }

    private fun startAlarmSound() {
        // Force the ALARM stream up — on many OEM ROMs (Vivo/OriginOS included)
        // there's a separate "alarm volume" that can be 0 even when media/ringer
        // volume is fine, in which case USAGE_ALARM audio plays completely silently.
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val current = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (current <= 0) {
                originalAlarmVolume = current
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, (max * 0.7).toInt().coerceAtLeast(1), 0)
            }
        } catch (e: Exception) {
            Log.e("AlarmRingActivity", "raising alarm volume failed", e)
        }

        val alarmUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: run {
                Toast.makeText(this, "No alarm sound file found — using beep fallback", Toast.LENGTH_SHORT).show()
                startFallbackTone()
                return
            }

        // Try 1: android.media.Ringtone — this is what the system's own alarm
        // clock app uses to play the user's chosen ringtone, and it's far more
        // reliable across OEM ROMs than manually calling MediaPlayer.setDataSource
        // on a content:// URI (which fails with a system error on some devices,
        // e.g. Vivo/OriginOS).
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
            Log.e("AlarmRingActivity", "startAlarmSound (Ringtone) failed", e)
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
                setDataSource(this@AlarmRingActivity, alarmUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            // Both playback paths failed — fall back to a synthesized beep loop
            // via ToneGenerator, which needs no file/URI at all.
            Log.e("AlarmRingActivity", "startAlarmSound (MediaPlayer) failed", e)
            mediaPlayer?.release()
            mediaPlayer = null
            Toast.makeText(this, "Alarm ringtone unavailable — using beep fallback", Toast.LENGTH_SHORT).show()
            startFallbackTone()
        }
    }

    /** Pre-API28 devices: [Ringtone] has no setLooping, so poll and restart it manually. */
    private fun startRingtoneLoopWatcher(rt: Ringtone) {
        val runnable = object : Runnable {
            override fun run() {
                if (ringtone === rt && !rt.isPlaying) {
                    try { rt.play() } catch (e: Exception) {
                        Log.e("AlarmRingActivity", "ringtone loop restart failed", e)
                    }
                }
                toneHandler.postDelayed(this, 500)
            }
        }
        ringtoneLoopRunnable = runnable
        toneHandler.postDelayed(runnable, 500)
    }

    private fun stopRingtone() {
        ringtoneLoopRunnable?.let { toneHandler.removeCallbacks(it) }
        ringtoneLoopRunnable = null
        safely("stopRingtone") { ringtone?.stop() }
        ringtone = null
    }

    private fun startFallbackTone() {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
            val runnable = object : Runnable {
                override fun run() {
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 700)
                    toneHandler.postDelayed(this, 1200)
                }
            }
            toneRunnable = runnable
            toneHandler.post(runnable)
        } catch (e: Exception) {
            Log.e("AlarmRingActivity", "startFallbackTone failed", e)
        }
    }

    private fun stopFallbackTone() {
        toneRunnable?.let { toneHandler.removeCallbacks(it) }
        toneRunnable = null
        toneGenerator?.release()
        toneGenerator = null
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

    private fun stopAlarmAndFinish() {
        safely("stopAlarmAndFinish") {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        vibrator?.cancel()
        stopRingtone()
        stopFallbackTone()
        restoreAlarmVolume()
        finish()
    }

    private fun restoreAlarmVolume() {
        val original = originalAlarmVolume ?: return
        safely("restoreAlarmVolume") {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, original, 0)
        }
        originalAlarmVolume = null
    }

    override fun onDestroy() {
        safely("onDestroy release") { mediaPlayer?.release() }
        mediaPlayer = null
        vibrator?.cancel()
        stopRingtone()
        stopFallbackTone()
        restoreAlarmVolume()
        super.onDestroy()
    }

    override fun onBackPressed() {
        // Swallow back button — alarm must be dismissed via the button, not skipped.
    }
}

@Composable
private fun AlarmRingScreen(
    exerciseName: String,
    duration: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OrangeEnd),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Wake Up!",
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "$exerciseName · $duration to complete",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(64.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = OrangeEnd
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Dismiss & Start Challenge", fontWeight = FontWeight.Bold)
            }
        }
    }
}
