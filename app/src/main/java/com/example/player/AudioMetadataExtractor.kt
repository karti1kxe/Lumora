package com.example.player

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.ui.screens.AudioTrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.util.Locale

/**
 * Advanced Audio Metadata & Embedded Lyrics Extractor.
 * Supports:
 * - ID3v2.2, ID3v2.3, ID3v2.4 frames: USLT (Unsynchronized lyrics), SYLT (Synchronized lyrics), TXXX (Lyrics)
 * - MP4/M4A atoms: moov/udta/meta/ilst -> "\u00A9lyr" (proper box walking, works regardless of
 *   whether the moov box sits before or after mdat in the file)
 * - FLAC metadata-block walking for the VORBIS_COMMENT block (LYRICS / UNSYNCEDLYRICS), robust to
 *   large embedded cover-art PICTURE blocks appearing before it
 * - OGG/Opus Vorbis comment scanning (buffer-based fallback)
 * - MediaMetadataRetriever tags
 * - Companion .lrc / .txt files
 */
object AudioMetadataExtractor {
    private const val TAG = "AudioMetadataExtractor"

    data class ExtractedAudioMeta(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val durationMs: Long = 0L,
        val lyrics: String? = null,
        val isSyncedLyrics: Boolean = false,
        val audioType: String? = null
    )

