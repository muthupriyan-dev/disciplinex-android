package com.muthu.disciplinex.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.muthu.disciplinex.data.AppSettings

@Composable
fun DisciplineXTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    // Load the saved theme/language BEFORE any child reads them (no flash of the wrong theme).
    remember(context) {
        AppSettings.load(context)
        AppSettings.systemDark = systemDark
        true
    }
    SideEffect { AppSettings.systemDark = systemDark }

    val dark = AppSettings.isDark
    val colorScheme = if (dark) {
        darkColorScheme(
            primary = OrangeStart,
            onPrimary = Surface,
            secondary = OrangeDeep,
            background = Background,
            surface = Surface,
            surfaceContainerHigh = Surface,
            surfaceContainerHighest = Divider,
            surfaceVariant = Divider,
            onBackground = TextPrimary,
            onSurface = TextPrimary,
            onSurfaceVariant = TextMuted,
            error = Danger
        )
    } else {
        lightColorScheme(
            primary = OrangeStart,
            onPrimary = Surface,
            secondary = OrangeDeep,
            background = Background,
            surface = Surface,
            onBackground = TextPrimary,
            onSurface = TextPrimary,
            error = Danger
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DisciplineXTypography,
        shapes = DisciplineXShapes,
        content = content
    )
}
