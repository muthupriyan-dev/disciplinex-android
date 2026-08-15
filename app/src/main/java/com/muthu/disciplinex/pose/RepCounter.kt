package com.muthu.disciplinex.pose

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
 * Mirrors the anti-cheat logic already proven out in the web version:
 * a real rep needs a genuine down/up angle transition AND the confirming
 * secondary check (plank line for push-ups, hip drop for squats), and a
 * short grace period absorbs single-frame tracking flicker instead of
 * cancelling the rep in progress.
 */
interface RepCounter {
    fun onFrame(pose: Pose?): RepUpdate
}

private const val MIN_LANDMARK_CONFIDENCE = 0.5f
private const val TRACKING_LOSS_GRACE_FRAMES = 6

private fun Pose.landmarkOrNull(type: Int): PoseLandmark? {
    val lm = getPoseLandmark(type) ?: return null
    return if (lm.inFrameLikelihood >= MIN_LANDMARK_CONFIDENCE) lm else null
}

/**
 * Push-up counter: elbow angle (shoulder-elbow-wrist) drives the down/up
 * state machine. Two anti-cheat checks guard against "fist-curl" cheating
 * (bending only the elbow while sitting):
 *  - plank check: shoulder-hip line must stay roughly horizontal
 *  - displacement check: the shoulder must actually move a meaningful
 *    fraction of torso length between the down and up positions
 */
class PushUpRepCounter : RepCounter {
    private var state = State.UP
    private var reps = 0
    private var trackingLossFrames = 0
    private var shoulderYAtDown: Float? = null

    private enum class State { UP, DOWN }

    override fun onFrame(pose: Pose?): RepUpdate {
        val shoulder = pose?.landmarkOrNull(PoseLandmark.LEFT_SHOULDER)
            ?: pose?.landmarkOrNull(PoseLandmark.RIGHT_SHOULDER)
        val elbow = pose?.landmarkOrNull(PoseLandmark.LEFT_ELBOW)
            ?: pose?.landmarkOrNull(PoseLandmark.RIGHT_ELBOW)
        val wrist = pose?.landmarkOrNull(PoseLandmark.LEFT_WRIST)
            ?: pose?.landmarkOrNull(PoseLandmark.RIGHT_WRIST)
        val hip = pose?.landmarkOrNull(PoseLandmark.LEFT_HIP)
            ?: pose?.landmarkOrNull(PoseLandmark.RIGHT_HIP)

        if (shoulder == null || elbow == null || wrist == null || hip == null) {
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

        val elbowAngle = jointAngle(shoulder.position3D, elbow.position3D, wrist.position3D)
        val plankTilt = tiltFromHorizontal(shoulder.position3D, hip.position3D)
        val torsoLength = kotlin.math.abs(hip.position.y - shoulder.position.y).coerceAtLeast(1f)
        val isPlank = plankTilt < 35.0 // roughly horizontal body, not sitting upright

        val liveProgress = ((elbowAngle - 70.0) / (160.0 - 70.0)).toFloat().coerceIn(0f, 1f)

        when (state) {
            State.UP -> {
                if (elbowAngle < 95.0 && isPlank) {
                    state = State.DOWN
                    shoulderYAtDown = shoulder.position.y
                }
            }
            State.DOWN -> {
                val downY = shoulderYAtDown
                val movedEnough = downY != null &&
                    kotlin.math.abs(shoulder.position.y - downY) > torsoLength * 0.12f
                if (elbowAngle > 150.0 && isPlank && movedEnough) {
                    reps++
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
 * machine, confirmed by genuine hip vertical drop so a half-bend can't be
 * cheated into a full rep.
 */
class SquatRepCounter : RepCounter {
    private var state = State.UP
    private var reps = 0
    private var trackingLossFrames = 0
    private var hipYAtDown: Float? = null

    private enum class State { UP, DOWN }

    override fun onFrame(pose: Pose?): RepUpdate {
        val hip = pose?.landmarkOrNull(PoseLandmark.LEFT_HIP)
            ?: pose?.landmarkOrNull(PoseLandmark.RIGHT_HIP)
        val knee = pose?.landmarkOrNull(PoseLandmark.LEFT_KNEE)
            ?: pose?.landmarkOrNull(PoseLandmark.RIGHT_KNEE)
        val ankle = pose?.landmarkOrNull(PoseLandmark.LEFT_ANKLE)
            ?: pose?.landmarkOrNull(PoseLandmark.RIGHT_ANKLE)
        val shoulder = pose?.landmarkOrNull(PoseLandmark.LEFT_SHOULDER)
            ?: pose?.landmarkOrNull(PoseLandmark.RIGHT_SHOULDER)

        if (hip == null || knee == null || ankle == null || shoulder == null) {
            trackingLossFrames++
            if (trackingLossFrames > TRACKING_LOSS_GRACE_FRAMES) {
                state = State.UP
                hipYAtDown = null
                return RepUpdate(reps, 0f, isTracking = false)
            }
            return RepUpdate(reps, if (state == State.DOWN) 0.5f else 0f, isTracking = false)
        }
        trackingLossFrames = 0

        val kneeAngle = jointAngle(hip.position3D, knee.position3D, ankle.position3D)
        val legLength = kotlin.math.abs(ankle.position.y - hip.position.y).coerceAtLeast(1f)

        val liveProgress = ((kneeAngle - 80.0) / (165.0 - 80.0)).toFloat().coerceIn(0f, 1f)

        when (state) {
            State.UP -> {
                if (kneeAngle < 110.0) {
                    state = State.DOWN
                    hipYAtDown = hip.position.y
                }
            }
            State.DOWN -> {
                val downY = hipYAtDown
                val droppedEnough = downY != null &&
                    kotlin.math.abs(hip.position.y - downY) > legLength * 0.10f
                if (kneeAngle > 155.0 && droppedEnough) {
                    reps++
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
