package com.agendabarber.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AgendaDarkColors = darkColorScheme(
    primary = BarberGold,
    onPrimary = BarberOnGold,
    secondary = BarberInProgress,
    onSecondary = BarberBackground,
    tertiary = BarberCompleted,
    background = BarberBackground,
    onBackground = BarberTextPrimary,
    surface = BarberSurface,
    onSurface = BarberTextPrimary,
    surfaceVariant = BarberSurfaceRaised,
    onSurfaceVariant = BarberTextSecondary,
    outline = BarberOutline,
    error = BarberCancelAction,
    onError = Color.White
)

@Composable
fun AgendaBarberTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AgendaDarkColors,
        typography = AgendaBarberTypography,
        content = content
    )
}
