package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import java.io.File
import java.security.MessageDigest
import kotlin.math.roundToInt
import com.example.ui.state.ThumbnailQuality
import com.example.ui.state.ThumbnailStrategy
import com.example.util.MkvManagerEngine

/**
 * Bounded in-memory cache for list thumbnails.
 * The disk cache below is the source of truth across app restarts; this cache only avoids
 * decoding the same thumbnail repeatedly during the current process.
 */
object ThumbnailCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    // Keep substantially more decoded thumbnails resident so fast LazyColumn/LazyGrid
    // scrolling does not immediately evict frames that are still likely to be revisited.
    // The hard cap keeps this bounded on devices with large heaps.
    private val cacheSize = (maxMemory / 8).coerceIn(32 * 1024, 96 * 1024)

    val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int =
            (bitmap.byteCount / 1024).coerceAtLeast(1)
    }

    fun getCachedThumbnail(
        videoId: Long,
        path: String,
        strategy: ThumbnailStrategy,
        quality: ThumbnailQuality,
        fallbackSecond: Int
    ): Bitmap? {
        val suffix = "${strategy.name}_${quality.name}_t${fallbackSecond.coerceIn(1, 10)}"
        if (videoId > 0 && path.isNotBlank()) {
            memoryCache.get("${videoId}_${path}_$suffix")?.let { if (!it.isRecycled) return it }
        }
        if (path.isNotBlank()) {
            memoryCache.get("${path}_$suffix")?.let { if (!it.isRecycled) return it }
        }
        return null
    }
}

/**
 * Persistent thumbnail cache. Files live under Context.cacheDir, so Android may evict them when
 * storage is under pressure, but a normal process/app restart reuses already generated thumbnails.
 * The cache key includes the source signature, strategy, quality and requested second, so changing
 * any thumbnail setting cannot accidentally reuse an incompatible image.
 */
private object VideoThumbnailDiskCache {
    private const val CACHE_VERSION = "v3"
    private const val MAX_CACHE_BYTES = 256L * 1024L * 1024L

    private fun directory(context: Context): File =
        File(context.cacheDir, "video_thumbnail_cache").apply { mkdirs() }

    fun key(
        videoId: Long,
        uri: Uri,
        path: String,
        strategy: ThumbnailStrategy,
        quality: ThumbnailQuality,
        fallbackSecond: Int
    ): String {
        val source = if (path.isNotBlank()) {
            val file = File(path)
            if (file.exists()) {
                "file:${file.absolutePath}|${file.length()}|${file.lastModified()}"
            } else {
                "path:$path"
            }
        } else {
            "uri:${uri}"
        }
        val raw = "$CACHE_VERSION|$source|${strategy.name}|${quality.name}|$fallbackSecond"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "${strategy.name}_${quality.name}_$fallbackSecond-$digest"
    }

    // Do not purge other strategy/position caches when a thumbnail is requested.
    // Lazy lists can request several settings during recomposition and deleting the other
    // entries here was the reason cached thumbnails disappeared while scrolling.

    fun read(context: Context, key: String): Bitmap? {
        val file = File(directory(context), "$key.png")
        if (!file.isFile || file.length() <= 0L) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)?.takeUnless { it.isRecycled }
        } catch (_: Throwable) {
            try { file.delete() } catch (_: Throwable) {}
            null
        }
    }

    fun write(context: Context, key: String, bitmap: Bitmap) {
        val dir = directory(context)
        val target = File(dir, "$key.png")
        if (target.isFile && target.length() > 0L) return
        val temp = File(dir, "$key.tmp")
        try {
            temp.outputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) return
            }
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
        } catch (_: Throwable) {
            try { temp.delete() } catch (_: Throwable) {}
            return
        }
        trim(dir)
    }

    private fun trim(dir: File) {
        try {
            val files = dir.listFiles { f -> f.isFile && f.extension.equals("png", true) }.orEmpty()
            var total = files.sumOf { it.length() }
            if (total <= MAX_CACHE_BYTES) return
            for (file in files.sortedBy { it.lastModified() }) {
                if (total <= MAX_CACHE_BYTES) break
                total -= file.length()
                file.delete()
            }
        } catch (_: Throwable) {}
    }
}

