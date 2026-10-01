package com.example.ui.components

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.screens.AudioTrackItem
import com.example.ui.theme.AccentGradient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.security.MessageDigest


private object RemoteAudioThumbnailDiskCache {
    private const val VERSION = "v1"
    private const val MAX_BYTES = 48L * 1024L * 1024L

    private fun dir(context: Context): File =
        File(context.cacheDir, "remote_audio_thumbnail_cache").apply { mkdirs() }

    private fun key(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$VERSION|$url".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return digest
    }

    fun read(context: Context, url: String): Bitmap? {
        val file = File(dir(context), "${key(url)}.png")
        if (!file.isFile || file.length() <= 0L) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath)?.takeUnless { it.isRecycled } }.getOrNull()
    }

    fun write(context: Context, url: String, bitmap: Bitmap) {
        val target = File(dir(context), "${key(url)}.png")
        if (target.isFile && target.length() > 0L) return
        val temp = File(dir(context), "${key(url)}.tmp")
        runCatching {
            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!temp.renameTo(target)) { target.delete(); temp.renameTo(target) }
            val files = dir(context).listFiles { f -> f.isFile && f.extension.equals("png", true) }.orEmpty()
            var total = files.sumOf { it.length() }
            for (file in files.sortedBy { it.lastModified() }) {
                if (total <= MAX_BYTES) break
                total -= file.length()
                file.delete()
            }
        }.onFailure { runCatching { temp.delete() } }
    }
}

/**
 * Asynchronous, cached loader for remote audio thumbnails / artwork URLs with multi-tier high-res fallbacks.
 */
fun getCachedAudioArtwork(track: AudioTrackItem): Bitmap? {
    // 1. Try remote thumbnail cache
    if (!track.thumbnailUrl.isNullOrBlank()) {
        val remoteKeys = listOf(
            "remote_audio_${track.thumbnailUrl}_384",
            "remote_audio_${track.thumbnailUrl}_600",
            "remote_audio_${track.thumbnailUrl}_800",
            "remote_audio_${track.thumbnailUrl}_1000",
            track.thumbnailUrl
        )
        for (key in remoteKeys) {
            ThumbnailCache.memoryCache.get(key)?.let { if (!it.isRecycled) return it }
        }
    }
    // 2. Try local audio cache
    val localKeys = listOf(
        "audio_${track.id}_${track.path}_384",
        "audio_${track.id}_${track.path}_300",
        "audio_${track.id}_${track.path}_600",
        "audio_${track.id}_${track.path}_800",
        "audio_${track.id}_${track.path}_1000"
    )
    for (key in localKeys) {
        ThumbnailCache.memoryCache.get(key)?.let { if (!it.isRecycled) return it }
    }
    // 3. Try snapshot matching
    try {
        val snap = ThumbnailCache.memoryCache.snapshot()
        if (!track.thumbnailUrl.isNullOrBlank()) {
            for ((k, bmp) in snap) {
                if (k.contains(track.thumbnailUrl) && bmp != null && !bmp.isRecycled) {
                    return bmp
                }
            }
        }
        if (track.path.isNotBlank()) {
            for ((k, bmp) in snap) {
                if (k.contains(track.path) && bmp != null && !bmp.isRecycled) {
                    return bmp
                }
            }
        }
    } catch (_: Throwable) {}
    return null
}

