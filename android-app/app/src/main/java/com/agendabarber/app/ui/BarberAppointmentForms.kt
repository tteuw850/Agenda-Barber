package com.agendabarber.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agendabarber.app.network.AgendaApi
import com.agendabarber.app.network.AppointmentView
import com.agendabarber.app.network.ClientView
import com.agendabarber.app.network.CompleteAppointment
import com.agendabarber.app.network.CreateAppointment
import com.agendabarber.app.network.HistoryItem
import com.agendabarber.app.network.ServiceView
import com.agendabarber.app.network.SlotView
import com.agendabarber.app.servicecatalog.formatBrl
import com.agendabarber.app.ui.theme.BarberBackground
import com.agendabarber.app.ui.theme.BarberCancelAction
import com.agendabarber.app.ui.theme.BarberGold
import com.agendabarber.app.ui.theme.BarberOnGold
import com.agendabarber.app.ui.theme.BarberOutline
import com.agendabarber.app.ui.theme.BarberStartAction
import com.agendabarber.app.ui.theme.BarberSurface
import com.agendabarber.app.ui.theme.BarberSurfaceRaised
import com.agendabarber.app.ui.theme.BarberTextPrimary
import com.agendabarber.app.ui.theme.BarberTextSecondary
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch
import retrofit2.HttpException

private val uiZone = ZoneId.of("America/Sao_Paulo")
private val ptBr = Locale.forLanguageTag("pt-BR")
private val displayDate = DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", ptBr)
private val displayClock = DateTimeFormatter.ofPattern("HH:mm", ptBr)
private val displayHistory = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", ptBr)
private fun localClock(iso: String): String =
    runCatching { Instant.parse(iso).atZone(uiZone).format(displayClock) }.getOrDefault(iso)

