package com.agendabarber.app.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agendabarber.app.ui.theme.BarberBackground
import com.agendabarber.app.ui.theme.BarberCancelAction
import com.agendabarber.app.ui.theme.BarberGold
import com.agendabarber.app.ui.theme.BarberOutline
import com.agendabarber.app.ui.theme.BarberSurface
import com.agendabarber.app.ui.theme.BarberSurfaceRaised
import com.agendabarber.app.ui.theme.BarberTextPrimary
import com.agendabarber.app.ui.theme.BarberTextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun lastCacheWriteCaption(value: Long?): String = if (value == null) {
    "Nenhuma gravação de cache confirmada nesta sessão."
} else {
    val timestamp = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        .withZone(ZoneId.of("America/Sao_Paulo"))
        .format(Instant.ofEpochMilli(value))
    "Última gravação do cache nesta sessão: $timestamp."
}

internal enum class MoreInfo(val title: String, val detail: String) {
    PROFILE(
        "Perfil",
        "A edição do nome, foto e dados do barbeiro ainda não está disponível. " +
            "Nenhuma alteração de perfil foi enviada ao servidor."
    ),
    BACKUP(
        "Backup e sincronização",
        "A Agenda mantém um cache local de leitura com Room para consultas offline. " +
            "Isso não é um backup completo. Nenhum backup nem sincronização manual " +
            "é executado por esta tela."
    ),
    THEME(
        "Tema",
        "Automático (preferência informativa). A troca de tema ainda não está implementada. " +
            "O aplicativo permanece no visual aprovado, com Estatísticas em tema claro."
    ),
    HELP(
        "Ajuda e suporte",
        "Use a Agenda para gerenciar atendimentos, Clientes para os cadastros e Mais para " +
            "consultar serviços, expediente e bloqueios. Um canal de suporte ainda não foi configurado."
    ),
    ABOUT(
        "Sobre o app",
        "Agenda Barber — versão 0.1.0. Aplicativo Android interno para uso do barbeiro, " +
            "sem agendamento público. Desenvolvido com Kotlin e Jetpack Compose."
    )
}

@Composable
internal fun BarberMoreSettingsScreen(
    lastCacheWriteAt: Long?,
    onWorkingHours: () -> Unit,
    onServices: () -> Unit,
    onBlocks: () -> Unit,
    onExpenses: () -> Unit,
    onLogout: () -> Unit
) {
    var detail by rememberSaveable { mutableStateOf<MoreInfo?>(null) }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Mais",
            color = BarberTextPrimary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = BarberSurfaceRaised),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, BarberOutline)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = CircleShape,
                        colors = CardDefaults.cardColors(containerColor = BarberBackground)
                    ) {
                        Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.PersonOutline,
                                contentDescription = "Foto de perfil não cadastrada",
                                tint = BarberGold
                            )
                        }
                    }
                }
                Spacer(Modifier.size(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Barbeiro",
                        color = BarberTextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Perfil não configurado • Barbeiro",
                        color = BarberTextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        MoreMenuItem(
            title = "Perfil",
            icon = Icons.Outlined.PersonOutline,
            onClick = { detail = MoreInfo.PROFILE }
        )
        MoreMenuItem(
            title = "Horário de funcionamento",
            icon = Icons.Outlined.AccessTime,
            onClick = onWorkingHours
        )
        MoreMenuItem(
            title = "Notificações",
            icon = Icons.Outlined.NotificationsNone,
            subtitle = "Indisponível",
            onClick = {},
            enabled = false,
            trailing = {
                Switch(
                    checked = false,
                    onCheckedChange = null,
                    enabled = false,
                    colors = SwitchDefaults.colors(
                        disabledUncheckedTrackColor = BarberGold.copy(alpha = 0.48f),
                        disabledUncheckedThumbColor = BarberGold
                    )
                )
            }
        )
        MoreMenuItem(
            title = "Backup e sincronização",
            icon = Icons.Outlined.Backup,
            onClick = { detail = MoreInfo.BACKUP }
        )
        MoreMenuItem(
            title = "Tema",
            icon = Icons.Outlined.AutoAwesome,
            subtitle = "Automático",
            onClick = { detail = MoreInfo.THEME }
        )
        MoreMenuItem(
            title = "Ajuda e suporte",
            icon = Icons.Outlined.HelpOutline,
            onClick = { detail = MoreInfo.HELP }
        )
        MoreMenuItem(
            title = "Sobre o app",
            icon = Icons.Outlined.Info,
            onClick = { detail = MoreInfo.ABOUT }
        )

        Text(
            "Gestão",
            color = BarberTextSecondary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
        MoreMenuItem(
            title = "Serviços",
            icon = Icons.Outlined.ContentCut,
            onClick = onServices
        )
        MoreMenuItem(
            title = "Bloquear horário",
            icon = Icons.Outlined.Build,
            onClick = onBlocks
        )
        MoreMenuItem(
            title = "Despesas",
            icon = Icons.Outlined.ReceiptLong,
            onClick = onExpenses
        )

        OutlinedButton(
            onClick = { confirmLogout = true },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            border = BorderStroke(1.dp, BarberCancelAction),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                Icons.Outlined.Logout,
                contentDescription = null,
                tint = BarberCancelAction
            )
            Spacer(Modifier.size(8.dp))
            Text("Sair", color = BarberCancelAction)
        }
    }

    detail?.let { selected ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(selected.title) },
            text = {
                Text(
                    if (selected == MoreInfo.BACKUP) {
                        selected.detail + "\n" + lastCacheWriteCaption(lastCacheWriteAt)
                    } else {
                        selected.detail
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { detail = null }) {
                    Text("Entendi", color = BarberGold)
                }
            }
        )
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Sair da conta?") },
            text = { Text("A sessão será encerrada. O aplicativo tentará limpar o cache de leitura da Agenda.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        onLogout()
                    }
                ) { Text("Sair", color = BarberCancelAction) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun MoreMenuItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BarberSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BarberOutline),
        modifier = Modifier.fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = BarberGold)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = BarberTextPrimary)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        color = BarberTextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (trailing != null) {
                trailing()
            } else {
                Icon(
                    Icons.Outlined.ArrowForwardIos,
                    contentDescription = null,
                    tint = BarberTextSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
