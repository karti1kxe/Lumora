package com.example.util.media

/** One provider plugin listed in a repository's manifest.json (Vega-style). */
data class MediaPluginInfo(
    val value: String,
    val name: String,
    val version: String,
    val icon: String,
    val type: String,
    val installedAt: Long = 0L
)

/** A category row of a plugin (e.g. "New", "Netflix") or one of its genres. [filter] is passed back to the plugin. */
data class MediaCatalogEntry(val title: String, val filter: String)

/** One thumbnail card returned by a plugin's getPosts. Opening it is not implemented yet. */
data class MediaPost(val title: String, val link: String, val image: String, val tag: String?)

class PluginException(message: String) : Exception(message)
