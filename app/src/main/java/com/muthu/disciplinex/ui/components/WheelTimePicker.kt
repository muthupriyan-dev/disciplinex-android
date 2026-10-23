package com.muthu.disciplinex.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.muthu.disciplinex.ui.theme.TextPrimary
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A native-feeling wheel time picker (hour / minute / AM-PM),
 * with a curved "drum" look: rows bend away and fade above and below the
 * selected one, hours and minutes loop around, and AM/PM is a small two-row wheel.
 *
 * @param initialHour 1-12
 * @param initialMinute 0-59
 * @param initialIsAm true = AM, false = PM
 * @param onTimeChanged called whenever the selected time changes, with (hour, minute, isAm)
 */
@Composable
fun WheelTimePicker(
    initialHour: Int = 9,
    initialMinute: Int = 0,
    initialIsAm: Boolean = true,
    onTimeChanged: (hour: Int, minute: Int, isAm: Boolean) -> Unit
) {
    val hours = remember { (1..12).toList() }
    val minutes = remember { (0..59).toList() }
    val hourLabels = remember { hours.map { it.toString().padStart(2, '0') } }
    val minuteLabels = remember { minutes.map { it.toString().padStart(2, '0') } }
    val ampm = remember { listOf("AM", "PM") }

    var selectedHour by remember { mutableIntStateOf(initialHour) }
    var selectedMinute by remember { mutableIntStateOf(initialMinute) }
    var selectedIsAm by remember { mutableIntStateOf(if (initialIsAm) 0 else 1) }

    LaunchedEffect(selectedHour, selectedMinute, selectedIsAm) {
        onTimeChanged(selectedHour, selectedMinute, selectedIsAm == 0)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(WHEEL_HEIGHT),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        WheelColumn(
            items = hourLabels,
            initialIndex = hours.indexOf(initialHour).coerceAtLeast(0),
            circular = true, // 12 -> 01 -> 02 ... wraps around like a real wheel
            onSelected = { index -> selectedHour = hours[index] }
        )

        Text(
            text = ":",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp)
        )

        WheelColumn(
            items = minuteLabels,
            initialIndex = minutes.indexOf(initialMinute).coerceAtLeast(0),
            circular = true, // 59 -> 00 wraps around
            onSelected = { index -> selectedMinute = minutes[index] }
        )

        Box(modifier = Modifier.width(24.dp))

        WheelColumn(
            items = ampm,
            initialIndex = if (initialIsAm) 0 else 1,
            width = 60.dp,
            itemHeight = 38.dp,
            fontSize = 20.sp,
            onSelected = { index -> selectedIsAm = index }
        )
    }
}

private val ITEM_HEIGHT = 48.dp
internal val WHEEL_HEIGHT = 264.dp

/** Each row sits 30 degrees (0.5236 rad) further round the drum than the one before. */
private const val STEP_RADIANS = 0.5235988f

/** A circular wheel repeats its items this many times so it can be scrolled "forever". */
private const val CIRCULAR_REPEATS = 1000

/** Which row is currently in the centre, straight from the scroll position. */
private fun centerIndexOf(state: LazyListState, itemPx: Int, lastIndex: Int): Int {
    val pos = state.firstVisibleItemIndex * itemPx + state.firstVisibleItemScrollOffset
    return (pos / itemPx.toFloat()).roundToInt().coerceIn(0, lastIndex)
}

/**
 * Fling that always ends exactly on a row, in ONE continuous glide.
 *
 * The distance a normal fling would have travelled is worked out first and
 * rounded to the nearest row; the wheel then decelerates smoothly onto that
 * row. (Before, the wheel flung freely, stopped, and only then animated a second
 * time onto a row - two separate motions that felt like a stutter.)
 */
