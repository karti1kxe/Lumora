package com.example.torrent

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class TorrentPickerRequest(
    val source: String
)

/** The torrent file picker (download only) is opened from anywhere (Network tab, magnet / .torrent intent) through this state. */
object TorrentPickerState {
    var request by mutableStateOf<TorrentPickerRequest?>(null)
        private set

    fun open(source: String) {
        request = TorrentPickerRequest(source.trim())
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
