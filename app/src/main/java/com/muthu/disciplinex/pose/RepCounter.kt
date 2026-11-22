package com.muthu.disciplinex.pose

import android.os.SystemClock
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

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
 * One joint angle (elbow for push-ups, knee for squats) drives the down/up state machine,
 * exactly like before. NEW: when a rep finishes it is checked against the movement of the
 * whole body during that rep, so moving an arm or a leg while standing, or sitting down and
 * standing up, is no longer counted as a push-up. A rejected rep shows "skipped: <reason>"
 * in the debug line under the camera.
 */
interface RepCounter {
    fun onFrame(pose: Pose?): RepUpdate
}

private const val MIN_CONFIDENCE = 0.4f
private const val LOSS_GRACE_FRAMES = 10
private const val MIN_REP_INTERVAL_MS = 400L
private const val MIN_DOWN_TO_UP_MS = 200L

/** How long a "skipped: reason" message stays in the debug line. */
private const val SKIP_MESSAGE_MS = 3000L

// ---- push-up rep validation (all measured over ONE up -> down -> up cycle) ----
private const val PU_DOWN = 100.0
private const val PU_UP = 145.0
/** The shoulder must travel at least this fraction of the upper-arm length. */
private const val PU_MIN_SHOULDER_TRAVEL = 0.35f
/** Hands are planted in a push-up: the wrist may move at most this fraction of the shoulder's travel. */
private const val PU_MAX_WRIST_RATIO = 0.6f
/** Hip / knee / ankle must be visible in at least this fraction of the frames. */
private const val PU_MIN_LOWER_FRACTION = 0.6f
/** Legs stay straight in a push-up; a knee angle that changes more than this means squat / sitting. */
private const val PU_MAX_KNEE_RANGE = 40.0
/** Seen from the side, a push-up body is roughly level: shoulder->hip tilt (degrees from horizontal). */
private const val PU_MAX_TORSO_TILT = 55.0
/** Shoulder width below this fraction of the upper-arm length = camera sees you from the side. */
private const val PU_SIDE_VIEW_RATIO = 0.8f
/** Show the "get your whole body in frame" hint after this many frames without any lower-body point. */
private const val NO_LOWER_HINT_FRAMES = 15

// ---- squat rep validation ----
private const val SQ_DOWN = 105.0
private const val SQ_UP = 150.0
/** The hip must drop at least this fraction of the thigh length. */
private const val SQ_MIN_HIP_TRAVEL = 0.12f
/** A body this close to horizontal is lying down, not squatting. */
private const val SQ_LYING_TILT = 35.0

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

/** How far the line a->b is tilted away from horizontal, in degrees (0 = level, 90 = upright). */
private fun tiltFromHorizontal(a: PoseLandmark, b: PoseLandmark): Double {
    val dx = abs(a.position.x - b.position.x).toDouble()
    val dy = abs(a.position.y - b.position.y).toDouble()
    return Math.toDegrees(atan2(dy, dx))
}

private fun dist(a: PoseLandmark, b: PoseLandmark): Float =
    hypot(
        (a.position.x - b.position.x).toDouble(),
        (a.position.y - b.position.y).toDouble()
    ).toFloat()

/** One body side (left or right). Only some joints are required, the rest are optional. */
private class BodySide(
    val shoulder: PoseLandmark?,
    val elbow: PoseLandmark?,
    val wrist: PoseLandmark?,
    val hip: PoseLandmark?,
    val knee: PoseLandmark?,
    val ankle: PoseLandmark?,
    val otherShoulder: PoseLandmark?
)

private fun readSide(pose: Pose, left: Boolean) = BodySide(
    shoulder = pose.lm(if (left) PoseLandmark.LEFT_SHOULDER else PoseLandmark.RIGHT_SHOULDER),
    elbow = pose.lm(if (left) PoseLandmark.LEFT_ELBOW else PoseLandmark.RIGHT_ELBOW),
    wrist = pose.lm(if (left) PoseLandmark.LEFT_WRIST else PoseLandmark.RIGHT_WRIST),
    hip = pose.lm(if (left) PoseLandmark.LEFT_HIP else PoseLandmark.RIGHT_HIP),
    knee = pose.lm(if (left) PoseLandmark.LEFT_KNEE else PoseLandmark.RIGHT_KNEE),
    ankle = pose.lm(if (left) PoseLandmark.LEFT_ANKLE else PoseLandmark.RIGHT_ANKLE),
    otherShoulder = pose.lm(if (left) PoseLandmark.RIGHT_SHOULDER else PoseLandmark.LEFT_SHOULDER)
)

