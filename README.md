# DisciplineX — Native Android (Milestone 1)

This is the **first milestone** of the full native Android app from your spec. Building all 26 feature areas (alarm, AI pose verification, Accessibility Service app-locking, Firebase, anti-cheat, leaderboards, penalties, analytics...) properly in one shot isn't realistic — that's a multi-week production build. So we're doing it in stages, like you asked.

## What's in this milestone
- Full Gradle project structure (Kotlin + Jetpack Compose + Material 3)
- Orange gradient theme matching your reference screenshots (`ui/theme/`)
- Onboarding flow: Welcome → Wake-up time & duration → Exercise type → Permissions explanation → Home
- A placeholder Home screen showing the chosen challenge

## What's NOT in this milestone yet (coming in later ones)
- Actual alarm firing (AlarmManager) + full-screen lock alarm UI
- Live challenge countdown timer
- CameraX + TensorFlow Lite MoveNet pose detection & rep counting
- Motion sensor verification for non-camera exercises
- Accessibility Service app-locking
- Firebase Auth / Firestore / FCM / Crashlytics
- Streaks, XP, leaderboard, penalties, anti-cheat, analytics dashboard

## Why this needs a real device (not just Codemagic)
Once we add Accessibility Service, CameraX, and Firebase, you'll need to **install the APK on your actual phone** to test them — accessibility permissions and camera behavior don't work properly on typical emulators, and Codemagic only builds the APK, it doesn't run it. Codemagic + install-on-phone is the same pattern as your web builds, just for an APK instead of a URL.

## Build steps (phone-only, via Codemagic)
1. Create a GitHub repo (e.g. `disciplinex-android`), upload this whole folder structure using the GitHub web upload button — keep the folder paths exactly as they are (`app/src/main/java/...` etc.).
2. In Codemagic: **Add application → GitHub → select the repo**. Codemagic should auto-detect it as a native Android project (it reads `codemagic.yaml`).
3. Start a build. It generates the Gradle wrapper on Codemagic's machine (no need to upload wrapper binaries yourself) and produces a debug APK as a build artifact.
4. Download the APK from the Codemagic build page on your phone, tap to install (you may need to allow "install unknown apps" for your browser once).

## Note on the app icon
The launcher icon is currently a simple placeholder (orange square with a bold "X"). We can design a proper one later — it doesn't block the build.

## Next milestone (M2)
Alarm + live challenge countdown screen. Say the word when you want to start it.
