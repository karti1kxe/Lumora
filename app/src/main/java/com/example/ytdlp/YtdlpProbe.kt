/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Lumora addition on top of the mpvRx yt-dlp runtime (AGPL-3.0-or-later): turns `yt-dlp -J` output into the
 * quality list of the download options sheet.
 */

package com.example.ytdlp

import android.content.Context
import com.example.util.FormatOption
import com.example.util.LinkInfo
import org.json.JSONObject

object YtdlpProbe {
  suspend fun load(
    context: Context,
    url: String,
    headers: Map<String, String> = emptyMap(),
  ): Pair<LinkInfo?, String?> {
    if (!com.example.BuildConfig.ENABLE_YTDLP || !YtdlpManager.requiresYtdlp(url)) {
      return Pair(null, "quality list is not available")
    }
    val json = YtdlpManager.dumpSingleJson(context, url, YtdlPreferences.get(context), headers)
    val payload = json.getOrElse { return Pair(null, it.message?.take(160) ?: "quality list is not available") }
    return runCatching { parse(payload) }
      .fold({ Pair(it, null) }, { Pair(null, "quality list is not available") })
  }

  /**
   * `--format` string for an HLS / DASH manifest without a chosen quality: the best video plus EVERY audio language the
   * manifest lists (best stream per language). null when the manifest could not be read: yt-dlp's default is used then.
   */
  suspend fun autoSpec(
    context: Context,
    url: String,
    headers: Map<String, String>,
  ): String? {
    if (!com.example.BuildConfig.ENABLE_YTDLP) return null
    val payload = YtdlpManager.dumpSingleJson(context, url, YtdlPreferences.get(context), headers).getOrNull() ?: return null
    return runCatching { buildAutoSpec(payload) }.getOrNull()
  }

  internal fun buildAutoSpec(payload: String): String? {
    val info = parse(payload)
    val top = info.videoFormats.firstOrNull() ?: return null
    val topSpec = top.spec ?: return null
    // a stream that already carries its sound and no separate audio exists: nothing to add
    val array = JSONObject(payload).optJSONArray("formats") ?: return topSpec
    class Track(val id: String, val key: String, val rate: Double)
    val best = LinkedHashMap<String, Track>()
    for (i in 0 until array.length()) {
      val f = array.optJSONObject(i) ?: continue
      val vcodec = f.str("vcodec")
      val acodec = codecOrNull(f.str("acodec")) ?: continue
      if (vcodec != null && vcodec != "none") continue
      val id = f.str("format_id") ?: continue
      val key = f.str("language") ?: f.str("format_note") ?: id
      val rate = f.optDouble("abr", 0.0).let { if (it.isFinite()) it else 0.0 } +
        f.optDouble("tbr", 0.0).let { if (it.isFinite()) it else 0.0 }
      val old = best[key]
      if (old == null || rate > old.rate) best[key] = Track(id, key, rate)
    }
    val already = topSpec.substringAfter(',', "").substringBefore('/')
    val extra = best.values.map { it.id }.filter { it != already && !topSpec.contains(",$it/") }.take(8)
    if (!topSpec.contains(',') && best.isEmpty()) return topSpec
    // top spec already holds its own audio: add the other languages after it
    return (listOf(topSpec) + extra.map { "$it/bestaudio" }).joinToString(",")
  }

  private class Raw(
    val id: String,
    val ext: String,
    val vcodec: String?,
    val acodec: String?,
    val height: Int,
    val fps: Int,
    val tbr: Double,
    val abr: Double,
    /** Exact size, else yt-dlp's own approximation, else bitrate x duration; 0 = really unknown. */
    val size: Long,
    val isHls: Boolean,
    val note: String,
    val language: String?,
  ) {
    val hasVideo get() = vcodec != null
    val hasAudio get() = acodec != null
    val isAv1 get() = vcodec?.startsWith("av01", true) == true
    val isVp9 get() = vcodec?.let { it.startsWith("vp9", true) || it.startsWith("vp09", true) } == true
    val isAvc get() = vcodec?.let { it.startsWith("avc", true) || it.startsWith("h264", true) } == true
    val isOpus get() = acodec?.let { it.startsWith("opus", true) || it.startsWith("vorbis", true) } == true
    val isAac get() = acodec?.startsWith("mp4a", true) == true
  }

  private fun JSONObject.str(key: String): String? =
    optString(key).trim().takeIf { it.isNotEmpty() && !it.equals("null", true) }

  private fun codecOrNull(value: String?): String? = value?.takeIf { it != "none" }

  /** Best of several streams of one quality: the biggest bitrate, then the biggest size. */
  private fun List<Raw>.best(): Raw = maxWithOrNull(compareBy<Raw>({ it.tbr }, { it.size })) ?: first()