/**
 * Push-up: shoulder–elbow–wrist angle. Bent below 100 = down, straight above 145 = up.
 * A finished rep only counts if, during that rep, the shoulder really travelled, the hand
 * stayed planted, the legs stayed straight and (side view) the body stayed level.
 */
class PushUpRepCounter : RepCounter {
    private var preferLeft = true
    private var down = false
    private var cycleActive = false
    private var reps = 0
    private var lossFrames = 0
    private var noLowerFrames = 0
    private var downAtMs = 0L
    private var lastRepMs = 0L
    private val history = ArrayDeque<Double>()

    // measurements of the current cycle
    private var frames = 0
    private var lowerFrames = 0
    private var shoulderMin = 0f
    private var shoulderMax = 0f
    private var wristMin = 0f
    private var wristMax = 0f
    private var armLen = 1f
    private var kneeMin = 180.0
    private var kneeMax = 0.0
    private var kneeFrames = 0
    private var torsoFrames = 0
    private var torsoUpright = 0

    private var skipReason = ""
    private var skipAtMs = 0L

    private fun pickSide(pose: Pose?): BodySide? {
        if (pose == null) return null
        val l = readSide(pose, true)
        val r = readSide(pose, false)
        val lOk = l.shoulder != null && l.elbow != null && l.wrist != null
        val rOk = r.shoulder != null && r.elbow != null && r.wrist != null
        val useLeft = when {
            lOk && rOk -> preferLeft
            lOk -> true
            rOk -> false
            else -> return null
        }
        preferLeft = useLeft
        return if (useLeft) l else r
    }

    private fun startCycle(shoulder: PoseLandmark, wrist: PoseLandmark) {
        cycleActive = true
        frames = 0
        lowerFrames = 0
        shoulderMin = shoulder.position.y
        shoulderMax = shoulder.position.y
        wristMin = wrist.position.y
        wristMax = wrist.position.y
        armLen = 1f
        kneeMin = 180.0
        kneeMax = 0.0
        kneeFrames = 0
        torsoFrames = 0
        torsoUpright = 0
    }

    private fun accumulate(s: BodySide, shoulder: PoseLandmark, elbow: PoseLandmark, wrist: PoseLandmark) {
        frames++
        shoulderMin = min(shoulderMin, shoulder.position.y)
        shoulderMax = max(shoulderMax, shoulder.position.y)
        wristMin = min(wristMin, wrist.position.y)
        wristMax = max(wristMax, wrist.position.y)
        armLen = max(armLen, dist(shoulder, elbow))

        val hip = s.hip
        val knee = s.knee
        val ankle = s.ankle
        if (hip != null || knee != null || ankle != null) lowerFrames++
        if (hip != null && knee != null && ankle != null) {
            val k = angleAt(hip, knee, ankle)
            kneeMin = min(kneeMin, k)
            kneeMax = max(kneeMax, k)
            kneeFrames++
        }
        val other = s.otherShoulder
        if (other != null && hip != null) {
            val width = abs(shoulder.position.x - other.position.x)
            if (width < PU_SIDE_VIEW_RATIO * armLen) {
                torsoFrames++
                if (tiltFromHorizontal(shoulder, hip) >= PU_MAX_TORSO_TILT) torsoUpright++
            }
        }
    }

    /** null = the finished rep looks like a real push-up, otherwise the reason it is skipped. */
    private fun judge(): String? {
        val shoulderTravel = shoulderMax - shoulderMin
        val wristTravel = wristMax - wristMin
        return when {
            shoulderTravel < PU_MIN_SHOULDER_TRAVEL * armLen -> "body barely moved"
            wristTravel > PU_MAX_WRIST_RATIO * shoulderTravel -> "hands moved"
            lowerFrames < PU_MIN_LOWER_FRACTION * frames -> "legs not in frame"
            kneeFrames >= 5 && kneeMax - kneeMin > PU_MAX_KNEE_RANGE -> "knees bent"
            torsoFrames >= 5 && torsoUpright > 0.4f * torsoFrames -> "body upright"
            else -> null
        }
    }

