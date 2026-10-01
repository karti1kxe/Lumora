package com.example.player

import android.content.ContentUris
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.MediaStore
import android.util.Log
import com.example.ui.screens.AudioTrackItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import com.example.util.AudioPlaybackHistoryManager

enum class AudioRepeatMode(val label: String) {
    OFF("Off"),
    ALL("Repeat All"),
    ONE("Repeat One")
}

/**
 * Global Singleton Audio Playback Engine for the entire application.
 * Manages background playback, queue management, seeking, shuffle, repeat,
 * playback speed, and synchronized state between Full-Screen and Minimized Player.
 */
object AudioPlaybackManager {
    private const val TAG = "AudioPlaybackManager"
    /** How many upcoming online tracks are cached in the background while the current one plays. */
    private const val PREFETCH_AHEAD = 2
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var applicationContext: Context? = null

    // Real-Time Hardware-Accelerated Audio Effects (Zero-glitch live equalizer)
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private var isEqEnabled: Boolean = true
    private var eqGain60: Float = 0f
    private var eqGain230: Float = 0f
    private var eqGain910: Float = 0f
    private var eqGain3600: Float = 0f
    private var eqGain14000: Float = 0f
    private var eqVolumeBoostDb: Float = 0f
    private var isHeadphoneSurroundEnabled: Boolean = false

    // State Flows
    private val _currentTrack = MutableStateFlow<AudioTrackItem?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering = _isBuffering.asStateFlow()

    private var streamResolutionJob: Job? = null
    private var streamPreloadJob: Job? = null
    private var cacheJob: Job? = null
    private var prefetchJob: Job? = null

    // Pre-buffered player for the NEXT online track (cached on disk + already prepared).
    private var standbyPlayer: MediaPlayer? = null
    private var standbyTrackId: Long? = null
    private var standbyPrepared: Boolean = false

    // Incremented for every (non-seamless) player start; lets a seamless hand-off detect that it
    // has been superseded by a newer start.
    @Volatile
    private var startToken: Long = 0L
    private val preloadedOnlineStreams = ConcurrentHashMap<String, String>()

    // Guards MediaPlayer callbacks from an older, rapidly-replaced player instance.
    // This prevents late onCompletion/onError events from restarting the wrong track.
    private var playbackGeneration: Long = 0L
    @Volatile
    private var mediaPlayerPrepared: Boolean = false

    // Exponential-backoff network retry and stall tracking
    @Volatile
    private var trackRetryCount: Int = 0
    @Volatile
    private var lastRetriedTrackId: Long? = null

    // Audio focus & user pause tracking (prevents microphone / keyboard voice typing from auto-starting music)
    @Volatile
    private var userExplicitlyPaused: Boolean = false

    @Volatile
    private var resumeOnFocusGain: Boolean = false

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs = _durationMs.asStateFlow()

    private val _queue = MutableStateFlow<List<AudioTrackItem>>(emptyList())
    val queue = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex = _currentIndex.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle = _isShuffle.asStateFlow()

    private var originalQueue: List<AudioTrackItem> = emptyList()

    private val _repeatMode = MutableStateFlow(AudioRepeatMode.ALL)
    val repeatMode = _repeatMode.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed = _playbackSpeed.asStateFlow()

    private val _isFullScreenOpen = MutableStateFlow(false)
    val isFullScreenOpen = _isFullScreenOpen.asStateFlow()

    private val _isMiniPlayerVisible = MutableStateFlow(false)
    val isMiniPlayerVisible = _isMiniPlayerVisible.asStateFlow()

    // A-B Loop State Flows
    private val _loopPointA = MutableStateFlow<Long?>(null)
    val loopPointA = _loopPointA.asStateFlow()

    private val _loopPointB = MutableStateFlow<Long?>(null)
    val loopPointB = _loopPointB.asStateFlow()

    private val _isABLoopActive = MutableStateFlow(false)
    val isABLoopActive = _isABLoopActive.asStateFlow()

    fun setLoopPointA(timeMs: Long) {
        val dur = _durationMs.value.coerceAtLeast(1L)
        val clampedA = timeMs.coerceIn(0L, dur)
        _loopPointA.value = clampedA
        val currentB = _loopPointB.value
        if (currentB != null && currentB <= clampedA) {
            _loopPointB.value = (clampedA + 2000L).coerceAtMost(dur)
        }
        _isABLoopActive.value = (_loopPointA.value != null && _loopPointB.value != null)
    }

    fun setLoopPointB(timeMs: Long) {
        val dur = _durationMs.value.coerceAtLeast(1L)
        val clampedB = timeMs.coerceIn(0L, dur)
        val currentA = _loopPointA.value
        if (currentA == null) {
            _loopPointA.value = (clampedB - 2000L).coerceAtLeast(0L)
        } else if (clampedB <= currentA) {
            _loopPointB.value = (currentA + 2000L).coerceAtMost(dur)
        } else {
            _loopPointB.value = clampedB
        }
        _isABLoopActive.value = true
        val finalA = _loopPointA.value ?: 0L
        val finalB = _loopPointB.value ?: dur
        if (_currentPositionMs.value < finalA || _currentPositionMs.value >= finalB) {
            seekTo(finalA)
        }
    }

    fun toggleABLoop(currentPos: Long? = null) {
        val pos = currentPos ?: _currentPositionMs.value
        if (_loopPointA.value == null) {
            setLoopPointA(pos)
        } else if (_loopPointB.value == null) {
            setLoopPointB(pos)
        } else {
            clearABLoop()
        }
    }

    fun clearABLoop() {
        _loopPointA.value = null
        _loopPointB.value = null
        _isABLoopActive.value = false
    }

    fun adjustLoopPointA(deltaMs: Long) {
        val curA = _loopPointA.value ?: _currentPositionMs.value
        val maxLimit = (_loopPointB.value ?: _durationMs.value) - 500L
        val newA = (curA + deltaMs).coerceIn(0L, maxLimit.coerceAtLeast(0L))
        _loopPointA.value = newA
        _isABLoopActive.value = (_loopPointA.value != null && _loopPointB.value != null)
        seekTo(newA)
    }

    fun adjustLoopPointB(deltaMs: Long) {
        val curB = _loopPointB.value ?: _durationMs.value
        val minLimit = (_loopPointA.value ?: 0L) + 500L
        val newB = (curB + deltaMs).coerceIn(minLimit, _durationMs.value.coerceAtLeast(minLimit))
        _loopPointB.value = newB
        _isABLoopActive.value = (_loopPointA.value != null && _loopPointB.value != null)
    }

    fun jumpToPointA() {
        _loopPointA.value?.let { seekTo(it) }
    }

    fun jumpToPointB() {
        _loopPointB.value?.let { seekTo(it) }
    }

    fun openFullScreen() {
        _isFullScreenOpen.value = true
    }

