package com.example.player

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.example.MainActivity
import com.example.R
import com.example.ui.components.ThumbnailCache
import com.example.ui.components.getCachedAudioArtwork
import com.example.ui.screens.AudioTrackItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles music playback system notification, MediaSessionCompat,
 * Dynamic Island / Smart Capsule integration, and lock-screen controls.
 *
 * Fully supports:
 * - Android 13/14/15/16 System Media Card UI with live scrubbable slider & timestamps
 * - Dynamic Island / Live Activity / Aqua Dynamics / HyperOS Island status pills
 * - Real-time PlaybackStateCompat (Play, Pause, Skip Next, Skip Prev, SeekTo)
 * - MediaMetadataCompat (Title, Artist, Album, Duration, High-Res Album Art)
 * - Custom application icon resources for Previous, Play/Pause, Next
 */
object AudioNotificationManager {
    private const val TAG = "AudioNotificationMgr"
    const val CHANNEL_ID = "audio_playback_channel_v1"
    const val NOTIFICATION_ID = 4040

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var channelCreated = false
    private var mediaSession: MediaSessionCompat? = null
    private var notificationGeneration: Long = 0L
    private var currentNotificationJob: kotlinx.coroutines.Job? = null

    fun initChannel(context: Context) {
        if (channelCreated) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Music Playback",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Music playback controls and current track info"
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                }
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.createNotificationChannel(channel)
                channelCreated = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create NotificationChannel", e)
            }
        }
    }

    fun getOrCreateMediaSession(context: Context): MediaSessionCompat {
        val existing = mediaSession
        if (existing != null) return existing

        val appContext = context.applicationContext
        val session = MediaSessionCompat(appContext, "AppAudioPlaybackSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    AudioPlaybackManager.resume()
                }

                override fun onPause() {
                    AudioPlaybackManager.pause()
                }

                override fun onSkipToNext() {
                    AudioPlaybackManager.skipNext()
                }

                override fun onSkipToPrevious() {
                    AudioPlaybackManager.skipPrevious()
                }

                override fun onSeekTo(pos: Long) {
                    AudioPlaybackManager.seekTo(pos)
                }

                override fun onStop() {
                    AudioPlaybackManager.closeAndStop()
                }
            })

            isActive = true
        }

        mediaSession = session
        return session
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun updateMediaSessionState(
        context: Context,
        track: AudioTrackItem?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        playbackSpeed: Float = 1.0f,
        artworkBitmap: Bitmap? = null,
        isBuffering: Boolean = false
    ) {
        try {
            val session = getOrCreateMediaSession(context)
            // If buffering, report STATE_BUFFERING with 0f playback speed so the slider freezes immediately
            val state = when {
                isBuffering -> PlaybackStateCompat.STATE_BUFFERING
                isPlaying -> PlaybackStateCompat.STATE_PLAYING
                else -> PlaybackStateCompat.STATE_PAUSED
            }
            val effectiveSpeed = if (isBuffering || !isPlaying) 0f else playbackSpeed
            val actions = PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO or
                    PlaybackStateCompat.ACTION_STOP

            val playbackState = PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, positionMs, effectiveSpeed)
                .build()
            session.setPlaybackState(playbackState)

            if (track != null) {
                val artistName = if (track.artist.isNotBlank() && track.artist != "<unknown>") track.artist else track.album
                val metadataBuilder = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artistName)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, if (durationMs > 0) durationMs else track.durationMs)

                val art = artworkBitmap ?: getCachedAudioArtwork(track)
                if (art != null && !art.isRecycled) {
                    metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
                    metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, art)
                }
                session.setMetadata(metadataBuilder.build())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating MediaSession state: ${e.message}", e)
        }
    }

    fun buildNotification(
        context: Context,
        track: AudioTrackItem,
        isPlaying: Boolean,
        positionMs: Long = 0L,
        durationMs: Long = 0L,
        playbackSpeed: Float = 1.0f,
        isBuffering: Boolean = false,
        artworkBitmap: Bitmap? = null
    ): Notification {
        val appContext = context.applicationContext
        initChannel(appContext)
        val session = getOrCreateMediaSession(appContext)

        val contentIntent = Intent(appContext, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            putExtra("EXTRA_OPEN_AUDIO_PLAYER", true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            appContext,
            100,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Previous Track
        val prevIntent = Intent(appContext, AudioNotificationReceiver::class.java).apply {
            action = AudioNotificationReceiver.ACTION_PREV
        }
        val prevPendingIntent = PendingIntent.getBroadcast(
            appContext,
            101,
            prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Play/Pause Toggle
        val playPauseIntent = Intent(appContext, AudioNotificationReceiver::class.java).apply {
            action = AudioNotificationReceiver.ACTION_PLAY_PAUSE
        }
        val playPausePendingIntent = PendingIntent.getBroadcast(
            appContext,
            102,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Next Track
        val nextIntent = Intent(appContext, AudioNotificationReceiver::class.java).apply {
            action = AudioNotificationReceiver.ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getBroadcast(
            appContext,
            103,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Stop / Close
        val stopIntent = Intent(appContext, AudioNotificationReceiver::class.java).apply {
            action = AudioNotificationReceiver.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            appContext,
            104,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val subText = if (isBuffering) {
            "Buffering • ${track.format}"
        } else if (track.artist.isNotBlank() && track.artist != "<unknown>") {
            "${track.artist} • ${track.format}"
        } else {
            track.format
        }

        val mediaStyle = MediaStyle()
            .setMediaSession(session.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)
            .setShowCancelButton(true)
            .setCancelButtonIntent(stopPendingIntent)

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setStyle(mediaStyle)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(track.title)
            .setContentText(if (track.artist.isNotBlank() && track.artist != "<unknown>") track.artist else track.album)
            .setSubText(subText)
            .setContentIntent(contentPendingIntent)
            .setOngoing(isPlaying || isBuffering)
            .setAutoCancel(!isPlaying && !isBuffering)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.lumora_arrow_left_solid,
                "Previous",
                prevPendingIntent
            )
            .addAction(
                if (isPlaying) R.drawable.lumora_pause_solid else R.drawable.lumora_play_solid,
                if (isPlaying) "Pause" else "Play",
                playPausePendingIntent
            )
            .addAction(
                R.drawable.lumora_arrow_right_solid,
                "Next",
                nextPendingIntent
            )
            .addAction(
                R.drawable.lumora_close_circle,
                "Close",
                stopPendingIntent
            )

        val art = artworkBitmap ?: getCachedAudioArtwork(track)
        if (art != null && !art.isRecycled) {
            builder.setLargeIcon(art)
        }

        return builder.build()
    }

    fun buildCurrentNotification(context: Context): Notification {
        val track = AudioPlaybackManager.currentTrack.value
        return if (track != null) {
            buildNotification(
                context = context,
                track = track,
                isPlaying = AudioPlaybackManager.isPlaying.value,
                positionMs = AudioPlaybackManager.currentPositionMs.value,
                durationMs = AudioPlaybackManager.durationMs.value,
                playbackSpeed = AudioPlaybackManager.playbackSpeed.value,
                isBuffering = AudioPlaybackManager.isBuffering.value,
                artworkBitmap = getCachedAudioArtwork(track)
            )
        } else {
            val fallbackTrack = AudioTrackItem(
                id = -1L,
                uri = Uri.EMPTY,
                title = "Lumora Audio Player",
                artist = "Ready to play",
                album = "Lumora",
                durationMs = 0L,
                path = "",
                sizeBytes = 0L,
                format = "AUDIO",
                audioType = "Stereo",
                dateModified = 0L
            )
            buildNotification(context, fallbackTrack, isPlaying = false)
        }
    }

    fun showOrUpdateNotification(
        context: Context,
        track: AudioTrackItem?,
        isPlaying: Boolean,
        positionMs: Long = 0L,
        durationMs: Long = 0L,
        playbackSpeed: Float = 1.0f,
        isBuffering: Boolean = false
    ) {
        if (track == null) {
            cancelNotification(context)
            return
        }

        val appContext = context.applicationContext
        initChannel(appContext)

        // Instant synchronous update to MediaSession to eliminate Dynamic Island & system notification lag
        val immediateArtwork = getCachedAudioArtwork(track)
        updateMediaSessionState(
            context = appContext,
            track = track,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            playbackSpeed = playbackSpeed,
            artworkBitmap = immediateArtwork,
            isBuffering = isBuffering
        )

        // Build immediate notification and start or update foreground service immediately
        val initialNotification = buildNotification(
            context = appContext,
            track = track,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            playbackSpeed = playbackSpeed,
            isBuffering = isBuffering,
            artworkBitmap = immediateArtwork
        )

        if (isPlaying || isBuffering) {
            if (AudioPlaybackService.activeService != null) {
                AudioPlaybackService.activeService?.updateForegroundNotification(initialNotification)
            } else {
                AudioPlaybackService.startService(appContext)
            }
        } else if (AudioPlaybackService.activeService != null) {
            AudioPlaybackService.activeService?.updateForegroundNotification(initialNotification)
        }

        if (hasNotificationPermission(appContext)) {
            try {
                NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, initialNotification)
            } catch (e: Exception) {
                Log.w(TAG, "Notification notify failed: ${e.message}")
            }
        }

        val jobGen = ++notificationGeneration
        currentNotificationJob?.cancel()
        currentNotificationJob = scope.launch {
            try {
                // Look for cached album artwork or load asynchronously in background
                var artworkBitmap: Bitmap? = immediateArtwork
                if (artworkBitmap == null || artworkBitmap.isRecycled) {
                    val cacheKey = if (!track.thumbnailUrl.isNullOrBlank()) {
                        "remote_audio_${track.thumbnailUrl}_384"
                    } else {
                        "audio_${track.id}_${track.path}_300"
                    }
                    artworkBitmap = ThumbnailCache.memoryCache.get(cacheKey)
                    if (artworkBitmap == null || artworkBitmap.isRecycled) {
                        artworkBitmap = if (!track.thumbnailUrl.isNullOrBlank()) {
                            com.example.ui.components.loadRemoteAudioThumbnail(
                                url = track.thumbnailUrl,
                                targetSizePx = 384
                            )
                        } else {
                            com.example.ui.components.loadAudioThumbnail(
                                context = appContext,
                                audioId = track.id,
                                uri = track.uri,
                                path = track.path,
                                targetSizePx = 384
                            )
                        }
                    }
                }

                // If a newer notification update has been issued or track changed, cancel stale job
                if (jobGen != notificationGeneration) return@launch
                val liveTrack = AudioPlaybackManager.currentTrack.value
                if (liveTrack == null || liveTrack.id != track.id) return@launch

                val liveIsPlaying = AudioPlaybackManager.isPlaying.value
                val liveIsBuffering = AudioPlaybackManager.isBuffering.value
                val livePosition = AudioPlaybackManager.currentPositionMs.value
                val liveDuration = AudioPlaybackManager.durationMs.value
                val liveSpeed = AudioPlaybackManager.playbackSpeed.value

                // If freshly resolved artwork bitmap differs or was loaded, update media session with LIVE values
                if (artworkBitmap != null && !artworkBitmap.isRecycled && artworkBitmap != immediateArtwork) {
                    updateMediaSessionState(
                        context = appContext,
                        track = liveTrack,
                        isPlaying = liveIsPlaying,
                        positionMs = livePosition,
                        durationMs = liveDuration,
                        playbackSpeed = liveSpeed,
                        artworkBitmap = artworkBitmap,
                        isBuffering = liveIsBuffering
                    )
                }

                val finalNotification = buildNotification(
                    context = appContext,
                    track = liveTrack,
                    isPlaying = liveIsPlaying,
                    positionMs = livePosition,
                    durationMs = liveDuration,
                    playbackSpeed = liveSpeed,
                    isBuffering = liveIsBuffering,
                    artworkBitmap = artworkBitmap
                )

                if (AudioPlaybackService.activeService != null) {
                    AudioPlaybackService.activeService?.updateForegroundNotification(finalNotification)
                }

                if (hasNotificationPermission(appContext)) {
                    withContext(Dispatchers.Main) {
                        try {
                            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, finalNotification)
                        } catch (e: Exception) {
                            Log.w(TAG, "Notification notify failed: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error building notification: ${e.message}", e)
            }
        }
    }

    fun cancelNotification(context: Context) {
        try {
            AudioPlaybackService.stopService(context)
            NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
            mediaSession?.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_STOPPED, 0L, 0f)
                    .build()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel notification", e)
        }
    }
}
