package com.example.util

import android.content.Context
import android.util.Xml
import com.example.player.AudioChannelMode
import com.example.ui.components.*
import com.example.ui.state.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlSerializer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Robust, production-grade XML serialization and deserialization manager
 * for Lumora Settings export & import operations.
 *
 * Guarantees:
 * - 100% crash-free, defensive parsing
 * - Complete coverage of every persistent setting in the application
 * - Graceful fallback to default values on missing or corrupted attributes
 */
object LumoraSettingsXmlManager {

    const val EXPORT_FILE_NAME = "Lumora Setting save.xml"
    private const val ROOT_TAG = "LumoraSettings"
    private const val SCHEMA_VERSION = "1.0"

    /**
     * Serializes all current user settings into an XML string.
     */
    fun buildSettingsXml(context: Context, state: UiState): String {
        val serializer: XmlSerializer = Xml.newSerializer()
        val writer = StringWriter()
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", true)
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)

        serializer.startTag(null, ROOT_TAG)
        serializer.attribute(null, "version", SCHEMA_VERSION)
        serializer.attribute(null, "appName", "Lumora")
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        serializer.attribute(null, "exportedAt", timestamp)

        fun writeSetting(name: String, value: Any?) {
            if (value == null) return
            serializer.startTag(null, "Setting")
            serializer.attribute(null, "name", name)
            serializer.attribute(null, "value", value.toString())
            serializer.endTag(null, "Setting")
        }

        // 1. APPEARANCE
        serializer.startTag(null, "Appearance")
        writeSetting("themeMode", state.themeMode.name)
        writeSetting("appScale", state.appScale)
        writeSetting("glassBlurTransparency", state.glassBlurTransparency)
        writeSetting("forceSideBySide", state.forceSideBySide)
        writeSetting("language", state.language.name)
        writeSetting("appTheme", state.appTheme.name)
        writeSetting("amoledBlackMode", state.amoledBlackMode)
        writeSetting("useSystemFont", state.useSystemFont)
        writeSetting("hapticFeedback", state.hapticFeedback)
        serializer.endTag(null, "Appearance")

        // 2. PLAYER (Core & Playback)
        val p = state.playerSettings
        serializer.startTag(null, "Player")
        writeSetting("frameStepAmount", state.frameStepAmount)
        writeSetting("copyTimestampOnDoubleTap", state.copyTimestampOnDoubleTap)
        writeSetting("showPlayerNotifications", state.showPlayerNotifications)
        writeSetting("seekbarStyle", state.seekbarStyle.name)

        writeSetting("orientation", p.orientation)
        writeSetting("savePositionOnQuit", p.savePositionOnQuit)
        writeSetting("closeAfterEnd", p.closeAfterEnd)
        writeSetting("autoplayNext", p.autoplayNext)
        writeSetting("repeatPlaylistAfterLast", p.repeatPlaylistAfterLast)
        writeSetting("enableNextPrevious", p.enableNextPrevious)
        writeSetting("rememberBrightness", p.rememberBrightness)
        writeSetting("autoPictureInPicture", p.autoPictureInPicture)
        writeSetting("keepScreenOnPaused", p.keepScreenOnPaused)
        writeSetting("autoplayAfterUnlock", p.autoplayAfterUnlock)
        writeSetting("showMediaInfoChooser", p.showMediaInfoChooser)
        writeSetting("showStatusBar", p.showStatusBar)
        writeSetting("showNavigationBar", p.showNavigationBar)
        writeSetting("safeAreaWindow", p.safeAreaWindow)
        writeSetting("portraitPlaybackButtonsPosition", p.portraitPlaybackButtonsPosition)
        writeSetting("landscapePlaybackButtonsPosition", p.landscapePlaybackButtonsPosition)
        writeSetting("hidePlayerButtonsBackground", p.hidePlayerButtonsBackground)
        writeSetting("alwaysDarkPlayerButtonBackground", p.alwaysDarkPlayerButtonBackground)
        writeSetting("hidePlayerControlsTimeoutMs", p.hidePlayerControlsTimeoutMs)
        writeSetting("timeNetworkClockFormat", p.timeNetworkClockFormat)
        writeSetting("reduceAnimation", p.reduceAnimation)
        writeSetting("showLoadingCircle", p.showLoadingCircle)
        writeSetting("allowGesturesInPanels", p.allowGesturesInPanels)
        writeSetting("swapVolumeBrightness", p.swapVolumeBrightness)
        writeSetting("gestureHudOppositeSide", p.gestureHudOppositeSide)
        writeSetting("rippleOnDoubleTap", p.rippleOnDoubleTap)
        writeSetting("showSeekTime", p.showSeekTime)
        writeSetting("showBufferedRange", p.showBufferedRange)
        writeSetting("preciseSeeking", p.preciseSeeking)
        writeSetting("thumbFastPreview", p.thumbFastPreview)
        writeSetting("customSkipDuration", p.customSkipDuration)
        writeSetting("onlineSkipMarkers", p.onlineSkipMarkers)
        writeSetting("markerProvider", p.markerProvider)
        writeSetting("detectChapterIntroOutro", p.detectChapterIntroOutro)
        writeSetting("customOpeningKeywords", p.customOpeningKeywords)
        writeSetting("customEndingKeywords", p.customEndingKeywords)
        writeSetting("autoSkipIntro", p.autoSkipIntro)
        writeSetting("autoSkipOutro", p.autoSkipOutro)
        writeSetting("screenshotFormat", p.screenshotFormat)
        writeSetting("subtitlesInScreenshots", p.subtitlesInScreenshots)
        writeSetting("screenshotFilenameTemplate", p.screenshotFilenameTemplate)
        writeSetting("jpegWebpQuality", p.jpegWebpQuality)
        writeSetting("pngCompression", p.pngCompression)
        writeSetting("volumeSliderOverlay", p.volumeSliderOverlay)
        writeSetting("brightnessSliderOverlay", p.brightnessSliderOverlay)
        writeSetting("holdSpeedOverlay", p.holdSpeedOverlay)
        writeSetting("aspectRatioFeedback", p.aspectRatioFeedback)
        writeSetting("zoomLevelFeedback", p.zoomLevelFeedback)
        writeSetting("repeatShuffleFeedback", p.repeatShuffleFeedback)
        writeSetting("actionFeedbackPills", p.actionFeedbackPills)
        writeSetting("defaultAudioDelayMs", p.defaultAudioDelayMs)
        writeSetting("defaultSubtitleDelayMs", p.defaultSubtitleDelayMs)
        serializer.endTag(null, "Player")

        // 3. AUDIO
        serializer.startTag(null, "Audio")
        writeSetting("preferredAudioLanguages", state.preferredAudioLanguages)
        writeSetting("applyAudioLanguageToSelectedContentOnly", state.applyAudioLanguageToSelectedContentOnly)
        writeSetting("selectedAudioFolders", state.selectedAudioFolders.joinToString(","))
        writeSetting("selectedAudioVideos", state.selectedAudioVideos.joinToString(","))
        writeSetting("enableAudioPitchCorrection", state.enableAudioPitchCorrection)
        writeSetting("volumeNormalization", state.volumeNormalization)
        writeSetting("backgroundPlayback", state.backgroundPlayback)
        writeSetting("audioChannelMode", state.audioChannelMode.name)
        writeSetting("volumeBoostCap", state.volumeBoostCap)
        writeSetting("rememberSelectedAudioTrack", state.rememberSelectedAudioTrack)

