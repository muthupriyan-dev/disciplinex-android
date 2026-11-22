#!/usr/bin/env python3
"""Phase 10 patch: fixes push-ups being rejected with 'skipped: knees bent'.
From the FRONT your legs point at the camera, so their angle on screen is meaningless; the knee and
torso-tilt checks now only run when the camera sees you from the SIDE. The debug line also shows the
numbers of every finished rep (sh / hand / knee / sep) so thresholds can be tuned from real data.
Run from the repo root: python3 apply_phase10_patch.py   (safe to run twice)"""
import os, shutil, sys
HERE = os.path.dirname(os.path.abspath(__file__))
target = "app/src/main/java/com/muthu/disciplinex/pose/RepCounter.kt"
new = os.path.join(HERE, "RepCounter.kt")
if not (os.path.exists(target) and os.path.exists(new)):
    print("MISSING", target, "or", new); sys.exit(1)
cur = open(target, encoding="utf-8").read()
if "lastMetrics" in cur:
    print("already patched", target); sys.exit(0)
if "PU_MIN_SHOULDER_TRAVEL" not in cur:
    print("!! RepCounter.kt is not the phase 9 version. Not overwritten. Apply phase 9 first."); sys.exit(1)
shutil.copyfile(new, target)
print("replaced", target)
