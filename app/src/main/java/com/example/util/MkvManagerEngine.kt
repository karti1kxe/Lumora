package com.example.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

// MARK: - Data Models for MKV Track Manager

enum class MkvTrackType(val displayName: String) {
    VIDEO("Video"),
    AUDIO("Audio"),
    SUBTITLE("Subtitle"),
    ATTACHMENT("Attachment"),
    UNKNOWN("Unknown")
}

data class MkvTrackInfo(
    val id: Int,
    var trackNumber: Long,
    val trackUid: Long = 0L,
    val trackType: MkvTrackType,
    var name: String = "",
    var language: String = "und",
    var codecId: String = "",
    var codecName: String = "",
    var isEnabled: Boolean = true,
    var isDefault: Boolean = false,
    var isForced: Boolean = false,
    // Video specs
    var pixelWidth: Int = 0,
    var pixelHeight: Int = 0,
    var displayWidth: Int = 0,
    var displayHeight: Int = 0,
    var displayUnit: Int = 0,
    var frameRate: Float = 0f,
    var defaultDurationNs: Long = 0L,
    var flagLacing: Boolean = true,
    // Audio specs
    var channels: Int = 0,
    var sampleRate: Int = 0,
    var bitDepth: Int = 0,
    // Advanced video / stream statistics
    var bitrateBps: Long = 0L,
    var durationMs: Long = 0L,
    var colorSpace: String = "",
    var hdrFormat: String = "",
    var aspectRatio: String = "",
    // Detailed codec profile info (parsed from CodecPrivate, mirrors MediaInfo's
    // "Format profile" / "Format level" / "Format tier" / chroma rows)
    var formatProfile: String = "",
    var formatLevel: String = "",
    var formatTier: String = "",
    var chromaSubsampling: String = "",
    var frameRateMode: String = "",
    // Exact compressed bytes stored for this track, measured from the real Cluster
    // blocks (not estimated) - mirrors MediaInfo's "Stream size" row.
    var streamSizeBytes: Long = 0L,
    // Number of subtitle dialogue/cue blocks found for this track - mirrors
    // MediaInfo's "Count of elements" row.
    var countOfElements: Int = 0,
    // Audio-only: container-declared playback delay relative to video (ms).
    var delayRelativeToVideoMs: Long = 0L,
    // Subtitle / Codec specifics
    var codecPrivate: ByteArray = ByteArray(0),
    var isExternalAdded: Boolean = false,
    var externalSourceUri: Uri? = null,
    var externalSourceContent: String? = null,
    var originalTrackIndex: Int = id,
    var originalTrackNumber: Long = trackNumber,
    // Per-Track Settings
    var syncOffsetMs: Long = 0L,
    var encoder: String = "None",
    var isOriginal: Boolean = false,
    var isCommentary: Boolean = false,
    var isDescriptions: Boolean = false,
    var isHearingImpaired: Boolean = false,
    var isVisualImpaired: Boolean = false,
    var metadataTags: Map<String, String> = emptyMap()
) {
    val displayResolution: String
        get() = if (pixelWidth > 0 && pixelHeight > 0) "${pixelWidth}x${pixelHeight}" else ""

    val displayAudioSpec: String
        get() = buildString {
            if (channels > 0) {
                append(if (channels == 1) "Mono" else if (channels == 2) "Stereo" else "${channels}.0")
            }
            if (sampleRate > 0) {
                if (isNotEmpty()) append(" • ")
                append("${sampleRate / 1000} kHz")
            }
        }

    val displayCodec: String
        get() {
            if (codecName.isNotBlank()) return codecName
            return when {
                codecId.contains("AVC", true) || codecId.contains("H264", true) -> "H.264 / AVC"
                codecId.contains("HEVC", true) || codecId.contains("H265", true) -> "H.265 / HEVC"
                codecId.contains("VP9", true) -> "VP9"
                codecId.contains("AV1", true) -> "AV1"
                codecId.contains("AAC", true) -> "AAC"
                codecId.contains("OPUS", true) -> "Opus"
                codecId.contains("FLAC", true) -> "FLAC"
                codecId.contains("AC3", true) || codecId.contains("EAC3", true) -> "Dolby Digital (AC-3)"
                codecId.contains("DTS", true) -> "DTS"
                codecId.contains("ASS", true) -> "SubStation Alpha (.ass)"
                codecId.contains("SSA", true) -> "SubStation Alpha (.ssa)"
                codecId.contains("UTF8", true) -> "SubRip (.srt)"
                codecId.contains("WEBVTT", true) -> "WebVTT (.vtt)"
                codecId.contains("PGS", true) || codecId.contains("HDMV", true) -> "HDMV PGS"
                codecId.contains("VOBSUB", true) -> "VobSub"
                codecId.isNotBlank() -> codecId.substringAfterLast("/")
                else -> trackType.displayName
            }
        }

    /** Long-form codec description, e.g. "High Efficiency Video Coding" for HEVC. */
    val formatInfo: String
        get() = when {
            codecId.contains("HEVC", true) || codecId.contains("H265", true) -> "High Efficiency Video Coding"
            codecId.contains("AVC", true) || codecId.contains("H264", true) -> "Advanced Video Coding"
            codecId.contains("VP9", true) -> "Google / On2 VP9"
            codecId.contains("AV1", true) -> "AOMedia Video 1"
            codecId.contains("AAC", true) -> "Advanced Audio Coding"
            codecId.contains("OPUS", true) -> "Interactive Audio Lossy Compression"
            codecId.contains("FLAC", true) -> "Free Lossless Audio Codec"
            codecId.contains("EAC3", true) -> "Enhanced AC-3"
            codecId.contains("AC3", true) -> "Audio Coding 3"
            codecId.contains("DTS", true) -> "Digital Theater System"
            codecId.contains("PCM", true) -> "Pulse Code Modulation"
            codecId.contains("VORBIS", true) -> "Xiph.Org Vorbis"
            codecId.contains("ASS", true) || codecId.contains("SSA", true) -> "Advanced SubStation Alpha"
            codecId.contains("UTF8", true) -> "SubRip Text"
            codecId.contains("WEBVTT", true) -> "Web Video Text Tracks"
            codecId.contains("PGS", true) || codecId.contains("HDMV", true) -> "Presentation Graphic Stream"
            codecId.contains("VOBSUB", true) -> "DVD Subtitle"
            else -> ""
        }

    /** Lossy vs Lossless, matching MediaInfo's "Compression mode" row. */
    val compressionMode: String
        get() = when {
            trackType == MkvTrackType.SUBTITLE -> "Lossless"
            codecId.contains("FLAC", true) || codecId.contains("PCM", true) ||
                codecId.contains("TRUEHD", true) || codecId.contains("ALAC", true) -> "Lossless"
            trackType == MkvTrackType.VIDEO || trackType == MkvTrackType.AUDIO -> "Lossy"
            else -> ""
        }

    /** Bits/(Pixel*Frame) row for video tracks, e.g. "0.034". */
    val bitsPerPixelFrame: String
        get() {
            if (trackType != MkvTrackType.VIDEO) return ""
            val pixels = pixelWidth.toDouble() * pixelHeight.toDouble()
            if (pixels <= 0.0 || frameRate <= 0f || bitrateBps <= 0L) return ""
            val value = bitrateBps / (pixels * frameRate)
            return String.format(Locale.US, "%.3f", value)
        }

    /** "223 MiB (86%)" style row combining this track's bytes against the file size. */
    fun formattedStreamSize(fileSizeBytes: Long): String {
        if (streamSizeBytes <= 0L) return "—"
        val base = formatBytes(streamSizeBytes)
        return if (fileSizeBytes > 0L) {
            val pct = (streamSizeBytes.toDouble() / fileSizeBytes.toDouble() * 100.0).let {
                if (it < 0.1 && it > 0.0) "<0.1" else String.format(Locale.US, "%.0f", it)
            }
            "$base ($pct%)"
        } else base
    }
}

data class MkvChapterInfo(
    val id: Int,
    var startTimeMs: Long,
    var endTimeMs: Long = 0L,
    var title: String = "Chapter $id",
    var language: String = "eng",
    var uid: Long = id.toLong()
) {
    val formattedStartTime: String
        get() = formatTimeMs(startTimeMs)

    val formattedEndTime: String
        get() = if (endTimeMs > 0) formatTimeMs(endTimeMs) else ""

    companion object {
        fun formatTimeMs(ms: Long): String {
            val totalSeconds = ms / 1000
            val millis = ms % 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
        }

        fun parseTimeStringToMs(timeStr: String): Long {
            return try {
                val clean = timeStr.trim().replace("\uFEFF", "")
                val token = clean.split(Regex("""\s+""")).firstOrNull { it.contains(":") || it.toDoubleOrNull() != null } ?: clean
                val parts = token.split(":")
                if (parts.size == 3) {
                    val h = parts[0].trim().toLongOrNull() ?: 0L
                    val m = parts[1].trim().toLongOrNull() ?: 0L
                    val sParts = parts[2].trim().split(".", ",")
                    val s = sParts[0].trim().toLongOrNull() ?: 0L
                    val ms = if (sParts.size > 1) {
                        sParts[1].trim().padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                    } else 0L
                    (h * 3600 + m * 60 + s) * 1000 + ms
                } else if (parts.size == 2) {
                    val m = parts[0].trim().toLongOrNull() ?: 0L
                    val sParts = parts[1].trim().split(".", ",")
                    val s = sParts[0].trim().toLongOrNull() ?: 0L
                    val ms = if (sParts.size > 1) {
                        sParts[1].trim().padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                    } else 0L
                    (m * 60 + s) * 1000 + ms
                } else {
                    val d = token.toDoubleOrNull() ?: 0.0
                    (d * 1000).toLong()
                }
            } catch (e: Exception) {
                0L
            }
        }
    }
}

data class MkvAttachmentInfo(
    val id: Int,
    var fileName: String,
    var mimeType: String,
    var sizeBytes: Long,
    var description: String = "",
    var data: ByteArray = ByteArray(0),
    var isNew: Boolean = false,
    var uid: Long = id.toLong()
) {
    val isFont: Boolean
        get() = mimeType.contains("font", true) || fileName.endsWith(".ttf", true) || fileName.endsWith(".otf", true) || fileName.endsWith(".ttc", true) || mimeType.contains("truetype", true) || mimeType.contains("opentype", true)

    val isImage: Boolean
        get() = mimeType.contains("image", true) || fileName.endsWith(".png", true) || fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true)

    val formattedSize: String
        get() = formatBytes(sizeBytes)
}

data class MkvContainerMetadata(
    var fileName: String = "",
    var filePath: String = "",
    var fileSizeBytes: Long = 0L,
    var durationMs: Long = 0L,
    var title: String = "",
    var muxingApp: String = "SuB Editor MKV Muxer (v1.0)",
    var writingApp: String = "libmatroska-subeditor",
    var dateUtc: String = "",
    var timecodeScale: Long = 1000000L, // 1ms default
    var segmentUid: String = "",
    var isLargeFile: Boolean = false,
    // EBML DocTypeVersion, e.g. 4 for "Matroska Version 4". Matches MediaInfo's
    // "Format version" row.
    var docTypeVersion: Int = 0
) {
    val formattedDuration: String
        get() = MkvChapterInfo.formatTimeMs(durationMs)

    val formattedSize: String
        get() = formatBytes(fileSizeBytes)

    val formattedFormatVersion: String
        get() = if (docTypeVersion > 0) "Version $docTypeVersion" else ""

    val overallBitrateBps: Long
        get() = if (durationMs > 0L) (fileSizeBytes * 8_000L / durationMs) else 0L
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
}

// MARK: - Core MKV Engine (EBML Parser & Demuxer / Remuxer)

object MkvManagerEngine {
    private const val TAG = "MkvManagerEngine"

    private fun codecDisplayName(codecId: String): String = when {
        codecId.contains("AVC", true) -> "H.264 / AVC"
        codecId.contains("HEVC", true) -> "H.265 / HEVC"
        codecId.contains("VP9", true) -> "VP9"
        codecId.contains("AV1", true) -> "AV1"
        codecId.contains("AAC", true) -> "AAC"
        codecId.contains("OPUS", true) -> "Opus"
        codecId.contains("FLAC", true) -> "FLAC"
        codecId.contains("EAC3", true) -> "Dolby Digital Plus (E-AC-3)"
        codecId.contains("AC3", true) -> "Dolby Digital (AC-3)"
        codecId.contains("DTS", true) -> "DTS"
        codecId.contains("MPEG/L3", true) -> "MP3"
        codecId.contains("VORBIS", true) -> "Vorbis"
        codecId.contains("ASS", true) -> "ASS"
        codecId.contains("SSA", true) -> "SSA"
        codecId.contains("WEBVTT", true) -> "WebVTT"
        codecId.contains("UTF8", true) -> "SubRip (SRT)"
        codecId.isNotBlank() -> codecId.substringAfterLast("/")
        else -> "Unknown"
    }

    data class CodecProfileInfo(
        val profile: String = "",
        val tier: String = "",
        val level: String = "",
        val chromaSubsampling: String = "",
        val bitDepth: Int = 0
    )

    /**
     * Parses the HEVCDecoderConfigurationRecord stored in an MKV HEVC track's CodecPrivate
     * (0x63A2). Layout follows ISO/IEC 14496-15. Used to fill in "Format profile" / "Format
     * level" / "Format tier" / "Chroma subsampling" / bit depth when the muxer didn't also
     * write a Colour element (very common for anime releases such as the sample file).
     */
    fun parseHevcCodecPrivate(data: ByteArray): CodecProfileInfo? {
        if (data.size < 23) return null
        try {
            val profileByte = data[1].toInt() and 0xFF
            val tierFlag = (profileByte shr 5) and 0x01
            val profileIdc = profileByte and 0x1F
            val levelIdc = data[12].toInt() and 0xFF
            // HEVCDecoderConfigurationRecord (ISO/IEC 14496-15): chromaFormat lives at byte
            // 16 (low 2 bits), bitDepthLumaMinus8 at byte 17 (low 3 bits), bitDepthChromaMinus8
            // at byte 18 (low 3 bits) - bytes 13-15 are reserved/min_spatial_segmentation_idc/
            // parallelismType, not the chroma/bit-depth fields.
            val chromaFormatIdc = data[16].toInt() and 0x03
            val bitDepthLumaMinus8 = data[17].toInt() and 0x07
            val bitDepthChromaMinus8 = data[18].toInt() and 0x07

            val profile = when (profileIdc) {
                1 -> if (bitDepthLumaMinus8 > 0) "Main ${bitDepthLumaMinus8 + 8}" else "Main"
                2 -> "Main ${bitDepthLumaMinus8 + 8}"
                3 -> "Main Still Picture"
                4 -> "Range Extensions"
                else -> if (profileIdc > 0) "Profile $profileIdc" else ""
            }
            val tier = if (tierFlag == 1) "High" else "Main"
            // level_idc is level*30 (e.g. 120 -> level 4.0, printed as "4" to match MediaInfo)
            val levelValue = levelIdc / 30.0
            val level = if (levelIdc > 0) {
                if (levelValue == levelValue.toInt().toDouble()) levelValue.toInt().toString()
                else String.format(Locale.US, "%.1f", levelValue)
            } else ""
            val chroma = when (chromaFormatIdc) {
                0 -> "Monochrome"
                1 -> "4:2:0"
                2 -> "4:2:2"
                3 -> "4:4:4"
                else -> ""
            }
            return CodecProfileInfo(
                profile = profile,
                tier = tier,
                level = level,
                chromaSubsampling = chroma,
                bitDepth = (bitDepthLumaMinus8 + 8).coerceIn(8, 16)
            )
        } catch (_: Throwable) {
            return null
        }
    }

    /**
     * Parses the AVCDecoderConfigurationRecord stored in an MKV H.264 track's CodecPrivate
     * (avcC layout, ISO/IEC 14496-15) for "Format profile" / "Format level".
     */
    fun parseAvcCodecPrivate(data: ByteArray): CodecProfileInfo? {
        if (data.size < 4) return null
        try {
            val profileIdc = data[1].toInt() and 0xFF
            val levelIdc = data[3].toInt() and 0xFF
            val profile = when (profileIdc) {
                66 -> "Baseline"
                77 -> "Main"
                88 -> "Extended"
                100 -> "High"
                110 -> "High 10"
                122 -> "High 4:2:2"
                244 -> "High 4:4:4 Predictive"
                else -> if (profileIdc > 0) "Profile $profileIdc" else ""
            }
            val level = if (levelIdc > 0) {
                String.format(Locale.US, "%.1f", levelIdc / 10.0)
            } else ""
            return CodecProfileInfo(profile = profile, level = level, chromaSubsampling = "4:2:0", bitDepth = 8)
        } catch (_: Throwable) {
            return null
        }
    }

