package com.example.util

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate

/**
 * libmpv / FFmpeg on Android has no system CA folder it can read (no /etc/ssl/certs), so with "tls-verify=yes" and no
 * "tls-ca-file" EVERY https stream fails the certificate check: no data is downloaded and the player sits on
 * "Buffering" forever. mpvRx avoids this by handing mpv a cacert.pem (PlaybackSession: tls-ca-file).
 *
 * This builds the same kind of PEM bundle from the phone's own trust store (AndroidCAStore), once, into
 * filesDir/cacert.pem. It is rebuilt when missing or older than a week, so CA updates of the phone are picked up.
 */
object TlsCaBundle {
    private const val TAG = "TlsCaBundle"
    private const val FILE_NAME = "cacert.pem"
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000L

    @Volatile private var cachedPath: String? = null

    /** Absolute path of the PEM bundle, or null when it could not be built (callers then skip certificate checks). */
    fun path(context: Context): String? {
        cachedPath?.let { if (File(it).length() > 0L) return it }
        synchronized(this) {
            cachedPath?.let { if (File(it).length() > 0L) return it }
            val target = File(context.applicationContext.filesDir, FILE_NAME)
            val fresh = target.isFile && target.length() > 0L &&
                System.currentTimeMillis() - target.lastModified() < MAX_AGE_MS
            if (fresh) {
                cachedPath = target.absolutePath
                return cachedPath
            }
            val built = build(target)
            if (built) {
                cachedPath = target.absolutePath
                return cachedPath
            }
            // an older bundle is still better than none
            if (target.isFile && target.length() > 0L) {
                cachedPath = target.absolutePath
                return cachedPath
            }
            return null
        }
    }

    private fun build(target: File): Boolean {
        return try {
            val store = KeyStore.getInstance("AndroidCAStore")
            store.load(null, null)
            val sb = StringBuilder(256 * 1024)
            var count = 0
            val aliases = store.aliases()
            while (aliases.hasMoreElements()) {
                val cert = try { store.getCertificate(aliases.nextElement()) as? X509Certificate } catch (_: Throwable) { null }
                    ?: continue
                val der = try { cert.encoded } catch (_: Throwable) { continue }
                sb.append("-----BEGIN CERTIFICATE-----\n")
                sb.append(Base64.encodeToString(der, Base64.NO_WRAP).chunked(64).joinToString("\n"))
                sb.append("\n-----END CERTIFICATE-----\n")
                count++
            }
            if (count == 0) return false
            val tmp = File(target.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(sb.toString())
            if (!tmp.renameTo(target)) {
                target.writeText(sb.toString())
                tmp.delete()
            }
            Log.d(TAG, "CA bundle written: $count certificates")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "could not build the CA bundle", t)
            false
        }
    }
}
