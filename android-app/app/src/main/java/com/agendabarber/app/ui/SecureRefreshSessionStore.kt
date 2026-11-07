package com.agendabarber.app.ui

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Persists only the rotating, opaque refresh token. Passwords and access tokens are never saved.
 * The AES key is non-exportable and stays in the Android Keystore for this installation.
 */
internal class SecureRefreshSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "agenda-barber-secure-session",
        Context.MODE_PRIVATE
    )

    fun hasSavedToken(): Boolean = preferences.contains(TOKEN_KEY)

    fun load(): String? {
        val encoded = preferences.getString(TOKEN_KEY, null) ?: return null
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            require(bytes.size > IV_LENGTH)
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, bytes.copyOfRange(0, IV_LENGTH))
            )
            val raw = String(
                cipher.doFinal(bytes.copyOfRange(IV_LENGTH, bytes.size)),
                StandardCharsets.UTF_8
            )
            if (raw.matches(REFRESH_TOKEN_FORMAT)) raw else null.also { clear() }
        } catch (ignored: Exception) {
            // Corrupted blob or invalidated device key: never fall back to plaintext.
            clear()
            null
        }
    }

    fun save(rawRefreshToken: String) {
        require(rawRefreshToken.matches(REFRESH_TOKEN_FORMAT)) {
            "Invalid refresh credential"
        }
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(rawRefreshToken.toByteArray(StandardCharsets.UTF_8))
        val blob = cipher.iv + encrypted
        check(
            preferences.edit()
                .putString(TOKEN_KEY, Base64.encodeToString(blob, Base64.NO_WRAP))
                .commit()
        ) { "Unable to persist encrypted session" }
    }

    fun clear() {
        preferences.edit().remove(TOKEN_KEY).commit()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
    }

    private companion object {
        const val TOKEN_KEY = "refresh_credential_v1"
        const val KEY_ALIAS = "agenda_barber_refresh_aes_gcm_v1"
        const val IV_LENGTH = 12
        const val CIPHER = "AES/GCM/NoPadding"
        val REFRESH_TOKEN_FORMAT = Regex("[A-Za-z0-9_-]{43}")
    }
}
