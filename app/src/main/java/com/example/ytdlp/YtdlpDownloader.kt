/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Adapted from mpvRx (https://github.com/Riteshp2001/mpvRx) domain/download/YtdlpDownloadEngine.kt and
 * YtdlpMediaMerger.kt, AGPL-3.0-or-later. Lumora runs it as one job of its own DownloadManager
 * (no separate service/queue); the separate video+audio streams are muxed with MediaMuxHelper.
 */

package com.example.ytdlp

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.example.util.MediaMuxHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Downloads page / HLS / DASH links (YouTube, m3u8, mpd, extractor sites) with the bundled yt-dlp runtime.
 * Output lands in [workDir] as `media.<ext>`; the caller moves it to the public folder.
 */
object YtdlpDownloader {
  private const val TAG = "YtdlpDownloader"
  private const val FINAL_OUTPUT_PREFIX = "LUMORA_FINAL_OUTPUT="
  const val DEFAULT_SINGLE_FILE_FORMAT = "best/bestvideo/bestaudio"
  const val AUDIO_FORMAT = "bestaudio[ext=m4a]/bestaudio"
  const val YOUTUBE_SPLIT_FORMAT = "bestvideo[vcodec^=avc1]/bestvideo,bestaudio[ext=m4a]/bestaudio"

  data class Progress(
    /** Bytes finished over all streams of this job (0 when unknown). */
    val downloadedBytes: Long,
    /** Total over all streams (0 when yt-dlp does not know it yet). */
    val totalBytes: Long,
    val speedBps: Long,
    val etaSec: Long,
    /** Shown while no data flows ("Preparing…", "Merging…"), null otherwise. */
    val phase: String?,
  )

  class DownloadException(
    message: String,
  ) : RuntimeException(message)

  private val PROGRESS_REGEX = Regex("""\[download]\s+([0-9.]+)%(.*)""")
  private val SIZE_REGEX = Regex("""of\s+~?\s*([0-9.]+)\s*([KMGT]?i?B)""")
  private val SPEED_REGEX = Regex("""at\s+([0-9.]+)\s*([KMGT]?i?B)/s""")
  private val ETA_REGEX = Regex("""ETA\s+([0-9:]+)""")
  private val DESTINATION_REGEX = Regex("""\[download] Destination: (.+)""")
  private val PLAYABLE_EXTENSIONS =
    setOf("mkv", "mp4", "m4v", "webm", "mov", "avi", "ts", "m2ts", "mp3", "m4a", "opus", "ogg", "flac", "wav", "aac")

  /**
   * [formats] is a yt-dlp `--format` string; a comma in it means several separate streams (video,audio) that
   * are fetched one after another and muxed to `media.mp4` afterwards.
   */
  suspend fun download(
    context: Context,
    url: String,
    formats: String?,
    workDir: File,
    headers: Map<String, String>,
    isCancelled: () -> Boolean,
    onLog: (String) -> Unit = {},
    onProgress: (Progress) -> Unit,
  ): File? {
    workDir.mkdirs()
    val runtimeLog = StringBuilder()
    onProgress(Progress(0L, 0L, 0L, -1L, "Preparing…"))
    val ready =
      try {
        YtdlpManager.ensureRuntimeInstalled(context) { message ->
          runtimeLog.append(message)
          if (runtimeLog.length > 8_192) runtimeLog.delete(0, runtimeLog.length - 8_192)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        runtimeLog.append(e.message ?: e.javaClass.simpleName)
        false
      }
    if (isCancelled()) return null
    if (!ready) {
      throw DownloadException(runtimeLog.toString().trim().ifBlank { "yt-dlp runtime is not installed" })
    }

    val split = formats?.contains(',') == true
    val template = if (split) "media.f%(format_id)s.%(ext)s" else "media.%(ext)s"
    val command = buildCommand(context, url, template, workDir, formats, headers)

    val errorLines = ArrayDeque<String>()
    var printedOutput: String? = null
    var finishedBytes = 0L
    var currentTotal = 0L
    var streamCount = 0
    val exitCode =
      withContext(Dispatchers.IO) {
        val process = YtdlpManager.startPythonProcess(command, context)
        coroutineScope {
          val watcher =
            launch {
              while (!isCancelled()) delay(300)
              process.destroyForcibly()
            }
          try {
            BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
              for (line in lines) {
                if (isCancelled()) break
                if (line.startsWith(FINAL_OUTPUT_PREFIX)) {
                  printedOutput = line.removePrefix(FINAL_OUTPUT_PREFIX).trim()
                  continue
                }
                if (DESTINATION_REGEX.containsMatchIn(line)) {
                  if (streamCount > 0) finishedBytes += currentTotal
                  streamCount++
                  currentTotal = 0L
                  continue
                }
                val match = PROGRESS_REGEX.find(line.trim())
                if (match != null) {
                  val percent = match.groupValues[1].toFloatOrNull()?.coerceIn(0f, 100f) ?: 0f
                  val rest = match.groupValues[2]
                  val size = SIZE_REGEX.find(rest)?.let { toBytes(it.groupValues[1], it.groupValues[2]) } ?: 0L
                  if (size > 0L) currentTotal = size
                  val speed = SPEED_REGEX.find(rest)?.let { toBytes(it.groupValues[1], it.groupValues[2]) } ?: 0L
                  val eta = ETA_REGEX.find(rest)?.let { parseEta(it.groupValues[1]) } ?: -1L
                  val done = finishedBytes + (currentTotal * percent / 100f).toLong()
                  // yt-dlp reports one stream at a time: the total of later streams is not known yet
                  val total = if (currentTotal > 0L) finishedBytes + currentTotal else 0L
                  onProgress(Progress(done, total, speed, eta, null))
                } else if (line.isNotBlank()) {
                  errorLines.addLast(line.take(2_048))
                  if (errorLines.size > 8) errorLines.removeFirst()
                  onLog(line)
                }
              }
            }
            process.waitFor()
          } finally {
            watcher.cancel()
            if (process.isAlive) process.destroyForcibly()
          }
        }
      }

    if (isCancelled()) return null
    if (exitCode != 0) {
      throw DownloadException(
        errorLines.lastOrNull { it.startsWith("ERROR:", ignoreCase = true) }
          ?: errorLines.joinToString("\n").ifBlank { "yt-dlp exited with code $exitCode" },
      )
    }

    return withContext(Dispatchers.IO) {
      if (split) {
        onProgress(Progress(finishedBytes + currentTotal, finishedBytes + currentTotal, 0L, -1L, "Merging…"))
        mergeStreams(workDir, isCancelled)
      } else {
        val printed = printedOutput?.let { File(it.trim().removeSurrounding("\"")) }?.takeIf { it.isFile }
        val result =
          printed
            ?: workDir
              .listFiles()
              ?.filter { it.isFile && it.name.startsWith("media.") && it.extension.lowercase() in PLAYABLE_EXTENSIONS }
              ?.maxByOrNull { it.lastModified() }
            ?: throw DownloadException("yt-dlp finished without a playable output file")
        if (result.name.startsWith("media.") && result.parentFile == workDir) result else {
          val target = File(workDir, "media.${result.extension.ifBlank { "mp4" }}")
          if (result.renameTo(target)) target else result
        }
      }
    }
  }

  private fun buildCommand(
    context: Context,
    url: String,
    outputTemplate: String,
    workDir: File,
    formats: String?,
    headers: Map<String, String>,
  ): List<String> {
    val prefs = YtdlPreferences.get(context)
    return buildList {
      add(YtdlpManager.getExecutablePath(context))
      add(File(YtdlpManager.getYtdlDir(context), "yt-dlp").absolutePath)
      add("--ignore-config")
      add("--no-playlist")
      add("--newline")
      add("--no-warnings")
      add("--no-colors")
      add("--progress")
      add("--retries")
      add("5")
      add("--fragment-retries")
      add("5")
      add("--concurrent-fragments")
      add("4")
      add("--no-keep-video")
      add("--continue")
      add("--paths")
      add("home:${workDir.absolutePath}")
      add("--paths")
      add("temp:${workDir.absolutePath}")
      add("--print")
      add("after_move:$FINAL_OUTPUT_PREFIX%(filepath)s")
      add("--format")
      add(formats?.trim()?.takeIf(String::isNotBlank) ?: DEFAULT_SINGLE_FILE_FORMAT)
      add("-o")
      add(outputTemplate)

      prefs.customUserAgent.get().takeIf(String::isNotBlank)?.let {
        add("--user-agent")
        add(it)
      }
      val referer = prefs.referer.get().takeIf(String::isNotBlank) ?: headers.entries.firstOrNull { it.key.equals("Referer", true) }?.value
      referer?.let {
        add("--referer")
        add(it)
      }
      prefs.proxy.get().takeIf(String::isNotBlank)?.let {
        add("--proxy")
        add(it)
      }
      prefs.extractorArgs.get().takeIf(String::isNotBlank)?.let {
        add("--extractor-args")
        add(it)
      }
      if (prefs.geoBypass.get()) add("--geo-bypass")
      prefs.cookiesFile.get().takeIf(String::isNotBlank)?.let(::File)?.takeIf(File::isFile)?.let {
        add("--cookies")
        add(it.absolutePath)
      }
      File(context.applicationInfo.nativeLibraryDir, "libqjs.so").takeIf(File::isFile)?.let { quickJs ->
        add("--js-runtimes")
        add("quickjs:${quickJs.absolutePath}")
        add("--remote-components")
        add("ejs:github")
      }
      add("--")
      add(url)
    }
  }

  private fun mergeStreams(
    workDir: File,
    isCancelled: () -> Boolean,
  ): File {
    val candidates =
      workDir
        .listFiles()
        ?.filter { it.isFile && it.length() > 0L && it.name.startsWith("media.f") && it.extension.lowercase() in PLAYABLE_EXTENSIONS }
        .orEmpty()
    val inspected = candidates.mapNotNull(::inspect)
    val video =
      inspected.firstOrNull { it.hasVideo && !it.hasAudio } ?: inspected.firstOrNull { it.hasVideo }
        ?: throw DownloadException("Downloaded video stream was not found")
    val audio =
      inspected.firstOrNull { it.file != video.file && it.hasAudio && !it.hasVideo }
        ?: inspected.firstOrNull { it.file != video.file && it.hasAudio }
        ?: throw DownloadException("Downloaded audio stream was not found")
    val out = File(workDir, "media.mp4")
    try {
      MediaMuxHelper.mux(video.file, audio.file, File(workDir, "merging.mp4").also { it.delete() }, isCancelled)
      val muxed = File(workDir, "merging.mp4")
      if (!muxed.isFile || muxed.length() == 0L) throw DownloadException("Media merge produced no output")
      out.delete()
      if (!muxed.renameTo(out)) throw DownloadException("Could not finalize merged download")
    } catch (e: MediaMuxHelper.Cancelled) {
      File(workDir, "merging.mp4").delete()
      throw e
    }
    candidates.forEach { runCatching { it.delete() } }
    return out
  }

  private class Inspected(
    val file: File,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
  )

  private fun inspect(file: File): Inspected? =
    runCatching {
      val extractor = MediaExtractor()
      try {
        extractor.setDataSource(file.absolutePath)
        var hasVideo = false
        var hasAudio = false
        repeat(extractor.trackCount) { index ->
          val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
          if (mime.startsWith("video/")) hasVideo = true
          if (mime.startsWith("audio/")) hasAudio = true
        }
        Inspected(file, hasVideo, hasAudio).takeIf { it.hasVideo || it.hasAudio }
      } finally {
        extractor.release()
      }
    }.onFailure { Log.w(TAG, "Could not inspect ${file.name}", it) }.getOrNull()

  private fun toBytes(
    number: String,
    unit: String,
  ): Long {
    val value = number.toDoubleOrNull() ?: return 0L
    val factor =
      when (unit.uppercase()) {
        "B" -> 1.0
        "KIB" -> 1024.0
        "MIB" -> 1024.0 * 1024
        "GIB" -> 1024.0 * 1024 * 1024
        "TIB" -> 1024.0 * 1024 * 1024 * 1024
        "KB" -> 1000.0
        "MB" -> 1_000_000.0
        "GB" -> 1_000_000_000.0
        else -> 1.0
      }
    return (value * factor).toLong()
  }

  private fun parseEta(text: String): Long {
    val parts = text.split(':').mapNotNull { it.toLongOrNull() }
    if (parts.isEmpty()) return -1L
    return parts.fold(0L) { acc, part -> acc * 60 + part }
  }
}
