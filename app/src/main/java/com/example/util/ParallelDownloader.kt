package com.example.util

import android.util.Log
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Multi-connection HTTP(S) file downloader used by the Download page.
 *
 * A single connection is capped by the server (YouTube hands out only a few MB/s per connection), which is why
 * downloads stayed at 4-5 MB/s on a 60 MB/s line. The file is split into [CHUNK_BYTES] ranges that [MAX_CONNECTIONS]
 * connections fetch in parallel, always taking the lowest unfinished range first, so the start of the file is complete
 * long before the end (that part can already be played).
 *
 * Resume: finished ranges are recorded in "<name>.lstate" next to the "<name>.lpart" data file. After a pause, a crash,
 * a restart of the app or a lost connection the download continues with the missing ranges only - never from zero.
 */
object ParallelDownloader {
    private const val TAG = "ParallelDownloader"
    private const val CHUNK_BYTES = 4L * 1024 * 1024
    private const val MAX_CONNECTIONS = 16
    private const val CHUNK_RETRIES = 6
    const val PART_EXT = "lpart"
    const val STATE_EXT = "lstate"

    /** The user paused / cancelled the download. */
    class CancelledException : RuntimeException("cancelled")

    /** The server does not support byte ranges (or the link is not a plain file): use the normal downloader instead. */
    class UnsupportedException(message: String) : RuntimeException(message)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // one TCP connection per range request: HTTP/2 would squeeze all of them through ONE connection again
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectionPool(ConnectionPool(MAX_CONNECTIONS + 4, 2, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun partFile(dest: File): File = File(dest.parentFile, dest.name + "." + PART_EXT)
    private fun stateFile(dest: File): File = File(dest.parentFile, dest.name + "." + STATE_EXT)

    private fun request(url: String, headers: Map<String, String>, range: String): Request {
        val b = Request.Builder().url(url)
        for ((k, v) in headers) {
            if (k.isBlank() || v.isBlank()) continue
            if (k.equals("Range", true) || k.equals("Accept-Encoding", true) || k.equals("Host", true)) continue
            try { b.header(k, v) } catch (_: Throwable) {}
        }
        // identity: a gzip-transparent response would make the byte ranges meaningless
        b.header("Accept-Encoding", "identity")
        b.header("Range", range)
        return b.build()
    }

    /** Size of the file (and a check that ranges work) from a 1-byte range request. */
    private fun probeSize(url: String, headers: Map<String, String>): Long {
        client.newCall(request(url, headers, "bytes=0-0")).execute().use { resp ->
            if (resp.code == 206) {
                val total = resp.header("Content-Range")?.substringAfterLast('/')?.trim()?.toLongOrNull()
                if (total != null && total > 0L) return total
                throw UnsupportedException("unknown file size")
            }
            if (resp.code == 200) throw UnsupportedException("server ignores byte ranges")
            if (resp.code == 403 || resp.code == 404 || resp.code == 410) throw java.io.IOException("HTTP ${resp.code} (link expired?)")
            throw UnsupportedException("HTTP ${resp.code}")
        }
    }

    private class State(val total: Long, val chunks: Int) {
        val done = BooleanArray(chunks)
    }

    private fun readState(file: File, total: Long, chunks: Int): State {
        val st = State(total, chunks)
        try {
            if (file.exists()) {
                val lines = file.readLines()
                if (lines.size >= 2 && lines[0] == "$total|$CHUNK_BYTES") {
                    val bits = lines[1]
                    for (i in 0 until minOf(chunks, bits.length)) st.done[i] = bits[i] == '1'
                }
            }
        } catch (_: Throwable) {
        }
        return st
    }

    private fun writeState(file: File, st: State) {
        try {
            val sb = StringBuilder(st.chunks)
            synchronized(st) { for (b in st.done) sb.append(if (b) '1' else '0') }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText("${st.total}|$CHUNK_BYTES\n$sb\n")
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        } catch (_: Throwable) {
        }
    }

    /** Bytes of the file that are downloaded in one piece from the start (what a player can already read). */
    fun contiguousBytes(dest: File): Long {
        return try {
            val lines = stateFile(dest).readLines()
            val head = lines[0].split('|')
            val total = head[0].toLong()
            val bits = lines[1]
            var n = 0
            while (n < bits.length && bits[n] == '1') n++
            minOf(total, n * CHUNK_BYTES)
        } catch (_: Throwable) { 0L }
    }

    /**
     * Downloads [url] into [dest]. [live] is increased with every byte that arrives (and already holds the bytes of an
     * earlier run when resuming); [onTotal] reports the real file size. Returns when [dest] is complete.
     * Throws [CancelledException], [UnsupportedException] (before anything was written) or an IOException.
     */
    fun download(
        url: String,
        headers: Map<String, String>,
        dest: File,
        live: AtomicLong,
        onTotal: (Long) -> Unit,
        isCancelled: () -> Boolean
    ) {
        if (dest.exists() && dest.length() > 0L) {
            // an earlier run finished this file already
            live.addAndGet(dest.length())
            onTotal(dest.length())
            return
        }
        dest.parentFile?.mkdirs()
        val total = probeSize(url, headers)
        onTotal(total)
        val chunks = ((total + CHUNK_BYTES - 1) / CHUNK_BYTES).toInt()
        val part = partFile(dest)
        val stFile = stateFile(dest)
        var st = readState(stFile, total, chunks)
        if (!part.exists() || part.length() == 0L) st = State(total, chunks)

        var already = 0L
        for (i in 0 until chunks) if (st.done[i]) already += chunkLength(i, total)
        live.addAndGet(already)

        val next = AtomicInteger(0)
        val failure = AtomicReference<Throwable?>(null)
        val stop = java.util.concurrent.atomic.AtomicBoolean(false)
        val workers = minOf(MAX_CONNECTIONS, chunks).coerceAtLeast(1)

        RandomAccessFile(part, "rw").use { raf ->
            val channel = raf.channel
            val threads = ArrayList<Thread>()
            val lock = Any()
            for (w in 0 until workers) {
                val t = Thread({
                    try {
                        while (!stop.get()) {
                            if (isCancelled()) throw CancelledException()
                            val index = next.getAndIncrement()
                            if (index >= chunks) break
                            if (st.done[index]) continue
                            fetchChunk(url, headers, index, total, channel, live, isCancelled, stop)
                            synchronized(st) { st.done[index] = true }
                            synchronized(lock) { writeState(stFile, st) }
                        }
                    } catch (t: Throwable) {
                        if (failure.compareAndSet(null, t)) stop.set(true)
                    }
                }, "lumora-dl-$w")
                t.isDaemon = true
                threads.add(t)
                t.start()
            }
            for (t in threads) t.join()
            val err = failure.get()
            if (err != null) {
                writeState(stFile, st)
                throw err
            }
            try { channel.force(false) } catch (_: Throwable) {}
        }

        // every range is there: the part file becomes the real file
        if (part.length() != total) {
            RandomAccessFile(part, "rw").use { it.setLength(total) }
        }
        if (dest.exists()) dest.delete()
        if (!part.renameTo(dest)) {
            part.copyTo(dest, overwrite = true)
            part.delete()
        }
        stFile.delete()
    }

    private fun chunkLength(index: Int, total: Long): Long {
        val start = index * CHUNK_BYTES
        return minOf(CHUNK_BYTES, total - start)
    }

    private fun fetchChunk(
        url: String,
        headers: Map<String, String>,
        index: Int,
        total: Long,
        channel: java.nio.channels.FileChannel,
        live: AtomicLong,
        isCancelled: () -> Boolean,
        stop: java.util.concurrent.atomic.AtomicBoolean
    ) {
        val start = index * CHUNK_BYTES
        val end = start + chunkLength(index, total) - 1
        var attempt = 0
        val buf = ByteArray(128 * 1024)
        while (true) {
            var got = 0L
            try {
                client.newCall(request(url, headers, "bytes=$start-$end")).execute().use { resp ->
                    if (resp.code != 206) {
                        if (resp.code == 403 || resp.code == 404 || resp.code == 410) {
                            // the link expired: the caller resolves a fresh one and continues
                            attempt = CHUNK_RETRIES
                        }
                        throw java.io.IOException("HTTP ${resp.code}")
                    }
                    val body = resp.body ?: throw java.io.IOException("empty response")
                    body.byteStream().use { input ->
                        var pos = start
                        while (pos <= end) {
                            if (stop.get() || isCancelled()) throw CancelledException()
                            val want = minOf(buf.size.toLong(), end - pos + 1).toInt()
                            val n = input.read(buf, 0, want)
                            if (n < 0) throw java.io.IOException("connection closed early")
                            val bb = ByteBuffer.wrap(buf, 0, n)
                            var written = 0
                            while (written < n) written += channel.write(bb, pos + written)
                            pos += n
                            got += n
                            live.addAndGet(n.toLong())
                        }
                    }
                }
                return
            } catch (c: CancelledException) {
                live.addAndGet(-got)
                throw c
            } catch (t: Throwable) {
                live.addAndGet(-got)
                attempt++
                if (stop.get() || isCancelled()) throw CancelledException()
                if (attempt >= CHUNK_RETRIES) throw t
                Log.w(TAG, "chunk $index retry $attempt: ${t.message}")
                try { Thread.sleep(400L * attempt) } catch (_: InterruptedException) { throw CancelledException() }
            }
        }
    }
}
