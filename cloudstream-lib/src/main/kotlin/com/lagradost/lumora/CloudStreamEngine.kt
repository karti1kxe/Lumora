package com.lagradost.lumora

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TorrentLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.initClient
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.extractorApis
import dalvik.system.PathClassLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Runs CloudStream extensions (.cs3 files, compiled Kotlin / DEX) inside Lumora.
 *
 * It is the CloudStream twin of Lumora's Vega engine: it loads a downloaded `.cs3` the same way CloudStream's own
 * PluginManager does (PathClassLoader + manifest.json + `pluginClassName`), lets it register its [MainAPI]s, and then
 * answers the calls of Lumora's provider pages (categories, posts, search, title details, stream links) as JSON in the
 * same shape the Vega plugins produce, so the existing provider layout is used unchanged.
 */
object CloudStreamEngine {
    private const val TAG = "CloudStreamEngine"
    private const val SEP = '\u0001'
    private const val TORRENT_PREFIX = "cs-torrent:"

    private class Loaded(val plugin: BasePlugin, val path: String, val apis: List<MainAPI>)

    private val loaded = ConcurrentHashMap<String, Loaded>()
    private val loadLock = Mutex()
    /** Runs the extension's loadLinks on its own, so a slow server never holds the answer back once links have arrived. */
    private val fetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var initialised = false

    fun isLoaded(pluginId: String): Boolean = loaded.containsKey(pluginId)

    private fun init(context: Context) {
        if (initialised) return
        synchronized(this) {
            if (initialised) return
            val ctx = context.applicationContext
            CloudStreamApp.context = ctx
            try {
                app.initClient(ctx)
            } catch (t: Throwable) {
                Log.e(TAG, "could not set up the HTTP client: $t")
            }
            initialised = true
        }
    }

    /**
     * Loads (or, with [force], reloads) the extension file [file] under the id [pluginId]. Throws with a readable message
     * on failure. Without [force] an extension that is already loaded is left alone.
     */
    suspend fun load(context: Context, pluginId: String, file: File, force: Boolean = true) = withContext(Dispatchers.IO) {
        loadLock.withLock {
            if (!force && loaded.containsKey(pluginId)) return@withLock
            init(context)
            unloadLocked(pluginId)
            try {
                file.setReadOnly() // Android 14+ refuses to load writable dex files
            } catch (_: Throwable) {
            }
            val path = file.absolutePath
            val loader = PathClassLoader(path, context.applicationContext.classLoader)
            val manifestText = loader.getResourceAsStream("manifest.json")?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: throw IllegalStateException("This extension file has no manifest.json")
            val manifest = JSONObject(manifestText)
            val className = manifest.optString("pluginClassName", "")
            if (className.isBlank()) throw IllegalStateException("This extension has no plugin class")
            val instance = try {
                loader.loadClass(className).getDeclaredConstructor().newInstance() as BasePlugin
            } catch (t: Throwable) {
                throw IllegalStateException("Could not start the extension: ${t.cause?.message ?: t.message ?: t.javaClass.simpleName}")
            }
            instance.filename = path
            if (manifest.optBoolean("requiresResources", false)) {
                try {
                    val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
                    AssetManager::class.java.getMethod("addAssetPath", String::class.java).invoke(assets, path)
                    val res = context.resources
                    @Suppress("DEPRECATION")
                    (instance as? Plugin)?.resources = Resources(assets, res.displayMetrics, res.configuration)
                } catch (t: Throwable) {
                    Log.w(TAG, "extension resources not available: $t")
                }
            }
            try {
                if (instance is Plugin) instance.load(context.applicationContext) else instance.load()
            } catch (t: Throwable) {
                removeRegistered(path)
                throw IllegalStateException("The extension failed to load: ${t.cause?.message ?: t.message ?: t.javaClass.simpleName}")
            }
            val apis = APIHolder.allProviders.withLock { APIHolder.allProviders.filter { it.sourcePlugin == path } }
            for (api in apis) {
                try {
                    api.init()
                } catch (_: Throwable) {
                }
            }
            if (apis.isEmpty()) {
                removeRegistered(path)
                throw IllegalStateException("This extension does not contain any provider")
            }
            loaded[pluginId] = Loaded(instance, path, apis)
        }
    }

