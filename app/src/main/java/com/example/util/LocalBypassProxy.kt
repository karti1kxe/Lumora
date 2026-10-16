package com.example.util

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Minimal HTTP proxy for the app's own traffic (127.0.0.1 only): CONNECT tunnels (https) and absolute-URI requests (http).
 * Resolves host names itself (DoH when [dohUrl] is set) and, when [fragment] is on, sends the first packet of the
 * connection in two pieces cut inside the site name.
 */
class LocalBypassProxy {
    @Volatile var fragment: Boolean = true
    @Volatile var tlsRecordSplit: Boolean = true
    @Volatile var dohUrl: String? = null

    private var server: ServerSocket? = null
    @Volatile private var running = false
    val port: Int get() = server?.localPort ?: 0

    private val seq = AtomicInteger(0)
    private val pool = Executors.newCachedThreadPool(ThreadFactory { r ->
        Thread(r, "lumora-bypass-" + seq.incrementAndGet()).apply { isDaemon = true }
    })
    private val dnsCache = ConcurrentHashMap<String, Pair<List<InetAddress>, Long>>()
    /** Hosts that refused the split connection: they get a plain connection straight away (host -> until when). */
    private val noSplitUntil = ConcurrentHashMap<String, Long>()
    /** While the DoH server itself cannot be reached, host names are not sent to it (every new connection would wait for its timeout). */
    @Volatile private var dohBlockedUntil = 0L

    fun isRunning(): Boolean = running && server?.isClosed == false