/**
 * Dedicated bounded dispatcher for thumbnail generation so fast scrolling
 * never starves the CPU or UI thread, and queues do not accumulate stale tasks.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private val thumbnailDispatcher = Dispatchers.IO.limitedParallelism(3)

suspend fun loadVideoThumbnail(
    context: Context,
    videoId: Long,
    uri: Uri,
    path: String,
    strategy: ThumbnailStrategy = ThumbnailStrategy.SMART,
    quality: ThumbnailQuality = ThumbnailQuality.HIGH,
    allowNetwork: Boolean = false,
    fallbackSecond: Int = 1
): Bitmap? = withContext(thumbnailDispatcher) {
    if (!isActive) return@withContext null

    val targetWidth = quality.sizeDp.coerceIn(120, 720)
    val targetHeight = (targetWidth * 9f / 16f).roundToInt().coerceAtLeast(68)
    val isNetworkUri = uri.scheme == "http" || uri.scheme == "https"
    if (isNetworkUri && !allowNetwork) return@withContext null

    // The selected strategies no longer use a user-picked second. Keep the old parameter in
    // the public signature for source compatibility with existing callers, but never let it
    // fragment the cache or control thumbnail selection.
    val diskFallbackSecond = 0
    val diskKey = VideoThumbnailDiskCache.key(
        videoId = videoId,
        uri = uri,
        path = path,
        strategy = strategy,
        quality = quality,
        fallbackSecond = diskFallbackSecond
    )
    val cacheKey = "video_thumbnail_$diskKey"
    ThumbnailCache.memoryCache.get(cacheKey)?.let { return@withContext it }
    VideoThumbnailDiskCache.read(context, diskKey)?.let { cached ->
        ThumbnailCache.memoryCache.put(cacheKey, cached)
        return@withContext cached
    }

    fun cache(bitmap: Bitmap): Bitmap {
        ThumbnailCache.memoryCache.put(cacheKey, bitmap)
        // Keep aliases used by list/grid composables so a recomposed item can reuse the
        // already-decoded bitmap without starting another extraction.
        val aliasSecond = fallbackSecond.coerceIn(1, 10)
        val aliasSuffix = "${strategy.name}_${quality.name}_t${aliasSecond}"
        if (videoId > 0 && path.isNotBlank()) {
            ThumbnailCache.memoryCache.put("${videoId}_${path}_$aliasSuffix", bitmap)
        }
        if (path.isNotBlank()) {
            ThumbnailCache.memoryCache.put("${path}_$aliasSuffix", bitmap)
        }
        // Persist only successfully extracted/generated images. A placeholder/gradient is never cached.
        VideoThumbnailDiskCache.write(context, diskKey, bitmap)
        return bitmap
    }

    fun openRetriever(): MediaMetadataRetriever? {
        return try {
            MediaMetadataRetriever().also { retriever ->
                if (path.isNotBlank() && File(path).exists()) retriever.setDataSource(path)
                else if (uri.toString().isNotBlank()) retriever.setDataSource(context, uri)
                else { retriever.release(); return null }
            }
        } catch (_: Throwable) { null }
    }

    fun scaleToFit(source: Bitmap): Bitmap {
        val maxW = targetWidth
        val maxH = targetHeight
        if (source.width <= maxW && source.height <= maxH) return source
        val scale = minOf(maxW.toFloat() / source.width, maxH.toFloat() / source.height)
        val width = (source.width * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true).also {
            if (it !== source && !source.isRecycled) source.recycle()
        }
    }

    fun tryEmbedded(retriever: MediaMetadataRetriever): Bitmap? {
        return try {
            val bytes = retriever.embeddedPicture ?: return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let(::scaleToFit)
        } catch (_: Throwable) { null }
    }

    // Embedded mode means authored artwork only. Android's retriever does not expose every MKV
    // attachment, so the native MKV parser is the second, container-accurate attachment source.
    if (strategy == ThumbnailStrategy.EMBEDDED_AND_GENERATED) {
        openRetriever()?.let { retriever ->
            try {
                tryEmbedded(retriever)?.let { return@withContext cache(it) }
            } finally {
                try { retriever.release() } catch (_: Throwable) {}
            }
        }

        if (!isNetworkUri && (path.endsWith(".mkv", true) || uri.toString().endsWith(".mkv", true))) {
            try {
                val sourceUri = if (path.isNotBlank() && File(path).exists()) Uri.fromFile(File(path)) else uri
                val attachments = MkvManagerEngine.parseMkvFile(context, sourceUri).third.second
                val imageAttachment = attachments
                    .filter { it.isImage && it.data.isNotEmpty() }
                    .sortedByDescending {
                        val name = (it.fileName + " " + it.description).lowercase()
                        when {
                            "cover" in name || "poster" in name || "front" in name || "folder" in name -> 3
                            else -> 1
                        }
                    }
                    .firstOrNull()
                if (imageAttachment != null) {
                    BitmapFactory.decodeByteArray(imageAttachment.data, 0, imageAttachment.data.size)?.let {
                        return@withContext cache(scaleToFit(it))
                    }
                }
            } catch (_: Throwable) {}
        }

        // Embedded + Generated is attachment-first: if no image attachment exists, the
        // generated-frame path below is the only fallback. No unrelated poster source is used.
    }

    if (!isActive) return@withContext null

    // Smart mode deliberately samples several points through the media instead of always using
    // the first second. This avoids black/logo/title-card intros and works for very long files.
    // Generated mode remains deterministic and uses exactly the user-selected second (clamped to
    // the media duration).
    openRetriever()?.let { retriever ->
        try {
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val durationUs = (durationMs * 1000L).coerceAtLeast(1L)

            fun getFrame(timeUs: Long): Bitmap? {
                return try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(
                            timeUs.coerceIn(0L, durationUs),
                            MediaMetadataRetriever.OPTION_CLOSEST,
                            targetWidth,
                            targetHeight
                        )
                    } else {
                        retriever.getFrameAtTime(
                            timeUs.coerceIn(0L, durationUs),
                            MediaMetadataRetriever.OPTION_CLOSEST
                        )?.let(::scaleToFit)
                    }
                } catch (_: Throwable) { null }
            }

            fun frameScore(bitmap: Bitmap): Double {
                if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return -1.0
                val stepX = maxOf(1, bitmap.width / 24)
                val stepY = maxOf(1, bitmap.height / 14)
                var samples = 0
                var sum = 0.0
                var sumSq = 0.0
                var dark = 0
                val pixels = IntArray(1)
                var y = 0
                while (y < bitmap.height) {
                    var x = 0
                    while (x < bitmap.width) {
                        bitmap.getPixels(pixels, 0, 1, x, y, 1, 1)
                        val c = pixels[0]
                        val r = (c shr 16) and 0xFF
                        val g = (c shr 8) and 0xFF
                        val b = c and 0xFF
                        val lum = (0.2126 * r + 0.7152 * g + 0.0722 * b)
                        sum += lum
                        sumSq += lum * lum
                        if (lum < 10.0) dark++
                        samples++
                        x += stepX
                    }
                    y += stepY
                }
                if (samples == 0) return -1.0
                val mean = sum / samples
                val variance = (sumSq / samples) - (mean * mean)
                val darkRatio = dark.toDouble() / samples
                // Prefer real visual content: moderate brightness + local variation; heavily
                // black/uniform frames are penalized so a title-card/blank frame is less likely.
                return variance.coerceAtLeast(0.0) + mean * 0.45 - darkRatio * 220.0
            }

            if (strategy == ThumbnailStrategy.EMBEDDED_AND_GENERATED) {
                // No attached artwork was found. Pick a representative frame from the actual
                // video timeline instead of using a fixed 1–10 second fallback.
                val sourceSeed = (path.ifBlank { uri.toString() }).hashCode().toLong()
                val fraction = (((sourceSeed and Long.MAX_VALUE) % 8000L) / 10000.0 + 0.10).coerceIn(0.10, 0.90)
                val frame = getFrame((durationUs * fraction).toLong())
                    ?: getFrame((durationUs * 0.5).toLong())
                    ?: getFrame(0L)
                    ?: retriever.frameAtTime
                if (frame != null) return@withContext cache(scaleToFit(frame))
            } else if (strategy == ThumbnailStrategy.SMART) {
                val fractions = doubleArrayOf(0.12, 0.30, 0.48, 0.66, 0.84)
                var best: Bitmap? = null
                var bestScore = Double.NEGATIVE_INFINITY
                for (fraction in fractions) {
                    if (!isActive) return@withContext null
                    val frame = getFrame((durationUs * fraction).toLong()) ?: continue
                    val score = frameScore(frame)
                    if (score > bestScore) {
                        best?.let { if (!it.isRecycled) it.recycle() }
                        best = frame
                        bestScore = score
                    } else if (!frame.isRecycled) {
                        frame.recycle()
                    }
                }
                if (best != null) return@withContext cache(scaleToFit(best))
            }
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }

    null
}

/**
 * Warms the thumbnail cache for the complete visible library in the background. Three workers
 * keep extraction moving without creating one coroutine per video, while loadVideoThumbnail's
 * own bounded dispatcher remains the final CPU/IO guard.
 */
