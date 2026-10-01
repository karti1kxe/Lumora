package com.example.ui.state

import android.content.Context
import android.content.SharedPreferences
import com.example.player.AudioChannelMode
import com.example.player.TrackAudioConfig
import org.json.JSONObject

/**
 * Centralized player settings. Every value is persisted and consumed by the video player.
 * Defaults intentionally match conservative/native Android behaviour.
 */
data class PlayerSettings(
    val orientation: String = "FREE",
    val savePositionOnQuit: Boolean = true,
    val closeAfterEnd: Boolean = false,
    val autoplayNext: Boolean = false,
    val repeatPlaylistAfterLast: Boolean = false,
    val enableNextPrevious: Boolean = true,
    val rememberBrightness: Boolean = true,
    val autoPictureInPicture: Boolean = false,
    val keepScreenOnPaused: Boolean = false,
    val autoplayAfterUnlock: Boolean = false,
    val showMediaInfoChooser: Boolean = true,

    val showStatusBar: Boolean = false,
    val showNavigationBar: Boolean = false,
    val safeAreaWindow: Boolean = true,
    val portraitPlaybackButtonsPosition: String = "CENTER", // "CENTER", "BOTTOM"
    val landscapePlaybackButtonsPosition: String = "CENTER", // "CENTER", "BOTTOM"
    val hidePlayerButtonsBackground: Boolean = false,
    val alwaysDarkPlayerButtonBackground: Boolean = true,
    val hidePlayerControlsTimeoutMs: Int = 4000,
    val timeNetworkClockFormat: String = "SYSTEM", // "SYSTEM", "12_HOUR", "24_HOUR"
    val reduceAnimation: Boolean = false,
    val showLoadingCircle: Boolean = true,
    val allowGesturesInPanels: Boolean = true,
    val swapVolumeBrightness: Boolean = false,
    // When true the volume / brightness indicator pops up on the OPPOSITE side of the screen from
    // the side that was swiped (swipe left -> indicator on the right, and vice versa).
    val gestureHudOppositeSide: Boolean = false,

    val rippleOnDoubleTap: Boolean = true,
    val showSeekTime: Boolean = true,
    val showBufferedRange: Boolean = true,
    val preciseSeeking: Boolean = true,
    val thumbFastPreview: Boolean = false,

    val customSkipDuration: Int = 90,
    val onlineSkipMarkers: Boolean = false,
    val markerProvider: String = "ANISKIP",
    val detectChapterIntroOutro: Boolean = true,
    val customOpeningKeywords: String = "Opening,OP,Intro",
    val customEndingKeywords: String = "Ending,ED,Outro,Credits",
    val autoSkipIntro: Boolean = false,
    val autoSkipOutro: Boolean = false,

    val screenshotFormat: String = "PNG",
    val subtitlesInScreenshots: Boolean = true,
    val screenshotFilenameTemplate: String = "{title}_{time}",
    val jpegWebpQuality: Int = 90,
    val pngCompression: Int = 6,

    val volumeSliderOverlay: Boolean = true,
    val brightnessSliderOverlay: Boolean = true,
    val holdSpeedOverlay: Boolean = true,
    val aspectRatioFeedback: Boolean = true,
    val zoomLevelFeedback: Boolean = true,
    val repeatShuffleFeedback: Boolean = true,
    val actionFeedbackPills: Boolean = true,
    val defaultAudioDelayMs: Long = 0L,
    val defaultSubtitleDelayMs: Long = 0L,

    // Audio Equalizer & Processing settings
    val equalizerEnabled: Boolean = false,
    val equalizer60Hz: Float = 0f,
    val equalizer230Hz: Float = 0f,
    val equalizer910Hz: Float = 0f,
    val equalizer3600Hz: Float = 0f,
    val equalizer14000Hz: Float = 0f,
    val equalizerVolumeBoostDb: Float = 0f,
    val equalizerPreset: String = "Flat",
    val audioChannelMode: String = "auto-safe",
    val volumeNormalization: Boolean = false,
    val dynamicRangeCompression: Boolean = false,
    val voiceEnhancement: Boolean = false,
    val surroundSoundMode: String = "OFF",
    val trackAudioConfigsJson: String = "{}",

    // Video Filter & Manual Adjustments settings
    val videoFilterPreset: String = "none",
    val videoBrightness: Int = 0,
    val videoContrast: Int = 0,
    val videoSaturation: Int = 0,
    val videoGamma: Int = 0,
    val videoSharpness: Int = 0,
    val videoHue: Int = 0,
    val videoTemperature: Int = 0,
    val videoTint: Int = 0,
    val videoDeband: Boolean = false,

    // Gesture settings
    val brightnessGestures: Boolean = true,
    val volumeGestures: Boolean = true,
    val pinchToZoom: Boolean = true,
    val pinchToZoomSubtitles: Boolean = true,
    val swipeSubtitlesToSeekDialog: Boolean = true,
    val horizontalSwipeToSeek: Boolean = true,
    val swipeUpCenterForPlaylist: Boolean = true,
    val horizontalSwipeSensitivity: Int = 10,
    val holdMultiSpeed: Boolean = true,
    val holdSpeedMultiplier: Double = 2.0,
    val dynamicSpeedOverlay: Boolean = true,
    val doubleTapSeekDuration: Int = 10,
    val doubleTapSeekAreaWidth: Int = 35,
    val doubleTapLeftAction: String = "SEEK_BACK",
    val doubleTapCenterAction: String = "PLAY_PAUSE",
    val doubleTapRightAction: String = "SEEK_FORWARD",
    val enableDoubleTap: Boolean = true,
    val enableSingleTap: Boolean = false,
    val singleTapSeekDuration: Int = 10,
    val singleTapSeekAreaWidth: Int = 35,
    val singleTapLeftAction: String = "NONE",
    val singleTapCenterAction: String = "NONE",
    val singleTapRightAction: String = "NONE",
    val singleTapCenterGesture: Boolean = false,
    val holdDragMovesSubtitles: Boolean = true,
    val mediaPreviousControl: Boolean = true,
    val mediaPlayPauseControl: Boolean = true,
    val mediaNextControl: Boolean = true,

    // Subtitle settings
    val preferredSubtitleLanguages: String = "",
    val applySubtitleLanguageToSelectedContentOnly: Boolean = false,
    val selectedSubtitleFolders: Set<String> = emptySet(),
    val selectedSubtitleVideos: Set<String> = emptySet(),
    val subtitleSignsAndSongs: Boolean = false,
    val dualSubtitlesEnabled: Boolean = false,
    val preferredSecondarySubtitleLanguages: String = "",
    val detectSubtitlesByFilename: Boolean = true,
    val autoLoadExternalSubtitles: Boolean = true,
    val overrideAssSsaSubtitles: Boolean = false,
    val scaleSubtitlesByWindow: Boolean = true,
    val subtitleFontDirectoryUri: String = "",
    val selectedSubtitleFont: String = "",
    val showVideoEmbeddedSubtitleFonts: Boolean = true,
    val subtitleFontsReloadNonce: Long = 0L,

    // Live subtitle appearance/editor settings. These are renderer settings,
    // intentionally separate from the app UI theme/transparency and subtitle delay.
    val subtitleBold: Boolean = true,
    val subtitleItalic: Boolean = false,
    val subtitleUnderline: Boolean = false,
    val subtitleAlignment: String = "center",
    val subtitleFontSize: Float = 52f,
    val subtitleBorderStyle: String = "outline_shadow",
    val subtitleBorderSize: Float = 3f,
    val subtitleShadowOffset: Float = 1.5f,
    val subtitleShadowOffsetX: Float = 2f,
    val subtitleShadowOffsetY: Float = 2f,
    val subtitleShadowBlur: Float = 4f,
    val subtitleBackgroundPadding: Float = 8f,
    val subtitleBackgroundCornerRadius: Float = 8f,
    val subtitleLetterSpacing: Float = 0f,
    val subtitleTextColor: Long = 0xFFFFFFFFL,
    val subtitleBorderColor: Long = 0xFF000000L,
    val subtitleBackgroundColor: Long = 0x99000000L,
    val subtitleShadowColor: Long = 0xBF000000L,
    val subtitleScale: Float = 1.0f,
    val subtitlePosition: Float = 100f,
    val subtitlePositionDirection: String = "BOTTOM_TO_TOP",
    val subtitleBlendWithVideo: Boolean = false,

    // Advanced ASS/SSA per-style editor. When [advancedAssEnabled] is false every existing
    // subtitle behaviour is unchanged. [assStyleOverridesJson] holds the per-subtitle style
    // overrides (see AssOverridesStore in player/AdvancedAssStyleEngine.kt), keyed by subtitle identity.
    val advancedAssEnabled: Boolean = false,
    // "View Raw [Script Info] & Subtitle text": shows the full text of the active subtitle (any
    // format) in an editor section. Only the toggle is persisted here; applied text edits are kept
    // per subtitle (see AssRawTextStore + AssSubtitleOverrides.rawRevision).
    val rawSubtitleEditorEnabled: Boolean = false,
    val assStyleOverridesJson: String = ""
) {
    companion object {
        private const val PREF = "lumora_player_settings"
        private const val JSON = "settings"

        fun load(context: Context): PlayerSettings {
            val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(JSON, null) ?: return PlayerSettings()
            return try {
                val o = JSONObject(raw)
                PlayerSettings(
                    orientation = o.optString("orientation", "FREE"),
                    savePositionOnQuit = o.optBoolean("savePositionOnQuit", true),
                    closeAfterEnd = o.optBoolean("closeAfterEnd", false),
                    autoplayNext = o.optBoolean("autoplayNext", false),
                    repeatPlaylistAfterLast = o.optBoolean("repeatPlaylistAfterLast", false),
                    enableNextPrevious = o.optBoolean("enableNextPrevious", true),
                    rememberBrightness = o.optBoolean("rememberBrightness", true),
                    autoPictureInPicture = o.optBoolean("autoPictureInPicture", false),
                    keepScreenOnPaused = o.optBoolean("keepScreenOnPaused", false),
                    autoplayAfterUnlock = o.optBoolean("autoplayAfterUnlock", false),
                    showMediaInfoChooser = o.optBoolean("showMediaInfoChooser", true),
                    showStatusBar = o.optBoolean("showStatusBar", false),
                    showNavigationBar = o.optBoolean("showNavigationBar", false),
                    safeAreaWindow = o.optBoolean("safeAreaWindow", true),
                    portraitPlaybackButtonsPosition = o.optString("portraitPlaybackButtonsPosition", "CENTER"),
                    landscapePlaybackButtonsPosition = o.optString("landscapePlaybackButtonsPosition", "CENTER"),
                    hidePlayerButtonsBackground = o.optBoolean("hidePlayerButtonsBackground", false),
                    alwaysDarkPlayerButtonBackground = o.optBoolean("alwaysDarkPlayerButtonBackground", true),
                    hidePlayerControlsTimeoutMs = o.optInt("hidePlayerControlsTimeoutMs", 4000).coerceIn(500, 10000),
                    timeNetworkClockFormat = o.optString("timeNetworkClockFormat", "SYSTEM"),
                    reduceAnimation = o.optBoolean("reduceAnimation", false),
                    showLoadingCircle = o.optBoolean("showLoadingCircle", true),
                    allowGesturesInPanels = o.optBoolean("allowGesturesInPanels", true),
                    swapVolumeBrightness = o.optBoolean("swapVolumeBrightness", false),
                    gestureHudOppositeSide = o.optBoolean("gestureHudOppositeSide", false),
                    rippleOnDoubleTap = o.optBoolean("rippleOnDoubleTap", true),
                    showSeekTime = o.optBoolean("showSeekTime", true),
                    showBufferedRange = o.optBoolean("showBufferedRange", true),
                    preciseSeeking = o.optBoolean("preciseSeeking", true),
                    thumbFastPreview = o.optBoolean("thumbFastPreview", false),
                    customSkipDuration = o.optInt("customSkipDuration", 90).coerceIn(1, 600),
                    onlineSkipMarkers = o.optBoolean("onlineSkipMarkers", false),
                    markerProvider = o.optString("markerProvider", "ANISKIP"),
                    detectChapterIntroOutro = o.optBoolean("detectChapterIntroOutro", true),
                    customOpeningKeywords = o.optString("customOpeningKeywords", "Opening,OP,Intro"),
                    customEndingKeywords = o.optString("customEndingKeywords", "Ending,ED,Outro,Credits"),
                    autoSkipIntro = o.optBoolean("autoSkipIntro", false),
                    autoSkipOutro = o.optBoolean("autoSkipOutro", false),
                    screenshotFormat = o.optString("screenshotFormat", "PNG"),
                    subtitlesInScreenshots = o.optBoolean("subtitlesInScreenshots", true),
                    screenshotFilenameTemplate = o.optString("screenshotFilenameTemplate", "{title}_{time}"),
                    jpegWebpQuality = o.optInt("jpegWebpQuality", 90).coerceIn(1, 100),
                    pngCompression = o.optInt("pngCompression", 6).coerceIn(0, 9),
                    volumeSliderOverlay = o.optBoolean("volumeSliderOverlay", true),
                    brightnessSliderOverlay = o.optBoolean("brightnessSliderOverlay", true),
                    holdSpeedOverlay = o.optBoolean("holdSpeedOverlay", true),
                    aspectRatioFeedback = o.optBoolean("aspectRatioFeedback", true),
                    zoomLevelFeedback = o.optBoolean("zoomLevelFeedback", true),
                    repeatShuffleFeedback = o.optBoolean("repeatShuffleFeedback", true),
                    actionFeedbackPills = o.optBoolean("actionFeedbackPills", true),
                    defaultAudioDelayMs = o.optLong("defaultAudioDelayMs", 0L),
                    defaultSubtitleDelayMs = o.optLong("defaultSubtitleDelayMs", 0L),
                    equalizerEnabled = o.optBoolean("equalizerEnabled", false),
                    equalizer60Hz = o.optDouble("equalizer60Hz", 0.0).toFloat(),
                    equalizer230Hz = o.optDouble("equalizer230Hz", 0.0).toFloat(),
                    equalizer910Hz = o.optDouble("equalizer910Hz", 0.0).toFloat(),
                    equalizer3600Hz = o.optDouble("equalizer3600Hz", 0.0).toFloat(),
                    equalizer14000Hz = o.optDouble("equalizer14000Hz", 0.0).toFloat(),
                    equalizerVolumeBoostDb = o.optDouble("equalizerVolumeBoostDb", 0.0).toFloat(),
                    equalizerPreset = o.optString("equalizerPreset", "Flat"),
                    audioChannelMode = o.optString("audioChannelMode", "auto-safe"),
                    volumeNormalization = o.optBoolean("volumeNormalization", false),
                    dynamicRangeCompression = o.optBoolean("dynamicRangeCompression", false),
                    voiceEnhancement = o.optBoolean("voiceEnhancement", false),
                    surroundSoundMode = o.optString("surroundSoundMode", "OFF"),
                    trackAudioConfigsJson = o.optString("trackAudioConfigsJson", "{}"),
                    videoFilterPreset = o.optString("videoFilterPreset", "none"),
                    videoBrightness = o.optInt("videoBrightness", 0).coerceIn(-100, 100),
                    videoContrast = o.optInt("videoContrast", 0).coerceIn(-100, 100),
                    videoSaturation = o.optInt("videoSaturation", 0).coerceIn(-100, 100),
                    videoGamma = o.optInt("videoGamma", 0).coerceIn(-100, 100),
                    videoSharpness = o.optInt("videoSharpness", 0).coerceIn(0, 100),
                    videoHue = o.optInt("videoHue", 0).coerceIn(-100, 100),
                    videoTemperature = o.optInt("videoTemperature", 0).coerceIn(-100, 100),
                    videoTint = o.optInt("videoTint", 0).coerceIn(-100, 100),
                    videoDeband = o.optBoolean("videoDeband", false),
                    brightnessGestures = o.optBoolean("brightnessGestures", true),
                    volumeGestures = o.optBoolean("volumeGestures", true),
                    pinchToZoom = o.optBoolean("pinchToZoom", true),
                    pinchToZoomSubtitles = o.optBoolean("pinchToZoomSubtitles", true),
                    swipeSubtitlesToSeekDialog = o.optBoolean("swipeSubtitlesToSeekDialog", true),
                    horizontalSwipeToSeek = o.optBoolean("horizontalSwipeToSeek", true),
                    swipeUpCenterForPlaylist = o.optBoolean("swipeUpCenterForPlaylist", true),
                    horizontalSwipeSensitivity = o.optInt("horizontalSwipeSensitivity", 10).coerceIn(1, 100),
                    holdMultiSpeed = o.optBoolean("holdMultiSpeed", true),
                    holdSpeedMultiplier = o.optDouble("holdSpeedMultiplier", 2.0).coerceIn(1.1, 4.0),
                    dynamicSpeedOverlay = o.optBoolean("dynamicSpeedOverlay", true),
                    doubleTapSeekDuration = o.optInt("doubleTapSeekDuration", 10).coerceIn(1, 120),
                    doubleTapSeekAreaWidth = o.optInt("doubleTapSeekAreaWidth", 35).coerceIn(10, 50),
                    doubleTapLeftAction = o.optString("doubleTapLeftAction", "SEEK_BACK"),
                    doubleTapCenterAction = o.optString("doubleTapCenterAction", "PLAY_PAUSE"),
                    doubleTapRightAction = o.optString("doubleTapRightAction", "SEEK_FORWARD"),
                    enableDoubleTap = o.optBoolean("enableDoubleTap", true),
                    enableSingleTap = o.optBoolean("enableSingleTap", false),
                    singleTapSeekDuration = o.optInt("singleTapSeekDuration", 10).coerceIn(1, 120),
                    singleTapSeekAreaWidth = o.optInt("singleTapSeekAreaWidth", 35).coerceIn(10, 50),
                    singleTapLeftAction = o.optString("singleTapLeftAction", "NONE"),
                    singleTapCenterAction = o.optString("singleTapCenterAction", "NONE"),
                    singleTapRightAction = o.optString("singleTapRightAction", "NONE"),
                    singleTapCenterGesture = o.optBoolean("singleTapCenterGesture", false),
                    holdDragMovesSubtitles = o.optBoolean("holdDragMovesSubtitles", true),
                    mediaPreviousControl = o.optBoolean("mediaPreviousControl", true),
                    mediaPlayPauseControl = o.optBoolean("mediaPlayPauseControl", true),
                    mediaNextControl = o.optBoolean("mediaNextControl", true),
                    preferredSubtitleLanguages = o.optString("preferredSubtitleLanguages", ""),
                    applySubtitleLanguageToSelectedContentOnly = o.optBoolean("applySubtitleLanguageToSelectedContentOnly", false),
                    selectedSubtitleFolders = parseStringSet(o.optJSONArray("selectedSubtitleFolders")),
                    selectedSubtitleVideos = parseStringSet(o.optJSONArray("selectedSubtitleVideos")),
                    subtitleSignsAndSongs = o.optBoolean("subtitleSignsAndSongs", false),
                    dualSubtitlesEnabled = o.optBoolean("dualSubtitlesEnabled", false),
                    preferredSecondarySubtitleLanguages = o.optString("preferredSecondarySubtitleLanguages", ""),
                    detectSubtitlesByFilename = o.optBoolean("detectSubtitlesByFilename", true),
                    autoLoadExternalSubtitles = o.optBoolean("autoLoadExternalSubtitles", true),
                    overrideAssSsaSubtitles = o.optBoolean("overrideAssSsaSubtitles", false),
                    scaleSubtitlesByWindow = o.optBoolean("scaleSubtitlesByWindow", true),
                    subtitleFontDirectoryUri = o.optString("subtitleFontDirectoryUri", ""),
                    selectedSubtitleFont = o.optString("selectedSubtitleFont", ""),
                    showVideoEmbeddedSubtitleFonts = o.optBoolean("showVideoEmbeddedSubtitleFonts", true),
                    subtitleFontsReloadNonce = o.optLong("subtitleFontsReloadNonce", 0L),
                    subtitleBold = o.optBoolean("subtitleBold", true),
                    subtitleItalic = o.optBoolean("subtitleItalic", false),
                    subtitleUnderline = o.optBoolean("subtitleUnderline", false),
                    subtitleAlignment = o.optString("subtitleAlignment", "center"),
                    subtitleFontSize = o.optDouble("subtitleFontSize", 52.0).toFloat().coerceIn(8f, 120f),
                    subtitleBorderStyle = o.optString("subtitleBorderStyle", "outline_shadow"),
                    subtitleBorderSize = o.optDouble("subtitleBorderSize", 3.0).toFloat().coerceIn(0f, 20f),
                    subtitleShadowOffset = o.optDouble("subtitleShadowOffset", 1.5).toFloat().coerceIn(0f, 20f),
                    subtitleShadowOffsetX = o.optDouble("subtitleShadowOffsetX", 2.0).toFloat().coerceIn(-15f, 15f),
                    subtitleShadowOffsetY = o.optDouble("subtitleShadowOffsetY", 2.0).toFloat().coerceIn(-15f, 15f),
                    subtitleShadowBlur = o.optDouble("subtitleShadowBlur", 4.0).toFloat().coerceIn(0f, 20f),
                    subtitleBackgroundPadding = o.optDouble("subtitleBackgroundPadding", 8.0).toFloat().coerceIn(0f, 30f),
                    subtitleBackgroundCornerRadius = o.optDouble("subtitleBackgroundCornerRadius", 8.0).toFloat().coerceIn(0f, 30f),
                    subtitleLetterSpacing = o.optDouble("subtitleLetterSpacing", 0.0).toFloat().coerceIn(-5f, 20f),
                    subtitleTextColor = o.optLong("subtitleTextColor", 0xFFFFFFFFL),
                    subtitleBorderColor = o.optLong("subtitleBorderColor", 0xFF000000L),
                    subtitleBackgroundColor = o.optLong("subtitleBackgroundColor", 0x99000000L),
                    subtitleShadowColor = o.optLong("subtitleShadowColor", 0xBF000000L),
                    subtitleScale = o.optDouble("subtitleScale", 1.0).toFloat().coerceIn(0.5f, 3f),
                    subtitlePosition = o.optDouble("subtitlePosition", 100.0).toFloat().coerceIn(0f, 150f),
                    subtitlePositionDirection = o.optString("subtitlePositionDirection", "BOTTOM_TO_TOP"),
                    subtitleBlendWithVideo = o.optBoolean("subtitleBlendWithVideo", false),
                    advancedAssEnabled = o.optBoolean("advancedAssEnabled", false),
                    rawSubtitleEditorEnabled = o.optBoolean("rawSubtitleEditorEnabled", false),
                    assStyleOverridesJson = o.optString("assStyleOverridesJson", "")
                )
            } catch (_: Throwable) { PlayerSettings() }
        }

        fun save(context: Context, value: PlayerSettings) {
            val o = JSONObject()
            o.put("orientation", value.orientation)
            o.put("savePositionOnQuit", value.savePositionOnQuit)
            o.put("closeAfterEnd", value.closeAfterEnd)
            o.put("autoplayNext", value.autoplayNext)
            o.put("repeatPlaylistAfterLast", value.repeatPlaylistAfterLast)
            o.put("enableNextPrevious", value.enableNextPrevious)
            o.put("rememberBrightness", value.rememberBrightness)
            o.put("autoPictureInPicture", value.autoPictureInPicture)
            o.put("keepScreenOnPaused", value.keepScreenOnPaused)
            o.put("autoplayAfterUnlock", value.autoplayAfterUnlock)
            o.put("showMediaInfoChooser", value.showMediaInfoChooser)
            o.put("showStatusBar", value.showStatusBar)
            o.put("showNavigationBar", value.showNavigationBar)
            o.put("safeAreaWindow", value.safeAreaWindow)
            o.put("portraitPlaybackButtonsPosition", value.portraitPlaybackButtonsPosition)
            o.put("landscapePlaybackButtonsPosition", value.landscapePlaybackButtonsPosition)
            o.put("hidePlayerButtonsBackground", value.hidePlayerButtonsBackground)
            o.put("alwaysDarkPlayerButtonBackground", value.alwaysDarkPlayerButtonBackground)
            o.put("hidePlayerControlsTimeoutMs", value.hidePlayerControlsTimeoutMs)
            o.put("timeNetworkClockFormat", value.timeNetworkClockFormat)
            o.put("reduceAnimation", value.reduceAnimation)
            o.put("showLoadingCircle", value.showLoadingCircle)
            o.put("allowGesturesInPanels", value.allowGesturesInPanels)
            o.put("swapVolumeBrightness", value.swapVolumeBrightness)
            o.put("gestureHudOppositeSide", value.gestureHudOppositeSide)
            o.put("rippleOnDoubleTap", value.rippleOnDoubleTap)
            o.put("showSeekTime", value.showSeekTime)
            o.put("showBufferedRange", value.showBufferedRange)
            o.put("preciseSeeking", value.preciseSeeking)
            o.put("thumbFastPreview", value.thumbFastPreview)
            o.put("customSkipDuration", value.customSkipDuration)
            o.put("onlineSkipMarkers", value.onlineSkipMarkers)
            o.put("markerProvider", value.markerProvider)
            o.put("detectChapterIntroOutro", value.detectChapterIntroOutro)
            o.put("customOpeningKeywords", value.customOpeningKeywords)
            o.put("customEndingKeywords", value.customEndingKeywords)
            o.put("autoSkipIntro", value.autoSkipIntro)
            o.put("autoSkipOutro", value.autoSkipOutro)
            o.put("screenshotFormat", value.screenshotFormat)
            o.put("subtitlesInScreenshots", value.subtitlesInScreenshots)
            o.put("screenshotFilenameTemplate", value.screenshotFilenameTemplate)
            o.put("jpegWebpQuality", value.jpegWebpQuality)
            o.put("pngCompression", value.pngCompression)
            o.put("volumeSliderOverlay", value.volumeSliderOverlay)
            o.put("brightnessSliderOverlay", value.brightnessSliderOverlay)
            o.put("holdSpeedOverlay", value.holdSpeedOverlay)
            o.put("aspectRatioFeedback", value.aspectRatioFeedback)
            o.put("zoomLevelFeedback", value.zoomLevelFeedback)
            o.put("repeatShuffleFeedback", value.repeatShuffleFeedback)
            o.put("actionFeedbackPills", value.actionFeedbackPills)
            o.put("defaultAudioDelayMs", value.defaultAudioDelayMs)
            o.put("defaultSubtitleDelayMs", value.defaultSubtitleDelayMs)
            o.put("equalizerEnabled", value.equalizerEnabled)
            o.put("equalizer60Hz", value.equalizer60Hz.toDouble())
            o.put("equalizer230Hz", value.equalizer230Hz.toDouble())
            o.put("equalizer910Hz", value.equalizer910Hz.toDouble())
            o.put("equalizer3600Hz", value.equalizer3600Hz.toDouble())
            o.put("equalizer14000Hz", value.equalizer14000Hz.toDouble())
            o.put("equalizerVolumeBoostDb", value.equalizerVolumeBoostDb.toDouble())
            o.put("equalizerPreset", value.equalizerPreset)
            o.put("audioChannelMode", value.audioChannelMode)
            o.put("volumeNormalization", value.volumeNormalization)
            o.put("dynamicRangeCompression", value.dynamicRangeCompression)
            o.put("voiceEnhancement", value.voiceEnhancement)
            o.put("surroundSoundMode", value.surroundSoundMode)
            o.put("trackAudioConfigsJson", value.trackAudioConfigsJson)
            o.put("videoFilterPreset", value.videoFilterPreset)
            o.put("videoBrightness", value.videoBrightness)
            o.put("videoContrast", value.videoContrast)
            o.put("videoSaturation", value.videoSaturation)
            o.put("videoGamma", value.videoGamma)
            o.put("videoSharpness", value.videoSharpness)
            o.put("videoHue", value.videoHue)
            o.put("videoTemperature", value.videoTemperature)
            o.put("videoTint", value.videoTint)
            o.put("videoDeband", value.videoDeband)
            o.put("brightnessGestures", value.brightnessGestures)
            o.put("volumeGestures", value.volumeGestures)
            o.put("pinchToZoom", value.pinchToZoom)
            o.put("pinchToZoomSubtitles", value.pinchToZoomSubtitles)
            o.put("swipeSubtitlesToSeekDialog", value.swipeSubtitlesToSeekDialog)
            o.put("horizontalSwipeToSeek", value.horizontalSwipeToSeek)
            o.put("swipeUpCenterForPlaylist", value.swipeUpCenterForPlaylist)
            o.put("horizontalSwipeSensitivity", value.horizontalSwipeSensitivity)
            o.put("holdMultiSpeed", value.holdMultiSpeed)
            o.put("holdSpeedMultiplier", value.holdSpeedMultiplier)
            o.put("dynamicSpeedOverlay", value.dynamicSpeedOverlay)
            o.put("doubleTapSeekDuration", value.doubleTapSeekDuration)
            o.put("doubleTapSeekAreaWidth", value.doubleTapSeekAreaWidth)
            o.put("doubleTapLeftAction", value.doubleTapLeftAction)
            o.put("doubleTapCenterAction", value.doubleTapCenterAction)
            o.put("doubleTapRightAction", value.doubleTapRightAction)
            o.put("enableDoubleTap", value.enableDoubleTap)
            o.put("enableSingleTap", value.enableSingleTap)
            o.put("singleTapSeekDuration", value.singleTapSeekDuration)
            o.put("singleTapSeekAreaWidth", value.singleTapSeekAreaWidth)
            o.put("singleTapLeftAction", value.singleTapLeftAction)
            o.put("singleTapCenterAction", value.singleTapCenterAction)
            o.put("singleTapRightAction", value.singleTapRightAction)
            o.put("singleTapCenterGesture", value.singleTapCenterGesture)
            o.put("holdDragMovesSubtitles", value.holdDragMovesSubtitles)
            o.put("mediaPreviousControl", value.mediaPreviousControl)
            o.put("mediaPlayPauseControl", value.mediaPlayPauseControl)
            o.put("mediaNextControl", value.mediaNextControl)
            o.put("preferredSubtitleLanguages", value.preferredSubtitleLanguages)
            o.put("applySubtitleLanguageToSelectedContentOnly", value.applySubtitleLanguageToSelectedContentOnly)
            o.put("selectedSubtitleFolders", org.json.JSONArray(value.selectedSubtitleFolders.toList()))
            o.put("selectedSubtitleVideos", org.json.JSONArray(value.selectedSubtitleVideos.toList()))
            o.put("subtitleSignsAndSongs", value.subtitleSignsAndSongs)
            o.put("dualSubtitlesEnabled", value.dualSubtitlesEnabled)
            o.put("preferredSecondarySubtitleLanguages", value.preferredSecondarySubtitleLanguages)
            o.put("detectSubtitlesByFilename", value.detectSubtitlesByFilename)
            o.put("autoLoadExternalSubtitles", value.autoLoadExternalSubtitles)
            o.put("overrideAssSsaSubtitles", value.overrideAssSsaSubtitles)
            o.put("scaleSubtitlesByWindow", value.scaleSubtitlesByWindow)
            o.put("subtitleFontDirectoryUri", value.subtitleFontDirectoryUri)
            o.put("selectedSubtitleFont", value.selectedSubtitleFont)
            o.put("showVideoEmbeddedSubtitleFonts", value.showVideoEmbeddedSubtitleFonts)
            o.put("subtitleFontsReloadNonce", value.subtitleFontsReloadNonce)
            o.put("subtitleBold", value.subtitleBold)
            o.put("subtitleItalic", value.subtitleItalic)
            o.put("subtitleUnderline", value.subtitleUnderline)
            o.put("subtitleAlignment", value.subtitleAlignment)
            o.put("subtitleFontSize", value.subtitleFontSize.toDouble())
            o.put("subtitleBorderStyle", value.subtitleBorderStyle)
            o.put("subtitleBorderSize", value.subtitleBorderSize.toDouble())
            o.put("subtitleShadowOffset", value.subtitleShadowOffset.toDouble())
            o.put("subtitleShadowOffsetX", value.subtitleShadowOffsetX.toDouble())
            o.put("subtitleShadowOffsetY", value.subtitleShadowOffsetY.toDouble())
            o.put("subtitleShadowBlur", value.subtitleShadowBlur.toDouble())
            o.put("subtitleBackgroundPadding", value.subtitleBackgroundPadding.toDouble())
            o.put("subtitleBackgroundCornerRadius", value.subtitleBackgroundCornerRadius.toDouble())
            o.put("subtitleLetterSpacing", value.subtitleLetterSpacing.toDouble())
            o.put("subtitleTextColor", value.subtitleTextColor)
            o.put("subtitleBorderColor", value.subtitleBorderColor)
            o.put("subtitleBackgroundColor", value.subtitleBackgroundColor)
            o.put("subtitleShadowColor", value.subtitleShadowColor)
            o.put("subtitleScale", value.subtitleScale.toDouble())
            o.put("subtitlePosition", value.subtitlePosition.toDouble())
            o.put("subtitlePositionDirection", value.subtitlePositionDirection)
            o.put("subtitleBlendWithVideo", value.subtitleBlendWithVideo)
            o.put("advancedAssEnabled", value.advancedAssEnabled)
            o.put("rawSubtitleEditorEnabled", value.rawSubtitleEditorEnabled)
            o.put("assStyleOverridesJson", value.assStyleOverridesJson)
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(JSON, o.toString()).apply()
        }

        private fun parseStringSet(arr: org.json.JSONArray?): Set<String> {
            if (arr == null) return emptySet()
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                val str = arr.optString(i, "")
                if (str.isNotBlank()) set.add(str)
            }
            return set
        }

        fun parseTrackAudioConfigs(json: String): Map<Int, TrackAudioConfig> {
            val result = mutableMapOf<Int, TrackAudioConfig>()
            if (json.isBlank()) return result
            try {
                val obj = JSONObject(json)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val trackId = key.toIntOrNull() ?: continue
                    val item = obj.optJSONObject(key) ?: continue
                    val channelModeStr = item.optString("channelMode", "auto-safe")
                    val channelMode = AudioChannelMode.entries.firstOrNull {
                        it.id == channelModeStr || it.name.equals(channelModeStr, ignoreCase = true)
                    } ?: AudioChannelMode.AUTO_SAFE
                    val volNorm = item.optBoolean("volumeNormalization", false)
                    val drc = item.optBoolean("dynamicRangeCompression", false)
                    result[trackId] = TrackAudioConfig(
                        channelMode = channelMode,
                        volumeNormalization = volNorm,
                        dynamicRangeCompression = drc
                    )
                }
            } catch (_: Throwable) {}
            return result
        }

        fun encodeTrackAudioConfigs(map: Map<Int, TrackAudioConfig>): String {
            val obj = JSONObject()
            map.forEach { (trackId, config) ->
                val item = JSONObject()
                item.put("channelMode", config.channelMode.id)
                item.put("volumeNormalization", config.volumeNormalization)
                item.put("dynamicRangeCompression", config.dynamicRangeCompression)
                obj.put(trackId.toString(), item)
            }
            return obj.toString()
        }
    }
}