    fun unload(pluginId: String) {
        runCatching { kotlinx.coroutines.runBlocking { loadLock.withLock { unloadLocked(pluginId) } } }
    }

    private fun unloadLocked(pluginId: String) {
        val l = loaded.remove(pluginId) ?: return
        try {
            l.plugin.beforeUnload()
        } catch (t: Throwable) {
            Log.e(TAG, "beforeUnload failed: $t")
        }
        removeRegistered(l.path)
    }

    private fun removeRegistered(path: String) {
        APIHolder.apis.filter { it.sourcePlugin == path }.forEach { APIHolder.removePluginMapping(it) }
        APIHolder.allProviders.withLock { APIHolder.allProviders.removeAll { it.sourcePlugin == path } }
        extractorApis.withLock { extractorApis.removeAll { it.sourcePlugin == path } }
    }

    private fun entry(pluginId: String): Loaded =
        loaded[pluginId] ?: throw IllegalStateException("This extension is not loaded. Re-import its local plugin file to use it again.")

    // ---- link encoding: a plugin may register several providers, so their name travels with every link ----

    private fun encode(l: Loaded, api: MainAPI, value: String): String =
        if (l.apis.size > 1) api.name + SEP + value else value

    private fun decode(l: Loaded, link: String): Pair<MainAPI, String> {
        val i = link.indexOf(SEP)
        if (i > 0) {
            val name = link.substring(0, i)
            val api = l.apis.firstOrNull { it.name == name }
            if (api != null) return api to link.substring(i + 1)
        }
        return (l.apis.firstOrNull() ?: throw IllegalStateException("No provider in this extension")) to link
    }

    private fun failure(t: Throwable): Throwable = when (t) {
        is CancellationException -> t
        is NotImplementedError -> IllegalStateException("This provider does not support this")
        is kotlinx.coroutines.TimeoutCancellationException -> IllegalStateException("The site took too long to respond")
        else -> IllegalStateException(t.message ?: t.javaClass.simpleName)
    }

    private suspend fun <T> guarded(timeoutMs: Long?, block: suspend () -> T): T {
        try {
            return withTimeout(timeoutMs ?: 60_000L) { withContext(Dispatchers.IO) { block() } }
        } catch (t: Throwable) {
            throw failure(t)
        }
    }

    // ---- categories ----

    /** `[{title, filter}]` - the provider's main-page sections. */
    suspend fun catalogJson(context: Context, pluginId: String): String {
        init(context)
        val l = entry(pluginId)
        val out = JSONArray()
        for (api in l.apis) {
            if (!api.hasMainPage) continue
            for (p in api.mainPage) {
                val title = p.name.ifBlank { api.name }
                out.put(
                    JSONObject()
                        .put("title", if (l.apis.size > 1) "${api.name} · $title" else title)
                        .put("filter", listOf(api.name, p.name, if (p.horizontalImages) "1" else "0", p.data).joinToString(SEP.toString()))
                )
            }
        }
        return out.toString()
    }

    private fun post(l: Loaded, api: MainAPI, r: SearchResponse) = JSONObject()
        .put("title", r.name)
        .put("link", encode(l, api, r.url))
        .put("image", r.posterUrl.orEmpty())
        .apply { r.quality?.name?.let { put("tag", it) } }

    /** `[{title, link, image, tag}]` of one category and page. */
    suspend fun postsJson(context: Context, pluginId: String, filter: String, page: Int): String {
        init(context)
        val l = entry(pluginId)
        val parts = filter.split(SEP, limit = 4)
        if (parts.size < 4) return "[]"
        val api = l.apis.firstOrNull { it.name == parts[0] } ?: return "[]"
        val req = MainPageRequest(parts[1], parts[3], parts[2] == "1")
        val resp = guarded(api.getMainPageTimeoutMs) { api.getMainPage(page, req) }
        val out = JSONArray()
        resp?.items?.forEach { list -> list.list.forEach { out.put(post(l, api, it)) } }
        return out.toString()
    }

    /** Search results of every provider of the extension. */
    suspend fun searchJson(context: Context, pluginId: String, query: String, page: Int): String {
        init(context)
        val l = entry(pluginId)
        val out = JSONArray()
        var lastError: Throwable? = null
        var anyOk = false
        for (api in l.apis) {
            try {
                val res = guarded(api.searchTimeoutMs) { api.search(query, page) }
                anyOk = true
                res?.items?.forEach { out.put(post(l, api, it)) }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                lastError = t
            }
        }
        if (!anyOk && lastError != null) throw lastError
        return out.toString()
    }

