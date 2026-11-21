#!/usr/bin/env python3
"""Phase 7 patch (fixes from your phone test):
 1. Guide (i) popups: Home gets a Statistics step, Settings gets Data-and-backup + Privacy steps,
    the Statistics screen gets its own (i) button + guide. Weekday labels in the chart no longer cut off.
 2. Walking: no step-length question any more; live count no longer freezes then jumps.
 3. Push-ups: sitting down / standing up (or legs not in frame) is no longer counted.
Run from the repo root after phases 2-6:  python3 apply_phase7_patch.py   (safe to run twice)
Needs RepCounter.kt and StatsScreen.kt (shipped next to this script)."""
import hashlib, os, shutil, sys
M = "app/src/main/java/com/muthu/disciplinex/"
HERE = os.path.dirname(os.path.abspath(__file__))
ok = True

def sha(p): return hashlib.sha256(open(p, "rb").read()).hexdigest()

def replace_file(target, shipped, old_sha):
    global ok
    src = os.path.join(HERE, shipped)
    if not (os.path.exists(target) and os.path.exists(src)):
        print("MISSING", target, "or", src); ok = False; return
    if sha(target) == sha(src):
        print("already patched", target)
    elif sha(target) == old_sha:
        shutil.copyfile(src, target); print("replaced", target)
    else:
        print("!! %s differs from what I expect (you edited it?). Not overwritten. Send it to me." % target); ok = False

def edit(path, pairs):
    """pairs: (marker, old, new[, many]); marker already present -> skipped."""
    global ok
    if not os.path.exists(path):
        print("MISSING FILE:", path); ok = False; return
    s = open(path, encoding="utf-8").read(); orig = s
    for pair in pairs:
        marker, old, new = pair[0], pair[1], pair[2]
        many = len(pair) > 3 and pair[3]
        if marker in s: continue
        n = s.count(old)
        if n == 0 or (n > 1 and not many):
            print("ANCHOR PROBLEM (%d matches) in %s -> %s" % (n, path, old[:70].replace("\n", " "))); ok = False; return
        s = s.replace(old, new)
    if s != orig:
        open(path, "w", encoding="utf-8").write(s); print("patched", path)
    else:
        print("already patched", path)

# ------------------------------------------------ push-ups + statistics screen
replace_file(M + "pose/RepCounter.kt", "RepCounter.kt", "19e532009e5937b16d3cd3377822344b83a7b1cae2f84b2850cdebe6b8d5506d")
replace_file(M + "ui/stats/StatsScreen.kt", "StatsScreen.kt", "9e53ddbeebce1a6e9798c011362de6c9a989ff9f48aee6b934382391d154d2c8")

# ------------------------------------------------------------ walking: live count
OLD_LISTENER = '''        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.isNotEmpty()) {
                    steps.value = WalkSteps.stepsSince(context, event.values[0].toLong())
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }'''
NEW_LISTENER = '''        // The step COUNTER reports in delayed bursts (the number freezes, then jumps).
        // The step DETECTOR fires once per step with almost no delay and fills those gaps;
        // every counter reading is the authoritative total and absorbs the detector tally.
        val detector = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        var counterSteps = 0
        var detected = 0
        fun publish() {
            val computed = counterSteps + detected
            if (computed > (steps.value ?: -1)) steps.value = computed
        }
        val counterListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.isNotEmpty()) {
                    val newTotal = WalkSteps.stepsSince(context, event.values[0].toLong())
                    val delta = (newTotal - counterSteps).coerceAtLeast(0)
                    counterSteps = newTotal
                    detected = (detected - delta).coerceAtLeast(0)
                    publish()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        val detectorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                detected++
                publish()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        // maxReportLatency 0 = deliver events immediately instead of batching them.
        sm.registerListener(counterListener, sensor, SensorManager.SENSOR_DELAY_UI, 0)
        if (detector != null) {
            sm.registerListener(detectorListener, detector, SensorManager.SENSOR_DELAY_UI, 0)
        }
        onDispose {
            sm.unregisterListener(counterListener)
            sm.unregisterListener(detectorListener)
        }'''
edit(M + "data/WalkSteps.kt", [("val detector = sm.getDefaultSensor", OLD_LISTENER, NEW_LISTENER)])

# ----------------------------------------- walking: no step-length question
p = M + "ui/alarms/AlarmEditScreen.kt"
if os.path.exists(p):
    s = open(p, encoding="utf-8").read(); orig = s
    a = s.find('                    Spacer(modifier = Modifier.height(14.dp))\n                    Text(tr("Your step length"')
    endmark = 'onValueChange = { strideCm = it })'
    b = s.find(endmark)
    if a != -1 and b != -1 and b > a:
        s = s[:a] + '''                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        tr("Distance is estimated from your phone's step counter.", "தூரம் போனின் அடி கவுண்டரிலிருந்து மதிப்பிடப்படும்."),
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )''' + s[b + len(endmark):]
    elif "Your step length" in s:
        print("ANCHOR PROBLEM: step length block in", p); ok = False
    for old in ('    var strideCm by remember { mutableStateOf(com.muthu.disciplinex.data.WalkSteps.strideCm(context)) }\n',
                '        if (alarm.isDistance) com.muthu.disciplinex.data.WalkSteps.setStrideCm(context, strideCm)\n'):
        s = s.replace(old, "")
    s = s.replace("(your phone's step counter x your step length)", "(estimated from your phone's step counter)")
    s = s.replace("(போனின் அடி கவுண்டர் x உங்கள் அடி நீளம்)", "(போனின் அடி கவுண்டரிலிருந்து மதிப்பீடு)")
    if s != orig:
        open(p, "w", encoding="utf-8").write(s); print("patched", p)
    else:
        print("already patched", p)
