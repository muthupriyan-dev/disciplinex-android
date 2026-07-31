package com.muthu.disciplinex.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlin.math.abs

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
    val hours = (1..12).toList()
    val minutes = (0..59).toList()
    val ampm = listOf("AM", "PM")

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
            items = hours.map { it.toString().padStart(2, '0') },
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
            items = minutes.map { it.toString().padStart(2, '0') },
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

/**
 * A single scrollable, snapping wheel column used by [WheelTimePicker].
 * Centers the selected item, scales/fades items based on distance from center.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelColumn(
    items: List<String>,
    initialIndex: Int,
    onSelected: (Int) -> Unit
) {
    val itemHeight = 48.dp
    val visibleItems = 5 // must be odd: 2 above, center, 2 below
    val paddingCount = visibleItems / 2

    // Pad the list conceptually by centering via contentPadding instead of fake items,
    // so index math stays simple and matches the real data.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    // Determine the item currently closest to the center of the column.
    val currentCenterItem by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { item ->
                abs((item.offset + item.size / 2) - viewportCenter)
            }?.index ?: initialIndex
        }
    }

    LaunchedEffect(currentCenterItem) {
        onSelected(currentCenterItem.coerceIn(0, items.lastIndex))
    }

    Box(
        modifier = Modifier
            .width(64.dp)
            .height(itemHeight * visibleItems)
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                vertical = itemHeight * paddingCount
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(items) { index, label ->
                val distance = abs(index - currentCenterItem)
                val isSelected = distance == 0

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = if (isSelected) 32.sp else 22.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isSelected -> Color.Black
                            distance == 1 -> Color(0xFF9E9E9E)
                            else -> Color(0xFFD0D0D0)
                        },
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
