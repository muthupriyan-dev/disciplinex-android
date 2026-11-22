#!/usr/bin/env python3
"""Phase 11 patch (reliability):
 1. Important state is written with commit() instead of apply(): lock/session state, streak days,
    history, alarms, backup restore, onboarding flag, allowed apps. apply() writes in the background,
    so a process kill right after a miss/complete could lose it; commit() waits until it is on disk.
 2. PoseAnalyzer: a camera frame that arrives after the detector was closed (leaving the screen) is
    dropped instead of crashing the app.
 3. Codemagic runs the unit tests (testDebugUnitTest) after the APK is built. The build does NOT fail
    if a test fails, but the log shows the result.
Run from the repo root: python3 apply_phase11_patch.py   (safe to run twice)"""
import os, sys
M = "app/src/main/java/com/muthu/disciplinex/"
ok = True

def read(p):
    if not os.path.exists(p):
        print("MISSING FILE:", p); return None
    return open(p, encoding="utf-8").read()

def write(p, s, orig):
    if s != orig:
        open(p, "w", encoding="utf-8").write(s); print("patched", p)
    else:
        print("already patched", p)

# 1. commit() for important state
for f in ("data/ChallengeSession.kt", "data/StreakStore.kt", "data/HistoryStore.kt",
          "data/AlarmStore.kt", "data/BackupManager.kt"):
    s = read(M + f)
    if s is None: ok = False; continue
    write(M + f, s.replace(".apply()", ".commit()"), s)

p = M + "data/UserPrefs.kt"
s = read(p)
if s is None:
    ok = False
else:
    t = s
    for old in ("KEY_ONBOARDED, true).apply()", "KEY_ALLOWED_APPS, HashSet(apps)).apply()"):
        if old in t:
            t = t.replace(old, old.replace(".apply()", ".commit()"))
        elif old.replace(".apply()", ".commit()") not in t:
            print("ANCHOR PROBLEM in", p, "->", old); ok = False
    write(p, t, s)

# 2. PoseAnalyzer
p = M + "pose/PoseAnalyzer.kt"
s = read(p)
OLD = '''    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        detector.process(inputImage)
            .addOnSuccessListener { pose -> onPose(pose) }
            .addOnFailureListener { onPose(null) }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun close() {
        detector.close()
    }'''
NEW = '''    @Volatile
    private var closed = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (closed || mediaImage == null) {
            imageProxy.close()
            return
        }
        try {
            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            detector.process(inputImage)
                .addOnSuccessListener { pose -> if (!closed) onPose(pose) }
                .addOnFailureListener { if (!closed) onPose(null) }
                .addOnCompleteListener { imageProxy.close() }
        } catch (e: Exception) {
            // The detector was closed between the check above and process(): drop this frame.
            imageProxy.close()
        }
    }

    fun close() {
        closed = true
        detector.close()
    }'''
if s is None:
    ok = False
elif "private var closed" in s:
    print("already patched", p)
elif s.count(OLD) == 1:
    write(p, s.replace(OLD, NEW), s)
else:
    print("ANCHOR PROBLEM in", p, "(your PoseAnalyzer differs; send it to me)"); ok = False

# 3. codemagic.yaml
p = "codemagic.yaml"
s = read(p)
STEP_OLD = "      - name: Build debug APK\n        script: |\n          ./gradlew assembleDebug\n"
STEP_NEW = STEP_OLD + "      - name: Run unit tests\n        script: |\n          ./gradlew testDebugUnitTest\n        ignore_failure: true\n"
if s is None:
    ok = False
elif "testDebugUnitTest" in s:
    print("already patched", p)
elif s.count(STEP_OLD) == 1:
    write(p, s.replace(STEP_OLD, STEP_NEW), s)
else:
    print("ANCHOR PROBLEM in", p, "(add the 'Run unit tests' step by hand, see below)"); ok = False
sys.exit(0 if ok else 1)
