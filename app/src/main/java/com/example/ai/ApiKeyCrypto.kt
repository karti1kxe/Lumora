package com.example.ai

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the user's AI provider API key at rest using an Android Keystore AES key,
 * so the key never sits in SharedPreferences as plain text. No new Gradle dependency
 * needed (uses the platform Keystore directly instead of androidx.security).
 */
object ApiKeyCrypto {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "lumora_ai_api_key_alias"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
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

    /** Returns "base64(iv):base64(ciphertext)", or null if [plainText] is blank. */
    fun encrypt(plainText: String): String? {
        if (plainText.isBlank()) return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv
            "${Base64.encodeToString(iv, Base64.NO_WRAP)}:${Base64.encodeToString(cipherBytes, Base64.NO_WRAP)}"
        } catch (_: Throwable) {
            null
        }
    }

    fun decrypt(stored: String?): String {
        if (stored.isNullOrBlank() || !stored.contains(":")) return ""
        return try {
            val (ivPart, cipherPart) = stored.split(":", limit = 2)
            val iv = Base64.decode(ivPart, Base64.NO_WRAP)
            val cipherBytes = Base64.decode(cipherPart, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (_: Throwable) {
            ""
        }
    }
}
