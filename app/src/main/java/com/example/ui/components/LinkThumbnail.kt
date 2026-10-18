package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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

/**
 * Small thumbnail for a link / download row. Order: the thumbnail attached to the link (image URL, or a frame saved
 * from the video), then — for a finished local video — a frame of the file itself; [fallback] (the link icon) while
 * nothing is available.
 */
@Composable
fun LinkThumbnail(
    thumbnail: String?,
    modifier: Modifier = Modifier,
    localVideoUri: Uri? = null,
    localVideoPath: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val key = (thumbnail ?: "") + "|" + (localVideoUri?.toString() ?: "") + "|" + (localVideoPath ?: "")
    var bitmap by remember(key) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(key) {
        var bmp: Bitmap? = null
        if (!thumbnail.isNullOrBlank()) {
            bmp = if (thumbnail.startsWith("http")) {
                try { loadRemoteAudioThumbnail(thumbnail, 480, context) } catch (_: Throwable) { null }
            } else {
                withContext(Dispatchers.IO) {
                    try { if (File(thumbnail).isFile) BitmapFactory.decodeFile(thumbnail) else null } catch (_: Throwable) { null }
                }
            }
        }
        if (bmp == null && localVideoUri != null) {
            bmp = try {
                loadVideoThumbnail(context = context, videoId = 0L, uri = localVideoUri, path = localVideoPath.orEmpty())
            } catch (_: Throwable) { null }
        }
        bitmap = bmp
    }
    val shown = bitmap
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (shown != null && !shown.isRecycled) {
            Image(
                bitmap = shown.asImageBitmap(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.matchParentSize()
            )
        } else {
            fallback()
        }
    }
}
