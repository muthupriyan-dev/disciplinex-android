# DisciplineX 1.2.1 - Phase 1 Audit + Phase 2 (partial)

STATUS: nothing below was compiled or run. The sandbox has no Android SDK, no Gradle and no network.
Every change was reviewed by reading only. Build it (Codemagic) before trusting it.

## Project facts (verified from files)
- AGP 8.5.2, Kotlin 1.9.24, Compose compiler 1.5.14, BOM 2024.06, compileSdk/targetSdk 34, minSdk 26
- CameraX 1.3.4, ML Kit pose-detection 18.0.0-beta5 (beta)
- ~30 Kotlin files, all state in SharedPreferences (JSON blobs), no Room, no ViewModels, no tests
- No gradle wrapper in repo (Codemagic generates it), no release buildType, no unit tests

## Findings
| # | Severity | Finding | Status |
|---|----------|---------|--------|
| 1 | High | versionName 1.2.0 while the project is 1.2.1 (versionCode not bumped) | FIXED (1.2.1 / 13) |
| 2 | High | Only debug signing exists; debug.keystore with password "android" is committed | PARTIAL: release signing from git-ignored keystore.properties / env added. debug.keystore left in place on purpose so Codemagic debug APKs still install over old ones. Rotate before any public release. |
| 3 | High | Duplicate alarm delivery (e.g. re-arm after TIME_SET) while a session is active recorded a false MISSED day and restarted the session | FIXED in ChallengeSession.start (60 s same-alarm, same-boot guard); AlarmReceiver skips service start on duplicate |
| 4 | High | Completed challenges only bump per-day counters: no reps, exercise or duration stored, so Statistics/Achievements cannot be real | FIXED (data layer): new HistoryStore, written once from ChallengeSession.complete. No UI yet. Old completions are not recoverable. |
| 5 | Medium | PoseCameraView: if the screen is left before the CameraProvider future resolves, camera binds after dispose and is never released | FIXED (disposed flag) |
| 6 | Medium | BootReceiver exported=true though it only needs system broadcasts | FIXED (exported=false) |
| 7 | Medium | Accessibility config has canRetrieveWindowContent=true. Code only reads package names, but the capability is broader than needed. It is required for AccessibilityWindowInfo.root (split-screen check), so it was NOT removed. Be honest about this in the privacy text. | OPEN |
| 8 | Medium | REQUEST_IGNORE_BATTERY_OPTIMIZATIONS and FOREGROUND_SERVICE_SPECIAL_USE need Play Store declarations; fine for sideloading | OPEN |
| 9 | Medium | PoseAnalyzer closes the detector while frames may be in flight; RepCounter state is not reset on camera rebind | OPEN |
| 10 | Low | SharedPreferences .apply() everywhere; a process kill right after a write can lose it (streak/lock state) | OPEN |
| 11 | Low | R8 off, no ProGuard config; ML Kit/CameraX consumer rules would need a verified build | OPEN |
| 12 | Low | allowBackup=false, so the "Data and Backup" setting needs an in-app export/import, not system backup | OPEN |

## Good as found (kept)
Monotonic-clock lock timing with boot-count fallback, setAlarmClock (no exact-alarm permission needed), notification fallback if the service cannot start, per-side landmark picking and anti-cheat timing in RepCounter, fail-open app-lock so the phone cannot be bricked.

## Files changed
app/build.gradle.kts, app/src/main/AndroidManifest.xml, .gitignore, alarm/AlarmReceiver.kt, data/ChallengeSession.kt, pose/PoseCameraView.kt
## Files added
AUDIT.md, data/HistoryStore.kt

## Not done yet (nothing here is implemented)
Design system, Home/Editor/Challenge/History/Settings redesign, Statistics, Achievements, ViewModels, Room/DataStore, backup/restore, privacy policy, widgets, all unit/instrumented tests, performance work, release APK/AAB.
