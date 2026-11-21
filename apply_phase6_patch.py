#!/usr/bin/env python3
"""Phase 6 patch: Settings gets "Data and backup" (save / restore a JSON file) and "Privacy" cards.
Unzip next to the repo files first (adds BackupManager.kt, BackupCard.kt, BackupManagerTest.kt),
then run from the repo root:  python3 apply_phase6_patch.py   (safe to run twice)
Needs phases 2-5 applied (uses StreakStore.allDays and HistoryStore)."""
import os, sys
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
            continue
        if s.count(old) != 1:
            print("ANCHOR PROBLEM (%d matches) in %s -> %s" % (s.count(old), path, old[:70].replace("\n", " ")))
            ok = False; return
        s = s.replace(old, new)
    if s != orig:
        open(path, "w", encoding="utf-8").write(s); print("patched", path)
    else:
        print("already patched", path)

for f in (M + "data/BackupManager.kt", M + "ui/settings/BackupCard.kt",
          "app/src/test/java/com/muthu/disciplinex/data/BackupManagerTest.kt"):
    if not os.path.exists(f):
        print("MISSING new file:", f); ok = False

edit(M + "ui/settings/SettingsScreen.kt", [
    ("            BackupCard()",
     '            // ---- About ----\n            SettingsCard(tr("About", "பற்றி")',
     '            // ---- Data and backup / Privacy ----\n            BackupCard()\n\n            PrivacyCard()\n\n            // ---- About ----\n            SettingsCard(tr("About", "பற்றி")'),
])
edit("app/build.gradle.kts", [
    ("org.json:json",
     '    testImplementation("junit:junit:4.13.2")\n',
     '    testImplementation("junit:junit:4.13.2")\n    // Android\'s org.json is only a stub on the JVM, so unit tests need the real one.\n    testImplementation("org.json:json:20240303")\n'),
])
sys.exit(0 if ok else 1)
