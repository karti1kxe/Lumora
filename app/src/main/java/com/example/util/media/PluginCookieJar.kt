package com.example.util.media

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

/**
 * Cookie jar of the provider (plugin) requests, matched by domain only (like Vega's providerCookieJar).
 * OkHttp stores every Set-Cookie here ([saveFromResponse]); the Cookie header itself is built by [header] so a Cookie
 * a provider sets on its own request wins over the jar's value (OkHttp would otherwise replace it), so [loadForRequest]
 * deliberately returns nothing. Cookies read from the browser-check WebView go in through [putFromWebView].
 */
object PluginCookieJar : CookieJar {
    private const val PREFS = "media_plugin_cookies"
    private const val KEY = "jar"
    private const val SESSION_COOKIE_MS = 12L * 60L * 60L * 1000L
    private const val MAX_COOKIES = 500

    private class Entry(val value: String, val expiresAt: Long, val hostOnly: Boolean)
    private class Incoming(val name: String, val value: String, val domain: String?, val expiresAt: Long?)

    /** domain -> cookie name -> cookie */
    private val jar = HashMap<String, HashMap<String, Entry>>()
    private var prefs: SharedPreferences? = null
    private var loaded = false

    @Synchronized
    fun attach(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!loaded) {
            loaded = true
            try {
                val text = prefs?.getString(KEY, null)
                if (!text.isNullOrBlank()) {
                    val root = JSONObject(text)
                    val domains = root.keys()
                    while (domains.hasNext()) {
                        val d = domains.next()
                        val cookies = root.getJSONObject(d)
                        val map = HashMap<String, Entry>()
                        val names = cookies.keys()
                        while (names.hasNext()) {
                            val n = names.next()
                            val o = cookies.getJSONObject(n)
                            map[n] = Entry(o.optString("v"), o.optLong("e"), o.optBoolean("h"))
                        }
                        jar[d] = map
                    }
                }
            } catch (_: Throwable) {
                jar.clear()
            }
        }
    }

    private fun domainMatches(host: String, domain: String, hostOnly: Boolean): Boolean =
        host == domain || (!hostOnly && host.endsWith(".$domain"))

    private fun hostOf(url: String): String? = url.toHttpUrlOrNull()?.host?.lowercase()

    private fun persist() {
        val now = System.currentTimeMillis()
        var count = 0
        val root = JSONObject()
        for ((domain, cookies) in jar.entries.toList()) {
            val iter = cookies.entries.iterator()
            val out = JSONObject()
            while (iter.hasNext()) {
                val e = iter.next()
                val name = e.key
                val c = e.value
                if (c.expiresAt <= now || ++count > MAX_COOKIES) { iter.remove(); continue }
                out.put(name, JSONObject().put("v", c.value).put("e", c.expiresAt).put("h", c.hostOnly))
            }
            if (cookies.isEmpty()) jar.remove(domain) else root.put(domain, out)
        }
        prefs?.edit()?.putString(KEY, root.toString())?.apply()
    }

    @Synchronized
    private fun putAll(host: String, incoming: List<Incoming>) {
        if (incoming.isEmpty()) return
        val now = System.currentTimeMillis()
        for (c in incoming) {
            if (c.name.isEmpty()) continue
            val attr = c.domain?.trim()?.lowercase()?.removePrefix(".")?.takeIf { it.isNotEmpty() }
            if (attr != null && !domainMatches(host, attr, false)) continue
            val domain = attr ?: host
            val expiresAt = c.expiresAt ?: (now + SESSION_COOKIE_MS)
            // a cookie replaces (or, when expired, removes) a same-named one this host can see
            for (d in jar.keys.toList()) if (domainMatches(host, d, false)) jar[d]?.remove(c.name)
            if (expiresAt > now) jar.getOrPut(domain) { HashMap() }[c.name] = Entry(c.value, expiresAt, attr == null)
        }
        persist()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        putAll(
            url.host.lowercase(),
            cookies.map { Incoming(it.name, it.value, if (it.hostOnly) null else it.domain, if (it.persistent) it.expiresAt else null) }
        )
    }

    /** Intentionally empty: the Cookie header is built by [header]. */
    override fun loadForRequest(url: HttpUrl): List<Cookie> = emptyList()

    /** Cookies (as "a=b; c=d") read from the WebView for [url]. */
    fun putFromWebView(url: String, cookieHeader: String) {
        val host = hostOf(url) ?: return
        val list = ArrayList<Incoming>()
        for (part in cookieHeader.split(';')) {
            val eq = part.indexOf('=')
            if (eq > 0) list.add(Incoming(part.substring(0, eq).trim(), part.substring(eq + 1).trim(), null, null))
        }
        putAll(host, list)
    }

    @Synchronized
    fun cookieMap(url: String): Map<String, String> {
        val host = hostOf(url) ?: return emptyMap()
        val now = System.currentTimeMillis()
        val map = LinkedHashMap<String, String>()
        for ((domain, cookies) in jar) for ((name, c) in cookies) {
            if (c.expiresAt > now && domainMatches(host, domain, c.hostOnly)) map[name] = c.value
        }
        return map
    }

    @Synchronized
    fun delete(url: String, name: String) {
        val host = hostOf(url) ?: return
        for (d in jar.keys.toList()) if (domainMatches(host, d, false)) jar[d]?.remove(name)
        persist()
    }

    private fun parseHeader(h: String?): LinkedHashMap<String, String> {
        val map = LinkedHashMap<String, String>()
        if (h.isNullOrBlank()) return map
        for (part in h.split(';')) {
            val eq = part.indexOf('=')
            if (eq > 0) map[part.substring(0, eq).trim()] = part.substring(eq + 1).trim()
        }
        return map
    }

    private fun toHeader(map: Map<String, String>): String = map.entries.joinToString("; ") { it.key + "=" + it.value }

    /** Cookie header for a request: the jar's cookies plus the ones the provider supplied (those win on a name clash). */
    fun header(url: String, supplied: String?): String {
        val jarMap = LinkedHashMap(cookieMap(url))
        if (supplied.isNullOrBlank()) return toHeader(jarMap)
        val mine = parseHeader(supplied)
        for (n in mine.keys) jarMap.remove(n)
        val merged = LinkedHashMap<String, String>()
        merged.putAll(mine)
        merged.putAll(jarMap)
        return toHeader(merged)
    }

    /** Same-host redirect: the header that was sent plus what the redirecting response set. */
    fun carry(sentHeader: String, responseCookies: List<Cookie>): String {
        val map = parseHeader(sentHeader)
        val now = System.currentTimeMillis()
        for (c in responseCookies) {
            if (c.persistent && c.expiresAt <= now) map.remove(c.name) else map[c.name] = c.value
        }
        return toHeader(map)
    }
}
