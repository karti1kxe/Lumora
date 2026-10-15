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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
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
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val UPSTREAM_URLS = "https://raw.githubusercontent.com/Zenda-Cross/vega-providers/refs/heads/main/urls.json"
    private const val MAX_BODY_BYTES = 12L * 1024 * 1024

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
                                view.evaluateJavascript(runtimeJs) { loaded.complete(Unit) }
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
                "get" -> prefs.getString(prefix + key, null) ?: "null"
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
        val req = JSONObject(reqJson)
        val url = req.getString("url")
        val method = req.optString("method", "GET").uppercase()
        val follow = req.optBoolean("follow", true)
        val binary = req.optBoolean("binary", false)
        val timeoutSec = (req.optLong("timeout", 0L) / 1000L).coerceIn(0L, 90L)

        val builder = Request.Builder().url(url)
        var hasUa = false
        var contentType: String? = null
        val headers = req.optJSONObject("headers")
        if (headers != null) {
            val names = headers.keys()
            while (names.hasNext()) {
                val name = names.next()
                val lower = name.lowercase()
                // Accept-Encoding is left to OkHttp so the body is decompressed for us.
                if (lower == "accept-encoding" || lower == "host" || lower == "content-length" || lower == "connection") continue
                if (lower == "user-agent") hasUa = true
                if (lower == "content-type") contentType = headers.getString(name)
                try { builder.header(name, headers.getString(name)) } catch (_: Throwable) { }
            }
        }
        if (!hasUa) builder.header("User-Agent", DEFAULT_UA)

        val bodyStr = if (req.isNull("body")) null else req.optString("body")
        if (method == "GET" || method == "HEAD") {
            builder.method(method, null)
        } else {
            val mt = (contentType ?: "application/x-www-form-urlencoded").toMediaTypeOrNull()
            builder.method(method, (bodyStr ?: "").toRequestBody(mt))
        }

        var c = client.newBuilder().followRedirects(follow).followSslRedirects(follow)
        if (timeoutSec > 0) c = c.callTimeout(timeoutSec, TimeUnit.SECONDS).readTimeout(timeoutSec, TimeUnit.SECONDS)
        return c.build().newCall(builder.build()).execute().use { resp ->
            val out = JSONObject()
            out.put("status", resp.code)
            out.put("statusText", resp.message)
            out.put("url", resp.request.url.toString())
            val hmap = JSONObject()
            for (name in resp.headers.names()) {
                hmap.put(name.lowercase(), resp.headers.values(name).joinToString(", "))
            }
            out.put("headers", hmap)
            val body = resp.body
            if (body != null) {
                val declared = body.contentLength()
                if (declared > MAX_BODY_BYTES) throw java.io.IOException("Response is too large")
                val bytes = body.bytes()
                if (binary) {
                    out.put("bodyBase64", Base64.encodeToString(bytes, Base64.NO_WRAP))
                } else {
                    val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                    out.put("body", String(bytes, charset))
                }
            } else {
                out.put("body", "")
            }
            out.toString()
        }
    }
}
