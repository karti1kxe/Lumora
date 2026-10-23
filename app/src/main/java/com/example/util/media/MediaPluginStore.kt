package com.example.util.media

import android.content.Context
import com.lagradost.lumora.CloudStreamEngine
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Locally installed providers for the Network > Media section. Repository fetching and provider install/update transfer
 * paths have been removed; providers that were installed before this cleanup can still be opened and played.
 */
object MediaPluginStore {
    private const val PREFS = "media_plugin_prefs"
    private const val KEY_REPO = "repo_url"
    private const val KEY_BASE = "repo_base"
    private const val KEY_MANIFEST = "manifest_json"
    private const val CS_FILE = "plugin.cs3"

    /** Bumped whenever the retained locally installed plugin list changes (collect it to refresh the UI). */
    val changes = MutableStateFlow(0)

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
                        name = o.optString("name", dir.name).let { n -> if (isCs && n == o.getString("value")) cleanName(null, n) else n },
                        version = o.optString("version", ""),
                        icon = cleanIconUrl(o.optString("icon", "")),
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

    /** True when the installed plugin [value] is a CloudStream extension (.cs3), false for a Vega JavaScript plugin. */
    fun isCloudStream(context: Context, value: String): Boolean = File(dirFor(context, value), CS_FILE).isFile

    /** Loads the installed CloudStream extension [value] into the process (once per app start). */
    suspend fun ensureCloudStreamLoaded(context: Context, value: String) {
        if (CloudStreamEngine.isLoaded(value)) return
        val file = File(dirFor(context, value), CS_FILE)
        if (!file.isFile) throw IllegalStateException("This provider is not installed")
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
     * A repository's icon link made loadable: CloudStream's "%size%" placeholder is filled in, "//host/..." gets a scheme and
     * http links are upgraded to https (the image loader does not follow an http -> https redirect, so those logos stayed empty).
     */
    internal fun cleanIconUrl(raw: String?): String {
        var u = raw?.trim().orEmpty()
        if (u.isEmpty() || u.equals("null", true)) return ""
        u = u.replace("%size%", "128").replace("{size}", "128").replace("\$size", "128")
        if (u.startsWith("//")) u = "https:$u"
        if (u.startsWith("http://", true)) u = "https://" + u.substring(7)
        return u
    }

    /** The name a repository lists, unchanged. Only when it lists none, the internal class name is shown without its "Provider" suffix. */
    internal fun cleanName(name: String?, internal: String): String {
        val n = name?.trim().orEmpty()
        if (n.isNotEmpty() && !n.equals("null", true)) return n
        val stripped = internal.removeSuffix("Provider").removeSuffix("provider").trim()
        return stripped.ifBlank { internal }
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
                    name = o.optString("display_name", value).let { dn ->
                        if (o.optString("engine", "") == MediaPluginInfo.ENGINE_CLOUDSTREAM && dn == value) cleanName(null, value) else dn
                    },
                    version = o.optString("version", ""),
                    icon = cleanIconUrl(o.optString("icon", "")),
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

}
