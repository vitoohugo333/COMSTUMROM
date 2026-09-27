package com.customrom.adb

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

class GitHubCredentialStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        val clean = token.trim()
        require(clean.isNotEmpty()) { "GitHub token cannot be empty" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
        val payload = JSONObject()
            .put("v", 1)
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .toString()
        check(prefs.edit().putString(KEY_PAYLOAD, payload).commit()) { "Unable to persist GitHub credential" }
    }

    fun loadToken(): String? {
        val payload = prefs.getString(KEY_PAYLOAD, null) ?: return null
        return runCatching {
            val json = JSONObject(payload)
            require(json.optInt("v") == 1) { "Unsupported credential payload" }
            val iv = Base64.decode(json.getString("iv"), Base64.NO_WRAP)
            val encrypted = Base64.decode(json.getString("ciphertext"), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8).takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun clearToken() {
        prefs.edit().remove(KEY_PAYLOAD).apply()
    }

    fun hasToken(): Boolean = loadToken() != null

    private fun getOrCreateKey(): SecretKey {
        getKeyOrNull()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun getKey(): SecretKey = getKeyOrNull()
        ?: throw IllegalStateException("GitHub credential key is unavailable")

    private fun getKeyOrNull(): SecretKey? {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    companion object {
        private const val PREFS = "customrom_remote_control"
        private const val KEY_PAYLOAD = "github_token_v1"
        private const val KEY_ALIAS = "customrom_github_control_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
