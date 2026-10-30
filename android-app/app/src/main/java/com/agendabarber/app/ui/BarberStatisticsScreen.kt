package com.agendabarber.app.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agendabarber.app.cache.StatisticsRepository
import com.agendabarber.app.servicecatalog.formatBrl
import com.agendabarber.app.ui.theme.StatsBackground
import com.agendabarber.app.ui.theme.StatsCancellationsCard
import com.agendabarber.app.ui.theme.StatsChartBlue
import com.agendabarber.app.ui.theme.StatsCutsCard
import com.agendabarber.app.ui.theme.StatsOutline
import com.agendabarber.app.ui.theme.StatsRevenueCard
import com.agendabarber.app.ui.theme.StatsSurface
import com.agendabarber.app.ui.theme.StatsTextPrimary
import com.agendabarber.app.ui.theme.StatsTextSecondary
import com.agendabarber.app.ui.theme.StatsTicketCard
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.time.ZoneId

private fun statisticsError(error: Exception): String = when ((error as? HttpException)?.code()) {
    400 -> "Período inválido. Selecione outro mês."
    401, 403 -> "Sessão expirada ou sem acesso. Entre novamente para visualizar os resultados."
    else -> "Não foi possível carregar os indicadores. Verifique a conexão e tente atualizar."
}

