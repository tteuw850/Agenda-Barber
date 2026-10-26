package com.agendabarber.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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

// Aplicar apenas ao conteudo da UX-09 (Estatisticas); bottom nav segue DARK.
private val StatisticsLightColors = lightColorScheme(
    primary = BarberGold,
    onPrimary = BarberOnGold,
    secondary = StatsChartBlue,
    onSecondary = StatsTextPrimary,
    background = StatsBackground,
    onBackground = StatsTextPrimary,
    surface = StatsSurface,
    onSurface = StatsTextPrimary,
    surfaceVariant = StatsCutsCard,
    onSurfaceVariant = StatsTextSecondary,
    outline = StatsOutline,
    error = BarberCancelAction,
    onError = Color.White
)

@Composable
fun AgendaBarberTheme(
    statisticsScreen: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (statisticsScreen) StatisticsLightColors else AgendaDarkColors,
        typography = AgendaBarberTypography,
        content = content
    )
}
