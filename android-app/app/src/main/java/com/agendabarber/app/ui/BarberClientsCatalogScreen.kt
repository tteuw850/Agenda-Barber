package com.agendabarber.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agendabarber.app.network.AgendaApi
import com.agendabarber.app.network.ClientView
import com.agendabarber.app.ui.theme.BarberGold
import com.agendabarber.app.ui.theme.BarberOnGold
import com.agendabarber.app.ui.theme.BarberSurface
import com.agendabarber.app.ui.theme.BarberTextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class ClientFilter(val caption: String, val apiValue: String) {
    ALL("Todos", "all"),
    RECENT("Recentes", "recent"),
    FAVORITES("Favoritos", "favorites")
}

internal fun clientLastVisit(instant: String?): String {
    if (instant.isNullOrBlank()) return "Sem atendimentos"
    return runCatching {
        DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("America/Sao_Paulo"))
            .format(Instant.parse(instant))
    }.getOrDefault("Data indisponível")
}

@Composable
internal fun BarberClientsCatalogScreen(api: AgendaApi, token: String, refreshSignal: Int, onNewClient: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ClientFilter.ALL) }
    var clients by remember { mutableStateOf(emptyList<ClientView>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var revision by remember { mutableStateOf(0) }
    var favoriteBusy by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<ClientView?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(token, query, filter, refreshSignal, revision) {
        loading = true
        error = ""
        delay(250)
        try {
            clients = api.clients(token, query.trim(), filter = filter.apiValue)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            clients = emptyList()
            error = "Não foi possível carregar os clientes. Verifique a conexão e tente novamente."
        } finally {
            loading = false
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Clientes", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Opções de clientes")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Novo cliente") },
                        onClick = {
                            menuOpen = false
                            onNewClient()
                        }
                    )
                }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar por nome ou telefone") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ClientFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = { Text(option.caption) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BarberGold,
                        selectedLabelColor = BarberOnGold
                    )
                )
            }
        }
        if (loading) CircularProgressIndicator(color = BarberGold)
        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { revision++ }) { Text("Tentar novamente") }
        }
        if (!loading && error.isBlank() && clients.isEmpty()) {
            Text(
                if (query.isNotBlank()) {
                    "Nenhum cliente corresponde à busca."
                } else if (filter == ClientFilter.FAVORITES) {
                    "Nenhum cliente favorito."
                } else if (filter == ClientFilter.RECENT) {
                    "Nenhum atendimento concluído ainda."
                } else {
                    "Nenhum cliente cadastrado."
                },
                color = BarberTextSecondary
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(clients, key = { it.id }) { client ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { selected = client },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(46.dp).background(BarberSurface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(client.name.take(1).uppercase(), color = BarberGold)
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(client.name, fontWeight = FontWeight.Medium)
                            Text(
                                "Último atendimento: " + clientLastVisit(client.lastAppointmentAt),
                                color = BarberTextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "${client.completedAppointments} atendimentos",
                                color = BarberTextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(
                            enabled = favoriteBusy == null,
                            onClick = {
                                favoriteBusy = client.id
                                scope.launch {
                                    try {
                                        val updated = api.favoriteClient(
                                            token,
                                            client.id,
                                            mapOf("favorite" to !client.favorite)
                                        )
                                        clients = clients.map { if (it.id == updated.id) updated else it }
                                        if (filter == ClientFilter.FAVORITES && !updated.favorite) {
                                            clients = clients.filterNot { it.id == updated.id }
                                        }
                                        if (selected?.id == updated.id) selected = updated
                                    } catch (failure: Exception) {
                                        error = "Não foi possível atualizar o favorito. Tente novamente."
                                    } finally {
                                        favoriteBusy = null
                                    }
                                }
                            }
                        ) {
                            Icon(
                                if (client.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (client.favorite) {
                                    "Remover dos favoritos"
                                } else {
                                    "Adicionar aos favoritos"
                                },
                                tint = BarberGold
                            )
                        }
                    }
                }
            }
        }
    }
    selected?.let { client ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(client.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(client.phone ?: "Telefone não informado")
                    Text("Último atendimento: " + clientLastVisit(client.lastAppointmentAt))
                    Text("${client.completedAppointments} atendimentos concluídos")
                    Text(
                        "Histórico detalhado disponível em uma etapa posterior.",
                        color = BarberTextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selected = null }) { Text("Fechar") }
            }
        )
    }
}
