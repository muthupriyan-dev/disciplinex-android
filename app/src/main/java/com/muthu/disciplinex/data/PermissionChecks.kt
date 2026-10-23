package com.muthu.disciplinex.data

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.muthu.disciplinex.lock.LockAccessibilityService

/**
 * Live status of every permission the app needs, plus the Settings screen that
 * fixes each one. Used by the Settings screen and the warning card on Home.
 */
object PermissionChecks {

    enum class Kind { NOTIFICATIONS, FULL_SCREEN, CAMERA, BATTERY, ACCESSIBILITY }

    fun title(kind: Kind): String = when (kind) {
        Kind.NOTIFICATIONS -> tr("Notifications", "அறிவிப்புகள்")
        Kind.FULL_SCREEN -> tr("Alarm over lock screen", "லாக் ஸ்கிரீன் மேல் அலாரம்")
        Kind.CAMERA -> tr("Camera", "கேமரா")
        Kind.BATTERY -> tr("Run in background", "பின்னணியில் இயங்கு")
        Kind.ACCESSIBILITY -> tr("App lock (Accessibility)", "ஆப் லாக் (Accessibility)")
    }

    fun isGranted(context: Context, kind: Kind): Boolean = when (kind) {
        Kind.NOTIFICATIONS ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        Kind.FULL_SCREEN ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                (context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() ?: true)
        Kind.CAMERA ->
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        Kind.BATTERY ->
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        Kind.ACCESSIBILITY -> LockAccessibilityService.isEnabled(context)
    }

    /** Everything that is switched off right now. */
    fun missing(context: Context): List<Kind> = Kind.values().filter { !isGranted(context, it) }

    /** The system screen where the user can switch this permission on. */
    fun fixIntent(context: Context, kind: Kind): Intent {
        val pkgUri = Uri.parse("package:${context.packageName}")
        return when (kind) {
            Kind.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            Kind.FULL_SCREEN ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri)
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
                }
            Kind.CAMERA -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
            Kind.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri)
            Kind.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
    }
}
