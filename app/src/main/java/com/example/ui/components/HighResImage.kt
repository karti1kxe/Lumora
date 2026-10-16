package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Sharp full-width image (detail page backdrop, hero slider). [LinkThumbnail] keeps small 480 px list thumbnails that
 * look pixelated when stretched over the whole screen width, so this loader decodes the original bytes at up to
 * [maxSidePx], tries a larger variant of the same image first (TMDB / IMDb / WordPress size suffixes) and caches the
 * untouched bytes on disk (no PNG re-encode, no shared small-size cache).
 */
object HighResImageLoader {
    private const val DIR = "hq_image_cache"
    private const val MAX_DISK_BYTES = 80L * 1024L * 1024L
    private const val UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    private val memory = object : LruCache<String, Bitmap>(
        ((Runtime.getRuntime().maxMemory() / 1024L) / 10L).toInt().coerceIn(16 * 1024, 64 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    private val TMDB_RE = Regex("(image\\.tmdb\\.org/t/p/)(w\\d+|h\\d+|original)(/)", RegexOption.IGNORE_CASE)
    private val AMAZON_RE = Regex("\\._V1_[^/]*?(\\.(?:jpg|jpeg|png|webp))$", RegexOption.IGNORE_CASE)
    private val WP_SIZE_RE = Regex("-\\d{2,4}x\\d{2,4}(\\.(?:jpg|jpeg|png|webp))(?=$|\\?)", RegexOption.IGNORE_CASE)

    /** Larger variants of [url] first, the url itself last. */
    fun candidates(url: String): List<String> {
        val out = LinkedHashSet<String>()
        if (TMDB_RE.containsMatchIn(url)) out.add(TMDB_RE.replace(url, "$1w1280$3"))
        if (AMAZON_RE.containsMatchIn(url)) out.add(AMAZON_RE.replace(url, "._V1_$1"))
        if (WP_SIZE_RE.containsMatchIn(url)) out.add(WP_SIZE_RE.replace(url, "$1"))
        out.add(url)
        return out.toList()
    }

    private fun key(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun dir(context: Context): File = File(context.cacheDir, DIR).apply { mkdirs() }

    private fun decode(bytes: ByteArray, maxSidePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / (sample * 2) >= maxSidePx) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun download(url: String): ByteArray? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as? HttpURLConnection) ?: return null
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "image/webp,image/apng,image/jpeg,image/*,*/*;q=0.8")
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.size < 512) null else bytes
        } catch (_: Throwable) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun trimDisk(context: Context) {
        val files = dir(context).listFiles { f -> f.isFile }.orEmpty()
        var total = files.sumOf { it.length() }
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= MAX_DISK_BYTES) break
            total -= f.length()
            f.delete()
        }
    }

    suspend fun load(context: Context, url: String, maxSidePx: Int = 1600): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank() || !url.startsWith("http")) return@withContext null
        memory.get("$maxSidePx|$url")?.let { if (!it.isRecycled) return@withContext it }
        val app = context.applicationContext
        for (candidate in candidates(url)) {
            val file = File(dir(app), key(candidate))
            var bytes: ByteArray? = if (file.isFile && file.length() > 0L) runCatching { file.readBytes() }.getOrNull() else null
            if (bytes == null) {
                bytes = download(candidate)
                if (bytes != null) {
                    runCatching {
                        val tmp = File(dir(app), key(candidate) + ".tmp")
                        tmp.writeBytes(bytes)
                        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
                        trimDisk(app)
                    }
                }
            }
            val bmp = bytes?.let { runCatching { decode(it, maxSidePx) }.getOrNull() }
            if (bmp != null) {
                memory.put("$maxSidePx|$url", bmp)
                return@withContext bmp
            }
        }
        null
    }
}

/** Sharp network image; [placeholder] is shown until it is loaded (or when it cannot be loaded). */
@Composable
fun HighResImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    maxSidePx: Int = 1600,
    placeholder: @Composable () -> Unit = {}
) {
    val context = LocalContext.current.applicationContext
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = if (url.isNullOrBlank()) null else try { HighResImageLoader.load(context, url, maxSidePx) } catch (_: Throwable) { null }
    }
    val shown = bitmap
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (shown != null && !shown.isRecycled) {
            Image(
                bitmap = shown.asImageBitmap(),
                contentDescription = null,
                contentScale = contentScale,
                alignment = alignment,
                modifier = Modifier.matchParentSize()
            )
        } else {
            placeholder()
        }
    }
}
