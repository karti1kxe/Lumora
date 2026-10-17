package com.example.util.media

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.util.NetworkBypass
import okhttp3.Cookie
import okhttp3.Response
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs Vega provider plugins (compiled JavaScript) inside a hidden WebView. The page gets `fetch`, `axios`, `cheerio`
 * (a DOM based re-implementation) and `kvStore` from assets/media/plugin_runtime.js; every network request and every
 * stored value goes through [Bridge] so it is done natively with OkHttp / SharedPreferences.
 */
object PluginJsEngine {
    private const val TAG = "PluginJsEngine"
    private const val DEFAULT_UA =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private const val UPSTREAM_URLS = "https://raw.githubusercontent.com/Zenda-Cross/vega-providers/refs/heads/main/urls.json"
    private const val MAX_BODY_BYTES = 12L * 1024 * 1024
    private const val MAX_REDIRECTS = 10
    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

    /**
     * Vega's `commonHeaders` (src/lib/providers/headers.ts, Android): merged under every provider request and given to the
     * provider as providerContext.commonHeaders. It must stay exactly Vega's small set: the browser-navigation headers that
     * used to be added here (Accept: text/html, Sec-Fetch-Mode: navigate, Upgrade-Insecure-Requests ...) were sent with the
     * JSON / AJAX / POST calls of the providers too, so many hosts answered with a web page instead of the link.
     */
    private val COMMON_HEADERS: Map<String, String> = linkedMapOf(
        "sec-ch-ua" to "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"",
        "sec-ch-ua-mobile" to "?1",
        "sec-ch-ua-platform" to "\"Android\"",
        "User-Agent" to DEFAULT_UA,
        "Accept-Language" to "en-US,en;q=0.9"
    )

    /** Host -> true when its requests only worked through the AntiDPI / DoH proxy (false: direct). The next request starts that way. */
    private val hostUsesProxy = ConcurrentHashMap<String, Boolean>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initMutex = Mutex()
    private val callSeq = AtomicInteger(0)
    private val calls = ConcurrentHashMap<Int, CompletableDeferred<String>>()
    private val httpResults = ConcurrentHashMap<Int, String>()

    @Volatile private var webView: WebView? = null
    @Volatile private var ready = false
    @Volatile private var appContext: Context? = null

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .cookieJar(PluginCookieJar)
        .build()

    /** Runs `module.fnName(args)` of a plugin. Returns the JSON-encoded result; throws [PluginException] on failure. */
    suspend fun call(
        context: Context,
        pluginId: String,
        module: String,
        fnName: String,
        argsJson: String = "{}",
        timeoutMs: Long = 90_000L
    ): String {
        ensureReady(context)
        val id = callSeq.incrementAndGet()
        val deferred = CompletableDeferred<String>()
        calls[id] = deferred
        val script = "__runPlugin($id,${JSONObject.quote(pluginId)},${JSONObject.quote(module)}," +
            "${JSONObject.quote(fnName)},${JSONObject.quote(argsJson)})"
        try {
            withContext(Dispatchers.Main) { webView?.evaluateJavascript(script, null) }
            return withTimeout(timeoutMs) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            throw PluginException("The site took too long to respond")
        } finally {
            calls.remove(id)
        }
    }

    /** A repository was (re)loaded: forget every cached module and every cached provider URL list (domains change often). */
    fun resetAllCaches() {
        val wv = webView ?: return
        mainHandler.post { wv.evaluateJavascript("if(window.__dropAllPluginCaches)__dropAllPluginCaches()", null) }
    }

    /** GitHub raw answers are cached by its CDN for minutes: files that decide where a provider's site is get a fresh copy. */
    private fun freshUrl(url: String): String {
        val u = url.toHttpUrlOrNull() ?: return url
        if (!u.host.equals("raw.githubusercontent.com", true)) return url
        return u.newBuilder().setQueryParameter("t", System.currentTimeMillis().toString()).build().toString()
    }

