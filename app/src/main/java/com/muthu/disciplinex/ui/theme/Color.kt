package com.muthu.disciplinex.ui.theme

import androidx.compose.ui.graphics.Color
import com.muthu.disciplinex.data.AppSettings

// Core brand orange (gradient endpoints used on hero cards) - same in light and dark.
val OrangeStart = Color(0xFFFF7A45)
val OrangeEnd = Color(0xFFFF4E3D)
val OrangeDeep = Color(0xFFE8471F)

// Neutral colors follow the theme. They are plain getters that read Compose
// state (AppSettings), so any screen using them recomposes when the theme changes.
val Background: Color get() = if (AppSettings.isDark) Color(0xFF121216) else Color(0xFFF7F7FA)
val Surface: Color get() = if (AppSettings.isDark) Color(0xFF1E1E24) else Color(0xFFFFFFFF)
val TextPrimary: Color get() = if (AppSettings.isDark) Color(0xFFF2F2F5) else Color(0xFF1E1E24)
val TextMuted: Color get() = if (AppSettings.isDark) Color(0xFF9A9AA4) else Color(0xFF8A8A93)
val Divider: Color get() = if (AppSettings.isDark) Color(0xFF2C2C34) else Color(0xFFEDEDF2)

/** Soft grey chip / circle background that works in both themes. */
val ChipBackground: Color get() = if (AppSettings.isDark) Color(0xFF2C2C34) else Color(0xFFF1F1F5)

val Success = Color(0xFF2ECC71)
val Warning = Color(0xFFFFB020)
val Danger = Color(0xFFFF4D4F)
