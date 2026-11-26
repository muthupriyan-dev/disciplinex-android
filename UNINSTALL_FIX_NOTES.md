# Uninstall-protection fixes

NOT compiled or run (no Android SDK in the sandbox). Build with Codemagic and test on a real phone.

1. Device admin (no policies) added: DisciplineAdminReceiver, res/xml/device_admin.xml, manifest receiver,
   new "Uninstall protection" permission card (PermissionChecks.Kind.DEVICE_ADMIN).
2. LockAccessibilityService: window CLASS name ("...Uninstall...") is trusted first; if the app name is not readable
   the window is re-read after 450 ms; unreadable text = fail closed.
3. If the OS refuses to start the password screen from the background, a notification opens it + user is sent Home.
4. Settings stale state: `locked` and `uninstallProtected` now refresh every second.
5. More uninstall words (hi, es/pt, fr, de, ru, zh, ja, ar, id) - best effort, class-name check is the main detector.
6. Grant: 60 s, shrinks to 30 s once the dialog is seen; revokeGrant() available.
7. promptShowing now follows onResume/onPause and the screen finishes in onStop (Home no longer leaves a bypass).
8. Device-admin deactivate screen and "Stop App Lock?" dialog in Settings also ask for the password
   (unlocked) or are blocked (challenge pending/missed/emergency unlock).
9. After a correct password the app removes its own device admin, then opens the real uninstall dialog.

Known limits: adb/root/safe mode; accessibility switched off while unlocked still removes the app-lock
(the stop-dialog gate is best-effort and English-only); stop-dialog text differs per Android version.

## Round 2: launcher long-press -> Uninstall
Many launchers show their own uninstall confirm dialog (window owned by the launcher, not the installer),
so the first version never saw it. Added Watched.LAUNCHER: launcher windows are checked; while locked/missed/
emergency-unlock any launcher window asking to uninstall DisciplineX is blocked. The device admin (enable the
"Uninstall protection" card) is the real safety net: Android refuses to uninstall an active admin app for
every caller, including the launcher.

## Round 3
Onboarding "Why we ask for this" screen now has an "Uninstall protection" (device admin) row; Continue stays locked
until it is active. Phones without FEATURE_DEVICE_ADMIN count it as granted so nobody gets stuck.

## Round 4
Bug: the device-admin ACTIVATE screen (same DeviceAdminAdd class as deactivate) asked for the uninstall password,
so a fresh install could never switch Uninstall protection on. Now the screen is guarded only while the admin is
already active.
