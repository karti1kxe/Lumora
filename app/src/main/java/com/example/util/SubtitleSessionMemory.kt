package com.example.util

import com.example.player.PlayerMediaTrack

/**
 * SubtitleSessionMemory
 *
 * Implements "Subtitle Track Persistence & Session Memory":
 * 1. Auto-Select Same Subtitle Track Number for Next Episodes (Series/Playlist):
 *    - When continuous playback or playlist navigation moves to the next episode/video,
 *      if the user previously selected a specific subtitle track number (e.g. Track 2, Track 3),
 *      that exact track number is automatically selected and played if the next video has it.
 *    - If the next episode does not have that specific track number, it falls back gracefully
 *      to the default player behavior without forcing an invalid track.
 * 2. Session memory tracks whether subtitles are enabled or explicitly turned off.
 */
object SubtitleSessionMemory {

    /**
     * The 1-based subtitle track number chosen by the user during this session
     * (e.g. 2 for "Track 2", 3 for "Track 3").
     * null indicates no explicit user override (default behavior).
     */
    @Volatile
    var preferredTrackNumber: Int? = null
        private set

    /**
     * The MPV track ID chosen by the user (if known).
     */
    @Volatile
    var preferredTrackId: Int? = null
        private set

    /**
     * Whether subtitles are currently enabled or explicitly turned off by the user.
     */
    @Volatile
    var isSubtitlesEnabled: Boolean = true
        private set

    /**
     * Update the user's preferred subtitle track number.
     * @param trackNumber 1-based track number (e.g. 1, 2, 3...). 0 or negative indicates Subtitles Off.
     * @param trackId The underlying MPV track ID.
     */
    fun setPreferredTrack(trackNumber: Int, trackId: Int = trackNumber) {
        if (trackNumber <= 0) {
            isSubtitlesEnabled = false
            preferredTrackNumber = 0
            preferredTrackId = 0
        } else {
            isSubtitlesEnabled = true
            preferredTrackNumber = trackNumber
            preferredTrackId = trackId
        }
    }

    /**
     * Updates visibility state when user toggles subtitles on or off.
     */
    fun setSubtitlesEnabled(enabled: Boolean) {
        isSubtitlesEnabled = enabled
        if (!enabled) {
            preferredTrackNumber = 0
            preferredTrackId = 0
        }
    }

    /**
     * Inspects a newly loaded episode's subtitle tracks to see if a track with
     * the user's preferred track number exists.
     *
     * Condition: Auto-selection only applies if the next video/episode has a subtitle
     * track with the same track number (e.g. "Track 2" or "Track 3").
     * If the next episode does not have that specific track number, returns null
     * so that the player keeps default behavior.
     */
    fun findMatchingTrackForNextEpisode(tracks: List<PlayerMediaTrack>): PlayerMediaTrack? {
        if (!isSubtitlesEnabled) return null
        val targetNumber = preferredTrackNumber ?: return null
        if (targetNumber <= 0) return null

        val embedded = tracks.filter { !it.isExternal }

        // 1. Try matching embedded tracks by 1-based index (e.g. Track 2 -> index 1)
        val byIndex = embedded.getOrNull(targetNumber - 1)
        if (byIndex != null) return byIndex

        // 2. Try matching by track ID
        val byId = tracks.firstOrNull { it.id == targetNumber }
        if (byId != null) return byId

        // 3. Try matching by title pattern "Track $targetNumber"
        val byTitle = tracks.firstOrNull {
            it.title.equals("Track $targetNumber", ignoreCase = true) ||
            it.title.startsWith("Track $targetNumber ", ignoreCase = true) ||
            it.title.startsWith("Track $targetNumber:", ignoreCase = true) ||
            it.title.startsWith("Track $targetNumber •", ignoreCase = true)
        }
        if (byTitle != null) return byTitle

        return null
    }
}