suspend fun loadRemoteAudioThumbnail(
    url: String,
    targetSizePx: Int = 800,
    context: Context? = null
): Bitmap? = withContext(Dispatchers.IO) {
    if (url.isBlank()) return@withContext null
    val cacheKey = "remote_audio_${url}_${targetSizePx}"
    ThumbnailCache.memoryCache.get(cacheKey)?.let { return@withContext it }
    context?.applicationContext?.let { RemoteAudioThumbnailDiskCache.read(it, url) }?.let { cached ->
        ThumbnailCache.memoryCache.put(cacheKey, cached)
        ThumbnailCache.memoryCache.put("remote_audio_${url}_384", cached)
        ThumbnailCache.memoryCache.put("remote_audio_${url}_600", cached)
        ThumbnailCache.memoryCache.put("remote_audio_${url}_1000", cached)
        ThumbnailCache.memoryCache.put(url, cached)
        return@withContext cached
    }
    ThumbnailCache.memoryCache.get("remote_audio_${url}_384")?.let { return@withContext it }
    ThumbnailCache.memoryCache.get("remote_audio_${url}_600")?.let { return@withContext it }
    ThumbnailCache.memoryCache.get("remote_audio_${url}_1000")?.let { return@withContext it }
    ThumbnailCache.memoryCache.get(url)?.let { return@withContext it }

    if (!isActive) return@withContext null

    // Extract potential YouTube video ID
    val ytVideoId = when {
        url.contains("/vi/") -> url.substringAfter("/vi/").substringBefore("/")
        url.contains("/vi_webp/") -> url.substringAfter("/vi_webp/").substringBefore("/")
        url.contains("img.youtube.com/vi/") -> url.substringAfter("img.youtube.com/vi/").substringBefore("/")
        url.contains("v=") -> url.substringAfter("v=").substringBefore("&").substringBefore("?")
        url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
        else -> null
    }

    val urlsToTry = if (!ytVideoId.isNullOrBlank() && ytVideoId.length in 8..16) {
        listOf(
            "https://i.ytimg.com/vi/$ytVideoId/maxresdefault.jpg",
            "https://i.ytimg.com/vi/$ytVideoId/sddefault.jpg",
            "https://i.ytimg.com/vi/$ytVideoId/hq720.jpg",
            "https://i.ytimg.com/vi/$ytVideoId/hqdefault.jpg",
            "https://i.ytimg.com/vi/$ytVideoId/mqdefault.jpg",
            url
        ).distinct()
    } else if (url.contains("scdn.co/image/") || url.contains("spotifycdn.com/image/")) {
        val highRes = url.replace("00001e02", "0000b273").replace("00004851", "0000b273").replace("00000002", "0000b273")
        listOf(highRes, url).distinct()
    } else if (url.contains("googleusercontent.com") || url.contains("yt3.ggpht.com")) {
        val highRes = url.replace(Regex("=s\\d+.*"), "=s800").replace(Regex("=w\\d+-h\\d+.*"), "=w800-h800")
        listOf(highRes, url).distinct()
    } else {
        listOf(url)
    }

    for (candidateUrl in urlsToTry) {
        if (!isActive) return@withContext null
        try {
            val connection = (URL(candidateUrl).openConnection() as? java.net.HttpURLConnection) ?: continue
            connection.apply {
                connectTimeout = 7000
                readTimeout = 9000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                setRequestProperty("Accept", "image/webp,image/apng,image/jpeg,image/*,*/*;q=0.8")
            }
            val code = connection.responseCode
            if (code != java.net.HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                continue
            }

            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inDither = true
            }
            val bmp = connection.inputStream.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
            connection.disconnect()

            if (bmp == null) continue

            // YouTube dummy placeholder detector:
            // When maxresdefault or sddefault does NOT exist, YouTube often returns a 120x90 transparent or gray image with HTTP 200
            if (bmp.width <= 120 && bmp.height <= 90 && (candidateUrl.contains("maxresdefault") || candidateUrl.contains("sddefault"))) {
                bmp.recycle()
                continue // Try next candidate (hq720, hqdefault, etc.)
            }

            val finalBmp = if (bmp.width > targetSizePx || bmp.height > targetSizePx) {
                val ratio = bmp.width.toFloat() / bmp.height.toFloat()
                val (w, h) = if (ratio > 1f) {
                    targetSizePx to (targetSizePx / ratio).toInt().coerceAtLeast(1)
                } else {
                    (targetSizePx * ratio).toInt().coerceAtLeast(1) to targetSizePx
                }
                val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
                if (scaled !== bmp && !bmp.isRecycled) {
                    bmp.recycle()
                }
                scaled
            } else {
                bmp
            }

            ThumbnailCache.memoryCache.put(cacheKey, finalBmp)
            ThumbnailCache.memoryCache.put("remote_audio_${url}_384", finalBmp)
            ThumbnailCache.memoryCache.put("remote_audio_${url}_600", finalBmp)
            ThumbnailCache.memoryCache.put("remote_audio_${url}_1000", finalBmp)
            ThumbnailCache.memoryCache.put(url, finalBmp)
            context?.applicationContext?.let { RemoteAudioThumbnailDiskCache.write(it, url, finalBmp) }
            return@withContext finalBmp
        } catch (_: Throwable) {
            // Try next candidate fallback URL
        }
    }
    null
}

