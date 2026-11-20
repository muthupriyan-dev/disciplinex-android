#!/usr/bin/env python3
"""Phase 4 patch: (1) stricter push-up / squat / jumping-jack counting,
(2) Walking challenge measured in distance (steps x step length) instead of a timer.

Run from the repo root (after phase 2 + phase 3 are applied):
    python3 apply_phase4_patch.py
Every edit is anchored on exact text; a file is only written if ALL its anchors are found.
Running it twice is safe ("already patched")."""
import hashlib, os, shutil, sys

M = "app/src/main/java/com/muthu/disciplinex/"
HERE = os.path.dirname(os.path.abspath(__file__))
ok = True

def sha(path):
    return hashlib.sha256(open(path, "rb").read()).hexdigest()

def edit(path, pairs):
    """pairs: (marker, old, new[, allow_many]). marker already in file -> skipped."""
    global ok
    if not os.path.exists(path):
        print("MISSING FILE:", path); ok = False; return
    s = open(path, encoding="utf-8").read()
    orig = s
    for pair in pairs:
        marker, old, new = pair[0], pair[1], pair[2]
        many = len(pair) > 3 and pair[3]
        if marker in s:
            continue
        n = s.count(old)
        if n == 0 or (n > 1 and not many):
            print("ANCHOR PROBLEM (%d matches) in %s -> %s" % (n, path, old[:70].replace("\n", " ")))
            ok = False
            return
        s = s.replace(old, new)
    if s != orig:
        open(path, "w", encoding="utf-8").write(s)
        print("patched", path)
    else:
        print("already patched", path)

# ---------------------------------------------------------------- RepCounter
REPCOUNTER_ORIGINAL_SHA = "fade30c07f80270780e556191c5dbf907111a920860fc634660cf33463578939"
rc = M + "pose/RepCounter.kt"
new_rc = os.path.join(HERE, "RepCounter.kt")
if os.path.exists(rc) and os.path.exists(new_rc):
    if sha(rc) == sha(new_rc):
        print("already patched", rc)
    elif sha(rc) == REPCOUNTER_ORIGINAL_SHA:
        shutil.copyfile(new_rc, rc)
        print("replaced", rc)
    else:
        print("!! RepCounter.kt in your repo is DIFFERENT from the original (you edited it).")
        print("!! Not overwriting it. Send me your current RepCounter.kt and I will merge the fix.")
        ok = False
else:
    print("RepCounter.kt or the new RepCounter.kt (next to this script) not found"); ok = False

# ------------------------------------------------------------- ExerciseKinds
edit(M + "data/ExerciseKinds.kt", [
    ("val DISTANCE",
     'val TIMED = setOf("Reading", "Meditation", "Walking")',
     'val TIMED = setOf("Reading", "Meditation")\n\n    /** Walking: finished by walking a distance, measured from the step counter. */\n    val DISTANCE = setOf("Walking")'),
    ("fun isDistance",
     "    fun isTimed(name: String) = name in TIMED",
     '''    fun isTimed(name: String) = name in TIMED
    fun isDistance(name: String) = name in DISTANCE

    /** "350 m" or "1.5 km". */
    fun distanceText(meters: Int): String =
        if (meters >= 1000) String.format(java.util.Locale.US, "%.1f km", meters / 1000.0) else "$meters m"'''),
])