  internal fun parse(payload: String): LinkInfo {
    val root = JSONObject(payload)
    val array = root.optJSONArray("formats") ?: throw IllegalStateException("no formats")
    val duration = root.optDouble("duration", 0.0).let { if (it.isFinite()) it else 0.0 }
    val rootLanguage = root.str("language")?.substringBefore('-')?.lowercase()
    val raws = ArrayList<Raw>()
    for (i in 0 until array.length()) {
      val f = array.optJSONObject(i) ?: continue
      val protocol = f.str("protocol").orEmpty()
      if (protocol.startsWith("mhtml") || f.str("format_note")?.contains("storyboard", true) == true) continue
      val id = f.str("format_id") ?: continue
      val tbr = f.optDouble("tbr", 0.0).let { if (it.isFinite()) it else 0.0 }
      val exact = f.optLong("filesize", 0L)
      val approx = f.optLong("filesize_approx", 0L)
      val size =
        when {
          exact > 0L -> exact
          approx > 0L -> approx
          tbr > 0.0 && duration > 0.0 -> (tbr * 1000.0 / 8.0 * duration).toLong()
          else -> 0L
        }
      raws +=
        Raw(
          id = id,
          ext = f.str("ext") ?: "mp4",
          vcodec = codecOrNull(f.str("vcodec")),
          acodec = codecOrNull(f.str("acodec")),
          height = f.optInt("height", 0),
          fps = f.optDouble("fps", 0.0).let { if (it.isFinite()) it.toInt() else 0 },
          tbr = tbr,
          abr = f.optDouble("abr", 0.0).let { if (it.isFinite()) it else 0.0 },
          size = size,
          isHls = protocol.contains("m3u8", true),
          note = f.str("format_note").orEmpty(),
          language = f.str("language")?.substringBefore('-')?.lowercase(),
        )
    }

    // Streams that are not the original track: dubbed language tracks and the "Premium" bitrate of a resolution
    // (it needs a subscription) are not offered while an ordinary stream exists.
    fun List<Raw>.original(): List<Raw> {
      val noDubs = filter { !it.note.contains("dubbed", true) && (rootLanguage == null || it.language == null || it.language == rootLanguage) }
      return noDubs.ifEmpty { this }
    }
    fun List<Raw>.notPremium(): List<Raw> = filter { !it.note.contains("premium", true) }.ifEmpty { this }
    // HLS renditions of the same video come without a real size (and often with another stream layout): they are
    // only offered when the link has nothing else.
    fun List<Raw>.preferDirect(): List<Raw> = filter { !it.isHls }.ifEmpty { this }

    val audioOnly = raws.filter { it.hasAudio && !it.hasVideo }.original().preferDirect()
    val bestM4a = audioOnly.filter { it.isAac || it.ext == "m4a" }.maxByOrNull { it.abr + it.tbr }
    val bestOpus = audioOnly.filter { it.isOpus }.maxByOrNull { it.abr + it.tbr }
    val bestAnyAudio = bestM4a ?: bestOpus ?: audioOnly.maxByOrNull { it.abr + it.tbr }

    val video = ArrayList<FormatOption>()
    val videoPool =
      raws.filter { it.hasVideo && it.height > 0 }
        .preferDirect()
        .notPremium()
        .let { pool -> pool.filter { !it.isAv1 }.ifEmpty { pool } }
    // one row per resolution (and per 50+ fps variant), highest first: the list never repeats a quality
    videoPool
      .groupBy { it.height to (it.fps >= 50) }
      .values
      .map { group ->
        // an H.264 stream joins with AAC into a plain MP4 everywhere; VP9 only exists where H.264 stops (1440p / 2160p)
        val avc = group.filter { it.isAvc }
        if (avc.isNotEmpty()) avc.best() else group.best()
      }
      .sortedWith(compareByDescending<Raw> { it.height }.thenByDescending { it.fps })
      .forEach { v ->
        val withSound = v.hasAudio
        val audio =
          when {
            withSound -> null
            v.isVp9 -> bestOpus ?: bestAnyAudio
            else -> bestM4a ?: bestAnyAudio
          }
        val webm = !withSound && v.isVp9 && audio?.isOpus == true
        val fpsPart = if (v.fps >= 50) "${v.fps}" else ""
        val codecFilter = if (v.isVp9) "[vcodec^=vp]" else if (v.isAvc) "[vcodec^=avc]" else ""
        val videoSpec = "${v.id}/bestvideo[height<=${v.height}]$codecFilter/bestvideo[height<=${v.height}]"
        val spec =
          when {
            withSound -> "${v.id}/best[height<=${v.height}]"
            audio == null -> videoSpec
            else -> "$videoSpec,${audio.id}/bestaudio[ext=${audio.ext}]/bestaudio"
          }
        // unknown video size stays unknown: the audio alone must never be shown as the size of the whole file
        val total = if (v.size > 0L) v.size + (audio?.size ?: 0L) else 0L
        video +=
          FormatOption(
            id = if (audio == null) v.id else "${v.id}+${audio.id}",
            label = "${v.height}p$fpsPart",
            ext = if (withSound) v.ext else if (webm) "webm" else "mp4",
            codec = (v.vcodec ?: "").substringBefore('.'),
            sizeBytes = total,
            hasAudio = withSound || audio != null,
            height = v.height,
            fps = v.fps,
            spec = spec,
          )
      }

    val audio =
      audioOnly
        .groupBy { it.ext to it.abr.toInt() }
        .values
        .map { it.best() }
        .sortedWith(compareByDescending<Raw> { it.abr + it.tbr }.thenBy { if (it.ext == "m4a") 0 else 1 })
        .map { a ->
          FormatOption(
            id = a.id,
            label = if (a.abr > 0) "${a.abr.toInt()} kbps" else a.ext.uppercase(),
            ext = a.ext,
            codec = (a.acodec ?: "").substringBefore('.'),
            sizeBytes = a.size,
            hasAudio = true,
            spec = "${a.id}/bestaudio[ext=${a.ext}]/bestaudio",
          )
        }
    if (video.isEmpty() && audio.isEmpty()) throw IllegalStateException("no usable formats")
    return LinkInfo(
      title = root.str("title"),
      uploader = root.str("uploader") ?: root.str("channel"),
      durationSec = duration.toLong(),
      videoFormats = video,
      audioFormats = audio,
      thumbnailUrl = root.str("thumbnail"),
    )
  }
}
