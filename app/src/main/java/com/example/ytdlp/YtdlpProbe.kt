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
  ): Pair<LinkInfo?, String?> {
    if (!com.example.BuildConfig.ENABLE_YTDLP || !YtdlpManager.requiresYtdlp(url)) {
      return Pair(null, "quality list is not available")
    }
    val json = YtdlpManager.dumpSingleJson(context, url, YtdlPreferences.get(context))
    val payload = json.getOrElse { return Pair(null, it.message?.take(160) ?: "quality list is not available") }
    return runCatching { parse(payload) }
      .fold({ Pair(it, null) }, { Pair(null, "quality list is not available") })
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
    val size: Long,
  ) {
    val hasVideo get() = vcodec != null
    val hasAudio get() = acodec != null
  }

  private fun JSONObject.str(key: String): String? =
    optString(key).trim().takeIf { it.isNotEmpty() && !it.equals("null", true) }

  private fun codecOrNull(value: String?): String? = value?.takeIf { it != "none" }

  internal fun parse(payload: String): LinkInfo {
    val root = JSONObject(payload)
    val array = root.optJSONArray("formats") ?: throw IllegalStateException("no formats")
    val raws = ArrayList<Raw>()
    for (i in 0 until array.length()) {
      val f = array.optJSONObject(i) ?: continue
      val protocol = f.str("protocol").orEmpty()
      if (protocol.startsWith("mhtml") || f.str("format_note")?.contains("storyboard", true) == true) continue
      val id = f.str("format_id") ?: continue
      val size = f.optLong("filesize", 0L).takeIf { it > 0L } ?: f.optLong("filesize_approx", 0L)
      raws +=
        Raw(
          id = id,
          ext = f.str("ext") ?: "mp4",
          vcodec = codecOrNull(f.str("vcodec")),
          acodec = codecOrNull(f.str("acodec")),
          height = f.optInt("height", 0),
          fps = f.optDouble("fps", 0.0).let { if (it.isFinite()) it.toInt() else 0 },
          tbr = f.optDouble("tbr", 0.0).let { if (it.isFinite()) it else 0.0 },
          abr = f.optDouble("abr", 0.0).let { if (it.isFinite()) it else 0.0 },
          size = size,
        )
    }
    val audioOnly = raws.filter { it.hasAudio && !it.hasVideo }
    val bestAudio = audioOnly.maxByOrNull { (if (it.ext == "m4a") 1_000_000.0 else 0.0) + it.abr + it.tbr }

    val video = ArrayList<FormatOption>()
    // best stream per resolution and container
    raws
      .filter { it.hasVideo && it.height > 0 }
      .groupBy { Triple(it.height, it.fps >= 50, it.ext) }
      .values
      .map { group -> group.maxByOrNull { it.tbr } ?: group.first() }
      .sortedWith(compareByDescending<Raw> { it.height }.thenByDescending { it.fps })
      .forEach { v ->
        val withSound = v.hasAudio
        val id = if (withSound || bestAudio == null) v.id else "${v.id}+${bestAudio.id}"
        val total = v.size + if (withSound) 0L else (bestAudio?.size ?: 0L)
        video +=
          FormatOption(
            id = id,
            label = "${v.height}p" + if (v.fps >= 50) "${v.fps}" else "",
            ext = if (withSound) v.ext else "mp4",
            codec = (v.vcodec ?: "").substringBefore('.'),
            sizeBytes = total,
            hasAudio = withSound || bestAudio != null,
            height = v.height,
            fps = v.fps,
          )
      }
    val audio =
      audioOnly
        .groupBy { it.ext }
        .values
        .map { group -> group.maxByOrNull { it.abr + it.tbr } ?: group.first() }
        .sortedByDescending { it.abr + it.tbr }
        .map { a ->
          FormatOption(
            id = a.id,
            label = if (a.abr > 0) "${a.abr.toInt()} kbps" else a.ext.uppercase(),
            ext = a.ext,
            codec = (a.acodec ?: "").substringBefore('.'),
            sizeBytes = a.size,
            hasAudio = true,
          )
        }
    if (video.isEmpty() && audio.isEmpty()) throw IllegalStateException("no usable formats")
    return LinkInfo(
      title = root.str("title"),
      uploader = root.str("uploader") ?: root.str("channel"),
      durationSec = root.optDouble("duration", 0.0).let { if (it.isFinite()) it.toLong() else 0L },
      videoFormats = video,
      audioFormats = audio,
      thumbnailUrl = root.str("thumbnail"),
    )
  }
}