    /**
     * Reads until [buffer] is completely filled or the stream is exhausted, looping over
     * however many chunks InputStream.read() decides to hand back. A single, unlooped
     * input.read(buffer) call is NOT guaranteed by the InputStream contract to fill the
     * buffer even when enough bytes remain (Android's storage/FUSE layer routinely returns
     * partial reads for large single calls) — relying on that previously truncated tag/atom
     * data and silently missed embedded lyrics placed after other data (e.g. album art).
     * Returns the number of bytes actually read (less than buffer.size only at true EOF).
     */
    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var totalRead = 0
        while (totalRead < buffer.size) {
            val n = input.read(buffer, totalRead, buffer.size - totalRead)
            if (n < 0) break
            totalRead += n
        }
        return totalRead
    }

    /**
     * Extracts full metadata and embedded lyrics from an AudioTrackItem.
     */
    suspend fun extractFullMetadata(context: Context, track: AudioTrackItem): ExtractedAudioMeta =
        withContext(Dispatchers.IO) {
            val file = if (track.path.isNotBlank()) File(track.path) else null
            val uri = track.uri

            var lyrics: String? = null
            var isSynced = false
            var title: String? = null
            var artist: String? = null
            var album: String? = null
            var durationMs: Long = track.durationMs

            // 1. Try MediaMetadataRetriever
            val retriever = MediaMetadataRetriever()
            try {
                if (file != null && file.exists() && file.canRead()) {
                    retriever.setDataSource(file.absolutePath)
                } else if (uri != Uri.EMPTY) {
                    retriever.setDataSource(context, uri)
                }

                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                if (durStr != null) {
                    durStr.toLongOrNull()?.let { if (it > 0) durationMs = it }
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaMetadataRetriever failed: ${e.message}")
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }

            // 2. Extract Embedded Lyrics from file directly if available
            if (file != null && file.exists() && file.canRead()) {
                // A. Check companion .lrc file first
                val lrcFile = File(file.parentFile ?: file, file.nameWithoutExtension + ".lrc")
                if (lrcFile.exists() && lrcFile.length() > 0) {
                    try {
                        lyrics = lrcFile.readText(Charsets.UTF_8).ifBlank { null }
                        if (lyrics != null) isSynced = lyrics.contains("[0") || lyrics.contains("[1")
                    } catch (_: Exception) {}
                }

                // B. If no companion .lrc, extract embedded ID3v2 / MP4 / FLAC lyrics
                if (lyrics.isNullOrBlank()) {
                    val ext = file.extension.lowercase()
                    when (ext) {
                        "mp3" -> {
                            val id3Lyrics = extractId3Lyrics(file)
                            if (!id3Lyrics.isNullOrBlank()) {
                                lyrics = id3Lyrics
                                isSynced = lyrics.contains("[0") || lyrics.contains("[1")
                            }
                        }
                        "m4a", "mp4", "aac" -> {
                            val m4aLyrics = extractMp4Lyrics(file)
                            if (!m4aLyrics.isNullOrBlank()) {
                                lyrics = m4aLyrics
                            }
                        }
                        "flac", "ogg", "opus" -> {
                            val vorbisLyrics = extractVorbisLyrics(file)
                            if (!vorbisLyrics.isNullOrBlank()) {
                                lyrics = vorbisLyrics
                            }
                        }
                    }
                }

                // C. Check companion .txt file if still no lyrics
                if (lyrics.isNullOrBlank()) {
                    val txtFile = File(file.parentFile ?: file, file.nameWithoutExtension + ".txt")
                    if (txtFile.exists() && txtFile.length() in 10..100_000) {
                        try {
                            val text = txtFile.readText(Charsets.UTF_8)
                            if (text.lines().size >= 4) {
                                lyrics = text
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            ExtractedAudioMeta(
                title = title?.ifBlank { null },
                artist = artist?.ifBlank { null },
                album = album?.ifBlank { null },
                durationMs = durationMs,
                lyrics = lyrics?.trim()?.ifBlank { null },
                isSyncedLyrics = isSynced
            )
        }

    /**
     * Extracts ID3v2 USLT (Unsynchronized lyrics) or SYLT (Synchronized lyrics) from MP3 file.
     */
    fun extractId3Lyrics(file: File): String? {
        try {
            FileInputStream(file).use { input ->
                val header = ByteArray(10)
                if (readFully(input, header) != 10) return null

                // Check "ID3" identifier
                if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
                    return null
                }

                val majorVersion = header[3].toInt() and 0xFF
                val tagSize = decodeSynchSafeInt(header[6], header[7], header[8], header[9])
                if (tagSize <= 0 || tagSize > 15 * 1024 * 1024) return null

                val tagBuffer = ByteArray(tagSize.coerceAtMost(4 * 1024 * 1024))
                // Loop-read the full tag (see readFully doc) — a single read() call can return
                // far fewer bytes than requested, which previously truncated the tag right
                // before frames placed after large data (e.g. an APIC cover-art frame that
                // commonly precedes USLT/SYLT), making real lyrics silently undetectable.
                val bytesRead = readFully(input, tagBuffer)
                if (bytesRead <= 0) return null

                return parseId3FramesForLyrics(tagBuffer, bytesRead, majorVersion)
            }
        } catch (e: Exception) {
            Log.d(TAG, "ID3 extraction error: ${e.message}")
        }
        return null
    }

    private fun parseId3FramesForLyrics(buffer: ByteArray, length: Int, version: Int): String? {
        var offset = 0
        val isV22 = (version == 2)
        val frameHeaderSize = if (isV22) 6 else 10

        var foundSyltLyrics: String? = null
        var foundUsltLyrics: String? = null
        var foundTxxxLyrics: String? = null

        while (offset + frameHeaderSize < length) {
            val frameId = if (isV22) {
                String(buffer, offset, 3, Charsets.ISO_8859_1)
            } else {
                String(buffer, offset, 4, Charsets.ISO_8859_1)
            }

            if (frameId.isBlank() || frameId[0] == '\u0000') break

            val frameSize = if (isV22) {
                ((buffer[offset + 3].toInt() and 0xFF) shl 16) or
                        ((buffer[offset + 4].toInt() and 0xFF) shl 8) or
                        (buffer[offset + 5].toInt() and 0xFF)
            } else if (version == 4) {
                decodeSynchSafeInt(buffer[offset + 4], buffer[offset + 5], buffer[offset + 6], buffer[offset + 7])
            } else {
                ((buffer[offset + 4].toInt() and 0xFF) shl 24) or
                        ((buffer[offset + 5].toInt() and 0xFF) shl 16) or
                        ((buffer[offset + 6].toInt() and 0xFF) shl 8) or
                        (buffer[offset + 7].toInt() and 0xFF)
            }

            if (frameSize <= 0 || offset + frameHeaderSize + frameSize > length) break

            val frameDataOffset = offset + frameHeaderSize

            // Check for SYLT (ID3v2.3/2.4) or SLT (ID3v2.2) - Prioritize synced lyrics
            if (frameId == "SYLT" || frameId == "SLT") {
                val lyrics = parseSyltFrame(buffer, frameDataOffset, frameSize)
                if (!lyrics.isNullOrBlank()) {
                    foundSyltLyrics = lyrics
                }
            }

            // Check for USLT (ID3v2.3/2.4) or ULT (ID3v2.2)
            if (frameId == "USLT" || frameId == "ULT") {
                val lyrics = parseUsltFrame(buffer, frameDataOffset, frameSize)
                if (!lyrics.isNullOrBlank()) {
                    foundUsltLyrics = lyrics
                }
            }

            // Check for TXXX frame with description "LYRICS" or "UNSYNCEDLYRICS"
            if (frameId == "TXXX" || frameId == "TXX") {
                val lyrics = parseTxxxFrame(buffer, frameDataOffset, frameSize)
                if (!lyrics.isNullOrBlank()) {
                    foundTxxxLyrics = lyrics
                }
            }

            offset += frameHeaderSize + frameSize
        }

        // Return best lyrics found (prefer SYLT > USLT > TXXX)
        return foundSyltLyrics ?: foundUsltLyrics ?: foundTxxxLyrics
    }

    private fun parseUsltFrame(buffer: ByteArray, offset: Int, size: Int): String? {
        if (size < 5) return null
        val encodingByte = buffer[offset].toInt() and 0xFF
        val charset = getCharset(encodingByte)

        var dataPos = offset + 4
        val endPos = offset + size

        // Skip descriptor
        if (encodingByte == 1 || encodingByte == 2) {
            while (dataPos + 1 < endPos) {
                if (buffer[dataPos] == 0.toByte() && buffer[dataPos + 1] == 0.toByte()) {
                    dataPos += 2
                    break
                }
                dataPos += 2
            }
        } else {
            while (dataPos < endPos) {
                if (buffer[dataPos] == 0.toByte()) {
                    dataPos += 1
                    break
                }
                dataPos += 1
            }
        }

        val textLength = endPos - dataPos
        if (textLength > 0) {
            return String(buffer, dataPos, textLength, charset).trim().ifBlank { null }
        }
        return null
    }

    private fun parseSyltFrame(buffer: ByteArray, offset: Int, size: Int): String? {
        if (size < 6) return null
        val encodingByte = buffer[offset].toInt() and 0xFF
        val charset = getCharset(encodingByte)
        val timeStampFormat = buffer[offset + 4].toInt() and 0xFF // 1 = MPEG frames, 2 = ms

        var dataPos = offset + 6
        val endPos = offset + size

        // Skip descriptor
        if (encodingByte == 1 || encodingByte == 2) {
            while (dataPos + 1 < endPos) {
                if (buffer[dataPos] == 0.toByte() && buffer[dataPos + 1] == 0.toByte()) {
                    dataPos += 2
                    break
                }
                dataPos += 2
            }
        } else {
            while (dataPos < endPos) {
                if (buffer[dataPos] == 0.toByte()) {
                    dataPos += 1
                    break
                }
                dataPos += 1
            }
        }

        // Parse SYLT entries: [Text string][Delimiter][4-byte timestamp]
        val builder = StringBuilder()
        while (dataPos < endPos) {
            val textStart = dataPos
            if (encodingByte == 1 || encodingByte == 2) {
                while (dataPos + 1 < endPos && !(buffer[dataPos] == 0.toByte() && buffer[dataPos + 1] == 0.toByte())) {
                    dataPos += 2
                }
            } else {
                while (dataPos < endPos && buffer[dataPos] != 0.toByte()) {
                    dataPos++
                }
            }

            val textLen = dataPos - textStart
            val text = if (textLen > 0) String(buffer, textStart, textLen, charset).trim() else ""

            // Advance over null delimiter
            if (encodingByte == 1 || encodingByte == 2) dataPos += 2 else dataPos += 1

            if (dataPos + 4 <= endPos) {
                val rawTime = ((buffer[dataPos].toInt() and 0xFF) shl 24) or
                        ((buffer[dataPos + 1].toInt() and 0xFF) shl 16) or
                        ((buffer[dataPos + 2].toInt() and 0xFF) shl 8) or
                        (buffer[dataPos + 3].toInt() and 0xFF)
                dataPos += 4

                val timeMs = if (timeStampFormat == 2 || timeStampFormat == 0) {
                    rawTime.toLong()
                } else {
                    // MPEG frames -> rough approx 26ms per frame
                    (rawTime * 26L)
                }

                if (timeMs >= 0) {
                    val totalSec = timeMs / 1000
                    val min = totalSec / 60
                    val sec = totalSec % 60
                    val ms = (timeMs % 1000) / 10
                    builder.append(String.format(Locale.US, "[%02d:%02d.%02d]%s\n", min, sec, ms, text))
                }
            } else {
                break
            }
        }

        val result = builder.toString().trim()
        if (result.isNotBlank()) return result

        // Fallback to raw string if structured parsing yielded nothing
        val raw = String(buffer, offset + 6, (endPos - (offset + 6)).coerceAtLeast(0), charset)
        return raw.trim().ifBlank { null }
    }

    private fun parseTxxxFrame(buffer: ByteArray, offset: Int, size: Int): String? {
        if (size < 2) return null
        val encodingByte = buffer[offset].toInt() and 0xFF
        val charset = getCharset(encodingByte)
        val full = String(buffer, offset + 1, size - 1, charset)
        if (full.contains("LYRICS", ignoreCase = true)) {
            val parts = full.split("\u0000")
            if (parts.size >= 2) return parts.subList(1, parts.size).joinToString("\n").trim()
        }
        return null
    }

    /**
     * Extracts MP4/M4A embedded lyrics by walking the real ISO-BMFF box structure:
     * moov -> udta -> meta (full box, +4 bytes version/flags) -> ilst -> "\u00A9lyr" -> data.
     * Unlike a blind byte-scan of only the first N KB, this correctly finds the lyrics
     * regardless of where `moov` sits in the file — many M4A files place `moov` (and its
     * metadata) *after* the audio data (`mdat`), sometimes tens of megabytes in, which a
     * fixed-size prefix scan would always miss.
     */
    fun extractMp4Lyrics(file: File): String? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                findMp4LyricsInRange(raf, 0L, raf.length(), depth = 0)
            }
        } catch (e: Exception) {
            Log.d(TAG, "MP4 lyrics extraction error: ${e.message}")
            null
        }
    }

    private fun findMp4LyricsInRange(raf: RandomAccessFile, start: Long, end: Long, depth: Int): String? {
        // Sanity guard against malformed/corrupt box sizes that could otherwise recurse deeply.
        if (depth > 8) return null
        var pos = start

        while (pos + 8 <= end) {
            raf.seek(pos)
            val sizeBytes = ByteArray(4)
            val typeBytes = ByteArray(4)
            raf.readFully(sizeBytes)
            raf.readFully(typeBytes)

            var boxSize = ((sizeBytes[0].toLong() and 0xFF) shl 24) or
                    ((sizeBytes[1].toLong() and 0xFF) shl 16) or
                    ((sizeBytes[2].toLong() and 0xFF) shl 8) or
                    (sizeBytes[3].toLong() and 0xFF)
            var headerSize = 8L

            if (boxSize == 1L) {
                // 64-bit extended size follows the type field
                if (pos + 16 > end) break
                val largeBytes = ByteArray(8)
                raf.readFully(largeBytes)
                var large = 0L
                for (b in largeBytes) large = (large shl 8) or (b.toLong() and 0xFF)
                boxSize = large
                headerSize = 16L
            } else if (boxSize == 0L) {
                // Box extends to the end of its parent/file
                boxSize = end - pos
            }

            if (boxSize < headerSize || pos + boxSize > end) break

            val boxType = String(typeBytes, Charsets.ISO_8859_1)
            val contentStart = pos + headerSize
            val contentEnd = (pos + boxSize).coerceAtMost(end)
            if (contentEnd <= contentStart) {
                pos += boxSize
                continue
            }

            when (boxType) {
                "moov", "udta", "ilst" -> {
                    val found = findMp4LyricsInRange(raf, contentStart, contentEnd, depth + 1)
                    if (found != null) return found
                }
                "meta" -> {
                    // 'meta' is a "full box": 4 bytes of version+flags precede its children.
                    val childStart = (contentStart + 4).coerceAtMost(contentEnd)
                    val found = findMp4LyricsInRange(raf, childStart, contentEnd, depth + 1)
                    if (found != null) return found
                }
                "\u00A9lyr" -> {
                    val text = readMp4IlstTextData(raf, contentStart, contentEnd)
                    if (!text.isNullOrBlank()) return text
                }
            }

            pos += boxSize
        }
        return null
    }

    /** Reads the text payload out of the "data" sub-box of an ilst item atom (e.g. "\u00A9lyr"). */
    private fun readMp4IlstTextData(raf: RandomAccessFile, start: Long, end: Long): String? {
        var pos = start
        while (pos + 8 <= end) {
            raf.seek(pos)
            val sizeBytes = ByteArray(4)
            val typeBytes = ByteArray(4)
            raf.readFully(sizeBytes)
            raf.readFully(typeBytes)
            val boxSize = ((sizeBytes[0].toLong() and 0xFF) shl 24) or
                    ((sizeBytes[1].toLong() and 0xFF) shl 16) or
                    ((sizeBytes[2].toLong() and 0xFF) shl 8) or
                    (sizeBytes[3].toLong() and 0xFF)
            if (boxSize < 8) break

            val boxType = String(typeBytes, Charsets.ISO_8859_1)
            val contentStart = pos + 8
            val contentEnd = (pos + boxSize).coerceAtMost(end)

            if (boxType == "data" && contentEnd - contentStart > 8) {
                // Skip 4-byte type indicator + 4-byte locale/reserved field.
                val textStart = contentStart + 8
                val textLen = (contentEnd - textStart).toInt()
                if (textLen > 0) {
                    val textBytes = ByteArray(textLen)
                    raf.seek(textStart)
                    raf.readFully(textBytes)
                    val text = String(textBytes, Charsets.UTF_8).trim()
                    if (text.isNotBlank()) return text
                }
            }
            pos += boxSize
        }
        return null
    }

    /**
     * Extracts FLAC / OGG Vorbis comment lyrics (LYRICS, UNSYNCEDLYRICS or SYNCEDLYRICS).
     */
    fun extractVorbisLyrics(file: File): String? {
        return try {
            if (file.extension.equals("flac", ignoreCase = true)) {
                extractFlacVorbisComment(file) ?: extractOggStyleVorbisComment(file)
            } else {
                extractOggStyleVorbisComment(file)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Vorbis lyrics extraction error: ${e.message}")
            null
        }
    }

    /**
     * Properly walks FLAC's metadata-block chain (each block has an explicit length) to reach
     * the VORBIS_COMMENT block (type 4), instead of blindly scanning only the first N bytes of
     * the file. A large embedded cover-art PICTURE block placed before the comment block used to
     * push it past a fixed-size scan window — this walk skips blocks by their declared length
     * regardless of how large they are, so it always reaches VORBIS_COMMENT correctly.
     */
    private fun extractFlacVorbisComment(file: File): String? {
        try {
            RandomAccessFile(file, "r").use { raf ->
                val marker = ByteArray(4)
                raf.readFully(marker)
                if (String(marker, Charsets.ISO_8859_1) != "fLaC") return null

                while (true) {
                    val headerByte = raf.read()
                    if (headerByte == -1) return null
                    val isLast = (headerByte and 0x80) != 0
                    val blockType = headerByte and 0x7F

                    val lenBytes = ByteArray(3)
                    raf.readFully(lenBytes)
                    val blockLen = ((lenBytes[0].toInt() and 0xFF) shl 16) or
                            ((lenBytes[1].toInt() and 0xFF) shl 8) or
                            (lenBytes[2].toInt() and 0xFF)

                    if (blockType == 4) { // VORBIS_COMMENT
                        if (blockLen <= 0 || blockLen > 8 * 1024 * 1024) return null
                        val data = ByteArray(blockLen)
                        raf.readFully(data)
                        return parseVorbisCommentBlock(data)
                    } else {
                        val next = raf.filePointer + blockLen
                        if (next > raf.length()) return null
                        raf.seek(next)
                    }

                    if (isLast) break
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "FLAC metadata walk error: ${e.message}")
        }
        return null
    }

    /** Parses a raw Vorbis comment block: [vendor_length][vendor][count][len,comment]*. */
    private fun parseVorbisCommentBlock(data: ByteArray): String? {
        try {
            var offset = 0
            fun readUInt32LE(): Int {
                if (offset + 4 > data.size) return -1
                val v = (data[offset].toInt() and 0xFF) or
                        ((data[offset + 1].toInt() and 0xFF) shl 8) or
                        ((data[offset + 2].toInt() and 0xFF) shl 16) or
                        ((data[offset + 3].toInt() and 0xFF) shl 24)
                offset += 4
                return v
            }

            val vendorLen = readUInt32LE()
            if (vendorLen < 0 || offset + vendorLen > data.size) return null
            offset += vendorLen

            val commentCount = readUInt32LE()
            if (commentCount < 0) return null

            for (i in 0 until commentCount) {
                val commentLen = readUInt32LE()
                if (commentLen < 0 || offset + commentLen > data.size) break
                val comment = String(data, offset, commentLen, Charsets.UTF_8)
                offset += commentLen

                val eq = comment.indexOf('=')
                if (eq > 0) {
                    val key = comment.substring(0, eq)
                    if (key.equals("LYRICS", ignoreCase = true) ||
                        key.equals("UNSYNCEDLYRICS", ignoreCase = true) ||
                        key.equals("SYNCEDLYRICS", ignoreCase = true)
                    ) {
                        val value = comment.substring(eq + 1)
                        if (value.isNotBlank()) return value.trim()
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Vorbis comment parse error: ${e.message}")
        }
        return null
    }

    /**
     * Buffer-scan fallback for OGG/Opus containers (their Vorbis/Opus comment header packet is
     * always the second page, close to the start of the file, so a generous fully-read buffer
     * is reliable here — unlike FLAC, which needs real block-length walking because of how much
     * larger a preceding cover-art block can be).
     */
    private fun extractOggStyleVorbisComment(file: File): String? {
        try {
            FileInputStream(file).use { input ->
                val buffer = ByteArray(1024 * 1024)
                val bytesRead = readFully(input, buffer)
                if (bytesRead <= 16) return null

                val content = String(buffer, 0, bytesRead, Charsets.ISO_8859_1)
                val keywords = listOf("LYRICS=", "UNSYNCEDLYRICS=", "SYNCEDLYRICS=")
                for (kw in keywords) {
                    val idx = content.indexOf(kw, ignoreCase = true)
                    if (idx != -1) {
                        val start = idx + kw.length
                        var end = content.indexOf("\u0000", start)
                        if (end == -1) end = (start + 4000).coerceAtMost(bytesRead)
                        val raw = String(buffer, start, (end - start).coerceAtLeast(0), Charsets.UTF_8)
                        if (raw.isNotBlank()) return raw.trim()
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun decodeSynchSafeInt(b1: Byte, b2: Byte, b3: Byte, b4: Byte): Int {
        return ((b1.toInt() and 0x7F) shl 21) or
                ((b2.toInt() and 0x7F) shl 14) or
                ((b3.toInt() and 0x7F) shl 7) or
                (b4.toInt() and 0x7F)
    }

    private fun getCharset(encodingByte: Int): Charset {
        return when (encodingByte) {
            1 -> Charsets.UTF_16 // With BOM
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
    }
}
