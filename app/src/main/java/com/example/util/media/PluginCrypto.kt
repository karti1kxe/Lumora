package com.example.util.media

import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Native crypto for plugin_runtime.js (`providerContext.Aes`, `Crypto`, and the `crypto-js` shim). Requests and answers are
 * JSON; every binary value travels as base64. Request: {op: digest|hmac|aes|pbkdf2, ...} -> {result: base64} or {error}.
 */
object PluginCrypto {
    private fun b64(s: String): ByteArray = Base64.decode(s, Base64.DEFAULT)
    private fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)

    fun run(reqJson: String): String {
        return try {
            val r = JSONObject(reqJson)
            val out: ByteArray = when (r.getString("op")) {
                "digest" -> MessageDigest.getInstance(r.getString("alg")).digest(b64(r.optString("dataB64", "")))
                "hmac" -> {
                    val alg = r.getString("alg")
                    val mac = Mac.getInstance(alg)
                    val key = b64(r.optString("keyB64", ""))
                    mac.init(SecretKeySpec(if (key.isEmpty()) ByteArray(1) else key, alg))
                    mac.doFinal(b64(r.optString("dataB64", "")))
                }
                "aes" -> aes(r)
                "pbkdf2" -> pbkdf2(r)
                else -> throw IllegalArgumentException("unsupported crypto op")
            }
            JSONObject().put("result", b64(out)).toString()
        } catch (t: Throwable) {
            JSONObject().put("error", t.message ?: t.javaClass.simpleName).toString()
        }
    }

    private fun aes(r: JSONObject): ByteArray {
        val mode = r.optString("mode", "CBC").uppercase()
        val padding = r.optString("padding", if (mode == "CBC" || mode == "ECB") "PKCS5Padding" else "NoPadding")
        val encrypt = r.optBoolean("encrypt", true)
        val key = b64(r.getString("keyB64"))
        val iv = b64(r.optString("ivB64", ""))
        val data = b64(r.optString("dataB64", ""))
        val cipher = Cipher.getInstance("AES/$mode/$padding")
        val opmode = if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE
        val spec = SecretKeySpec(key, "AES")
        when (mode) {
            "ECB" -> cipher.init(opmode, spec)
            "GCM" -> cipher.init(opmode, spec, GCMParameterSpec(r.optInt("tagBits", 128), iv))
            else -> cipher.init(opmode, spec, IvParameterSpec(iv))
        }
        val aad = r.optString("aadB64", "")
        if (mode == "GCM" && aad.isNotEmpty()) cipher.updateAAD(b64(aad))
        return cipher.doFinal(data)
    }

    private fun pbkdf2(r: JSONObject): ByteArray {
        val alg = when (r.optString("alg", "sha512").lowercase()) {
            "sha1" -> "PBKDF2WithHmacSHA1"
            "sha256" -> "PBKDF2WithHmacSHA256"
            else -> "PBKDF2WithHmacSHA512"
        }
        // the password / salt are text: PBEKeySpec takes chars, so the UTF-8 bytes come back as chars
        val password = String(b64(r.getString("passwordB64")), Charsets.UTF_8).toCharArray()
        val spec = PBEKeySpec(password, b64(r.getString("saltB64")), r.getInt("cost"), r.optInt("bits", 256))
        return SecretKeyFactory.getInstance(alg).generateSecret(spec).encoded
    }

    @Suppress("unused")
    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { SecureRandom().nextBytes(it) }
}
