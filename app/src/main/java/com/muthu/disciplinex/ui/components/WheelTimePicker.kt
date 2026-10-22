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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A native-feeling wheel time picker (hour / minute / AM-PM),
 * matching the iOS-style scroll wheel with center snap + fade.
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
            .height(200.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        WheelColumn(
            items = hourLabels,
            initialIndex = hours.indexOf(initialHour).coerceAtLeast(0),
            onSelected = { index -> selectedHour = hours[index] }
        )

        Text(
            text = ":",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        WheelColumn(
            items = minuteLabels,
            initialIndex = minutes.indexOf(initialMinute).coerceAtLeast(0),
            onSelected = { index -> selectedMinute = minutes[index] }
        )

        Box(modifier = Modifier.width(24.dp))

        WheelColumn(
            items = ampm,
            initialIndex = if (initialIsAm) 0 else 1,
            onSelected = { index -> selectedIsAm = index }
        )
    }
}

private val ITEM_HEIGHT = 48.dp
private const val VISIBLE_ITEMS = 5 // must be odd: 2 above, centre, 2 below

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
        val projected = start + decay.getTargetValue(0f, initialVelocity)
        val targetIndex = (projected / itemPx).roundToInt().coerceIn(0, lastIndex)
        val distance = targetIndex * itemPx - start
        if (abs(distance) < 0.5f) return 0f

        val rows = abs(distance) / itemPx
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
 * One wheel (hours, minutes or AM/PM).
 *
 *  - The row styling (size, fade) is applied inside graphicsLayer, so while the
 *    wheel moves only the draw layer updates - no recomposition per frame.
 *  - The selected value is reported only when the wheel is at rest, so dragging
 *    doesn't recompose the whole alarm screen on every row.
 *  - A light tick is played every time a new row passes the centre.
 */
@Composable
private fun WheelColumn(
    items: List<String>,
    initialIndex: Int,
    onSelected: (Int) -> Unit
) {
    val itemPx = with(LocalDensity.current) { ITEM_HEIGHT.roundToPx() }
    val lastIndex = items.lastIndex
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val fling = remember(listState, itemPx, lastIndex) { WheelFlingBehavior(listState, itemPx, lastIndex) }
    val haptic = LocalHapticFeedback.current
    val currentOnSelected by rememberUpdatedState(onSelected)

    LaunchedEffect(listState, itemPx, lastIndex) {
        snapshotFlow { if (listState.isScrollInProgress) -1 else centerIndexOf(listState, itemPx, lastIndex) }
            .filter { it >= 0 }
            .distinctUntilChanged()
            .collect { currentOnSelected(it) }
    }

    LaunchedEffect(listState, itemPx, lastIndex) {
        snapshotFlow { centerIndexOf(listState, itemPx, lastIndex) }
            .distinctUntilChanged()
            .drop(1)
            .collect { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }

    Box(
        modifier = Modifier
            .width(64.dp)
            .height(ITEM_HEIGHT * VISIBLE_ITEMS)
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = fling,
            // Padding lets the first and last real rows sit in the centre row.
            contentPadding = PaddingValues(vertical = ITEM_HEIGHT * (VISIBLE_ITEMS / 2)),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(items) { index, label ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ITEM_HEIGHT)
                        .graphicsLayer {
                            val pos = listState.firstVisibleItemIndex * itemPx +
                                listState.firstVisibleItemScrollOffset
                            val d = abs(index * itemPx - pos) / itemPx.toFloat() // rows from centre
                            val near = 1f - d.coerceIn(0f, 1f)
                            val scale = 1f + 0.45f * near           // 22sp -> ~32sp at the centre
                            scaleX = scale
                            scaleY = scale
                            alpha = if (d <= 1f) 1f - 0.62f * d else (0.38f - 0.2f * (d - 1f)).coerceAtLeast(0.08f)
                            compositingStrategy = CompositingStrategy.ModulateAlpha // keeps text crisp
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
