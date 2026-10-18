package com.example.util

import android.content.Context
import android.media.MediaExtractor
import android.util.Log
import com.example.player.SubtitleFontManager
import java.io.File
import java.util.Locale

/**
 * Joins a downloaded video with extra audio languages, subtitles and subtitle fonts into ONE Matroska file, without
 * re-encoding (stream copy), using the ffmpeg binary that ships with the youtubedl-android ffmpeg module.
 *
 * Everything here is best effort: when ffmpeg is not available or a merge fails, the caller simply keeps the plain
 * video file, so a download never fails because of the extra tracks.
 */
object FfmpegMuxer {
    private const val TAG = "FfmpegMuxer"

    @Volatile private var libraryInitialised = false
    @Volatile private var cachedBinary: File? = null

    /** An extra audio / subtitle file that is merged in, with the name and language shown in the player's track list. */
    class Part(val file: File, val title: String, val language: String)

    /**
     * Unpacks the ffmpeg module (once). Called by reflection so the app still starts when the module is missing.
     * Must run after YoutubeDL.init().
     */
    fun initLibrary(context: Context) {
        if (libraryInitialised) return
        try {
            val cls = Class.forName("com.yausername.ffmpeg.FFmpeg")
            val instance = cls.getMethod("getInstance").invoke(null)
            cls.getMethod("init", Context::class.java).invoke(instance, context.applicationContext)
            libraryInitialised = true
        } catch (t: Throwable) {
            Log.w(TAG, "ffmpeg module could not be initialised", t)
        }
    }

    /** The ffmpeg executable, or null when the module is not installed. */
    fun binary(context: Context): File? {
        cachedBinary?.takeIf { it.isFile }?.let { return it }
        val app = context.applicationContext
        val found: File? = try {
            val inNative = File(app.applicationInfo.nativeLibraryDir, "libffmpeg.so")
            if (inNative.isFile) inNative
            else File(app.noBackupFilesDir, "youtubedl-android").walkTopDown().maxDepth(8)
                .firstOrNull { it.isFile && (it.name == "libffmpeg.so" || it.name == "ffmpeg") && it.length() > 100_000L }
        } catch (_: Throwable) { null }
        cachedBinary = found
        return found
    }

    fun isAvailable(context: Context): Boolean {
        initLibrary(context)
        return binary(context) != null
    }

    private fun libraryPath(context: Context): String {
        val app = context.applicationContext
        val dirs = LinkedHashSet<String>()
        dirs.add(app.applicationInfo.nativeLibraryDir)
        try {
            File(app.noBackupFilesDir, "youtubedl-android").walkTopDown().maxDepth(8)
                .filter { it.isDirectory && it.name == "lib" }
                .forEach { dirs.add(it.absolutePath) }
        } catch (_: Throwable) {}
        System.getenv("LD_LIBRARY_PATH")?.takeIf { it.isNotBlank() }?.let { dirs.add(it) }
        return dirs.joinToString(":")
    }

    /** Runs ffmpeg with [args]; true when it finished with exit code 0. */
    private fun run(context: Context, args: List<String>, isCancelled: () -> Boolean): Boolean {
        val bin = binary(context) ?: return false
        val pb = ProcessBuilder(listOf(bin.absolutePath) + args).redirectErrorStream(true)
        pb.environment()["LD_LIBRARY_PATH"] = libraryPath(context)
        pb.environment()["TMPDIR"] = context.cacheDir.absolutePath
        val process = pb.start()
        val log = StringBuilder()
        val reader = Thread {
            try {
                process.inputStream.bufferedReader().forEachLine { line ->
                    if (log.length < 6000) log.append(line).append('\n')
                }
            } catch (_: Throwable) {}
        }
        reader.isDaemon = true
        reader.start()
        while (true) {
            if (isCancelled()) {
                process.destroy()
                return false
            }
            try {
                val code = process.exitValue()
                reader.join(1000)
                if (code != 0) Log.w(TAG, "ffmpeg exit $code: $log")
                return code == 0
            } catch (_: IllegalThreadStateException) {
                Thread.sleep(250)
            }
        }
    }