/**
 * Asynchronous, cached loader for audio album artwork & track thumbnails.
 * Tries embedded ID3 picture first, then MediaStore ContentResolver thumbnail (API 29+),
 * and finally scans local directory for folder/cover artwork files.
 */
private object LocalAudioThumbnailDiskCache {
    private const val VERSION = "v1"
    private const val MAX_BYTES = 48L * 1024L * 1024L

    private fun dir(context: Context): File =
        File(context.cacheDir, "local_audio_thumbnail_cache").apply { mkdirs() }

    private fun key(audioId: Long, uri: Uri, path: String, targetSizePx: Int): String {
        val source = if (path.isNotBlank()) {
            val file = File(path)
            if (file.exists()) "file:${file.absolutePath}|${file.length()}|${file.lastModified()}"
            else "path:$path"
        } else {
            "uri:$uri"
        }
        val raw = "$VERSION|$audioId|$source|$targetSizePx"
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun read(context: Context, audioId: Long, uri: Uri, path: String, targetSizePx: Int): Bitmap? {
        val file = File(dir(context), "${key(audioId, uri, path, targetSizePx)}.png")
        if (!file.isFile || file.length() <= 0L) return null
        return runCatching {
            BitmapFactory.decodeFile(file.absolutePath)?.takeUnless { it.isRecycled }
        }.getOrNull()
    }

    fun write(context: Context, audioId: Long, uri: Uri, path: String, targetSizePx: Int, bitmap: Bitmap) {
        val folder = dir(context)
        val key = key(audioId, uri, path, targetSizePx)
        val target = File(folder, "$key.png")
        if (target.isFile && target.length() > 0L) return
        val temp = File(folder, "$key.tmp")
        runCatching {
            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
            val files = folder.listFiles { f -> f.isFile && f.extension.equals("png", true) }.orEmpty()
            var total = files.sumOf { it.length() }
            for (file in files.sortedBy { it.lastModified() }) {
                if (total <= MAX_BYTES) break
                total -= file.length()
                file.delete()
            }
        }.onFailure { runCatching { temp.delete() } }
    }
}

suspend fun loadAudioThumbnail(
    context: Context,
    audioId: Long,
    uri: Uri,
    path: String,
    targetSizePx: Int = 800
): Bitmap? = withContext(Dispatchers.IO) {
    val cacheKey = "audio_${audioId}_${path}_${targetSizePx}"
    ThumbnailCache.memoryCache.get(cacheKey)?.let { return@withContext it }
    LocalAudioThumbnailDiskCache.read(context, audioId, uri, path, targetSizePx)?.let { cached ->
        ThumbnailCache.memoryCache.put(cacheKey, cached)
        return@withContext cached
    }

    if (!isActive) return@withContext null

    fun cacheAndScale(bitmap: Bitmap?): Bitmap? {
        if (bitmap == null) return null
        val finalBmp = if (bitmap.width > targetSizePx || bitmap.height > targetSizePx) {
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val (w, h) = if (ratio > 1f) {
                targetSizePx to (targetSizePx / ratio).toInt().coerceAtLeast(1)
            } else {
                (targetSizePx * ratio).toInt().coerceAtLeast(1) to targetSizePx
            }
            val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
            if (scaled !== bitmap && !bitmap.isRecycled) {
                bitmap.recycle()
            }
            scaled
        } else {
            bitmap
        }
        ThumbnailCache.memoryCache.put(cacheKey, finalBmp)
        LocalAudioThumbnailDiskCache.write(context, audioId, uri, path, targetSizePx, finalBmp)
        return finalBmp
    }

    // 1. Try MediaMetadataRetriever embedded picture (ID3 APIC frame)
    try {
        val retriever = MediaMetadataRetriever()
        try {
            if (path.isNotBlank() && File(path).exists()) {
                retriever.setDataSource(path)
            } else if (uri.toString().isNotBlank()) {
                retriever.setDataSource(context, uri)
            }
            val rawBytes = retriever.embeddedPicture
            if (rawBytes != null && rawBytes.isNotEmpty()) {
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inDither = true
                }
                val bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts)
                val res = cacheAndScale(bmp)
                if (res != null) return@withContext res
            }
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    } catch (_: Throwable) {}

