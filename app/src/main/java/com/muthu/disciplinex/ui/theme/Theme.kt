package com.muthu.disciplinex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DisciplineXColorScheme = lightColorScheme(
    primary = OrangeStart,
    onPrimary = Surface,
    secondary = OrangeDeep,
    background = Background,
    surface = Surface,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = Danger
)

@Composable
fun DisciplineXTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DisciplineXColorScheme,
        typography = DisciplineXTypography,
        shapes = DisciplineXShapes,
        content = content
    )
}
