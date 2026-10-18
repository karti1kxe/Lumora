package com.example.util.media

import android.content.Context
import com.lagradost.lumora.CloudStreamEngine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

/** Loads a plugin's categories and thumbnails through [PluginJsEngine] and keeps them in memory for the session. */
object MediaProviderRepository {
    class Catalog(val categories: List<MediaCatalogEntry>, val genres: List<MediaCatalogEntry>)

    private val catalogs = ConcurrentHashMap<String, Catalog>()
    private val posts = ConcurrentHashMap<String, List<MediaPost>>()
    // Search answers kept for a few minutes: typing / deleting letters again shows the earlier results instantly.
    private class CachedSearch(val at: Long, val posts: List<MediaPost>)
    private val searches = ConcurrentHashMap<String, CachedSearch>()
    private const val SEARCH_TTL_MS = 5 * 60_000L
    private val infos = ConcurrentHashMap<String, MediaInfo>()
    private val episodeLists = ConcurrentHashMap<String, List<MediaDirectLink>>()
    // A few requests at a time so several rows scrolling into view don't hammer one site.
    private val gate = Semaphore(3)

    /** CloudStream extensions run natively ([CloudStreamEngine]); everything else is a Vega JavaScript plugin. */
    private fun isCs(context: Context, pluginId: String) = MediaPluginStore.isCloudStream(context, pluginId)

    private suspend fun csCall(context: Context, pluginId: String, block: suspend () -> String): String {
        try {
            MediaPluginStore.ensureCloudStreamLoaded(context, pluginId)
            return block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw if (t is PluginException) t else PluginException(t.message ?: "The provider failed")
        }
    }

    fun clearAll() {
        catalogs.clear(); posts.clear(); searches.clear(); infos.clear(); episodeLists.clear(); streamCache.clear()
    }

