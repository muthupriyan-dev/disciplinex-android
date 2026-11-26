package com.muthu.disciplinex.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muthu.disciplinex.data.AppSettings
import com.muthu.disciplinex.data.ExerciseKinds
import com.muthu.disciplinex.data.HistoryStore
import com.muthu.disciplinex.data.StreakStore
import com.muthu.disciplinex.data.tr
import com.muthu.disciplinex.domain.Achievement
import com.muthu.disciplinex.ui.components.GuideButton
import com.muthu.disciplinex.ui.components.TourOverlay
import com.muthu.disciplinex.ui.components.TourStep
import com.muthu.disciplinex.ui.components.rememberTourState
import com.muthu.disciplinex.ui.components.tourTarget
import com.muthu.disciplinex.domain.Achievements
import com.muthu.disciplinex.domain.DayBar
import com.muthu.disciplinex.domain.ExerciseStat
import com.muthu.disciplinex.domain.Stats
import com.muthu.disciplinex.domain.StatsCalculator
import com.muthu.disciplinex.ui.theme.Background
import com.muthu.disciplinex.ui.theme.ChipBackground
import com.muthu.disciplinex.ui.theme.Danger
import com.muthu.disciplinex.ui.theme.OrangeStart
import com.muthu.disciplinex.ui.theme.Success
import com.muthu.disciplinex.ui.theme.SuccessText
import com.muthu.disciplinex.ui.theme.Surface
import com.muthu.disciplinex.ui.theme.TextMuted
import com.muthu.disciplinex.ui.theme.TextPrimary
import java.time.LocalDate
import java.time.format.TextStyle

