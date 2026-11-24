#!/usr/bin/env python3
"""Phase 12 patch - Tamil localization (round 2, as reviewed).

Run from the repo root:  python3 apply_phase12_patch.py        (safe to run twice)
Put this script and localization_changes.json in the repo root first.

What it does
  A. Replaces the Tamil text of every tr("English", "Tamil") call that is in the approved list
     (new glossary: சவால் / தொடர் சாதனை / காப்பு நகல் / அணுகல் வசதி ...). A call is only changed when its
     CURRENT Tamil is exactly the Tamil from the inventory, so anything you edited by hand is left alone
     and listed at the end.
  B. Achievements titles/units, "OK" button, "m"/"km" units, notification channel names -> Tamil.
  C. New res/values-ta/strings.xml (Tamil text for the Accessibility settings screen).
  D. Developer readout under the camera only in debuggable builds (Codemagic debug APKs keep it).
  E. Walking guide texts (rows 94 and 252) say "reps, minutes or distance".
  F. codemagic.yaml prints the real merged-manifest permissions in the build log (to check INTERNET).
No logic is changed. AM/PM stays English."""
import json, os, re, sys

ROOT = "app/src/main/java/com/muthu/disciplinex"
HERE = os.path.dirname(os.path.abspath(__file__))
ok = True

# ------------------------------------------------------------------ Kotlin string scanner
def tr_pairs(src):
    """Return [(lit1_start, lit1_end, lit2_start, lit2_end)] for every tr("..", "..") call made of two plain literals."""
    n = len(src); i = 0
    paren = []              # stack: is this '(' a call to tr?
    lits = []               # (start, end, under_tr, nested)

    def ident_before(pos):
        j = pos - 1
        while j >= 0 and src[j] in ' \t\n': j -= 1
        k = j
        while k >= 0 and (src[k].isalnum() or src[k] == '_'): k -= 1
        return src[k + 1:j + 1]

    def parse_string(i, raw3):
        nested = []
        j = i + (3 if raw3 else 1)
        while j < n:
            c = src[j]
            if raw3:
                if src.startswith('"""', j): return j + 3, nested
            else:
                if c == '\\': j += 2; continue
                if c == '"': return j + 1, nested
                if c == '\n': return j, nested
            if c == '$' and j + 1 < n and src[j + 1] == '{':
                depth = 1; k = j + 2
                while k < n and depth:
                    if src[k] == '"':
                        e, nn = parse_string(k, src.startswith('"""', k))
                        nested.append((k, e)); k = e; continue
                    if src[k] == '{': depth += 1
                    elif src[k] == '}': depth -= 1
                    k += 1
                j = k; continue
            j += 1
        return j, nested

    while i < n:
        c = src[i]
        if c == '/' and i + 1 < n and src[i + 1] == '/':
            while i < n and src[i] != '\n': i += 1
            continue
        if c == '/' and i + 1 < n and src[i + 1] == '*':
            e = src.find('*/', i + 2); i = n if e < 0 else e + 2; continue
        if c == "'":
            j = i + 1
            j += 2 if (j < n and src[j] == '\\') else 1
            while j < n and src[j] != "'": j += 1
            i = j + 1; continue
        if c == '"':
            raw3 = src.startswith('"""', i)
            e, nested = parse_string(i, raw3)
            lits.append((i, e, any(paren), raw3))
            i = e; continue
        if c == '(': paren.append(ident_before(i) == 'tr')
        elif c == ')':
            if paren: paren.pop()
        i += 1
    # keep only top-level literals that sit directly inside a tr( ... ) call, pair them up
    pairs = []
    direct = [(s, e) for (s, e, under, raw3) in lits if under and not raw3]
    k = 0
    while k + 1 < len(direct):
        (s1, e1), (s2, e2) = direct[k], direct[k + 1]
        between = src[e1:s2]
        if re.fullmatch(r'\s*,\s*', between) and re.search(r'\btr\(\s*$', src[max(0, s1 - 300):s1]):
            pairs.append((s1, e1, s2, e2)); k += 2
        else:
            k += 1
    return pairs

# ------------------------------------------------------------------ A. tr() Tamil replacements
changes = json.load(open(os.path.join(HERE, "localization_changes.json"), encoding="utf-8"))
def src_form(t):
    # The Excel inventory shows a "\n" escape of the Kotlin source as a real line break; turn it back.
    return None if t is None else t.replace("\r", "").replace("\n", "\\n")
for c in changes:
    for k in ("en", "old_ta", "new_ta", "new_en"): c[k] = src_form(c[k])
by_en = {}
for c in changes: by_en.setdefault(c["en"], []).append(c)

