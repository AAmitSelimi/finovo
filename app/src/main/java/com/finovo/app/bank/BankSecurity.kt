package com.finovo.app.bank

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted storage for the tokens of the bank connection.
 *
 * Bank user names and passwords are NEVER stored - the app only keeps an
 * access token for its own backend. That token is encrypted with an AES-GCM
 * key that lives inside the Android keystore and never leaves the device.
 */
object BankSecurity {

    private const val PREFS = "finovo_bank_secure"
    private const val ALIAS = "finovo_bank_key"
    private const val KEY_BACKEND_URL = "backend_url"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val DEFAULT_BACKEND = "https://bank.finovo.app"

    fun saveBackendUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_BACKEND_URL, url.trim()).apply()
    }

    fun backendUrl(context: Context): String {
        val stored = prefs(context).getString(KEY_BACKEND_URL, null)
        return if (stored.isNullOrBlank()) DEFAULT_BACKEND else stored
    }

    fun saveAccessToken(context: Context, token: String) {
        val encrypted = encrypt(context, token)
        prefs(context).edit().putString(KEY_ACCESS_TOKEN, encrypted).apply()
    }

    fun accessToken(context: Context): String {
        val stored = prefs(context).getString(KEY_ACCESS_TOKEN, null) ?: return ""
        return decrypt(context, stored)
    }

    /** Removes every token - used when the user disconnects the account. */
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        runCatching {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            ks.deleteEntry(ALIAS)
        }
    }

    fun hasToken(context: Context): Boolean =
        !prefs(context).getString(KEY_ACCESS_TOKEN, null).isNullOrBlank()

    // ------------------------------------------------------------------ crypto

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        ks.getKey(ALIAS, null)?.let { return it as SecretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(context: Context, plain: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(iv + data, Base64.DEFAULT)
        } catch (e: Exception) {
            ""
        }
    }

    private fun decrypt(context: Context, blob: String): String {
        return try {
            val raw = Base64.decode(blob, Base64.DEFAULT)
            val iv = raw.copyOfRange(0, 12)
            val data = raw.copyOfRange(12, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}
