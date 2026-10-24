package com.muthu.disciplinex.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Step check for the Walking challenge, so it can't be passed by leaving the
 * phone on a table. Uses the phone's step counter. If the phone has no step
 * counter, or the "Physical activity" permission is refused, the check is
 * skipped (the timer alone decides) - a missing sensor must never lock someone in.
 */
object WalkSteps {
    /** A slow walk is about 80-100 steps a minute; this is deliberately lenient. */
    const val STEPS_PER_MINUTE = 50

    private const val PREFS = "disciplinex_walk"
    private const val K_SESSION = "session"
    private const val K_BASE = "base"

    fun required(minutes: Int): Int = minutes.coerceAtLeast(1) * STEPS_PER_MINUTE

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Steps taken since this challenge started, from the phone's running total. */
    fun stepsSince(context: Context, total: Long): Int {
        val p = prefs(context)
        val session = ChallengeSession.sessionId(context)
        var base = p.getLong(K_BASE, -1L)
        // New alarm session, first reading, or a reboot (the total restarts from 0).
        if (p.getLong(K_SESSION, -1L) != session || base < 0 || total < base) {
            base = total
            p.edit().putLong(K_SESSION, session).putLong(K_BASE, base).apply()
        }
        return (total - base).toInt().coerceAtLeast(0)
    }

    /** Start counting from zero again (used when the 5-minute away rule resets progress). */
    fun reset(context: Context) {
        prefs(context).edit().putLong(K_SESSION, -1L).apply()
    }
}

/**
 * Live step count for the Walking challenge: null while unavailable (no sensor,
 * no permission, or not a walking challenge). Pass a changing [refresh] value
 * after the permission was just granted.
 */
@Composable
fun rememberWalkSteps(enabled: Boolean, refresh: Int): MutableState<Int?> {
    val context = LocalContext.current
    val steps = remember { mutableStateOf<Int?>(null) }
    DisposableEffect(enabled, refresh) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (!enabled || sm == null || sensor == null || !WalkSteps.hasPermission(context)) {
            return@DisposableEffect onDispose { }
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.isNotEmpty()) {
                    steps.value = WalkSteps.stepsSince(context, event.values[0].toLong())
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return steps
}
