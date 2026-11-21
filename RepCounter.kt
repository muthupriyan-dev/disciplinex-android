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
 * A real rep needs a genuine down/up angle transition AND the confirming
 * secondary check (plank line for push-ups, hip drop for squats), AND it must
 * take a believable amount of time — shaking an arm for a split second no
 * longer counts. A short grace period absorbs single-frame tracking flicker
 * instead of cancelling the rep in progress.
 */
interface RepCounter {
    fun onFrame(pose: Pose?): RepUpdate
}

private const val MIN_LANDMARK_CONFIDENCE = 0.5f
private const val TRACKING_LOSS_GRACE_FRAMES = 6

/** Bottom -> top of one rep can't be faster than this. */
private const val MIN_DOWN_TO_UP_MS = 300L

/** Two counted reps can't be closer together than this. */
private const val MIN_REP_INTERVAL_MS = 900L

/**
 * Push-up posture: how far (degrees) a body line may be off-horizontal before the
 * body counts as upright, i.e. NOT in a plank. The shoulder->knee and shoulder->ankle
 * lines are the reliable ones: in a squat they stay steep even when the torso leans.
 */
private const val PLANK_MAX_TORSO_TILT = 55.0
private const val PLANK_MAX_LEG_TILT = 45.0

/** The level-body posture must hold for this many frames in a row before a push-up may start. */
private const val MIN_PLANK_FRAMES = 10

/** In a push-up the hands are planted: the wrist may move at most this fraction of the shoulder's movement. */
private const val WRIST_MOVE_RATIO_MAX = 0.7f

/** A squat must drop the hip by at least this fraction of the leg length. */
private const val SQUAT_MIN_DROP = 0.10f

/** A body this close to horizontal is lying/planking, never squatting. */
private const val SQUAT_LYING_TILT = 35.0

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
private class AngleSmoother(private val size: Int = 3) {
    private val values = ArrayDeque<Double>()
    fun add(v: Double): Double {
        values.addLast(v)
        while (values.size > size) values.removeFirst()
        return values.average()
    }
    fun clear() = values.clear()
}

/**
 * Push-up counter: elbow angle (shoulder-elbow-wrist) drives the down/up
 * state machine. Anti-cheat checks guard against "fist-curl" cheating
 * (bending only the elbow while sitting):
 *  - plank check: shoulder-hip line must not be bolt upright
 *  - displacement check: the shoulder must actually move between down and up
 *  - timing: the rep can't be faster than [MIN_DOWN_TO_UP_MS], and reps can't
 *    be closer together than [MIN_REP_INTERVAL_MS]
 */
class PushUpRepCounter : RepCounter {
    // Shoulder, elbow and wrist must be visible. The hip is optional: with the
    // phone close to the floor the hip is often out of frame, and requiring it
    // used to stop every rep from being counted.
    private val sides = SidePicker(
        intArrayOf(
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW,
            PoseLandmark.LEFT_WRIST, PoseLandmark.LEFT_HIP,
            PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE
        ),
        intArrayOf(
            PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.RIGHT_WRIST, PoseLandmark.RIGHT_HIP,
            PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE
        ),
        required = 3
    )
    private val smoother = AngleSmoother(3)
    private var state = State.UP
    private var reps = 0
    private var trackingLossFrames = 0
    private var shoulderYAtDown: Float? = null
    private var wristYAtDown: Float? = null
    private var plankFrames = 0
    private var downAtMs = 0L
    private var lastRepAtMs = 0L

    // Highest (most extended) elbow angle seen lately. The "arms straight again"
    // line adapts to it, so someone whose arms only read ~150 degrees on camera
    // still gets counted instead of being stuck in the DOWN state forever.
    private var peakAngle = 150.0

    private enum class State { UP, DOWN }