suspend fun prefetchVideoThumbnails(
    context: Context,
    videos: List<com.example.ui.screens.VideoItem>,
    strategy: ThumbnailStrategy,
    quality: ThumbnailQuality,
    allowNetwork: Boolean = false,
    fallbackSecond: Int = 1
) {
    if (videos.isEmpty()) return
    val uniqueVideos = videos.distinctBy { "${it.id}|${it.path}|${it.uri}" }
    val nextIndex = AtomicInteger(0)
    coroutineScope {
        repeat(3) {
            launch {
                while (isActive) {
                    val index = nextIndex.getAndIncrement()
                    if (index >= uniqueVideos.size) break
                    val video = uniqueVideos[index]
                    try {
                        loadVideoThumbnail(
                            context = context,
                            videoId = video.id,
                            uri = video.uri,
                            path = video.path,
                            strategy = strategy,
                            quality = quality,
                            allowNetwork = allowNetwork,
                            fallbackSecond = fallbackSecond
                        )
                    } catch (_: Throwable) {
                        // One unreadable/corrupt file must not stop the rest of the library.
                    }
                }
            }
        }
    }
}

/**
 * Video Thumbnail composable matching the exact requested UI:
 * - 16:9 rounded thumbnail container
 * - Red "NEW" badge on top-left
 * - Semi-transparent duration badge on bottom-right (e.g. "23:59")
 */
