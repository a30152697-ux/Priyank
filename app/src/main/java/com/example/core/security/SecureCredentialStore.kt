package com.example.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.data.ai.AiProviderType
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores Owner-configured API keys & proxy bearer tokens locally using
 * Android Keystore AES-256-GCM hardware-backed encryption where available.
 * Never logs, hardcodes, or exports secrets in backups.
 */
class SecureCredentialStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveApiKey(provider: AiProviderType, apiKey: String) {
        val trimmed = apiKey.trim()
        val key = keyName(provider)
        if (trimmed.isEmpty()) {
            prefs.edit().remove(key).apply()
            return
        }
        val encrypted = encrypt(trimmed)
        prefs.edit().putString(key, encrypted).apply()
    }

    fun getApiKey(provider: AiProviderType): String {
        val stored = prefs.getString(keyName(provider), null) ?: return ""
        return runCatching { decrypt(stored) }.getOrDefault("")
    }

    fun hasApiKey(provider: AiProviderType): Boolean = getApiKey(provider).isNotBlank()

    fun clearApiKey(provider: AiProviderType) {
        prefs.edit().remove(keyName(provider)).apply()
    }

    fun saveProxyAuthToken(token: String) {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) {
            prefs.edit().remove(KEY_PROXY_TOKEN).apply()
            return
        }
        prefs.edit().putString(KEY_PROXY_TOKEN, encrypt(trimmed)).apply()
    }

    fun getProxyAuthToken(): String {
        val stored = prefs.getString(KEY_PROXY_TOKEN, null) ?: return ""
        return runCatching { decrypt(stored) }.getOrDefault("")
    }

    fun maskedKeyPreview(provider: AiProviderType): String {
        val key = getApiKey(provider)
        if (key.isBlank()) return "Not configured"
        if (key.length <= 8) return "••••••••"
        return "${key.take(4)}••••••${key.takeLast(4)}"
    }

    private fun keyName(provider: AiProviderType): String = "enc_key_${provider.name}"

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val dataB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        return "$ivB64:$dataB64"
    }

    private fun decrypt(payload: String): String {
        val parts = payload.split(":", limit = 2)
        if (parts.size != 2) return ""
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
        return String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
    }

    companion object {
        private const val PREFS_NAME = "sukoon_encrypted_credentials"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "sukoon_api_master_key_v1"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_PROXY_TOKEN = "enc_proxy_bearer"
    }
}
