package com.muthu.disciplinex.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muthu.disciplinex.data.AppSettings
import com.muthu.disciplinex.data.StreakStore
import com.muthu.disciplinex.data.tr
import com.muthu.disciplinex.ui.theme.Background
import com.muthu.disciplinex.ui.theme.ChipBackground
import com.muthu.disciplinex.ui.theme.Danger
import com.muthu.disciplinex.ui.theme.OrangeEnd
import com.muthu.disciplinex.ui.theme.Success
import com.muthu.disciplinex.ui.theme.Surface
import com.muthu.disciplinex.ui.theme.TextMuted
import com.muthu.disciplinex.ui.theme.TextPrimary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

/** Month calendar of finished (green) and missed (red) days, plus current and best streak. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    var month by remember { mutableStateOf(YearMonth.from(today)) }

    val streak = remember { StreakStore.currentStreak(context) }
    val best = remember { StreakStore.bestStreak(context) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(tr("Streak history", "ஸ்ட்ரீக் வரலாறு"), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = tr("Back", "பின்"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ---- Streak numbers ----
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatBox(
                    value = "\uD83D\uDD25 $streak",
                    label = tr("Current streak", "தற்போதைய ஸ்ட்ரீக்"),
                    modifier = Modifier.weight(1f)
                )
                StatBox(
                    value = "\uD83C\uDFC6 $best",
                    label = tr("Best streak", "சிறந்த ஸ்ட்ரீக்"),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Calendar ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Surface)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { month = month.minusMonths(1) }) {
                        Text("\u2039", fontSize = 24.sp, color = OrangeEnd, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = month.month.getDisplayName(TextStyle.FULL, AppSettings.locale) + " " + month.year,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(
                        onClick = { month = month.plusMonths(1) },
                        enabled = month.isBefore(YearMonth.from(today))
                    ) {
                        Text(
                            "\u203A",
                            fontSize = 24.sp,
                            color = if (month.isBefore(YearMonth.from(today))) OrangeEnd else TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Weekday header (Monday first)
                Row(modifier = Modifier.fillMaxWidth()) {
                    DayOfWeek.values().forEach { dow ->
                        Text(
                            dow.getDisplayName(TextStyle.NARROW, AppSettings.locale),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            color = TextMuted,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                val offset = month.atDay(1).dayOfWeek.value - 1
                val cells = offset + month.lengthOfMonth()
                val rows = (cells + 6) / 7
                for (r in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val dayNumber = r * 7 + c - offset + 1
                            Box(
                                modifier = Modifier.weight(1f).aspectRatio(1f).padding(3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (dayNumber in 1..month.lengthOfMonth()) {
                                    DayCell(month.atDay(dayNumber), today)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ---- Legend ----
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendDot(Success, tr("Finished", "முடித்தது"))
                LegendDot(Danger, tr("Missed", "தவறியது"))
                LegendDot(ChipBackground, tr("No result", "முடிவு இல்லை"))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, today: LocalDate) {
    val context = LocalContext.current
    val future = date.isAfter(today)
    val status = remember(date) {
        if (future) StreakStore.DayStatus.NONE else StreakStore.status(context, date)
    }
    val bg = when (status) {
        StreakStore.DayStatus.DONE -> Success
        StreakStore.DayStatus.MISSED -> Danger
        StreakStore.DayStatus.NONE -> ChipBackground
    }
    val textColor = when {
        status != StreakStore.DayStatus.NONE -> Color.White
        future -> TextMuted
        else -> TextPrimary
    }
    var modifier = Modifier.fillMaxSize().clip(CircleShape).background(bg)
    if (date == today) modifier = modifier.border(BorderStroke(2.dp, OrangeEnd), CircleShape)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            "${date.dayOfMonth}",
            color = textColor,
            fontWeight = if (status != StreakStore.DayStatus.NONE) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun StatBox(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .padding(16.dp)
    ) {
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
        Text(label, color = TextMuted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = TextMuted, style = MaterialTheme.typography.labelMedium)
    }
}
