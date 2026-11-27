package com.muthu.disciplinex.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muthu.disciplinex.data.AppSettings
import com.muthu.disciplinex.data.HistoryStore
import com.muthu.disciplinex.data.StreakStore
import com.muthu.disciplinex.data.tr
import com.muthu.disciplinex.domain.Achievement
import com.muthu.disciplinex.domain.Achievements
import com.muthu.disciplinex.domain.StatsCalculator
import com.muthu.disciplinex.ui.theme.Background
import com.muthu.disciplinex.ui.theme.ChipBackground
import com.muthu.disciplinex.ui.theme.Success
import com.muthu.disciplinex.ui.theme.SuccessText
import com.muthu.disciplinex.ui.theme.Surface
import com.muthu.disciplinex.ui.theme.TextMuted
import com.muthu.disciplinex.ui.theme.TextPrimary
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin

/**
 * The badges screen. Which badge is unlocked is decided only by [Achievements.evaluate]
 * (real stored activity); this screen just draws the result. Badge artwork is drawn in
 * code, so there are no image files and it works in light and dark themes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val achievements = remember {
        val stats = StatsCalculator.compute(
            StreakStore.allDays(context),
            HistoryStore.getAll(context),
            LocalDate.now()
        )
        Achievements.evaluate(stats.totalDone, StreakStore.bestStreak(context), stats.totalReps)
    }
    val unlockedCount = achievements.count { it.unlocked }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(tr("Achievements", "சாதனைகள்"), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = tr("Back", "பின்செல்"))
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(tipBackground()),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProgressBanner(unlockedCount, achievements.size)

            achievements.chunked(3).forEach { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { a ->
                        BadgeCard(a, Modifier.weight(1f).fillMaxHeight())
                    }
                    repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            TipCard()
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Banner and tip
// ---------------------------------------------------------------------------------------------

@Composable
private fun ProgressBanner(unlocked: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF7B3FE4), Color(0xFF3B1F8F), Color(0xFF241466))
                )
            )
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.EmojiEvents,
            contentDescription = null,
            tint = Color(0xFFFFC83D),
            modifier = Modifier.size(76.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tr("Your Progress Journey", "உங்கள் முன்னேற்றப் பயணம்"),
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                tr("Keep going! You're doing great!", "தொடருங்கள்! நீங்கள் சிறப்பாக செய்கிறீர்கள்!"),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "\uD83D\uDD25 " + tr(
                    "$unlocked / $total Badges Unlocked",
                    "$total-ல் $unlocked பதக்கம் திறக்கப்பட்டது"
                ),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

private fun tipBackground(): Color =
    if (AppSettings.isDark) Color(0xFF2A2A48) else Color(0xFFE8E8FF)

@Composable
private fun TipCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(tipBackground())
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Lightbulb,
            contentDescription = null,
            tint = Color(0xFFFFB020),
            modifier = Modifier.size(34.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                tr("Tip:", "குறிப்பு:"),
                color = if (AppSettings.isDark) Color(0xFFB7B2FF) else Color(0xFF4B3FD6),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                tr(
                    "Complete your daily challenges to earn more badges!",
                    "மேலும் பதக்கங்களை பெற தினசரி சவால்களை முடியுங்கள்!"
                ),
                color = TextPrimary,
                fontSize = 14.sp
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// One badge card
// ---------------------------------------------------------------------------------------------

private data class BadgeStyle(val light: Color, val dark: Color, val ribbon: Color)

private fun styleFor(id: String): BadgeStyle = when (id) {
    "first" -> BadgeStyle(Color(0xFF5BE38A), Color(0xFF1B9E4B), Color(0xFF0E7A37))
    "streak3" -> BadgeStyle(Color(0xFFFFB04A), Color(0xFFE8631F), Color(0xFFD62F3A))
    "streak7" -> BadgeStyle(Color(0xFF7DB3FF), Color(0xFF2557C9), Color(0xFF1B3F9B))
    "streak30" -> BadgeStyle(Color(0xFFC58BFF), Color(0xFF7A2FD9), Color(0xFF5A1FB0))
    "done100" -> BadgeStyle(Color(0xFF4FE3CB), Color(0xFF0F9A8B), Color(0xFF0B766B))
    else -> BadgeStyle(Color(0xFFFFD25A), Color(0xFFE08E00), Color(0xFFB86A00))
}

@Composable
private fun BadgeCard(a: Achievement, modifier: Modifier) {
    val style = styleFor(a.id)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Badge(style) { BadgeFace(a, style) }
        Spacer(Modifier.height(6.dp))
        Text(
            tr(a.titleEn, a.titleTa),
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            minLines = 2
        )
        Spacer(Modifier.height(2.dp))
        Text(
            tr(a.descEn, a.descTa),
            color = TextMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(8.dp))
        if (a.unlocked) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Success.copy(alpha = 0.18f))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SuccessText,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    tr("Unlocked", "திறக்கப்பட்டது"),
                    color = SuccessText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        } else {
            ProgressPill(a, style)
        }
    }
}

@Composable
private fun ProgressPill(a: Achievement, style: BadgeStyle) {
    val fill = if (a.progress > 0) a.fraction.coerceAtLeast(0.1f) else 0f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ChipBackground)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fill)
                .fillMaxHeight()
                .background(style.dark)
        )
        Text(
            "${minOf(a.progress, a.goal)} / ${a.goal} " + tr(a.unitEn, a.unitTa),
            color = TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Badge artwork (hexagon medal with two ribbon tails), drawn with Canvas
// ---------------------------------------------------------------------------------------------

private fun hexagon(cx: Float, cy: Float, r: Float): Path = Path().apply {
    for (i in 0 until 6) {
        val angle = Math.toRadians((-90 + 60 * i).toDouble())
        val x = cx + (r * cos(angle)).toFloat()
        val y = cy + (r * sin(angle)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

@Composable
private fun Badge(style: BadgeStyle, face: @Composable () -> Unit) {
    Box(modifier = Modifier.size(76.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val cx = w / 2f
            val cy = size.height * 0.5f
            val r = w * 0.38f

            // Ribbon tails, behind the medal.
            val left = Path().apply {
                moveTo(cx - 0.62f * r, cy + 0.45f * r)
                lineTo(cx - 0.12f * r, cy + 0.55f * r)
                lineTo(cx - 0.30f * r, cy + 1.18f * r)
                lineTo(cx - 0.80f * r, cy + 1.05f * r)
                close()
            }
            val right = Path().apply {
                moveTo(cx + 0.62f * r, cy + 0.45f * r)
                lineTo(cx + 0.12f * r, cy + 0.55f * r)
                lineTo(cx + 0.30f * r, cy + 1.18f * r)
                lineTo(cx + 0.80f * r, cy + 1.05f * r)
                close()
            }
            drawPath(left, style.ribbon, style = Fill)
            drawPath(right, style.ribbon, style = Fill)

            // Outer medal.
            val outerBrush = Brush.verticalGradient(
                colors = listOf(style.light, style.dark),
                startY = cy - r,
                endY = cy + r
            )
            val outer = hexagon(cx, cy, r)
            drawPath(outer, outerBrush, style = Fill)
            drawPath(outer, outerBrush, style = Stroke(width = r * 0.14f, join = StrokeJoin.Round))

            // Inner medal.
            val innerBrush = Brush.verticalGradient(
                colors = listOf(lerp(style.dark, Color.Black, 0.18f), style.dark),
                startY = cy - r,
                endY = cy + r
            )
            val inner = hexagon(cx, cy, r * 0.78f)
            drawPath(inner, innerBrush, style = Fill)
            drawPath(inner, innerBrush, style = Stroke(width = r * 0.08f, join = StrokeJoin.Round))

            // Small highlight on the top edge.
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = Offset(cx - r * 0.35f, cy - r * 0.62f),
                end = Offset(cx + r * 0.35f, cy - r * 0.62f),
                strokeWidth = r * 0.06f
            )
        }
        face()
    }
}

@Composable
private fun BadgeFace(a: Achievement, style: BadgeStyle) {
    when (a.id) {
        "first" -> Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(34.dp)
        )
        "streak3", "streak7", "streak30" -> CalendarFace(a.goal.toString(), style)
        "done100" -> Icon(
            Icons.Default.Terrain,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
        else -> Icon(
            Icons.Default.Star,
            contentDescription = null,
            tint = Color(0xFFFFF1B8),
            modifier = Modifier.size(34.dp)
        )
    }
}

/** A tiny calendar page showing the day count. */
@Composable
private fun CalendarFace(number: String, style: BadgeStyle) {
    Column(
        modifier = Modifier
            .size(width = 30.dp, height = 32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .background(style.ribbon)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number,
                color = style.ribbon,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
