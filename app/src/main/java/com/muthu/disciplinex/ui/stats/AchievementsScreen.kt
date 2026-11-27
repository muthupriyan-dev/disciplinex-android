package com.muthu.disciplinex.ui.stats

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.muthu.disciplinex.R
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
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * The badges screen. Which badge is unlocked, and every progress number, comes only from
 * [Achievements.evaluate] (real stored activity); this screen just draws the result.
 * The badge artwork is a set of PNG drawables (res/drawable-nodpi/badge_*.png).
 *
 * The only thing this screen stores itself is which unlocked badges it has already
 * celebrated (a small "achievements_ui" preference), so the unlock animation and the haptic
 * tick play once per new badge. That never touches the achievement data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val achievements = remember {
        val stats = StatsCalculator.compute(
            StreakStore.allDays(context),
            HistoryStore.getAll(context),
            LocalDate.now()
        )
        Achievements.evaluate(stats.totalDone, StreakStore.bestStreak(context), stats.totalReps)
    }
    val unlockedIds = remember(achievements) { achievements.filter { it.unlocked }.map { it.id }.toSet() }
    val celebrated = remember { loadCelebrated(context) } // null on the very first visit
    val newIds: Set<String> = remember(celebrated) {
        if (celebrated == null) emptySet() else unlockedIds - celebrated
    }
    var selected by remember { mutableStateOf<Achievement?>(null) }

    LaunchedEffect(Unit) {
        try {
            if (newIds.isNotEmpty()) {
                delay(450)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        } finally {
            saveCelebrated(context, unlockedIds)
        }
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(tr("Achievements", "சாதனைகள்"), fontWeight = FontWeight.Bold) },
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
        BoxWithConstraints(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Three columns on normal phones, two on very narrow screens.
            val columns = if (maxWidth >= 340.dp) 3 else 2
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(GAP)
            ) {
                ProgressBanner(achievements)

                achievements.chunked(columns).forEachIndexed { rowIndex, rowItems ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(GAP)
                    ) {
                        rowItems.forEachIndexed { col, a ->
                            BadgeCard(
                                a = a,
                                index = rowIndex * columns + col,
                                isNew = a.id in newIds,
                                onClick = { selected = a },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                        repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                TipCard()
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    selected?.let { BadgeDetailDialog(it, onDismiss = { selected = null }) }
}

private val GAP = 10.dp

// ---------------------------------------------------------------------------------------------
// Celebration bookkeeping (UI only)
// ---------------------------------------------------------------------------------------------

private const val UI_PREFS = "achievements_ui"
private const val KEY_CELEBRATED = "celebrated_ids"

private fun loadCelebrated(context: Context): Set<String>? {
    val p = context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
    return if (p.contains(KEY_CELEBRATED)) {
        p.getStringSet(KEY_CELEBRATED, emptySet())?.toSet() ?: emptySet()
    } else {
        null
    }
}

private fun saveCelebrated(context: Context, ids: Set<String>) {
    context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
        .edit().putStringSet(KEY_CELEBRATED, ids.toSet()).apply()
}

// ---------------------------------------------------------------------------------------------
// Look of each badge: artwork plus a colour identity
// ---------------------------------------------------------------------------------------------

private data class BadgeLook(
    @DrawableRes val image: Int,
    val accent: Color,
    val tintLight: Color
)

private fun lookFor(id: String): BadgeLook = when (id) {
    "first" -> BadgeLook(R.drawable.badge_first, Color(0xFF1B9E4B), Color(0xFFD3F7DC))
    "streak3" -> BadgeLook(R.drawable.badge_streak3, Color(0xFFF57C00), Color(0xFFFFE5C8))
    "streak7" -> BadgeLook(R.drawable.badge_streak7, Color(0xFF1E6BFF), Color(0xFFD6E6FF))
    "streak30" -> BadgeLook(R.drawable.badge_streak30, Color(0xFF8E3FF0), Color(0xFFE6D8FF))
    "done100" -> BadgeLook(R.drawable.badge_done100, Color(0xFF12A594), Color(0xFFCFF5EE))
    else -> BadgeLook(R.drawable.badge_reps500, Color(0xFFF59E0B), Color(0xFFFFEBC2))
}

/** Vibrant for unlocked badges, muted for locked ones. */
private fun badgeFilter(unlocked: Boolean): ColorFilter? =
    if (unlocked) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.25f) })

private fun tipBackground(): Color =
    if (AppSettings.isDark) Color(0xFF2A2A48) else Color(0xFFE8E8FF)

/** "1 day" / "3 days", "1 challenge" / "100 challenges", "1 rep" / "500 reps". */
private fun unitText(a: Achievement, n: Int): String =
    if (n == 1) {
        tr(
            a.unitEn.removeSuffix("s"),
            when (a.unitTa) {
                "நாட்கள்" -> "நாள்"
                "சவால்கள்" -> "சவால்"
                else -> a.unitTa
            }
        )
    } else {
        tr(a.unitEn, a.unitTa)
    }

private fun progressText(a: Achievement): String =
    "${minOf(a.progress, a.goal)} / ${a.goal} " + unitText(a, a.goal)

// ---------------------------------------------------------------------------------------------
// Banner and tip
// ---------------------------------------------------------------------------------------------

@Composable
private fun ProgressBanner(achievements: List<Achievement>) {
    val total = achievements.size
    val unlocked = achievements.count { it.unlocked }
    val next = achievements.filter { !it.unlocked }.maxByOrNull { it.fraction }
    val barTarget = if (total == 0) 0f else unlocked.toFloat() / total
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val bar by animateFloatAsState(if (started) barTarget else 0f, tween(800), label = "banner")

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
            modifier = Modifier.size(72.dp)
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
                if (next == null) {
                    tr("All badges unlocked. Amazing!", "எல்லா பதக்கங்களும் திறக்கப்பட்டன. அருமை!")
                } else {
                    tr(
                        "Next: ${next.titleEn} \u00b7 ${next.goal - next.progress} ${unitText(next, next.goal - next.progress)} to go",
                        "அடுத்தது: ${next.titleTa} \u00b7 இன்னும் ${next.goal - next.progress} ${unitText(next, next.goal - next.progress)}"
                    )
                },
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.18f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bar)
                        .fillMaxHeight()
                        .background(Color(0xFFFFC83D))
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "\uD83D\uDD25 " + tr(
                    "$unlocked / $total Badges Unlocked",
                    "$total-ல் $unlocked பதக்கம் திறக்கப்பட்டது"
                ),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

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
// Badge card
// ---------------------------------------------------------------------------------------------

@Composable
private fun BadgeCard(
    a: Achievement,
    index: Int,
    isNew: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val look = lookFor(a.id)

    // Gentle entrance, staggered by position, and a pop for a badge that was just unlocked.
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val enter by animateFloatAsState(
        if (started) 1f else 0f,
        tween(durationMillis = 380, delayMillis = index * 60),
        label = "enter"
    )
    val pop = remember { Animatable(1f) }
    LaunchedEffect(isNew) {
        if (isNew) {
            delay(450)
            pop.animateTo(1.18f, tween(200))
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }

    val top = if (AppSettings.isDark) lerp(Surface, look.accent, 0.20f) else look.tintLight
    val brush = if (a.unlocked) {
        Brush.verticalGradient(listOf(top, Surface))
    } else {
        Brush.verticalGradient(listOf(lerp(Surface, top, 0.45f), Surface))
    }

    Column(
        modifier = modifier
            .graphicsLayer {
                alpha = enter
                scaleX = 0.94f + 0.06f * enter
                scaleY = 0.94f + 0.06f * enter
            }
            .clip(RoundedCornerShape(20.dp))
            .background(brush)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(look.image),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = badgeFilter(a.unlocked),
            alpha = if (a.unlocked) 1f else 0.9f,
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            tr(a.titleEn, a.titleTa),
            color = TextPrimary,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            minLines = 2
        )
        Spacer(Modifier.height(2.dp))
        Text(
            tr(a.descEn, a.descTa),
            color = TextMuted,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
            minLines = 2
        )
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(10.dp))
        StatusPill(a.unlocked)
        Spacer(Modifier.height(8.dp))
        ProgressBlock(a, look.accent)
    }
}