    fun playRecentTrack(context: Context, resume: com.example.util.AudioPlaybackResume, startFromBeginning: Boolean = false): Boolean {
        val uri = try { if (resume.uriString.isNotBlank()) Uri.parse(resume.uriString) else Uri.EMPTY } catch (_: Throwable) { Uri.EMPTY }
        val track = AudioTrackItem(
            id = resume.id,
            uri = uri,
            title = resume.title.ifBlank { resume.path.substringAfterLast('/').ifBlank { "Recent Audio" } },
            artist = resume.artist,
            album = resume.album,
            durationMs = resume.durationMs,
            path = resume.path,
            sizeBytes = 0L,
            format = if (resume.path.startsWith(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX)) "STREAM" else resume.path.substringAfterLast('.', ""),
            dateModified = resume.lastWatchedTimestamp / 1000L,
            thumbnailUrl = resume.thumbnailUrl
        )
        if (track.path.isBlank() && track.uri == Uri.EMPTY) return false

        val targetPosition = if (startFromBeginning) 0L else resume.positionMs

        // If online track, re-resolve stream URL because CDN tokens expire
        if (track.path.startsWith(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX) || !resume.videoUrl.isNullOrBlank()) {
            val videoUrl = resume.videoUrl ?: track.path.removePrefix(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX)
            // Already cached on disk: play instantly, no stream resolution / network round trip.
            if (track.path.startsWith(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX) &&
                OnlineAudioCache.cachedFile(
                    context.applicationContext,
                    track.path.removePrefix(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX)
                ) != null
            ) {
                playTrack(context, track, listOf(track), targetPosition)
                openFullScreen()
                return true
            }
            scope.launch(Dispatchers.IO) {
                val freshStreamUrl = com.example.util.OnlineMusicService.resolveStreamUrl(videoUrl, track.title, track.artist)
                val activeTrack = if (freshStreamUrl != null) {
                    track.copy(uri = Uri.parse(freshStreamUrl))
                } else {
                    track
                }
                withContext(Dispatchers.Main) {
                    playTrack(context, activeTrack, listOf(activeTrack), targetPosition)
                    openFullScreen()
                }
            }
            return true
        }

        playTrack(context, track, listOf(track), targetPosition)
        openFullScreen()
        return true
    }

    fun collapseToMiniPlayer() {
        _isFullScreenOpen.value = false
        if (_currentTrack.value != null) {
            _isMiniPlayerVisible.value = true
        }
    }

    fun closeAndStop(context: Context? = null) {
        applicationContext?.let { ctx ->
            AudioPlaybackHistoryManager.savePosition(ctx, _currentTrack.value, _currentPositionMs.value, _durationMs.value, force = true)
        }
        stop(context)
        _isFullScreenOpen.value = false
        _isMiniPlayerVisible.value = false
        _currentTrack.value = null
        clearABLoop()
    }

    /**
     * Removes an upcoming queue item without interrupting the currently playing track.
     * The active track is intentionally protected so a swipe cannot unexpectedly stop playback.
     */
    fun removeFromQueue(index: Int) {
        val currentQ = _queue.value.toMutableList()
        if (index !in currentQ.indices) return
        if (index == _currentIndex.value) return

        currentQ.removeAt(index)
        _queue.value = currentQ

        val currentIndex = _currentIndex.value
        _currentIndex.value = when {
            currentQ.isEmpty() -> 0
            index < currentIndex -> (currentIndex - 1).coerceAtLeast(0)
            else -> currentIndex.coerceIn(0, currentQ.lastIndex)
        }
        onQueueOrderChanged()
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val currentQ = _queue.value.toMutableList()
        if (fromIndex !in currentQ.indices || toIndex !in currentQ.indices || fromIndex == toIndex) return
        val currentTrackId = _currentTrack.value?.id

        val moved = currentQ.removeAt(fromIndex)
        currentQ.add(toIndex, moved)
        _queue.value = currentQ

        if (currentTrackId != null) {
            val newIdx = currentQ.indexOfFirst { it.id == currentTrackId }
            if (newIdx >= 0) {
                _currentIndex.value = newIdx
            }
        }
        onQueueOrderChanged()
    }

    fun setLyricsForCurrentTrack(lyrics: String) {
        val cur = _currentTrack.value ?: return
        val hasL = lyrics.isNotBlank()
        val updated = cur.copy(hasLyrics = hasL, lyricsText = lyrics)
        _currentTrack.value = updated

        // Update in queue
        val currentQ = _queue.value.map { if (it.id == cur.id) updated else it }
        _queue.value = currentQ
        if (originalQueue.isNotEmpty()) {
            originalQueue = originalQueue.map { if (it.id == cur.id) updated else it }
        }
    }

    fun updateCurrentTrackStream(track: AudioTrackItem) {
        if (_currentTrack.value?.id == track.id) {
            _currentTrack.value = track
            val updatedQueue = _queue.value.map { if (it.id == track.id) track else it }
            _queue.value = updatedQueue
            if (originalQueue.isNotEmpty()) {
                originalQueue = originalQueue.map { if (it.id == track.id) track else it }
            }
        }
    }

    fun updateQueue(newQueue: List<AudioTrackItem>) {
        if (newQueue.isEmpty()) return
        originalQueue = newQueue
        val curId = _currentTrack.value?.id
        val idx = if (curId != null) newQueue.indexOfFirst { it.id == curId }.coerceAtLeast(0) else 0
        _queue.value = newQueue
        _currentIndex.value = idx
        // Recommendations / playlist items often arrive AFTER playback started: make sure the
        // upcoming track still gets pre-buffered.
        onQueueOrderChanged()
    }

    fun playTrack(
        context: Context,
        track: AudioTrackItem,
        newQueue: List<AudioTrackItem> = emptyList(),
        startPositionMs: Long = 0L
    ) {
        val appContext = context.applicationContext
        applicationContext = appContext
        if (audioManager == null) {
            audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        }

        // Invalidate callbacks from the previous player before replacing it.
        playbackGeneration++
        mediaPlayerPrepared = false
        trackRetryCount = 0
        lastRetriedTrackId = track.id

        // Cancel previous stream resolution job if running
        streamResolutionJob?.cancel()
        streamResolutionJob = null

        // Stop progress tracking and kill previous player immediately to avoid overlapping audio
        stopProgressTracker()
        releaseAudioEffects()
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
        } catch (_: Throwable) {}
        mediaPlayer = null

        _isPlaying.value = false
        _isBuffering.value = true
        _currentPositionMs.value = startPositionMs.coerceAtLeast(0L)

        // Set or update queue
        val currentQ = if (newQueue.isNotEmpty()) newQueue else if (_queue.value.isNotEmpty()) _queue.value else listOf(track)
        originalQueue = currentQ

        if (_isShuffle.value && currentQ.size > 1) {
            val shuffled = currentQ.toMutableList()
            val playingItem = shuffled.firstOrNull { it.id == track.id } ?: track
            shuffled.remove(playingItem)
            shuffled.shuffle()
            shuffled.add(0, playingItem)
            _queue.value = shuffled
            _currentIndex.value = 0
        } else {
            _queue.value = currentQ
            val idx = currentQ.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            _currentIndex.value = idx
        }

        _currentTrack.value = track
        _durationMs.value = track.durationMs
        _isMiniPlayerVisible.value = true
        com.example.util.MediaSeenManager.markSeen(appContext, track.path, track.id)
        clearABLoop()
        com.example.util.AudioPlaybackHistoryManager.savePosition(appContext, track, startPositionMs, track.durationMs, force = true)