    fun clear(pluginId: String) {
        catalogs.remove(pluginId)
        val prefix = "$pluginId|"
        posts.keys.filter { it.startsWith(prefix) }.forEach { posts.remove(it) }
        searches.keys.filter { it.startsWith(prefix) }.forEach { searches.remove(it) }
        infos.keys.filter { it.startsWith(prefix) }.forEach { infos.remove(it) }
        episodeLists.keys.filter { it.startsWith(prefix) }.forEach { episodeLists.remove(it) }
        streamCache.keys.filter { it.startsWith(prefix) }.forEach { streamCache.remove(it) }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Title details + episode lists are also kept on disk (as the plugin's raw JSON), so opening a title that was seen
    // before shows ALL its information at once - the page does not wait for the plugin / website again. A saved copy
    // older than REVALIDATE_AFTER_MS is shown immediately and refreshed silently in the background.
    // ------------------------------------------------------------------------------------------------------------
    private const val DISK_TTL_MS = 24L * 60L * 60L * 1000L
    private const val REVALIDATE_AFTER_MS = 20L * 60L * 1000L
    private val revalidating = ConcurrentHashMap.newKeySet<String>()
    private val bgScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    private fun diskFile(context: Context, kind: String, key: String): java.io.File {
        val dir = java.io.File(context.applicationContext.cacheDir, "media_titles").apply { mkdirs() }
        val md = java.security.MessageDigest.getInstance("MD5").digest(key.toByteArray(Charsets.UTF_8))
        return java.io.File(dir, kind + "_" + md.joinToString("") { "%02x".format(it) } + ".json")
    }

    private fun diskRead(context: Context, kind: String, key: String): Pair<String, Long>? {
        return try {
            val f = diskFile(context, kind, key)
            if (!f.exists() || f.length() == 0L) return null
            val age = System.currentTimeMillis() - f.lastModified()
            if (age > DISK_TTL_MS) { f.delete(); return null }
            f.readText(Charsets.UTF_8) to age
        } catch (_: Throwable) { null }
    }

    private fun diskWrite(context: Context, kind: String, key: String, json: String) {
        try { diskFile(context, kind, key).writeText(json, Charsets.UTF_8) } catch (_: Throwable) {}
    }

    private fun diskDelete(context: Context, kind: String, key: String) {
        try { diskFile(context, kind, key).delete() } catch (_: Throwable) {}
    }

    /** The details of a title that are already known (memory or disk): no waiting, null when it was never loaded. */
    fun peekInfo(context: Context, pluginId: String, link: String): MediaInfo? {
        val key = "$pluginId|$link"
        infos[key]?.let { return it }
        val saved = diskRead(context, "info", key) ?: return null
        return try { parseInfo(saved.first).also { infos[key] = it } } catch (_: Throwable) { null }
    }

    /** The episodes of a quality option that are already known (memory or disk); null when never loaded. */
    fun peekEpisodes(context: Context, pluginId: String, episodesLink: String): List<MediaDirectLink>? {
        val key = "$pluginId|$episodesLink"
        episodeLists[key]?.let { return it }
        val saved = diskRead(context, "eps", key) ?: return null
        return try { parseEpisodes(saved.first).also { if (it.isNotEmpty()) episodeLists[key] = it } } catch (_: Throwable) { null }
    }

    /** Pull-to-refresh on a title page: everything remembered about this title is dropped. */
    fun clearTitle(context: Context, pluginId: String, link: String) {
        val key = "$pluginId|$link"
        val info = infos.remove(key)
        diskDelete(context, "info", key)
        info?.linkList?.forEach { l ->
            l.episodesLink?.let { e ->
                episodeLists.remove("$pluginId|$e")
                diskDelete(context, "eps", "$pluginId|$e")
            }
        }
        // episode lists of a title that is not in memory (app restarted): its stored info tells which ones belong to it
    }

    private suspend fun fetchInfoJson(context: Context, pluginId: String, link: String): String =
        if (isCs(context, pluginId)) {
            csCall(context, pluginId) { CloudStreamEngine.infoJson(context, pluginId, link) }
        } else {
            val args = org.json.JSONObject().put("link", link).put("provider", pluginId).toString()
            PluginJsEngine.call(context, pluginId, "meta", "getMeta", args, timeoutMs = 120_000L)
        }

    /** Details of one title (getMeta). */
    suspend fun loadInfo(context: Context, pluginId: String, link: String, force: Boolean = false): MediaInfo {
        val key = "$pluginId|$link"
        if (!force) {
            peekInfo(context, pluginId, link)?.let { cached ->
                val age = diskRead(context, "info", key)?.second ?: 0L
                if (age > REVALIDATE_AFTER_MS && revalidating.add("info|$key")) {
                    val app = context.applicationContext
                    bgScope.launch {
                        try {
                            val json = fetchInfoJson(app, pluginId, link)
                            val fresh = parseInfo(json)
                            infos[key] = fresh
                            diskWrite(app, "info", key, json)
                        } catch (_: Throwable) {
                        } finally { revalidating.remove("info|$key") }
                    }
                }
                return cached
            }
        }
        val json = fetchInfoJson(context, pluginId, link)
        val info = parseInfo(json)
        infos[key] = info
        diskWrite(context, "info", key, json)
        return info
    }

    private fun parseEpisodes(json: String): List<MediaDirectLink> {
        val arr = JSONArray(json)
        val out = ArrayList<MediaDirectLink>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val link = o.optString("link", "")
            if (link.isBlank()) continue
            out.add(
                MediaDirectLink(
                    title = o.optString("title", "").ifBlank { "Episode ${i + 1}" },
                    link = link,
                    type = "series",
                    image = o.optString("image", "").ifBlank { null },
                    description = o.optString("description", "").ifBlank { null }
                )
            )
        }
        return out
    }

    private suspend fun fetchEpisodesJson(context: Context, pluginId: String, episodesLink: String): String {
        val args = org.json.JSONObject().put("url", episodesLink).toString()
        return PluginJsEngine.call(context, pluginId, "episodes", "getEpisodes", args, timeoutMs = 120_000L)
    }

    /** Episodes behind a quality option's episodesLink (getEpisodes). */
    suspend fun loadEpisodes(context: Context, pluginId: String, episodesLink: String, force: Boolean = false): List<MediaDirectLink> {
        val key = "$pluginId|$episodesLink"
        // a CloudStream title lists its episodes directly in getMeta: there is no separate episode request
        if (isCs(context, pluginId)) return emptyList()
        if (!force) {
            peekEpisodes(context, pluginId, episodesLink)?.let { cached ->
                val age = diskRead(context, "eps", key)?.second ?: 0L
                if (age > REVALIDATE_AFTER_MS && revalidating.add("eps|$key")) {
                    val app = context.applicationContext
                    bgScope.launch {
                        try {
                            val json = fetchEpisodesJson(app, pluginId, episodesLink)
                            val fresh = parseEpisodes(json)
                            if (fresh.isNotEmpty()) { episodeLists[key] = fresh; diskWrite(app, "eps", key, json) }
                        } catch (_: Throwable) {
                        } finally { revalidating.remove("eps|$key") }
                    }
                }
                return cached
            }
        }
        val json = fetchEpisodesJson(context, pluginId, episodesLink)
        val out = parseEpisodes(json)
        if (out.isNotEmpty()) {
            episodeLists[key] = out
            diskWrite(context, "eps", key, json)
        }
        return out
    }

    private class CachedStreams(val at: Long, val list: List<MediaStream>)
    private val streamCache = ConcurrentHashMap<String, CachedStreams>()
    // stream links expire, so they are only reused for a short moment (opening the same episode again, the download list
    // right after the player, a retry): no second "Getting video link" wait
    private const val STREAM_CACHE_MS = 3 * 60_000L

    /** Playable sources of a movie / episode link (getStream). Kept for 3 minutes only: stream links expire. */
    suspend fun loadStreams(context: Context, pluginId: String, link: String, type: String, forDownload: Boolean, fresh: Boolean = false): List<MediaStream> {
        val key = "$pluginId|$link|$type|$forDownload"
        if (!fresh) {
            streamCache[key]?.let { c ->
                if (System.currentTimeMillis() - c.at < STREAM_CACHE_MS) return c.list
                streamCache.remove(key)
            }
        }
        val list = loadStreamsUncached(context, pluginId, link, type, forDownload)
        if (list.isNotEmpty()) {
            if (streamCache.size > 60) streamCache.clear()
            streamCache[key] = CachedStreams(System.currentTimeMillis(), list)
        }
        return list
    }

    /** The links of [link] did not play: the next request asks the provider again instead of reusing them. */
    fun forgetStreams(pluginId: String, link: String) {
        val prefix = "$pluginId|$link|"
        streamCache.keys.filter { it.startsWith(prefix) }.forEach { streamCache.remove(it) }
    }

    private suspend fun loadStreamsUncached(context: Context, pluginId: String, link: String, type: String, forDownload: Boolean): List<MediaStream> {
        val json = if (isCs(context, pluginId)) {
            csCall(context, pluginId) { CloudStreamEngine.streamsJson(context, pluginId, link, forDownload) }
        } else {
            val args = org.json.JSONObject()
                .put("link", link)
                .put("type", type)
                .put("isDownload", forDownload)
                .toString()
            PluginJsEngine.call(context, pluginId, "stream", "getStream", args, timeoutMs = 120_000L)
        }
        val arr = try { JSONArray(json) } catch (_: Throwable) { return emptyList() }
        val out = ArrayList<MediaStream>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            var url = o.optString("link", "").trim()
            if (url.isEmpty()) {
                // torrent providers: a "magnet" field, or only the info hash (Stremio style)
                val magnetField = o.optString("magnet", "").trim()
                val infoHash = o.optString("infoHash", "").trim()
                url = when {
                    magnetField.startsWith("magnet:", ignoreCase = true) -> magnetField
                    infoHash.length == 40 || infoHash.length == 32 ->
                        "magnet:?xt=urn:btih:" + infoHash + o.optString("title", "").trim().takeIf { it.isNotEmpty() }
                            ?.let { "&dn=" + java.net.URLEncoder.encode(it, "UTF-8") }.orEmpty()
                    else -> ""
                }
            }
            val isHttp = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
            // Vega-compatible torrent providers may return an app-native torrent path rather than an HTTP URL.
            // It must be passed to the existing torrent engine, not silently discarded as an unplayable stream.
            val isTorrentPath = url.startsWith("torrent://", ignoreCase = true)
            // a magnet link is turned into a playable / downloadable torrent by MediaPlayback
            val isMagnet = url.startsWith("magnet:", ignoreCase = true)
            if (!isHttp && !isTorrentPath && !isMagnet) continue
            val headers = LinkedHashMap<String, String>()
            o.optJSONObject("headers")?.let { h ->
                val keys = h.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = h.optString(k, "")
                    if (k.isNotBlank() && v.isNotBlank()) headers[k] = v
                }
            }
            // like Vega (ProviderManager.withJarCookies): the provider's own cookies (WAF clearance, session) go to the player
            // with the stream, unless the provider set a Cookie header itself
            if (isHttp && headers.keys.none { it.equals("Cookie", true) }) {
                val jarCookie = try { PluginCookieJar.header(url, null) } catch (_: Throwable) { "" }
                if (jarCookie.isNotBlank()) headers["Cookie"] = jarCookie
            }
            val subtitles = ArrayList<MediaSubtitle>()
            o.optJSONArray("subtitles")?.let { sa ->
                for (j in 0 until sa.length()) {
                    val so = sa.optJSONObject(j) ?: continue
                    val suri = (so.optString("uri", "").ifBlank { so.optString("url", "").ifBlank { so.optString("link", "") } }).trim()
                    if (!suri.startsWith("http://", ignoreCase = true) && !suri.startsWith("https://", ignoreCase = true)) continue
                    val lang = so.optString("language", "").ifBlank { so.optString("lang", "") }.trim()
                    val stitle = so.optString("title", "").ifBlank { so.optString("label", "") }.ifBlank { lang }.trim()
                    subtitles.add(MediaSubtitle(title = stitle.ifBlank { "Subtitle ${j + 1}" }, language = lang, uri = suri))
                }
            }
            out.add(
                MediaStream(
                    server = o.optString("server", "").ifBlank { "Server ${i + 1}" },
                    link = url,
                    type = o.optString("type", "").lowercase(),
                    quality = o.optString("quality", "").ifBlank { null },
                    headers = headers,
                    subtitles = subtitles
                )
            )
        }
        return out
    }

    private fun parseInfo(json: String): MediaInfo {
        val o = org.json.JSONObject(json)
        val tags = ArrayList<String>()
        o.optJSONArray("tags")?.let { a ->
            for (i in 0 until a.length()) a.optString(i, "").trim().takeIf { it.isNotEmpty() }?.let { tags.add(it) }
        }
        val links = ArrayList<MediaLink>()
        o.optJSONArray("linkList")?.let { a ->
            for (i in 0 until a.length()) {
                val l = a.optJSONObject(i) ?: continue
                val directs = ArrayList<MediaDirectLink>()
                l.optJSONArray("directLinks")?.let { d ->
                    for (j in 0 until d.length()) {
                        val e = d.optJSONObject(j) ?: continue
                        val link = e.optString("link", "")
                        if (link.isBlank()) continue
                        directs.add(
                            MediaDirectLink(
                                title = e.optString("title", "").ifBlank { "Play" },
                                link = link,
                                type = e.optString("type", "").ifBlank { null },
                                image = e.optString("image", "").ifBlank { null },
                                description = e.optString("description", "").ifBlank { null }
                            )
                        )
                    }
                }
                val episodesLink = l.optString("episodesLink", "").ifBlank { null }
                if (directs.isEmpty() && episodesLink == null) continue
                links.add(
                    MediaLink(
                        title = l.optString("title", ""),
                        quality = l.optString("quality", "").ifBlank { null },
                        episodesLink = episodesLink,
                        directLinks = directs
                    )
                )
            }
        }
        return MediaInfo(
            title = o.optString("title", "").trim(),
            image = o.optString("image", ""),
            poster = o.optString("poster", ""),
            logo = o.optString("logo", ""),
            synopsis = o.optString("synopsis", "").trim(),
            type = o.optString("type", "movie"),
            tags = tags,
            rating = o.optString("rating", ""),
            webUrl = o.optString("webUrl", ""),
            linkList = links
        )
    }

    suspend fun loadCatalog(context: Context, pluginId: String, force: Boolean = false): Catalog {
        if (!force) catalogs[pluginId]?.let { return it }
        val cs = isCs(context, pluginId)
        val categories = parseEntries(
            if (cs) csCall(context, pluginId) { CloudStreamEngine.catalogJson(context, pluginId) }
            else PluginJsEngine.call(context, pluginId, "catalog", "catalog")
        )
        val genres = if (cs) emptyList() else try {
            parseEntries(PluginJsEngine.call(context, pluginId, "catalog", "genres"))
        } catch (_: PluginException) {
            emptyList()
        }
        val result = Catalog(
            categories.distinctBy { it.title + "|" + it.filter },
            genres.distinctBy { it.title + "|" + it.filter }
        )
        catalogs[pluginId] = result
        return result
    }

    suspend fun loadPosts(context: Context, pluginId: String, filter: String, page: Int, force: Boolean = false): List<MediaPost> {
        val key = "$pluginId|$filter|$page"
        if (!force) posts[key]?.let { return it }
        val json = if (isCs(context, pluginId)) {
            gate.withPermit { csCall(context, pluginId) { CloudStreamEngine.postsJson(context, pluginId, filter, page) } }
        } else {
            val args = org.json.JSONObject()
                .put("filter", filter)
                .put("page", page)
                .put("providerValue", pluginId)
                .toString()
            gate.withPermit { PluginJsEngine.call(context, pluginId, "posts", "getPosts", args) }
        }
        val list = parsePosts(json)
        posts[key] = list
        return list
    }

    /** Cached results of an earlier identical search (null when there is none or it is older than a few minutes). */
    fun cachedSearch(pluginId: String, query: String, page: Int): List<MediaPost>? {
        val hit = searches["$pluginId|search|${query.lowercase()}|$page"] ?: return null
        return if (System.currentTimeMillis() - hit.at <= SEARCH_TTL_MS) hit.posts else null
    }

    /** Search inside one plugin (the plugin's own getSearchPosts). Answers are cached for a few minutes unless [force]. */
    suspend fun searchPosts(context: Context, pluginId: String, query: String, page: Int, force: Boolean = false): List<MediaPost> {
        if (!force) cachedSearch(pluginId, query, page)?.let { return it }
        val json = if (isCs(context, pluginId)) {
            gate.withPermit { csCall(context, pluginId) { CloudStreamEngine.searchJson(context, pluginId, query, page) } }
        } else {
            val args = org.json.JSONObject()
                .put("searchQuery", query)
                .put("page", page)
                .put("providerValue", pluginId)
                .toString()
            gate.withPermit { PluginJsEngine.call(context, pluginId, "posts", "getSearchPosts", args) }
        }
        val list = parsePosts(json)
        if (list.isNotEmpty()) searches["$pluginId|search|${query.lowercase()}|$page"] = CachedSearch(System.currentTimeMillis(), list)
        return list
    }

    private fun parseEntries(json: String): List<MediaCatalogEntry> {
        val arr = JSONArray(json)
        val out = ArrayList<MediaCatalogEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.optString("title", "").trim()
            if (title.isEmpty()) continue
            out.add(MediaCatalogEntry(title, o.optString("filter", "")))
        }
        return out
    }

    private fun parsePosts(json: String): List<MediaPost> {
        val arr = try { JSONArray(json) } catch (_: Throwable) { return emptyList() }
        val out = ArrayList<MediaPost>()
        val seen = HashSet<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.optString("title", "").trim()
            val link = o.optString("link", "").trim()
            if (title.isEmpty() || !seen.add(link.ifEmpty { title })) continue
            val tag = if (o.isNull("tag")) null else o.optString("tag", "").trim().takeIf { it.isNotEmpty() && !it.equals("null", true) }
            out.add(MediaPost(title, link, o.optString("image", "").trim(), tag))
        }
        return out
    }
}
