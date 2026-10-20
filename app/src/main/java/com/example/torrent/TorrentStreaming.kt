package com.example.torrent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.ConcurrentHashMap

/**
 * Process wide holder of the torrent streaming engine (mpvRx gets it from Koin; Lumora has no DI container).
 * The engine owns ONE libtorrent session at a time: the picker's prepared session, then the playing stream.
 */
object TorrentStreaming {
    @Volatile private var instance: TorrentStreamingEngine? = null

    fun engine(context: Context): TorrentStreamingEngine =
        instance ?: synchronized(this) {
            instance ?: TorrentStreamingEngine(context.applicationContext).also { instance = it }
        }

    /** true while the video player owns the engine session (it called startStream for a torrent item). */
    @Volatile private var playerOwnsSession = false

    fun markPlayerOwnsSession() {
        playerOwnsSession = true
    }

    /**
     * Player closed / switched to a non-torrent item: native session and temporary data are released (mpvRx:
     * stopStream when playback ends). Does nothing when the player never started a torrent, so the picker's own
     * prepared session is not torn down by an unrelated player exit.
     */
    fun stopPlayback() {
        if (!playerOwnsSession) return
        playerOwnsSession = false
        instance?.stopStream()
    }

    /** Engine state for the player's buffering text; Idle when no torrent is involved. */
    val idleState = kotlinx.coroutines.flow.MutableStateFlow<TorrentStreamingState>(TorrentStreamingState.Idle)

    /** "<source>#<fileIndex>" -> preparation token of the picker, consumed once by the player (see [takePreparation]). */
    private val preparations = ConcurrentHashMap<String, String>()

    /**
     * libtorrent sessions (stream, picker preparation, downloads) are created and closed one at a time: two sessions
     * starting / stopping at the same moment (picker closes while the download starts) is a native crash risk.
     */
    val nativeLock = Any()

    /** Work that must outlive the picker / player composables (closing a session, starting a download). */
    val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    fun handOffPreparation(source: String, fileIndex: Int, preparationId: String) {
        preparations["$source#$fileIndex"] = preparationId
    }

    fun takePreparation(source: String, fileIndex: Int): String? = preparations.remove("$source#$fileIndex")
}

enum class TorrentPickerMode { PLAY, DOWNLOAD }

data class TorrentPickerRequest(
    val source: String,
    val mode: TorrentPickerMode = TorrentPickerMode.PLAY
)

/** The torrent file picker is opened from anywhere (Network tab, magnet / .torrent intent, player) through this state. */
object TorrentPickerState {
    var request by mutableStateOf<TorrentPickerRequest?>(null)
        private set

    fun open(source: String, mode: TorrentPickerMode = TorrentPickerMode.PLAY) {
        request = TorrentPickerRequest(source.trim(), mode)
    }

    fun close() {
        request = null
    }
}

private val magnetInText = Regex("""magnet:\?[^\s"'<>]+""", RegexOption.IGNORE_CASE)

/**
 * The torrent source carried by an incoming VIEW / SEND intent (magnet link, torrent:// link, .torrent file or link,
 * or a magnet link inside shared text), or null when the intent is not a torrent.
 */
fun torrentSourceFromIntent(intent: Intent): String? {
    val type = intent.type
    val candidates = ArrayList<String>(4)
    intent.dataString?.let(candidates::add)
    if (intent.action == Intent.ACTION_SEND) {
        val stream: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        stream?.toString()?.let(candidates::add)
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
            magnetInText.find(text)?.value?.let(candidates::add) ?: candidates.add(text.trim())
        }
    }
    for (candidate in candidates) {
        val value = candidate.trim()
        if (value.isNotEmpty() && isTorrentSource(value, type)) return normalizeTorrentSource(value) ?: value
    }
    return null
}
