package com.agendabarber.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.room.Room
import com.agendabarber.app.cache.AgendaCacheDatabase
import com.agendabarber.app.cache.AgendaRepository
import com.agendabarber.app.network.AgendaApi
import com.agendabarber.app.ui.theme.BarberBackground
import com.agendabarber.app.ui.theme.BarberGold
import com.agendabarber.app.ui.theme.BarberOnGold
import com.agendabarber.app.ui.theme.BarberOutline
import com.agendabarber.app.ui.theme.BarberSurface
import com.agendabarber.app.ui.theme.BarberTextSecondary
import kotlinx.coroutines.launch

/** Routes match the ten UX references. Inner forms intentionally have no bottom navigation. */
internal object BarberRoute {
    const val AGENDA = "agenda"
    const val CLIENTS = "clients"
    const val BOOKING = "booking"
    const val STATS = "statistics"
    const val MORE = "more"
    const val NEW_CLIENT = "new-client"
    const val SERVICES = "services"
    const val BLOCK_TIME = "block-time"
    const val WORKING_HOURS = "working-hours"
    const val EXPENSES = "expenses"
    const val DETAILS = "appointment/{id}"
    val bottomDestinations = setOf(AGENDA, CLIENTS, STATS, MORE)
}

@Composable
internal fun BarberAuthenticatedApp(api: AgendaApi, token: String, onLogout: () -> Unit) {
    val context = LocalContext.current
    val database = remember {
        Room.databaseBuilder(
            context.applicationContext,
            AgendaCacheDatabase::class.java,
            "agenda-barber-read-cache"
        ).build()
    }
    val repository = remember(api, database) {
        AgendaRepository(api, database.agendaCacheDao())
    }
    val scope = rememberCoroutineScope()
    val navigation = rememberNavController()
    val entry by navigation.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: BarberRoute.AGENDA
    var revision by remember { mutableStateOf(0) }

    Scaffold(
        containerColor = BarberBackground,
        bottomBar = {
            if (route in BarberRoute.bottomDestinations) {
                BarberBottomBar(current = route) { target ->
                    if (target == BarberRoute.BOOKING) {
                        navigation.navigate(target)
                    } else {
                        navigation.navigate(target) {
                            popUpTo(BarberRoute.AGENDA) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navigation,
            startDestination = BarberRoute.AGENDA,
            modifier = Modifier.padding(padding)
        ) {
            composable(BarberRoute.AGENDA) {
                BarberAgendaTimeline(
                    repository = repository,
                    token = token,
                    revision = revision,
                    onNew = { navigation.navigate(BarberRoute.BOOKING) },
                    onDetails = { id -> navigation.navigate("appointment/" + id) }
                )
            }
            composable(BarberRoute.BOOKING) { entry ->
                val createdClientId by entry.savedStateHandle
                    .getStateFlow("f0207ClientId", "").collectAsState()
                val createdClientName by entry.savedStateHandle
                    .getStateFlow("f0207ClientName", "").collectAsState()
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    BackHeader("Novo Agendamento") { navigation.popBackStack() }
                    BarberBookingForm(
                        api = api,
                        token = token,
                        onSaved = {
                            revision++
                            navigation.popBackStack(BarberRoute.AGENDA, false)
                        },
                        onNewClient = { navigation.navigate(BarberRoute.NEW_CLIENT) },
                        newClientId = createdClientId,
                        newClientName = createdClientName
                    )
                }
            }
            composable(
                BarberRoute.DETAILS,
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { backStack ->
                val id = requireNotNull(backStack.arguments?.getString("id"))
                val detailRefresh by backStack.savedStateHandle.getStateFlow("f0207UpdatedAt", 0L).collectAsState()
                BarberAppointmentDetails(
                    api = api,
                    token = token,
                    id = id,
                    onBack = {
                        revision++
                        navigation.popBackStack()
                    },
                    onEdit = { navigation.navigate("appointment/" + id + "/edit") },
                    refreshSignal = detailRefresh
                )
            }
            composable(
                "appointment/{id}/edit",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { entry ->
                val id = requireNotNull(entry.arguments?.getString("id"))
                val createdClientId by entry.savedStateHandle
                    .getStateFlow("f0207ClientId", "").collectAsState()
                val createdClientName by entry.savedStateHandle
                    .getStateFlow("f0207ClientName", "").collectAsState()
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    BackHeader("Editar agendamento") { navigation.popBackStack() }
                    BarberBookingForm(
                        api = api,
                        token = token,
                        onSaved = {
                            navigation.previousBackStackEntry?.savedStateHandle?.set(
                                "f0207UpdatedAt",
                                System.currentTimeMillis()
                            )
                            revision++
                            navigation.popBackStack()
                        },
                        onNewClient = { navigation.navigate(BarberRoute.NEW_CLIENT) },
                        newClientId = createdClientId,
                        newClientName = createdClientName,
                        editId = id
                    )
                }
            }
            composable(BarberRoute.CLIENTS) {
                BarberClientsCatalogScreen(api, token, revision) {
                    navigation.navigate(BarberRoute.NEW_CLIENT)
                }
            }
            composable(BarberRoute.NEW_CLIENT) {
                BarberNewClientScreen(
                    api = api,
                    token = token,
                    onBack = { navigation.popBackStack() },
                    onCreated = { created ->
                        revision++
                        navigation.previousBackStackEntry?.savedStateHandle
                            ?.set("f0207ClientId", created.id)
                        navigation.previousBackStackEntry?.savedStateHandle
                            ?.set("f0207ClientName", created.name)
                        navigation.popBackStack()
                    }
                )
            }
            composable(BarberRoute.SERVICES) {
                BarberServicesCatalogScreen(api, token) { navigation.popBackStack() }
            }
            composable(BarberRoute.BLOCK_TIME) {
                BarberBlockTimeScreen(api, token) { navigation.popBackStack() }
            }
            composable(BarberRoute.EXPENSES) {
                BarberExpensesScreen(api, token) { navigation.popBackStack() }
            }
            composable(BarberRoute.WORKING_HOURS) {
                BarberWorkingHoursScreen(api, token) { navigation.popBackStack() }
            }
            composable(BarberRoute.STATS) {
                BarberStatisticsScreen()
            }
            composable(BarberRoute.MORE) {
                val lastCacheWriteAt by repository.lastCacheWriteAt.collectAsState()
                BarberMoreSettingsScreen(
                    lastCacheWriteAt = lastCacheWriteAt,
                    onWorkingHours = { navigation.navigate(BarberRoute.WORKING_HOURS) },
                    onServices = { navigation.navigate(BarberRoute.SERVICES) },
                    onBlocks = { navigation.navigate(BarberRoute.BLOCK_TIME) },
                    onExpenses = { navigation.navigate(BarberRoute.EXPENSES) },
                    onLogout = {
                        scope.launch {
                            try {
                                repository.clear()
                            } finally {
                                onLogout()
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun BarberBottomBar(current: String, onNavigate: (String) -> Unit) {
    HorizontalDivider(color = BarberOutline)
    NavigationBar(containerColor = BarberBackground) {
        listOf(
            Triple(BarberRoute.AGENDA, "Agenda", Icons.Outlined.CalendarMonth),
            Triple(BarberRoute.CLIENTS, "Clientes", Icons.Outlined.Groups),
            Triple(BarberRoute.BOOKING, "+", Icons.Outlined.Add),
            Triple(BarberRoute.STATS, "Estatísticas", Icons.Outlined.BarChart),
            Triple(BarberRoute.MORE, "Mais", Icons.Outlined.MoreHoriz)
        ).forEach { (target, label, image) ->
            val central = target == BarberRoute.BOOKING
            NavigationBarItem(
                selected = target == current,
                onClick = { onNavigate(target) },
                icon = {
                    if (central) {
                        Surface(shape = CircleShape, color = BarberGold) {
                            Icon(
                                imageVector = image,
                                contentDescription = "Novo Agendamento",
                                tint = BarberOnGold,
                                modifier = Modifier.padding(10.dp).size(26.dp)
                            )
                        }
                    } else {
                        Icon(imageVector = image, contentDescription = label)
                    }
                },
                label = { Text(label, maxLines = 1) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BarberGold,
                    selectedTextColor = BarberGold,
                    unselectedIconColor = BarberTextSecondary,
                    unselectedTextColor = BarberTextSecondary,
                    indicatorColor = BarberSurface
                )
            )
        }
    }
}

@Composable
private fun BackHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) { Text("‹ Voltar") }
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Avatar(name: String) {
    Surface(shape = CircleShape, color = BarberGold.copy(alpha = 0.18f)) {
        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            Text(name.trim().take(1).uppercase().ifBlank { "?" }, color = BarberGold)
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Surface(color = BarberSurface, shape = RoundedCornerShape(14.dp)) {
        Text(
            message,
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            color = BarberTextSecondary
        )
    }
}
