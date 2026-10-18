package com.example.util.media

import android.content.Context
import com.example.util.NetworkBypass
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Fetches the subtitle files a provider lists (links) into the app cache, so the player opens them as local files:
 * mpv then never waits on a subtitle website while the video plays, and choosing a subtitle does not make the video buffer.
 */
object SubtitleDownloader {
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val MAX_BYTES = 6L * 1024 * 1024
    private val SKIP_HEADERS = setOf(
        "host", "connection", "content-length", "content-type", "accept-encoding", "range", "if-range",
        "transfer-encoding", "te", "upgrade-insecure-requests"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun extensionOf(url: String, body: ByteArray): String {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        val fromUrl = path.substringAfterLast('.', "")
        if (fromUrl in setOf("srt", "vtt", "ass", "ssa", "sub", "smi", "sbv", "ttml", "dfxp", "lrc")) return fromUrl
        val head = String(body, 0, minOf(body.size, 2048), Charsets.UTF_8)
        return when {
            head.contains("WEBVTT") -> "vtt"
            head.contains("[Script Info]", ignoreCase = true) -> "ass"
            head.contains("<tt", ignoreCase = true) || head.contains("<?xml", ignoreCase = true) -> "ttml"
            head.contains("<SAMI", ignoreCase = true) -> "smi"
            else -> "srt"
        }
    }

    private fun fetch(client: OkHttpClient, url: String, headers: Map<String, String>): ByteArray? {
        val b = Request.Builder().url(url).header("User-Agent", headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value ?: UA)
        for ((k, v) in headers) {
            if (k.isBlank() || v.isBlank() || k.equals("User-Agent", true) || k.lowercase() in SKIP_HEADERS) continue
            try { b.header(k, v) } catch (_: Throwable) {}
        }
        return client.newCall(b.build()).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val src = resp.body?.byteStream() ?: return@use null
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val n = src.read(buf)
                if (n <= 0) break
                total += n
                if (total > MAX_BYTES) return@use null
                out.write(buf, 0, n)
            }
            out.toByteArray().takeIf { it.isNotEmpty() }
        }
    }

    /** The subtitle at [url] as a local file, or null when it could not be fetched (the caller then hands the link itself to the player). */
    fun download(context: Context, url: String, headers: Map<String, String> = emptyMap()): File? {
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return null
        val dir = File(context.applicationContext.cacheDir, "plugin_subtitles").apply { mkdirs() }
        val key = MessageDigest.getInstance("MD5").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        dir.listFiles { f -> f.name.startsWith("$key.") }?.firstOrNull { it.length() > 0L }?.let { return it }
        var body = try { fetch(client, url, headers) } catch (_: Throwable) { null }
        if (body == null) {
            val proxy = try { NetworkBypass.okHttpProxy(context) } catch (_: Throwable) { null }
            if (proxy != null) body = try { fetch(client.newBuilder().proxy(proxy).build(), url, headers) } catch (_: Throwable) { null }
        }
        if (body == null) return null
        val file = File(dir, "$key.${extensionOf(url, body)}")
        file.writeBytes(body)
        return file
    }

    /**
     * A segmented HLS subtitle (a playlist of WebVTT pieces, like the subtitle renditions of a master playlist) as one
     * local .vtt file, or null when it could not be fetched. A playlist that points straight at a .vtt file works too.
     */
    fun downloadHls(context: Context, playlistUrl: String, headers: Map<String, String> = emptyMap()): File? {
        if (!playlistUrl.startsWith("http://", true) && !playlistUrl.startsWith("https://", true)) return null
        val dir = File(context.applicationContext.cacheDir, "plugin_subtitles").apply { mkdirs() }
        val key = MessageDigest.getInstance("MD5").digest(("hls:" + playlistUrl).toByteArray()).joinToString("") { "%02x".format(it) }
        val file = File(dir, "$key.vtt")
        if (file.length() > 0L) return file
        fun get(url: String): ByteArray? {
            var body = try { fetch(client, url, headers) } catch (_: Throwable) { null }
            if (body == null) {
                val proxy = try { NetworkBypass.okHttpProxy(context) } catch (_: Throwable) { null }
                if (proxy != null) body = try { fetch(client.newBuilder().proxy(proxy).build(), url, headers) } catch (_: Throwable) { null }
            }
            return body
        }
        val listing = get(playlistUrl) ?: return null
        val text = String(listing, Charsets.UTF_8)
        if (!text.trimStart().startsWith("#EXTM3U")) {
            // not a playlist: the link is already the subtitle itself
            file.writeBytes(listing)
            return file
        }
        val base = try { java.net.URI(playlistUrl) } catch (_: Throwable) { return null }
        val segments = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { try { base.resolve(it).toString() } catch (_: Throwable) { it } }
        if (segments.isEmpty()) return null
        val sb = StringBuilder("WEBVTT\n\n")
        for (seg in segments) {
            val part = get(seg) ?: continue
            val body = String(part, Charsets.UTF_8).replace("\r\n", "\n").removePrefix("\uFEFF")
            // every segment repeats the WEBVTT header (up to its first blank line): only the cues are kept
            val cues = if (body.trimStart().startsWith("WEBVTT")) body.substringAfter("\n\n", "") else body
            if (cues.isNotBlank()) sb.append(cues.trim()).append("\n\n")
        }
        if (sb.length <= 8) return null
        file.writeText(sb.toString())
        return file
    }
}