@Composable
private fun StatusPill(unlocked: Boolean) {
    val color = if (unlocked) SuccessText else TextMuted
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (unlocked) Success.copy(alpha = 0.18f) else ChipBackground)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (unlocked) Icons.Default.CheckCircle else Icons.Default.Lock,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            if (unlocked) tr("Unlocked", "திறக்கப்பட்டது") else tr("Locked", "பூட்டப்பட்டது"),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/** The progress bar and the count under it; identical for every badge. */
@Composable
private fun ProgressBlock(a: Achievement, accent: Color) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val fraction by animateFloatAsState(
        if (started) a.fraction else 0f,
        tween(durationMillis = 700, delayMillis = 200),
        label = "progress"
    )
    val barColor = if (a.unlocked) Success else accent
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ChipBackground)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(barColor)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (a.unlocked) {
                "${a.goal} / ${a.goal} " + tr("Completed", "நிறைவு")
            } else {
                progressText(a)
            },
            color = if (a.unlocked) SuccessText else TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Detail dialog
// ---------------------------------------------------------------------------------------------

@Composable
private fun BadgeDetailDialog(a: Achievement, onDismiss: () -> Unit) {
    val look = lookFor(a.id)
    val top = if (AppSettings.isDark) lerp(Surface, look.accent, 0.22f) else look.tintLight
    val goalText = if (a.id == "first") tr("1 challenge", "1 சவால்") else "${a.goal} " + unitText(a, a.goal)
    val isStreak = a.id.startsWith("streak")

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(top, Surface)))
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(look.image),
                contentDescription = tr(a.titleEn, a.titleTa),
                contentScale = ContentScale.Fit,
                colorFilter = badgeFilter(a.unlocked),
                modifier = Modifier.fillMaxWidth().height(150.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                tr(a.titleEn, a.titleTa),
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                tr(a.descEn, a.descTa),
                color = TextMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            StatusPill(a.unlocked)
            Spacer(Modifier.height(14.dp))

            DetailLine(tr("Requirement", "தேவை"), goalText)
            Spacer(Modifier.height(8.dp))
            DetailLine(tr("Progress", "முன்னேற்றம்"), progressText(a))
            Spacer(Modifier.height(8.dp))
            ProgressBlockBar(a, look.accent)
            if (isStreak) {
                Spacer(Modifier.height(8.dp))
                Text(
                    tr(
                        "Counted from your best streak.",
                        "உங்கள் அதிகபட்ச தொடர்ச்சியான சாதனையிலிருந்து கணக்கிடப்படும்."
                    ),
                    color = TextMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onDismiss) {
                Text(tr("Close", "மூடு"), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextMuted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProgressBlockBar(a: Achievement, accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(ChipBackground)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(a.fraction)
                .fillMaxHeight()
                .background(if (a.unlocked) Success else accent)
        )
    }
}
