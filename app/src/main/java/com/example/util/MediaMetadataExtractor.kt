package com.example.util

import android.content.ContentUris
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.player.SubtitleFontManager
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Unified metadata extraction result for a video file.
 */
data class ExtractedMediaMetadata(
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val resolution: String = "",
    val framerate: Double = 0.0,
    val embeddedSubtitleFormats: List<String> = emptyList(),
    val externalSubtitleFormats: List<String> = emptyList(),
    val embeddedFontCount: Int = 0,
    val embeddedFontNames: List<String> = emptyList(),
    val isCompleteScan: Boolean = true,
    val metadataSource: String = "CONTAINER"
) {
    val hasSubtitles: Boolean
        get() = embeddedSubtitleFormats.isNotEmpty() || externalSubtitleFormats.isNotEmpty()

    val effectiveSubtitleFormats: List<String>
        get() = if (embeddedSubtitleFormats.isNotEmpty()) embeddedSubtitleFormats else externalSubtitleFormats
}

/**
 * Centralized, authoritative media metadata extractor for video dimensions, exact FPS,
 * embedded subtitles, external subtitles, and embedded font attachments.
 */
object MediaMetadataExtractor {

    private const val TAG = "MediaMetaExtractor"
    const val METADATA_VERSION = 3

    /**
     * Extracts full media metadata using authoritative container demuxing,
     * MediaExtractor, and MediaMetadataRetriever.
     */
    fun extractMetadata(
        filePath: String,
        context: Context? = null,
        uri: Uri? = null,
        displayName: String = "",
        sizeBytes: Long = 0L,
        dateModified: Long = 0L
    ): ExtractedMediaMetadata {
        val startTime = System.currentTimeMillis()
        val normPath = VideoLibraryCache.normalizeCanonicalPath(filePath)
        val file = if (normPath.isNotBlank()) File(normPath) else null
        val actualSize = if (sizeBytes > 0) sizeBytes else file?.length() ?: 0L
        val actualDate = if (dateModified > 0) dateModified else file?.lastModified() ?: 0L

        var durMs = 0L
        var width = 0
        var height = 0
        var fps = 0.0
        var fpsSource = "NONE"
        val embeddedSubsRaw = mutableListOf<String>()
        val embeddedFonts = mutableListOf<String>()
        var primarySource = "NONE"

        // 1. Authoritative Container Parsing (MKV/WebM EBML & MP4/MOV ISOBMFF)
        if (file != null && file.exists() && file.canRead() && file.length() > 64) {
            val ext = file.extension.lowercase(Locale.ROOT)
            if (ext in setOf("mkv", "webm", "mk3d", "mka")) {
                try {
                    val mkvMeta = parseMkvContainer(file)
                    if (mkvMeta != null) {
                        primarySource = "MKV_CONTAINER"
                        if (mkvMeta.width > 0 && mkvMeta.height > 0) {
                            width = mkvMeta.width
                            height = mkvMeta.height
                        }
                        if (mkvMeta.framerate > 0.0) {
                            fps = mkvMeta.framerate
                            fpsSource = "MKV_DEFAULT_DURATION"
                        }
                        if (mkvMeta.durationMs > 0) {
                            durMs = mkvMeta.durationMs
                        }
                        embeddedSubsRaw.addAll(mkvMeta.subtitleCodecs)
                        embeddedFonts.addAll(mkvMeta.fontNames)
                    }
                } catch (t: Throwable) {
                    Log.d(TAG, "MKV container parse error on $normPath: ${t.message}")
                }
            } else if (ext in setOf("mp4", "m4v", "mov", "qt", "3gp", "3g2")) {
                try {
                    val mp4Meta = parseMp4Container(file)
                    if (mp4Meta != null) {
                        primarySource = "MP4_CONTAINER"
                        if (mp4Meta.width > 0 && mp4Meta.height > 0) {
                            width = mp4Meta.width
                            height = mp4Meta.height
                        }
                        if (mp4Meta.framerate > 0.0) {
                            fps = mp4Meta.framerate
                            fpsSource = "MP4_STTS_TIMESCALE"
                        }
                        if (mp4Meta.durationMs > 0) {
                            durMs = mp4Meta.durationMs
                        }
                        embeddedSubsRaw.addAll(mp4Meta.subtitleCodecs)
                    }
                } catch (t: Throwable) {
                    Log.d(TAG, "MP4 container parse error on $normPath: ${t.message}")
                }
            }
        }

        // 2. Android MediaExtractor pass (for additional containers or missing tracks)
        if (width <= 0 || height <= 0 || fps <= 0.0 || embeddedSubsRaw.isEmpty()) {
            try {
                val extractor = MediaExtractor()
                var loaded = false
                if (file != null && file.exists() && file.canRead()) {
                    extractor.setDataSource(file.absolutePath)
                    loaded = true
                } else if (context != null && uri != null) {
                    extractor.setDataSource(context, uri, null)
                    loaded = true
                }

                if (loaded) {
                    val count = extractor.trackCount
                    for (i in 0 until count) {
                        try {
                            val format = extractor.getTrackFormat(i)
                            val mime = format.getString(MediaFormat.KEY_MIME)?.lowercase(Locale.ROOT) ?: ""

                            if (mime.startsWith("video/")) {
                                if (width <= 0 && format.containsKey(MediaFormat.KEY_WIDTH)) {
                                    width = format.getInteger(MediaFormat.KEY_WIDTH)
                                }
                                if (height <= 0 && format.containsKey(MediaFormat.KEY_HEIGHT)) {
                                    height = format.getInteger(MediaFormat.KEY_HEIGHT)
                                }
                                if (durMs <= 0 && format.containsKey(MediaFormat.KEY_DURATION)) {
                                    durMs = format.getLong(MediaFormat.KEY_DURATION) / 1000L
                                }

                                if (fps <= 0.0) {
                                    val fRate = extractFramerateFromFormat(format)
                                    if (fRate in 1.0..240.0) {
                                        fps = fRate
                                        fpsSource = "MEDIA_EXTRACTOR"
                                    }
                                }
                            } else if (isSubtitleMime(mime)) {
                                val normCodec = normalizeSubtitleCodec(mime, mime)
                                embeddedSubsRaw.add(normCodec)
                            }
                        } catch (_: Throwable) {}
                    }
                    extractor.release()
                }
            } catch (_: Throwable) {}
        }

        // 3. MediaMetadataRetriever pass (dimensions, duration, capture-rate, frame-count)
        if (durMs <= 0 || width <= 0 || height <= 0 || fps <= 0.0) {
            try {
                val retriever = MediaMetadataRetriever()
                var loaded = false
                if (file != null && file.exists() && file.canRead()) {
                    retriever.setDataSource(file.absolutePath)
                    loaded = true
                } else if (context != null && uri != null) {
                    retriever.setDataSource(context, uri)
                    loaded = true
                }

                if (loaded) {
                    if (durMs <= 0) {
                        durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    }
                    if (width <= 0) {
                        width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    }
                    if (height <= 0) {
                        height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    }

                    if (fps <= 0.0) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val capRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toDoubleOrNull()
                            if (capRate != null && capRate in 1.0..240.0) {
                                fps = capRate
                                fpsSource = "RETRIEVER_CAPTURE_FRAMERATE"
                            }
                        }
                        if (fps <= 0.0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && durMs > 0) {
                            val frameCount = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toLongOrNull()
                            if (frameCount != null && frameCount > 0) {
                                val calcFps = (frameCount.toDouble() * 1000.0) / durMs.toDouble()
                                if (calcFps in 1.0..240.0) {
                                    fps = refineCalculatedFps(calcFps)
                                    fpsSource = "RETRIEVER_FRAME_COUNT"
                                }
                            }
                        }
                    }
                    retriever.release()
                }
            } catch (_: Throwable) {}
        }

        // 4. Filename FPS fallback (only as lowest priority fallback)
        if (fps <= 0.0) {
            val nameToMatch = if (displayName.isNotBlank()) displayName else file?.name ?: ""
            if (nameToMatch.isNotBlank()) {
                val regex = Regex("(?i)\\b(\\d{2,3}(?:\\.\\d{1,4})?)\\s*(?:fps|hz)\\b")
                val match = regex.find(nameToMatch)
                if (match != null) {
                    val parsed = match.groupValues[1].toDoubleOrNull()
                    if (parsed != null && parsed in 1.0..240.0) {
                        fps = parsed
                        fpsSource = "FILENAME_FALLBACK"
                    }
                }
            }
        }

        // 5. Derive clean resolution label
        val resolution = formatResolution(width, height, displayName)

        // 6. Embedded subtitles formatting & counting
        val embeddedFormatted = formatSubtitleCounts(embeddedSubsRaw)

        // 7. External companion subtitle files scan
        val externalSubsRaw = if (file != null) scanExternalSubtitleFiles(file) else emptyList()
        val externalFormatted = formatSubtitleCounts(externalSubsRaw)

        // 8. Register embedded font attachments with SubtitleFontManager if any
        if (embeddedFonts.isNotEmpty()) {
            try {
                embeddedFonts.forEach { SubtitleFontManager.registerVideoEmbeddedFont(it) }
            } catch (_: Throwable) {}
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.d(
            TAG,
            "MEDIA_META path=${file?.name ?: normPath} fps=$fps src=$fpsSource res=$resolution embeddedSubs=$embeddedFormatted externalSubs=$externalFormatted fonts=${embeddedFonts.size} took=${elapsed}ms"
        )

        val result = ExtractedMediaMetadata(
            durationMs = durMs,
            width = width,
            height = height,
            resolution = resolution,
            framerate = fps,
            embeddedSubtitleFormats = embeddedFormatted,
            externalSubtitleFormats = externalFormatted,
            embeddedFontCount = embeddedFonts.size,
            embeddedFontNames = embeddedFonts,
            isCompleteScan = fps > 0.0 || resolution.isNotBlank() || embeddedFormatted.isNotEmpty(),
            metadataSource = primarySource
        )

        try {
            val cachedMeta = CachedVideoMeta(
                durationMs = durMs,
                resolution = resolution,
                framerate = fps,
                subtitleFormats = result.effectiveSubtitleFormats,
                hasSubtitles = result.effectiveSubtitleFormats.isNotEmpty(),
                embeddedSubtitleFormats = embeddedFormatted,
                externalSubtitleFormats = externalFormatted,
                embeddedFontCount = embeddedFonts.size
            )
            if (normPath.isNotBlank()) {
                VideoLibraryCache.putCachedVideoMeta(normPath, actualSize, actualDate, cachedMeta)
            }
            if (uri != null) {
                VideoLibraryCache.putCachedVideoMeta(uri.toString(), actualSize, actualDate, cachedMeta)
            }
        } catch (_: Throwable) {}

        return result
    }

    /**
     * Resolves and refines near-standard broadcast framerates while preserving exact decimals like 29.789.
     */
    fun refineCalculatedFps(rawFps: Double): Double {
        if (rawFps <= 0.0) return 0.0
        // Near standard NTSC framerate tolerances
        return when {
            Math.abs(rawFps - 23.976023924) < 0.015 -> 23.976
            Math.abs(rawFps - 29.970029970) < 0.015 -> 29.970
            Math.abs(rawFps - 59.940059940) < 0.020 -> 59.940
            Math.abs(rawFps - Math.round(rawFps)) < 0.0001 -> Math.round(rawFps).toDouble()
            else -> {
                // Round to 3 decimal places to trim double calculation jitter
                Math.round(rawFps * 1000.0) / 1000.0
            }
        }
    }

    private fun extractFramerateFromFormat(format: MediaFormat): Double {
        if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
            val v = try {
                format.getFloat(MediaFormat.KEY_FRAME_RATE).toDouble()
            } catch (_: Throwable) {
                try {
                    format.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble()
                } catch (_: Throwable) {
                    0.0
                }
            }
            if (v in 1.0..240.0) return refineCalculatedFps(v)
        }
        if (format.containsKey("frame-rate")) {
            val v = try {
                format.getFloat("frame-rate").toDouble()
            } catch (_: Throwable) {
                try {
                    format.getInteger("frame-rate").toDouble()
                } catch (_: Throwable) {
                    0.0
                }
            }
            if (v in 1.0..240.0) return refineCalculatedFps(v)
        }
        if (format.containsKey("capture-rate")) {
            val v = try { format.getFloat("capture-rate").toDouble() } catch (_: Throwable) { 0.0 }
            if (v in 1.0..240.0) return refineCalculatedFps(v)
        }
        return 0.0
    }

    private fun isSubtitleMime(mime: String): Boolean {
        val m = mime.lowercase(Locale.ROOT)
        return m.startsWith("subtitle/") ||
            m.startsWith("text/") ||
            m.contains("subrip") ||
            m.contains("vtt") ||
            m.contains("ass") ||
            m.contains("ssa") ||
            m.contains("pgs") ||
            m.contains("ttml") ||
            m.contains("cea-608") ||
            m.contains("cea-708") ||
            m.contains("dvb") ||
            m.contains("tx3g") ||
            m.contains("sami")
    }

    /**
     * Normalizes any subtitle codec string into canonical standard badge name (SRT, ASS, SSA, VTT, PGS, etc.)
     */
    fun normalizeSubtitleCodec(rawCodec: String, mime: String = ""): String {
        val text = (rawCodec + " " + mime).lowercase(Locale.ROOT).trim()
        return when {
            text.contains("subrip") || text.contains("x-subrip") || text.contains("s_text/utf8") || text.contains("utf-8") || text.contains("utf8") || text.contains("srt") -> "SRT"
            text.contains("x-ass") || text.contains("s_text/ass") || text.contains("ass") || text.contains("aegisub") -> "ASS"
            text.contains("x-ssa") || text.contains("s_text/ssa") || text.contains("ssa") -> "SSA"
            text.contains("webvtt") || text.contains("s_text/webvtt") || text.contains("vtt") || text.contains("wvtt") -> "VTT"
            text.contains("hdmv_pgs") || text.contains("s_hdmv/pgs") || text.contains("pgs") || text.contains("sup") || text.contains("hdmv") -> "PGS"
            text.contains("vobsub") || text.contains("s_vobsub") || text.contains("dvd_sub") || text.contains("subp") || text.contains("idx") -> "SUB"
            text.contains("dvb_subtitle") || text.contains("s_dvbsub") || text.contains("dvbsub") || text.contains("dvb") -> "DVB"
            text.contains("ttml") || text.contains("stpp") || text.contains("xml") -> "TTML"
            text.contains("tx3g") || text.contains("3gpp-tt") || text.contains("mp4s") -> "TX3G"
            text.contains("cea-608") || text.contains("cea-708") || text.contains("c608") || text.contains("c708") || text.contains("closedcaption") || text.contains("clcp") -> "CC"
            text.contains("sami") || text.contains("smi") || text.contains("x-sami") -> "SAMI"
            text.contains("lrc") -> "LRC"
            text.contains("txt") -> "TXT"
            rawCodec.isNotBlank() -> rawCodec.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]"), "").take(6).ifBlank { "SUB" }
            else -> "SUB"
        }
    }

    /**
     * Counts and formats subtitle items, preserving container track order: e.g. ["7ASS", "2SRT"] or ["1ASS"]
     */
    fun formatSubtitleCounts(rawCodecs: List<String>): List<String> {
        if (rawCodecs.isEmpty()) return emptyList()
        val counts = LinkedHashMap<String, Int>()
        for (raw in rawCodecs) {
            val norm = normalizeSubtitleCodec(raw)
            if (norm.isNotBlank()) {
                counts[norm] = (counts[norm] ?: 0) + 1
            }
        }
        return counts.map { (format, count) ->
            "$count$format"
        }
    }

    fun formatResolution(width: Int, height: Int, displayName: String = ""): String {
        if (width > 0 && height > 0) {
            val minDim = minOf(width, height)
            val maxDim = maxOf(width, height)
            return when {
                maxDim >= 3800 || minDim >= 2100 -> "4K"
                minDim >= 1000 || maxDim >= 1900 -> "1080p"
                minDim >= 700 || maxDim >= 1200 -> "720p"
                minDim >= 450 || maxDim >= 800 -> "480p"
                else -> "${width}x${height}"
            }
        }
        if (displayName.isNotBlank()) {
            return when {
                displayName.contains("4k", ignoreCase = true) || displayName.contains("2160p", ignoreCase = true) -> "4K"
                displayName.contains("1080p", ignoreCase = true) || displayName.contains("1080", ignoreCase = true) -> "1080p"
                displayName.contains("720p", ignoreCase = true) -> "720p"
                displayName.contains("480p", ignoreCase = true) -> "480p"
                else -> ""
            }
        }
        return ""
    }

    /**
     * Scans for external companion subtitle files in the video's directory and companion Subs folders.
     */
    fun scanExternalSubtitleFiles(videoFile: File): List<String> {
        val results = mutableListOf<String>()
        val parent = videoFile.parentFile ?: return results
        if (!parent.exists() || !parent.isDirectory) return results

        val baseName = videoFile.nameWithoutExtension.lowercase(Locale.ROOT)
        val searchDirs = mutableListOf<File>()
        searchDirs.add(parent)

        val subsFolders = listOf("Subs", "Subtitles", "subs", "subtitles")
        for (subDirName in subsFolders) {
            val sub = File(parent, subDirName)
            if (sub.exists() && sub.isDirectory && !searchDirs.contains(sub)) {
                searchDirs.add(sub)
            }
        }

        val supportedSubExts = setOf(
            "srt", "ass", "ssa", "vtt", "sub", "pgs", "idx", "smi", "sami", "sup", "txt", "ttml", "lrc"
        )

        for (dir in searchDirs) {
            val candidateFiles = dir.listFiles { f ->
                f.isFile && f.extension.lowercase(Locale.ROOT) in supportedSubExts
            } ?: continue

            for (sf in candidateFiles) {
                val sfName = sf.name.lowercase(Locale.ROOT)
                val sfExt = sf.extension.lowercase(Locale.ROOT)
                val matchesThisVideo = sfName.startsWith(baseName) ||
                    (baseName.length >= 4 && sfName.contains(baseName)) ||
                    (candidateFiles.size <= 3 && (parent.listFiles { f -> f.isFile && f.extension.lowercase() in setOf("mp4", "mkv", "webm", "avi", "mov") }?.size ?: 0) == 1)

                if (matchesThisVideo) {
                    val normalized = normalizeSubtitleCodec(sfExt)
                    results.add(normalized)
                }
            }
        }
        return results
    }

    // ---------------------------------------------------------------------------------------------
    // MATROSKA / WEBM (EBML) DEMUXER / CONTAINER PARSER
    // ---------------------------------------------------------------------------------------------

    private data class ParsedMkvData(
        val width: Int,
        val height: Int,
        val framerate: Double,
        val durationMs: Long,
        val subtitleCodecs: List<String>,
        val fontNames: List<String>
    )

    private fun parseMkvContainer(file: File): ParsedMkvData? {
        RandomAccessFile(file, "r").use { raf ->
            val magic = ByteArray(4)
            raf.readFully(magic)
            // EBML Header magic: 0x1A 0x45 0xDF 0xA3
            if (magic[0] != 0x1A.toByte() || magic[1] != 0x45.toByte() || magic[2] != 0xDF.toByte() || magic[3] != 0xA3.toByte()) {
                return null
            }

            var videoWidth = 0
            var videoHeight = 0
            var framerate = 0.0
            var durationMs = 0L
            var timecodeScale = 1_000_000L // default 1ms in ns
            val subCodecs = mutableListOf<String>()
            val fontNames = mutableListOf<String>()

            val fileLen = raf.length()
            var filePos = 0L

            // Quick scan EBML elements
            // We search for Tracks element (0x16 0x54 0xAE 0x6B) and Info element (0x15 0x49 0xA9 0x66)
            // and Attachments (0x19 0x41 0xA4 0x69)
            val bufferSize = 128 * 1024
            val buffer = ByteArray(bufferSize)

            // Read first up to 8MB or until Cluster starts to quickly extract tracks and info
            val maxScanBytes = minOf(fileLen, 16L * 1024L * 1024L)

            while (filePos < maxScanBytes) {
                raf.seek(filePos)
                val bytesRead = raf.read(buffer)
                if (bytesRead <= 8) break

                var i = 0
                while (i < bytesRead - 4) {
                    // Check for Tracks master element: 0x16 0x54 0xAE 0x6B
                    if (buffer[i] == 0x16.toByte() && buffer[i + 1] == 0x54.toByte() && buffer[i + 2] == 0xAE.toByte() && buffer[i + 3] == 0x6B.toByte()) {
                        val elementAbsPos = filePos + i
                        try {
                            raf.seek(elementAbsPos + 4)
                            val tracksSize = readEbmlVint(raf)
                            if (tracksSize in 1..(10 * 1024 * 1024)) {
                                parseMkvTracks(raf, raf.filePointer, tracksSize) { w, h, fpsVal, subs ->
                                    if (w > 0) videoWidth = w
                                    if (h > 0) videoHeight = h
                                    if (fpsVal > 0.0) framerate = fpsVal
                                    subCodecs.addAll(subs)
                                }
                            }
                        } catch (_: Throwable) {}
                        i += 4
                    }
                    // Check for AttachedFile: 0x61 0xA7
                    else if (buffer[i] == 0x61.toByte() && buffer[i + 1] == 0xA7.toByte()) {
                        val elementAbsPos = filePos + i
                        try {
                            raf.seek(elementAbsPos + 2)
                            val attSize = readEbmlVint(raf)
                            if (attSize in 1..(50 * 1024 * 1024)) {
                                val fontName = parseMkvAttachedFileName(raf, raf.filePointer, attSize)
                                if (fontName != null) fontNames.add(fontName)
                            }
                        } catch (_: Throwable) {}
                        i += 2
                    }
                    // Check for Segment Info: 0x15 0x49 0xA9 0x66
                    else if (buffer[i] == 0x15.toByte() && buffer[i + 1] == 0x49.toByte() && buffer[i + 2] == 0xA9.toByte() && buffer[i + 3] == 0x66.toByte()) {
                        val elementAbsPos = filePos + i
                        try {
                            raf.seek(elementAbsPos + 4)
                            val infoSize = readEbmlVint(raf)
                            if (infoSize in 1..65536) {
                                val dur = parseMkvSegmentInfo(raf, raf.filePointer, infoSize)
                                if (dur > 0) durationMs = dur
                            }
                        } catch (_: Throwable) {}
                        i += 4
                    } else {
                        i++
                    }
                }
                filePos += (bytesRead - 8)
                // If we got video dimensions, fps, and at least some tracks or scanned enough, continue
                if (videoWidth > 0 && framerate > 0.0 && filePos > 2 * 1024 * 1024) {
                    break
                }
            }

            return ParsedMkvData(
                width = videoWidth,
                height = videoHeight,
                framerate = framerate,
                durationMs = durationMs,
                subtitleCodecs = subCodecs,
                fontNames = fontNames
            )
        }
    }

    private fun parseMkvTracks(
        raf: RandomAccessFile,
        startPos: Long,
        tracksSize: Long,
        onResult: (width: Int, height: Int, fps: Double, subCodecs: List<String>) -> Unit
    ) {
        val endPos = startPos + tracksSize
        var width = 0
        var height = 0
        var fps = 0.0
        val subs = mutableListOf<String>()

        while (raf.filePointer < endPos) {
            val elemId = readEbmlId(raf) ?: break
            val elemSize = readEbmlVint(raf)
            if (elemSize < 0 || raf.filePointer + elemSize > endPos + 1024) break

            if (elemId == 0xAE) { // TrackEntry
                val trackEnd = raf.filePointer + elemSize
                var trackType = -1
                var codecId = ""
                var defaultDurationNs = 0L
                var trackFps = 0.0
                var trackWidth = 0
                var trackHeight = 0

                while (raf.filePointer < trackEnd) {
                    val childId = readEbmlId(raf) ?: break
                    val childSize = readEbmlVint(raf)
                    if (childSize < 0 || raf.filePointer + childSize > trackEnd + 16) break

                    when (childId) {
                        0x83 -> { // TrackType: 1 = Video, 2 = Audio, 17 = Subtitle (0x11)
                            trackType = readEbmlUint(raf, childSize.toInt()).toInt()
                        }
                        0x86 -> { // CodecID (ASCII/UTF-8 string)
                            val bytes = ByteArray(childSize.toInt().coerceAtMost(256))
                            raf.readFully(bytes)
                            codecId = String(bytes, StandardCharsets.UTF_8).trim()
                            if (childSize > bytes.size) raf.skipBytes((childSize - bytes.size).toInt())
                        }
                        0x23E383 -> { // DefaultDuration (nanoseconds per frame)
                            defaultDurationNs = readEbmlUint(raf, childSize.toInt())
                        }
                        0xE0 -> { // Video Settings Master
                            val videoEnd = raf.filePointer + childSize
                            while (raf.filePointer < videoEnd) {
                                val vId = readEbmlId(raf) ?: break
                                val vSize = readEbmlVint(raf)
                                if (vSize < 0 || raf.filePointer + vSize > videoEnd + 16) break

                                when (vId) {
                                    0xB0 -> trackWidth = readEbmlUint(raf, vSize.toInt()).toInt() // PixelWidth
                                    0xBA -> trackHeight = readEbmlUint(raf, vSize.toInt()).toInt() // PixelHeight
                                    0x2383E3 -> { // FrameRate (float/double)
                                        trackFps = if (vSize == 4L) {
                                            raf.readFloat().toDouble()
                                        } else if (vSize == 8L) {
                                            raf.readDouble()
                                        } else {
                                            raf.skipBytes(vSize.toInt()); 0.0
                                        }
                                    }
                                    else -> raf.skipBytes(vSize.toInt())
                                }
                            }
                        }
                        else -> {
                            raf.skipBytes(childSize.toInt())
                        }
                    }
                }

                if (trackType == 1) { // Video track
                    if (trackWidth > 0) width = trackWidth
                    if (trackHeight > 0) height = trackHeight
                    if (trackFps > 0.0) {
                        fps = refineCalculatedFps(trackFps)
                    } else if (defaultDurationNs > 0) {
                        val rawFps = 1_000_000_000.0 / defaultDurationNs.toDouble()
                        if (rawFps in 1.0..240.0) {
                            fps = refineCalculatedFps(rawFps)
                        }
                    }
                } else if (trackType == 17 || trackType == 0x11 || codecId.startsWith("S_")) { // Subtitle track
                    if (codecId.isNotBlank()) {
                        subs.add(normalizeSubtitleCodec(codecId))
                    }
                }
            } else {
                raf.skipBytes(elemSize.toInt())
            }
        }
        onResult(width, height, fps, subs)
    }

    private fun parseMkvAttachedFileName(raf: RandomAccessFile, startPos: Long, size: Long): String? {
        val endPos = startPos + size
        var fileName: String? = null
        var fileMimeType: String? = null

        while (raf.filePointer < endPos) {
            val childId = readEbmlId(raf) ?: break
            val childSize = readEbmlVint(raf)
            if (childSize < 0 || raf.filePointer + childSize > endPos + 16) break

            when (childId) {
                0x466E -> { // FileName
                    val nameBytes = ByteArray(childSize.toInt().coerceAtMost(512))
                    raf.readFully(nameBytes)
                    fileName = String(nameBytes, StandardCharsets.UTF_8).trim()
                    if (childSize > nameBytes.size) raf.skipBytes((childSize - nameBytes.size).toInt())
                }
                0x4660 -> { // FileMimeType
                    val mimeBytes = ByteArray(childSize.toInt().coerceAtMost(256))
                    raf.readFully(mimeBytes)
                    fileMimeType = String(mimeBytes, StandardCharsets.US_ASCII).trim()
                    if (childSize > mimeBytes.size) raf.skipBytes((childSize - mimeBytes.size).toInt())
                }
                else -> raf.skipBytes(childSize.toInt())
            }
        }

        val validExts = listOf(".ttf", ".otf", ".ttc", ".woff", ".woff2")
        val isFont = (fileName != null && validExts.any { fileName.endsWith(it, ignoreCase = true) }) ||
            (fileMimeType != null && (fileMimeType.contains("font", ignoreCase = true) || fileMimeType.contains("truetype", ignoreCase = true) || fileMimeType.contains("opentype", ignoreCase = true)))

        return if (isFont && !fileName.isNullOrBlank()) fileName else null
    }

    private fun parseMkvSegmentInfo(raf: RandomAccessFile, startPos: Long, size: Long): Long {
        val endPos = startPos + size
        var timecodeScale = 1_000_000L
        var durationFloat = 0.0

        while (raf.filePointer < endPos) {
            val childId = readEbmlId(raf) ?: break
            val childSize = readEbmlVint(raf)
            if (childSize < 0 || raf.filePointer + childSize > endPos + 16) break

            when (childId) {
                0x2AD7B1 -> { // TimestampScale / TimecodeScale (in ns, default 1,000,000)
                    timecodeScale = readEbmlUint(raf, childSize.toInt())
                }
                0x4489 -> { // Duration (in TimestampScale units)
                    durationFloat = if (childSize == 4L) {
                        raf.readFloat().toDouble()
                    } else if (childSize == 8L) {
                        raf.readDouble()
                    } else {
                        raf.skipBytes(childSize.toInt()); 0.0
                    }
                }
                else -> raf.skipBytes(childSize.toInt())
            }
        }

        return if (durationFloat > 0) {
            ((durationFloat * timecodeScale) / 1_000_000.0).toLong()
        } else 0L
    }

    private fun readEbmlId(raf: RandomAccessFile): Int? {
        val b0 = raf.read()
        if (b0 == -1) return null
        var id = b0
        val len: Int
        var mask = 0x80
        var count = 1
        while (count <= 4) {
            if ((b0 and mask) != 0) {
                break
            }
            mask = mask shr 1
            count++
        }
        len = if (count <= 4) count else 1
        for (k in 1 until len) {
            val b = raf.read()
            if (b == -1) return null
            id = (id shl 8) or (b and 0xFF)
        }
        return id
    }

    private fun readEbmlVint(raf: RandomAccessFile): Long {
        val b0 = raf.read()
        if (b0 == -1) return -1L
        var mask = 0x80
        var len = 1
        while (len <= 8) {
            if ((b0 and mask) != 0) break
            mask = mask shr 1
            len++
        }
        if (len > 8) return -1L
        var value = (b0 and (mask - 1)).toLong()
        for (k in 1 until len) {
            val b = raf.read()
            if (b == -1) return -1L
            value = (value shl 8) or (b and 0xFF).toLong()
        }
        return value
    }

    private fun readEbmlUint(raf: RandomAccessFile, size: Int): Long {
        var v = 0L
        for (i in 0 until size) {
            val b = raf.read()
            if (b == -1) break
            v = (v shl 8) or (b and 0xFF).toLong()
        }
        return v
    }

    // ---------------------------------------------------------------------------------------------
    // MP4 / MOV / M4V (ISOBMFF) DEMUXER / CONTAINER PARSER
    // ---------------------------------------------------------------------------------------------

    private data class ParsedMp4Data(
        val width: Int,
        val height: Int,
        val framerate: Double,
        val durationMs: Long,
        val subtitleCodecs: List<String>
    )

    private fun parseMp4Container(file: File): ParsedMp4Data? {
        RandomAccessFile(file, "r").use { raf ->
            val fileLen = raf.length()
            var width = 0
            var height = 0
            var fps = 0.0
            var durationMs = 0L
            val subCodecs = mutableListOf<String>()

            // Traverse top-level boxes (ftyp, moov, mdat, etc.)
            while (raf.filePointer < fileLen - 8) {
                val boxStart = raf.filePointer
                val boxSize = raf.readInt().toLong() and 0xFFFFFFFFL
                val boxTypeBytes = ByteArray(4)
                raf.readFully(boxTypeBytes)
                val boxType = String(boxTypeBytes, StandardCharsets.US_ASCII)

                val effectiveSize = if (boxSize == 1L) {
                    raf.readLong()
                } else if (boxSize == 0L) {
                    fileLen - boxStart
                } else {
                    boxSize
                }

                if (boxType == "moov") {
                    val moovEnd = boxStart + effectiveSize
                    parseMp4Moov(raf, raf.filePointer, moovEnd) { w, h, fpsVal, dur, subs ->
                        if (w > 0) width = w
                        if (h > 0) height = h
                        if (fpsVal > 0.0) fps = fpsVal
                        if (dur > 0) durationMs = dur
                        subCodecs.addAll(subs)
                    }
                    break
                } else {
                    // Skip box
                    val nextPos = boxStart + effectiveSize
                    if (nextPos <= boxStart || nextPos > fileLen) break
                    raf.seek(nextPos)
                }
            }

            return ParsedMp4Data(
                width = width,
                height = height,
                framerate = fps,
                durationMs = durationMs,
                subtitleCodecs = subCodecs
            )
        }
    }

    private fun parseMp4Moov(
        raf: RandomAccessFile,
        startPos: Long,
        moovEnd: Long,
        onResult: (width: Int, height: Int, fps: Double, dur: Long, subs: List<String>) -> Unit
    ) {
        var width = 0
        var height = 0
        var fps = 0.0
        var durMs = 0L
        val subs = mutableListOf<String>()

        while (raf.filePointer < moovEnd - 8) {
            val boxStart = raf.filePointer
            val boxSize = raf.readInt().toLong() and 0xFFFFFFFFL
            val typeBytes = ByteArray(4)
            raf.readFully(typeBytes)
            val boxType = String(typeBytes, StandardCharsets.US_ASCII)

            val effectiveSize = if (boxSize == 1L) raf.readLong() else boxSize
            if (effectiveSize < 8 || boxStart + effectiveSize > moovEnd + 64) break

            if (boxType == "mvhd") {
                val version = raf.read()
                raf.skipBytes(3) // flags
                if (version == 1) {
                    raf.skipBytes(16) // creation & modification
                    val timeScale = raf.readInt().toLong() and 0xFFFFFFFFL
                    val duration = raf.readLong()
                    if (timeScale > 0) durMs = (duration * 1000L) / timeScale
                } else {
                    raf.skipBytes(8) // creation & modification
                    val timeScale = raf.readInt().toLong() and 0xFFFFFFFFL
                    val duration = raf.readInt().toLong() and 0xFFFFFFFFL
                    if (timeScale > 0) durMs = (duration * 1000L) / timeScale
                }
                raf.seek(boxStart + effectiveSize)
            } else if (boxType == "trak") {
                val trakEnd = boxStart + effectiveSize
                parseMp4Trak(raf, raf.filePointer, trakEnd) { tw, th, tfps, tsubs ->
                    if (tw > 0 && width <= 0) width = tw
                    if (th > 0 && height <= 0) height = th
                    if (tfps > 0.0 && fps <= 0.0) fps = tfps
                    subs.addAll(tsubs)
                }
                raf.seek(boxStart + effectiveSize)
            } else {
                raf.seek(boxStart + effectiveSize)
            }
        }
        onResult(width, height, fps, durMs, subs)
    }

    private fun parseMp4Trak(
        raf: RandomAccessFile,
        startPos: Long,
        trakEnd: Long,
        onTrak: (width: Int, height: Int, fps: Double, subs: List<String>) -> Unit
    ) {
        var trackWidth = 0
        var trackHeight = 0
        var trackFps = 0.0
        var isVideo = false
        var isSub = false
        val subFormats = mutableListOf<String>()

        while (raf.filePointer < trakEnd - 8) {
            val boxStart = raf.filePointer
            val boxSize = raf.readInt().toLong() and 0xFFFFFFFFL
            val typeBytes = ByteArray(4)
            raf.readFully(typeBytes)
            val boxType = String(typeBytes, StandardCharsets.US_ASCII)

            val effectiveSize = if (boxSize == 1L) raf.readLong() else boxSize
            if (effectiveSize < 8 || boxStart + effectiveSize > trakEnd + 64) break

            if (boxType == "tkhd") {
                val version = raf.read()
                raf.skipBytes(3) // flags
                if (version == 1) {
                    raf.skipBytes(32)
                } else {
                    raf.skipBytes(20)
                }
                raf.skipBytes(48) // matrix
                val w = (raf.readInt().toLong() and 0xFFFFFFFFL) shr 16
                val h = (raf.readInt().toLong() and 0xFFFFFFFFL) shr 16
                if (w > 0) trackWidth = w.toInt()
                if (h > 0) trackHeight = h.toInt()
                raf.seek(boxStart + effectiveSize)
            } else if (boxType == "mdia") {
                val mdiaEnd = boxStart + effectiveSize
                var timescale = 0L

                while (raf.filePointer < mdiaEnd - 8) {
                    val mStart = raf.filePointer
                    val mSize = raf.readInt().toLong() and 0xFFFFFFFFL
                    val mTypeBytes = ByteArray(4)
                    raf.readFully(mTypeBytes)
                    val mType = String(mTypeBytes, StandardCharsets.US_ASCII)
                    val mEffSize = if (mSize == 1L) raf.readLong() else mSize
                    if (mEffSize < 8 || mStart + mEffSize > mdiaEnd + 64) break

                    if (mType == "mdhd") {
                        val ver = raf.read()
                        raf.skipBytes(3)
                        if (ver == 1) {
                            raf.skipBytes(16)
                            timescale = raf.readInt().toLong() and 0xFFFFFFFFL
                        } else {
                            raf.skipBytes(8)
                            timescale = raf.readInt().toLong() and 0xFFFFFFFFL
                        }
                        raf.seek(mStart + mEffSize)
                    } else if (mType == "hdlr") {
                        raf.skipBytes(4) // ver + flags
                        raf.skipBytes(4) // pre-defined
                        val hTypeBytes = ByteArray(4)
                        raf.readFully(hTypeBytes)
                        val hType = String(hTypeBytes, StandardCharsets.US_ASCII).lowercase(Locale.ROOT)
                        if (hType == "vide") isVideo = true
                        if (hType == "sbtl" || hType == "subt" || hType == "text" || hType == "clcp") isSub = true
                        raf.seek(mStart + mEffSize)
                    } else if (mType == "minf") {
                        val minfEnd = mStart + mEffSize
                        while (raf.filePointer < minfEnd - 8) {
                            val stStart = raf.filePointer
                            val stSize = raf.readInt().toLong() and 0xFFFFFFFFL
                            val stTypeBytes = ByteArray(4)
                            raf.readFully(stTypeBytes)
                            val stType = String(stTypeBytes, StandardCharsets.US_ASCII)
                            val stEffSize = if (stSize == 1L) raf.readLong() else stSize
                            if (stEffSize < 8 || stStart + stEffSize > minfEnd + 64) break

                            if (stType == "stbl") {
                                val stblEnd = stStart + stEffSize
                                while (raf.filePointer < stblEnd - 8) {
                                    val tbStart = raf.filePointer
                                    val tbSize = raf.readInt().toLong() and 0xFFFFFFFFL
                                    val tbTypeBytes = ByteArray(4)
                                    raf.readFully(tbTypeBytes)
                                    val tbType = String(tbTypeBytes, StandardCharsets.US_ASCII)
                                    val tbEffSize = if (tbSize == 1L) raf.readLong() else tbSize
                                    if (tbEffSize < 8 || tbStart + tbEffSize > stblEnd + 64) break

                                    if (tbType == "stsd") { // Sample descriptions -> codec FourCC
                                        raf.skipBytes(4) // ver + flags
                                        val entryCount = raf.readInt()
                                        if (entryCount in 1..32) {
                                            for (k in 0 until entryCount) {
                                                if (raf.filePointer >= tbStart + tbEffSize - 8) break
                                                val entrySize = raf.readInt().toLong() and 0xFFFFFFFFL
                                                val formatBytes = ByteArray(4)
                                                raf.readFully(formatBytes)
                                                val formatCode = String(formatBytes, StandardCharsets.US_ASCII).lowercase(Locale.ROOT)
                                                if (isSub || formatCode in setOf("tx3g", "wvtt", "c608", "c708", "subp", "mp4s")) {
                                                    subFormats.add(normalizeSubtitleCodec(formatCode))
                                                }
                                                if (entrySize > 8) raf.skipBytes((entrySize - 8).toInt())
                                            }
                                        }
                                        raf.seek(tbStart + tbEffSize)
                                    } else if (tbType == "stts" && isVideo && timescale > 0) { // Time to sample -> FPS
                                        raf.skipBytes(4) // ver + flags
                                        val entryCount = raf.readInt()
                                        if (entryCount in 1..50) {
                                            val sampleCount = raf.readInt().toLong() and 0xFFFFFFFFL
                                            val sampleDelta = raf.readInt().toLong() and 0xFFFFFFFFL
                                            if (sampleDelta > 0) {
                                                val calculatedFps = timescale.toDouble() / sampleDelta.toDouble()
                                                if (calculatedFps in 1.0..240.0) {
                                                    trackFps = refineCalculatedFps(calculatedFps)
                                                }
                                            }
                                        }
                                        raf.seek(tbStart + tbEffSize)
                                    } else {
                                        raf.seek(tbStart + tbEffSize)
                                    }
                                }
                                raf.seek(stStart + stEffSize)
                            } else {
                                raf.seek(stStart + stEffSize)
                            }
                        }
                        raf.seek(mStart + mEffSize)
                    } else {
                        raf.seek(mStart + mEffSize)
                    }
                }
                raf.seek(boxStart + effectiveSize)
            } else {
                raf.seek(boxStart + effectiveSize)
            }
        }
        onTrak(trackWidth, trackHeight, trackFps, subFormats)
    }
}