    /** How many audio tracks and subtitle tracks [file] already has (so the new ones can be numbered and labelled). */
    private fun countTracks(file: File): Pair<Int, Int> {
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(file.absolutePath)
            var audio = 0
            var sub = 0
            for (i in 0 until ex.trackCount) {
                val mime = ex.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) audio++
                else if (mime.startsWith("text/") || mime == "application/x-subrip" || mime.contains("subtitle") || mime.contains("vtt")) sub++
            }
            audio to sub
        } catch (_: Throwable) {
            -1 to -1
        } finally {
            try { ex.release() } catch (_: Throwable) {}
        }
    }

    /** ffmpeg stores the language as ISO 639-2 ("hin"); returns null when [lang] is not a known language code. */
    private fun iso3(lang: String): String? {
        val l = lang.trim().substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)
        if (l.length !in 2..3) return null
        return try { Locale(l).isO3Language.takeIf { it.length == 3 } } catch (_: Throwable) { null }
    }

    /** WebVTT -> SubRip text, so the subtitle can be copied into Matroska as a normal text track. */
    fun vttToSrt(text: String): String {
        val out = StringBuilder()
        var index = 1
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').removePrefix("﻿").split('\n')
        var i = 0
        // header (WEBVTT ...) ends at the first blank line
        while (i < lines.size && lines[i].isNotBlank()) i++
        fun fixTime(raw: String): String {
            var t = raw.trim().replace('.', ',')
            if (t.count { it == ':' } == 1) t = "00:$t"
            return t
        }
        val tag = Regex("<(?!/?[biu]>)[^>]*>")
        while (i < lines.size) {
            while (i < lines.size && lines[i].isBlank()) i++
            if (i >= lines.size) break
            val block = ArrayList<String>()
            while (i < lines.size && lines[i].isNotBlank()) { block.add(lines[i]); i++ }
            val timeAt = block.indexOfFirst { it.contains("-->") }
            if (timeAt < 0) continue // NOTE / STYLE / REGION blocks
            val parts = block[timeAt].split("-->")
            if (parts.size < 2) continue
            val start = fixTime(parts[0])
            val end = fixTime(parts[1].trim().substringBefore(' '))
            val body = block.drop(timeAt + 1).joinToString("\n") { it.replace(tag, "") }.trim()
            if (body.isEmpty()) continue
            out.append(index++).append('\n').append(start).append(" --> ").append(end).append('\n').append(body).append("\n\n")
        }
        return out.toString()
    }

    /** Font names an ASS / SSA script asks for (Style lines and \fn overrides). */
    private fun assFontNames(text: String): Set<String> {
        val names = LinkedHashSet<String>()
        var fontIdx = -1
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("Format:", true) && line.contains("Fontname", true)) {
                fontIdx = line.substringAfter(':').split(',').indexOfFirst { it.trim().equals("Fontname", true) }
            } else if (line.startsWith("Style:", true) && fontIdx >= 0) {
                line.substringAfter(':').split(',').getOrNull(fontIdx)?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() }?.let { names.add(it) }
            }
        }
        Regex("\\\\fn([^\\\\}]+)").findAll(text).forEach { m -> m.groupValues[1].trim().removePrefix("@").takeIf { it.isNotBlank() }?.let { names.add(it) } }
        return names
    }

    /** The fonts the app already has for the given ASS subtitle files, to be attached to the Matroska file. */
    private fun fontsFor(context: Context, subs: List<Part>): List<File> {
        val fonts = LinkedHashMap<String, File>()
        for (s in subs) {
            val ext = s.file.extension.lowercase(Locale.ROOT)
            if (ext != "ass" && ext != "ssa") continue
            val text = try { s.file.readText() } catch (_: Throwable) { continue }
            for (name in assFontNames(text)) {
                val f = try { SubtitleFontManager.findFontFile(context, name) } catch (_: Throwable) { null } ?: continue
                if (f.name == "GoNotoCurrent-Regular.ttf") continue
                fonts.putIfAbsent(f.absolutePath, f)
                if (fonts.size >= 12) return fonts.values.toList()
            }
        }
        return fonts.values.toList()
    }

    /**
     * Writes [out] (Matroska) = [main] + every [audios] / [subs] file + the fonts of the ASS subtitles.
     * Returns true when [out] was written; on false nothing usable was produced and the caller keeps [main].
     */
    fun mux(context: Context, main: File, audios: List<Part>, subs: List<Part>, out: File, isCancelled: () -> Boolean): Boolean {
        if (!isAvailable(context)) return false
        val work = out.parentFile ?: return false
        // vtt -> srt; formats ffmpeg cannot copy into Matroska as text are left out
        val textSubs = ArrayList<Part>()
        for ((n, s) in subs.withIndex()) {
            when (s.file.extension.lowercase(Locale.ROOT)) {
                "srt", "ass", "ssa" -> textSubs.add(s)
                "vtt" -> try {
                    val srt = File(work, "conv_$n.srt")
                    srt.writeText(vttToSrt(s.file.readText()))
                    if (srt.length() > 0L) textSubs.add(Part(srt, s.title, s.language))
                } catch (_: Throwable) {}
                else -> {}
            }
        }
        val (mainAudio, mainSub) = countTracks(main)
        val mainIsMatroska = main.extension.lowercase(Locale.ROOT).let { it == "mkv" || it == "webm" || it == "mka" }

        // 1st try: everything. 2nd: without the subtitle tracks of the video file itself (e.g. mov_text). 3rd: audio only.
        val attempts = listOf(
            Triple(true, textSubs, true),
            Triple(false, textSubs, true),
            Triple(false, emptyList<Part>(), true)
        )
        for ((keepMainSubs, extraSubs, _) in attempts) {
            if (isCancelled()) return false
            if (audios.isEmpty() && extraSubs.isEmpty()) return false
            out.delete()
            val fonts = if (mainIsMatroska) emptyList() else fontsFor(context, extraSubs)
            val args = ArrayList<String>()
            args += listOf("-y", "-hide_banner", "-nostdin", "-loglevel", "error")
            args += listOf("-i", main.absolutePath)
            for (a in audios) args += listOf("-i", a.file.absolutePath)
            for (s in extraSubs) args += listOf("-i", s.file.absolutePath)
            args += listOf("-map", "0:v?", "-map", "0:a?")
            if (keepMainSubs) args += listOf("-map", "0:s?")
            if (mainIsMatroska) args += listOf("-map", "0:t?")
            audios.forEachIndexed { i, _ -> args += listOf("-map", "${1 + i}:a:0") }
            extraSubs.forEachIndexed { j, _ -> args += listOf("-map", "${1 + audios.size + j}:0") }
            args += listOf("-c", "copy")
            // names + languages of the new tracks (numbered after the ones the video file already has)
            audios.forEachIndexed { i, a ->
                val idx = if (mainAudio >= 0) mainAudio + i else i
                if (mainAudio >= 0) {
                    if (a.title.isNotBlank()) args += listOf("-metadata:s:a:$idx", "title=${a.title}")
                    iso3(a.language)?.let { args += listOf("-metadata:s:a:$idx", "language=$it") }
                }
            }
            val mainSubKept = if (keepMainSubs && mainSub > 0) mainSub else if (keepMainSubs && mainSub < 0) -1 else 0
            extraSubs.forEachIndexed { j, s ->
                if (mainSubKept >= 0) {
                    val idx = mainSubKept + j
                    if (s.title.isNotBlank()) args += listOf("-metadata:s:s:$idx", "title=${s.title}")
                    iso3(s.language)?.let { args += listOf("-metadata:s:s:$idx", "language=$it") }
                }
            }
            fonts.forEachIndexed { k, f ->
                val mime = if (f.extension.equals("otf", true)) "application/vnd.ms-opentype" else "application/x-truetype-font"
                args += listOf("-attach", f.absolutePath, "-metadata:s:t:$k", "mimetype=$mime", "-metadata:s:t:$k", "filename=${f.name}")
            }
            args += listOf("-f", "matroska", out.absolutePath)
            val ok = try { run(context, args, isCancelled) } catch (t: Throwable) { Log.w(TAG, "ffmpeg could not run", t); false }
            if (ok && out.isFile && out.length() > 0L) return true
            out.delete()
        }
        return false
    }
}
