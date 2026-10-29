package com.agendabarber.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import com.agendabarber.app.network.AgendaApi
import com.agendabarber.app.network.ExpenseInput
import com.agendabarber.app.network.ExpenseView
import com.agendabarber.app.servicecatalog.formatBrl
import com.agendabarber.app.ui.theme.BarberGold
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException

private fun expenseError(e: Exception): String = when ((e as? HttpException)?.code()) {
    400 -> "Revise data, descrição, categoria e valor."
    401, 403 -> "Sua sessão expirou ou não permite esta ação."
    404 -> "Despesa não encontrada nesta conta."
    409 -> "Conflito financeiro. Confira o lançamento antes de tentar novamente."
    else -> "Operação não confirmada. Confira a conexão e a lista antes de tentar novamente."
}

@Composable
internal fun BarberExpensesScreen(api: AgendaApi, token: String, onBack: () -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now(barberScheduleZone)) }
    var expenses by remember { mutableStateOf(emptyList<ExpenseView>()) }
    var loading by remember { mutableStateOf(true) }
    var revision by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ExpenseView?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ExpenseView?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(token, month, revision) {
        loading = true
        error = ""
        try {
            expenses = api.expenses(token, month.atDay(1).toString(), month.atEndOfMonth().toString())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            expenses = emptyList()
            error = expenseError(failure)
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹ Voltar", color = BarberGold) }
        Text("Despesas", color = BarberGold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹", color = BarberGold) }
            Text(statisticsMonthCaption(month))
            TextButton(onClick = { month = month.plusMonths(1) }) { Text("›", color = BarberGold) }
        }
        Text("As despesas não são descontadas dos atendimentos nesta tela. O resumo ficará na F03-02.")
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                editing = null
                showForm = true
            }
        ) { Text("+ Nova despesa") }
        if (loading) CircularProgressIndicator()
        if (error.isNotBlank()) {
            Text(error)
            TextButton(onClick = { revision++ }) { Text("Tentar novamente") }
        }
        if (message.isNotBlank()) Text(message, color = BarberGold)
        if (!loading && error.isBlank() && expenses.isEmpty()) Text("Nenhuma despesa neste mês.")
        expenses.forEach { expense ->
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(expense.description)
                Text("${expense.occurredOn} • ${expense.category} • ${formatBrl(expense.amount)}")
                Row {
                    TextButton(onClick = { editing = expense; showForm = true }) {
                        Text("Editar", color = BarberGold)
                    }
                    TextButton(onClick = { pendingDelete = expense }) { Text("Excluir") }
                }
                HorizontalDivider()
            }
        }
    }
    if (showForm) {
        ExpenseEditorDialog(
            source = editing,
            busy = busy,
            onDismiss = { if (!busy) showForm = false },
            onSave = { input, key ->
                busy = true
                error = ""
                message = ""
                scope.launch {
                    try {
                        if (editing == null) {
                            api.createExpense(token, key, input)
                        } else {
                            api.updateExpense(token, editing!!.id, input)
                        }
                        showForm = false
                        message = "Despesa confirmada pela API."
                        revision++
                    } catch (failure: Exception) {
                        error = expenseError(failure)
                    } finally {
                        busy = false
                    }
                }
            }
        )
    }
    pendingDelete?.let { expense ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingDelete = null },
            title = { Text("Excluir despesa?") },
            text = { Text("O lançamento será retirado dos relatórios, mas preservado para auditoria.") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                api.deleteExpense(token, expense.id)
                                pendingDelete = null
                                message = "Despesa excluída."
                                revision++
                            } catch (failure: Exception) {
                                error = expenseError(failure)
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text(if (busy) "Excluindo..." else "Excluir") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { pendingDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun ExpenseEditorDialog(
    source: ExpenseView?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (ExpenseInput, String) -> Unit
) {
    var date by remember(source?.id) { mutableStateOf(source?.occurredOn ?: LocalDate.now(barberScheduleZone).toString()) }
    var description by remember(source?.id) { mutableStateOf(source?.description ?: "") }
    var category by remember(source?.id) { mutableStateOf(source?.category ?: "") }
    var amountText by remember(source?.id) { mutableStateOf(source?.amount?.toPlainString() ?: "") }
    var key by remember(source?.id) { mutableStateOf(UUID.randomUUID().toString()) }
    val amount = parseMoney(amountText, positiveOnly = true)
    val valid = runCatching { LocalDate.parse(date) }.isSuccess &&
        description.isNotBlank() && description.length <= 255 &&
        category.isNotBlank() && category.length <= 80 && amount != null
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (source == null) "Nova despesa" else "Editar despesa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it.take(10); key = UUID.randomUUID().toString() },
                    label = { Text("Data (AAAA-MM-DD)") },
                    enabled = !busy
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(255); key = UUID.randomUUID().toString() },
                    label = { Text("Descrição") },
                    enabled = !busy
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it.take(80); key = UUID.randomUUID().toString() },
                    label = { Text("Categoria") },
                    enabled = !busy
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.take(14); key = UUID.randomUUID().toString() },
                    label = { Text("Valor (R$)") },
                    enabled = !busy
                )
                if (!valid) Text("Informe data válida, descrição, categoria e valor positivo.")
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && valid,
                onClick = {
                    val checked = amount ?: return@TextButton
                    onSave(ExpenseInput(date, description.trim(), checked, category.trim()), key)
                }
            ) { Text(if (busy) "Salvando..." else "Salvar", color = BarberGold) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
