package com.agendabarber.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.agendabarber.app.network.AgendaApi
import com.agendabarber.app.network.RecurringBlockView
import com.agendabarber.app.network.SingleBlockView
import com.agendabarber.app.ui.theme.BarberGold
import com.agendabarber.app.ui.theme.BarberOnGold
import com.agendabarber.app.ui.theme.BarberSurfaceRaised
import com.agendabarber.app.ui.theme.BarberTextSecondary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException

private enum class BlockDateTarget { START, UNTIL }

private val displayDate = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val reasons = listOf("Almoço", "Compromisso", "Folga", "Feriado", "Outro")

private fun dateLabel(raw: String): String = runCatching {
    LocalDate.parse(raw).format(displayDate)
}.getOrDefault(raw)

private fun dateTimeLabel(raw: String): String = runCatching {
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        .withZone(barberScheduleZone)
        .format(Instant.parse(raw))
}.getOrDefault(raw)

private fun blockError(failure: Exception): String = when ((failure as? HttpException)?.code()) {
    409 -> "Conflito: existe um atendimento ou outro bloqueio nesse horário."
    400 -> "Verifique a data, os horários e a vigência do bloqueio."
    401, 403 -> "A sessão expirou ou não tem permissão para esta ação."
    503 -> "Serviço indisponível. Nenhum bloqueio foi confirmado."
    else -> "Operação não confirmada. Verifique a conexão e tente novamente."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BarberBlockTimeScreen(api: AgendaApi, token: String, onBack: () -> Unit) {
    val today = remember { LocalDate.now(barberScheduleZone) }
    var mode by remember { mutableStateOf(BlockMode.SINGLE) }
    var date by remember { mutableStateOf(today.toString()) }
    var validUntil by remember { mutableStateOf(today.plusDays(28).toString()) }
    var startsAt by remember { mutableStateOf("12:00") }
    var endsAt by remember { mutableStateOf("13:00") }
    var reason by remember { mutableStateOf("Almoço") }
    var notes by remember { mutableStateOf("") }
    var picker by remember { mutableStateOf<BlockDateTarget?>(null) }
    var reasonMenu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var pendingSubmission by remember { mutableStateOf<Pair<Any, String>?>(null) }
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var listError by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var revision by remember { mutableStateOf(0) }
    var singleBlocks by remember { mutableStateOf(emptyList<SingleBlockView>()) }
    var recurringBlocks by remember { mutableStateOf(emptyList<RecurringBlockView>()) }
    var deleting by remember { mutableStateOf<Pair<String, BlockMode>?>(null) }
    var removing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val formError = validateBlockFields(mode, date, startsAt, endsAt, validUntil)

    LaunchedEffect(token, date, revision) {
        loading = true
        listError = ""
        try {
            singleBlocks = api.blocks(token, date)
            recurringBlocks = api.recurringBlocks(token)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            singleBlocks = emptyList()
            recurringBlocks = emptyList()
            listError = blockError(failure)
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.Close, contentDescription = "Fechar bloqueio")
            }
            Text("Bloquear Horário", style = MaterialTheme.typography.titleLarge)
        }

        BarberSegmentedSelector(
            labels = BlockMode.entries.map { it.label },
            selected = BlockMode.entries.indexOf(mode),
            onSelect = {
                mode = BlockMode.entries[it]
                message = ""
                error = ""
            }
        )

        BlockDateField(
            label = if (mode == BlockMode.SINGLE) "Data" else "Início da vigência",
            value = date,
            onSelect = { picker = BlockDateTarget.START },
            enabled = !saving
        )
        if (mode == BlockMode.RECURRING) {
            Text(
                "Repetição semanal: ${runCatching {
                    LocalDate.parse(date).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("pt-BR"))
                }.getOrDefault("-")}",
                color = BarberTextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            BlockDateField(
                label = "Fim da vigência (máximo de 180 dias)",
                value = validUntil,
                onSelect = { picker = BlockDateTarget.UNTIL },
                enabled = !saving
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = startsAt,
                onValueChange = {
                    startsAt = it.take(5)
                    message = ""
                    error = ""
                },
                label = { Text("Início") },
                placeholder = { Text("08:00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = endsAt,
                onValueChange = {
                    endsAt = it.take(5)
                    message = ""
                    error = ""
                },
                label = { Text("Fim") },
                placeholder = { Text("09:00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.weight(1f)
            )
        }

        Column {
            Text("Motivo", style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(reason, modifier = Modifier.weight(1f))
                IconButton(onClick = { reasonMenu = true }, enabled = !saving) {
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Selecionar motivo")
                }
            }
            DropdownMenu(expanded = reasonMenu, onDismissRequest = { reasonMenu = false }) {
                reasons.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            reason = option
                            reasonMenu = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it.take(2000) },
            label = { Text("Observações (opcional)") },
            enabled = !saving,
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Horários de Brasília (America/Sao_Paulo). Bloqueios só são confirmados online.",
            color = BarberTextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        if (formError != null) {
            Text(formError, color = MaterialTheme.colorScheme.error)
        }
        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        if (message.isNotBlank()) {
            Text(message, color = BarberGold)
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving && formError == null,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BarberGold,
                contentColor = BarberOnGold
            ),
            onClick = {
                val single = if (mode == BlockMode.SINGLE) {
                    oneTimeBlockPayload(date, startsAt, endsAt, reason, notes)
                } else {
                    null
                }
                val recurring = if (mode == BlockMode.RECURRING) {
                    recurringBlockPayload(date, startsAt, endsAt, validUntil, reason, notes)
                } else {
                    null
                }
                val payload = checkNotNull(single ?: recurring)
                val key = pendingSubmission?.takeIf { it.first == payload }?.second
                    ?: UUID.randomUUID().toString().also { pendingSubmission = payload to it }
                saving = true
                error = ""
                message = ""
                scope.launch {
                    try {
                        if (single != null) {
                            api.createBlock(token, key, single)
                        } else {
                            api.addWeekly(token, key, checkNotNull(recurring))
                        }
                        pendingSubmission = null
                        message = "Bloqueio confirmado."
                        revision++
                    } catch (failure: Exception) {
                        error = blockError(failure)
                    } finally {
                        saving = false
                    }
                }
            }
        ) {
            if (saving) {
                CircularProgressIndicator(color = BarberOnGold)
            } else {
                Text("Bloquear horário", fontWeight = FontWeight.Bold)
            }
        }

        Text("Bloqueios da data", style = MaterialTheme.typography.titleMedium)
        if (loading) CircularProgressIndicator(color = BarberGold)
        if (listError.isNotBlank()) {
            Text(listError, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { revision++ }) { Text("Tentar novamente") }
        }
        if (!loading && listError.isBlank() && singleBlocks.isEmpty()) {
            Text("Nenhum bloqueio único nesta data.", color = BarberTextSecondary)
        }
        singleBlocks.forEach { item ->
            BlockCard(
                title = item.reason ?: "Bloqueio",
                detail = "${dateTimeLabel(item.startsAt)} – ${dateTimeLabel(item.endsAt)}",
                notes = item.notes,
                onRemove = { deleting = item.id to BlockMode.SINGLE }
            )
        }

        Text("Bloqueios recorrentes", style = MaterialTheme.typography.titleMedium)
        if (!loading && listError.isBlank() && recurringBlocks.isEmpty()) {
            Text("Nenhuma regra recorrente.", color = BarberTextSecondary)
        }
        recurringBlocks.forEach { item ->
            BlockCard(
                title = item.reason ?: "Bloqueio recorrente",
                detail = "Dia ${item.weekday} • ${item.startsAt}–${item.endsAt} • " +
                    "${dateLabel(item.validFrom)} até ${dateLabel(item.validUntil)}",
                notes = item.notes,
                onRemove = { deleting = item.id to BlockMode.RECURRING }
            )
        }
    }

    picker?.let { target ->
        val current = if (target == BlockDateTarget.START) date else validUntil
        val millis = LocalDate.parse(current).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(
            onDismissRequest = { picker = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { selected ->
                        val chosen = Instant.ofEpochMilli(selected)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .toString()
                        if (target == BlockDateTarget.START) date = chosen else validUntil = chosen
                        message = ""
                        error = ""
                    }
                    picker = null
                }) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { picker = null }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    deleting?.let { (id, type) ->
        AlertDialog(
            onDismissRequest = { if (!removing) deleting = null },
            title = { Text("Desbloquear horário?") },
            text = { Text("O bloqueio será excluído após confirmação da API.") },
            confirmButton = {
                TextButton(
                    enabled = !removing,
                    onClick = {
                        removing = true
                        error = ""
                        scope.launch {
                            try {
                                if (type == BlockMode.SINGLE) {
                                    api.deleteBlock(token, id)
                                } else {
                                    api.deleteRecurringBlock(token, id)
                                }
                                deleting = null
                                message = "Bloqueio removido."
                                revision++
                            } catch (failure: Exception) {
                                error = blockError(failure)
                            } finally {
                                removing = false
                            }
                        }
                    }
                ) { Text(if (removing) "Removendo..." else "Desbloquear") }
            },
            dismissButton = {
                TextButton(
                    enabled = !removing,
                    onClick = { deleting = null }
                ) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun BlockDateField(label: String, value: String, onSelect: () -> Unit, enabled: Boolean) {
    OutlinedTextField(
        value = dateLabel(value),
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = onSelect, enabled = enabled) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = "Selecionar $label")
            }
        }
    )
}

@Composable
private fun BlockCard(title: String, detail: String, notes: String?, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BarberSurfaceRaised)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, color = BarberTextSecondary)
            if (!notes.isNullOrBlank()) {
                Text(notes, color = BarberTextSecondary)
            }
            TextButton(onClick = onRemove) { Text("Desbloquear", color = BarberGold) }
        }
    }
}