    override fun onFrame(pose: Pose?): RepUpdate {
        val s = pickSide(pose)
        val shoulder = s?.shoulder
        val elbow = s?.elbow
        val wrist = s?.wrist
        if (s == null || shoulder == null || elbow == null || wrist == null) {
            lossFrames++
            if (lossFrames > LOSS_GRACE_FRAMES) {
                down = false
                cycleActive = false
                history.clear()
                return RepUpdate(reps, 0f, isTracking = false, debug = "no body")
            }
            // brief flicker: keep state, don't reset, don't count
            return RepUpdate(reps, if (down) 0.3f else 1f, isTracking = false, debug = "flicker")
        }
        lossFrames = 0

        history.addLast(angleAt(shoulder, elbow, wrist))
        if (history.size > 3) history.removeFirst()
        val angle = history.average()
        val now = SystemClock.elapsedRealtime()

        if (s.hip != null || s.knee != null || s.ankle != null) noLowerFrames = 0 else noLowerFrames++

        if (!down && !cycleActive && angle < PU_UP) startCycle(shoulder, wrist)
        if (cycleActive) accumulate(s, shoulder, elbow, wrist)

        if (!down) {
            if (angle < PU_DOWN) {
                down = true
                downAtMs = now
            } else if (angle >= PU_UP) {
                cycleActive = false
            }
        } else if (angle > PU_UP) {
            down = false
            val reason = if (cycleActive) judge() else null
            cycleActive = false
            if (reason != null) {
                skipReason = reason
                skipAtMs = now
            } else if (now - downAtMs >= MIN_DOWN_TO_UP_MS && now - lastRepMs >= MIN_REP_INTERVAL_MS) {
                reps++
                lastRepMs = now
            }
        }

        val progress = ((angle - PU_DOWN) / (PU_UP - PU_DOWN)).toFloat().coerceIn(0f, 1f)
        val showSkip = skipReason.isNotEmpty() && now - skipAtMs < SKIP_MESSAGE_MS
        val debug = "elbow ${angle.toInt()}\u00b0 \u00b7 down<${PU_DOWN.toInt()} up>${PU_UP.toInt()} \u00b7 " +
            (if (down) "DOWN" else "UP") +
            (if (showSkip) " \u00b7 skipped: $skipReason" else "")
        return RepUpdate(reps, progress, isTracking = noLowerFrames < NO_LOWER_HINT_FRAMES, debug = debug)
    }
}

/**
 * Squat: hip–knee–ankle angle. Bent below 105 = down, standing above 150 = up.
 * A finished rep only counts if the hip really dropped (a raised knee or walking on the spot
 * does not) and the body was not lying down.
 */
class SquatRepCounter : RepCounter {
    private var preferLeft = true
    private var down = false
    private var cycleActive = false
    private var reps = 0
    private var lossFrames = 0
    private var downAtMs = 0L
    private var lastRepMs = 0L
    private val history = ArrayDeque<Double>()

    private var frames = 0
    private var hipMin = 0f
    private var hipMax = 0f
    private var thighLen = 1f
    private var torsoFrames = 0
    private var lying = 0

    private var skipReason = ""
    private var skipAtMs = 0L

    private fun pickSide(pose: Pose?): BodySide? {
        if (pose == null) return null
        val l = readSide(pose, true)
        val r = readSide(pose, false)
        val lOk = l.hip != null && l.knee != null && l.ankle != null
        val rOk = r.hip != null && r.knee != null && r.ankle != null
        val useLeft = when {
            lOk && rOk -> preferLeft
            lOk -> true
            rOk -> false
            else -> return null
        }
        preferLeft = useLeft
        return if (useLeft) l else r
    }