@Composable
internal fun BarberAppointmentDetails(
    api: AgendaApi,
    token: String,
    id: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    refreshSignal: Long = 0L
) {
    var appointment by remember(id) { mutableStateOf<AppointmentView?>(null) }
    var history by remember(id) { mutableStateOf(emptyList<HistoryItem>()) }
    var selectedTab by rememberSaveable(id) { mutableIntStateOf(0) }
    var revision by remember(id) { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var confirmCancel by remember { mutableStateOf(false) }
    var confirmComplete by remember { mutableStateOf(false) }
    var receivedInput by remember(id) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(id, token, revision, refreshSignal) {
        try {
            appointment = api.appointment(token, id)
            history = api.history(token, id)
            error = ""
        } catch (failure: Exception) {
            error = apiMessage(failure)
        }
    }
    fun refreshAfterAction() {
        revision++
    }
    if (confirmComplete) {
        val amount = parseMoney(receivedInput)
        AlertDialog(
            onDismissRequest = { if (!busy) confirmComplete = false },
            title = { Text("Concluir atendimento") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Preço agendado: " + formatBrl(appointment?.price ?: BigDecimal.ZERO))
                    Text("Informe o valor realmente recebido. Ele será registrado no financeiro.")
                    OutlinedTextField(
                        value = receivedInput,
                        onValueChange = { receivedInput = it.take(14) },
                        label = { Text("Valor recebido (R$)") },
                        placeholder = { Text("0,00") },
                        singleLine = true,
                        enabled = !busy
                    )
                    if (amount == null) {
                        Text("Informe um valor válido, com até duas casas decimais.")
                    }
                    if (error.isNotBlank()) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && amount != null,
                    onClick = {
                        val confirmed = amount ?: return@TextButton
                        busy = true
                        scope.launch {
                            try {
                                appointment = api.complete(token, id, CompleteAppointment(confirmed))
                                confirmComplete = false
                                refreshAfterAction()
                            } catch (failure: Exception) {
                                error = apiMessage(failure)
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text(if (busy) "Salvando..." else "Confirmar recebimento", color = BarberGold) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { confirmComplete = false }) {
                    Text("Voltar")
                }
            }
        )
    }
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Cancelar agendamento?") },
            text = { Text("O horário será liberado e a ação aparecerá no histórico.") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        confirmCancel = false
                        busy = true
                        scope.launch {
                            try {
                                appointment = api.cancel(token, id)
                                refreshAfterAction()
                            } catch (failure: Exception) {
                                error = apiMessage(failure)
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text("Confirmar", color = BarberCancelAction) }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Voltar") } }
        )
    }
    Scaffold(
        containerColor = BarberBackground,
        bottomBar = {
            val current = appointment
            if (selectedTab == 0 && current != null && current.status in listOf("SCHEDULED", "IN_PROGRESS")) {
                Column(
                    Modifier.background(BarberBackground).imePadding()
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = BarberStartAction),
                        onClick = {
                            if (current.status == "IN_PROGRESS") {
                                receivedInput = current.price.toPlainString()
                                error = ""
                                confirmComplete = true
                            } else {
                                busy = true
                                scope.launch {
                                    try {
                                        appointment = api.start(token, id)
                                        refreshAfterAction()
                                    } catch (failure: Exception) {
                                        error = apiMessage(failure)
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        }
                    ) {
                        Text(
                            if (current.status == "SCHEDULED") "Iniciar atendimento" else "Concluir atendimento",
                            color = Color.White
                        )
                    }
                    if (current.status == "SCHEDULED") {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, BarberTextPrimary),
                            enabled = !busy,
                            onClick = onEdit
                        ) { Text("Editar agendamento", color = BarberTextPrimary) }
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, BarberCancelAction),
                        enabled = !busy,
                        onClick = { confirmCancel = true }
                    ) { Text("Cancelar agendamento", color = BarberCancelAction) }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TextButton(onClick = onBack) { Text("‹ Voltar", color = BarberGold) }
            Text(
                "Detalhes do agendamento",
                color = BarberTextPrimary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            val current = appointment
            if (current == null) CircularProgressIndicator(color = BarberGold)
            if (current != null) {
                Surface(color = BarberSurfaceRaised, shape = RoundedCornerShape(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(shape = CircleShape, color = BarberGold.copy(alpha = 0.18f)) {
                            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    current.clientName.trim().take(1).uppercase(ptBr).ifBlank {
                                        "?"
                                    },
                                    color = BarberGold
                                )
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(current.clientName, color = BarberTextPrimary, fontWeight = FontWeight.Bold)
                            Text(current.clientPhone ?: "Telefone não informado", color = BarberTextSecondary)
                            Text(statusText(current.status), color = statusColor(current.status))
                        }
                        if (!current.clientPhone.isNullOrBlank()) {
                            IconButton(onClick = {
                                val phone = current.clientPhone.filter { it.isDigit() || it == '+' }
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                            }) {
                                Icon(Icons.Outlined.Call, contentDescription = "Ligar para cliente", tint = BarberGold)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    listOf("Detalhes", "Histórico").forEachIndexed { i, tab ->
                        Column(Modifier.weight(1f)) {
                            Surface(onClick = { selectedTab = i }, color = Color.Transparent) {
                                Text(
                                    tab,
                                    Modifier.fillMaxWidth().padding(vertical = 13.dp),
                                    textAlign = TextAlign.Center,
                                    color = if (selectedTab == i) BarberGold else BarberTextSecondary
                                )
                            }
                            Box(
                                Modifier.fillMaxWidth().height(2.dp).background(
                                    if (selectedTab == i) BarberGold else BarberOutline
                                )
                            )
                        }
                    }
                }
                if (selectedTab == 0) {
                    DetailRow(
                        Icons.Outlined.CalendarMonth,
                        "Data",
                        runCatching {
                            Instant.parse(current.startsAt).atZone(uiZone).toLocalDate().format(displayDate)
                        }.getOrDefault(current.startsAt)
                    )
                    DetailRow(
                        Icons.Outlined.AccessTime,
                        "Horário",
                        localClock(current.startsAt) + " – " + localClock(current.endsAt) +
                            " · " + current.durationMinutes + " min"
                    )
                    DetailRow(Icons.Outlined.ContentCut, "Serviço", current.serviceName)
                    DetailRow(Icons.Outlined.AttachMoney, "Preço agendado", formatBrl(current.price))
                    if (current.status == "COMPLETED") {
                        DetailRow(
                            Icons.Outlined.AttachMoney,
                            "Valor recebido",
                            current.receivedAmount?.let { formatBrl(it) } ?: "Não informado (histórico)"
                        )
                    }
                    DetailRow(
                        Icons.Outlined.Notes,
                        "Observações",
                        current.notes?.ifBlank { "Sem observações" } ?: "Sem observações"
                    )
                } else if (history.isEmpty()) {
                    Text("Sem eventos registrados.", color = BarberTextSecondary)
                } else {
                    history.forEach { event ->
                        Surface(shape = RoundedCornerShape(12.dp), color = BarberSurface) {
                            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                Text(event.eventType, color = BarberTextPrimary)
                                Text(
                                    runCatching {
                                        Instant.parse(event.happenedAt).atZone(uiZone).format(displayHistory)
                                    }.getOrDefault(event.happenedAt),
                                    color = BarberTextSecondary
                                )
                                Text(
                                    (event.previousStatus?.let { statusText(it) + " → " } ?: "") +
                                        statusText(event.newStatus),
                                    color = BarberGold
                                )
                            }
                        }
                    }
                }
            }
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = BarberGold)
            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { refreshAfterAction() }) { Text("Tentar novamente", color = BarberGold) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String) {
    Surface(
        color = BarberSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BarberOutline)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, tint = BarberGold)
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium, color = BarberTextSecondary)
                Text(value, color = BarberTextPrimary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BarberBookingForm(
    api: AgendaApi,
    token: String,
    onSaved: () -> Unit,
    onNewClient: () -> Unit,
    newClientId: String = "",
    newClientName: String = "",
    editId: String? = null
) {
    val scope = rememberCoroutineScope()
    var date by rememberSaveable(editId) { mutableStateOf(LocalDate.now(uiZone).toString()) }
    var search by rememberSaveable(editId) { mutableStateOf("") }
    var clientId by rememberSaveable(editId) { mutableStateOf("") }
    var serviceId by rememberSaveable(editId) { mutableStateOf("") }
    var selectedStart by rememberSaveable(editId) { mutableStateOf("") }
    var price by rememberSaveable(editId) { mutableStateOf("") }
    var notes by rememberSaveable(editId) { mutableStateOf("") }
    var key by rememberSaveable(editId) { mutableStateOf(UUID.randomUUID().toString()) }
    var clients by remember { mutableStateOf(emptyList<ClientView>()) }
    var services by remember { mutableStateOf(emptyList<ServiceView>()) }
    var slots by remember { mutableStateOf(emptyList<SlotView>()) }
    var original by remember(editId) { mutableStateOf<AppointmentView?>(null) }
    var loading by remember { mutableStateOf(editId != null) }
    var busy by remember { mutableStateOf(false) }
    var slotsLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var conflict by remember { mutableStateOf(false) }
    var pickerVisible by remember { mutableStateOf(false) }
    var reloadSlots by remember { mutableIntStateOf(0) }
    val priceValid = remember(price) {
        runCatching {
            val amount = BigDecimal(price.replace(",", "."))
            amount.signum() >= 0 && amount.scale() <= 2 && amount.precision() - amount.scale() <= 10
        }.getOrDefault(false)
    }
    LaunchedEffect(token, editId) {
        try {
            services = api.services(token)
            if (editId != null) {
                val existing = api.appointment(token, editId)
                original = existing
                date = Instant.parse(existing.startsAt).atZone(uiZone).toLocalDate().toString()
                search = existing.clientName
                clientId = existing.clientId
                serviceId = existing.serviceId
                selectedStart = existing.startsAt
                price = existing.price.toPlainString()
                notes = existing.notes.orEmpty()
            }
        } catch (failure: Exception) {
            error = apiMessage(failure)
        } finally {
            loading = false
        }
    }
    LaunchedEffect(token, search, newClientId) {
        try {
            val found = api.clients(token, search, 50)
            clients = if (newClientId.isNotEmpty() && found.none { it.id == newClientId }) {
                listOf(ClientView(newClientId, newClientName.ifBlank { "Novo cliente" }, null)) + found
            } else {
                found
            }
        } catch (failure: Exception) {
            error = apiMessage(failure)
        }
    }
    LaunchedEffect(newClientId, newClientName) {
        if (newClientId.isNotBlank()) {
            clientId = newClientId
            search = newClientName
            key = UUID.randomUUID().toString()
        }
    }
    LaunchedEffect(token, date, serviceId, reloadSlots) {
        if (serviceId.isNotBlank() && validDate(date)) {
            slotsLoading = true
            try {
                val available = api.slots(token, date, serviceId)
                val old = original
                val keepExisting = old != null &&
                    old.serviceId == serviceId &&
                    Instant.parse(old.startsAt).atZone(uiZone).toLocalDate().toString() == date
                slots = if (keepExisting && available.none { it.startsAt == old!!.startsAt }) {
                    listOf(SlotView(old!!.startsAt, old.endsAt, localClock(old.startsAt))) + available
                } else {
                    available
                }
            } catch (failure: Exception) {
                error = apiMessage(failure)
            } finally {
                slotsLoading = false
            }
        } else {
            slots = emptyList()
        }
    }
    if (pickerVisible) {
        val picker = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickerVisible = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let {
                        val chosen = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        if (chosen != date) {
                            date = chosen
                            selectedStart = ""
                            key = UUID.randomUUID().toString()
                        }
                    }
                    pickerVisible = false
                }) { Text("Escolher", color = BarberGold) }
            },
            dismissButton = { TextButton(onClick = { pickerVisible = false }) { Text("Cancelar") } }
        ) { DatePicker(state = picker) }
    }
    val valid = !busy &&
        !loading &&
        !slotsLoading &&
        clientId.isNotBlank() &&
        serviceId.isNotBlank() &&
        selectedStart.isNotBlank() &&
        priceValid &&
        notes.length <= 2000
    Scaffold(
        containerColor = BarberBackground,
        bottomBar = {
            Button(
                onClick = {
                    busy = true
                    conflict = false
                    error = ""
                    scope.launch {
                        try {
                            val amount = BigDecimal(price.replace(",", "."))
                            if (editId == null) {
                                api.book(
                                    token,
                                    key,
                                    CreateAppointment(
                                        clientId,
                                        serviceId,
                                        selectedStart,
                                        amount,
                                        notes.ifBlank {
                                            null
                                        }
                                    )
                                )
                            } else {
                                api.edit(
                                    token,
                                    editId,
                                    mapOf(
                                        "clientId" to clientId,
                                        "serviceId" to serviceId,
                                        "startsAt" to selectedStart,
                                        "price" to amount,
                                        "notes" to notes
                                    )
                                )
                            }
                            onSaved()
                        } catch (failure: Exception) {
                            conflict = (failure as? HttpException)?.code() == 409
                            error = if (conflict) {
                                "Conflito 409: horário ocupado ou agendamento alterado. Selecione outro horário."
                            } else {
                                apiMessage(failure)
                            }
                            if (conflict) {
                                selectedStart = ""
                                reloadSlots++
                            }
                            // Same key is kept for a retry with an unchanged request after a transport failure.
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 18.dp, vertical = 10.dp)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BarberGold)
            ) {
                Text(
                    if (busy) {
                        "Salvando..."
                    } else if (editId != null) {
                        "Salvar alterações"
                    } else {
                        "Salvar agendamento"
                    },
                    color = BarberOnGold,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(Modifier.height(6.dp))
            Text("Cliente", color = BarberTextPrimary, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = {
                        search = it
                        clientId = ""
                        key = UUID.randomUUID().toString()
                    },
                    label = { Text("Buscar cliente") },
                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Surface(onClick = onNewClient, color = BarberGold, shape = RoundedCornerShape(12.dp)) {
                    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                        Text("+", color = BarberOnGold, style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                clients.forEach { client ->
                    SelectionChip(client.name, client.id == clientId) {
                        clientId = client.id
                        search = client.name
                        key = UUID.randomUUID().toString()
                    }
                }
            }
            Text("Data", color = BarberTextPrimary, style = MaterialTheme.typography.titleMedium)
            Surface(
                onClick = { pickerVisible = true },
                color = BarberSurface,
                border = BorderStroke(1.dp, BarberOutline),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CalendarMonth, null, tint = BarberGold)
                    Spacer(Modifier.width(12.dp))
                    Text(LocalDate.parse(date).format(displayDate), color = BarberTextPrimary)
                }
            }
            Text("Horário", color = BarberTextPrimary, style = MaterialTheme.typography.titleMedium)
            if (serviceId.isEmpty()) Text("Escolha um serviço para consultar horários.", color = BarberTextSecondary)
            if (slotsLoading) CircularProgressIndicator(Modifier.size(18.dp), color = BarberGold)
            if (!slotsLoading && serviceId.isNotEmpty() && slots.isEmpty()) {
                Text("Nenhum horário livre nesta data.", color = BarberTextSecondary)
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                slots.forEach { slot ->
                    SelectionChip(localClock(slot.startsAt), selectedStart == slot.startsAt) {
                        selectedStart = slot.startsAt
                        key = UUID.randomUUID().toString()
                    }
                }
            }
            Text("Serviço", color = BarberTextPrimary, style = MaterialTheme.typography.titleMedium)
            services.forEach { service ->
                Surface(
                    onClick = {
                        serviceId = service.id
                        selectedStart = ""
                        price = service.defaultPrice.toPlainString()
                        key = UUID.randomUUID().toString()
                    },
                    color = if (service.id == serviceId) BarberGold.copy(alpha = 0.15f) else BarberSurface,
                    border = BorderStroke(1.dp, if (service.id == serviceId) BarberGold else BarberOutline),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ContentCut, null, tint = BarberGold)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(service.name, color = BarberTextPrimary)
                            Text(service.durationMinutes.toString() + " min", color = BarberTextSecondary)
                        }
                        Text(formatBrl(service.defaultPrice), color = BarberGold)
                    }
                }
            }
            Text("Valor", color = BarberTextPrimary, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = price,
                onValueChange = {
                    price = it
                    key = UUID.randomUUID().toString()
                },
                label = { Text("Valor (R$)") },
                leadingIcon = { Icon(Icons.Outlined.AttachMoney, null) },
                isError = price.isNotBlank() && !priceValid,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (price.isNotBlank() && !priceValid) {
                Text("Valor inválido; use até duas casas decimais.", color = MaterialTheme.colorScheme.error)
            }
            Text("Observações", color = BarberTextPrimary, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = notes,
                onValueChange = {
                    notes = it
                    key = UUID.randomUUID().toString()
                },
                label = { Text("Observações (opcional)") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
                isError = notes.length > 2000
            )
            if (error.isNotBlank()) {
                Surface(color = BarberSurfaceRaised, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        if (conflict) {
                            TextButton(onClick = { reloadSlots++ }) {
                                Text("Atualizar horários", color = BarberGold)
                            }
                        }
                    }
                }
            }
            if (loading) CircularProgressIndicator(color = BarberGold)
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun SelectionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) BarberGold else BarberSurface,
        shape = RoundedCornerShape(50),
        border = if (selected) null else BorderStroke(1.dp, BarberOutline)
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            color = if (selected) BarberOnGold else BarberTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