    if (!isActive) return@withContext null

    // 2. Try MediaStore thumbnail API (Android Q / 29+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != Uri.EMPTY) {
        try {
            val bmp = context.contentResolver.loadThumbnail(uri, Size(targetSizePx, targetSizePx), null)
            val res = cacheAndScale(bmp)
            if (res != null) return@withContext res
        } catch (_: Throwable) {}
    }

    if (!isActive) return@withContext null

    // 3. Try standard album art URI lookup via MediaStore content provider
    try {
        val albumArtUri = Uri.parse("content://media/external/audio/albumart")
        val audioAlbumUri = ContentUris.withAppendedId(albumArtUri, audioId)
        context.contentResolver.openInputStream(audioAlbumUri)?.use { stream ->
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inDither = true
            }
            val bmp = BitmapFactory.decodeStream(stream, null, opts)
            val res = cacheAndScale(bmp)
            if (res != null) return@withContext res
        }
    } catch (_: Throwable) {}

    // 4. Try local folder cover images (cover.jpg, folder.jpg, album.jpg, etc.)
    if (path.isNotBlank()) {
        try {
            val parentDir = File(path).parentFile
            if (parentDir != null && parentDir.exists() && parentDir.isDirectory) {
                val candidateNames = listOf(
                    "cover.jpg", "cover.png", "cover.jpeg",
                    "folder.jpg", "folder.png", "folder.jpeg",
                    "albumart.jpg", "albumart.png",
                    "album.jpg", "album.png",
                    "front.jpg", "front.png",
                    ".folder.png", ".cover.jpg"
                )
                for (name in candidateNames) {
                    val coverFile = File(parentDir, name)
                    if (coverFile.exists() && coverFile.isFile && coverFile.length() > 0) {
                        val opts = BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                            inDither = true
                        }
                        val bmp = BitmapFactory.decodeFile(coverFile.absolutePath, opts)
                        val res = cacheAndScale(bmp)
                        if (res != null) return@withContext res
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    null
}

/**
 * Reusable Composable for Audio Track Thumbnails with fallback artwork icon.
 */
@Composable
fun AudioTrackThumbnail(
    track: AudioTrackItem,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    fallbackIcon: ImageVector = Icons.Filled.Headphones,
    fallbackIconSize: Dp = 22.dp
) {
    val context = LocalContext.current
    var bitmap by remember(track.id, track.path, track.thumbnailUrl) {
        val cacheKey = if (!track.thumbnailUrl.isNullOrBlank()) {
            "remote_audio_${track.thumbnailUrl}_600"
        } else {
            "audio_${track.id}_${track.path}_600"
        }
        val cached = ThumbnailCache.memoryCache.get(cacheKey)
        mutableStateOf<Bitmap?>(cached)
    }

    LaunchedEffect(track.id, track.path, track.uri, track.thumbnailUrl) {
        if (bitmap == null) {
            val loaded = if (!track.thumbnailUrl.isNullOrBlank()) {
                loadRemoteAudioThumbnail(
                    url = track.thumbnailUrl,
                    targetSizePx = 384,
                    context = context
                )
            } else {
                loadAudioThumbnail(
                    context = context,
                    audioId = track.id,
                    uri = track.uri,
                    path = track.path,
                    targetSizePx = 384
                )
            }
            bitmap = loaded
        }
    }

    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E293B)),
                contentAlignment = Alignment.Center
            ) {
                StyledIcon(
                    imageVector = fallbackIcon,
                    contentDescription = track.title,
                    tint = Color.White,
                    modifier = Modifier.size(fallbackIconSize)
                )
            }
        }
    }
}
