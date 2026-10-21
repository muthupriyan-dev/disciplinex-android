package com.muthu.disciplinex.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muthu.disciplinex.ui.theme.OrangeEnd
import com.muthu.disciplinex.ui.theme.Surface
import com.muthu.disciplinex.ui.theme.TextMuted
import com.muthu.disciplinex.ui.theme.TextPrimary

/** One step of the "what does this button do?" guide. */
data class TourStep(val key: String, val title: String, val body: String)

/** Remembers where each tagged element is on screen. */
class TourState {
    val bounds: SnapshotStateMap<String, Rect> = mutableStateMapOf()
}

@Composable
fun rememberTourState(): TourState = remember { TourState() }

/** Tag a composable so the guide can highlight it. */
fun Modifier.tourTarget(state: TourState, key: String): Modifier =
    this.onGloballyPositioned { state.bounds[key] = it.boundsInRoot() }

/**
 * A dimmed overlay that highlights one element at a time with a short
 * explanation, a Next button and a Skip button.
 */
@Composable
fun TourOverlay(steps: List<TourStep>, state: TourState, onFinish: () -> Unit) {
    if (steps.isEmpty()) {
        LaunchedEffect(Unit) { onFinish() }
        return
    }
    var index by remember { mutableStateOf(0) }
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    val target = state.bounds[step.key]
    val isLast = index >= steps.lastIndex
    val screenHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }
    val cardAtTop = target != null && target.center.y > screenHeightPx / 2f

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Swallow taps so the screen underneath can't be used during the guide.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            drawRect(Color.Black.copy(alpha = 0.72f))
            if (target != null) {
                val pad = 8.dp.toPx()
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(target.left - pad, target.top - pad),
                    size = Size(target.width + pad * 2, target.height + pad * 2),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    blendMode = BlendMode.Clear
                )
            }
        }

        Column(
            modifier = Modifier
                .align(if (cardAtTop) Alignment.TopCenter else Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 48.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Surface)
                .padding(20.dp)
        ) {
            Text(
                "${index + 1} of ${steps.size}",
                color = TextMuted,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(step.title, color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(step.body, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onFinish) { Text("Skip", color = TextMuted) }
                TextButton(onClick = { if (isLast) onFinish() else index++ }) {
                    Text(if (isLast) "Got it" else "Next", color = OrangeEnd, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