@Composable
internal fun BarberStatisticsScreen(repository: StatisticsRepository, token: String, revision: Int) {
    var monthCode by rememberSaveable {
        mutableStateOf(YearMonth.now(ZoneId.of("America/Sao_Paulo")).toString())
    }
    var showExample by rememberSaveable { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    var actualState by remember(token) { mutableStateOf<StatisticsState>(StatisticsState.Loading) }
    val month = YearMonth.parse(monthCode)

    LaunchedEffect(repository, token, monthCode, revision, refresh) {
        actualState = StatisticsState.Loading
        try {
            val result = repository.dashboard(token, month)
            actualState = StatisticsState.Available(
                reportToStatistics(result.dashboard),
                result.fromCache,
                result.synchronizedAt
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            actualState = StatisticsState.Failed(statisticsError(error))
        }
    }

    StatisticsDashboard(
        month = month,
        state = if (showExample) StatisticsState.Available(exampleStatistics(month)) else actualState,
        isDemonstration = showExample,
        onPreviousMonth = { monthCode = month.minusMonths(1).toString() },
        onNextMonth = { monthCode = month.plusMonths(1).toString() },
        onToggleDemonstration = { showExample = !showExample },
        onRefresh = { refresh++ }
    )
}

/** UX-09 layout is unchanged; real data is loaded by BarberStatisticsScreen. */
@Composable
internal fun StatisticsDashboard(
    month: YearMonth,
    state: StatisticsState,
    isDemonstration: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToggleDemonstration: () -> Unit,
    onRefresh: () -> Unit
) {
    val snapshot = (state as? StatisticsState.Available)?.snapshot
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StatsBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Estatísticas",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = StatsTextPrimary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPreviousMonth) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Mês anterior", tint = StatsTextPrimary)
            }
            Text(
                statisticsMonthCaption(month),
                color = StatsTextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = onNextMonth) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "Próximo mês", tint = StatsTextPrimary)
            }
        }
        when {
            isDemonstration -> StatusNote("PRÉVIA ILUSTRATIVA — valores fictícios, não representam seu negócio.")
            state is StatisticsState.Loading -> StatusNote("Buscando resultados financeiros...")
            state is StatisticsState.Failed -> StatusNote(state.message)
            state is StatisticsState.Available -> {
                val money = "Despesas: " + formatBrl(state.snapshot.expenses) +
                    " • Lucro estimado: " + formatBrl(state.snapshot.estimatedProfit)
                val source = if (state.fromCache) {
                    val lastSync = state.synchronizedAt?.let {
                        DateTimeFormatter.ofPattern("dd/MM HH:mm").format(
                            Instant.ofEpochMilli(it).atZone(ZoneId.of(state.snapshot.timezone))
                        )
                    } ?: "data desconhecida"
                    "OFFLINE • Última sincronização: " + lastSync + ". "
                } else {
                    "Dados atualizados. "
                }
                val unknown = if (state.snapshot.missingReceipts > 0) {
                    " • " + state.snapshot.missingReceipts + " atendimentos sem recebimento informado"
                } else {
                    ""
                }
                StatusNote(source + money + unknown)
            }
            else -> StatusNote("Nenhum resultado disponível neste período.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatisticsKpi(
                title = "Faturamento",
                value = snapshot?.let { formatBrl(it.revenue) } ?: "—",
                subtitle = if (isDemonstration) {
                    "↑ 12% • exemplo"
                } else if (snapshot == null) {
                    "Sem dados"
                } else {
                    "No mês"
                },
                icon = Icons.Outlined.AttachMoney,
                background = StatsRevenueCard,
                modifier = Modifier.weight(1f)
            )
            StatisticsKpi(
                title = "Cortes realizados",
                value = snapshot?.completedCuts?.toString() ?: "—",
                subtitle = if (isDemonstration) {
                    "↑ 8% • exemplo"
                } else if (snapshot ==
                    null
                ) {
                    "Sem dados"
                } else {
                    "Concluídos"
                },
                icon = Icons.Outlined.ContentCut,
                background = StatsCutsCard,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatisticsKpi(
                title = "Ticket médio",
                value = snapshot?.let { formatBrl(it.averageTicket) } ?: "—",
                subtitle = if (isDemonstration) {
                    "↑ 5% • exemplo"
                } else if (snapshot ==
                    null
                ) {
                    "Sem dados"
                } else {
                    "Por atendimento"
                },
                icon = Icons.Outlined.LocalOffer,
                background = StatsTicketCard,
                modifier = Modifier.weight(1f)
            )
            StatisticsKpi(
                title = "Cancelamentos",
                value = snapshot?.cancellations?.toString() ?: "—",
                subtitle = if (isDemonstration) {
                    "↓ 2% • exemplo"
                } else if (snapshot == null) {
                    "Sem dados"
                } else {
                    "Cancelados no mês"
                },
                icon = Icons.Outlined.Close,
                background = StatsCancellationsCard,
                modifier = Modifier.weight(1f)
            )
        }

        StatisticsPanel(title = "Faturamento por dia") {
            val values = snapshot?.dailyRevenue.orEmpty()
            if (values.isEmpty()) {
                ChartEmptyText("Sem faturamento registrado neste período.")
            } else {
                val highest = values.maxOf { it.amount }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    values.sortedBy { it.day }.forEach { entry ->
                        val fraction = statisticsBarFraction(entry.amount, highest)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.semantics {
                                contentDescription = "Dia ${entry.day}: ${formatBrl(entry.amount)}"
                            }
                        ) {
                            Box(
                                modifier = Modifier.width(28.dp).height(140.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    Modifier
                                        .width(22.dp)
                                        .height((140f * fraction).dp)
                                        .background(StatsChartBlue, RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                )
                            }
                            Text(
                                entry.day.toString().padStart(2, '0'),
                                color = StatsTextSecondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        StatisticsPanel(title = "Serviços mais realizados") {
            val services = snapshot?.topServices.orEmpty()
            if (services.isEmpty()) {
                ChartEmptyText("Nenhum serviço concluído neste período.")
            } else {
                val maximum = services.maxOf { it.completed }
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    services.sortedByDescending { it.completed }.forEach { service ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    service.name,
                                    color = StatsTextPrimary,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "${service.completed} realizados",
                                    color = StatsTextSecondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            LinearProgressIndicator(
                                progress = { statisticsRankFraction(service.completed, maximum) },
                                modifier = Modifier.fillMaxWidth().height(9.dp),
                                color = StatsChartBlue,
                                trackColor = StatsOutline
                            )
                        }
                    }
                }
            }
        }

        when (state) {
            StatisticsState.Loading -> ChartEmptyText("Carregando indicadores...")
            is StatisticsState.Failed -> ChartEmptyText(state.message)
            else -> Unit
        }
        Row(modifier = Modifier.align(Alignment.CenterHorizontally)) {
            TextButton(onClick = onRefresh) {
                Text("Atualizar", color = StatsTextPrimary)
            }
            TextButton(onClick = onToggleDemonstration) {
                Text(
                    if (isDemonstration) "Ocultar demonstração" else "Ver prévia fictícia",
                    color = StatsTextPrimary
                )
            }
        }
        Text(
            "A visualização de exemplo não consulta a API e não registra valores.",
            color = StatsTextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StatusNote(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StatsSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            message,
            color = StatsTextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        )
    }
}

@Composable
private fun StatisticsKpi(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    background: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.heightIn(min = 128.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    title,
                    color = StatsTextPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
                Icon(icon, contentDescription = null, tint = StatsTextPrimary)
            }
            Text(
                value,
                color = StatsTextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                subtitle,
                color = StatsTextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun StatisticsPanel(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StatsSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                title,
                color = StatsTextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
private fun ChartEmptyText(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(110.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            color = StatsTextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