    // ---- title details ----

    private fun episodeTitle(e: Episode, index: Int): String {
        val base = if (e.episode != null) "Episode ${e.episode}" else (e.name?.takeIf { it.isNotBlank() } ?: "Episode ${index + 1}")
        val name = e.name?.trim().orEmpty()
        return if (e.episode != null && name.isNotEmpty() && !name.equals(base, true)) "$base: $name" else base
    }

    private fun episodeLinks(l: Loaded, api: MainAPI, episodes: List<Episode>, title: String): List<JSONObject> {
        // one quality option (= one "season + audio" group) per season, in the order of the season numbers
        val bySeason = episodes.withIndex().groupBy { it.value.season }
        val seasons = bySeason.keys.sortedWith(compareBy<Int?> { it == null }.thenBy { it ?: 0 })
        return seasons.map { s ->
            val items = JSONArray()
            for ((idx, ep) in bySeason.getValue(s)) {
                items.put(
                    JSONObject()
                        .put("title", episodeTitle(ep, idx))
                        .put("link", encode(l, api, ep.data))
                        .put("type", "series")
                        .put("image", ep.posterUrl.orEmpty())
                        .put("description", ep.description.orEmpty())
                )
            }
            val seasonName = if (s != null && (seasons.size > 1 || s > 1)) "Season $s" else ""
            JSONObject().put("title", listOf(title, seasonName).filter { it.isNotBlank() }.joinToString(" ")).put("directLinks", items)
        }
    }

    /** Details of one title in the shape of a Vega getMeta result. */
    suspend fun infoJson(context: Context, pluginId: String, link: String): String {
        init(context)
        val l = entry(pluginId)
        val (api, url) = decode(l, link)
        val r: LoadResponse = guarded(api.loadTimeoutMs) { api.load(url) }
            ?: throw IllegalStateException("This title could not be loaded")
        val linkList = ArrayList<JSONObject>()
        var isSeries = false
        fun single(dataUrl: String, type: String, shownTitle: String) {
            val d = JSONArray().put(
                JSONObject().put("title", shownTitle).put("link", encode(l, api, dataUrl)).put("type", type)
            )
            linkList.add(JSONObject().put("title", "").put("directLinks", d))
        }
        when (r) {
            is MovieLoadResponse -> single(r.dataUrl, "movie", "Movie")
            is LiveStreamLoadResponse -> single(r.dataUrl, "movie", "Live")
            is TorrentLoadResponse -> {
                val t = r.magnet?.takeIf { it.isNotBlank() } ?: r.torrent?.takeIf { it.isNotBlank() }
                if (t != null) single(TORRENT_PREFIX + t, "movie", "Movie")
            }
            is TvSeriesLoadResponse -> {
                isSeries = true
                linkList.addAll(episodeLinks(l, api, r.episodes, ""))
            }
            is AnimeLoadResponse -> {
                isSeries = true
                for ((dub, eps) in r.episodes) {
                    if (eps.isEmpty()) continue
                    val label = when (dub) {
                        DubStatus.Dubbed -> "Dubbed"
                        DubStatus.Subbed -> "Subbed"
                        else -> ""
                    }
                    linkList.addAll(episodeLinks(l, api, eps, label))
                }
            }
            else -> {
                // other kinds of response (custom media ...) cannot be played from here
            }
        }
        if (linkList.isEmpty()) throw IllegalStateException("No episode or video was found for this title")
        val tags = JSONArray()
        r.year?.let { tags.put(it.toString()) }
        r.tags?.forEach { if (it.isNotBlank()) tags.put(it) }
        val rating = try {
            r.score?.toDouble(10)?.takeIf { it > 0.0 }?.let { String.format(java.util.Locale.US, "%.1f", it) }.orEmpty()
        } catch (_: Throwable) {
            ""
        }
        val poster = r.posterUrl.orEmpty()
        return JSONObject()
            .put("title", r.name)
            .put("image", r.backgroundPosterUrl?.takeIf { it.isNotBlank() } ?: poster)
            .put("poster", poster)
            .put("logo", r.logoUrl.orEmpty())
            .put("synopsis", r.plot.orEmpty())
            .put("type", if (isSeries) "series" else "movie")
            .put("tags", tags)
            .put("rating", rating)
            .put("webUrl", r.url)
            .put("linkList", JSONArray(linkList))
            .toString()
    }