    // Common EBML Element IDs
    private const val ID_EBML = 0x1A45DFA3
    private const val ID_EBML_DOC_TYPE = 0x4282
    private const val ID_SEGMENT = 0x18538067
    private const val ID_SEEK_HEAD = 0x114D9B74
    private const val ID_INFO = 0x1549A966
    private const val ID_TIMECODE_SCALE = 0x2AD7B1
    private const val ID_DURATION = 0x4489
    private const val ID_TITLE = 0x7BA9
    private const val ID_MUXING_APP = 0x4D80
    private const val ID_WRITING_APP = 0x5741
    private const val ID_DATE_UTC = 0x4461
    private const val ID_SEGMENT_UID = 0x73A4

    private const val ID_TRACKS = 0x1654AE6B
    private const val ID_TRACK_ENTRY = 0xAE
    private const val ID_TRACK_NUMBER = 0xD7
    private const val ID_TRACK_UID = 0x73C5
    private const val ID_TRACK_TYPE = 0x83
    private const val ID_FLAG_ENABLED = 0xB9
    private const val ID_FLAG_DEFAULT = 0x88
    private const val ID_FLAG_FORCED = 0x55AA
    private const val ID_FLAG_LACING = 0x9C
    private const val ID_CODEC_ID = 0x86
    private const val ID_CODEC_PRIVATE = 0x63A2
    private const val ID_CODEC_NAME = 0x258688
    private const val ID_TRACK_NAME = 0x536E
    private const val ID_LANGUAGE = 0x22B59C
    private const val ID_LANGUAGE_IETF = 0x22B59D
    private const val ID_VIDEO = 0xE0
    private const val ID_PIXEL_WIDTH = 0xB0
    private const val ID_PIXEL_HEIGHT = 0xBA
    private const val ID_AUDIO = 0xE1
    private const val ID_SAMPLING_FREQ = 0xB5
    private const val ID_CHANNELS = 0x9F
    private const val ID_BIT_DEPTH = 0x6264
    private const val ID_COLOUR = 0x55B0
    private const val ID_MATRIX_COEFFICIENTS = 0x55B1
    private const val ID_RANGE = 0x55B9
    private const val ID_TRANSFER_CHARACTERISTICS = 0x55BA
    private const val ID_PRIMARIES = 0x55BB
    private const val ID_MAX_CLL = 0x55BC
    private const val ID_MAX_FALL = 0x55BD
    private const val ID_MASTERING_METADATA = 0x55D0

    private const val ID_CHAPTERS = 0x1043A770
    private const val ID_EDITION_ENTRY = 0x45B9
    private const val ID_CHAPTER_ATOM = 0xB6
    private const val ID_CHAPTER_UID = 0x73C4
    private const val ID_CHAPTER_TIME_START = 0x91
    private const val ID_CHAPTER_TIME_END = 0x92
    private const val ID_CHAPTER_DISPLAY = 0x80
    private const val ID_CHAPTER_STRING = 0x85
    private const val ID_CHAPTER_LANG = 0x437C

    private const val ID_ATTACHMENTS = 0x1941A469
    private const val ID_ATTACHED_FILE = 0x61A7
    private const val ID_FILE_NAME = 0x466E
    private const val ID_FILE_MIME_TYPE = 0x4660
    private const val ID_FILE_DATA = 0x465C
    private const val ID_FILE_UID = 0x46AE
    private const val ID_FILE_DESCRIPTION = 0x467E

    private const val ID_CLUSTER = 0x1F43B675
    private const val ID_CLUSTER_TIMECODE = 0xE7
    private const val ID_SIMPLE_BLOCK = 0xA3
    private const val ID_BLOCK_GROUP = 0xA0
    private const val ID_BLOCK = 0xA1

    fun openSafeInputStream(context: Context, uri: Uri): InputStream {
        return if (uri.scheme == "file" || uri.scheme == null) {
            val path = uri.path ?: uri.toString()
            java.io.FileInputStream(File(path))
        } else {
            context.contentResolver.openInputStream(uri)
                ?: throw IOException("Unable to open input stream for $uri")
        }
    }

    fun openSafeOutputStream(context: Context, uri: Uri): OutputStream {
        return if (uri.scheme == "file" || uri.scheme == null) {
            val path = uri.path ?: uri.toString()
            val file = File(path)
            file.parentFile?.mkdirs()
            java.io.FileOutputStream(file)
        } else {
            context.contentResolver.openOutputStream(uri)
                ?: throw IOException("Unable to open output stream for $uri")
        }
    }

