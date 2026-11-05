package com.muthu.disciplinex.pose

import android.os.SystemClock
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

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
 * Base contract for a camera-based rep counter. One instance per challenge
 * session — feed it every analyzed frame's [Pose] via [onFrame].
 *
 * Counting is deliberately lenient: if the movement looks like the normal
 * exercise (down, then back up) it counts. Only a very short wiggle is ignored.
 * A grace period absorbs single-frame tracking flicker.
 */
interface RepCounter {
    fun onFrame(pose: Pose?): RepUpdate
}

private const val MIN_LANDMARK_CONFIDENCE = 0.5f
private const val TRACKING_LOSS_GRACE_FRAMES = 10

/** Bottom -> top of one rep can't be faster than this. */
private const val MIN_DOWN_TO_UP_MS = 200L

/** Two counted reps can't be closer together than this. */
private const val MIN_REP_INTERVAL_MS = 500L

private fun Pose.landmarkOrNull(type: Int): PoseLandmark? {
    val lm = getPoseLandmark(type) ?: return null
    return if (lm.inFrameLikelihood >= MIN_LANDMARK_CONFIDENCE) lm else null
}

/**
 * Picks ONE body side (left or right) on which every needed joint is
 * confidently visible. Mixing sides (e.g. left shoulder + right elbow) gives
 * a meaningless angle, so joints are never taken from different sides. Sticks
 * with the side used last frame so it doesn't flip back and forth.
 */
private class SidePicker(
    private val left: IntArray,
    private val right: IntArray,
    /** The first [required] landmarks must be visible; the rest are optional (null if hidden). */
    private val required: Int = left.size
) {
    private var preferLeft = true

    fun pick(pose: Pose?): List<PoseLandmark?>? {
        if (pose == null) return null
        val l = left.map { pose.landmarkOrNull(it) }
        val r = right.map { pose.landmarkOrNull(it) }
        val leftOk = l.take(required).all { it != null }
        val rightOk = r.take(required).all { it != null }
        val useLeft = when {
            leftOk && rightOk -> preferLeft
            leftOk -> true
            rightOk -> false
            else -> return null
        }
        preferLeft = useLeft
        return if (useLeft) l else r
    }
}

/** Average of the last few angle readings, so one noisy frame can't fake or cancel a rep. */
private class AngleSmoother(private val size: Int = 4) {
    private val values = ArrayDeque<Double>()
    fun add(v: Double): Double {
        values.addLast(v)
        while (values.size > size) values.removeFirst()
        return values.average()
    }
    fun clear() = values.clear()
}

/**
 * Push-up counter. Lenient: a rep = "bent" then "straight again".
 *
 * "Bend" (0..1) is the larger of two signals, so it works from the side AND
 * from the front (camera at the head end, like the stick-figure videos):
 *  - elbow angle (best from the side)
 *  - shoulder-to-wrist distance shrinking vs. its recent max (best from the front,
 *    where the elbow angle is foreshortened)
 * Only one sanity check stays: if the hip is visible and the body is bolt
 * upright (standing/sitting), nothing counts.
 */
class PushUpRepCounter : RepCounter {
    private val sides = SidePicker(
        intArrayOf(
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW,
            PoseLandmark.LEFT_WRIST, PoseLandmark.LEFT_HIP
        ),
        intArrayOf(
            PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.RIGHT_WRIST, PoseLandmark.RIGHT_HIP
        ),
        required = 3
    )
    private val smoother = AngleSmoother(4)
    private var state = State.UP
    private var reps = 0
    private var trackingLossFrames = 0
    private var downAtMs = 0L
    private var lastRepAtMs = 0L
    private var peakAngle = 150.0
    private var maxReach = 0f

    private enum class State { UP, DOWN }

