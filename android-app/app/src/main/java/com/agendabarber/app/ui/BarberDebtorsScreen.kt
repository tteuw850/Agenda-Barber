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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agendabarber.app.network.AgendaApi
import com.agendabarber.app.network.ClientView
import com.agendabarber.app.network.DebtDetail
import com.agendabarber.app.network.DebtInput
import com.agendabarber.app.network.DebtSummary
import com.agendabarber.app.servicecatalog.formatBrl
import com.agendabarber.app.ui.theme.BarberGold
import com.agendabarber.app.ui.theme.BarberOnGold
import com.agendabarber.app.ui.theme.BarberOutline
import com.agendabarber.app.ui.theme.BarberSurface
import com.agendabarber.app.ui.theme.BarberTextPrimary
import com.agendabarber.app.ui.theme.BarberTextSecondary
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun BarberDebtorsScreen(api: AgendaApi, token: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var clients by remember { mutableStateOf(emptyList<ClientView>()) }
    var debtors by remember { mutableStateOf(emptyList<DebtSummary>()) }
    var selectedId by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<DebtDetail?>(null) }
    var search by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var debtorsAvailable by remember { mutableStateOf(false) }
    var detailLoading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    var revision by remember { mutableStateOf(0) }

    LaunchedEffect(token, revision) {
        loading = true
        debtorsAvailable = false
        error = ""
        try {
            clients = api.clients(token, "", 100)
            debtors = api.debtors(token)
            debtorsAvailable = true
            if (selectedId.isNotEmpty()) detail = api.debt(token, selectedId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "Não foi possível consultar os devedores. Verifique a conexão e tente novamente."
        } finally {
            loading = false
        }
    }

    fun record(kind: String) {
        if (saving || selectedId.isEmpty()) return
        val value = amount.trim().replace(",", ".").toBigDecimalOrNull()
        if (value == null || value <= BigDecimal.ZERO || value.scale() > 2) {
            error = "Informe um valor válido maior que zero."
            return
        }
        saving = true
        error = ""
        info = ""
        scope.launch {
            try {
                val result = api.recordDebt(
                    token,
                    selectedId,
                    UUID.randomUUID().toString(),
                    DebtInput(kind, value, note.trim().ifBlank { null })
                )
                detail = result
                amount = ""
                note = ""
                info = if (kind == "CHARGE") "Dívida registrada." else "Pagamento registrado."
                revision++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = "Não foi possível confirmar o lançamento. Confira a conexão antes de repetir."
            } finally {
                saving = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹ Voltar", color = BarberGold) }
        Text("Devedores", color = BarberTextPrimary, fontWeight = FontWeight.Bold)
        Text("Controle de valores pendentes", color = BarberTextSecondary)
        if (debtorsAvailable) {
            Text(
            "Clientes com dívida: ${debtors.size} • Total: ${formatBrl(
                debtors.fold(BigDecimal.ZERO) { sum, item ->
                    sum + item.balance
                }
            )}",
            color = BarberGold,
            fontWeight = FontWeight.Bold
        )
        if (!debtorsAvailable && !loading) {
            Text("Saldo indisponível: API DEV ainda não atualizada.", color = BarberTextSecondary)
        }
        if (loading) Text("Carregando...", color = BarberTextSecondary)
        if (error.isNotEmpty()) Text(error, color = androidx.compose.ui.graphics.Color(0xFFFF7777))
        if (info.isNotEmpty()) Text(info, color = BarberGold)

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Buscar cliente") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        val matching = clients.filter { it.name.contains(search, ignoreCase = true) }.take(30)
        matching.forEach { client ->
            val due = debtors.firstOrNull { it.clientId == client.id }?.balance ?: BigDecimal.ZERO
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    selectedId = client.id
                    detail = null
                    detailLoading = true
                    amount = ""
                    error = ""
                    scope.launch {
                        try {
                            detail = api.debt(token, client.id)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = "Não foi possível consultar o saldo do cliente."
                        } finally {
                            detailLoading = false
                        }
                    }
                },
                colors = CardDefaults.cardColors(containerColor = BarberSurface),
                border = BorderStroke(if (selectedId == client.id) 2.dp else 1.dp, BarberOutline),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(client.name, color = BarberTextPrimary)
                    if (due > BigDecimal.ZERO) {
                        Text("Deve ${formatBrl(due)}", color = BarberGold)
                    }
                }
            }
        }
        if (selectedId.isNotEmpty()) {
            val current = detail
            if (current == null) {
                if (detailLoading) {
                    Text("Consultando cliente...", color = BarberTextSecondary)
                } else {
                    Text(
                        "Saldo indisponível. Não foi possível carregar o cadastro financeiro.",
                        color = BarberTextSecondary
                    )
                }
            } else {
                Text(current.clientName, color = BarberTextPrimary, fontWeight = FontWeight.Bold)
                Text("Saldo pendente: ${formatBrl(current.balance)}", color = BarberGold)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Valor (R$)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(255) },
                    label = { Text("Observação (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { record("CHARGE") },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = BarberGold)
                ) { Text("Adicionar dívida", color = BarberOnGold) }
                OutlinedButton(
                    onClick = { record("PAYMENT") },
                    enabled = !saving && current.balance > BigDecimal.ZERO,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Registrar pagamento") }
                Text("Histórico", color = BarberTextPrimary, fontWeight = FontWeight.Bold)
                current.entries.forEach { entry ->
                    Text(
                        (if (entry.kind == "CHARGE") "Dívida + " else "Pagamento − ") +
                            formatBrl(entry.amount) + "  " + entry.note.orEmpty(),
                        color = BarberTextSecondary
                    )
                }
            }
        }
    }
}