    override fun onFrame(pose: Pose?): RepUpdate {
        val joints = sides.pick(pose)

        if (joints == null) {
            trackingLossFrames++
            // Single-frame flicker: hold the current rep state instead of
            // resetting mid-rep. Only a sustained loss clears progress.
            if (trackingLossFrames > TRACKING_LOSS_GRACE_FRAMES) {
                state = State.UP
                shoulderYAtDown = null
                smoother.clear()
                return RepUpdate(reps, 0f, isTracking = false, debug = "no body")
            }
            return RepUpdate(reps, if (state == State.DOWN) 0.5f else 0f, isTracking = false, debug = "flicker")
        }
        trackingLossFrames = 0

        val shoulder = joints[0]!!
        val elbow = joints[1]!!
        val wrist = joints[2]!!
        val hip = joints[3]
        val knee = joints[4]
        val ankle = joints[5]

        val rawAngle = jointAngle(shoulder.position3D, elbow.position3D, wrist.position3D)
        val elbowAngle = smoother.add(rawAngle)

        // Upper-arm length on screen is a stable ruler for "did the shoulder really move".
        val upperArm = kotlin.math.hypot(
            (shoulder.position.x - elbow.position.x).toDouble(),
            (shoulder.position.y - elbow.position.y).toDouble()
        ).toFloat().coerceAtLeast(1f)

        val plankTilt = hip?.let { tiltFromHorizontal(shoulder.position3D, it.position3D) }
        val kneeTilt = knee?.let { tiltFromHorizontal(shoulder.position3D, it.position3D) }
        val ankleTilt = ankle?.let { tiltFromHorizontal(shoulder.position3D, it.position3D) }

        // A push-up needs POSITIVE proof of a level body: at least one of hip / knee /
        // ankle must be visible and every visible body line must be roughly horizontal.
        // (Before, "nothing visible" counted as a plank, so sitting down and standing up
        // with the hands on a desk or on the knees was counted as push-ups.)
        val lowerBodyVisible = hip != null || knee != null || ankle != null
        if (!lowerBodyVisible) {
            state = State.UP
            shoulderYAtDown = null
            plankFrames = 0
            return RepUpdate(reps, 0f, isTracking = false, debug = "hips / legs not visible")
        }
        val isPlank = (plankTilt == null || plankTilt < PLANK_MAX_TORSO_TILT) &&
            (kneeTilt == null || kneeTilt < PLANK_MAX_LEG_TILT) &&
            (ankleTilt == null || ankleTilt < PLANK_MAX_LEG_TILT)
        if (isPlank) plankFrames++ else plankFrames = 0

        if (elbowAngle > peakAngle) peakAngle = elbowAngle else peakAngle -= 0.02
        peakAngle = peakAngle.coerceIn(140.0, 175.0)
        val upThreshold = (peakAngle - 12.0).coerceIn(128.0, 145.0)
        val downThreshold = 105.0

        val liveProgress = ((elbowAngle - 70.0) / (peakAngle - 70.0)).toFloat().coerceIn(0f, 1f)
        val now = SystemClock.elapsedRealtime()

        when (state) {
            State.UP -> {
                if (elbowAngle < downThreshold && isPlank && plankFrames >= MIN_PLANK_FRAMES) {
                    state = State.DOWN
                    shoulderYAtDown = shoulder.position.y
                    wristYAtDown = wrist.position.y
                    downAtMs = now
                }
            }
            State.DOWN -> {
                val downY = shoulderYAtDown
                val movedEnough = downY == null ||
                    kotlin.math.abs(shoulder.position.y - downY) > upperArm * (if (hip == null) 0.22f else 0.10f)
                val wristDown = wristYAtDown
                val shoulderMove = if (downY == null) 0f else kotlin.math.abs(shoulder.position.y - downY)
                val wristMove = if (wristDown == null) 0f else kotlin.math.abs(wrist.position.y - wristDown)
                // Hands are planted in a push-up. If the wrist travelled along with the
                // shoulder, the whole body moved (squat, standing up): not a push-up.
                val wristPlanted = wristMove <= shoulderMove * WRIST_MOVE_RATIO_MAX ||
                    wristMove <= upperArm * 0.25f
                if (elbowAngle > upThreshold && isPlank) {
                    val believable = now - downAtMs >= MIN_DOWN_TO_UP_MS &&
                        now - lastRepAtMs >= MIN_REP_INTERVAL_MS
                    if (movedEnough && believable && wristPlanted) {
                        reps++
                        lastRepAtMs = now
                    }
                    // Counted or rejected as a wiggle — either way this attempt is over.
                    state = State.UP
                    shoulderYAtDown = null
                } else if (!isPlank) {
                    // Sat up out of plank mid-rep — don't award a cheated rep.
                    state = State.UP
                    shoulderYAtDown = null
                }
            }
        }

        val debug = "elbow ${elbowAngle.toInt()}\u00b0 \u00b7 up>${upThreshold.toInt()} down<${downThreshold.toInt()} \u00b7 " +
            "${state.name}" + if (plankTilt == null) " \u00b7 hip hidden" else " \u00b7 tilt ${plankTilt.toInt()}\u00b0"
        return RepUpdate(reps, liveProgress, isTracking = true, debug = debug)
    }
}