replaced = already = 0
differs = []   # English matched but the Tamil in the repo is not the one I expected
files_touched = []
for root, _, fs in os.walk(ROOT):
    for f in sorted(fs):
        if not f.endswith(".kt"): continue
        p = os.path.join(root, f)
        src = open(p, encoding="utf-8").read()
        edits = []
        for (s1, e1, s2, e2) in tr_pairs(src):
            en, ta = src[s1 + 1:e1 - 1], src[s2 + 1:e2 - 1]
            for c in by_en.get(en, []):
                if ta == c["old_ta"]:
                    if c["new_en"]: edits.append((s1 + 1, e1 - 1, c["new_en"]))
                    edits.append((s2 + 1, e2 - 1, c["new_ta"])); replaced += 1; break
                if ta == c["new_ta"]:
                    already += 1; break
            else:
                if en in by_en:
                    differs.append((f, en[:50], ta[:50]))
        if edits:
            for a, b, new in sorted(edits, reverse=True):
                src = src[:a] + new + src[b:]
            open(p, "w", encoding="utf-8").write(src)
            files_touched.append(f)
print("A. tr() calls updated: %d   already updated: %d   files: %s" % (replaced, already, ", ".join(files_touched) or "-"))
if differs:
    print("   NOT changed (your Tamil differs from the inventory) - %d:" % len(differs))
    for d in differs: print("     ", d)

# ------------------------------------------------------------------ helpers for anchored edits
def edit(path, pairs):
    global ok
    if not os.path.exists(path):
        print("MISSING FILE:", path); ok = False; return
    s = open(path, encoding="utf-8").read(); orig = s
    for marker, old, new in pairs:
        if marker in s: continue
        if s.count(old) != 1:
            print("ANCHOR PROBLEM (%d matches) in %s -> %s" % (s.count(old), path, old[:70].replace("\n", " "))); ok = False; return
        s = s.replace(old, new)
    if s != orig:
        open(path, "w", encoding="utf-8").write(s); print("patched", path)
    else:
        print("already patched", path)

# ------------------------------------------------------------------ B. Achievements, OK, units, channels
edit(ROOT + "/domain/Achievements.kt", [
    ('"முதல் சவால்"', '"முதல் சேலஞ்ச்", 1, totalDone, "challenges", "சேலஞ்ச்")', '"முதல் சவால்", 1, totalDone, "challenges", "சவால்கள்")'),
    ('"3 நாள் தொடர் சாதனை"', '"3 நாள் ஸ்ட்ரீக்"', '"3 நாள் தொடர் சாதனை"'),
    ('"7 நாள் தொடர் சாதனை"', '"7 நாள் ஸ்ட்ரீக்"', '"7 நாள் தொடர் சாதனை"'),
    ('"30 நாள் தொடர் சாதனை"', '"30 நாள் ஸ்ட்ரீக்"', '"30 நாள் தொடர் சாதனை"'),
    ('"100 சவால்கள்", 100, totalDone, "challenges", "சவால்கள்")', '"100 சேலஞ்ச்கள்", 100, totalDone, "challenges", "சேலஞ்ச்")', '"100 சவால்கள்", 100, totalDone, "challenges", "சவால்கள்")'),
])
edit(ROOT + "/alarm/AlarmRingActivity.kt", [
    ('Text(tr("OK", "சரி")', 'Text("OK", fontWeight = FontWeight.Bold)', 'Text(tr("OK", "சரி"), fontWeight = FontWeight.Bold)'),
])
edit(ROOT + "/data/ExerciseKinds.kt", [
    ("கி.மீ",
     '        if (meters >= 1000) String.format(java.util.Locale.US, "%.1f km", meters / 1000.0) else "$meters m"',
     '''        if (meters >= 1000) {
            val km = String.format(java.util.Locale.US, "%.1f", meters / 1000.0)
            tr("$km km", "$km கி.மீ")
        } else {
            tr("$meters m", "$meters மீ")
        }'''),
])
edit(ROOT + "/ui/challenge/ChallengeScreen.kt", [
    ("} மீ\"",
     '"${walkedM.toInt().coerceAtMost(target)} m"',
     'tr("${walkedM.toInt().coerceAtMost(target)} m", "${walkedM.toInt().coerceAtMost(target)} மீ")'),
])
edit(ROOT + "/alarm/AlarmService.kt", [
    ('tr("Alarm", "அலாரம்")', 'CHANNEL_ID, "Alarm", NotificationManager.IMPORTANCE_HIGH', 'CHANNEL_ID, tr("Alarm", "அலாரம்"), NotificationManager.IMPORTANCE_HIGH'),
    ('எழுப்பும் அலாரம்', 'description = "DisciplineX wake-up alarm"', 'description = tr("DisciplineX wake-up alarm", "DisciplineX எழுப்பும் அலாரம்")'),
])
edit(ROOT + "/alarm/AlarmFallback.kt", [
    ('அலாரம் (மாற்று)', 'CHANNEL_ID, "Alarm (backup)", NotificationManager.IMPORTANCE_HIGH', 'CHANNEL_ID, tr("Alarm (backup)", "அலாரம் (மாற்று)"), NotificationManager.IMPORTANCE_HIGH'),
    ('மட்டுமே பயன்படும்', 'description = "Used only if the main alarm service can\'t start"', 'description = tr("Used only if the main alarm service can\'t start", "முதன்மை அலாரம் சேவை தொடங்க முடியாதபோது மட்டுமே பயன்படும்")'),
])