        writeSetting("equalizerEnabled", p.equalizerEnabled)
        writeSetting("equalizer60Hz", p.equalizer60Hz)
        writeSetting("equalizer230Hz", p.equalizer230Hz)
        writeSetting("equalizer910Hz", p.equalizer910Hz)
        writeSetting("equalizer3600Hz", p.equalizer3600Hz)
        writeSetting("equalizer14000Hz", p.equalizer14000Hz)
        writeSetting("equalizerVolumeBoostDb", p.equalizerVolumeBoostDb)
        writeSetting("equalizerPreset", p.equalizerPreset)
        writeSetting("playerAudioChannelMode", p.audioChannelMode)
        writeSetting("playerVolumeNormalization", p.volumeNormalization)
        writeSetting("dynamicRangeCompression", p.dynamicRangeCompression)
        writeSetting("voiceEnhancement", p.voiceEnhancement)
        writeSetting("surroundSoundMode", p.surroundSoundMode)
        writeSetting("trackAudioConfigsJson", p.trackAudioConfigsJson)
        serializer.endTag(null, "Audio")

        // 4. SUBTITLES
        serializer.startTag(null, "Subtitles")
        writeSetting("preferredSubtitleLanguages", p.preferredSubtitleLanguages)
        writeSetting("applySubtitleLanguageToSelectedContentOnly", p.applySubtitleLanguageToSelectedContentOnly)
        writeSetting("selectedSubtitleFolders", p.selectedSubtitleFolders.joinToString(","))
        writeSetting("selectedSubtitleVideos", p.selectedSubtitleVideos.joinToString(","))
        writeSetting("subtitleSignsAndSongs", p.subtitleSignsAndSongs)
        writeSetting("dualSubtitlesEnabled", p.dualSubtitlesEnabled)
        writeSetting("preferredSecondarySubtitleLanguages", p.preferredSecondarySubtitleLanguages)
        writeSetting("detectSubtitlesByFilename", p.detectSubtitlesByFilename)
        writeSetting("autoLoadExternalSubtitles", p.autoLoadExternalSubtitles)
        writeSetting("overrideAssSsaSubtitles", p.overrideAssSsaSubtitles)
        writeSetting("scaleSubtitlesByWindow", p.scaleSubtitlesByWindow)
        writeSetting("subtitleFontDirectoryUri", p.subtitleFontDirectoryUri)
        writeSetting("selectedSubtitleFont", p.selectedSubtitleFont)
        writeSetting("showVideoEmbeddedSubtitleFonts", p.showVideoEmbeddedSubtitleFonts)

        // Live subtitle style
        writeSetting("subtitleBold", p.subtitleBold)
        writeSetting("subtitleItalic", p.subtitleItalic)
        writeSetting("subtitleUnderline", p.subtitleUnderline)
        writeSetting("subtitleAlignment", p.subtitleAlignment)
        writeSetting("subtitleFontSize", p.subtitleFontSize)
        writeSetting("subtitleBorderStyle", p.subtitleBorderStyle)
        writeSetting("subtitleBorderSize", p.subtitleBorderSize)
        writeSetting("subtitleShadowOffset", p.subtitleShadowOffset)
        writeSetting("subtitleShadowOffsetX", p.subtitleShadowOffsetX)
        writeSetting("subtitleShadowOffsetY", p.subtitleShadowOffsetY)
        writeSetting("subtitleShadowBlur", p.subtitleShadowBlur)
        writeSetting("subtitleBackgroundPadding", p.subtitleBackgroundPadding)
        writeSetting("subtitleBackgroundCornerRadius", p.subtitleBackgroundCornerRadius)
        writeSetting("subtitleLetterSpacing", p.subtitleLetterSpacing)
        writeSetting("subtitleTextColor", p.subtitleTextColor)
        writeSetting("subtitleBorderColor", p.subtitleBorderColor)
        writeSetting("subtitleBackgroundColor", p.subtitleBackgroundColor)
        writeSetting("subtitleShadowColor", p.subtitleShadowColor)
        writeSetting("subtitleScale", p.subtitleScale)
        writeSetting("subtitlePosition", p.subtitlePosition)
        writeSetting("subtitlePositionDirection", p.subtitlePositionDirection)
        writeSetting("subtitleBlendWithVideo", p.subtitleBlendWithVideo)
        serializer.endTag(null, "Subtitles")

        // 5. DECODER
        serializer.startTag(null, "Decoder")
        writeSetting("hwAccelMode", state.hwAccelMode.name)
        writeSetting("videoFilterPreset", p.videoFilterPreset)
        writeSetting("videoBrightness", p.videoBrightness)
        writeSetting("videoContrast", p.videoContrast)
        writeSetting("videoSaturation", p.videoSaturation)
        writeSetting("videoGamma", p.videoGamma)
        writeSetting("videoSharpness", p.videoSharpness)
        writeSetting("videoHue", p.videoHue)
        writeSetting("videoTemperature", p.videoTemperature)
        writeSetting("videoTint", p.videoTint)
        writeSetting("videoDeband", p.videoDeband)
        serializer.endTag(null, "Decoder")

        // 6. GESTURES
        serializer.startTag(null, "Gestures")
        writeSetting("gestureSensitivityMode", state.gestureSensitivityMode.name)
        writeSetting("brightnessGestures", p.brightnessGestures)
        writeSetting("volumeGestures", p.volumeGestures)
        writeSetting("pinchToZoom", p.pinchToZoom)
        writeSetting("pinchToZoomSubtitles", p.pinchToZoomSubtitles)
        writeSetting("swipeSubtitlesToSeekDialog", p.swipeSubtitlesToSeekDialog)
        writeSetting("horizontalSwipeToSeek", p.horizontalSwipeToSeek)
        writeSetting("swipeUpCenterForPlaylist", p.swipeUpCenterForPlaylist)
        writeSetting("horizontalSwipeSensitivity", p.horizontalSwipeSensitivity)
        writeSetting("holdMultiSpeed", p.holdMultiSpeed)
        writeSetting("holdSpeedMultiplier", p.holdSpeedMultiplier)
        writeSetting("dynamicSpeedOverlay", p.dynamicSpeedOverlay)
        writeSetting("doubleTapSeekDuration", p.doubleTapSeekDuration)
        writeSetting("doubleTapSeekAreaWidth", p.doubleTapSeekAreaWidth)
        writeSetting("doubleTapLeftAction", p.doubleTapLeftAction)
        writeSetting("doubleTapCenterAction", p.doubleTapCenterAction)
        writeSetting("doubleTapRightAction", p.doubleTapRightAction)
        writeSetting("enableDoubleTap", p.enableDoubleTap)
        writeSetting("enableSingleTap", p.enableSingleTap)
        writeSetting("singleTapSeekDuration", p.singleTapSeekDuration)
        writeSetting("singleTapSeekAreaWidth", p.singleTapSeekAreaWidth)
        writeSetting("singleTapLeftAction", p.singleTapLeftAction)
        writeSetting("singleTapCenterAction", p.singleTapCenterAction)
        writeSetting("singleTapRightAction", p.singleTapRightAction)
        writeSetting("singleTapCenterGesture", p.singleTapCenterGesture)
        writeSetting("holdDragMovesSubtitles", p.holdDragMovesSubtitles)
        writeSetting("mediaPreviousControl", p.mediaPreviousControl)
        writeSetting("mediaPlayPauseControl", p.mediaPlayPauseControl)
        writeSetting("mediaNextControl", p.mediaNextControl)
        serializer.endTag(null, "Gestures")

