/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Lumora addition (uses the libtorrent4j handling ported from mpvRx, AGPL-3.0).
 */

package com.example.torrent

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Hosts the libtorrent session of torrent DOWNLOADS in its own process (":torrent"). libtorrent is native code: if it
 * ever crashes, only this process dies - the app and its UI stay alive and the Download page simply retries. Progress
 * is handed over through a small state file next to the download folder (see [TorrentDownloadEngine]).
 */
class TorrentDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val stopped = ConcurrentHashMap.newKeySet<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val dirPath = intent?.getStringExtra(EXTRA_DIR)
        if (intent == null || dirPath.isNullOrBlank()) {
            stopIfIdle()
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_STOP -> {
                stopped.add(dirPath)
                jobs.remove(dirPath)?.cancel()
                stopIfIdle()
            }
            else -> {
                val source = intent.getStringExtra(EXTRA_SOURCE).orEmpty()
                val fileIndex = intent.getIntExtra(EXTRA_FILE_INDEX, -1)
                stopped.add(dirPath) // the previous run of this folder (if any) must end first
                jobs.remove(dirPath)?.cancel()
                stopped.remove(dirPath)
                jobs[dirPath] = scope.launch(start = CoroutineStart.LAZY) { run(dirPath, source, fileIndex) }
                    .also { it.start() }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun run(dirPath: String, source: String, fileIndex: Int) {
        val me = currentCoroutineContext()[Job]
        val dir = File(dirPath)
        val stateFile = TorrentDownloadEngine.stateFile(dir)
        var seq = 0L
        fun write(status: String, downloaded: Long = 0L, total: Long = 0L, speed: Long = 0L, phase: String? = null,
                  error: String? = null, file: String? = null) {
            try {
                val json = JSONObject()
                    .put("seq", ++seq)
                    .put("status", status)
                    .put("downloaded", downloaded)
                    .put("total", total)
                    .put("speed", speed)
                    .put("phase", phase ?: JSONObject.NULL)
                    .put("error", error ?: JSONObject.NULL)
                    .put("file", file ?: JSONObject.NULL)
                val tmp = File(stateFile.parentFile, stateFile.name + ".tmp")
                tmp.writeText(json.toString())
                if (!tmp.renameTo(stateFile)) { stateFile.writeText(json.toString()); tmp.delete() }
            } catch (_: Throwable) {}
        }
        try {
            write("running", phase = "Starting torrent engine…")
            val out = TorrentDownloadWorker.download(
                context = applicationContext,
                source = source,
                fileIndex = fileIndex,
                saveDir = dir,
                isCancelled = { stopped.contains(dirPath) || me?.isActive == false }
            ) { p ->
                write("running", p.downloadedBytes, p.totalBytes, p.speedBps, p.phase)
            }
            if (out == null) write("stopped")
            else write("done", file = out.relativeTo(dir).path)
        } catch (c: CancellationException) {
            write("stopped")
        } catch (e: TorrentStreamException) {
            write("error", error = e.message ?: "Couldn't download this torrent file.")
        } catch (t: Throwable) {
            Log.e(TAG, "torrent download failed", t)
            write("error", error = "Couldn't download this torrent file.")
        } finally {
            if (me != null) jobs.remove(dirPath, me)
            stopIfIdle()
        }
    }

    private fun stopIfIdle() {
        if (jobs.isEmpty()) stopSelf()
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TorrentDownloadService"
        const val ACTION_STOP = "com.example.torrent.STOP"
        const val EXTRA_DIR = "dir"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_FILE_INDEX = "file_index"

        fun start(context: Context, dir: File, source: String, fileIndex: Int) {
            context.startService(
                Intent(context, TorrentDownloadService::class.java)
                    .putExtra(EXTRA_DIR, dir.absolutePath)
                    .putExtra(EXTRA_SOURCE, source)
                    .putExtra(EXTRA_FILE_INDEX, fileIndex)
            )
        }

        fun stop(context: Context, dir: File) {
            try {
                context.startService(
                    Intent(context, TorrentDownloadService::class.java)
                        .setAction(ACTION_STOP)
                        .putExtra(EXTRA_DIR, dir.absolutePath)
                )
            } catch (_: Throwable) {}
        }
    }
}
