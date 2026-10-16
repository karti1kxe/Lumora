package com.example.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * Network settings that run in the background while a provider (media plugin) page or video is opened:
 *  - AntiDPI Mode: a tiny local HTTP/CONNECT proxy (127.0.0.1:<port>) that splits the TLS "ClientHello" (and the first
 *    HTTP request) into pieces, so an ISP's DPI box cannot read the site name and cut the connection. No VPN is used.
 *  - DNS over HTTPS: host names are resolved through Cloudflare / Google instead of the (often poisoned) ISP resolver.
 * Both the plugin engine (OkHttp) and the player (mpv / ffmpeg `http_proxy`) go through that proxy.
 */
object NetworkBypass {
    private const val PREFS = "lumora_network_bypass"
    private const val KEY_ANTIDPI = "antidpi"
    private const val KEY_DOH = "doh"
    private const val KEY_DOH_PROVIDER = "doh_provider"
    private const val KEY_METHOD = "method"

    const val METHOD_TLS_AND_TCP = 0
    const val METHOD_TCP_ONLY = 1

    class DohProvider(val label: String, val url: String)

    val dohProviders: List<DohProvider> = listOf(
        DohProvider("Cloudflare (1.1.1.1)", "https://1.1.1.1/dns-query"),
        DohProvider("Google (8.8.8.8)", "https://8.8.8.8/resolve")
    )

    private val _activePort = MutableStateFlow(0)
    /** Port of the running local proxy, 0 = not running. Shown as "Active :port" in the settings. */
    val activePort: StateFlow<Int> = _activePort.asStateFlow()

    @Volatile private var proxy: LocalBypassProxy? = null
    private val lock = Any()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Default ON: blocked provider sites are the usual reason a stream only buffers. The proxy falls back to a plain
    // connection by itself when the split connection is refused, so turning it on never makes a working site worse.
    fun isAntiDpiEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ANTIDPI, true)
    fun isDohEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_DOH, true)
    fun dohProviderIndex(context: Context): Int = prefs(context).getInt(KEY_DOH_PROVIDER, 0).coerceIn(0, dohProviders.size - 1)
    fun method(context: Context): Int = prefs(context).getInt(KEY_METHOD, METHOD_TLS_AND_TCP)

    fun setAntiDpiEnabled(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean(KEY_ANTIDPI, enabled).apply(); refresh(context) }
    fun setDohEnabled(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean(KEY_DOH, enabled).apply(); refresh(context) }
    fun setDohProvider(context: Context, index: Int) { prefs(context).edit().putInt(KEY_DOH_PROVIDER, index).apply(); refresh(context) }
    fun setMethod(context: Context, method: Int) { prefs(context).edit().putInt(KEY_METHOD, method).apply(); refresh(context) }

    /** Starts / stops / re-configures the local proxy to match the saved settings. Safe to call often. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        val antiDpi = isAntiDpiEnabled(app)
        val doh = isDohEnabled(app)
        synchronized(lock) {
            if (!antiDpi && !doh) {
                proxy?.stop()
                proxy = null
                _activePort.value = 0
                return
            }
            var p = proxy
            if (p == null || !p.isRunning()) {
                p = try {
                    LocalBypassProxy().also { it.start() }
                } catch (_: Throwable) {
                    null
                }
                proxy = p
            }
            if (p != null) {
                p.fragment = antiDpi
                p.tlsRecordSplit = method(app) == METHOD_TLS_AND_TCP
                p.dohUrl = if (doh) dohProviders[dohProviderIndex(app)].url else null
                _activePort.value = p.port
            } else {
                _activePort.value = 0
            }
        }
    }

    /** The running proxy's port (starts it if needed), or 0 when both settings are off / the proxy could not start. */
    fun ensureStarted(context: Context): Int {
        if (proxy?.isRunning() != true) refresh(context) else if (_activePort.value == 0) refresh(context)
        return _activePort.value
    }

    /** java.net.Proxy for OkHttp, or null when requests should go out directly. */
    fun okHttpProxy(context: Context): Proxy? {
        val port = ensureStarted(context)
        return if (port > 0) Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port)) else null
    }

    /** "http_proxy=http://127.0.0.1:<port>" for mpv's stream-lavf-o / demuxer-lavf-o, or null when off. */
    fun ffmpegProxyOption(context: Context): String? {
        val port = ensureStarted(context)
        return if (port > 0) "http_proxy=http://127.0.0.1:$port" else null
    }
}
