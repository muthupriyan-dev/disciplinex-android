#!/usr/bin/env python3
"""Phase 9 patch: replaces YOUR simplified RepCounter.kt (the one with
'class PushUpRepCounter : RepCounter by AngleRepCounter') with a version that keeps the
same angle thresholds (push-up down<100 up>145, squat down<105 up>150) and the same public API,
but validates every finished rep against the movement of the whole body:
  Push-up : shoulder really moved, hand stayed planted, hip/knee/ankle visible, knees stayed straight,
            (side view) body level.
  Squat   : hip really dropped, body not lying.
  Jumping jack : BOTH hands must go up / down.
A skipped rep shows 'skipped: <reason>' in the debug line under the camera.
Run from the repo root: python3 apply_phase9_patch.py   (safe to run twice)"""
import os, shutil, sys
HERE = os.path.dirname(os.path.abspath(__file__))
target = "app/src/main/java/com/muthu/disciplinex/pose/RepCounter.kt"
new = os.path.join(HERE, "RepCounter.kt")
if not (os.path.exists(target) and os.path.exists(new)):
    print("MISSING", target, "or", new); sys.exit(1)
cur = open(target, encoding="utf-8").read()
if "PU_MIN_SHOULDER_TRAVEL" in cur:
    print("already patched", target); sys.exit(0)
if "class PushUpRepCounter : RepCounter by AngleRepCounter" not in cur:
    print("!! RepCounter.kt is not the version I expect. Not overwritten.")
    print("!! Send me your current RepCounter.kt."); sys.exit(1)
shutil.copyfile(target, target + ".bak")
shutil.copyfile(new, target)
print("replaced", target, "(your old file is saved as RepCounter.kt.bak - delete it before committing)")
