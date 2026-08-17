package com.localcharacter.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** API credentials are encrypted with a non-exportable Android Keystore AES key. */
class ApiCredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    fun save(providerId: String, apiKey: String) = synchronized(lock) {
        val normalized = apiKey.trim()
        require(normalized.isNotEmpty()) { "La API Key está vacía." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(normalized.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        preferences.edit().putString(storageKey(providerId), Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
    }

    fun get(providerId: String): String? = synchronized(lock) {
        val encoded = preferences.getString(storageKey(providerId), null) ?: return@synchronized null
        runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            require(payload.size > IV_SIZE_BYTES)
            val iv = payload.copyOfRange(0, IV_SIZE_BYTES)
            val encrypted = payload.copyOfRange(IV_SIZE_BYTES, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        }.getOrElse {
            preferences.edit().remove(storageKey(providerId)).apply()
            null
        }
    }

    fun contains(providerId: String): Boolean = get(providerId) != null
    fun delete(providerId: String) = preferences.edit().remove(storageKey(providerId)).apply()

    fun masked(providerId: String): String? = get(providerId)?.let(::mask)

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun storageKey(providerId: String) = "credential_${providerId.replace(Regex("[^a-zA-Z0-9_.-]"), "_")}"

    companion object {
        private const val PREFERENCES_NAME = "api_credentials_encrypted"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "local_character_api_credentials_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE_BYTES = 12

        fun mask(value: String): String {
            if (value.length <= 6) return "••••••"
            return value.take(3) + "••••••••••••" + value.takeLast(3)
        }
    }
}