private class WheelFlingBehavior(
    private val state: LazyListState,
    private val itemPx: Int,
    private val lastIndex: Int
) : FlingBehavior {

    private val decay = FloatExponentialDecaySpec(frictionMultiplier = 1.6f)
    private val easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f) // ease-out: quick start, soft landing

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val start = (state.firstVisibleItemIndex * itemPx + state.firstVisibleItemScrollOffset).toFloat()
        val projected: Float = start + decay.getTargetValue(0f, initialVelocity)
        val targetIndex: Int = (projected / itemPx).roundToInt().coerceIn(0, lastIndex)
        val distance: Float = targetIndex * itemPx - start
        if (abs(distance) < 0.5f) return 0f

        val rows: Float = abs(distance) / itemPx
        val duration = (180 + 170 * sqrt(rows)).toInt().coerceAtMost(1200)
        var last = 0f
        animate(0f, distance, animationSpec = tween(duration, easing = easing)) { value, _ ->
            scrollBy(value - last)
            last = value
        }
        return 0f
    }
}

/**
 * One wheel (hours, minutes or AM/PM), drawn as the front of a rotating drum:
 * the further a row is from the centre, the more it is squashed, pulled
 * towards the centre and faded - the selected row is big and black.
 *
 *  - All of that happens inside graphicsLayer, so while the wheel moves only
 *    the draw layer updates - no recomposition per frame.
 *  - The selected value is reported only when the wheel is at rest, so dragging
 *    doesn't recompose the whole alarm screen on every row.
 *  - A light tick is played every time a new row passes the centre.
 *
 * @param circular true = the list loops (12 -> 01, 59 -> 00)
 */
@Composable
private fun WheelColumn(
    items: List<String>,
    initialIndex: Int,
    onSelected: (Int) -> Unit,
    circular: Boolean = false,
    width: Dp = 72.dp,
    itemHeight: Dp = ITEM_HEIGHT,
    fontSize: TextUnit = 30.sp
) {
    val itemPx = with(LocalDensity.current) { itemHeight.roundToPx() }
    val size = items.size
    val count = if (circular) size * CIRCULAR_REPEATS else size
    val lastIndex = count - 1
    // Circular wheels start in the middle of the long list so both directions have room.
    val startIndex = if (circular) (CIRCULAR_REPEATS / 2) * size + initialIndex else initialIndex

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val fling = remember(listState, itemPx, lastIndex) { WheelFlingBehavior(listState, itemPx, lastIndex) }
    val haptic = LocalHapticFeedback.current
    val currentOnSelected by rememberUpdatedState(onSelected)
    val radius = itemPx / STEP_RADIANS // drum radius in pixels

    LaunchedEffect(listState, itemPx, lastIndex) {
        snapshotFlow { if (listState.isScrollInProgress) -1 else centerIndexOf(listState, itemPx, lastIndex) }
            .filter { it >= 0 }
            .distinctUntilChanged()
            .collect { currentOnSelected(it % size) }
    }

    LaunchedEffect(listState, itemPx, lastIndex) {
        snapshotFlow { centerIndexOf(listState, itemPx, lastIndex) }
            .distinctUntilChanged()
            .drop(1)
            .collect { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }

    Box(
        modifier = Modifier
            .width(width)
            .height(WHEEL_HEIGHT)
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = fling,
            // Padding lets the first and last real rows sit in the centre row.
            contentPadding = PaddingValues(vertical = (WHEEL_HEIGHT - itemHeight) / 2),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(count) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .graphicsLayer {
                            val pos = listState.firstVisibleItemIndex * itemPx +
                                listState.firstVisibleItemScrollOffset
                            val d = (index * itemPx - pos) / itemPx.toFloat() // rows from centre (signed)
                            val ad = abs(d)

                            // Position on the drum: rows bunch up towards the top and bottom.
                            val theta = (d * STEP_RADIANS).coerceIn(-1.5707964f, 1.5707964f)
                            translationY = radius * sin(theta) - d * itemPx
                            val grow = 1f + 0.22f * (1f - ad.coerceIn(0f, 1f)) // selected row is a bit bigger
                            scaleX = grow
                            scaleY = grow * cos(theta)

                            alpha = when {
                                ad <= 1f -> 1f - 0.62f * ad
                                ad <= 2f -> 0.38f - 0.20f * (ad - 1f)
                                else -> (0.18f - 0.225f * (ad - 2f)).coerceAtLeast(0f)
                            }
                            compositingStrategy = CompositingStrategy.ModulateAlpha // keeps text crisp
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[index % size],
                        fontSize = fontSize,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
