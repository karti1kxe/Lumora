package com.example.util

import android.content.Context

/**
 * Settings > Network: P2P torrent streaming, the HLS stream handling, which yt-dlp release channel is kept up to date
 * and whether subtitles of pasted links are loaded. Plain SharedPreferences, readable from anywhere (resolver, player,
 * torrent engine) without going through the view model.
 */
object NetworkSettings {
    private const val PREFS = "lumora_network_settings"
    private const val KEY_P2P = "p2p_torrent_streaming"
    private const val KEY_HLS = "hls_stream_handling"
    private const val KEY_SUBS = "download_media_subtitles"
    private const val KEY_AUTO_SUBS = "include_auto_generated_subtitles"
    private const val KEY_CHANNEL = "ytdlp_release_channel"

    const val CHANNEL_STABLE = "stable"
    const val CHANNEL_NIGHTLY = "nightly"

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun p2pEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_P2P, true)
    fun setP2pEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_P2P, v).apply()

    fun hlsHandlingEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_HLS, true)
    fun setHlsHandlingEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_HLS, v).apply()

    fun downloadSubtitles(c: Context): Boolean = prefs(c).getBoolean(KEY_SUBS, true)
    fun setDownloadSubtitles(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_SUBS, v).apply()

    fun autoSubtitles(c: Context): Boolean = prefs(c).getBoolean(KEY_AUTO_SUBS, true)
    fun setAutoSubtitles(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_AUTO_SUBS, v).apply()

    /** [CHANNEL_STABLE] (default) or [CHANNEL_NIGHTLY]. */
    fun ytDlpChannel(c: Context): String =
        if (prefs(c).getString(KEY_CHANNEL, CHANNEL_STABLE) == CHANNEL_NIGHTLY) CHANNEL_NIGHTLY else CHANNEL_STABLE
    fun setYtDlpChannel(c: Context, channel: String) =
        prefs(c).edit().putString(KEY_CHANNEL, if (channel == CHANNEL_NIGHTLY) CHANNEL_NIGHTLY else CHANNEL_STABLE).apply()
}
