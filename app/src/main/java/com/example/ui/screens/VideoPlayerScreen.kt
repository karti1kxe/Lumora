package com.example.ui.screens

import com.example.ai.AiFeaturesSettingsStore
import com.example.ai.TrackTranslationStatus
import com.example.ai.TranslateLanguages
import com.example.ui.components.StyledIcon
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ui.components.AudioFileTreeDialog
import com.example.ui.components.SubtitleFileTreeDialog
import androidx.core.view.WindowInsetsControllerCompat
import com.example.player.AudioLanguageMatcher
import com.example.player.OnlineSkipMarkerService
import com.example.player.EqualizerPreset
import com.example.player.TrackAudioConfig
import com.example.player.AudioChannelMode
import com.example.player.VideoFilterPreset
import com.example.player.ManualVideoAdjustments
import com.example.player.MpvPlayerController
import com.example.player.SubtitleFontManager
import com.example.player.VideoPlayerMpvView
import com.example.player.ChapterSkipDetector
import com.example.player.ChapterSkipMarker
import com.example.ui.components.ThumbnailCache
import com.example.ui.components.loadVideoThumbnail
import com.example.ui.state.ControlsAnimationStyle
import com.example.ui.state.GestureSensitivityMode
import com.example.ui.state.HwAccelMode
import com.example.ui.state.PlayerSettings
import com.example.ui.state.UiState
import com.example.MainActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.example.ui.components.LumoraExpressiveLoadingIndicator
import com.example.ui.components.loadRemoteAudioThumbnail
import kotlinx.coroutines.withContext
import com.example.util.PipModeState
import com.example.util.PlaybackHistoryManager
import com.example.util.CustomSubtitlePersistenceManager
import com.example.util.AttachedSubtitleRecord
import com.example.util.SavedSubtitleRef
import com.example.util.VideoSubtitleSelection
import com.example.player.PlayerMediaTrack
import com.example.util.SubtitleSessionMemory
import com.example.util.VideoAudioSelectionMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File





private fun resolveContainerAudioFallback(tracks: List<PlayerMediaTrack>): PlayerMediaTrack? {
    val embedded = tracks.filter { !it.isExternal }
    if (embedded.isEmpty()) return null

    fun score(track: PlayerMediaTrack): Int {
        val title = track.title.lowercase()
        val isDescription = title.contains("audio description") || title.contains("descriptive")
        val isCommentary = track.isAudioCommentary || title.contains("commentary") || title.contains("commentator")
        val isImpaired = track.isHearingImpaired || track.isVisualImpaired
        var value = 0
        if (track.isDefault) value += 100
        if (track.isForced) value += 35
        if (title.contains("original")) value += 25
        if (isDescription) value -= 80
        if (isCommentary) value -= 100
        if (isImpaired) value -= 30
        return value
    }

    val usable = embedded.filter { track ->
        val title = track.title.lowercase()
        !track.isAudioCommentary &&
            !track.isHearingImpaired &&
            !track.isVisualImpaired &&
            !title.contains("commentary") &&
            !title.contains("commentator") &&
            !title.contains("audio description") &&
            !title.contains("descriptive")
    }
    return (usable.ifEmpty { embedded }).maxByOrNull(::score)
}

private fun resolveContainerSubtitleFallback(tracks: List<PlayerMediaTrack>): PlayerMediaTrack? {
    val embedded = tracks.filter { !it.isExternal }
    if (embedded.isEmpty()) return null

    fun score(track: PlayerMediaTrack): Int {
        val title = track.title.lowercase()
        val isSignsOrForcedTitle = Regex("(?i)signs|songs|forced|narrative").containsMatchIn(track.title)
        var value = 0
        if (track.isForced) value += 120
        if (track.isDefault) value += 80
        if (isSignsOrForcedTitle) value += 15
        if (track.isHearingImpaired) value -= 25
        if (track.isVisualImpaired) value -= 25
        if (title.contains("commentary")) value -= 80
        return value
    }

    val usable = embedded.filter { track ->
        val title = track.title.lowercase()
        !track.isHearingImpaired &&
            !track.isVisualImpaired &&
            !title.contains("commentary")
    }.ifEmpty { embedded }
    val flagged = usable.filter { it.isForced || it.isDefault }
    return if (flagged.isNotEmpty()) flagged.maxByOrNull(::score) else usable.firstOrNull()
}

/**
 * Polls the MPV controller for up to [timeoutMs] to verify playback has GENUINELY started -
 * not just that `isPlaying` flipped true, but that the playback position is actually
 * advancing and no error has been reported. Used by the regular online (YouTube)
 * path, so the loading spinner/hidden playback buttons never disappear before the video
 * can actually be seen playing.
 */
private suspend fun waitForPlaybackStart(
    controller: MpvPlayerController,
    timeoutMs: Long = 30_000L
): Boolean {
    val start = System.currentTimeMillis()
    var lastPos = -1L
    var advancingChecks = 0
    while (System.currentTimeMillis() - start < timeoutMs) {
        val err = controller.errorMessage.value
        if (!err.isNullOrBlank()) return false
        val pos = controller.currentPositionMs.value
        val playing = controller.isPlaying.value
        if (playing && pos > 300L && pos > lastPos) {
            advancingChecks++
            if (advancingChecks >= 2) return true
        } else if (!playing) {
            advancingChecks = 0
        }
        lastPos = pos
        kotlinx.coroutines.delay(700L)
    }
    return false
}

/**
 * Ultra-Fast, Zero-Lag Video Player Screen with Native MPV & ASS Subtitle Engine
 */
