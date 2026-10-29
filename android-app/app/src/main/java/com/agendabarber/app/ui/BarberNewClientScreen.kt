package com.agendabarber.app.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agendabarber.app.network.AgendaApi
import com.agendabarber.app.network.ClientView
import com.agendabarber.app.network.CreateClient
import com.agendabarber.app.ui.theme.BarberBackground
import com.agendabarber.app.ui.theme.BarberGold
import com.agendabarber.app.ui.theme.BarberOnGold
import com.agendabarber.app.ui.theme.BarberOutline
import com.agendabarber.app.ui.theme.BarberSurface
import com.agendabarber.app.ui.theme.BarberTextPrimary
import com.agendabarber.app.ui.theme.BarberTextSecondary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import retrofit2.HttpException

private val br = Locale.forLanguageTag("pt-BR")

internal fun maskClientPhone(value: String): String {
    val d = value.filter(Char::isDigit).take(11)
    return when {
        d.length > 10 -> "(" + d.take(2) + ") " + d.substring(2, 7) + "-" + d.substring(7)
        d.length > 6 -> "(" + d.take(2) + ") " + d.substring(2, 6) + "-" + d.substring(6)
        d.length > 2 -> "(" + d.take(2) + ") " + d.drop(2)
        d.isNotEmpty() -> "(" + d
        else -> ""
    }
}
internal fun isValidClientPhone(value: String): Boolean =
    value.isBlank() || value.filter(Char::isDigit).length in 10..11

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BarberNewClientScreen(api: AgendaApi, token: String, onBack: () -> Unit, onCreated: (ClientView) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var birthDate by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photoPath = uri.toString()
    }
    val preview = remember(photoPath) {
        photoPath?.let { path ->
            runCatching {
                val uri = Uri.parse(path)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val options = BitmapFactory.Options().apply {
                    inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 512).coerceAtLeast(1)
                }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    val validName = name.trim().isNotBlank() && name.trim().length <= 150
    val validEmail = email.isBlank() || (email.length <= 254 && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches())
    val validBirth = birthDate.isBlank() ||
        runCatching {
            LocalDate.parse(birthDate) <= LocalDate.now()
        }.getOrDefault(false)
    val canSave = validName && isValidClientPhone(phone) && validEmail && validBirth && notes.length <= 4000 && !busy

    if (pickerOpen) {
        val initial = birthDate.takeIf { it.isNotBlank() }
            ?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = initial,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= LocalDate.now()
                override fun isSelectableYear(year: Int) = year <= LocalDate.now().year
            }
        )
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let {
                        birthDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    pickerOpen = false
                }) { Text("Escolher") }
            },
            dismissButton = { TextButton(onClick = { pickerOpen = false }) { Text("Cancelar") } }
        ) { DatePicker(state = picker) }
    }

    Scaffold(
        containerColor = BarberBackground,
        bottomBar = {
            Button(
                onClick = {
                    busy = true
                    error = ""
                    scope.launch {
                        try {
                            val created = api.newClient(
                                token,
                                CreateClient(
                                    name = name.trim(),
                                    phone = phone.ifBlank { null },
                                    email = email.trim().ifBlank { null },
                                    birthDate = birthDate.ifBlank { null },
                                    notes = notes.trim().ifBlank { null }
                                )
                            )
                            onCreated(created)
                        } catch (failure: Exception) {
                            error = if ((failure as? HttpException)?.code() == 409) {
                                "Telefone já cadastrado. Selecione o cliente existente ou informe outro número."
                            } else {
                                apiMessage(failure)
                            }
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().imePadding().padding(
                    horizontal = 18.dp,
                    vertical = 12.dp
                ).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BarberGold),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp))
                } else {
                    Text("Salvar cliente", color = BarberOnGold, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { inset ->
        Column(
            Modifier.fillMaxSize().padding(inset).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, enabled = !busy) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar", tint = BarberTextPrimary)
                }
                Text(
                    "Novo Cliente",
                    color = BarberTextPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier.size(100.dp).clickable {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    shape = CircleShape,
                    color = BarberSurface,
                    border = BorderStroke(1.dp, BarberOutline)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (preview == null) {
                            Icon(
                                Icons.Outlined.CameraAlt,
                                "Escolher foto",
                                tint = BarberGold,
                                modifier = Modifier.size(32.dp)
                            )
                        } else {
                            Image(
                                preview,
                                "Prévia da foto",
                                Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
                TextButton(onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Adicionar foto", color = BarberGold) }
                if (photoPath != null) {
                    Text(
                        "Prévia apenas neste dispositivo: upload de foto ainda não disponível.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BarberTextSecondary,
                        textAlign = TextAlign.Center
                    )
                    TextButton(onClick = { photoPath = null }) { Text("Remover foto") }
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(150) },
                label = { Text("Nome completo") },
                leadingIcon = { Icon(Icons.Outlined.PersonOutline, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = maskClientPhone(phone),
                onValueChange = { phone = it.filter(Char::isDigit).take(11) },
                label = { Text("Telefone com WhatsApp (opcional)") },
                leadingIcon = { Icon(Icons.Outlined.Phone, null, tint = BarberGold) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = phone.isNotBlank() && !isValidClientPhone(phone)
            )
            if (phone.isNotBlank() && !isValidClientPhone(phone)) {
                Text("Informe DDD e 10 ou 11 dígitos.", color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.take(254) },
                label = { Text("E-mail (opcional)") },
                leadingIcon = { Icon(Icons.Outlined.Email, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = email.isNotBlank() && !validEmail
            )
            if (email.isNotBlank() && !validEmail) {
                Text("Digite um e-mail válido.", color = MaterialTheme.colorScheme.error)
            }
            Surface(
                onClick = { pickerOpen = true },
                color = BarberSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BarberOutline)
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CalendarMonth, null, tint = BarberGold)
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text("Data de nascimento (opcional)", color = BarberTextSecondary)
                        Text(
                            birthDate.takeIf { it.isNotBlank() }
                                ?.let { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("dd/MM/yyyy", br)) }
                                ?: "Selecionar data",
                            color = BarberTextPrimary
                        )
                    }
                }
            }
            if (birthDate.isNotBlank()) {
                TextButton(onClick = { birthDate = "" }) { Text("Limpar data", color = BarberGold) }
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it.take(4000) },
                label = { Text("Observações (opcional)") },
                leadingIcon = { Icon(Icons.Outlined.Notes, null) },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