    override fun onFrame(pose: Pose?): RepUpdate {
        val joints = sides.pick(pose)

        if (joints == null) {
            trackingLossFrames++
            if (trackingLossFrames > TRACKING_LOSS_GRACE_FRAMES) {
                state = State.UP
                smoother.clear()
                maxReach = 0f
                return RepUpdate(reps, 0f, isTracking = false, debug = "no body")
            }
            return RepUpdate(reps, if (state == State.DOWN) 0.5f else 1f, isTracking = false, debug = "flicker")
        }
        trackingLossFrames = 0

        val shoulder = joints[0]!!
        val elbow = joints[1]!!
        val wrist = joints[2]!!
        val hip = joints[3]

        val angle = smoother.add(jointAngle(shoulder.position3D, elbow.position3D, wrist.position3D))

        // Shoulder-to-wrist distance on screen, relative to its recent maximum.
        val reach = kotlin.math.hypot(
            (shoulder.position.x - wrist.position.x).toDouble(),
            (shoulder.position.y - wrist.position.y).toDouble()
        ).toFloat()
        maxReach = if (reach > maxReach) reach else maxReach * 0.9995f
        val reachRatio = reach / maxReach.coerceAtLeast(1f)

        if (angle > peakAngle) peakAngle = angle else peakAngle -= 0.02
        peakAngle = peakAngle.coerceIn(140.0, 175.0)

        val angleBend = ((peakAngle - angle) / (peakAngle - 95.0)).coerceIn(0.0, 1.0)
        val reachBend = ((1.0 - reachRatio) / 0.30).coerceIn(0.0, 1.0)
        val bend = kotlin.math.max(angleBend, reachBend)

        val tilt = hip?.let { tiltFromHorizontal(shoulder.position3D, it.position3D) }
        val upright = tilt != null && tilt > 80.0

        val now = SystemClock.elapsedRealtime()
        when (state) {
            State.UP -> {
                if (bend > 0.55 && !upright) {
                    state = State.DOWN
                    downAtMs = now
                }
            }
            State.DOWN -> {
                if (bend < 0.30) {
                    if (now - downAtMs >= MIN_DOWN_TO_UP_MS && now - lastRepAtMs >= MIN_REP_INTERVAL_MS) {
                        reps++
                        lastRepAtMs = now
                    }
                    state = State.UP
                }
            }
        }

        val debug = "elbow ${angle.toInt()}\u00b0 \u00b7 reach ${(reachRatio * 100).toInt()}% \u00b7 " +
            "bend ${(bend * 100).toInt()}% \u00b7 ${state.name}"
        return RepUpdate(reps, (1.0 - bend).toFloat(), isTracking = true, debug = debug)
    }
}

/**
 * Squat counter. Lenient: knee bends (below ~125 degrees), then stands back
 * up (above ~150 degrees) = 1 rep. No depth or hip-drop checks.
 */
class SquatRepCounter : RepCounter {
    private val sides = SidePicker(
        intArrayOf(
            PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE,
            PoseLandmark.LEFT_ANKLE, PoseLandmark.LEFT_SHOULDER
        ),
        intArrayOf(
            PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE,
            PoseLandmark.RIGHT_ANKLE, PoseLandmark.RIGHT_SHOULDER
        ),
        required = 3
    )
    private val smoother = AngleSmoother(3)
    private var state = State.UP
    private var reps = 0
    private var trackingLossFrames = 0
    private var downAtMs = 0L
    private var lastRepAtMs = 0L

    private enum class State { UP, DOWN }