    /** Forget the JS modules cached for [pluginId] (after it was downloaded again or removed). */
    fun invalidate(pluginId: String) {
        val wv = webView ?: return
        mainHandler.post { wv.evaluateJavascript("if(window.__dropPluginCache)__dropPluginCache(${JSONObject.quote(pluginId)})", null) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun ensureReady(context: Context) {
        if (ready) return
        initMutex.withLock {
            if (ready) return
            val app = context.applicationContext
            appContext = app
            val runtimeJs = withContext(Dispatchers.IO) {
                app.assets.open("media/plugin_runtime.js").bufferedReader().use { it.readText() }
            }
            val extractorsJs = withContext(Dispatchers.IO) {
                try { app.assets.open("media/plugin_extractors.js").bufferedReader().use { it.readText() } } catch (_: Throwable) { "" }
            }
            PluginCookieJar.attach(app)
            val loaded = CompletableDeferred<Unit>()
            try {
                withContext(Dispatchers.Main) {
                    val wv = WebView(app)
                    wv.settings.javaScriptEnabled = true
                    wv.settings.allowFileAccess = false
                    wv.settings.allowContentAccess = false
                    wv.settings.blockNetworkLoads = true
                    wv.addJavascriptInterface(Bridge(), "LumoraHost")
                    wv.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            if (!loaded.isCompleted) {
                                view.evaluateJavascript(runtimeJs) {
                                    if (extractorsJs.isEmpty()) loaded.complete(Unit)
                                    else view.evaluateJavascript(extractorsJs) { loaded.complete(Unit) }
                                }
                            }
                        }
                    }
                    webView = wv
                    wv.loadDataWithBaseURL(
                        "https://lumora.local/",
                        "<html><head><meta charset=\"utf-8\"></head><body></body></html>",
                        "text/html",
                        "utf-8",
                        null
                    )
                }
                withTimeout(20_000L) { loaded.await() }
                ready = true
            } catch (t: Throwable) {
                webView = null
                throw PluginException("Could not start the plugin engine (" + (t.message ?: t.javaClass.simpleName) + ")")
            }
        }
    }

    /** Methods called from JavaScript (names must match plugin_runtime.js). Runs on WebView's JavaBridge thread. */
    class Bridge {
        @JavascriptInterface
        fun httpAsync(id: Int, reqJson: String) {
            ioScope.launch {
                val result = try { doHttp(reqJson) } catch (t: Throwable) {
                    JSONObject().put("error", t.message ?: t.javaClass.simpleName).toString()
                }
                httpResults[id] = result
                mainHandler.post { webView?.evaluateJavascript("__hostResolve($id)", null) }
            }
        }

        /** providerContext.openWebView: hidden WebView browser check (answer comes back like an http result). */
        @JavascriptInterface
        fun webViewAsync(id: Int, url: String, optionsJson: String) {
            ioScope.launch {
                val result = try {
                    PluginWebViewSolver.solve(appContext ?: throw PluginException("not ready"), url, optionsJson, DEFAULT_UA)
                } catch (t: Throwable) {
                    JSONObject().put("error", t.message ?: t.javaClass.simpleName).toString()
                }
                httpResults[id] = result
                mainHandler.post { webView?.evaluateJavascript("__hostResolve($id)", null) }
            }
        }

        /** providerContext.getCookies: the jar's cookies for [url] as a JSON name -> value map. */
        @JavascriptInterface
        fun getCookies(url: String): String {
            val m = JSONObject()
            for ((k, v) in PluginCookieJar.cookieMap(url)) m.put(k, v)
            return m.toString()
        }

        @JavascriptInterface
        fun crypto(opJson: String): String = PluginCrypto.run(opJson)

        @JavascriptInterface
        fun commonHeaders(): String {
            val o = JSONObject()
            for ((k, v) in COMMON_HEADERS) o.put(k, v)
            return o.toString()
        }

        @JavascriptInterface
        fun takeResult(id: Int): String = httpResults.remove(id) ?: ""

        @JavascriptInterface
        fun getModule(plugin: String, module: String): String {
            val ctx = appContext ?: return ""
            return MediaPluginStore.readModule(ctx, plugin, module).orEmpty()
        }

