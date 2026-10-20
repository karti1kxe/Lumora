package com.example.util

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * Joins and tags media with Android's own MediaExtractor / MediaMuxer (no ffmpeg, nothing is re-encoded, so it is
 * fast and the app stays small):
 *  - [mux]: one video-only file + one audio-only file -> one MP4 / WebM file,
 *  - [remuxAudio] + [embedCoverAndTags]: a clean .m4a with the thumbnail (cover art), title and artist inside,
 *  - [embedMp3Cover]: cover art for mp3 files.
 */
object MediaMuxHelper {

    class Cancelled : RuntimeException("cancelled")

    /** Video-only [videoFile] + audio-only [audioFile] -> [out] (mp4 or webm, chosen from the out file's extension). */
    fun mux(videoFile: File, audioFile: File, out: File, isCancelled: () -> Boolean) =
        muxMulti(videoFile, listOf(audioFile), out, isCancelled)

    /**
     * One video + every audio file (several languages) -> [out]. All audio tracks are copied unchanged; a track this
     * phone's muxer refuses is skipped (the first audio must work). WebM keeps only the first audio.
     */
    fun muxMulti(videoFile: File, audioFiles: List<File>, out: File, isCancelled: () -> Boolean) {
        val isWebm = out.extension.equals("webm", ignoreCase = true)
        val format = if (isWebm) MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        val files = if (isWebm) audioFiles.take(1) else audioFiles
        val vEx = MediaExtractor()
        val aExs = ArrayList<MediaExtractor>()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            vEx.setDataSource(videoFile.absolutePath)
            val vTrack = firstTrack(vEx, "video/") ?: throw IllegalStateException("No video track in the downloaded video")
            vEx.selectTrack(vTrack)
            val vFormat = vEx.getTrackFormat(vTrack)

            muxer = MediaMuxer(out.absolutePath, format)
            if (vFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                try { muxer.setOrientationHint(vFormat.getInteger(MediaFormat.KEY_ROTATION)) } catch (_: Throwable) {}
            }
            val vOut = muxer.addTrack(vFormat)
            val audioOuts = ArrayList<Int>()
            var maxHint = maxOf(sizeHint(vFormat), 1 shl 20)
            for ((i, f) in files.withIndex()) {
                val ex = MediaExtractor()
                try {
                    ex.setDataSource(f.absolutePath)
                    val t = firstTrack(ex, "audio/") ?: throw IllegalStateException("No audio track in the downloaded audio")
                    ex.selectTrack(t)
                    val fmt = ex.getTrackFormat(t)
                    val outIndex = muxer.addTrack(fmt)
                    aExs += ex
                    audioOuts += outIndex
                    maxHint = maxOf(maxHint, sizeHint(fmt))
                } catch (e: Exception) {
                    try { ex.release() } catch (_: Throwable) {}
                    if (i == 0) throw e
                }
            }
            muxer.start()
            started = true

            var buffer = ByteBuffer.allocateDirect(maxHint)
            val info = MediaCodec.BufferInfo()
            val all = ArrayList<MediaExtractor>().apply { add(vEx); addAll(aExs) }
            val outs = ArrayList<Int>().apply { add(vOut); addAll(audioOuts) }
            val done = BooleanArray(all.size)
            var counter = 0
            // all streams are written in time order, so the file plays and seeks smoothly
            while (done.any { !it }) {
                if ((++counter and 0xFF) == 0 && isCancelled()) throw Cancelled()
                var pick = -1
                var pickTime = Long.MAX_VALUE
                for (i in all.indices) {
                    if (done[i]) continue
                    val t = all[i].sampleTime
                    if (t < 0L) { done[i] = true; continue }
                    if (t < pickTime) { pickTime = t; pick = i }
                }
                if (pick < 0) break
                val ex = all[pick]
                val size = ex.sampleSize
                if (size < 0L) { done[pick] = true; continue }
                if (size > buffer.capacity()) buffer = ByteBuffer.allocateDirect((size * 2).toInt())
                buffer.clear()
                val read = ex.readSampleData(buffer, 0)
                if (read < 0) { done[pick] = true; continue }
                info.set(0, read, ex.sampleTime, flagsOf(ex.sampleFlags))
                muxer.writeSampleData(outs[pick], buffer, info)
                ex.advance()
            }
        } finally {
            try { vEx.release() } catch (_: Throwable) {}
            for (e in aExs) { try { e.release() } catch (_: Throwable) {} }
            try { if (started) muxer?.stop() } catch (_: Throwable) {}
            try { muxer?.release() } catch (_: Throwable) {}
        }
    }

    /** Copies the audio track of [src] into a clean (non-fragmented) .m4a; the moov atom ends up at the file's end. */
    fun remuxAudio(src: File, out: File, isCancelled: () -> Boolean) {
        val ex = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            ex.setDataSource(src.absolutePath)
            val track = firstTrack(ex, "audio/") ?: throw IllegalStateException("No audio track found")
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outTrack = muxer.addTrack(fmt)
            muxer.start()
            started = true
            var buffer = ByteBuffer.allocateDirect(maxOf(sizeHint(fmt), 1 shl 18))
            val info = MediaCodec.BufferInfo()
            var counter = 0
            while (true) {
                if ((++counter and 0xFF) == 0 && isCancelled()) throw Cancelled()
                val size = ex.sampleSize
                if (size < 0L || ex.sampleTime < 0L) break
                if (size > buffer.capacity()) buffer = ByteBuffer.allocateDirect((size * 2).toInt())
                buffer.clear()
                val read = ex.readSampleData(buffer, 0)
                if (read < 0) break
                info.set(0, read, ex.sampleTime, flagsOf(ex.sampleFlags))
                muxer.writeSampleData(outTrack, buffer, info)
                ex.advance()
            }
        } finally {
            try { ex.release() } catch (_: Throwable) {}
            try { if (started) muxer?.stop() } catch (_: Throwable) {}
            try { muxer?.release() } catch (_: Throwable) {}
        }
    }

    private fun firstTrack(ex: MediaExtractor, mimePrefix: String): Int? {
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith(mimePrefix)) return i
        }
        return null
    }

    private fun sizeHint(f: MediaFormat): Int =
        if (f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0

    private fun flagsOf(extractorFlags: Int): Int {
        var flags = 0
        if (extractorFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        return flags
    }

    // ---------------------------------------------------------------------------------------------
    // Cover art
    // ---------------------------------------------------------------------------------------------

    /** Cuts the centre square out of a thumbnail and returns it as JPEG bytes (cover art of a downloaded audio). */
    fun coverJpeg(source: Bitmap, maxSide: Int = 1000): ByteArray? {
        return try {
            val side = minOf(source.width, source.height)
            if (side <= 0) return null
            val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
            val scaled = if (side > maxSide) Bitmap.createScaledBitmap(square, maxSide, maxSide, true) else square
            val bos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 92, bos)
            bos.toByteArray().takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Writes title, artist and the cover picture into a plain (non-fragmented) .m4a produced by [remuxAudio]. The moov
     * atom is the last atom of such a file, so the new tags only grow it and no sample offset has to be changed.
     * Returns false (file untouched) when the file does not have that simple layout.
     */
    fun embedCoverAndTags(m4a: File, cover: ByteArray?, title: String?, artist: String?): Boolean {
        if (cover == null && title.isNullOrBlank() && artist.isNullOrBlank()) return false
        try {
            RandomAccessFile(m4a, "rw").use { raf ->
                val total = raf.length()
                var pos = 0L
                var moovPos = -1L
                var moovSize = 0L
                while (pos + 8 <= total) {
                    raf.seek(pos)
                    var size = raf.readInt().toLong() and 0xFFFFFFFFL
                    val type = ByteArray(4).also { raf.readFully(it) }.toString(Charsets.ISO_8859_1)
                    if (size == 1L) {
                        if (type == "moov") return false // 64-bit atom header: not handled
                        size = raf.readLong()
                    }
                    if (size == 0L) size = total - pos
                    if (size < 8L || pos + size > total) return false
                    if (type == "moov") { moovPos = pos; moovSize = size }
                    pos += size
                }
                if (moovPos < 0L || moovPos + moovSize != total) return false // moov must be the last atom
                // an existing udta (e.g. written by the muxer) makes the layout more complex: leave such files alone
                val moovBody = ByteArray((moovSize - 8).toInt())
                raf.seek(moovPos + 8)
                raf.readFully(moovBody)
                if (containsAtom(moovBody, "udta")) return false

                val ilst = ByteArrayOutputStream()
                if (!title.isNullOrBlank()) ilst.write(textItem(byteArrayOf(0xA9.toByte(), 'n'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte()), title))
                if (!artist.isNullOrBlank()) ilst.write(textItem(byteArrayOf(0xA9.toByte(), 'A'.code.toByte(), 'R'.code.toByte(), 'T'.code.toByte()), artist))
                if (cover != null) {
                    val data = ByteArrayOutputStream().apply {
                        write(int32(16 + cover.size)); write("data".toByteArray())
                        write(int32(13)); write(int32(0)); write(cover) // 13 = JPEG
                    }.toByteArray()
                    ilst.write(int32(8 + data.size)); ilst.write("covr".toByteArray()); ilst.write(data)
                }
                val ilstAtom = atom("ilst", ilst.toByteArray())
                val hdlrBody = ByteArrayOutputStream().apply {
                    write(int32(0)); write(int32(0)); write("mdir".toByteArray()); write("appl".toByteArray())
                    write(int32(0)); write(int32(0)); write(byteArrayOf(0))
                }.toByteArray()
                val metaBody = ByteArrayOutputStream().apply {
                    write(int32(0)) // version + flags
                    write(atom("hdlr", hdlrBody))
                    write(ilstAtom)
                }.toByteArray()
                val udta = atom("udta", atom("meta", metaBody))

                val newMoovSize = moovSize + udta.size
                if (newMoovSize > 0xFFFFFFFFL) return false
                raf.seek(moovPos)
                raf.writeInt(newMoovSize.toInt())
                raf.seek(total)
                raf.write(udta)
            }
            return true
        } catch (_: Throwable) {
            return false
        }
    }

    /** Puts [cover] into an mp3 as an ID3v2.3 attached picture (only when the file has no ID3 tag yet). */
    fun embedMp3Cover(mp3: File, cover: ByteArray, title: String?, artist: String?): Boolean {
        return try {
            val head = ByteArray(3)
            mp3.inputStream().use { it.read(head) }
            if (String(head, Charsets.ISO_8859_1) == "ID3") return false
            val frames = ByteArrayOutputStream()
            fun textFrame(id: String, value: String) {
                val body = byteArrayOf(1) + byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + value.toByteArray(Charsets.UTF_16LE) + byteArrayOf(0, 0)
                frames.write(id.toByteArray()); frames.write(int32(body.size)); frames.write(byteArrayOf(0, 0)); frames.write(body)
            }
            if (!title.isNullOrBlank()) textFrame("TIT2", title)
            if (!artist.isNullOrBlank()) textFrame("TPE1", artist)
            val pic = ByteArrayOutputStream().apply {
                write(0); write("image/jpeg".toByteArray()); write(0); write(3); write(0); write(cover)
            }.toByteArray()
            frames.write("APIC".toByteArray()); frames.write(int32(pic.size)); frames.write(byteArrayOf(0, 0)); frames.write(pic)
            val body = frames.toByteArray()
            val tmp = File(mp3.parentFile, mp3.name + ".tag")
            tmp.outputStream().buffered(1 shl 16).use { o ->
                o.write("ID3".toByteArray()); o.write(byteArrayOf(3, 0, 0))
                o.write(byteArrayOf(((body.size shr 21) and 0x7F).toByte(), ((body.size shr 14) and 0x7F).toByte(), ((body.size shr 7) and 0x7F).toByte(), (body.size and 0x7F).toByte()))
                o.write(body)
                mp3.inputStream().use { it.copyTo(o, 1 shl 16) }
            }
            if (!mp3.delete() || !tmp.renameTo(mp3)) { tmp.delete(); return false }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun int32(v: Int): ByteArray =
        byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

    private fun atom(type: String, body: ByteArray): ByteArray =
        int32(8 + body.size) + type.toByteArray(Charsets.ISO_8859_1) + body

    private fun textItem(type: ByteArray, value: String): ByteArray {
        val text = value.toByteArray(Charsets.UTF_8)
        val data = int32(16 + text.size) + "data".toByteArray() + int32(1) + int32(0) + text
        return int32(8 + data.size) + type + data
    }

    private fun containsAtom(body: ByteArray, type: String): Boolean {
        var p = 0
        val t = type.toByteArray(Charsets.ISO_8859_1)
        while (p + 8 <= body.size) {
            val size = ((body[p].toLong() and 0xFF) shl 24) or ((body[p + 1].toLong() and 0xFF) shl 16) or
                ((body[p + 2].toLong() and 0xFF) shl 8) or (body[p + 3].toLong() and 0xFF)
            if (body[p + 4] == t[0] && body[p + 5] == t[1] && body[p + 6] == t[2] && body[p + 7] == t[3]) return true
            if (size < 8L) return false
            p += size.toInt()
        }
        return false
    }
}