# ---------------------------------------------------------------- AlarmEntry
edit(M + "data/AlarmEntry.kt", [
    ("val meters: Int = DEFAULT_METERS",
     "    val windowMinutes: Int = DEFAULT_WINDOW_MINUTES\n) {",
     "    val windowMinutes: Int = DEFAULT_WINDOW_MINUTES,\n    /** Target distance in metres for the Walking challenge. */\n    val meters: Int = DEFAULT_METERS\n) {"),
    ("val isDistance",
     "    val isTimed: Boolean get() = ExerciseKinds.isTimed(exercise)\n",
     "    val isTimed: Boolean get() = ExerciseKinds.isTimed(exercise)\n    val isDistance: Boolean get() = ExerciseKinds.isDistance(exercise)\n"),
    ("ExerciseKinds.distanceText(meters)",
     "val summary: String get() = if (isTimed)",
     "val summary: String get() = if (isDistance) ExerciseKinds.distanceText(meters) else if (isTimed)"),
    ('.put("dm", meters)',
     '.put("w", windowMinutes)',
     '.put("w", windowMinutes)\n        .put("dm", meters)'),
    ("DEFAULT_METERS = 500",
     "        const val DEFAULT_WINDOW_MINUTES = 15",
     "        const val DEFAULT_WINDOW_MINUTES = 15\n        const val DEFAULT_METERS = 500"),
    ("ExerciseKinds.isDistance(exercise) -> 45",
     "                ExerciseKinds.isTimed(exercise) -> minutes + 10",
     "                ExerciseKinds.isDistance(exercise) -> 45\n                ExerciseKinds.isTimed(exercise) -> minutes + 10"),
    ('o.optInt("dm"',
     "                windowMinutes = window\n            )",
     '                windowMinutes = window,\n                meters = o.optInt("dm", DEFAULT_METERS).coerceIn(10, 100_000)\n            )'),
])

# ---------------------------------------------------------- ChallengeSession
edit(M + "data/ChallengeSession.kt", [
    ("K_METERS",
     '    private const val K_MINUTES = "minutes"\n',
     '    private const val K_MINUTES = "minutes"\n    private const val K_METERS = "meters"\n'),
    (".putInt(K_METERS, alarm.meters)",
     "            .putInt(K_MINUTES, alarm.minutes)\n",
     "            .putInt(K_MINUTES, alarm.minutes)\n            .putInt(K_METERS, alarm.meters)\n"),
    ("fun meters(context",
     "    fun minutes(context: Context): Int = prefs(context).getInt(K_MINUTES, 15)\n",
     "    fun minutes(context: Context): Int = prefs(context).getInt(K_MINUTES, 15)\n    fun meters(context: Context): Int = prefs(context).getInt(K_METERS, AlarmEntry.DEFAULT_METERS)\n"),
    ("ExerciseKinds.distanceText(meters(context))",
     "fun summary(context: Context): String =\n        if (ExerciseKinds.isTimed(exercise(context)))",
     "fun summary(context: Context): String =\n        if (ExerciseKinds.isDistance(exercise(context))) ExerciseKinds.distanceText(meters(context))\n        else if (ExerciseKinds.isTimed(exercise(context)))"),
    ("ExerciseKinds.isDistance(ex) -> meters(context)",
     "                    target = if (ExerciseKinds.isTimed(ex)) minutes(context) else reps(context),\n                    durationMs = elapsedSinceRing(context, p).coerceAtLeast(0L)",
     "                    target = when {\n                        ExerciseKinds.isDistance(ex) -> meters(context)\n                        ExerciseKinds.isTimed(ex) -> minutes(context)\n                        else -> reps(context)\n                    },\n                    durationMs = elapsedSinceRing(context, p).coerceAtLeast(0L),\n                    distance = ExerciseKinds.isDistance(ex)"),
])

# ------------------------------------------------------------- HistoryStore
edit(M + "data/HistoryStore.kt", [
    ("val distance: Boolean = false",
     "        val durationMs: Long\n    )",
     "        val durationMs: Long,\n        /** True for the Walking challenge: [target] is then metres. */\n        val distance: Boolean = false\n    )"),
    ('o.optBoolean("ds")',
     'durationMs = o.optLong("ms")',
     'durationMs = o.optLong("ms"),\n                    distance = o.optBoolean("ds")'),
    ('.put("ds", it.distance)',
     '.put("ms", it.durationMs)',
     '.put("ms", it.durationMs)\n                    .put("ds", it.distance)'),
])

# ------------------------------------------------------------ StatsCalculator
edit(M + "domain/StatsCalculator.kt", [
    ("val distance: Boolean = false",
     "    val total: Int\n)",
     "    val total: Int,\n    /** True for walking: [total] is metres. */\n    val distance: Boolean = false\n)"),
    ("val totalMeters: Int,",
     "    val totalMinutes: Int,\n",
     "    val totalMinutes: Int,\n    val totalMeters: Int,\n"),
    ("val walk = ",
     "        val rep = history.filter { !it.timed }\n",
     "        val rep = history.filter { !it.timed && !it.distance }\n        val walk = history.filter { it.distance }\n"),
    ("distance = list.first().distance",
     "                    total = list.sumOf { it.target.coerceAtLeast(0) }\n",
     "                    total = list.sumOf { it.target.coerceAtLeast(0) },\n                    distance = list.first().distance\n"),
    ("totalMeters = walk.sumOf",
     "            totalMinutes = timed.sumOf { it.target.coerceAtLeast(0) },\n",
     "            totalMinutes = timed.sumOf { it.target.coerceAtLeast(0) },\n            totalMeters = walk.sumOf { it.target.coerceAtLeast(0) },\n"),
])

