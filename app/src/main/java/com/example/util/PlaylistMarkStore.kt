package com.example.util

import android.content.Context

/**
 * Remembers which items of a playlist the user marked as Favourite (heart) or Saved (bookmark).
 * Marks are kept per playlist (by playlist id) and per item path, in one small SharedPreferences file,
 * so they survive restarts and never touch the playlist itself.
 */
object PlaylistMarkStore {
    const val KIND_FAVOURITE = "fav"
    const val KIND_SAVED = "saved"

    private const val PREFS = "lumora_playlist_marks"
    private const val SEP = "\u0001"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(kind: String, playlistId: String) = "${kind}_$playlistId"

    /** All marked item paths of [playlistId] for [kind]. */
    fun paths(context: Context, kind: String, playlistId: String): Set<String> =
        prefs(context).getStringSet(key(kind, playlistId), emptySet())
            ?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    /** Toggles the mark and returns the new state (true = now marked). */
    @Synchronized
    fun toggle(context: Context, kind: String, playlistId: String, path: String): Boolean {
        val p = prefs(context)
        val current = (p.getStringSet(key(kind, playlistId), emptySet()) ?: emptySet()).toMutableSet()
        val nowMarked = if (current.contains(path)) { current.remove(path); false } else { current.add(path); true }
        // getStringSet's result must never be modified in place, so always store a fresh copy.
        p.edit().putStringSet(key(kind, playlistId), HashSet(current)).apply()
        return nowMarked
    }

    /** Drops marks of items that are no longer in the playlist. */
    @Synchronized
    fun prune(context: Context, playlistId: String, existingPaths: Collection<String>) {
        val keep = existingPaths.toHashSet()
        val p = prefs(context)
        for (kind in listOf(KIND_FAVOURITE, KIND_SAVED)) {
            val current = p.getStringSet(key(kind, playlistId), null) ?: continue
            val filtered = current.filter { it in keep }
            if (filtered.size != current.size) p.edit().putStringSet(key(kind, playlistId), HashSet(filtered)).apply()
        }
    }
}
