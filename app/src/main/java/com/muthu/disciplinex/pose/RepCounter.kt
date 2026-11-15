package com.muthu.disciplinex.pose

import android.os.SystemClock
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot

/** Result of feeding one pose frame into a [RepCounter]. */
data class RepUpdate(
    val reps: Int,
    /** 0f (bottom of rep) .. 1f (top of rep) — drives the progress ring during a live rep. */
    val liveProgress: Float,
    /** True when a real body is being tracked. False shows a "get in frame" hint. */
    val isTracking: Boolean,
    /** Short live readout (angle / state) shown under the camera while testing. */
    val debug: String = ""
)

/**
 * One instance per challenge session — feed it every analyzed frame's [Pose].
 *
 * Same idea as the web version: ONE joint angle decides everything.
 *   angle < DOWN  -> "down"
 *   angle > UP    -> back up -> rep + 1
 * No plank / displacement checks, so normal-looking reps always count.
 */
interface RepCounter {
    fun onFrame(pose: Pose?): RepUpdate
}

private const val MIN_CONFIDENCE = 0.4f
private const val LOSS_GRACE_FRAMES = 10
private const val MIN_REP_INTERVAL_MS = 400L

private fun Pose.lm(type: Int): PoseLandmark? {
    val l = getPoseLandmark(type) ?: return null
    return if (l.inFrameLikelihood >= MIN_CONFIDENCE) l else null
}

/** Angle (degrees) at joint b, formed by a-b-c. */
private fun angleAt(a: PoseLandmark, b: PoseLandmark, c: PoseLandmark): Double {
    val abx = (a.position.x - b.position.x).toDouble()
    val aby = (a.position.y - b.position.y).toDouble()
    val cbx = (c.position.x - b.position.x).toDouble()
    val cby = (c.position.y - b.position.y).toDouble()
    val mag = hypot(abx, aby) * hypot(cbx, cby)
    if (mag == 0.0) return 180.0
    val cos = ((abx * cbx + aby * cby) / mag).coerceIn(-1.0, 1.0)
    return Math.toDegrees(acos(cos))
}

/**
 * Shared engine for angle-based exercises (push-up = elbow, squat = knee).
 * Picks whichever body side (left / right) is visible, never mixing sides,
 * and sticks with it so it doesn't flip every frame.
 */
private abstract class AngleRepCounter(
    private val left: IntArray,
    private val right: IntArray,
    private val downBelow: Double,
    private val upAbove: Double,
    private val minDownToUpMs: Long,
    private val label: String
) : RepCounter {

    private var preferLeft = true
    private var down = false
    private var reps = 0
    private var lossFrames = 0
    private var downAtMs = 0L
    private var lastRepMs = 0L
    private val history = ArrayDeque<Double>()

    private fun pickSide(pose: Pose?): List<PoseLandmark>? {
        if (pose == null) return null
        val l = left.map { pose.lm(it) }
        val r = right.map { pose.lm(it) }
        val lOk = l.all { it != null }
        val rOk = r.all { it != null }
        val useLeft = when {
            lOk && rOk -> preferLeft
            lOk -> true
            rOk -> false
            else -> return null
        }
        preferLeft = useLeft
        return (if (useLeft) l else r).map { it!! }
    }

    override fun onFrame(pose: Pose?): RepUpdate {
        val j = pickSide(pose)
        if (j == null) {
            lossFrames++
            if (lossFrames > LOSS_GRACE_FRAMES) {
                down = false
                history.clear()
                return RepUpdate(reps, 0f, isTracking = false, debug = "no body")
            }
            // brief flicker: keep state, don't reset, don't count
            return RepUpdate(reps, if (down) 0.3f else 1f, isTracking = false, debug = "flicker")
        }
        lossFrames = 0

        history.addLast(angleAt(j[0], j[1], j[2]))
        if (history.size > 3) history.removeFirst()
        val angle = history.average()

        val now = SystemClock.elapsedRealtime()
        if (!down && angle < downBelow) {
            down = true
            downAtMs = now
        } else if (down && angle > upAbove) {
            down = false
            if (now - downAtMs >= minDownToUpMs && now - lastRepMs >= MIN_REP_INTERVAL_MS) {
                reps++
                lastRepMs = now
            }
        }

        val progress = ((angle - downBelow) / (upAbove - downBelow)).toFloat().coerceIn(0f, 1f)
        val debug = "$label ${angle.toInt()}\u00b0 \u00b7 down<${downBelow.toInt()} up>${upAbove.toInt()} \u00b7 " +
            if (down) "DOWN" else "UP"
        return RepUpdate(reps, progress, isTracking = true, debug = debug)
    }
}