        // Asynchronously extract deep embedded ID3/MP4/FLAC lyrics & metadata if missing
        scope.launch(Dispatchers.IO) {
            try {
                val extracted = AudioMetadataExtractor.extractFullMetadata(appContext, track)
                if (extracted.lyrics != null || extracted.artist != null) {
                    val updated = track.copy(
                        title = extracted.title ?: track.title,
                        artist = extracted.artist ?: track.artist,
                        album = extracted.album ?: track.album,
                        hasLyrics = !extracted.lyrics.isNullOrBlank(),
                        lyricsText = extracted.lyrics ?: track.lyricsText,
                        durationMs = if (extracted.durationMs > 0L) extracted.durationMs else track.durationMs
                    )
                    if (_currentTrack.value?.id == track.id) {
                        _currentTrack.value = updated
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Background metadata extraction error: ${e.message}")
            }
        }

        userExplicitlyPaused = false
        resumeOnFocusGain = false
        requestAudioFocus()
        startMediaPlayer(appContext, track, startPositionMs.coerceAtLeast(0L))
    }

    private fun startMediaPlayer(
        context: Context,
        track: AudioTrackItem,
        startPositionMs: Long = 0L,
        seamless: Boolean = false
    ) {
        val generation = playbackGeneration
        val uriStr = track.uri.toString()
        val isOnlineTrack = track.path.startsWith(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX)
        // Seamless hand-off (used when the whole online track finished caching mid-playback):
        // the current player keeps playing until the replacement is prepared and positioned, so
        // there is no stop / buffering flash / restart from 0.
        val previousPlayer: MediaPlayer? = if (seamless) mediaPlayer else null

        if (!seamless) {
            startToken++
            stopProgressTracker()
            mediaPlayerPrepared = false

            // Cancel previous stream resolution job if running
            streamResolutionJob?.cancel()
            streamResolutionJob = null

            // Stop and release previous player instance immediately
            releaseAudioEffects()
            try {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) mp.stop()
                    mp.release()
                }
            } catch (_: Throwable) {}
            mediaPlayer = null

            // A pre-buffered next track starts instantly, so no buffering state is shown for it.
            val standbyWaiting = isOnlineTrack && standbyMatches(track.id)
            _isPlaying.value = false
            _isBuffering.value = !standbyWaiting
            _currentPositionMs.value = startPositionMs.coerceAtLeast(0L)
            updateNotification(context, track, playing = false, isBuffering = !standbyWaiting)
        }
        val token = startToken

        // Fully cached online track: play the local file (no network, no buffering, cannot end early).
        if (isOnlineTrack && !uriStr.startsWith("file:")) {
            val cachedFile = OnlineAudioCache.cachedFile(
                context, track.path.removePrefix(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX)
            )
            if (cachedFile != null) {
                startMediaPlayer(context, track.copy(uri = Uri.fromFile(cachedFile)), startPositionMs, seamless)
                return
            }
        }
        val isUnresolvedOnlineUri = isOnlineTrack &&
            (track.uri == Uri.EMPTY ||
             uriStr.isBlank() ||
             uriStr.startsWith(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX) ||
             uriStr.contains("youtube.com") ||
             uriStr.contains("youtu.be") ||
             uriStr.contains("spotify.com") ||
             uriStr.startsWith("spotify:"))
        if (isUnresolvedOnlineUri) {
            _isBuffering.value = true
            streamResolutionJob = scope.launch(Dispatchers.IO) {
                val videoUrl = track.path.removePrefix(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX)
                // For a fresh start or stream resume, prefer fresh resolution if not in cache
                var resolvedUrl: String? = if (startPositionMs > 5000L) null else preloadedOnlineStreams[videoUrl]
                if (resolvedUrl == null) {
                    for (attempt in 1..4) {
                        resolvedUrl = com.example.util.OnlineMusicService.resolveStreamUrl(videoUrl, track.title, track.artist)
                        if (resolvedUrl != null) break
                        delay(400L * attempt)
                    }
                }
                withContext(Dispatchers.Main) {
                    if (generation != playbackGeneration) return@withContext
                    if (resolvedUrl != null) {
                        preloadedOnlineStreams[videoUrl] = resolvedUrl
                        val updated = track.copy(uri = Uri.parse(resolvedUrl))
                        val updatedQueue = _queue.value.map { if (it.id == track.id) updated else it }
                        _queue.value = updatedQueue
                        if (originalQueue.isNotEmpty()) {
                            originalQueue = originalQueue.map { if (it.id == track.id) updated else it }
                        }
                        if (_currentTrack.value?.id == track.id) {
                            _currentTrack.value = updated
                        }
                        startMediaPlayer(context, updated, startPositionMs)
                    } else {
                        _isBuffering.value = false
                        Log.e(TAG, "Failed to resolve stream for ${track.title} after retries. Auto-skipping to next track in queue...")
                        android.widget.Toast.makeText(context, "Unable to load stream. Skipping...", android.widget.Toast.LENGTH_SHORT).show()
                        skipNext(context)
                    }
                }
            }
            return
        }

        // Online track: download the WHOLE track (and the next ones) to disk in the background.
        if (isOnlineTrack && uriStr.startsWith("http")) {
            startCachePipeline(context, track, generation)
        } else if (isOnlineTrack && uriStr.startsWith("file:") && !seamless) {
            // Playing from disk already: nothing to download for this track. The upcoming tracks
            // are pre-buffered as soon as playback starts (see beginPlayback below).
            cacheJob?.cancel()
            cacheJob = null
            prefetchJob?.cancel()
            prefetchJob = null
        }

        // Pre-buffered (already prepared) player for this exact track, if one is waiting.
        val standby: MediaPlayer? =
            if (isOnlineTrack && uriStr.startsWith("file:") && !seamless) takeStandbyPlayer(track.id) else null
        val player: MediaPlayer = standby ?: MediaPlayer()
        if (!seamless) mediaPlayer = player

        var playbackBegan = false

        fun beginPlayback(mp: MediaPlayer) {
            if (playbackBegan) return
            if (generation != playbackGeneration || token != startToken) {
                runCatching { mp.release() }
                return
            }
            playbackBegan = true
            if (seamless && (userExplicitlyPaused || runCatching { previousPlayer?.isPlaying }.getOrNull() == false)) {
                // The user paused while the local copy was being prepared: keep everything as it
                // is (the stream player stays paused); the next start uses the cached file anyway.
                runCatching { mp.release() }
                return
            }
            if (seamless) mediaPlayer = mp
            mediaPlayerPrepared = true
            _isBuffering.value = false
            val dur = mp.duration.toLong().coerceAtLeast(0L)
            _durationMs.value = if (dur > 0L) dur else track.durationMs
            applyPlaybackSpeed(_playbackSpeed.value)
            setupAudioEffects(mp.audioSessionId)
            mp.start()
            if (seamless) {
                try {
                    previousPlayer?.let { old ->
                        if (old.isPlaying) old.stop()
                        old.release()
                    }
                } catch (_: Throwable) {}
            }
            _currentPositionMs.value = runCatching { mp.currentPosition.toLong() }.getOrDefault(startPositionMs.coerceAtLeast(0L))
            _isPlaying.value = true
            startProgressTracker()
            updateNotification(context, track, true, isBuffering = false)
            preloadUpcomingOnlineStreams()
            if (isOnlineTrack) {
                // While this track plays, get the next one ready: cached on disk + a prepared
                // player waiting, so the switch at the end of the song is instant.
                prepareStandbyForUpcoming(context)
                if (cacheJob?.isActive != true && prefetchJob?.isActive != true) requestPrefetch(context)
            }
        }

        val onPrepared: (MediaPlayer) -> Unit = prepared@{ mp ->
            if (generation != playbackGeneration || token != startToken) {
                runCatching { mp.release() }
                return@prepared
            }
            val dur = mp.duration.toLong().coerceAtLeast(0L)
            val wantedStart = if (seamless) {
                (runCatching { previousPlayer?.currentPosition?.toLong() }.getOrNull() ?: startPositionMs) + 180L
            } else {
                startPositionMs
            }
            val safeStart = if (wantedStart > 500L) {
                wantedStart.coerceIn(0L, (dur.coerceAtLeast(1L) - 250L).coerceAtLeast(0L))
            } else {
                0L
            }
            if (safeStart > 0L) {
                // Seek FIRST, start on seek-complete: no audible blip from 0:00 when resuming.
                mp.setOnSeekCompleteListener { p ->
                    p.setOnSeekCompleteListener(null)
                    beginPlayback(p)
                }
                scope.launch {
                    delay(2500L)
                    if (!playbackBegan) beginPlayback(mp)
                }
                try {
                    mp.seekTo(safeStart, MediaPlayer.SEEK_CLOSEST)
                } catch (_: Throwable) {
                    beginPlayback(mp)
                }
            } else {
                beginPlayback(mp)
            }
        }

