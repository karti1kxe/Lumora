package com.example.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Read-only clients for the public intro/skip marker providers exposed by Player Settings. */
object OnlineSkipMarkerService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(7, TimeUnit.SECONDS)
        .build()

    data class RemoteMarker(val type: SkipMarkerType, val startMs: Long, val endMs: Long)

    suspend fun fetch(
        provider: String,
        title: String,
        durationMs: Long
    ): List<RemoteMarker> = withContext(Dispatchers.IO) {
        if (title.isBlank() || durationMs <= 0L) return@withContext emptyList()
        val identity = parseIdentity(title)
        when (provider.uppercase(Locale.US)) {
            "INTRODB" -> fetchIntroDb(identity, durationMs)
            "TIDB" -> fetchTheIntroDb(identity, durationMs)
            "ANISKIP" -> fetchAniSkip(identity, durationMs)
            else -> emptyList()
        }
    }

    private fun fetchIntroDb(identity: Identity, durationMs: Long): List<RemoteMarker> {
        val imdb = identity.imdbId ?: return emptyList()
        val query = buildString {
            append("imdb=").append(imdb)
            identity.season?.let { append("&season=").append(it) }
            identity.episode?.let { append("&episode=").append(it) }
        }
        val root = getJson("https://api.introdb.app/intro?$query") ?: return emptyList()
        val start = root.optDouble("start", Double.NaN)
        val end = root.optDouble("end", Double.NaN)
        if (!start.isFinite() || !end.isFinite() || end <= start) return emptyList()
        return listOf(RemoteMarker(SkipMarkerType.OPENING, (start * 1000).toLong(), (end * 1000).toLong()))
            .map { it.clampTo(durationMs) }
    }

    private fun fetchTheIntroDb(identity: Identity, durationMs: Long): List<RemoteMarker> {
        val imdb = identity.imdbId ?: return emptyList()
        val query = buildString {
            append("imdb_id=").append(imdb)
            identity.season?.let { append("&season=").append(it) }
            identity.episode?.let { append("&episode=").append(it) }
            append("&duration_ms=").append(durationMs)
        }
        val root = getJson("https://api.theintrodb.org/v3/media?$query") ?: return emptyList()
        val result = mutableListOf<RemoteMarker>()
        appendWindows(result, root.optJSONArray("intro"), SkipMarkerType.OPENING, durationMs)
        appendWindows(result, root.optJSONArray("recap"), SkipMarkerType.RECAP, durationMs)
        appendWindows(result, root.optJSONArray("credits"), SkipMarkerType.ENDING, durationMs)
        appendWindows(result, root.optJSONArray("preview"), SkipMarkerType.PREVIEW, durationMs)
        return result
    }

    private fun fetchAniSkip(identity: Identity, durationMs: Long): List<RemoteMarker> {
        val episode = identity.episode ?: return emptyList()
        val title = identity.cleanTitle ?: return emptyList()
        val search = getJson("https://api.jikan.moe/v4/anime?q=${java.net.URLEncoder.encode(title, "UTF-8")}&limit=1")
            ?: return emptyList()
        val data = search.optJSONArray("data") ?: return emptyList()
        val malId = data.optJSONObject(0)?.optInt("mal_id", 0) ?: 0
        if (malId <= 0) return emptyList()
        val root = getJson("https://api.aniskip.com/v2/skip-times/$malId/$episode?types=op&types=ed&types=recap&episodeLength=${durationMs / 1000.0}")
            ?: return emptyList()
        val results = root.optJSONArray("results") ?: return emptyList()
        val out = mutableListOf<RemoteMarker>()
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val type = when (item.optString("skipType").lowercase(Locale.US)) {
                "op", "mixed-op" -> SkipMarkerType.OPENING
                "ed", "mixed-ed" -> SkipMarkerType.ENDING
                "recap" -> SkipMarkerType.RECAP
                else -> continue
            }
            val interval = item.optJSONObject("interval") ?: continue
            val start = interval.optDouble("startTime", Double.NaN)
            val end = interval.optDouble("endTime", Double.NaN)
            if (!start.isFinite() || !end.isFinite() || end <= start) continue
            out += RemoteMarker(type, (start * 1000).toLong(), (end * 1000).toLong()).clampTo(durationMs)
        }
        return out
    }

    private fun appendWindows(
        out: MutableList<RemoteMarker>,
        windows: org.json.JSONArray?,
        type: SkipMarkerType,
        durationMs: Long
    ) {
        if (windows == null) return
        for (i in 0 until windows.length()) {
            val w = windows.optJSONObject(i) ?: continue
            val start = w.optLong("start_ms", -1L)
            val end = w.optLong("end_ms", durationMs)
            if (start >= 0L && end > start) out += RemoteMarker(type, start, end).clampTo(durationMs)
        }
    }

    private fun getJson(url: String): JSONObject? {
        return try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                JSONObject(response.body?.string().orEmpty())
            }
        } catch (_: Throwable) {
            null
        }
    }

    private data class Identity(
        val imdbId: String?,
        val season: Int?,
        val episode: Int?,
        val cleanTitle: String?
    )

    private fun parseIdentity(raw: String): Identity {
        val base = raw.substringBeforeLast('.').replace('_', ' ').replace('.', ' ').trim()
        val imdb = Regex("\\btt\\d{7,8}\\b", RegexOption.IGNORE_CASE).find(raw)?.value
        val se = Regex("(?i)\\bS(\\d{1,2})E(\\d{1,3})\\b").find(base)
        val oneX = Regex("(?i)\\b(\\d{1,2})x(\\d{1,3})\\b").find(base)
        val season = se?.groupValues?.getOrNull(1)?.toIntOrNull() ?: oneX?.groupValues?.getOrNull(1)?.toIntOrNull()
        val episode = se?.groupValues?.getOrNull(2)?.toIntOrNull() ?: oneX?.groupValues?.getOrNull(2)?.toIntOrNull()
        val title = base
            .replace(Regex("(?i)\\bS\\d{1,2}E\\d{1,3}\\b"), " ")
            .replace(Regex("(?i)\\b\\d{1,2}x\\d{1,3}\\b"), " ")
            .replace(Regex("(?i)\\b(episode|ep)\\s*\\d{1,3}\\b"), " ")
            .replace(Regex("[\\[\\]{}()]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return Identity(imdb, season, episode, title.ifBlank { null })
    }

    private fun RemoteMarker.clampTo(durationMs: Long): RemoteMarker {
        return copy(
            startMs = startMs.coerceIn(0L, durationMs),
            endMs = endMs.coerceIn(0L, durationMs)
        )
    }
}