    override fun onFrame(pose: Pose?): RepUpdate {
        val joints = sides.pick(pose)

        if (joints == null) {
            trackingLossFrames++
            if (trackingLossFrames > TRACKING_LOSS_GRACE_FRAMES) {
                state = State.UP
                smoother.clear()
                return RepUpdate(reps, 0f, isTracking = false, debug = "no body")
            }
            return RepUpdate(reps, if (state == State.DOWN) 0.5f else 1f, isTracking = false, debug = "flicker")
        }
        trackingLossFrames = 0

        val hip = joints[0]!!
        val knee = joints[1]!!
        val ankle = joints[2]!!

        val kneeAngle = smoother.add(jointAngle(hip.position3D, knee.position3D, ankle.position3D))
        val liveProgress = ((kneeAngle - 90.0) / (165.0 - 90.0)).toFloat().coerceIn(0f, 1f)
        val now = SystemClock.elapsedRealtime()

        when (state) {
            State.UP -> {
                if (kneeAngle < 125.0) {
                    state = State.DOWN
                    downAtMs = now
                }
            }
            State.DOWN -> {
                if (kneeAngle > 150.0) {
                    if (now - downAtMs >= MIN_DOWN_TO_UP_MS && now - lastRepAtMs >= 600L) {
                        reps++
                        lastRepAtMs = now
                    }
                    state = State.UP
                }
            }
        }

        val debug = "knee ${kneeAngle.toInt()}\u00b0 \u00b7 down<125 up>150 \u00b7 ${state.name}"
        return RepUpdate(reps, liveProgress, isTracking = true, debug = debug)
    }
}

/**
 * Jumping jacks, lenient. Open = hands above the shoulders (and, if the feet
 * are visible, feet apart). Closed = hands back down (and feet together if
 * visible). If the legs are out of frame, arms alone are enough.
 */
class JumpingJackRepCounter : RepCounter {
    private var open = false
    private var reps = 0
    private var trackingLossFrames = 0
    private var openedAtMs = 0L
    private var lastRepAtMs = 0L

    override fun onFrame(pose: Pose?): RepUpdate {
        val ls = pose?.landmarkOrNull(PoseLandmark.LEFT_SHOULDER)
        val rs = pose?.landmarkOrNull(PoseLandmark.RIGHT_SHOULDER)
        val lw = pose?.landmarkOrNull(PoseLandmark.LEFT_WRIST)
        val rw = pose?.landmarkOrNull(PoseLandmark.RIGHT_WRIST)
        val la = pose?.landmarkOrNull(PoseLandmark.LEFT_ANKLE)
        val ra = pose?.landmarkOrNull(PoseLandmark.RIGHT_ANKLE)

        if (ls == null || rs == null || lw == null || rw == null) {
            trackingLossFrames++
            if (trackingLossFrames > TRACKING_LOSS_GRACE_FRAMES) {
                open = false
                return RepUpdate(reps, 0f, isTracking = false, debug = "no body")
            }
            return RepUpdate(reps, if (open) 1f else 0f, isTracking = false, debug = "flicker")
        }
        trackingLossFrames = 0

        val shoulderWidth = kotlin.math.abs(ls.position.x - rs.position.x).coerceAtLeast(1f)
        val shoulderY = (ls.position.y + rs.position.y) / 2f
        val wristY = (lw.position.y + rw.position.y) / 2f
        // > 0: hands above the shoulders. Hanging arms read about -1.5.
        val armLift = (shoulderY - wristY) / shoulderWidth
        val feetKnown = la != null && ra != null
        val feetGap = if (feetKnown) kotlin.math.abs(la!!.position.x - ra!!.position.x) / shoulderWidth else 0f

        val now = SystemClock.elapsedRealtime()
        if (!open) {
            if (armLift > 0.3f && (!feetKnown || feetGap > 1.2f)) {
                open = true
                openedAtMs = now
            }
        } else if (armLift < -0.3f && (!feetKnown || feetGap < 1.6f)) {
            if (now - openedAtMs >= 100L && now - lastRepAtMs >= 300L) {
                reps++
                lastRepAtMs = now
            }
            open = false
        }

        val progress = ((armLift + 0.5f) / 1.5f).coerceIn(0f, 1f)
        val debug = "arms ${"%.1f".format(armLift)} \u00b7 feet " + (if (feetKnown) "%.1f".format(feetGap) else "n/a") +
            " \u00b7 " + if (open) "OPEN" else "CLOSED"
        return RepUpdate(reps, progress, isTracking = true, debug = debug)
    }
}

fun repCounterFor(exerciseName: String): RepCounter? = when (exerciseName) {
    "Push-ups" -> PushUpRepCounter()
    "Squats" -> SquatRepCounter()
    "Jumping Jacks" -> JumpingJackRepCounter()
    else -> null
}
