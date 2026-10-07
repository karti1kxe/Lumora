package com.example.player

import com.example.ui.components.StyledIcon

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.SurfaceHolder
import android.widget.Toast
import com.example.ui.state.HwAccelMode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.screens.SubtitleCue
import com.example.ui.screens.AssParser
import com.example.ui.screens.getPlayableFilePath
import com.example.util.AppStorageManager
import com.example.util.CustomSubtitlePersistenceManager
import com.example.util.SubtitleSessionMemory
import com.example.util.MkvManagerEngine
import com.example.ai.AiFeaturesSettings
import com.example.ai.SubtitleTranslationService
import com.example.ai.TranslateLanguages
import android.os.ParcelFileDescriptor
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class PlayerMediaTrack(
    val id: Int,
    val type: String, // "audio" or "sub"
    val title: String,
    val language: String,
    val codec: String,
    val isSelected: Boolean,
    val isExternal: Boolean = false,
    val originalFilename: String = "",
    val isSecondarySelected: Boolean = false,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val isHearingImpaired: Boolean = false,
    val isVisualImpaired: Boolean = false,
    val isAudioCommentary: Boolean = false
)

data class PlayerVideoChapter(
    val index: Int,
    val title: String,
    val timeMs: Long,
    val formattedTime: String
)

class MpvPlayerController {
    // Guards against use-after-release: once the player screen is exited we tear this
    // controller down immediately (see release()). Any late/queued callback or coroutine
    // tick that still fires afterwards (surface callbacks, polling loop, pending seeks)
    // must no-op instead of touching a destroyed native mpv session, which is what caused
    // crashes when the user tapped something immediately after leaving the player.
    @Volatile
    private var isReleased = false
    @Volatile
    private var mpvView: VideoPlayerMpvView? = null
    private val releaseGuard = Any()
    private var pollingJob: Job? = null
    private var currentFilePath: String? = null
    private var subtitlesLoadedOnce = false
    private val loadedExternalSubtitles = mutableListOf<String>()
    private val externalSubtitlesMap = mutableMapOf<String, String>()
    private val subtitleTrackSourcePaths = mutableMapOf<Int, String>()
    private val subtitleTrackExtractorIndices = mutableMapOf<Int, Int>()
    private val loadedExternalAudios = mutableListOf<String>()
    private val externalAudiosMap = mutableMapOf<String, String>()
    private val externalAudiosLangMap = mutableMapOf<String, String>()
    private var lastSurfaceRefreshTimestamp = 0L

    var preferredSubtitleTrackNumber: Int? = null

    /**
     * While true no subtitle is auto-selected (neither by mpv's default pick nor by the fallback in
     * refreshTracksAndChapters): a saved per-video subtitle selection is about to be restored and
     * must not be overridden or flashed over by a default track. Always released by the caller.
     */
    @Volatile
    var holdSubtitleAutoSelect: Boolean = false

    fun releaseSubtitleAutoSelectHold() {
        holdSubtitleAutoSelect = false
    }

    /**
     * `sub-reload` (used by Override ASS/SSA, font and style changes) unloads and re-adds the
     * subtitle, so for a moment mpv reports "no subtitle selected" and the re-added external
     * track may even get a new id. That transient state must never be mistaken for "the user has
     * nothing selected": the track list refresh would otherwise auto-pick another track (and
     * switch Override OFF), which looks like the subtitle un-selecting itself.
     */
    @Volatile private var subtitleReloadUntilMs = 0L

    /**
     * Storage key of an uploaded/pasted subtitle's edits. It includes the VIDEO it is used with,
     * so the same subtitle text used in another video gets its own independent edits (embedded
     * subtitles were already per video).
     */
    private fun externalSubtitleKey(text: String): String {
        val video = currentFilePath
        return AdvancedAssStyleEngine.contentKey(if (video.isNullOrBlank()) text else "$video\u0000$text")
    }

    private fun markSubtitleReload() {
        subtitleReloadUntilMs = System.currentTimeMillis() + 2500L
    }

    /** Identity of the track the user picked for THIS video, to find it again if its id changes. */
    @Volatile private var userSubtitleIdentity: String? = null

    private fun subtitleIdentity(t: PlayerMediaTrack): String =
        (if (t.isExternal) "ext|" + t.originalFilename.ifBlank { t.title } else "emb|" + t.title + "|" + t.language + "|" + t.codec)

    /**
     * The track the user explicitly chose in this video, if it is still present (by id, or - when
     * mpv re-created it with a new id - by identity). Returns null if the user never picked one.
     */
    private fun findUserSelectedSubtitle(list: List<PlayerMediaTrack>): PlayerMediaTrack? {
        val logical = lastActiveSubtitleTrackId
        if (logical <= 0 || isSubtitleExplicitlyDisabled) return null
        list.firstOrNull { it.id == logical }?.let { return it }
        val ident = userSubtitleIdentity ?: return null
        return list.firstOrNull { subtitleIdentity(it) == ident }
    }

    // Track discovery bookkeeping (see startPolling / discoverTracksPromptly)
    @Volatile private var trackDiscoveryStartedAt = 0L
    @Volatile private var lastTrackListCount = -1
    @Volatile private var lastTrackRefreshAt = 0L

    // Live subtitle preferences supplied by the Settings screen. These are applied
    // before every new media load and can also be changed while playback is active.
    private var subtitleAutoLoadExternal = true
    private var subtitleOverrideAssSsa = false
    // The raw "Override ASS/SSA Styles" toggle exactly as set in the Subtitle Style panel
    // (PlayerSettings.overrideAssSsaSubtitles). [subtitleOverrideAssSsa] above instead stores
    // the EFFECTIVE, per-track value actually applied to mpv (it is forced to true for non-
    // ASS/SSA formats, which used to silently clobber the user's real preference). Every place
    // that needs to know "did the user turn this switch on" - in particular whenever the
    // selected subtitle TRACK changes - must read this field, not [subtitleOverrideAssSsa].
    @Volatile private var subtitleOverrideAssSsaUserPref = false
    private var subtitleScaleWithWindow = true
    private var subtitleFontDirectory: String? = null
    private var subtitleFontFamily: String? = null
    private var subtitleScale = 1.0
    private var subtitlePosition = 100.0
    private var subtitleBlendWithVideo = false
    private var subtitleBold = false
    private var subtitleItalic = false
    private var subtitleUnderline = false
    private var subtitleAlignment = "center"
    private var subtitleFontSize = 52.0
    private var subtitleBorderStyle = "outline_shadow"
    private var subtitleBorderSize = 3.0
    private var subtitleShadowOffset = 1.5
    private var subtitleTextColor = "1.0/1.0/1.0/1.0"
    private var subtitleBorderColor = "0.0/0.0/0.0/1.0"
    private var subtitleBackgroundColor = "0.0/0.0/0.0/0.60"
    private var subtitleShadowColor = "0.0/0.0/0.0/0.75"
    private var subtitleUsesGeneratedAss = false
    private var lastAppliedFontDirectory: String? = null

    /** Prepared (cached) subtitle file path -> the original subtitle file it was built from. */
    private val preparedSubtitleSources = ConcurrentHashMap<String, File>()
    private var lastSelectedFontKey: String? = null

    /**
     * The user picked (or cleared) a subtitle font. Remember it for the subtitle preparation code,
     * then rebuild the already loaded external subtitle files so that font (plus the Go Noto glyph
     * fallback for characters it lacks) shows up right away, without touching playback.
     */
    private fun onSelectedSubtitleFontChanged(fontFamily: String?) {
        val forceFont = subtitleOverrideAssSsaUserPref && !advancedAssGoverning
        val key = fontFamily?.trim().orEmpty() + "|force=" + forceFont
        if (key == lastSelectedFontKey) return
        lastSelectedFontKey = key
        SubtitleFontManager.forceSelectedFontOnAss = forceFont
        SubtitleFontManager.setSelectedFont(fontFamily)
        val ctx = mpvView?.context ?: return
        // Edited / pasted (raw) copies carry the font inside their text: rebuild them too.
        if (rawEditorEnabled || rawBindings.isNotEmpty()) scheduleAdvancedAssSync()
        // Embedded ASS/SSA tracks are not rewritten by us; libass caches the font it resolved for
        // each style, so a live font change only shows up after the track is re-initialised.
        val refreshEmbeddedAss = preparedSubtitleSources.isEmpty() && isCurrentSubtitleNativeAssSsa()
        if (preparedSubtitleSources.isEmpty() && !refreshEmbeddedAss) return
        playerScope.launch {
            try {
                if (refreshEmbeddedAss) {
                    withMpvLock(Unit) { v ->
                        try {
                            val sid = v.mpv.getPropertyInt("sid") ?: 0
                            if (sid > 0) {
                                v.mpv.setPropertyString("sid", "no")
                                v.mpv.setPropertyInt("sid", sid)
                            }
                        } catch (_: Throwable) {}
                    }
                    nudgeRedrawIfPaused()
                    return@launch
                }
                var changed = false
                for ((prepared, source) in preparedSubtitleSources.entries.toList()) {
                    if (!source.exists()) continue
                    val before = try { File(prepared).takeIf { it.exists() }?.readText() } catch (_: Throwable) { null }
                    val newPath = UniversalSubtitleEngine.prepareSubtitleForPlayback(
                        context = ctx,
                        file = source,
                        videoFps = _estimatedFps.value.takeIf { it > 0.0 } ?: 24.0,
                        videoPath = currentFilePath
                    )
                    if (newPath != prepared) continue
                    val after = try { File(prepared).readText() } catch (_: Throwable) { null }
                    if (before != after) changed = true
                }
                if (changed) {
                    withMpvLock(Unit) { v ->
                        try { markSubtitleReload(); v.mpv.command("sub-reload") } catch (_: Throwable) {}
                    }
                    nudgeRedrawIfPaused()
                }
            } catch (_: Throwable) {}
        }
    }

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(60000L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(true)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0)
    val playbackSpeed: StateFlow<Double> = _playbackSpeed.asStateFlow()

    private val _isLooping = MutableStateFlow(false)
    val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()

    private val _eofReached = MutableStateFlow(false)
    val eofReached: StateFlow<Boolean> = _eofReached.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _droppedFrameCount = MutableStateFlow(0)
    val droppedFrameCount: StateFlow<Int> = _droppedFrameCount.asStateFlow()

    private val _estimatedFps = MutableStateFlow(0.0)
    val estimatedFps: StateFlow<Double> = _estimatedFps.asStateFlow()

    private val _isHwAccelActive = MutableStateFlow(true)
    val isHwAccelActive: StateFlow<Boolean> = _isHwAccelActive.asStateFlow()

    private val _videoWidth = MutableStateFlow(0)
    val videoWidth: StateFlow<Int> = _videoWidth.asStateFlow()

    private val _videoHeight = MutableStateFlow(0)
    val videoHeight: StateFlow<Int> = _videoHeight.asStateFlow()

    private val _performanceStatus = MutableStateFlow("MediaCodec HW Accelerated")
    val performanceStatus: StateFlow<String> = _performanceStatus.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<PlayerMediaTrack>>(emptyList())
    val audioTracks: StateFlow<List<PlayerMediaTrack>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<PlayerMediaTrack>>(emptyList())
    val subtitleTracks: StateFlow<List<PlayerMediaTrack>> = _subtitleTracks.asStateFlow()

    private val _secondarySubtitleTrackId = MutableStateFlow(0)
    val secondarySubtitleTrackId: StateFlow<Int> = _secondarySubtitleTrackId.asStateFlow()

    // Bumped whenever a subtitle track that the user never explicitly chose for THIS video (the
    // "next episode" / no-saved-selection auto-match below) silently drops "Override ASS/SSA
    // Styles" back to OFF, so the on-screen style panel/persisted setting can follow. Only ever
    // signals a reset to OFF - the video player screen must observe it and set its own
    // overrideAssSsaSubtitles to false when it fires. Turning it ON is always the user's own
    // explicit action in the style panel and never comes through this flow.
    private val _overrideAssSsaAutoResetEvent = MutableStateFlow(0L)
    val overrideAssSsaAutoResetEvent: StateFlow<Long> = _overrideAssSsaAutoResetEvent.asStateFlow()

    private val _chapters = MutableStateFlow<List<PlayerVideoChapter>>(emptyList())
    val chapters: StateFlow<List<PlayerVideoChapter>> = _chapters.asStateFlow()

    private val _lastCapturedFrame = MutableStateFlow<Bitmap?>(null)
    val lastCapturedFrame: StateFlow<Bitmap?> = _lastCapturedFrame.asStateFlow()

    private val _bufferedPositionMs = MutableStateFlow(0L)
    val bufferedPositionMs: StateFlow<Long> = _bufferedPositionMs.asStateFlow()

    @Volatile
    private var preciseSeekingEnabled: Boolean = true

    private var lastDroppedFrames = 0
    private var currentHwMode: HwAccelMode = HwAccelMode.FORCE
    private var pendingFilePath: String? = null
    private var pendingStartPositionMs: Long = 0L
    private var isSurfaceReady = false

    @Volatile
    var isResumeSeekVerified: Boolean = true
        private set
    @Volatile
    private var targetResumePositionMs: Long = 0L

    private var currentAudioDelayMs: Long = 0L
    private var currentSubtitleDelayMs: Long = 0L
    private var controllerScope: CoroutineScope? = null

    /**
     * Captures the live hardware surface buffer into state memory.
     * Used across screen rotation transitions to eliminate black screen flashes.
     */
    fun captureCurrentFrame(onComplete: ((Bitmap?) -> Unit)? = null) {
        val view = mpvView
        if (view != null && view.holder.surface?.isValid == true) {
            view.captureSurfaceFrame { bmp ->
                if (bmp != null) {
                    _lastCapturedFrame.value = bmp
                }
                onComplete?.invoke(bmp ?: _lastCapturedFrame.value)
            }
        } else {
            onComplete?.invoke(_lastCapturedFrame.value)
        }
    }

    /**
     * Shifts the audio track relative to the video timeline.
     * Positive [delayMs] delays the audio (plays later); negative advances it (plays earlier).
     * Safe to call at any time; if the native surface isn't ready yet, the value is
     * cached and re-applied automatically once the video (re)loads.
     */
    fun setAudioDelay(delayMs: Long) {
        currentAudioDelayMs = delayMs
        val view = mpvView ?: return
        try {
            view.mpv.setPropertyDouble("audio-delay", delayMs / 1000.0)
        } catch (_: Throwable) {}
    }

    fun getAudioDelayMs(): Long = currentAudioDelayMs

    private val trackDelayMap = mutableMapOf<Int, Long>()

    /**
     * Shifts subtitle timing relative to the video timeline.
     * Positive [delayMs] delays subtitles; negative advances them.
     * Applies ONLY to the Primary Subtitle track; Secondary Subtitle remains completely untouched.
     */
    fun setSubtitleDelay(delayMs: Long) {
        currentSubtitleDelayMs = delayMs
        if (lastActiveSubtitleTrackId > 0) {
            trackDelayMap[lastActiveSubtitleTrackId] = delayMs
        }
        val view = mpvView ?: return
        try {
            // sub-delay shifts the Primary Subtitle (in seconds with millisecond double precision)
            view.mpv.setPropertyDouble("sub-delay", delayMs / 1000.0)
            nudgeRedrawIfPaused()
            // Note: Never reset or touch secondary-sub-delay here; secondary subtitle delay is preserved!
        } catch (_: Throwable) {}
    }

    fun getSubtitleDelayMs(): Long = currentSubtitleDelayMs

    fun setHwAccelMode(mode: HwAccelMode) {
        currentHwMode = mode
        val view = mpvView ?: return
        try {
            view.mpv.setPropertyString("hwdec", resolveHwdecValue())
            _isHwAccelActive.value = (mode != HwAccelMode.DISABLE)
            _performanceStatus.value = when (mode) {
                HwAccelMode.FORCE -> "MediaCodec HW Forced"
                HwAccelMode.PREFER -> "MediaCodec Auto/Copy Active"
                HwAccelMode.DISABLE -> "Hardware Dec Disabled (SW CPU)"
            }
        } catch (_: Throwable) {}
    }

    fun attach(view: VideoPlayerMpvView, scope: CoroutineScope) {
        if (isReleased) return
        mpvView = view
        controllerScope = scope
        view.onSurfaceCreatedCallback = {
            onSurfaceCreated()
        }
        view.onSurfaceChangedCallback = {
            // Screen orientation change or resize: MPV handles viewport resize natively.
            // Only attach surface if it was not ready yet.
            if (!isSurfaceReady) {
                try {
                    val surface = mpvView?.holder?.surface
                    if (surface != null && surface.isValid) {
                        mpvView?.mpv?.attachSurface(surface)
                        isSurfaceReady = true
                    }
                } catch (_: Throwable) {}
            }
        }
        view.onSurfaceDestroyedCallback = {
            isSurfaceReady = false
        }
        startPolling(scope)

        if (view.holder.surface?.isValid == true) {
            onSurfaceCreated()
        } else {
            val path = pendingFilePath ?: currentFilePath
            if (!path.isNullOrBlank()) {
                loadVideoInternal(path, pendingStartPositionMs)
            }
        }
    }

    private var lastActiveSubtitleTrackId: Int = 0
    /** Monotonic token used to invalidate delayed subtitle-selection recovery jobs. */
    private val subtitleSelectionGeneration = AtomicLong(0L)

    private fun onSurfaceCreated() {
        if (isReleased) return
        isSurfaceReady = true
        val path = pendingFilePath ?: currentFilePath
        if (!path.isNullOrBlank() && mpvView != null) {
            if (currentFilePath == path && _durationMs.value > 0L && pendingStartPositionMs == 0L) {
                // Video is already actively loaded and playing; seamlessly attach surface without reloading file
                try {
                    val surface = mpvView?.holder?.surface
                    if (surface != null && surface.isValid) {
                        mpvView?.mpv?.attachSurface(surface)
                    }
                } catch (_: Throwable) {}
            } else {
                val targetPos = if (pendingStartPositionMs > 500L) pendingStartPositionMs else targetResumePositionMs
                pendingFilePath = null
                pendingStartPositionMs = 0L
                loadVideoInternal(path, targetPos)
            }
        }
    }

    private var wasPlayingBeforeStop: Boolean = false

