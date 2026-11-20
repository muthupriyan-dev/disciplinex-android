#!/usr/bin/env python3
"""Phase 3 patch: wires the Statistics screen into an EXISTING repo in place.
Run from the repo root:  python3 apply_phase3_patch.py
Each edit is anchored on exact text. If an anchor is missing the script stops
for that file and changes nothing in it. Running it twice is safe."""
import sys, os
M = "app/src/main/java/com/muthu/disciplinex/"
ok = True

def edit(path, pairs):
    global ok
    if not os.path.exists(path):
        print("MISSING FILE:", path); ok = False; return
    s = open(path, encoding="utf-8").read()
    orig = s
    for marker, old, new in pairs:
        if marker in s:
            continue  # already applied
        if s.count(old) != 1:
            print("ANCHOR NOT FOUND (or not unique) in", path, "->", old[:60].replace("\n", " "))
            ok = False
            return
        s = s.replace(old, new)
    if s != orig:
        open(path, "w", encoding="utf-8").write(s)
        print("patched", path)
    else:
        print("already patched", path)

edit(M + "ui/navigation/NavGraph.kt", [
    ("import com.muthu.disciplinex.ui.stats.StatsScreen",
     "import com.muthu.disciplinex.ui.settings.SettingsScreen\n",
     "import com.muthu.disciplinex.ui.settings.SettingsScreen\nimport com.muthu.disciplinex.ui.stats.StatsScreen\n"),
    ('const val STATS',
     '    const val HISTORY = "history"\n',
     '    const val HISTORY = "history"\n    const val STATS = "stats"\n'),
    ("onOpenStats = { navController.navigate(Routes.STATS) }",
     "                onOpenHistory = { navController.navigate(Routes.HISTORY) },\n                onStartChallenge",
     "                onOpenHistory = { navController.navigate(Routes.HISTORY) },\n                onOpenStats = { navController.navigate(Routes.STATS) },\n                onStartChallenge"),
    ("composable(Routes.STATS)",
     "        composable(Routes.ALLOWED_APPS) {",
     "        composable(Routes.STATS) {\n            StatsScreen(onBack = { navController.popBackStack() })\n        }\n        composable(Routes.ALLOWED_APPS) {"),
])

edit(M + "ui/home/HomeScreen.kt", [
    ("import androidx.compose.material.icons.filled.BarChart",
     "import androidx.compose.material.icons.filled.Add\n",
     "import androidx.compose.material.icons.filled.Add\nimport androidx.compose.material.icons.filled.BarChart\n"),
    ("onOpenStats: () -> Unit = {}",
     "    onOpenHistory: () -> Unit = {}\n) {",
     "    onOpenHistory: () -> Unit = {},\n    onOpenStats: () -> Unit = {}\n) {"),
    ("Icons.Default.BarChart",
     "                    IconButton(\n                        onClick = onOpenSettings,",
     "                    IconButton(\n                        onClick = onOpenStats\n                    ) { Icon(Icons.Default.BarChart, contentDescription = tr(\"Statistics\", \"புள்ளிவிவரங்கள்\"), tint = TextMuted) }\n                    IconButton(\n                        onClick = onOpenSettings,"),
])

edit(M + "data/StreakStore.kt", [
    ("fun allDays(",
     "    private fun statusOf(obj: JSONObject",
     """    /** Every recorded day as (done, missed) counters. Used by the statistics screen. */
    fun allDays(context: Context): Map<LocalDate, Pair<Int, Int>> {
        val obj = load(context)
        val out = HashMap<LocalDate, Pair<Int, Int>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val d = try { LocalDate.parse(key) } catch (e: Exception) { continue }
            val arr = obj.optJSONArray(key) ?: continue
            out[d] = arr.optInt(0) to arr.optInt(1)
        }
        return out
    }

    private fun statusOf(obj: JSONObject"""),
])

edit("app/build.gradle.kts", [
    ("testImplementation",
     '    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")\n',
     '    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")\n\n    testImplementation("junit:junit:4.13.2")\n'),
])
sys.exit(0 if ok else 1)
