package com.agendabarber.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.agendabarber.app.network.AgendaApi
import com.agendabarber.app.network.WorkingHoursView
import com.agendabarber.app.ui.theme.BarberGold
import com.agendabarber.app.ui.theme.BarberOnGold
import com.agendabarber.app.ui.theme.BarberOutline
import com.agendabarber.app.ui.theme.BarberSurface
import com.agendabarber.app.ui.theme.BarberTextPrimary
import com.agendabarber.app.ui.theme.BarberTextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException

private data class DayEditor(val weekday: Int, val data: WorkingHoursView?)

private fun workingHoursApiError(failure: Exception): String = when ((failure as? HttpException)?.code()) {
    409 -> "Conflito: há agendamentos que impedem essa alteração."
    400 -> "Verifique os horários enviados."
    401, 403 -> "Sessão inválida ou sem permissão."
    else -> "Não foi possível confirmar a operação. Verifique a conexão."
}

@Composable
internal fun BarberWorkingHoursScreen(api: AgendaApi, token: String, onBack: () -> Unit) {
    var hours by remember { mutableStateOf(emptyList<WorkingHoursView>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var revision by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<DayEditor?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(api, token, revision) {
        loading = true
        error = ""
        try {
            hours = api.workingHours(token)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            hours = emptyList()
            error = workingHoursApiError(failure)
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar")
            }
            Text(
                "Horário de funcionamento",
                color = BarberTextPrimary,
                style = MaterialTheme.typography.titleLarge
            )
        }
        Text(
            "Os horários são carregados e salvos na API. Para pausas, use Bloquear horário.",
            color = BarberTextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        if (loading) {
            CircularProgressIndicator(color = BarberGold)
        }
        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { revision++ }) { Text("Tentar novamente") }
        }
        if (message.isNotBlank()) {
            Text(message, color = BarberGold)
        }
        if (!loading && error.isBlank()) {
            scheduleWeekdayNames.forEachIndexed { index, name ->
                val weekday = index + 1
                val current = hours.firstOrNull { it.weekday == weekday }
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        saveError = ""
                        message = ""
                        selected = DayEditor(weekday, current)
                    },
                    colors = CardDefaults.cardColors(containerColor = BarberSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BarberOutline)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(name, color = BarberTextPrimary, fontWeight = FontWeight.Medium)
                            Text(
                                if (current?.enabled == true) {
                                    "${scheduleClock(current.opensAt)} – ${scheduleClock(current.closesAt)}"
                                } else {
                                    "Fechado"
                                },
                                color = BarberTextSecondary
                            )
                        }
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "Editar $name",
                            tint = BarberGold
                        )
                    }
                }
            }
        }
    }

    selected?.let { editor ->
        WorkingHoursEditor(
            name = scheduleWeekdayNames[editor.weekday - 1],
            source = editor.data,
            saving = saving,
            error = saveError,
            onDismiss = { if (!saving) selected = null },
            onSave = { enabled, opens, closes ->
                if (!saving) {
                    saving = true
                    saveError = ""
                    scope.launch {
                        try {
                            api.saveWorkingHours(
                                token,
                                editor.weekday,
                                workingHoursPayload(enabled, opens, closes)
                            )
                            selected = null
                            message = "Expediente atualizado na API."
                            revision++
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            saveError = workingHoursApiError(failure)
                        } finally {
                            saving = false
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun WorkingHoursEditor(
    name: String,
    source: WorkingHoursView?,
    saving: Boolean,
    error: String,
    onDismiss: () -> Unit,
    onSave: (Boolean, String, String) -> Unit
) {
    var enabled by remember(source, name) { mutableStateOf(source?.enabled ?: false) }
    var opens by remember(source, name) {
        mutableStateOf(source?.opensAt?.let(::scheduleClock) ?: "09:00")
    }
    var closes by remember(source, name) {
        mutableStateOf(source?.closesAt?.let(::scheduleClock) ?: "18:00")
    }
    val validation = workingHoursError(enabled, opens, closes)
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Aberto", modifier = Modifier.weight(1f))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        enabled = !saving
                    )
                }
                OutlinedTextField(
                    value = opens,
                    onValueChange = { opens = it.take(5) },
                    label = { Text("Abre às (HH:mm)") },
                    enabled = !saving && enabled,
                    singleLine = true
                )
                OutlinedTextField(
                    value = closes,
                    onValueChange = { closes = it.take(5) },
                    label = { Text("Fecha às (HH:mm)") },
                    enabled = !saving && enabled,
                    singleLine = true
                )
                Text(
                    "Alterar um dia com reservas existentes pode gerar conflito (409).",
                    color = BarberTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                if (validation != null) {
                    Text(validation, color = MaterialTheme.colorScheme.error)
                }
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && validation == null,
                onClick = { onSave(enabled, opens, closes) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BarberGold,
                    contentColor = BarberOnGold
                )
            ) {
                Text(if (saving) "Salvando..." else "Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") }
        }
    )
}
