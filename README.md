# DisciplineX — Native Android

A wake-up accountability app: an alarm rings, and **your phone stays locked down until you finish a challenge** (push-ups / squats counted by the camera, or a timed Reading / Meditation / Walking session).

## What it does
- **Alarm** at your wake time (`AlarmManager.setAlarmClock`, works in Doze, no special permission), with snooze (2×10 min) and "skip today" (2 per week).
- **Camera rep counting** (CameraX + ML Kit pose detection) for Push-ups and Squats, with anti-cheat (consistent body side, minimum rep time, plank/hip-drop checks).
- **Timed challenges** (Reading, Meditation, Walking): the timer only runs while the challenge screen is open and can't be skipped by tapping.
- **App lock** (Accessibility Service): while a challenge is pending, every app is blocked except Phone, Camera, your allowed apps (default: WhatsApp), keyboards, the home screen and system screens. It only watches *which app opened* — it never reads screen content. Fail-safe: the lock expires by itself after 3 hours.
- **Settings**: change wake time, exercise, target and allowed apps without reinstalling (read-only while locked).

## Build (phone-only, via Codemagic)
1. Push this repo to GitHub.
2. Codemagic reads `codemagic.yaml`, generates the Gradle wrapper and builds a debug APK.
3. Download the APK from Codemagic and install it.

**Signing:** `app/debug.keystore` is a fixed key committed on purpose so every build is signed the same way and installs *over* the previous version. The first time after switching to this key you must uninstall the old (differently signed) app once. Keep the repo private if you don't want others to be able to sign updates for this app.

## Project layout
- `alarm/` — scheduling, ring screen, foreground service, boot/time-change re-arming, backup notification
- `lock/` — the accessibility service that enforces the lock
- `pose/` — camera preview, ML Kit analyzer, rep counters
- `data/` — `UserPrefs` (all saved settings + lock state), `ExerciseKinds`
- `ui/` — Compose screens (onboarding, home, challenge, settings)

## Known limits
- Settings stays reachable while locked (it is how you switch the lock off in an emergency), so the lock can always be bypassed by disabling the accessibility service or uninstalling the app.
- Jumping Jacks still uses manual "+1 Rep" until motion-sensor tracking is added.

## Next
Streaks / history, motion-sensor tracking, Firebase (auth, leaderboard).