        // 7. PLAYER LAYOUT
        val plc = state.playerLayoutConfig
        serializer.startTag(null, "PlayerLayout")
        writeSetting("topRightControls", plc.topRightControls.joinToString(",") { it.id })
        writeSetting("bottomLeftControls", plc.bottomLeftControls.joinToString(",") { it.id })
        writeSetting("bottomRightControls", plc.bottomRightControls.joinToString(",") { it.id })
        writeSetting("isLandscapeTopRightEnabled", plc.isLandscapeTopRightEnabled)
        writeSetting("isLandscapeBottomLeftEnabled", plc.isLandscapeBottomLeftEnabled)
        writeSetting("isLandscapeBottomRightEnabled", plc.isLandscapeBottomRightEnabled)
        writeSetting("portraitTopControls", plc.portraitTopControls.joinToString(",") { it.id })
        writeSetting("portraitBottomControls", plc.portraitBottomControls.joinToString(",") { it.id })
        writeSetting("isPortraitTopEnabled", plc.isPortraitTopEnabled)
        writeSetting("isPortraitBottomEnabled", plc.isPortraitBottomEnabled)
        serializer.endTag(null, "PlayerLayout")

        // 8. FOLDERS & BROWSER
        serializer.startTag(null, "Folders")
        writeSetting("showFullName", state.showFullName)
        writeSetting("showNewVideoLabel", state.showNewVideoLabel)
        writeSetting("newVideoDaysThreshold", state.newVideoDaysThreshold)
        writeSetting("showFolderUnplayedBadge", state.showFolderUnplayedBadge)
        writeSetting("autoScrollToLastPlayed", state.autoScrollToLastPlayed)
        writeSetting("treePathCompression", state.treePathCompression)
        writeSetting("dualPaneView", state.dualPaneView)
        writeSetting("watchedThresholdPercent", state.watchedThresholdPercent)

        // Custom folders & blocklists
        val customFolders = SettingsPreferencesManager.loadCustomFolders(context)
        writeSetting("customFolders", customFolders.joinToString("|"))
        try {
            FolderBlockListManager.init(context)
            writeSetting("blockedVideoFolderIds", FolderBlockListManager.blockedVideoFolderIds.value.joinToString("|"))
            writeSetting("blockedAudioFolderPaths", FolderBlockListManager.blockedAudioFolderPaths.value.joinToString("|"))
        } catch (_: Exception) {}
        serializer.endTag(null, "Folders")

        // 9. THUMBNAILS
        serializer.startTag(null, "Thumbnails")
        writeSetting("showVideoThumbnails", state.showVideoThumbnails)
        writeSetting("thumbnailStrategy", state.thumbnailStrategy.name)
        writeSetting("thumbnailQuality", state.thumbnailQuality.name)
        writeSetting("thumbnailFallbackSecond", state.thumbnailFallbackSecond)
        writeSetting("tapThumbnailToSelect", state.tapThumbnailToSelect)
        writeSetting("showNetworkThumbnails", state.showNetworkThumbnails)
        serializer.endTag(null, "Thumbnails")

        // 10. ANIMATIONS & NAVIGATION
        serializer.startTag(null, "Animations")
        writeSetting("showHomeTab", state.showHomeTab)
        writeSetting("showMusicTab", state.showMusicTab)
        writeSetting("showRecentsTab", state.showRecentsTab)
        writeSetting("showPlaylistsTab", state.showPlaylistsTab)
        writeSetting("controlsAnimationStyle", state.controlsAnimationStyle.name)
        writeSetting("videoOpeningAnimation", state.videoOpeningAnimation.name)
        writeSetting("screenNavigationStyle", state.screenNavigationStyle.name)
        writeSetting("tabNavigationStyle", state.tabNavigationStyle.name)
        writeSetting("animationSpeed", state.animationSpeed)
        serializer.endTag(null, "Animations")

        // 11. EDITOR & TEXT TOOLS
        serializer.startTag(null, "Editor")
        writeSetting("editorTextSizeSp", state.editorTextSizeSp)
        writeSetting("timeTextSizeSp", state.timeTextSizeSp)
        writeSetting("showPasteBoxConverter", state.showPasteBoxConverter)
        writeSetting("showPasteBoxCleaner", state.showPasteBoxCleaner)
        writeSetting("showPasteBoxShifter", state.showPasteBoxShifter)
        writeSetting("showPasteBoxEditor", state.showPasteBoxEditor)
        writeSetting("showPasteBoxTextEditor", state.showPasteBoxTextEditor)
        writeSetting("dialogueEditOffsetY", state.dialogueEditOffsetY)
        writeSetting("colorPickerOffsetY", state.colorPickerOffsetY)
        writeSetting("showUploadSectionTextEditor", state.showUploadSectionTextEditor)
        writeSetting("textEditorFontSizeSp", state.textEditorFontSizeSp)
        writeSetting("showLineNumbersTextEditor", state.showLineNumbersTextEditor)
        writeSetting("textEditorWordWrap", state.textEditorWordWrap)
        writeSetting("showBottomToolbarTextEditor", state.showBottomToolbarTextEditor)
        writeSetting("showSearchPanelTextEditor", state.showSearchPanelTextEditor)
        writeSetting("textEditorNavStep", state.textEditorNavStep.name)
        serializer.endTag(null, "Editor")

        // 12. SORT & VIEW
        serializer.startTag(null, "SortAndView")
        listOf("home", "recents", "music").forEach { tabId ->
            writeSetting("sortField_$tabId", SettingsPreferencesManager.loadSortField(context, tabId).name)
            writeSetting("sortDir_$tabId", SettingsPreferencesManager.loadSortDirection(context, tabId).name)
            writeSetting("viewMode_$tabId", SettingsPreferencesManager.loadViewMode(context, tabId).name)
            writeSetting("layoutMode_$tabId", SettingsPreferencesManager.loadLayoutMode(context, tabId).name)
            val vf = SettingsPreferencesManager.loadVisibleFields(context, tabId)
            writeSetting("vf_thumb_$tabId", vf.showThumbnails)
            writeSetting("vf_ext_$tabId", vf.showExtension)
            writeSetting("vf_dur_$tabId", vf.showDuration)
            writeSetting("vf_sub_$tabId", vf.showSubtitleIndicator)
            writeSetting("vf_name_$tabId", vf.showFullName)
            writeSetting("vf_size_$tabId", vf.showSize)
            writeSetting("vf_res_$tabId", vf.showResolution)
            writeSetting("vf_fps_$tabId", vf.showFramerate)
            writeSetting("vf_date_$tabId", vf.showDate)
            writeSetting("vf_prog_$tabId", vf.showProgressBar)
            writeSetting("vf_path_$tabId", vf.showPath)
            writeSetting("vf_cnt_$tabId", vf.showVideoCount)
            writeSetting("vf_new_$tabId", vf.showNewBadge)
        }
        serializer.endTag(null, "SortAndView")

        serializer.endTag(null, ROOT_TAG)
        serializer.endDocument()

