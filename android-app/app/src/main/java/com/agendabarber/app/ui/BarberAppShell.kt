package com.agendabarber.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.room.Room
import com.agendabarber.app.cache.AgendaCacheDatabase
import com.agendabarber.app.cache.AgendaRepository
import com.agendabarber.app.cache.AgendaViewModel
import com.agendabarber.app.network.AgendaApi
import com.agendabarber.app.network.ClientView
import com.agendabarber.app.network.CreateClient
import com.agendabarber.app.network.ServiceView
import com.agendabarber.app.servicecatalog.formatBrl
import com.agendabarber.app.ui.theme.BarberBackground
import com.agendabarber.app.ui.theme.BarberCancelAction
import com.agendabarber.app.ui.theme.BarberCompleted
import com.agendabarber.app.ui.theme.BarberGold
import com.agendabarber.app.ui.theme.BarberInProgress
import com.agendabarber.app.ui.theme.BarberOnGold
import com.agendabarber.app.ui.theme.BarberOutline
import com.agendabarber.app.ui.theme.BarberSurface
import com.agendabarber.app.ui.theme.BarberSurfaceRaised
import com.agendabarber.app.ui.theme.BarberTextPrimary
import com.agendabarber.app.ui.theme.BarberTextSecondary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
                BarberAgendaScreen(
                    repository = repository,
                    token = token,
                    revision = revision,
                    onNew = { navigation.navigate(BarberRoute.BOOKING) },
                    onDetails = { id -> navigation.navigate("appointment/" + id) }
                )
            }
            composable(BarberRoute.BOOKING) {
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    BackHeader("Novo Agendamento") { navigation.popBackStack() }
                    BookingForm(api, token) {
                        revision++
                        navigation.popBackStack(BarberRoute.AGENDA, false)
                    }
                }
            }
            composable(
                BarberRoute.DETAILS,
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { backStack ->
                val id = requireNotNull(backStack.arguments?.getString("id"))
                AppointmentDetails(api, token, id) {
                    revision++
                    navigation.popBackStack()
                }
            }
            composable(BarberRoute.CLIENTS) {
                BarberClientsScreen(api, token) {
                    navigation.navigate(BarberRoute.NEW_CLIENT)
                }
            }
            composable(BarberRoute.NEW_CLIENT) {
                BarberNewClientScreen(api, token) { navigation.popBackStack() }
            }
            composable(BarberRoute.SERVICES) {
                BarberServicesScreen(api, token) { navigation.popBackStack() }
            }
            composable(BarberRoute.BLOCK_TIME) {
                BarberBlockScreen { navigation.popBackStack() }
            }
            composable(BarberRoute.STATS) {
                BarberStatisticsScreen()
            }
            composable(BarberRoute.MORE) {
                BarberMoreScreen(
                    onServices = { navigation.navigate(BarberRoute.SERVICES) },
                    onBlocks = { navigation.navigate(BarberRoute.BLOCK_TIME) },
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
private fun BarberAgendaScreen(
    repository: AgendaRepository,
    token: String,
    revision: Int,
    onNew: () -> Unit,
    onDetails: (String) -> Unit
) {
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    var period by remember { mutableStateOf("day") }
    var filter by remember { mutableStateOf("all") }
    var reload by remember { mutableStateOf(0) }
    val agendaViewModel: AgendaViewModel = viewModel(
        factory = remember(repository) { AgendaViewModel.Factory(repository) }
    )
    val state by agendaViewModel.state.collectAsState()
    LaunchedEffect(token, day, period, filter, reload, revision) {
        if (validDate(day)) {
            agendaViewModel.refresh(token, day, period, filter)
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Olá, barbeiro!", style = MaterialTheme.typography.headlineSmall)
        Text("Sua agenda, seus resultados.", color = BarberTextSecondary)
        OutlinedTextField(
            value = day,
            onValueChange = { day = it },
            label = { Text("Data (AAAA-MM-DD)") },
            isError = !validDate(day),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("day" to "Hoje", "week" to "Semana", "month" to "Mês").forEach { (value, label) ->
                FilterChip(
                    selected = period == value,
                    onClick = { period = value },
                    label = { Text(label) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "Todos", "SCHEDULED" to "Agendados", "COMPLETED" to "Concluídos")
                .forEach { (value, label) ->
                    FilterChip(
                        selected = filter == value,
                        onClick = { filter = value },
                        label = { Text(label) }
                    )
                }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Atendimentos", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { reload++ }) { Text("Atualizar") }
            TextButton(onClick = onNew) { Text("+ Agendar") }
        }
        if (state.loading) CircularProgressIndicator()
        if (state.error.isNotBlank()) {
            Text(state.error, color = MaterialTheme.colorScheme.error)
        }
        if (state.offline) {
            val time = state.lastSyncedAt?.let {
                DateTimeFormatter.ofPattern("dd/MM HH:mm")
                    .format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
            } ?: "desconhecida"
            Text("Modo offline · Última sincronização: " + time, color = BarberGold)
        }
        if (!state.loading && state.error.isBlank() && state.appointments.isEmpty()) {
            EmptyState("Nenhum atendimento neste período.")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.appointments, key = { it.id }) { appointment ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onDetails(appointment.id) },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(appointment.clientName, fontWeight = FontWeight.SemiBold)
                        Text(
                            appointment.serviceName + " · " + appointment.durationMinutes + " min",
                            color = BarberTextSecondary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(displayTime(appointment.startsAt))
                            Spacer(Modifier.width(12.dp))
                            Text(formatBrl(appointment.price))
                            Spacer(Modifier.weight(1f))
                            Text(
                                statusText(appointment.status),
                                color = statusColor(appointment.status)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarberClientsScreen(api: AgendaApi, token: String, onNewClient: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var clients by remember { mutableStateOf(emptyList<ClientView>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(token, query) {
        loading = true
        try {
            clients = api.clients(token, query)
            error = ""
        } catch (failure: Exception) {
            clients = emptyList()
            error = apiMessage(failure)
        } finally {
            loading = false
        }
    }
    Column(
        Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Clientes", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onNewClient) { Text("+ Novo") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar clientes") },
            modifier = Modifier.fillMaxWidth()
        )
        if (loading) CircularProgressIndicator()
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        if (!loading && error.isBlank() && clients.isEmpty()) EmptyState("Nenhum cliente encontrado.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(clients, key = { it.id }) { client ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(client.name)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(client.name, fontWeight = FontWeight.Medium)
                            Text(client.phone ?: "Telefone não informado", color = BarberTextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarberNewClientScreen(api: AgendaApi, token: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BackHeader("Novo Cliente", onBack)
        Avatar(name)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nome completo") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Telefone / WhatsApp (opcional)") },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Foto, aniversário e campos adicionais serão entregues na F02-08.",
            color = BarberTextSecondary
        )
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Button(
            enabled = name.isNotBlank() && !loading,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                loading = true
                scope.launch {
                    try {
                        api.newClient(token, CreateClient(name.trim(), phone.ifBlank { null }))
                        onBack()
                    } catch (failure: Exception) {
                        error = apiMessage(failure)
                    } finally {
                        loading = false
                    }
                }
            }
        ) { Text(if (loading) "Salvando..." else "Salvar cliente") }
    }
}

@Composable
private fun BarberServicesScreen(api: AgendaApi, token: String, onBack: () -> Unit) {
    var services by remember { mutableStateOf(emptyList<ServiceView>()) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(token) {
        loading = true
        try {
            services = api.services(token)
            error = ""
        } catch (failure: Exception) {
            error = apiMessage(failure)
        } finally {
            loading = false
        }
    }
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackHeader("Serviços", onBack)
        if (loading) CircularProgressIndicator()
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        if (!loading && error.isBlank() && services.isEmpty()) EmptyState("Nenhum serviço cadastrado.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(services, key = { it.id }) { service ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ContentCut, contentDescription = null, tint = BarberGold)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(service.name, fontWeight = FontWeight.Medium)
                            Text(service.durationMinutes.toString() + " min", color = BarberTextSecondary)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(formatBrl(service.defaultPrice))
                    }
                }
            }
        }
    }
}

@Composable
private fun BarberBlockScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        BackHeader("Bloquear Horário", onBack)
        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = BarberGold)
        Text("Bloqueios únicos e recorrentes", style = MaterialTheme.typography.titleLarge)
        Text(
            "A API já possui expediente e bloqueios. O formulário UX-08 será conectado na etapa dedicada.",
            color = BarberTextSecondary
        )
        EmptyState("Nenhum bloqueio fictício foi criado.")
    }
}

@Composable
private fun BarberStatisticsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BarberBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Estatísticas",
                    style = MaterialTheme.typography.headlineMedium,
                    color = BarberTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Seus resultados em um só lugar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BarberTextSecondary
                )
            }
            Surface(shape = CircleShape, color = BarberGold.copy(alpha = 0.14f)) {
                Icon(
                    Icons.Outlined.BarChart,
                    contentDescription = null,
                    tint = BarberGold,
                    modifier = Modifier.padding(12.dp).size(24.dp)
                )
            }
        }

        Surface(
            color = BarberSurface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BarberOutline)
        ) {
            Text(
                "Os indicadores serão exibidos após a integração com a API de resultados.",
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                color = BarberTextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Faturamento", BarberGold, Modifier.weight(1f))
            StatCard("Cortes realizados", BarberCompleted, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Ticket médio", BarberInProgress, Modifier.weight(1f))
            StatCard("Cancelamentos", BarberCancelAction, Modifier.weight(1f))
        }

        Text(
            "Faturamento por dia",
            style = MaterialTheme.typography.titleMedium,
            color = BarberTextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        StatisticsEmptyPanel("Gráfico indisponível", "Aguardando dados de faturamento.")

        Text(
            "Serviços mais realizados",
            style = MaterialTheme.typography.titleMedium,
            color = BarberTextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        StatisticsEmptyPanel("Sem dados de serviços", "Os serviços aparecerão após a integração.")
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StatCard(title: String, accent: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 124.dp),
        color = BarberSurfaceRaised,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BarberOutline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(accent, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    color = BarberTextPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Text("—", color = BarberGold, style = MaterialTheme.typography.headlineMedium)
            Text(
                "Aguardando dados",
                color = BarberTextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun StatisticsEmptyPanel(title: String, description: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BarberSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BarberOutline)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Outlined.BarChart, contentDescription = null, tint = BarberGold)
            Text(
                title,
                color = BarberTextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                description,
                color = BarberTextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun BarberMoreScreen(onServices: () -> Unit, onBlocks: () -> Unit, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Mais", style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar("Barbeiro")
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Meu perfil", fontWeight = FontWeight.SemiBold)
                Text("Área interna do barbeiro", color = BarberTextSecondary)
            }
        }
        MenuAction("Serviços", onServices)
        MenuAction("Horário de funcionamento e bloqueios", onBlocks)
        MenuAction("Sair da conta", onLogout)
        Text("Notificações, sincronização e tema serão implementados nas próximas etapas.", color = BarberTextSecondary)
    }
}

@Composable
private fun MenuAction(label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
        }
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
