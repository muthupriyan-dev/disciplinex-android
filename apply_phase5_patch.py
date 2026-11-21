#!/usr/bin/env python3
"""Phase 5 patch: History screen shows the completed challenges of the shown month
(exercise, amount, date, time from alarm to finish). Needs phases 2, 3 and 4 applied first.
Run from the repo root: python3 apply_phase5_patch.py   (safe to run twice)"""
import sys, os
P = "app/src/main/java/com/muthu/disciplinex/ui/settings/HistoryScreen.kt"
if not os.path.exists(P):
    print("MISSING", P); sys.exit(1)
s = open(P, encoding="utf-8").read()
if "private fun CompletionRow" in s:
    print("already patched", P); sys.exit(0)

def rep(old, new):
    global s
    if s.count(old) != 1:
        print("ANCHOR PROBLEM (%d matches): %s" % (s.count(old), old[:70].replace("\n", " ")))
        sys.exit(1)
    s = s.replace(old, new)

rep("import com.muthu.disciplinex.data.AppSettings\n",
    "import com.muthu.disciplinex.data.AppSettings\nimport com.muthu.disciplinex.data.ExerciseKinds\nimport com.muthu.disciplinex.data.HistoryStore\n")
rep("    val best = remember { StreakStore.bestStreak(context) }\n",
    "    val best = remember { StreakStore.bestStreak(context) }\n    val completions = remember { HistoryStore.getAll(context).sortedByDescending { it.completedAtMillis } }\n")
rep('''                LegendDot(ChipBackground, tr("No result", "முடிவு இல்லை"))
            }
''','''                LegendDot(ChipBackground, tr("No result", "முடிவு இல்லை"))
            }

            Spacer(modifier = Modifier.height(20.dp))
            val monthEntries = completions.filter { e ->
                try { YearMonth.from(LocalDate.parse(e.date)) == month } catch (ex: Exception) { false }
            }
            Text(
                tr("Completed challenges", "முடித்த சேலஞ்ச்கள்"),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (monthEntries.isEmpty()) {
                Text(
                    tr(
                        "No detailed records for this month. Details are saved from version 1.2.1 onwards.",
                        "இந்த மாதத்திற்கு விரிவான பதிவுகள் இல்லை. விவரங்கள் பதிப்பு 1.2.1 முதல் சேமிக்கப்படும்."
                    ),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                monthEntries.forEach { e -> CompletionRow(e) }
            }
''')
s = s.rstrip("\n") + '''

@Composable
private fun CompletionRow(e: HistoryStore.Entry) {
    val amount = when {
        e.distance -> ExerciseKinds.distanceText(e.target)
        e.timed -> tr("${e.target} min", "${e.target} நிமிடம்")
        else -> tr("${e.target} reps", "${e.target} முறை")
    }
    val totalSec = (e.durationMs / 1000).coerceAtLeast(0)
    val took = String.format(java.util.Locale.US, "%d:%02d", totalSec / 60, totalSec % 60)
    val dayLabel = try {
        LocalDate.parse(e.date).let { it.dayOfMonth.toString() + " " + it.month.getDisplayName(TextStyle.SHORT, AppSettings.locale) }
    } catch (ex: Exception) {
        e.date
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(ExerciseKinds.display(e.exercise), fontWeight = FontWeight.SemiBold)
            Text(
                dayLabel + " \\u00b7 " + tr("alarm to finish $took", "அலாரம் முதல் முடிவு வரை $took"),
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(amount, fontWeight = FontWeight.Bold)
    }
}
'''
open(P, "w", encoding="utf-8").write(s)
print("patched", P)