        @JavascriptInterface
        fun finish(callId: Int, ok: Boolean, payload: String) {
            val d = calls.remove(callId) ?: return
            if (ok) d.complete(payload) else d.completeExceptionally(PluginException(payload))
        }

        @JavascriptInterface
        fun log(message: String) {
            Log.w(TAG, message)
        }

        @JavascriptInterface
        fun urlsEndpoint(): String {
            val ctx = appContext ?: return UPSTREAM_URLS
            val base = MediaPluginStore.repoBase(ctx)
            return if (base != null && base.startsWith("https://raw.githubusercontent.com/")) "$base/urls.json" else UPSTREAM_URLS
        }

        @JavascriptInterface
        fun kv(plugin: String, op: String, key: String, valueJson: String): String {
            val ctx = appContext ?: return "null"
            val prefs = ctx.getSharedPreferences("media_plugin_kv", Context.MODE_PRIVATE)
            val prefix = "$plugin|"
            return when (op) {
                "get" -> prefs.getString(prefix + key, null) ?: ""
                "set" -> { prefs.edit().putString(prefix + key, valueJson).apply(); "null" }
                "delete" -> {
                    val had = prefs.contains(prefix + key)
                    prefs.edit().remove(prefix + key).apply()
                    had.toString()
                }
                "keys" -> {
                    val arr = JSONArray()
                    prefs.all.keys.filter { it.startsWith(prefix) }.forEach { arr.put(it.removePrefix(prefix)) }
                    arr.toString()
                }
                "clear" -> {
                    val e = prefs.edit()
                    prefs.all.keys.filter { it.startsWith(prefix) }.forEach { e.remove(it) }
                    e.apply()
                    "null"
                }
                else -> "null"
            }
        }
    }

    private fun doHttp(reqJson: String): String {
        appContext?.let { PluginCookieJar.attach(it) }
        val req = JSONObject(reqJson)
        var url = req.getString("url")
        var method = req.optString("method", "GET").uppercase()
        if (method == "GET") url = freshUrl(url)
        val follow = req.optBoolean("follow", true)
        val binary = req.optBoolean("binary", false)
        val timeoutSec = (req.optLong("timeout", 0L) / 1000L).coerceIn(0L, 90L)

        val headers = LinkedHashMap<String, String>()
        req.optJSONObject("headers")?.let { h ->
            val names = h.keys()
            while (names.hasNext()) {
                val name = names.next()
                val lower = name.lowercase()
                // Accept-Encoding is left to OkHttp so the body is decompressed for us.
                if (lower == "accept-encoding" || lower == "host" || lower == "content-length" || lower == "connection") continue
                val v = h.optString(name, "")
                if (name.isNotBlank()) headers[name] = v
            }
        }
        // Vega merges its common headers under every provider request
        for ((k, v) in COMMON_HEADERS) if (headers.keys.none { it.equals(k, true) }) headers[k] = v

        var bodyBytes: ByteArray? = when {
            !req.isNull("bodyBase64") -> Base64.decode(req.getString("bodyBase64"), Base64.DEFAULT)
            !req.isNull("body") -> req.optString("body").toByteArray(Charsets.UTF_8)
            else -> null
        }
        var contentType: String? = headers.entries.firstOrNull { it.key.equals("content-type", true) }?.value

        // the provider's own Cookie header + the jar's cookies (the provider's win)
        val cookieKey = headers.keys.firstOrNull { it.equals("cookie", true) }
        val supplied = cookieKey?.let { headers[it] }
        if (cookieKey != null) headers.remove(cookieKey)
        var cookieHeader = PluginCookieJar.header(url, supplied)

        var redirects = 0
        while (true) {
            val builder = Request.Builder().url(url)
            for ((k, v) in headers) { try { builder.header(k, v) } catch (_: Throwable) { } }
            if (cookieHeader.isNotEmpty()) builder.header("Cookie", cookieHeader)
            if (method == "GET" || method == "HEAD") {
                builder.method(method, null)
            } else {
                val mt = (contentType ?: "application/x-www-form-urlencoded").toMediaTypeOrNull()
                builder.method(method, (bodyBytes ?: ByteArray(0)).toRequestBody(mt))
            }
            val response = execute(builder.build(), timeoutSec)
            var answer: String? = null
            response.use { resp ->
                val location = resp.header("Location")
                if (follow && resp.code in REDIRECT_CODES && location != null) {
                    if (redirects >= MAX_REDIRECTS) throw IOException("Too many redirects")
                    val prev = resp.request.url
                    val next = prev.resolve(location) ?: throw IOException("Bad redirect location")
                    if (resp.code == 303 || ((resp.code == 301 || resp.code == 302) && method != "GET" && method != "HEAD")) {
                        if (method != "HEAD") method = "GET"
                        bodyBytes = null
                        contentType = null
                        headers.keys.removeAll { it.equals("content-type", true) || it.equals("content-length", true) }
                    }
                    if (!next.host.equals(prev.host, true)) headers.keys.removeAll { it.equals("authorization", true) }
                    cookieHeader = if (next.host.equals(prev.host, true)) {
                        PluginCookieJar.carry(cookieHeader, Cookie.parseAll(prev, resp.headers))
                    } else {
                        PluginCookieJar.header(next.toString(), null)
                    }
                    url = next.toString()
                } else {
                    answer = buildAnswer(resp, binary)
                }
            }
            answer?.let { return it }
            redirects++
        }
    }

