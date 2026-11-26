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
