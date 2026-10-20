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
    val isTracking: Boolean
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
private class SidePicker(private val left: IntArray, private val right: IntArray) {
    private var preferLeft = true

    fun pick(pose: Pose?): List<PoseLandmark>? {
        if (pose == null) return null
        val l = left.map { pose.landmarkOrNull(it) }
        val r = right.map { pose.landmarkOrNull(it) }
        val leftOk = l.all { it != null }
        val rightOk = r.all { it != null }
        val useLeft = when {
            leftOk && rightOk -> preferLeft
            leftOk -> true
            rightOk -> false
            else -> return null
        }
        preferLeft = useLeft
        return (if (useLeft) l else r).filterNotNull()
    }
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
    private val sides = SidePicker(
        intArrayOf(
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW,
            PoseLandmark.LEFT_WRIST, PoseLandmark.LEFT_HIP
        ),
        intArrayOf(
            PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.RIGHT_WRIST, PoseLandmark.RIGHT_HIP
        )
    )
    private var state = State.UP
    private var reps = 0
    private var trackingLossFrames = 0
    private var shoulderYAtDown: Float? = null
    private var downAtMs = 0L
    private var lastRepAtMs = 0L

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
                return RepUpdate(reps, 0f, isTracking = false)
            }
            return RepUpdate(reps, if (state == State.DOWN) 0.5f else 0f, isTracking = false)
        }
        trackingLossFrames = 0

        val (shoulder, elbow, wrist, hip) = joints

        val elbowAngle = jointAngle(shoulder.position3D, elbow.position3D, wrist.position3D)
        val plankTilt = tiltFromHorizontal(shoulder.position3D, hip.position3D)
        val torsoLength = kotlin.math.abs(hip.position.y - shoulder.position.y).coerceAtLeast(1f)
        // Loose on purpose: with the phone just propped on the floor the
        // shoulder-hip line rarely reads as truly horizontal. This only
        // rejects someone sitting bolt upright.
        val isPlank = plankTilt < 65.0

        val liveProgress = ((elbowAngle - 70.0) / (160.0 - 70.0)).toFloat().coerceIn(0f, 1f)
        val now = SystemClock.elapsedRealtime()

        when (state) {
            State.UP -> {
                if (elbowAngle < 100.0 && isPlank) {
                    state = State.DOWN
                    shoulderYAtDown = shoulder.position.y
                    downAtMs = now
                }
            }
            State.DOWN -> {
                val downY = shoulderYAtDown
                val movedEnough = downY == null ||
                    kotlin.math.abs(shoulder.position.y - downY) > torsoLength * 0.04f
                if (elbowAngle > 145.0 && isPlank) {
                    val believable = now - downAtMs >= MIN_DOWN_TO_UP_MS &&
                        now - lastRepAtMs >= MIN_REP_INTERVAL_MS
                    if (movedEnough && believable) {
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

        return RepUpdate(reps, liveProgress, isTracking = true)
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
        )
    )
    private var state = State.UP
    private var reps = 0
    private var trackingLossFrames = 0
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
                return RepUpdate(reps, 0f, isTracking = false)
            }
            return RepUpdate(reps, if (state == State.DOWN) 0.5f else 0f, isTracking = false)
        }
        trackingLossFrames = 0

        val (hip, knee, ankle, _) = joints

        val kneeAngle = jointAngle(hip.position3D, knee.position3D, ankle.position3D)
        val legLength = kotlin.math.abs(ankle.position.y - hip.position.y).coerceAtLeast(1f)

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
                    kotlin.math.abs(hip.position.y - downY) > legLength * 0.04f
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

fun repCounterFor(exerciseName: String): RepCounter? = when (exerciseName) {
    "Push-ups" -> PushUpRepCounter()
    "Squats" -> SquatRepCounter()
    else -> null
}
