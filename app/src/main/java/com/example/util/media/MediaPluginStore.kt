package com.example.util.media

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Plugin repositories + downloaded plugins for the Network > Media section.
 *
 * A repository is a GitHub project laid out like github.com/Zenda-Cross/vega-providers: `manifest.json` lists the
 * plugins and `dist/<value>/{catalog,posts,meta,stream,episodes,settings}.js` holds each compiled plugin. A downloaded
 * plugin is stored in `filesDir/media_plugins/<value>/` and executed by [PluginJsEngine].
 */
object MediaPluginStore {
    private const val PREFS = "media_plugin_prefs"
    private const val KEY_REPO = "repo_url"
    private const val KEY_BASE = "repo_base"
    private const val KEY_MANIFEST = "manifest_json"
    private val MODULES = listOf("catalog", "posts", "meta", "stream", "episodes", "settings")

    /** Bumped whenever the manifest or the set of downloaded plugins changes (collect it to refresh the UI). */
    val changes = MutableStateFlow(0)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun root(context: Context): File = File(context.applicationContext.filesDir, "media_plugins").apply { mkdirs() }

    private fun safeName(value: String) = value.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    private fun dirFor(context: Context, value: String) = File(root(context), safeName(value))

    fun getRepoUrl(context: Context): String = prefs(context).getString(KEY_REPO, "").orEmpty()

    /** Base URL (raw GitHub / jsDelivr) of the last repository that loaded successfully, or null. */
    fun repoBase(context: Context): String? = prefs(context).getString(KEY_BASE, null)

    fun getManifest(context: Context): List<MediaPluginInfo> {
        val text = prefs(context).getString(KEY_MANIFEST, null) ?: return emptyList()
        return try { parseManifest(text) } catch (_: Throwable) { emptyList() }
    }

    fun getInstalled(context: Context): List<MediaPluginInfo> {
        val dirs = root(context).listFiles { f -> f.isDirectory && !f.name.startsWith(".") } ?: return emptyList()
        val out = ArrayList<MediaPluginInfo>()
        for (dir in dirs) {
            val meta = File(dir, "meta.json")
            if (!meta.isFile || !File(dir, "catalog.js").isFile || !File(dir, "posts.js").isFile) continue
            try {
                val o = JSONObject(meta.readText())
                out.add(
                    MediaPluginInfo(
                        value = o.getString("value"),
                        name = o.optString("name", dir.name),
                        version = o.optString("version", ""),
                        icon = o.optString("icon", ""),
                        type = o.optString("type", ""),
                        installedAt = o.optLong("installedAt", 0L)
                    )
                )
            } catch (_: Throwable) {
            }
        }
        return out.sortedBy { it.installedAt }
    }

    fun getInstalledVersion(context: Context, value: String): String? =
        getInstalled(context).firstOrNull { it.value == value }?.version

    fun readModule(context: Context, value: String, module: String): String? {
        val f = File(dirFor(context, value), "$module.js")
        return if (f.isFile) f.readText() else null
    }

    fun remove(context: Context, value: String) {
        dirFor(context, value).deleteRecursively()
        PluginJsEngine.invalidate(value)
        MediaProviderRepository.clear(value)
        changes.value++
    }

    /** Downloads manifest.json of [input] (a GitHub repo URL such as https://github.com/Zenda-Cross/vega-providers.git). */
    suspend fun refreshManifest(context: Context, input: String): Result<List<MediaPluginInfo>> = withContext(Dispatchers.IO) {
        val bases = candidateBases(input)
        if (bases.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Enter a valid plugin repository URL"))
        }
        var lastError = "Could not load manifest.json from this URL"
        for (base in bases) {
            val text = try {
                fetchText("$base/manifest.json")
            } catch (t: Throwable) {
                lastError = t.message ?: lastError
                null
            }
            if (text == null) continue
            val list = try { parseManifest(text) } catch (_: Throwable) { null }
            if (list == null || list.isEmpty()) continue
            prefs(context).edit()
                .putString(KEY_REPO, input.trim())
                .putString(KEY_BASE, base)
                .putString(KEY_MANIFEST, text)
                .apply()
            // newest code / domains from now on: nothing cached from the previous load is used
            PluginJsEngine.resetAllCaches()
            MediaProviderRepository.clearAll()
            changes.value++
            return@withContext Result.success(list)
        }
        Result.failure(IllegalStateException(lastError))
    }

