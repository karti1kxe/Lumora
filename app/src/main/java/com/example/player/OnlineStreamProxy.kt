package com.example.player

import android.util.Log
import com.example.util.OnlineVideoService
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Tiny HTTP server that only listens on this phone (127.0.0.1). The video player (mpv) opens
 * `http://127.0.0.1:<port>/s/<token>` and this proxy fetches the REAL YouTube address in small Range chunks with
 * OkHttp - exactly the way the Online Music downloader ([OnlineAudioCache]) does, which is the way YouTube accepts.
 *
 * Why it is needed: mpv asks googlevideo for the whole file in one open-ended request (and needs an HTTPS
 * certificate list). YouTube answers such requests with 403/throttling, so the video never started and only a
 * spinner was visible. Through this proxy mpv speaks plain local HTTP and YouTube only ever sees normal chunked
 * requests. Seeking works too: mpv's Range requests are translated into upstream Range chunks.
 */
object OnlineStreamProxy {
    private const val TAG = "OnlineStreamProxy"
    private const val CHUNK_BYTES = 2L * 1024L * 1024L
    private const val MAX_ENTRIES = 96
    private const val MAX_CHUNK_FAILURES = 4

    private class Entry(
        val upstream: String,
        val rangeCapable: Boolean,
        val mime: String
    ) {
        @Volatile
        var total: Long = -1L
    }

    private val entries = ConcurrentHashMap<String, Entry>()
    private val order = ArrayList<String>()
    private val pool = Executors.newCachedThreadPool()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile
    private var server: ServerSocket? = null

    private class ClientGone : RuntimeException()

    @Synchronized
    private fun ensureStarted(): Int? {
        server?.let { if (!it.isClosed) return it.localPort }
        return try {
            val s = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            server = s
            Thread({
                while (!s.isClosed) {
                    try {
                        val socket = s.accept()
                        pool.execute { handle(socket) }
                    } catch (_: Throwable) {
                        if (s.isClosed) break
                    }
                }
            }, "OnlineStreamProxy-accept").apply { isDaemon = true }.start()
            s.localPort
        } catch (t: Throwable) {
            Log.e(TAG, "Could not start proxy: ${t.message}")
            null
        }
    }