/**
 * Squat counter: knee angle (hip-knee-ankle) drives the down/up state
 * machine, confirmed by genuine hip vertical drop and by the same timing
 * limits as push-ups, so a half-bend or a quick bounce can't be cheated
 * into a rep.
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
    private var state = State.UP
    private var reps = 0
    private var trackingLossFrames = 0
    private val smoother = AngleSmoother(3)
    private var hipYAtDown: Float? = null
    private var downAtMs = 0L
    private var lastRepAtMs = 0L

    private enum class State { UP, DOWN }

    override fun onFrame(pose: Pose?): RepUpdate {
        val joints = sides.pick(pose)

        if (joints == null) {
            trackingLossFrames++
            if (trackingLossFrames > TRACKING_LOSS_GRACE_FRAMES) {
                state = State.UP
                hipYAtDown = null
                smoother.clear()
                return RepUpdate(reps, 0f, isTracking = false)
            }
            return RepUpdate(reps, if (state == State.DOWN) 0.5f else 0f, isTracking = false)
        }
        trackingLossFrames = 0

        val hip = joints[0]!!
        val knee = joints[1]!!
        val ankle = joints[2]!!

        val kneeAngle = smoother.add(jointAngle(hip.position3D, knee.position3D, ankle.position3D))
        val legLength = kotlin.math.abs(ankle.position.y - hip.position.y).coerceAtLeast(1f)

        val shoulder = joints[3]
        val torsoTilt = shoulder?.let { tiltFromHorizontal(it.position3D, hip.position3D) }
        if (torsoTilt != null && torsoTilt < SQUAT_LYING_TILT) {
            // Body is horizontal (push-up, plank, lying down): never a squat.
            state = State.UP
            hipYAtDown = null
            return RepUpdate(reps, 0f, isTracking = true)
        }

        val liveProgress = ((kneeAngle - 80.0) / (165.0 - 80.0)).toFloat().coerceIn(0f, 1f)
        val now = SystemClock.elapsedRealtime()

        when (state) {
            State.UP -> {
                if (kneeAngle < 110.0) {
                    state = State.DOWN
                    hipYAtDown = hip.position.y
                    downAtMs = now
                }
            }
            State.DOWN -> {
                val downY = hipYAtDown
                val droppedEnough = downY == null ||
                    kotlin.math.abs(hip.position.y - downY) > legLength * SQUAT_MIN_DROP
                if (kneeAngle > 155.0) {
                    val believable = now - downAtMs >= MIN_DOWN_TO_UP_MS &&
                        now - lastRepAtMs >= MIN_REP_INTERVAL_MS
                    if (droppedEnough && believable) {
                        reps++
                        lastRepAtMs = now
                    }
                    state = State.UP
                    hipYAtDown = null
                }
            }
        }

        return RepUpdate(reps, liveProgress, isTracking = true)
    }
}

/**
 * Jumping jacks, counted from the front camera. One rep = arms up above the
 * shoulders AND feet wide apart ("open"), then arms back down AND feet back
 * together ("closed"). Needs the whole body in frame. Widths are measured in
 * shoulder-widths, so it doesn't matter how far the phone is.
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

        if (ls == null || rs == null || lw == null || rw == null || la == null || ra == null) {
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
        // Judge each arm on its own: one raised hand must not open a jack.
        val leftLift = (ls.position.y - lw.position.y) / shoulderWidth
        val rightLift = (rs.position.y - rw.position.y) / shoulderWidth
        val bothArmsUp = minOf(leftLift, rightLift) > 0.3f
        val bothArmsDown = maxOf(leftLift, rightLift) < -0.5f
        val feetGap = kotlin.math.abs(la.position.x - ra.position.x) / shoulderWidth

        val now = SystemClock.elapsedRealtime()
        if (!open) {
            if (bothArmsUp && feetGap > 1.5f) {
                open = true
                openedAtMs = now
            }
        } else if (bothArmsDown && feetGap < 1.1f) {
            if (now - openedAtMs >= 150L && now - lastRepAtMs >= 450L) {
                reps++
                lastRepAtMs = now
            }
            open = false
        }

        val progress = ((armLift + 0.5f) / 1.5f).coerceIn(0f, 1f)
        val debug = "arms ${"%.1f".format(armLift)} \u00b7 feet ${"%.1f".format(feetGap)} \u00b7 " + if (open) "OPEN" else "CLOSED"
        return RepUpdate(reps, progress, isTracking = true, debug = debug)
    }
}

fun repCounterFor(exerciseName: String): RepCounter? = when (exerciseName) {
    "Push-ups" -> PushUpRepCounter()
    "Squats" -> SquatRepCounter()
    "Jumping Jacks" -> JumpingJackRepCounter()
    else -> null
}
