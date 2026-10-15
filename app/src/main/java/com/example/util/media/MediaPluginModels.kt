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

/** One playable entry of a quality option: the movie itself, or one episode (a link the plugin's getStream understands). */
data class MediaDirectLink(
    val title: String,
    val link: String,
    val type: String?,
    val image: String?,
    val description: String?
)

/**
 * One option of a title's "quality" list. [directLinks] are the movie / episodes themselves, or [episodesLink] has to be
 * opened with the plugin's getEpisodes first (series).
 */
data class MediaLink(
    val title: String,
    val quality: String?,
    val episodesLink: String?,
    val directLinks: List<MediaDirectLink>
)

/** Details of one title (movie / series), from the plugin's getMeta. */
data class MediaInfo(
    val title: String,
    val image: String,
    val poster: String,
    val logo: String,
    val synopsis: String,
    val type: String,
    val tags: List<String>,
    val rating: String,
    val webUrl: String,
    val linkList: List<MediaLink>
)

/** One playable source returned by a plugin's getStream. */
data class MediaStream(
    val server: String,
    val link: String,
    val type: String,
    val quality: String?,
    val headers: Map<String, String>
)
