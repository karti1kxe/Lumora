package com.example.util.media

/** One provider plugin listed in a repository: a Vega manifest.json entry, or a CloudStream plugins.json entry. */
data class MediaPluginInfo(
    val value: String,
    val name: String,
    val version: String,
    val icon: String,
    val type: String,
    val installedAt: Long = 0L,
    /** [MediaPluginInfo.ENGINE_VEGA] (JavaScript provider) or [MediaPluginInfo.ENGINE_CLOUDSTREAM] (a CloudStream .cs3 extension). */
    val engine: String = ENGINE_VEGA,
    /** CloudStream only: where the .cs3 file is downloaded from. */
    val url: String = "",
    /** CloudStream only: the "sha256-..." the repository lists for the .cs3 file (empty = not checked). */
    val fileHash: String = "",
    /** Language code the repository lists ("en", "hi", "multi" ...), empty when unknown. */
    val language: String = "",
    /** One-line description of the provider, empty when the repository has none. */
    val description: String = "",
    /** Size of the downloadable file in bytes, 0 when unknown. */
    val size: Long = 0L
) {
    val isCloudStream: Boolean get() = engine == ENGINE_CLOUDSTREAM

    companion object {
        const val ENGINE_VEGA = "vega"
        const val ENGINE_CLOUDSTREAM = "cloudstream"
    }
}

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

/** One subtitle track a plugin's getStream offers next to a stream. */
data class MediaSubtitle(val title: String, val language: String, val uri: String)

/** One playable source returned by a plugin's getStream. */
data class MediaStream(
    val server: String,
    val link: String,
    val type: String,
    val quality: String?,
    val headers: Map<String, String>,
    val subtitles: List<MediaSubtitle> = emptyList(),
    /** The other servers the plugin returned for the same movie / episode (the player's Server list and its fallback). */
    val alternatives: List<MediaStream> = emptyList(),
    /** The plugin link (and its type) this stream was asked for: lets the player ask the same plugin for a download link later. */
    val sourceLink: String? = null,
    val sourceType: String? = null
)