# --------------------------------------------------------------- StatsScreen
edit(M + "ui/stats/StatsScreen.kt", [
    ("s.distance",
     'if (s.timed) tr("${s.total} min", "${s.total} நிமிடம்") else',
     'if (s.distance) ExerciseKinds.distanceText(s.total) else if (s.timed) tr("${s.total} min", "${s.total} நிமிடம்") else'),
    ("tr(\"Walked\"",
     '                StatTile(tr("Timed minutes", "நேர நிமிடங்கள்"), stats.totalMinutes.toString(), Modifier.weight(1f))\n            }\n',
     '                StatTile(tr("Timed minutes", "நேர நிமிடங்கள்"), stats.totalMinutes.toString(), Modifier.weight(1f))\n            }\n            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {\n                StatTile(tr("Walked", "நடந்த தூரம்"), ExerciseKinds.distanceText(stats.totalMeters), Modifier.weight(1f))\n                Spacer(Modifier.weight(1f))\n            }\n'),
])

# ----------------------------------------------------------------- WalkSteps
edit(M + "data/WalkSteps.kt", [
    ("fun strideCm",
     "    fun required(minutes: Int): Int = minutes.coerceAtLeast(1) * STEPS_PER_MINUTE\n",
     '''    fun required(minutes: Int): Int = minutes.coerceAtLeast(1) * STEPS_PER_MINUTE

    /** Average adult step is roughly 70 cm. The user can adjust it in the alarm editor. */
    const val DEFAULT_STRIDE_CM = 70
    private const val K_STRIDE = "stride_cm"

    fun strideCm(context: Context): Int =
        prefs(context).getInt(K_STRIDE, DEFAULT_STRIDE_CM).coerceIn(40, 120)

    fun setStrideCm(context: Context, cm: Int) {
        prefs(context).edit().putInt(K_STRIDE, cm.coerceIn(40, 120)).apply()
    }

    /** True if the phone has a step counter AND the Physical activity permission is granted. */
    fun isAvailable(context: Context): Boolean {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        return sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null && hasPermission(context)
    }
'''),
])

# -------------------------------------------------------------- TargetStepper
edit(M + "ui/components/TargetStepper.kt", [
    ("smallStep: Int = 1",
     "    bigStep: Int = 5\n) {",
     "    bigStep: Int = 5,\n    smallStep: Int = 1\n) {"),
    ("(value - smallStep)",
     r'StepButton("\u22121", enabled && value > min) { onValueChange((value - 1).coerceAtLeast(min)) }',
     r'StepButton("\u2212$smallStep", enabled && value > min) { onValueChange((value - smallStep).coerceAtLeast(min)) }'),
    ("(value + smallStep)",
     'StepButton("+1", enabled && value < max) { onValueChange((value + 1).coerceAtMost(max)) }',
     'StepButton("+$smallStep", enabled && value < max) { onValueChange((value + smallStep).coerceAtMost(max)) }'),
])

# ---------------------------------------------------------- ExerciseTypeScreen
edit(M + "ui/onboarding/ExerciseTypeScreen.kt", [
    ("ExerciseKinds.isDistance(option.name)",
     '                ExerciseKinds.isTimed(option.name) -> tr("Timer", "டைமர்")',
     '                ExerciseKinds.isDistance(option.name) -> tr("Steps", "அடிகள்")\n                ExerciseKinds.isTimed(option.name) -> tr("Timer", "டைமர்")'),
])