    /** Downloads (or updates) one plugin's files. Only needs a successful [refreshManifest] before. */
    suspend fun download(context: Context, plugin: MediaPluginInfo): Result<Unit> = withContext(Dispatchers.IO) {
        val base = repoBase(context)
            ?: return@withContext Result.failure(IllegalStateException("Load the repository first"))
        try {
            val tmp = File(root(context), ".tmp_" + safeName(plugin.value))
            tmp.deleteRecursively()
            tmp.mkdirs()
            val bases = listOf(base) + candidateBases(getRepoUrl(context)).filter { it != base }
            for (module in MODULES) {
                var body: String? = null
                for (b in bases) {
                    body = try { fetchText("$b/dist/${plugin.value}/$module.js") } catch (t: IOException) { if (b == bases.last()) throw t else null }
                    if (body != null) break
                }
                File(tmp, "$module.js").writeText(body ?: continue)
            }
            if (!File(tmp, "catalog.js").isFile || !File(tmp, "posts.js").isFile) {
                tmp.deleteRecursively()
                throw IllegalStateException("Plugin files not found in the repository")
            }
            val meta = JSONObject()
                .put("value", plugin.value)
                .put("name", plugin.name)
                .put("version", plugin.version)
                .put("icon", plugin.icon)
                .put("type", plugin.type)
                .put("installedAt", getInstalled(context).firstOrNull { it.value == plugin.value }?.installedAt
                    ?: System.currentTimeMillis())
            File(tmp, "meta.json").writeText(meta.toString())
            val dest = dirFor(context, plugin.value)
            dest.deleteRecursively()
            if (!tmp.renameTo(dest)) throw IOException("Could not save the plugin")
            PluginJsEngine.invalidate(plugin.value)
            MediaProviderRepository.clear(plugin.value)
            changes.value++
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * After a repository was loaded: every plugin that is already downloaded is fetched again, so what runs is always the
     * latest code of the repository (a plugin that fails to update keeps its old files). Returns how many were updated.
     */
    suspend fun updateInstalled(context: Context): Int {
        val wanted = getManifest(context).filter { m -> getInstalled(context).any { it.value == m.value } }
        var updated = 0
        for (plugin in wanted) {
            if (download(context, plugin).isSuccess) updated++
        }
        return updated
    }

    /** Returns the body, null on HTTP errors (404 etc.), throws on network failures. */
    private fun fetchText(url: String): String? {
        // always the newest file: a cache-busting query (raw.githubusercontent.com) and no-cache headers, like Vega's own download
        val fresh = if (url.startsWith("https://raw.githubusercontent.com/")) url + (if (url.contains('?')) "&" else "?") + "t=" + System.currentTimeMillis() else url
        val req = Request.Builder().url(fresh).header("User-Agent", "Lumora")
            .header("Cache-Control", "no-cache, no-store, must-revalidate").header("Pragma", "no-cache").build()
        return client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() else null
        }
    }

    private fun parseManifest(text: String): List<MediaPluginInfo> {
        val arr = JSONArray(text)
        val out = ArrayList<MediaPluginInfo>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optBoolean("disabled", false)) continue
            val value = o.optString("value", "")
            if (value.isBlank()) continue
            out.add(
                MediaPluginInfo(
                    value = value,
                    name = o.optString("display_name", value),
                    version = o.optString("version", ""),
                    icon = o.optString("icon", ""),
                    type = o.optString("type", "")
                )
            )
        }
        return out
    }

    /** GitHub project URL (with or without .git / branch), `owner/repo`, or a direct base URL -> candidate base URLs. */
    internal fun candidateBases(input: String): List<String> {
        var s = input.trim().removeSuffix("/")
        if (s.isEmpty()) return emptyList()
        if (s.endsWith("manifest.json", ignoreCase = true)) s = s.dropLast("manifest.json".length).removeSuffix("/")
        val gh = Regex(
            "^(?:https?://)?(?:www\\.)?github\\.com/([^/\\s]+)/([^/\\s#?]+?)(?:\\.git)?(?:/(?:tree|blob)/([^\\s#?]+))?/?$",
            RegexOption.IGNORE_CASE
        ).find(s) ?: if (!s.contains("://")) {
            Regex("^([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\\.git)?$").find(s)
        } else null
        if (gh != null) {
            val owner = gh.groupValues[1]
            val repo = gh.groupValues[2]
            val branch = gh.groupValues.getOrNull(3).orEmpty()
            val branches = if (branch.isNotBlank()) listOf(branch) else listOf("main", "master")
            val out = ArrayList<String>()
            for (b in branches) out.add("https://raw.githubusercontent.com/$owner/$repo/$b")
            for (b in branches) out.add("https://cdn.jsdelivr.net/gh/$owner/$repo@$b")
            return out
        }
        return if (s.startsWith("http://") || s.startsWith("https://")) listOf(s) else emptyList()
    }
}
