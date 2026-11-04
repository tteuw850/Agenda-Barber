package com.agendabarber.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agendabarber.app.ui.theme.BarberBackground
import com.agendabarber.app.ui.theme.BarberGold
import com.agendabarber.app.ui.theme.BarberOnGold
import com.agendabarber.app.ui.theme.BarberOutline
import com.agendabarber.app.ui.theme.BarberTextPrimary
import com.agendabarber.app.ui.theme.BarberTextSecondary
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Profile is private to this device and JWT subject; no server profile API exists yet. */
internal data class BarberLocalProfile(
    val name: String = "",
    val businessName: String = "",
    val phone: String = "",
    val email: String = "",
    val notes: String = "",
    val photoPath: String? = null,
    val photoRevision: Long = 0L
)

internal fun validBarberProfile(profile: BarberLocalProfile): Boolean = profile.name.trim().length in 2..100 &&
    profile.businessName.trim().length <= 100 &&
    profile.notes.trim().length <= 500 &&
    (profile.phone.isBlank() || profile.phone.filter(Char::isDigit).length in 10..11) &&
    (
        profile.email.isBlank() ||
            (profile.email.length <= 254 && Patterns.EMAIL_ADDRESS.matcher(profile.email.trim()).matches())
        )

internal class BarberProfileStore(private val context: Context, token: String) {
    private val scope: String = run {
        val jwtSubject = runCatching {
            val payload = token.removePrefix("Bearer ").split(".")[1]
            val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
            JSONObject(json).optString("sub")
        }.getOrNull().orEmpty()
        // If the JWT cannot be parsed, isolate this session rather than sharing data across users.
        val key = jwtSubject.ifBlank { token }
        MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it) }
    }
    private val prefs = context.getSharedPreferences("barber_profile_$scope", Context.MODE_PRIVATE)
    private val avatar = File(context.filesDir, "barber-profile-$scope.jpg")

    fun load() = BarberLocalProfile(
        name = prefs.getString("name", "").orEmpty(),
        businessName = prefs.getString("business_name", "").orEmpty(),
        phone = prefs.getString("phone", "").orEmpty(),
        email = prefs.getString("email", "").orEmpty(),
        notes = prefs.getString("notes", "").orEmpty(),
        photoPath = avatar.takeIf { it.exists() }?.absolutePath,
        photoRevision = avatar.takeIf { it.exists() }?.lastModified() ?: 0L
    )

    fun save(profile: BarberLocalProfile, newPhoto: Uri?, removePhoto: Boolean): BarberLocalProfile {
        require(validBarberProfile(profile)) { "Dados de perfil inválidos" }
        if (newPhoto != null) {
            val bitmap = decodeProfileBitmap(context, newPhoto) ?: throw IOException("Imagem inválida")
            val temp = File(context.filesDir, "barber-profile-$scope.tmp")
            try {
                temp.outputStream().use { stream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)) {
                        throw IOException("Não foi possível salvar a imagem")
                    }
                }
                if (!temp.renameTo(avatar)) {
                    temp.copyTo(avatar, overwrite = true)
                }
            } finally {
                temp.delete()
                bitmap.recycle()
            }
        } else if (removePhoto) {
            if (avatar.exists() && !avatar.delete()) throw IOException("Não foi possível remover a foto")
        }
        val stored = prefs.edit()
            .putString("name", profile.name.trim())
            .putString("business_name", profile.businessName.trim())
            .putString("phone", profile.phone.trim())
            .putString("email", profile.email.trim())
            .putString("notes", profile.notes.trim())
            .commit()
        if (!stored) throw IOException("Não foi possível salvar o perfil")
        return load()
    }
}

private fun decodeProfileBitmap(context: Context, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 ||
        bounds.outHeight <= 0 ||
        bounds.outWidth > 16384 ||
        bounds.outHeight > 16384
    ) {
        return null
    }
    var sample = 1
    while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > 1024) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: return null
    val factor = minOf(1f, 512f / maxOf(decoded.width, decoded.height))
    if (factor >= 1f) return decoded
    val reduced = Bitmap.createScaledBitmap(
        decoded,
        (decoded.width * factor).toInt().coerceAtLeast(1),
        (decoded.height * factor).toInt().coerceAtLeast(1),
        true
    )
    decoded.recycle()
    return reduced
}

