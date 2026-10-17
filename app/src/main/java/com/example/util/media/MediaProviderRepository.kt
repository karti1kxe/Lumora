package com.example.util.media

import android.content.Context
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

/** Loads a plugin's categories and thumbnails through [PluginJsEngine] and keeps them in memory for the session. */
object MediaProviderRepository {
    class Catalog(val categories: List<MediaCatalogEntry>, val genres: List<MediaCatalogEntry>)

    private val catalogs = ConcurrentHashMap<String, Catalog>()
    private val posts = ConcurrentHashMap<String, List<MediaPost>>()
    private val infos = ConcurrentHashMap<String, MediaInfo>()
    private val episodeLists = ConcurrentHashMap<String, List<MediaDirectLink>>()
    // A few requests at a time so several rows scrolling into view don't hammer one site.
    private val gate = Semaphore(3)

    fun clear(pluginId: String) {
        catalogs.remove(pluginId)
        val prefix = "$pluginId|"
        posts.keys.filter { it.startsWith(prefix) }.forEach { posts.remove(it) }
        infos.keys.filter { it.startsWith(prefix) }.forEach { infos.remove(it) }
        episodeLists.keys.filter { it.startsWith(prefix) }.forEach { episodeLists.remove(it) }
    }

    /** Details of one title (getMeta). */
    suspend fun loadInfo(context: Context, pluginId: String, link: String, force: Boolean = false): MediaInfo {
        val key = "$pluginId|$link"
        if (!force) infos[key]?.let { return it }
        val args = org.json.JSONObject().put("link", link).put("provider", pluginId).toString()
        val json = PluginJsEngine.call(context, pluginId, "meta", "getMeta", args, timeoutMs = 120_000L)
        val info = parseInfo(json)
        infos[key] = info
        return info
    }

    /** Episodes behind a quality option's episodesLink (getEpisodes). */
    suspend fun loadEpisodes(context: Context, pluginId: String, episodesLink: String): List<MediaDirectLink> {
        val key = "$pluginId|$episodesLink"
        episodeLists[key]?.let { return it }
        val args = org.json.JSONObject().put("url", episodesLink).toString()
        val json = PluginJsEngine.call(context, pluginId, "episodes", "getEpisodes", args, timeoutMs = 120_000L)
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
        episodeLists[key] = out
        return out
    }

    /** Playable sources of a movie / episode link (getStream). Not cached: stream links expire. */
    suspend fun loadStreams(context: Context, pluginId: String, link: String, type: String, forDownload: Boolean): List<MediaStream> {
        val args = org.json.JSONObject()
            .put("link", link)
            .put("type", type)
            .put("isDownload", forDownload)
            .toString()
        val json = PluginJsEngine.call(context, pluginId, "stream", "getStream", args, timeoutMs = 120_000L)
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
        val categories = parseEntries(PluginJsEngine.call(context, pluginId, "catalog", "catalog"))
        val genres = try {
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
        val args = org.json.JSONObject()
            .put("filter", filter)
            .put("page", page)
            .put("providerValue", pluginId)
            .toString()
        val json = gate.withPermit { PluginJsEngine.call(context, pluginId, "posts", "getPosts", args) }
        val list = parsePosts(json)
        posts[key] = list
        return list
    }

    /** Search inside one plugin (the plugin's own getSearchPosts). Not cached. */
    suspend fun searchPosts(context: Context, pluginId: String, query: String, page: Int): List<MediaPost> {
        val args = org.json.JSONObject()
            .put("searchQuery", query)
            .put("page", page)
            .put("providerValue", pluginId)
            .toString()
        val json = gate.withPermit { PluginJsEngine.call(context, pluginId, "posts", "getSearchPosts", args) }
        return parsePosts(json)
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
            val tag = o.optString("tag", "").trim().ifEmpty { null }
            out.add(MediaPost(title, link, o.optString("image", "").trim(), tag))
        }
        return out
    }
}
