package com.agendabarber.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.agendabarber.app.R
import com.agendabarber.app.network.AgendaApiFactory
import com.agendabarber.app.network.LoginRequest
import com.agendabarber.app.ui.theme.BarberBackground
import com.agendabarber.app.ui.theme.BarberCancelAction
import com.agendabarber.app.ui.theme.BarberCompleted
import com.agendabarber.app.ui.theme.BarberGold
import com.agendabarber.app.ui.theme.BarberInProgress
import com.agendabarber.app.ui.theme.BarberOnGold
import com.agendabarber.app.ui.theme.BarberScheduled
import kotlinx.coroutines.launch

internal fun validLoginInput(email: String, password: String, url: String): Boolean =
    email.trim().matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) &&
        password.isNotBlank() &&
        url.trim().startsWith("https://", true)

@Composable
fun AgendaApp() {
    var url by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var token by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var recoveryNotice by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentToken = token
    if (currentToken == null) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(R.drawable.login_barber_photo),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        0.48f to Color.Black.copy(alpha = 0.63f),
                        1f to BarberBackground.copy(alpha = 0.97f)
                    )
                )
            )
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Outlined.ContentCut,
                    null,
                    tint = BarberGold,
                    modifier = Modifier.align(Alignment.CenterHorizontally).height(56.dp)
                )
                Text(
                    "BARBER",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Text(
                    "AGENDA",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Text(
                    "Sua agenda, seus resultados.",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(16.dp))
                if (showSettings) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Endereço HTTPS da API") },
                        enabled = !busy,
                        placeholder = { Text("https://api.exemplo.com/") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-mail") },
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    leadingIcon = { Icon(Icons.Outlined.Email, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Senha") },
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = {
                            showPassword = !showPassword
                        }) {
                            Icon(
                                if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                if (showPassword) "Ocultar senha" else "Mostrar senha"
                            )
                        }
                    },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(enabled = !busy, onClick = {
                    recoveryNotice = !recoveryNotice
                }) { Text("Esqueci minha senha", color = BarberGold) }
                if (recoveryNotice) Text("Recuperação indisponível. Contate o administrador.")
                TextButton(enabled = !busy, onClick = {
                    showSettings = !showSettings
                }) { Text("API HTTPS", color = BarberGold) }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                Button(
                    enabled = !busy && validLoginInput(email, password, url),
                    colors = ButtonDefaults.buttonColors(containerColor = BarberGold, contentColor = BarberOnGold),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val client = AgendaApiFactory.create(url)
                                val response = client.login(LoginRequest(email.trim(), password))
                                require(response.accessToken.isNotBlank()) { "Token de acesso vazio" }
                                token = "Bearer " + response.accessToken
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