@Composable
internal fun BarberProfileAvatar(photoPath: String?, photoRevision: Long, size: Dp, modifier: Modifier = Modifier) {
    val photo = remember(photoPath, photoRevision) {
        photoPath?.let { path -> runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() }
    }
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = BarberBackground,
        border = BorderStroke(1.dp, BarberOutline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (photo != null) {
                Image(
                    photo,
                    "Foto do barbeiro",
                    Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Outlined.PersonOutline, "Foto não cadastrada", tint = BarberGold)
            }
        }
    }
}

@Composable
internal fun BarberProfileScreen(
    store: BarberProfileStore,
    initial: BarberLocalProfile,
    onBack: () -> Unit,
    onSaved: (BarberLocalProfile) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var businessName by rememberSaveable { mutableStateOf(initial.businessName) }
    var phone by rememberSaveable { mutableStateOf(initial.phone) }
    var email by rememberSaveable { mutableStateOf(initial.email) }
    var notes by rememberSaveable { mutableStateOf(initial.notes) }
    var newPhoto by remember { mutableStateOf<Uri?>(null) }
    var removePhoto by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            newPhoto = uri
            removePhoto = false
            error = ""
        }
    }
    val photoPreview = remember(newPhoto) {
        newPhoto?.let { uri ->
            runCatching { decodeProfileBitmap(context, uri)?.asImageBitmap() }.getOrNull()
        }
    }
    val changedProfile = initial.copy(
        name = name,
        businessName = businessName,
        phone = phone,
        email = email,
        notes = notes
    )

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .imePadding().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !saving) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar", tint = BarberTextPrimary)
            }
            Text("Editar perfil", style = MaterialTheme.typography.headlineSmall, color = BarberTextPrimary)
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (photoPreview != null && !removePhoto) {
                Image(
                    photoPreview,
                    "Nova foto de perfil",
                    Modifier.size(112.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                BarberProfileAvatar(
                    if (removePhoto) null else initial.photoPath,
                    initial.photoRevision,
                    112.dp
                )
            }
            TextButton(
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = !saving
            ) {
                Icon(Icons.Outlined.CameraAlt, null, tint = BarberGold)
                Spacer(Modifier.size(8.dp))
                Text("Escolher foto", color = BarberGold)
            }
            if (newPhoto != null || initial.photoPath != null) {
                TextButton(onClick = {
                    newPhoto = null
                    removePhoto = true
                }, enabled = !saving) {
                    Text("Remover foto", color = BarberTextSecondary)
                }
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(100) },
            label = { Text("Nome completo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving
        )
        OutlinedTextField(
            value = businessName,
            onValueChange = { businessName = it.take(100) },
            label = { Text("Nome da barbearia (opcional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving
        )
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it.take(20) },
            label = { Text("Telefone / WhatsApp (opcional)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it.take(254) },
            label = { Text("E-mail (opcional)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it.take(500) },
            label = { Text("Sobre o barbeiro (opcional)") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving
        )
        Text(
            "Estes dados e a foto são salvos apenas neste dispositivo. " +
                "A sincronização do perfil com a API ainda não está disponível.",
            color = BarberTextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Button(
            onClick = {
                saving = true
                error = ""
                scope.launch {
                    try {
                        val saved = withContext(Dispatchers.IO) {
                            store.save(changedProfile, newPhoto, removePhoto)
                        }
                        onSaved(saved)
                    } catch (failure: Exception) {
                        error = "Não foi possível salvar o perfil. Verifique os campos e a foto."
                    } finally {
                        saving = false
                    }
                }
            },
            enabled = validBarberProfile(changedProfile) && !saving,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BarberGold)
        ) {
            if (saving) {
                CircularProgressIndicator(Modifier.size(20.dp))
            } else {
                Text("Salvar perfil", color = BarberOnGold, fontWeight = FontWeight.Bold)
            }
        }
    }
}
