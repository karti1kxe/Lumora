package com.example.util

import android.content.Context
import com.example.player.PlayerMediaTrack
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers, PER VIDEO, which audio track (language) the user picked, so that leaving the
 * video and opening it again brings back the same audio track instead of the default one.
 *
 * Tracks are identified by title / language / index (never by mpv's numeric id, which can change
 * between loads). Keys are path / uri / id based; file-name-only keys are excluded so two
 * different videos that share a file name never share a selection.
 */
data class SavedAudioRef(
    val title: String,
    val language: String,
    val index: Int,
    val isExternal: Boolean,
    val externalName: String
)

object VideoAudioSelectionMemory {

    private const val PREFS_NAME = "video_audio_selection_v1"
    private const val KEY_PREFIX = "audio_sel_"
    private val cache = ConcurrentHashMap<String, SavedAudioRef>()

    private fun keys(path: String?, videoId: Long, uriString: String?, title: String?): List<String> =
        CustomSubtitlePersistenceManager
            .generateCandidateKeys(path, videoId, uriString, title)
            .filterNot { it.startsWith("name_") || it == "unknown_video" }

    /** Saves the audio track the user picked for this video. */
    fun save(
        context: Context,
        path: String?,
        videoId: Long,
        uriString: String?,
        title: String?,
        track: PlayerMediaTrack,
        allTracks: List<PlayerMediaTrack>
    ) {
        val ks = keys(path, videoId, uriString, title)
        if (ks.isEmpty()) return
        val embedded = allTracks.filter { !it.isExternal }
        val ref = SavedAudioRef(
            title = track.title,
            language = track.language,
            index = if (track.isExternal) -1 else embedded.indexOfFirst { it.id == track.id },
            isExternal = track.isExternal,
            externalName = track.originalFilename.ifBlank { track.title }
        )
        val json = JSONObject().apply {
            put("t", ref.title)
            put("l", ref.language)
            put("i", ref.index)
            put("x", ref.isExternal)
            put("n", ref.externalName)
        }.toString()
        for (k in ks) cache[k] = ref
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
                for (k in ks) putString(KEY_PREFIX + k, json)
                apply()
            }
        } catch (_: Throwable) {}
    }

    /** The remembered audio choice of this video, or null when the user never chose one. */
    fun get(
        context: Context,
        path: String?,
        videoId: Long,
        uriString: String?,
        title: String?
    ): SavedAudioRef? {
        val ks = keys(path, videoId, uriString, title)
        if (ks.isEmpty()) return null
        for (k in ks) cache[k]?.let { return it }
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            for (k in ks) {
                val json = prefs.getString(KEY_PREFIX + k, null) ?: continue
                val o = JSONObject(json)
                val ref = SavedAudioRef(
                    title = o.optString("t", ""),
                    language = o.optString("l", ""),
                    index = o.optInt("i", -1),
                    isExternal = o.optBoolean("x", false),
                    externalName = o.optString("n", "")
                )
                for (key in ks) cache[key] = ref
                return ref
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Finds the track that matches a remembered choice among the tracks of the freshly loaded
     * video, or null when it is not (yet) there. Never guesses a different language.
     */
    fun resolve(ref: SavedAudioRef, tracks: List<PlayerMediaTrack>): PlayerMediaTrack? {
        if (tracks.isEmpty()) return null
        if (ref.isExternal) {
            val wanted = ref.externalName.trim()
            if (wanted.isBlank()) return null
            return tracks.firstOrNull { t ->
                t.isExternal && (
                    t.originalFilename.equals(wanted, ignoreCase = true) ||
                    t.title.equals(wanted, ignoreCase = true) ||
                    File(t.originalFilename).name.equals(wanted, ignoreCase = true)
                )
            }
        }
        val embedded = tracks.filter { !it.isExternal }
        return embedded.firstOrNull { it.title == ref.title && it.language.equals(ref.language, ignoreCase = true) }
            ?: embedded.firstOrNull { ref.title.isNotBlank() && it.title == ref.title }
            ?: embedded.getOrNull(ref.index)?.takeIf {
                ref.language.isBlank() || it.language.equals(ref.language, ignoreCase = true)
            }
    }
}
