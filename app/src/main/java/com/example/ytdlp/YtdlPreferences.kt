/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Ported from mpvRx (https://github.com/Riteshp2001/mpvRx), AGPL-3.0-or-later.
 * Adapted for Lumora: plain SharedPreferences instead of mpvRx's preference store.
 */

package com.example.ytdlp

import android.content.Context
import android.content.SharedPreferences

/** One stored value; `get()`/`set()` keep the ported yt-dlp code identical to mpvRx. */
class YtdlPref<T>(
  private val prefs: SharedPreferences,
  private val key: String,
  private val default: T,
  private val read: (SharedPreferences, String, T) -> T,
  private val write: (SharedPreferences.Editor, String, T) -> Unit,
) {
  fun get(): T = runCatching { read(prefs, key, default) }.getOrDefault(default)

  fun set(value: T) {
    prefs.edit().also { write(it, key, value) }.apply()
  }
}

/** yt-dlp settings (format/quality/network). Same keys and defaults as mpvRx's YtdlPreferences. */
class YtdlPreferences(
  context: Context,
) {
  private val prefs = context.applicationContext.getSharedPreferences("lumora_ytdlp_settings", Context.MODE_PRIVATE)

  private fun string(key: String, default: String = "") =
    YtdlPref(prefs, key, default, { p, k, d -> p.getString(k, d) ?: d }, { e, k, v -> e.putString(k, v) })

  private fun int(key: String, default: Int) =
    YtdlPref(prefs, key, default, { p, k, d -> p.getInt(k, d) }, { e, k, v -> e.putInt(k, v) })

  private fun bool(key: String, default: Boolean) =
    YtdlPref(prefs, key, default, { p, k, d -> p.getBoolean(k, d) }, { e, k, v -> e.putBoolean(k, v) })

  private inline fun <reified E : Enum<E>> enum(key: String, default: E) =
    YtdlPref(
      prefs,
      key,
      default,
      { p, k, d -> p.getString(k, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: d },
      { e, k, v -> e.putString(k, v.name) },
    )

  val ytdlFormat = string("video_ytdl_format")
  val ytdlQuality = int("ytdl_quality", -1) // -1 for any
  val preferH264 = bool("ytdl_prefer_h264", false)
  val codecPreference = enum("ytdl_codec_preference", YtdlCodecPreference.AUTO)
  val maxFps = int("ytdl_max_fps", 0)
  val hdrPreference = enum("ytdl_hdr_preference", YtdlHdrPreference.ANY)
  val containerPreference = enum("ytdl_container_preference", YtdlContainerPreference.ANY)
  val audioPreference = enum("ytdl_audio_preference", YtdlAudioPreference.AUTO)
  val audioQuality = enum("ytdl_audio_quality", YtdlAudioQuality.AUTO)
  val formatSort = string("ytdl_format_sort")
  val mergeOutputFormat = string("ytdl_merge_output_format")

  val writeSubs = bool("ytdl_write_subs", true)
  val writeAutoSubs = bool("ytdl_write_auto_subs", false)
  val subtitleLanguages = string("ytdl_subtitle_languages")
  val customUserAgent = string("ytdl_custom_user_agent")
  val referer = string("ytdl_referer")
  val cookiesFile = string("ytdl_cookies_file")
  val proxy = string("ytdl_proxy")
  val extractorArgs = string("ytdl_extractor_args")
  val geoBypass = bool("ytdl_geo_bypass", false)
  val playlistMode = enum("ytdl_playlist_mode", YtdlPlaylistMode.DEFAULT)
  val liveFromStart = bool("ytdl_live_from_start", false)
  val sponsorBlockMark = string("ytdl_sponsorblock_mark")
  val sponsorBlockRemove = string("ytdl_sponsorblock_remove")
  val customRawOptions = string("ytdl_custom_raw_options")

  /** "stable" or "nightly": which release the Update button in Settings > Network installs. */
  val updateChannel = string("ytdl_update_channel", "stable")

  companion object {
    @Volatile private var instance: YtdlPreferences? = null

    fun get(context: Context): YtdlPreferences =
      instance ?: synchronized(this) {
        instance ?: YtdlPreferences(context.applicationContext).also { instance = it }
      }
  }
}
