package com.example.util

import java.io.File
import java.io.RandomAccessFile

/**
 * Detects "stub" MP4 files: a tiny, valid MP4 (usually a ~10 s placeholder clip saying the format is
 * not supported) followed by a large block of extra data that no standard player can read.
 *
 * Some downloader/player apps package videos like this so that the file only plays inside their own
 * app. Every ordinary player (mpv included) can only see the placeholder, which looks like a broken
 * or "unsupported" video. Detecting the layout lets Lumora tell the user what is going on instead of
 * silently showing the placeholder.
 *
 * Only the top-level box headers are read (a few small reads), so this is cheap and never touches
 * the actual video data.
 */
object PackagedStubVideoDetector {
    private const val MIN_TRAILING_BYTES = 1_000_000L
    private const val MAX_BOXES = 64

    fun isPackagedStub(path: String): Boolean {
        if (path.isBlank() || !path.startsWith("/")) return false
        val file = File(path)
        if (!file.isFile) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val total = raf.length()
                var pos = 0L
                var lastValidEnd = 0L
                var sawMdat = false
                val header = ByteArray(16)
                var count = 0
                while (count < MAX_BOXES && pos + 8 <= total) {
                    raf.seek(pos)
                    raf.readFully(header, 0, 8)
                    var size = ((header[0].toLong() and 0xFF) shl 24) or
                        ((header[1].toLong() and 0xFF) shl 16) or
                        ((header[2].toLong() and 0xFF) shl 8) or
                        (header[3].toLong() and 0xFF)
                    var printable = true
                    for (i in 4..7) {
                        val b = header[i].toInt() and 0xFF
                        if (b < 0x20 || b > 0x7E) printable = false
                    }
                    if (!printable) break
                    val isMdat = header[4] == 'm'.code.toByte() && header[5] == 'd'.code.toByte() &&
                        header[6] == 'a'.code.toByte() && header[7] == 't'.code.toByte()
                    if (size == 1L) {
                        if (pos + 16 > total) break
                        raf.readFully(header, 8, 8)
                        size = 0L
                        for (i in 8..15) size = (size shl 8) or (header[i].toLong() and 0xFF)
                    } else if (size == 0L) {
                        size = total - pos
                    }
                    if (size < 8 || pos + size > total) break
                    if (isMdat) sawMdat = true
                    pos += size
                    lastValidEnd = pos
                    count++
                }
                val trailing = total - lastValidEnd
                // Stopping only because MAX_BOXES was reached (e.g. a long fragmented MP4) is NOT a stub:
                // a stub is a file whose box chain hits unreadable data.
                count < MAX_BOXES && sawMdat && lastValidEnd > 0 &&
                    trailing > MIN_TRAILING_BYTES && trailing > lastValidEnd * 4
            }
        } catch (_: Throwable) {
            false
        }
    }
}