else:
    print("MISSING FILE:", p); ok = False

edit(M + "ui/challenge/ChallengeScreen.kt", [
    ("distance is an estimate",
     r'tr("Steps: $steps \u00b7 step length ${(strideM * 100).toInt()} cm (estimate)", "அடிகள்: $steps \u00b7 அடி நீளம் ${(strideM * 100).toInt()} செ.மீ (மதிப்பீடு)")',
     r'tr("Steps: $steps \u00b7 distance is an estimate", "அடிகள்: $steps \u00b7 தூரம் மதிப்பீடு")'),
    ("Distance is estimated from your phone's step count.",
     "Distance is your phone's step count times your step length, so it is an estimate.",
     "Distance is estimated from your phone's step count."),
    ("தூரம் போன் எண்ணும் அடிகளிலிருந்து மதிப்பிடப்படும்.",
     "தூரம் = போன் எண்ணும் அடிகள் x உங்கள் அடி நீளம், எனவே இது மதிப்பீடு.",
     "தூரம் போன் எண்ணும் அடிகளிலிருந்து மதிப்பிடப்படும்."),
])

# ---------------------------------------------------------------- guide: Home
edit(M + "ui/home/HomeScreen.kt", [
    ('modifier = Modifier.tourTarget(tour, "stats")',
     "                        onClick = onOpenStats\n                    ) { Icon(Icons.Default.BarChart",
     '                        onClick = onOpenStats,\n                        modifier = Modifier.tourTarget(tour, "stats")\n                    ) { Icon(Icons.Default.BarChart'),
    ('"stats", tr("Statistics"',
     '    TourStep(\n        "settings", tr("Settings", "அமைப்புகள்"),',
     '''    TourStep(
        "stats", tr("Statistics", "புள்ளிவிவரங்கள்"),
        tr("See your totals, the last 7 days, results for each exercise and your achievements.", "மொத்த எண்கள், கடந்த 7 நாட்கள், ஒவ்வொரு பயிற்சியின் முடிவு, சாதனைகளை பாருங்கள்.")
    ),
    TourStep(
        "settings", tr("Settings", "அமைப்புகள்"),'''),
    ("save or restore a backup",
     "Choose which apps stay open while your phone is locked. Phone and Camera are always open.",
     "Choose which apps stay open while locked, change theme and language, save or restore a backup, and read the privacy notes."),
    ("காப்பு பிரதி",
     "போன் பூட்டியிருக்கும்போது எந்த ஆப்கள் திறந்திருக்க வேண்டும் என்பதை தேர்ந்தெடுங்கள். போன், கேமரா எப்போதும் திறந்திருக்கும்.",
     "பூட்டியிருக்கும்போது எந்த ஆப்கள் திறந்திருக்க வேண்டும், தீம், மொழி, காப்பு பிரதி சேமிப்பு / மீட்பு, தனியுரிமை குறிப்புகள் அனைத்தும் இங்கே."),
])

# ------------------------------------------------------------- guide: Settings
edit(M + "ui/settings/BackupCard.kt", [
    ("fun BackupCard(modifier",
     "private fun InfoCard(title: String, content: @Composable () -> Unit) {\n    Column(\n        modifier = Modifier\n            .fillMaxWidth()",
     "private fun InfoCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {\n    Column(\n        modifier = modifier\n            .fillMaxWidth()"),
    ("fun BackupCard(modifier", "fun BackupCard() {", "fun BackupCard(modifier: Modifier = Modifier) {"),
    ('"தரவு மற்றும் பேக்கப்"), modifier)',
     'InfoCard(tr("Data and backup", "தரவு மற்றும் பேக்கப்")) {',
     'InfoCard(tr("Data and backup", "தரவு மற்றும் பேக்கப்"), modifier) {'),
    ("fun PrivacyCard(modifier", "fun PrivacyCard() {", "fun PrivacyCard(modifier: Modifier = Modifier) {"),
    ('"தனியுரிமை"), modifier)',
     'InfoCard(tr("Privacy", "தனியுரிமை")) {',
     'InfoCard(tr("Privacy", "தனியுரிமை"), modifier) {'),
])
edit(M + "ui/settings/SettingsScreen.kt", [
    ('BackupCard(Modifier.tourTarget(tour, "backup"))',
     "            BackupCard()\n\n            PrivacyCard()",
     '            BackupCard(Modifier.tourTarget(tour, "backup"))\n\n            PrivacyCard(Modifier.tourTarget(tour, "privacy"))'),
    ('"backup", tr("Data and backup"',
     '    TourStep(\n        "about", tr("About", "பற்றி"),',
     '''    TourStep(
        "backup", tr("Data and backup", "தரவு மற்றும் பேக்கப்"),
        tr("Save your alarms, streak, history and allowed apps to a file. Restore works only on a fresh start (no recorded days yet), so a backup can never undo a missed day.",
            "அலாரங்கள், ஸ்ட்ரீக், வரலாறு, அனுமதிக்கப்பட்ட ஆப்களை கோப்பாக சேமிக்கலாம். எந்த நாளும் பதிவாகும் முன் மட்டுமே மீட்டெடுக்க முடியும், எனவே தவறிய நாளை அழிக்க முடியாது.")
    ),
    TourStep(
        "privacy", tr("Privacy", "தனியுரிமை"),
        tr("What the camera, the accessibility service and your data are used for. Nothing leaves your phone.",
            "கேமரா, அக்சசிபிலிட்டி சேவை, உங்கள் தரவு எதற்கு பயன்படுகிறது என்பது. எதுவும் போனை விட்டு வெளியே போகாது.")
    ),
    TourStep(
        "about", tr("About", "பற்றி"),'''),
])
sys.exit(0 if ok else 1)