# ------------------------------------------------------------- AlarmEditScreen
EN_OLD = "Walking, meditation and reading run on a timer you can't skip. Walking also checks your steps."
EN_NEW = "Walking is measured by the distance you walk (your phone's step counter x your step length). Meditation and reading run on a timer you can't skip."
TA_OLD = "நடைப்பயிற்சி, தியானம், வாசிப்பு தவிர்க்க முடியாத டைமரில் ஓடும். நடைப்பயிற்சியில் உங்கள் அடிகளும் சரிபார்க்கப்படும்."
TA_NEW = "நடைப்பயிற்சி நீங்கள் நடக்கும் தூரத்தால் அளக்கப்படும் (போனின் அடி கவுண்டர் x உங்கள் அடி நீளம்). தியானம், வாசிப்பு தவிர்க்க முடியாத டைமரில் ஓடும்."
edit(M + "ui/alarms/AlarmEditScreen.kt", [
    ("var meters by remember",
     "    var minutes by remember { mutableStateOf(existing?.minutes ?: 15) }\n",
     "    var minutes by remember { mutableStateOf(existing?.minutes ?: 15) }\n    var meters by remember { mutableStateOf(existing?.meters ?: AlarmEntry.DEFAULT_METERS) }\n    var strideCm by remember { mutableStateOf(com.muthu.disciplinex.data.WalkSteps.strideCm(context)) }\n"),
    ("val isDistance = ",
     "    val isTimed = ExerciseKinds.isTimed(selected.name)\n    var windowMin",
     "    val isTimed = ExerciseKinds.isTimed(selected.name)\n    val isDistance = ExerciseKinds.isDistance(selected.name)\n    var windowMin"),
    ("if (isDistance) ((meters + 59) / 60)",
     "    val minWindow = if (isTimed) minutes else 1\n",
     "    // Walking needs at least ~1 m/s (a slow walk) to be possible inside the time limit.\n    val minWindow = if (isDistance) ((meters + 59) / 60).coerceAtLeast(5) else if (isTimed) minutes else 1\n"),
    ("meters = meters,",
     "            minutes = minutes,\n            enabled = true,",
     "            minutes = minutes,\n            meters = meters,\n            enabled = true,"),
    ("WalkSteps.setStrideCm",
     "        AlarmStore.save(context, alarm)\n        AlarmScheduler.schedule(context, alarm)\n        onClose()",
     "        if (alarm.isDistance) com.muthu.disciplinex.data.WalkSteps.setStrideCm(context, strideCm)\n        AlarmStore.save(context, alarm)\n        AlarmScheduler.schedule(context, alarm)\n        onClose()"),
    ('tr("How far?"',
     'if (isTimed) tr("How many minutes?", "எத்தனை நிமிடங்கள்?") else tr("How many reps?", "எத்தனை முறை?"),',
     'if (isDistance) tr("How far?", "எவ்வளவு தூரம்?") else if (isTimed) tr("How many minutes?", "எத்தனை நிமிடங்கள்?") else tr("How many reps?", "எத்தனை முறை?"),'),
    ("bigStep = 250, smallStep = 50",
     '                if (isTimed) {\n                    TargetStepper(value = minutes,',
     '''                if (isDistance) {
                    TargetStepper(
                        value = meters, unit = tr("metres", "மீட்டர்"), min = 100, max = 10000,
                        onValueChange = { meters = it }, bigStep = 250, smallStep = 50
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(tr("Your step length", "உங்கள் அடி நீளம்"), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        tr("Distance = steps counted by your phone x step length, so it is an estimate. A normal step is about 70 cm. Adjust it until the distance matches a walk you know.", "தூரம் = போன் எண்ணும் அடிகள் x அடி நீளம், எனவே இது மதிப்பீடு. சாதாரண அடி சுமார் 70 செ.மீ. உங்களுக்குத் தெரிந்த தூரத்துடன் பொருந்தும்படி மாற்றுங்கள்."),
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TargetStepper(value = strideCm, unit = tr("cm per step", "செ.மீ / அடி"), min = 40, max = 120, onValueChange = { strideCm = it })
                } else if (isTimed) {
                    TargetStepper(value = minutes,'''),
    ("Walking is measured by the distance", EN_OLD, EN_NEW, True),
    ("நடைப்பயிற்சி நீங்கள் நடக்கும் தூரத்தால்", TA_OLD, TA_NEW, True),
])

