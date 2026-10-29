package com.agendabarber.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agendabarber.app.network.AgendaApiFactory
import com.agendabarber.app.network.LoginRequest
import com.agendabarber.app.ui.theme.BarberCancelAction
import com.agendabarber.app.ui.theme.BarberCompleted
import com.agendabarber.app.ui.theme.BarberInProgress
import com.agendabarber.app.ui.theme.BarberScheduled
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

internal fun statusColor(status: String): Color = when (status) {
    "COMPLETED" -> BarberCompleted
    "IN_PROGRESS" -> BarberInProgress
    "CANCELLED" -> BarberCancelAction
    else -> BarberScheduled
}
