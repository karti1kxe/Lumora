package com.example.util.media

import android.content.Context
import com.lagradost.lumora.CloudStreamEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Plugin repositories + downloaded plugins for the Network > Media section.
 *
 * A repository is a GitHub project laid out like github.com/Zenda-Cross/vega-providers: `manifest.json` lists the
 * plugins and `dist/<value>/{catalog,posts,meta,stream,episodes,settings}.js` holds each compiled plugin. A downloaded
 * plugin is stored in `filesDir/media_plugins/<value>/` and executed by [PluginJsEngine].
 *
 * A repository may also be a CloudStream repository (repo.json -> pluginLists -> plugins.json, shortcode, cloudstreamrepo://
 * link): its extensions are `.cs3` files, stored as `media_plugins/<value>/plugin.cs3` and run by [CloudStreamEngine].
 * Both kinds look the same to the rest of the app (the Media list, the provider page, the title page and the player).
 */
object MediaPluginStore {
    private const val PREFS = "media_plugin_prefs"
    private const val KEY_REPO = "repo_url"
    private const val KEY_BASE = "repo_base"
    private const val KEY_MANIFEST = "manifest_json"
    private val MODULES = listOf("catalog", "posts", "meta", "stream", "episodes", "settings")
    private const val CS_FILE = "plugin.cs3"

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
            val isCs = File(dir, CS_FILE).isFile
            if (!meta.isFile) continue
            if (!isCs && (!File(dir, "catalog.js").isFile || !File(dir, "posts.js").isFile)) continue
            try {
                val o = JSONObject(meta.readText())
                out.add(
                    MediaPluginInfo(
                        value = o.getString("value"),
                        name = o.optString("name", dir.name),
                        version = o.optString("version", ""),
                        icon = o.optString("icon", ""),
                        type = o.optString("type", ""),
                        installedAt = o.optLong("installedAt", 0L),
                        engine = if (isCs) MediaPluginInfo.ENGINE_CLOUDSTREAM else MediaPluginInfo.ENGINE_VEGA,
                        url = o.optString("url", ""),
                        fileHash = o.optString("hash", ""),
                        language = o.optString("language", ""),
                        description = o.optString("description", ""),
                        size = o.optLong("size", 0L)
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

    /** True when the downloaded plugin [value] is a CloudStream extension (.cs3), false for a Vega JavaScript plugin. */
    fun isCloudStream(context: Context, value: String): Boolean = File(dirFor(context, value), CS_FILE).isFile

    /** Loads the downloaded CloudStream extension [value] into the process (once per app start). */
    suspend fun ensureCloudStreamLoaded(context: Context, value: String) {
        if (CloudStreamEngine.isLoaded(value)) return
        val file = File(dirFor(context, value), CS_FILE)
        if (!file.isFile) throw IllegalStateException("This provider is not downloaded")
        CloudStreamEngine.load(context, value, file, force = false)
    }

    fun remove(context: Context, value: String) {
        CloudStreamEngine.unload(value)
        dirFor(context, value).deleteRecursively()
        PluginJsEngine.invalidate(value)
        MediaProviderRepository.clear(value)
        changes.value++
    }

    /**
     * Loads the plugin list of [input]: a Vega repository (GitHub URL such as https://github.com/Zenda-Cross/vega-providers.git,
     * whose manifest.json lists the plugins) or a CloudStream repository (repo.json / plugins.json link, GitHub project URL,
     * `cloudstreamrepo://...` link or shortcode). The kind is detected from what the link answers.
     */
    suspend fun refreshManifest(context: Context, input: String): Result<List<MediaPluginInfo>> = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Enter a valid plugin repository URL"))
        }
        val errors = ArrayList<String>()
        val csFirst = looksLikeCloudStream(trimmed)
        val order = if (csFirst) listOf(true, false) else listOf(false, true)
        for (cs in order) {
            val r = if (cs) refreshCloudStream(context, trimmed) else refreshVega(context, trimmed)
            if (r.isSuccess) return@withContext r
            r.exceptionOrNull()?.message?.let { errors.add(it) }
        }
        val message = when {
            csFirst -> errors.firstOrNull()
            errors.size >= 2 -> "No Vega manifest.json and no CloudStream repo.json was found at this URL"
            else -> errors.firstOrNull()
        }
        Result.failure(IllegalStateException(message ?: "Could not load the plugin list from this URL"))
    }

    private fun refreshVega(context: Context, input: String): Result<List<MediaPluginInfo>> {
        val bases = candidateBases(input)
        if (bases.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter a valid plugin repository URL"))
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
            return Result.success(list)
        }
        return Result.failure(IllegalStateException(lastError))
    }

    // ---------------------------------------------------------------------------------------------------------
    // CloudStream repositories
    // ---------------------------------------------------------------------------------------------------------

    private val CS_SHORTCODE = Regex("^[a-zA-Z0-9!_-]+$")
    private val CS_REPO_LINK = Regex("^(cloudstreamrepo://)|(https://cs\\.repo/\\??)", RegexOption.IGNORE_CASE)
    // plugin types Lumora cannot play (it plays video only)
    private val CS_UNPLAYABLE_TYPES = setOf("music", "audiobook", "podcast", "audio", "custommedia")

    private fun looksLikeCloudStream(input: String): Boolean {
        val s = input.trim()
        if (CS_REPO_LINK.containsMatchIn(s)) return true
        if (s.endsWith("repo.json", true) || s.endsWith("plugins.json", true)) return true
        if (!s.contains("/") && !s.contains(".") && CS_SHORTCODE.matches(s)) return true
        return false
    }

    /** Follows a cutt.ly / py.md shortcode (what CloudStream repositories are shared as) to the real repository URL. */
    private fun resolveShortcode(code: String): String? {
        val noRedirect = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
        val targets = if (code.startsWith("!")) listOf("https://py.md/" + code.removePrefix("!")) else listOf("https://cutt.ly/$code")
        for (t in targets) {
            try {
                noRedirect.newCall(Request.Builder().url(t).header("User-Agent", "Lumora").build()).execute().use { resp ->
                    val loc = resp.header("Location") ?: return@use
                    if (loc.startsWith("https://py.md/404") || loc.startsWith("https://cutt.ly/404")) return@use
                    if (loc.trimEnd('/') == "https://py.md" || loc.trimEnd('/') == "https://cutt.ly") return@use
                    return loc
                }
            } catch (_: Throwable) {
            }
        }
        return null
    }

    /** The URLs a CloudStream repository link may stand for (the repo.json itself, or where a GitHub project keeps it). */
    private fun csCandidates(input: String): List<String> {
        var s = input.trim()
        if (CS_REPO_LINK.containsMatchIn(s)) {
            s = s.replace(CS_REPO_LINK, "")
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "https://$s"
        } else if (!s.contains("://") && !s.contains("/") && !s.contains(".") && CS_SHORTCODE.matches(s)) {
            s = resolveShortcode(s) ?: return emptyList()
        } else if (!s.contains("://") && Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(s)) {
            s = "https://github.com/$s"
        }
        val gh = Regex(
            "^(?:https?://)?(?:www\\.)?github\\.com/([^/\\s]+)/([^/\\s#?]+?)(?:\\.git)?(?:/(?:tree|blob)/([^\\s#?]+))?/?$",
            RegexOption.IGNORE_CASE
        ).find(s)
        if (gh != null) {
            val owner = gh.groupValues[1]
            val repo = gh.groupValues[2]
            val given = gh.groupValues.getOrNull(3).orEmpty().substringBefore('/')
            val branches = (listOfNotNull(given.takeIf { it.isNotBlank() }) + listOf("master", "main", "builds")).distinct()
            val out = ArrayList<String>()
            for (b in branches) out.add("https://raw.githubusercontent.com/$owner/$repo/$b/repo.json")
            for (b in branches) out.add("https://raw.githubusercontent.com/$owner/$repo/$b/plugins.json")
            for (b in branches) out.add("https://cdn.jsdelivr.net/gh/$owner/$repo@$b/repo.json")
            return out
        }
        // a link to github.com/<user>/<repo>/blob/<branch>/repo.json -> the raw file
        val blob = Regex("^https?://github\\.com/([^/]+)/([^/]+)/blob/([^/]+)/(.+)$", RegexOption.IGNORE_CASE).find(s)
        if (blob != null) {
            return listOf("https://raw.githubusercontent.com/${blob.groupValues[1]}/${blob.groupValues[2]}/${blob.groupValues[3]}/${blob.groupValues[4]}")
        }
        return if (s.startsWith("http://", true) || s.startsWith("https://", true)) listOf(s) else emptyList()
    }

    private fun pluginsFrom(text: String): List<JSONObject>? {
        val arr = try { JSONArray(text.trim()) } catch (_: Throwable) { return null }
        val out = ArrayList<JSONObject>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("url", "").isBlank() || o.optString("internalName", "").isBlank()) continue
            out.add(o)
        }
        return out
    }

    private fun refreshCloudStream(context: Context, input: String): Result<List<MediaPluginInfo>> {
        val candidates = csCandidates(input)
        if (candidates.isEmpty()) return Result.failure(IllegalArgumentException("Enter a valid plugin repository URL"))
        var lastError = "Could not find a CloudStream repository at this URL"
        for (url in candidates) {
            val text = try {
                fetchText(url)
            } catch (t: Throwable) {
                lastError = t.message ?: lastError
                null
            } ?: continue
            val trimmed = text.trim()
            val plugins = ArrayList<JSONObject>()
            if (trimmed.startsWith("{")) {
                // repo.json: { name, description, manifestVersion, pluginLists: [url, ...] }
                val repo = try { JSONObject(trimmed) } catch (_: Throwable) { continue }
                val lists = repo.optJSONArray("pluginLists") ?: continue
                for (i in 0 until lists.length()) {
                    val listUrl = lists.optString(i, "").trim()
                    if (listUrl.isEmpty()) continue
                    val listText = try { fetchText(listUrl) } catch (t: Throwable) { lastError = t.message ?: lastError; null } ?: continue
                    pluginsFrom(listText)?.let { plugins.addAll(it) }
                }
            } else {
                // a plugins.json link pasted directly
                pluginsFrom(trimmed)?.let { plugins.addAll(it) }
            }
            val usable = plugins.filter { p ->
                // status 0 = switched off by the repository owner
                if (p.has("status") && p.optInt("status", 1) == 0) return@filter false
                val types = p.optJSONArray("tvTypes")
                if (types != null && types.length() > 0) {
                    val all = (0 until types.length()).map { types.optString(it, "").trim().lowercase() }
                    if (all.all { it in CS_UNPLAYABLE_TYPES }) return@filter false
                }
                true
            }.distinctBy { it.optString("internalName") }
            if (usable.isEmpty()) {
                lastError = "This repository has no providers"
                continue
            }
            val normalised = JSONArray()
            for (p in usable) {
                val internal = p.optString("internalName")
                normalised.put(
                    JSONObject()
                        .put("value", internal)
                        .put("display_name", p.optString("name", internal).ifBlank { internal })
                        .put("version", p.optString("version", "1"))
                        .put("icon", p.optString("iconUrl", "").takeIf { it != "null" }.orEmpty())
                        .put("type", "CloudStream")
                        .put("engine", MediaPluginInfo.ENGINE_CLOUDSTREAM)
                        .put("url", p.optString("url"))
                        .put("hash", p.optString("fileHash", "").takeIf { it != "null" }.orEmpty())
                        .put("language", p.optString("language", "").takeIf { it != "null" }.orEmpty())
                        .put("description", p.optString("description", "").takeIf { it != "null" }.orEmpty())
                        .put("size", p.optLong("fileSize", 0L))
                )
            }
            val manifestText = normalised.toString()
            val list = parseManifest(manifestText)
            prefs(context).edit()
                .putString(KEY_REPO, input.trim())
                .remove(KEY_BASE)
                .putString(KEY_MANIFEST, manifestText)
                .apply()
            PluginJsEngine.resetAllCaches()
            MediaProviderRepository.clearAll()
            changes.value++
            return Result.success(list)
        }
        return Result.failure(IllegalStateException(lastError))
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read = input.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return "sha256-" + digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Downloads one CloudStream extension (.cs3), checks its hash like CloudStream does, and loads it. */
    private suspend fun downloadCloudStream(context: Context, plugin: MediaPluginInfo): Result<Unit> {
        if (plugin.url.isBlank()) return Result.failure(IllegalStateException("This provider has no download link"))
        val tmp = File(root(context), ".tmp_" + safeName(plugin.value))
        val old = File(root(context), ".old_" + safeName(plugin.value))
        val dest = dirFor(context, plugin.value)
        return try {
            tmp.deleteRecursively()
            tmp.mkdirs()
            val file = File(tmp, CS_FILE)
            val req = Request.Builder().url(plugin.url).header("User-Agent", "Lumora").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Download failed (HTTP ${resp.code})")
                val body = resp.body ?: throw IOException("Empty download")
                body.byteStream().use { input -> file.outputStream().use { input.copyTo(it) } }
            }
            if (file.length() < 100L) throw IOException("The downloaded file is empty")
            if (plugin.fileHash.isNotBlank() && plugin.fileHash.startsWith("sha256-")) {
                val got = sha256(file)
                if (!got.equals(plugin.fileHash, ignoreCase = true)) {
                    throw IllegalStateException("The downloaded file does not match the repository's checksum")
                }
            }
            val meta = JSONObject()
                .put("value", plugin.value)
                .put("name", plugin.name)
                .put("version", plugin.version)
                .put("icon", plugin.icon)
                .put("type", plugin.type)
                .put("engine", MediaPluginInfo.ENGINE_CLOUDSTREAM)
                .put("url", plugin.url)
                .put("hash", plugin.fileHash)
                .put("language", plugin.language)
                .put("description", plugin.description)
                .put("size", plugin.size)
                .put("installedAt", getInstalled(context).firstOrNull { it.value == plugin.value }?.installedAt
                    ?: System.currentTimeMillis())
            File(tmp, "meta.json").writeText(meta.toString())
            // the running copy is stopped, the old files are kept until the new ones really load
            CloudStreamEngine.unload(plugin.value)
            old.deleteRecursively()
            if (dest.exists() && !dest.renameTo(old)) throw IOException("Could not replace the old provider files")
            if (!tmp.renameTo(dest)) {
                if (old.exists()) old.renameTo(dest)
                throw IOException("Could not save the provider")
            }
            try {
                CloudStreamEngine.load(context, plugin.value, File(dest, CS_FILE), force = true)
            } catch (t: Throwable) {
                CloudStreamEngine.unload(plugin.value)
                dest.deleteRecursively()
                if (old.exists()) {
                    old.renameTo(dest)
                    runCatching { CloudStreamEngine.load(context, plugin.value, File(dest, CS_FILE), force = true) }
                }
                throw t
            }
            old.deleteRecursively()
            MediaProviderRepository.clear(plugin.value)
            changes.value++
            Result.success(Unit)
        } catch (t: Throwable) {
            tmp.deleteRecursively()
            Result.failure(if (t is kotlinx.coroutines.CancellationException) t else IllegalStateException(t.message ?: "Could not install this provider"))
        }
    }

    /** Downloads (or updates) one plugin's files. Only needs a successful [refreshManifest] before. */
    suspend fun download(context: Context, plugin: MediaPluginInfo): Result<Unit> = withContext(Dispatchers.IO) {
        if (plugin.isCloudStream) return@withContext downloadCloudStream(context, plugin)
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
            // a CloudStream extension is a compiled file: it is fetched again only when the repository lists a new version
            if (plugin.isCloudStream) {
                val have = getInstalled(context).firstOrNull { it.value == plugin.value }
                if (have != null && have.version == plugin.version && isCloudStream(context, plugin.value)) continue
            }
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
                    type = o.optString("type", ""),
                    engine = if (o.optString("engine", "") == MediaPluginInfo.ENGINE_CLOUDSTREAM) MediaPluginInfo.ENGINE_CLOUDSTREAM else MediaPluginInfo.ENGINE_VEGA,
                    url = o.optString("url", ""),
                    fileHash = o.optString("hash", ""),
                    language = o.optString("language", ""),
                    description = o.optString("description", ""),
                    size = o.optLong("size", 0L)
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
