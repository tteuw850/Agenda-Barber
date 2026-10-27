package com.agendabarber.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agendabarber.app.network.AgendaApi
import com.agendabarber.app.network.AgendaApiFactory
import com.agendabarber.app.network.AppointmentView
import com.agendabarber.app.network.ClientView
import com.agendabarber.app.network.CreateAppointment
import com.agendabarber.app.network.CreateClient
import com.agendabarber.app.network.HistoryItem
import com.agendabarber.app.network.LoginRequest
import com.agendabarber.app.network.ServiceView
import com.agendabarber.app.network.SlotView
import com.agendabarber.app.servicecatalog.formatBrl
import com.agendabarber.app.ui.theme.BarberCancelAction
import com.agendabarber.app.ui.theme.BarberCompleted
import com.agendabarber.app.ui.theme.BarberInProgress
import com.agendabarber.app.ui.theme.BarberScheduled
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun AgendaApp() {
    var url by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var token by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentToken = token
    if (currentToken == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("AGENDA BARBER", style = MaterialTheme.typography.headlineLarge)
            Text("Acesso exclusivo do barbeiro")
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Endereço HTTPS da API") },
                placeholder = { Text("https://api.exemplo.com/") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("E-mail") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Senha") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            Button(
                enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val client = AgendaApiFactory.create(url)
                            token = "Bearer " + client.login(LoginRequest(email.trim(), password)).accessToken
                            password = ""
                            error = ""
                        } catch (e: IllegalArgumentException) {
                            error = "Informe um endereço HTTPS válido."
                        } catch (e: Exception) {
                            error = apiMessage(e)
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text(if (busy) "Conectando..." else "Entrar") }
            Text("Login e senha não são salvos no dispositivo. A API precisa estar publicada.")
        }
        return
    }
    val api = remember(url) { AgendaApiFactory.create(url) }
    BarberAuthenticatedApp(api, currentToken, onLogout = { token = null })
}