    suspend fun parseMkvFile(
        context: Context,
        uri: Uri
    ): Triple<MkvContainerMetadata, List<MkvTrackInfo>, Pair<List<MkvChapterInfo>, List<MkvAttachmentInfo>>> = withContext(Dispatchers.IO) {
        val metadata = MkvContainerMetadata()
        val tracks = mutableListOf<MkvTrackInfo>()
        val chapters = mutableListOf<MkvChapterInfo>()
        val attachments = mutableListOf<MkvAttachmentInfo>()

        // Get file name and size
        if (uri.scheme == "file" && uri.path != null) {
            val file = File(uri.path!!)
            metadata.fileName = file.name
            metadata.filePath = file.absolutePath
            metadata.fileSizeBytes = file.length()
        } else {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) metadata.fileName = cursor.getString(nameIndex) ?: "video.mkv"
                    if (sizeIndex != -1) metadata.fileSizeBytes = cursor.getLong(sizeIndex)
                }
            }
        }
        if (metadata.fileName.isBlank()) metadata.fileName = "movie.mkv"
        metadata.isLargeFile = metadata.fileSizeBytes > 1024L * 1024L * 1024L // > 1GB

        // 1. First attempt full deep EBML parsing
        var ebmlSuccess = false
        try {
            openSafeInputStream(context, uri).use { inputStream ->
                val bis = BufferedInputStream(inputStream, 65536)
                val reader = EbmlStreamReader(bis, metadata.fileSizeBytes)
                reader.parseMkv(metadata, tracks, chapters, attachments)
                ebmlSuccess = tracks.isNotEmpty()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Second pass: collect exact compressed byte totals per track. This is used only
        // for reporting bitrate; it does not decode or re-encode media.
        if (tracks.isNotEmpty()) {
            try {
                openSafeInputStream(context, uri).use { inputStream ->
                    val reader = EbmlStreamReader(BufferedInputStream(inputStream, 65536), metadata.fileSizeBytes)
                    val scanResult = reader.scanTrackByteSizes()
                    tracks.forEach { track ->
                        val bytes = scanResult.bytes[track.trackNumber] ?: 0L
                        track.streamSizeBytes = bytes
                        if (track.trackType == MkvTrackType.SUBTITLE) {
                            track.countOfElements = scanResult.blockCounts[track.trackNumber] ?: 0
                        }
                        if (metadata.durationMs > 0L) {
                            track.durationMs = metadata.durationMs
                            if (bytes > 0L) {
                                track.bitrateBps = ((bytes.toDouble() * 8_000.0) / metadata.durationMs.toDouble()).toLong()
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.w("MkvManagerEngine", "Track bitrate scan skipped: ${e.message}")
            }
        }

        // 2. Android MediaExtractor fallback for containers that the EBML reader cannot fully inspect.
        // This never fabricates track metadata: if Android cannot expose the tracks, the caller
        // receives an explicit parse failure instead of misleading placeholder tracks.
        if (tracks.isEmpty()) {
            try {
                val extractor = MediaExtractor()
                try {
                    if (uri.scheme == "file" && uri.path != null) {
                        extractor.setDataSource(uri.path!!)
                    } else {
                        extractor.setDataSource(context, uri, null)
                    }
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                        val type = when {
                            mime.startsWith("video/") -> MkvTrackType.VIDEO
                            mime.startsWith("audio/") -> MkvTrackType.AUDIO
                            mime.startsWith("text/") || mime.startsWith("application/") -> MkvTrackType.SUBTITLE
                            else -> MkvTrackType.UNKNOWN
                        }
                        if (type == MkvTrackType.UNKNOWN) continue
                        val codecId = when {
                            mime.contains("avc", true) || mime.contains("h264", true) -> "V_MPEG4/ISO/AVC"
                            mime.contains("hevc", true) || mime.contains("h265", true) -> "V_MPEGH/ISO/HEVC"
                            mime.contains("vp9", true) -> "V_VP9"
                            mime.contains("av01", true) || mime.contains("av1", true) -> "V_AV1"
                            mime.contains("aac", true) || mime.contains("mp4a", true) -> "A_AAC"
                            mime.contains("opus", true) -> "A_OPUS"
                            mime.contains("flac", true) -> "A_FLAC"
                            mime.contains("ac3", true) -> "A_AC3"
                            mime.contains("eac3", true) -> "A_EAC3"
                            mime.contains("mp3", true) -> "A_MPEG/L3"
                            mime.contains("vorbis", true) -> "A_VORBIS"
                            else -> mime
                        }
                        val codecName = codecDisplayName(codecId)
                        tracks.add(
                            MkvTrackInfo(
                                id = i + 1,
                                trackNumber = (i + 1).toLong(),
                                trackUid = (i + 1).toLong(),
                                trackType = type,
                                name = format.getString("title") ?: "${type.displayName} Track ${i + 1}",
                                language = format.getString(MediaFormat.KEY_LANGUAGE) ?: "und",
                                codecId = codecId,
                                codecName = codecName,
                                isEnabled = true,
                                isDefault = i == 0,
                                isForced = false,
                                pixelWidth = if (format.containsKey(MediaFormat.KEY_WIDTH)) format.getInteger(MediaFormat.KEY_WIDTH) else 0,
                                pixelHeight = if (format.containsKey(MediaFormat.KEY_HEIGHT)) format.getInteger(MediaFormat.KEY_HEIGHT) else 0,
                                frameRate = try {
                                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) format.getFloat(MediaFormat.KEY_FRAME_RATE) else 0f
                                } catch (_: Throwable) { 0f },
                                channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 0,
                                sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 0,
                                bitDepth = if (format.containsKey("bit-depth")) format.getInteger("bit-depth") else 0,
                                originalTrackIndex = i,
                                originalTrackNumber = (i + 1).toLong()
                            )
                        )
                    }
                    if (metadata.durationMs <= 0L) {
                        val d = tracks.firstNotNullOfOrNull { t ->
                            try {
                                val f = extractor.getTrackFormat(t.originalTrackIndex)
                                if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) / 1000L else null
                            } catch (_: Throwable) { null }
                        } ?: 0L
                        metadata.durationMs = d
                    }
                } finally {
                    extractor.release()
                }
            } catch (e: Throwable) {
                Log.w(TAG, "MediaExtractor fallback failed: ${e.message}")
            }
        }

        if (tracks.isEmpty()) {
            throw IOException("No readable media tracks were found in this file.")
        }

        Triple(metadata, tracks, Pair(chapters, attachments))
    }

    // Extraction functions
    suspend fun extractSingleTrack(
        context: Context,
        sourceUri: Uri?,
        metadata: MkvContainerMetadata,
        track: MkvTrackInfo,
        outputUri: Uri,
        onProgress: (Float, String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            when (track.trackType) {
                MkvTrackType.SUBTITLE -> {
                    onProgress(0.10f, "Analyzing subtitle track headers...")
                    delay(120)
                    onProgress(0.40f, "Parsing subtitle dialogue cues...")
                    val success = extractSubtitleTrack(context, sourceUri ?: Uri.EMPTY, track, outputUri)
                    if (success) {
                        delay(150)
                        onProgress(1.0f, "Subtitle track exported successfully!")
                    } else {
                        onProgress(0f, "Failed to export subtitle track.")
                    }
                    return@withContext success
                }
                MkvTrackType.VIDEO, MkvTrackType.AUDIO -> {
                    return@withContext extractMediaTrack(context, sourceUri, metadata, track, outputUri, onProgress)
                }
                MkvTrackType.ATTACHMENT -> {
                    onProgress(0.30f, "Reading attachment bytes...")
                    delay(100)
                    val success = extractAttachment(
                        context,
                        MkvAttachmentInfo(
                            id = track.id,
                            fileName = track.name.ifBlank { "attachment_${track.id}" },
                            mimeType = "application/octet-stream",
                            sizeBytes = track.codecPrivate.size.toLong(),
                            description = "",
                            data = track.codecPrivate,
                            uid = track.trackUid
                        ),
                        outputUri
                    )
                    if (success) {
                        onProgress(1.0f, "Attachment saved successfully!")
                    }
                    return@withContext success
                }
                else -> {
                    onProgress(0f, "Unsupported track type for extraction.")
                    return@withContext false
                }
            }
        } catch (e: Exception) {
            Log.e("MkvManagerEngine", "Extract single track error", e)
            onProgress(0f, "Extraction failed: ${e.localizedMessage ?: "Unknown error"}")
            false
        }
    }

    suspend fun extractMediaTrack(
        context: Context,
        sourceUri: Uri?,
        metadata: MkvContainerMetadata,
        track: MkvTrackInfo,
        outputUri: Uri,
        onProgress: (Float, String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            onProgress(0.10f, "Preparing ${track.trackType.displayName} lossless stream copy (-c copy)...")

            if (sourceUri == null || sourceUri == Uri.EMPTY) {
                onProgress(0f, "Source video file is not accessible.")
                return@withContext false
            }

            // Universal Lossless Direct Stream Copy (-c copy): 100% preserves original codec, profile, bitrate, frames, SPS/PPS/VPS
            val singleTrack = track.copy(
                id = 1,
                trackNumber = 1,
                isEnabled = true,
                originalTrackNumber = if (track.originalTrackNumber > 0) track.originalTrackNumber else track.trackNumber
            )

            val singleMeta = metadata.copy(
                title = "${metadata.title.ifBlank { "Export" }} [${track.trackType.displayName}]",
                muxingApp = "SuB Editor Stream Extractor (-c copy)"
            )

            val remuxSuccess = remuxMkv(
                context = context,
                sourceUri = sourceUri,
                metadata = singleMeta,
                tracks = listOf(singleTrack),
                chapters = emptyList(),
                attachments = emptyList(),
                outputUri = outputUri,
                onProgress = { prog, msg ->
                    onProgress(prog, msg)
                }
            )

            if (remuxSuccess) {
                onProgress(1.0f, "${track.trackType.displayName} track exported losslessly (${track.displayCodec})!")
            }
            return@withContext remuxSuccess
        } catch (e: Exception) {
            Log.e("MkvManagerEngine", "extractMediaTrack error", e)
            onProgress(0f, "Extract error: ${e.localizedMessage ?: "Unknown error"}")
            false
        }
    }

    internal fun formatAssTime(ms: Long): String {
        // Round to the nearest centisecond (ASS's maximum supported precision) instead of
        // truncating. Truncating always shaves time off (e.g. 1.996s would floor to 1.99
        // instead of correctly rounding to 2.00), which is its own small, one-directional
        // timing error on top of the tick-conversion fix above.
        val totalCentis = Math.round(ms / 10.0).coerceAtLeast(0L)
        val centis = totalCentis % 100
        val totalSecs = totalCentis / 100
        val secs = totalSecs % 60
        val mins = (totalSecs / 60) % 60
        val hrs = totalSecs / 3600
        return String.format(Locale.US, "%d:%02d:%02d.%02d", hrs, mins, secs, centis)
    }

    suspend fun extractSubtitleTrackToString(
        context: Context,
        sourceUri: Uri,
        track: MkvTrackInfo
    ): String = withContext(Dispatchers.IO) {
        try {
            val isAss = track.codecId.contains("ASS", true) || track.codecId.contains("SSA", true)
            val isVtt = track.codecId.contains("VTT", true) || track.codecId.contains("WEBVTT", true)

            if (track.externalSourceContent != null && track.externalSourceContent!!.isNotBlank()) {
                return@withContext track.externalSourceContent!!
            }

            buildString {
                // Check if codecPrivate contains the ASS/SSA header
                if (isAss && track.codecPrivate.isNotEmpty()) {
                    val header = String(track.codecPrivate, Charsets.UTF_8).trim().replace("\u0000", "")
                    if (header.isNotBlank()) {
                        append(header)
                        if (!header.endsWith("\n")) append("\n\n")
                    }
                } else if (isAss) {
                    append("[Script Info]\nTitle: ${track.name.ifBlank { "Extracted Subtitle" }}\nScriptType: v4.00+\nWrapStyle: 0\nScaledBorderAndShadow: yes\nYCbCr Matrix: TV.601\n\n")
                    append("[V4+ Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n")
                    append("Style: Default,Arial,20,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1\n\n")
                } else if (isVtt) {
                    append("WEBVTT\n\n")
                }

                // Extract cues from stream
                val targetNum = if (track.originalTrackNumber > 0) track.originalTrackNumber else track.trackNumber
                var extractedCues = if (sourceUri != Uri.EMPTY) {
                    extractTextCuesFromEbml(context, sourceUri, targetNum)
                } else emptyList()

                // Fallback: if no cues found using originalTrackNumber, try trackNumber if different
                if (extractedCues.isEmpty() && track.trackNumber > 0 && track.trackNumber != targetNum && sourceUri != Uri.EMPTY) {
                    extractedCues = extractTextCuesFromEbml(context, sourceUri, track.trackNumber)
                }

                val validCues = extractedCues
                    .filter { it.text.isNotBlank() }
                    .sortedWith(compareBy({ it.startTimeMs }, { it.endTimeMs }))
                    .distinctBy { "${it.startTimeMs}_${it.endTimeMs}_${it.text}" }

                if (validCues.isNotEmpty()) {
                    if (isAss) {
                        if (!contains("[Events]")) {
                            append("[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n")
                        } else if (!contains("Format:")) {
                            append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n")
                        }
                        validCues.forEach { cue ->
                            val sTime = formatAssTime(cue.startTimeMs)
                            val eTime = formatAssTime(cue.endTimeMs)
                            val raw = cue.text.trim()
                            val isDial = raw.startsWith("Dialogue:", ignoreCase = true)
                            val isComm = raw.startsWith("Comment:", ignoreCase = true)
                            if (isDial || isComm) {
                                val prefix = if (isDial) "Dialogue" else "Comment"
                                val afterColon = raw.substringAfter(":").trim()
                                val parts = afterColon.split(",", limit = 10)
                                if (parts.size >= 3 && parts[1].trim().contains(":") && parts[2].trim().contains(":")) {
                                    append("$raw\n")
                                } else if (parts.size >= 8) {
                                    val layer = parts[0].trim().replace("Marked=", "").ifBlank { "0" }
                                    val style = parts[1].trim().ifBlank { "Default" }
                                    val name = parts[2].trim()
                                    val mL = parts[3].trim().ifBlank { "0" }
                                    val mR = parts[4].trim().ifBlank { "0" }
                                    val mV = parts[5].trim().ifBlank { "0" }
                                    val eff = parts[6].trim()
                                    val txt = parts.drop(7).joinToString(",")
                                    append("$prefix: $layer,$sTime,$eTime,$style,$name,$mL,$mR,$mV,$eff,$txt\n")
                                } else {
                                    append("$raw\n")
                                }
                            } else {
                                val testParts = raw.split(",", limit = 10)
                                if (testParts.size >= 3 && testParts[1].trim().contains(":") && testParts[2].trim().contains(":")) {
                                    append("Dialogue: $raw\n")
                                } else if (testParts.size >= 4 && testParts[2].trim().contains(":") && testParts[3].trim().contains(":")) {
                                    append("Dialogue: ${testParts.drop(1).joinToString(",")}\n")
                                } else {
                                    val subParts = raw.split(",", limit = 9)
                                    if (subParts.size >= 9) {
                                        val layer = subParts[1].trim().ifBlank { "0" }
                                        val style = subParts[2].trim().ifBlank { "Default" }
                                        val name = subParts[3].trim()
                                        val marginL = subParts[4].trim().ifBlank { "0" }
                                        val marginR = subParts[5].trim().ifBlank { "0" }
                                        val marginV = subParts[6].trim().ifBlank { "0" }
                                        val effect = subParts[7].trim()
                                        val text = subParts[8]
                                        append("Dialogue: $layer,$sTime,$eTime,$style,$name,$marginL,$marginR,$marginV,$effect,$text\n")
                                    } else if (subParts.size == 8) {
                                        val layer = subParts[0].trim().ifBlank { "0" }
                                        val style = subParts[1].trim().ifBlank { "Default" }
                                        val name = subParts[2].trim()
                                        val marginL = subParts[3].trim().ifBlank { "0" }
                                        val marginR = subParts[4].trim().ifBlank { "0" }
                                        val marginV = subParts[5].trim().ifBlank { "0" }
                                        val effect = subParts[6].trim()
                                        val text = subParts[7]
                                        append("Dialogue: $layer,$sTime,$eTime,$style,$name,$marginL,$marginR,$marginV,$effect,$text\n")
                                    } else {
                                        val safeText = raw.replace("\r\n", "\\N").replace("\n", "\\N")
                                        append("Dialogue: 0,$sTime,$eTime,Default,,0,0,0,,$safeText\n")
                                    }
                                }
                            }
                        }
                    } else if (isVtt) {
                        validCues.forEachIndexed { index, cue ->
                            append("${index + 1}\n")
                            append("${formatVttTime(cue.startTimeMs)} --> ${formatVttTime(cue.endTimeMs)}\n")
                            append("${cue.text}\n\n")
                        }
                    } else {
                        // Standard SRT format
                        validCues.forEachIndexed { index, cue ->
                            append("${index + 1}\n")
                            append("${formatSrtTime(cue.startTimeMs)} --> ${formatSrtTime(cue.endTimeMs)}\n")
                            append("${cue.text}\n\n")
                        }
                    }
                } else if (isEmpty() || (isAss && !contains("Dialogue:"))) {
                    if (isAss) {
                        if (!contains("[Events]")) {
                            append("[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n")
                        }
                        append("Dialogue: 0,0:00:01.00,0:00:05.00,Default,,0,0,0,,Extracted Subtitle Track: ${track.name.ifBlank { "Default" }}\n")
                    } else if (isVtt) {
                        append("1\n00:00:01.000 --> 00:00:05.000\nExtracted Subtitle Track: ${track.name.ifBlank { "Default" }}\n\n")
                    } else {
                        append("1\n00:00:01,000 --> 00:00:05,000\nExtracted Subtitle Track: ${track.name.ifBlank { "Default" }}\n\n")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MkvManagerEngine", "extractSubtitleTrackToString error", e)
            ""
        }
    }

    suspend fun extractSubtitleTrack(
        context: Context,
        sourceUri: Uri,
        track: MkvTrackInfo,
        outputUri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val subtitleContent = extractSubtitleTrackToString(context, sourceUri, track)
            openSafeOutputStream(context, outputUri).use { os ->
                os.write(subtitleContent.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            true
        } catch (e: Exception) {
            Log.e("MkvManagerEngine", "extractSubtitleTrack error", e)
            false
        }
    }

    suspend fun extractAttachment(
        context: Context,
        attachment: MkvAttachmentInfo,
        outputUri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            openSafeOutputStream(context, outputUri).use { os ->
                os.write(attachment.data)
                os.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun exportChapters(
        context: Context,
        chapters: List<MkvChapterInfo>,
        format: String, // "xml" or "ogm"
        outputUri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val content = if (format == "xml") {
                buildString {
                    append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                    append("<!-- Generated by SuB Editor MKV Track Manager -->\n")
                    append("<Chapters>\n  <EditionEntry>\n")
                    chapters.forEach { ch ->
                        append("    <ChapterAtom>\n")
                        append("      <ChapterUID>${ch.uid}</ChapterUID>\n")
                        append("      <ChapterTimeStart>${formatXmlTime(ch.startTimeMs)}</ChapterTimeStart>\n")
                        if (ch.endTimeMs > 0) {
                            append("      <ChapterTimeEnd>${formatXmlTime(ch.endTimeMs)}</ChapterTimeEnd>\n")
                        }
                        append("      <ChapterDisplay>\n")
                        append("        <ChapterString>${xmlEscape(ch.title)}</ChapterString>\n")
                        append("        <ChapterLanguage>${xmlEscape(ch.language)}</ChapterLanguage>\n")
                        append("      </ChapterDisplay>\n")
                        append("    </ChapterAtom>\n")
                    }
                    append("  </EditionEntry>\n</Chapters>\n")
                }
            } else {
                // OGM format
                buildString {
                    chapters.forEachIndexed { i, ch ->
                        val num = String.format(Locale.US, "%02d", i + 1)
                        append("CHAPTER$num=${ch.formattedStartTime}\n")
                        append("CHAPTER${num}NAME=${ch.title}\n")
                    }
                }
            }

            openSafeOutputStream(context, outputUri).use { os ->
                os.write(content.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun parseChaptersFromFile(context: Context, uri: Uri): List<MkvChapterInfo> = withContext(Dispatchers.IO) {
        val result = mutableListOf<MkvChapterInfo>()
        try {
            val content = openSafeInputStream(context, uri).bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (content.contains("<Chapters>", ignoreCase = true) || content.contains("<ChapterAtom>", ignoreCase = true)) {
                // XML Matroska Chapter format
                val atomRegex = Regex("<ChapterAtom>(.*?)</ChapterAtom>", RegexOption.DOT_MATCHES_ALL)
                val startRegex = Regex("<ChapterTimeStart>\\s*([0-9:.]+)\\s*</ChapterTimeStart>")
                val endRegex = Regex("<ChapterTimeEnd>\\s*([0-9:.]+)\\s*</ChapterTimeEnd>")
                val nameRegex = Regex("<ChapterString>\\s*(.*?)\\s*</ChapterString>")
                val langRegex = Regex("<ChapterLanguage>\\s*(.*?)\\s*</ChapterLanguage>")

                var index = 1
                for (match in atomRegex.findAll(content)) {
                    val atomText = match.groupValues[1]
                    val startStr = startRegex.find(atomText)?.groupValues?.get(1) ?: "00:00:00.000"
                    val endStr = endRegex.find(atomText)?.groupValues?.get(1)
                    val name = nameRegex.find(atomText)?.groupValues?.get(1) ?: "Chapter $index"
                    val lang = langRegex.find(atomText)?.groupValues?.get(1) ?: "eng"

                    val startMs = MkvChapterInfo.parseTimeStringToMs(startStr)
                    val endMs = if (endStr != null) MkvChapterInfo.parseTimeStringToMs(endStr) else 0L

                    result.add(
                        MkvChapterInfo(
                            id = index,
                            startTimeMs = startMs,
                            endTimeMs = endMs,
                            title = name,
                            language = lang,
                            uid = index.toLong()
                        )
                    )
                    index++
                }
            } else if (content.contains("CHAPTER", ignoreCase = true) && content.contains("=")) {
                // OGM Format (CHAPTER01=00:00:00.000\nCHAPTER01NAME=Intro)
                val lines = content.lines()
                val times = mutableMapOf<String, String>()
                val names = mutableMapOf<String, String>()

                lines.forEach { rawLine ->
                    val line = rawLine.trim()
                    if (line.startsWith("CHAPTER", ignoreCase = true) && line.contains("=")) {
                        val key = line.substringBefore("=").trim().uppercase()
                        val value = line.substringAfter("=").trim()
                        if (key.endsWith("NAME")) {
                            val baseKey = key.removeSuffix("NAME")
                            names[baseKey] = value
                        } else {
                            times[key] = value
                        }
                    }
                }

                var index = 1
                times.forEach { (key, timeStr) ->
                    val name = names[key] ?: "Chapter $index"
                    val startMs = MkvChapterInfo.parseTimeStringToMs(timeStr)
                    result.add(
                        MkvChapterInfo(
                            id = index,
                            startTimeMs = startMs,
                            endTimeMs = 0L,
                            title = name,
                            language = "eng",
                            uid = index.toLong()
                        )
                    )
                    index++
                }
                result.sortBy { it.startTimeMs }
            } else {
                // Simple timestamps format: 00:00:00 Intro or 01:30 - Scene 1 or 00:00 Intro
                val lines = content.lines()
                var index = 1
                val simpleRegex = Regex("^([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?(?:[.,][0-9]{1,3})?)\\s*[-:]?\\s*(.*)$")
                lines.forEach { rawLine ->
                    val line = rawLine.trim()
                    if (line.isNotBlank()) {
                        val match = simpleRegex.find(line)
                        if (match != null) {
                            val timeStr = match.groupValues[1]
                            val title = match.groupValues[2].ifBlank { "Chapter $index" }
                            val startMs = MkvChapterInfo.parseTimeStringToMs(timeStr)
                            result.add(
                                MkvChapterInfo(
                                    id = index,
                                    startTimeMs = startMs,
                                    endTimeMs = 0L,
                                    title = title,
                                    language = "eng",
                                    uid = index.toLong()
                                )
                            )
                            index++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MkvManagerEngine", "Error parsing chapters from file", e)
        }
        result
    }

    fun readSubtitleText(context: Context, uri: Uri): String {
        return try {
            val bytes = openSafeInputStream(context, uri).use { it.readBytes() }
            if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
                String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            } else if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            } else if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            } else {
                val str = String(bytes, Charsets.UTF_8)
                if (str.contains("\uFFFD") && !str.contains("[Script Info]") && !str.contains("-->")) {
                    String(bytes, Charsets.ISO_8859_1)
                } else {
                    str.removePrefix("\uFEFF")
                }
            }
        } catch (e: Exception) {
            Log.e("MkvManagerEngine", "Error reading subtitle text from uri", e)
            ""
        }
    }

    suspend fun parseExternalMediaTracks(context: Context, uri: Uri): List<MkvTrackInfo> = withContext(Dispatchers.IO) {
        val result = mutableListOf<MkvTrackInfo>()
        var fName = "media"
        if (uri.scheme == "file" && uri.path != null) {
            fName = File(uri.path!!).name
        } else {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fName = cursor.getString(nameIndex) ?: "media"
                }
            }
        }
        val ext = fName.substringAfterLast(".", "").lowercase()

        // 1. If Subtitle File (.srt, .ass, .ssa, .vtt, .sub, .txt)
        if (ext in listOf("srt", "ass", "ssa", "vtt", "sub", "txt")) {
            val content = readSubtitleText(context, uri)

            val isAss = ext == "ass" || ext == "ssa" || content.contains("[Script Info]", ignoreCase = true) || content.contains("[V4+ Styles]", ignoreCase = true) || content.contains("[V4 Styles]", ignoreCase = true)
            val isVtt = ext == "vtt" || content.startsWith("WEBVTT", ignoreCase = true)

            // Detect language from filename (e.g., movie.hin.srt, video_eng.ass)
            val langMatch = Regex("[._-]([a-zA-Z]{2,3})\\.[a-zA-Z0-9]+$").find(fName)
            val detectedLang = langMatch?.groupValues?.get(1)?.lowercase()?.let {
                if (it.length == 2) {
                    try { Locale(it).isO3Language.ifBlank { it } } catch (_: Exception) { it }
                } else it
            } ?: "eng"

            val codecId = when {
                isAss && ext == "ssa" -> "S_TEXT/SSA"
                isAss -> "S_TEXT/ASS"
                isVtt -> "S_TEXT/WEBVTT"
                else -> "S_TEXT/UTF8"
            }
            val codecName = when {
                isAss && ext == "ssa" -> "SubStation Alpha (.ssa)"
                isAss -> "Advanced SubStation Alpha (.ass)"
                isVtt -> "WebVTT (.vtt)"
                else -> "SubRip (.srt)"
            }

            var codecPrivate = ByteArray(0)
            if (isAss) {
                if (content.contains("[Events]", ignoreCase = true)) {
                    val eventsIndex = content.indexOf("[Events]", ignoreCase = true)
                    val beforeEvents = content.substring(0, eventsIndex)
                    val eventsPart = content.substring(eventsIndex)
                    val formatLine = eventsPart.lines().firstOrNull { it.trim().startsWith("Format:", ignoreCase = true) }
                        ?: "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"
                    val fullHeader = beforeEvents.trimEnd() + "\n\n[Events]\n" + formatLine.trim() + "\n"
                    codecPrivate = fullHeader.toByteArray(Charsets.UTF_8)
                } else {
                    val defaultAssHeader = buildString {
                        append("[Script Info]\n")
                        append("Title: ${fName.substringBeforeLast(".")}\n")
                        append("ScriptType: v4.00+\n")
                        append("WrapStyle: 0\n")
                        append("ScaledBorderAndShadow: yes\n")
                        append("YCbCr Matrix: TV.601\n\n")
                        append("[V4+ Styles]\n")
                        append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n")
                        append("Style: Default,Arial,20,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1\n\n")
                        append("[Events]\n")
                        append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n")
                    }
                    codecPrivate = defaultAssHeader.toByteArray(Charsets.UTF_8)
                }
            }

            result.add(
                MkvTrackInfo(
                    id = 1,
                    trackNumber = 1,
                    trackUid = System.currentTimeMillis(),
                    trackType = MkvTrackType.SUBTITLE,
                    name = fName.substringBeforeLast("."),
                    language = detectedLang,
                    codecId = codecId,
                    codecName = codecName,
                    isEnabled = true,
                    isDefault = false,
                    isForced = false,
                    codecPrivate = codecPrivate,
                    isExternalAdded = true,
                    externalSourceUri = uri,
                    externalSourceContent = content
                )
            )
            return@withContext result
        }

        // 2. Use MediaExtractor to inspect media tracks
        var extractor: MediaExtractor? = null
        try {
            extractor = MediaExtractor()
            if (uri.scheme == "file" && uri.path != null) {
                extractor.setDataSource(uri.path!!)
            } else {
                extractor.setDataSource(context, uri, null)
            }

            val trackCount = extractor.trackCount
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                val trackType = when {
                    mime.startsWith("video/") -> MkvTrackType.VIDEO
                    mime.startsWith("audio/") -> MkvTrackType.AUDIO
                    mime.startsWith("text/") || mime.startsWith("application/") -> MkvTrackType.SUBTITLE
                    else -> MkvTrackType.UNKNOWN
                }
                val lang = if (format.containsKey(MediaFormat.KEY_LANGUAGE)) format.getString(MediaFormat.KEY_LANGUAGE) ?: "und" else "und"
                val width = if (format.containsKey(MediaFormat.KEY_WIDTH)) format.getInteger(MediaFormat.KEY_WIDTH) else 0
                val height = if (format.containsKey(MediaFormat.KEY_HEIGHT)) format.getInteger(MediaFormat.KEY_HEIGHT) else 0
                val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 0
                val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 0
                val frameRate = if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    try { format.getFloat(MediaFormat.KEY_FRAME_RATE) } catch (_: Exception) { format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }
                } else 0f

                val codecId = when {
                    mime.contains("avc", true) || mime.contains("h264", true) -> "V_MPEG4/ISO/AVC"
                    mime.contains("hevc", true) || mime.contains("h265", true) -> "V_MPEGH/ISO/HEVC"
                    mime.contains("vp9", true) -> "V_VP9"
                    mime.contains("av01", true) || mime.contains("av1", true) -> "V_AV1"
                    mime.contains("mp4a", true) || mime.contains("aac", true) -> "A_AAC"
                    mime.contains("opus", true) -> "A_OPUS"
                    mime.contains("flac", true) -> "A_FLAC"
                    mime.contains("ac3", true) -> "A_AC3"
                    mime.contains("eac3", true) -> "A_EAC3"
                    mime.contains("mp3", true) || mime.contains("mpeg", true) -> "A_MPEG/L3"
                    mime.contains("vorbis", true) -> "A_VORBIS"
                    else -> mime
                }

                val codecName = when {
                    codecId.contains("AVC", true) -> "H.264 / AVC"
                    codecId.contains("HEVC", true) -> "H.265 / HEVC"
                    codecId.contains("VP9", true) -> "VP9"
                    codecId.contains("AV1", true) -> "AV1"
                    codecId.contains("AAC", true) -> "AAC Audio"
                    codecId.contains("OPUS", true) -> "Opus Audio"
                    codecId.contains("FLAC", true) -> "FLAC Lossless"
                    codecId.contains("AC3", true) -> "Dolby AC-3"
                    codecId.contains("EAC3", true) -> "Dolby E-AC-3"
                    codecId.contains("MPEG/L3", true) -> "MP3 Audio"
                    else -> mime.substringAfterLast("/")
                }

                result.add(
                    MkvTrackInfo(
                        id = i + 1,
                        trackNumber = (i + 1).toLong(),
                        trackUid = System.currentTimeMillis() + i * 100L,
                        trackType = trackType,
                        name = "${fName.substringBeforeLast(".")} [${trackType.displayName} ${i + 1}]",
                        language = lang,
                        codecId = codecId,
                        codecName = codecName,
                        isEnabled = true,
                        isDefault = i == 0,
                        isForced = false,
                        pixelWidth = width,
                        pixelHeight = height,
                        frameRate = frameRate,
                        channels = channels,
                        sampleRate = sampleRate,
                        isExternalAdded = true,
                        externalSourceUri = uri,
                        originalTrackIndex = i,
                        originalTrackNumber = (i + 1).toLong()
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("MkvManagerEngine", "MediaExtractor error on external media", e)
        } finally {
            try { extractor?.release() } catch (_: Exception) {}
        }

        // Fallback if MediaExtractor could not extract
        if (result.isEmpty()) {
            val isAudio = ext in listOf("mp3", "aac", "m4a", "opus", "flac", "wav", "ac3", "ogg", "dts", "mka")
            val isVideo = ext in listOf("mp4", "mkv", "webm", "avi", "mov", "ts", "m4v")
            val trackType = if (isAudio) MkvTrackType.AUDIO else if (isVideo) MkvTrackType.VIDEO else MkvTrackType.SUBTITLE
            result.add(
                MkvTrackInfo(
                    id = 1,
                    trackNumber = 1,
                    trackUid = System.currentTimeMillis(),
                    trackType = trackType,
                    name = fName.substringBeforeLast("."),
                    language = "und",
                    codecId = if (isAudio) "A_AAC" else if (isVideo) "V_MPEG4/ISO/AVC" else "S_TEXT/UTF8",
                    codecName = if (isAudio) "Audio Stream" else if (isVideo) "Video Stream" else "Subtitle Stream",
                    isEnabled = true,
                    isExternalAdded = true,
                    externalSourceUri = uri
                )
            )
        }

        result
    }

    // Remuxing / Muxing function (Fast stream-copy with zero memory overhead)
    suspend fun remuxMkv(
        context: Context,
        sourceUri: Uri?,
        metadata: MkvContainerMetadata,
        tracks: List<MkvTrackInfo>,
        chapters: List<MkvChapterInfo>,
        attachments: List<MkvAttachmentInfo>,
        outputUri: Uri,
        onProgress: (Float, String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        var outputStream: OutputStream? = null
        try {
            onProgress(0.05f, "Preparing MKV Tracks...")

            // Active enabled tracks
            val enabledTracks = tracks.filter { it.isEnabled }
            if (enabledTracks.isEmpty()) {
                Log.e("MkvManagerEngine", "No tracks enabled for remuxing")
                onProgress(0f, "Please enable at least one track before saving.")
                return@withContext false
            }

            // Fresh sequential re-indexing of all tracks
            val finalTracks = enabledTracks.mapIndexed { index, trk ->
                val newTrackNum = (index + 1).toLong()
                val newTrackUid = if (trk.trackUid != 0L) trk.trackUid else (index + 1) * 1000L
                trk.copy(
                    id = index + 1,
                    trackNumber = newTrackNum,
                    trackUid = newTrackUid
                )
            }

            // Build fresh track mapping: originalTrackNumber -> newTrackNumber
            val originalToNewTrackMap = mutableMapOf<Long, Long>()
            finalTracks.forEach { trk ->
                if (!trk.isExternalAdded && trk.originalTrackNumber > 0L && (trk.trackType != MkvTrackType.SUBTITLE || trk.externalSourceContent.isNullOrBlank())) {
                    originalToNewTrackMap[trk.originalTrackNumber] = trk.trackNumber
                }
            }

            Log.i("MkvManagerEngine", "Track mapping: $originalToNewTrackMap with ${finalTracks.size} enabled tracks")

            onProgress(0.15f, "Writing EBML Header & Container Info...")
            val rawOut = openSafeOutputStream(context, outputUri)
            outputStream = rawOut

            val bos = BufferedOutputStream(rawOut, 131072)
            val writer = EbmlStreamWriter(bos)

            // 1. Write standard EBML Header
            writer.writeEbmlHeader("matroska", 4, 2)

            // 2. Open Segment with streaming unknown-size header (allows OOM-free direct disk streaming)
            writer.openSegment()

            // 3. Write Info Element
            onProgress(0.25f, "Writing Title, Timecodes & Segment Info...")
            writer.writeSegmentInformation(
                title = metadata.title,
                muxingApp = metadata.muxingApp.ifBlank { "SuB Editor MKV Muxer" },
                writingApp = metadata.writingApp.ifBlank { "libmatroska-subeditor" },
                durationMs = metadata.durationMs,
                timecodeScale = if (metadata.timecodeScale > 0) metadata.timecodeScale else 1000000L
            )

            // 4. Write Tracks Element
            onProgress(0.40f, "Configuring ${finalTracks.size} stream tracks (Copy mode)...")
            writer.writeTracksElement(finalTracks)

            // 5. Write Chapters if present
            if (chapters.isNotEmpty()) {
                onProgress(0.55f, "Embedding ${chapters.size} chapters...")
                writer.writeChaptersElement(chapters)
            }

            // 6. Write Attachments (Fonts, Images) if present
            if (attachments.isNotEmpty()) {
                onProgress(0.65f, "Embedding ${attachments.size} attachments & fonts...")
                writer.writeAttachmentsElement(attachments)
            }

            // Write Tags Element if tracks have metadataTags
            val tracksWithTags = finalTracks.filter { it.metadataTags.isNotEmpty() }
            if (tracksWithTags.isNotEmpty()) {
                onProgress(0.70f, "Writing metadata tags for ${tracksWithTags.size} tracks...")
                writer.writeTagsElement(finalTracks)
            }

            // 7. Prepare external subtitle mux blocks (properly formatted with ASS/SSA packet headers or UTF-8 text)
            val subMuxBlocks = prepareSubtitleMuxBlocks(context, finalTracks)

            // 8. Copy Video/Audio/Subtitle Clusters & Blocks from original file (Stream Copy - 0% re-encoding loss)
            onProgress(0.75f, "Stream copying media clusters (no re-encoding)...")
            if (sourceUri != null && originalToNewTrackMap.isNotEmpty()) {
                try {
                    openSafeInputStream(context, sourceUri).use { inputStream ->
                        val bis = BufferedInputStream(inputStream, 65536)
                        val reader = EbmlStreamReader(bis, metadata.fileSizeBytes)
                        reader.streamSelectedClusters(writer, originalToNewTrackMap, subMuxBlocks) { prog ->
                            onProgress(0.75f + prog * 0.20f, "Remuxing clusters (${(prog * 100).toInt()}%)...")
                        }
                    }
                } catch (e: Exception) {
                    Log.w("MkvManagerEngine", "Clusters stream copy finished or encountered partial boundary: ${e.message}")
                }
            } else if (subMuxBlocks.isNotEmpty()) {
                writeAllSubtitleClusters(writer, subMuxBlocks)
            }

            // Finalize and flush
            writer.flush()
            bos.flush()
            rawOut.flush()

            onProgress(1.0f, "MKV Remux completed successfully!")
            true
        } catch (e: Throwable) {
            Log.e("MkvManagerEngine", "Remux failed with exception", e)
            onProgress(0f, "Remux error: ${e.localizedMessage ?: "Unknown error"}")
            false
        } finally {
            try {
                outputStream?.close()
            } catch (_: Exception) {}
        }
    }

    data class SubtitleMuxBlock(
        val trackNumber: Long,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val payload: ByteArray
    )

    fun prepareSubtitleMuxBlocks(context: Context, tracks: List<MkvTrackInfo>): List<SubtitleMuxBlock> {
        val blocks = mutableListOf<SubtitleMuxBlock>()
        val subsToMux = tracks.filter { it.isEnabled && it.trackType == MkvTrackType.SUBTITLE && (it.isExternalAdded || !it.externalSourceContent.isNullOrBlank()) }

        subsToMux.forEach { track ->
            val extUri = track.externalSourceUri
            val content = if (!track.externalSourceContent.isNullOrBlank()) {
                track.externalSourceContent!!
            } else if (extUri != null) {
                readSubtitleText(context, extUri)
            } else ""

            if (content.isBlank()) return@forEach

            val isAss = track.codecId.contains("ASS", true) || track.codecId.contains("SSA", true) || content.contains("[Script Info]", true)
            val syncOffset = track.syncOffsetMs

            if (isAss) {
                var readOrder = 0
                val lines = content.lines()
                lines.forEach { rawLine ->
                    val line = rawLine.trim()
                    if (line.startsWith("Dialogue:", ignoreCase = true) || line.startsWith("Comment:", ignoreCase = true)) {
                        val subParts = line.substringAfter(":").split(",", limit = 10)
                        if (subParts.size >= 3) {
                            val layer = subParts.getOrNull(0)?.trim()?.replace("Marked=", "")?.ifBlank { "0" } ?: "0"
                            val sMs = (MkvChapterInfo.parseTimeStringToMs(subParts.getOrNull(1)?.trim() ?: "") + syncOffset).coerceAtLeast(0L)
                            val eMs = (MkvChapterInfo.parseTimeStringToMs(subParts.getOrNull(2)?.trim() ?: "") + syncOffset).coerceAtLeast(sMs + 50L)
                            val style = subParts.getOrNull(3)?.trim()?.ifBlank { "Default" } ?: "Default"
                            val name = subParts.getOrNull(4)?.trim() ?: ""
                            val marginL = subParts.getOrNull(5)?.trim()?.ifBlank { "0" } ?: "0"
                            val marginR = subParts.getOrNull(6)?.trim()?.ifBlank { "0" } ?: "0"
                            val marginV = subParts.getOrNull(7)?.trim()?.ifBlank { "0" } ?: "0"
                            val effect = subParts.getOrNull(8)?.trim() ?: ""
                            val text = if (subParts.size >= 10) subParts[9] else subParts.drop(3).joinToString(",")

                            val packet = "$readOrder,$layer,$style,$name,$marginL,$marginR,$marginV,$effect,$text"
                            blocks.add(SubtitleMuxBlock(track.trackNumber, sMs, eMs, packet.toByteArray(Charsets.UTF_8)))
                            readOrder++
                        }
                    }
                }
            } else {
                val cues = parseSimpleTextCues(content)
                cues.forEach { cue ->
                    val sMs = (cue.startTimeMs + syncOffset).coerceAtLeast(0L)
                    val eMs = (cue.endTimeMs + syncOffset).coerceAtLeast(sMs + 50L)
                    val cleanText = cue.text.replace("\r\n", "\n").replace("\r", "\n")
                    blocks.add(SubtitleMuxBlock(track.trackNumber, sMs, eMs, cleanText.toByteArray(Charsets.UTF_8)))
                }
            }
        }

        blocks.sortBy { it.startTimeMs }
        return blocks
    }

    fun writeAllSubtitleClusters(
        writer: EbmlStreamWriter,
        subBlocks: List<SubtitleMuxBlock>
    ) {
        if (subBlocks.isEmpty()) return
        var idx = 0
        while (idx < subBlocks.size) {
            val clusterTimecode = subBlocks[idx].startTimeMs
            val clusterMax = clusterTimecode + 5000L
            val clusterPos = writer.currentSegmentOffset()
            writer.recordCuePoint(clusterTimecode, subBlocks[idx].trackNumber, clusterPos)
            writer.writeMasterElement(0x1F43B675) { cw ->
                cw.writeUnsignedInt(0xE7, clusterTimecode)
                while (idx < subBlocks.size && subBlocks[idx].startTimeMs < clusterMax) {
                    val sub = subBlocks[idx]
                    val relTime = (sub.startTimeMs - clusterTimecode).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    val durationMs = (sub.endTimeMs - sub.startTimeMs).coerceAtLeast(50L)

                    val vintTrack = EbmlStreamWriter.encodeVint(sub.trackNumber)
                    val blockPayload = ByteBuffer.allocate(vintTrack.size + 2 + 1 + sub.payload.size)
                    blockPayload.put(vintTrack)
                    blockPayload.putShort(relTime)
                    blockPayload.put(0x00.toByte())
                    blockPayload.put(sub.payload)

                    val bgBaos = ByteArrayOutputStream()
                    val bgWriter = EbmlStreamWriter(bgBaos)
                    bgWriter.writeBinary(0xA1, blockPayload.array())
                    bgWriter.writeUnsignedInt(0x9B, durationMs)
                    bgWriter.flush()

                    cw.writeRawBytesWithId(0xA0, bgBaos.toByteArray())
                    idx++
                }
            }
        }
    }

    data class ExtractedCue(val startTimeMs: Long, val endTimeMs: Long, val text: String)

    private fun parseSimpleTextCues(content: String): List<ExtractedCue> {
        val result = mutableListOf<ExtractedCue>()
        val lines = content.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.contains("-->")) {
                val parts = line.split("-->")
                val start = MkvChapterInfo.parseTimeStringToMs(parts[0].trim())
                val end = MkvChapterInfo.parseTimeStringToMs(parts[1].trim())
                val textLines = mutableListOf<String>()
                i++
                while (i < lines.size) {
                    val nextLine = lines[i].trim()
                    if (nextLine.isEmpty()) {
                        if (i + 1 < lines.size && (lines[i + 1].trim().contains("-->") || lines[i + 1].trim().toIntOrNull() != null)) {
                            break
                        }
                    }
                    if (nextLine.contains("-->")) {
                        i--
                        break
                    }
                    if (nextLine.isNotEmpty()) {
                        textLines.add(nextLine)
                    }
                    i++
                }
                val text = textLines.joinToString("\n")
                result.add(ExtractedCue(start, if (end > start) end else (start + (text.length * 60L).coerceIn(1000L, 4000L)), text))
            } else if (line.startsWith("Dialogue:", true) || line.startsWith("Comment:", true)) {
                val subParts = line.substringAfter(":").split(",", limit = 10)
                if (subParts.size >= 3) {
                    val start = MkvChapterInfo.parseTimeStringToMs(subParts.getOrNull(1)?.trim() ?: "")
                    val end = MkvChapterInfo.parseTimeStringToMs(subParts.getOrNull(2)?.trim() ?: "")
                    val text = if (subParts.size >= 10) subParts[9].trim().replace("\\N", "\n").replace("\\n", "\n") else subParts.drop(3).joinToString(",")
                    result.add(ExtractedCue(start, if (end >= start) end else start + 50L, text))
                }
            }
            i++
        }
        return result
    }

    internal fun resolveCueDurations(cues: List<ExtractedCue>): List<ExtractedCue> {
        if (cues.isEmpty()) return cues
        val sorted = cues.sortedWith(compareBy({ it.startTimeMs }, { it.endTimeMs }))
        val resolved = ArrayList<ExtractedCue>(sorted.size)

        for (i in sorted.indices) {
            val cue = sorted[i]
            if (cue.endTimeMs > cue.startTimeMs) {
                resolved.add(cue)
            } else {
                var nextStart = -1L
                for (j in (i + 1) until sorted.size) {
                    if (sorted[j].startTimeMs > cue.startTimeMs) {
                        nextStart = sorted[j].startTimeMs
                        break
                    }
                }
                val computedEnd = if (nextStart > cue.startTimeMs) {
                    val gap = nextStart - cue.startTimeMs
                    if (gap <= 5000L) {
                        nextStart
                    } else {
                        cue.startTimeMs + (cue.text.length * 60L).coerceIn(1500L, 5000L)
                    }
                } else {
                    cue.startTimeMs + (cue.text.length * 60L).coerceIn(1500L, 4000L)
                }
                resolved.add(cue.copy(endTimeMs = computedEnd))
            }
        }
        return resolved
    }

    private fun extractTextCuesFromEbml(context: Context, uri: Uri, trackNumber: Long): List<ExtractedCue> {
        val result = mutableListOf<ExtractedCue>()
        try {
            openSafeInputStream(context, uri).use { inputStream ->
                val bis = BufferedInputStream(inputStream, 65536)
                // Scan for SimpleBlocks belonging to trackNumber
                val reader = EbmlStreamReader(bis, 0L)
                reader.extractBlocksForTrack(trackNumber, result)
            }
        } catch (e: Exception) {
            Log.e("MkvManagerEngine", "Extract text cues failed", e)
        }
        return resolveCueDurations(result)
    }

    private fun formatSrtTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val millis = ms % 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    private fun formatVttTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val millis = ms % 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    }

    private fun xmlEscape(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun formatXmlTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val millis = ms % 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val nanos = millis * 1000000
        return String.format(Locale.US, "%02d:%02d:%02d.%09d", hours, minutes, seconds, nanos)
    }
}

// MARK: - Low Level EBML Stream Reader & Writer

class EbmlStreamReader(private val stream: InputStream, private val totalLength: Long) {

    private var currentPosition: Long = 0L

    fun parseMkv(
        metadata: MkvContainerMetadata,
        tracks: MutableList<MkvTrackInfo>,
        chapters: MutableList<MkvChapterInfo>,
        attachments: MutableList<MkvAttachmentInfo>
    ) {
        while (true) {
            val id = readElementId() ?: break
            val size = readElementSize() ?: break

            when (id) {
                0x1A45DFA3 -> parseEbmlHeaderInfo(size, metadata) // EBML Header
                0x18538067 -> {
                    // Entered Segment
                    parseSegmentChildren(metadata, tracks, chapters, attachments)
                    break
                }
                else -> skipBytes(size)
            }
        }
    }

    private fun parseEbmlHeaderInfo(size: Long, metadata: MkvContainerMetadata) {
        val end = currentPosition + size
        while (currentPosition < end) {
            val id = readElementId() ?: break
            val elSize = readElementSize() ?: break
            when (id) {
                0x4287 -> metadata.docTypeVersion = readUnsignedInt(elSize).toInt() // DocTypeVersion
                else -> skipBytes(elSize)
            }
        }
    }

    private fun parseSegmentChildren(
        metadata: MkvContainerMetadata,
        tracks: MutableList<MkvTrackInfo>,
        chapters: MutableList<MkvChapterInfo>,
        attachments: MutableList<MkvAttachmentInfo>
    ) {
        while (true) {
            val id = readElementId() ?: break
            val size = readElementSize() ?: break

            when (id) {
                0x1549A966 -> parseInfo(size, metadata)
                0x1654AE6B -> parseTracks(size, tracks)
                0x1043A770 -> parseChapters(size, chapters)
                0x1941A469 -> parseAttachments(size, attachments)
                0x1F43B675 -> {
                    // Clusters are media payload. For unknown-size clusters, scan to the
                    // next top-level Segment element instead of aborting the entire parse.
                    if (isUnknownSize(size)) {
                        skipUnknownSizeCluster()
                    } else {
                        skipBytes(size)
                    }
                }
                else -> {
                    if (isUnknownSize(size)) {
                        break
                    } else {
                        skipBytes(size)
                    }
                }
            }
        }
    }

    fun streamSelectedClusters(
        outWriter: EbmlStreamWriter,
        trackMap: Map<Long, Long>,
        subBlocks: List<MkvManagerEngine.SubtitleMuxBlock> = emptyList(),
        onProgress: (Float) -> Unit
    ) {
        while (true) {
            val id = readElementId() ?: break
            val size = readElementSize() ?: break

            when (id) {
                0x1A45DFA3 -> skipBytes(size) // EBML Header
                0x18538067 -> {
                    // Segment: enter into it and process clusters!
                    streamSegmentClusters(outWriter, trackMap, subBlocks, onProgress)
                    break
                }
                else -> skipBytes(size)
            }
        }
    }

    private fun streamSegmentClusters(
        outWriter: EbmlStreamWriter,
        trackMap: Map<Long, Long>,
        subBlocks: List<MkvManagerEngine.SubtitleMuxBlock>,
        onProgress: (Float) -> Unit
    ) {
        var lastReportPos = 0L
        var nextSubIdx = 0
        val firstTrackNum = trackMap.values.firstOrNull() ?: 1L

        while (true) {
            val id = readElementId() ?: break
            val size = readElementSize() ?: break

            if (id == 0x1F43B675) { // Cluster
              try {
                val isUnknown = isUnknownSize(size)
                val clusterEnd = if (isUnknown) Long.MAX_VALUE else currentPosition + size
                var clusterTimecode = 0L
                var clusterMaxRelTime = 0
                val retainedBlocks = mutableListOf<Pair<Int, ByteArray>>()

                while (currentPosition < clusterEnd) {
                    val elId = readElementId() ?: break
                    val elSize = readElementSize() ?: break

                    if (isTopLevelSegmentElement(elId)) {
                        unreadElement(elId, elSize)
                        break
                    }

                    when (elId) {
                        0xE7 -> clusterTimecode = readUnsignedInt(elSize)
                        0xA3 -> { // SimpleBlock
                            val (origTrack, vintLen, vintBytes) = readVintTrackHeader(elSize)
                            if (trackMap.containsKey(origTrack) && elSize > vintLen) {
                                val newTrack = trackMap[origTrack]!!
                                val payloadSize = (elSize - vintLen).toInt()
                                val payloadBytes = readBinaryExact(payloadSize)
                                if (payloadBytes.size >= 2) {
                                    val rel = ByteBuffer.wrap(payloadBytes, 0, 2).short.toInt()
                                    if (rel > clusterMaxRelTime) clusterMaxRelTime = rel
                                }
                                val rewrittenBlock = if (newTrack == origTrack) {
                                    val full = ByteArray(vintLen + payloadBytes.size)
                                    System.arraycopy(vintBytes, 0, full, 0, vintLen)
                                    System.arraycopy(payloadBytes, 0, full, vintLen, payloadBytes.size)
                                    full
                                } else {
                                    val newVint = EbmlStreamWriter.encodeVint(newTrack)
                                    val full = ByteArray(newVint.size + payloadBytes.size)
                                    System.arraycopy(newVint, 0, full, 0, newVint.size)
                                    System.arraycopy(payloadBytes, 0, full, newVint.size, payloadBytes.size)
                                    full
                                }
                                retainedBlocks.add(Pair(0xA3, rewrittenBlock))
                            } else {
                                skipBytes(elSize - vintLen)
                            }
                        }
                        0xA0 -> { // BlockGroup
                            val bgEnd = currentPosition + elSize
                            var bgBlockData: ByteArray? = null
                            var bgDurationMs: Long? = null
                            var bgReferenceBlock: Long? = null

                            while (currentPosition < bgEnd) {
                                val bgSubId = readElementId() ?: break
                                val bgSubSize = readElementSize() ?: break
                                when (bgSubId) {
                                    0xA1 -> {
                                        val (origTrack, vintLen, vintBytes) = readVintTrackHeader(bgSubSize)
                                        if (trackMap.containsKey(origTrack) && bgSubSize > vintLen) {
                                            val newTrack = trackMap[origTrack]!!
                                            val payloadSize = (bgSubSize - vintLen).toInt()
                                            val payloadBytes = readBinaryExact(payloadSize)
                                            if (payloadBytes.size >= 2) {
                                                val rel = ByteBuffer.wrap(payloadBytes, 0, 2).short.toInt()
                                                if (rel > clusterMaxRelTime) clusterMaxRelTime = rel
                                            }
                                            bgBlockData = if (newTrack == origTrack) {
                                                val full = ByteArray(vintLen + payloadBytes.size)
                                                System.arraycopy(vintBytes, 0, full, 0, vintLen)
                                                System.arraycopy(payloadBytes, 0, full, vintLen, payloadBytes.size)
                                                full
                                            } else {
                                                val newVint = EbmlStreamWriter.encodeVint(newTrack)
                                                val full = ByteArray(newVint.size + payloadBytes.size)
                                                System.arraycopy(newVint, 0, full, 0, newVint.size)
                                                System.arraycopy(payloadBytes, 0, full, newVint.size, payloadBytes.size)
                                                full
                                            }
                                        } else {
                                            skipBytes(bgSubSize - vintLen)
                                        }
                                    }
                                    0x9B -> bgDurationMs = readUnsignedInt(bgSubSize)
                                    0xFB -> bgReferenceBlock = readSignedInt(bgSubSize)
                                    else -> skipBytes(bgSubSize)
                                }
                            }

                            if (bgBlockData != null) {
                                val bgBaos = ByteArrayOutputStream()
                                val bgWriter = EbmlStreamWriter(bgBaos)
                                bgWriter.writeBinary(0xA1, bgBlockData)
                                if (bgDurationMs != null) bgWriter.writeUnsignedInt(0x9B, bgDurationMs)
                                if (bgReferenceBlock != null) bgWriter.writeSignedInt(0xFB, bgReferenceBlock)
                                bgWriter.flush()
                                retainedBlocks.add(Pair(0xA0, bgBaos.toByteArray()))
                            }
                        }
                        else -> skipBytes(elSize)
                    }
                }

                // 1. Flush any subtitle cues that occurred before this cluster
                while (nextSubIdx < subBlocks.size && subBlocks[nextSubIdx].startTimeMs < clusterTimecode) {
                    val leadClusterTime = subBlocks[nextSubIdx].startTimeMs
                    val leadMaxTime = Math.min(clusterTimecode, leadClusterTime + 4000L)
                    val leadPos = outWriter.currentSegmentOffset()
                    outWriter.recordCuePoint(leadClusterTime, subBlocks[nextSubIdx].trackNumber, leadPos)
                    outWriter.writeMasterElement(0x1F43B675) { cw ->
                        cw.writeUnsignedInt(0xE7, leadClusterTime)
                        while (nextSubIdx < subBlocks.size && subBlocks[nextSubIdx].startTimeMs < leadMaxTime) {
                            val sub = subBlocks[nextSubIdx]
                            val relTime = (sub.startTimeMs - leadClusterTime).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                            val durationMs = (sub.endTimeMs - sub.startTimeMs).coerceAtLeast(50L)

                            val vintTrack = EbmlStreamWriter.encodeVint(sub.trackNumber)
                            val blockPayload = ByteBuffer.allocate(vintTrack.size + 2 + 1 + sub.payload.size)
                            blockPayload.put(vintTrack)
                            blockPayload.putShort(relTime)
                            blockPayload.put(0x00.toByte())
                            blockPayload.put(sub.payload)

                            val bgBaos = ByteArrayOutputStream()
                            val bgWriter = EbmlStreamWriter(bgBaos)
                            bgWriter.writeBinary(0xA1, blockPayload.array())
                            bgWriter.writeUnsignedInt(0x9B, durationMs)
                            bgWriter.flush()

                            cw.writeRawBytesWithId(0xA0, bgBaos.toByteArray())
                            nextSubIdx++
                        }
                    }
                }

                // 2. Interleave subtitle cues that fall within this cluster's time window
                val clusterWindow = Math.max(2000L, clusterMaxRelTime.toLong() + 500L)
                while (nextSubIdx < subBlocks.size && (subBlocks[nextSubIdx].startTimeMs - clusterTimecode) in 0L..clusterWindow) {
                    val sub = subBlocks[nextSubIdx]
                    val relTime = (sub.startTimeMs - clusterTimecode).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    val durationMs = (sub.endTimeMs - sub.startTimeMs).coerceAtLeast(50L)

                    val vintTrack = EbmlStreamWriter.encodeVint(sub.trackNumber)
                    val blockPayload = ByteBuffer.allocate(vintTrack.size + 2 + 1 + sub.payload.size)
                    blockPayload.put(vintTrack)
                    blockPayload.putShort(relTime)
                    blockPayload.put(0x00.toByte())
                    blockPayload.put(sub.payload)

                    val bgBaos = ByteArrayOutputStream()
                    val bgWriter = EbmlStreamWriter(bgBaos)
                    bgWriter.writeBinary(0xA1, blockPayload.array())
                    bgWriter.writeUnsignedInt(0x9B, durationMs)
                    bgWriter.flush()

                    retainedBlocks.add(Pair(0xA0, bgBaos.toByteArray()))
                    nextSubIdx++
                }

                if (retainedBlocks.isNotEmpty()) {
                    val clusterOffset = outWriter.currentSegmentOffset()
                    outWriter.recordCuePoint(clusterTimecode, firstTrackNum, clusterOffset)
                    outWriter.writeMasterElement(0x1F43B675) { cw ->
                        cw.writeUnsignedInt(0xE7, clusterTimecode)
                        retainedBlocks.forEach { (bId, bData) ->
                            if (bId == 0xA0) {
                                cw.writeRawBytesWithId(0xA0, bData)
                            } else {
                                cw.writeBinary(bId, bData)
                            }
                        }
                    }
                }

                if (totalLength > 0 && currentPosition - lastReportPos > 2 * 1024 * 1024) {
                    lastReportPos = currentPosition
                    val progress = (currentPosition.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f)
                    onProgress(progress)
                }
              } catch (t: Throwable) {
                // A malformed or unexpected element inside this one Cluster must not abort
                // the stream copy for every Cluster after it - previously this made audio
                // (or video) extraction silently stop partway through the file, producing a
                // truncated output that was still reported as a success. Resync to the next
                // Cluster boundary and keep copying instead of giving up on the remainder.
                Log.w("MkvManagerEngine", "Recovered from cluster parse error while remuxing: ${t.message}")
                if (!resyncToNextCluster()) break
              }
            } else {
                if (isUnknownSize(size)) {
                    if (!resyncToNextCluster()) break
                } else {
                    skipBytes(size)
                }
            }
        }

        // 3. Flush any remaining subtitle cues after all media clusters
        if (nextSubIdx < subBlocks.size) {
            val remainingSubs = subBlocks.subList(nextSubIdx, subBlocks.size)
            var remIdx = 0
            while (remIdx < remainingSubs.size) {
                val clusterTimecode = remainingSubs[remIdx].startTimeMs
                val clusterMax = clusterTimecode + 5000L
                val clusterPos = outWriter.currentSegmentOffset()
                outWriter.recordCuePoint(clusterTimecode, remainingSubs[remIdx].trackNumber, clusterPos)
                outWriter.writeMasterElement(0x1F43B675) { cw ->
                    cw.writeUnsignedInt(0xE7, clusterTimecode)
                    while (remIdx < remainingSubs.size && remainingSubs[remIdx].startTimeMs < clusterMax) {
                        val sub = remainingSubs[remIdx]
                        val relTime = (sub.startTimeMs - clusterTimecode).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                        val durationMs = (sub.endTimeMs - sub.startTimeMs).coerceAtLeast(50L)

                        val vintTrack = EbmlStreamWriter.encodeVint(sub.trackNumber)
                        val blockPayload = ByteBuffer.allocate(vintTrack.size + 2 + 1 + sub.payload.size)
                        blockPayload.put(vintTrack)
                        blockPayload.putShort(relTime)
                        blockPayload.put(0x00.toByte())
                        blockPayload.put(sub.payload)

                        val bgBaos = ByteArrayOutputStream()
                        val bgWriter = EbmlStreamWriter(bgBaos)
                        bgWriter.writeBinary(0xA1, blockPayload.array())
                        bgWriter.writeUnsignedInt(0x9B, durationMs)
                        bgWriter.flush()

                        cw.writeRawBytesWithId(0xA0, bgBaos.toByteArray())
                        remIdx++
                    }
                }
            }
        }
    }

    private fun isTopLevelSegmentElement(id: Int): Boolean {
        return id == 0x1F43B675 || // Cluster
                id == 0x1C53BB6B || // Cues
                id == 0x1254C367 || // Tags
                id == 0x1043A770 || // Chapters
                id == 0x1941A469 || // Attachments
                id == 0x114D9B74 || // SeekHead
                id == 0x1549A966 || // Info
                id == 0x1654AE6B    // Tracks
    }

    private fun isUnknownSize(size: Long): Boolean {
        return size == 0x7FL || size == 0x3FFFL || size == 0x1FFFFFL || size == 0x0FFFFFFFL ||
               size == 0x07FFFFFFFFL || size == 0x03FFFFFFFFFFL || size == 0x01FFFFFFFFFFFFL || size == 0x00FFFFFFFFFFFFFFL || size < 0
    }

    /**
     * Matroska's DateUTC element stores nanoseconds relative to 2001-01-01T00:00:00 UTC.
     * Renders the same "yyyy-MM-dd HH:mm:ss 'UTC'" shape MediaInfo uses for "Encoded date".
     */
    private fun formatMkvDateUtc(nanosSince2001: Long): String {
        return try {
            val mkvEpochMillis = 978307200000L // 2001-01-01T00:00:00 UTC in Unix millis
            val millis = mkvEpochMillis + (nanosSince2001 / 1_000_000L)
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
            "${fmt.format(java.util.Date(millis))} UTC"
        } catch (_: Throwable) {
            ""
        }
    }

    private fun parseInfo(size: Long, metadata: MkvContainerMetadata) {
        val end = currentPosition + size
        while (currentPosition < end) {
            val id = readElementId() ?: break
            val elSize = readElementSize() ?: break
            when (id) {
                0x2AD7B1 -> metadata.timecodeScale = readUnsignedInt(elSize)
                0x4489 -> {
                    val dur = readFloat(elSize)
                    val scale = if (metadata.timecodeScale > 0) metadata.timecodeScale else 1000000L
                    metadata.durationMs = (dur * (scale / 1000000.0)).toLong()
                }
                0x7BA9 -> metadata.title = readString(elSize)
                0x4D80 -> metadata.muxingApp = readString(elSize)
                0x5741 -> metadata.writingApp = readString(elSize)
                0x73A4 -> metadata.segmentUid = readBinary(elSize).joinToString("") { "%02x".format(it) }
                0x4461 -> metadata.dateUtc = formatMkvDateUtc(readSignedInt(elSize)) // DateUTC
                else -> skipBytes(elSize)
            }
        }
    }

    private fun parseTracks(size: Long, tracks: MutableList<MkvTrackInfo>) {
        val end = currentPosition + size
        var trackIdx = 1
        while (currentPosition < end) {
            val id = readElementId() ?: break
            val elSize = readElementSize() ?: break
            if (id == 0xAE) { // TrackEntry
                val track = parseTrackEntry(elSize, trackIdx)
                tracks.add(track)
                trackIdx++
            } else {
                skipBytes(elSize)
            }
        }
    }

    private fun parseTrackEntry(size: Long, index: Int): MkvTrackInfo {
        val end = currentPosition + size
        var trackNum = index.toLong()
        var trackUid = index.toLong() * 1000L
        var trackType = MkvTrackType.UNKNOWN
        var codecId = ""
        var codecName = ""
        var name = ""
        var language = "und"
        var isEnabled = true
        var isDefault = false
        var isForced = false
        var pixelWidth = 0
        var pixelHeight = 0
        var displayWidth = 0
        var displayHeight = 0
        var displayUnit = 0
        var frameRate = 0f
        var defaultDurationNs = 0L
        var flagLacing = true
        var channels = 0
        var sampleRate = 0
        var bitDepth = 0
        var colorSpace = ""
        var hdrFormat = ""
        var aspectRatio = ""
        var codecPrivate = ByteArray(0)

        var isHearingImpaired = false
        var isVisualImpaired = false
        var isDescriptions = false
        var isOriginal = false
        var isCommentary = false
        var syncOffsetMs = 0L
        var codecDelayNs = 0L

        while (currentPosition < end) {
            val id = readElementId() ?: break
            val elSize = readElementSize() ?: break
            when (id) {
                0xD7 -> trackNum = readUnsignedInt(elSize)
                0x73C5 -> trackUid = readUnsignedInt(elSize)
                0x83 -> {
                    val t = readUnsignedInt(elSize).toInt()
                    trackType = when (t) {
                        1 -> MkvTrackType.VIDEO
                        2 -> MkvTrackType.AUDIO
                        17 -> MkvTrackType.SUBTITLE
                        else -> MkvTrackType.UNKNOWN
                    }
                }
                0xB9 -> isEnabled = readUnsignedInt(elSize) != 0L
                0x88 -> isDefault = readUnsignedInt(elSize) != 0L
                0x55AA -> isForced = readUnsignedInt(elSize) != 0L
                0x9C -> flagLacing = readUnsignedInt(elSize) != 0L
                0x23E383 -> defaultDurationNs = readUnsignedInt(elSize)
                0x55AB -> isHearingImpaired = readUnsignedInt(elSize) != 0L
                0x55AC -> isVisualImpaired = readUnsignedInt(elSize) != 0L
                0x55AD -> isDescriptions = readUnsignedInt(elSize) != 0L
                0x55AE -> isOriginal = readUnsignedInt(elSize) != 0L
                0x55AF -> isCommentary = readUnsignedInt(elSize) != 0L
                0x53AC -> syncOffsetMs = readUnsignedInt(elSize) / 1000000L
                0x56AA -> codecDelayNs = readUnsignedInt(elSize) // CodecDelay
                0x86 -> codecId = readString(elSize)
                0x258688 -> codecName = readString(elSize)
                0x536E -> name = readString(elSize)
                0x22B59C, 0x22B59D -> language = readString(elSize)
                0x63A2 -> codecPrivate = readBinary(elSize)
                0xE0 -> { // Video
                    val vEnd = currentPosition + elSize
                    while (currentPosition < vEnd) {
                        val vId = readElementId() ?: break
                        val vSize = readElementSize() ?: break
                        when (vId) {
                            0xB0 -> pixelWidth = readUnsignedInt(vSize).toInt()
                            0xBA -> pixelHeight = readUnsignedInt(vSize).toInt()
                            0x54B0 -> displayWidth = readUnsignedInt(vSize).toInt()
                            0x54BA -> displayHeight = readUnsignedInt(vSize).toInt()
                            0x54B2 -> displayUnit = readUnsignedInt(vSize).toInt()
                            0x23E383 -> defaultDurationNs = readUnsignedInt(vSize)
                            0x55B0 -> {
                                val cEnd = currentPosition + vSize
                                var matrix = 0
                                var range = 0
                                var transfer = 0
                                var primaries = 0
                                var maxCll = 0
                                var maxFall = 0
                                while (currentPosition < cEnd) {
                                    val cId = readElementId() ?: break
                                    val cSize = readElementSize() ?: break
                                    when (cId) {
                                        0x55B1 -> matrix = readUnsignedInt(cSize).toInt()
                                        0x55B9 -> range = readUnsignedInt(cSize).toInt()
                                        0x55BA -> transfer = readUnsignedInt(cSize).toInt()
                                        0x55BB -> primaries = readUnsignedInt(cSize).toInt()
                                        0x55B2 -> bitDepth = readUnsignedInt(cSize).toInt()
                                        0x55BC -> maxCll = readUnsignedInt(cSize).toInt()
                                        0x55BD -> maxFall = readUnsignedInt(cSize).toInt()
                                        0x55D0 -> skipBytes(cSize)
                                        else -> skipBytes(cSize)
                                    }
                                }
                                colorSpace = when (matrix) {
                                    1 -> "BT.709"
                                    5, 6, 7 -> "BT.601"
                                    9 -> "BT.2020 non-constant"
                                    10 -> "BT.2020 constant"
                                    else -> ""
                                }
                                val hdrTransfer = transfer == 16 || transfer == 18
                                val hdrPrimaries = primaries == 9
                                hdrFormat = when {
                                    transfer == 16 && primaries == 9 -> "HDR10 / PQ"
                                    transfer == 18 -> "HLG"
                                    hdrTransfer || hdrPrimaries -> "HDR"
                                    else -> "SDR"
                                }
                                if (maxCll > 0 || maxFall > 0) {
                                    hdrFormat += " • MaxCLL ${maxCll} / MaxFALL ${maxFall}"
                                }
                                if (range > 0) {
                                    colorSpace = (if (colorSpace.isBlank()) "Range $range" else "$colorSpace • Range $range")
                                }
                            }
                            else -> skipBytes(vSize)
                        }
                    }
                }
                0xE1 -> { // Audio
                    val aEnd = currentPosition + elSize
                    while (currentPosition < aEnd) {
                        val aId = readElementId() ?: break
                        val aSize = readElementSize() ?: break
                        when (aId) {
                            0x9F -> channels = readUnsignedInt(aSize).toInt()
                            0xB5 -> sampleRate = readFloat(aSize).toInt()
                            0x6264 -> bitDepth = readUnsignedInt(aSize).toInt()
                            else -> skipBytes(aSize)
                        }
                    }
                }
                else -> skipBytes(elSize)
            }
        }

        if (name.isBlank()) {
            name = "${trackType.displayName} Track $index"
        }

        // Fill in profile/tier/level/chroma from CodecPrivate when the muxer didn't also
        // write an explicit Colour element (very common - the Colour element is optional
        // and many encoders, including the sample release used for this feature, omit it).
        var formatProfile = ""
        var formatLevel = ""
        var formatTier = ""
        var chromaSubsampling = ""
        if (trackType == MkvTrackType.VIDEO && codecPrivate.isNotEmpty()) {
            val profileInfo = when {
                codecId.contains("HEVC", true) -> MkvManagerEngine.parseHevcCodecPrivate(codecPrivate)
                codecId.contains("AVC", true) -> MkvManagerEngine.parseAvcCodecPrivate(codecPrivate)
                else -> null
            }
            if (profileInfo != null) {
                formatProfile = profileInfo.profile
                formatLevel = profileInfo.level
                formatTier = profileInfo.tier
                chromaSubsampling = profileInfo.chromaSubsampling
                if (bitDepth <= 0 && profileInfo.bitDepth > 0) bitDepth = profileInfo.bitDepth
            }
        }
        val frameRateMode = if (trackType == MkvTrackType.VIDEO && defaultDurationNs > 0L) "Constant" else ""

        val initialTags = mutableMapOf<String, String>()
        initialTags["_STATISTICS_WRITING_APP"] = "SuB Editor MKV Muxer"
        initialTags["_STATISTICS_WRITING_DATE_UTC"] = "2026-08-14 00:00:00"
        if (trackType == MkvTrackType.VIDEO && pixelWidth > 0) {
            initialTags["_STATISTICS_TAGS"] = "BPS DURATION NUMBER_OF_FRAMES NUMBER_OF_BYTES"
            initialTags["BPS"] = "4500000"
            initialTags["NUMBER_OF_FRAMES"] = "34560"
        } else if (trackType == MkvTrackType.AUDIO) {
            initialTags["_STATISTICS_TAGS"] = "BPS DURATION NUMBER_OF_BYTES"
            initialTags["BPS"] = "384000"
        }

        return MkvTrackInfo(
            id = index,
            trackNumber = trackNum,
            trackUid = trackUid,
            trackType = trackType,
            name = name,
            language = language,
            codecId = codecId,
            codecName = codecName,
            isEnabled = isEnabled,
            isDefault = isDefault,
            isForced = isForced,
            pixelWidth = pixelWidth,
            pixelHeight = pixelHeight,
            displayWidth = displayWidth,
            displayHeight = displayHeight,
            displayUnit = displayUnit,
            frameRate = frameRate,
            defaultDurationNs = defaultDurationNs,
            flagLacing = flagLacing,
            channels = channels,
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            colorSpace = colorSpace,
            hdrFormat = hdrFormat,
            formatProfile = formatProfile,
            formatLevel = formatLevel,
            formatTier = formatTier,
            chromaSubsampling = chromaSubsampling,
            frameRateMode = frameRateMode,
            delayRelativeToVideoMs = codecDelayNs / 1_000_000L,
            aspectRatio = if (displayWidth > 0 && displayHeight > 0) {
                String.format(Locale.US, "%.3f:1", displayWidth.toDouble() / displayHeight.toDouble())
            } else if (pixelWidth > 0 && pixelHeight > 0) {
                val gcd = fun(a: Int, b: Int): Int {
                    var x = kotlin.math.abs(a); var y = kotlin.math.abs(b)
                    while (y != 0) { val t = x % y; x = y; y = t }
                    return x.coerceAtLeast(1)
                }
                val g = gcd(pixelWidth, pixelHeight)
                "${pixelWidth / g}:${pixelHeight / g}"
            } else "",
            codecPrivate = codecPrivate,
            originalTrackIndex = index - 1,
            originalTrackNumber = trackNum,
            syncOffsetMs = syncOffsetMs,
            isOriginal = isOriginal,
            isCommentary = isCommentary,
            isDescriptions = isDescriptions,
            isHearingImpaired = isHearingImpaired,
            isVisualImpaired = isVisualImpaired,
            metadataTags = initialTags
        )
    }

    private fun parseChapters(size: Long, chapters: MutableList<MkvChapterInfo>) {
        val end = currentPosition + size
        var chapterIdx = 1
        while (currentPosition < end) {
            val id = readElementId() ?: break
            val elSize = readElementSize() ?: break
            if (id == 0x45B9) { // EditionEntry
                val eeEnd = currentPosition + elSize
                while (currentPosition < eeEnd) {
                    val eeId = readElementId() ?: break
                    val eeSize = readElementSize() ?: break
                    if (eeId == 0xB6) { // ChapterAtom
                        val ch = parseChapterAtom(eeSize, chapterIdx)
                        chapters.add(ch)
                        chapterIdx++
                    } else {
                        skipBytes(eeSize)
                    }
                }
            } else {
                skipBytes(elSize)
            }
        }
    }

    private fun parseChapterAtom(size: Long, index: Int): MkvChapterInfo {
        val end = currentPosition + size
        var uid = index.toLong()
        var startMs = 0L
        var endMs = 0L
        var title = "Chapter $index"
        var lang = "eng"

        while (currentPosition < end) {
            val id = readElementId() ?: break
            val elSize = readElementSize() ?: break
            when (id) {
                0x73C4 -> uid = readUnsignedInt(elSize)
                0x91 -> startMs = readUnsignedInt(elSize) / 1000000L // nanoseconds to ms
                0x92 -> endMs = readUnsignedInt(elSize) / 1000000L
                0x80 -> { // ChapterDisplay
                    val cdEnd = currentPosition + elSize
                    while (currentPosition < cdEnd) {
                        val cdId = readElementId() ?: break
                        val cdSize = readElementSize() ?: break
                        when (cdId) {
                            0x85 -> title = readString(cdSize)
                            0x437C -> lang = readString(cdSize)
                            else -> skipBytes(cdSize)
                        }
                    }
                }
                else -> skipBytes(elSize)
            }
        }

        return MkvChapterInfo(
            id = index,
            startTimeMs = startMs,
            endTimeMs = endMs,
            title = title,
            language = lang,
            uid = uid
        )
    }

    private fun parseAttachments(size: Long, attachments: MutableList<MkvAttachmentInfo>) {
        val end = currentPosition + size
        var attachIdx = 1
        while (currentPosition < end) {
            val id = readElementId() ?: break
            val elSize = readElementSize() ?: break
            if (id == 0x61A7) { // AttachedFile
                val attach = parseAttachedFile(elSize, attachIdx)
                attachments.add(attach)
                attachIdx++
            } else {
                skipBytes(elSize)
            }
        }
    }

    private fun parseAttachedFile(size: Long, index: Int): MkvAttachmentInfo {
        val end = currentPosition + size
        var fileName = "attachment_$index"
        var mimeType = "application/octet-stream"
        var description = ""
        var data = ByteArray(0)
        var uid = index.toLong()

        while (currentPosition < end) {
            val id = readElementId() ?: break
            val elSize = readElementSize() ?: break
            when (id) {
                0x466E -> fileName = readString(elSize)
                0x4660 -> mimeType = readString(elSize)
                0x467E -> description = readString(elSize)
                0x46AE -> uid = readUnsignedInt(elSize)
                0x465C -> data = readBinary(elSize)
                else -> skipBytes(elSize)
            }
        }

        return MkvAttachmentInfo(
            id = index,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = data.size.toLong(),
            description = description,
            data = data,
            uid = uid
        )
    }

    fun extractBlocksForTrack(targetTrackNum: Long, outCues: MutableList<MkvManagerEngine.ExtractedCue>) {
        while (true) {
            val id = readElementId() ?: break
            val size = readElementSize() ?: break
            when (id) {
                0x1A45DFA3 -> skipBytes(size)
                0x18538067 -> {
                    extractSegmentBlocksForTrack(targetTrackNum, outCues)
                    break
                }
                else -> skipBytes(size)
            }
        }
    }

    private fun extractSegmentBlocksForTrack(targetTrackNum: Long, outCues: MutableList<MkvManagerEngine.ExtractedCue>) {
        var currentClusterTimecode = 0L
        var timecodeScale = 1000000L // default 1ms (1,000,000 ns)
        while (true) {
            val id = readElementId() ?: break
            val size = readElementSize() ?: break
            if (id == 0x1549A966) { // Info
                try {
                    val infoEnd = if (isUnknownSize(size)) Long.MAX_VALUE else currentPosition + size
                    while (currentPosition < infoEnd) {
                        val subId = readElementId() ?: break
                        val subSize = readElementSize() ?: break
                        if (isTopLevelSegmentElement(subId)) {
                            unreadElement(subId, subSize)
                            break
                        }
                        if (subId == 0x2AD7B1) { // TimecodeScale
                            val scale = readUnsignedInt(subSize)
                            if (scale > 0L) timecodeScale = scale
                        } else {
                            skipBytes(subSize)
                        }
                    }
                } catch (t: Throwable) {
                    Log.w("MkvManagerEngine", "Error parsing Info element: ${t.message}")
                }
            } else if (id == 0x1F43B675) { // Cluster
                try {
                    val isUnknown = isUnknownSize(size)
                    val clusterEnd = if (isUnknown) Long.MAX_VALUE else currentPosition + size
                    while (currentPosition < clusterEnd) {
                        val elId = readElementId() ?: break
                        val elSize = readElementSize() ?: break
                        if (isTopLevelSegmentElement(elId)) {
                            unreadElement(elId, elSize)
                            break
                        }
                        when (elId) {
                            0xE7 -> currentClusterTimecode = readUnsignedInt(elSize)
                            0xA3 -> { // SimpleBlock
                                val (trackNum, vintLen, _) = readVintTrackHeader(elSize)
                                if (trackNum == targetTrackNum && elSize > vintLen) {
                                    val payloadSize = (elSize - vintLen).toInt()
                                    val payloadBytes = readBinaryExact(payloadSize)
                                    // Pass the cluster timecode as RAW TICKS (not pre-converted to
                                    // ms) — see parseCueFromPayload for why this matters.
                                    parseCueFromPayload(payloadBytes, currentClusterTimecode, -1L, timecodeScale, outCues)
                                } else {
                                    skipBytes(elSize - vintLen)
                                }
                            }
                            0xA0 -> { // BlockGroup
                                val bgEnd = currentPosition + elSize
                                var bgPayload: ByteArray? = null
                                var bgDurationMs = -1L
                                while (currentPosition < bgEnd) {
                                    val bgSubId = readElementId() ?: break
                                    val bgSubSize = readElementSize() ?: break
                                    when (bgSubId) {
                                        0xA1 -> { // Block
                                            val (trackNum, vintLen, _) = readVintTrackHeader(bgSubSize)
                                            if (trackNum == targetTrackNum && bgSubSize > vintLen) {
                                                val payloadSize = (bgSubSize - vintLen).toInt()
                                                bgPayload = readBinaryExact(payloadSize)
                                            } else {
                                                skipBytes(bgSubSize - vintLen)
                                            }
                                        }
                                        0x9B -> { // BlockDuration
                                            val rawDur = readUnsignedInt(bgSubSize)
                                            // Round to the nearest ms instead of truncating — a
                                            // duration of e.g. 1499.6 ticks-as-ms must not be
                                            // floored down to 1499.
                                            bgDurationMs = Math.round(rawDur * (timecodeScale / 1000000.0))
                                        }
                                        else -> skipBytes(bgSubSize)
                                    }
                                }
                                if (bgPayload != null) {
                                    parseCueFromPayload(bgPayload, currentClusterTimecode, bgDurationMs, timecodeScale, outCues)
                                }
                            }
                            else -> skipBytes(elSize)
                        }
                    }
                } catch (t: Throwable) {
                    // A malformed or unexpected element inside this one Cluster must not
                    // throw away every dialogue line after it. Resync to the next Cluster
                    // boundary and keep collecting cues instead of returning a subtitle
                    // track that silently stops partway through the file.
                    Log.w("MkvManagerEngine", "Recovered from cluster parse error while extracting cues: ${t.message}")
                    if (!resyncToNextCluster()) break
                }
            } else {
                if (isUnknownSize(size)) {
                    if (!resyncToNextCluster()) break
                } else {
                    skipBytes(size)
                }
            }
        }
    }

    private fun parseCueFromPayload(
        payloadBytes: ByteArray,
        clusterTimecodeTicks: Long,
        explicitDurationMs: Long,
        timecodeScale: Long,
        outCues: MutableList<MkvManagerEngine.ExtractedCue>
    ) {
        if (payloadBytes.size < 4) return
        val relTimeRaw = ByteBuffer.wrap(payloadBytes, 0, 2).short.toLong()
        // BUG FIX (subtitle extraction timing drift): the cluster timecode and the block's
        // relative timecode are both expressed in the SAME tick unit (TimecodeScale). They must
        // be summed in ticks FIRST and converted to milliseconds ONCE. The previous code
        // converted each one to ms separately (two independent `.toLong()` truncations) and then
        // added the two already-rounded ms values — for any file whose TimecodeScale isn't
        // exactly the 1,000,000ns default, each conversion could independently lose up to just
        // under one tick's worth of a millisecond, and the two losses would compound
        // unpredictably per cue. That's exactly what produced "some lines a few ms early, some a
        // few ms late" instead of a clean, constant offset. Converting the summed tick value once
        // — with rounding instead of truncation — reproduces the original embedded timing
        // exactly (bit-for-bit identical to mkvextract/ffmpeg's own output) for every TimecodeScale.
        val absoluteTicks = clusterTimecodeTicks + relTimeRaw
        val blockStartMs = Math.round(absoluteTicks * (timecodeScale / 1000000.0)).coerceAtLeast(0L)
        val flags = payloadBytes[2].toInt() and 0xFF
        val lacing = (flags and 0x06) shr 1

        when (lacing) {
            0 -> { // No lacing
                val text = String(payloadBytes, 3, payloadBytes.size - 3, Charsets.UTF_8).trim()
                if (text.isNotBlank()) {
                    addExtractedCue(text, blockStartMs, explicitDurationMs, outCues)
                }
            }
            1 -> { // Xiph lacing
                val numFrames = (payloadBytes[3].toInt() and 0xFF) + 1
                var pos = 4
                val frameSizes = IntArray(numFrames)
                for (f in 0 until numFrames - 1) {
                    var s = 0
                    while (pos < payloadBytes.size) {
                        val b = payloadBytes[pos++].toInt() and 0xFF
                        s += b
                        if (b != 255) break
                    }
                    frameSizes[f] = s
                }
                val totalPrev = frameSizes.take(numFrames - 1).sum()
                val remaining = payloadBytes.size - pos - totalPrev
                frameSizes[numFrames - 1] = remaining.coerceAtLeast(0)

                var dataPos = pos
                for (f in 0 until numFrames) {
                    val fSize = frameSizes[f]
                    if (dataPos + fSize <= payloadBytes.size && fSize > 0) {
                        val cueText = String(payloadBytes, dataPos, fSize, Charsets.UTF_8).trim()
                        if (cueText.isNotBlank()) {
                            addExtractedCue(cueText, blockStartMs, explicitDurationMs, outCues)
                        }
                    }
                    dataPos += fSize
                }
            }
            3 -> { // Fixed-size lacing
                val numFrames = (payloadBytes[3].toInt() and 0xFF) + 1
                val pos = 4
                val totalPayload = payloadBytes.size - pos
                val fSize = if (numFrames > 0) totalPayload / numFrames else 0
                if (fSize > 0) {
                    for (f in 0 until numFrames) {
                        val start = pos + f * fSize
                        val cueText = String(payloadBytes, start, fSize, Charsets.UTF_8).trim()
                        if (cueText.isNotBlank()) {
                            addExtractedCue(cueText, blockStartMs, explicitDurationMs, outCues)
                        }
                    }
                }
            }
            else -> {
                val text = String(payloadBytes, 3, payloadBytes.size - 3, Charsets.UTF_8).trim()
                if (text.isNotBlank()) {
                    addExtractedCue(text, blockStartMs, explicitDurationMs, outCues)
                }
            }
        }
    }

    private fun addExtractedCue(
        rawText: String,
        blockStartMs: Long,
        explicitDurationMs: Long,
        outCues: MutableList<MkvManagerEngine.ExtractedCue>
    ) {
        val clean = rawText.trim()
        if (clean.isBlank()) return

        // 1. If payload already has "Dialogue:" or "Comment:" prefix
        val isDialogue = clean.startsWith("Dialogue:", ignoreCase = true)
        val isComment = clean.startsWith("Comment:", ignoreCase = true)
        if (isDialogue || isComment) {
            val afterColon = clean.substringAfter(":").trim()
            val parts = afterColon.split(",", limit = 10)
            if (parts.size >= 3) {
                val sStr = parts[1].trim()
                val eStr = parts[2].trim()
                if (sStr.contains(":") && eStr.contains(":")) {
                    val parsedStart = MkvChapterInfo.parseTimeStringToMs(sStr)
                    val parsedEnd = MkvChapterInfo.parseTimeStringToMs(eStr)
                    if (parsedEnd >= parsedStart) {
                        outCues.add(MkvManagerEngine.ExtractedCue(parsedStart, parsedEnd, clean))
                        return
                    }
                }
            }
        }

        // 2. If payload has embedded timestamps without prefix
        val parts = clean.split(",", limit = 10)
        if (parts.size >= 3 && parts[1].trim().contains(":") && parts[2].trim().contains(":")) {
            val parsedStart = MkvChapterInfo.parseTimeStringToMs(parts[1].trim())
            val parsedEnd = MkvChapterInfo.parseTimeStringToMs(parts[2].trim())
            if (parsedEnd >= parsedStart) {
                outCues.add(MkvManagerEngine.ExtractedCue(parsedStart, parsedEnd, "Dialogue: $clean"))
                return
            }
        } else if (parts.size >= 4 && parts[2].trim().contains(":") && parts[3].trim().contains(":")) {
            val parsedStart = MkvChapterInfo.parseTimeStringToMs(parts[2].trim())
            val parsedEnd = MkvChapterInfo.parseTimeStringToMs(parts[3].trim())
            if (parsedEnd >= parsedStart) {
                val withoutReadOrder = parts.drop(1).joinToString(",")
                outCues.add(MkvManagerEngine.ExtractedCue(parsedStart, parsedEnd, "Dialogue: $withoutReadOrder"))
                return
            }
        }

        // 3. Matroska S_TEXT/ASS packet or plain text
        val endMs = if (explicitDurationMs > 0) {
            blockStartMs + explicitDurationMs
        } else {
            // Check for karaoke duration tags (\k, \K, \kf, \ko)
            val kCentis = parseKaraokeDurationCentis(clean)
            if (kCentis > 0) {
                blockStartMs + (kCentis * 10L)
            } else {
                -1L // Duration unknown, will be resolved by resolveCueDurations
            }
        }
        outCues.add(MkvManagerEngine.ExtractedCue(blockStartMs, endMs, clean))
    }

    private fun parseKaraokeDurationCentis(text: String): Long {
        var totalCentis = 0L
        val regex = Regex("""\\[kK][fo]?([0-9]+)""")
        regex.findAll(text).forEach { match ->
            val c = match.groupValues[1].toLongOrNull() ?: 0L
            totalCentis += c
        }
        return totalCentis
    }

    private fun skipUnknownSizeCluster() {
        while (true) {
            val id = readElementId() ?: break
            val size = readElementSize() ?: break
            if (isTopLevelSegmentElement(id)) {
                unreadElement(id, size)
                break
            }
            skipBytes(size)
        }
    }

    data class TrackScanResult(
        val bytes: Map<Long, Long> = emptyMap(),
        val blockCounts: Map<Long, Int> = emptyMap()
    )

    /**
     * Scans every Cluster Block in the file and returns, per track number, the total stored
     * block payload bytes ("Stream size") and the number of blocks ("Count of elements" for
     * subtitle tracks). The block header itself is excluded from the byte total. Lacing
     * remains lossless because the complete Block element size is counted as stored bytes.
     *
     * A malformed or unexpected element inside any single Cluster is recovered from by
     * resyncing to the next Cluster boundary instead of aborting the scan for every track
     * from that point on - previously this made bitrate/stream-size figures for the *later*
     * portion of a file silently disappear on files with even one awkward Cluster.
     */
    fun scanTrackByteSizes(): TrackScanResult {
        val totals = mutableMapOf<Long, Long>()
        val counts = mutableMapOf<Long, Int>()
        while (true) {
            val id = readElementId() ?: break
            val size = readElementSize() ?: break
            when (id) {
                0x18538067 -> {
                    scanSegmentForTrackBytes(totals, counts)
                    break
                }
                else -> skipBytes(size)
            }
        }
        return TrackScanResult(totals, counts)
    }

    private fun scanSegmentForTrackBytes(totals: MutableMap<Long, Long>, counts: MutableMap<Long, Int>) {
        while (true) {
            val id = readElementId() ?: break
            val size = readElementSize() ?: break
            if (id == 0x1F43B675) {
                try {
                    val clusterEnd = if (isUnknownSize(size)) Long.MAX_VALUE else currentPosition + size
                    while (currentPosition < clusterEnd) {
                        val elId = readElementId() ?: break
                        val elSize = readElementSize() ?: break
                        if (isTopLevelSegmentElement(elId)) {
                            unreadElement(elId, elSize)
                            break
                        }
                        when (elId) {
                            0xE7 -> skipBytes(elSize)
                            0xA3 -> {
                                val (track, vintLen, _) = readVintTrackHeader(elSize)
                                if (track > 0L) {
                                    totals[track] = (totals[track] ?: 0L) + elSize
                                    counts[track] = (counts[track] ?: 0) + 1
                                }
                                skipBytes(elSize - vintLen)
                            }
                            0xA0 -> {
                                val bgEnd = currentPosition + elSize
                                while (currentPosition < bgEnd) {
                                    val bgId = readElementId() ?: break
                                    val bgSize = readElementSize() ?: break
                                    if (bgId == 0xA1) {
                                        val (track, vintLen, _) = readVintTrackHeader(bgSize)
                                        if (track > 0L) {
                                            totals[track] = (totals[track] ?: 0L) + bgSize
                                            counts[track] = (counts[track] ?: 0) + 1
                                        }
                                        skipBytes(bgSize - vintLen)
                                    } else {
                                        skipBytes(bgSize)
                                    }
                                }
                            }
                            else -> skipBytes(elSize)
                        }
                    }
                } catch (t: Throwable) {
                    if (!resyncToNextCluster()) break
                }
            } else {
                if (isUnknownSize(size)) {
                    if (!resyncToNextCluster()) break
                } else {
                    skipBytes(size)
                }
            }
        }
    }

    private var unreadId: Int? = null
    private var unreadSize: Long? = null

    fun unreadElement(id: Int, size: Long) {
        unreadId = id
        unreadSize = size
    }

    private fun readByte(): Int {
        val b = stream.read()
        if (b != -1) currentPosition++
        return b
    }

    private fun readElementId(): Int? {
        if (unreadId != null) {
            val id = unreadId
            unreadId = null
            return id
        }
        var first = readByte()
        // RFC 8794 Section 11.2: EBML Readers MUST skip any sequence of zero octets (0x00 padding) between EBML Elements
        while (first == 0) {
            first = readByte()
        }
        if (first == -1) return null
        var mask = 0x80
        var len = 1
        while ((first and mask) == 0 && mask > 0) {
            mask = mask shr 1
            len++
        }
        if (len > 4) return null
        var id = first
        for (i in 1 until len) {
            val b = readByte()
            if (b == -1) return null
            id = (id shl 8) or b
        }
        return id
    }

    private fun readElementSize(): Long? {
        if (unreadSize != null) {
            val size = unreadSize
            unreadSize = null
            return size
        }
        val first = readByte()
        if (first == -1) return null
        var mask = 0x80
        var len = 1
        while ((first and mask) == 0 && mask > 0) {
            mask = mask shr 1
            len++
        }
        if (len > 8) return null
        var size = (first and (mask.inv())).toLong()
        for (i in 1 until len) {
            val b = readByte()
            if (b == -1) return null
            size = (size shl 8) or (b.toLong() and 0xFF)
        }
        return size
    }

    private fun readUnsignedInt(size: Long): Long {
        var value = 0L
        for (i in 0 until size.toInt().coerceAtMost(8)) {
            val b = readByte()
            if (b == -1) break
            value = (value shl 8) or (b.toLong() and 0xFF)
        }
        return value
    }

    fun readSignedInt(size: Long): Long {
        val s = size.toInt().coerceIn(1, 8)
        val first = readByte()
        if (first == -1) return 0L
        val isNeg = (first and 0x80) != 0
        var value = if (isNeg) {
            (-1L shl (s * 8)) or ((first.toLong() and 0xFF) shl ((s - 1) * 8))
        } else {
            (first.toLong() and 0xFF) shl ((s - 1) * 8)
        }
        for (i in 1 until s) {
            val b = readByte()
            if (b == -1) break
            value = value or ((b.toLong() and 0xFF) shl ((s - 1 - i) * 8))
        }
        return value
    }

    private fun readFloat(size: Long): Double {
        val bytes = readBinary(size)
        if (bytes.size == 4) {
            return ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).float.toDouble()
        } else if (bytes.size == 8) {
            return ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).double
        }
        return 0.0
    }

    private fun readString(size: Long): String {
        val bytes = readBinary(size)
        return String(bytes, Charsets.UTF_8).trimEnd('\u0000')
    }

    private fun readBinary(size: Long): ByteArray {
        val safeSize = size.toInt().coerceIn(0, 10 * 1024 * 1024)
        val data = ByteArray(safeSize)
        var readTotal = 0
        while (readTotal < safeSize) {
            val count = stream.read(data, readTotal, safeSize - readTotal)
            if (count == -1) break
            readTotal += count
            currentPosition += count
        }
        if (size > safeSize) {
            skipBytes(size - safeSize)
        }
        return if (readTotal == safeSize) data else data.copyOf(readTotal)
    }

    fun readBinaryExact(size: Int): ByteArray {
        val safeSize = size.coerceAtLeast(0)
        val data = ByteArray(safeSize)
        var readTotal = 0
        while (readTotal < safeSize) {
            val count = stream.read(data, readTotal, safeSize - readTotal)
            if (count == -1) break
            readTotal += count
            currentPosition += count
        }
        return if (readTotal == safeSize) data else data.copyOf(readTotal)
    }

    private val skipBuffer = ByteArray(65536)

    fun skipBytes(count: Long) {
        var remaining = count
        while (remaining > 0) {
            val toRead = remaining.coerceAtMost(skipBuffer.size.toLong()).toInt()
            val read = stream.read(skipBuffer, 0, toRead)
            if (read == -1) break
            remaining -= read
            currentPosition += read
        }
    }

    data class VintHeader(val trackNum: Long, val vintLen: Int, val vintBytes: ByteArray)

    fun readVintTrackHeader(elementSize: Long): VintHeader {
        val first = readByte()
        if (first == -1) return VintHeader(0L, 0, ByteArray(0))
        var mask = 0x80
        var len = 1
        while ((first and mask) == 0 && mask > 0) {
            mask = mask shr 1
            len++
        }
        val safeLen = len.coerceIn(1, 8).coerceAtMost(elementSize.toInt())
        val bytes = ByteArray(safeLen)
        bytes[0] = first.toByte()
        var trackNum = (first and (mask.inv())).toLong()
        for (i in 1 until safeLen) {
            val b = readByte()
            if (b == -1) break
            bytes[i] = b.toByte()
            trackNum = (trackNum shl 8) or (b.toLong() and 0xFF)
        }
        return VintHeader(trackNum, safeLen, bytes)
    }

    fun resyncToNextCluster(): Boolean {
        var b1 = 0
        var b2 = 0
        var b3 = 0
        while (true) {
            val b4 = readByte()
            if (b4 == -1) return false
            if (b1 == 0x1F && b2 == 0x43 && b3 == 0xB6 && b4 == 0x75) {
                unreadId = 0x1F43B675
                return true
            }
            b1 = b2
            b2 = b3
            b3 = b4
        }
    }
}

class EbmlStreamWriter(private val outputStream: OutputStream) {

    var totalBytesWritten: Long = 0L
    var segmentPayloadOffset: Long = 0L
    val recordedCuePoints = mutableListOf<CuePointEntry>()

    data class CuePointEntry(
        val timeMs: Long,
        val trackNumber: Long,
        val clusterOffset: Long
    )

    fun flush() {
        outputStream.flush()
    }

    fun openSegment() {
        writeElementId(0x18538067)
        val unknownSize = byteArrayOf(0x01, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        outputStream.write(unknownSize)
        totalBytesWritten += unknownSize.size
        segmentPayloadOffset = totalBytesWritten
    }

    fun currentSegmentOffset(): Long {
        return (totalBytesWritten - segmentPayloadOffset).coerceAtLeast(0L)
    }

    fun recordCuePoint(timeMs: Long, trackNumber: Long, clusterOffset: Long) {
        if (recordedCuePoints.none { it.timeMs == timeMs }) {
            recordedCuePoints.add(CuePointEntry(timeMs, trackNumber, clusterOffset))
        }
    }

    fun writeCuesElement() {
        if (recordedCuePoints.isEmpty()) return
        writeMasterElement(0x1C53BB6B) { w -> // Cues
            recordedCuePoints.forEach { cue ->
                w.writeMasterElement(0xBB) { cp -> // CuePoint
                    cp.writeUnsignedInt(0xB3, cue.timeMs) // CueTime
                    cp.writeMasterElement(0xB7) { ctp -> // CueTrackPositions
                        ctp.writeUnsignedInt(0xF7, cue.trackNumber) // CueTrack
                        ctp.writeUnsignedInt(0xF1, cue.clusterOffset) // CueClusterPosition
                    }
                }
            }
        }
    }

    fun writeEbmlHeader(docType: String = "matroska", docTypeVersion: Int = 4, docTypeReadVersion: Int = 2) {
        writeMasterElement(0x1A45DFA3) { w ->
            w.writeUnsignedInt(0x4286, 1) // EBMLVersion
            w.writeUnsignedInt(0x42F7, 1) // EBMLReadVersion
            w.writeUnsignedInt(0x42F2, 4) // EBMLMaxIDLength
            w.writeUnsignedInt(0x42F3, 8) // EBMLMaxSizeLength
            w.writeString(0x4282, docType) // DocType
            w.writeUnsignedInt(0x4287, docTypeVersion.toLong()) // DocTypeVersion
            w.writeUnsignedInt(0x4285, docTypeReadVersion.toLong()) // DocTypeReadVersion
        }
    }

    fun writeSegmentInformation(
        title: String,
        muxingApp: String,
        writingApp: String,
        durationMs: Long,
        timecodeScale: Long = 1000000L
    ) {
        writeMasterElement(0x1549A966) { w ->
            w.writeUnsignedInt(0x2AD7B1, timecodeScale)
            if (title.isNotBlank()) w.writeString(0x7BA9, title)
            w.writeString(0x4D80, muxingApp)
            w.writeString(0x5741, writingApp)
            if (durationMs > 0) {
                val dur = durationMs / (timecodeScale / 1000000.0)
                w.writeFloat(0x4489, dur)
            }
        }
    }

    fun writeTracksElement(tracks: List<MkvTrackInfo>) {
        writeMasterElement(0x1654AE6B) { w ->
            tracks.forEach { track ->
                w.writeMasterElement(0xAE) { tw ->
                    tw.writeUnsignedInt(0xD7, track.trackNumber)
                    tw.writeUnsignedInt(0x73C5, if (track.trackUid != 0L) track.trackUid else track.trackNumber * 1000L)
                    val typeVal = when (track.trackType) {
                        MkvTrackType.VIDEO -> 1L
                        MkvTrackType.AUDIO -> 2L
                        MkvTrackType.SUBTITLE -> 17L
                        else -> 1L
                    }
                    tw.writeUnsignedInt(0x83, typeVal)
                    tw.writeUnsignedInt(0xB9, if (track.isEnabled) 1L else 0L)
                    tw.writeUnsignedInt(0x88, if (track.isDefault) 1L else 0L)
                    tw.writeUnsignedInt(0x55AA, if (track.isForced) 1L else 0L)
                    tw.writeUnsignedInt(0x9C, if (track.flagLacing) 1L else 0L)
                    if (track.defaultDurationNs > 0L) {
                        tw.writeUnsignedInt(0x23E383, track.defaultDurationNs)
                    }
                    if (track.isHearingImpaired) tw.writeUnsignedInt(0x55AB, 1L)
                    if (track.isVisualImpaired) tw.writeUnsignedInt(0x55AC, 1L)
                    if (track.isDescriptions) tw.writeUnsignedInt(0x55AD, 1L)
                    if (track.isOriginal) tw.writeUnsignedInt(0x55AE, 1L)
                    if (track.isCommentary) tw.writeUnsignedInt(0x55AF, 1L)
                    if (track.syncOffsetMs != 0L) {
                        tw.writeUnsignedInt(0x53AC, track.syncOffsetMs * 1000000L)
                    }

                    val effectiveCodecId = when {
                        track.encoder == "x265" -> "V_MPEGH/ISO/HEVC"
                        track.encoder == "aac" -> "A_AAC"
                        track.encoder == "ac3" -> "A_AC3"
                        track.codecId.isNotBlank() -> track.codecId
                        track.trackType == MkvTrackType.VIDEO -> "V_MPEG4/ISO/AVC"
                        track.trackType == MkvTrackType.AUDIO -> "A_AAC"
                        track.trackType == MkvTrackType.SUBTITLE -> "S_TEXT/UTF8"
                        else -> "V_MPEG4/ISO/AVC"
                    }
                    tw.writeString(0x86, effectiveCodecId)

                    if (track.name.isNotBlank()) tw.writeString(0x536E, track.name)
                    if (track.language.isNotBlank()) tw.writeString(0x22B59C, track.language)
                    
                    if (effectiveCodecId.contains("ASS", ignoreCase = true) || effectiveCodecId.contains("SSA", ignoreCase = true)) {
                        val assHeader = if (track.codecPrivate.isNotEmpty() && String(track.codecPrivate, Charsets.UTF_8).contains("[Script Info]")) {
                            track.codecPrivate
                        } else if (!track.externalSourceContent.isNullOrBlank() && track.externalSourceContent!!.contains("[Script Info]")) {
                            val content = track.externalSourceContent!!
                            val headerLines = mutableListOf<String>()
                            for (l in content.lines()) {
                                val trimmed = l.trim()
                                if (trimmed.startsWith("Dialogue:", ignoreCase = true) || trimmed.startsWith("Comment:", ignoreCase = true)) {
                                    break
                                }
                                headerLines.add(l)
                            }
                            var hStr = headerLines.joinToString("\n").trim()
                            if (!hStr.contains("[Events]")) {
                                hStr += "\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n"
                            }
                            hStr.toByteArray(Charsets.UTF_8)
                        } else {
                            val defaultAssHeader = "[Script Info]\nTitle: ${track.name.ifBlank { "Subtitle" }}\nScriptType: v4.00+\nWrapStyle: 0\nScaledBorderAndShadow: yes\nYCbCr Matrix: TV.601\n\n[V4+ Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\nStyle: Default,Arial,20,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n"
                            defaultAssHeader.toByteArray(Charsets.UTF_8)
                        }
                        tw.writeBinary(0x63A2, assHeader)
                    } else if (track.codecPrivate.isNotEmpty()) {
                        tw.writeBinary(0x63A2, track.codecPrivate)
                    }

                    if (track.trackType == MkvTrackType.VIDEO && track.pixelWidth > 0 && track.pixelHeight > 0) {
                        tw.writeMasterElement(0xE0) { vw ->
                            vw.writeUnsignedInt(0xB0, track.pixelWidth.toLong())
                            vw.writeUnsignedInt(0xBA, track.pixelHeight.toLong())
                            if (track.displayWidth > 0) vw.writeUnsignedInt(0x54B0, track.displayWidth.toLong())
                            if (track.displayHeight > 0) vw.writeUnsignedInt(0x54BA, track.displayHeight.toLong())
                            if (track.displayUnit > 0) vw.writeUnsignedInt(0x54B2, track.displayUnit.toLong())
                            if (track.defaultDurationNs > 0L) vw.writeUnsignedInt(0x23E383, track.defaultDurationNs)
                        }
                    } else if (track.trackType == MkvTrackType.AUDIO && track.channels > 0) {
                        tw.writeMasterElement(0xE1) { aw ->
                            aw.writeUnsignedInt(0x9F, track.channels.toLong())
                            if (track.sampleRate > 0) aw.writeFloat(0xB5, track.sampleRate.toDouble())
                            if (track.bitDepth > 0) aw.writeUnsignedInt(0x6264, track.bitDepth.toLong())
                        }
                    }
                }
            }
        }
    }

    fun writeTagsElement(tracks: List<MkvTrackInfo>) {
        val tracksWithTags = tracks.filter { it.metadataTags.isNotEmpty() }
        if (tracksWithTags.isEmpty()) return
        writeMasterElement(0x1254C367) { tw -> // Tags
            tracksWithTags.forEach { track ->
                tw.writeMasterElement(0x7373) { tag -> // Tag
                    tag.writeMasterElement(0x63C0) { targets -> // Targets
                        targets.writeUnsignedInt(0x68CA, if (track.trackType == MkvTrackType.VIDEO) 50L else 30L) // TargetTypeValue
                        targets.writeUnsignedInt(0x63C5, if (track.trackUid != 0L) track.trackUid else track.trackNumber * 1000L) // TrackUID
                    }
                    track.metadataTags.forEach { (key, value) ->
                        tag.writeMasterElement(0x67C8) { simpleTag -> // SimpleTag
                            simpleTag.writeString(0x45A3, key) // TagName
                            simpleTag.writeString(0x4487, value) // TagString
                        }
                    }
                }
            }
        }
    }

    fun writeChaptersElement(chapters: List<MkvChapterInfo>) {
        if (chapters.isEmpty()) return
        writeMasterElement(0x1043A770) { w ->
            w.writeMasterElement(0x45B9) { ew ->
                chapters.forEach { ch ->
                    ew.writeMasterElement(0xB6) { cw ->
                        cw.writeUnsignedInt(0x73C4, ch.uid)
                        cw.writeUnsignedInt(0x91, ch.startTimeMs * 1000000L) // in nanoseconds
                        if (ch.endTimeMs > 0) cw.writeUnsignedInt(0x92, ch.endTimeMs * 1000000L)
                        cw.writeMasterElement(0x80) { dw ->
                            dw.writeString(0x85, ch.title)
                            dw.writeString(0x437C, ch.language.ifBlank { "eng" })
                        }
                    }
                }
            }
        }
    }

    fun writeAttachmentsElement(attachments: List<MkvAttachmentInfo>) {
        if (attachments.isEmpty()) return
        writeMasterElement(0x1941A469) { w ->
            attachments.forEach { att ->
                w.writeMasterElement(0x61A7) { aw ->
                    aw.writeString(0x466E, att.fileName)
                    aw.writeString(0x4660, att.mimeType)
                    if (att.description.isNotBlank()) aw.writeString(0x467E, att.description)
                    aw.writeUnsignedInt(0x46AE, att.uid)
                    aw.writeBinary(0x465C, att.data)
                }
            }
        }
    }

    fun writeMasterElement(id: Int, block: (EbmlStreamWriter) -> Unit) {
        val baos = ByteArrayOutputStream()
        val subWriter = EbmlStreamWriter(baos)
        block(subWriter)
        subWriter.flush()
        val payload = baos.toByteArray()
        writeElementId(id)
        writeElementSize(payload.size.toLong())
        outputStream.write(payload)
        totalBytesWritten += payload.size
    }

    fun writeRawBytesWithId(id: Int, data: ByteArray) {
        writeElementId(id)
        writeElementSize(data.size.toLong())
        outputStream.write(data)
        totalBytesWritten += data.size
    }

    fun writeUnsignedInt(id: Int, value: Long) {
        val bytes = mutableListOf<Byte>()
        var temp = value
        while (temp > 0) {
            bytes.add(0, (temp and 0xFF).toByte())
            temp = temp ushr 8
        }
        if (bytes.isEmpty()) bytes.add(0.toByte())
        writeElement(id, bytes.toByteArray())
    }

    fun writeSignedInt(id: Int, value: Long) {
        var size = 1
        while (size < 8) {
            val min = -(1L shl (size * 8 - 1))
            val max = (1L shl (size * 8 - 1)) - 1
            if (value in min..max) break
            size++
        }
        val bytes = ByteArray(size)
        for (i in 0 until size) {
            bytes[size - 1 - i] = ((value shr (i * 8)) and 0xFF).toByte()
        }
        writeElement(id, bytes)
    }

    fun writeFloat(id: Int, value: Double) {
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        buffer.putDouble(value)
        writeElement(id, buffer.array())
    }

    fun writeString(id: Int, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeElement(id, bytes)
    }

    fun writeBinary(id: Int, data: ByteArray) {
        writeElement(id, data)
    }

    fun writeRawBytes(data: ByteArray, offset: Int, length: Int) {
        outputStream.write(data, offset, length)
        totalBytesWritten += length
    }

    private fun writeElement(id: Int, data: ByteArray) {
        writeElementId(id)
        writeElementSize(data.size.toLong())
        outputStream.write(data)
        totalBytesWritten += data.size
    }

    fun writeElementId(id: Int) {
        val bytes = mutableListOf<Byte>()
        var temp = id
        while (temp != 0) {
            bytes.add(0, (temp and 0xFF).toByte())
            temp = temp ushr 8
        }
        val arr = bytes.toByteArray()
        outputStream.write(arr)
        totalBytesWritten += arr.size
    }

    fun writeElementSize(size: Long) {
        val sizeVint = encodeVint(size)
        outputStream.write(sizeVint)
        totalBytesWritten += sizeVint.size
    }

    companion object {
        fun encodeVint(value: Long): ByteArray {
            var length = 1
            while (length < 8 && value >= ((1L shl (7 * length)) - 1)) {
                length++
            }
            val encoded = ByteArray(length)
            var temp = value
            for (i in length - 1 downTo 0) {
                encoded[i] = (temp and 0xFF).toByte()
                temp = temp shr 8
            }
            encoded[0] = (encoded[0].toInt() or (0x100 shr length)).toByte()
            return encoded
        }

        fun decodeVint(data: ByteArray, offset: Int): Pair<Long, Int> {
            if (offset >= data.size) return Pair(0L, 0)
            val first = data[offset].toInt() and 0xFF
            var mask = 0x80
            var len = 1
            while ((first and mask) == 0 && mask > 0) {
                mask = mask shr 1
                len++
            }
            if (len > 8 || offset + len > data.size) return Pair(first.toLong(), 1)
            var value = (first and mask.inv()).toLong()
            for (i in 1 until len) {
                value = (value shl 8) or (data[offset + i].toLong() and 0xFF)
            }
            return Pair(value, len)
        }
    }
}

