# DisciplineX — Native Android

A discipline app: **an alarm rings, and your phone stays locked down until you finish a challenge.** Push-ups, squats and jumping jacks are counted by the camera; Walking (with a step check), Meditation and Reading run on a timer you can't skip.

## How it works (v1.3)
1. **Alarm list.** Add as many alarms as you want with the **+** button. Each has a time (drum-style wheel), repeat (every day, specific weekdays, once, or **Custom** dates), label, exercise, target and a **time limit** ("finish within N minutes").
2. **Alarm rings.** The lock turns ON and the time limit starts. Dismiss only stops the sound.
3. **Finish in time.** The phone unlocks and the day counts for your streak.
4. **Miss the time limit.** The day is missed: the streak resets and the phone stays locked for **24 hours**. You can't carry on after the time is up.
5. **Away rule.** During a challenge you may leave the app for up to 5 minutes (an emergency). Come back later and the progress starts again from zero.
6. **Emergency unlock.** After a *missed* challenge you can unlock early from Home: keep the app open for 10 minutes (the clock pauses when you leave it), then the phone unlocks for 30 minutes and locks again by itself. The day stays missed and the streak stays 0.
7. **Alarm lock-in.** An alarm that rings within 2 hours can't be switched off, edited or deleted.

## Settings
- **Appearance:** theme (system / light / dark), language (English / தமிழ்).
- **Alarm preferences:** ringtone, vibration, test alarm sound.
- **Lock & Protection:** allowed apps (with icons), emergency unlock info, uninstall password, permission status with fix buttons.
- **Progress & History:** streak history calendar. Statistics and Achievements open from Home.
- **Data & Privacy:** save / restore a backup file, privacy explanation.
- **About:** **How this app works**, app version.

**How this app works** (Settings → About) explains every rule as IF / THEN / ELSE cards: finishing in time, missing the time limit, leaving the app for more or less than 5 minutes, emergency unlock, the 2-hour alarm lock-in, what opens while the phone is locked, switched-off phone, permissions off and uninstall protection. It is read-only text, so it never changes any setting. If you change a rule in the code (for example the 24 hours, 5 minutes or 2 hours), update `ui/settings/HowAppWorksScreen.kt` and this README too.

Every screen also has an **(i)** button that explains what is on it.

## App lock
- An accessibility service blocks every app except Phone, Camera, your allowed apps, keyboards, the home screen and system screens.
- It also checks split-screen and floating windows.
- System Settings is blocked while locked, so the lock can't be switched off from the Accessibility page.
- It only reads *which app owns a window* (package name), never text, messages or typing.
- **Uninstall protection:** DisciplineX is a device admin (it asks for no control over your phone). While a challenge is pending, missed or in emergency unlock, Android refuses to uninstall it and the uninstall password stays hidden. When the phone is not locked, the password in Settings lets you uninstall.
- Alarms and the allowed-apps list can't be edited while locked.

## Installing on a friend's phone
1. Install the APK (choose "Install anyway" if Play Protect warns).
2. **Android 13+:** the Accessibility switch is greyed out for apps installed from an APK. Open Settings → Apps → DisciplineX → ⋮ → **Allow restricted settings**.
3. Open the app and allow every permission it asks for.
4. Some phones (Xiaomi, Oppo, Vivo, Realme, Samsung) stop background apps aggressively. Allow auto-start / "no restrictions" battery for DisciplineX.

## Known limits (Android does not allow more)
- Uninstall protection needs the "Uninstall protection" permission to be On. Without it, the app can be uninstalled from the launcher (long-press → Uninstall).
- adb, root and Safe mode can still get around the lock.
- Safe mode switches off third-party accessibility services.
- If the phone is switched off at alarm time, the alarm doesn't ring and nothing is recorded.
- Camera counting can be fooled with a lot of effort; it is a self-discipline tool, not a security product.

## Build (phone-only, via Codemagic)
1. Push this repo to GitHub.
2. Codemagic reads `codemagic.yaml`, generates the Gradle wrapper, builds a **signed release APK** and runs the unit tests.
3. Download the APK and install it over the old one.

**Signing:** release APKs are signed with a private key that lives only in Codemagic (reference name `dx_release_key`) and in your own backup. It is never committed. Lose it and you can no longer ship updates that install over the old version, so keep a safe copy of the keystore file and its passwords.

## Project layout
- `alarm/` — scheduling, ring screen, foreground service, boot re-arming, test sound
- `lock/` — the accessibility service that enforces the lock
- `pose/` — camera preview, ML Kit analyzer, rep counters (push-ups, squats, jumping jacks)
- `data/` — alarms, `ChallengeSession` (lock, time limit, away rule, emergency unlock), streaks, challenge history, settings, backup, uninstall guard, step check
- `domain/` — statistics and achievements calculation
- `ui/` — Compose screens: `home/`, `alarms/` (editor), `challenge/`, `onboarding/`, `stats/` (Statistics + Achievements), `settings/` (Settings, history calendar, allowed apps, backup, **How this app works**), `navigation/`, `components/` (guide tour)