@Composable
fun VideoPlayerScreen(
    video: VideoItem?,
    playlist: List<VideoItem> = emptyList(),
    uiState: UiState,
    onGestureSensitivityModeChange: (GestureSensitivityMode) -> Unit = {},
    onSeekbarStyleChange: (com.example.ui.state.SeekbarStyle) -> Unit = {},
    onPlayerLayoutConfigChange: (com.example.ui.state.PlayerLayoutConfig) -> Unit = {},
    onResetPlayerLayoutConfig: () -> Unit = {},
    onLandscapeChanged: (Boolean) -> Unit = {},
    // Called on every subtitle style change (typography/colors/misc, the Override ASS/SSA
    // toggle, scale, position) alongside this screen's own debounced disk save, so the
    // ViewModel's cached UiState.playerSettings never goes stale - see
    // AppViewModel.syncPlayerSettingsInMemory for why this must not be skipped.
    onPlayerSettingsChange: (PlayerSettings) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var wasInLandscape by remember { mutableStateOf(false) }

    var waitForPortraitBeforeBack by remember { mutableStateOf(false) }
    var entryLoadDelayDone by remember { mutableStateOf(false) }

    // ---- Seamless landscape entry -------------------------------------------------------
    // Exact mirror of the landscape exit: when this player is going to be landscape but the
    // window is still portrait, the player slides in (bottom -> up) already drawn landscape
    // via a Compose rotation, and only afterwards is the real window orientation switched
    // (with the system rotation animation disabled), so the user never sees a rotate/flip.
    val entryRequestedMode = uiState.playerSettings.orientation.uppercase()
    val entryVideoIsWide = remember(video?.path) {
        val m = Regex("(\\d+)\\s*[x\u00D7]\\s*(\\d+)").find(video?.resolution.orEmpty())
        val w = m?.groupValues?.getOrNull(1)?.toLongOrNull()
        val h = m?.groupValues?.getOrNull(2)?.toLongOrNull()
        w != null && h != null && w > h
    }
    val entryWantsLandscape = entryRequestedMode == "LANDSCAPE" ||
        entryRequestedMode == "REVERSE_LANDSCAPE" ||
        entryRequestedMode == "SENSOR_LANDSCAPE" ||
        (entryRequestedMode == "VIDEO" && entryVideoIsWide)
    var entryFakeLandscape by remember { mutableStateOf(entryWantsLandscape && !isLandscape) }
    val entryFakeRotation = if (entryRequestedMode == "REVERSE_LANDSCAPE") -90f else 90f

    LaunchedEffect(isLandscape) {
        if (isLandscape) {
            wasInLandscape = true
        }
        onLandscapeChanged(isLandscape)
    }

    // Physical orientation follows the selected Player Settings mode.
    DisposableEffect(uiState.playerSettings.orientation) {
        val requested = when (uiState.playerSettings.orientation) {
            "VIDEO" -> if (entryVideoIsWide || configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "PORTRAIT" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "REVERSE_PORTRAIT" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            "SENSOR_PORTRAIT" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            "LANDSCAPE" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            "REVERSE_LANDSCAPE" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            "SENSOR_LANDSCAPE" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
        // While the fake-landscape entry is running the window stays portrait; the real
        // orientation is applied by the hand-off effect below.
        if (!entryFakeLandscape) {
            activity?.requestedOrientation = requested
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // Hand-off: after the slide-in finishes, switch the window to real landscape without the
    // system rotation animation, then drop the Compose rotation on the very next frame.
    LaunchedEffect(Unit) {
        if (!entryFakeLandscape) return@LaunchedEffect
        val speed = uiState.animationSpeed.coerceIn(0.25f, 2f)
        kotlinx.coroutines.delay((420 / speed).toLong())
        activity?.window?.let { window ->
            val params = window.attributes
            params.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_JUMPCUT
            window.attributes = params
        }
        activity?.requestedOrientation = if (entryRequestedMode == "REVERSE_LANDSCAPE") {
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        // Safety net: never stay in the fake-landscape state if the rotation never arrives.
        kotlinx.coroutines.delay(1800)
        entryFakeLandscape = false
    }
    LaunchedEffect(entryFakeLandscape, isLandscape) {
        if (entryFakeLandscape && isLandscape) {
            androidx.compose.runtime.withFrameNanos { }
            entryFakeLandscape = false
            activity?.window?.let { window ->
                val params = window.attributes
                params.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_ROTATE
                window.attributes = params
            }
            if (entryRequestedMode == "SENSOR_LANDSCAPE") {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
    }

    // System Action Notification Banner State
    var playerNotificationMessage by remember { mutableStateOf<String?>(null) }
    var playerNotificationKey by remember { mutableLongStateOf(0L) }

    val showNotification: (String) -> Unit = { msg ->
        if (uiState.showPlayerNotifications && msg.isNotBlank()) {
            playerNotificationMessage = msg
            playerNotificationKey = System.currentTimeMillis()
        }
    }

    // Active playlist and video index
    var currentPlaylist by remember(playlist, video) {
        mutableStateOf(
            if (playlist.isNotEmpty()) playlist
            else listOfNotNull(video)
        )
    }

    var currentVideoIndex by remember(video, playlist) {
        val initialList = if (playlist.isNotEmpty()) playlist else listOfNotNull(video)
        val idx = initialList.indexOfFirst { it.id == video?.id }
        mutableIntStateOf(if (idx >= 0) idx else 0)
    }

    val currentVideo = remember(currentVideoIndex, currentPlaylist, video) {
        currentPlaylist.getOrNull(currentVideoIndex) ?: video
    }

    val initialSavedPosition = remember(currentVideo, uiState.playerSettings.savePositionOnQuit) {
        if (uiState.playerSettings.savePositionOnQuit && currentVideo != null) {
            val p = PlaybackHistoryManager.getSavedPosition(
                context = context,
                videoId = currentVideo.id,
                path = currentVideo.path,
                uriString = currentVideo.uri.toString(),
                title = currentVideo.displayName
            )
            if (p > 500L) p else 0L
        } else 0L
    }

    // Playback state
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember(currentVideo) { mutableLongStateOf(initialSavedPosition) }
    var durationMs by remember(currentVideo) {
        mutableLongStateOf(if ((currentVideo?.durationMs ?: 0L) > 0L) currentVideo?.durationMs ?: 0L else 0L)
    }
    var watchedThresholdReached by remember(currentVideo) { mutableStateOf(false) }
    var playbackSpeed by remember { mutableDoubleStateOf(1.0) }
    var decoderMode by remember { mutableStateOf(DecoderMode.HW_PLUS) }
    var repeatMode by remember { mutableStateOf(PlayerRepeatMode.OFF) }
    var isShuffle by remember { mutableStateOf(false) }
    var isAudioOnly by remember { mutableStateOf(false) }
    var aspectRatioMode by remember { mutableStateOf(AspectRatioMode.FIT) }

    // Video Zoom & Pan State (Realtime Interactive Scaling)
    var videoZoomScale by remember { mutableFloatStateOf(1.0f) }
    var videoPanOffset by remember { mutableStateOf(Offset.Zero) }
    var isPanAndZoomEnabled by remember { mutableStateOf(false) }
    var isVideoFlippedVertically by remember(currentVideo) { mutableStateOf(false) }
    var isVideoMirrored by remember(currentVideo) { mutableStateOf(false) }
    var isPitchCorrectionEnabled by remember { mutableStateOf(true) }

    // A-B Repeat Loop points
    var loopPointA by remember { mutableStateOf<Long?>(null) }
    var loopPointB by remember { mutableStateOf<Long?>(null) }

    // Video Adjustments
    val initialBrightness = remember(currentVideo, uiState.playerSettings.rememberBrightness) {
        val remembered = if (uiState.playerSettings.rememberBrightness) {
            context.getSharedPreferences("lumora_display", Context.MODE_PRIVATE)
                .getFloat("remembered_brightness", Float.NaN)
                .takeIf { it.isFinite() }
        } else null
        remembered ?: run {
            val wBright = activity?.window?.attributes?.screenBrightness ?: -1f
            if (wBright in 0.01f..1.0f) wBright
            else {
            try {
                val sys = android.provider.Settings.System.getInt(
                    context.contentResolver,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS
                )
                (sys / 255f).coerceIn(0.05f, 1.0f)
                } catch (_: Throwable) {
                    0.5f
                }
            }
        }
    }
    var brightness by remember { mutableFloatStateOf(initialBrightness) }
    var contrast by remember { mutableFloatStateOf(1.0f) }
    var saturation by remember { mutableFloatStateOf(1.0f) }

    // Audio & Subtitle tracks
    var hasManualAudioTrackSelection by remember(currentVideo) { mutableStateOf(false) }
    var selectedAudioTrackId by remember { mutableIntStateOf(1) }
    var selectedSubtitleTrackId by remember(currentVideo) { mutableIntStateOf(0) }
    var lastTouchedSubtitleTrackId by remember(currentVideo) { mutableIntStateOf(0) }
    var translationStatusByTrackId by remember {
        mutableStateOf<Map<Int, TrackTranslationStatus>>(emptyMap())
    }
    var isSubtitleVisible by remember(currentVideo) { mutableStateOf(false) }
    var subtitleOffsetMs by remember { mutableLongStateOf(0L) }
    var audioDelayMs by remember { mutableLongStateOf(0L) }
    var subtitleScale by remember(uiState.playerSettings.subtitleScale) { mutableFloatStateOf(uiState.playerSettings.subtitleScale) }
    var subtitlePosition by remember(uiState.playerSettings.subtitlePosition) { mutableFloatStateOf(uiState.playerSettings.subtitlePosition) }
    var liveSubtitleSettings by remember { mutableStateOf(uiState.playerSettings) }
    var showSubtitleFileTree by remember { mutableStateOf(false) }
    var showAudioFileTree by remember { mutableStateOf(false) }

    // Audio Equalizer & Volume Boost
    var equalizerEnabled by remember { mutableStateOf(uiState.playerSettings.equalizerEnabled) }
    var eq60Hz by remember { mutableFloatStateOf(uiState.playerSettings.equalizer60Hz) }
    var eq230Hz by remember { mutableFloatStateOf(uiState.playerSettings.equalizer230Hz) }
    var eq910Hz by remember { mutableFloatStateOf(uiState.playerSettings.equalizer910Hz) }
    var eq3600Hz by remember { mutableFloatStateOf(uiState.playerSettings.equalizer3600Hz) }
    var eq14000Hz by remember { mutableFloatStateOf(uiState.playerSettings.equalizer14000Hz) }
    var volumeBoostDb by remember { mutableFloatStateOf(uiState.playerSettings.equalizerVolumeBoostDb) }
    var equalizerPreset by remember { mutableStateOf(uiState.playerSettings.equalizerPreset) }
    var trackAudioConfigs by remember {
        mutableStateOf(PlayerSettings.parseTrackAudioConfigs(uiState.playerSettings.trackAudioConfigsJson))
    }

    // Video Filter Presets & Manual Video Adjustments
    var videoFilterPreset by remember {
        mutableStateOf(VideoFilterPreset.fromId(uiState.playerSettings.videoFilterPreset))
    }
    var manualVideoAdjustments by remember {
        mutableStateOf(
            ManualVideoAdjustments(
                brightness = uiState.playerSettings.videoBrightness,
                contrast = uiState.playerSettings.videoContrast,
                saturation = uiState.playerSettings.videoSaturation,
                gamma = uiState.playerSettings.videoGamma,
                sharpness = uiState.playerSettings.videoSharpness,
                hue = uiState.playerSettings.videoHue,
                temperature = uiState.playerSettings.videoTemperature,
                tint = uiState.playerSettings.videoTint,
                deband = uiState.playerSettings.videoDeband
            )
        )
    }

    val videoTitle = currentVideo?.displayName ?: "Video"
    val totalEpisodes = if (currentPlaylist.isNotEmpty()) currentPlaylist.size else 1
    val currentEpisodeIndex = if (currentPlaylist.isNotEmpty()) currentVideoIndex + 1 else 1

    var lastLoadedVideoKey by remember { mutableStateOf<String?>(null) }

    // Video thumbnail / first frame state
    val thumbnailCacheKey = remember(currentVideo, uiState.thumbnailStrategy, uiState.thumbnailQuality, uiState.thumbnailFallbackSecond) {
        currentVideo?.let { "${it.id}|${it.path}|${uiState.thumbnailStrategy.name}|${uiState.thumbnailQuality.name}" }
    }
    var thumbnailBitmap by remember(thumbnailCacheKey) {
        mutableStateOf(
            currentVideo?.let {
                ThumbnailCache.getCachedThumbnail(
                    videoId = it.id,
                    path = it.path,
                    strategy = uiState.thumbnailStrategy,
                    quality = uiState.thumbnailQuality,
                    fallbackSecond = uiState.thumbnailFallbackSecond
                )
            }
        )
    }
    var hasRenderedInitialFrame by remember(currentVideo) { mutableStateOf(false) }

    LaunchedEffect(currentVideo) {
        if (currentVideo != null) {
            com.example.util.MediaSeenManager.markSeen(context, currentVideo.path, currentVideo.id)
            if (thumbnailBitmap == null) {
                val bmp = loadVideoThumbnail(context, currentVideo.id, currentVideo.uri, currentVideo.path, strategy = uiState.thumbnailStrategy, quality = uiState.thumbnailQuality, fallbackSecond = uiState.thumbnailFallbackSecond)
                if (bmp != null) {
                    thumbnailBitmap = bmp
                }
            }
        }
    }

    // MPV Controller integration
    val controller = remember { MpvPlayerController() }

    val onStartTranslate: (Int) -> Unit = { sourceTrackId ->
        if (translationStatusByTrackId[sourceTrackId] is TrackTranslationStatus.Translating) {
            // Already running translation for this track
        } else {
            val settings = AiFeaturesSettingsStore.load(context)
            if (!settings.hasUsableModel) {
                showNotification("Configure a usable API model or installed offline model first")
            } else {
                val targetLang = TranslateLanguages.ALL.firstOrNull { it.code == settings.translateLanguageCode }?.label ?: settings.translateLanguageCode
                coroutineScope.launch {
                    val startTime = System.currentTimeMillis()
                    translationStatusByTrackId = translationStatusByTrackId +
                        (sourceTrackId to TrackTranslationStatus.Translating(0, null))

                    val result = controller.translateSubtitleTrack(
                        context = context,
                        trackId = sourceTrackId,
                        settings = settings,
                        targetLanguageLabel = targetLang,
                        onProgress = { p ->
                            val percent = if (p.total == 0) 0 else ((p.completed * 100) / p.total).coerceIn(0, 100)
                            val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
                            val etaSeconds = if (percent in 1..99) {
                                val totalEstimateMs = elapsedMs / (percent / 100.0)
                                val remainingMs = (totalEstimateMs - elapsedMs).coerceAtLeast(0.0)
                                (remainingMs / 1000.0).toInt().coerceAtLeast(1)
                            } else null
                            translationStatusByTrackId = translationStatusByTrackId +
                                (sourceTrackId to TrackTranslationStatus.Translating(percent, etaSeconds))
                        }
                    )
                    result.fold(
                        onSuccess = { translatedTrackId ->
                            val status = TrackTranslationStatus.Completed(targetLang, translatedTrackId)
                            translationStatusByTrackId = translationStatusByTrackId + (translatedTrackId to status)
                            if (translatedTrackId != sourceTrackId) {
                                translationStatusByTrackId = translationStatusByTrackId - sourceTrackId
                            }
                            showNotification("Translated to $targetLang")
                        },
                        onFailure = { error ->
                            val msg = error.localizedMessage ?: "Translation failed"
                            translationStatusByTrackId = translationStatusByTrackId +
                                (sourceTrackId to TrackTranslationStatus.Failed(msg))
                            showNotification("Translation failed: $msg")
                        }
                    )
                }
            }
        }
    }
    var lastKnownOrientation by remember { mutableIntStateOf(configuration.orientation) }

    LaunchedEffect(configuration.orientation) {
        if (lastKnownOrientation != configuration.orientation) {
            lastKnownOrientation = configuration.orientation
        }
    }
    val resolvedPlayablePath = remember(currentVideo) {
        if (currentVideo != null) {
            getPlayableFilePath(context, currentVideo.uri, currentVideo.path)
        } else ""
    }

    LaunchedEffect(selectedAudioTrackId, trackAudioConfigs, resolvedPlayablePath) {
        val config = trackAudioConfigs[selectedAudioTrackId] ?: TrackAudioConfig()
        controller.setAudioChannelMode(config.channelMode)
        controller.setVolumeNormalization(config.volumeNormalization)
        controller.setDynamicRangeCompression(config.dynamicRangeCompression)
    }

    // Audio Equalizer & Dynamic Filter Sync
    LaunchedEffect(resolvedPlayablePath, equalizerEnabled, eq60Hz, eq230Hz, eq910Hz, eq3600Hz, eq14000Hz, volumeBoostDb) {
        controller.setEqualizer(
            enabled = equalizerEnabled,
            eq60Hz = eq60Hz,
            eq230Hz = eq230Hz,
            eq910Hz = eq910Hz,
            eq3600Hz = eq3600Hz,
            eq14000Hz = eq14000Hz,
            volumeBoostDb = volumeBoostDb
        )
    }

    // Video Filter Presets & Manual Video Adjustments Live Sync
    LaunchedEffect(resolvedPlayablePath, videoFilterPreset, manualVideoAdjustments) {
        controller.setVideoFilterAndAdjustments(videoFilterPreset, manualVideoAdjustments)
    }

    // Flip Vertical / Mirror -> actual mpv video frames. Keyed on resolvedPlayablePath so the state
    // (which is reset per video via remember(currentVideo)) is pushed again whenever the video changes.
    LaunchedEffect(resolvedPlayablePath, isVideoFlippedVertically, isVideoMirrored) {
        controller.setVideoTransform(
            flipVertical = isVideoFlippedVertically,
            mirrorHorizontal = isVideoMirrored
        )
    }

    // ---- Per-video subtitle SELECTION memory (embedded / uploaded / both) ----
    fun toSavedSubtitleRef(track: PlayerMediaTrack?, allTracks: List<PlayerMediaTrack>): SavedSubtitleRef? {
        if (track == null) return null
        return if (track.isExternal) {
            SavedSubtitleRef(
                kind = CustomSubtitlePersistenceManager.KIND_UPLOADED,
                name = track.originalFilename.ifBlank { track.title },
                language = track.language,
                index = -1
            )
        } else {
            SavedSubtitleRef(
                kind = CustomSubtitlePersistenceManager.KIND_EMBEDDED,
                name = track.title,
                language = track.language,
                index = allTracks.filter { !it.isExternal }.indexOfFirst { it.id == track.id }
            )
        }
    }

    /** Remembers the audio track (language) the user picked for the CURRENT video only. */
    fun saveAudioSelection(trackId: Int) {
        val vid = currentVideo ?: return
        val tracks = controller.audioTracks.value
        val track = tracks.firstOrNull { it.id == trackId } ?: return
        VideoAudioSelectionMemory.save(
            context = context,
            path = vid.path,
            videoId = vid.id,
            uriString = vid.uri.toString(),
            title = vid.displayName,
            track = track,
            allTracks = tracks
        )
    }

    /** Remembers the main + 2nd subtitle of the CURRENT video only (0 = nothing selected). */
    fun saveSubtitleSelection(
        primaryId: Int,
        secondaryId: Int = controller.secondarySubtitleTrackId.value
    ) {
        val vid = currentVideo ?: return
        val tracks = controller.subtitleTracks.value
        val selection = VideoSubtitleSelection(
            primary = toSavedSubtitleRef(tracks.firstOrNull { primaryId > 0 && it.id == primaryId }, tracks),
            secondary = toSavedSubtitleRef(tracks.firstOrNull { secondaryId > 0 && it.id == secondaryId }, tracks)
        )
        CustomSubtitlePersistenceManager.saveSubtitleSelection(
            context = context,
            path = vid.path,
            videoId = vid.id,
            uriString = vid.uri.toString(),
            title = vid.displayName,
            selection = selection
        )
    }

    fun resolveSavedSubtitle(ref: SavedSubtitleRef?, tracks: List<PlayerMediaTrack>): PlayerMediaTrack? {
        if (ref == null) return null
        return if (ref.kind == CustomSubtitlePersistenceManager.KIND_UPLOADED) {
            val wanted = ref.name.trim()
            val wantedBase = wanted.substringBeforeLast('.', wanted).trim()
            tracks.firstOrNull { t ->
                t.isExternal && (
                    t.originalFilename.equals(wanted, ignoreCase = true) ||
                    t.title.equals(wanted, ignoreCase = true) ||
                    File(t.originalFilename).name.equals(wanted, ignoreCase = true) ||
                    File(t.title).name.equals(wanted, ignoreCase = true)
                )
            } ?: tracks.firstOrNull { t ->
                val n = t.originalFilename.ifBlank { t.title }.trim()
                val nBase = n.substringBeforeLast('.', n).trim()
                t.isExternal && (
                    n.equals(wantedBase, ignoreCase = true) ||
                    nBase.equals(wantedBase, ignoreCase = true) ||
                    n.contains(wantedBase, ignoreCase = true) ||
                    wanted.contains(nBase, ignoreCase = true)
                )
            } ?: tracks.firstOrNull { t ->
                // If only one external subtitle track is present and this ref is for an uploaded subtitle, resolve it!
                t.isExternal
            }
        } else {
            val embedded = tracks.filter { !it.isExternal }
            embedded.firstOrNull { it.title == ref.name && it.language.equals(ref.language, ignoreCase = true) }
                ?: embedded.firstOrNull { it.title == ref.name }
                ?: embedded.getOrNull(ref.index)
        }
    }

    fun selectSubtitleTracks(primaryId: Int, secondaryId: Int, notify: Boolean = true, persist: Boolean = true) {
        // Enforce the atomic state machine:
        // If primary is unselected (<= 0) but secondary exists (> 0),
        // secondary is automatically and immediately promoted to Primary!
        val (finalPrimaryId, finalSecondaryId) = if (primaryId <= 0 && secondaryId > 0) {
            secondaryId to 0
        } else {
            primaryId to if (secondaryId > 0 && secondaryId != primaryId) secondaryId else 0
        }

        selectedSubtitleTrackId = finalPrimaryId
        if (finalPrimaryId > 0) {
            lastTouchedSubtitleTrackId = finalPrimaryId
        } else if (finalSecondaryId > 0) {
            lastTouchedSubtitleTrackId = finalSecondaryId
        }

        val hasAny = finalPrimaryId > 0 || finalSecondaryId > 0
        isSubtitleVisible = hasAny
        SubtitleSessionMemory.setSubtitlesEnabled(hasAny)

        controller.selectSubtitleTracksAtomic(finalPrimaryId, finalSecondaryId)

        if (finalPrimaryId > 0) {
            val selectedTrack = controller.subtitleTracks.value.firstOrNull { it.id == finalPrimaryId }
            if (selectedTrack != null) {
                if (selectedTrack.isExternal) {
                    if (persist) CustomSubtitlePersistenceManager.setSubtitleSelected(
                        context = context,
                        path = currentVideo?.path,
                        videoId = currentVideo?.id ?: 0L,
                        uriString = currentVideo?.uri?.toString(),
                        title = currentVideo?.displayName,
                        originalName = selectedTrack.originalFilename.ifBlank { selectedTrack.title },
                        isSelected = true
                    )
                } else {
                    val embedded = controller.subtitleTracks.value.filter { !it.isExternal }
                    val idx = embedded.indexOfFirst { it.id == finalPrimaryId }
                    val trackNum = if (idx >= 0) idx + 1 else finalPrimaryId
                    SubtitleSessionMemory.setPreferredTrack(trackNum, finalPrimaryId)
                    controller.preferredSubtitleTrackNumber = trackNum
                }
            }
            if (notify) {
                val subName = selectedTrack?.title ?: "Track $finalPrimaryId"
                showNotification("Primary Subtitle: $subName")
            }
        } else {
            controller.preferredSubtitleTrackNumber = 0
            SubtitleSessionMemory.setPreferredTrack(0, 0)
            if (notify && finalSecondaryId <= 0) {
                showNotification("Subtitles Off")
            }
        }

        // Restoring a remembered choice must never rewrite that memory (persist = false).
        if (!persist) return

        // Update persistence for all external subtitles so unselected ones are marked false
        val allSubs = controller.subtitleTracks.value
        allSubs.filter { it.isExternal }.forEach { extTrack ->
            val isNowSelected = (extTrack.id == finalPrimaryId) || (extTrack.id == finalSecondaryId)
            CustomSubtitlePersistenceManager.setSubtitleSelected(
                context = context,
                path = currentVideo?.path,
                videoId = currentVideo?.id ?: 0L,
                uriString = currentVideo?.uri?.toString(),
                title = currentVideo?.displayName,
                originalName = extTrack.originalFilename.ifBlank { extTrack.title },
                isSelected = isNowSelected
            )
        }

        saveSubtitleSelection(primaryId = finalPrimaryId, secondaryId = finalSecondaryId)
    }

    /** Re-applies the saved main / 2nd subtitle of this video (each one independently). */
    fun applySavedSubtitleSelection(saved: VideoSubtitleSelection) {
        val tracks = controller.subtitleTracks.value
        val primary = resolveSavedSubtitle(saved.primary, tracks)
        val secondary = resolveSavedSubtitle(saved.secondary, tracks)?.takeIf { it.id != primary?.id }
        val primId = primary?.id ?: 0
        val secId = secondary?.id ?: 0
        // A remembered track that can't be found right now (not loaded yet) must NOT be turned
        // into "Subtitles Off" and saved over the user's real choice: keep the memory intact.
        val unresolved = (saved.primary != null && primary == null) || (saved.secondary != null && secondary == null)
        if (unresolved && primId <= 0 && secId <= 0) return
        selectSubtitleTracks(primId, secId, notify = false, persist = !unresolved)
    }

    /**
     * Restores the subtitles of a freshly loaded video. Runs as soon as mpv has really opened the
     * file (not after guessed delays): every subtitle the user uploaded for this video is added
     * back to the upload section (never dropped), then the remembered selection of THIS video is
     * re-applied. [saved] == null means the user never chose anything for this video, so the
     * normal default selection applies.
     */
    fun restoreSubtitlesForVideo(targetVideo: VideoItem, loadKey: String, saved: VideoSubtitleSelection?) {
        coroutineScope.launch {
            try {
                val uploaded = withContext(Dispatchers.IO) {
                    try {
                        CustomSubtitlePersistenceManager.getAttachedSubtitles(
                            context = context,
                            path = targetVideo.path,
                            videoId = targetVideo.id,
                            uriString = targetVideo.uri.toString(),
                            title = targetVideo.displayName
                        ).also { list ->
                            // Pick up edits the user made to the ORIGINAL file since last time
                            // (runs while the video is still opening, so it costs no extra wait).
                            for (sub in list) CustomSubtitlePersistenceManager.syncAttachedFromSource(context, sub)
                        }
                    } catch (_: Throwable) {
                        emptyList()
                    }
                }
                withContext(Dispatchers.IO) { controller.awaitFileLoaded(15000L) }
                if (lastLoadedVideoKey != loadKey) return@launch

                if (uiState.playerSettings.detectSubtitlesByFilename) {
                    val videoFile = File(targetVideo.path)
                    val parent = videoFile.parentFile
                    val base = videoFile.nameWithoutExtension.lowercase()
                    val candidates = parent?.listFiles()?.filter { file ->
                        file.isFile && file.extension.lowercase() in setOf("srt", "ass", "ssa", "vtt", "ttml", "smi", "sub", "sup") &&
                            file.nameWithoutExtension.lowercase().startsWith(base)
                    }.orEmpty()
                    withContext(Dispatchers.IO) {
                        for (file in candidates) {
                            if (uploaded.none { it.filePath.equals(file.absolutePath, ignoreCase = true) }) {
                                controller.addExternalSubtitle(file.absolutePath, context, originalName = file.name, select = false)
                            }
                        }
                    }
                }
                if (uploaded.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        for (sub in uploaded) {
                            if (File(sub.filePath).exists()) {
                                controller.addExternalSubtitle(
                                    filePath = sub.filePath,
                                    context = context,
                                    originalName = sub.originalName,
                                    // With a saved selection nothing is selected while adding;
                                    // the saved choice is applied below.
                                    select = saved == null && sub.isSelected
                                )
                            }
                        }
                    }
                }
                // Make sure the track list is up to date before resolving the saved choice.
                withContext(Dispatchers.IO) { controller.refreshTracksAndChapters() }
                if (lastLoadedVideoKey != loadKey) return@launch
                if (saved != null) {
                    applySavedSubtitleSelection(saved)
                } else {
                    // If no explicit selection was saved, check if an uploaded subtitle is active or was selected
                    val activeUploadedTrack = controller.subtitleTracks.value.firstOrNull {
                        it.isExternal && uploaded.any { u -> u.isSelected && (u.originalName.equals(it.originalFilename, ignoreCase = true) || u.originalName.equals(it.title, ignoreCase = true)) }
                    }
                    if (activeUploadedTrack != null && activeUploadedTrack.id > 0) {
                        selectSubtitleTracks(activeUploadedTrack.id, 0, notify = false)
                    } else {
                        // Check if preferred subtitle language matches
                        val preferred = uiState.playerSettings.preferredSubtitleLanguages
                            .split(',', ';')
                            .map { it.trim().lowercase() }
                            .filter { it.isNotBlank() }
                        val currentPath = targetVideo.path
                        val selectedOnly = uiState.playerSettings.applySubtitleLanguageToSelectedContentOnly
                        val appliesToCurrent = !selectedOnly ||
                            uiState.playerSettings.selectedSubtitleVideos.any { it.equals(currentPath, ignoreCase = true) } ||
                            uiState.playerSettings.selectedSubtitleFolders.any { folder ->
                                currentPath.equals(folder, ignoreCase = true) || currentPath.startsWith(folder.trimEnd(File.separatorChar) + File.separator)
                            }
                        val matchingLangTrack = if (preferred.isNotEmpty() && appliesToCurrent) {
                            controller.subtitleTracks.value
                                .filter { track ->
                                    val lang = track.language.lowercase()
                                    preferred.any { pref -> lang == pref || lang.startsWith("$pref-") || lang.startsWith(pref) }
                                }
                                .maxByOrNull { track ->
                                    var score = 0
                                    if (track.isDefault) score += 20
                                    if (track.isForced) score += if (uiState.playerSettings.subtitleSignsAndSongs) 35 else 10
                                    if (uiState.playerSettings.subtitleSignsAndSongs && Regex("(?i)signs|songs|forced|narrative").containsMatchIn(track.title)) score += 40
                                    if (preferred.any { pref -> track.language.lowercase() == pref }) score += 30
                                    score
                                }
                                ?: if (uiState.playerSettings.subtitleSignsAndSongs) {
                                    controller.subtitleTracks.value.maxByOrNull {
                                        (if (it.isForced) 40 else 0) +
                                            if (Regex("(?i)signs|songs|forced|narrative").containsMatchIn(it.title)) 35 else 0 +
                                            if (it.isDefault) 10 else 0
                                    }?.takeIf { it.isForced || Regex("(?i)signs|songs|forced|narrative").containsMatchIn(it.title) }
                                } else null
                        } else {
                            // No app-level language preference: respect the MKV/container flags.
                            resolveContainerSubtitleFallback(controller.subtitleTracks.value)
                        }

                        if (matchingLangTrack != null && matchingLangTrack.id > 0) {
                            selectSubtitleTracks(matchingLangTrack.id, 0, notify = false)
                        } else {
                            // User ne koi subtitle select nahi kiya:
                            // koi subtitle automatically select mat karo!
                            selectSubtitleTracks(0, 0, notify = false)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Throwable) {
            } finally {
                // Only release our own hold (a newer video load manages its own).
                if (lastLoadedVideoKey == loadKey) controller.releaseSubtitleAutoSelectHold()
            }
        }
    }

    // A newly uploaded / pasted subtitle becomes Primary and the subtitle that was Primary until
    // now (embedded or uploaded) becomes Secondary. Any older Secondary is released, so the
    // result is always exactly two tidy slots and a later tap on Secondary simply unselects it.
    fun promoteNewSubtitleToPrimary(newTrackId: Int, previousPrimaryId: Int) {
        if (newTrackId <= 0) return
        val oldPrimary = if (previousPrimaryId > 0 && previousPrimaryId != newTrackId &&
            controller.subtitleTracks.value.any { it.id == previousPrimaryId }
        ) previousPrimaryId else 0
        selectSubtitleTracks(newTrackId, oldPrimary, notify = false)
    }

    // Persist an uploaded subtitle once per source and return the canonical private path.
    // This keeps repeated select/unselect/select operations idempotent.
    suspend fun attachUploadedSubtitle(
        filePath: String,
        originalName: String,
        sourcePath: String = "",
        sourceUri: String = ""
    ): String? {
        return CustomSubtitlePersistenceManager.addAttachedSubtitle(
            context = context,
            path = currentVideo?.path,
            videoId = currentVideo?.id ?: 0L,
            uriString = currentVideo?.uri?.toString(),
            title = currentVideo?.displayName,
            filePath = filePath,
            originalName = originalName,
            isSelected = true,
            sourcePath = sourcePath,
            sourceUri = sourceUri
        )
    }

    // Imports a subtitle file chosen in the in-app explorer. All file copying, font syncing and
    // subtitle preparation run OFF the main thread (they used to run on it, which froze the UI and
    // crashed the app for big / font-heavy subtitles). Any Throwable is reported instead of crashing.
    // Returns true when the subtitle was attached.
    suspend fun importSubtitleFileSafely(selectedFile: File): Boolean {
        val previousPrimaryId = controller.subtitleTracks.value.firstOrNull { it.isSelected }?.id ?: selectedSubtitleTrackId
        val targetId = withContext(Dispatchers.IO) {
            try {
                if (!selectedFile.exists() || !selectedFile.isFile || selectedFile.length() <= 0L) {
                    return@withContext null
                }
                val persistentFile = CustomSubtitlePersistenceManager.persistSubtitleFromFile(context, selectedFile)
                val attachedPath = attachUploadedSubtitle(
                    filePath = persistentFile.absolutePath,
                    originalName = selectedFile.name,
                    sourcePath = selectedFile.absolutePath
                ) ?: persistentFile.absolutePath
                val addedSubId = controller.addExternalSubtitle(
                    attachedPath, context, originalName = selectedFile.name, select = true
                )
                addedSubId?.takeIf { it > 0 } ?: controller.subtitleTracks.value.lastOrNull()?.id ?: 0
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                android.util.Log.w("VideoPlayerScreen", "Subtitle import failed", t)
                null
            }
        } ?: return false
        if (targetId > 0) {
            promoteNewSubtitleToPrimary(targetId, previousPrimaryId)
        }
        return true
    }

    // Subtitle File Picker Launcher
    val subtitleFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val subFileName = try {
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIdx != -1 && cursor.moveToFirst()) cursor.getString(nameIdx) else null
                        }
                    } catch (_: Throwable) { null } ?: "imported_sub_${System.currentTimeMillis() % 10000}.ass"

                    val previousPrimaryId = controller.subtitleTracks.value.firstOrNull { it.isSelected }?.id ?: selectedSubtitleTrackId
                    val persistentSubFile = withContext(Dispatchers.IO) {
                        CustomSubtitlePersistenceManager.persistSubtitleFromUri(context, uri, subFileName)
                    }

                    if (persistentSubFile != null && persistentSubFile.exists() && persistentSubFile.length() > 0) {
                        val attachedPath = attachUploadedSubtitle(
                            filePath = persistentSubFile.absolutePath,
                            originalName = subFileName,
                            sourceUri = uri.toString()
                        ) ?: persistentSubFile.absolutePath
                        val addedSubId = withContext(Dispatchers.IO) {
                            controller.addExternalSubtitle(attachedPath, context, originalName = subFileName, select = true)
                        }
                        val targetId = addedSubId?.takeIf { it > 0 } ?: controller.subtitleTracks.value.lastOrNull()?.id ?: 0
                        if (targetId > 0) {
                            promoteNewSubtitleToPrimary(targetId, previousPrimaryId)
                        }
                        showNotification("Loaded Subtitle: $subFileName")
                    } else {
                        showNotification("Failed to load subtitle file")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    showNotification("Error loading subtitle: ${e.message}")
                }
            }
        }
    }

    // Audio File Picker Launcher
    val audioFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val audioFileName = try {
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIdx != -1 && cursor.moveToFirst()) cursor.getString(nameIdx) else null
                        }
                    } catch (_: Throwable) { null } ?: "imported_audio_${System.currentTimeMillis() % 10000}.mp3"

                    val tempAssetsDir = File(context.cacheDir, "temp_player_assets").apply { mkdirs() }
                    val tempAudioFile = File(tempAssetsDir, audioFileName)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempAudioFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    if (tempAudioFile.exists() && tempAudioFile.length() > 0) {
                        hasManualAudioTrackSelection = true
                        val addedAudioId = controller.addExternalAudio(tempAudioFile.absolutePath, originalName = audioFileName)
                        if (addedAudioId != null && addedAudioId > 0) {
                            selectedAudioTrackId = addedAudioId
                            controller.setAudioTrack(addedAudioId)
                        } else {
                            val lastTrack = controller.audioTracks.value.lastOrNull()?.id
                            if (lastTrack != null && lastTrack > 0) {
                                selectedAudioTrackId = lastTrack
                                controller.setAudioTrack(lastTrack)
                            }
                        }
                        showNotification("Loaded Audio Track: $audioFileName")
                    } else {
                        showNotification("Failed to load audio file")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    showNotification("Error loading audio: ${e.message}")
                }
            }
        }
    }

    val realAudioTracks by controller.audioTracks.collectAsState()
    val realSubtitleTracks by controller.subtitleTracks.collectAsState()
    val realSecondarySubtitleTrackId by controller.secondarySubtitleTrackId.collectAsState()
    val realChapters by controller.chapters.collectAsState()
    val realVideoWidth by controller.videoWidth.collectAsState()
    val realVideoHeight by controller.videoHeight.collectAsState()
    val overrideAssSsaAutoResetEvent by controller.overrideAssSsaAutoResetEvent.collectAsState()

    val currentChapterTitle by remember(realChapters, currentPositionMs, currentEpisodeIndex) {
        derivedStateOf {
            if (realChapters.isNotEmpty()) {
                val chap = realChapters.filter { it.timeMs <= currentPositionMs }.maxByOrNull { it.timeMs }
                    ?: realChapters.firstOrNull()
                chap?.title ?: "Chapter $currentEpisodeIndex"
            } else {
                "Chapter $currentEpisodeIndex"
            }
        }
    }

    val controllerIsPlaying by controller.isPlaying.collectAsState()
    val controllerPos by controller.currentPositionMs.collectAsState()
    val controllerDur by controller.durationMs.collectAsState()
    val controllerEof by controller.eofReached.collectAsState()

    LaunchedEffect(controllerIsPlaying) {
        isPlaying = controllerIsPlaying
    }

    LaunchedEffect(controllerPos) {
        currentPositionMs = controllerPos
    }

    LaunchedEffect(controllerDur) {
        if (controllerDur > 0L) {
            durationMs = controllerDur
        }
    }

    // Real watched-threshold behavior: once playback crosses the configured
    // percentage, persist the video at its full duration so the library sees it
    // as completed/watched. The flag resets automatically for the next video.
    LaunchedEffect(currentPositionMs, durationMs, currentVideo, uiState.watchedThresholdPercent) {
        val vid = currentVideo ?: return@LaunchedEffect
        val dur = durationMs
        if (watchedThresholdReached || dur <= 0L) return@LaunchedEffect
        val threshold = (dur * uiState.watchedThresholdPercent.coerceIn(50, 100)) / 100L
        if (currentPositionMs >= threshold) {
            watchedThresholdReached = true
            PlaybackHistoryManager.savePosition(
                context = context,
                videoId = vid.id,
                path = vid.path,
                positionMs = dur,
                durationMs = dur,
                uriString = vid.uri.toString(),
                title = vid.displayName,
                forceDiskWrite = true
            )
        }
    }

    // Apply Audio Pitch Correction setting
    LaunchedEffect(uiState.enableAudioPitchCorrection) {
        isPitchCorrectionEnabled = uiState.enableAudioPitchCorrection
        controller.setAudioPitchCorrection(uiState.enableAudioPitchCorrection)
    }

    // Apply Audio Volume Normalization setting
    LaunchedEffect(uiState.volumeNormalization) {
        controller.setVolumeNormalization(uiState.volumeNormalization)
    }

    // Apply Audio Channel Mode setting
    LaunchedEffect(uiState.audioChannelMode) {
        controller.setAudioChannelMode(uiState.audioChannelMode)
    }

    var subtitleDiskSaveJob by remember { mutableStateOf<Job?>(null) }
    val saveSubtitleSettingsDebounced: (PlayerSettings) -> Unit = remember(coroutineScope, context) {
        { settings ->
            subtitleDiskSaveJob?.cancel()
            subtitleDiskSaveJob = coroutineScope.launch(Dispatchers.IO) {
                delay(300L)
                PlayerSettings.save(context, settings)
            }
        }
    }

    var eqDiskSaveJob by remember { mutableStateOf<Job?>(null) }
    val saveEqualizerSettingsDebounced: ((PlayerSettings) -> PlayerSettings) -> Unit = remember(coroutineScope, context) {
        { transform ->
            eqDiskSaveJob?.cancel()
            eqDiskSaveJob = coroutineScope.launch(Dispatchers.IO) {
                delay(300L)
                val current = PlayerSettings.load(context)
                PlayerSettings.save(context, transform(current))
            }
        }
    }

    // Last global appearance pushed to mpv (everything EXCEPT the Advanced ASS per-style edits).
    // Editing one ASS style only changes assStyleOverridesJson; re-pushing ~25 unchanged global
    // mpv sub-* properties on every keystroke / colour-drag tick made libass re-layout the whole
    // subtitle and caused animation/timing hitches on styles that were never touched.
    val lastAppliedAppearanceKey = remember(controller) { arrayOfNulls<PlayerSettings>(1) }
    val applySubtitleAppearanceDirect: (PlayerSettings) -> Unit = remember(controller, context) {
        appearance@{ settings ->
            // Advanced ASS/SSA state first, so the global override is suppressed correctly for
            // ASS/SSA tracks that are governed by per-style edits.
            controller.setAdvancedAssState(
                enabled = settings.advancedAssEnabled,
                overridesJson = settings.assStyleOverridesJson
            )
            controller.setRawSubtitleEditorEnabled(settings.rawSubtitleEditorEnabled)
            val appearanceKey = settings.copy(assStyleOverridesJson = "", rawSubtitleEditorEnabled = false)
            if (lastAppliedAppearanceKey[0] == appearanceKey) return@appearance
            lastAppliedAppearanceKey[0] = appearanceKey
            val selectedFamily = settings.selectedSubtitleFont
                .takeIf { it.isNotBlank() }
                ?.let { name ->
                    val installed = SubtitleFontManager.getInstalledFonts(
                        context,
                        includeVideoExtracted = settings.showVideoEmbeddedSubtitleFonts
                    )
                    val matchedFile = installed.firstOrNull { it.name.equals(name, ignoreCase = true) }
                        ?: installed.firstOrNull { it.nameWithoutExtension.equals(name, ignoreCase = true) }
                        ?: installed.firstOrNull { SubtitleFontManager.getOriginalFontName(it).equals(name, ignoreCase = true) }
                    matchedFile?.let { file -> SubtitleFontManager.getRenderFontName(file) } ?: name
                }
            controller.applySubtitleAppearance(
                bold = settings.subtitleBold,
                italic = settings.subtitleItalic,
                underline = settings.subtitleUnderline,
                alignment = settings.subtitleAlignment,
                fontSize = settings.subtitleFontSize,
                borderStyle = settings.subtitleBorderStyle,
                borderSize = settings.subtitleBorderSize,
                shadowOffset = settings.subtitleShadowOffset,
                shadowBlur = settings.subtitleShadowBlur,
                backgroundPadding = settings.subtitleBackgroundPadding,
                textColorArgb = (settings.subtitleTextColor and 0xFFFFFFFFL).toInt(),
                borderColorArgb = (settings.subtitleBorderColor and 0xFFFFFFFFL).toInt(),
                backgroundColorArgb = (settings.subtitleBackgroundColor and 0xFFFFFFFFL).toInt(),
                shadowColorArgb = (settings.subtitleShadowColor and 0xFFFFFFFFL).toInt(),
                overrideAssSsa = settings.overrideAssSsaSubtitles,
                scaleWithWindow = settings.scaleSubtitlesByWindow,
                scale = settings.subtitleScale,
                position = settings.subtitlePosition,
                blendWithVideo = settings.subtitleBlendWithVideo,
                fontDirectory = SubtitleFontManager.getSubFontsDir(context).absolutePath,
                fontFamily = selectedFamily,
                letterSpacing = settings.subtitleLetterSpacing,
                shadowOffsetX = settings.subtitleShadowOffsetX,
                shadowOffsetY = settings.subtitleShadowOffsetY
            )
        }
    }

    // Apply subtitle settings live and before each subsequent media load.
    // Apply the complete live subtitle style without recreating the player.
    LaunchedEffect(liveSubtitleSettings) {
        applySubtitleAppearanceDirect(liveSubtitleSettings)
    }

    // The controller silently drops "Override ASS/SSA Styles" back to OFF whenever it auto-picks
    // a subtitle the user never explicitly chose for THIS video (a fresh video/next episode with
    // no saved choice of its own - see the comment in MpvPlayerController's "next episode" block).
    // Follow that reset here so the style panel switch and the persisted setting match what is
    // actually being rendered, instead of still showing ON while the video plays with original
    // (non-overridden) styling. 0L is just this flow's initial value, not a real reset - skip it.
    LaunchedEffect(overrideAssSsaAutoResetEvent) {
        if (overrideAssSsaAutoResetEvent == 0L) return@LaunchedEffect
        if (liveSubtitleSettings.overrideAssSsaSubtitles) {
            val updated = liveSubtitleSettings.copy(overrideAssSsaSubtitles = false)
            liveSubtitleSettings = updated
            saveSubtitleSettingsDebounced(updated)
            onPlayerSettingsChange(updated)
        }
    }

    LaunchedEffect(
        uiState.playerSettings.autoLoadExternalSubtitles,
        uiState.playerSettings.overrideAssSsaSubtitles,
        uiState.playerSettings.scaleSubtitlesByWindow,
        uiState.playerSettings.subtitleFontDirectoryUri,
        uiState.playerSettings.selectedSubtitleFont,
        uiState.playerSettings.subtitleFontsReloadNonce,
        uiState.playerSettings.showVideoEmbeddedSubtitleFonts
    ) {
        val selectedFamily = uiState.playerSettings.selectedSubtitleFont
            .takeIf { it.isNotBlank() }
            ?.let { name ->
                val installed = SubtitleFontManager.getInstalledFonts(
                    context,
                    includeVideoExtracted = uiState.playerSettings.showVideoEmbeddedSubtitleFonts
                )
                val matchedFile = installed.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ?: installed.firstOrNull { it.nameWithoutExtension.equals(name, ignoreCase = true) }
                    ?: installed.firstOrNull { SubtitleFontManager.getOriginalFontName(it).equals(name, ignoreCase = true) }
                matchedFile?.let { file -> SubtitleFontManager.getRenderFontName(file) } ?: name
            }
        controller.applySubtitleSettings(
            autoLoadExternal = uiState.playerSettings.autoLoadExternalSubtitles,
            overrideAssSsa = uiState.playerSettings.overrideAssSsaSubtitles,
            scaleWithWindow = uiState.playerSettings.scaleSubtitlesByWindow,
            fontDirectory = SubtitleFontManager.getSubFontsDir(context).absolutePath,
            fontFamily = selectedFamily
        )
    }

    LaunchedEffect(uiState.playerSettings.preciseSeeking) {
        controller.setPreciseSeekingEnabled(uiState.playerSettings.preciseSeeking)
    }

    // Sync PipModeState for picture-in-picture actions and Auto-PiP background triggers
    DisposableEffect(Unit) {
        PipModeState.isPlayerActive = true
        PipModeState.onPlayPauseToggle = {
            controller.togglePlayPause()
        }
        onDispose {
            PipModeState.isPlayerActive = false
            PipModeState.isCurrentlyPlaying = false
            PipModeState.autoPipEnabled = false
            PipModeState.onPlayPauseToggle = null
            PipModeState.requestPipUpdate?.invoke(false, false)
        }
    }

    LaunchedEffect(isPlaying, uiState.playerSettings.autoPictureInPicture) {
        PipModeState.isCurrentlyPlaying = isPlaying
        PipModeState.autoPipEnabled = uiState.playerSettings.autoPictureInPicture
        PipModeState.requestPipUpdate?.invoke(isPlaying, uiState.playerSettings.autoPictureInPicture)
    }

    LaunchedEffect(realSubtitleTracks) {
        if (realSubtitleTracks.isEmpty()) return@LaunchedEffect
        val active = realSubtitleTracks.firstOrNull { it.isSelected }
        if (active != null && active.id > 0) {
            selectedSubtitleTrackId = active.id
            lastTouchedSubtitleTrackId = active.id
            isSubtitleVisible = SubtitleSessionMemory.isSubtitlesEnabled
        } else if (selectedSubtitleTrackId > 0 && realSubtitleTracks.none { it.id == selectedSubtitleTrackId }) {
            selectedSubtitleTrackId = 0
        }
    }

    // Automatically select Preferred Audio Language if available
    LaunchedEffect(realSubtitleTracks, uiState.playerSettings.preferredSubtitleLanguages, uiState.playerSettings.applySubtitleLanguageToSelectedContentOnly, uiState.playerSettings.selectedSubtitleFolders, uiState.playerSettings.selectedSubtitleVideos, uiState.playerSettings.subtitleSignsAndSongs, currentVideo) {
        if (SubtitleSessionMemory.preferredTrackNumber != null) return@LaunchedEffect
        // This video has a subtitle choice of its own: never override it with a language default.
        val videoForSelection = currentVideo
        if (videoForSelection != null && CustomSubtitlePersistenceManager.getSubtitleSelection(
                context = context,
                path = videoForSelection.path,
                videoId = videoForSelection.id,
                uriString = videoForSelection.uri.toString(),
                title = videoForSelection.displayName
            ) != null
        ) return@LaunchedEffect
        val currentPath = currentVideo?.path.orEmpty()
        val appliesToCurrent = !uiState.playerSettings.applySubtitleLanguageToSelectedContentOnly ||
            uiState.playerSettings.selectedSubtitleVideos.any { it.equals(currentPath, ignoreCase = true) } ||
            uiState.playerSettings.selectedSubtitleFolders.any { folder -> currentPath.equals(folder, ignoreCase = true) || currentPath.startsWith(folder.trimEnd(File.separatorChar) + File.separator) }
        val preferred = uiState.playerSettings.preferredSubtitleLanguages
            .split(',', ';')
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
        if (realSubtitleTracks.isEmpty()) return@LaunchedEffect

        val best = if (preferred.isNotEmpty() && appliesToCurrent) {
            realSubtitleTracks.maxByOrNull { track ->
                var score = 0
                if (track.isDefault) score += 20
                if (track.isForced) score += if (uiState.playerSettings.subtitleSignsAndSongs) 35 else 10
                if (uiState.playerSettings.subtitleSignsAndSongs && Regex("(?i)signs|songs|forced|narrative").containsMatchIn(track.title)) score += 40
                val lang = track.language.lowercase()
                if (preferred.any { pref -> lang == pref }) score += 30
                else if (preferred.any { pref -> lang.startsWith("$pref-") || lang.startsWith(pref) }) score += 20
                score
            }?.takeIf { track ->
                preferred.any { pref ->
                    val lang = track.language.lowercase()
                    lang == pref || lang.startsWith("$pref-") || lang.startsWith(pref)
                }
            }
        } else {
            // No dashboard language preference: follow the MKV/container's default/forced flags.
            resolveContainerSubtitleFallback(realSubtitleTracks)
        }

        if (best != null && best.id != selectedSubtitleTrackId) {
            selectSubtitleTracks(best.id, controller.secondarySubtitleTrackId.value)
        }
    }

    LaunchedEffect(realAudioTracks, uiState.preferredAudioLanguages, uiState.applyAudioLanguageToSelectedContentOnly, uiState.selectedAudioFolders, uiState.selectedAudioVideos, uiState.rememberSelectedAudioTrack, currentVideo) {
        if (realAudioTracks.isNotEmpty() && !hasManualAudioTrackSelection) {
            // The audio track the user picked for THIS video always wins over language defaults.
            val rememberedVideo = currentVideo
            val rememberedTrack = if (uiState.rememberSelectedAudioTrack && rememberedVideo != null) {
                VideoAudioSelectionMemory.get(
                    context = context,
                    path = rememberedVideo.path,
                    videoId = rememberedVideo.id,
                    uriString = rememberedVideo.uri.toString(),
                    title = rememberedVideo.displayName
                )?.let { VideoAudioSelectionMemory.resolve(it, realAudioTracks) }
            } else null
            if (rememberedTrack != null) {
                if (!rememberedTrack.isSelected || rememberedTrack.id != selectedAudioTrackId) {
                    selectedAudioTrackId = rememberedTrack.id
                    controller.setAudioTrack(rememberedTrack.id)
                }
                return@LaunchedEffect
            }
            val audioPath = currentVideo?.path.orEmpty()
            val audioPreferenceApplies = !uiState.applyAudioLanguageToSelectedContentOnly ||
                uiState.selectedAudioVideos.any { it.equals(audioPath, ignoreCase = true) } ||
                uiState.selectedAudioFolders.any { folder ->
                    audioPath.equals(folder, ignoreCase = true) ||
                        audioPath.startsWith(folder.trimEnd(File.separatorChar) + File.separator)
                }
            val bestTrack = if (uiState.preferredAudioLanguages.isNotBlank() && audioPreferenceApplies) {
                // Dashboard language preferences always have priority when configured for this video.
                AudioLanguageMatcher.findBestAudioTrack(
                    tracks = realAudioTracks,
                    videoPath = audioPath,
                    preferredLanguages = uiState.preferredAudioLanguages,
                    applyToSelectedContentOnly = false,
                    selectedFolders = emptySet(),
                    selectedVideos = emptySet(),
                    rememberSelectedTrack = uiState.rememberSelectedAudioTrack
                )
            } else {
                // No applicable dashboard preference: use the MKV/container's own
                // automatic/default/forced metadata instead of forcing a hard-coded language.
                resolveContainerAudioFallback(realAudioTracks)
            }
            if (bestTrack != null && bestTrack.id != selectedAudioTrackId) {
                selectedAudioTrackId = bestTrack.id
                controller.setAudioTrack(bestTrack.id)
            }
        }
    }

    fun persistPlaybackPosition(posMs: Long = currentPositionMs, forceDisk: Boolean = true) {
        val vid = currentVideo ?: return
        if (!uiState.playerSettings.savePositionOnQuit) return

        val controllerVal = controller.currentPositionMs.value
        val rawPos = if (controllerVal > 0L) controllerVal else posMs

        val effectivePos = if (rawPos <= 500L && !controller.isResumeSeekVerified) {
            val saved = PlaybackHistoryManager.getSavedPosition(
                context = context,
                videoId = vid.id,
                path = vid.path,
                uriString = vid.uri.toString(),
                title = vid.displayName
            )
            if (saved > 500L) saved else rawPos
        } else {
            rawPos
        }

        val effectiveDur = if (durationMs > 0L) durationMs else controller.durationMs.value

        if (effectivePos >= 0L) {
            PlaybackHistoryManager.savePosition(
                context = context,
                videoId = vid.id,
                path = vid.path,
                positionMs = effectivePos,
                durationMs = effectiveDur,
                uriString = vid.uri.toString(),
                title = vid.displayName,
                forceDiskWrite = forceDisk
            )
        }
    }

    var isExitingPlayer by remember { mutableStateOf(false) }
    var exitFreezeFrameBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // --- Swipe-down-from-top dismiss gesture state --------------------------------------
    // A drag starting near the phone's top edge (camera side) and moving towards the bottom
    // edge dismisses the player, in both portrait and landscape. The whole player (video +
    // controls overlay) follows the finger via `dismissDragAnim`, then either springs back
    // (drag released short of the threshold) or completes into the normal exit animation
    // (dismissDragAnim's offset is why the ScreenTransition's outgoing content quietly resets
    // to 0 the moment `exitPlayerNow()` swaps the screen away).
    val dismissDragAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    var isDismissDragging by remember { mutableStateOf(false) }

    /**
     * Leaves the player screen smoothly and safely.
     * Persists position, captures live surface frame for seamless zero-flicker transition,
     * pauses playback, initiates the portrait orientation transition,
     * and triggers the back transition.
     */
    fun exitPlayerNow() {
        if (isExitingPlayer) return
        isExitingPlayer = true
        persistPlaybackPosition()
        val snap = controller.lastCapturedFrame.value
        if (snap != null) {
            exitFreezeFrameBitmap = snap
        }
        controller.captureCurrentFrame { bmp ->
            if (bmp != null) {
                exitFreezeFrameBitmap = bmp
            }
        }
        controller.pause()

        // The player owns the visual back animation, so Android must not add its own
        // rotate-the-whole-window animation underneath it. Without this, the already-portrait
        // folder/library surface can visibly rotate for a few frames while the landscape player
        // is sliding down. JUMPCUT removes only that system rotation animation; the existing
        // Compose player rotation/slide animation remains unchanged.
        activity?.window?.let { window ->
            val params = window.attributes
            params.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_JUMPCUT
            window.attributes = params
        }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        waitForPortraitBeforeBack = true
    }

    LaunchedEffect(waitForPortraitBeforeBack, configuration.orientation) {
        if (waitForPortraitBeforeBack && configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            // Let the portrait configuration commit one frame before MainActivity swaps to HOME.
            androidx.compose.runtime.withFrameNanos { }
            androidx.compose.runtime.withFrameNanos { }
            waitForPortraitBeforeBack = false
            // Restore Android's normal rotation behavior for future orientation changes.
            activity?.window?.let { window ->
                val params = window.attributes
                params.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_ROTATE
                window.attributes = params
            }
            onBack()
        }
    }

    /**
     * Unified playlist navigation & episode playback function.
     * Systematically saves current state, resets video-specific parameters,
     * updates index & UI state, loads the target media source into MPV engine,
     * and manages subtitle fonts & chapters consistently.
     */
    fun playEpisodeAtIndex(targetIndex: Int) {
        if (currentPlaylist.isEmpty()) return
        val validIndex = targetIndex.coerceIn(0, currentPlaylist.size - 1)

        // 1. Save position of current episode before navigating away
        persistPlaybackPosition()

        // 2. Reset episode-specific states & adjustments
        loopPointA = null
        loopPointB = null
        subtitleOffsetMs = 0L
        audioDelayMs = 0L
        controller.setAudioDelay(0L)
        controller.setSubtitleDelay(0L)
        videoZoomScale = 1.0f
        videoPanOffset = Offset.Zero
        isPanAndZoomEnabled = false
        selectedAudioTrackId = 1
        selectedSubtitleTrackId = 0
        lastTouchedSubtitleTrackId = 0
        isSubtitleVisible = false

        // 3. Update active episode index
        currentVideoIndex = validIndex

        // 4. Retrieve target VideoItem and resolved file path
        val targetVideo = currentPlaylist.getOrNull(validIndex) ?: return
        com.example.util.MediaSeenManager.markSeen(context, targetVideo.path, targetVideo.id)
        // This episode is now the "last watched" one right away (not only after the first 3 s tick),
        // so the folder's resume button can never fall back to the episode before it.
        com.example.util.PlaybackHistoryManager.markOpened(
            context = context,
            videoId = targetVideo.id,
            path = targetVideo.path,
            uriString = targetVideo.uri.toString(),
            title = targetVideo.displayName,
            durationMs = targetVideo.durationMs
        )
        val path = getPlayableFilePath(context, targetVideo.uri, targetVideo.path)

        // 5. Restore saved position & duration (respecting Resume Playback setting)
        val savedPos = if (uiState.playerSettings.savePositionOnQuit) {
            PlaybackHistoryManager.getSavedPosition(
                context = context,
                videoId = targetVideo.id,
                path = targetVideo.path,
                uriString = targetVideo.uri.toString(),
                title = targetVideo.displayName
            )
        } else 0L
        val initialPos = if (savedPos > 500L) savedPos else 0L
        currentPositionMs = initialPos
        if (targetVideo.durationMs > 0) {
            durationMs = targetVideo.durationMs
        }

        // 6. Update thumbnail
        val cachedThumb = ThumbnailCache.getCachedThumbnail(
            videoId = targetVideo.id,
            path = targetVideo.path,
            strategy = uiState.thumbnailStrategy,
            quality = uiState.thumbnailQuality,
            fallbackSecond = uiState.thumbnailFallbackSecond
        )
        if (cachedThumb != null) {
            thumbnailBitmap = cachedThumb
        } else {
            coroutineScope.launch {
                val bmp = loadVideoThumbnail(
                    context,
                    targetVideo.id,
                    targetVideo.uri,
                    targetVideo.path,
                    strategy = uiState.thumbnailStrategy,
                    quality = uiState.thumbnailQuality,
                    fallbackSecond = uiState.thumbnailFallbackSecond
                )
                if (bmp != null) thumbnailBitmap = bmp
            }
        }

        // 7. Load into MPV engine & initiate playback
        if (path.isNotBlank() && (File(path).exists() || path.startsWith("fd://") || path.startsWith("http://") || path.startsWith("https://"))) {
            val key = "${targetVideo.id}_${path}_$validIndex"
            lastLoadedVideoKey = key
            // A subtitle choice the user made for THIS video must not be overridden (or flashed
            // over) by a default track while the video opens.
            val savedSubtitleSelection = CustomSubtitlePersistenceManager.getSubtitleSelection(
                context = context,
                path = targetVideo.path,
                videoId = targetVideo.id,
                uriString = targetVideo.uri.toString(),
                title = targetVideo.displayName
            )
            controller.holdSubtitleAutoSelect = savedSubtitleSelection != null
            // Opening a file (demuxer probe, hwdec init, GL surface) is heavy. Doing it while the
            // slide-in animation is still running is what made the entry stutter, so on the very
            // first load of this screen the animation is allowed to finish first.
            val firstLoad = !entryLoadDelayDone
            entryLoadDelayDone = true
            val entryDelayMs = (330 / uiState.animationSpeed.coerceIn(0.25f, 2f)).toLong()
            coroutineScope.launch {
                if (firstLoad) kotlinx.coroutines.delay(entryDelayMs)
                if (lastLoadedVideoKey != key) return@launch
                controller.loadVideo(path, startPositionMs = initialPos)
                controller.play()
                isPlaying = true

                // Re-attach the user's uploaded subtitles + remembered selection (see restoreSubtitlesForVideo)
                restoreSubtitlesForVideo(targetVideo, key, savedSubtitleSelection)
            }
        }

        // 8. Sync subtitle fonts for the new directory in background
        coroutineScope.launch {
            try {
                SubtitleFontManager.syncFonts(context, targetVideo.path)
            } catch (_: Throwable) {}
        }
    }

    LaunchedEffect(currentVideoIndex, currentPlaylist) {
        val targetVideo = currentPlaylist.getOrNull(currentVideoIndex) ?: video ?: return@LaunchedEffect
        val path = getPlayableFilePath(context, targetVideo.uri, targetVideo.path)
        if (path.isNotBlank() && (File(path).exists() || path.startsWith("fd://"))) {
            val key = "${targetVideo.id}_${path}_$currentVideoIndex"
            if (lastLoadedVideoKey != key) {
                playEpisodeAtIndex(currentVideoIndex)
            }
        }
    }

    // Hardware-synchronized A-B loop boundaries, repeat mode auto-advance, and periodic playback persistence
    // The end-of-video action must run ONCE per ending. It is re-armed as soon as playback is no
    // longer at the end (new episode loaded, or the user dragged the slider back).
    var endActionHandled by remember { mutableStateOf(false) }
    LaunchedEffect(controllerPos, isPlaying, loopPointA, loopPointB, repeatMode, durationMs, controllerEof) {
        val ptA = loopPointA
        val ptB = loopPointB
        val isAtEnd = (durationMs > 1000L && controllerPos >= (durationMs - 450L)) || (controllerEof && durationMs > 0L)
        // Stays "handled" while the old episode's end state is still visible during the switch to the
        // next one (otherwise the next-next episode would be started too); re-armed once playback has
        // left the end (new file loaded / user seeked back).
        if (!isAtEnd) {
            endActionHandled = false
        }
        if (ptA != null && ptB != null && ptB > ptA && isPlaying) {
            if (controllerPos >= ptB) {
                controller.seekTo(ptA)
            }
        // At EOF mpv (keep-open) is PAUSED, so `isPlaying` is false there: the old `isAtEnd && isPlaying`
        // test never became true and the player just sat on the last frame.
        } else if (isAtEnd && (isPlaying || controllerEof) && !endActionHandled) {
            endActionHandled = true
            when (repeatMode) {
                PlayerRepeatMode.ONE -> {
                    controller.seekTo(0L)
                    controller.play()
                }
                PlayerRepeatMode.ALL -> {
                    val nextIdx = if (currentVideoIndex + 1 < currentPlaylist.size) currentVideoIndex + 1 else 0
                    playEpisodeAtIndex(nextIdx)
                }
                PlayerRepeatMode.OFF -> {
                    if (currentVideoIndex + 1 < currentPlaylist.size) {
                        if (uiState.playerSettings.autoplayNext) {
                            playEpisodeAtIndex(currentVideoIndex + 1)
                        } else if (uiState.playerSettings.closeAfterEnd) {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            exitPlayerNow()
                        } else {
                            persistPlaybackPosition(durationMs)
                            controller.pause()
                        }
                    } else {
                        // Last episode in playlist
                        if (uiState.playerSettings.repeatPlaylistAfterLast && currentPlaylist.size > 1) {
                            showNotification("Restarting playlist")
                            playEpisodeAtIndex(0)
                        } else if (uiState.playerSettings.closeAfterEnd) {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            exitPlayerNow()
                        } else {
                            persistPlaybackPosition(durationMs)
                            controller.pause()
                        }
                    }
                }
            }
        }

        // Periodic state save every 3 seconds of active playback (debounced memory update)
        if (controllerPos > 0L && currentVideo != null && (controllerPos / 1000) % 3 == 0L) {
            persistPlaybackPosition(controllerPos, forceDisk = false)
        }
    }

    var onlineSkipMarkers by remember(currentVideo) { mutableStateOf<List<ChapterSkipMarker>>(emptyList()) }

    LaunchedEffect(currentVideo, uiState.playerSettings.onlineSkipMarkers, uiState.playerSettings.markerProvider, durationMs) {
        onlineSkipMarkers = emptyList()
        if (!uiState.playerSettings.onlineSkipMarkers || currentVideo == null || durationMs <= 0L) return@LaunchedEffect
        val remote = OnlineSkipMarkerService.fetch(
            provider = uiState.playerSettings.markerProvider,
            title = currentVideo.displayName,
            durationMs = durationMs
        )
        onlineSkipMarkers = remote.mapIndexed { index, marker ->
            ChapterSkipMarker(
                chapterIndex = -10000 - index,
                originalTitle = "Online ${marker.type.defaultPrimaryLabel}",
                displayButtonTitle = marker.type.defaultPrimaryLabel,
                secondaryTitle = "Online",
                type = marker.type,
                startTimeMs = marker.startMs,
                endTimeMs = marker.endMs
            )
        }
    }

    // Accurately detected chapter-based skip markers using real embedded metadata & configured settings
    val skipMarkers = remember(
        realChapters,
        durationMs,
        uiState.playerSettings.detectChapterIntroOutro,
        uiState.playerSettings.customOpeningKeywords,
        uiState.playerSettings.customEndingKeywords
    ) {
        (ChapterSkipDetector.detectSkipMarkers(
            chapters = realChapters,
            durationMs = durationMs,
            settings = uiState.playerSettings
        ) + onlineSkipMarkers).distinctBy {
            Triple(it.type, it.startTimeMs, it.endTimeMs)
        }.sortedBy { it.startTimeMs }
    }

    // Auto-skip opening/ending chapters when playback enters detected ranges
    var lastAutoSkippedMarkerIndex by remember(currentVideo?.path) { mutableStateOf<Int?>(null) }
    LaunchedEffect(
        currentPositionMs,
        skipMarkers,
        uiState.playerSettings.autoSkipIntro,
        uiState.playerSettings.autoSkipOutro
    ) {
        if (skipMarkers.isEmpty()) return@LaunchedEffect
        val activeMarker = ChapterSkipDetector.getActiveMarker(skipMarkers, currentPositionMs) ?: return@LaunchedEffect
        if (lastAutoSkippedMarkerIndex == activeMarker.chapterIndex) return@LaunchedEffect

        // Opening / Intro Auto-Skip
        if (activeMarker.type.isOpening && uiState.playerSettings.autoSkipIntro) {
            if (currentPositionMs < activeMarker.endTimeMs - 800L) {
                lastAutoSkippedMarkerIndex = activeMarker.chapterIndex
                controller.seekTo(activeMarker.endTimeMs)
                currentPositionMs = activeMarker.endTimeMs
                persistPlaybackPosition(activeMarker.endTimeMs)
                showNotification("Auto-skipped ${activeMarker.type.defaultPrimaryLabel.removePrefix("Skip ")}")
            }
        }
        // Ending / Outro / Preview Auto-Skip
        else if (activeMarker.type.isEnding && uiState.playerSettings.autoSkipOutro) {
            if (currentPositionMs < activeMarker.endTimeMs - 800L) {
                lastAutoSkippedMarkerIndex = activeMarker.chapterIndex
                val isAtEnd = durationMs > 0 && activeMarker.endTimeMs >= (durationMs - 1000L)
                if (isAtEnd) {
                    if (currentVideoIndex + 1 < currentPlaylist.size) {
                        showNotification("Auto-skipped to next episode")
                        playEpisodeAtIndex(currentVideoIndex + 1)
                    } else if (uiState.playerSettings.repeatPlaylistAfterLast && currentPlaylist.size > 1) {
                        showNotification("Restarting playlist")
                        playEpisodeAtIndex(0)
                    } else {
                        persistPlaybackPosition(durationMs)
                        exitPlayerNow()
                    }
                } else {
                    controller.seekTo(activeMarker.endTimeMs)
                    currentPositionMs = activeMarker.endTimeMs
                    persistPlaybackPosition(activeMarker.endTimeMs)
                    showNotification("Auto-skipped ${activeMarker.type.defaultPrimaryLabel.removePrefix("Skip ")}")
                }
            }
        }
    }

    // Reset orientation & exit immersive mode when exiting player
    BackHandler {
        exitPlayerNow()
    }

    // App & Screen Lifecycle Event Observer to handle background/foreground transitions cleanly
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestCurrentVideo by rememberUpdatedState(currentVideo)
    val latestIsPlaying by rememberUpdatedState(isPlaying)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    controller.onStart()
                }
                Lifecycle.Event.ON_RESUME -> {
                    controller.onResume()
                    // The user may have opened a file manager/text editor while the player was
                    // paused and changed the ORIGINAL uploaded subtitle. Sync that source back
                    // into the canonical app-private copy, then reload the active MPV subtitle
                    // without adding another track. This also covers timing/text edits.
                    val resumedVideo = latestCurrentVideo
                    if (resumedVideo != null) {
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val attached = CustomSubtitlePersistenceManager.getAttachedSubtitles(
                                    context = context,
                                    path = resumedVideo.path,
                                    videoId = resumedVideo.id,
                                    uriString = resumedVideo.uri.toString(),
                                    title = resumedVideo.displayName
                                )
                                val changedRecords = mutableListOf<AttachedSubtitleRecord>()
                                for (record in attached) {
                                    if (CustomSubtitlePersistenceManager.syncAttachedFromSource(context, record)) {
                                        changedRecords += record
                                    }
                                }
                                if (changedRecords.isNotEmpty() && latestCurrentVideo?.id == resumedVideo.id) {
                                    // Do a seamless external-track hand-off. Do NOT call
                                    // selectSubtitleTracksAtomic() here: that path is for user
                                    // selection and must never reload an already-visible subtitle.
                                    for (record in changedRecords) {
                                        try {
                                            controller.reloadExternalSubtitleSource(
                                                filePath = record.filePath,
                                                originalName = record.originalName,
                                                context = context
                                            )
                                        } catch (_: Throwable) {}
                                    }
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                    if (uiState.playerSettings.autoplayAfterUnlock && !latestIsPlaying) {
                        controller.play()
                        isPlaying = true
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    persistPlaybackPosition()
                    controller.onPause()
                }
                Lifecycle.Event.ON_STOP -> {
                    persistPlaybackPosition()
                    val act = activity
                    if (act != null && uiState.playerSettings.autoPictureInPicture && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !act.isFinishing) {
                        try {
                            act.enterPictureInPictureMode(
                                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                            )
                        } catch (_: Throwable) {}
                    }
                    controller.onStop()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    persistPlaybackPosition()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Keep-screen-on: When actively playing (video is NOT paused), the screen stays awake (FLAG_KEEP_SCREEN_ON).
    // When paused, the screen follows normal system lock behavior (flag cleared), unless keepScreenOnPaused is enabled.
    // When the player is dismissed, onDispose clears the flag so Folder/File browsing locks normally.
    DisposableEffect(activity, isPlaying, uiState.playerSettings.keepScreenOnPaused) {
        val win = activity?.window
        val shouldKeepScreenOn = isPlaying || uiState.playerSettings.keepScreenOnPaused
        if (shouldKeepScreenOn) {
            win?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            win?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            win?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Screen bars and brightness follow the active Player Settings.
    LaunchedEffect(brightness) {
        val window = activity?.window ?: return@LaunchedEffect
        val lp = window.attributes
        lp.screenBrightness = brightness.coerceIn(0.01f, 1.0f)
        window.attributes = lp
    }

    // NOTE: This effect is intentionally keyed on the display settings ONLY for applying
    // insets, and its onDispose here must NOT release the mpv controller — otherwise simply
    // toggling "show status bar" etc. mid-playback would tear down and restart the player.
    DisposableEffect(activity, uiState.playerSettings.showStatusBar, uiState.playerSettings.showNavigationBar, uiState.playerSettings.safeAreaWindow) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            val allTypes = WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
            var showTypes = 0
            if (uiState.playerSettings.showStatusBar) showTypes = showTypes or WindowInsetsCompat.Type.statusBars()
            if (uiState.playerSettings.showNavigationBar) showTypes = showTypes or WindowInsetsCompat.Type.navigationBars()
            if (showTypes != 0) insetsController.show(showTypes) else insetsController.hide(allTypes)
            WindowCompat.setDecorFitsSystemWindows(window, uiState.playerSettings.safeAreaWindow)
        }
        onDispose { /* insets are re-applied above on the next key change; nothing to tear down here */ }
    }

    // True screen-exit safety net: runs exactly once, only when this screen actually leaves
    // composition (not on every settings toggle). exitPlayerNow()/release() are idempotent,
    // so this is harmless even when BackHandler or an autoplay-close path already ran it.
    DisposableEffect(Unit) {
        onDispose {
            val win = activity?.window
            if (win != null) {
                val insetsController = WindowCompat.getInsetsController(win, win.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                val lp = win.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                win.attributes = lp
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            persistPlaybackPosition()
            controller.release()
        }
    }

    val isBuffering by controller.isBuffering.collectAsState()
    val bufferedPositionMs by controller.bufferedPositionMs.collectAsState()

    val playlistNames = remember(playlist) {
        if (playlist.isNotEmpty()) playlist.map { it.displayName }
        else listOf(videoTitle)
    }

    val currentConfig = LocalConfiguration.current
    val isWindowPortrait = currentConfig.orientation == Configuration.ORIENTATION_PORTRAIT
    val shouldRotateForExit = isExitingPlayer && isWindowPortrait && wasInLandscape

    // Distance (in the drag axis) the player must travel before a release commits to closing
    // rather than springing back — kept as a density-independent constant used by both the
    // gesture handler and the live visual progress below.
    val dismissThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 120.dp.toPx() }
    val dismissHotZonePx = with(androidx.compose.ui.platform.LocalDensity.current) { 96.dp.toPx() }

    // Context.getDisplay() only exists from API 30; below that we must use the deprecated
    // windowManager.defaultDisplay instead or this throws NoSuchMethodError at runtime.
    fun currentDisplayRotation(): Int {
        val act = activity ?: return Surface.ROTATION_0
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                act.display?.rotation ?: Surface.ROTATION_0
            } else {
                @Suppress("DEPRECATION")
                act.windowManager.defaultDisplay.rotation
            }
        } catch (_: Throwable) {
            Surface.ROTATION_0
        }
    }

    // During Back, the player is dismissed as a rounded bottom-sheet surface. The radius is
    // animated only for the exit so the normal player UI remains edge-to-edge while playing.
    // The native video surface is replaced by the captured Compose frame during exit below,
    // allowing this clip to remain visually consistent while the whole player slides away.
    val exitCornerRadius by animateDpAsState(
        targetValue = if (isExitingPlayer) 30.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(180),
        label = "PlayerExitCornerRadius"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(exitCornerRadius))
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitEachGesture {
                    // requireUnconsumed = false: we only care whether the touch STARTS in the
                    // top hot-zone; if it doesn't, we do nothing and this same down event still
                    // reaches the seek bar / tap-to-toggle-controls / pinch-zoom handlers below
                    // us in the tree exactly as before this feature was added.
                    val down = awaitFirstDown(requireUnconsumed = false)

                    // Physical-top-of-device edge, expressed in THIS window's current pixel
                    // coordinate space. Portrait (and upside-down portrait) keep the natural
                    // top edge at the screen's top. In landscape, which screen edge is the
                    // physical top edge depends on which way the device was rotated to get
                    // there, so we ask the display directly rather than assuming a fixed side.
                    val rotation = currentDisplayRotation()
                    val axisIsVertical = rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180
                    val inHotZone = when {
                        axisIsVertical -> down.position.y <= dismissHotZonePx
                        rotation == Surface.ROTATION_90 -> down.position.x <= dismissHotZonePx
                        else /* ROTATION_270 */ -> down.position.x >= size.width - dismissHotZonePx
                    }
                    if (!inHotZone) return@awaitEachGesture

                    var traveled = 0f
                    try {
                        // The hot zone overlaps the top control bar, so the drag is only claimed
                        // once it is clearly a downward (top -> bottom) swipe. Horizontal drags
                        // (sliding the top bar) and anything already consumed by a child are
                        // left completely untouched.
                        val touchSlop = viewConfiguration.touchSlop
                        var accAlong = 0f
                        var accAcross = 0f
                        var claimed = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (!claimed && change.isConsumed) break
                            val rawDelta = change.positionChange()
                            // Map this rotation's forward (top-towards-bottom) direction onto a
                            // single positive "traveled" amount, regardless of axis/sign.
                            val delta = when {
                                axisIsVertical -> rawDelta.y
                                rotation == Surface.ROTATION_90 -> rawDelta.x
                                else -> -rawDelta.x
                            }
                            val across = if (axisIsVertical) rawDelta.x else rawDelta.y
                            if (!claimed) {
                                accAlong += delta
                                accAcross += across
                                if (kotlin.math.max(kotlin.math.abs(accAlong), kotlin.math.abs(accAcross)) < touchSlop) continue
                                if (accAlong <= 0f || kotlin.math.abs(accAcross) > kotlin.math.abs(accAlong)) break
                                claimed = true
                                isDismissDragging = true
                                traveled = accAlong.coerceAtLeast(0f)
                                change.consume()
                                coroutineScope.launch { dismissDragAnim.snapTo(traveled) }
                                continue
                            }
                            change.consume()
                            traveled = (traveled + delta).coerceAtLeast(0f)
                            coroutineScope.launch { dismissDragAnim.snapTo(traveled) }
                        }
                    } finally {
                        isDismissDragging = false
                        if (traveled >= dismissThresholdPx) {
                            exitPlayerNow()
                        } else {
                            coroutineScope.launch {
                                dismissDragAnim.animateTo(
                                    0f,
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        }
                    }
                }
            }
            .graphicsLayer {
                val progress = (dismissDragAnim.value / dismissThresholdPx).coerceIn(0f, 1f)
                // Stay fully opaque while dragging so portrait/landscape look identical.
                val rotation = currentDisplayRotation()
                when {
                    rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180 ->
                        translationY = dismissDragAnim.value
                    rotation == Surface.ROTATION_90 -> translationX = dismissDragAnim.value
                    else -> translationX = -dismissDragAnim.value
                }
            },

        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = if (shouldRotateForExit || entryFakeLandscape) {
                val screenW = currentConfig.screenWidthDp.dp
                val screenH = currentConfig.screenHeightDp.dp
                Modifier
                    .requiredSize(screenH, screenW)
                    .graphicsLayer {
                        rotationZ = if (entryFakeLandscape) entryFakeRotation else 90f
                    }
            } else {
                Modifier.fillMaxSize()
            }.background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
        val isFirstFrameReady = !isBuffering && (currentPositionMs > 100L)
        val posterAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isFirstFrameReady) 0f else 1f,
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 350,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            ),
            label = "PosterAlpha"
        )

        // Calculate dynamic fill scale for Cover / Crop rendering modes
        val containerW = maxWidth.value
        val containerH = maxHeight.value
        val vw = if (realVideoWidth > 0) realVideoWidth.toFloat() else 16f
        val vh = if (realVideoHeight > 0) realVideoHeight.toFloat() else 9f

        val fillScale = remember(containerW, containerH, vw, vh) {
            val scaleX = if (vw > 0f) containerW / vw else 1f
            val scaleY = if (vh > 0f) containerH / vh else 1f
            val minScale = minOf(scaleX, scaleY)
            val maxScale = maxOf(scaleX, scaleY)
            if (minScale > 0.0001f) (maxScale / minScale).coerceAtLeast(1.0f) else 1.0f
        }

        val baseDisplayScale = when (aspectRatioMode) {
            AspectRatioMode.FIT -> 1.0f
            AspectRatioMode.FILL -> fillScale
            AspectRatioMode.CROP -> fillScale * 1.25f
            AspectRatioMode.ORIGINAL -> 1.0f
        }

        val animatedDisplayScale by androidx.compose.animation.core.animateFloatAsState(
            targetValue = baseDisplayScale * videoZoomScale,
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 220,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            ),
            label = "VideoDisplayScale"
        )
        // -------------------------------------------------------------
        // LIVE SCALABLE & PANNABLE VIDEO RENDERING CONTAINER
        // -------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = animatedDisplayScale,
                        scaleY = animatedDisplayScale,
                        // NOTE: Flip / Mirror are intentionally NOT applied here. The mpv video is a native
                        // SurfaceView that ignores Compose rotationX/rotationY; the transform is applied
                        // inside mpv's filter chain via controller.setVideoTransform(...) instead.
                        translationX = videoPanOffset.x,
                        translationY = videoPanOffset.y
                    )
                    .then(
                        if (isPanAndZoomEnabled && (animatedDisplayScale > 1.0f || videoZoomScale > 1.0f)) {
                            Modifier.pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    videoPanOffset += dragAmount
                                }
                            }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                // VIDEO RENDERING SURFACE (NATIVE MPV)
                // Hidden once `shouldRotateForExit` is true: at that point the Activity's real
                // window orientation has already flipped to portrait and this whole box is being
                // held "landscape-looking" purely via a Compose graphicsLayer rotationZ trick.
                // A live SurfaceView (this AndroidView) is composited by the OS independently of
                // its parent's Compose transforms, so it does NOT rotate/resize along with that
                // trick — it keeps rendering at its old (now-mismatched) geometry for a few
                // frames, which is what was letting the Home screen behind it show through as a
                // brief flash during the landscape dismiss animation. The freeze-frame Image
                // below (a plain Compose bitmap, not a native surface) rotates correctly with the
                // container, so from this point on it alone represents the paused video while the
                // dismiss animation finishes.
                // Keep the live surface in place during the exit animation whenever the
                // window itself is not yet forced through the landscape->portrait handoff.
                // The captured frame is drawn above it, so switching from SurfaceView to a
                // bitmap cannot produce the tiny down/up refresh that was visible at Back time.
                // For a landscape exit, shouldRotateForExit becomes true only after the window
                // is portrait, at which point the native surface is hidden and the frozen frame
                // carries the rest of the animation.
                if (!shouldRotateForExit &&
                    !entryFakeLandscape &&
                    resolvedPlayablePath.isNotBlank() &&
                    (File(resolvedPlayablePath).exists() || resolvedPlayablePath.startsWith("fd://") || resolvedPlayablePath.startsWith("http://") || resolvedPlayablePath.startsWith("https://"))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            VideoPlayerMpvView(ctx).also { mpv ->
                                mpv.initialize(ctx.filesDir.path, ctx.cacheDir.path)
                                // playEpisodeAtIndex() queues the pending path before the native view is attached.
                                // The surface callback owns the single initial MPV load.
                                controller.attach(mpv, coroutineScope)
                            }
                        },
                        update = {
                            // Keep update empty to prevent recomposition reloads and lag
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // VIDEO THUMBNAIL / FIRST FRAME AS INITIAL POSTER (Smoothly fades out when video frame is ready to avoid black screen)
                if ((controllerPos > 200L || currentPositionMs > 200L) && !isBuffering) {
                    hasRenderedInitialFrame = true
                }
                val thumbAlpha by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (hasRenderedInitialFrame) 0f else 1f,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 280,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    ),
                    label = "ThumbnailTransitionAlpha"
                )

                if (thumbnailBitmap != null && thumbAlpha > 0.01f) {
                    Image(
                        bitmap = thumbnailBitmap!!.asImageBitmap(),
                        contentDescription = "Video Initial Frame",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(alpha = thumbAlpha)
                    )
                }

                val activeFreezeFrame = exitFreezeFrameBitmap ?: controller.lastCapturedFrame.value
                if (isExitingPlayer && activeFreezeFrame != null) {
                    Image(
                        bitmap = activeFreezeFrame.asImageBitmap(),
                        contentDescription = "Video Exit Freeze Frame",
                        contentScale = when (aspectRatioMode) {
                            AspectRatioMode.FILL, AspectRatioMode.CROP -> ContentScale.Crop
                            else -> ContentScale.Fit
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

            }
        }

        // -------------------------------------------------------------
        // BUFFERING INDICATOR
        // -------------------------------------------------------------
        if (uiState.playerSettings.showLoadingCircle && isBuffering && isPlaying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                LumoraExpressiveLoadingIndicator(
                    size = 56.dp,
                    isDark = true
                )
            }
        }

        // -------------------------------------------------------------
        // LIQUID GLASS PLAYER CONTROLS OVERLAY
        // -------------------------------------------------------------
        if (!entryFakeLandscape) VideoPlayerOverlay(
            videoTitle = videoTitle,
            currentEpisodeIndex = currentEpisodeIndex,
            totalEpisodes = totalEpisodes,
            chapterTitle = currentChapterTitle,
            isPlaying = isPlaying,
            onPlayPauseToggle = {
                isPlaying = !isPlaying
                if (isPlaying) {
                    controller.play()
                } else {
                    controller.pause()
                    persistPlaybackPosition()
                }
            },
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            onSeekTo = { targetMs ->
                currentPositionMs = targetMs
                controller.seekTo(targetMs)
                persistPlaybackPosition(targetMs)
            },
            onPreviousVideo = {
                if (currentVideoIndex > 0) {
                    playEpisodeAtIndex(currentVideoIndex - 1)
                } else if (currentPlaylist.size > 1 && repeatMode == PlayerRepeatMode.ALL) {
                    playEpisodeAtIndex(currentPlaylist.size - 1)
                }
            },
            onNextVideo = {
                if (isShuffle && currentPlaylist.size > 1) {
                    val availableIndices = currentPlaylist.indices.filter { it != currentVideoIndex }
                    val randomIdx = availableIndices.randomOrNull() ?: ((currentVideoIndex + 1) % currentPlaylist.size)
                    playEpisodeAtIndex(randomIdx)
                } else if (currentVideoIndex + 1 < currentPlaylist.size) {
                    playEpisodeAtIndex(currentVideoIndex + 1)
                } else if (currentPlaylist.size > 1 && repeatMode == PlayerRepeatMode.ALL) {
                    playEpisodeAtIndex(0)
                }
            },
            hasPrevious = uiState.playerSettings.enableNextPrevious && (currentVideoIndex > 0 || (currentPlaylist.size > 1 && repeatMode == PlayerRepeatMode.ALL)),
            hasNext = uiState.playerSettings.enableNextPrevious && ((currentVideoIndex + 1 < currentPlaylist.size) || (isShuffle && currentPlaylist.size > 1) || (currentPlaylist.size > 1 && repeatMode == PlayerRepeatMode.ALL)),
            onBack = {
                exitPlayerNow()
            },
            playbackSpeed = playbackSpeed,
            onSpeedChange = { spd ->
                playbackSpeed = spd
                controller.setPlaybackSpeed(spd)
                showNotification("Speed: ${spd}x")
            },
            decoderMode = decoderMode,
            onDecoderModeChange = { dec ->
                decoderMode = dec
                showNotification("Decoder: ${dec.label}")
            },
            repeatMode = repeatMode,
            onRepeatModeChange = { rpt ->
                repeatMode = rpt
                val msg = when (rpt) {
                    PlayerRepeatMode.OFF -> "Repeat: Off"
                    PlayerRepeatMode.ONE -> "Repeat: Single Episode"
                    PlayerRepeatMode.ALL -> "Repeat: Entire Playlist"
                }
                showNotification(msg)
            },
            isShuffle = isShuffle,
            onShuffleChange = { shf ->
                isShuffle = shf
                showNotification(if (shf) "Shuffle: ON" else "Shuffle: OFF")
            },
            isAudioOnly = isAudioOnly,
            onAudioOnlyChange = { ao ->
                isAudioOnly = ao
                showNotification(if (ao) "Audio-only playback active" else "Video playback active")
            },
            aspectRatioMode = aspectRatioMode,
            onAspectRatioChange = { asp ->
                aspectRatioMode = asp
                showNotification("Display: ${asp.label}")
            },
            videoZoomScale = videoZoomScale,
            onVideoZoomChange = { z -> videoZoomScale = z },
            isVideoFlippedVertically = isVideoFlippedVertically,
            isVideoMirrored = isVideoMirrored,
            onToggleVideoFlipVertical = {
                isVideoFlippedVertically = !isVideoFlippedVertically
                showNotification(if (isVideoFlippedVertically) "Flip Vertical: ON" else "Flip Vertical: OFF")
            },
            onMirrorVideoRight = {
                isVideoMirrored = true
                showNotification("Mirror: RIGHT")
            },
            onMirrorVideoLeft = {
                isVideoMirrored = false
                showNotification("Mirror: LEFT")
            },
            isPanAndZoomEnabled = isPanAndZoomEnabled,
            onTogglePanAndZoom = {
                isPanAndZoomEnabled = it
                showNotification(if (it) "Pan & Zoom: Enabled" else "Pan & Zoom: Disabled")
            },
            onResetZoomAndPan = {
                videoZoomScale = 1.0f
                videoPanOffset = Offset.Zero
                isPanAndZoomEnabled = false
                showNotification("Zoom Reset: 100%")
            },
            isPitchCorrectionEnabled = isPitchCorrectionEnabled,
            onTogglePitchCorrection = { enabled ->
                isPitchCorrectionEnabled = enabled
                controller.setAudioPitchCorrection(enabled)
                showNotification(if (enabled) "Pitch Correction: ON (Natural Voice Pitch)" else "Pitch Correction: OFF (Dynamic Pitch)")
            },
            loopPointA = loopPointA,
            loopPointB = loopPointB,
            onSetLoopPointA = { pt ->
                loopPointA = pt
                showNotification("Loop Point A set: ${formatPlaybackTime(pt)}")
            },
            onSetLoopPointB = { pt ->
                loopPointB = pt
                showNotification("Loop Point B set: ${formatPlaybackTime(pt)}")
            },
            onClearLoop = {
                loopPointA = null
                loopPointB = null
                showNotification("A-B Loop Cleared")
            },
            brightness = brightness,
            onBrightnessChange = { brightness = it },
            volumeBoostCap = uiState.volumeBoostCap,
            onVolumeBoostChange = { boost -> controller.setVolume(boost) },
            contrast = contrast,
            onContrastChange = { contrast = it },
            saturation = saturation,
            onSaturationChange = { saturation = it },
            videoFilterPreset = videoFilterPreset,
            onSelectVideoFilterPreset = { preset ->
                videoFilterPreset = preset
                coroutineScope.launch {
                    val current = PlayerSettings.load(context)
                    PlayerSettings.save(context, current.copy(videoFilterPreset = preset.id))
                }
                showNotification("Filter: ${preset.displayName}")
            },
            manualVideoAdjustments = manualVideoAdjustments,
            onManualVideoAdjustmentsChange = { manual ->
                manualVideoAdjustments = manual
                coroutineScope.launch {
                    val current = PlayerSettings.load(context)
                    PlayerSettings.save(
                        context,
                        current.copy(
                            videoBrightness = manual.brightness,
                            videoContrast = manual.contrast,
                            videoSaturation = manual.saturation,
                            videoGamma = manual.gamma,
                            videoSharpness = manual.sharpness,
                            videoHue = manual.hue,
                            videoTemperature = manual.temperature,
                            videoTint = manual.tint,
                            videoDeband = manual.deband
                        )
                    )
                }
            },
            selectedAudioTrackId = selectedAudioTrackId,
            onSelectAudioTrack = { id ->
                hasManualAudioTrackSelection = true
                selectedAudioTrackId = id
                controller.setAudioTrack(id)
                saveAudioSelection(id)
                val trackName = realAudioTracks.firstOrNull { it.id == id }?.title ?: "Track $id"
                showNotification("Audio Track: $trackName")
            },
            trackAudioConfigs = trackAudioConfigs,
            onTrackChannelModeChange = { trackId, mode ->
                val old = trackAudioConfigs[trackId] ?: TrackAudioConfig()
                val updated = old.copy(channelMode = mode)
                val newMap = trackAudioConfigs + (trackId to updated)
                trackAudioConfigs = newMap
                if (trackId == selectedAudioTrackId) {
                    controller.setAudioChannelMode(mode)
                }
                coroutineScope.launch {
                    val current = PlayerSettings.load(context)
                    PlayerSettings.save(
                        context,
                        current.copy(trackAudioConfigsJson = PlayerSettings.encodeTrackAudioConfigs(newMap))
                    )
                }
                showNotification("Audio Channel: ${mode.getDisplayName()}")
            },
            onTrackVolumeNormalizationChange = { trackId, enabled ->
                val old = trackAudioConfigs[trackId] ?: TrackAudioConfig()
                val updated = old.copy(volumeNormalization = enabled)
                val newMap = trackAudioConfigs + (trackId to updated)
                trackAudioConfigs = newMap
                if (trackId == selectedAudioTrackId) {
                    controller.setVolumeNormalization(enabled)
                }
                coroutineScope.launch {
                    val current = PlayerSettings.load(context)
                    PlayerSettings.save(
                        context,
                        current.copy(trackAudioConfigsJson = PlayerSettings.encodeTrackAudioConfigs(newMap))
                    )
                }
                showNotification("Volume Normalization: ${if (enabled) "On" else "Off"}")
            },
            onTrackDynamicRangeCompressionChange = { trackId, enabled ->
                val old = trackAudioConfigs[trackId] ?: TrackAudioConfig()
                val updated = old.copy(dynamicRangeCompression = enabled)
                val newMap = trackAudioConfigs + (trackId to updated)
                trackAudioConfigs = newMap
                if (trackId == selectedAudioTrackId) {
                    controller.setDynamicRangeCompression(enabled)
                }
                coroutineScope.launch {
                    val current = PlayerSettings.load(context)
                    PlayerSettings.save(
                        context,
                        current.copy(trackAudioConfigsJson = PlayerSettings.encodeTrackAudioConfigs(newMap))
                    )
                }
                showNotification("Dynamic Range Compression: ${if (enabled) "On" else "Off"}")
            },
            onRemoveSubtitleTrack = { trackId ->
                val trackToRemove = realSubtitleTracks.firstOrNull { it.id == trackId }
                controller.removeSubtitleTrack(trackId)
                if (trackToRemove != null) {
                    CustomSubtitlePersistenceManager.removeAttachedSubtitle(
                        context = context,
                        path = currentVideo?.path,
                        videoId = currentVideo?.id ?: 0L,
                        uriString = currentVideo?.uri?.toString(),
                        title = currentVideo?.displayName,
                        trackId = trackId,
                        originalName = trackToRemove.originalFilename.ifBlank { trackToRemove.title }
                    )
                }
                // Forget the removed track in the remembered selection (the other side stays).
                // Persist the selection independent of the on/off toggle so a temporarily
                // hidden primary track isn't silently dropped from the saved selection.
                saveSubtitleSelection(
                    primaryId = if (selectedSubtitleTrackId != trackId) selectedSubtitleTrackId else 0,
                    secondaryId = if (controller.secondarySubtitleTrackId.value != trackId) controller.secondarySubtitleTrackId.value else 0
                )
                showNotification("Removed Subtitle Track")
            },
            onRemoveAudioTrack = { trackId ->
                controller.removeAudioTrack(trackId)
                showNotification("Removed Audio Track")
            },
            availableVideoQualities = emptyList(),
            selectedVideoQuality = null,
            onSelectVideoQuality = {},
            selectedSubtitleTrackId = selectedSubtitleTrackId,
            secondarySubtitleTrackId = realSecondarySubtitleTrackId,
            lastTouchedSubtitleTrackId = lastTouchedSubtitleTrackId,
            onLastTouchedSubtitleTrackIdChange = { lastTouchedSubtitleTrackId = it },
            onSelectSubtitleTrack = { id ->
                selectSubtitleTracks(id, controller.secondarySubtitleTrackId.value)
            },
            onSelectSecondarySubtitleTrack = { id ->
                selectSubtitleTracks(selectedSubtitleTrackId, id)
            },
            onSelectSubtitleTracksCascade = { primId, secId ->
                selectSubtitleTracks(primId, secId)
            },
            isSubtitleVisible = isSubtitleVisible,
            onToggleSubtitleVisibility = {
                val nextVis = !isSubtitleVisible
                isSubtitleVisible = nextVis
                controller.setSubtitleVisibility(nextVis)
                SubtitleSessionMemory.setSubtitlesEnabled(nextVis)
                if (!nextVis) {
                    controller.selectSubtitleTracksAtomic(0, 0)
                    selectedSubtitleTrackId = 0
                    controller.preferredSubtitleTrackNumber = 0
                    saveSubtitleSelection(primaryId = 0, secondaryId = 0)
                    showNotification("Subtitles Off")
                } else {
                    val activeTrack = (if (lastTouchedSubtitleTrackId > 0) lastTouchedSubtitleTrackId else null)
                        ?: realSubtitleTracks.firstOrNull { it.isSelected }?.id
                        ?: realSubtitleTracks.firstOrNull()?.id
                        ?: 0
                    if (activeTrack > 0) {
                        selectSubtitleTracks(activeTrack, 0)
                    } else {
                        showNotification("No subtitle selected")
                    }
                }
            },
            subtitleOffsetMs = subtitleOffsetMs,
            onSubtitleOffsetChange = { offset ->
                subtitleOffsetMs = offset
                controller.setSubtitleDelay(offset)
                showNotification("Subtitle Sync: ${if (offset >= 0) "+$offset" else "$offset"} ms")
            },
            audioDelayMs = audioDelayMs,
            onAudioDelayChange = { delay ->
                audioDelayMs = delay
                controller.setAudioDelay(delay)
            },
            equalizerEnabled = equalizerEnabled,
            onToggleEqualizer = { enabled ->
                equalizerEnabled = enabled
                controller.setEqualizer(
                    enabled = enabled,
                    eq60Hz = eq60Hz,
                    eq230Hz = eq230Hz,
                    eq910Hz = eq910Hz,
                    eq3600Hz = eq3600Hz,
                    eq14000Hz = eq14000Hz,
                    volumeBoostDb = volumeBoostDb,
                    immediate = true
                )
                saveEqualizerSettingsDebounced { it.copy(equalizerEnabled = enabled) }
            },
            eq60Hz = eq60Hz,
            onEq60HzChange = { v ->
                eq60Hz = v
                controller.setEqualizer(
                    enabled = equalizerEnabled,
                    eq60Hz = v,
                    eq230Hz = eq230Hz,
                    eq910Hz = eq910Hz,
                    eq3600Hz = eq3600Hz,
                    eq14000Hz = eq14000Hz,
                    volumeBoostDb = volumeBoostDb,
                    immediate = false
                )
                saveEqualizerSettingsDebounced { it.copy(equalizer60Hz = v) }
            },
            eq230Hz = eq230Hz,
            onEq230HzChange = { v ->
                eq230Hz = v
                controller.setEqualizer(
                    enabled = equalizerEnabled,
                    eq60Hz = eq60Hz,
                    eq230Hz = v,
                    eq910Hz = eq910Hz,
                    eq3600Hz = eq3600Hz,
                    eq14000Hz = eq14000Hz,
                    volumeBoostDb = volumeBoostDb,
                    immediate = false
                )
                saveEqualizerSettingsDebounced { it.copy(equalizer230Hz = v) }
            },
            eq910Hz = eq910Hz,
            onEq910HzChange = { v ->
                eq910Hz = v
                controller.setEqualizer(
                    enabled = equalizerEnabled,
                    eq60Hz = eq60Hz,
                    eq230Hz = eq230Hz,
                    eq910Hz = v,
                    eq3600Hz = eq3600Hz,
                    eq14000Hz = eq14000Hz,
                    volumeBoostDb = volumeBoostDb,
                    immediate = false
                )
                saveEqualizerSettingsDebounced { it.copy(equalizer910Hz = v) }
            },
            eq3600Hz = eq3600Hz,
            onEq3600HzChange = { v ->
                eq3600Hz = v
                controller.setEqualizer(
                    enabled = equalizerEnabled,
                    eq60Hz = eq60Hz,
                    eq230Hz = eq230Hz,
                    eq910Hz = eq910Hz,
                    eq3600Hz = v,
                    eq14000Hz = eq14000Hz,
                    volumeBoostDb = volumeBoostDb,
                    immediate = false
                )
                saveEqualizerSettingsDebounced { it.copy(equalizer3600Hz = v) }
            },
            eq14000Hz = eq14000Hz,
            onEq14000HzChange = { v ->
                eq14000Hz = v
                controller.setEqualizer(
                    enabled = equalizerEnabled,
                    eq60Hz = eq60Hz,
                    eq230Hz = eq230Hz,
                    eq910Hz = eq910Hz,
                    eq3600Hz = eq3600Hz,
                    eq14000Hz = v,
                    volumeBoostDb = volumeBoostDb,
                    immediate = false
                )
                saveEqualizerSettingsDebounced { it.copy(equalizer14000Hz = v) }
            },
            volumeBoostDb = volumeBoostDb,
            onVolumeBoostDbChange = { v ->
                volumeBoostDb = v
                controller.setEqualizer(
                    enabled = equalizerEnabled,
                    eq60Hz = eq60Hz,
                    eq230Hz = eq230Hz,
                    eq910Hz = eq910Hz,
                    eq3600Hz = eq3600Hz,
                    eq14000Hz = eq14000Hz,
                    volumeBoostDb = v,
                    immediate = false
                )
                saveEqualizerSettingsDebounced { it.copy(equalizerVolumeBoostDb = v) }
            },
            equalizerPreset = equalizerPreset,
            onSelectEqualizerPreset = { preset ->
                val pEq60 = preset.gains[0]
                val pEq230 = preset.gains[1]
                val pEq910 = preset.gains[2]
                val pEq3600 = preset.gains[3]
                val pEq14000 = preset.gains[4]
                equalizerPreset = preset.id
                eq60Hz = pEq60
                eq230Hz = pEq230
                eq910Hz = pEq910
                eq3600Hz = pEq3600
                eq14000Hz = pEq14000
                controller.setEqualizer(
                    enabled = equalizerEnabled,
                    eq60Hz = pEq60,
                    eq230Hz = pEq230,
                    eq910Hz = pEq910,
                    eq3600Hz = pEq3600,
                    eq14000Hz = pEq14000,
                    volumeBoostDb = volumeBoostDb,
                    immediate = true
                )
                saveEqualizerSettingsDebounced {
                    it.copy(
                        equalizerPreset = preset.id,
                        equalizer60Hz = pEq60,
                        equalizer230Hz = pEq230,
                        equalizer910Hz = pEq910,
                        equalizer3600Hz = pEq3600,
                        equalizer14000Hz = pEq14000
                    )
                }
            },
            onResetEqualizer = {
                val flat = EqualizerPreset.FLAT
                val fEq60 = flat.gains[0]
                val fEq230 = flat.gains[1]
                val fEq910 = flat.gains[2]
                val fEq3600 = flat.gains[3]
                val fEq14000 = flat.gains[4]
                equalizerPreset = flat.id
                eq60Hz = fEq60
                eq230Hz = fEq230
                eq910Hz = fEq910
                eq3600Hz = fEq3600
                eq14000Hz = fEq14000
                volumeBoostDb = 0f
                controller.setEqualizer(
                    enabled = equalizerEnabled,
                    eq60Hz = fEq60,
                    eq230Hz = fEq230,
                    eq910Hz = fEq910,
                    eq3600Hz = fEq3600,
                    eq14000Hz = fEq14000,
                    volumeBoostDb = 0f,
                    immediate = true
                )
                saveEqualizerSettingsDebounced {
                    it.copy(
                        equalizerPreset = flat.id,
                        equalizer60Hz = fEq60,
                        equalizer230Hz = fEq230,
                        equalizer910Hz = fEq910,
                        equalizer3600Hz = fEq3600,
                        equalizer14000Hz = fEq14000,
                        equalizerVolumeBoostDb = 0f
                    )
                }
            },
            chapters = realChapters,
            bufferedPositionMs = bufferedPositionMs,
            showBufferedRange = uiState.playerSettings.showBufferedRange,
            thumbFastPreview = uiState.playerSettings.thumbFastPreview,
            previewVideoUri = currentVideo?.uri,
            previewVideoPath = currentVideo?.path,
            onSeekToChapter = { chap ->
                currentPositionMs = chap.timeMs
                controller.seekToChapter(chap)
                persistPlaybackPosition(chap.timeMs)
                showNotification("Chapter: ${chap.title}")
            },
            skipMarkers = skipMarkers,
            onSkipToPosition = { targetMs ->
                val isVideoFullyFinished = durationMs > 0 && targetMs >= (durationMs - 600L)

                if (isVideoFullyFinished) {
                    if (currentVideoIndex + 1 < currentPlaylist.size) {
                        showNotification("Skipped to next episode")
                        playEpisodeAtIndex(currentVideoIndex + 1)
                    } else if (uiState.playerSettings.repeatPlaylistAfterLast && currentPlaylist.size > 1) {
                        showNotification("Restarting playlist")
                        playEpisodeAtIndex(0)
                    } else {
                        persistPlaybackPosition(durationMs)
                        exitPlayerNow()
                    }
                } else {
                    currentPositionMs = targetMs
                    controller.seekTo(targetMs)
                    persistPlaybackPosition(targetMs)
                }
            },
            audioTracks = realAudioTracks,
            subtitleTracks = realSubtitleTracks,
            playlistVideos = playlistNames,
            playlistItems = currentPlaylist,
            onSelectPlaylistItem = { idx ->
                playEpisodeAtIndex(idx - 1)
            },
            onReorderPlaylist = { from, to ->
                val list = currentPlaylist.toMutableList()
                if (from in list.indices && to in list.indices) {
                    val moved = list.removeAt(from)
                    list.add(to, moved)
                    currentPlaylist = list
                    val currentVidId = currentVideo?.id
                    if (currentVidId != null) {
                        val newIdx = list.indexOfFirst { it.id == currentVidId }
                        if (newIdx >= 0) {
                            currentVideoIndex = newIdx
                        }
                    }
                }
            },
            onImportSubtitle = {
                showSubtitleFileTree = true
            },
            onPasteSubtitle = {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                val clipData = clipboard?.primaryClip
                val pastedText = if (clipData != null && clipData.itemCount > 0) {
                    clipData.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                } else null

                if (pastedText.isNullOrBlank()) {
                    showNotification("Clipboard is empty or contains no subtitle text")
                } else {
                    val previousPrimaryId = realSubtitleTracks.firstOrNull { it.isSelected }?.id ?: selectedSubtitleTrackId
                    coroutineScope.launch {
                        try {
                            val detectedFormat = SubtitleEngine.detectFormat(pastedText)
                            val extension = detectedFormat.extension
                            // Every paste gets its own name (PASTED-SUB, PASTED-SUB-2, ...): a fixed name made
                            // the next paste replace the previous one, so two pasted subtitles could never
                            // exist together in the Uploaded list.
                            val usedPasteNames = controller.subtitleTracks.value
                                .filter { it.isExternal }
                                .map { (it.originalFilename.ifBlank { it.title }).substringBeforeLast('.').lowercase() }
                                .toSet()
                            var pasteIndex = 1
                            var trackName = "PASTED-SUB"
                            while (trackName.lowercase() in usedPasteNames) {
                                pasteIndex++
                                trackName = "PASTED-SUB-$pasteIndex"
                            }
                            val originalName = "$trackName.$extension"
                            val targetId = withContext(Dispatchers.IO) {
                                val persistentFile = CustomSubtitlePersistenceManager.persistSubtitleFromText(
                                    context = context,
                                    content = pastedText,
                                    trackName = trackName,
                                    extension = extension
                                )
                                val attachedPath = attachUploadedSubtitle(
                                    filePath = persistentFile.absolutePath,
                                    originalName = originalName
                                ) ?: persistentFile.absolutePath
                                val addedSubId = controller.addExternalSubtitle(
                                    filePath = attachedPath,
                                    context = context,
                                    originalName = originalName,
                                    select = true
                                )
                                addedSubId?.takeIf { it > 0 } ?: controller.subtitleTracks.value.lastOrNull()?.id ?: 0
                            }
                            if (targetId > 0) {
                                promoteNewSubtitleToPrimary(targetId, previousPrimaryId)
                            }
                            showNotification("Added & Applied: $originalName")
                        } catch (e: Exception) {
                            e.printStackTrace()
                            showNotification("Error pasting subtitle: ${e.message}")
                        }
                    }
                }
            },
            onImportAudio = {
                showAudioFileTree = true
            },
            translationStatusByTrackId = translationStatusByTrackId,
            onStartTranslate = onStartTranslate,
            onTranslateSubtitleTrack = { trackId, progress ->
                val settings = AiFeaturesSettingsStore.load(context)
                if (!settings.hasUsableModel) {
                    Result.failure(IllegalStateException("Configure a usable API model or installed offline model first"))
                } else {
                    controller.translateSubtitleTrack(
                        context = context,
                        trackId = trackId,
                        settings = settings,
                        targetLanguageLabel = TranslateLanguages.ALL.firstOrNull { it.code == settings.translateLanguageCode }?.label ?: settings.translateLanguageCode,
                        onProgress = { p -> progress(if (p.total == 0) 0 else (p.completed * 100) / p.total) }
                    )
                }
            },
            onSelectSubtitleFile = { selectedFile ->
                coroutineScope.launch {
                    try {
                        if (importSubtitleFileSafely(selectedFile)) {
                            showNotification("Loaded Subtitle: ${selectedFile.name}")
                        } else {
                            showNotification("Failed to load subtitle file")
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        t.printStackTrace()
                        showNotification("Error: ${t.message}")
                    }
                }
            },
            onTakeScreenshot = {
                coroutineScope.launch {
                    val vid = currentVideo
                    val pos = currentPositionMs
                    showNotification("Capturing clean video frame...")
                    val result = if (uiState.playerSettings.subtitlesInScreenshots) {
                        kotlinx.coroutines.suspendCancellableCoroutine<Result<String>> { cont ->
                            controller.captureCurrentFrame { bitmap ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    val save = if (bitmap != null) {
                                        com.example.util.VideoScreenshotSaver.saveBitmapFrame(
                                            context = context,
                                            frameBitmap = bitmap,
                                            settings = uiState.playerSettings,
                                            title = vid?.displayName ?: "Screenshot",
                                            positionMs = pos
                                        )
                                    } else Result.failure(Exception("Unable to capture the rendered video surface"))
                                    if (cont.isActive) cont.resume(save) {}
                                }
                            }
                        }
                    } else {
                        com.example.util.VideoScreenshotSaver.captureAndSaveCleanVideoFrame(
                            context = context, uri = vid?.uri, path = vid?.path, positionMs = pos, settings = uiState.playerSettings
                        )
                    }
                    result.onSuccess { msg ->
                        showNotification(msg)
                    }.onFailure { err ->
                        showNotification("Screenshot failed: ${err.localizedMessage ?: "Unknown error"}")
                    }
                }
            },
            onTogglePiP = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                    try {
                        val params = if (activity is MainActivity) {
                            activity.buildPipParams(isPlaying, uiState.playerSettings.autoPictureInPicture)
                        } else {
                            PictureInPictureParams.Builder()
                                .setAspectRatio(Rational(16, 9))
                                .build()
                        }
                        if (params != null) {
                            activity.enterPictureInPictureMode(params)
                        }
                    } catch (e: Exception) {
                        showNotification("PiP not supported on this device")
                    }
                } else {
                    showNotification("Picture-in-Picture requires Android 8.0+")
                }
            },
            onToggleOrientation = {
                val current = activity?.requestedOrientation
                if (current == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || isLandscape) {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
            },
            isLandscape = isLandscape,
            glassBlurTransparency = uiState.glassBlurTransparency,
            thumbnailStrategy = uiState.thumbnailStrategy,
            thumbnailQuality = uiState.thumbnailQuality,
            thumbnailFallbackSecond = uiState.thumbnailFallbackSecond,
            controlsAnimationStyle = if (uiState.playerSettings.reduceAnimation) ControlsAnimationStyle.MINIMAL_FADE else uiState.controlsAnimationStyle,
            animationSpeed = uiState.animationSpeed,
            showPlayerNotifications = uiState.showPlayerNotifications,
            enableDoubleTap = uiState.playerSettings.enableDoubleTap,
            doubleTapSeekSeconds = (if (uiState.playerSettings.customSkipDuration != 90) uiState.playerSettings.customSkipDuration else uiState.playerSettings.doubleTapSeekDuration).coerceIn(1, 600),
            doubleTapSeekAreaWidthPercent = uiState.playerSettings.doubleTapSeekAreaWidth,
            doubleTapLeftAction = uiState.playerSettings.doubleTapLeftAction,
            doubleTapCenterAction = uiState.playerSettings.doubleTapCenterAction,
            doubleTapRightAction = uiState.playerSettings.doubleTapRightAction,
            enableSingleTap = uiState.playerSettings.enableSingleTap,
            singleTapSeekSeconds = uiState.playerSettings.singleTapSeekDuration,
            singleTapSeekAreaWidthPercent = uiState.playerSettings.singleTapSeekAreaWidth,
            singleTapLeftAction = uiState.playerSettings.singleTapLeftAction,
            singleTapCenterAction = uiState.playerSettings.singleTapCenterAction,
            singleTapRightAction = uiState.playerSettings.singleTapRightAction,
            singleTapCenterGesture = uiState.playerSettings.singleTapCenterGesture,
            brightnessGesturesEnabled = uiState.playerSettings.brightnessGestures,
            volumeGesturesEnabled = uiState.playerSettings.volumeGestures,
            swapVolumeBrightnessGestures = uiState.playerSettings.swapVolumeBrightness,
            gestureHudOppositeSide = uiState.playerSettings.gestureHudOppositeSide,
            pinchToZoomEnabled = uiState.playerSettings.pinchToZoom,
            pinchToZoomSubtitlesEnabled = uiState.playerSettings.pinchToZoomSubtitles,
            swipeSubtitlesToSeekDialogEnabled = uiState.playerSettings.swipeSubtitlesToSeekDialog,
            horizontalSwipeToSeekEnabled = uiState.playerSettings.horizontalSwipeToSeek,
            swipeUpCenterForPlaylistEnabled = uiState.playerSettings.swipeUpCenterForPlaylist,
            horizontalSwipeSensitivity = uiState.playerSettings.horizontalSwipeSensitivity,
            holdMultiSpeedEnabled = uiState.playerSettings.holdMultiSpeed,
            holdSpeedMultiplier = uiState.playerSettings.holdSpeedMultiplier,
            dynamicSpeedOverlayEnabled = uiState.playerSettings.dynamicSpeedOverlay,
            holdDragMovesSubtitlesEnabled = uiState.playerSettings.holdDragMovesSubtitles,
            mediaPreviousControlEnabled = uiState.playerSettings.mediaPreviousControl,
            mediaPlayPauseControlEnabled = uiState.playerSettings.mediaPlayPauseControl,
            mediaNextControlEnabled = uiState.playerSettings.mediaNextControl,
            onSubtitleScaleChange = { scale ->
                subtitleScale = scale.coerceIn(0.5f, 3.0f)
                val updated = liveSubtitleSettings.copy(subtitleScale = subtitleScale)
                liveSubtitleSettings = updated
                controller.setSubtitleScale(subtitleScale.toDouble())
                applySubtitleAppearanceDirect(updated)
                saveSubtitleSettingsDebounced(updated)
                onPlayerSettingsChange(updated)
            },
            onSubtitlePositionChange = { position ->
                subtitlePosition = position.coerceIn(0f, 150f)
                val updated = liveSubtitleSettings.copy(subtitlePosition = subtitlePosition)
                liveSubtitleSettings = updated
                controller.setSubtitlePosition(subtitlePosition.toDouble())
                applySubtitleAppearanceDirect(updated)
                saveSubtitleSettingsDebounced(updated)
                onPlayerSettingsChange(updated)
            },
            subtitleScale = subtitleScale,
            subtitlePosition = subtitlePosition,
            subtitleSettings = liveSubtitleSettings,
            onSubtitleSettingsChange = { updated ->
                liveSubtitleSettings = updated
                subtitleScale = updated.subtitleScale
                subtitlePosition = updated.subtitlePosition
                applySubtitleAppearanceDirect(updated)
                saveSubtitleSettingsDebounced(updated)
                onPlayerSettingsChange(updated)
            },
            onLoadAdvancedAssSource = { trackId -> controller.readAdvancedAssSource(context, trackId) },
            onLoadRawSubtitleSource = { trackId -> controller.readRawSubtitleSource(context, trackId) },
            onRawSubtitlePreview = { key, text -> controller.setRawSubtitlePreview(key, text) },
            showDoubleTapSeekFeedback = uiState.playerSettings.rippleOnDoubleTap && uiState.playerSettings.showSeekTime,
            showVolumeSliderOverlay = uiState.playerSettings.volumeSliderOverlay,
            showBrightnessSliderOverlay = uiState.playerSettings.brightnessSliderOverlay,
            showHoldSpeedOverlay = uiState.playerSettings.holdSpeedOverlay,
            showAspectRatioFeedback = uiState.playerSettings.aspectRatioFeedback,
            showZoomLevelFeedback = uiState.playerSettings.zoomLevelFeedback,
            showRepeatShuffleFeedback = uiState.playerSettings.repeatShuffleFeedback,
            showActionFeedbackPills = uiState.playerSettings.actionFeedbackPills,
            externalNotification = playerNotificationMessage,
            externalNotificationKey = playerNotificationKey,
            gestureSensitivityMode = uiState.gestureSensitivityMode,
            onGestureSensitivityModeChange = onGestureSensitivityModeChange,
            seekbarStyle = uiState.seekbarStyle,
            onSeekbarStyleChange = onSeekbarStyleChange,
            playerLayoutConfig = uiState.playerLayoutConfig,
            onPlayerLayoutConfigChange = onPlayerLayoutConfigChange,
            onResetPlayerLayoutConfig = onResetPlayerLayoutConfig,
            playerSettings = uiState.playerSettings,
            videoWidth = realVideoWidth,
            videoHeight = realVideoHeight
        )

        if (showSubtitleFileTree) {
            SubtitleFileTreeDialog(
                initialVideoPath = currentVideo?.path,
                isLandscape = isLandscape,
                glassBlurTransparency = uiState.glassBlurTransparency,
                onSubtitleSelected = { selectedFile ->
                    coroutineScope.launch {
                        try {
                            if (importSubtitleFileSafely(selectedFile)) {
                                Toast.makeText(context, "Loaded Subtitle: ${selectedFile.name}", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Failed to load subtitle file", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            t.printStackTrace()
                            Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onOpenSystemPicker = {
                    subtitleFilePicker.launch(arrayOf("*/*"))
                },
                onDismiss = {
                    showSubtitleFileTree = false
                }
            )
        }

        if (showAudioFileTree) {
            AudioFileTreeDialog(
                initialVideoPath = currentVideo?.path,
                isLandscape = isLandscape,
                glassBlurTransparency = uiState.glassBlurTransparency,
                onAudioSelected = { selectedFile ->
                    coroutineScope.launch {
                        try {
                            if (selectedFile.exists() && selectedFile.length() > 0) {
                                hasManualAudioTrackSelection = true
                                val addedAudioId = controller.addExternalAudio(selectedFile.absolutePath, originalName = selectedFile.name)
                                if (addedAudioId != null && addedAudioId > 0) {
                                    selectedAudioTrackId = addedAudioId
                                    controller.setAudioTrack(addedAudioId)
                                } else {
                                    val lastTrack = controller.audioTracks.value.lastOrNull()?.id
                                    if (lastTrack != null && lastTrack > 0) {
                                        selectedAudioTrackId = lastTrack
                                        controller.setAudioTrack(lastTrack)
                                    }
                                }
                                Toast.makeText(context, "Loaded Audio Track: ${selectedFile.name}", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Failed to load audio file", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(context, "Error loading audio: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onOpenSystemPicker = {
                    audioFilePicker.launch(arrayOf("audio/*", "*/*"))
                },
                onDismiss = {
                    showAudioFileTree = false
                }
            )
        }
    }
}
}

fun getPlayableFilePath(context: Context, uri: Uri?, fallbackPath: String? = null): String {
    try {
        if (!fallbackPath.isNullOrBlank()) {
            if (fallbackPath.startsWith("/") || fallbackPath.startsWith("fd://") || fallbackPath.startsWith("http://") || fallbackPath.startsWith("https://")) {
                return fallbackPath
            }
            if (File(fallbackPath).exists() && File(fallbackPath).length() > 0) {
                return fallbackPath
            }
        }
        if (uri == null) return fallbackPath ?: ""

        val uriStr = uri.toString()
        if (uri.scheme == "file") {
            val path = uri.path
            if (!path.isNullOrBlank()) {
                return path
            }
        }
        if (uri.scheme == "asset" || uriStr.startsWith("asset:///")) {
            val assetName = if (uriStr.startsWith("asset:///")) {
                uriStr.removePrefix("asset:///")
            } else {
                uri.path?.removePrefix("/") ?: ""
            }
            if (assetName.isNotBlank()) {
                val targetFile = File(context.cacheDir, assetName)
                if (!targetFile.exists() || targetFile.length() == 0L) {
                    try {
                        context.assets.open(assetName).use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    } catch (_: Throwable) {}
                }
                if (targetFile.exists() && targetFile.length() > 0) {
                    return targetFile.absolutePath
                }
            }
        }
        if (uri.scheme == "content") {
            try {
                val projection = arrayOf(android.provider.MediaStore.Video.Media.DATA)
                val directPath = context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    val columnIndex = cursor.getColumnIndex(android.provider.MediaStore.Video.Media.DATA)
                    if (columnIndex != -1 && cursor.moveToFirst()) {
                        cursor.getString(columnIndex)
                    } else null
                }
                if (!directPath.isNullOrBlank() && File(directPath).exists() && File(directPath).length() > 0) {
                    return directPath
                }
            } catch (_: Throwable) {}

            // ZERO-COPY DIRECT STREAMING: Directly acquire FileDescriptor from ContentResolver without copying any data to storage
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    val fd = pfd.detachFd()
                    if (fd >= 0) {
                        return "fd://$fd"
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("VideoPlayerScreen", "Failed to get direct file descriptor for $uri", e)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return fallbackPath ?: ""
}

private fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}