    /**
     * Local address that mpv can open for [upstream]. Returns [upstream] itself if the proxy cannot start,
     * so playback is still attempted directly.
     */
    fun urlFor(
        upstream: String,
        rangeCapable: Boolean = true,
        mime: String = "application/octet-stream",
        suffix: String = ""
    ): String {
        val port = ensureStarted() ?: return upstream
        val token = UUID.randomUUID().toString().replace("-", "")
        synchronized(order) {
            // YouTube states the real container type in the address itself (mime=video%2Fwebm ...).
            val urlMime = try {
                Regex("[?&]mime=([^&]+)").find(upstream)?.groupValues?.get(1)
                    ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            } catch (_: Throwable) { null }
            entries[token] = Entry(upstream, rangeCapable, if (rangeCapable && !urlMime.isNullOrBlank()) urlMime else mime)
            order.add(token)
            while (order.size > MAX_ENTRIES) entries.remove(order.removeAt(0))
        }
        return "http://127.0.0.1:$port/s/$token$suffix"
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
            if (sb.length > 8192) return null
        }
        return sb.toString()
    }

    private fun write(out: OutputStream, bytes: ByteArray, len: Int = bytes.size) {
        try {
            out.write(bytes, 0, len)
        } catch (_: IOException) {
            throw ClientGone()
        }
    }

    private fun sendStatus(out: OutputStream, status: String, extra: String = "") {
        val head = "HTTP/1.1 $status\r\nContent-Length: 0\r\nConnection: close\r\n$extra\r\n"
        try {
            out.write(head.toByteArray(Charsets.ISO_8859_1))
            out.flush()
        } catch (_: IOException) {}
    }

    private fun handle(socket: Socket) {
        try {
            socket.soTimeout = 30_000
            val input = socket.getInputStream()
            val out = socket.getOutputStream()
            val requestLine = readLine(input) ?: return
            val headers = HashMap<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val i = line.indexOf(':')
                if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
            }
            socket.soTimeout = 0

            val parts = requestLine.split(' ')
            val method = parts.getOrNull(0) ?: return
            val path = parts.getOrNull(1) ?: return
            val token = path.removePrefix("/s/").substringBefore('?').substringBefore('.')
            val entry = entries[token]
            if (entry == null) {
                sendStatus(out, "404 Not Found")
                return
            }
            if (method != "GET" && method != "HEAD") {
                sendStatus(out, "405 Method Not Allowed")
                return
            }
            if (entry.rangeCapable) serveRanged(out, entry, method == "HEAD", headers["range"])
            else servePlain(out, entry, method == "HEAD")
        } catch (_: ClientGone) {
            // mpv closed the connection (seek / quality switch): normal.
        } catch (t: Throwable) {
            Log.w(TAG, "Connection failed: ${t.message}")
        } finally {
            try { socket.close() } catch (_: Throwable) {}
        }
    }

    private fun upstreamRequest(entry: Entry): Request.Builder =
        Request.Builder()
            .url(entry.upstream)
            .header("User-Agent", OnlineVideoService.userAgentFor(entry.upstream))

    /** Full size of the upstream file: from YouTube's own `clen` parameter, otherwise one tiny ranged probe. */
    private fun totalLength(entry: Entry): Long {
        if (entry.total > 0L) return entry.total
        Regex("[?&]clen=(\\d+)").find(entry.upstream)?.groupValues?.get(1)?.toLongOrNull()?.let {
            if (it > 0L) {
                entry.total = it
                return it
            }
        }
        var lastError: Throwable? = null
        repeat(3) {
            try {
                client.newCall(upstreamRequest(entry).header("Range", "bytes=0-1").build()).execute().use { resp ->
                    val fromRange = resp.header("Content-Range")?.substringAfterLast('/')?.trim()?.toLongOrNull()
                    val length = fromRange ?: if (resp.code == 200) resp.body?.contentLength() else null
                    if (resp.isSuccessful && length != null && length > 0L) {
                        entry.total = length
                        return length
                    }
                    lastError = IOException("HTTP ${resp.code}")
                }
            } catch (t: Throwable) {
                lastError = t
            }
            try { Thread.sleep(400) } catch (_: InterruptedException) {}
        }
        Log.w(TAG, "Could not read the size of the stream: ${lastError?.message}")
        return -1L
    }

    private fun serveRanged(out: OutputStream, entry: Entry, headOnly: Boolean, rangeHeader: String?) {
        val total = totalLength(entry)
        if (total <= 0L) {
            sendStatus(out, "502 Bad Gateway")
            return
        }
        var start = 0L
        var end = total - 1
        var partial = false
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            val spec = rangeHeader.removePrefix("bytes=").substringBefore(',').trim()
            val s = spec.substringBefore('-').trim()
            val e = spec.substringAfter('-', "").trim()
            if (s.isEmpty()) {
                val n = e.toLongOrNull() ?: 0L
                start = (total - n).coerceAtLeast(0L)
            } else {
                start = s.toLongOrNull() ?: 0L
                if (e.isNotEmpty()) end = minOf(e.toLongOrNull() ?: end, total - 1)
            }
            partial = true
        }
        if (start >= total || start > end) {
            sendStatus(out, "416 Range Not Satisfiable", "Content-Range: bytes */$total\r\n")
            return
        }

        val head = StringBuilder()
        head.append(if (partial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
        head.append("Content-Type: ").append(entry.mime).append("\r\n")
        head.append("Accept-Ranges: bytes\r\n")
        head.append("Content-Length: ").append(end - start + 1).append("\r\n")
        if (partial) head.append("Content-Range: bytes ").append(start).append('-').append(end).append('/').append(total).append("\r\n")
        head.append("Connection: close\r\n\r\n")
        write(out, head.toString().toByteArray(Charsets.ISO_8859_1))
        if (headOnly) return

        var pos = start
        var failures = 0
        val buffer = ByteArray(64 * 1024)
        while (pos <= end) {
            val chunkEnd = minOf(pos + CHUNK_BYTES - 1, end)
            try {
                client.newCall(upstreamRequest(entry).header("Range", "bytes=$pos-$chunkEnd").build()).execute().use { resp ->
                    if (resp.code != 206 && resp.code != 200) throw IOException("HTTP ${resp.code}")
                    val body = resp.body ?: throw IOException("Empty body")
                    val upstreamIn = body.byteStream()
                    if (resp.code == 200 && pos > 0L) {
                        // The server ignored Range and sends the file from the beginning: skip to our position.
                        var toSkip = pos
                        while (toSkip > 0L) {
                            val skipped = upstreamIn.skip(toSkip)
                            if (skipped <= 0L) {
                                if (upstreamIn.read() < 0) throw IOException("Stream ended while skipping")
                                toSkip--
                            } else toSkip -= skipped
                        }
                    }
                    var remaining = chunkEnd - pos + 1
                    while (remaining > 0L) {
                        val n = upstreamIn.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (n < 0) break
                        write(out, buffer, n)
                        pos += n
                        remaining -= n
                        failures = 0
                    }
                    if (remaining > 0L) throw IOException("Chunk ended early")
                }
            } catch (e: ClientGone) {
                throw e
            } catch (e: IOException) {
                failures++
                Log.w(TAG, "Chunk at $pos failed (attempt $failures): ${e.message}")
                if (failures >= MAX_CHUNK_FAILURES) return
                try { Thread.sleep(500L * failures) } catch (_: InterruptedException) { return }
            }
        }
        try { out.flush() } catch (_: IOException) {}
    }

    /** For small files without Range support (subtitles): the whole body is passed through. */
    private fun servePlain(out: OutputStream, entry: Entry, headOnly: Boolean) {
        try {
            client.newCall(upstreamRequest(entry).build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    sendStatus(out, "502 Bad Gateway")
                    return
                }
                val body = resp.body ?: run {
                    sendStatus(out, "502 Bad Gateway")
                    return
                }
                val bytes = body.bytes()
                val head = "HTTP/1.1 200 OK\r\nContent-Type: ${entry.mime}\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                write(out, head.toByteArray(Charsets.ISO_8859_1))
                if (!headOnly) write(out, bytes)
                try { out.flush() } catch (_: IOException) {}
            }
        } catch (e: ClientGone) {
            throw e
        } catch (e: IOException) {
            sendStatus(out, "502 Bad Gateway")
        }
    }
}