@Composable
internal fun AppointmentDetails(api: AgendaApi, token: String, id: String, onBack: () -> Unit) {
    var detail by remember(id) { mutableStateOf<AppointmentView?>(null) }
    var events by remember(id) { mutableStateOf(emptyList<HistoryItem>()) }
    var revision by remember(id) { mutableStateOf(0) }
    var busy by remember(id) { mutableStateOf(false) }
    var editing by remember(id) { mutableStateOf(false) }
    var notes by remember(id) { mutableStateOf("") }
    var message by remember(id) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id, revision) {
        try {
            val updated = api.appointment(token, id)
            detail = updated
            notes = updated.notes.orEmpty()
            events = api.history(token, id)
            message = ""
        } catch (e: Exception) {
            message = apiMessage(e)
        }
    }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        TextButton(onClick = onBack) { Text("‹ Voltar") }
        Text("Detalhes do atendimento", style = MaterialTheme.typography.headlineSmall)
        val item = detail
        if (item == null) {
            if (message.isBlank()) CircularProgressIndicator()
        } else {
            Text(item.clientName, style = MaterialTheme.typography.titleLarge)
            Text(item.clientPhone ?: "Telefone não informado")
            Text(item.serviceName + " • " + item.durationMinutes + " min")
            Text(displayTime(item.startsAt) + " — " + displayTime(item.endsAt))
            Text(formatBrl(item.price), style = MaterialTheme.typography.titleLarge)
            Text(statusText(item.status), color = statusColor(item.status))
            Text("Observações: " + item.notes.orEmpty())
            Text("Versão " + item.version)
            if (item.status == "SCHEDULED") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            try {
                                api.start(token, id)
                                revision++
                            } catch (e: Exception) {
                                message = apiMessage(e)
                            } finally {
                                busy = false
                            }
                        }
                    }) { Text("Iniciar") }
                    OutlinedButton(onClick = { editing = !editing }) { Text("Editar") }
                }
                if (editing) {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Observações") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            try {
                                api.edit(token, id, mapOf("notes" to notes))
                                editing = false
                                revision++
                            } catch (e: Exception) {
                                message = apiMessage(e)
                            } finally {
                                busy = false
                            }
                        }
                    }) { Text("Salvar alterações") }
                }
            }
            if (item.status == "IN_PROGRESS") {
                Button(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            api.complete(token, id)
                            revision++
                        } catch (e: Exception) {
                            message = apiMessage(e)
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Concluir") }
            }
            if (item.status == "SCHEDULED" || item.status == "IN_PROGRESS") {
                OutlinedButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            api.cancel(token, id)
                            revision++
                        } catch (e: Exception) {
                            message = apiMessage(e)
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Cancelar", color = BarberCancelAction) }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("Histórico", style = MaterialTheme.typography.titleMedium)
            events.forEach { event ->
                Text(
                    displayTime(event.happenedAt) + " • " +
                        event.eventType + " → " + statusText(event.newStatus)
                )
            }
        }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
internal fun BookingForm(api: AgendaApi, token: String, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    var query by remember { mutableStateOf("") }
    var clients by remember { mutableStateOf(emptyList<ClientView>()) }
    var services by remember { mutableStateOf(emptyList<ServiceView>()) }
    var slots by remember { mutableStateOf(emptyList<SlotView>()) }
    var clientId by remember { mutableStateOf("") }
    var serviceId by remember { mutableStateOf("") }
    var startsAt by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var key by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var createClient by remember { mutableStateOf(false) }
    var clientName by remember { mutableStateOf("") }
    var clientPhone by remember { mutableStateOf("") }

    LaunchedEffect(token) {
        try {
            services = api.services(token)
        } catch (e: Exception) {
            message = apiMessage(e)
        }
    }
    LaunchedEffect(token, query) {
        try {
            clients = api.clients(token, query)
        } catch (e: Exception) {
            message = apiMessage(e)
        }
    }
    LaunchedEffect(token, day, serviceId) {
        slots = emptyList()
        startsAt = ""
        if (serviceId.isNotBlank() && validDate(day)) {
            try {
                slots = api.slots(token, day, serviceId)
            } catch (e: Exception) {
                message = apiMessage(e)
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Novo Agendamento", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = day,
            onValueChange = {
                day = it
                key = UUID.randomUUID().toString()
            },
            label = { Text("Data AAAA-MM-DD") },
            isError = !validDate(day),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar cliente por nome/telefone") },
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(onClick = { createClient = !createClient }) { Text("+ Novo cliente") }
        if (createClient) {
            OutlinedTextField(
                value = clientName,
                onValueChange = { clientName = it },
                label = { Text("Nome obrigatório") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = clientPhone,
                onValueChange = { clientPhone = it },
                label = { Text("Telefone opcional") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(enabled = !busy && clientName.isNotBlank(), onClick = {
                busy = true
                scope.launch {
                    try {
                        val created = api.newClient(
                            token,
                            CreateClient(clientName.trim(), clientPhone.ifBlank { null })
                        )
                        clientId = created.id
                        clients = listOf(created)
                        createClient = false
                        key = UUID.randomUUID().toString()
                        message = "Cliente salvo."
                    } catch (e: Exception) {
                        message = apiMessage(e)
                    } finally {
                        busy = false
                    }
                }
            }) { Text("Salvar cliente") }
        }
        Text("Cliente", style = MaterialTheme.typography.titleMedium)
        clients.forEach { customer ->
            FilterChip(
                selected = clientId == customer.id,
                onClick = {
                    clientId = customer.id
                    key = UUID.randomUUID().toString()
                },
                label = { Text(customer.name + " " + customer.phone.orEmpty()) }
            )
        }
        HorizontalDivider()
        Text("Serviço", style = MaterialTheme.typography.titleMedium)
        services.forEach { service ->
            FilterChip(
                selected = serviceId == service.id,
                onClick = {
                    serviceId = service.id
                    price = service.defaultPrice.toPlainString()
                    key = UUID.randomUUID().toString()
                },
                label = {
                    Text(
                        service.name + " • " + service.durationMinutes +
                            " min • " + formatBrl(service.defaultPrice)
                    )
                }
            )
        }
        Text("Horários disponíveis", style = MaterialTheme.typography.titleMedium)
        if (serviceId.isNotBlank() && slots.isEmpty()) Text("Nenhum horário livre nesta data.")
        slots.forEach { available ->
            FilterChip(
                selected = startsAt == available.startsAt,
                onClick = {
                    startsAt = available.startsAt
                    key = UUID.randomUUID().toString()
                },
                label = { Text(available.localStart) }
            )
        }
        OutlinedTextField(
            value = price,
            onValueChange = {
                price = it
                key = UUID.randomUUID().toString()
            },
            label = { Text("Valor (ex.: 45.50)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = notes,
            onValueChange = {
                notes = it
                key = UUID.randomUUID().toString()
            },
            label = { Text("Observações") },
            modifier = Modifier.fillMaxWidth()
        )
        if (message.isNotBlank()) Text(message)
        val validPrice = runCatching {
            BigDecimal(price).let { it.signum() >= 0 && it.scale() <= 2 }
        }.getOrDefault(false)
        val canBook = listOf(
            !busy,
            clientId.isNotBlank(),
            serviceId.isNotBlank(),
            startsAt.isNotBlank(),
            validPrice
        ).all { it }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = canBook,
            onClick = {
                busy = true
                message = ""
                scope.launch {
                    try {
                        api.book(
                            token,
                            key,
                            CreateAppointment(
                                clientId,
                                serviceId,
                                startsAt,
                                BigDecimal(price),
                                notes
                            )
                        )
                        onSaved()
                    } catch (e: Exception) {
                        message = apiMessage(e)
                    } finally {
                        busy = false
                    }
                }
            }
        ) { Text(if (busy) "Salvando..." else "Confirmar agendamento") }
        Spacer(Modifier.height(24.dp))
    }
}

internal fun statusColor(status: String): Color = when (status) {
    "COMPLETED" -> BarberCompleted
    "IN_PROGRESS" -> BarberInProgress
    "CANCELLED" -> BarberCancelAction
    else -> BarberScheduled
}
