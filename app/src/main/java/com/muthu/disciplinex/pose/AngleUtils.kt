package com.muthu.disciplinex.pose

import com.google.mlkit.vision.common.PointF3D
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Angle (in degrees, 0-180) at [mid] formed by the rays mid->first and
 * mid->last. Used for elbow angle (shoulder-elbow-wrist) and knee angle
 * (hip-knee-ankle) rep detection, same approach as the web version's
 * MoveNet-based counter.
 */
fun jointAngle(first: PointF3D, mid: PointF3D, last: PointF3D): Double {
    val angle = Math.toDegrees(
        (atan2((last.y - mid.y).toDouble(), (last.x - mid.x).toDouble()) -
            atan2((first.y - mid.y).toDouble(), (first.x - mid.x).toDouble()))
    )
    var result = abs(angle)
    if (result > 180) result = 360 - result
    return result
}

/** How far off-horizontal the line between two points is, in degrees (0 = perfectly level). */
fun tiltFromHorizontal(a: PointF3D, b: PointF3D): Double {
    val angle = Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble()))
    var result = abs(angle)
    if (result > 90) result = 180 - result
    return result
}
