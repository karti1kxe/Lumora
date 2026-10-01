package com.example.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Singleton — fetches synced (LRC) or plain lyrics for a track from LRCLIB (https://lrclib.net),
 * a free, keyless, open lyrics API. Used for Online Music tracks only; local files keep using
 * the existing embedded-tag extraction in AudioMetadataExtractor.kt (untouched).
 *
 * The returned syncedLyrics is plain LRC-format text (e.g. "[00:12.34]Some line"), which is
 * assigned directly to AudioTrackItem.lyricsText — AudioPlayerScreen.kt's existing LRC
 * timestamp parsing / auto-scroll pipeline (built for local .lrc files) consumes it unchanged.
 */
object LyricsFetcher {
    private const val TAG = "LyricsFetcher"
    private const val BASE_URL = "https://lrclib.net/api"
    private val httpClient = OkHttpClient.Builder().build()

    data class LyricsResult(val syncedLyrics: String?, val plainLyrics: String?)

    /** Best-effort lyrics lookup. Returns null if nothing found or on any network error. */
    suspend fun fetch(title: String, artist: String, durationSec: Long): LyricsResult? =
        withContext(Dispatchers.IO) {
            try {
                fetchExact(title, artist, durationSec) ?: fetchViaSearch(title, artist)
            } catch (e: Exception) {
                Log.e(TAG, "fetch failed for '$title' by '$artist': ${e.message}", e)
                null
            }
        }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun fetchExact(title: String, artist: String, durationSec: Long): LyricsResult? {
        var url = "$BASE_URL/get?track_name=${encode(title)}&artist_name=${encode(artist)}"
        if (durationSec > 0) url += "&duration=$durationSec"
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            return parseEntry(JSONObject(body))
        }
    }

    private fun fetchViaSearch(title: String, artist: String): LyricsResult? {
        val url = "$BASE_URL/search?track_name=${encode(title)}&artist_name=${encode(artist)}"
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val results = JSONArray(body)
            if (results.length() == 0) return null
            return parseEntry(results.getJSONObject(0))
        }
    }

    private fun parseEntry(json: JSONObject): LyricsResult? {
        val synced = json.optString("syncedLyrics").takeIf { it.isNotBlank() }
        val plain = json.optString("plainLyrics").takeIf { it.isNotBlank() }
        if (synced == null && plain == null) return null
        return LyricsResult(synced, plain)
    }
}
