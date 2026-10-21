# DisciplineX — Native Android

A discipline app: **an alarm rings, and your phone stays locked down until you finish a challenge** (push-ups / squats counted by the camera, jumping jacks, or a timed Walking / Meditation / Reading session).

## How it works (v0.5)
1. **Alarm list** — like a normal clock app. Add as many alarms as you want with the **+** button. Each alarm has its own time, repeat days (or "Once"), label, exercise and target (reps or minutes). Morning, evening, any time.
2. **Alarm rings** — the lock turns ON and you get **10 minutes to start** the challenge.
3. **Start in time** — finish the challenge and the phone unlocks; the day counts for your 🔥 streak.
4. **Don't start in time** — the challenge is *missed*: the streak resets and the phone stays locked until the next alarm (`ChallengeSession.MISSED_KEEPS_LOCK`, set it to `false` to unlock right after a miss).
5. **Safety net** — the lock always ends at the next alarm, or after 24 hours, whichever comes first.

## App lock
- Accessibility service blocks every app except Phone, Camera, your allowed apps (default: WhatsApp), keyboards, the home screen and system screens.
- It also checks split-screen / floating "mini" windows, so a blocked app can't be used next to the challenge.
- Timed challenges only count time while the challenge window itself has focus (pauses when another window is on top).
- System Settings is blocked while locked, so the lock can't be switched off from the Accessibility page.
- It only reads *which app owns a window* (package name) — never text, messages or typing. (Android requires the "retrieve window content" capability for window-level checks; the code never reads node content.)
- Alarms, allowed apps and settings can't be edited while locked.

## Streak
A day is a success only if every challenge that day was finished (at least one done, none missed). Home shows the current streak, your best, and the last 7 days.

## First-run guide
A short "what does this button do?" tour (Next / Skip) runs on first launch. Replay it from the ⓘ button on Home or Settings.

## Build (phone-only, via Codemagic)
1. Push this repo to GitHub.
2. Codemagic reads `codemagic.yaml`, generates the Gradle wrapper and builds a debug APK.
3. Download the APK from Codemagic and install it over the old one.

**Signing:** `app/debug.keystore` is a fixed key committed on purpose so every build installs *over* the previous one. Keep the repo private.

## Project layout
- `alarm/` — scheduling (one system alarm per list entry), ring screen, foreground service, boot re-arming, backup notification
- `lock/` — the accessibility service that enforces the lock
- `pose/` — camera preview, ML Kit analyzer, rep counters
- `data/` — `AlarmEntry` / `AlarmStore` (the alarm list), `ChallengeSession` (lock + 10-minute window), `StreakStore`, `UserPrefs`
- `ui/` — Compose screens (home, alarm editor, challenge, settings, guide tour)

## Known limits
- The app can still be uninstalled from the launcher (long-press → Uninstall). Blocking that needs a device-admin feature.
- Jumping Jacks still uses manual "+1 Rep" until motion-sensor tracking is added; Walking is timer-only.

## Next
Motion-sensor tracking, Firebase (auth, leaderboard).