    fun start() {
        val s = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
        server = s
        running = true
        val t = Thread({
            while (running) {
                val c = try { s.accept() } catch (_: Throwable) { break }
                pool.execute { handleClient(c) }
            }
            running = false
        }, "lumora-bypass-accept")
        t.isDaemon = true
        t.start()
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Throwable) {}
        server = null
    }

    // ------------------------------------------------------------------ client handling

    private fun handleClient(client: Socket) {
        var upstream: Socket? = null
        try {
            client.tcpNoDelay = true
            client.soTimeout = 20_000
            val cin = client.getInputStream()
            val cout = client.getOutputStream()
            val head = readHead(cin) ?: return
            val headText = String(head.bytes, 0, head.headEnd, Charsets.ISO_8859_1)
            val firstLine = headText.substringBefore("\r\n")
            val parts = firstLine.split(' ')
            if (parts.size < 3) { cout.write("HTTP/1.1 400 Bad Request\r\n\r\n".toByteArray()); return }
            val method = parts[0]
            val target = parts[1]
            val version = parts[2]
            client.soTimeout = 0
            client.keepAlive = true

            if (method.equals("CONNECT", true)) {
                val host = target.substringBeforeLast(':').trim('[', ']')
                val port = target.substringAfterLast(':', "443").toIntOrNull() ?: 443
                cout.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                cout.flush()
                // Whatever the client already sent after the CONNECT head (normally nothing) is the first payload.
                var first = head.bytes.copyOfRange(head.headEnd + 4, head.length)
                if (first.isEmpty()) first = readFirstPayload(cin, port) ?: return
                upstream = tunnel(host, port, first, client, cout)
                if (upstream == null) return
                relay(client, upstream)
            } else {
                val url = try { URL(target) } catch (_: Throwable) { null }
                if (url == null || !url.protocol.equals("http", true)) {
                    cout.write("HTTP/1.1 400 Bad Request\r\n\r\n".toByteArray()); return
                }
                val host = url.host
                val port = if (url.port > 0) url.port else 80
                val path = (url.file.ifEmpty { "/" })
                // Origin-form request line, one request per connection (the proxy cannot tell where the next one goes).
                val sb = StringBuilder()
                sb.append(method).append(' ').append(path).append(' ').append(version).append("\r\n")
                for (line in headText.substringAfter("\r\n", "").split("\r\n")) {
                    if (line.isEmpty()) continue
                    val name = line.substringBefore(':').trim().lowercase()
                    if (name == "proxy-connection" || name == "connection" || name == "proxy-authorization") continue
                    sb.append(line).append("\r\n")
                }
                sb.append("Connection: close\r\n\r\n")
                val rewritten = sb.toString().toByteArray(Charsets.ISO_8859_1)
                val body = head.bytes.copyOfRange(head.headEnd + 4, head.length)
                val payload = rewritten + body
                upstream = tunnel(host, port, payload, client, cout, httpHostSplit = true)
                if (upstream == null) return
                relay(client, upstream)
            }
        } catch (_: Throwable) {
        } finally {
            try { upstream?.close() } catch (_: Throwable) {}
            try { client.close() } catch (_: Throwable) {}
        }
    }

    private class Head(val bytes: ByteArray, val length: Int, val headEnd: Int)

    private fun readHead(input: InputStream): Head? {
        val buf = ByteArray(16 * 1024)
        var n = 0
        while (n < buf.size) {
            val r = input.read(buf, n, buf.size - n)
            if (r < 0) return null
            n += r
            val end = indexOfHeadEnd(buf, n)
            if (end >= 0) return Head(buf, n, end)
        }
        return null
    }

    private fun indexOfHeadEnd(b: ByteArray, n: Int): Int {
        for (i in 0..n - 4) {
            if (b[i] == 13.toByte() && b[i + 1] == 10.toByte() && b[i + 2] == 13.toByte() && b[i + 3] == 10.toByte()) return i
        }
        return -1
    }

    /** After "200 Connection Established" the client sends its first bytes (the TLS ClientHello): read the whole first record. */
    private fun readFirstPayload(input: InputStream, port: Int): ByteArray? {
        val buf = ByteArray(16 * 1024 + 8)
        var n = input.read(buf, 0, buf.size)
        if (n <= 0) return null
        if (buf[0] == 0x16.toByte() && n >= 5) {
            val need = 5 + (((buf[3].toInt() and 0xFF) shl 8) or (buf[4].toInt() and 0xFF))
            while (n < need && n < buf.size) {
                val r = input.read(buf, n, minOf(buf.size - n, need - n))
                if (r < 0) break
                n += r
            }
        }
        return buf.copyOf(n)
    }

    /**
     * Connects to the site and sends [first] (split when AntiDPI is on). When the split connection gets no answer at all
     * the same request is repeated once as a plain connection. Returns the upstream socket once the site has answered
     * (its first bytes are already written to the client).
     */
    private fun tunnel(host: String, port: Int, first: ByteArray, client: Socket, cout: OutputStream, httpHostSplit: Boolean = false): Socket? {
        val direct = isLocalHost(host) && hostsOverride[host.lowercase()] == null
        val hostKey = host.lowercase()
        val splitAllowed = fragment && !direct && (noSplitUntil[hostKey] ?: 0L) < System.currentTimeMillis()
        val tries = if (splitAllowed) 2 else 1
        val addrs = resolve(host, direct)
        if (addrs.isEmpty()) return null
        for (attempt in 0 until tries) {
            val splitNow = splitAllowed && attempt == 0
            val s = connectAny(addrs, port) ?: return null
            try {
                s.tcpNoDelay = true
                s.keepAlive = true
                val out = s.getOutputStream()
                if (splitNow) sendSplit(out, first, httpHostSplit) else { out.write(first); out.flush() }
                // The first answer decides: a connection that is reset / silent right after the split gets one plain retry.
                // A TLS server answers at once; an HTTP server may need longer, and repeating that request would only double it.
                val tlsFirst = first.isNotEmpty() && first[0] == 0x16.toByte()
                s.soTimeout = if (attempt < tries - 1 && tlsFirst) 6_000 else 30_000
                val buf = ByteArray(32 * 1024)
                val n = s.getInputStream().read(buf)
                if (n > 0) {
                    if (attempt > 0) noSplitUntil[hostKey] = System.currentTimeMillis() + 10 * 60_000L
                    s.soTimeout = 0
                    cout.write(buf, 0, n)
                    cout.flush()
                    return s
                }
                try { s.close() } catch (_: Throwable) {}
            } catch (t: Throwable) {
                try { s.close() } catch (_: Throwable) {}
            }
        }
        return null
    }

    private fun connectAny(addrs: List<InetAddress>, port: Int): Socket? {
        for (a in addrs) {
            val s = Socket()
            try {
                s.connect(InetSocketAddress(a, port), 8_000)
                return s
            } catch (_: Throwable) {
                try { s.close() } catch (_: Throwable) {}
            }
        }
        return null
    }

    private fun relay(client: Socket, upstream: Socket) {
        val up = pool.submit {
            try { copy(client.getInputStream(), upstream.getOutputStream()) } catch (_: Throwable) {}
            try { upstream.shutdownOutput() } catch (_: Throwable) {}
        }
        try { copy(upstream.getInputStream(), client.getOutputStream()) } catch (_: Throwable) {}
        try { client.close() } catch (_: Throwable) {}
        try { upstream.close() } catch (_: Throwable) {}
        try { up.cancel(true) } catch (_: Throwable) {}
    }

    private fun copy(input: InputStream, output: OutputStream) {
        val buf = ByteArray(32 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            output.write(buf, 0, n)
            output.flush()
        }
    }

    // ------------------------------------------------------------------ splitting

    private fun sendSplit(out: OutputStream, first: ByteArray, httpHostSplit: Boolean) {
        if (first.size < 8) { out.write(first); out.flush(); return }
        if (first[0] == 0x16.toByte() && first.size > 43) {
            val sni = findSniOffset(first)
            if (sni != null) {
                val cut = (sni.first + (sni.second / 2).coerceAtLeast(1)).coerceIn(6, first.size - 1)
                if (tlsRecordSplit) {
                    // Two TLS records (the handshake message is simply continued in the second one), each in its own packet.
                    val ver0 = first[1]; val ver1 = first[2]
                    val payloadCut = cut - 5
                    val r1 = ByteArray(5 + payloadCut)
                    r1[0] = 0x16; r1[1] = ver0; r1[2] = ver1
                    r1[3] = (payloadCut shr 8).toByte(); r1[4] = payloadCut.toByte()
                    System.arraycopy(first, 5, r1, 5, payloadCut)
                    val restLen = first.size - cut
                    val r2 = ByteArray(5 + restLen)
                    r2[0] = 0x16; r2[1] = ver0; r2[2] = ver1
                    r2[3] = (restLen shr 8).toByte(); r2[4] = restLen.toByte()
                    System.arraycopy(first, cut, r2, 5, restLen)
                    out.write(r1); out.flush()
                    pause()
                    out.write(r2); out.flush()
                } else {
                    out.write(first, 0, cut); out.flush()
                    pause()
                    out.write(first, cut, first.size - cut); out.flush()
                }
                return
            }
            out.write(first, 0, 1); out.flush()
            pause()
            out.write(first, 1, first.size - 1); out.flush()
            return
        }
        if (httpHostSplit) {
            val text = String(first, 0, minOf(first.size, 2048), Charsets.ISO_8859_1)
            val idx = text.indexOf("\r\nHost:", ignoreCase = true)
            if (idx > 0) {
                val cut = (idx + 8).coerceIn(1, first.size - 1)
                out.write(first, 0, cut); out.flush()
                pause()
                out.write(first, cut, first.size - cut); out.flush()
                return
            }
        }
        out.write(first, 0, 1); out.flush()
        pause()
        out.write(first, 1, first.size - 1); out.flush()
    }

    private fun pause() { try { Thread.sleep(4) } catch (_: InterruptedException) {} }

    /** Offset of the server name inside a TLS ClientHello record and its length, or null when there is none. */
    private fun findSniOffset(r: ByteArray): Pair<Int, Int>? {
        try {
            var p = 5
            if (r[p].toInt() != 1) return null           // handshake type: ClientHello
            p += 4                                        // type + 3 bytes length
            p += 2 + 32                                   // version + random
            p += 1 + (r[p].toInt() and 0xFF)              // session id
            p += 2 + (((r[p].toInt() and 0xFF) shl 8) or (r[p + 1].toInt() and 0xFF)) // cipher suites
            p += 1 + (r[p].toInt() and 0xFF)              // compression methods
            val extEnd = p + 2 + (((r[p].toInt() and 0xFF) shl 8) or (r[p + 1].toInt() and 0xFF))
            p += 2
            while (p + 4 <= extEnd && p + 4 <= r.size) {
                val type = ((r[p].toInt() and 0xFF) shl 8) or (r[p + 1].toInt() and 0xFF)
                val len = ((r[p + 2].toInt() and 0xFF) shl 8) or (r[p + 3].toInt() and 0xFF)
                if (type == 0) {
                    // list length (2), name type (1), name length (2), name
                    val nameLen = ((r[p + 7].toInt() and 0xFF) shl 8) or (r[p + 8].toInt() and 0xFF)
                    return Pair(p + 9, nameLen)
                }
                p += 4 + len
            }
        } catch (_: Throwable) {
        }
        return null
    }

    // ------------------------------------------------------------------ DNS

    private fun isLocalHost(host: String): Boolean {
        val h = host.lowercase()
        if (h == "localhost" || h.endsWith(".local")) return true
        if (h.startsWith("127.") || h.startsWith("10.") || h.startsWith("192.168.") || h == "::1") return true
        if (h.startsWith("172.")) {
            val second = h.split('.').getOrNull(1)?.toIntOrNull()
            if (second != null && second in 16..31) return true
        }
        return false
    }

    private fun isIpLiteral(host: String): Boolean =
        host.contains(':') || host.all { it.isDigit() || it == '.' }

    /** Fixed host -> address answers (used before any DNS lookup). */
    @Volatile var hostsOverride: Map<String, InetAddress> = emptyMap()

    private fun resolve(host: String, direct: Boolean): List<InetAddress> {
        hostsOverride[host.lowercase()]?.let { return listOf(it) }
        if (isIpLiteral(host)) return try { listOf(InetAddress.getByName(host)) } catch (_: Throwable) { emptyList() }
        val out = ArrayList<InetAddress>()
        val doh = dohUrl
        if (doh != null && !direct && System.currentTimeMillis() >= dohBlockedUntil) {
            val cached = dnsCache[host]
            if (cached != null && cached.second > System.currentTimeMillis()) return cached.first
            val viaDoh = dohLookup(doh, host)
            if (viaDoh == null) dohBlockedUntil = System.currentTimeMillis() + 2 * 60_000L
            else if (viaDoh.isNotEmpty()) {
                dnsCache[host] = Pair(viaDoh, System.currentTimeMillis() + 5 * 60_000L)
                out.addAll(viaDoh)
            }
        }
        // The system resolver is the fallback (and the only one for local names).
        try {
            for (a in InetAddress.getAllByName(host)) if (out.none { it.hostAddress == a.hostAddress }) out.add(a)
        } catch (_: Throwable) {
        }
        return out
    }

    /** The host's A records, an empty list when it has none, null when the DoH server could not be reached / answered badly. */
    private fun dohLookup(base: String, host: String): List<InetAddress>? {
        try {
            val sep = if (base.contains('?')) '&' else '?'
            val conn = URL("$base${sep}name=${java.net.URLEncoder.encode(host, "UTF-8")}&type=A").openConnection(Proxy.NO_PROXY) as HttpURLConnection
            conn.connectTimeout = 4_000
            conn.readTimeout = 4_000
            conn.setRequestProperty("Accept", "application/dns-json")
            try {
                if (conn.responseCode != 200) return null
                val text = conn.inputStream.use { ins ->
                    val bos = ByteArrayOutputStream()
                    ins.copyTo(bos)
                    bos.toString("UTF-8")
                }
                // {"Answer":[{"name":"x.","type":1,"TTL":300,"data":"1.2.3.4"}, ...]} - only the A records are needed
                val list = ArrayList<InetAddress>()
                for (m in Regex("\\{[^{}]*\\}").findAll(text.substringAfter("\"Answer\"", ""))) {
                    val obj = m.value
                    if (!Regex("\"type\"\\s*:\\s*1\\b").containsMatchIn(obj)) continue
                    val data = Regex("\"data\"\\s*:\\s*\"([0-9.]+)\"").find(obj)?.groupValues?.get(1) ?: continue
                    list.add(InetAddress.getByName(data))
                }
                return list
            } finally {
                conn.disconnect()
            }
        } catch (_: IOException) {
            return null
        } catch (_: Throwable) {
            return null
        }
    }
}
