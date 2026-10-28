package com.agendabarber.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agendabarber.app.cache.AgendaRepository
import com.agendabarber.app.cache.AgendaViewModel
import com.agendabarber.app.network.AppointmentView
import com.agendabarber.app.ui.theme.BarberBackground
import com.agendabarber.app.ui.theme.BarberGold
import com.agendabarber.app.ui.theme.BarberOnGold
import com.agendabarber.app.ui.theme.BarberOutline
import com.agendabarber.app.ui.theme.BarberSurface
import com.agendabarber.app.ui.theme.BarberSurfaceRaised
import com.agendabarber.app.ui.theme.BarberTextPrimary
import com.agendabarber.app.ui.theme.BarberTextSecondary
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val agendaZone = ZoneId.of("America/Sao_Paulo")
private val brazil = Locale.forLanguageTag("pt-BR")
private val dateTitle = DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM", brazil)
private val monthTitle = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", brazil)
private val shortDate = DateTimeFormatter.ofPattern("dd/MM", brazil)
private val hourFormat = DateTimeFormatter.ofPattern("HH:mm", brazil)
private val syncFormat = DateTimeFormatter.ofPattern("dd/MM HH:mm", brazil)
private val dayLabel = DateTimeFormatter.ofPattern("EEE", brazil)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BarberAgendaTimeline(
    repository: AgendaRepository,
    token: String,
    revision: Int,
    onNew: () -> Unit,
    onDetails: (String) -> Unit
) {
    var date by rememberSaveable { mutableStateOf(LocalDate.now(agendaZone).toString()) }
    var period by rememberSaveable { mutableStateOf("day") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var refresh by rememberSaveable { mutableIntStateOf(0) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val selected = remember(date) { LocalDate.parse(date) }
    val vm: AgendaViewModel = viewModel(factory = remember(repository) { AgendaViewModel.Factory(repository) })
    val state by vm.state.collectAsState()
    LaunchedEffect(token, date, period, filter, refresh, revision) {
        vm.refresh(token, date, period, filter)
    }
    if (pickerOpen) {
        val picker = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = selected.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    pickerOpen = false
                }) { Text("Escolher") }
            },
            dismissButton = { TextButton(onClick = { pickerOpen = false }) { Text("Cancelar") } }
        ) { DatePicker(state = picker) }
    }
    val appointments = remember(state.appointments) { state.appointments.sortedBy { it.startsAt } }
    val grouped = remember(appointments) { appointments.groupBy { it.localDate() } }

    Column(
        modifier = Modifier.fillMaxSize().background(BarberBackground).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Olá, barbeiro!",
                    color = BarberTextPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    selected.format(dateTitle).replaceFirstChar { it.titlecase(brazil) },
                    color = BarberTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IconButton(onClick = { pickerOpen = true }) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = "Selecionar data", tint = BarberGold)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                date = when (period) {
                    "week" -> selected.minusWeeks(1)
                    "month" -> selected.minusMonths(1)
                    else -> selected.minusDays(1)
                }.toString()
            }) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Período anterior", tint = BarberGold)
            }
            Text(
                when (period) {
                    "month" -> selected.format(monthTitle)
                    "week" -> {
                        val monday = selected.with(DayOfWeek.MONDAY)
                        monday.format(shortDate) + " — " + monday.plusDays(6).format(shortDate)
                    }
                    else -> selected.format(monthTitle)
                }.replaceFirstChar { it.titlecase(brazil) },
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                color = BarberTextPrimary,
                style = MaterialTheme.typography.titleSmall
            )
            IconButton(onClick = {
                date = when (period) {
                    "week" -> selected.plusWeeks(1)
                    "month" -> selected.plusMonths(1)
                    else -> selected.plusDays(1)
                }.toString()
            }) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "Próximo período", tint = BarberGold)
            }
        }
        if (period != "month") {
            val monday = selected.with(DayOfWeek.MONDAY)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(7) { index ->
                    val day = monday.plusDays(index.toLong())
                    val active = day == selected
                    Surface(
                        onClick = { date = day.toString() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = if (active) BarberGold else BarberSurface,
                        border = if (active) null else BorderStroke(1.dp, BarberOutline)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                day.format(dayLabel).take(3).uppercase(brazil),
                                color = if (active) BarberOnGold else BarberTextSecondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                day.dayOfMonth.toString(),
                                color = if (active) BarberOnGold else BarberTextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                Modifier.size(4.dp).background(
                                    if (day == LocalDate.now(agendaZone)) BarberGold else Color.Transparent,
                                    CircleShape
                                )
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().background(BarberSurface, RoundedCornerShape(12.dp)).padding(4.dp)
        ) {
            listOf("day" to "Hoje", "week" to "Semana", "month" to "Mês").forEach { (key, label) ->
                val active = key == period
                Surface(
                    onClick = {
                        period = key
                        if (key == "day") date = LocalDate.now(agendaZone).toString()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(9.dp),
                    color = if (active) BarberGold else Color.Transparent
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = if (active) BarberOnGold else BarberTextPrimary,
                        textAlign = TextAlign.Center,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "all" to "Todos",
                "SCHEDULED" to "Agendados",
                "IN_PROGRESS" to "Em atendimento",
                "COMPLETED" to "Concluídos",
                "CANCELLED" to "Cancelados"
            ).forEach { (key, label) ->
                Surface(
                    onClick = { filter = key },
                    shape = RoundedCornerShape(50),
                    color = if (key == filter) BarberGold.copy(alpha = 0.16f) else BarberSurface,
                    border = BorderStroke(1.dp, if (key == filter) BarberGold else BarberOutline)
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = if (key == filter) BarberGold else BarberTextSecondary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Atendimentos",
                color = BarberTextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(10.dp))
            Text(appointments.size.toString(), color = BarberGold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { refresh++ }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Atualizar agenda", tint = BarberGold)
            }
            TextButton(onClick = onNew) { Text("+ Agendar", color = BarberGold) }
        }
        if (state.offline) {
            val stamp = state.lastSyncedAt?.let {
                Instant.ofEpochMilli(it).atZone(agendaZone).format(syncFormat)
            } ?: "desconhecida"
            Surface(color = BarberGold.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
                Text(
                    "Offline · Última sincronização: " + stamp + " · somente leitura",
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    color = BarberGold,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (state.loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Carregando agenda...", color = BarberTextSecondary)
            }
        }
        if (state.error.isNotBlank()) {
            Surface(color = BarberSurface, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { refresh++ }) { Text("Tentar novamente") }
                }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (period == "month") {
                item {
                    AgendaMonthGrid(selected, grouped) { date = it.toString() }
                }
            }
            if (!state.loading && state.error.isBlank() && appointments.isEmpty()) {
                item {
                    val message = when (period) {
                        "week" -> "Nenhum agendamento nesta semana."
                        "month" -> "Nenhum agendamento neste mês."
                        else -> "Nenhum agendamento para este dia."
                    }
                    Surface(
                        color = BarberSurface,
                        border = BorderStroke(1.dp, BarberOutline),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            message + " Use + para criar um atendimento.",
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            color = BarberTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            if (period == "day" && appointments.isNotEmpty()) {
                val hours = appointments.map { it.localHour() }
                val first = minOf(8, hours.minOrNull() ?: 8)
                val last = maxOf(20, hours.maxOrNull() ?: 20)
                items((first..last).toList()) { hour ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(
                            "%02d:00".format(brazil, hour),
                            modifier = Modifier.width(54.dp).padding(top = 12.dp),
                            color = BarberTextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val inHour = appointments.filter { it.localHour() == hour }
                            if (inHour.isEmpty()) {
                                Spacer(Modifier.height(22.dp))
                                Box(Modifier.fillMaxWidth().height(1.dp).background(BarberOutline))
                            } else {
                                inHour.forEach { appointment ->
                                    AgendaTimelineCard(appointment) { onDetails(appointment.id) }
                                }
                            }
                        }
                    }
                }
            }
            if (period != "day" && appointments.isNotEmpty()) {
                items(grouped.keys.sorted()) { day ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            day.format(dateTitle).replaceFirstChar { it.titlecase(brazil) },
                            color = if (day == selected) BarberGold else BarberTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        grouped[day].orEmpty().forEach { appointment ->
                            AgendaTimelineCard(appointment) { onDetails(appointment.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaMonthGrid(
    focused: LocalDate,
    appointments: Map<LocalDate, List<AppointmentView>>,
    onSelect: (LocalDate) -> Unit
) {
    val firstCell = focused.withDayOfMonth(1).with(DayOfWeek.MONDAY)
    Column(
        Modifier.fillMaxWidth().background(BarberSurface, RoundedCornerShape(14.dp)).padding(8.dp)
    ) {
        Row {
            listOf("S", "T", "Q", "Q", "S", "S", "D").forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = BarberTextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        repeat(6) { week ->
            Row {
                repeat(7) { index ->
                    val day = firstCell.plusDays((week * 7 + index).toLong())
                    val active = day == focused
                    Surface(
                        onClick = { onSelect(day) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        color = if (active) BarberGold else Color.Transparent
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 5.dp)
                        ) {
                            Text(
                                day.dayOfMonth.toString(),
                                color = when {
                                    active -> BarberOnGold
                                    day.month == focused.month -> BarberTextPrimary
                                    else -> BarberTextSecondary
                                }
                            )
                            Box(
                                Modifier.size(4.dp).background(
                                    if (appointments[day].isNullOrEmpty()) Color.Transparent else BarberGold,
                                    CircleShape
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaTimelineCard(item: AppointmentView, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = BarberSurfaceRaised,
        border = BorderStroke(1.dp, BarberOutline),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = BarberGold.copy(alpha = 0.17f)) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Text(
                        item.clientName.trim().take(1).uppercase(brazil).ifBlank { "?" },
                        color = BarberGold,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    item.clientName,
                    color = BarberTextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.serviceName + " · " + item.durationMinutes + " min",
                    style = MaterialTheme.typography.bodySmall,
                    color = BarberTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.localTime() + " – " + item.localEndTime(),
                    color = BarberGold,
                    style = MaterialTheme.typography.labelMedium
                )
                val accent = statusColor(item.status)
                Surface(color = accent.copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        if (item.status == "IN_PROGRESS") "Em atendimento" else statusText(item.status),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = accent,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Icon(
                Icons.Outlined.ChevronRight,
                tint = BarberTextSecondary,
                contentDescription = "Detalhes do atendimento"
            )
        }
    }
}

private fun AppointmentView.localDate(): LocalDate = Instant.parse(startsAt).atZone(agendaZone).toLocalDate()

private fun AppointmentView.localHour(): Int = Instant.parse(startsAt).atZone(agendaZone).hour

private fun AppointmentView.localTime(): String = Instant.parse(startsAt).atZone(agendaZone).format(hourFormat)

private fun AppointmentView.localEndTime(): String = Instant.parse(endsAt).atZone(agendaZone).format(hourFormat)
