# DisciplineX — Native Android

A discipline app: **an alarm rings, and your phone stays locked down until you finish a challenge.** Push-ups, squats and jumping jacks are counted by the camera; Walking (with a step check), Meditation and Reading run on a timer you can't skip.

## How it works (v1.2)
1. **Alarm list.** Add as many alarms as you want with the **+** button. Each has a time (drum-style wheel), repeat (every day, specific weekdays, once, or **Custom** dates), label, exercise, target and a **time limit** ("finish within N minutes").
2. **Alarm rings.** The lock turns ON and the time limit starts. Dismiss only stops the sound.
3. **Finish in time.** The phone unlocks and the day counts for your streak.
4. **Miss the time limit.** The day is missed: the streak resets and the phone stays locked for **24 hours**. You can't carry on after the time is up.
5. **Away rule.** During a challenge you may leave the app for up to 5 minutes (an emergency). Come back later and the progress starts again from zero.
6. **Emergency unlock.** After a *missed* challenge you can unlock early from Home, but only after a 10-minute wait. The day stays missed and the streak stays 0.
7. **Alarm lock-in.** An alarm that rings within 2 hours can't be switched off, edited or deleted.

## Settings
Theme (system / light / dark), language (English / தமிழ்), alarm ringtone and vibration, test alarm sound, allowed apps (with icons), permission status with fix buttons, streak history calendar, about. Every screen has an **(i)** button that explains what is on it.

## App lock
- An accessibility service blocks every app except Phone, Camera, your allowed apps, keyboards, the home screen and system screens.
- It also checks split-screen and floating windows.
- System Settings is blocked while locked, so the lock can't be switched off from the Accessibility page.
- It only reads *which app owns a window* (package name), never text, messages or typing.
- Alarms and the allowed-apps list can't be edited while locked.

## Installing on a friend's phone
1. Install the APK (choose "Install anyway" if Play Protect warns).
2. **Android 13+:** the Accessibility switch is greyed out for apps installed from an APK. Open Settings → Apps → DisciplineX → ⋮ → **Allow restricted settings**.
3. Open the app and allow every permission it asks for.
4. Some phones (Xiaomi, Oppo, Vivo, Realme, Samsung) stop background apps aggressively. Allow auto-start / "no restrictions" battery for DisciplineX.

## Known limits (Android does not allow more)
- The app can still be uninstalled from the launcher (long-press → Uninstall). Blocking that needs a device-admin feature.
- Safe mode switches off third-party accessibility services.
- If the phone is switched off at alarm time, the alarm doesn't ring and nothing is recorded.
- Camera counting can be fooled with a lot of effort; it is a self-discipline tool, not a security product.

## Build (phone-only, via Codemagic)
1. Push this repo to GitHub.
2. Codemagic reads `codemagic.yaml`, generates the Gradle wrapper and builds a debug APK.
3. Download the APK and install it over the old one.

**Signing:** `app/debug.keystore` is a fixed key committed on purpose so every build installs *over* the previous one. Keep the repo private, or create your own release key before sharing widely.

## Project layout
- `alarm/` — scheduling, ring screen, foreground service, boot re-arming, test sound
- `lock/` — the accessibility service that enforces the lock
- `pose/` — camera preview, ML Kit analyzer, rep counters (push-ups, squats, jumping jacks)
- `data/` — alarms, `ChallengeSession` (lock, time limit, away rule, emergency unlock), streaks, settings, step check
- `ui/` — Compose screens (home, alarm editor, challenge, settings, history, guide tour)

## Changelog

### 1.2.3
- Alarm ring screen is drawn edge-to-edge: no white strip above the orange screen.

### 1.2.2
- **Fixed:** a missed alarm's 24-hour lock could be lifted by finishing a *different* alarm. The penalty lock is now stored separately and only ends when 24 hours pass (or via the emergency unlock).
- **Fixed:** the Welcome card was only as wide as its text and its text/button turned dark in the phone's dark mode. Card is full width, text is always white, main buttons are always orange.
- Welcome, Challenge and Alarm-ring screens now scroll, so buttons can't be pushed off-screen on small phones or with large font sizes.
