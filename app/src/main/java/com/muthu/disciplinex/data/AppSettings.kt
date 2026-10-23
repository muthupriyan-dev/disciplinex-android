package com.muthu.disciplinex.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * App-wide look and feel settings: theme (system / light / dark), language
 * (English / Tamil), alarm ringtone and vibration.
 *
 * Theme and language are Compose state, so changing them in Settings
 * recomposes every screen straight away - no restart needed. Call [load]
 * before any screen or notification text is built (DisciplineXTheme and the
 * alarm service both do).
 */
object AppSettings {
    private const val PREFS_NAME = "disciplinex_settings"
    private const val KEY_THEME = "theme"
    private const val KEY_LANG = "lang"
    private const val KEY_RINGTONE = "ringtone"
    private const val KEY_VIBRATE = "vibrate"

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    const val LANG_EN = "en"
    const val LANG_TA = "ta"

    private var loaded = false

    var themeMode by mutableStateOf(THEME_SYSTEM)
        private set
    var language by mutableStateOf(LANG_EN)
        private set

    /** Follows the phone's own dark mode; updated by DisciplineXTheme. */
    var systemDark by mutableStateOf(false)

    val isDark: Boolean
        get() = when (themeMode) {
            THEME_DARK -> true
            THEME_LIGHT -> false
            else -> systemDark
        }

    val isTamil: Boolean get() = language == LANG_TA

    /** Locale for month / day names and dates, following the chosen language. */
    val locale: Locale get() = if (isTamil) Locale("ta", "IN") else Locale.getDefault()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(context: Context) {
        if (loaded) return
        val p = prefs(context)
        themeMode = p.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
        language = p.getString(KEY_LANG, LANG_EN) ?: LANG_EN
        loaded = true
    }

    fun setTheme(context: Context, mode: String) {
        themeMode = mode
        prefs(context).edit().putString(KEY_THEME, mode).apply()
    }

    fun setLanguage(context: Context, lang: String) {
        language = lang
        prefs(context).edit().putString(KEY_LANG, lang).apply()
    }

    // ---- Alarm sound ----

    /** The ringtone the user picked, or null for the phone's default alarm sound. */
    fun ringtoneUri(context: Context): String? = prefs(context).getString(KEY_RINGTONE, null)

    fun setRingtoneUri(context: Context, uri: String?) {
        prefs(context).edit().apply {
            if (uri == null) remove(KEY_RINGTONE) else putString(KEY_RINGTONE, uri)
        }.apply()
    }

    fun vibrate(context: Context): Boolean = prefs(context).getBoolean(KEY_VIBRATE, true)

    fun setVibrate(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_VIBRATE, on).apply()
    }
}

/** Picks the English or the Tamil text, depending on the language chosen in Settings. */
fun tr(en: String, ta: String): String = if (AppSettings.isTamil) ta else en
