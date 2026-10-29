package com.agendabarber.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.agendabarber.app.network.AgendaApi
import com.agendabarber.app.network.ServiceView
import com.agendabarber.app.network.ServiceWriteRequest
import com.agendabarber.app.servicecatalog.formatBrl
import com.agendabarber.app.ui.theme.BarberGold
import com.agendabarber.app.ui.theme.BarberOnGold
import com.agendabarber.app.ui.theme.BarberSurface
import com.agendabarber.app.ui.theme.BarberTextSecondary
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal enum class BarberServiceIcon(val label: String) {
    SCISSORS("Corte"), BEARD("Barba"), COMBO("Corte + Barba"),
    EYEBROW("Sobrancelha"), CHILD("Infantil"), OTHER("Outro")
}

internal fun servicePriceInput(raw: String): BigDecimal? {
    val value = raw.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
    return value.takeIf { it.signum() >= 0 && it.scale() <= 2 && it.precision() <= 12 }
}

internal fun serviceFormValid(name: String, duration: String, price: String): Boolean {
    val minutes = duration.toIntOrNull() ?: return false
    return name.trim().isNotEmpty() && name.trim().length <= 150 &&
        minutes in 1..480 && servicePriceInput(price) != null
}

private fun iconFor(key: String): ImageVector = when (key) {
    "BEARD" -> Icons.Outlined.Face
    "COMBO" -> Icons.Outlined.AutoAwesome
    "EYEBROW" -> Icons.Outlined.Spa
    "CHILD" -> Icons.Outlined.ChildCare
    "OTHER" -> Icons.Outlined.MoreVert
    else -> Icons.Outlined.ContentCut
}