# ------------------------------------------------------------------ D. debug readout only in debuggable builds
edit(ROOT + "/ui/challenge/ChallengeScreen.kt", [
    ("private fun isDebugBuild",
     '/** Small live angle readout under the camera. Set to false to hide it. */\nprivate const val SHOW_REP_DEBUG = true\n',
     '''/**
 * Small live readout under the camera (angle, "skipped: ..."). Shown only in DEBUGGABLE builds
 * (the Codemagic debug APK); signed release builds hide it. Nothing else about debugging changes.
 */
private fun isDebugBuild(context: android.content.Context): Boolean =
    (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
'''),
    ("isDebugBuild(LocalContext.current)",
     "if (SHOW_REP_DEBUG && debugText.isNotBlank()) {",
     "if (isDebugBuild(LocalContext.current) && debugText.isNotBlank()) {"),
])

# ------------------------------------------------------------------ C. Tamil resource for the Accessibility settings screen
res_dir = "app/src/main/res/values-ta"
res_file = os.path.join(res_dir, "strings.xml")
TA_XML = '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="accessibility_service_description">DisciplineX App Lock திரையில் எந்த ஆப் திறந்துள்ளது என்பதை மட்டுமே பார்க்கிறது. அதனால், சவாலை முடிக்கும் வரை உங்களை மீண்டும் சவாலுக்கு அனுப்ப முடியும் (திரையை இரண்டாக பிரித்து அல்லது சிறிய மிதக்கும் திரைகளில் திறக்கும் ஆப்கள் உட்பட). போன், கேமரா, நீங்கள் அனுமதித்த ஆப்கள் தொடர்ந்து கிடைக்கும். உங்கள் செய்திகள், கடவுச்சொற்கள், நீங்கள் தட்டச்சு செய்யும் எதையும் இது ஒருபோதும் படிக்காது.</string>
</resources>
'''
if os.path.exists(res_file):
    print("already exists", res_file)
elif os.path.isdir("app/src/main/res"):
    os.makedirs(res_dir, exist_ok=True)
    open(res_file, "w", encoding="utf-8").write(TA_XML); print("created", res_file)
else:
    print("MISSING app/src/main/res"); ok = False

# ------------------------------------------------------------------ F. codemagic: show real merged-manifest permissions
cm = "codemagic.yaml"
STEP = '''      - name: Show merged manifest permissions
        script: |
          echo "=== merged manifest permissions ==="
          find app/build -path "*merged_manifest*" -name AndroidManifest.xml -exec grep -H "uses-permission" {} \\;
          APK=$(find app/build/outputs/apk/debug -name "*.apk" | head -1)
          AAPT=$(find "$ANDROID_SDK_ROOT/build-tools" "$ANDROID_HOME/build-tools" -name aapt2 2>/dev/null | sort | tail -1)
          if [ -n "$AAPT" ] && [ -n "$APK" ]; then "$AAPT" dump permissions "$APK"; fi
        ignore_failure: true
'''
if os.path.exists(cm):
    s = open(cm, encoding="utf-8").read()
    if "Show merged manifest permissions" in s:
        print("already patched", cm)
    else:
        anchor = "      - name: Run unit tests\n"
        if s.count(anchor) == 1:
            open(cm, "w", encoding="utf-8").write(s.replace(anchor, STEP + anchor)); print("patched", cm)
        else:
            alt = "          ./gradlew assembleDebug\n"
            if s.count(alt) == 1:
                open(cm, "w", encoding="utf-8").write(s.replace(alt, alt + STEP)); print("patched", cm)
            else:
                print("ANCHOR PROBLEM in codemagic.yaml (add the 'Show merged manifest permissions' step by hand)"); ok = False
else:
    print("codemagic.yaml not found (skipped)")
sys.exit(0 if ok else 1)