        player.apply {
            try {
                runCatching { setWakeMode(context.applicationContext, PowerManager.PARTIAL_WAKE_LOCK) }
                if (standby == null) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    if (track.uri != Uri.EMPTY) {
                        setDataSource(context, track.uri)
                    } else if (track.path.isNotBlank()) {
                        setDataSource(track.path)
                    } else {
                        Log.e(TAG, "No valid URI or path for track: ${track.title}")
                        _isBuffering.value = false
                        if (seamless) runCatching { release() } else skipNext(context)
                        return
                    }
                }

                setOnInfoListener { _, what, _ ->
                    if (generation != playbackGeneration || token != startToken || !playbackBegan) return@setOnInfoListener true
                    when (what) {
                        MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                            _isBuffering.value = true
                            updateNotification(context, track, playing = true, isBuffering = true)
                        }
                        MediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                            _isBuffering.value = false
                            updateNotification(context, track, playing = true, isBuffering = false)
                        }
                    }
                    false
                }

                setOnPreparedListener { mp -> onPrepared(mp) }

                setOnCompletionListener { mp ->
                    if (generation != playbackGeneration || token != startToken) return@setOnCompletionListener
                    mediaPlayerPrepared = false
                    _isBuffering.value = false
                    val isOnline = track.path.startsWith(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX) ||
                                   track.uri.toString().startsWith("http")
                    val dur = expectedDurationMs(track, isOnline)
                    val pos = runCatching { mp.currentPosition.toLong() }.getOrNull() ?: _currentPositionMs.value

                    // Strict requirement: Fix premature termination (e.g. ~70% stopping bug)
                    // If an online stream closed or finished before reaching the end of the song (pos < dur - 3000L),
                    // it is a network drop / CDN chunk timeout. Reconnect with backoff or advance to next track.
                    if (isOnline && dur > 5000L && pos < (dur - 3000L)) {
                        if (lastRetriedTrackId != track.id) {
                            lastRetriedTrackId = track.id
                            trackRetryCount = 0
                        }
                        if (trackRetryCount < 3) {
                            trackRetryCount++
                            val videoUrl = track.path.removePrefix(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX)
                            preloadedOnlineStreams.remove(videoUrl)
                            Log.w(TAG, "Online audio stream ended prematurely at $pos ms / $dur ms. Re-connecting attempt $trackRetryCount of 3...")
                            _isBuffering.value = true
                            updateNotification(context, track, playing = true, isBuffering = true)
                            scope.launch {
                                delay(300L * trackRetryCount)
                                startMediaPlayer(context, track.copy(uri = Uri.EMPTY), startPositionMs = pos)
                            }
                        } else {
                            Log.w(TAG, "Premature completion retries exhausted for ${track.title}. Auto-advancing to next track.")
                            trackRetryCount = 0
                            handleTrackCompletion(context)
                        }
                    } else {
                        trackRetryCount = 0
                        handleTrackCompletion(context)
                    }
                }

                setOnErrorListener { failedPlayer, what, extra ->
                    if (generation != playbackGeneration || token != startToken) return@setOnErrorListener true
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    if (seamless && !playbackBegan) {
                        // The local copy could not be played: drop it, keep playing the stream.
                        runCatching { failedPlayer.release() }
                        if (uriStr.startsWith("file:")) {
                            OnlineAudioCache.delete(context, track.path.removePrefix(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX))
                        }
                        return@setOnErrorListener true
                    }
                    mediaPlayerPrepared = false
                    val isOnline = track.path.startsWith(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX) ||
                                   track.uri.toString().startsWith("http")
                    if (isOnline) {
                        val pos = _currentPositionMs.value
                        val dur = expectedDurationMs(track, true)
                        // A corrupt cached file must never be replayed: drop it so the retry streams again.
                        if (track.uri.toString().startsWith("file:")) {
                            OnlineAudioCache.delete(context, track.path.removePrefix(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX))
                        }
                        // If stream errored before reaching 100% completion, reconnect and resume with backoff
                        if (dur <= 0L || pos < (dur - 3000L)) {
                            if (lastRetriedTrackId != track.id) {
                                lastRetriedTrackId = track.id
                                trackRetryCount = 0
                            }
                            if (trackRetryCount < 8) {
                                trackRetryCount++
                                val videoUrl = track.path.removePrefix(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX)
                                preloadedOnlineStreams.remove(videoUrl)
                                Log.w(TAG, "Online audio stream error at $pos ms / $dur ms. Re-connecting attempt $trackRetryCount/8...")
                                _isBuffering.value = true
                                updateNotification(context, track, playing = true, isBuffering = true)
                                scope.launch {
                                    delay(minOf(400L * trackRetryCount, 4000L))
                                    startMediaPlayer(context, track.copy(uri = Uri.EMPTY), startPositionMs = pos)
                                }
                                return@setOnErrorListener true
                            } else {
                                Log.e(TAG, "Online stream error retries exhausted. Auto-skipping to next track in playlist.")
                                trackRetryCount = 0
                                _isBuffering.value = false
                                scope.launch(Dispatchers.Main) {
                                    android.widget.Toast.makeText(context, "Playback error. Skipping to next track...", android.widget.Toast.LENGTH_SHORT).show()
                                    skipNext(context)
                                }
                                return@setOnErrorListener true
                            }
                        }
                    }
                    _isBuffering.value = false
                    _isPlaying.value = false
                    stopProgressTracker()
                    updateNotification(context, track, false)
                    // If error occurs, auto-skip to next track so player never gets permanently stuck
                    scope.launch(Dispatchers.Main) {
                        skipNext(context)
                    }
                    true
                }

                if (standby != null) {
                    onPrepared(this)
                } else {
                    prepareAsync()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up MediaPlayer: ${e.message}", e)
                if (seamless) {
                    // Keep the current (streaming) player untouched; just discard the failed copy.
                    runCatching { release() }
                } else {
                    mediaPlayerPrepared = false
                    _isBuffering.value = false
                    _isPlaying.value = false
                    scope.launch(Dispatchers.Main) {
                        skipNext(context)
                    }
                }
            }
        }
    }

    /**
     * Duration that decides whether a track really finished. For online tracks the catalogue
     * duration is trusted when it is longer than what the stream reports, so a truncated CDN
     * stream can never be mistaken for the end of the song (no auto-advance before the end).
     */
    private fun expectedDurationMs(track: AudioTrackItem, isOnline: Boolean): Long {
        val playerDur = _durationMs.value.takeIf { it > 0L } ?: track.durationMs
        if (!isOnline || track.durationMs <= 5000L) return playerDur
        // A complete local file knows its own length; only a live stream can be truncated.
        if (track.uri.toString().startsWith("file:")) return playerDur
        return maxOf(playerDur, track.durationMs)
    }

    /**
     * Background caching: download the current online track completely and, once done, hand
     * playback over to the local file WITHOUT interrupting the audio (see the `seamless` mode of
     * [startMediaPlayer]). Afterwards the upcoming tracks are pre-buffered by [requestPrefetch].
     */
    private fun startCachePipeline(context: Context, current: AudioTrackItem?, generation: Long) {
        cacheJob?.cancel()
        prefetchJob?.cancel()
        if (current == null) return
        val appContext = context.applicationContext
        cacheJob = scope.launch(Dispatchers.IO) {
            try {
                val videoUrl = current.path.removePrefix(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX)
                val file = OnlineAudioCache.download(appContext, videoUrl, current.uri.toString())
                if (file != null) {
                    withContext(Dispatchers.Main) { swapToCachedFile(appContext, current, file, generation) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "Cache pipeline error: ${t.message}")
            }
            // The current track is done (or could not be cached): now pre-buffer what comes next.
            withContext(Dispatchers.Main) {
                if (generation == playbackGeneration) requestPrefetch(appContext, fromPipeline = true)
            }
        }
    }

    private fun swapToCachedFile(context: Context, track: AudioTrackItem, file: File, generation: Long) {
        if (generation != playbackGeneration) return
        if (_currentTrack.value?.id != track.id) return
        val mp = mediaPlayer ?: return
        if (!mediaPlayerPrepared || !_isPlaying.value) return
        val pos = runCatching { mp.currentPosition.toLong() }.getOrDefault(_currentPositionMs.value)
        val dur = _durationMs.value
        // Nearly finished or not really playing: leave it, the next start uses the cached file anyway.
        if (dur > 0L && dur - pos < 8000L) return
        startMediaPlayer(context, track.copy(uri = Uri.fromFile(file)), pos, seamless = true)
    }

    /** A direct media URL (not a YouTube/Spotify web page that still has to be resolved). */
    private fun isDirectStreamUrl(url: String): Boolean {
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return false
        val host = runCatching { Uri.parse(url).host }.getOrNull()?.lowercase() ?: return false
        return !(host.endsWith("youtube.com") || host == "youtu.be" || host.endsWith("spotify.com"))
    }

    /** The online track [offset] places after the current one, exactly as skipNext() will pick it. */
    private fun upcomingOnlineTrack(offset: Int): AudioTrackItem? {
        if (_repeatMode.value == AudioRepeatMode.ONE) return null
        val q = _queue.value
        if (q.size < 2) return null
        val idx = _currentIndex.value
        val i = idx + offset
        val candidate = q.getOrNull(i)
            ?: if (_repeatMode.value == AudioRepeatMode.ALL) q[i % q.size] else null
        return candidate?.takeIf {
            it.path.startsWith(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX) &&
                it.id != _currentTrack.value?.id
        }
    }

    /**
     * Pre-buffers the next tracks in the background WHILE the current one plays. The queue is
     * re-read for every track, so recommendations / playlist items that arrive late are still
     * picked up. Safe to call repeatedly: a running prefetch is replaced (partial downloads resume).
     */
    private fun requestPrefetch(context: Context, fromPipeline: Boolean = false) {
        val cur = _currentTrack.value ?: return
        if (!cur.path.startsWith(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX)) return
        // Don't compete with the current track's own download for bandwidth.
        if (!fromPipeline && cacheJob?.isActive == true) return
        val appContext = context.applicationContext
        prefetchJob?.cancel()
        prefetchJob = scope.launch(Dispatchers.IO) {
            try {
                prefetchUpcomingOnline(appContext)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "Prefetch error: ${t.message}")
            }
        }
    }

    private suspend fun prefetchUpcomingOnline(context: Context) {
        for (offset in 1..PREFETCH_AHEAD) {
            val next = upcomingOnlineTrack(offset) ?: continue
            val videoUrl = next.path.removePrefix(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX)
            if (videoUrl.isBlank()) continue
            if (OnlineAudioCache.cachedFile(context, videoUrl) == null) {
                // Never download a web-page URI (e.g. youtube.com/watch?v=...) as if it were audio.
                var stream = preloadedOnlineStreams[videoUrl]
                    ?: next.uri.toString().takeIf { isDirectStreamUrl(it) }
                if (stream == null) {
                    stream = com.example.util.OnlineMusicService.resolveStreamUrl(videoUrl, next.title, next.artist)
                    if (stream != null) preloadedOnlineStreams[videoUrl] = stream
                }
                if (stream == null) continue
                var file = OnlineAudioCache.download(context, videoUrl, stream)
                if (file == null) {
                    // The resolved URL may have expired: resolve a fresh one and try once more.
                    val fresh = com.example.util.OnlineMusicService.resolveStreamUrl(videoUrl, next.title, next.artist)
                    if (fresh != null) {
                        preloadedOnlineStreams[videoUrl] = fresh
                        file = OnlineAudioCache.download(context, videoUrl, fresh)
                    }
                }
                if (file == null) continue
            }
            if (offset == 1) {
                withContext(Dispatchers.Main) { prepareStandbyForUpcoming(context) }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Standby player: the NEXT online track, already cached on disk and already prepared, so the
    // switch at the end of the current song only has to call start().
    // ---------------------------------------------------------------------------------------
    private fun standbyMatches(trackId: Long): Boolean =
        standbyPlayer != null && standbyPrepared && standbyTrackId == trackId

    private fun takeStandbyPlayer(trackId: Long): MediaPlayer? {
        val p = standbyPlayer
        if (p != null && standbyPrepared && standbyTrackId == trackId) {
            standbyPlayer = null
            standbyTrackId = null
            standbyPrepared = false
            return p
        }
        return null
    }

    private fun releaseStandby() {
        val p = standbyPlayer
        standbyPlayer = null
        standbyTrackId = null
        standbyPrepared = false
        if (p != null) {
            try { p.setOnPreparedListener(null) } catch (_: Throwable) {}
            try { p.setOnErrorListener(null) } catch (_: Throwable) {}
            try { p.release() } catch (_: Throwable) {}
        }
    }

    /** Main thread only. Prepares a player for the track skipNext() would play next (if cached). */
    private fun prepareStandbyForUpcoming(context: Context) {
        if (!mediaPlayerPrepared) return
        val next = upcomingOnlineTrack(1)
        if (next == null) {
            releaseStandby()
            return
        }
        if (standbyPlayer != null && standbyTrackId == next.id) return
        val videoUrl = next.path.removePrefix(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX)
        val file = OnlineAudioCache.cachedFile(context, videoUrl)
        releaseStandby()
        if (file == null) return
        val mp = MediaPlayer()
        try {
            runCatching { mp.setWakeMode(context.applicationContext, PowerManager.PARTIAL_WAKE_LOCK) }
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mp.setDataSource(file.absolutePath)
            standbyPlayer = mp
            standbyTrackId = next.id
            standbyPrepared = false
            mp.setOnPreparedListener { p ->
                if (standbyPlayer === p) {
                    standbyPrepared = true
                } else {
                    runCatching { p.release() }
                }
            }
            mp.setOnErrorListener { p, _, _ ->
                if (standbyPlayer === p) {
                    OnlineAudioCache.delete(context, videoUrl)
                    releaseStandby()
                } else {
                    runCatching { p.release() }
                }
                true
            }
            mp.prepareAsync()
        } catch (t: Throwable) {
            Log.w(TAG, "Standby prepare failed: ${t.message}")
            if (standbyPlayer === mp) releaseStandby() else runCatching { mp.release() }
        }
    }

    /** Queue order / repeat mode changed: the pre-buffered "next" track may no longer be right. */
    private fun onQueueOrderChanged() {
        val ctx = applicationContext ?: return
        if (_currentTrack.value == null) return
        prepareStandbyForUpcoming(ctx)
        requestPrefetch(ctx)
    }

    /**
     * Resolve the next three online queue items and prefetch their thumbnails in the background
     * so that track changes and Dynamic Island updates are instantaneous.
     */
    private fun preloadUpcomingOnlineStreams() {
        streamPreloadJob?.cancel()
        val queueSnapshot = _queue.value
        val currentIndexSnapshot = _currentIndex.value
        if (queueSnapshot.isEmpty()) return

        val upcoming = (1..3).mapNotNull { offset ->
            queueSnapshot.getOrNull(currentIndexSnapshot + offset)
                ?: if (_repeatMode.value == AudioRepeatMode.ALL && queueSnapshot.isNotEmpty()) {
                    queueSnapshot.getOrNull((currentIndexSnapshot + offset) % queueSnapshot.size)
                } else null
        }.filter { it.path.startsWith(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX) }
            .distinctBy { it.path }

        if (upcoming.isEmpty()) return

        streamPreloadJob = scope.launch(Dispatchers.IO) {
            for (track in upcoming) {
                // Prefetch high-resolution thumbnail so Next button shows image instantly
                if (!track.thumbnailUrl.isNullOrBlank()) {
                    try {
                        com.example.ui.components.loadRemoteAudioThumbnail(track.thumbnailUrl, 384)
                    } catch (_: Throwable) {}
                }
                val videoUrl = track.path.removePrefix(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX)
                if (videoUrl.isBlank() || preloadedOnlineStreams.containsKey(videoUrl)) continue
                val resolved = com.example.util.OnlineMusicService.resolveStreamUrl(videoUrl, track.title, track.artist)
                if (!resolved.isNullOrBlank()) {
                    preloadedOnlineStreams[videoUrl] = resolved
                    withContext(Dispatchers.Main) {
                        if (_queue.value.any { it.id == track.id }) {
                            val updated = track.copy(uri = Uri.parse(resolved))
                            _queue.value = _queue.value.map { if (it.id == track.id) updated else it }
                            if (originalQueue.isNotEmpty()) {
                                originalQueue = originalQueue.map { if (it.id == track.id) updated else it }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateNotification(
        context: Context?,
        track: AudioTrackItem? = _currentTrack.value,
        playing: Boolean = _isPlaying.value,
        isBuffering: Boolean = _isBuffering.value
    ) {
        val targetContext = context ?: applicationContext ?: return
        AudioNotificationManager.showOrUpdateNotification(
            context = targetContext,
            track = track,
            isPlaying = playing,
            positionMs = _currentPositionMs.value,
            durationMs = _durationMs.value,
            playbackSpeed = _playbackSpeed.value,
            isBuffering = isBuffering
        )
    }

    private fun handleTrackCompletion(context: Context) {
        val current = _currentTrack.value
        if (current != null) {
            val isOnline = current.path.startsWith(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX) ||
                           current.uri.toString().startsWith("http")
            val dur = _durationMs.value.takeIf { it > 0L } ?: current.durationMs
            val pos = _currentPositionMs.value
            // Only switch tracks upon 100% completion
            if (isOnline && dur > 5000L && pos < (dur - 3000L)) {
                val videoUrl = current.path.removePrefix(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX)
                preloadedOnlineStreams.remove(videoUrl)
                Log.w(TAG, "handleTrackCompletion invoked prematurely at $pos ms of $dur ms (~${((pos.toFloat()/dur)*100).toInt()}%). Resuming stream...")
                _isBuffering.value = true
                scope.launch {
                    delay(200L)
                    startMediaPlayer(context, current.copy(uri = Uri.EMPTY), startPositionMs = pos)
                }
                return
            }
        }
        when (_repeatMode.value) {
            AudioRepeatMode.ONE -> {
                seekTo(0L)
                mediaPlayer?.start()
                _isPlaying.value = true
                updateNotification(context, _currentTrack.value, true)
            }
            AudioRepeatMode.ALL -> {
                skipNext(context)
            }
            AudioRepeatMode.OFF -> {
                val q = _queue.value
                val cur = _currentIndex.value
                if (cur < q.size - 1) {
                    skipNext(context)
                } else {
                    _isPlaying.value = false
                    seekTo(0L)
                    stopProgressTracker()
                    updateNotification(context, _currentTrack.value, false)
                }
            }
        }
    }

    fun togglePlayPause(context: Context? = null) {
        val targetContext = context ?: applicationContext
        val player = mediaPlayer ?: return
        if (!mediaPlayerPrepared) return
        try {
            if (player.isPlaying) {
                userExplicitlyPaused = true
                resumeOnFocusGain = false
                abandonAudioFocus()
                val exactPos = runCatching { player.currentPosition.toLong() }.getOrNull() ?: _currentPositionMs.value
                _currentPositionMs.value = exactPos
                targetContext?.let { ctx -> AudioPlaybackHistoryManager.savePosition(ctx, _currentTrack.value, exactPos, _durationMs.value, force = true) }
                player.pause()
                _isPlaying.value = false
                _isBuffering.value = false
                stopProgressTracker()
                targetContext?.let { updateNotification(it, _currentTrack.value, playing = false, isBuffering = false) }
            } else {
                userExplicitlyPaused = false
                resumeOnFocusGain = false
                requestAudioFocus()
                applyPlaybackSpeed(_playbackSpeed.value)
                player.start()
                _isPlaying.value = true
                _isBuffering.value = false
                startProgressTracker()
                targetContext?.let { updateNotification(it, _currentTrack.value, playing = true, isBuffering = false) }
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Ignoring rapid play/pause while MediaPlayer is not ready: ${e.message}")
        }
    }

    fun pause(context: Context? = null) {
        val targetContext = context ?: applicationContext
        userExplicitlyPaused = true
        resumeOnFocusGain = false
        abandonAudioFocus()
        mediaPlayer?.let { player ->
            if (!mediaPlayerPrepared) return
            if (player.isPlaying) {
                val exactPos = runCatching { player.currentPosition.toLong() }.getOrNull() ?: _currentPositionMs.value
                _currentPositionMs.value = exactPos
                targetContext?.let { ctx -> AudioPlaybackHistoryManager.savePosition(ctx, _currentTrack.value, exactPos, _durationMs.value, force = true) }
                player.pause()
                _isPlaying.value = false
                _isBuffering.value = false
                stopProgressTracker()
                targetContext?.let { updateNotification(it, _currentTrack.value, playing = false, isBuffering = false) }
            }
        }
    }

    fun resume(context: Context? = null) {
        val targetContext = context ?: applicationContext
        userExplicitlyPaused = false
        resumeOnFocusGain = false
        mediaPlayer?.let { player ->
            if (!mediaPlayerPrepared) return
            if (!player.isPlaying) {
                requestAudioFocus()
                applyPlaybackSpeed(_playbackSpeed.value)
                player.start()
                _isPlaying.value = true
                startProgressTracker()
                targetContext?.let { updateNotification(it, _currentTrack.value, true) }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val dur = _durationMs.value.coerceAtLeast(1L)
        val clamped = positionMs.coerceIn(0L, dur)
        _currentPositionMs.value = clamped
        applicationContext?.let { ctx -> AudioPlaybackHistoryManager.savePosition(ctx, _currentTrack.value, clamped, _durationMs.value, force = true) }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mediaPlayer?.seekTo(clamped, MediaPlayer.SEEK_CLOSEST)
            } else {
                mediaPlayer?.seekTo(clamped.toInt())
            }
            applicationContext?.let { ctx ->
                AudioNotificationManager.updateMediaSessionState(
                    context = ctx,
                    track = _currentTrack.value,
                    isPlaying = _isPlaying.value,
                    positionMs = clamped,
                    durationMs = _durationMs.value,
                    playbackSpeed = _playbackSpeed.value
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Seek error: ${e.message}")
        }
    }

    fun seekRelative(deltaMs: Long) {
        val newPos = _currentPositionMs.value + deltaMs
        seekTo(newPos)
    }

    fun skipNext(context: Context? = null) {
        val targetContext = context ?: applicationContext
        playbackGeneration++
        mediaPlayerPrepared = false
        val q = _queue.value
        if (q.isEmpty()) return

        streamResolutionJob?.cancel()
        streamResolutionJob = null
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
        } catch (_: Throwable) {}
        mediaPlayer = null
        _isPlaying.value = false
        _isBuffering.value = true
        _currentPositionMs.value = 0L

        val nextIndex = (_currentIndex.value + 1) % q.size
        val nextTrack = q.getOrNull(nextIndex) ?: return
        _currentIndex.value = nextIndex
        _currentTrack.value = nextTrack
        _durationMs.value = nextTrack.durationMs
        trackRetryCount = 0
        lastRetriedTrackId = nextTrack.id
        clearABLoop()
        // Next track already cached + prepared: it starts instantly, so don't flash "buffering".
        val nextIsPrebuffered = nextTrack.path.startsWith(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX) &&
            standbyMatches(nextTrack.id)
        if (nextIsPrebuffered) _isBuffering.value = false

        if (targetContext != null) {
            updateNotification(targetContext, nextTrack, playing = false, isBuffering = !nextIsPrebuffered)
            startMediaPlayer(targetContext, nextTrack, startPositionMs = 0L)
        }
    }

    fun skipPrevious(context: Context? = null) {
        val targetContext = context ?: applicationContext
        // If current position > 3 seconds, restart current track without replacing the player.
        if (_currentPositionMs.value > 3000L) {
            seekTo(0L)
            return
        }

        playbackGeneration++
        mediaPlayerPrepared = false
        val q = _queue.value
        if (q.isEmpty()) return

        streamResolutionJob?.cancel()
        streamResolutionJob = null
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
        } catch (_: Throwable) {}
        mediaPlayer = null
        _isPlaying.value = false
        _isBuffering.value = true
        _currentPositionMs.value = 0L

        val prevIndex = if (_currentIndex.value - 1 < 0) q.size - 1 else _currentIndex.value - 1
        val prevTrack = q.getOrNull(prevIndex) ?: return
        _currentIndex.value = prevIndex
        _currentTrack.value = prevTrack
        _durationMs.value = prevTrack.durationMs
        trackRetryCount = 0
        lastRetriedTrackId = prevTrack.id
        clearABLoop()

        if (targetContext != null) {
            updateNotification(targetContext, prevTrack, playing = false, isBuffering = true)
            startMediaPlayer(targetContext, prevTrack, startPositionMs = 0L)
        }
    }

    fun toggleShuffle() {
        val enablingShuffle = !_isShuffle.value
        _isShuffle.value = enablingShuffle

        val curTrack = _currentTrack.value
        val curTrackId = curTrack?.id

        if (enablingShuffle) {
            if (originalQueue.isEmpty() || originalQueue.size != _queue.value.size) {
                originalQueue = _queue.value
            }
            if (_queue.value.size > 1) {
                val currentQ = _queue.value.toMutableList()
                val currentPlaying = currentQ.firstOrNull { it.id == curTrackId }
                if (currentPlaying != null) {
                    currentQ.remove(currentPlaying)
                    currentQ.shuffle()
                    currentQ.add(0, currentPlaying)
                } else {
                    currentQ.shuffle()
                }
                _queue.value = currentQ
                _currentIndex.value = 0
            }
        } else {
            if (originalQueue.isNotEmpty()) {
                val restored = originalQueue
                _queue.value = restored
                if (curTrackId != null) {
                    val idx = restored.indexOfFirst { it.id == curTrackId }.coerceAtLeast(0)
                    _currentIndex.value = idx
                }
            }
        }
        onQueueOrderChanged()
    }

    fun toggleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            AudioRepeatMode.ALL -> AudioRepeatMode.ONE
            AudioRepeatMode.ONE -> AudioRepeatMode.OFF
            AudioRepeatMode.OFF -> AudioRepeatMode.ALL
        }
        onQueueOrderChanged()
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        applyPlaybackSpeed(speed)
    }

    private fun applyPlaybackSpeed(speed: Float) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying || _isPlaying.value) {
                        val params = player.playbackParams ?: PlaybackParams()
                        params.speed = speed.coerceIn(0.25f, 3.0f)
                        player.playbackParams = params
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply playback speed: ${e.message}")
        }
    }

    fun stop(context: Context? = null) {
        val targetContext = context ?: applicationContext
        playbackGeneration++
        mediaPlayerPrepared = false
        streamResolutionJob?.cancel()
        streamResolutionJob = null
        streamPreloadJob?.cancel()
        streamPreloadJob = null
        cacheJob?.cancel()
        cacheJob = null
        prefetchJob?.cancel()
        prefetchJob = null
        releaseStandby()
        preloadedOnlineStreams.clear()
        targetContext?.let { ctx -> AudioPlaybackHistoryManager.savePosition(ctx, _currentTrack.value, _currentPositionMs.value, _durationMs.value, force = true) }
        stopProgressTracker()
        releaseAudioEffects()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}
        _isPlaying.value = false
        _isBuffering.value = false
        _currentPositionMs.value = 0L
        abandonAudioFocus()
        targetContext?.let { AudioNotificationManager.cancelNotification(it) }
    }

    private fun setupAudioEffects(sessionId: Int) {
        releaseAudioEffects()
        if (sessionId <= 0) return
        try {
            equalizer = Equalizer(0, sessionId).apply {
                enabled = isEqEnabled
            }
        } catch (e: Exception) {
            Log.w(TAG, "Equalizer init error: ${e.message}")
        }

        try {
            bassBoost = BassBoost(0, sessionId).apply {
                enabled = isEqEnabled && (eqVolumeBoostDb > 0.05f)
            }
        } catch (e: Exception) {
            Log.w(TAG, "BassBoost init error: ${e.message}")
        }

        try {
            virtualizer = Virtualizer(0, sessionId).apply {
                enabled = isHeadphoneSurroundEnabled
            }
        } catch (e: Exception) {
            Log.w(TAG, "Virtualizer init error: ${e.message}")
        }

        applyAudioFxImmediately()
    }

    private fun releaseAudioEffects() {
        try {
            equalizer?.release()
            equalizer = null
        } catch (_: Exception) {}
        try {
            bassBoost?.release()
            bassBoost = null
        } catch (_: Exception) {}
        try {
            virtualizer?.release()
            virtualizer = null
        } catch (_: Exception) {}
    }

    /**
     * Applies real-time hardware Equalizer, BassBoost, and Volume Boost directly
     * to the running audio stream with zero delay, zero audio dropout, and zero glitches.
     */
    fun updateEqualizer(
        enabled: Boolean,
        eq60: Float,
        eq230: Float,
        eq910: Float,
        eq3600: Float,
        eq14000: Float,
        volumeBoostDb: Float
    ) {
        isEqEnabled = enabled
        eqGain60 = eq60.coerceIn(-12f, 12f)
        eqGain230 = eq230.coerceIn(-12f, 12f)
        eqGain910 = eq910.coerceIn(-12f, 12f)
        eqGain3600 = eq3600.coerceIn(-12f, 12f)
        eqGain14000 = eq14000.coerceIn(-12f, 12f)
        eqVolumeBoostDb = volumeBoostDb.coerceIn(0f, 10f)

        applyAudioFxImmediately()
    }

    /**
     * Toggles 3D Spatial Virtualizer for immersive headphone surround sound.
     */
    fun setHeadphoneSurround(enabled: Boolean) {
        isHeadphoneSurroundEnabled = enabled
        try {
            virtualizer?.enabled = enabled
            if (enabled) {
                virtualizer?.setStrength(1000.toShort())
            }
        } catch (_: Exception) {}
    }

    private fun applyAudioFxImmediately() {
        try {
            equalizer?.let { eq ->
                eq.enabled = isEqEnabled
                if (isEqEnabled) {
                    val minLevel = eq.bandLevelRange?.getOrNull(0) ?: -1500
                    val maxLevel = eq.bandLevelRange?.getOrNull(1) ?: 1500
                    val numBands = eq.numberOfBands.toInt()
                    val uiGains = floatArrayOf(eqGain60, eqGain230, eqGain910, eqGain3600, eqGain14000)
                    val uiFreqs = intArrayOf(60, 230, 910, 3600, 14000)

                    for (band in 0 until numBands) {
                        val centerFreqHz = try { eq.getCenterFreq(band.toShort()) / 1000 } catch (_: Exception) { 0 }
                        var closestIdx = 0
                        var minDiff = Int.MAX_VALUE
                        for (i in uiFreqs.indices) {
                            val diff = kotlin.math.abs(uiFreqs[i] - centerFreqHz)
                            if (diff < minDiff) {
                                minDiff = diff
                                closestIdx = i
                            }
                        }
                        val gainDb = if (numBands == 5 && band < 5) uiGains[band] else uiGains[closestIdx]
                        val millibels = (gainDb * 100f).toInt().coerceIn(minLevel.toInt(), maxLevel.toInt())
                        eq.setBandLevel(band.toShort(), millibels.toShort())
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying equalizer bands: ${e.message}")
        }

        try {
            bassBoost?.let { bb ->
                val active = isEqEnabled && (eqVolumeBoostDb > 0.05f)
                bb.enabled = active
                if (active) {
                    val strength = (eqVolumeBoostDb * 100f).toInt().coerceIn(0, 1000)
                    bb.setStrength(strength.toShort())
                }
            }
        } catch (_: Exception) {}

        try {
            virtualizer?.let { v ->
                v.enabled = isHeadphoneSurroundEnabled
                if (isHeadphoneSurroundEnabled) {
                    v.setStrength(1000.toShort())
                }
            }
        } catch (_: Exception) {}

        try {
            mediaPlayer?.let { player ->
                if (eqVolumeBoostDb > 0.05f) {
                    val multiplier = Math.pow(10.0, (eqVolumeBoostDb / 25.0)).toFloat().coerceIn(1.0f, 2.0f)
                    player.setVolume(multiplier, multiplier)
                } else {
                    player.setVolume(1.0f, 1.0f)
                }
            }
        } catch (_: Exception) {}
    }

    private fun handleOnlineStreamStall(context: Context?, track: AudioTrackItem, pos: Long) {
        val targetContext = context ?: applicationContext ?: return
        if (lastRetriedTrackId != track.id) {
            lastRetriedTrackId = track.id
            trackRetryCount = 0
        }
        if (trackRetryCount < 40) {
            trackRetryCount++
            val videoUrl = track.path.removePrefix(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX)
            preloadedOnlineStreams.remove(videoUrl)
            Log.w(TAG, "Online stream stalled at $pos ms. Watchdog triggering reconnect retry $trackRetryCount of 40...")
            _isBuffering.value = true
            updateNotification(targetContext, track, playing = true, isBuffering = true)
            scope.launch {
                delay(minOf(500L * trackRetryCount, 5000L))
                startMediaPlayer(targetContext, track.copy(uri = Uri.EMPTY), startPositionMs = pos)
            }
        } else {
            // Never abandon a song mid-way because of a slow network: keep waiting and retrying.
            Log.w(TAG, "Online stream still stalled at $pos ms; continuing to wait instead of skipping.")
            trackRetryCount = 20
            _isBuffering.value = true
        }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = scope.launch {
            var lastPos = -1L
            var stalledTicks = 0
            while (isActive) {
                try {
                    mediaPlayer?.let { player ->
                        if (player.isPlaying) {
                            val pos = player.currentPosition.toLong()
                            _currentPositionMs.value = pos
                            val dur = player.duration.toLong()
                            if (dur > 0L) {
                                _durationMs.value = dur
                            }
                            applicationContext?.let { ctx ->
                                AudioPlaybackHistoryManager.savePosition(ctx, _currentTrack.value, pos, _durationMs.value)
                            }

                            // Buffer & network stall watchdog for online tracks
                            val current = _currentTrack.value
                            val isOnline = current != null && (
                                current.path.startsWith(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX) ||
                                current.uri.toString().startsWith("http")
                            )
                            if (isOnline && dur > 5000L && pos < (dur - 2000L)) {
                                if (pos == lastPos) {
                                    stalledTicks++
                                    // Stalled for > 3 seconds (12 ticks of 250ms)
                                    if (stalledTicks == 12) {
                                        _isBuffering.value = true
                                        updateNotification(applicationContext, current, playing = true, isBuffering = true)
                                    }
                                    // Stalled for > 6 seconds (24 ticks of 250ms) -> trigger auto-recovery
                                    if (stalledTicks >= 24) {
                                        stalledTicks = 0
                                        handleOnlineStreamStall(applicationContext, current, pos)
                                    }
                                } else {
                                    if (stalledTicks >= 12) {
                                        _isBuffering.value = false
                                        updateNotification(applicationContext, current, playing = true, isBuffering = false)
                                    }
                                    stalledTicks = 0
                                    lastPos = pos
                                }
                            } else {
                                stalledTicks = 0
                                lastPos = pos
                            }

                            // AB Loop checking
                            val ptA = _loopPointA.value
                            val ptB = _loopPointB.value
                            if (ptA != null && ptB != null && ptB > ptA) {
                                if (pos >= ptB) {
                                    seekTo(ptA)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
                delay(250L)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        player.pause()
                        _isPlaying.value = false
                        stopProgressTracker()
                        applicationContext?.let { updateNotification(it, _currentTrack.value, false) }
                    }
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // If music was actively playing when mic or external app requested audio focus,
                // pause immediately so microphone (e.g. keyboard voice typing) does not record music.
                // However, if the user had ALREADY explicitly paused playback, keep resumeOnFocusGain = false
                // so that when mic closes, music does NOT automatically start playing!
                val wasPlaying = _isPlaying.value && (mediaPlayer?.isPlaying == true)
                if (wasPlaying && !userExplicitlyPaused) {
                    resumeOnFocusGain = true
                    mediaPlayer?.let { player ->
                        player.pause()
                        _isPlaying.value = false
                        stopProgressTracker()
                        applicationContext?.let { updateNotification(it, _currentTrack.value, false) }
                    }
                } else {
                    resumeOnFocusGain = false
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // NEVER resume if user explicitly paused the music!
                if (resumeOnFocusGain && !userExplicitlyPaused) {
                    resumeOnFocusGain = false
                    mediaPlayer?.let { player ->
                        if (!player.isPlaying) {
                            applyPlaybackSpeed(_playbackSpeed.value)
                            player.start()
                            _isPlaying.value = true
                            startProgressTracker()
                            applicationContext?.let { updateNotification(it, _currentTrack.value, true) }
                        }
                    }
                } else {
                    resumeOnFocusGain = false
                }
            }
        }
    }

    private fun requestAudioFocus() {
        try {
            val am = audioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { focusChange ->
                        handleAudioFocusChange(focusChange)
                    }
                    .build()
                focusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    { focusChange ->
                        handleAudioFocusChange(focusChange)
                    },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        } catch (_: Exception) {}
    }

    private fun abandonAudioFocus() {
        try {
            resumeOnFocusGain = false
            val am = audioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
    }
}