/**
 * Statistics + achievements. Everything shown is read from stored activity
 * (StreakStore day counters and HistoryStore completions). Rep / minute /
 * per-exercise numbers only cover challenges completed since v1.2.1, and the
 * screen says so instead of pretending otherwise.
 */
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val stats = remember {
        StatsCalculator.compute(
            StreakStore.allDays(context),
            HistoryStore.getAll(context),
            LocalDate.now()
        )
    }
    val currentStreak = remember { StreakStore.currentStreak(context) }
    val bestStreak = remember { StreakStore.bestStreak(context) }
    val achievements = remember { Achievements.evaluate(stats.totalDone, bestStreak, stats.totalReps) }

    val tour = rememberTourState()
    var showTour by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val tourSteps = remember(showTour, AppSettings.language) {
        statsTourSteps().filter { tour.bounds.containsKey(it.key) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = tr("Back", "பின்செல்"),
                    tint = TextPrimary
                )
            }
            Text(
                tr("Statistics", "புள்ளிவிவரங்கள்"),
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            GuideButton(onClick = { showTour = true })
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (stats.totalDone == 0 && stats.totalMissed == 0) {
                Card {
                    Text(
                        tr(
                            "No challenges recorded yet. Finish your first alarm challenge and your numbers will appear here.",
                            "இன்னும் சவால் பதிவுகள் இல்லை. உங்கள் முதல் அலாரம் சவாலை முடித்தால், உங்கள் எண்கள் இங்கே தெரியும்."
                        ),
                        color = TextMuted,
                        fontSize = 15.sp
                    )
                }
            }

            Column(
                modifier = Modifier.tourTarget(tour, "numbers"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(tr("Completed", "முடிந்தவை"), stats.totalDone.toString(), Modifier.weight(1f))
                StatTile(
                    tr("Completion", "நிறைவு சதவீதம்"),
                    stats.completionPercent?.let { "$it%" } ?: "-",
                    Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(tr("Current streak", "தற்போதைய தொடர்ச்சியான சாதனை"), currentStreak.toString(), Modifier.weight(1f))
                StatTile(tr("Best streak", "அதிகபட்ச தொடர்ச்சியான சாதனை"), bestStreak.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(tr("Total reps", "மொத்த முறைகள்"), stats.totalReps.toString(), Modifier.weight(1f))
                StatTile(tr("Timed minutes", "டைமர் நிமிடங்கள்"), stats.totalMinutes.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(tr("Walked", "நடந்த தூரம்"), ExerciseKinds.distanceText(stats.totalMeters), Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }
            }

            Card(Modifier.tourTarget(tour, "chart")) {
                SectionTitle(tr("Last 7 days", "கடந்த 7 நாட்கள்"))
                Spacer(Modifier.height(12.dp))
                WeekChart(stats.last7)
                Spacer(Modifier.height(12.dp))
                Text(
                    tr(
                        "This month: ${stats.monthDone} done, ${stats.monthMissed} missed",
                        "இந்த மாதம்: ${stats.monthDone} முடிந்தது, ${stats.monthMissed} தவறியது"
                    ),
                    color = TextMuted,
                    fontSize = 14.sp
                )
            }

            Card(Modifier.tourTarget(tour, "exercise")) {
                SectionTitle(tr("By exercise", "பயிற்சி வாரியாக"))
                Spacer(Modifier.height(8.dp))
                if (stats.perExercise.isEmpty()) {
                    Text(
                        tr(
                            "Exercise details are recorded from version 1.2.1 onwards. Complete a challenge to see them.",
                            "பயிற்சி விவரங்கள் பதிப்பு 1.2.1 முதல் பதிவாகும். ஒரு சவாலை முடித்தால் இங்கே தெரியும்."
                        ),
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                } else {
                    stats.perExercise.forEach { ExerciseRow(it) }
                }
            }

            Card(Modifier.tourTarget(tour, "achievements")) {
                SectionTitle(tr("Achievements", "சாதனைகள்"))
                Spacer(Modifier.height(8.dp))
                achievements.forEach { AchievementRow(it) }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
    if (showTour) {
        TourOverlay(tourSteps, tour, onFinish = { showTour = false }, scrollState = scroll)
    }
    }
}

private fun statsTourSteps(): List<TourStep> = listOf(
    TourStep(
        "numbers", tr("Your numbers", "உங்கள் எண்கள்"),
        tr("Challenges you finished, how many of your alarms you completed, your streaks, total reps, timed minutes and distance walked. All of it comes from your real activity.",
            "நீங்கள் முடித்த சவால்கள், உங்கள் அலாரங்களில் எத்தனையை முடித்தீர்கள், தொடர்ச்சியான சாதனைகள், மொத்த முறைகள், டைமர் நிமிடங்கள், நடந்த தூரம் ஆகியவை. இவை எல்லாம் உங்கள் உண்மையான செயல்பாட்டிலிருந்தே வருகின்றன.")
    ),
    TourStep(
        "chart", tr("Last 7 days", "கடந்த 7 நாட்கள்"),
        tr("Green bars are days you finished, red bars are days you missed. The number above a bar is how many challenges that day.",
            "பச்சை பட்டை நீங்கள் முடித்த நாள், சிவப்பு பட்டை தவறிய நாள். பட்டைக்கு மேலே உள்ள எண், அந்த நாளின் சவால்களின் எண்ணிக்கை.")
    ),
    TourStep(
        "exercise", tr("By exercise", "பயிற்சி வாரியாக"),
        tr("Totals for each exercise. Details are saved from version 1.2.1 onwards, so older challenges are not listed here.",
            "ஒவ்வொரு பயிற்சிக்கான மொத்தம். விவரங்கள் பதிப்பு 1.2.1 முதல் சேமிக்கப்படுவதால், பழைய சவால்கள் இங்கே இருக்காது.")
    ),
    TourStep(
        "achievements", tr("Achievements", "சாதனைகள்"),
        tr("A badge unlocks only when your real activity reaches the goal, like a 7-day streak or 100 finished challenges.",
            "7 நாள் தொடர்ச்சியான சாதனை அல்லது 100 சவால்களை முடித்தது போன்ற இலக்கை உங்கள் உண்மையான செயல்பாடு எட்டினால் மட்டுமே பதக்கம் திறக்கும்.")
    )
)

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .padding(16.dp)
    ) { content() }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .padding(16.dp)
    ) {
        Text(value, color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextMuted, fontSize = 13.sp)
    }
}

@Composable
private fun WeekChart(days: List<DayBar>) {
    val maxCount = (days.maxOfOrNull { it.done + it.missed } ?: 0).coerceAtLeast(1)
    Row(
        modifier = Modifier.fillMaxWidth().height(140.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEach { d ->
            val total = d.done + d.missed
            val color = when {
                d.missed > 0 -> Danger
                d.done > 0 -> Success
                else -> ChipBackground
            }
            val barHeight = if (total == 0) 6.dp else (14 + 56 * total / maxCount).dp
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                if (total > 0) {
                    Text(total.toString(), color = TextMuted, fontSize = 11.sp)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .clip(RoundedCornerShape(6.dp))
                        .background(color)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, AppSettings.locale),
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ExerciseRow(s: ExerciseStat) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(ExerciseKinds.display(s.exercise), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                tr("${s.completed} completed", "${s.completed} முடிந்தது"),
                color = TextMuted,
                fontSize = 13.sp
            )
        }
        Text(
            if (s.distance) ExerciseKinds.distanceText(s.total) else if (s.timed) tr("${s.total} min", "${s.total} நிமிடம்") else tr("${s.total} reps", "${s.total} முறை"),
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AchievementRow(a: Achievement) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (a.unlocked) Success else ChipBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (a.unlocked) Icons.Default.Check else Icons.Default.Lock,
                contentDescription = if (a.unlocked) tr("Unlocked", "திறக்கப்பட்டது") else tr("Locked", "பூட்டப்பட்டது"),
                tint = if (a.unlocked) Color.White else TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tr(a.titleEn, a.titleTa),
                color = if (a.unlocked) SuccessText else TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(ChipBackground)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(a.fraction)
                        .height(6.dp)
                        .background(if (a.unlocked) Success else OrangeStart)
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "${minOf(a.progress, a.goal)} / ${a.goal} " + tr(a.unitEn, a.unitTa),
                color = TextMuted,
                fontSize = 12.sp
            )
        }
    }
}