    /**
     * One request. Provider sites are tried on a direct connection first (what Vega does); when that fails with a network
     * error and the AntiDPI / DoH proxy is on, the same request goes through the proxy. The route that worked is remembered
     * per host, so the next request of that host starts with it.
     */
    private fun execute(request: Request, timeoutSec: Long): Response {
        val host = request.url.host.lowercase()
        val bypass = appContext?.let { try { NetworkBypass.okHttpProxy(it) } catch (_: Throwable) { null } }
        fun call(proxy: java.net.Proxy?, shortConnect: Boolean): Response {
            var b = client.newBuilder().proxy(proxy)
            if (timeoutSec > 0) b = b.callTimeout(timeoutSec, TimeUnit.SECONDS).readTimeout(timeoutSec, TimeUnit.SECONDS)
            if (shortConnect) b = b.connectTimeout(10, TimeUnit.SECONDS)
            return b.build().newCall(request).execute()
        }
        if (bypass == null) return call(null, false)
        val proxyFirst = hostUsesProxy[host] == true
        return try {
            call(if (proxyFirst) bypass else null, true)
        } catch (_: IOException) {
            val second = call(if (proxyFirst) null else bypass, false)
            hostUsesProxy[host] = !proxyFirst
            second
        }
    }

    private fun buildAnswer(resp: Response, binary: Boolean): String {
        val out = JSONObject()
        out.put("status", resp.code)
        out.put("statusText", resp.message)
        out.put("url", resp.request.url.toString())
        val hmap = JSONObject()
        for (name in resp.headers.names()) {
            if (name.equals("set-cookie", true)) continue
            hmap.put(name.lowercase(), resp.headers.values(name).joinToString(", "))
        }
        out.put("headers", hmap)
        val cookies = JSONArray()
        for (c in resp.headers.values("Set-Cookie")) cookies.put(c)
        out.put("setCookies", cookies)
        val body = resp.body
        if (body != null && resp.request.method != "HEAD") {
            val declared = body.contentLength()
            if (declared > MAX_BODY_BYTES) throw IOException("Response is too large")
            val bytes = body.bytes()
            if (bytes.size > MAX_BODY_BYTES) throw IOException("Response is too large")
            // UTF-8 always (what Vega's Response.text() does)
            if (binary) out.put("bodyBase64", Base64.encodeToString(bytes, Base64.NO_WRAP))
            else out.put("body", String(bytes, Charsets.UTF_8))
        } else {
            if (binary) out.put("bodyBase64", "") else out.put("body", "")
        }
        return out.toString()
    }
}