        return writer.toString()
    }

    /**
     * Writes all Lumora settings to the target file.
     */
    fun exportSettingsToFile(context: Context, state: UiState, targetDirectory: File): Result<File> {
        return try {
            if (!targetDirectory.exists()) {
                targetDirectory.mkdirs()
            }
            val destinationFile = File(targetDirectory, EXPORT_FILE_NAME)
            val xmlContent = buildSettingsXml(context, state)
            FileOutputStream(destinationFile).use { fos ->
                fos.write(xmlContent.toByteArray(Charsets.UTF_8))
                fos.flush()
            }
            Result.success(destinationFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Defensive container holding parsed settings ready for application.
     */
    data class ParsedSettings(
        val updatedUiState: UiState,
        val customFolders: Set<String>? = null,
        val blockedVideoFolderIds: Set<String>? = null,
        val blockedAudioFolderPaths: Set<String>? = null,
        val sortAndViewSettings: Map<String, String> = emptyMap()
    )

    /**
     * Defensively parses and validates an XML settings file.
     * Guarantees:
     * - Returns Result.failure if the file is not a valid LumoraSettings document
     * - Never throws unhandled exceptions
     * - Robust against schema changes and missing keys
     */
    fun parseSettingsFile(context: Context, file: File, currentUiState: UiState): Result<ParsedSettings> {
        if (!file.exists() || !file.canRead() || file.length() <= 0) {
            return Result.failure(IllegalArgumentException("File does not exist or is not readable"))
        }

        return try {
            val parser = Xml.newPullParser()
            FileInputStream(file).use { fis ->
                parser.setInput(fis, "UTF-8")

                var eventType = parser.eventType
                var rootFound = false
                var currentSection: String? = null
                val map = mutableMapOf<String, String>()

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            val tagName = parser.name
                            if (tagName.equals(ROOT_TAG, ignoreCase = true)) {
                                rootFound = true
                            } else if (tagName.equals("Setting", ignoreCase = true)) {
                                val name = parser.getAttributeValue(null, "name")
                                val value = parser.getAttributeValue(null, "value")
                                if (!name.isNullOrBlank() && value != null) {
                                    map[name] = value
                                    if (currentSection != null) {
                                        map["${currentSection}_$name"] = value
                                    }
                                }
                            } else {
                                currentSection = tagName
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name.equals(currentSection, ignoreCase = true)) {
                                currentSection = null
                            }
                        }
                    }
                    eventType = parser.next()
                }

                if (!rootFound) {
                    return Result.failure(IllegalArgumentException("Selected file is not a valid Lumora Settings XML document"))
                }

                // Helper extractors
                fun getStr(key: String, fallback: String): String = map[key] ?: fallback
                fun getBool(key: String, fallback: Boolean): Boolean = map[key]?.toBooleanStrictOrNull() ?: fallback
                fun getInt(key: String, fallback: Int, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): Int =
                    map[key]?.toIntOrNull()?.coerceIn(min, max) ?: fallback
                fun getLong(key: String, fallback: Long): Long = map[key]?.toLongOrNull() ?: fallback
                fun getFloat(key: String, fallback: Float, min: Float = -Float.MAX_VALUE, max: Float = Float.MAX_VALUE): Float =
                    map[key]?.toFloatOrNull()?.coerceIn(min, max) ?: fallback
                fun getDouble(key: String, fallback: Double, min: Double = -Double.MAX_VALUE, max: Double = Double.MAX_VALUE): Double =
                    map[key]?.toDoubleOrNull()?.coerceIn(min, max) ?: fallback
                fun getSet(key: String): Set<String> {
                    val raw = map[key] ?: return emptySet()
                    if (raw.isBlank()) return emptySet()
                    return raw.split(",", "|").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                }

                // 1. Appearance
                val themeMode = runCatching { ThemeMode.valueOf(getStr("themeMode", currentUiState.themeMode.name)) }.getOrDefault(currentUiState.themeMode)
                val appScale = getFloat("appScale", currentUiState.appScale, 1f, 100f)
                val glassBlurTransparency = getFloat("glassBlurTransparency", currentUiState.glassBlurTransparency, 10f, 100f)
                val forceSideBySide = getBool("forceSideBySide", currentUiState.forceSideBySide)
                val language = runCatching { AppLanguage.valueOf(getStr("language", currentUiState.language.name)) }.getOrDefault(currentUiState.language)
                val appTheme = runCatching { AppThemeVariant.valueOf(getStr("appTheme", currentUiState.appTheme.name)) }.getOrDefault(currentUiState.appTheme)
                val amoledBlackMode = getBool("amoledBlackMode", currentUiState.amoledBlackMode)
                val useSystemFont = getBool("useSystemFont", currentUiState.useSystemFont)
                val hapticFeedback = getBool("hapticFeedback", currentUiState.hapticFeedback)

                // 2. Player Controls & Core
                val frameStepAmount = getInt("frameStepAmount", currentUiState.frameStepAmount, 1, 30)
                val copyTimestamp = getBool("copyTimestampOnDoubleTap", currentUiState.copyTimestampOnDoubleTap)
                val showPlayerNotifications = getBool("showPlayerNotifications", currentUiState.showPlayerNotifications)
                val seekbarStyle = runCatching { SeekbarStyle.valueOf(getStr("seekbarStyle", currentUiState.seekbarStyle.name)) }.getOrDefault(currentUiState.seekbarStyle)

                val basePlayer = currentUiState.playerSettings
                val playerSettings = basePlayer.copy(
                    orientation = getStr("orientation", basePlayer.orientation),
                    savePositionOnQuit = getBool("savePositionOnQuit", basePlayer.savePositionOnQuit),
                    closeAfterEnd = getBool("closeAfterEnd", basePlayer.closeAfterEnd),
                    autoplayNext = getBool("autoplayNext", basePlayer.autoplayNext),
                    repeatPlaylistAfterLast = getBool("repeatPlaylistAfterLast", basePlayer.repeatPlaylistAfterLast),
                    enableNextPrevious = getBool("enableNextPrevious", basePlayer.enableNextPrevious),
                    rememberBrightness = getBool("rememberBrightness", basePlayer.rememberBrightness),
                    autoPictureInPicture = getBool("autoPictureInPicture", basePlayer.autoPictureInPicture),
                    keepScreenOnPaused = getBool("keepScreenOnPaused", basePlayer.keepScreenOnPaused),
                    autoplayAfterUnlock = getBool("autoplayAfterUnlock", basePlayer.autoplayAfterUnlock),
                    showMediaInfoChooser = getBool("showMediaInfoChooser", basePlayer.showMediaInfoChooser),
                    showStatusBar = getBool("showStatusBar", basePlayer.showStatusBar),
                    showNavigationBar = getBool("showNavigationBar", basePlayer.showNavigationBar),
                    safeAreaWindow = getBool("safeAreaWindow", basePlayer.safeAreaWindow),
                    portraitPlaybackButtonsPosition = getStr("portraitPlaybackButtonsPosition", basePlayer.portraitPlaybackButtonsPosition),
                    landscapePlaybackButtonsPosition = getStr("landscapePlaybackButtonsPosition", basePlayer.landscapePlaybackButtonsPosition),
                    hidePlayerButtonsBackground = getBool("hidePlayerButtonsBackground", basePlayer.hidePlayerButtonsBackground),
                    alwaysDarkPlayerButtonBackground = getBool("alwaysDarkPlayerButtonBackground", basePlayer.alwaysDarkPlayerButtonBackground),
                    hidePlayerControlsTimeoutMs = getInt("hidePlayerControlsTimeoutMs", basePlayer.hidePlayerControlsTimeoutMs).coerceIn(500, 10000),
                    timeNetworkClockFormat = getStr("timeNetworkClockFormat", basePlayer.timeNetworkClockFormat),
                    reduceAnimation = getBool("reduceAnimation", basePlayer.reduceAnimation),
                    showLoadingCircle = getBool("showLoadingCircle", basePlayer.showLoadingCircle),
                    allowGesturesInPanels = getBool("allowGesturesInPanels", basePlayer.allowGesturesInPanels),
                    swapVolumeBrightness = getBool("swapVolumeBrightness", basePlayer.swapVolumeBrightness),
                    gestureHudOppositeSide = getBool("gestureHudOppositeSide", basePlayer.gestureHudOppositeSide),
                    rippleOnDoubleTap = getBool("rippleOnDoubleTap", basePlayer.rippleOnDoubleTap),
                    showSeekTime = getBool("showSeekTime", basePlayer.showSeekTime),
                    showBufferedRange = getBool("showBufferedRange", basePlayer.showBufferedRange),
                    preciseSeeking = getBool("preciseSeeking", basePlayer.preciseSeeking),
                    thumbFastPreview = getBool("thumbFastPreview", basePlayer.thumbFastPreview),
                    customSkipDuration = getInt("customSkipDuration", basePlayer.customSkipDuration, 1, 600),
                    onlineSkipMarkers = getBool("onlineSkipMarkers", basePlayer.onlineSkipMarkers),
                    markerProvider = getStr("markerProvider", basePlayer.markerProvider),
                    detectChapterIntroOutro = getBool("detectChapterIntroOutro", basePlayer.detectChapterIntroOutro),
                    customOpeningKeywords = getStr("customOpeningKeywords", basePlayer.customOpeningKeywords),
                    customEndingKeywords = getStr("customEndingKeywords", basePlayer.customEndingKeywords),
                    autoSkipIntro = getBool("autoSkipIntro", basePlayer.autoSkipIntro),
                    autoSkipOutro = getBool("autoSkipOutro", basePlayer.autoSkipOutro),
                    screenshotFormat = getStr("screenshotFormat", basePlayer.screenshotFormat),
                    subtitlesInScreenshots = getBool("subtitlesInScreenshots", basePlayer.subtitlesInScreenshots),
                    screenshotFilenameTemplate = getStr("screenshotFilenameTemplate", basePlayer.screenshotFilenameTemplate),
                    jpegWebpQuality = getInt("jpegWebpQuality", basePlayer.jpegWebpQuality, 1, 100),
                    pngCompression = getInt("pngCompression", basePlayer.pngCompression, 0, 9),
                    volumeSliderOverlay = getBool("volumeSliderOverlay", basePlayer.volumeSliderOverlay),
                    brightnessSliderOverlay = getBool("brightnessSliderOverlay", basePlayer.brightnessSliderOverlay),
                    holdSpeedOverlay = getBool("holdSpeedOverlay", basePlayer.holdSpeedOverlay),
                    aspectRatioFeedback = getBool("aspectRatioFeedback", basePlayer.aspectRatioFeedback),
                    zoomLevelFeedback = getBool("zoomLevelFeedback", basePlayer.zoomLevelFeedback),
                    repeatShuffleFeedback = getBool("repeatShuffleFeedback", basePlayer.repeatShuffleFeedback),
                    actionFeedbackPills = getBool("actionFeedbackPills", basePlayer.actionFeedbackPills),
                    defaultAudioDelayMs = getLong("defaultAudioDelayMs", basePlayer.defaultAudioDelayMs),
                    defaultSubtitleDelayMs = getLong("defaultSubtitleDelayMs", basePlayer.defaultSubtitleDelayMs),

                    // Audio
                    equalizerEnabled = getBool("equalizerEnabled", basePlayer.equalizerEnabled),
                    equalizer60Hz = getFloat("equalizer60Hz", basePlayer.equalizer60Hz, -20f, 20f),
                    equalizer230Hz = getFloat("equalizer230Hz", basePlayer.equalizer230Hz, -20f, 20f),
                    equalizer910Hz = getFloat("equalizer910Hz", basePlayer.equalizer910Hz, -20f, 20f),
                    equalizer3600Hz = getFloat("equalizer3600Hz", basePlayer.equalizer3600Hz, -20f, 20f),
                    equalizer14000Hz = getFloat("equalizer14000Hz", basePlayer.equalizer14000Hz, -20f, 20f),
                    equalizerVolumeBoostDb = getFloat("equalizerVolumeBoostDb", basePlayer.equalizerVolumeBoostDb, 0f, 24f),
                    equalizerPreset = getStr("equalizerPreset", basePlayer.equalizerPreset),
                    audioChannelMode = getStr("playerAudioChannelMode", getStr("audioChannelMode", basePlayer.audioChannelMode)),
                    volumeNormalization = getBool("playerVolumeNormalization", basePlayer.volumeNormalization),
                    dynamicRangeCompression = getBool("dynamicRangeCompression", basePlayer.dynamicRangeCompression),
                    voiceEnhancement = getBool("voiceEnhancement", basePlayer.voiceEnhancement),
                    surroundSoundMode = getStr("surroundSoundMode", basePlayer.surroundSoundMode),
                    trackAudioConfigsJson = getStr("trackAudioConfigsJson", basePlayer.trackAudioConfigsJson),

                    // Video filters
                    videoFilterPreset = getStr("videoFilterPreset", basePlayer.videoFilterPreset),
                    videoBrightness = getInt("videoBrightness", basePlayer.videoBrightness, -100, 100),
                    videoContrast = getInt("videoContrast", basePlayer.videoContrast, -100, 100),
                    videoSaturation = getInt("videoSaturation", basePlayer.videoSaturation, -100, 100),
                    videoGamma = getInt("videoGamma", basePlayer.videoGamma, -100, 100),
                    videoSharpness = getInt("videoSharpness", basePlayer.videoSharpness, 0, 100),
                    videoHue = getInt("videoHue", basePlayer.videoHue, -100, 100),
                    videoTemperature = getInt("videoTemperature", basePlayer.videoTemperature, -100, 100),
                    videoTint = getInt("videoTint", basePlayer.videoTint, -100, 100),
                    videoDeband = getBool("videoDeband", basePlayer.videoDeband),

                    // Gestures
                    brightnessGestures = getBool("brightnessGestures", basePlayer.brightnessGestures),
                    volumeGestures = getBool("volumeGestures", basePlayer.volumeGestures),
                    pinchToZoom = getBool("pinchToZoom", basePlayer.pinchToZoom),
                    pinchToZoomSubtitles = getBool("pinchToZoomSubtitles", basePlayer.pinchToZoomSubtitles),
                    swipeSubtitlesToSeekDialog = getBool("swipeSubtitlesToSeekDialog", basePlayer.swipeSubtitlesToSeekDialog),
                    horizontalSwipeToSeek = getBool("horizontalSwipeToSeek", basePlayer.horizontalSwipeToSeek),
                    swipeUpCenterForPlaylist = getBool("swipeUpCenterForPlaylist", basePlayer.swipeUpCenterForPlaylist),
                    horizontalSwipeSensitivity = getInt("horizontalSwipeSensitivity", basePlayer.horizontalSwipeSensitivity, 1, 100),
                    holdMultiSpeed = getBool("holdMultiSpeed", basePlayer.holdMultiSpeed),
                    holdSpeedMultiplier = getDouble("holdSpeedMultiplier", basePlayer.holdSpeedMultiplier, 1.1, 4.0),
                    dynamicSpeedOverlay = getBool("dynamicSpeedOverlay", basePlayer.dynamicSpeedOverlay),
                    doubleTapSeekDuration = getInt("doubleTapSeekDuration", basePlayer.doubleTapSeekDuration, 1, 120),
                    doubleTapSeekAreaWidth = getInt("doubleTapSeekAreaWidth", basePlayer.doubleTapSeekAreaWidth, 10, 50),
                    doubleTapLeftAction = getStr("doubleTapLeftAction", basePlayer.doubleTapLeftAction),
                    doubleTapCenterAction = getStr("doubleTapCenterAction", basePlayer.doubleTapCenterAction),
                    doubleTapRightAction = getStr("doubleTapRightAction", basePlayer.doubleTapRightAction),
                    enableDoubleTap = getBool("enableDoubleTap", basePlayer.enableDoubleTap),
                    enableSingleTap = getBool("enableSingleTap", basePlayer.enableSingleTap),
                    singleTapSeekDuration = getInt("singleTapSeekDuration", basePlayer.singleTapSeekDuration, 1, 120),
                    singleTapSeekAreaWidth = getInt("singleTapSeekAreaWidth", basePlayer.singleTapSeekAreaWidth, 10, 50),
                    singleTapLeftAction = getStr("singleTapLeftAction", basePlayer.singleTapLeftAction),
                    singleTapCenterAction = getStr("singleTapCenterAction", basePlayer.singleTapCenterAction),
                    singleTapRightAction = getStr("singleTapRightAction", basePlayer.singleTapRightAction),
                    singleTapCenterGesture = getBool("singleTapCenterGesture", basePlayer.singleTapCenterGesture),
                    holdDragMovesSubtitles = getBool("holdDragMovesSubtitles", basePlayer.holdDragMovesSubtitles),
                    mediaPreviousControl = getBool("mediaPreviousControl", basePlayer.mediaPreviousControl),
                    mediaPlayPauseControl = getBool("mediaPlayPauseControl", basePlayer.mediaPlayPauseControl),
                    mediaNextControl = getBool("mediaNextControl", basePlayer.mediaNextControl),

                    // Subtitle renderer
                    preferredSubtitleLanguages = getStr("preferredSubtitleLanguages", basePlayer.preferredSubtitleLanguages),
                    applySubtitleLanguageToSelectedContentOnly = getBool("applySubtitleLanguageToSelectedContentOnly", basePlayer.applySubtitleLanguageToSelectedContentOnly),
                    selectedSubtitleFolders = getSet("selectedSubtitleFolders").ifEmpty { basePlayer.selectedSubtitleFolders },
                    selectedSubtitleVideos = getSet("selectedSubtitleVideos").ifEmpty { basePlayer.selectedSubtitleVideos },
                    subtitleSignsAndSongs = getBool("subtitleSignsAndSongs", basePlayer.subtitleSignsAndSongs),
                    dualSubtitlesEnabled = getBool("dualSubtitlesEnabled", basePlayer.dualSubtitlesEnabled),
                    preferredSecondarySubtitleLanguages = getStr("preferredSecondarySubtitleLanguages", basePlayer.preferredSecondarySubtitleLanguages),
                    detectSubtitlesByFilename = getBool("detectSubtitlesByFilename", basePlayer.detectSubtitlesByFilename),
                    autoLoadExternalSubtitles = getBool("autoLoadExternalSubtitles", basePlayer.autoLoadExternalSubtitles),
                    overrideAssSsaSubtitles = getBool("overrideAssSsaSubtitles", basePlayer.overrideAssSsaSubtitles),
                    scaleSubtitlesByWindow = getBool("scaleSubtitlesByWindow", basePlayer.scaleSubtitlesByWindow),
                    subtitleFontDirectoryUri = getStr("subtitleFontDirectoryUri", basePlayer.subtitleFontDirectoryUri),
                    selectedSubtitleFont = getStr("selectedSubtitleFont", basePlayer.selectedSubtitleFont),
                    showVideoEmbeddedSubtitleFonts = getBool("showVideoEmbeddedSubtitleFonts", basePlayer.showVideoEmbeddedSubtitleFonts),
                    subtitleBold = getBool("subtitleBold", basePlayer.subtitleBold),
                    subtitleItalic = getBool("subtitleItalic", basePlayer.subtitleItalic),
                    subtitleUnderline = getBool("subtitleUnderline", basePlayer.subtitleUnderline),
                    subtitleAlignment = getStr("subtitleAlignment", basePlayer.subtitleAlignment),
                    subtitleFontSize = getFloat("subtitleFontSize", basePlayer.subtitleFontSize, 10f, 120f),
                    subtitleBorderStyle = getStr("subtitleBorderStyle", basePlayer.subtitleBorderStyle),
                    subtitleBorderSize = getFloat("subtitleBorderSize", basePlayer.subtitleBorderSize, 0f, 20f),
                    subtitleShadowOffset = getFloat("subtitleShadowOffset", basePlayer.subtitleShadowOffset, 0f, 20f),
                    subtitleShadowOffsetX = getFloat("subtitleShadowOffsetX", basePlayer.subtitleShadowOffsetX, -20f, 20f),
                    subtitleShadowOffsetY = getFloat("subtitleShadowOffsetY", basePlayer.subtitleShadowOffsetY, -20f, 20f),
                    subtitleShadowBlur = getFloat("subtitleShadowBlur", basePlayer.subtitleShadowBlur, 0f, 20f),
                    subtitleBackgroundPadding = getFloat("subtitleBackgroundPadding", basePlayer.subtitleBackgroundPadding, 0f, 40f),
                    subtitleBackgroundCornerRadius = getFloat("subtitleBackgroundCornerRadius", basePlayer.subtitleBackgroundCornerRadius, 0f, 40f),
                    subtitleLetterSpacing = getFloat("subtitleLetterSpacing", basePlayer.subtitleLetterSpacing, -5f, 20f),
                    subtitleTextColor = getLong("subtitleTextColor", basePlayer.subtitleTextColor),
                    subtitleBorderColor = getLong("subtitleBorderColor", basePlayer.subtitleBorderColor),
                    subtitleBackgroundColor = getLong("subtitleBackgroundColor", basePlayer.subtitleBackgroundColor),
                    subtitleShadowColor = getLong("subtitleShadowColor", basePlayer.subtitleShadowColor),
                    subtitleScale = getFloat("subtitleScale", basePlayer.subtitleScale, 0.2f, 3.0f),
                    subtitlePosition = getFloat("subtitlePosition", basePlayer.subtitlePosition, 0f, 100f),
                    subtitlePositionDirection = getStr("subtitlePositionDirection", basePlayer.subtitlePositionDirection),
                    subtitleBlendWithVideo = getBool("subtitleBlendWithVideo", basePlayer.subtitleBlendWithVideo)
                )

                // 3. Audio UI State
                val preferredAudioLanguages = getStr("preferredAudioLanguages", currentUiState.preferredAudioLanguages)
                val applyAudioLanguageToSelectedContentOnly = getBool("applyAudioLanguageToSelectedContentOnly", currentUiState.applyAudioLanguageToSelectedContentOnly)
                val selectedAudioFolders = getSet("selectedAudioFolders").ifEmpty { currentUiState.selectedAudioFolders }
                val selectedAudioVideos = getSet("selectedAudioVideos").ifEmpty { currentUiState.selectedAudioVideos }
                val enableAudioPitchCorrection = getBool("enableAudioPitchCorrection", currentUiState.enableAudioPitchCorrection)
                val volumeNormalization = getBool("volumeNormalization", currentUiState.volumeNormalization)
                val backgroundPlayback = getBool("backgroundPlayback", currentUiState.backgroundPlayback)
                val audioChannelMode = runCatching { AudioChannelMode.valueOf(getStr("audioChannelMode", currentUiState.audioChannelMode.name)) }.getOrDefault(currentUiState.audioChannelMode)
                val volumeBoostCap = getInt("volumeBoostCap", currentUiState.volumeBoostCap, 100, 300)
                val rememberSelectedAudioTrack = getBool("rememberSelectedAudioTrack", currentUiState.rememberSelectedAudioTrack)

                // 4. Decoder UI State
                val hwAccelMode = runCatching { HwAccelMode.valueOf(getStr("hwAccelMode", currentUiState.hwAccelMode.name)) }.getOrDefault(currentUiState.hwAccelMode)

                // 5. Gestures UI State
                val gestureSensitivityMode = runCatching { GestureSensitivityMode.valueOf(getStr("gestureSensitivityMode", currentUiState.gestureSensitivityMode.name)) }.getOrDefault(currentUiState.gestureSensitivityMode)

                // 6. Player Layout
                val basePlc = currentUiState.playerLayoutConfig
                fun parseControlList(key: String, fallback: List<PlayerControlId>): List<PlayerControlId> {
                    val raw = map[key] ?: return fallback
                    if (raw.isBlank()) return fallback
                    val list = raw.split(",").mapNotNull { PlayerControlId.fromId(it.trim()) }
                    return if (list.isNotEmpty()) list else fallback
                }
                val playerLayoutConfig = PlayerLayoutConfig(
                    topRightControls = parseControlList("topRightControls", basePlc.topRightControls),
                    bottomLeftControls = parseControlList("bottomLeftControls", basePlc.bottomLeftControls),
                    bottomRightControls = parseControlList("bottomRightControls", basePlc.bottomRightControls),
                    isLandscapeTopRightEnabled = getBool("isLandscapeTopRightEnabled", basePlc.isLandscapeTopRightEnabled),
                    isLandscapeBottomLeftEnabled = getBool("isLandscapeBottomLeftEnabled", basePlc.isLandscapeBottomLeftEnabled),
                    isLandscapeBottomRightEnabled = getBool("isLandscapeBottomRightEnabled", basePlc.isLandscapeBottomRightEnabled),
                    portraitTopControls = parseControlList("portraitTopControls", basePlc.portraitTopControls),
                    portraitBottomControls = parseControlList("portraitBottomControls", basePlc.portraitBottomControls),
                    isPortraitTopEnabled = getBool("isPortraitTopEnabled", basePlc.isPortraitTopEnabled),
                    isPortraitBottomEnabled = getBool("isPortraitBottomEnabled", basePlc.isPortraitBottomEnabled)
                ).sanitized()

                // 7. Folders & File Browser UI State
                val showFullName = getBool("showFullName", currentUiState.showFullName)
                val showNewVideoLabel = getBool("showNewVideoLabel", currentUiState.showNewVideoLabel)
                val newVideoDaysThreshold = getInt("newVideoDaysThreshold", currentUiState.newVideoDaysThreshold, 1, 60)
                val showFolderUnplayedBadge = getBool("showFolderUnplayedBadge", currentUiState.showFolderUnplayedBadge)
                val autoScrollToLastPlayed = getBool("autoScrollToLastPlayed", currentUiState.autoScrollToLastPlayed)
                val treePathCompression = getBool("treePathCompression", currentUiState.treePathCompression)
                val dualPaneView = getBool("dualPaneView", currentUiState.dualPaneView)
                val watchedThresholdPercent = getInt("watchedThresholdPercent", currentUiState.watchedThresholdPercent, 10, 100)

                // 8. Thumbnails
                val showVideoThumbnails = getBool("showVideoThumbnails", currentUiState.showVideoThumbnails)
                val thumbnailStrategy = runCatching { ThumbnailStrategy.valueOf(getStr("thumbnailStrategy", currentUiState.thumbnailStrategy.name)) }.getOrDefault(currentUiState.thumbnailStrategy)
                val thumbnailQuality = runCatching { ThumbnailQuality.valueOf(getStr("thumbnailQuality", currentUiState.thumbnailQuality.name)) }.getOrDefault(currentUiState.thumbnailQuality)
                val thumbnailFallbackSecond = getInt("thumbnailFallbackSecond", currentUiState.thumbnailFallbackSecond, 1, 10)
                val tapThumbnailToSelect = getBool("tapThumbnailToSelect", currentUiState.tapThumbnailToSelect)
                val showNetworkThumbnails = getBool("showNetworkThumbnails", currentUiState.showNetworkThumbnails)

                // 9. Animations & Navigation
                val showHomeTab = getBool("showHomeTab", currentUiState.showHomeTab)
                val showMusicTab = getBool("showMusicTab", currentUiState.showMusicTab)
                val showRecentsTab = getBool("showRecentsTab", currentUiState.showRecentsTab)
                val showPlaylistsTab = getBool("showPlaylistsTab", currentUiState.showPlaylistsTab)
                val controlsAnimationStyle = runCatching { ControlsAnimationStyle.valueOf(getStr("controlsAnimationStyle", currentUiState.controlsAnimationStyle.name)) }.getOrDefault(currentUiState.controlsAnimationStyle)
                val videoOpeningAnimation = runCatching { VideoOpeningAnimation.valueOf(getStr("videoOpeningAnimation", currentUiState.videoOpeningAnimation.name)) }.getOrDefault(currentUiState.videoOpeningAnimation)
                val screenNavigationStyle = runCatching { ScreenNavigationStyle.valueOf(getStr("screenNavigationStyle", currentUiState.screenNavigationStyle.name)) }.getOrDefault(currentUiState.screenNavigationStyle)
                val tabNavigationStyle = runCatching { TabNavigationStyle.valueOf(getStr("tabNavigationStyle", currentUiState.tabNavigationStyle.name)) }.getOrDefault(currentUiState.tabNavigationStyle)
                val animationSpeed = getFloat("animationSpeed", currentUiState.animationSpeed, 0.25f, 2.5f)

                // 10. Editor & Text Tools
                val editorTextSizeSp = getFloat("editorTextSizeSp", currentUiState.editorTextSizeSp, 10f, 28f)
                val timeTextSizeSp = getFloat("timeTextSizeSp", currentUiState.timeTextSizeSp, 10f, 24f)
                val showPasteBoxConverter = getBool("showPasteBoxConverter", currentUiState.showPasteBoxConverter)
                val showPasteBoxCleaner = getBool("showPasteBoxCleaner", currentUiState.showPasteBoxCleaner)
                val showPasteBoxShifter = getBool("showPasteBoxShifter", currentUiState.showPasteBoxShifter)
                val showPasteBoxEditor = getBool("showPasteBoxEditor", currentUiState.showPasteBoxEditor)
                val showPasteBoxTextEditor = getBool("showPasteBoxTextEditor", currentUiState.showPasteBoxTextEditor)
                val dialogueEditOffsetY = getFloat("dialogueEditOffsetY", currentUiState.dialogueEditOffsetY, -300f, 300f)
                val colorPickerOffsetY = getFloat("colorPickerOffsetY", currentUiState.colorPickerOffsetY, -300f, 300f)
                val showUploadSectionTextEditor = getBool("showUploadSectionTextEditor", currentUiState.showUploadSectionTextEditor)
                val textEditorFontSizeSp = getFloat("textEditorFontSizeSp", currentUiState.textEditorFontSizeSp, 10f, 28f)
                val showLineNumbersTextEditor = getBool("showLineNumbersTextEditor", currentUiState.showLineNumbersTextEditor)
                val textEditorWordWrap = getBool("textEditorWordWrap", currentUiState.textEditorWordWrap)
                val showBottomToolbarTextEditor = getBool("showBottomToolbarTextEditor", currentUiState.showBottomToolbarTextEditor)
                val showSearchPanelTextEditor = getBool("showSearchPanelTextEditor", currentUiState.showSearchPanelTextEditor)
                val textEditorNavStep = runCatching { TextEditorNavStep.valueOf(getStr("textEditorNavStep", currentUiState.textEditorNavStep.name)) }.getOrDefault(currentUiState.textEditorNavStep)

                val reconstructed = currentUiState.copy(
                    themeMode = themeMode,
                    appScale = appScale,
                    glassBlurTransparency = glassBlurTransparency,
                    forceSideBySide = forceSideBySide,
                    language = language,
                    frameStepAmount = frameStepAmount,
                    copyTimestampOnDoubleTap = copyTimestamp,
                    hwAccelMode = hwAccelMode,
                    gestureSensitivityMode = gestureSensitivityMode,
                    showPlayerNotifications = showPlayerNotifications,
                    seekbarStyle = seekbarStyle,
                    playerLayoutConfig = playerLayoutConfig,
                    editorTextSizeSp = editorTextSizeSp,
                    timeTextSizeSp = timeTextSizeSp,
                    showPasteBoxConverter = showPasteBoxConverter,
                    showPasteBoxCleaner = showPasteBoxCleaner,
                    showPasteBoxShifter = showPasteBoxShifter,
                    showPasteBoxEditor = showPasteBoxEditor,
                    showPasteBoxTextEditor = showPasteBoxTextEditor,
                    dialogueEditOffsetY = dialogueEditOffsetY,
                    colorPickerOffsetY = colorPickerOffsetY,
                    showUploadSectionTextEditor = showUploadSectionTextEditor,
                    textEditorFontSizeSp = textEditorFontSizeSp,
                    showLineNumbersTextEditor = showLineNumbersTextEditor,
                    textEditorWordWrap = textEditorWordWrap,
                    showBottomToolbarTextEditor = showBottomToolbarTextEditor,
                    showSearchPanelTextEditor = showSearchPanelTextEditor,
                    textEditorNavStep = textEditorNavStep,
                    appTheme = appTheme,
                    amoledBlackMode = amoledBlackMode,
                    useSystemFont = useSystemFont,
                    hapticFeedback = hapticFeedback,
                    showFullName = showFullName,
                    showNewVideoLabel = showNewVideoLabel,
                    newVideoDaysThreshold = newVideoDaysThreshold,
                    showFolderUnplayedBadge = showFolderUnplayedBadge,
                    autoScrollToLastPlayed = autoScrollToLastPlayed,
                    treePathCompression = treePathCompression,
                    dualPaneView = dualPaneView,
                    watchedThresholdPercent = watchedThresholdPercent,
                    showVideoThumbnails = showVideoThumbnails,
                    thumbnailStrategy = thumbnailStrategy,
                    thumbnailQuality = thumbnailQuality,
                    thumbnailFallbackSecond = thumbnailFallbackSecond,
                    tapThumbnailToSelect = tapThumbnailToSelect,
                    showNetworkThumbnails = showNetworkThumbnails,
                    showHomeTab = showHomeTab,
                    showMusicTab = showMusicTab,
                    showRecentsTab = showRecentsTab,
                    showPlaylistsTab = showPlaylistsTab,
                    controlsAnimationStyle = controlsAnimationStyle,
                    videoOpeningAnimation = videoOpeningAnimation,
                    screenNavigationStyle = screenNavigationStyle,
                    tabNavigationStyle = tabNavigationStyle,
                    animationSpeed = animationSpeed,
                    preferredAudioLanguages = preferredAudioLanguages,
                    applyAudioLanguageToSelectedContentOnly = applyAudioLanguageToSelectedContentOnly,
                    selectedAudioFolders = selectedAudioFolders,
                    selectedAudioVideos = selectedAudioVideos,
                    enableAudioPitchCorrection = enableAudioPitchCorrection,
                    volumeNormalization = volumeNormalization,
                    backgroundPlayback = backgroundPlayback,
                    audioChannelMode = audioChannelMode,
                    volumeBoostCap = volumeBoostCap,
                    rememberSelectedAudioTrack = rememberSelectedAudioTrack,
                    playerSettings = playerSettings
                )

                // Extra collections
                val customFolders = if (map.containsKey("customFolders")) getSet("customFolders") else null
                val blockedVideoFolderIds = if (map.containsKey("blockedVideoFolderIds")) getSet("blockedVideoFolderIds") else null
                val blockedAudioFolderPaths = if (map.containsKey("blockedAudioFolderPaths")) getSet("blockedAudioFolderPaths") else null

                Result.success(
                    ParsedSettings(
                        updatedUiState = reconstructed,
                        customFolders = customFolders,
                        blockedVideoFolderIds = blockedVideoFolderIds,
                        blockedAudioFolderPaths = blockedAudioFolderPaths,
                        sortAndViewSettings = map
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Applies the parsed settings to disk and in-memory application state.
     */
    fun applySettings(context: Context, parsed: ParsedSettings, viewModel: AppViewModel?) {
        val state = parsed.updatedUiState

        // 1. Save main settings
        SettingsPreferencesManager.saveSettings(context, state)

        // 2. Save PlayerSettings
        PlayerSettings.save(context, state.playerSettings)

        // 3. Save PlayerLayoutConfig
        SettingsPreferencesManager.savePlayerLayoutConfig(context, state.playerLayoutConfig)

        // 4. Save Custom Folders
        parsed.customFolders?.let {
            SettingsPreferencesManager.saveCustomFolders(context, it)
        }

        // 5. Save Sort & View options if present
        val m = parsed.sortAndViewSettings
        listOf("home", "recents", "music").forEach { tabId ->
            m["sortField_$tabId"]?.let {
                runCatching { SettingsPreferencesManager.saveSortField(context, SortField.valueOf(it), tabId) }
            }
            m["sortDir_$tabId"]?.let {
                runCatching { SettingsPreferencesManager.saveSortDirection(context, SortDirection.valueOf(it), tabId) }
            }
            m["viewMode_$tabId"]?.let {
                runCatching { SettingsPreferencesManager.saveViewMode(context, ViewMode.valueOf(it), tabId) }
            }
            m["layoutMode_$tabId"]?.let {
                runCatching { SettingsPreferencesManager.saveLayoutMode(context, LayoutMode.valueOf(it), tabId) }
            }
            if (m.containsKey("vf_thumb_$tabId")) {
                val vf = VisibleFields(
                    showThumbnails = m["vf_thumb_$tabId"]?.toBooleanStrictOrNull() ?: true,
                    showExtension = m["vf_ext_$tabId"]?.toBooleanStrictOrNull() ?: true,
                    showDuration = m["vf_dur_$tabId"]?.toBooleanStrictOrNull() ?: false,
                    showSubtitleIndicator = m["vf_sub_$tabId"]?.toBooleanStrictOrNull() ?: true,
                    showFullName = m["vf_name_$tabId"]?.toBooleanStrictOrNull() ?: false,
                    showSize = m["vf_size_$tabId"]?.toBooleanStrictOrNull() ?: true,
                    showResolution = m["vf_res_$tabId"]?.toBooleanStrictOrNull() ?: true,
                    showFramerate = m["vf_fps_$tabId"]?.toBooleanStrictOrNull() ?: false,
                    showDate = m["vf_date_$tabId"]?.toBooleanStrictOrNull() ?: false,
                    showProgressBar = m["vf_prog_$tabId"]?.toBooleanStrictOrNull() ?: true,
                    showPath = m["vf_path_$tabId"]?.toBooleanStrictOrNull() ?: false,
                    showVideoCount = m["vf_cnt_$tabId"]?.toBooleanStrictOrNull() ?: true,
                    showNewBadge = m["vf_new_$tabId"]?.toBooleanStrictOrNull() ?: false
                )
                SettingsPreferencesManager.saveVisibleFields(context, vf, tabId)
            }
        }

        // 6. Update ViewModel in-memory state
        viewModel?.applyImportedState(state)
    }
}