@Composable
fun VideoThumbnailCard(
    videoId: Long,
    videoUri: Uri,
    videoPath: String,
    durationText: String,
    isNew: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    customGradientSeed: Int = 0,
    showDuration: Boolean = true,
    showProgressBar: Boolean = true,
    watchedProgress: Float = 0f,
    isSelected: Boolean = false,
    isLastPlayed: Boolean = false,
    thumbnailStrategy: ThumbnailStrategy = ThumbnailStrategy.SMART,
    thumbnailQuality: ThumbnailQuality = ThumbnailQuality.HIGH,
    thumbnailFallbackSecond: Int = 1,
    showNetworkThumbnails: Boolean = false,
    remoteThumbnailUrl: String? = null,
    onThumbnailClick: (() -> Unit)? = null
) {
    val context = LocalContext.current.applicationContext
    val cacheKey = remember(videoId, videoPath, remoteThumbnailUrl, thumbnailStrategy, thumbnailQuality, thumbnailFallbackSecond) {
        if (!remoteThumbnailUrl.isNullOrBlank()) "remote_vid_${remoteThumbnailUrl}_400"
        else "${videoId}_${videoPath}_${thumbnailStrategy.name}_${thumbnailQuality.name}_t${thumbnailFallbackSecond.coerceIn(1, 10)}"
    }
    val initialCached = remember(cacheKey, videoId, videoPath) {
        ThumbnailCache.memoryCache.get(cacheKey)
            ?: ThumbnailCache.getCachedThumbnail(
                videoId, videoPath, thumbnailStrategy, thumbnailQuality, thumbnailFallbackSecond
            )
    }
    var thumbnailBitmap by remember(cacheKey) { mutableStateOf(initialCached) }

    LaunchedEffect(cacheKey) {
        if (thumbnailBitmap == null) {
            if (!remoteThumbnailUrl.isNullOrBlank()) {
                val bmp = loadRemoteAudioThumbnail(remoteThumbnailUrl, targetSizePx = 400)
                if (bmp != null) {
                    thumbnailBitmap = bmp
                }
            } else {
                val bmp = loadVideoThumbnail(
                    context, videoId, videoUri, videoPath,
                    strategy = thumbnailStrategy,
                    quality = thumbnailQuality,
                    allowNetwork = showNetworkThumbnails,
                    fallbackSecond = thumbnailFallbackSecond
                )
                if (bmp != null) {
                    thumbnailBitmap = bmp
                }
            }
        }
    }

    val shape = RoundedCornerShape(14.dp)

    val lastPlayedGradient = remember {
        Brush.linearGradient(
            listOf(
                Color(0xFF00E5FF),
                Color(0xFF6366F1),
                Color(0xFFEC4899),
                Color(0xFF00E5FF)
            )
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(if (onThumbnailClick != null) Modifier.clickable(onClick = onThumbnailClick) else Modifier)
            .background(if (isDark) Color(0xFF0F172A) else Color(0xFFE2E8F0))
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, com.example.ui.theme.AccentGradient, shape)
                } else if (isLastPlayed) {
                    Modifier.border(2.5.dp, lastPlayedGradient, shape)
                } else {
                    Modifier.border(
                        1.dp,
                        if (isDark) Color(0xFF334155).copy(alpha = 0.6f) else Color(0xFFCBD5E1).copy(alpha = 0.7f),
                        shape
                    )
                }
            )
    ) {
        // Thumbnail content (Image or artistic placeholder)
        if (thumbnailBitmap != null) {
            Image(
                bitmap = thumbnailBitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Never show an artistic gradient in place of a real video thumbnail.
            // The neutral surface remains stable until the cached/generated frame arrives.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isDark) Color(0xFF0F172A) else Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                StyledIcon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.35f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 1. Top-Left: Red "NEW" badge with white bold text (matching user screenshot)
        if (isNew) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 6.dp, y = 6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFE11D48)) // Vivid red matching screenshot
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "NEW",
                    color = Color.White,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.4.sp
                )
            }
        }

        // 2. Bottom-Right: Duration badge (e.g. "23:59") with dark translucent background
        if (showDuration && durationText.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-6).dp, y = if (showProgressBar && watchedProgress > 0f) (-8).dp else (-6).dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = durationText,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )
            }
        }

        // 3. Bottom Edge: Watched Progress Bar (Kitna aage tak dekha hai)
        if (showProgressBar && watchedProgress > 0f) {
            val clampedProgress = watchedProgress.coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(clampedProgress)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFEF4444), Color(0xFFF97316))
                            )
                        )
                )
            }
        }
    }
}

/**
 * Lightweight Thumbnail Loader helper for simple path-based video thumbnails.
 */
@Composable
fun VideoThumbnailLoader(
    videoPath: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val cacheKey = remember(videoPath) { "path_$videoPath" }
    var bitmap by remember(cacheKey) { mutableStateOf(ThumbnailCache.memoryCache.get(cacheKey)) }

    LaunchedEffect(cacheKey) {
        if (bitmap == null && videoPath.isNotBlank()) {
            val bmp = loadVideoThumbnail(
                context = context,
                videoId = 0L,
                uri = Uri.EMPTY,
                path = videoPath
            )
            if (bmp != null) {
                bitmap = bmp
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(Color(0xFF1E293B)),
            contentAlignment = Alignment.Center
        ) {
            StyledIcon(
                imageVector = Icons.Outlined.Movie,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

