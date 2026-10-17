package com.example.util.media

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.ConcurrentHashMap

/**
 * `providerContext.openWebView`: a hidden WebView opens the page (Cloudflare / browser check), waits until the check is
 * passed - the awaited cookie (e.g. cf_clearance) appears, the provider's injected script posts a result, or the page
 * stops showing a challenge title - and returns the page HTML and the cookies. The cookies also go into [PluginCookieJar]
 * so later requests of the provider send them. A check that needs a tap / captcha cannot be solved while hidden.
 */
object PluginWebViewSolver {
    private val inflight = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val CHALLENGE_TITLES = listOf("just a moment", "attention required", "checking your browser", "verifying", "one more step", "security check")
    private const val SHIM =
        "(function(){if(!window.ReactNativeWebView){window.ReactNativeWebView={postMessage:function(m){try{LumoraWafHost.post(String(m));}catch(e){}}};}})();"

    private class WafBridge(val onMessage: (String) -> Unit) {
        @JavascriptInterface
        fun post(message: String) { onMessage(message) }
    }

    /** Returns the JSON answer {data, cookies, cookie, cookieMap, url, userAgent}; throws when the check was not passed. */
    suspend fun solve(context: Context, url: String, optionsJson: String, defaultUserAgent: String): String {
        val app = context.applicationContext
        val opts = try { JSONObject(optionsJson) } catch (_: Throwable) { JSONObject() }
        val waitCookie = opts.optString("waitForCookie", "").ifBlank { null }
        val host = try { java.net.URI(url).host ?: url } catch (_: Throwable) { url }
        val key = if (waitCookie != null) "$host:$waitCookie" else url
        // requests waiting for the same thing share one WebView
        inflight[key]?.let { return it.await() }
        val mine = CompletableDeferred<String>()
        val existing = inflight.putIfAbsent(key, mine)
        if (existing != null) return existing.await()
        try {
            val result = run(app, url, opts, waitCookie, defaultUserAgent)
            mine.complete(result)
            return result
        } catch (t: Throwable) {
            mine.completeExceptionally(t)
            throw t
        } finally {
            inflight.remove(key)
        }
    }

    private fun userAgentOf(opts: JSONObject, fallback: String): String {
        val h = opts.optJSONObject("headers") ?: return fallback
        val names = h.keys()
        while (names.hasNext()) {
            val n = names.next()
            if (n.equals("user-agent", true)) return h.optString(n, fallback).ifBlank { fallback }
        }
        return fallback
    }

