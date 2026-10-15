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
    // A few requests at a time so several rows scrolling into view don't hammer one site.
    private val gate = Semaphore(3)

    fun clear(pluginId: String) {
        catalogs.remove(pluginId)
        val prefix = "$pluginId|"
        posts.keys.filter { it.startsWith(prefix) }.forEach { posts.remove(it) }
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