/** Push-up: shoulder–elbow–wrist angle. Bent below 100 = down, straight above 145 = up. */
class PushUpRepCounter : RepCounter by AngleRepCounter(
    left = intArrayOf(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST),
    right = intArrayOf(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST),
    downBelow = 100.0,
    upAbove = 145.0,
    minDownToUpMs = 200L,
    label = "elbow"
)

/** Squat: hip–knee–ankle angle. Bent below 105 = down, standing above 150 = up. */
class SquatRepCounter : RepCounter by AngleRepCounter(
    left = intArrayOf(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE),
    right = intArrayOf(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE),
    downBelow = 105.0,
    upAbove = 150.0,
    minDownToUpMs = 200L,
    label = "knee"
)

/**
 * Jumping jacks (not in the web version, kept lenient).
 * Open = hands above shoulders (and feet apart if feet are visible).
 * Closed = hands back down (and feet together if visible).
 */
class JumpingJackRepCounter : RepCounter {
    private var open = false
    private var reps = 0
    private var lossFrames = 0
    private var openedAtMs = 0L
    private var lastRepMs = 0L

    override fun onFrame(pose: Pose?): RepUpdate {
        val ls = pose?.lm(PoseLandmark.LEFT_SHOULDER)
        val rs = pose?.lm(PoseLandmark.RIGHT_SHOULDER)
        val lw = pose?.lm(PoseLandmark.LEFT_WRIST)
        val rw = pose?.lm(PoseLandmark.RIGHT_WRIST)
        val la = pose?.lm(PoseLandmark.LEFT_ANKLE)
        val ra = pose?.lm(PoseLandmark.RIGHT_ANKLE)

        if (ls == null || rs == null || lw == null || rw == null) {
            lossFrames++
            if (lossFrames > LOSS_GRACE_FRAMES) {
                open = false
                return RepUpdate(reps, 0f, isTracking = false, debug = "no body")
            }
            return RepUpdate(reps, if (open) 1f else 0f, isTracking = false, debug = "flicker")
        }
        lossFrames = 0

        val shoulderWidth = abs(ls.position.x - rs.position.x).coerceAtLeast(1f)
        val shoulderY = (ls.position.y + rs.position.y) / 2f
        val wristY = (lw.position.y + rw.position.y) / 2f
        val armLift = (shoulderY - wristY) / shoulderWidth // >0 hands above shoulders
        val feetKnown = la != null && ra != null
        val feetGap = if (feetKnown) abs(la!!.position.x - ra!!.position.x) / shoulderWidth else 0f

        val now = SystemClock.elapsedRealtime()
        if (!open) {
            if (armLift > 0.3f && (!feetKnown || feetGap > 1.2f)) {
                open = true
                openedAtMs = now
            }
        } else if (armLift < -0.3f && (!feetKnown || feetGap < 1.6f)) {
            if (now - openedAtMs >= 100L && now - lastRepMs >= 300L) {
                reps++
                lastRepMs = now
            }
            open = false
        }

        val progress = ((armLift + 0.5f) / 1.5f).coerceIn(0f, 1f)
        val debug = "arms ${"%.1f".format(armLift)} \u00b7 feet " +
            (if (feetKnown) "%.1f".format(feetGap) else "n/a") + " \u00b7 " + if (open) "OPEN" else "CLOSED"
        return RepUpdate(reps, progress, isTracking = true, debug = debug)
    }
}

fun repCounterFor(exerciseName: String): RepCounter? = when (exerciseName) {
    "Push-ups" -> PushUpRepCounter()
    "Squats" -> SquatRepCounter()
    "Jumping Jacks" -> JumpingJackRepCounter()
    else -> null
}