# ------------------------------------------------------------- ChallengeGate
edit(M + "ui/challenge/ChallengeGate.kt", [
    ("targetMeters = ChallengeSession.meters(context)",
     "                durationMinutes = ChallengeSession.minutes(context),\n",
     "                durationMinutes = ChallengeSession.minutes(context),\n                targetMeters = ChallengeSession.meters(context),\n"),
])

# ----------------------------------------------------------- ChallengeScreen
WALK_CODE = '''

// ----------------------------------------------------------------------
// Walking: finished by walking a distance (phone step counter x step length).
// ----------------------------------------------------------------------

private fun challengeTourStepsDistance(): List<TourStep> = listOf(
    TourStep(
        "deadline", tr("Time left", "மீதமுள்ள நேரம்"),
        tr("This is how long you have to finish. When it reaches zero the challenge is missed, your streak resets and your apps stay locked for 24 hours.",
            "முடிக்க உங்களுக்கு இருக்கும் நேரம் இது. பூஜ்யம் ஆனால் சேலஞ்ச் தவறியதாகும், ஸ்ட்ரீக் மீட்டமையும், ஆப்கள் 24 மணி நேரம் பூட்டப்படும்.")
    ),
    TourStep(
        "ring", tr("Distance walked", "நடந்த தூரம்"),
        tr("The ring fills as you walk. Distance is your phone's step count times your step length, so it is an estimate. When it is full you can unlock your phone.",
            "நீங்கள் நடக்க நடக்க வளையம் நிரம்பும். தூரம் = போன் எண்ணும் அடிகள் x உங்கள் அடி நீளம், எனவே இது மதிப்பீடு. முழுதாக நிரம்பியதும் போனை திறக்கலாம்.")
    ),
    TourStep(
        "bottom", tr("Keep the phone with you", "போனை உங்களுடன் வைத்திருங்கள்"),
        tr("Steps are counted even when the screen is off. Walk, then come back here to finish.",
            "திரை அணைந்திருந்தாலும் அடிகள் எண்ணப்படும். நடந்துவிட்டு இங்கே திரும்பி முடியுங்கள்.")
    )
)

@Composable
private fun WalkChallenge(targetMeters: Int, onComplete: () -> Unit, onMissed: () -> Unit) {
    val context = LocalContext.current
    val target = targetMeters.coerceAtLeast(10)

    var permissionTick by remember { mutableStateOf(0) }
    val stepPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) permissionTick++
    }
    LaunchedEffect(Unit) {
        if (!WalkSteps.hasPermission(context)) {
            stepPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    val sensorReady = remember(permissionTick) { WalkSteps.isAvailable(context) }
    val walkSteps = rememberWalkSteps(enabled = sensorReady, refresh = permissionTick)
    val steps = walkSteps.value ?: 0
    val strideM = remember { WalkSteps.strideCm(context) / 100f }
    val walkedM = steps * strideM

    // Safety net: a phone with no step counter (or the permission refused) must never lock
    // someone in, so the distance is replaced by a slow-walk timer (1 m per second).
    val fallbackMs = target * 1000L
    var fallbackElapsedMs by remember {
        mutableStateOf(UserPrefs.getTimedProgressMs(context).coerceIn(0L, fallbackMs))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(sensorReady) {
        if (sensorReady) return@LaunchedEffect
        var last = SystemClock.elapsedRealtime()
        var sinceSave = 0L
        try {
            while (fallbackElapsedMs < fallbackMs) {
                delay(500)
                val now = SystemClock.elapsedRealtime()
                val step = now - last
                last = now
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    fallbackElapsedMs = (fallbackElapsedMs + step).coerceAtMost(fallbackMs)
                }
                sinceSave += step
                if (sinceSave >= 5_000L) {
                    UserPrefs.setTimedProgressMs(context, fallbackElapsedMs)
                    sinceSave = 0L
                }
            }
        } finally {
            UserPrefs.setTimedProgressMs(context, fallbackElapsedMs)
        }
    }

    val isComplete = if (sensorReady) walkedM >= target else fallbackElapsedMs >= fallbackMs
    val rawProgress = if (sensorReady) walkedM / target else fallbackElapsedMs.toFloat() / fallbackMs
    val progress by animateFloatAsState(
        targetValue = rawProgress.coerceIn(0f, 1f),
        animationSpec = tween(250),
        label = "walkProgress"
    )
    val centerText = if (sensorReady) {
        "${walkedM.toInt().coerceAtMost(target)} m"
    } else {
        val rem = ((fallbackMs - fallbackElapsedMs + 999) / 1000).coerceAtLeast(0)
        String.format(Locale.US, "%02d:%02d", rem / 60, rem % 60)
    }

    Scaffold(containerColor = Background) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = ExerciseKinds.display("Walking"),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tr("Walk ${ExerciseKinds.distanceText(target)} \\u00b7 complete to unlock your phone", "${ExerciseKinds.distanceText(target)} நடங்கள் \\u00b7 முடித்தால் போன் திறக்கும்"),
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.tour("deadline")) { DeadlineBar(onMissed) }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (sensorReady) {
                    tr("Steps: $steps \\u00b7 step length ${(strideM * 100).toInt()} cm (estimate)", "அடிகள்: $steps \\u00b7 அடி நீளம் ${(strideM * 100).toInt()} செ.மீ (மதிப்பீடு)")
                } else {
                    tr("No step counter available \\u2014 a timer replaces the distance.", "அடி கவுண்டர் கிடைக்கவில்லை \\u2014 தூரத்துக்கு பதிலாக டைமர் ஓடும்.")
                },
                color = TextMuted,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge
            )

            Spacer(modifier = Modifier.weight(1f))

            ProgressRing(
                progress = progress,
                centerText = centerText,
                subText = if (sensorReady) tr("of ${ExerciseKinds.distanceText(target)}", "/ ${ExerciseKinds.distanceText(target)}")
                else tr("remaining", "மீதம்"),
                isComplete = isComplete,
                modifier = Modifier.tour("ring")
            )

            Spacer(modifier = Modifier.weight(1f))

            if (isComplete) {
                Text(
                    text = tr("Challenge complete!", "சேலஞ்ச் முடிந்தது!"),
                    color = Success,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.tour("bottom").padding(bottom = 16.dp)
                )
                PrimaryPillButton(text = tr("Finish & Unlock Phone", "முடித்து போனை திற"), onClick = onComplete)
            } else {
                Text(
                    text = if (sensorReady) {
                        tr("Walk with your phone \\u2014 steps count even with the screen off.", "போனுடன் நடங்கள் \\u2014 திரை அணைந்திருந்தாலும் அடிகள் எண்ணப்படும்.")
                    } else {
                        tr("Keep this screen open until the timer ends.", "டைமர் முடியும் வரை இந்த திரையை திறந்து வையுங்கள்.")
                    },
                    color = TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.tour("bottom").padding(bottom = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
'''