    private fun result(html: String, url: String, ua: String, cookieHeader: String): String {
        val map = JSONObject()
        for (part in cookieHeader.split(';')) {
            val eq = part.indexOf('=')
            if (eq > 0) map.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim())
        }
        return JSONObject()
            .put("data", html)
            .put("cookies", cookieHeader)
            .put("cookie", cookieHeader)
            .put("cookieMap", map)
            .put("url", url)
            .put("userAgent", ua)
            .toString()
    }

    private fun jarCookieHeader(url: String): String =
        PluginCookieJar.cookieMap(url).entries.joinToString("; ") { it.key + "=" + it.value }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun run(app: Context, url: String, opts: JSONObject, waitCookie: String?, defaultUa: String): String {
        PluginCookieJar.attach(app)
        val ua = userAgentOf(opts, defaultUa)
        val force = opts.optBoolean("force", false)
        // the awaited cookie is already there and a fresh check is not demanded: no browser needed
        if (!force && waitCookie != null && PluginCookieJar.cookieMap(url)[waitCookie] != null) {
            return result("", url, ua, jarCookieHeader(url))
        }
        if (waitCookie != null) PluginCookieJar.delete(url, waitCookie) // expired / rejected (it caused the 403)
        val timeoutMs = opts.optLong("timeoutMs", 0L).let { if (it > 0L) it else 45_000L }.coerceIn(5_000L, 120_000L)
        val injected = opts.optString("injectedJavaScript", "").ifBlank { null }

        val done = CompletableDeferred<String>()
        withContext(Dispatchers.Main) {
            val handler = Handler(Looper.getMainLooper())
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            val wv = WebView(app)
            var finished = false
            var pageLoadedAt = 0L
            val startedAt = System.currentTimeMillis()
            var initialValue: String? = null

            fun cookieNow(): String = try { cm.flush(); cm.getCookie(url) ?: "" } catch (_: Throwable) { "" }
            fun cookieValue(header: String, name: String): String? {
                for (part in header.split(';')) {
                    val eq = part.indexOf('=')
                    if (eq > 0 && part.substring(0, eq).trim() == name) return part.substring(eq + 1).trim()
                }
                return null
            }

            fun cleanup() {
                handler.removeCallbacksAndMessages(null)
                try { wv.stopLoading() } catch (_: Throwable) {}
                try { wv.removeJavascriptInterface("LumoraWafHost") } catch (_: Throwable) {}
                try { wv.destroy() } catch (_: Throwable) {}
            }

            fun finish(html: String) {
                if (finished) return
                finished = true
                val cookies = cookieNow()
                if (cookies.isNotEmpty()) PluginCookieJar.putFromWebView(url, cookies)
                cleanup()
                done.complete(result(html, url, ua, cookies))
            }

            fun fail(message: String) {
                if (finished) return
                finished = true
                cleanup()
                done.completeExceptionally(PluginException(message))
            }

            fun grabHtmlThenFinish() {
                if (finished) return
                try {
                    wv.evaluateJavascript("(function(){return document.documentElement.outerHTML})()") { raw ->
                        val html = try {
                            if (raw == null || raw == "null") "" else (JSONTokener(raw).nextValue() as? String).orEmpty()
                        } catch (_: Throwable) { "" }
                        finish(html)
                    }
                } catch (_: Throwable) {
                    finish("")
                }
            }

            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            wv.settings.userAgentString = ua
            cm.setAcceptThirdPartyCookies(wv, true)
            wv.addJavascriptInterface(WafBridge { msg ->
                // window.ReactNativeWebView.postMessage(JSON.stringify({__waf: true, data | token: ...}))
                handler.post {
                    if (finished) return@post
                    try {
                        val o = JSONObject(msg)
                        if (o.optBoolean("__waf", false)) {
                            val payload: Any? = if (o.has("data")) o.get("data") else if (o.has("token")) o.get("token") else null
                            if (payload != null) finish(if (payload is String) payload else payload.toString())
                        }
                    } catch (_: Throwable) {}
                }
            }, "LumoraWafHost")
            wv.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, u: String?, favicon: android.graphics.Bitmap?) {
                    view.evaluateJavascript(SHIM, null)
                }
                override fun onPageFinished(view: WebView, u: String?) {
                    view.evaluateJavascript(SHIM, null)
                    if (injected != null) view.evaluateJavascript("$injected;\ntrue;", null)
                    pageLoadedAt = System.currentTimeMillis()
                }
            }

            // start from the jar's cookies (without the awaited one), so the page sees the same session
            for ((n, v) in PluginCookieJar.cookieMap(url)) {
                if (n != waitCookie) try { cm.setCookie(url, "$n=$v") } catch (_: Throwable) {}
            }
            if (waitCookie != null) {
                // a stale value of the awaited cookie must not count as "solved"
                initialValue = cookieValue(cookieNow(), waitCookie)
            }

            val extra = HashMap<String, String>()
            val hs = opts.optJSONObject("headers")
            if (hs != null) {
                val names = hs.keys()
                while (names.hasNext()) {
                    val n = names.next()
                    val low = n.lowercase()
                    if (low == "user-agent" || low == "cookie" || low == "host" || low == "content-length") continue
                    val v = hs.optString(n, "")
                    if (v.isNotBlank()) extra[n] = v
                }
            }

            val poll = object : Runnable {
                override fun run() {
                    if (finished) return
                    val now = System.currentTimeMillis()
                    if (waitCookie != null) {
                        val v = cookieValue(cookieNow(), waitCookie)
                        if (v != null && v.isNotEmpty() && v != initialValue) { grabHtmlThenFinish(); return }
                    } else if (pageLoadedAt > 0L && now - pageLoadedAt > 1_500L) {
                        val title = (wv.title ?: "").lowercase()
                        if (CHALLENGE_TITLES.none { title.contains(it) }) { grabHtmlThenFinish(); return }
                    }
                    if (now - startedAt > timeoutMs) {
                        if (waitCookie == null && pageLoadedAt > 0L) grabHtmlThenFinish()
                        else fail("The browser check was not passed in time (it may need a manual tap, which a hidden check cannot do)")
                        return
                    }
                    handler.postDelayed(this, 600L)
                }
            }
            try {
                wv.loadUrl(url, extra)
            } catch (t: Throwable) {
                fail("Could not open the page: " + (t.message ?: t.javaClass.simpleName))
                return@withContext
            }
            handler.postDelayed(poll, 800L)
        }
        return done.await()
    }
}