    // ---- stream links ----

    private fun typeOf(l: ExtractorLink): String = when (l.type) {
        ExtractorLinkType.M3U8 -> "m3u8"
        ExtractorLinkType.DASH -> "mpd"
        ExtractorLinkType.TORRENT -> "torrent"
        ExtractorLinkType.MAGNET -> "magnet"
        else -> {
            val path = l.url.substringBefore('?').substringBefore('#').lowercase()
            when {
                path.endsWith(".m3u8") -> "m3u8"
                path.endsWith(".mpd") -> "mpd"
                path.endsWith(".mkv") -> "mkv"
                path.endsWith(".webm") -> "webm"
                else -> "mp4"
            }
        }
    }

    /** `[{server, link, type, quality, headers, subtitles}]` - what the extension's loadLinks found for [link]. */
    suspend fun streamsJson(context: Context, pluginId: String, link: String): String {
        init(context)
        val l = entry(pluginId)
        if (link.startsWith(TORRENT_PREFIX)) {
            val t = link.removePrefix(TORRENT_PREFIX)
            val isMagnet = t.startsWith("magnet:", ignoreCase = true)
            return JSONArray().put(
                JSONObject().put("server", "Torrent").put("link", t).put("type", if (isMagnet) "magnet" else "torrent")
            ).toString()
        }
        val (api, data) = decode(l, link)
        val links = java.util.Collections.synchronizedList(ArrayList<ExtractorLink>())
        val subs = java.util.Collections.synchronizedList(ArrayList<SubtitleFile>())
        // Providers may resolve several servers; playback only needs the first stable group of usable links.
        val firstAt = AtomicLong(0L)
        val lastAt = AtomicLong(0L)
        val idleMs = 500L
        val afterFirstMs = 1_800L
        val loadError = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
        val job = fetchScope.launch {
            try {
                api.loadLinks(data, false, { s -> subs.add(s) }, { c ->
                    links.add(c)
                    val now = System.currentTimeMillis()
                    firstAt.compareAndSet(0L, now)
                    lastAt.set(now)
                })
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                loadError.set(t)
            }
        }
        try {
            guarded(api.loadLinksTimeoutMs ?: 90_000L) {
                while (job.isActive) {
                    delay(60L)
                    val first = firstAt.get()
                    if (first == 0L) continue
                    val now = System.currentTimeMillis()
                    if (now - lastAt.get() >= idleMs || now - first >= afterFirstMs) break
                }
            }
        } catch (t: Throwable) {
            if (t is CancellationException) {
                job.cancel()
                throw t
            }
            // the time limit only matters when nothing was found at all
            if (firstAt.get() == 0L) {
                job.cancel()
                throw t
            }
        }
        val failed = loadError.get()
        val finished = !job.isActive
        job.cancel()
        if (failed != null && finished && links.isEmpty()) throw failure(failed)
        val subJson = JSONArray()
        val seenSubs = HashSet<String>()
        synchronized(subs) {
            for (s in subs) {
                if (s.url.isBlank() || !seenSubs.add(s.url)) continue
                subJson.put(JSONObject().put("title", s.lang).put("language", s.lang).put("uri", s.url))
            }
        }
        val out = JSONArray()
        synchronized(links) {
            for (c in links) {
                if (c.url.isBlank()) continue
                val headers = JSONObject()
                for ((k, v) in c.headers) if (k.isNotBlank() && v.isNotBlank()) headers.put(k, v)
                if (c.referer.isNotBlank() && !c.headers.keys.any { it.equals("Referer", true) }) headers.put("Referer", c.referer)
                val q = Qualities.getStringByInt(c.quality).takeIf { it.isNotBlank() }
                out.put(
                    JSONObject()
                        .put("server", c.name.ifBlank { c.source })
                        .put("link", c.url)
                        .put("type", typeOf(c))
                        .put("quality", q ?: JSONObject.NULL)
                        .put("headers", headers)
                        .put("subtitles", subJson)
                )
            }
        }
        return out.toString()
    }
}