cs = M + "ui/challenge/ChallengeScreen.kt"
edit(cs, [
    ("targetMeters: Int = 500",
     "    durationMinutes: Int,\n    onComplete: () -> Unit,\n    onMissed: () -> Unit = {}\n) {\n    val timed = ExerciseKinds.isTimed(exerciseName)\n",
     "    durationMinutes: Int,\n    targetMeters: Int = 500,\n    onComplete: () -> Unit,\n    onMissed: () -> Unit = {}\n) {\n    val timed = ExerciseKinds.isTimed(exerciseName)\n    val distance = ExerciseKinds.isDistance(exerciseName)\n"),
    ("if (distance) challengeTourStepsDistance()",
     "challengeTourSteps(timed).filter { tour.bounds.containsKey(it.key) }",
     "(if (distance) challengeTourStepsDistance() else challengeTourSteps(timed)).filter { tour.bounds.containsKey(it.key) }"),
    ("WalkChallenge(targetMeters, onComplete, onMissed)",
     "            if (timed) {\n                TimedChallenge(exerciseName, durationMinutes, onComplete, onMissed)",
     "            if (distance) {\n                WalkChallenge(targetMeters, onComplete, onMissed)\n            } else if (timed) {\n                TimedChallenge(exerciseName, durationMinutes, onComplete, onMissed)"),
])
if os.path.exists(cs):
    t = open(cs, encoding="utf-8").read()
    if "private fun WalkChallenge" not in t:
        open(cs, "a", encoding="utf-8").write(WALK_CODE)
        print("appended WalkChallenge to", cs)
    else:
        print("WalkChallenge already present")

sys.exit(0 if ok else 1)