    private fun startCycle(hip: PoseLandmark) {
        cycleActive = true
        frames = 0
        hipMin = hip.position.y
        hipMax = hip.position.y
        thighLen = 1f
        torsoFrames = 0
        lying = 0
    }

    private fun accumulate(s: BodySide, hip: PoseLandmark, knee: PoseLandmark) {
        frames++
        hipMin = min(hipMin, hip.position.y)
        hipMax = max(hipMax, hip.position.y)
        thighLen = max(thighLen, dist(hip, knee))
        val shoulder = s.shoulder
        if (shoulder != null) {
            torsoFrames++
            if (tiltFromHorizontal(shoulder, hip) < SQ_LYING_TILT) lying++
        }
    }

    private fun judge(): String? = when {
        hipMax - hipMin < SQ_MIN_HIP_TRAVEL * thighLen -> "hips did not drop"
        torsoFrames >= 5 && lying > 0.4f * torsoFrames -> "body horizontal"
        else -> null
    }

    override fun onFrame(pose: Pose?): RepUpdate {
        val s = pickSide(pose)
        val hip = s?.hip
        val knee = s?.knee
        val ankle = s?.ankle
        if (s == null || hip == null || knee == null || ankle == null) {
            lossFrames++
            if (lossFrames > LOSS_GRACE_FRAMES) {
                down = false
                cycleActive = false
                history.clear()
                return RepUpdate(reps, 0f, isTracking = false, debug = "no body")
            }
            return RepUpdate(reps, if (down) 0.3f else 1f, isTracking = false, debug = "flicker")
        }
        lossFrames = 0

        history.addLast(angleAt(hip, knee, ankle))
        if (history.size > 3) history.removeFirst()
        val angle = history.average()
        val now = SystemClock.elapsedRealtime()

        if (!down && !cycleActive && angle < SQ_UP) startCycle(hip)
        if (cycleActive) accumulate(s, hip, knee)

        if (!down) {
            if (angle < SQ_DOWN) {
                down = true
                downAtMs = now
            } else if (angle >= SQ_UP) {
                cycleActive = false
            }
        } else if (angle > SQ_UP) {
            down = false
            val reason = if (cycleActive) judge() else null
            cycleActive = false
            if (reason != null) {
                skipReason = reason
                skipAtMs = now
            } else if (now - downAtMs >= MIN_DOWN_TO_UP_MS && now - lastRepMs >= MIN_REP_INTERVAL_MS) {
                reps++
                lastRepMs = now
            }
        }

        val progress = ((angle - SQ_DOWN) / (SQ_UP - SQ_DOWN)).toFloat().coerceIn(0f, 1f)
        val showSkip = skipReason.isNotEmpty() && now - skipAtMs < SKIP_MESSAGE_MS
        val debug = "knee ${angle.toInt()}\u00b0 \u00b7 down<${SQ_DOWN.toInt()} up>${SQ_UP.toInt()} \u00b7 " +
            (if (down) "DOWN" else "UP") +
            (if (showSkip) " \u00b7 skipped: $skipReason" else "")
        return RepUpdate(reps, progress, isTracking = true, debug = debug)
    }
}

/**
 * Jumping jacks (not in the web version, kept lenient).
 * Open = BOTH hands above the shoulders (and feet apart if feet are visible).
 * Closed = both hands back down (and feet together if visible).
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
        // >0 means that hand is above its shoulder. Each arm is judged on its own.
        val leftLift = (ls.position.y - lw.position.y) / shoulderWidth
        val rightLift = (rs.position.y - rw.position.y) / shoulderWidth
        val armLift = (leftLift + rightLift) / 2f
        val bothUp = min(leftLift, rightLift) > 0.3f
        val bothDown = max(leftLift, rightLift) < -0.3f
        val feetKnown = la != null && ra != null
        val feetGap = if (feetKnown) abs(la!!.position.x - ra!!.position.x) / shoulderWidth else 0f

        val now = SystemClock.elapsedRealtime()
        if (!open) {
            if (bothUp && (!feetKnown || feetGap > 1.2f)) {
                open = true
                openedAtMs = now
            }
        } else if (bothDown && (!feetKnown || feetGap < 1.6f)) {
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
