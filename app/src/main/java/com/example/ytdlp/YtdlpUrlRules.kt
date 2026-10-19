/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Ported from mpvRx (https://github.com/gyrolet/mpvRx) utils/media/HttpUtils.kt, AGPL-3.0-or-later.
 */

package com.example.ytdlp

import android.net.Uri

/** URL classification used to decide which links are routed through yt-dlp. */
internal object YtdlpUrlRules {
  val directMediaExtensions =
    setOf(
      "mp4", "m4v", "mkv", "webm", "avi", "mov", "wmv", "flv", "ts", "m2ts",
      "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "m3u", "m3u8", "mpd",
    )

  fun isYouTubeUrl(uri: Uri?): Boolean {
    if (uri == null) return false
    val host = uri.host?.lowercase().orEmpty()
    return host.contains("youtube.com") || host == "youtu.be"
  }

  fun isDirectMediaUrl(uri: Uri?): Boolean {
    if (uri == null) return false
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return false
    val lastSegment =
      uri.lastPathSegment
        ?.substringAfterLast('/')
        ?.let(Uri::decode)
        .orEmpty()
    if (lastSegment.isBlank()) return false
    return lastSegment.substringAfterLast('.', "").lowercase() in directMediaExtensions
  }
}
