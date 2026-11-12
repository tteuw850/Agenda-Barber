package com.agendabarber.app.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.agendabarber.app.BuildConfig
import com.agendabarber.app.R
import com.agendabarber.app.network.AgendaApiFactory
import com.agendabarber.app.network.LoginRequest
import com.agendabarber.app.network.RefreshRequest
import com.agendabarber.app.ui.theme.BarberBackground
import com.agendabarber.app.ui.theme.BarberCancelAction
import com.agendabarber.app.ui.theme.BarberCompleted
import com.agendabarber.app.ui.theme.BarberGold
import com.agendabarber.app.ui.theme.BarberInProgress
import com.agendabarber.app.ui.theme.BarberOnGold
import com.agendabarber.app.ui.theme.BarberScheduled
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun validLoginInput(username: String, password: String): Boolean =
    username.trim().matches(Regex("^[A-Za-z][A-Za-z0-9._-]{2,39}$")) && password.isNotBlank()

@Composable
fun AgendaApp() {
    val apiUrl = BuildConfig.API_BASE_URL
    val context = LocalContext.current
    val loginPreferences = remember(context) {
        context.applicationContext.getSharedPreferences("agenda-barber-login", Context.MODE_PRIVATE)
    }
    var rememberAccount by remember { mutableStateOf(loginPreferences.getBoolean("remember_account", false)) }
    var username by remember {
        mutableStateOf(if (rememberAccount) loginPreferences.getString("remembered_username", "").orEmpty() else "")
    }
    var password by remember { mutableStateOf("") }
    val api = remember(apiUrl) { AgendaApiFactory.create(apiUrl) }
    val secureSession = remember(context) { SecureRefreshSessionStore(context.applicationContext) }
    var token by remember { mutableStateOf<String?>(null) }
    var activeRefreshToken by remember { mutableStateOf<String?>(null) }
    var accessTtlSeconds by remember { mutableStateOf(900L) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var restoringSession by remember {
        mutableStateOf(rememberAccount && secureSession.hasSavedToken())
    }
    var restoreAttempt by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // Refresh tokens are single-use. Save the rotated replacement BEFORE opening the agenda.
    LaunchedEffect(apiUrl, restoreAttempt) {
        if (!rememberAccount) {
            restoringSession = false
            return@LaunchedEffect
        }
        val savedRefresh = withContext(Dispatchers.IO) { secureSession.load() }
        if (savedRefresh == null) {
            restoringSession = false
            return@LaunchedEffect
        }
        restoringSession = true
        error = ""
        try {
            val response = api.refresh(RefreshRequest(savedRefresh))
            check(response.accessToken.isNotBlank() && response.refreshToken.isNotBlank())
            withContext(Dispatchers.IO) { secureSession.save(response.refreshToken) }
            activeRefreshToken = response.refreshToken
            accessTtlSeconds = response.expiresInSeconds
            token = "Bearer " + response.accessToken
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (shouldForgetRefreshSession(failure)) {
                withContext(Dispatchers.IO) { secureSession.clear() }
                error = "Sua sessão expirou. Entre novamente."
            } else {
                error = sessionRestoreErrorMessage(failure)
            }
        } finally {
            restoringSession = false
        }
    }

    // While the app remains open, rotate credentials before the 15-minute access token expires.
    LaunchedEffect(token, activeRefreshToken) {
        val originalAccess = token ?: return@LaunchedEffect
        var currentRefresh = activeRefreshToken ?: return@LaunchedEffect
        delay((accessTtlSeconds - 90).coerceAtLeast(30L) * 1000L)
        while (token == originalAccess) {
            try {
                val response = api.refresh(RefreshRequest(currentRefresh))
                check(response.accessToken.isNotBlank() && response.refreshToken.isNotBlank())
                if (rememberAccount) {
                    withContext(Dispatchers.IO) { secureSession.save(response.refreshToken) }
                }
                currentRefresh = response.refreshToken
                activeRefreshToken = response.refreshToken
                accessTtlSeconds = response.expiresInSeconds
                token = "Bearer " + response.accessToken
                break
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (shouldForgetRefreshSession(failure)) {
                    withContext(Dispatchers.IO) { secureSession.clear() }
                    activeRefreshToken = null
                    token = null
                    error = "Sua sessão expirou. Entre novamente."
                    break
                }
                delay(30_000L)
            }
        }
    }

    if (restoringSession) {
        Box(
            modifier = Modifier.fillMaxSize().background(BarberBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.agenda_barber_ab_logo),
                    contentDescription = "Logo Agenda Barber",
                    modifier = Modifier.size(132.dp),
                    contentScale = ContentScale.Fit
                )
                Text("Entrando...", color = Color.White)
            }
        }
        return
    }

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
                Image(
                    painter = painterResource(R.drawable.agenda_barber_ab_logo),
                    contentDescription = "Logo Agenda Barber",
                    modifier = Modifier.align(Alignment.CenterHorizontally).size(92.dp),
                    contentScale = ContentScale.Fit
                )
                Text(
                    "AGENDA",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Text(
                    "BARBER",
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
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        error = ""
                    },
                    label = { Text("Nome de usuário") },
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    leadingIcon = { Icon(Icons.Outlined.Person, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = ""
                    },
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
                    visualTransformation =
                    if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = rememberAccount,
                        enabled = !busy,
                        onCheckedChange = { checked ->
                            rememberAccount = checked
                            val editor = loginPreferences.edit().putBoolean("remember_account", checked)
                            if (!checked) {
                                editor.remove("remembered_username")
                                scope.launch(Dispatchers.IO) { secureSession.clear() }
                            }
                            editor.apply()
                        },
                        colors = CheckboxDefaults.colors(checkedColor = BarberGold)
                    )
                    Text("Lembrar conta", color = Color.White)
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                if (rememberAccount && secureSession.hasSavedToken() && error.isNotBlank()) {
                    TextButton(onClick = { restoreAttempt++ }, enabled = !busy) {
                        Text("Tentar entrar automaticamente", color = BarberGold)
                    }
                }
                Button(
                    enabled = !busy && validLoginInput(username, password),
                    colors = ButtonDefaults.buttonColors(containerColor = BarberGold, contentColor = BarberOnGold),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        busy = true
                        error = ""
                        scope.launch {
                            try {
                                val response = api.login(LoginRequest(username.trim().lowercase(), password))
                                check(response.accessToken.isNotBlank() && response.refreshToken.isNotBlank()) {
                                    "Token de acesso vazio"
                                }
                                if (rememberAccount) {
                                    withContext(Dispatchers.IO) { secureSession.save(response.refreshToken) }
                                    loginPreferences.edit()
                                        .putBoolean("remember_account", true)
                                        .putString("remembered_username", username.trim().lowercase())
                                        .apply()
                                } else {
                                    withContext(Dispatchers.IO) { secureSession.clear() }
                                    loginPreferences.edit().remove("remembered_username").apply()
                                }
                                activeRefreshToken = response.refreshToken
                                accessTtlSeconds = response.expiresInSeconds
                                token = "Bearer " + response.accessToken
                                password = ""
                                error = ""
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (e: IllegalArgumentException) {
                                error = "Não foi possível iniciar a sessão segura."
                            } catch (e: Exception) {
                                error = loginErrorMessage(e)
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text(if (busy) "Conectando..." else "Entrar") }
            }
        }
        return
    }
    BarberAuthenticatedApp(
        api = api,
        token = currentToken,
        onLogout = {
            // Sair explicitly revokes the local automatic login and the server session.
            secureSession.clear()
            activeRefreshToken = null
            token = null
            password = ""
            scope.launch {
                try {
                    api.logout(currentToken)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (ignored: Exception) {
                    // Local logout must still succeed when offline.
                }
            }
        }
    )
}

internal fun statusColor(status: String): Color = when (status) {
    "COMPLETED" -> BarberCompleted
    "IN_PROGRESS" -> BarberInProgress
    "CANCELLED" -> BarberCancelAction
    else -> BarberScheduled
}
