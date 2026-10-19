package com.example.util

import android.content.Context

/**
 * Settings > Network: the HLS stream handling and whether subtitles of pasted links are loaded. Plain
 * SharedPreferences, readable from anywhere (resolver, player) without going through the view model.
 */
object NetworkSettings {
    private const val PREFS = "lumora_network_settings"
    private const val KEY_HLS = "hls_stream_handling"
    private const val KEY_SUBS = "download_media_subtitles"
    private const val KEY_AUTO_SUBS = "include_auto_generated_subtitles"

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hlsHandlingEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_HLS, true)
    fun setHlsHandlingEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_HLS, v).apply()

    fun downloadSubtitles(c: Context): Boolean = prefs(c).getBoolean(KEY_SUBS, true)
    fun setDownloadSubtitles(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_SUBS, v).apply()

    fun autoSubtitles(c: Context): Boolean = prefs(c).getBoolean(KEY_AUTO_SUBS, true)
    fun setAutoSubtitles(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_AUTO_SUBS, v).apply()

}