@Composable
internal fun BarberServicesCatalogScreen(api: AgendaApi, token: String, onBack: () -> Unit) {
    var services by remember { mutableStateOf(emptyList<ServiceView>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var revision by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<ServiceView?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<ServiceView?>(null) }
    var changingId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(api, token, revision) {
        loading = true
        error = ""
        try {
            services = api.services(token, activeOnly = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "Não foi possível carregar os serviços. Verifique sua conexão."
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar")
            }
            Text("Serviços", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    editing = null
                    formError = ""
                    editorOpen = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BarberGold, contentColor = BarberOnGold
                )
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Novo")
            }
        }
        if (loading) CircularProgressIndicator(color = BarberGold)
        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { revision++ }) { Text("Tentar novamente") }
        }
        if (!loading && error.isBlank() && services.isEmpty()) {
            Text("Nenhum serviço cadastrado.", color = BarberTextSecondary)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(services, key = { it.id }) { service ->
                var menuOpen by remember(service.id) { mutableStateOf(false) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(46.dp).background(BarberSurface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(iconFor(service.iconKey), contentDescription = null, tint = BarberGold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(service.name)
                            Text("${service.durationMinutes} min", color = BarberTextSecondary)
                            if (!service.active) Text("Inativo", color = BarberTextSecondary)
                        }
                        Text(formatBrl(service.defaultPrice), color = BarberGold)
                        Box {
                            IconButton(
                                onClick = { menuOpen = true },
                                enabled = changingId == null
                            ) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "Opções do serviço")
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Editar") },
                                    onClick = {
                                        menuOpen = false
                                        editing = service
                                        formError = ""
                                        editorOpen = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (service.active) "Desativar" else "Reativar") },
                                    onClick = {
                                        menuOpen = false
                                        if (service.active) {
                                            pendingDelete = service
                                        } else {
                                            changingId = service.id
                                            scope.launch {
                                                try {
                                                    api.setServiceActive(
                                                        token, service.id, mapOf("active" to true)
                                                    )
                                                    revision++
                                                } catch (failure: Exception) {
                                                    error = apiMessage(failure)
                                                } finally {
                                                    changingId = null
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (editorOpen) {
        BarberServiceEditor(
            service = editing,
            saving = saving,
            error = formError,
            onDismiss = { if (!saving) editorOpen = false },
            onSave = { request ->
                if (!saving) {
                    saving = true
                    formError = ""
                    scope.launch {
                        try {
                            val current = editing
                            if (current == null) api.createService(token, request)
                            else api.updateService(token, current.id, request)
                            editorOpen = false
                            revision++
                        } catch (failure: Exception) {
                            formError = apiMessage(failure)
                        } finally {
                            saving = false
                        }
                    }
                }
            }
        )
    }
    pendingDelete?.let { service ->
        AlertDialog(
            onDismissRequest = { if (changingId == null) pendingDelete = null },
            title = { Text("Desativar serviço?") },
            text = {
                Text(
                    "O serviço ${service.name} deixará de aparecer nos novos agendamentos. " +
                        "O histórico permanece preservado."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = changingId == null,
                    onClick = {
                        changingId = service.id
                        scope.launch {
                            try {
                                api.deactivateService(token, service.id)
                                pendingDelete = null
                                revision++
                            } catch (failure: Exception) {
                                error = apiMessage(failure)
                            } finally {
                                changingId = null
                            }
                        }
                    }
                ) { Text("Desativar") }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDelete = null },
                    enabled = changingId == null
                ) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun BarberServiceEditor(
    service: ServiceView?,
    saving: Boolean,
    error: String,
    onDismiss: () -> Unit,
    onSave: (ServiceWriteRequest) -> Unit
) {
    var name by remember { mutableStateOf(service?.name ?: "") }
    var duration by remember { mutableStateOf(service?.durationMinutes?.toString() ?: "") }
    var price by remember { mutableStateOf(service?.defaultPrice?.toPlainString() ?: "") }
    var isHaircut by remember { mutableStateOf(service?.isHaircut ?: true) }
    var icon by remember {
        mutableStateOf(
            BarberServiceIcon.entries.firstOrNull { it.name == service?.iconKey }
                ?: BarberServiceIcon.SCISSORS
        )
    }
    var iconMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (service == null) "Novo serviço" else "Editar serviço") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do serviço") },
                    enabled = !saving,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter(Char::isDigit).take(3) },
                    label = { Text("Duração (minutos)") },
                    enabled = !saving,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Preço (R$)") },
                    enabled = !saving,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Tipo de serviço")
                Box {
                    TextButton(
                        onClick = { iconMenu = true },
                        enabled = !saving
                    ) {
                        Icon(iconFor(icon.name), contentDescription = null, tint = BarberGold)
                        Spacer(Modifier.width(8.dp))
                        Text(icon.label)
                    }
                    DropdownMenu(
                        expanded = iconMenu,
                        onDismissRequest = { iconMenu = false }
                    ) {
                        BarberServiceIcon.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    icon = option
                                    isHaircut = option == BarberServiceIcon.SCISSORS ||
                                        option == BarberServiceIcon.COMBO ||
                                        option == BarberServiceIcon.CHILD
                                    iconMenu = false
                                }
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isHaircut,
                        onCheckedChange = { isHaircut = it },
                        enabled = !saving
                    )
                    Text("É serviço de corte")
                }
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                if (!serviceFormValid(name, duration, price)) {
                    Text(
                        "Informe nome, duração entre 1 e 480 min e preço com até 2 decimais.",
                        color = BarberTextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && serviceFormValid(name, duration, price),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BarberGold, contentColor = BarberOnGold
                ),
                onClick = {
                    val amount = servicePriceInput(price) ?: return@Button
                    onSave(
                        ServiceWriteRequest(
                            name = name.trim(),
                            durationMinutes = duration.toInt(),
                            defaultPrice = amount,
                            isHaircut = isHaircut,
                            active = service?.active ?: true,
                            iconKey = icon.name
                        )
                    )
                }
            ) { Text(if (saving) "Salvando..." else "Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") }
        }
    )
}