    fun onStart() {
        // Initial entry is already loaded by the surface callback. Do not trigger a
        // second load here, which caused the startup black/blur flash.
        if (!wasPlayingBeforeStop) return
        try {
            playerScope.launch {
                delay(40)
                withMpvLock(Unit) { view ->
                    try {
                        view.mpv.setPropertyBoolean("pause", false)
                        _isPlaying.value = true
                    } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {}
    }

    fun onStop() {
        try {
            wasPlayingBeforeStop = _isPlaying.value
            withMpvLock(Unit) { view ->
                try { view.mpv.setPropertyBoolean("pause", true) } catch (_: Throwable) {}
            }
            _isPlaying.value = false
        } catch (_: Throwable) {}
    }

    fun onPause() {
        try {
            withMpvLock(Unit) { view ->
                try { view.mpv.setPropertyBoolean("pause", true) } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    fun onResume() {
        // Only rebuild the native surface after a genuine background/foreground cycle.
        if (!wasPlayingBeforeStop) return
        try {
            playerScope.launch {
                delay(50)
                refreshSurface()
                wasPlayingBeforeStop = false
            }
        } catch (_: Throwable) {}
    }

    fun refreshSurface() {
        val now = System.currentTimeMillis()
        if (now - lastSurfaceRefreshTimestamp < 350L) {
            return
        }
        lastSurfaceRefreshTimestamp = now

        try {
            val view = mpvView ?: return
            val path = currentFilePath ?: pendingFilePath ?: return
            val isFdOrStream = path.startsWith("fd://") || path.startsWith("http://") || path.startsWith("https://") || path.startsWith("rtsp://")
            if (!isFdOrStream && !File(path).exists()) return

            val surface = view.holder.surface
            if (surface == null || !surface.isValid) {
                return
            }

            val currentPos = _currentPositionMs.value
            val wasPlaying = _isPlaying.value
            val currentSpeed = _playbackSpeed.value
            val activeAid = _audioTracks.value.firstOrNull { it.isSelected }?.id
            val activeSid = _subtitleTracks.value.firstOrNull { it.isSelected }?.id ?: lastActiveSubtitleTrackId

            // 1. Reconnect native surface and configure video output
            try {
                view.mpv.attachSurface(surface)
            } catch (_: Throwable) {}

            view.mpv.setOptionString("force-window", "yes")
            view.mpv.setPropertyString("vo", "gpu")
            view.mpv.setPropertyString("hwdec", resolveHwdecValue())
            view.mpv.setPropertyString("framedrop", "vo")
            _isBuffering.value = true

            // 2. Re-instantiate media stream decoder cleanly on the new Surface
            val startSec = (currentPos / 1000.0).coerceAtLeast(0.0)
            if (currentPos > 500L) {
                targetResumePositionMs = currentPos
                isResumeSeekVerified = false
                try {
                    view.mpv.setOptionString("start", "$startSec")
                } catch (_: Throwable) {}
                try {
                    view.mpv.setPropertyString("start", "+$startSec")
                } catch (_: Throwable) {}
            }

            resetAdvancedAssBindings()
            try {
                view.mpv.command("loadfile", path, "replace")
            } catch (_: Throwable) {
                view.playFile(path)
            }
            try {
                view.mpv.setPropertyDouble("audio-delay", currentAudioDelayMs / 1000.0)
                view.mpv.setPropertyDouble("sub-delay", currentSubtitleDelayMs / 1000.0)
            } catch (_: Throwable) {}

            view.mpv.setPropertyDouble("speed", currentSpeed.coerceIn(0.25, 4.0))
            view.mpv.setPropertyString("sub-ass", "yes")
            try { view.mpv.setPropertyString("sub-font-provider", "fontconfig") } catch (_: Throwable) {}
            view.mpv.setPropertyBoolean("sub-visibility", true)
            view.mpv.setPropertyString("sub-ass-override", if (subtitleUsesGeneratedAss || subtitleOverrideAssSsa) "force" else "no")
            view.mpv.setPropertyBoolean("embeddedfonts", true)
            view.mpv.setPropertyBoolean("pause", !wasPlaying)
            _isPlaying.value = wasPlaying

            // 3. Re-inject external subtitles if any were loaded
            loadedExternalSubtitles.forEach { subPath ->
                val isSubStream = subPath.startsWith("http://") || subPath.startsWith("https://")
                if (isSubStream || File(subPath).exists()) {
                    try {
                        val subTitle = externalSubtitlesMap[subPath] ?: "Subtitle"
                        view.mpv.command("sub-add", subPath, "auto", subTitle)
                    } catch (_: Throwable) {}
                }
            }
            loadedExternalAudios.forEach { audioPath ->
                val isAudioStream = audioPath.startsWith("http://") || audioPath.startsWith("https://")
                if (isAudioStream || File(audioPath).exists()) {
                    try {
                        val audioTitle = externalAudiosMap[audioPath] ?: "Audio"
                        view.mpv.command("audio-add", audioPath, "auto", audioTitle)
                    } catch (_: Throwable) {}
                }
            }

            // 4. Fine seek & track restoration
            playerScope.launch {
                if (currentPos > 500L) {
                    var verified = false
                    val sec = currentPos / 1000.0
                    repeat(8) { attempt ->
                        if (verified || isReleased) return@repeat
                        delay(if (attempt == 0) 100L else 150L)
                        try {
                            val v = mpvView ?: return@repeat
                            val realPosSec = v.mpv.getPropertyDouble("time-pos") ?: -1.0
                            val realPosMs = if (realPosSec >= 0.0) (realPosSec * 1000.0).toLong() else -1L
                            if (realPosMs >= 0L && kotlin.math.abs(realPosMs - currentPos) <= 2500L) {
                                verified = true
                                isResumeSeekVerified = true
                                _currentPositionMs.value = realPosMs
                            } else {
                                try {
                                    v.mpv.command("seek", "$sec", "absolute+exact")
                                } catch (_: Throwable) {
                                    v.mpv.command("seek", "$sec", "absolute")
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                    isResumeSeekVerified = true
                }
                try {
                    val v = mpvView ?: return@launch
                    if (activeAid != null && activeAid > 0) {
                        try { v.mpv.setPropertyInt("aid", activeAid) } catch (_: Throwable) {}
                    }
                    if (activeSid > 0) {
                        try { v.mpv.setPropertyInt("sid", activeSid) } catch (_: Throwable) {}
                    }
                    v.mpv.setPropertyBoolean("pause", !wasPlaying)
                    _isPlaying.value = wasPlaying
                } catch (_: Throwable) {}
                scheduleAdvancedAssSync(900L)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun startPolling(scope: CoroutineScope) {
        pollingJob?.cancel()
        pollingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    synchronized(advancedAssNativeLock) {
                        if (isReleased) return@launch
                        val view = mpvView
                        if (!isReleased && view != null && view.holder.surface?.isValid == true) {
                        val posSec = view.mpv.getPropertyDouble("time-pos")
                        val durSec = view.mpv.getPropertyDouble("duration")
                        val paused = view.mpv.getPropertyBoolean("pause") ?: true
                        val cacheTimeSec = view.mpv.getPropertyDouble("demuxer-cache-time") ?: 0.0

                        if (posSec != null && posSec >= 0.0) {
                            val realMs = (posSec * 1000.0).toLong()
                            if (!isResumeSeekVerified && targetResumePositionMs > 500L) {
                                if (kotlin.math.abs(realMs - targetResumePositionMs) <= 2500L || realMs > 500L) {
                                    isResumeSeekVerified = true
                                    _currentPositionMs.value = realMs
                                }
                                // Keep target position displayed until first positive position is reached
                            } else {
                                _currentPositionMs.value = realMs
                            }
                            val frameWidth = view.mpv.getPropertyInt("video-params/dw")
                                ?: view.mpv.getPropertyInt("dwidth") ?: 0
                            val frameHeight = view.mpv.getPropertyInt("video-params/dh")
                                ?: view.mpv.getPropertyInt("dheight") ?: 0
                            if (posSec >= 0.0) {
                                _isBuffering.value = false
                            }
                        }
                        if (durSec != null && durSec > 0.0) {
                            _durationMs.value = (durSec * 1000.0).toLong()
                        }
                        _isPlaying.value = !paused
                        if (cacheTimeSec > 0.0 && posSec != null && posSec >= 0.0) {
                            _bufferedPositionMs.value = ((posSec + cacheTimeSec) * 1000.0).toLong().coerceAtLeast(_currentPositionMs.value)
                        } else {
                            _bufferedPositionMs.value = _currentPositionMs.value
                        }

                        val eof = try {
                            view.mpv.getPropertyBoolean("eof-reached") ?: false
                        } catch (_: Throwable) { false }
                        if (eof) {
                            _eofReached.value = true
                        }

                        val vw = view.mpv.getPropertyInt("dwidth")
                            ?: view.mpv.getPropertyInt("video-params/dw")
                            ?: view.mpv.getPropertyInt("video-params/w")
                            ?: 0
                        val vh = view.mpv.getPropertyInt("dheight")
                            ?: view.mpv.getPropertyInt("video-params/dh")
                            ?: view.mpv.getPropertyInt("video-params/h")
                            ?: 0
                        if (vw > 0 && vh > 0) {
                            if (_videoWidth.value != vw) _videoWidth.value = vw
                            if (_videoHeight.value != vh) _videoHeight.value = vh
                        }

                        // Performance Monitor: Check dropped frames & hardware acceleration status
                        try {
                            val dropped = view.mpv.getPropertyInt("frame-drop-count")
                                ?: view.mpv.getPropertyInt("vo-drop-frame-count")
                                ?: view.mpv.getPropertyInt("decoder-frame-drop-count")
                                ?: 0
                            val estFps = view.mpv.getPropertyDouble("container-fps")
                                ?: view.mpv.getPropertyDouble("estimated-vf-fps")
                                ?: 0.0
                            val hwdec = view.mpv.getPropertyString("hwdec-current") ?: "mediacodec"

                            _droppedFrameCount.value = dropped
                            if (estFps > 0.0) {
                                _estimatedFps.value = estFps
                            }
                            val isHw = hwdec != "no" && hwdec.isNotBlank()
                            _isHwAccelActive.value = isHw

                            if (dropped > lastDroppedFrames && !paused) {
                                val delta = dropped - lastDroppedFrames
                                if (delta >= 2) {
                                    view.mpv.setPropertyString("hwdec", if (lastAppliedVfVideoFilter.isNotEmpty()) HwAccelMode.PREFER.mpvValue else "mediacodec")
                                    view.mpv.setPropertyString("framedrop", "vo")
                                    _performanceStatus.value = "Frame drops ($delta) resolved via MediaCodec HW alignment"
                                }
                            } else {
                                _performanceStatus.value = if (isHw) "MediaCodec HW Accelerated" else "Software Decoder Active"
                            }
                            lastDroppedFrames = dropped
                        } catch (_: Throwable) {
                            // Non-critical metric error
                        }

                        // Update tracks/chapters: immediately whenever mpv's track list changes
                        // (embedded tracks discovered, external subtitle/audio added), every 400 ms
                        // during the first seconds after a load, then every 2 s. Frame snapshot
                        // keeps its own ~2 s cadence.
                        try {
                            val now = System.currentTimeMillis()
                            val trackCount = try { view.mpv.getPropertyInt("track-list/count") } catch (_: Throwable) { null } ?: 0
                            val sinceLoad = now - trackDiscoveryStartedAt
                            val interval = if (sinceLoad in 0L..6000L) 400L else 2000L
                            if (trackCount != lastTrackListCount || now - lastTrackRefreshAt >= interval) {
                                lastTrackListCount = trackCount
                                lastTrackRefreshAt = now
                                refreshTracksAndChapters()
                            }
                            if (now % 2000 < 150 && view.holder.surface?.isValid == true) {
                                captureCurrentFrame()
                            }
                        } catch (_: Throwable) {}
                        }
                    }
                } catch (t: Throwable) {
                    // Ignore transient polling errors
                }
                delay(100)
            }
        }
    }

    private val playerScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    /**
     * Suspends until mpv has actually opened the CURRENT file (its `path` is set and it reports
     * tracks), i.e. the moment `sub-add`/`audio-add` and track discovery become possible.
     * Returns false on timeout. Replaces guessing with fixed sleeps, which made subtitle restore
     * and track lists appear late or only "sometimes".
     */
    suspend fun awaitFileLoaded(timeoutMs: Long = 15000L): Boolean {
        val started = System.currentTimeMillis()
        val expected = currentFilePath
        while (!isReleased) {
            val elapsed = System.currentTimeMillis() - started
            if (elapsed > timeoutMs) return false
            val ready = withMpvLock(false) { v ->
                val playing = try { v.mpv.getPropertyString("path") } catch (_: Throwable) { null }
                val count = try { v.mpv.getPropertyInt("track-list/count") } catch (_: Throwable) { null } ?: 0
                val samePath = playing != null && (
                    expected == null || playing == expected ||
                        playing.removePrefix("file://") == expected.removePrefix("file://")
                    )
                // After 1.5 s accept any opened file, in case mpv reports the path differently.
                count > 0 && playing != null && (samePath || elapsed > 1500L)
            }
            if (ready) return true
            delay(20L)
        }
        return false
    }

    private suspend fun discoverTracksPromptly() {
        awaitFileLoaded(20000L)
        if (isReleased) return
        refreshTracksAndChapters()
        delay(150L)
        refreshTracksAndChapters()
        delay(600L)
        refreshTracksAndChapters()
    }

    fun loadVideo(filePath: String, startPositionMs: Long = 0L) {
        if (filePath.isBlank()) {
            _errorMessage.value = "Invalid video file path"
            return
        }
        val isFdOrStream = filePath.startsWith("fd://") || filePath.startsWith("http://") || filePath.startsWith("https://") || filePath.startsWith("rtsp://")
        if (!isFdOrStream) {
            val file = File(filePath)
            if (!file.exists() || file.length() == 0L) {
                _errorMessage.value = "Video file does not exist or is inaccessible"
                return
            }
        }
        _errorMessage.value = null
        _isBuffering.value = true
        if (currentFilePath != filePath) {
            loadedExternalSubtitles.clear()
            loadedExternalAudios.clear()
            externalAudiosMap.clear()
            externalAudiosLangMap.clear()
            SubtitleFontManager.clearVideoEmbeddedFonts()
        }
        currentFilePath = filePath
        pendingFilePath = filePath
        pendingStartPositionMs = startPositionMs
        targetResumePositionMs = startPositionMs
        isResumeSeekVerified = (startPositionMs <= 500L)
        if (startPositionMs > 500L) {
            _currentPositionMs.value = startPositionMs
        }
        AppStorageManager.setActivePlaybackPath(filePath)

        val view = mpvView
        if (view != null && view.holder.surface?.isValid == true && isSurfaceReady) {
            if (!isFdOrStream) {
                playerScope.launch(Dispatchers.IO) {
                    try {
                        SubtitleFontManager.syncFonts(view.context, filePath)
                    } catch (_: Throwable) {}
                }
            }
            pendingFilePath = null
            pendingStartPositionMs = 0L
            loadVideoInternal(filePath, startPositionMs)
        }
        // If the native surface is not ready yet, onSurfaceReady() performs the
        // single initial load from pendingFilePath. This avoids a duplicate decoder load.
    }

    private fun loadVideoInternal(filePath: String, startPositionMs: Long = 0L) {
        if (isReleased) return
        subtitleSelectionGeneration.incrementAndGet()
        val view = mpvView ?: return
        _isBuffering.value = true
        _eofReached.value = false
        subtitlesLoadedOnce = false
        subtitleUsesGeneratedAss = false
        _durationMs.value = 0L
        _subtitleTracks.value = emptyList()
        resetAdvancedAssBindings()
        lastAppliedNativeAssOverride = null
        _audioTracks.value = emptyList()
        // Selections belong to ONE video: never carry the 2nd subtitle over to the next file.
        _secondarySubtitleTrackId.value = 0
        lastActiveSubtitleTrackId = 0
        userSubtitleIdentity = null
        subtitleReloadUntilMs = 0L
        isSubtitleExplicitlyDisabled = false
        trackDiscoveryStartedAt = System.currentTimeMillis()
        lastTrackListCount = -1
        lastTrackRefreshAt = 0L

        val initialPos = if (startPositionMs > 500L) startPositionMs else 0L
        targetResumePositionMs = initialPos
        isResumeSeekVerified = (initialPos <= 500L)
        _currentPositionMs.value = initialPos

        try {
            val startSec = initialPos / 1000.0
            try {
                // Do not disable native track selection while a new file is opening.
                // mpv's auto mode is responsible for honoring container/Matroska
                // default/forced/language metadata when the user has no explicit choice.
                view.mpv.setPropertyString("aid", "auto")
                view.mpv.setPropertyString("sid", "auto")
                view.mpv.setPropertyString("secondary-sid", "no")
            } catch (_: Throwable) {}
            try {
                view.mpv.command("loadfile", filePath, "replace")
            } catch (_: Throwable) {
                view.playFile(filePath)
            }
            try {
                view.mpv.setPropertyDouble("audio-delay", currentAudioDelayMs / 1000.0)
                view.mpv.setPropertyDouble("sub-delay", currentSubtitleDelayMs / 1000.0)
            } catch (_: Throwable) {}

            try {
                view.mpv.setPropertyString("hwdec", resolveHwdecValue())
                view.mpv.setPropertyString("framedrop", "vo")
                view.mpv.setPropertyDouble("speed", _playbackSpeed.value.coerceIn(0.25, 4.0))
                view.mpv.setPropertyBoolean("pause", false)
                view.mpv.setPropertyString("sub-ass", "yes")
                view.mpv.setPropertyBoolean("sub-visibility", true)
                view.mpv.setPropertyString("sub-ass-override", if (subtitleOverrideAssSsa) "force" else "no")
                view.mpv.setPropertyString("sub-auto", if (subtitleAutoLoadExternal) "exact" else "no")
                view.mpv.setPropertyBoolean("embeddedfonts", true)
                if (subtitleOverrideAssSsa) {
                    view.mpv.setPropertyString("sub-scale-with-window", if (subtitleScaleWithWindow) "yes" else "no")
                    view.mpv.setPropertyDouble("sub-scale", subtitleScale.coerceIn(0.5, 3.0))
                    view.mpv.setPropertyDouble("sub-pos", subtitlePosition.coerceIn(0.0, 150.0))
                } else {
                    // Neutral values are required because mpv documents that sub-scale and
                    // sub-pos can affect ASS/SSA even when the main override is disabled.
                    view.mpv.setPropertyString("sub-scale-with-window", "yes")
                    view.mpv.setPropertyDouble("sub-scale", 1.0)
                    view.mpv.setPropertyDouble("sub-pos", 100.0)
                    view.mpv.setPropertyBoolean("sub-ass-scale-with-window", false)
                    view.mpv.setPropertyBoolean("sub-ass-force-margins", false)
                    view.mpv.setPropertyString("sub-ass-force-style", "")
                }
                if (!subtitleFontDirectory.isNullOrBlank()) {
                    view.mpv.setPropertyString("sub-fonts-dir", subtitleFontDirectory!!)
                }
                // The user's selected font is libass' default family; glyphs it lacks are
                // swapped to Go Noto inside the subtitle text (SubtitleFontManager.applyGlyphFallback).
                view.mpv.setPropertyString(
                    "sub-font",
                    subtitleFontFamily?.takeIf { it.isNotBlank() } ?: SubtitleFontManager.BUNDLED_FALLBACK_FONT_FAMILY
                )
                // Keep mpv's native automatic track selection enabled. Explicit saved/user
                // selections are applied later by the existing restoration/manual-selection path.
                view.mpv.setPropertyString("aid", "auto")
                view.mpv.setPropertyString("sid", "auto")
                try { view.mpv.setPropertyString("secondary-sid", "no") } catch (_: Throwable) {}
                view.mpv.setPropertyString("volume-max", "200")
                view.mpv.setPropertyInt("volume", currentVolumeLevel)
                view.mpv.setPropertyBoolean("audio-pitch-correction", currentAudioPitchCorrection)
                view.mpv.setPropertyString("audio-pitch-correction", if (currentAudioPitchCorrection) "yes" else "no")
                updateAudioFilters()
                applyVideoAdjustments(immediate = true)
            } catch (_: Throwable) {}
            _isPlaying.value = true

            // Discover audio/subtitle tracks the instant mpv has the file open (no fixed sleeps,
            // and not blocked by the resume-seek verification below).
            playerScope.launch { discoverTracksPromptly() }

            playerScope.launch {
                if (initialPos > 500L) {
                    val sec = initialPos / 1000.0
                    var confirmed = false
                    for (attempt in 0..20) {
                        if (confirmed || isReleased) break
                        delay(if (attempt == 0) 50L else 100L)
                        try {
                            val v = mpvView ?: break
                            val realPosSec = v.mpv.getPropertyDouble("time-pos") ?: -1.0
                            val realPosMs = if (realPosSec >= 0.0) (realPosSec * 1000.0).toLong() else -1L
                            if (realPosMs >= 0L && kotlin.math.abs(realPosMs - initialPos) <= 2500L) {
                                confirmed = true
                                isResumeSeekVerified = true
                                _currentPositionMs.value = realPosMs
                                _isBuffering.value = false
                            } else {
                                try {
                                    v.mpv.command("seek", "$sec", "absolute+exact")
                                } catch (_: Throwable) {
                                    v.mpv.command("seek", "$sec", "absolute")
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                    isResumeSeekVerified = true
                } else {
                    delay(50L)
                }

                try {
                    val v = mpvView ?: return@launch
                    v.mpv.setPropertyBoolean("pause", false)
                    _isPlaying.value = true
                    _isBuffering.value = false
                } catch (_: Throwable) {}

            }
        } catch (t: Throwable) {
            t.printStackTrace()
            _errorMessage.value = "Error loading video: ${t.localizedMessage ?: "Unknown codec or format error"}"
        }

        // Asynchronously extract real tracks, dimensions, and sibling subtitles in background IO to eliminate startup delay
        playerScope.launch {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                if (filePath.startsWith("fd://")) {
                    val fdInt = filePath.removePrefix("fd://").toIntOrNull()
                    if (fdInt != null) {
                        val pfd = ParcelFileDescriptor.fromFd(fdInt)
                        retriever.setDataSource(pfd.fileDescriptor)
                    }
                } else {
                    retriever.setDataSource(filePath)
                }
                var w = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                var h = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val rot = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if ((rot == 90 || rot == 270) && w > 0 && h > 0) {
                    val tmp = w
                    w = h
                    h = tmp
                }
                retriever.release()
                if (w > 0 && h > 0) {
                    _videoWidth.value = w
                    _videoHeight.value = h
                }
            } catch (_: Throwable) {}

            extractRealTracksFromFile(filePath)

        }
    }

    suspend fun updateSubtitles(cues: List<SubtitleCue>, context: Context) = withContext(Dispatchers.IO) {
        try {
            val view = mpvView ?: return@withContext
            if (currentFilePath == null) return@withContext

            val cacheDir = context.cacheDir
            val subFile = File(cacheDir, "live_preview_subs.ass")
            
            if (cues.isNotEmpty()) {
                val rawAssContent = AssParser.export(cues)
                // Generated ASS already uses Lumora's multi-script fallback font. Do not
                // sanitize or rewrite subtitle text/tags here; preserving Unicode and
                // ASS formatting is more important than replacing glyphs heuristically.
                val assContent = rawAssContent
                subtitleUsesGeneratedAss = true

                // Sync fonts from assets, video directory, and embedded ASS attachments
                val fontsDir = SubtitleFontManager.syncFonts(context, currentFilePath, assContent)

                subFile.writeText(assContent)

                if (subFile.exists() && subFile.length() > 0) {
                    try {
                        view.mpv.setPropertyBoolean("sub-visibility", true)
                        view.mpv.setPropertyString("sub-ass", "yes")
                        view.mpv.setPropertyString("sub-ass-override", if (subtitleOverrideAssSsa) "force" else "no")
                        view.mpv.setPropertyBoolean("sub-use-margins", true)
                        view.mpv.setPropertyBoolean("sub-ass-force-margins", false)
                        view.mpv.setPropertyString("sub-margin-y", "0")
                        view.mpv.setPropertyString("sub-margin-x", "0")
                        view.mpv.setPropertyDouble("sub-pos", subtitlePosition.coerceIn(0.0, 150.0))
                        view.mpv.setPropertyBoolean("sub-ass-scale-with-window", false)
                        view.mpv.setPropertyBoolean("sub-scale-with-window", subtitleScaleWithWindow)
                        view.mpv.setPropertyString("sub-ass-hinting", "none")
                        view.mpv.setPropertyString("sub-ass-shaper", "complex")
                        view.mpv.setPropertyString("sub-ass-vsfilter-aspect-compat", "yes")
                        view.mpv.setPropertyString("sub-ass-vsfilter-blur-compat", "yes")
                        view.mpv.setPropertyString("sub-ass-vsfilter-color-compat", "full")
                        view.mpv.setPropertyString("sub-fix-timing", "no")
                        view.mpv.setPropertyString("sub-auto", "all")
                        view.mpv.setPropertyBoolean("embeddedfonts", true)
                        // Global fallback font: our bundled multi-script font, not a
                        // generic name. This is the font mpv/libass uses for any
                        // subtitle text that doesn't have its own working font (this
                        // covers SRT/VTT/etc too, since they're rendered through the
                        // same ASS pipeline internally) — fixes "tofu" boxes for
                        // Hindi/Devanagari and other non-Latin scripts.
                        view.mpv.setPropertyString("sub-font", SubtitleFontManager.BUNDLED_FALLBACK_FONT_FAMILY)
                        view.mpv.setPropertyString("sub-fonts-dir", fontsDir.absolutePath)
                    } catch (_: Throwable) {}

                    if (subtitlesLoadedOnce) {
                        // Keep the same subtitle track alive and ask libass/mpv to reload its
                        // file in place. Removing and re-adding the track caused visible subtitle
                        // flicker and could momentarily disturb the playback pipeline.
                        try {
                            markSubtitleReload(); view.mpv.command("sub-reload")
                        } catch (_: Throwable) {
                            // Last-resort fallback: add/select the same file without touching the video.
                            try {
                                view.mpv.command("sub-add", subFile.absolutePath, "select")
                            } catch (_: Throwable) {}
                        }
                    } else {
                        try {
                            view.mpv.command("sub-add", subFile.absolutePath, "select")
                            subtitlesLoadedOnce = true
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                    }
                    try {
                        view.mpv.setPropertyBoolean("sub-visibility", true)
                    } catch (_: Throwable) {}
                }
            } else {
                if (subtitlesLoadedOnce) {
                    try {
                        view.mpv.command("sub-remove")
                    } catch (_: Throwable) {}
                    subtitlesLoadedOnce = false
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        _isPlaying.value = true
        try {
            mpvView?.mpv?.setPropertyBoolean("pause", false)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun pause() {
        _isPlaying.value = false
        try {
            mpvView?.mpv?.setPropertyBoolean("pause", true)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun setPreciseSeekingEnabled(enabled: Boolean) {
        preciseSeekingEnabled = enabled
    }

    fun seekTo(timeMs: Long) {
        try {
            // Seeking away from the very end: with keep-open mpv sits paused at EOF, so the video
            // must be un-paused again or "drag the slider back and play" appears to do nothing.
            val wasAtEof = _eofReached.value || (try { mpvView?.mpv?.getPropertyBoolean("eof-reached") } catch (_: Throwable) { null } == true)
            _eofReached.value = false
            val sec = timeMs / 1000.0
            val mode = if (preciseSeekingEnabled) "absolute+exact" else "absolute"
            mpvView?.mpv?.command("seek", "$sec", mode)
            _currentPositionMs.value = timeMs
            if (wasAtEof) {
                mpvView?.mpv?.setPropertyBoolean("pause", false)
                _isPlaying.value = true
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun stepFrame(forward: Boolean) {
        try {
            mpvView?.mpv?.setPropertyBoolean("pause", true)
            _isPlaying.value = false
            if (forward) {
                mpvView?.mpv?.command("frame-step")
            } else {
                mpvView?.mpv?.command("frame-back-step")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private var currentVolumeLevel: Int = 100
    private var currentAudioChannelMode: AudioChannelMode = AudioChannelMode.AUTO_SAFE
    private var currentVolumeNormalization: Boolean = false
    private var currentDynamicRangeCompression: Boolean = false
    private var currentVoiceEnhance: Boolean = false
    private var currentAudioSurroundMode: AudioSurroundMode = AudioSurroundMode.OFF
    private var currentEqualizerEnabled: Boolean = false
    private var currentEq60Hz: Float = 0f
    private var currentEq230Hz: Float = 0f
    private var currentEq910Hz: Float = 0f
    private var currentEq3600Hz: Float = 0f
    private var currentEq14000Hz: Float = 0f
    private var currentVolumeBoostDb: Float = 0f
    private var lastAppliedAudioFilters: String = ""
    private var audioFilterUpdateJob: kotlinx.coroutines.Job? = null

    private fun updateAudioFilters(immediate: Boolean = false) {
        try {
            val view = mpvView ?: return
            
            val filterList = mutableListOf<String>()

            // 1. Audio Channel Mode routing filter (pan - zero device teardown, zero dropout)
            when (currentAudioChannelMode) {
                AudioChannelMode.MONO -> filterList.add("pan=stereo|c0=0.5*c0+0.5*c1|c1=0.5*c0+0.5*c1")
                AudioChannelMode.LEFT -> filterList.add("pan=stereo|c0=c0|c1=c0")
                AudioChannelMode.RIGHT -> filterList.add("pan=stereo|c0=c1|c1=c1")
                AudioChannelMode.REVERSED_STEREO -> filterList.add("pan=stereo|c0=c1|c1=c0")
                else -> {}
            }

            // 2. Real-Time 5-Band Equalizer (60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz)
            if (currentEqualizerEnabled) {
                val hasNonZeroGain = kotlin.math.abs(currentEq60Hz) > 0.05f ||
                        kotlin.math.abs(currentEq230Hz) > 0.05f ||
                        kotlin.math.abs(currentEq910Hz) > 0.05f ||
                        kotlin.math.abs(currentEq3600Hz) > 0.05f ||
                        kotlin.math.abs(currentEq14000Hz) > 0.05f
                if (hasNonZeroGain) {
                    val eqFilter = "lavfi=[equalizer=f=60:t=q:w=1.0:g=%.2f,equalizer=f=230:t=q:w=1.0:g=%.2f,equalizer=f=910:t=q:w=1.0:g=%.2f,equalizer=f=3600:t=q:w=1.0:g=%.2f,equalizer=f=14000:t=q:w=1.0:g=%.2f]".format(
                        java.util.Locale.US,
                        currentEq60Hz,
                        currentEq230Hz,
                        currentEq910Hz,
                        currentEq3600Hz,
                        currentEq14000Hz
                    )
                    filterList.add(eqFilter)
                }
            }

            // 3. Real-Time Volume Boost (0 dB to +10 dB)
            if (currentVolumeBoostDb > 0.05f) {
                val boostFilter = "lavfi=[volume=volume=+%.2fdB]".format(java.util.Locale.US, currentVolumeBoostDb)
                filterList.add(boostFilter)
            }

            // 4. Voice Enhancement (dialogue clarity filter)
            if (currentVoiceEnhance) {
                filterList.add("lavfi=[equalizer=f=3000:t=q:w=1.5:g=5.0,highpass=f=100,lowpass=f=10000]")
            }

            // 5. Volume Normalization (low-latency dynaudnorm)
            if (currentVolumeNormalization) {
                // Low-latency dynamic normalizer (f=12 frames = ~50ms lookahead vs 1750ms of f=75)
                filterList.add("lavfi=[dynaudnorm=f=12:g=7:p=0.95:m=10.0]")
            }

            // 6. Dynamic Range Compression (DRC - responsive acompressor)
            if (currentDynamicRangeCompression) {
                filterList.add("lavfi=[acompressor=threshold=-20dB:ratio=4:attack=15:release=150:makeup=2dB]")
            }

            // 7. Surround / Spatial Sound Filter
            if (currentAudioSurroundMode != AudioSurroundMode.OFF && currentAudioSurroundMode.filterString.isNotBlank()) {
                filterList.add(currentAudioSurroundMode.filterString)
            }

            // 8. Peak Limiter / Soft-Clipping protection (asc=false for instant zero-latency processing)
            val hasBoostOrHighEq = currentVolumeLevel > 100 || currentVolumeBoostDb > 0.05f || (currentEqualizerEnabled && (currentEq60Hz > 0.5f || currentEq230Hz > 0.5f || currentEq910Hz > 0.5f || currentEq3600Hz > 0.5f || currentEq14000Hz > 0.5f))
            if (hasBoostOrHighEq) {
                filterList.add("lavfi=[alimiter=limit=0.98:attack=5:release=50:asc=false]")
            }

            val afString = filterList.joinToString(",")
            if (afString == lastAppliedAudioFilters) {
                return
            }

            fun applyFilter() {
                try {
                    if (isReleased) return
                    lastAppliedAudioFilters = afString
                    view.mpv.setPropertyString("af", afString)
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }

            if (immediate) {
                audioFilterUpdateJob?.cancel()
                audioFilterUpdateJob = playerScope.launch(Dispatchers.Default) {
                    applyFilter()
                }
            } else {
                audioFilterUpdateJob?.cancel()
                audioFilterUpdateJob = playerScope.launch(Dispatchers.Default) {
                    delay(25) // Smooth 25ms micro-throttle during continuous slider dragging
                    applyFilter()
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    /**
     * Real-time Equalizer and Volume Boost configuration.
     * Integrates seamlessly with MPV audio filter chain.
     */
    fun setEqualizer(
        enabled: Boolean,
        eq60Hz: Float,
        eq230Hz: Float,
        eq910Hz: Float,
        eq3600Hz: Float,
        eq14000Hz: Float,
        volumeBoostDb: Float,
        immediate: Boolean = false
    ) {
        currentEqualizerEnabled = enabled
        currentEq60Hz = eq60Hz.coerceIn(-12f, 12f)
        currentEq230Hz = eq230Hz.coerceIn(-12f, 12f)
        currentEq910Hz = eq910Hz.coerceIn(-12f, 12f)
        currentEq3600Hz = eq3600Hz.coerceIn(-12f, 12f)
        currentEq14000Hz = eq14000Hz.coerceIn(-12f, 12f)
        currentVolumeBoostDb = volumeBoostDb.coerceIn(0f, 10f)
        updateAudioFilters(immediate = immediate)
    }

    // ==========================================
    // REAL-TIME VIDEO FILTERS & MANUAL ADJUSTMENTS
    // ==========================================
    private var currentVideoFilterPreset: VideoFilterPreset = VideoFilterPreset.NONE
    private var currentManualVideoAdjustments: ManualVideoAdjustments = ManualVideoAdjustments()
    private var lastAppliedVfVideoFilter: String = ""
    private var videoAdjustmentDebounceJob: kotlinx.coroutines.Job? = null

    // Geometric video transform (flip / mirror). This lives in the controller — not only in
    // Compose UI state — because the video is drawn by mpv onto a native SurfaceView, which
    // ignores Compose graphicsLayer rotations. The transform is applied inside mpv's own `vf`
    // chain, so it affects the decoded frames only (audio, subtitles, resolution, aspect ratio
    // and seek/pause state are untouched) and survives seek, pause/play and fullscreen/orientation
    // changes because the mpv filter chain is not tied to the surface.
    private var videoFlipVertical: Boolean = false
    private var videoMirrorHorizontal: Boolean = false

    /**
     * Applies vertical flip and/or horizontal mirror to the actual rendered video frames.
     * Safe to call before the view is attached: the values are stored and applied as soon as a
     * file is loaded (see loadVideoInternal -> applyVideoAdjustments).
     */
    fun setVideoTransform(flipVertical: Boolean, mirrorHorizontal: Boolean) {
        videoFlipVertical = flipVertical
        videoMirrorHorizontal = mirrorHorizontal
        applyVideoAdjustments(immediate = true)
    }

    /**
     * Direct `mediacodec` hands opaque hardware surfaces to the renderer, which mpv's software
     * `vf` filters (hflip / vflip / colorbalance) cannot process. While any `vf` filter is
     * active, a forced direct MediaCodec mode is therefore swapped for `mediacodec-copy`
     * (still hardware decoding, with frames copied back so filters can run). All other modes
     * are returned unchanged.
     */
    private fun resolveHwdecValue(): String =
        if (currentHwMode == HwAccelMode.FORCE && lastAppliedVfVideoFilter.isNotEmpty()) {
            HwAccelMode.PREFER.mpvValue
        } else {
            currentHwMode.mpvValue
        }

    /**
     * Real-time Video Filter Preset and Fine Manual Adjustments.
     * Integrates with MPV hardware/GPU rendering properties (brightness, contrast, saturation, gamma, hue, sharpen, deband)
     * and temperature/tint color balance video filters.
     */
    fun setVideoFilterAndAdjustments(
        preset: VideoFilterPreset,
        manual: ManualVideoAdjustments,
        immediate: Boolean = false
    ) {
        currentVideoFilterPreset = preset
        currentManualVideoAdjustments = manual
        applyVideoAdjustments(immediate = immediate)
    }

    private fun applyVideoAdjustments(immediate: Boolean = false) {
        try {
            val view = mpvView ?: return

            val totalBrightness = (currentVideoFilterPreset.brightness + currentManualVideoAdjustments.brightness).coerceIn(-100, 100)
            val totalContrast = (currentVideoFilterPreset.contrast + currentManualVideoAdjustments.contrast).coerceIn(-100, 100)
            val totalSaturation = (currentVideoFilterPreset.saturation + currentManualVideoAdjustments.saturation).coerceIn(-100, 100)
            val totalGamma = (currentVideoFilterPreset.gamma + currentManualVideoAdjustments.gamma).coerceIn(-100, 100)
            val totalHue = (currentVideoFilterPreset.hue + currentManualVideoAdjustments.hue).coerceIn(-100, 100)
            val totalSharpen = (currentVideoFilterPreset.sharpness + currentManualVideoAdjustments.sharpness).coerceIn(0, 100)
            val totalDeband = currentVideoFilterPreset.deband || currentManualVideoAdjustments.deband

            // 1. Direct Hardware/GPU Video Properties (Instant, 0-cost, no reload)
            try {
                view.mpv.setPropertyInt("brightness", totalBrightness)
                view.mpv.setPropertyInt("contrast", totalContrast)
                view.mpv.setPropertyInt("saturation", totalSaturation)
                view.mpv.setPropertyInt("gamma", totalGamma)
                view.mpv.setPropertyInt("hue", totalHue)
                view.mpv.setPropertyBoolean("deband", totalDeband)
                // Sharpen scale in MPV (0.0 to 5.0)
                view.mpv.setPropertyDouble("sharpen", totalSharpen.toDouble() / 20.0)
            } catch (t: Throwable) {
                t.printStackTrace()
            }

            // 2. Temperature & Tint Video Filter (colorbalance vf)
            val totalTemperature = (currentVideoFilterPreset.temperature + currentManualVideoAdjustments.temperature).coerceIn(-100, 100)
            val totalTint = (currentVideoFilterPreset.tint + currentManualVideoAdjustments.tint).coerceIn(-100, 100)

            // All video filters are combined into ONE lavfi graph because `vf` is set as a whole:
            // setting it for flips alone would wipe the colour-balance filter and vice versa.
            val vfParts = mutableListOf<String>()
            if (videoMirrorHorizontal) vfParts.add("hflip")
            if (videoFlipVertical) vfParts.add("vflip")
            if (totalTemperature != 0 || totalTint != 0) {
                val rs = (totalTemperature * 0.003 + totalTint * 0.0015).coerceIn(-1.0, 1.0)
                val gs = (-totalTint * 0.003).coerceIn(-1.0, 1.0)
                val bs = (-totalTemperature * 0.003 + totalTint * 0.0015).coerceIn(-1.0, 1.0)
                val rsStr = String.format(java.util.Locale.US, "%.3f", rs)
                val gsStr = String.format(java.util.Locale.US, "%.3f", gs)
                val bsStr = String.format(java.util.Locale.US, "%.3f", bs)
                vfParts.add("colorbalance=rs=$rsStr:gs=$gsStr:bs=$bsStr:rm=$rsStr:gm=$gsStr:bm=$bsStr:rh=$rsStr:gh=$gsStr:bh=$bsStr")
            }
            val vfFilterString = if (vfParts.isEmpty()) "" else "lavfi=[${vfParts.joinToString(",")}]"

            if (vfFilterString == lastAppliedVfVideoFilter) {
                return
            }

            fun applyVf() {
                try {
                    val enablingFilters = vfFilterString.isNotEmpty()
                    lastAppliedVfVideoFilter = vfFilterString
                    // Switch to a filter-capable decoder path BEFORE installing the filter chain,
                    // and back to the user's chosen mode only AFTER the chain has been cleared.
                    if (enablingFilters) syncHwdecWithVideoFilters(view)
                    view.mpv.setPropertyString("vf", vfFilterString)
                    if (!enablingFilters) syncHwdecWithVideoFilters(view)
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }

            if (immediate) {
                videoAdjustmentDebounceJob?.cancel()
                videoAdjustmentDebounceJob = null
                applyVf()
            } else {
                videoAdjustmentDebounceJob?.cancel()
                val scope = controllerScope ?: kotlinx.coroutines.GlobalScope
                videoAdjustmentDebounceJob = scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    kotlinx.coroutines.delay(30)
                    applyVf()
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun syncHwdecWithVideoFilters(view: VideoPlayerMpvView) {
        val target = resolveHwdecValue()
        // Only touch the property when it really differs: every hwdec change re-initialises the decoder.
        if (view.mpv.getPropertyString("hwdec") != target) {
            view.mpv.setPropertyString("hwdec", target)
        }
    }

    fun setVolume(volume: Int) {
        try {
            val clamped = volume.coerceIn(0, 200)
            val wasBoosted = currentVolumeLevel > 100
            val isBoosted = clamped > 100
            currentVolumeLevel = clamped
            playerScope.launch(Dispatchers.Default) {
                try {
                    mpvView?.mpv?.setPropertyString("volume-max", "200")
                    mpvView?.mpv?.setPropertyInt("volume", clamped)
                    if (wasBoosted != isBoosted) {
                        updateAudioFilters(immediate = true)
                    }
                } catch (_: Throwable) {}
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun toggleMute() {
        try {
            val nextMuted = !_isMuted.value
            _isMuted.value = nextMuted
            playerScope.launch(Dispatchers.Default) {
                try {
                    mpvView?.mpv?.setPropertyBoolean("mute", nextMuted)
                } catch (_: Throwable) {}
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun cycleSpeed() {
        try {
            val current = _playbackSpeed.value
            val next = when {
                current < 0.75 -> 1.0
                current < 1.1 -> 1.25
                current < 1.3 -> 1.5
                current < 1.6 -> 2.0
                current < 2.1 -> 0.5
                else -> 1.0
            }
            _playbackSpeed.value = next
            playerScope.launch(Dispatchers.Default) {
                try {
                    mpvView?.mpv?.setPropertyDouble("speed", next)
                } catch (_: Throwable) {}
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun setPlaybackSpeed(speed: Double) {
        try {
            val clamped = speed.coerceIn(0.25, 4.0)
            _playbackSpeed.value = clamped
            playerScope.launch(Dispatchers.Default) {
                try {
                    mpvView?.mpv?.setPropertyDouble("speed", clamped)
                } catch (_: Throwable) {}
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private var currentAudioPitchCorrection: Boolean = true

    fun setAudioPitchCorrection(enabled: Boolean) {
        try {
            currentAudioPitchCorrection = enabled
            playerScope.launch(Dispatchers.Default) {
                try {
                    val v = mpvView?.mpv
                    v?.setPropertyBoolean("audio-pitch-correction", enabled)
                    v?.setPropertyString("audio-pitch-correction", if (enabled) "yes" else "no")
                } catch (_: Throwable) {}
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun setAudioChannelMode(mode: AudioChannelMode) {
        try {
            currentAudioChannelMode = mode
            updateAudioFilters(immediate = true)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun setVolumeNormalization(enabled: Boolean) {
        try {
            currentVolumeNormalization = enabled
            updateAudioFilters(immediate = true)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun setDynamicRangeCompression(enabled: Boolean) {
        try {
            currentDynamicRangeCompression = enabled
            updateAudioFilters(immediate = true)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun setVoiceEnhancement(enabled: Boolean) {
        try {
            currentVoiceEnhance = enabled
            updateAudioFilters(immediate = true)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun setAudioSurroundMode(mode: AudioSurroundMode) {
        try {
            currentAudioSurroundMode = mode
            updateAudioFilters(immediate = true)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun applyPreferredAudioLanguages(preferredLanguages: String, manualOverride: Boolean = false) {
        try {
            if (manualOverride) return
            val tracks = _audioTracks.value
            if (tracks.isEmpty()) return
            val bestTrack = AudioLanguageMatcher.findBestAudioTrack(tracks, preferredLanguages)
            if (bestTrack != null && !bestTrack.isSelected) {
                setAudioTrack(bestTrack.id)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun toggleLoop() {
        try {
            val nextLoop = !_isLooping.value
            mpvView?.mpv?.setPropertyString("loop-file", if (nextLoop) "inf" else "no")
            _isLooping.value = nextLoop
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun extractRealTracksFromFile(filePath: String) {
        try {
            val extractor = android.media.MediaExtractor()
            if (filePath.startsWith("fd://")) {
                val fdInt = filePath.removePrefix("fd://").toIntOrNull()
                if (fdInt != null) {
                    val pfd = ParcelFileDescriptor.fromFd(fdInt)
                    extractor.setDataSource(pfd.fileDescriptor)
                } else {
                    return
                }
            } else {
                extractor.setDataSource(filePath)
            }
            val audioList = mutableListOf<PlayerMediaTrack>()
            val subList = mutableListOf<PlayerMediaTrack>()
            var audioIndex = 1
            var subIndex = 1

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                val lang = if (format.containsKey(android.media.MediaFormat.KEY_LANGUAGE)) {
                    format.getString(android.media.MediaFormat.KEY_LANGUAGE) ?: ""
                } else ""

                if (mime.startsWith("audio/")) {
                    val channels = if (format.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT) else 2
                    val codecName = mime.removePrefix("audio/").uppercase()
                    val langStr = if (lang.isNotBlank()) Locale(lang).displayLanguage else ""
                    val title = buildString {
                        append("Audio $audioIndex")
                        if (langStr.isNotBlank()) append(" • $langStr")
                        append(" [$codecName ${channels}ch]")
                    }
                    audioList.add(
                        PlayerMediaTrack(
                            id = audioIndex,
                            type = "audio",
                            title = title,
                            language = lang,
                            codec = codecName,
                            isSelected = (audioIndex == 1)
                        )
                    )
                    audioIndex++
                } else if (mime.startsWith("text/") || mime.contains("sub") || mime.contains("ass") || mime.contains("vtt")) {
                    val codecName = mime.substringAfter("/").uppercase()
                    val langStr = if (lang.isNotBlank()) Locale(lang).displayLanguage else ""
                    val title = buildString {
                        append("Subtitle $subIndex")
                        if (langStr.isNotBlank()) append(" • $langStr")
                        append(" [$codecName]")
                    }
                    subList.add(
                        PlayerMediaTrack(
                            id = subIndex,
                            type = "sub",
                            title = title,
                            language = lang,
                            codec = codecName,
                            isSelected = false
                        )
                    )
                    subIndex++
                }
            }
            extractor.release()

            // Also check for external subtitle files in same folder
            try {
                val videoFile = File(filePath)
                val parentDir = videoFile.parentFile
                if (parentDir != null && parentDir.exists()) {
                    val baseName = videoFile.nameWithoutExtension.lowercase()
                    val subExtensions = listOf(".srt", ".ass", ".ssa", ".vtt", ".ttml", ".xml", ".dfxp", ".smi", ".mpl2", ".sub", ".idx", ".sup", ".stl")
                    parentDir.listFiles()?.forEach { file ->
                        if (file.isFile && subExtensions.any { file.name.lowercase().endsWith(it) }) {
                            val ext = file.extension.uppercase()
                            subList.add(
                                PlayerMediaTrack(
                                    id = 100 + subList.size,
                                    type = "sub",
                                    title = file.name,
                                    language = "",
                                    codec = ext,
                                    isSelected = false,
                                    isExternal = true,
                                    originalFilename = file.name
                                )
                            )
                        }
                    }
                }
            } catch (_: Throwable) {}

            if (audioList.isNotEmpty()) _audioTracks.value = audioList
            if (subList.isNotEmpty()) _subtitleTracks.value = subList
        } catch (_: Throwable) {}
    }

    private var isSubtitleExplicitlyDisabled: Boolean = false

    fun refreshTracksAndChapters() {
        val view = mpvView ?: return
        // mpv can drop per-file delays while a file (re)loads; the user's dialled-in values win.
        try {
            val liveAudio = view.mpv.getPropertyDouble("audio-delay")
            if (liveAudio != null && kotlin.math.abs(liveAudio * 1000.0 - currentAudioDelayMs) > 1.0) {
                view.mpv.setPropertyDouble("audio-delay", currentAudioDelayMs / 1000.0)
            }
            val liveSub = view.mpv.getPropertyDouble("sub-delay")
            if (liveSub != null && kotlin.math.abs(liveSub * 1000.0 - currentSubtitleDelayMs) > 1.0) {
                view.mpv.setPropertyDouble("sub-delay", currentSubtitleDelayMs / 1000.0)
            }
        } catch (_: Throwable) {}
        try {
            val count = view.mpv.getPropertyInt("track-list/count") ?: 0
            if (count > 0) {
                val audioList = mutableListOf<PlayerMediaTrack>()
                val subList = mutableListOf<PlayerMediaTrack>()
                for (i in 0 until count) {
                    val type = view.mpv.getPropertyString("track-list/$i/type") ?: ""
                    val id = view.mpv.getPropertyInt("track-list/$i/id") ?: (i + 1)
                    val rawTitle = view.mpv.getPropertyString("track-list/$i/title") ?: ""
                    val lang = view.mpv.getPropertyString("track-list/$i/lang") ?: ""
                    val codec = view.mpv.getPropertyString("track-list/$i/codec") ?: ""
                    val selected = view.mpv.getPropertyBoolean("track-list/$i/selected") ?: false
                    val isDefault = view.mpv.getPropertyBoolean("track-list/$i/default") ?: false
                    val isForced = view.mpv.getPropertyBoolean("track-list/$i/forced") ?: false
                    val isHearingImpaired = view.mpv.getPropertyBoolean("track-list/$i/hearing-impaired") ?: false
                    val isVisualImpaired = view.mpv.getPropertyBoolean("track-list/$i/visual-impaired") ?: false
                    val isAudioCommentary = (view.mpv.getPropertyBoolean("track-list/$i/audio-commentary") ?: false) ||
                        rawTitle.contains(Regex("(?i)commentary|commentator|director.?s?\\s+commentary|audio description"))
                    val isExternal = view.mpv.getPropertyBoolean("track-list/$i/external") ?: false
                    val extFilename = view.mpv.getPropertyString("track-list/$i/external-filename")
                        ?: view.mpv.getPropertyString("track-list/$i/filename") ?: ""
                    if (type == "sub") {
                        view.mpv.getPropertyInt("track-list/$i/ff-index")?.let { ffIndex ->
                            if (ffIndex >= 0) subtitleTrackExtractorIndices[id] = ffIndex
                        }
                    }

                    if (type == "sub" && extFilename.isNotBlank()) {
                        val candidate = if (extFilename.startsWith("file://")) Uri.parse(extFilename).path else extFilename
                        if (!candidate.isNullOrBlank() && File(candidate).exists()) {
                            subtitleTrackSourcePaths[id] = candidate
                        }
                    }

                    val isExternalTrack = isExternal || (extFilename.isNotBlank() && (
                        externalSubtitlesMap.containsKey(extFilename) ||
                        externalSubtitlesMap.keys.any { extFilename.contains(File(it).nameWithoutExtension) } ||
                        loadedExternalSubtitles.contains(extFilename)
                    ))

                    val isExternalAudioTrack = isExternal || (extFilename.isNotBlank() && (
                        externalAudiosMap.containsKey(extFilename) ||
                        externalAudiosMap.keys.any { extFilename.contains(File(it).nameWithoutExtension) } ||
                        loadedExternalAudios.contains(extFilename)
                    ))

                    val matchedOriginalName = if (isExternalTrack && type == "sub") {
                        externalSubtitlesMap[extFilename]
                            ?: externalSubtitlesMap.entries.firstOrNull { extFilename.contains(File(it.key).nameWithoutExtension) }?.value
                            ?: if (extFilename.isNotBlank()) File(extFilename).name else rawTitle.ifBlank { "Uploaded Subtitle" }
                    } else ""

                    val matchedOriginalAudioName = if (isExternalAudioTrack && type == "audio") {
                        externalAudiosMap[extFilename]
                            ?: externalAudiosMap.entries.firstOrNull { extFilename.contains(it.key) || it.key.contains(extFilename) }?.value
                            ?: externalAudiosMap.entries.firstOrNull { extFilename.contains(File(it.key).nameWithoutExtension) }?.value
                            ?: if (extFilename.isNotBlank() && !extFilename.startsWith("http")) File(extFilename).name else rawTitle.ifBlank { "Online Audio" }
                    } else ""

                    val matchedAudioLang = if (isExternalAudioTrack && type == "audio") {
                        externalAudiosLangMap[extFilename]
                            ?: externalAudiosLangMap.entries.firstOrNull { extFilename.contains(it.key) || it.key.contains(extFilename) }?.value
                            ?: lang
                    } else lang

                    val cleanedTitle = rawTitle.trim().replace(Regex("^#\\s*\\d*\\s*[:\\-.]?\\s*"), "")
                    val finalTitle = if (isExternalTrack && type == "sub") {
                        matchedOriginalName
                    } else if (isExternalAudioTrack && type == "audio") {
                        matchedOriginalAudioName
                    } else {
                        when {
                            cleanedTitle.isNotBlank() && !cleanedTitle.matches(Regex("(?i)^[A-Z]?\\d+(sec|s)?$")) -> cleanedTitle
                            lang.isNotBlank() -> Locale(lang).displayLanguage.ifBlank { lang.uppercase() }
                            type == "sub" -> "Subtitle $id"
                            else -> "Audio Track $id"
                        }
                    }

                    val logicalActiveSubtitleId = if (lastActiveSubtitleTrackId > 0) {
                        resolveAdvancedAssOriginalId(lastActiveSubtitleTrackId)
                    } else {
                        0
                    }
                    val isTrackSelected = if (type == "sub") {
                        !isSubtitleExplicitlyDisabled && logicalActiveSubtitleId > 0 && id == logicalActiveSubtitleId
                    } else {
                        selected
                    }

                    // For external subtitles MPV may report the codec of Lumora's playback
                    // representation (for example ASS after converting an uploaded SRT). The UI
                    // and subtitle state should expose the ORIGINAL uploaded format instead.
                    val exposedCodec = if (type == "sub" && isExternalTrack && matchedOriginalName.isNotBlank()) {
                        val originalExt = matchedOriginalName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                        if (originalExt in setOf("srt", "ass", "ssa", "vtt", "ttml", "dfxp", "smi", "sub", "lrc", "txt", "sbv", "mpl2", "stl", "sup")) originalExt else codec
                    } else codec

                    val track = PlayerMediaTrack(
                        id = id,
                        type = type,
                        title = finalTitle,
                        language = if (type == "audio") matchedAudioLang else lang,
                        codec = exposedCodec,
                        isSelected = isTrackSelected,
                        isExternal = if (type == "sub") isExternalTrack else if (type == "audio") isExternalAudioTrack else isExternal,
                        originalFilename = if (type == "sub" && isExternalTrack) matchedOriginalName else if (type == "audio" && isExternalAudioTrack) matchedOriginalAudioName else "",
                        isSecondarySelected = (type == "sub" && _secondarySubtitleTrackId.value > 0 && id == _secondarySubtitleTrackId.value),
                        isDefault = isDefault,
                        isForced = isForced,
                        isHearingImpaired = isHearingImpaired,
                        isVisualImpaired = isVisualImpaired,
                        isAudioCommentary = isAudioCommentary
                    )
                    if (type == "audio") audioList.add(track)
                    else if (type == "sub") subList.add(track)
                    else if (type.equals("attachment", ignoreCase = true) || rawTitle.endsWith(".ttf", ignoreCase = true) || rawTitle.endsWith(".otf", ignoreCase = true)) {
                        if (rawTitle.isNotBlank()) SubtitleFontManager.registerVideoEmbeddedFont(rawTitle)
                    }
                }
                if (audioList.isNotEmpty()) _audioTracks.value = audioList
                if (subList.isNotEmpty()) {
                    // Advanced ASS/SSA uses a generated external track as the rendering
                    // implementation of the original logical subtitle. MPV necessarily exposes
                    // both tracks, but the generated one must never become a second user-visible
                    // subtitle entry. Keep the original track as the stable logical identity and
                    // map playback to the derived track internally.
                    val advancedDerivedIds = (
                        advancedAssBindings.values.mapNotNull { it.derivedTrackId.takeIf { id -> id > 0 } } +
                            rawBindings.values.mapNotNull { it.derivedTrackId.takeIf { id -> id > 0 } }
                        ).toSet()

                    // Retain all genuine tracks exposed by MPV preserving unique track ID
                    // identity, except for our private Advanced ASS/SSA rendering copies.
                    val preservedSubList = subList
                        .distinctBy { it.id }
                        .filterNot { it.id in advancedDerivedIds }

                    _subtitleTracks.value = preservedSubList
                    if ((advancedAssEnabled || rawEditorEnabled) && !advancedAssSyncing) scheduleAdvancedAssSync()
                    val currentSid = view.mpv.getPropertyString("sid")
                    val isNoneSelected = preservedSubList.none { it.isSelected } || currentSid == null || currentSid == "no" || currentSid == "0"
                    // Remember which track the user has selected in THIS video (id may change later).
                    preservedSubList.firstOrNull { it.isSelected }?.let { userSubtitleIdentity = subtitleIdentity(it) }
                    val reloading = System.currentTimeMillis() < subtitleReloadUntilMs
                    val ownSelection = if (isNoneSelected && !isSubtitleExplicitlyDisabled) findUserSelectedSubtitle(preservedSubList) else null
                    if (isNoneSelected && reloading && lastActiveSubtitleTrackId > 0) {
                        // Mid sub-reload: do nothing, the next refresh sees the re-added track.
                    } else if (ownSelection != null && ownSelection.id > 0) {
                        // The user's own subtitle lost its selection (reload / id change): put it
                        // back instead of guessing another track, and keep Override as it is.
                        try {
                            lastActiveSubtitleTrackId = ownSelection.id
                            view.mpv.setPropertyInt("sid", resolveAdvancedAssPlaybackId(ownSelection.id))
                            view.mpv.setPropertyBoolean("sub-visibility", true)
                            val secId = _secondarySubtitleTrackId.value
                            _subtitleTracks.value = preservedSubList.map {
                                it.copy(
                                    isSelected = (it.id == ownSelection.id),
                                    isSecondarySelected = (secId > 0 && it.id == secId)
                                )
                            }
                        } catch (_: Throwable) {}
                    } else if (isNoneSelected && !isSubtitleExplicitlyDisabled && !holdSubtitleAutoSelect && lastActiveSubtitleTrackId <= 0) {
                        val matched = SubtitleSessionMemory.findMatchingTrackForNextEpisode(preservedSubList)
                        if (matched != null && matched.id > 0) {
                            val chosenSubId = matched.id
                            try {
                                view.mpv.setPropertyInt("sid", chosenSubId)
                                view.mpv.setPropertyBoolean("sub-visibility", true)
                                view.mpv.setPropertyString("sub-ass", "yes")
                                lastActiveSubtitleTrackId = chosenSubId
                                val secId = _secondarySubtitleTrackId.value
                                // Update the selection first so Override ASS/SSA (and the saved
                                // Typography/Colors) get recomputed for THIS episode's track
                                // format, instead of carrying over whatever the previous episode
                                // last applied - this is the "next video mein bhi apply ho" case.
                                _subtitleTracks.value = preservedSubList.map {
                                    it.copy(
                                        isSelected = (it.id == chosenSubId),
                                        isSecondarySelected = (secId > 0 && it.id == secId)
                                    )
                                }
                                // This track was never explicitly picked by the user FOR THIS
                                // VIDEO - it's only a best-effort guess matched against whatever
                                // the previous episode had selected. "Override ASS/SSA Styles"
                                // must NOT silently carry over ON to a subtitle the user hasn't
                                // reviewed yet: force it back OFF (original embedded styling)
                                // here, and tell the UI so the style panel switch and the
                                // persisted setting follow. The user re-enables it explicitly, per
                                // episode, from the style panel when they actually want it.
                                if (subtitleOverrideAssSsaUserPref) {
                                    subtitleOverrideAssSsaUserPref = false
                                    _overrideAssSsaAutoResetEvent.value = _overrideAssSsaAutoResetEvent.value + 1
                                }
                                reapplyAppearanceForTrack(view, chosenSubId)
                                // Without this, mpv keeps rendering whatever it already had
                                // buffered for the subtitle stream at the time "sid" was set here
                                // (often nothing yet, since this fires the instant tracks are
                                // discovered) and never restarts decoding for [chosenSubId] on its
                                // own - the track shows as selected in the UI but no text appears
                                // until the user manually deselects and reselects it (which works
                                // only because that manual path already issues this same reload).
                                try {
                                    markSubtitleReload(); view.mpv.command("sub-reload")
                                } catch (_: Throwable) {}
                            } catch (_: Throwable) {}
                        }
                    }
                }
            }

            val chapCount = view.mpv.getPropertyInt("chapter-list/count") ?: 0
            if (chapCount > 0) {
                val list = mutableListOf<PlayerVideoChapter>()
                for (i in 0 until chapCount) {
                    val rawTitle = view.mpv.getPropertyString("chapter-list/$i/title") ?: ""
                    var cleanTitle = rawTitle.trim().replace(Regex("^#\\s*\\d*\\s*[:\\-.]?\\s*"), "")
                    if (cleanTitle.matches(Regex("(?i)^[A-Z]?\\d+(sec|s)?$")) || cleanTitle.matches(Regex("(?i)^ch(apter)?_?\\d+$")) || cleanTitle.isBlank()) {
                        cleanTitle = "Chapter ${i + 1}"
                    }
                    val timeSec = view.mpv.getPropertyDouble("chapter-list/$i/time") ?: 0.0
                    val ms = (timeSec * 1000.0).toLong()
                    list.add(
                        PlayerVideoChapter(
                            index = i,
                            title = cleanTitle,
                            timeMs = ms,
                            formattedTime = formatPlaybackTime(ms)
                        )
                    )
                }
                _chapters.value = list
                return
            } else {
                _chapters.value = emptyList()
            }
        } catch (_: Throwable) {
            _chapters.value = emptyList()
        }
    }

    fun applySubtitleSettings(
        autoLoadExternal: Boolean,
        overrideAssSsa: Boolean,
        scaleWithWindow: Boolean,
        fontDirectory: String? = null,
        fontFamily: String? = null
    ) {
        subtitleAutoLoadExternal = autoLoadExternal
        // Always keep the raw toggle current, independent of which track/format is active right
        // now - see [subtitleOverrideAssSsaUserPref].
        subtitleOverrideAssSsaUserPref = overrideAssSsa
        try {
            mpvView?.mpv?.setPropertyString("sub-auto", if (autoLoadExternal) "exact" else "no")
        } catch (_: Throwable) {}

        // If a full appearance (colours / font / borders / bold / italic ...) has already been
        // pushed at least once this session, REPLAY it through applySubtitleAppearance() below
        // instead of poking a handful of raw mpv properties here directly.
        //
        // This function used to always stamp sub-ass-force-style="" on every call, which
        // silently erased the FontName/Bold/Colour force-style block that
        // applySubtitleAppearance() had just built for the user's current selection. Because
        // VideoPlayerScreen reacts to two independent state sources (the live style-editor
        // state, and the persisted PlayerSettings) with two separate LaunchedEffects — one
        // calling applySubtitleAppearance(), the other calling this function — whichever of the
        // two happened to run last silently decided the real on-screen font/style, which is why
        // a freshly-picked font (or colour, or border) could appear "selected" in the UI but not
        // actually show up on the video. Routing everything through the single
        // applySubtitleAppearance() entry point keeps the two in sync no matter which one fires.
        val previous = lastAppearanceParams
        if (previous != null) {
            applySubtitleAppearance(
                bold = previous.bold,
                italic = previous.italic,
                underline = previous.underline,
                alignment = previous.alignment,
                fontSize = previous.fontSize,
                borderStyle = previous.borderStyle,
                borderSize = previous.borderSize,
                shadowOffset = previous.shadowOffset,
                shadowBlur = previous.shadowBlur,
                backgroundPadding = previous.backgroundPadding,
                textColorArgb = previous.textColorArgb,
                borderColorArgb = previous.borderColorArgb,
                backgroundColorArgb = previous.backgroundColorArgb,
                shadowColorArgb = previous.shadowColorArgb,
                overrideAssSsa = overrideAssSsa,
                scaleWithWindow = scaleWithWindow,
                scale = previous.scale,
                position = previous.position,
                blendWithVideo = previous.blendWithVideo,
                fontDirectory = fontDirectory ?: previous.fontDirectory,
                fontFamily = fontFamily ?: previous.fontFamily,
                letterSpacing = previous.letterSpacing,
                shadowOffsetX = previous.shadowOffsetX,
                shadowOffsetY = previous.shadowOffsetY
            )
            return
        }

        // No full appearance has been applied yet this session (this is the very first call,
        // made at player startup before the style editor has ever run) — fall back to a minimal
        // property set so subtitles still render sensibly before the user opens any style UI.
        // Non-ASS/SSA text subtitles (SRT/VTT/SAMI/... converted to ASS purely for mpv
        // rendering) should always honour the user's saved style, so only gate on the toggle
        // when the CURRENT track is a genuinely native ASS/SSA subtitle.
        val isNativeAssSsa = isCurrentSubtitleNativeAssSsa()
        val effectiveOverrideAss = if (isNativeAssSsa) (overrideAssSsa && !advancedAssGoverning) else true
        subtitleOverrideAssSsa = effectiveOverrideAss
        subtitleScaleWithWindow = scaleWithWindow
        subtitleFontDirectory = fontDirectory?.takeIf { it.isNotBlank() }
        subtitleFontFamily = fontFamily?.takeIf { it.isNotBlank() }
        try {
            val view = mpvView ?: return
            view.mpv.setPropertyString("sub-ass-override", if (effectiveOverrideAss) "force" else "no")
            view.mpv.setPropertyBoolean("sub-ass-scale-with-window", false)
            view.mpv.setPropertyBoolean("sub-ass-force-margins", false)
            view.mpv.setPropertyString("sub-scale-with-window", "yes")
            view.mpv.setPropertyDouble("sub-scale", if (effectiveOverrideAss) subtitleScale else 1.0)
            view.mpv.setPropertyDouble("sub-pos", if (effectiveOverrideAss) subtitlePosition else 100.0)
            if (subtitleFontDirectory != null && subtitleFontDirectory != lastAppliedFontDirectory) {
                lastAppliedFontDirectory = subtitleFontDirectory
                view.mpv.setPropertyString("sub-fonts-dir", subtitleFontDirectory!!)
            }
            view.mpv.setPropertyString(
                "sub-font",
                subtitleFontFamily?.takeIf { it.isNotBlank() } ?: SubtitleFontManager.BUNDLED_FALLBACK_FONT_FAMILY
            )
        } catch (_: Throwable) {}
        onSelectedSubtitleFontChanged(subtitleFontFamily)
        nudgeRedrawIfPaused()
    }

    /**
     * Whether the CURRENTLY SELECTED subtitle track is a genuinely native ASS/SSA subtitle,
     * as opposed to another text format (SRT/VTT/SAMI/MicroDVD/...) that UniversalSubtitleEngine
     * converted to an .ass file purely so libass can render it. For external tracks the original
     * file extension (always known, even after conversion) is authoritative; for embedded tracks
     * we fall back to the container-reported codec.
     */
    private fun isCurrentSubtitleNativeAssSsa(): Boolean {
        val track = _subtitleTracks.value.firstOrNull { it.isSelected } ?: return false
        val ext = track.originalFilename.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext.isNotBlank()) return ext == "ass" || ext == "ssa"
        val codec = track.codec.lowercase(Locale.ROOT)
        return codec == "ass" || codec == "ssa"
    }

    /** Same check as [isCurrentSubtitleNativeAssSsa], but for an arbitrary track id looked up by
     *  id rather than by its (possibly stale) `isSelected` flag. Used right when the user picks a
     *  NEW subtitle track, before [_subtitleTracks] has necessarily been re-flagged. */
    private fun isTrackNativeAssSsa(trackId: Int): Boolean {
        val track = _subtitleTracks.value.firstOrNull { it.id == trackId } ?: return false
        val ext = track.originalFilename.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext.isNotBlank()) return ext == "ass" || ext == "ssa"
        val codec = track.codec.lowercase(Locale.ROOT)
        return codec == "ass" || codec == "ssa"
    }

    /**
     * The correct "Override ASS/SSA" value for [trackId], derived fresh from the user's raw
     * toggle ([subtitleOverrideAssSsaUserPref]) every time - never from whatever the previously
     * selected track happened to leave behind in [subtitleOverrideAssSsa]. Non-ASS/SSA formats
     * (SRT/VTT/SAMI/...) always honour the user's saved style, exactly like
     * [applySubtitleAppearance] and [applySubtitleSettings] already do for the active track.
     */
    private fun effectiveOverrideForTrack(trackId: Int): Boolean {
        return if (isTrackNativeAssSsa(trackId)) (subtitleOverrideAssSsaUserPref && !advancedAssGoverning) else true
    }

    /**
     * mpv/libass only repaints the on-screen subtitle overlay when a new frame is decoded.
     * While the video is PAUSED, changing subtitle style properties (Override ASS/SSA, colours,
     * font, etc.) therefore updates internal state correctly but never becomes visible until the
     * user presses play - or, worse, briefly disappears because the previous force-style was
     * just cleared. A zero-offset, exact relative seek is the standard mpv trick to force a
     * redraw of the CURRENT frame (position and audio are completely unaffected) so the change
     * shows up immediately, exactly like it already does while playing.
     */
    private fun nudgeRedrawIfPaused() {
        try {
            if (!_isPlaying.value) {
                mpvView?.mpv?.command("seek", "0", "relative+exact")
            }
        } catch (_: Throwable) {}
    }

    /**
     * Re-applies the last known Typography/Colors/Miscellaneous appearance so it is correct for
     * [trackId]'s format (native ASS/SSA vs. every other format), instead of reusing whatever
     * [subtitleOverrideAssSsa] was left holding by the PREVIOUSLY selected track - which is what
     * used to make "Override ASS/SSA Styles" (and the saved Typography/Colors values) look like
     * they needed a manual re-toggle after switching tracks or opening a different video. Falls
     * back to patching just the override flag/force-style when no appearance has been pushed yet
     * this session (the very first subtitle selection, before the style editor's LaunchedEffect
     * has had a chance to run). Must be called AFTER [_subtitleTracks] already reflects [trackId]
     * as selected, since [applySubtitleAppearance] itself keys off that.
     */
    private fun reapplyAppearanceForTrack(view: VideoPlayerMpvView, trackId: Int) {
        val p = lastAppearanceParams
        if (p != null) {
            applySubtitleAppearance(
                bold = p.bold, italic = p.italic, underline = p.underline, alignment = p.alignment,
                fontSize = p.fontSize, borderStyle = p.borderStyle, borderSize = p.borderSize,
                shadowOffset = p.shadowOffset, shadowBlur = p.shadowBlur, backgroundPadding = p.backgroundPadding, textColorArgb = p.textColorArgb, borderColorArgb = p.borderColorArgb,
                backgroundColorArgb = p.backgroundColorArgb, shadowColorArgb = p.shadowColorArgb,
                overrideAssSsa = p.overrideAssSsa, scaleWithWindow = p.scaleWithWindow, scale = p.scale,
                position = p.position, blendWithVideo = p.blendWithVideo, fontDirectory = p.fontDirectory,
                fontFamily = p.fontFamily, letterSpacing = p.letterSpacing, shadowOffsetX = p.shadowOffsetX,
                shadowOffsetY = p.shadowOffsetY
            )
        } else {
            subtitleOverrideAssSsa = effectiveOverrideForTrack(trackId)
            try {
                view.mpv.setPropertyString("sub-ass-override", if (subtitleOverrideAssSsa) "force" else "no")
                if (!subtitleOverrideAssSsa) {
                    view.mpv.setPropertyString("sub-ass-force-style", "")
                }
            } catch (_: Throwable) {}
        }
    }

    /** Full snapshot of the last applySubtitleAppearance() call, so other entry points
     *  (applySubtitleSettings) can replay it instead of reconstructing/erasing it themselves. */
    private data class AppearanceParams(
        val bold: Boolean,
        val italic: Boolean,
        val underline: Boolean,
        val alignment: String,
        val fontSize: Float,
        val borderStyle: String,
        val borderSize: Float,
        val shadowOffset: Float,
        val shadowBlur: Float,
        val backgroundPadding: Float,
        val textColorArgb: Int,
        val borderColorArgb: Int,
        val backgroundColorArgb: Int,
        val shadowColorArgb: Int,
        val overrideAssSsa: Boolean,
        val scaleWithWindow: Boolean,
        val scale: Float,
        val position: Float,
        val blendWithVideo: Boolean,
        val fontDirectory: String?,
        val fontFamily: String?,
        val letterSpacing: Float,
        val shadowOffsetX: Float,
        val shadowOffsetY: Float
    )

    private var lastAppearanceParams: AppearanceParams? = null

    // Tracks the last effective native ASS/SSA override mode so a real toggle transition can
    // force libass to reload the script. Without this, mpv can keep the previously rendered
    // override state visible even after sub-ass-override has been changed back to "no".
    private var lastAppliedNativeAssOverride: Boolean? = null

    fun setSubtitleScale(scale: Double) {
        subtitleScale = scale.coerceIn(0.5, 3.0)
        try { mpvView?.mpv?.setPropertyDouble("sub-scale", subtitleScale) } catch (_: Throwable) {}
    }

    fun setSubtitlePosition(position: Double) {
        subtitlePosition = position.coerceIn(0.0, 150.0)
        try { mpvView?.mpv?.setPropertyDouble("sub-pos", subtitlePosition) } catch (_: Throwable) {}
    }

    private fun mpvColorFromArgb(argb: Int): String {
        val a = ((argb ushr 24) and 0xFF) / 255f
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return "${r.coerceIn(0f, 1f)}/${g.coerceIn(0f, 1f)}/${b.coerceIn(0f, 1f)}/${a.coerceIn(0f, 1f)}"
    }

    /**
     * Applies the live subtitle editor settings directly to mpv/libass with
     * instantaneous real-time updates and zero flickering or reload delays.
     */
    fun applySubtitleAppearance(
        bold: Boolean,
        italic: Boolean,
        underline: Boolean = false,
        alignment: String,
        fontSize: Float,
        borderStyle: String,
        borderSize: Float,
        shadowOffset: Float,
        shadowBlur: Float = 0f,
        backgroundPadding: Float = 0f,
        textColorArgb: Int,
        borderColorArgb: Int,
        backgroundColorArgb: Int,
        shadowColorArgb: Int,
        overrideAssSsa: Boolean,
        scaleWithWindow: Boolean,
        scale: Float,
        position: Float,
        blendWithVideo: Boolean,
        fontDirectory: String? = null,
        fontFamily: String? = null,
        letterSpacing: Float = 0f,
        shadowOffsetX: Float = 2f,
        shadowOffsetY: Float = 2f
    ) {
        // Non-ASS/SSA text subtitles (SRT/VTT/SAMI/MicroDVD/... converted to ASS purely so
        // libass can render them) must always honour the user's saved style — the override
        // toggle only makes sense for a genuinely native ASS/SSA track that already carries its
        // own embedded styling to override. Gating ALL formats on the same toggle used to mean
        // a plain SRT file wouldn't pick up colour/size/font changes until the user separately
        // switched on "Override ASS/SSA Styles", which is not what that toggle is supposed to
        // control for non-ASS subtitles.
        // Always keep the raw toggle current, independent of which track/format is active right
        // now - see [subtitleOverrideAssSsaUserPref].
        subtitleOverrideAssSsaUserPref = overrideAssSsa
        val isNativeAssSsa = isCurrentSubtitleNativeAssSsa()
        val effectiveOverrideAss = if (isNativeAssSsa) (overrideAssSsa && !advancedAssGoverning) else true
        lastAppearanceReplay = {
            applySubtitleAppearance(
                bold, italic, underline, alignment, fontSize, borderStyle, borderSize, shadowOffset, shadowBlur, backgroundPadding,
                textColorArgb, borderColorArgb, backgroundColorArgb, shadowColorArgb, overrideAssSsa,
                scaleWithWindow, scale, position, blendWithVideo, fontDirectory, fontFamily,
                letterSpacing, shadowOffsetX, shadowOffsetY
            )
        }
        lastAppearanceParams = AppearanceParams(
            bold = bold, italic = italic, underline = underline, alignment = alignment,
            fontSize = fontSize, borderStyle = borderStyle, borderSize = borderSize,
            shadowOffset = shadowOffset, shadowBlur = shadowBlur, backgroundPadding = backgroundPadding, textColorArgb = textColorArgb, borderColorArgb = borderColorArgb,
            backgroundColorArgb = backgroundColorArgb, shadowColorArgb = shadowColorArgb,
            overrideAssSsa = overrideAssSsa, scaleWithWindow = scaleWithWindow, scale = scale,
            position = position, blendWithVideo = blendWithVideo, fontDirectory = fontDirectory,
            fontFamily = fontFamily, letterSpacing = letterSpacing, shadowOffsetX = shadowOffsetX,
            shadowOffsetY = shadowOffsetY
        )

        subtitleBold = bold
        subtitleItalic = italic
        subtitleUnderline = underline
        subtitleAlignment = alignment
        subtitleFontSize = fontSize.coerceIn(8f, 120f).toDouble()
        subtitleBorderStyle = borderStyle
        subtitleBorderSize = borderSize.coerceIn(0f, 20f).toDouble()
        subtitleShadowOffset = shadowOffset.coerceIn(0f, 20f).toDouble()
        subtitleTextColor = mpvColorFromArgb(textColorArgb)
        subtitleBorderColor = mpvColorFromArgb(borderColorArgb)
        subtitleBackgroundColor = mpvColorFromArgb(backgroundColorArgb)
        subtitleShadowColor = mpvColorFromArgb(shadowColorArgb)
        subtitleOverrideAssSsa = effectiveOverrideAss
        subtitleScaleWithWindow = scaleWithWindow
        subtitleScale = scale.coerceIn(0.5f, 3f).toDouble()
        subtitlePosition = position.coerceIn(0f, 150f).toDouble()
        subtitleBlendWithVideo = blendWithVideo
        subtitleFontDirectory = fontDirectory?.takeIf { it.isNotBlank() }
        subtitleFontFamily = fontFamily?.takeIf { it.isNotBlank() }

        val view = mpvView ?: return
        try {
            // Guarantee subtitle rendering pipeline is active without toggling sid
            if (!isSubtitleExplicitlyDisabled) {
                view.mpv.setPropertyBoolean("sub-visibility", true)
            }
            view.mpv.setPropertyString("sub-ass", "yes")
            // This is a rendering override, never a destructive mutation of embedded ASS/SSA.
            view.mpv.setPropertyString("sub-ass-override", if (effectiveOverrideAss) "force" else "no")
            view.mpv.setPropertyBoolean("embeddedfonts", true)

            // Direct native MPV properties for instantaneous styling
            view.mpv.setPropertyString("sub-bold", if (effectiveOverrideAss && bold) "yes" else "no")
            view.mpv.setPropertyString("sub-italic", if (effectiveOverrideAss && italic) "yes" else "no")
            view.mpv.setPropertyDouble("sub-font-size", if (effectiveOverrideAss) subtitleFontSize else 52.0)
            view.mpv.setPropertyString("sub-align-x", if (effectiveOverrideAss) alignment.lowercase() else "center")
            view.mpv.setPropertyString("sub-scale-with-window", if (effectiveOverrideAss) if (scaleWithWindow) "yes" else "no" else "yes")
            view.mpv.setPropertyDouble("sub-scale", if (effectiveOverrideAss) subtitleScale else 1.0)
            view.mpv.setPropertyDouble("sub-pos", if (effectiveOverrideAss) subtitlePosition else 100.0)
            view.mpv.setPropertyString("sub-color", if (effectiveOverrideAss) subtitleTextColor else "1.0/1.0/1.0/1.0")
            view.mpv.setPropertyString("sub-border-color", if (effectiveOverrideAss) subtitleBorderColor else "0.0/0.0/0.0/1.0")
            view.mpv.setPropertyDouble(
                "sub-border-size",
                if (effectiveOverrideAss && borderStyle != "none" && borderStyle != "shadow") {
                    if (borderStyle == "opaque_box" || borderStyle == "background_box") {
                        maxOf(subtitleBorderSize, backgroundPadding.coerceIn(0f, 40f).toDouble())
                    } else subtitleBorderSize
                } else 0.0
            )
            view.mpv.setPropertyString("sub-shadow-color", if (effectiveOverrideAss) subtitleShadowColor else "0.0/0.0/0.0/0.75")
            view.mpv.setPropertyDouble(
                "sub-shadow-offset",
                if (effectiveOverrideAss && borderStyle != "none" && borderStyle != "outline") subtitleShadowOffset else 0.0
            )
            try {
                view.mpv.setPropertyDouble("sub-blur", if (effectiveOverrideAss) shadowBlur.coerceIn(0f, 20f).toDouble() else 0.0)
            } catch (_: Throwable) {}
            val backColor = if (effectiveOverrideAss && (borderStyle == "opaque_box" || borderStyle == "background_box")) {
                subtitleBackgroundColor
            } else {
                subtitleShadowColor
            }
            view.mpv.setPropertyString("sub-back-color", backColor)
            val mpvBorderStyle = when {
                !effectiveOverrideAss -> "outline-and-shadow"
                borderStyle == "opaque_box" -> "opaque-box"
                borderStyle == "background_box" -> "background-box"
                else -> "outline-and-shadow"
            }
            view.mpv.setPropertyString("sub-border-style", mpvBorderStyle)
            view.mpv.setPropertyString("blend-subtitles", if (effectiveOverrideAss && blendWithVideo) "yes" else "no")
            try {
                view.mpv.setPropertyDouble("sub-spacing", if (effectiveOverrideAss) letterSpacing.toDouble() else 0.0)
            } catch (_: Throwable) {}

            if (fontDirectory != null && fontDirectory.isNotBlank() && fontDirectory != lastAppliedFontDirectory) {
                lastAppliedFontDirectory = fontDirectory
                view.mpv.setPropertyString("sub-fonts-dir", fontDirectory)
            }
            val effectiveFontFamily = if (!fontFamily.isNullOrBlank()) fontFamily else SubtitleFontManager.BUNDLED_FALLBACK_FONT_FAMILY
            view.mpv.setPropertyString("sub-font", effectiveFontFamily)
            onSelectedSubtitleFontChanged(fontFamily)

            // Also configure sub-ass-force-style for fine-grained style override support
            val forceStyles = mutableListOf<String>()
            forceStyles.add("Bold=${if (bold) 1 else 0}")
            forceStyles.add("Italic=${if (italic) 1 else 0}")
            forceStyles.add("Underline=${if (underline) 1 else 0}")
            if (fontSize > 0f) {
                forceStyles.add("FontSize=${fontSize.toInt()}")
            }
            forceStyles.add("FontName=$effectiveFontFamily")
            if (letterSpacing != 0f) {
                forceStyles.add("Spacing=$letterSpacing")
            }
            val assAlign = when (alignment.lowercase()) {
                "left" -> 1
                "right" -> 3
                else -> 2
            }
            forceStyles.add("Alignment=$assAlign")

            // ASS color formatter: &HAABBGGRR with inverted alpha (00=opaque, FF=transparent)
            fun toAssHex(argb: Int): String {
                val aInv = 0xFF - ((argb ushr 24) and 0xFF)
                val r = (argb ushr 16) and 0xFF
                val g = (argb ushr 8) and 0xFF
                val b = argb and 0xFF
                return String.format(java.util.Locale.US, "&H%02X%02X%02X%02X", aInv, b, g, r)
            }

            forceStyles.add("PrimaryColour=${toAssHex(textColorArgb)}")
            forceStyles.add("OutlineColour=${toAssHex(borderColorArgb)}")

            when (borderStyle) {
                "none" -> {
                    forceStyles.add("BorderStyle=1")
                    forceStyles.add("Outline=0")
                    forceStyles.add("Shadow=0")
                    forceStyles.add("BackColour=${toAssHex(shadowColorArgb)}")
                }
                "outline" -> {
                    forceStyles.add("BorderStyle=1")
                    forceStyles.add("Outline=$borderSize")
                    forceStyles.add("Shadow=0")
                    forceStyles.add("BackColour=${toAssHex(shadowColorArgb)}")
                }
                "shadow" -> {
                    forceStyles.add("BorderStyle=1")
                    forceStyles.add("Outline=0")
                    forceStyles.add("Shadow=$shadowOffset")
                    forceStyles.add("BackColour=${toAssHex(shadowColorArgb)}")
                }
                "opaque_box", "background_box" -> {
                    forceStyles.add("BorderStyle=3")
                    forceStyles.add("Outline=$borderSize")
                    forceStyles.add("Shadow=0")
                    forceStyles.add("BackColour=${toAssHex(backgroundColorArgb)}")
                }
                else -> {
                    forceStyles.add("BorderStyle=1")
                    forceStyles.add("Outline=$borderSize")
                    forceStyles.add("Shadow=$shadowOffset")
                    forceStyles.add("BackColour=${toAssHex(shadowColorArgb)}")
                }
            }
            try {
                view.mpv.setPropertyString("sub-ass-force-style", if (effectiveOverrideAss) forceStyles.joinToString(",") else "")
                view.mpv.setPropertyBoolean("sub-ass-scale-with-window", false)
                view.mpv.setPropertyBoolean("sub-ass-force-margins", false)

                // Changing Override ASS/SSA is a renderer-state transition, not merely a UI
                // preference change. Clear force-style first, then reload the active native
                // ASS/SSA script so libass discards any cached overridden style and rebuilds
                // from the subtitle's own Style definitions when the toggle is OFF.
                val previousNativeOverride = lastAppliedNativeAssOverride
                if (isNativeAssSsa && previousNativeOverride != null && previousNativeOverride != effectiveOverrideAss) {
                    markSubtitleReload(); view.mpv.command("sub-reload")
                }
                lastAppliedNativeAssOverride = if (isNativeAssSsa) effectiveOverrideAss else null
            } catch (_: Throwable) {}
        } catch (_: Throwable) {}
        nudgeRedrawIfPaused()
    }

    // =============================================================================================
    // Raw subtitle text editor (works for every subtitle format: ASS/SSA, SRT, VTT, ... whether
    // uploaded, pasted, attached or embedded in the video).
    //
    // The original subtitle is never modified. The edited text is written to a cache file that is
    // added as an extra mpv subtitle track (hidden from the UI, mapped to the original track id,
    // exactly like the Advanced ASS/SSA rendering copy). Text coming from the live preview is NOT
    // persisted; only text the user APPLIED is stored (AssRawTextStore + rawRevision).
    // =============================================================================================
    private class RawBinding(
        val key: String,
        val title: String,
        val originalTrackId: Int,
        val isAss: Boolean,
        val extension: String,
        val sourceText: String
    ) {
        @Volatile var derivedTrackId: Int = 0
        @Volatile var derivedPath: String? = null
        @Volatile var derivedFiles: List<File> = emptyList()
        @Volatile var appliedSignature: String = ""
    }

    private class RawPlan(val info: AdvancedAssTrackInfo, val originalId: Int)

    @Volatile private var rawEditorEnabled = false
    @Volatile private var rawPreview: Pair<String, String>? = null
    private val rawBindings = ConcurrentHashMap<Int, RawBinding>()
    private var rawFileCounter = 0

    private val rawTextCodecs = setOf(
        "ass", "ssa", "subrip", "srt", "webvtt", "vtt", "mov_text", "text", "subviewer", "subviewer1",
        "microdvd", "sami", "jacosub", "realtext", "mpl2", "ttml", "dfxp", "lrc", "sbv", "pjs",
        "vplayer", "stl", "aqtitle"
    )

    /** Turns the raw editor pipeline on/off (the "View Raw [Script Info] & Subtitle text" toggle). */
    fun setRawSubtitleEditorEnabled(enabled: Boolean) {
        if (rawEditorEnabled == enabled) return
        rawEditorEnabled = enabled
        if (!enabled) rawPreview = null
        scheduleAdvancedAssSync()
    }

    /**
     * Shows [text] (not persisted) instead of the subtitle identified by [key]. Passing null
     * removes the preview, i.e. goes back to the saved/applied state.
     */
    fun setRawSubtitlePreview(key: String?, text: String?) {
        val next = if (key != null && text != null) key to text else null
        if (rawPreview == next) return
        rawPreview = next
        scheduleAdvancedAssSync(RAW_PREVIEW_DEBOUNCE_MS)
    }

    /** Original text of the subtitle [trackId] for the raw editor. Null when it cannot be edited. */
    suspend fun readRawSubtitleSource(context: Context, trackId: Int): RawSubtitleSource? =
        withContext(Dispatchers.IO) {
            try {
                val appContext = context.applicationContext
                val originalId = resolveAdvancedAssOriginalId(trackId)
                val info = withMpvLock<AdvancedAssTrackInfo?>(null) { view ->
                    readAdvancedAssTrackInfo(view, originalId)
                } ?: return@withContext null
                val b = resolveRawBinding(appContext, info) ?: return@withContext null
                RawSubtitleSource(b.key, b.title, b.sourceText, b.extension, b.isAss)
            } catch (_: Throwable) {
                null
            }
        }

    private suspend fun resolveRawBinding(context: Context, info: AdvancedAssTrackInfo): RawBinding? {
        rawBindings[info.id]?.let { return it }
        val listed = _subtitleTracks.value.firstOrNull { it.id == info.id }
        val title = listed?.title?.takeIf { it.isNotBlank() }
            ?: info.title.ifBlank { info.language.ifBlank { "Subtitle ${info.id}" } }
        val path = info.externalPath
        val text: String
        val key: String
        var sourceExtension = ""
        if (path != null) {
            val f = File(path)
            if (!f.isFile) return null
            if (f.name == "live_preview_subs.ass") return null
            val parentName = f.parentFile?.name
            if (parentName == ADVANCED_ASS_CACHE_DIR || parentName == RAW_SUBTITLE_CACHE_DIR) return null
            val generatedDir = File(context.cacheDir, "sub_transcoded").absolutePath
            val source: File = if (f.absolutePath.startsWith(generatedDir)) {
                // SRT/VTT/... converted to ASS for playback: edit the ORIGINAL uploaded text.
                preparedSubtitleSources[f.absolutePath]?.takeIf { it.isFile } ?: return null
            } else {
                if (info.codec !in rawTextCodecs) return null
                f
            }
            sourceExtension = source.extension.lowercase(Locale.ROOT)
            text = UniversalSubtitleEngine.readTextWithCharsetDetection(source)
            key = externalSubtitleKey(text)
        } else {
            if (info.isExternal) return null
            if (info.codec !in rawTextCodecs) return null
            val video = currentFilePath ?: return null
            if (video.startsWith("fd://") || video.startsWith("http://") || video.startsWith("https://") || video.startsWith("rtsp://")) return null
            val videoFile = File(video)
            if (!videoFile.isFile) return null
            val track = PlayerMediaTrack(info.id, "sub", info.title, info.language, info.codec, true)
            val extracted = extractEmbeddedSubtitleToTemp(context, videoFile, track)
            text = try {
                extracted?.let { UniversalSubtitleEngine.readTextWithCharsetDetection(it) } ?: ""
            } catch (_: Throwable) {
                ""
            } finally {
                try { extracted?.delete() } catch (_: Throwable) {}
            }
            key = "emb_" + AdvancedAssStyleEngine
                .sha1Hex("$video|${info.ffIndex}|${info.title}|${info.language}").take(16)
        }
        if (text.isBlank() || text.indexOf('\u0000') >= 0) return null
        val isAss = AdvancedAssStyleEngine.parse(text) != null
        val extension = when {
            isAss -> "ass"
            sourceExtension.isNotBlank() && sourceExtension.length <= 5 -> sourceExtension
            text.trimStart().startsWith("WEBVTT", ignoreCase = true) -> "vtt"
            else -> "srt"
        }
        val binding = RawBinding(key, title, info.id, isAss, extension, text)
        rawBindings[info.id] = binding
        return binding
    }

    private fun removeRawDerived(view: VideoPlayerMpvView, b: RawBinding, reselectOriginal: Boolean) {
        val id = b.derivedTrackId
        if (reselectOriginal && readAdvancedAssTrackInfo(view, b.originalTrackId) != null) {
            try { view.mpv.setPropertyInt("sid", b.originalTrackId) } catch (_: Throwable) {}
            lastActiveSubtitleTrackId = b.originalTrackId
        }
        if (id > 0) {
            try { view.mpv.command("sub-remove", id.toString()) } catch (_: Throwable) {}
        }
        b.derivedFiles.forEach { try { it.delete() } catch (_: Throwable) {} }
        b.derivedTrackId = 0
        b.derivedPath = null
        b.derivedFiles = emptyList()
        b.appliedSignature = ""
    }

    private suspend fun syncRawSubtitleNow(context: Context) {
        val enabled = rawEditorEnabled
        val preview = rawPreview
        val json = advancedAssOverridesJson
        val styleEditsOn = advancedAssEnabled

        // Feature unused: leave every existing subtitle behaviour (and the mpv lock) untouched.
        if (!enabled && rawBindings.values.none { it.derivedTrackId > 0 }) return

        // ---- Phase A (native, locked): inspect the current subtitle track ----
        val plan = withMpvLock<RawPlan?>(null) { view ->
            val sid = (try { view.mpv.getPropertyInt("sid") } catch (_: Throwable) { null }) ?: 0
            if (!enabled || sid <= 0) {
                var removed = false
                for (b in rawBindings.values) {
                    if (b.derivedTrackId > 0) {
                        removeRawDerived(view, b, reselectOriginal = (enabled.not() && sid == b.derivedTrackId))
                        removed = true
                    }
                }
                if (removed) {
                    if (!enabled) setAdvancedAssActive(false)
                    refreshTracksAndChapters()
                }
                return@withMpvLock null
            }
            val originalId = resolveAdvancedAssOriginalId(sid)
            val info = readAdvancedAssTrackInfo(view, originalId) ?: return@withMpvLock null
            RawPlan(info, originalId)
        } ?: return

        // ---- Phase B (no native calls): resolve text and build the file ----
        val binding = resolveRawBinding(context, plan.info) ?: return
        val overrides = AssOverridesStore.get(json, binding.key)
        val rawText: String? = when {
            preview != null && preview.first == binding.key -> preview.second
            overrides.rawRevision > 0L -> AssRawTextStore.read(context, binding.key)
            else -> null
        }
        if (rawText == null) {
            // No raw edit for this subtitle: make sure no edited copy remains.
            withMpvLock(Unit) { view ->
                if (binding.derivedTrackId > 0) {
                    val sid = (try { view.mpv.getPropertyInt("sid") } catch (_: Throwable) { null }) ?: 0
                    removeRawDerived(view, binding, reselectOriginal = (sid == binding.derivedTrackId))
                    refreshTracksAndChapters()
                }
            }
            return
        }
        // A fully emptied editor must not replace the subtitle with an unloadable empty file.
        if (rawText.isBlank()) return

        val styled = if (binding.isAss && styleEditsOn && !overrides.isEmpty) {
            AdvancedAssStyleEngine.apply(rawText, overrides.stylesOnly())
        } else {
            rawText
        }
        var renderText = styled
        if (binding.isAss) {
            // Make sure any font named in the (edited) script, including an uploaded font, is
            // registered, then swap only genuinely missing fonts for the fallback font.
            try { SubtitleFontManager.syncFonts(context, currentFilePath, styled) } catch (_: Throwable) {}
            val cleaned = SubtitleFontManager.stripGlyphFallback(context, renderText)
            val fixed = try {
                SubtitleFontManager.replaceMissingFontsKeepingLayout(context, cleaned)
            } catch (_: Throwable) {
                null
            }
            // "Override ASS/SSA Styles" ON: the selected font must drive every style and inline
            // \\fn of this edited/pasted script too (same rule as UniversalSubtitleEngine uses for
            // uploaded files). Without it the script's own font stays in the text, the glyph
            // fallback judges Hindi/other scripts against THAT font and wraps them in Go Noto.
            var forcedText = fixed ?: cleaned
            val forcedFontName = SubtitleFontManager.selectedFontRenderName
            if (SubtitleFontManager.forceSelectedFontOnAss && !forcedFontName.isNullOrBlank() && !advancedAssGoverning) {
                forcedText = SubtitleFontManager.forceSelectedFontEverywhere(forcedText, forcedFontName)
            }
            renderText = try {
                SubtitleFontManager.applyGlyphFallback(context, forcedText)
            } catch (_: Throwable) {
                forcedText
            }
        }
        val signature = "${binding.isAss}:${renderText.length}:${renderText.hashCode()}"

        val alreadyApplied = withMpvLock(false) { view ->
            binding.derivedTrackId > 0 &&
                binding.appliedSignature == signature &&
                readAdvancedAssTrackInfo(view, binding.derivedTrackId) != null
        }

        var pathToAdd: String? = null
        var writtenFiles: List<File> = emptyList()
        if (!alreadyApplied) {
            val dir = File(context.cacheDir, RAW_SUBTITLE_CACHE_DIR).apply { mkdirs() }
            val revision = synchronized(this@MpvPlayerController) {
                rawFileCounter += 1
                rawFileCounter
            }
            val rawFile = File(dir, "rawedit_${binding.key}_$revision.${binding.extension}")
            rawFile.writeText(renderText, Charsets.UTF_8)
            if (binding.isAss) {
                pathToAdd = rawFile.absolutePath
                writtenFiles = listOf(rawFile)
            } else {
                // Same conversion every uploaded SRT/VTT/... goes through, so all global subtitle
                // style settings keep working on the edited text.
                val prepared = UniversalSubtitleEngine.prepareSubtitleForPlayback(
                    context = context,
                    file = rawFile,
                    videoFps = _estimatedFps.value.takeIf { it > 0.0 } ?: 24.0,
                    videoPath = currentFilePath
                )
                pathToAdd = prepared
                preparedSubtitleSources[File(prepared).absolutePath] = rawFile
                writtenFiles = if (File(prepared).absolutePath == rawFile.absolutePath) {
                    listOf(rawFile)
                } else {
                    listOf(rawFile, File(prepared))
                }
            }
        }

        // ---- Phase C (native, locked): swap the subtitle track ----
        val addPath: String? = pathToAdd
        val addedFiles: List<File> = writtenFiles
        withMpvLock(Unit) { view ->
            val sid = (try { view.mpv.getPropertyInt("sid") } catch (_: Throwable) { null }) ?: 0
            if (alreadyApplied || addPath == null) {
                if (binding.derivedTrackId > 0 && sid != binding.derivedTrackId) {
                    try { view.mpv.setPropertyInt("sid", binding.derivedTrackId) } catch (_: Throwable) {}
                    lastActiveSubtitleTrackId = binding.originalTrackId
                    refreshTracksAndChapters()
                }
                return@withMpvLock
            }
            val newPath: String = addPath

            val fontsDir = SubtitleFontManager.syncFonts(context, currentFilePath)
            setMpvStringIfChanged(view, "sub-fonts-dir", fontsDir.absolutePath)
            if ((try { view.mpv.getPropertyBoolean("embeddedfonts") } catch (_: Throwable) { null }) != true) {
                view.mpv.setPropertyBoolean("embeddedfonts", true)
            }
            setMpvStringIfChanged(view, "sub-ass", "yes")
            if (binding.isAss && styleEditsOn) {
                // Advanced ASS/SSA is governing this ASS/SSA subtitle: libass must use the
                // script's own styles (the per-style edits are already inside the text).
                setMpvStringIfChanged(view, "sub-ass-override", "no")
                setMpvStringIfChanged(view, "sub-ass-force-style", "")
            }

            externalSubtitlesMap[File(newPath).absolutePath] = binding.title
            externalSubtitlesMap[File(newPath).name] = binding.title
            externalSubtitlesMap[File(newPath).nameWithoutExtension] = binding.title
            isSubtitleExplicitlyDisabled = false

            val oldId = binding.derivedTrackId
            val oldFiles = binding.derivedFiles
            try {
                // Attach the new revision first and remove the old one afterwards, so the
                // subtitle never disappears for a frame while typing.
                view.mpv.command("sub-add", newPath, "select", binding.title)
            } catch (_: Throwable) {
                addedFiles.forEach { try { it.delete() } catch (_: Throwable) {} }
                if (oldId > 0) {
                    try { view.mpv.setPropertyInt("sid", oldId) } catch (_: Throwable) {}
                }
                return@withMpvLock
            }
            val newId = findAdvancedAssTrackIdByPath(view, File(newPath).absolutePath)
                ?: findAdvancedAssTrackIdByPath(view, newPath)
                ?: (try { view.mpv.getPropertyInt("sid") } catch (_: Throwable) { null })
                ?: 0
            if (newId <= 0 || newId == binding.originalTrackId) {
                addedFiles.forEach { try { it.delete() } catch (_: Throwable) {} }
                if (oldId > 0) {
                    try { view.mpv.setPropertyInt("sid", oldId) } catch (_: Throwable) {}
                }
                return@withMpvLock
            }
            if (oldId > 0 && oldId != newId) {
                try { view.mpv.command("sub-remove", oldId.toString()) } catch (_: Throwable) {}
            }
            oldFiles.forEach { f ->
                if (addedFiles.none { it.absolutePath == f.absolutePath }) {
                    try { f.delete() } catch (_: Throwable) {}
                }
            }
            // The Advanced ASS/SSA rendering copy of the same subtitle is superseded by this one.
            advancedAssBindings[binding.originalTrackId]?.let { adv ->
                if (adv.derivedTrackId > 0) removeAdvancedAssDerived(view, adv, reselectOriginal = false)
            }

            binding.derivedTrackId = newId
            binding.derivedPath = File(newPath).absolutePath
            binding.derivedFiles = addedFiles
            binding.appliedSignature = signature
            lastActiveSubtitleTrackId = binding.originalTrackId
            try { view.mpv.setPropertyBoolean("sub-visibility", true) } catch (_: Throwable) {}
            setAdvancedAssActive(binding.isAss && styleEditsOn)
            refreshTracksAndChapters()
        }
    }

    // =============================================================================================
    // Advanced ASS/SSA per-style editor (data rules live in player/AdvancedAssStyleEngine.kt)
    //
    // The original subtitle track is never modified. When the user customises styles, the ORIGINAL
    // script text is re-written (Style lines only) into a cache file that is added as an extra mpv
    // subtitle track carrying the same title, and selected in place of the original. Only the
    // subtitle track changes: no loadfile / seek / pause, so position and audio are untouched.
    // =============================================================================================
    private class AdvancedAssBinding(
        val key: String,
        val title: String,
        val originalTrackId: Int,
        @Volatile var sourceText: String?,
        val embeddedTrack: PlayerMediaTrack?
    ) {
        @Volatile var derivedTrackId: Int = 0
        @Volatile var derivedPath: String? = null
        @Volatile var appliedSignature: String = ""
        @Volatile var unsupported: Boolean = false
        @Volatile var fontsPrepared: Boolean = false
    }

    private class AdvancedAssTrackInfo(
        val id: Int,
        val codec: String,
        val title: String,
        val language: String,
        val isExternal: Boolean,
        val externalPath: String?,
        val ffIndex: Int
    )

    @Volatile private var advancedAssEnabled = false
    @Volatile private var advancedAssOverridesJson = ""
    @Volatile private var advancedAssActive = false
    @Volatile private var advancedAssSyncing = false
    private val advancedAssBindings = ConcurrentHashMap<Int, AdvancedAssBinding>()
    private val advancedAssMutex = Mutex()

    // Every native mpv call made by the Advanced ASS code runs inside this monitor, and release()
    // takes it before destroying the native session. A queued/in-flight sync can therefore never
    // call mpv_get_property on a destroyed handle (that was a SIGSEGV in libmpv.so).
    private val advancedAssNativeLock = Any()

    private inline fun <T> withMpvLock(fallback: T, block: (VideoPlayerMpvView) -> T): T {
        // Never wait for the native lock once released: the background teardown may hold it.
        if (isReleased) return fallback
        return synchronized(advancedAssNativeLock) {
            val v = mpvView
            if (isReleased || v == null) fallback else block(v)
        }
    }
    private var advancedAssJob: Job? = null
    private var advancedAssFileCounter = 0
    private var lastAppearanceReplay: (() -> Unit)? = null

    /** True while Advanced ASS/SSA owns the styling of the current ASS/SSA track. */
    private val advancedAssGoverning: Boolean
        get() = advancedAssEnabled && advancedAssActive

    /**
     * Called whenever the subtitle settings change. Cheap when nothing relevant changed. Global
     * "Override ASS/SSA" is suppressed for an ASS/SSA track while Advanced ASS/SSA is enabled, so
     * global settings are never blindly applied on top of per-style values.
     */
    fun setAdvancedAssState(enabled: Boolean, overridesJson: String) {
        val changed = enabled != advancedAssEnabled || overridesJson != advancedAssOverridesJson
        advancedAssEnabled = enabled
        advancedAssOverridesJson = overridesJson
        if (!enabled) advancedAssActive = false
        if (changed) scheduleAdvancedAssSync()
    }

    /**
     * Returns the ORIGINAL ASS/SSA script of [trackId] (or of the original track when [trackId] is
     * the generated edited copy). Null for non-ASS/SSA, generated (SRT/VTT converted), streamed or
     * unreadable subtitles - the caller shows an "unsupported" state instead of an editor.
     */
    suspend fun readAdvancedAssSource(context: Context, trackId: Int): AdvancedAssSource? =
        withContext(Dispatchers.IO) {
            try {
                val appContext = context.applicationContext
                val info = withMpvLock<AdvancedAssTrackInfo?>(null) { view ->
                    readAdvancedAssTrackInfo(view, resolveAdvancedAssOriginalId(trackId))
                } ?: return@withContext null
                val binding = resolveAdvancedAssBinding(appContext, info) ?: return@withContext null
                val text = ensureAdvancedAssSourceText(appContext, binding) ?: return@withContext null
                AdvancedAssSource(binding.key, binding.title, text)
            } catch (_: Throwable) {
                null
            }
        }

    private fun scheduleAdvancedAssSync(delayMs: Long = ADVANCED_ASS_DEBOUNCE_MS) {
        if (isReleased) return
        val ctx = mpvView?.context?.applicationContext ?: return
        advancedAssJob?.cancel()
        advancedAssJob = playerScope.launch(Dispatchers.IO) {
            delay(delayMs)
            advancedAssMutex.withLock {
                withContext(NonCancellable) {
                    advancedAssSyncing = true
                    try {
                        syncAdvancedAssNow(ctx)
                    } catch (t: Throwable) {
                        Log.w("MpvPlayerController", "Advanced ASS sync failed", t)
                    } finally {
                        advancedAssSyncing = false
                    }
                }
            }
        }
    }

    private fun setAdvancedAssActive(active: Boolean) {
        if (advancedAssActive == active) return
        advancedAssActive = active
        // Re-apply the global appearance so the global override is (un)suppressed accordingly.
        try { lastAppearanceReplay?.invoke() } catch (_: Throwable) {}
    }

    private fun resetAdvancedAssBindings() {
        advancedAssJob?.cancel()
        val ctx = mpvView?.context
        for (b in advancedAssBindings.values) {
            b.derivedPath?.let { try { File(it).delete() } catch (_: Throwable) {} }
        }
        advancedAssBindings.clear()
        for (b in rawBindings.values) {
            b.derivedFiles.forEach { try { it.delete() } catch (_: Throwable) {} }
        }
        rawBindings.clear()
        rawPreview = null
        advancedAssActive = false
        if (ctx != null) {
            try { File(ctx.cacheDir, ADVANCED_ASS_CACHE_DIR).listFiles()?.forEach { it.delete() } } catch (_: Throwable) {}
            try { File(ctx.cacheDir, RAW_SUBTITLE_CACHE_DIR).listFiles()?.forEach { it.delete() } } catch (_: Throwable) {}
        }
    }

    private fun resolveAdvancedAssOriginalId(trackId: Int): Int {
        if (trackId <= 0) return trackId
        rawBindings.values.firstOrNull { it.derivedTrackId == trackId }?.let { return it.originalTrackId }
        return advancedAssBindings.values.firstOrNull { it.derivedTrackId == trackId }?.originalTrackId ?: trackId
    }

    /** Maps a logical subtitle ID exposed to the UI to the private Advanced ASS playback ID. */
    private fun resolveAdvancedAssPlaybackId(trackId: Int): Int {
        if (trackId <= 0) return trackId
        rawBindings.values.firstOrNull { it.originalTrackId == trackId && it.derivedTrackId > 0 }
            ?.let { return it.derivedTrackId }
        return advancedAssBindings.values.firstOrNull {
            it.originalTrackId == trackId && it.derivedTrackId > 0
        }?.derivedTrackId ?: trackId
    }

    private fun isAdvancedAssCodec(codec: String): Boolean = codec == "ass" || codec == "ssa"

    private fun readAdvancedAssTrackInfo(view: VideoPlayerMpvView, trackId: Int): AdvancedAssTrackInfo? {
        val count = view.mpv.getPropertyInt("track-list/count") ?: 0
        for (i in 0 until count) {
            if ((view.mpv.getPropertyString("track-list/$i/type") ?: "") != "sub") continue
            val id = view.mpv.getPropertyInt("track-list/$i/id") ?: continue
            if (id != trackId) continue
            val rawPath = (view.mpv.getPropertyString("track-list/$i/external-filename")
                ?: view.mpv.getPropertyString("track-list/$i/filename"))?.takeIf { it.isNotBlank() }
            val path = rawPath?.let { if (it.startsWith("file://")) Uri.parse(it).path else it }
            val isExternal = view.mpv.getPropertyBoolean("track-list/$i/external") ?: false
            return AdvancedAssTrackInfo(
                id = id,
                codec = (view.mpv.getPropertyString("track-list/$i/codec") ?: "").lowercase(Locale.ROOT),
                title = view.mpv.getPropertyString("track-list/$i/title") ?: "",
                language = view.mpv.getPropertyString("track-list/$i/lang") ?: "",
                isExternal = isExternal,
                externalPath = if (isExternal) path else null,
                ffIndex = view.mpv.getPropertyInt("track-list/$i/ff-index") ?: -1
            )
        }
        return null
    }

    private fun findAdvancedAssTrackIdByPath(view: VideoPlayerMpvView, path: String): Int? {
        val count = view.mpv.getPropertyInt("track-list/count") ?: 0
        for (i in 0 until count) {
            if ((view.mpv.getPropertyString("track-list/$i/type") ?: "") != "sub") continue
            val ext = view.mpv.getPropertyString("track-list/$i/external-filename") ?: continue
            val normalized = if (ext.startsWith("file://")) Uri.parse(ext).path else ext
            if (normalized == path) return view.mpv.getPropertyInt("track-list/$i/id")
        }
        return null
    }

    private fun isAdvancedAssEligible(context: Context, info: AdvancedAssTrackInfo): Boolean {
        if (!isAdvancedAssCodec(info.codec)) return false
        val path = info.externalPath
        if (path != null) {
            val f = File(path)
            if (!f.isFile) return false
            // SRT/VTT/... converted by Lumora, live previews and our own edited copies are not
            // real ASS/SSA subtitles.
            val generatedDir = File(context.cacheDir, "sub_transcoded").absolutePath
            if (f.absolutePath.startsWith(generatedDir)) return false
            if (f.name == "live_preview_subs.ass") return false
            if (f.parentFile?.name == ADVANCED_ASS_CACHE_DIR) return false
            return true
        }
        if (info.isExternal) return false
        val video = currentFilePath ?: return false
        if (video.startsWith("fd://") || video.startsWith("http://") || video.startsWith("https://") || video.startsWith("rtsp://")) return false
        return File(video).isFile
    }

    private fun resolveAdvancedAssBinding(context: Context, info: AdvancedAssTrackInfo): AdvancedAssBinding? {
        advancedAssBindings[info.id]?.let { return if (it.unsupported) null else it }
        if (!isAdvancedAssEligible(context, info)) return null
        val listed = _subtitleTracks.value.firstOrNull { it.id == info.id }
        val title = listed?.title?.takeIf { it.isNotBlank() }
            ?: info.title.ifBlank { info.language.ifBlank { "Subtitle ${info.id}" } }
        val path = info.externalPath
        val binding = if (path != null) {
            val text = UniversalSubtitleEngine.readTextWithCharsetDetection(File(path))
            if (AdvancedAssStyleEngine.parse(text) == null) return null
            AdvancedAssBinding(externalSubtitleKey(text), title, info.id, text, null)
        } else {
            val video = currentFilePath ?: return null
            val key = "emb_" + AdvancedAssStyleEngine
                .sha1Hex("$video|${info.ffIndex}|${info.title}|${info.language}").take(16)
            AdvancedAssBinding(
                key, title, info.id, null,
                PlayerMediaTrack(info.id, "sub", info.title, info.language, info.codec, true)
            )
        }
        advancedAssBindings[info.id] = binding
        return binding
    }

    private suspend fun ensureAdvancedAssSourceText(context: Context, binding: AdvancedAssBinding): String? {
        binding.sourceText?.let { return it }
        val track = binding.embeddedTrack ?: return null
        val video = currentFilePath?.let { File(it) }?.takeIf { it.isFile } ?: return null
        val extracted = extractEmbeddedSubtitleToTemp(context, video, track)
        val text = try {
            extracted?.let { UniversalSubtitleEngine.readTextWithCharsetDetection(it) }
        } catch (_: Throwable) {
            null
        } finally {
            try { extracted?.delete() } catch (_: Throwable) {}
        }
        if (text.isNullOrBlank() || AdvancedAssStyleEngine.parse(text) == null) {
            binding.unsupported = true
            return null
        }
        binding.sourceText = text
        return text
    }

    /** Removes stale Advanced ASS cache tracks left by an interrupted/older sync. */
    private fun removeOrphanedAdvancedAssTracks(
        view: VideoPlayerMpvView,
        bindingKey: String,
        keepPath: String? = null
    ) {
        val cacheDir = File(view.context.cacheDir, ADVANCED_ASS_CACHE_DIR).absolutePath
        val filePrefix = "adv_${bindingKey}"
        val ids = mutableListOf<Int>()
        val count = try { view.mpv.getPropertyInt("track-list/count") ?: 0 } catch (_: Throwable) { 0 }
        for (i in 0 until count) {
            if ((view.mpv.getPropertyString("track-list/$i/type") ?: "") != "sub") continue
            val id = view.mpv.getPropertyInt("track-list/$i/id") ?: continue
            val rawPath = view.mpv.getPropertyString("track-list/$i/external-filename")
                ?: view.mpv.getPropertyString("track-list/$i/filename") ?: continue
            val path = if (rawPath.startsWith("file://")) Uri.parse(rawPath).path ?: rawPath else rawPath
            if (File(path).absolutePath.startsWith(cacheDir + File.separator) &&
                File(path).name.startsWith(filePrefix) &&
                !path.equals(keepPath, ignoreCase = true)) {
                ids += id
            }
        }
        ids.forEach { id ->
            try { view.mpv.command("sub-remove", id.toString()) } catch (_: Throwable) {}
        }
    }

    private fun removeAdvancedAssDerived(view: VideoPlayerMpvView, b: AdvancedAssBinding, reselectOriginal: Boolean) {
        val id = b.derivedTrackId
        if (reselectOriginal && readAdvancedAssTrackInfo(view, b.originalTrackId) != null) {
            try { view.mpv.setPropertyInt("sid", b.originalTrackId) } catch (_: Throwable) {}
            lastActiveSubtitleTrackId = b.originalTrackId
        }
        if (id > 0) {
            try { view.mpv.command("sub-remove", id.toString()) } catch (_: Throwable) {}
        }
        b.derivedPath?.let { try { File(it).delete() } catch (_: Throwable) {} }
        b.derivedTrackId = 0
        b.derivedPath = null
        b.appliedSignature = ""
    }

    private class AdvancedAssPlan(val info: AdvancedAssTrackInfo, val originalId: Int)

    /** Sets a string option only when its current value differs (see syncAdvancedAssNow). */
    private fun setMpvStringIfChanged(view: VideoPlayerMpvView, name: String, value: String) {
        val current = try { view.mpv.getPropertyString(name) } catch (_: Throwable) { null }
        if (current == value || (value.isEmpty() && current.isNullOrEmpty())) return
        view.mpv.setPropertyString(name, value)
    }

    private suspend fun syncAdvancedAssNow(context: Context) {
        // The raw text editor runs first; when it owns the current track this pipeline skips it.
        try {
            syncRawSubtitleNow(context)
        } catch (t: Throwable) {
            Log.w("MpvPlayerController", "Raw subtitle sync failed", t)
        }
        val enabled = advancedAssEnabled
        val json = advancedAssOverridesJson

        // ---- Phase A (native, locked): inspect the current subtitle track ----
        val plan = withMpvLock<AdvancedAssPlan?>(null) { view ->
            val sid = (try { view.mpv.getPropertyInt("sid") } catch (_: Throwable) { null }) ?: 0
            if (!enabled) {
                var removed = false
                for (b in advancedAssBindings.values) {
                    if (b.derivedTrackId > 0) {
                        removeAdvancedAssDerived(view, b, reselectOriginal = (sid == b.derivedTrackId))
                        removed = true
                    }
                }
                setAdvancedAssActive(false)
                if (removed) refreshTracksAndChapters()
                return@withMpvLock null
            }
            if (sid <= 0) {
                // A real subtitle unselection must also remove the private Advanced ASS
                // rendering track. Leaving it attached while sid=no makes the next select/reload
                // path capable of seeing both the old generated track and a newly attached one.
                var removed = false
                for (b in advancedAssBindings.values) {
                    if (b.derivedTrackId > 0) {
                        removeAdvancedAssDerived(view, b, reselectOriginal = false)
                        removed = true
                    }
                }
                setAdvancedAssActive(false)
                if (removed) refreshTracksAndChapters()
                return@withMpvLock null
            }
            // A raw-edited copy owns this track: the raw pipeline already baked any per-style
            // edits into it, so there is nothing for this pipeline to swap.
            if (rawBindings.values.any { it.derivedTrackId > 0 && it.derivedTrackId == sid }) {
                return@withMpvLock null
            }
            val originalId = resolveAdvancedAssOriginalId(sid)
            val info = readAdvancedAssTrackInfo(view, originalId)
            if (info == null || !isAdvancedAssEligible(context, info)) {
                setAdvancedAssActive(false)
                return@withMpvLock null
            }
            setAdvancedAssActive(true)
            val existing = advancedAssBindings[originalId]
            // Even with no per-style edits the script may name fonts nobody ships; Phase B then
            // decides whether a font-fallback rendering copy is needed (see below).
            if (existing?.unsupported == true) return@withMpvLock null
            AdvancedAssPlan(info, originalId)
        } ?: return

        // ---- Phase B (no native calls): read source, build the edited script ----
        val binding = advancedAssBindings[plan.originalId]?.takeIf { !it.unsupported }
            ?: resolveAdvancedAssBinding(context, plan.info)
        if (binding == null) {
            withMpvLock(Unit) { setAdvancedAssActive(false) }
            return
        }
        val text = ensureAdvancedAssSourceText(context, binding)
        if (text == null) {
            withMpvLock(Unit) { setAdvancedAssActive(false) }
            return
        }
        val overrides = AssOverridesStore.get(json, binding.key)
        var signature = ""
        var builtFile: File? = null
        val existingFile = binding.derivedPath?.let { File(it) }
            ?.takeIf { it.parentFile?.absolutePath == File(context.cacheDir, ADVANCED_ASS_CACHE_DIR).absolutePath }

        // Advanced ASS/SSA renders with sub-ass-override=no, so libass uses the script's own
        // font names. If a style names a font that neither the video/subtitle nor the device
        // provides, libass has nothing to draw with and the whole subtitle vanishes. Detect that
        // and swap ONLY the missing font names for the fallback font (user's selected font, else
        // the bundled Go Noto). Fonts that exist are never touched, and nothing but font names
        // changes, so positions, tags, colours and animations stay exactly as authored.
        if (!binding.fontsPrepared) {
            try { SubtitleFontManager.syncFonts(context, currentFilePath, text) } catch (_: Throwable) {}
            binding.fontsPrepared = true
        }
        // The loaded file is the prepared copy, which may already carry Go Noto glyph-fallback
        // tags computed for the OLD font (e.g. every Hindi character wrapped in {\fnGo Noto...}).
        // Those tags would keep overriding the font the user now picks in Advanced ASS/SSA, so
        // they are removed first and recomputed below against the NEW fonts.
        val cleanText = SubtitleFontManager.stripGlyphFallback(context, text)
        val base = if (overrides.isEmpty) cleanText else AdvancedAssStyleEngine.apply(cleanText, overrides)
        val fontFixed = try {
            SubtitleFontManager.replaceMissingFontsKeepingLayout(context, base)
        } catch (_: Throwable) { null }
        val fontsReady = fontFixed ?: base
        // Only characters the (new) style font really lacks get the Go Noto fallback.
        val withFallback = try {
            SubtitleFontManager.applyGlyphFallback(context, fontsReady)
        } catch (_: Throwable) { fontsReady }
        val renderText = withFallback
        val needsDerived = !overrides.isEmpty || fontFixed != null || withFallback != text
        if (needsDerived) {
            val built = renderText
            signature = "${built.length}:${built.hashCode()}"

            // Do not rewrite an ASS file that is already attached to mpv and then call
            // `sub-reload`: mpv documents sub-reload as an unload + re-add operation, which
            // creates the exact visible OFF -> ON flash reported when a font/style is changed.
            // Build a fresh, uniquely named revision instead. The new track is selected first
            // and only then is the old track removed, so there is never a sid=no gap.
            val dir = File(context.cacheDir, ADVANCED_ASS_CACHE_DIR).apply { mkdirs() }
            val alreadyAppliedForSignature = binding.derivedTrackId > 0 && binding.appliedSignature == signature
            if (alreadyAppliedForSignature && existingFile?.isFile == true) {
                builtFile = existingFile
            } else {
                val revision = synchronized(this@MpvPlayerController) {
                    advancedAssFileCounter += 1
                    advancedAssFileCounter
                }
                val fileName = if (existingFile == null) {
                    "adv_${binding.key}.ass"
                } else {
                    "adv_${binding.key}_$revision.ass"
                }
                val nextFile = File(dir, fileName)
                nextFile.writeText(built, Charsets.UTF_8)
                builtFile = nextFile
            }
        }

        // ---- Phase C (native, locked): swap the subtitle track ----
        withMpvLock(Unit) { view ->
            val sid = (try { view.mpv.getPropertyInt("sid") } catch (_: Throwable) { null }) ?: 0
            if (!needsDerived) {
                if (binding.derivedTrackId > 0) {
                    removeAdvancedAssDerived(view, binding, reselectOriginal = (sid == binding.derivedTrackId))
                    refreshTracksAndChapters()
                }
                return@withMpvLock
            }
            val derivedAlive = binding.derivedTrackId > 0 &&
                readAdvancedAssTrackInfo(view, binding.derivedTrackId) != null
            val file = builtFile
            if (file == null) {
                if (derivedAlive && sid != binding.derivedTrackId) {
                    try { view.mpv.setPropertyInt("sid", binding.derivedTrackId) } catch (_: Throwable) {}
                    lastActiveSubtitleTrackId = binding.originalTrackId
                    refreshTracksAndChapters()
                }
                return@withMpvLock
            }

            // Only touch global mpv/libass options that actually differ. Re-assigning an
            // unchanged option (fonts dir, override mode...) makes libass rebuild its state and
            // re-layout EVERY subtitle line, which is what made animations/timing of untouched
            // styles hiccup while a single style was being edited.
            val fontsDir = SubtitleFontManager.syncFonts(context, currentFilePath)
            setMpvStringIfChanged(view, "sub-fonts-dir", fontsDir.absolutePath)
            if ((try { view.mpv.getPropertyBoolean("embeddedfonts") } catch (_: Throwable) { null }) != true) {
                view.mpv.setPropertyBoolean("embeddedfonts", true)
            }
            setMpvStringIfChanged(view, "sub-ass", "yes")
            setMpvStringIfChanged(view, "sub-ass-override", "no")
            setMpvStringIfChanged(view, "sub-ass-force-style", "")

            externalSubtitlesMap[file.absolutePath] = binding.title
            externalSubtitlesMap[file.name] = binding.title
            isSubtitleExplicitlyDisabled = false

            if (derivedAlive && binding.appliedSignature == signature && binding.derivedPath == file.absolutePath) {
                // Nothing changed. Keep the existing rendering track untouched. In particular,
                // never call sub-reload for a no-op style sync.
                if (sid != binding.derivedTrackId) {
                    try { view.mpv.setPropertyInt("sid", binding.derivedTrackId) } catch (_: Throwable) {}
                }
                try { view.mpv.setPropertyBoolean("sub-visibility", true) } catch (_: Throwable) {}
                lastActiveSubtitleTrackId = binding.originalTrackId
                return@withMpvLock
            }

            // First activation OR a style revision. Always attach the new revision before
            // removing the old one. mpv selects the new subtitle after it has loaded it, so the
            // old subtitle remains visible during the hand-off instead of disappearing for a
            // frame or two. This is deliberately different from sub-reload (which unloads first).
            val oldId = if (derivedAlive) binding.derivedTrackId else 0
            val oldPath = binding.derivedPath
            try {
                view.mpv.command("sub-add", file.absolutePath, "select", binding.title)
            } catch (_: Throwable) {
                // The existing track is still valid. Keep it active if the replacement failed.
                if (derivedAlive) {
                    try { view.mpv.setPropertyInt("sid", oldId) } catch (_: Throwable) {}
                }
                return@withMpvLock
            }

            val newId = findAdvancedAssTrackIdByPath(view, file.absolutePath)
                ?: (try { view.mpv.getPropertyInt("sid") } catch (_: Throwable) { null })
                ?: 0
            if (newId <= 0) {
                if (derivedAlive) {
                    try { view.mpv.setPropertyInt("sid", oldId) } catch (_: Throwable) {}
                }
                try { File(file.absolutePath).delete() } catch (_: Throwable) {}
                return@withMpvLock
            }

            // The new revision is selected now. Remove the previous revision only after the
            // successful hand-off. This prevents both the flash and stale duplicate tracks.
            if (oldId > 0 && oldId != newId) {
                try { view.mpv.command("sub-remove", oldId.toString()) } catch (_: Throwable) {}
            }
            if (oldPath != null && oldPath != file.absolutePath) {
                try { File(oldPath).delete() } catch (_: Throwable) {}
            }

            binding.derivedTrackId = newId
            binding.derivedPath = file.absolutePath
            binding.appliedSignature = signature
            lastActiveSubtitleTrackId = binding.originalTrackId
            try { view.mpv.setPropertyBoolean("sub-visibility", true) } catch (_: Throwable) {}

            // Clean up any older orphaned revisions left by an interrupted previous hand-off,
            // but explicitly preserve the revision that is now selected.
            removeOrphanedAdvancedAssTracks(view, binding.key, keepPath = file.absolutePath)
            refreshTracksAndChapters()

        }
    }

    fun setAudioTrack(trackId: Int) {
        try {
            if (trackId <= 0) {
                mpvView?.mpv?.setPropertyString("aid", "no")
            } else {
                mpvView?.mpv?.setPropertyInt("aid", trackId)
            }
            _audioTracks.value = _audioTracks.value.map {
                it.copy(isSelected = (it.id == trackId))
            }
        } catch (_: Throwable) {}
    }

    fun setSubtitleTrack(trackId: Int) {
        val selectionGeneration = subtitleSelectionGeneration.incrementAndGet()
        try {
            val view = mpvView ?: return
            if (trackId <= 0) {
                isSubtitleExplicitlyDisabled = true
                subtitleUsesGeneratedAss = false
                view.mpv.setPropertyBoolean("sub-visibility", false)
                view.mpv.setPropertyString("sid", "no")
                lastActiveSubtitleTrackId = 0
            } else {
                isSubtitleExplicitlyDisabled = false
                lastActiveSubtitleTrackId = trackId
                val playbackTrackId = resolveAdvancedAssPlaybackId(trackId)
                // Detect whether the selected subtitle points at one of Lumora's
                // generated ASS files. Native ASS/SSA must never receive a forced
                // override unless the user explicitly enabled it.
                subtitleUsesGeneratedAss = false
                val trackCount = view.mpv.getPropertyInt("track-list/count") ?: 0
                for (i in 0 until trackCount) {
                    val id = view.mpv.getPropertyInt("track-list/$i/id") ?: -1
                    if (id == playbackTrackId) {
                        val filename = view.mpv.getPropertyString("track-list/$i/filename") ?: ""
                        subtitleUsesGeneratedAss = loadedExternalSubtitles.any { it == filename || File(it).absolutePath == File(filename).absolutePath }
                        break
                    }
                }
                view.mpv.setPropertyInt("sid", playbackTrackId)
                view.mpv.setPropertyBoolean("sub-visibility", true)
                view.mpv.setPropertyString("sub-ass", "yes")
                // Keep the delay the user dialled in: re-selecting / auto-reselecting a track must
                // never silently fall back to 0 while the UI still shows the old value.
                view.mpv.setPropertyDouble("sub-delay", currentSubtitleDelayMs / 1000.0)
            }
            // Mark the new selection BEFORE recomputing/re-applying appearance below, so that
            // Override ASS/SSA (and every Typography/Colors value) is evaluated against the
            // track the user just picked, not whichever one was selected before.
            val secId = _secondarySubtitleTrackId.value
            _subtitleTracks.value = _subtitleTracks.value.map {
                it.copy(
                    isSelected = (trackId > 0 && it.id == trackId),
                    isSecondarySelected = (secId > 0 && it.id == secId)
                )
            }
            if (trackId > 0) {
                _subtitleTracks.value.firstOrNull { it.id == trackId }?.let { userSubtitleIdentity = subtitleIdentity(it) }
                reapplyAppearanceForTrack(view, trackId)
                // Native ASS/SSA can be enumerated by mpv slightly after the UI selection
                // callback.  If the selection lands in that short window, the UI can correctly
                // show the track as selected while libass has not yet rebuilt the subtitle event
                // state.  A small, cancellable recovery pass re-resolves the real MPV track id,
                // keeps the selection alive, and performs one controlled reload for ASS/SSA.
                if (isTrackNativeAssSsa(trackId)) {
                    scheduleNativeAssSelectionRecovery(trackId, selectionGeneration)
                }
            }
            if (advancedAssEnabled || rawEditorEnabled || advancedAssBindings.isNotEmpty() || rawBindings.isNotEmpty()) scheduleAdvancedAssSync()
            nudgeRedrawIfPaused()
        } catch (_: Throwable) {}
    }

    /**
     * Repairs the small race between `sid` selection and mpv/libass subtitle-track
     * enumeration.  This is deliberately limited to native ASS/SSA tracks so ordinary SRT/VTT
     * selections are not reloaded unnecessarily.  The job is invalidated as soon as the user
     * selects another subtitle or turns subtitles off.
     */
    private fun scheduleNativeAssSelectionRecovery(trackId: Int, generation: Long) {
        val expectedPlaybackId = resolveAdvancedAssPlaybackId(trackId)
        playerScope.launch {
            repeat(24) { attempt ->
                if (isReleased || generation != subtitleSelectionGeneration.get()) return@launch

                val recovered = withMpvLock(false) { view ->
                    var found = false
                    val count = try { view.mpv.getPropertyInt("track-list/count") ?: 0 } catch (_: Throwable) { 0 }
                    for (i in 0 until count) {
                        val id = try { view.mpv.getPropertyInt("track-list/$i/id") ?: -1 } catch (_: Throwable) { -1 }
                        if (id == expectedPlaybackId && (try { view.mpv.getPropertyString("track-list/$i/type") } catch (_: Throwable) { "" }) == "sub") {
                            found = true
                            break
                        }
                    }
                    if (!found) return@withMpvLock false

                    val currentSid = try { view.mpv.getPropertyInt("sid") ?: 0 } catch (_: Throwable) { 0 }
                    if (currentSid != expectedPlaybackId) {
                        try { view.mpv.setPropertyInt("sid", expectedPlaybackId) } catch (_: Throwable) {}
                    }
                    try { view.mpv.setPropertyBoolean("sub-visibility", true) } catch (_: Throwable) {}
                    try { view.mpv.setPropertyString("sub-ass", "yes") } catch (_: Throwable) {}
                    try {
                        view.mpv.setPropertyString(
                            "sub-ass-override",
                            if (effectiveOverrideForTrack(trackId)) "force" else "no"
                        )
                    } catch (_: Throwable) {}

                    // One reload after the real track exists fixes the exact failure mode where
                    // mpv reports `sid` successfully but libass has not attached the external
                    // ASS event stream yet. Do not repeat it on every polling attempt.
                    try { markSubtitleReload(); view.mpv.command("sub-reload") } catch (_: Throwable) {}
                    try {
                        if (!(_isPlaying.value)) view.mpv.command("seek", "0", "relative+exact")
                    } catch (_: Throwable) {}
                    true
                }

                if (recovered) {
                    lastActiveSubtitleTrackId = trackId
                    refreshTracksAndChapters()
                    return@launch
                }

                delay(if (attempt < 4) 35L else 75L)
            }
        }
    }

    fun setSecondarySubtitleTrack(trackId: Int) {
        try {
            val view = mpvView ?: return
            _secondarySubtitleTrackId.value = trackId
            if (trackId <= 0) {
                view.mpv.setPropertyString("secondary-sid", "no")
            } else {
                view.mpv.setPropertyInt("secondary-sid", trackId)
                view.mpv.setPropertyBoolean("secondary-sub-visibility", true)
                view.mpv.setPropertyString("secondary-sub-ass-override", if (effectiveOverrideForTrack(trackId)) "force" else "no")
                try {
                    // Position secondary subtitle at top of screen (pos 10) so it doesn't overlap primary (pos 100)
                    view.mpv.setPropertyDouble("secondary-sub-pos", 10.0)
                } catch (_: Throwable) {}
                val secDelay = trackDelayMap[trackId] ?: 0L
                view.mpv.setPropertyDouble("secondary-sub-delay", secDelay / 1000.0)
            }
            val primId = lastActiveSubtitleTrackId
            _subtitleTracks.value = _subtitleTracks.value.map {
                it.copy(
                    isSelected = (!isSubtitleExplicitlyDisabled && primId > 0 && it.id == primId),
                    isSecondarySelected = (trackId > 0 && it.id == trackId)
                )
            }
            nudgeRedrawIfPaused()
        } catch (_: Throwable) {}
    }

    /**
     * Atomically selects/updates Primary and Secondary subtitle tracks in a single transaction.
     * Prevents intermediate recomposition flashes or race conditions between primary and secondary.
     */
    fun selectSubtitleTracksAtomic(primaryTrackId: Int, secondaryTrackId: Int) {
        val selectionGeneration = subtitleSelectionGeneration.incrementAndGet()
        try {
            val view = mpvView
            isSubtitleExplicitlyDisabled = (primaryTrackId <= 0 && secondaryTrackId <= 0)
            lastActiveSubtitleTrackId = if (primaryTrackId > 0) primaryTrackId else 0
            _secondarySubtitleTrackId.value = if (secondaryTrackId > 0) secondaryTrackId else 0
            _subtitleTracks.value = _subtitleTracks.value.map {
                it.copy(
                    isSelected = (primaryTrackId > 0 && it.id == primaryTrackId),
                    isSecondarySelected = (secondaryTrackId > 0 && it.id == secondaryTrackId)
                )
            }

            if (view != null) {
                val playbackPrimaryTrackId = resolveAdvancedAssPlaybackId(primaryTrackId)
                if (primaryTrackId <= 0) {
                    subtitleUsesGeneratedAss = false
                    view.mpv.setPropertyBoolean("sub-visibility", false)
                    view.mpv.setPropertyString("sid", "no")
                    view.mpv.setPropertyDouble("sub-delay", currentSubtitleDelayMs / 1000.0)
                } else {
                    subtitleUsesGeneratedAss = false
                    val trackCount = (try { view.mpv.getPropertyInt("track-list/count") } catch (_: Throwable) { null }) ?: 0
                    for (i in 0 until trackCount) {
                        val id = (try { view.mpv.getPropertyInt("track-list/$i/id") } catch (_: Throwable) { null }) ?: -1
                        if (id == playbackPrimaryTrackId) {
                            val filename = (try { view.mpv.getPropertyString("track-list/$i/filename") } catch (_: Throwable) { null }) ?: ""
                            subtitleUsesGeneratedAss = loadedExternalSubtitles.any { it == filename || File(it).absolutePath == File(filename).absolutePath }
                            break
                        }
                    }
                    view.mpv.setPropertyInt("sid", playbackPrimaryTrackId)
                    view.mpv.setPropertyBoolean("sub-visibility", true)
                    view.mpv.setPropertyString("sub-ass", "yes")
                    view.mpv.setPropertyDouble("sub-delay", currentSubtitleDelayMs / 1000.0)
                }

                if (secondaryTrackId <= 0) {
                    view.mpv.setPropertyString("secondary-sid", "no")
                } else {
                    view.mpv.setPropertyInt("secondary-sid", secondaryTrackId)
                    view.mpv.setPropertyBoolean("secondary-sub-visibility", true)
                    view.mpv.setPropertyString("secondary-sub-ass-override", if (effectiveOverrideForTrack(secondaryTrackId)) "force" else "no")
                    try {
                        view.mpv.setPropertyDouble("secondary-sub-pos", 10.0)
                    } catch (_: Throwable) {}
                    val secDelay = trackDelayMap[secondaryTrackId] ?: 0L
                    view.mpv.setPropertyDouble("secondary-sub-delay", secDelay / 1000.0)
                }

                // _subtitleTracks already reflects the new primary/secondary selection (set at
                // the top of this function), so Override ASS/SSA and every Typography/Colors
                // value can now be recomputed correctly for whatever format the NEW primary
                // track actually is, instead of reusing the previous track's applied state.
                if (primaryTrackId > 0) {
                    reapplyAppearanceForTrack(view, primaryTrackId)
                    if (isTrackNativeAssSsa(primaryTrackId)) {
                        scheduleNativeAssSelectionRecovery(primaryTrackId, selectionGeneration)
                    }
                }

            }

            if (advancedAssEnabled || rawEditorEnabled || advancedAssBindings.isNotEmpty() || rawBindings.isNotEmpty()) scheduleAdvancedAssSync()

            _subtitleTracks.value = _subtitleTracks.value.map { track ->
                track.copy(
                    isSelected = (primaryTrackId > 0 && track.id == primaryTrackId),
                    isSecondarySelected = (secondaryTrackId > 0 && track.id == secondaryTrackId)
                )
            }
            nudgeRedrawIfPaused()
        } catch (_: Throwable) {}
    }

    /**
     * Synchronizes a changed external subtitle source without unloading the active track.
     * The caller has already synchronized the canonical file; we only refresh the existing
     * matching MPV track. This method is intentionally serialized with the native lock so a
     * lifecycle release cannot race a libmpv property call.
     */
    /**
     * Compatibility overload for callers that identify an uploaded subtitle by its source
     * path/name instead of the current MPV track id. The track id is resolved first and the
     * existing id-based implementation remains the single native reload path.
     *
     * This overload intentionally does not add/select/remove any track; it only resolves the
     * already attached track and delegates to the safe implementation below.
     */
    fun reloadExternalSubtitleSource(
        filePath: String,
        originalName: String? = null,
        context: Context? = null,
        trackId: Int = 0
    ) {
        if (isReleased) return
        val resolvedId = if (trackId > 0) {
            trackId
        } else {
            val name = originalName?.trim().orEmpty()
            _subtitleTracks.value.firstOrNull { track ->
                track.isExternal && (
                    (!name.isBlank() && (track.originalFilename.equals(name, ignoreCase = true) || track.title.equals(name, ignoreCase = true))) ||
                    (!filePath.isBlank() && track.originalFilename.equals(File(filePath).name, ignoreCase = true))
                )
            }?.id ?: 0
        }
        if (resolvedId > 0) reloadExternalSubtitleSource(resolvedId)
    }

    fun reloadExternalSubtitleSource(trackId: Int) {
        if (isReleased || trackId <= 0) return
        withMpvLock(Unit) { view ->
            try {
                val playbackId = resolveAdvancedAssPlaybackId(trackId)
                val count = view.mpv.getPropertyInt("track-list/count") ?: 0
                var exists = false
                for (i in 0 until count) {
                    val id = view.mpv.getPropertyInt("track-list/$i/id") ?: -1
                    if (id == playbackId) { exists = true; break }
                }
                if (!exists) return@withMpvLock
                val wasSelected = (view.mpv.getPropertyInt("sid") ?: 0) == playbackId
                // sub-reload is only used here because the SOURCE FILE CONTENT changed. Normal
                // select/unselect never reaches this path. Keep the same sid across the reload.
                markSubtitleReload(); view.mpv.command("sub-reload")
                if (wasSelected) {
                    try { view.mpv.setPropertyInt("sid", playbackId) } catch (_: Throwable) {}
                    try { view.mpv.setPropertyBoolean("sub-visibility", true) } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}
        }
    }

    fun removeSubtitleTrack(trackId: Int) {
        try {
            val view = mpvView ?: return
            val trackToRemove = _subtitleTracks.value.firstOrNull { it.id == trackId }
            // If primary or secondary, reset sid
            val currentSid = try { view.mpv.getPropertyInt("sid") } catch (_: Throwable) { 0 }
            if (currentSid == trackId) {
                view.mpv.setPropertyString("sid", "no")
            }
            if (_secondarySubtitleTrackId.value == trackId) {
                _secondarySubtitleTrackId.value = 0
                view.mpv.setPropertyString("secondary-sid", "no")
            }
            try {
                view.mpv.command("sub-remove", trackId.toString())
            } catch (_: Throwable) {}
            _subtitleTracks.value = _subtitleTracks.value.filter { it.id != trackId }
            if (trackToRemove != null) {
                loadedExternalSubtitles.removeAll {
                    it == trackToRemove.originalFilename ||
                    it == trackToRemove.title ||
                    File(it).name == trackToRemove.originalFilename ||
                    File(it).name == trackToRemove.title
                }
            }
        } catch (_: Throwable) {}
    }

    fun removeAudioTrack(trackId: Int) {
        try {
            val view = mpvView ?: return
            val currentAid = try { view.mpv.getPropertyInt("aid") } catch (_: Throwable) { 0 }
            if (currentAid == trackId) {
                view.mpv.setPropertyString("aid", "no")
            }
            try {
                view.mpv.command("audio-remove", trackId.toString())
            } catch (_: Throwable) {}
            _audioTracks.value = _audioTracks.value.filter { it.id != trackId }
            val remaining = _audioTracks.value
            if (currentAid == trackId && remaining.isNotEmpty()) {
                setAudioTrack(remaining.first().id)
            }
        } catch (_: Throwable) {}
    }

    /**
     * Translates the selected subtitle without touching the original mpv track.
     * External files are read directly. MKV/Matroska embedded text tracks use the existing
     * EBML parser so ASS codec-private style information can be retained; other containers
     * fall back to Android's MediaExtractor text samples.
     */
    suspend fun translateSubtitleTrack(
        context: Context,
        trackId: Int,
        settings: AiFeaturesSettings,
        targetLanguageLabel: String,
        onProgress: (SubtitleTranslationService.TranslationProgress) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        val track = _subtitleTracks.value.firstOrNull { it.id == trackId }
            ?: return@withContext Result.failure(IllegalArgumentException("Subtitle track not found"))

        val sourceFile = subtitleTrackSourcePaths[trackId]?.let(::File)
        val videoFile = currentFilePath?.let(::File)
        val source = sourceFile ?: run {
            if (videoFile == null || !videoFile.exists()) {
                return@withContext Result.failure(IllegalStateException("Embedded subtitle source video is unavailable"))
            }
            extractEmbeddedSubtitleToTemp(context, videoFile, track)
                ?: return@withContext Result.failure(IllegalStateException("Could not extract the selected embedded subtitle track"))
        }

        val target = TranslateLanguages.ALL.firstOrNull { it.code == settings.translateLanguageCode }
            ?: return@withContext Result.failure(IllegalArgumentException("Unsupported target language"))

        val safeBase = source.nameWithoutExtension.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val targetDir = File(context.filesDir, "translated_subtitles")
        targetDir.mkdirs()
        val output = File(targetDir, "${safeBase}.${target.code.replace("-", "_")}.${source.extension.ifBlank { "srt" }}")

        val result = SubtitleTranslationService.translateFile(
            context = context,
            sourceFile = source,
            destinationFile = output,
            settings = settings,
            onProgress = onProgress
        ).getOrElse { return@withContext Result.failure(it) }

        SubtitleTranslationService.validateTranslatedSubtitle(source, result.file)
            .getOrElse { return@withContext Result.failure(it) }

        val originalTitle = track.title.ifBlank { "Subtitle $trackId" }
        val translatedTitle = "$originalTitle • ${target.label}"
        val newTrackId = addExternalSubtitle(
            filePath = result.file.absolutePath,
            context = context,
            originalName = translatedTitle,
            select = false
        ) ?: return@withContext Result.failure(IllegalStateException("Translated subtitle was generated but could not be loaded into the player"))

        Result.success(newTrackId)
    }

    private suspend fun extractEmbeddedSubtitleToTemp(
        context: Context,
        video: File,
        track: PlayerMediaTrack
    ): File? {
        val extension = video.extension.lowercase(Locale.US)
        val tempDir = File(context.cacheDir, "lumora_embedded_subtitle")
        tempDir.mkdirs()
        val out = File(tempDir, "track_${track.id}_${System.currentTimeMillis()}.srt")

        // Matroska extraction already understands ASS/SSA codec-private headers and event
        // payloads. Prefer it for MKV/MKA/WebM so positioning/styles survive translation.
        if (extension in setOf("mkv", "mka", "webm")) {
            return try {
                val (_, tracks, _) = MkvManagerEngine.parseMkvFile(context, Uri.fromFile(video))
                val match = tracks.filter { it.trackType == com.example.util.MkvTrackType.SUBTITLE }.minByOrNull { candidate ->
                    val idDistance = kotlin.math.abs(candidate.trackNumber - track.id.toLong())
                    val langPenalty = if (candidate.language.equals(track.language, true)) 0 else 1000
                    val namePenalty = if (candidate.name.equals(track.title, true)) 0 else 100
                    idDistance + langPenalty + namePenalty
                } ?: return null
                val content = MkvManagerEngine.extractSubtitleTrackToString(context, Uri.fromFile(video), match)
                if (content.isBlank()) null else {
                    val ext = when {
                        content.contains("[Script Info]", true) -> "ass"
                        content.trimStart().startsWith("WEBVTT", true) -> "vtt"
                        else -> "srt"
                    }
                    val realOut = File(tempDir, "track_${track.id}_${System.currentTimeMillis()}.$ext")
                    realOut.writeText(content, Charsets.UTF_8)
                    realOut
                }
            } catch (_: Throwable) {
                null
            }
        }

        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(video.absolutePath)
            val extractorIndex = (subtitleTrackExtractorIndices[track.id] ?: (track.id - 1)).coerceAtLeast(0)
            if (extractorIndex >= extractor.trackCount) {
                extractor.release()
                return null
            }
            extractor.selectTrack(extractorIndex)
            val format = extractor.getTrackFormat(extractorIndex)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (!mime.startsWith("text/") && !mime.startsWith("application/")) {
                extractor.release()
                return null
            }
            val sb = StringBuilder()
            var cueIndex = 1
            val durationUs = format.getLong(MediaFormat.KEY_DURATION, 0L)
            val buffer = java.nio.ByteBuffer.allocate(1024 * 1024)
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val startUs = extractor.sampleTime
                val sampleDurationUs = extractor.sampleTime.takeIf { it >= 0L }?.let {
                    if (durationUs > 0) durationUs else 3_000_000L
                } ?: 3_000_000L
                val bytes = ByteArray(size)
                buffer.rewind()
                buffer.get(bytes)
                val text = String(bytes, Charsets.UTF_8).trim()
                if (text.isNotBlank()) {
                    val start = formatSrtTime(startUs / 1000L)
                    val end = formatSrtTime((startUs + sampleDurationUs) / 1000L)
                    sb.append(cueIndex++).append('\n')
                        .append(start).append(" --> ").append(end).append('\n')
                        .append(text).append("\n\n")
                }
                if (!extractor.advance()) break
            }
            extractor.release()
            if (cueIndex == 1) null else {
                out.writeText(sb.toString(), Charsets.UTF_8)
                out
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun formatSrtTime(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val h = safe / 3_600_000L
        val m = (safe % 3_600_000L) / 60_000L
        val s = (safe % 60_000L) / 1_000L
        val milli = safe % 1_000L
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, milli)
    }

    fun setSubtitleVisibility(visible: Boolean) {
        try {
            val view = mpvView ?: return
            view.mpv.setPropertyBoolean("sub-visibility", visible)
            if (!visible) {
                isSubtitleExplicitlyDisabled = true
                try { view.mpv.setPropertyString("sid", "no") } catch (_: Throwable) {}
                _subtitleTracks.value = _subtitleTracks.value.map { it.copy(isSelected = false) }
            } else {
                isSubtitleExplicitlyDisabled = false
                if (lastActiveSubtitleTrackId > 0) {
                    val targetSid = lastActiveSubtitleTrackId
                    try { view.mpv.setPropertyInt("sid", targetSid) } catch (_: Throwable) {}
                    _subtitleTracks.value = _subtitleTracks.value.map { it.copy(isSelected = (it.id == targetSid)) }
                }
            }
        } catch (_: Throwable) {}
    }

    /** Track id mpv assigned to the external subtitle loaded from [path], or 0 if not listed (yet). */
    private fun findExternalTrackIdByPath(view: VideoPlayerMpvView, path: String): Int {
        val target = File(path).absolutePath
        val targetName = File(path).name
        val count = try { view.mpv.getPropertyInt("track-list/count") ?: 0 } catch (_: Throwable) { 0 }
        for (i in 0 until count) {
            if ((try { view.mpv.getPropertyString("track-list/$i/type") } catch (_: Throwable) { "" }) != "sub") continue
            val ext = try {
                view.mpv.getPropertyString("track-list/$i/external-filename")
                    ?: view.mpv.getPropertyString("track-list/$i/filename") ?: ""
            } catch (_: Throwable) { "" }
            if (ext.isBlank()) continue
            val normalized = if (ext.startsWith("file://")) Uri.parse(ext).path ?: ext else ext
            if (normalized == target || File(normalized).name.equals(targetName, ignoreCase = true)) {
                return try { view.mpv.getPropertyInt("track-list/$i/id") ?: 0 } catch (_: Throwable) { 0 }
            }
        }
        return 0
    }

    /**
     * Right after `sub-add` mpv can take a few frames to list the new external track. Waiting for
     * it here (briefly, never on the main thread) means the Uploaded Subtitles list and the
     * returned track id are correct the moment the file is touched, instead of showing the old
     * list / returning the previous track's id. If it is still late, a short background watcher
     * refreshes the list as soon as the track appears.
     */
    private fun awaitAddedExternalTrack(view: VideoPlayerMpvView, path: String): Int {
        val onMain = android.os.Looper.myLooper() == android.os.Looper.getMainLooper()
        val attempts = if (onMain) 1 else 30
        for (attempt in 0 until attempts) {
            val id = findExternalTrackIdByPath(view, path)
            if (id > 0) {
                refreshTracksAndChapters()
                return id
            }
            if (!onMain) { try { Thread.sleep(25L) } catch (_: InterruptedException) { break } }
        }
        refreshTracksAndChapters()
        playerScope.launch {
            repeat(40) {
                if (isReleased) return@launch
                delay(50L)
                val found = try { findExternalTrackIdByPath(view, path) } catch (_: Throwable) { 0 }
                if (found > 0) {
                    refreshTracksAndChapters()
                    return@launch
                }
            }
        }
        return 0
    }

    fun addExternalSubtitle(filePath: String, context: Context? = null, originalName: String? = null, select: Boolean = true): Int? {
        try {
            val view = mpvView ?: return null
            val isStream = filePath.startsWith("http://") || filePath.startsWith("https://")
            val file = if (!isStream) File(filePath) else null
            if (!isStream && (file == null || !file.exists() || file.length() == 0L)) return null

            val origTitle = originalName?.ifBlank { null } ?: file?.name ?: "Online Subtitle"
            val origBase = if (origTitle.contains('.')) origTitle.substringBeforeLast('.') else origTitle

            // Prevent duplicate subtitle tracks: if a track with matching name or path is already loaded, reuse or replace it
            val existingTrack = _subtitleTracks.value.firstOrNull { track ->
                // An embedded track that merely shares a name must never be replaced/removed by an upload.
                if (!track.isExternal) return@firstOrNull false
                val tName = (if (track.originalFilename.isNotBlank()) track.originalFilename else track.title).trim()
                val tBase = if (tName.contains('.')) tName.substringBeforeLast('.') else tName
                tName.equals(origTitle, ignoreCase = true) ||
                tBase.equals(origBase, ignoreCase = true) ||
                (subtitleTrackSourcePaths[track.id]?.let { p ->
                    p == filePath || File(p).name.equals(origTitle, ignoreCase = true) ||
                    File(p).nameWithoutExtension.equals(origBase, ignoreCase = true)
                } == true) ||
                loadedExternalSubtitles.any {
                    it == filePath || File(it).name.equals(origTitle, ignoreCase = true) ||
                    File(it).nameWithoutExtension.equals(origBase, ignoreCase = true)
                }
            }

            if (existingTrack != null) {
                val existingSource = subtitleTrackSourcePaths[existingTrack.id]
                val isSameFile = existingSource == filePath || loadedExternalSubtitles.contains(filePath) ||
                    (file != null && existingSource != null && File(existingSource).name.equals(file.name, ignoreCase = true))
                if (isSameFile) {
                    if (select) {
                        setSubtitleTrack(existingTrack.id)
                        setSubtitleVisibility(true)
                    }
                    return existingTrack.id
                } else {
                    // If user is adding a new file that replaces the previous one with the same name, remove the old one first
                    removeSubtitleTrack(existingTrack.id)
                }
            }

            externalSubtitlesMap[filePath] = origTitle
            if (file != null) {
                externalSubtitlesMap[file.name] = origTitle
                externalSubtitlesMap[file.nameWithoutExtension] = origTitle
            }

            if (isStream) {
                if (!loadedExternalSubtitles.contains(filePath)) {
                    loadedExternalSubtitles.add(filePath)
                }
                isSubtitleExplicitlyDisabled = false
                view.mpv.setPropertyBoolean("sub-visibility", true)
                view.mpv.setPropertyString("sub-ass", "yes")
                view.mpv.command("sub-add", filePath, if (select) "select" else "auto", origTitle)
                refreshTracksAndChapters()
                val activeSid = try { view.mpv.getPropertyInt("sid") } catch (_: Throwable) { null }
                    ?: _subtitleTracks.value.firstOrNull { it.originalFilename == origTitle || it.title == origTitle }?.id
                    ?: _subtitleTracks.value.lastOrNull()?.id
                if (select && activeSid != null && activeSid > 0) {
                    lastActiveSubtitleTrackId = activeSid
                    return activeSid
                }
                return _subtitleTracks.value.lastOrNull()?.id
            }

            val ctx = context ?: view.context

            // Sync fonts with currently playing video and any embedded attachments synchronously
            val fontsDir = SubtitleFontManager.syncFonts(ctx, currentFilePath ?: file?.parentFile?.absolutePath)
            try {
                val count = view.mpv.getPropertyInt("track-list/count") ?: 0
                for (i in 0 until count) {
                    val type = view.mpv.getPropertyString("track-list/$i/type") ?: ""
                    val title = view.mpv.getPropertyString("track-list/$i/title") ?: ""
                    val filename = view.mpv.getPropertyString("track-list/$i/filename") ?: ""
                    if (type.equals("attachment", ignoreCase = true) ||
                        title.endsWith(".ttf", ignoreCase = true) || title.endsWith(".otf", ignoreCase = true) ||
                        filename.endsWith(".ttf", ignoreCase = true) || filename.endsWith(".otf", ignoreCase = true)
                    ) {
                        if (title.isNotBlank()) SubtitleFontManager.registerVideoEmbeddedFont(title)
                        if (filename.isNotBlank()) SubtitleFontManager.registerVideoEmbeddedFont(filename)
                    }
                }
            } catch (_: Throwable) {}

            val localFile = file ?: return null
            val isNativeAss = localFile.extension.equals("ass", ignoreCase = true) || localFile.extension.equals("ssa", ignoreCase = true)
            val pathToAdd = UniversalSubtitleEngine.prepareSubtitleForPlayback(
                context = ctx,
                file = localFile,
                videoFps = _estimatedFps.value.takeIf { it > 0.0 } ?: 24.0,
                videoPath = currentFilePath
            )
            // Only our own converted text subtitles are considered generated ASS.
            // Native ASS/SSA is kept completely untouched until the user enables override.
            subtitleUsesGeneratedAss = !isNativeAss && File(pathToAdd).extension.equals("ass", ignoreCase = true)

            preparedSubtitleSources[File(pathToAdd).absolutePath] = localFile
            externalSubtitlesMap[pathToAdd] = origTitle
            externalSubtitlesMap[File(pathToAdd).name] = origTitle
            externalSubtitlesMap[File(pathToAdd).nameWithoutExtension] = origTitle

            if (!loadedExternalSubtitles.contains(pathToAdd)) {
                loadedExternalSubtitles.add(pathToAdd)
            }

            isSubtitleExplicitlyDisabled = false
            view.mpv.setPropertyString("sub-fonts-dir", fontsDir.absolutePath)
            view.mpv.setPropertyBoolean("embeddedfonts", true)
            view.mpv.setPropertyBoolean("sub-visibility", true)
            view.mpv.setPropertyString("sub-ass", "yes")
            view.mpv.setPropertyString("sub-ass-override", if (subtitleOverrideAssSsa) "force" else "no")
            view.mpv.setPropertyString("sub-use-margins", "yes")
            if (!subtitleOverrideAssSsa) {
                view.mpv.setPropertyString("sub-ass-force-style", "")
            }
            // Native ASS/SSA must reach libass with its own script intact.  Keep the override
            // mode neutral during the initial attach; the user's explicit override preference is
            // re-applied only after the real track id has been discovered.
            if (isNativeAss) {
                view.mpv.setPropertyString("sub-ass-override", "no")
                view.mpv.setPropertyString("sub-ass-force-style", "")
            }
            view.mpv.command("sub-add", pathToAdd, if (select) "select" else "auto")
            val addedTrackId = awaitAddedExternalTrack(view, pathToAdd)

            // The track mpv just created is THE answer; `sid` may still be the previous track's id
            // for a moment, which used to make the new upload look "not loaded".
            val activeSid = (if (addedTrackId > 0) addedTrackId else null)
                ?: try { view.mpv.getPropertyInt("sid") } catch (_: Throwable) { null }
                ?: _subtitleTracks.value.firstOrNull {
                    it.isExternal && (
                        it.originalFilename.equals(origTitle, ignoreCase = true) ||
                        it.title.equals(origTitle, ignoreCase = true)
                    )
                }?.id
                ?: _subtitleTracks.value.lastOrNull()?.id
            if (select && activeSid != null && activeSid > 0) {
                lastActiveSubtitleTrackId = activeSid
                setSubtitleTrack(activeSid)
                return activeSid
            }
            if (!select && addedTrackId > 0) return addedTrackId
            // MPV may expose a newly attached external track a few frames after `sub-add`.
            // The command already requests `select`; the recovery pass below catches the late
            // track-list update without making the UI wait synchronously.
            if (select && isNativeAss) {
                val generation = subtitleSelectionGeneration.incrementAndGet()
                playerScope.launch {
                    repeat(24) { attempt ->
                        if (isReleased || generation != subtitleSelectionGeneration.get()) return@launch
                        val resolvedId = withMpvLock(0) { v ->
                            val count = try { v.mpv.getPropertyInt("track-list/count") ?: 0 } catch (_: Throwable) { 0 }
                            var found = 0
                            for (i in 0 until count) {
                                if ((try { v.mpv.getPropertyString("track-list/$i/type") } catch (_: Throwable) { "" }) != "sub") continue
                                val ext = try { v.mpv.getPropertyString("track-list/$i/external-filename") ?: v.mpv.getPropertyString("track-list/$i/filename") ?: "" } catch (_: Throwable) { "" }
                                val normalized = if (ext.startsWith("file://")) Uri.parse(ext).path ?: ext else ext
                                if (normalized == File(pathToAdd).absolutePath || File(normalized).name.equals(File(pathToAdd).name, ignoreCase = true)) {
                                    found = try { v.mpv.getPropertyInt("track-list/$i/id") ?: 0 } catch (_: Throwable) { 0 }
                                    break
                                }
                            }
                            if (found > 0) {
                                try { v.mpv.setPropertyInt("sid", found) } catch (_: Throwable) {}
                                try { v.mpv.setPropertyBoolean("sub-visibility", true) } catch (_: Throwable) {}
                                try { v.mpv.setPropertyString("sub-ass", "yes") } catch (_: Throwable) {}
                                try { v.mpv.setPropertyString("sub-ass-override", "no") } catch (_: Throwable) {}
                                try { markSubtitleReload(); v.mpv.command("sub-reload") } catch (_: Throwable) {}
                            }
                            found
                        }
                        if (resolvedId > 0) {
                            lastActiveSubtitleTrackId = resolvedId
                            refreshTracksAndChapters()
                            return@launch
                        }
                        delay(if (attempt < 4) 35L else 75L)
                    }
                }
            }
            return _subtitleTracks.value.lastOrNull()?.id
        } catch (_: Throwable) {
            return null
        }
    }

    fun addExternalAudio(
        filePath: String,
        originalName: String? = null,
        language: String? = null,
        select: Boolean = false
    ): Int? {
        try {
            val view = mpvView ?: return null
            val isStream = filePath.startsWith("http://") || filePath.startsWith("https://")
            val file = if (!isStream) File(filePath) else null
            if (!isStream && (file == null || !file.exists() || file.length() == 0L)) return null
            val origTitle = originalName?.ifBlank { null } ?: file?.name ?: "Online Audio"
            externalAudiosMap[filePath] = origTitle
            if (!language.isNullOrBlank()) {
                externalAudiosLangMap[filePath] = language
            }
            if (file != null) {
                externalAudiosMap[file.name] = origTitle
                externalAudiosMap[file.nameWithoutExtension] = origTitle
                if (!language.isNullOrBlank()) {
                    externalAudiosLangMap[file.name] = language
                    externalAudiosLangMap[file.nameWithoutExtension] = language
                }
            }
            if (!loadedExternalAudios.contains(filePath)) {
                loadedExternalAudios.add(filePath)
            }
            val flag = if (select) "select" else "auto"
            if (!language.isNullOrBlank()) {
                view.mpv.command("audio-add", filePath, flag, origTitle, language)
            } else {
                view.mpv.command("audio-add", filePath, flag, origTitle)
            }
            refreshTracksAndChapters()

            val activeAid = try { view.mpv.getPropertyInt("aid") } catch (_: Throwable) { null }
                ?: _audioTracks.value.firstOrNull { it.originalFilename == origTitle || it.title == origTitle }?.id
                ?: _audioTracks.value.lastOrNull()?.id
            if (activeAid != null && activeAid > 0) {
                return activeAid
            }
            return _audioTracks.value.lastOrNull()?.id
        } catch (_: Throwable) {
            return null
        }
    }

    fun seekToChapter(chapter: PlayerVideoChapter) {
        seekTo(chapter.timeMs)
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun release() {
        // Idempotent: this can now be called eagerly on back-press AND again from the
        // Compose onDispose safety net. Without this guard the second call could destroy
        // an already-destroyed native surface.
        if (isReleased) return
        val viewToDestroy: VideoPlayerMpvView? = synchronized(releaseGuard) {
            if (isReleased) return
            isReleased = true
            val v = mpvView
            mpvView = null
            v
        }
        // Everything below is non-blocking on purpose: release() runs on the UI thread when the
        // user presses back. Destroying mpv (and waiting for the native lock) used to happen right
        // here, so leaving and re-opening a video quickly could freeze the app until restart when
        // mpv was still busy opening the file. The slow native teardown now runs on a dedicated
        // background thread, one at a time, in order.
        pollingJob?.cancel()
        pollingJob = null
        advancedAssJob?.cancel()
        try {
            playerScope.cancel()
        } catch (_: Throwable) {}
        AppStorageManager.setActivePlaybackPath(null)
        if (viewToDestroy == null) return
        nativeReleaseExecutor.execute {
            // Take the native lock so no Advanced ASS sync is inside an mpv call while we destroy it.
            synchronized(advancedAssNativeLock) {
                try {
                    try {
                        viewToDestroy.mpv.setPropertyBoolean("pause", true)
                    } catch (_: Throwable) {}
                    viewToDestroy.destroy()
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
        }
    }
}

// Single background thread that performs the slow native mpv teardown (see release()).
private val nativeReleaseExecutor: java.util.concurrent.ExecutorService =
    java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "lumora-mpv-release").apply { isDaemon = true }
    }

private fun formatPlaybackTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val hours = totalSec / 3600
    val mins = (totalSec % 3600) / 60
    val secs = totalSec % 60
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

private fun formatPreciseTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val hours = totalSec / 3600
    val mins = (totalSec % 3600) / 60
    val secs = totalSec % 60
    val centis = (ms % 1000) / 10
    return String.format(Locale.US, "%d:%02d:%02d.%02d", hours, mins, secs, centis)
}

private fun formatLengthTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val hours = totalSec / 3600
    val mins = (totalSec % 3600) / 60
    val secs = totalSec % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

@Composable
fun MpvPlayerPreviewSection(
    videoUri: Uri?,
    videoFileName: String,
    isPlaying: Boolean,
    onPlayingChange: (Boolean) -> Unit,
    currentPlaybackMs: Long,
    onCurrentPlaybackMsChange: (Long) -> Unit,
    videoDurationMs: Long,
    onVideoDurationMsChange: (Long) -> Unit,
    videoFps: Double = 23.976,
    videoQualityLabel: String = "1080p FHD",
    videoResolutionLabel: String = "1920x1080",
    cues: List<SubtitleCue>,
    onSelectVideoClick: () -> Unit,
    onLoadSampleVideoClick: (() -> Unit)?,
    onRemoveVideoClick: () -> Unit,
    isDark: Boolean,
    surfaceColor: Color,
    borderColor: Color,
    primaryText: Color,
    secondaryText: Color,
    hwAccelMode: HwAccelMode = HwAccelMode.FORCE,
    onFrameClick: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val controller = remember { MpvPlayerController() }
    val posMs by controller.currentPositionMs.collectAsState()
    val durMs by controller.durationMs.collectAsState()
    val playingState by controller.isPlaying.collectAsState()
    val mutedState by controller.isMuted.collectAsState()
    val speedState by controller.playbackSpeed.collectAsState()
    val loopState by controller.isLooping.collectAsState()
    val errorState by controller.errorMessage.collectAsState()
    val perfStatus by controller.performanceStatus.collectAsState()
    val vWidth by controller.videoWidth.collectAsState()
    val vHeight by controller.videoHeight.collectAsState()

    val videoAspectRatio = remember(vWidth, vHeight, videoResolutionLabel) {
        if (vWidth > 0 && vHeight > 0) {
            (vWidth.toFloat() / vHeight.toFloat()).coerceIn(0.2f, 5.0f)
        } else if (videoResolutionLabel.isNotBlank() && videoResolutionLabel.contains("x")) {
            val parts = videoResolutionLabel.split("x")
            val w = parts.getOrNull(0)?.trim()?.toFloatOrNull()
            val h = parts.getOrNull(1)?.trim()?.toFloatOrNull()
            if (w != null && h != null && w > 0f && h > 0f) {
                (w / h).coerceIn(0.2f, 5.0f)
            } else {
                16f / 9f
            }
        } else if (videoResolutionLabel.contains(":")) {
            val parts = videoResolutionLabel.split(":")
            val w = parts.getOrNull(0)?.trim()?.toFloatOrNull()
            val h = parts.getOrNull(1)?.trim()?.toFloatOrNull()
            if (w != null && h != null && w > 0f && h > 0f) {
                (w / h).coerceIn(0.2f, 5.0f)
            } else {
                16f / 9f
            }
        } else {
            16f / 9f
        }
    }

    LaunchedEffect(hwAccelMode) {
        controller.setHwAccelMode(hwAccelMode)
    }

    var resolvedPlayablePath by remember(videoUri) { mutableStateOf("") }

    // Resolve URI to playable file path
    LaunchedEffect(videoUri) {
        if (videoUri != null) {
            val path = getPlayableFilePath(context, videoUri)
            resolvedPlayablePath = path
            if (path.isNotBlank()) {
                controller.loadVideo(path)
            }
        } else {
            resolvedPlayablePath = ""
        }
    }

    // Key derived from full cues state + style map fingerprint so any property change triggers instant re-render
    val styleFingerprint = com.example.ui.screens.AssParser.getStyleMapFingerprint()
    val cuesContentKey = remember(cues.size, cues.fold(17L) { acc, cue -> acc * 31L + cue.hashCode() }, styleFingerprint) {
        "${cues.size}_${cues.fold(17L) { acc, cue -> acc * 31L + cue.hashCode() }}_${styleFingerprint.hashCode()}"
    }

    // Keep Subtitles updated in MPV player
    LaunchedEffect(cuesContentKey, resolvedPlayablePath) {
        if (resolvedPlayablePath.isNotBlank()) {
            delay(30)
            controller.updateSubtitles(cues, context)
        }
    }

    // Active cues for live Compose overlay over the video player
    val activeCues = remember(posMs, cuesContentKey) {
        cues.filter { cue ->
            posMs in cue.startTimeMs..cue.endTimeMs
        }
    }

    // Sync state upward to the parent screen smoothly
    LaunchedEffect(posMs) {
        if (kotlin.math.abs(posMs - currentPlaybackMs) >= 100L) {
            onCurrentPlaybackMsChange(posMs)
        }
    }

    // Sync seek state downward from the parent screen when user taps dialogue cards or timestamp buttons
    LaunchedEffect(currentPlaybackMs) {
        val delta = kotlin.math.abs(posMs - currentPlaybackMs)
        val threshold = if (playingState) 1200L else 100L
        if (delta > threshold) {
            controller.seekTo(currentPlaybackMs)
        }
    }

    LaunchedEffect(durMs) {
        if (durMs > 0L && durMs != videoDurationMs) {
            onVideoDurationMsChange(durMs)
        }
    }

    LaunchedEffect(playingState) {
        if (playingState != isPlaying) {
            onPlayingChange(playingState)
        }
    }

    // Sync playing state downward from the parent screen
    LaunchedEffect(isPlaying) {
        if (isPlaying != playingState) {
            if (isPlaying) controller.play() else controller.pause()
        }
    }

    // Lifecycle observer to pause playback when activity/screen pauses
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                controller.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.release()
        }
    }

    val currentFrame = remember(posMs, videoFps) {
        (posMs * videoFps / 1000.0).toLong().coerceAtLeast(0L)
    }
    val totalFrames = remember(durMs, videoFps) {
        (durMs * videoFps / 1000.0).toLong().coerceAtLeast(1L)
    }

    Column(modifier = modifier) {
        // 1. CLEAN VIDEO CONTAINER (Dynamic Aspect Ratio matching video size) - No HUD or overlays blocking video
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .aspectRatio(videoAspectRatio)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (videoUri == null) {
                        onSelectVideoClick()
                    } else {
                        controller.togglePlayPause()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (videoUri == null || resolvedPlayablePath.isBlank()) {
                // TAP TO SELECT VIDEO EMPTY STATE
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onSelectVideoClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        StyledIcon(
                                imageVector = Icons.Outlined.VideoLibrary,
                            contentDescription = "Select Video",
                            tint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap to Load Video",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            } else {
                // REAL MPV ANDROID VIEW
                AndroidView(
                    factory = { ctx ->
                        VideoPlayerMpvView(ctx).also { mpv ->
                            mpv.initialize(ctx.filesDir.path, ctx.cacheDir.path)
                            controller.attach(mpv, coroutineScope)
                            if (resolvedPlayablePath.isNotBlank()) {
                                controller.loadVideo(resolvedPlayablePath)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Transparent Touch Overlay to ensure touching/tapping video toggles Play/Pause
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            controller.togglePlayPause()
                        }
                )

                // Error Banner
                if (errorState != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEF4444).copy(alpha = 0.95f))
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorState ?: "",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { controller.clearError() },
                                modifier = Modifier.size(20.dp)
                            ) {
                                StyledIcon(Icons.Outlined.Close, contentDescription = "Close Error", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }

        if (videoUri != null && resolvedPlayablePath.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))

            // 2. TIMELINE SEEKBAR SLIDER WITH STEP FRAME BUTTONS (< and >)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left 1-Frame Back Step Button (Slightly larger size, sleek icon)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                        .border(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1), CircleShape)
                        .clickable { controller.stepFrame(false) },
                    contentAlignment = Alignment.Center
                ) {
                    StyledIcon(
                                imageVector = Icons.Outlined.ChevronLeft,
                        contentDescription = "Step Back 1 Frame",
                        tint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Slim Thinner Seekbar Slider
                Slider(
                    value = posMs.toFloat().coerceIn(0f, (durMs.toFloat().coerceAtLeast(1f))),
                    valueRange = 0f..(durMs.toFloat().coerceAtLeast(1f)),
                    onValueChange = { valMs ->
                        controller.seekTo(valMs.toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00E5FF),
                        activeTrackColor = Color(0xFF00E5FF),
                        inactiveTrackColor = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                )

                // Right 1-Frame Forward Step Button (Slightly larger size, sleek icon)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                        .border(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1), CircleShape)
                        .clickable { controller.stepFrame(true) },
                    contentAlignment = Alignment.Center
                ) {
                    StyledIcon(
                                imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = "Step Forward 1 Frame",
                        tint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 4. CLEAN BLACK CONTROL HUD CARD (Current Time / Total Duration)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF070D1B))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                // Large Timecode 0:00:00.00 | Frame: 2453 | Length: 24:56
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Large Timecode 0:00:00.00
                    Column(
                        modifier = Modifier.clickable {
                            val timeStr = formatPreciseTime(posMs)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Timecode", timeStr)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied: $timeStr", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text(
                            text = "PLAYBACK TIMECODE",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = formatPreciseTime(posMs),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00E5FF)
                        )
                    }

                    // Right: Current Frame & Total Length
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.clickable {
                                onFrameClick?.invoke(posMs)
                            }
                        ) {
                            Text(
                                text = "CURRENT FRAME",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = "Frame: $currentFrame",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF00E5FF)
                            )
                        }

                        // Divider line
                        Box(
                            modifier = Modifier
                                .height(24.dp)
                                .width(1.dp)
                                .background(Color(0xFF1E293B))
                        )

                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.clickable {
                                val timeStr = formatPreciseTime(durMs)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Total Length", timeStr)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied: $timeStr", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(
                                text = "TOTAL LENGTH",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = formatLengthTime(durMs),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Coalesces rapid edits (typing digits, dragging the colour wheel) into ONE script reload.
private const val ADVANCED_ASS_DEBOUNCE_MS = 280L
private const val ADVANCED_ASS_CACHE_DIR = "lumora_adv_ass"
private const val RAW_SUBTITLE_CACHE_DIR = "lumora_raw_sub_edit"
private const val RAW_PREVIEW_DEBOUNCE_MS = 120L
