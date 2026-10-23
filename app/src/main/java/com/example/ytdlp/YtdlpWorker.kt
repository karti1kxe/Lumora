/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Lumora addition on top of the mpvRx yt-dlp runtime (AGPL-3.0-or-later).
 *
 * Starting `yt-dlp` costs several seconds on a phone (Python start + importing yt-dlp), and that was paid for EVERY quality
 * list. The worker keeps ONE Python process alive with yt-dlp already imported (assets/ytdl/ytdlp_worker.py) and answers
 * link-info requests over stdin / stdout, so only the very first request pays the start-up. It is stopped after a few idle
 * minutes and every failure of the worker itself falls back to the normal one-shot yt-dlp command.
 */

package com.example.ytdlp

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** The worker itself could not answer (not started, timed out, rejected an option): the caller runs yt-dlp normally. */
class YtdlpWorkerUnavailable(message: String) : RuntimeException(message)

object YtdlpWorker {
  private const val TAG = "YtdlpWorker"
  private const val IDLE_MS = 8 * 60_000L
  private const val START_TIMEOUT_S = 75L
  private const val EOF_MARK = "\u0000EOF"

  private class Session(
    val process: Process,
    val writer: BufferedWriter,
    val lines: LinkedBlockingQueue<String>,
  )

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val gate = Mutex()
  private val ids = AtomicInteger(1)

  @Volatile private var session: Session? = null

  @Volatile private var lastUsed = 0L

  @Volatile private var disabledUntil = 0L
  private var watchdog: Job? = null

  /** Starts the worker in the background (app start / Network tab / pasted link) so the first quality list is already warm. */
  fun prewarm(context: Context, installIfMissing: Boolean = true) {
    if (!BuildConfig.ENABLE_YTDLP || SystemClock.elapsedRealtime() < disabledUntil) return
    val app = context.applicationContext
    scope.launch {
      try {
        if (!installIfMissing && !YtdlpManager.isInstalled(app)) return@launch
        if (!YtdlpManager.ensureRuntimeInstalled(app)) return@launch
        gate.withLock { ensureSession(app) }
      } catch (_: Throwable) {
      }
    }
  }

  /** Answers one `yt-dlp -J` request. [argv] is the command line WITHOUT the executable, the script and the link. */
  suspend fun request(
    context: Context,
    argv: List<String>,
    url: String,
    timeoutMs: Long = 100_000L,
  ): Result<String> =
    withContext(Dispatchers.IO) {
      if (!BuildConfig.ENABLE_YTDLP) return@withContext Result.failure(YtdlpWorkerUnavailable("yt-dlp is disabled"))
      if (SystemClock.elapsedRealtime() < disabledUntil) {
        return@withContext Result.failure(YtdlpWorkerUnavailable("worker is paused"))
      }
      val app = context.applicationContext
      if (!YtdlpManager.ensureRuntimeInstalled(app)) {
        return@withContext Result.failure(YtdlpWorkerUnavailable("yt-dlp is not installed"))
      }
      gate.withLock {
        val s = ensureSession(app) ?: return@withLock Result.failure(YtdlpWorkerUnavailable("worker did not start"))
        exchange(s, argv, url, timeoutMs)
      }
    }

  /** Stops the worker (after an update, so the next request imports the new yt-dlp). */
  fun stop() {
    session?.let { kill(it) }
  }

  private suspend fun exchange(
    s: Session,
    argv: List<String>,
    url: String,
    timeoutMs: Long,
  ): Result<String> {
    val id = ids.getAndIncrement()
    val request = JSONObject().put("id", id).put("argv", JSONArray(argv)).put("url", url).toString()
    try {
      s.writer.write(request)
      s.writer.write("\n")
      s.writer.flush()
    } catch (e: IOException) {
      kill(s)
      return Result.failure(YtdlpWorkerUnavailable("worker pipe closed"))
    }
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    while (true) {
      val left = deadline - SystemClock.elapsedRealtime()
      val line = if (left <= 0L) null else runInterruptible { s.lines.poll(left, TimeUnit.MILLISECONDS) }
      if (line == null) {
        kill(s)
        return Result.failure(YtdlpWorkerUnavailable("yt-dlp took too long"))
      }
      if (line == EOF_MARK) {
        kill(s)
        return Result.failure(YtdlpWorkerUnavailable("worker stopped"))
      }
      val reply = try { JSONObject(line) } catch (_: Exception) { continue }
      // an answer to an earlier request that was given up (timeout / cancelled) is skipped
      if (reply.optInt("id", -1) != id) continue
      lastUsed = SystemClock.elapsedRealtime()
      return if (reply.optBoolean("ok")) {
        Result.success(reply.optString("json"))
      } else {
        val message = reply.optString("error").ifBlank { "yt-dlp could not read this link" }
        Result.failure(
          if (reply.optBoolean("infra")) YtdlpWorkerUnavailable(message) else IllegalStateException(message),
        )
      }
    }
  }

  /** Blocking; called with [gate] held. */
  private fun ensureSession(app: Context): Session? {
    session?.let { if (it.process.isAlive) return it else session = null }
    if (!YtdlpManager.isInstalled(app)) return null
    val ytdlDir = YtdlpManager.getYtdlDir(app)
    val script = File(ytdlDir, "ytdlp_worker.py")
    if (!script.isFile) return null
    val command = listOf(YtdlpManager.getExecutablePath(app), script.absolutePath, File(ytdlDir, "yt-dlp").absolutePath)
    val process =
      try {
        YtdlpManager.startPythonProcess(command, app, mergeStderr = false)
      } catch (e: Exception) {
        Log.w(TAG, "worker could not start", e)
        disabledUntil = SystemClock.elapsedRealtime() + 10 * 60_000L
        return null
      }
    val lines = LinkedBlockingQueue<String>()
    Thread(
      {
        try {
          process.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { lines.put(it) }
        } catch (_: Exception) {
        } finally {
          lines.offer(EOF_MARK)
        }
      },
      "ytdlp-worker-out",
    ).apply { isDaemon = true }.start()
    // stderr must be drained or a chatty yt-dlp would block on a full pipe
    Thread(
      {
        try {
          process.errorStream.bufferedReader(Charsets.UTF_8).forEachLine { }
        } catch (_: Exception) {
        }
      },
      "ytdlp-worker-err",
    ).apply { isDaemon = true }.start()

    val first = try { lines.poll(START_TIMEOUT_S, TimeUnit.SECONDS) } catch (_: InterruptedException) { null }
    val ready = first != null && runCatching { JSONObject(first).optBoolean("ready") }.getOrDefault(false)
    if (!ready) {
      Log.w(TAG, "worker did not become ready: $first")
      process.destroyForcibly()
      disabledUntil = SystemClock.elapsedRealtime() + 10 * 60_000L
      return null
    }
    val s = Session(process, BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8)), lines)
    session = s
    lastUsed = SystemClock.elapsedRealtime()
    startWatchdog()
    return s
  }

  private fun kill(s: Session) {
    if (session === s) session = null
    runCatching { s.writer.close() }
    runCatching { s.process.destroyForcibly() }
  }

  private fun startWatchdog() {
    if (watchdog?.isActive == true) return
    watchdog =
      scope.launch {
        while (isActive) {
          delay(60_000L)
          val s = session ?: break
          if (SystemClock.elapsedRealtime() - lastUsed > IDLE_MS && !gate.isLocked) kill(s)
        }
      }
  }
}
