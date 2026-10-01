package com.example.ui.state

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import com.example.player.AudioChannelMode
import com.example.player.AudioLanguageMatcher
import com.example.ui.components.LayoutMode
import com.example.ui.components.SortDirection
import com.example.ui.components.SortField
import com.example.ui.components.ViewMode
import com.example.ui.components.VisibleFields
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

enum class AppThemeVariant(
    val id: String,
    val displayName: String,
    val description: String,
    val primaryColorHex: Long,
    val secondaryColorHex: Long,
    val lightBgHex: Long = 0xFFF1F5F9,
    val darkBgHex: Long = 0xFF090D16,
    val lightCardHex: Long = 0xFFFFFFFF,
    val darkCardHex: Long = 0xFF111827,
    val lightBorderHex: Long = 0xFFE2E8F0,
    val darkBorderHex: Long = 0xFF1F2937
) {
    DYNAMIC("dynamic", "Dynamic", "Material You Sky Blue to Pink gradient", 0xFF38BDF8, 0xFFEC4899, 0xFFF1F5F9, 0xFF090D16, 0xFFFFFFFF, 0xFF111827, 0xFFE2E8F0, 0xFF1F2937),
    CATPPUCCIN("catppuccin", "Catppuccin", "Soft lavender, mauve & rose", 0xFFCBA6F7, 0xFFF5C2E7, 0xFFEFF1F5, 0xFF1E1E2E, 0xFFE6E9EF, 0xFF313244, 0xFFCCD0DA, 0xFF45475A),
    AURORA("aurora", "Aurora", "Cool blue & indigo twilight palette", 0xFF38BDF8, 0xFF818CF8, 0xFFF0F9FF, 0xFF0A1128, 0xFFE0F2FE, 0xFF101F42, 0xFFBAE6FD, 0xFF1E3A8A),
    CLOUDFLARE("cloudflare", "Cloudflare", "Warm orange & amber glass palette", 0xFFF97316, 0xFFFBBF24, 0xFFFFFBEB, 0xFF1A1412, 0xFFFEF3C7, 0xFF291E1A, 0xFFFDE68A, 0xFF43281C),
    OCEAN("ocean", "Ocean", "Deep marine azure & vibrant cyan waves", 0xFF0EA5E9, 0xFF06B6D4, 0xFFF0FDFA, 0xFF081C24, 0xFFCCFBF1, 0xFF0D2D3A, 0xFF99F6E4, 0xFF155E75),
    SUNSET("sunset", "Sunset", "Vibrant coral rose & glowing sunset orange", 0xFFF43F5E, 0xFFFB923C, 0xFFFFF1F2, 0xFF1C0D13, 0xFFFFE4E6, 0xFF2D1520, 0xFFFECDD3, 0xFF4C1D2E),
    EMERALD("emerald", "Emerald", "Lush jade green & bright mint emerald", 0xFF10B981, 0xFF34D399, 0xFFF0FDF4, 0xFF061A12, 0xFFDCFCE7, 0xFF0F2E22, 0xFFBBF7D0, 0xFF166534),
    ROYAL("royal", "Royal", "Majestic imperial violet & royal indigo", 0xFF8B5CF6, 0xFF6366F1, 0xFFF5F3FF, 0xFF130E26, 0xFFEDE9FE, 0xFF20183F, 0xFFDDD6FE, 0xFF3730A3),
    ROSE("rose", "Rose", "Sweet cherry blossom & vivid crimson petals", 0xFFF472B6, 0xFFE11D48, 0xFFFFF1F2, 0xFF1B0C14, 0xFFFFE4E6, 0xFF2C1322, 0xFFFECDD3, 0xFF4A152E),
    MIDNIGHT("midnight", "Midnight", "Deep night slate & electric blue neon", 0xFF60A5FA, 0xFF818CF8, 0xFFF1F5F9, 0xFF0B0F19, 0xFFE2E8F0, 0xFF131B2E, 0xFFCBD5E1, 0xFF1E293B);

    val primaryColor: androidx.compose.ui.graphics.Color get() = androidx.compose.ui.graphics.Color(primaryColorHex)
    val secondaryColor: androidx.compose.ui.graphics.Color get() = androidx.compose.ui.graphics.Color(secondaryColorHex)
    val lightBgColor: androidx.compose.ui.graphics.Color get() = androidx.compose.ui.graphics.Color(lightBgHex)
    val darkBgColor: androidx.compose.ui.graphics.Color get() = androidx.compose.ui.graphics.Color(darkBgHex)
    val lightCardColor: androidx.compose.ui.graphics.Color get() = androidx.compose.ui.graphics.Color(lightCardHex)
    val darkCardColor: androidx.compose.ui.graphics.Color get() = androidx.compose.ui.graphics.Color(darkCardHex)
    val lightBorderColor: androidx.compose.ui.graphics.Color get() = androidx.compose.ui.graphics.Color(lightBorderHex)
    val darkBorderColor: androidx.compose.ui.graphics.Color get() = androidx.compose.ui.graphics.Color(darkBorderHex)
    val gradient: androidx.compose.ui.graphics.Brush get() = androidx.compose.ui.graphics.Brush.linearGradient(listOf(primaryColor, secondaryColor))
}

enum class ThumbnailStrategy(val displayName: String, val description: String) {
    SMART("Smart", "Samples the video itself and chooses a clear representative frame"),
    EMBEDDED_AND_GENERATED("Embedded + Generated", "Uses an embedded MKV image attachment first; generates a frame only when none is attached")
}

enum class ThumbnailQuality(val displayName: String, val description: String, val sizeDp: Int) {
    LOW("Low", "Fast loading low-res preview (120px)", 120),
    MEDIUM("Medium", "Balanced standard quality (240px)", 240),
    HIGH("High", "Crisp detailed rendering (480px)", 480),
    MAXIMUM("Maximum", "Largest supported thumbnail cache size (720px)", 720)
}

enum class ControlsAnimationStyle(val displayName: String, val description: String) {
    DEFAULT("Default", "Smooth standard fade and slide overlay"),
    CINEMATIC_SCALE("Cinematic Scale", "Subtle zoom and cinematic depth fade"),
    FLUID_EXPAND("Fluid Expand", "Elastic outward expansion from screen center"),
    GENTLE_BOUNCE("Gentle Bounce", "Playful spring bounce on appear/dismiss"),
    MINIMAL_FADE("Minimal Fade", "Instant crisp linear opacity dissolve"),
    NONE("None", "Immediate snap display without motion")
}

enum class VideoOpeningAnimation(val displayName: String, val description: String) {
    DEFAULT("Default", "Standard bottom sheet upwards slide"),
    FADE_FROM_BLACK("Fade from Black", "Theatrical cinematic blackout dissolve"),
    ZOOM_BURST("Zoom Burst", "Expands directly from tapped video thumbnail"),
    SLIDE_UP("Slide Up", "Clean vertical glide covering previous screen"),
    CINEMA_BARS("Cinema Bars", "Aspect ratio letterbox wipe transition"),
    NONE("None", "Instant player launch without delay")
}

enum class ScreenNavigationStyle(val displayName: String, val description: String) {
    DEFAULT("Default", "Standard horizontal slide transition"),
    ELASTIC_SLIDE("Elastic Slide", "Bouncy damped spring horizontal slide"),
    DEPTH_ZOOM("Depth Zoom", "Parallax background depth push effect"),
    FLIP_FADE("Flip Fade", "Subtle 3D card tilt and fade cross dissolve"),
    MINIMAL_FADE("Minimal Fade", "Linear alpha crossfade between views"),
    NONE("None", "Instant view change")
}

enum class TabNavigationStyle(val displayName: String, val description: String) {
    DEFAULT("Default", "Smooth sliding underline and subtle slide"),
    ELASTIC_SLIDE("Elastic Slide", "Dynamic spring pill indicator transition"),
    DEPTH_ZOOM("Depth Zoom", "Scale pulse on tab switch with blur dissolve"),
    FLIP_FADE("Flip Fade", "Quick cross-fade with directional glide"),
    MINIMAL_FADE("Minimal Fade", "Clean instantaneous opacity crossfade"),
    NONE("None", "Direct tab switch without animation")
}

enum class AppLanguage {
    ENGLISH, HINGLISH, HINDI
}

enum class HwAccelMode(val displayName: String, val mpvValue: String, val description: String) {
    FORCE("Force MediaCodec", "mediacodec", "Direct MediaCodec HW acceleration for max frame sync & smoothness"),
    PREFER("Prefer (Auto Copy)", "mediacodec-copy", "Hardware decoding with auto copyback buffer fallback"),
    DISABLE("Disable (Software CPU)", "no", "Software CPU decoding to troubleshoot persistent GPU/rendering glitches");

    fun getDisplayName(lang: AppLanguage): String = when (this) {
        FORCE -> AppStrings.getHwForceTitle(lang)
        PREFER -> AppStrings.getHwPreferTitle(lang)
        DISABLE -> AppStrings.getHwDisableTitle(lang)
    }

    fun getDescription(lang: AppLanguage): String = when (this) {
        FORCE -> AppStrings.getHwForceDesc(lang)
        PREFER -> AppStrings.getHwPreferDesc(lang)
        DISABLE -> AppStrings.getHwDisableDesc(lang)
    }
}

enum class TextEditorNavStep {
    CHARACTER, WORD
}

enum class SeekbarStyle(val displayName: String, val description: String) {
    NORMAL(
        displayName = "Normal",
        description = "Classic sleek line with circular thumb knob"
    ),
    STANDARD(
        displayName = "Standard",
        description = "Medium rounded bar with vertical dividing pill"
    ),
    WAVY(
        displayName = "Wavy",
        description = "Dynamic wavy sine curve with dividing pin"
    ),
    THICK(
        displayName = "Thick",
        description = "Bold thick rounded pill bar with vertical dividing pin"
    ),
    SLIM(
        displayName = "Slim",
        description = "Clean continuous flat pill track without thumb"
    )
}

enum class GestureSensitivityMode(val displayName: String, val shortLabel: String, val description: String) {
    LINEAR(
        displayName = "Linear (1:1 Uniform)",
        shortLabel = "Linear",
        description = "Direct 1:1 proportional swipe response for uniform volume & brightness steps"
    ),
    EXPONENTIAL(
        displayName = "Exponential (Perceptual Curve)",
        shortLabel = "Exponential",
        description = "Fine-grain precision at lower levels with smooth dynamic curve to prevent lag & jumps"
    );

    fun getDisplayName(lang: AppLanguage): String = when (this) {
        LINEAR -> AppStrings.getGestureLinearTitle(lang)
        EXPONENTIAL -> AppStrings.getGestureExponentialTitle(lang)
    }

    fun getDescription(lang: AppLanguage): String = when (this) {
        LINEAR -> AppStrings.getGestureLinearDesc(lang)
        EXPONENTIAL -> AppStrings.getGestureExponentialDesc(lang)
    }
}

data class UiState(
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val appScale: Float = 75f, // 1f (maps to 85% min size) to 100f (maps to 100% full size)
    val glassBlurTransparency: Float = 85f, // 10f to 100f (controls frosted glass blur opacity & translucency)
    val forceSideBySide: Boolean = false,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val frameStepAmount: Int = 1, // 1 to 30 frames
    val copyTimestampOnDoubleTap: Boolean = true,
    val hwAccelMode: HwAccelMode = HwAccelMode.FORCE,
    val gestureSensitivityMode: GestureSensitivityMode = GestureSensitivityMode.EXPONENTIAL,
    val editorTextSizeSp: Float = 14f, // 10f to 28f
    val timeTextSizeSp: Float = 13.5f, // 10f to 24f
    val showPasteBoxConverter: Boolean = true,
    val showPasteBoxCleaner: Boolean = true,
    val showPasteBoxShifter: Boolean = true,
    val showPasteBoxEditor: Boolean = true,
    val showPasteBoxTextEditor: Boolean = true,
    val dialogueEditOffsetY: Float = 0f, // -300f to 300f dp
    val colorPickerOffsetY: Float = 0f,  // -300f to 300f dp
    val showUploadSectionTextEditor: Boolean = true,
    val textEditorFontSizeSp: Float = 14f, // 10f to 28f
    val showLineNumbersTextEditor: Boolean = true,
    val textEditorWordWrap: Boolean = true,
    val showBottomToolbarTextEditor: Boolean = true,
    val showSearchPanelTextEditor: Boolean = true,
    val textEditorNavStep: TextEditorNavStep = TextEditorNavStep.CHARACTER,
    val showPlayerNotifications: Boolean = true,
    val seekbarStyle: SeekbarStyle = SeekbarStyle.STANDARD,
    val playerLayoutConfig: PlayerLayoutConfig = PlayerLayoutConfig.default,
    val playerSettings: PlayerSettings = PlayerSettings(),

    // Appearance
    val appTheme: AppThemeVariant = AppThemeVariant.DYNAMIC,
    val amoledBlackMode: Boolean = false,
    val useSystemFont: Boolean = false,
    val hapticFeedback: Boolean = true,

    // File Browser
    val showFullName: Boolean = false,
    val showNewVideoLabel: Boolean = true,
    val newVideoDaysThreshold: Int = 7,
    val showFolderUnplayedBadge: Boolean = true,
    val autoScrollToLastPlayed: Boolean = true,
    val treePathCompression: Boolean = true,
    val dualPaneView: Boolean = false,
    val watchedThresholdPercent: Int = 95,

    // Thumbnails
    val showVideoThumbnails: Boolean = true,
    val thumbnailStrategy: ThumbnailStrategy = ThumbnailStrategy.SMART,
    val thumbnailQuality: ThumbnailQuality = ThumbnailQuality.HIGH,
    val thumbnailFallbackSecond: Int = 1,
    val tapThumbnailToSelect: Boolean = false,
    val showNetworkThumbnails: Boolean = false,

    // Navigation
    val showHomeTab: Boolean = true,
    val showMusicTab: Boolean = true,
    val showRecentsTab: Boolean = true,
    val showPlaylistsTab: Boolean = true,

    // Animations
    val controlsAnimationStyle: ControlsAnimationStyle = ControlsAnimationStyle.DEFAULT,
    val videoOpeningAnimation: VideoOpeningAnimation = VideoOpeningAnimation.DEFAULT,
    val screenNavigationStyle: ScreenNavigationStyle = ScreenNavigationStyle.DEFAULT,
    val tabNavigationStyle: TabNavigationStyle = TabNavigationStyle.DEFAULT,
    val animationSpeed: Float = 1.0f,

    // Audio & Sound System Preferences
    val preferredAudioLanguages: String = "",
    val applyAudioLanguageToSelectedContentOnly: Boolean = false,
    val selectedAudioFolders: Set<String> = emptySet(),
    val selectedAudioVideos: Set<String> = emptySet(),
    val enableAudioPitchCorrection: Boolean = true,
    val volumeNormalization: Boolean = false,
    val backgroundPlayback: Boolean = false,
    val audioChannelMode: AudioChannelMode = AudioChannelMode.AUTO,
    val volumeBoostCap: Int = 150,
    val rememberSelectedAudioTrack: Boolean = true
) {
    val isSystemFontEnabled: Boolean get() = useSystemFont
}

typealias AppConfig = SettingsPreferencesManager

object SettingsPreferencesManager {
    private const val PREF_NAME = "video_player_settings_prefs"
    private const val KEY_THEME_MODE = "key_theme_mode"
    private const val KEY_APP_SCALE = "key_app_scale"
    private const val KEY_GLASS_BLUR_TRANSPARENCY = "key_glass_blur_transparency"
    private const val KEY_FORCE_SIDE_BY_SIDE = "key_force_side_by_side"
    private const val KEY_LANGUAGE = "key_language"
    private const val KEY_FRAME_STEP_AMOUNT = "key_frame_step_amount"
    private const val KEY_COPY_TIMESTAMP = "key_copy_timestamp"
    private const val KEY_HW_ACCEL_MODE = "key_hw_accel_mode"
    private const val KEY_GESTURE_SENSITIVITY_MODE = "key_gesture_sensitivity_mode"
    private const val KEY_SHOW_PLAYER_NOTIFICATIONS = "key_show_player_notifications"
    private const val KEY_SEEKBAR_STYLE = "key_seekbar_style"
    private const val KEY_TOP_RIGHT_CONTROLS = "key_top_right_controls"
    private const val KEY_BOTTOM_LEFT_CONTROLS = "key_bottom_left_controls"
    private const val KEY_BOTTOM_RIGHT_CONTROLS = "key_bottom_right_controls"
    private const val KEY_LANDSCAPE_TR_ENABLED = "key_landscape_tr_enabled"
    private const val KEY_LANDSCAPE_BL_ENABLED = "key_landscape_bl_enabled"
    private const val KEY_LANDSCAPE_BR_ENABLED = "key_landscape_br_enabled"
    private const val KEY_PORTRAIT_TOP_CONTROLS = "key_portrait_top_controls"
    private const val KEY_PORTRAIT_BOTTOM_CONTROLS = "key_portrait_bottom_controls"
    private const val KEY_PORTRAIT_TOP_ENABLED = "key_portrait_top_enabled"
    private const val KEY_PORTRAIT_BOTTOM_ENABLED = "key_portrait_bottom_enabled"
    private const val KEY_EDITOR_TEXT_SIZE = "key_editor_text_size"
    private const val KEY_TIME_TEXT_SIZE = "key_time_text_size"
    private const val KEY_PASTE_CONVERTER = "key_paste_converter"
    private const val KEY_PASTE_CLEANER = "key_paste_cleaner"
    private const val KEY_PASTE_SHIFTER = "key_paste_shifter"
    private const val KEY_PASTE_EDITOR = "key_paste_editor"
    private const val KEY_PASTE_TEXT_EDITOR = "key_paste_text_editor"
    private const val KEY_DIALOGUE_EDIT_OFFSET_Y = "key_dialogue_edit_offset_y"
    private const val KEY_COLOR_PICKER_OFFSET_Y = "key_color_picker_offset_y"
    private const val KEY_SHOW_UPLOAD_SECTION_TEXT_EDITOR = "key_show_upload_section_text_editor"
    private const val KEY_TEXT_EDITOR_FONT_SIZE = "key_text_editor_font_size"
    private const val KEY_SHOW_LINE_NUMBERS_TEXT_EDITOR = "key_show_line_numbers_text_editor"
    private const val KEY_TEXT_EDITOR_WORD_WRAP = "key_text_editor_word_wrap"
    private const val KEY_SHOW_BOTTOM_TOOLBAR_TEXT_EDITOR = "key_show_bottom_toolbar_text_editor"
    private const val KEY_SHOW_SEARCH_PANEL_TEXT_EDITOR = "key_show_search_panel_text_editor"
    private const val KEY_TEXT_EDITOR_NAV_STEP = "key_text_editor_nav_step"

    // Appearance Keys
    private const val KEY_APP_THEME = "key_app_theme"
    private const val KEY_AMOLED_BLACK_MODE = "key_amoled_black_mode"
    private const val KEY_USE_SYSTEM_FONT = "key_use_system_font"
    private const val KEY_HAPTIC_FEEDBACK = "key_haptic_feedback"

    // File Browser Keys
    private const val KEY_SHOW_FULL_NAME = "key_show_full_name"
    private const val KEY_SHOW_NEW_VIDEO_LABEL = "key_show_new_video_label"
    private const val KEY_NEW_VIDEO_DAYS_THRESHOLD = "key_new_video_days_threshold"
    private const val KEY_SHOW_FOLDER_UNPLAYED_BADGE = "key_show_folder_unplayed_badge"
    private const val KEY_AUTO_SCROLL_TO_LAST_PLAYED = "key_auto_scroll_to_last_played"
    private const val KEY_TREE_PATH_COMPRESSION = "key_tree_path_compression"
    private const val KEY_DUAL_PANE_VIEW = "key_dual_pane_view"
    private const val KEY_WATCHED_THRESHOLD_PERCENT = "key_watched_threshold_percent"

    // Thumbnails Keys
    private const val KEY_SHOW_VIDEO_THUMBNAILS = "key_show_video_thumbnails"
    private const val KEY_THUMBNAIL_STRATEGY = "key_thumbnail_strategy"
    private const val KEY_THUMBNAIL_QUALITY = "key_thumbnail_quality"
    private const val KEY_THUMBNAIL_FALLBACK_SECOND = "key_thumbnail_fallback_second"
    private const val KEY_TAP_THUMBNAIL_TO_SELECT = "key_tap_thumbnail_to_select"
    private const val KEY_SHOW_NETWORK_THUMBNAILS = "key_show_network_thumbnails"

    // Navigation Keys
    private const val KEY_SHOW_HOME_TAB = "key_show_home_tab"
    private const val KEY_SHOW_MUSIC_TAB = "key_show_music_tab"
    private const val KEY_SHOW_RECENTS_TAB = "key_show_recents_tab"
    private const val KEY_SHOW_PLAYLISTS_TAB = "key_show_playlists_tab"

    // Animations Keys
    private const val KEY_CONTROLS_ANIMATION_STYLE = "key_controls_animation_style"
    private const val KEY_VIDEO_OPENING_ANIMATION = "key_video_opening_animation"
    private const val KEY_SCREEN_NAVIGATION_STYLE = "key_screen_navigation_style"
    private const val KEY_TAB_NAVIGATION_STYLE = "key_tab_navigation_style"
    private const val KEY_ANIMATION_SPEED = "key_animation_speed"

    // Audio & Sound Keys
    private const val KEY_PREFERRED_AUDIO_LANGUAGES = "key_preferred_audio_languages"
    private const val KEY_APPLY_AUDIO_LANG_SELECTED_ONLY = "key_apply_audio_lang_selected_only"
    private const val KEY_SELECTED_AUDIO_FOLDERS = "key_selected_audio_folders"
    private const val KEY_SELECTED_AUDIO_VIDEOS = "key_selected_audio_videos"
    private const val KEY_ENABLE_AUDIO_PITCH_CORRECTION = "key_enable_audio_pitch_correction"
    private const val KEY_VOLUME_NORMALIZATION = "key_volume_normalization"
    private const val KEY_BACKGROUND_PLAYBACK = "key_background_playback"
    private const val KEY_AUDIO_CHANNEL_MODE = "key_audio_channel_mode"
    private const val KEY_VOLUME_BOOST_CAP = "key_volume_boost_cap"
    private const val KEY_REMEMBER_SELECTED_AUDIO_TRACK = "key_remember_selected_audio_track"
    private const val KEY_REM_TRACK_PREFIX = "key_rem_track_"

    // Sort & View Options Keys
    private const val KEY_SORT_FIELD = "key_sort_field"
    private const val KEY_SORT_DIRECTION = "key_sort_direction"
    private const val KEY_VIEW_MODE = "key_view_mode"
    private const val KEY_LAYOUT_MODE = "key_layout_mode"

    // Visible Fields Keys
    private const val KEY_FIELD_SHOW_THUMBNAILS = "key_field_show_thumbnails"
    private const val KEY_FIELD_SHOW_EXTENSION = "key_field_show_extension"
    private const val KEY_FIELD_SHOW_DURATION = "key_field_show_duration"
    private const val KEY_FIELD_SHOW_SUBTITLE_INDICATOR = "key_field_show_subtitle_indicator"
    private const val KEY_FIELD_SHOW_FULL_NAME = "key_field_show_full_name"
    private const val KEY_FIELD_SHOW_SIZE = "key_field_show_size"
    private const val KEY_FIELD_SHOW_RESOLUTION = "key_field_show_resolution"
    private const val KEY_FIELD_SHOW_FRAMERATE = "key_field_show_framerate"
    private const val KEY_FIELD_SHOW_DATE = "key_field_show_date"
    private const val KEY_FIELD_SHOW_PROGRESS_BAR = "key_field_show_progress_bar"
    private const val KEY_FIELD_SHOW_PATH = "key_field_show_path"
    private const val KEY_FIELD_SHOW_VIDEO_COUNT = "key_field_show_video_count"
    private const val KEY_FIELD_SHOW_NEW_BADGE = "key_field_show_new_badge"
    private const val KEY_FIELD_SHOW_AUDIO_TYPE = "key_field_show_audio_type"

    // Custom Folders Key
    private const val KEY_CUSTOM_FOLDERS = "key_custom_folders"

    // Last Used Extract Directory Key
    private const val KEY_LAST_EXTRACT_DIRECTORY = "key_last_extract_directory"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getLastExtractDirectory(context: Context): String? {
        val path = getPrefs(context).getString(KEY_LAST_EXTRACT_DIRECTORY, null)
        if (!path.isNullOrBlank()) {
            val f = java.io.File(path)
            if (f.exists() && f.isDirectory && f.canRead()) {
                return f.absolutePath
            }
        }
        return null
    }

    fun setLastExtractDirectory(context: Context, path: String) {
        if (path.isNotBlank()) {
            getPrefs(context).edit().putString(KEY_LAST_EXTRACT_DIRECTORY, path).apply()
        }
    }

    fun loadSettings(context: Context): UiState {
        val prefs = getPrefs(context)
        val themeStr = prefs.getString(KEY_THEME_MODE, ThemeMode.LIGHT.name) ?: ThemeMode.LIGHT.name
        val themeMode = try {
            ThemeMode.valueOf(themeStr)
        } catch (_: Exception) {
            ThemeMode.LIGHT
        }

        val appScale = prefs.getFloat(KEY_APP_SCALE, 75f).coerceIn(1f, 100f)
        val glassBlurTransparency = prefs.getFloat(KEY_GLASS_BLUR_TRANSPARENCY, 85f).coerceIn(10f, 100f)
        val forceSideBySide = prefs.getBoolean(KEY_FORCE_SIDE_BY_SIDE, false)

        val langStr = prefs.getString(KEY_LANGUAGE, AppLanguage.ENGLISH.name) ?: AppLanguage.ENGLISH.name
        val language = try {
            AppLanguage.valueOf(langStr)
        } catch (_: Exception) {
            AppLanguage.ENGLISH
        }

        val frameStepAmount = prefs.getInt(KEY_FRAME_STEP_AMOUNT, 1).coerceIn(1, 30)
        val copyTimestamp = prefs.getBoolean(KEY_COPY_TIMESTAMP, true)

        val hwStr = prefs.getString(KEY_HW_ACCEL_MODE, HwAccelMode.FORCE.name) ?: HwAccelMode.FORCE.name
        val hwAccelMode = try {
            HwAccelMode.valueOf(hwStr)
        } catch (_: Exception) {
            HwAccelMode.FORCE
        }

        val gestureStr = prefs.getString(KEY_GESTURE_SENSITIVITY_MODE, GestureSensitivityMode.EXPONENTIAL.name) ?: GestureSensitivityMode.EXPONENTIAL.name
        val gestureSensitivityMode = try {
            GestureSensitivityMode.valueOf(gestureStr)
        } catch (_: Exception) {
            GestureSensitivityMode.EXPONENTIAL
        }

        val editorTextSizeSp = prefs.getFloat(KEY_EDITOR_TEXT_SIZE, 14f).coerceIn(10f, 28f)
        val timeTextSizeSp = prefs.getFloat(KEY_TIME_TEXT_SIZE, 13.5f).coerceIn(10f, 24f)
        val showPasteBoxConverter = prefs.getBoolean(KEY_PASTE_CONVERTER, true)
        val showPasteBoxCleaner = prefs.getBoolean(KEY_PASTE_CLEANER, true)
        val showPasteBoxShifter = prefs.getBoolean(KEY_PASTE_SHIFTER, true)
        val showPasteBoxEditor = prefs.getBoolean(KEY_PASTE_EDITOR, true)
        val showPasteBoxTextEditor = prefs.getBoolean(KEY_PASTE_TEXT_EDITOR, true)
        val dialogueEditOffsetY = prefs.getFloat(KEY_DIALOGUE_EDIT_OFFSET_Y, 0f).coerceIn(-300f, 300f)
        val colorPickerOffsetY = prefs.getFloat(KEY_COLOR_PICKER_OFFSET_Y, 0f).coerceIn(-300f, 300f)
        val showUploadSectionTextEditor = prefs.getBoolean(KEY_SHOW_UPLOAD_SECTION_TEXT_EDITOR, true)
        val textEditorFontSizeSp = prefs.getFloat(KEY_TEXT_EDITOR_FONT_SIZE, 14f).coerceIn(10f, 28f)
        val showLineNumbersTextEditor = prefs.getBoolean(KEY_SHOW_LINE_NUMBERS_TEXT_EDITOR, true)
        val textEditorWordWrap = prefs.getBoolean(KEY_TEXT_EDITOR_WORD_WRAP, true)
        val showBottomToolbarTextEditor = prefs.getBoolean(KEY_SHOW_BOTTOM_TOOLBAR_TEXT_EDITOR, true)
        val showSearchPanelTextEditor = prefs.getBoolean(KEY_SHOW_SEARCH_PANEL_TEXT_EDITOR, true)
        val navStepStr = prefs.getString(KEY_TEXT_EDITOR_NAV_STEP, TextEditorNavStep.CHARACTER.name) ?: TextEditorNavStep.CHARACTER.name
        val textEditorNavStep = try {
            TextEditorNavStep.valueOf(navStepStr)
        } catch (_: Exception) {
            TextEditorNavStep.CHARACTER
        }

        val showPlayerNotifications = prefs.getBoolean(KEY_SHOW_PLAYER_NOTIFICATIONS, true)
        val seekbarStyleStr = prefs.getString(KEY_SEEKBAR_STYLE, SeekbarStyle.STANDARD.name) ?: SeekbarStyle.STANDARD.name
        val seekbarStyle = try {
            SeekbarStyle.valueOf(seekbarStyleStr)
        } catch (_: Exception) {
            SeekbarStyle.STANDARD
        }

        val playerLayoutConfig = loadPlayerLayoutConfig(context)

        // Appearance
        val themeVariantStr = prefs.getString(KEY_APP_THEME, AppThemeVariant.DYNAMIC.name) ?: AppThemeVariant.DYNAMIC.name
        val appTheme = try {
            AppThemeVariant.valueOf(themeVariantStr)
        } catch (_: Exception) {
            AppThemeVariant.DYNAMIC
        }
        val amoledBlackMode = prefs.getBoolean(KEY_AMOLED_BLACK_MODE, false)
        val useSystemFont = prefs.getBoolean(KEY_USE_SYSTEM_FONT, false)
        val hapticFeedback = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)
        com.example.util.AppHaptics.isEnabled = hapticFeedback

        // File Browser
        val showFullName = prefs.getBoolean(KEY_SHOW_FULL_NAME, false)
        val showNewVideoLabel = prefs.getBoolean(KEY_SHOW_NEW_VIDEO_LABEL, true)
        val newVideoDaysThreshold = prefs.getInt(KEY_NEW_VIDEO_DAYS_THRESHOLD, 7).coerceIn(1, 30)
        val showFolderUnplayedBadge = prefs.getBoolean(KEY_SHOW_FOLDER_UNPLAYED_BADGE, true)
        val autoScrollToLastPlayed = prefs.getBoolean(KEY_AUTO_SCROLL_TO_LAST_PLAYED, true)
        val treePathCompression = prefs.getBoolean(KEY_TREE_PATH_COMPRESSION, true)
        val dualPaneView = prefs.getBoolean(KEY_DUAL_PANE_VIEW, false)
        val watchedThresholdPercent = prefs.getInt(KEY_WATCHED_THRESHOLD_PERCENT, 95).coerceIn(50, 100)

        // Thumbnails
        val showVideoThumbnails = prefs.getBoolean(KEY_SHOW_VIDEO_THUMBNAILS, true)
        val thumbStratStr = prefs.getString(KEY_THUMBNAIL_STRATEGY, ThumbnailStrategy.SMART.name) ?: ThumbnailStrategy.SMART.name
        val thumbnailStrategy = try {
            ThumbnailStrategy.valueOf(thumbStratStr)
        } catch (_: Exception) {
            ThumbnailStrategy.SMART
        }
        val thumbQualStr = prefs.getString(KEY_THUMBNAIL_QUALITY, ThumbnailQuality.HIGH.name) ?: ThumbnailQuality.HIGH.name
        val thumbnailQuality = try {
            ThumbnailQuality.valueOf(thumbQualStr)
        } catch (_: Exception) {
            ThumbnailQuality.HIGH
        }
        val thumbnailFallbackSecond = prefs.getInt(KEY_THUMBNAIL_FALLBACK_SECOND, 1).coerceIn(1, 10)
        val tapThumbnailToSelect = prefs.getBoolean(KEY_TAP_THUMBNAIL_TO_SELECT, false)
        val showNetworkThumbnails = prefs.getBoolean(KEY_SHOW_NETWORK_THUMBNAILS, false)

        // Navigation
        val showHomeTab = prefs.getBoolean(KEY_SHOW_HOME_TAB, true)
        val showMusicTab = prefs.getBoolean(KEY_SHOW_MUSIC_TAB, true)
        val showRecentsTab = prefs.getBoolean(KEY_SHOW_RECENTS_TAB, true)
        val showPlaylistsTab = prefs.getBoolean(KEY_SHOW_PLAYLISTS_TAB, true)

        // Animations
        val ctrlAnimStr = prefs.getString(KEY_CONTROLS_ANIMATION_STYLE, ControlsAnimationStyle.DEFAULT.name) ?: ControlsAnimationStyle.DEFAULT.name
        val controlsAnimationStyle = try {
            ControlsAnimationStyle.valueOf(ctrlAnimStr)
        } catch (_: Exception) {
            ControlsAnimationStyle.DEFAULT
        }
        val vidOpenAnimStr = prefs.getString(KEY_VIDEO_OPENING_ANIMATION, VideoOpeningAnimation.DEFAULT.name) ?: VideoOpeningAnimation.DEFAULT.name
        val videoOpeningAnimation = try {
            VideoOpeningAnimation.valueOf(vidOpenAnimStr)
        } catch (_: Exception) {
            VideoOpeningAnimation.DEFAULT
        }
        val scrNavStr = prefs.getString(KEY_SCREEN_NAVIGATION_STYLE, ScreenNavigationStyle.DEFAULT.name) ?: ScreenNavigationStyle.DEFAULT.name
        val screenNavigationStyle = try {
            ScreenNavigationStyle.valueOf(scrNavStr)
        } catch (_: Exception) {
            ScreenNavigationStyle.DEFAULT
        }
        val tabNavStr = prefs.getString(KEY_TAB_NAVIGATION_STYLE, TabNavigationStyle.DEFAULT.name) ?: TabNavigationStyle.DEFAULT.name
        val tabNavigationStyle = try {
            TabNavigationStyle.valueOf(tabNavStr)
        } catch (_: Exception) {
            TabNavigationStyle.DEFAULT
        }
        val animationSpeed = prefs.getFloat(KEY_ANIMATION_SPEED, 1.0f).coerceIn(0.25f, 2.0f)

        // Audio Settings Load
        val preferredAudioLanguages = prefs.getString(KEY_PREFERRED_AUDIO_LANGUAGES, "") ?: ""
        val applyAudioLanguageToSelectedContentOnly = prefs.getBoolean(KEY_APPLY_AUDIO_LANG_SELECTED_ONLY, false)
        val selectedAudioFolders = prefs.getStringSet(KEY_SELECTED_AUDIO_FOLDERS, emptySet()) ?: emptySet()
        val selectedAudioVideos = prefs.getStringSet(KEY_SELECTED_AUDIO_VIDEOS, emptySet()) ?: emptySet()
        val enableAudioPitchCorrection = prefs.getBoolean(KEY_ENABLE_AUDIO_PITCH_CORRECTION, true)
        val volumeNormalization = prefs.getBoolean(KEY_VOLUME_NORMALIZATION, false)
        val backgroundPlayback = prefs.getBoolean(KEY_BACKGROUND_PLAYBACK, false)
        val audioChannelStr = prefs.getString(KEY_AUDIO_CHANNEL_MODE, AudioChannelMode.AUTO.name) ?: AudioChannelMode.AUTO.name
        val audioChannelMode = try {
            AudioChannelMode.valueOf(audioChannelStr)
        } catch (_: Exception) {
            AudioChannelMode.AUTO
        }
        val volumeBoostCap = prefs.getInt(KEY_VOLUME_BOOST_CAP, 150).coerceIn(100, 200)
        val rememberSelectedAudioTrack = prefs.getBoolean(KEY_REMEMBER_SELECTED_AUDIO_TRACK, true)

        return UiState(
            themeMode = themeMode,
            appScale = appScale,
            glassBlurTransparency = glassBlurTransparency,
            forceSideBySide = forceSideBySide,
            language = language,
            frameStepAmount = frameStepAmount,
            copyTimestampOnDoubleTap = copyTimestamp,
            hwAccelMode = hwAccelMode,
            gestureSensitivityMode = gestureSensitivityMode,
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
            showPlayerNotifications = showPlayerNotifications,
            seekbarStyle = seekbarStyle,
            playerLayoutConfig = playerLayoutConfig,
            playerSettings = PlayerSettings.load(context),

            // Appearance
            appTheme = appTheme,
            amoledBlackMode = amoledBlackMode,
            useSystemFont = useSystemFont,
            hapticFeedback = hapticFeedback,

            // File Browser
            showFullName = showFullName,
            showNewVideoLabel = showNewVideoLabel,
            newVideoDaysThreshold = newVideoDaysThreshold,
            showFolderUnplayedBadge = showFolderUnplayedBadge,
            autoScrollToLastPlayed = autoScrollToLastPlayed,
            treePathCompression = treePathCompression,
            dualPaneView = dualPaneView,
            watchedThresholdPercent = watchedThresholdPercent,

            // Thumbnails
            showVideoThumbnails = showVideoThumbnails,
            thumbnailStrategy = thumbnailStrategy,
            thumbnailQuality = thumbnailQuality,
            thumbnailFallbackSecond = thumbnailFallbackSecond,
            tapThumbnailToSelect = tapThumbnailToSelect,
            showNetworkThumbnails = showNetworkThumbnails,

            // Navigation
            showHomeTab = showHomeTab,
            showMusicTab = showMusicTab,
            showRecentsTab = showRecentsTab,
            showPlaylistsTab = showPlaylistsTab,

            // Animations
            controlsAnimationStyle = controlsAnimationStyle,
            videoOpeningAnimation = videoOpeningAnimation,
            screenNavigationStyle = screenNavigationStyle,
            tabNavigationStyle = tabNavigationStyle,
            animationSpeed = animationSpeed,

            // Audio & Sound
            preferredAudioLanguages = preferredAudioLanguages,
            applyAudioLanguageToSelectedContentOnly = applyAudioLanguageToSelectedContentOnly,
            selectedAudioFolders = selectedAudioFolders,
            selectedAudioVideos = selectedAudioVideos,
            enableAudioPitchCorrection = enableAudioPitchCorrection,
            volumeNormalization = volumeNormalization,
            backgroundPlayback = backgroundPlayback,
            audioChannelMode = audioChannelMode,
            volumeBoostCap = volumeBoostCap,
            rememberSelectedAudioTrack = rememberSelectedAudioTrack
        )
    }

    fun loadPlayerLayoutConfig(context: Context): PlayerLayoutConfig {
        val prefs = getPrefs(context)
        val trStr = prefs.getString(KEY_TOP_RIGHT_CONTROLS, null)
        val blStr = prefs.getString(KEY_BOTTOM_LEFT_CONTROLS, null)
        val brStr = prefs.getString(KEY_BOTTOM_RIGHT_CONTROLS, null)
        val trEnabled = prefs.getBoolean(KEY_LANDSCAPE_TR_ENABLED, true)
        val blEnabled = prefs.getBoolean(KEY_LANDSCAPE_BL_ENABLED, true)
        val brEnabled = prefs.getBoolean(KEY_LANDSCAPE_BR_ENABLED, true)

        val ptStr = prefs.getString(KEY_PORTRAIT_TOP_CONTROLS, null)
        val pbStr = prefs.getString(KEY_PORTRAIT_BOTTOM_CONTROLS, null)
        val ptEnabled = prefs.getBoolean(KEY_PORTRAIT_TOP_ENABLED, true)
        val pbEnabled = prefs.getBoolean(KEY_PORTRAIT_BOTTOM_ENABLED, true)

        if (trStr == null && blStr == null && brStr == null && ptStr == null && pbStr == null) {
            return PlayerLayoutConfig.default
        }

        val trList = trStr?.split(",")?.mapNotNull { PlayerControlId.fromId(it.trim()) } ?: PlayerLayoutConfig.defaultTopRightControls
        val blList = blStr?.split(",")?.mapNotNull { PlayerControlId.fromId(it.trim()) } ?: PlayerLayoutConfig.defaultBottomLeftControls
        val brList = brStr?.split(",")?.mapNotNull { PlayerControlId.fromId(it.trim()) } ?: PlayerLayoutConfig.defaultBottomRightControls

        val ptList = ptStr?.split(",")?.mapNotNull { PlayerControlId.fromId(it.trim()) } ?: PlayerLayoutConfig.defaultPortraitTopControls
        val pbList = pbStr?.split(",")?.mapNotNull { PlayerControlId.fromId(it.trim()) } ?: PlayerLayoutConfig.defaultPortraitBottomControls

        return PlayerLayoutConfig(
            topRightControls = trList,
            bottomLeftControls = blList,
            bottomRightControls = brList,
            isLandscapeTopRightEnabled = trEnabled,
            isLandscapeBottomLeftEnabled = blEnabled,
            isLandscapeBottomRightEnabled = brEnabled,
            portraitTopControls = ptList,
            portraitBottomControls = pbList,
            isPortraitTopEnabled = ptEnabled,
            isPortraitBottomEnabled = pbEnabled
        ).sanitized()
    }

    fun savePlayerLayoutConfig(context: Context, config: PlayerLayoutConfig) {
        val sanitized = config.sanitized()
        getPrefs(context).edit()
            .putString(KEY_TOP_RIGHT_CONTROLS, sanitized.topRightControls.joinToString(",") { it.id })
            .putString(KEY_BOTTOM_LEFT_CONTROLS, sanitized.bottomLeftControls.joinToString(",") { it.id })
            .putString(KEY_BOTTOM_RIGHT_CONTROLS, sanitized.bottomRightControls.joinToString(",") { it.id })
            .putBoolean(KEY_LANDSCAPE_TR_ENABLED, sanitized.isLandscapeTopRightEnabled)
            .putBoolean(KEY_LANDSCAPE_BL_ENABLED, sanitized.isLandscapeBottomLeftEnabled)
            .putBoolean(KEY_LANDSCAPE_BR_ENABLED, sanitized.isLandscapeBottomRightEnabled)
            .putString(KEY_PORTRAIT_TOP_CONTROLS, sanitized.portraitTopControls.joinToString(",") { it.id })
            .putString(KEY_PORTRAIT_BOTTOM_CONTROLS, sanitized.portraitBottomControls.joinToString(",") { it.id })
            .putBoolean(KEY_PORTRAIT_TOP_ENABLED, sanitized.isPortraitTopEnabled)
            .putBoolean(KEY_PORTRAIT_BOTTOM_ENABLED, sanitized.isPortraitBottomEnabled)
            .apply()
    }

    fun saveSettings(context: Context, state: UiState) {
        val sanitized = state.playerLayoutConfig.sanitized()
        getPrefs(context).edit()
            .putString(KEY_THEME_MODE, state.themeMode.name)
            .putFloat(KEY_APP_SCALE, state.appScale)
            .putFloat(KEY_GLASS_BLUR_TRANSPARENCY, state.glassBlurTransparency)
            .putBoolean(KEY_FORCE_SIDE_BY_SIDE, state.forceSideBySide)
            .putString(KEY_LANGUAGE, state.language.name)
            .putInt(KEY_FRAME_STEP_AMOUNT, state.frameStepAmount)
            .putBoolean(KEY_COPY_TIMESTAMP, state.copyTimestampOnDoubleTap)
            .putString(KEY_HW_ACCEL_MODE, state.hwAccelMode.name)
            .putString(KEY_GESTURE_SENSITIVITY_MODE, state.gestureSensitivityMode.name)
            .putBoolean(KEY_SHOW_PLAYER_NOTIFICATIONS, state.showPlayerNotifications)
            .putString(KEY_SEEKBAR_STYLE, state.seekbarStyle.name)
            .putString(KEY_TOP_RIGHT_CONTROLS, sanitized.topRightControls.joinToString(",") { it.id })
            .putString(KEY_BOTTOM_LEFT_CONTROLS, sanitized.bottomLeftControls.joinToString(",") { it.id })
            .putString(KEY_BOTTOM_RIGHT_CONTROLS, sanitized.bottomRightControls.joinToString(",") { it.id })
            .putBoolean(KEY_LANDSCAPE_TR_ENABLED, sanitized.isLandscapeTopRightEnabled)
            .putBoolean(KEY_LANDSCAPE_BL_ENABLED, sanitized.isLandscapeBottomLeftEnabled)
            .putBoolean(KEY_LANDSCAPE_BR_ENABLED, sanitized.isLandscapeBottomRightEnabled)
            .putString(KEY_PORTRAIT_TOP_CONTROLS, sanitized.portraitTopControls.joinToString(",") { it.id })
            .putString(KEY_PORTRAIT_BOTTOM_CONTROLS, sanitized.portraitBottomControls.joinToString(",") { it.id })
            .putBoolean(KEY_PORTRAIT_TOP_ENABLED, sanitized.isPortraitTopEnabled)
            .putBoolean(KEY_PORTRAIT_BOTTOM_ENABLED, sanitized.isPortraitBottomEnabled)
            .putFloat(KEY_EDITOR_TEXT_SIZE, state.editorTextSizeSp)
            .putFloat(KEY_TIME_TEXT_SIZE, state.timeTextSizeSp)
            .putBoolean(KEY_PASTE_CONVERTER, state.showPasteBoxConverter)
            .putBoolean(KEY_PASTE_CLEANER, state.showPasteBoxCleaner)
            .putBoolean(KEY_PASTE_SHIFTER, state.showPasteBoxShifter)
            .putBoolean(KEY_PASTE_EDITOR, state.showPasteBoxEditor)
            .putBoolean(KEY_PASTE_TEXT_EDITOR, state.showPasteBoxTextEditor)
            .putFloat(KEY_DIALOGUE_EDIT_OFFSET_Y, state.dialogueEditOffsetY)
            .putFloat(KEY_COLOR_PICKER_OFFSET_Y, state.colorPickerOffsetY)
            .putBoolean(KEY_SHOW_UPLOAD_SECTION_TEXT_EDITOR, state.showUploadSectionTextEditor)
            .putFloat(KEY_TEXT_EDITOR_FONT_SIZE, state.textEditorFontSizeSp)
            .putBoolean(KEY_SHOW_LINE_NUMBERS_TEXT_EDITOR, state.showLineNumbersTextEditor)
            .putBoolean(KEY_TEXT_EDITOR_WORD_WRAP, state.textEditorWordWrap)
            .putBoolean(KEY_SHOW_BOTTOM_TOOLBAR_TEXT_EDITOR, state.showBottomToolbarTextEditor)
            .putBoolean(KEY_SHOW_SEARCH_PANEL_TEXT_EDITOR, state.showSearchPanelTextEditor)
            .putString(KEY_TEXT_EDITOR_NAV_STEP, state.textEditorNavStep.name)

            // Appearance
            .putString(KEY_APP_THEME, state.appTheme.name)
            .putBoolean(KEY_AMOLED_BLACK_MODE, state.amoledBlackMode)
            .putBoolean(KEY_USE_SYSTEM_FONT, state.useSystemFont)
            .putBoolean(KEY_HAPTIC_FEEDBACK, state.hapticFeedback)

            // File Browser
            .putBoolean(KEY_SHOW_FULL_NAME, state.showFullName)
            .putBoolean(KEY_SHOW_NEW_VIDEO_LABEL, state.showNewVideoLabel)
            .putInt(KEY_NEW_VIDEO_DAYS_THRESHOLD, state.newVideoDaysThreshold)
            .putBoolean(KEY_SHOW_FOLDER_UNPLAYED_BADGE, state.showFolderUnplayedBadge)
            .putBoolean(KEY_AUTO_SCROLL_TO_LAST_PLAYED, state.autoScrollToLastPlayed)
            .putBoolean(KEY_TREE_PATH_COMPRESSION, state.treePathCompression)
            .putBoolean(KEY_DUAL_PANE_VIEW, state.dualPaneView)
            .putInt(KEY_WATCHED_THRESHOLD_PERCENT, state.watchedThresholdPercent)

            // Thumbnails
            .putBoolean(KEY_SHOW_VIDEO_THUMBNAILS, state.showVideoThumbnails)
            .putString(KEY_THUMBNAIL_STRATEGY, state.thumbnailStrategy.name)
            .putString(KEY_THUMBNAIL_QUALITY, state.thumbnailQuality.name)
            .putInt(KEY_THUMBNAIL_FALLBACK_SECOND, state.thumbnailFallbackSecond.coerceIn(1, 10))
            .putBoolean(KEY_TAP_THUMBNAIL_TO_SELECT, state.tapThumbnailToSelect)
            .putBoolean(KEY_SHOW_NETWORK_THUMBNAILS, state.showNetworkThumbnails)

            // Navigation
            .putBoolean(KEY_SHOW_HOME_TAB, state.showHomeTab)
            .putBoolean(KEY_SHOW_MUSIC_TAB, state.showMusicTab)
            .putBoolean(KEY_SHOW_RECENTS_TAB, state.showRecentsTab)
            .putBoolean(KEY_SHOW_PLAYLISTS_TAB, state.showPlaylistsTab)

            // Animations
            .putString(KEY_CONTROLS_ANIMATION_STYLE, state.controlsAnimationStyle.name)
            .putString(KEY_VIDEO_OPENING_ANIMATION, state.videoOpeningAnimation.name)
            .putString(KEY_SCREEN_NAVIGATION_STYLE, state.screenNavigationStyle.name)
            .putString(KEY_TAB_NAVIGATION_STYLE, state.tabNavigationStyle.name)
            .putFloat(KEY_ANIMATION_SPEED, state.animationSpeed)

            // Audio & Sound
            .putString(KEY_PREFERRED_AUDIO_LANGUAGES, state.preferredAudioLanguages)
            .putBoolean(KEY_APPLY_AUDIO_LANG_SELECTED_ONLY, state.applyAudioLanguageToSelectedContentOnly)
            .putStringSet(KEY_SELECTED_AUDIO_FOLDERS, state.selectedAudioFolders)
            .putStringSet(KEY_SELECTED_AUDIO_VIDEOS, state.selectedAudioVideos)
            .putBoolean(KEY_ENABLE_AUDIO_PITCH_CORRECTION, state.enableAudioPitchCorrection)
            .putBoolean(KEY_VOLUME_NORMALIZATION, state.volumeNormalization)
            .putBoolean(KEY_BACKGROUND_PLAYBACK, state.backgroundPlayback)
            .putString(KEY_AUDIO_CHANNEL_MODE, state.audioChannelMode.name)
            .putInt(KEY_VOLUME_BOOST_CAP, state.volumeBoostCap)
            .putBoolean(KEY_REMEMBER_SELECTED_AUDIO_TRACK, state.rememberSelectedAudioTrack)
            .apply()
        PlayerSettings.save(context, state.playerSettings)
    }

    fun saveRememberedAudioTrack(context: Context, videoPath: String, trackId: Int, trackTitle: String, trackLang: String) {
        if (videoPath.isBlank()) return
        val key = KEY_REM_TRACK_PREFIX + videoPath.hashCode()
        getPrefs(context).edit()
            .putString(key, "$trackId|$trackTitle|$trackLang")
            .apply()
    }

    fun getRememberedAudioTrack(context: Context, videoPath: String): Triple<Int, String, String>? {
        if (videoPath.isBlank()) return null
        val key = KEY_REM_TRACK_PREFIX + videoPath.hashCode()
        val value = getPrefs(context).getString(key, null) ?: return null
        val parts = value.split("|")
        val id = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val title = parts.getOrNull(1) ?: ""
        val lang = parts.getOrNull(2) ?: ""
        return Triple(id, title, lang)
    }

    fun loadSortField(context: Context, tabId: String = "home"): SortField {
        val defaultField = SortField.TITLE.name
        val key = if (tabId == "home") KEY_SORT_FIELD else "${KEY_SORT_FIELD}_$tabId"
        val prefs = getPrefs(context)
        var str = prefs.getString(key, null)
        if (str == null && tabId.contains("_")) {
            val baseTab = tabId.substringBefore("_")
            val baseKey = if (baseTab == "home") KEY_SORT_FIELD else "${KEY_SORT_FIELD}_$baseTab"
            str = prefs.getString(baseKey, null)
        }
        val finalStr = str ?: defaultField
        return try {
            SortField.valueOf(finalStr)
        } catch (_: Exception) {
            SortField.TITLE
        }
    }

    fun saveSortField(context: Context, field: SortField, tabId: String = "home") {
        val key = if (tabId == "home") KEY_SORT_FIELD else "${KEY_SORT_FIELD}_$tabId"
        getPrefs(context).edit().putString(key, field.name).apply()
    }

    fun loadSortDirection(context: Context, tabId: String = "home"): SortDirection {
        val defaultDir = if (tabId.contains("recents")) SortDirection.DESCENDING.name else SortDirection.ASCENDING.name
        val key = if (tabId == "home") KEY_SORT_DIRECTION else "${KEY_SORT_DIRECTION}_$tabId"
        val prefs = getPrefs(context)
        var str = prefs.getString(key, null)
        if (str == null && tabId.contains("_")) {
            val baseTab = tabId.substringBefore("_")
            val baseKey = if (baseTab == "home") KEY_SORT_DIRECTION else "${KEY_SORT_DIRECTION}_$baseTab"
            str = prefs.getString(baseKey, null)
        }
        val finalStr = str ?: defaultDir
        return try {
            SortDirection.valueOf(finalStr)
        } catch (_: Exception) {
            if (tabId.contains("recents")) SortDirection.DESCENDING else SortDirection.ASCENDING
        }
    }

    fun saveSortDirection(context: Context, direction: SortDirection, tabId: String = "home") {
        val key = if (tabId == "home") KEY_SORT_DIRECTION else "${KEY_SORT_DIRECTION}_$tabId"
        getPrefs(context).edit().putString(key, direction.name).apply()
    }

    fun loadViewMode(context: Context, tabId: String = "home"): ViewMode {
        val defaultMode = if (tabId.contains("recents")) ViewMode.LIBRARY.name else ViewMode.FOLDER.name
        val key = if (tabId == "home") KEY_VIEW_MODE else "${KEY_VIEW_MODE}_$tabId"
        val prefs = getPrefs(context)
        var str = prefs.getString(key, null)
        if (str == null && tabId.contains("_")) {
            val baseTab = tabId.substringBefore("_")
            val baseKey = if (baseTab == "home") KEY_VIEW_MODE else "${KEY_VIEW_MODE}_$baseTab"
            str = prefs.getString(baseKey, null)
        }
        val finalStr = str ?: defaultMode
        return try {
            ViewMode.valueOf(finalStr)
        } catch (_: Exception) {
            if (tabId.contains("recents")) ViewMode.LIBRARY else ViewMode.FOLDER
        }
    }

    fun saveViewMode(context: Context, mode: ViewMode, tabId: String = "home") {
        val key = if (tabId == "home") KEY_VIEW_MODE else "${KEY_VIEW_MODE}_$tabId"
        getPrefs(context).edit().putString(key, mode.name).apply()
    }

    fun loadLayoutMode(context: Context, tabId: String = "home"): LayoutMode {
        val defaultLayout = LayoutMode.LIST.name
        val key = if (tabId == "home") KEY_LAYOUT_MODE else "${KEY_LAYOUT_MODE}_$tabId"
        val prefs = getPrefs(context)
        var str = prefs.getString(key, null)
        if (str == null && tabId.contains("_")) {
            val baseTab = tabId.substringBefore("_")
            val baseKey = if (baseTab == "home") KEY_LAYOUT_MODE else "${KEY_LAYOUT_MODE}_$baseTab"
            str = prefs.getString(baseKey, null)
        }
        val finalStr = str ?: defaultLayout
        return try {
            LayoutMode.valueOf(finalStr)
        } catch (_: Exception) {
            LayoutMode.LIST
        }
    }

    fun saveLayoutMode(context: Context, mode: LayoutMode, tabId: String = "home") {
        val key = if (tabId == "home") KEY_LAYOUT_MODE else "${KEY_LAYOUT_MODE}_$tabId"
        getPrefs(context).edit().putString(key, mode.name).apply()
    }

    fun loadVisibleFields(context: Context, tabId: String = "home"): VisibleFields {
        val prefs = getPrefs(context)
        val prefix = if (tabId == "home") "" else "${tabId}_"
        val hasSpecific = prefs.contains("${prefix}$KEY_FIELD_SHOW_THUMBNAILS")
        val effectivePrefix = if (!hasSpecific && tabId.contains("_")) {
            val baseTab = tabId.substringBefore("_")
            if (baseTab == "home") "" else "${baseTab}_"
        } else {
            prefix
        }
        val isFolderScope = tabId.endsWith("_folders") || (tabId in listOf("home", "music") && !tabId.endsWith("_content"))
        val defaultDuration = if (isFolderScope) false else (tabId.contains("recents") || tabId.contains("music") || tabId.endsWith("_content"))
        val defaultVideoCount = isFolderScope || tabId == "home" || tabId == "music"
        return VisibleFields(
            showThumbnails = prefs.getBoolean("${effectivePrefix}$KEY_FIELD_SHOW_THUMBNAILS", true),
            showExtension = prefs.getBoolean("${effectivePrefix}$KEY_FIELD_SHOW_EXTENSION", true),
            showDuration = prefs.getBoolean("${effectivePrefix}$KEY_FIELD_SHOW_DURATION", defaultDuration),
            showSubtitleIndicator = prefs.getBoolean("${effectivePrefix}$KEY_FIELD_SHOW_SUBTITLE_INDICATOR", true),
            showFullName = prefs.getBoolean("${effectivePrefix}$KEY_FIELD_SHOW_FULL_NAME", false),
            showSize = prefs.getBoolean("${effectivePrefix}$KEY_FIELD_SHOW_SIZE", true),
            showResolution = prefs.getBoolean("${effectivePrefix}$KEY_FIELD_SHOW_RESOLUTION", true),
            showFramerate = prefs.getBoolean("${effectivePrefix}$KEY_FIELD_SHOW_FRAMERATE", false),
            showDate = prefs.getBoolean("${effectivePrefix}$KEY_FIELD_SHOW_DATE", tabId.contains("recents")),
            showProgressBar = prefs.getBoolean("${effectivePrefix}$KEY_FIELD_SHOW_PROGRESS_BAR", true),
            showPath = prefs.getBoolean("${effectivePrefix}$KEY_FIELD_SHOW_PATH", false),
            showVideoCount = prefs.getBoolean("${effectivePrefix}$KEY_FIELD_SHOW_VIDEO_COUNT", defaultVideoCount),
            showNewBadge = prefs.getBoolean("${effectivePrefix}$KEY_FIELD_SHOW_NEW_BADGE", false),
            showAudioType = prefs.getBoolean("${effectivePrefix}$KEY_FIELD_SHOW_AUDIO_TYPE", true)
        )
    }

    fun saveVisibleFields(context: Context, fields: VisibleFields, tabId: String = "home") {
        val prefix = if (tabId == "home") "" else "${tabId}_"
        getPrefs(context).edit()
            .putBoolean("${prefix}$KEY_FIELD_SHOW_THUMBNAILS", fields.showThumbnails)
            .putBoolean("${prefix}$KEY_FIELD_SHOW_EXTENSION", fields.showExtension)
            .putBoolean("${prefix}$KEY_FIELD_SHOW_DURATION", fields.showDuration)
            .putBoolean("${prefix}$KEY_FIELD_SHOW_SUBTITLE_INDICATOR", fields.showSubtitleIndicator)
            .putBoolean("${prefix}$KEY_FIELD_SHOW_FULL_NAME", fields.showFullName)
            .putBoolean("${prefix}$KEY_FIELD_SHOW_SIZE", fields.showSize)
            .putBoolean("${prefix}$KEY_FIELD_SHOW_RESOLUTION", fields.showResolution)
            .putBoolean("${prefix}$KEY_FIELD_SHOW_FRAMERATE", fields.showFramerate)
            .putBoolean("${prefix}$KEY_FIELD_SHOW_DATE", fields.showDate)
            .putBoolean("${prefix}$KEY_FIELD_SHOW_PROGRESS_BAR", fields.showProgressBar)
            .putBoolean("${prefix}$KEY_FIELD_SHOW_PATH", fields.showPath)
            .putBoolean("${prefix}$KEY_FIELD_SHOW_VIDEO_COUNT", fields.showVideoCount)
            .putBoolean("${prefix}$KEY_FIELD_SHOW_NEW_BADGE", fields.showNewBadge)
            .putBoolean("${prefix}$KEY_FIELD_SHOW_AUDIO_TYPE", fields.showAudioType)
            .apply()
    }

    fun loadCustomFolders(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_CUSTOM_FOLDERS, emptySet()) ?: emptySet()
    }

    fun saveCustomFolders(context: Context, folders: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_CUSTOM_FOLDERS, folders).apply()
    }
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val _uiState = MutableStateFlow(SettingsPreferencesManager.loadSettings(appContext))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _hasMediaPermission = MutableStateFlow(com.example.ui.components.hasVideoPermission(application))
    val hasMediaPermission: StateFlow<Boolean> = _hasMediaPermission.asStateFlow()

    private val _mediaRefreshTrigger = MutableStateFlow(0L)
    val mediaRefreshTrigger: StateFlow<Long> = _mediaRefreshTrigger.asStateFlow()

    fun setHasMediaPermission(hasPermission: Boolean) {
        _hasMediaPermission.value = hasPermission
    }

    fun triggerLibraryRefresh() {
        _mediaRefreshTrigger.value = System.currentTimeMillis()
    }

    fun setThemeMode(mode: ThemeMode) {
        val newState = _uiState.value.copy(themeMode = mode)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setAppScale(scale: Float) {
        val newState = _uiState.value.copy(appScale = scale.coerceIn(1f, 100f))
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setGlassBlurTransparency(transparency: Float) {
        val newState = _uiState.value.copy(glassBlurTransparency = transparency.coerceIn(10f, 100f))
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setForceSideBySide(enabled: Boolean) {
        val newState = _uiState.value.copy(forceSideBySide = enabled)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setLanguage(lang: AppLanguage) {
        val newState = _uiState.value.copy(language = lang)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setFrameStepAmount(amount: Int) {
        val newState = _uiState.value.copy(frameStepAmount = amount.coerceIn(1, 30))
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setCopyTimestampOnDoubleTap(enabled: Boolean) {
        val newState = _uiState.value.copy(copyTimestampOnDoubleTap = enabled)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setHwAccelMode(mode: HwAccelMode) {
        val newState = _uiState.value.copy(hwAccelMode = mode)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setGestureSensitivityMode(mode: GestureSensitivityMode) {
        val newState = _uiState.value.copy(gestureSensitivityMode = mode)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setSeekbarStyle(style: SeekbarStyle) {
        val newState = _uiState.value.copy(seekbarStyle = style)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setEditorTextSizeSp(sizeSp: Float) {
        val newState = _uiState.value.copy(editorTextSizeSp = sizeSp.coerceIn(10f, 28f))
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setTimeTextSizeSp(sizeSp: Float) {
        val newState = _uiState.value.copy(timeTextSizeSp = sizeSp.coerceIn(10f, 24f))
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setShowPasteBoxConverter(show: Boolean) {
        val newState = _uiState.value.copy(showPasteBoxConverter = show)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setShowPasteBoxCleaner(show: Boolean) {
        val newState = _uiState.value.copy(showPasteBoxCleaner = show)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setShowPasteBoxShifter(show: Boolean) {
        val newState = _uiState.value.copy(showPasteBoxShifter = show)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setShowPasteBoxEditor(show: Boolean) {
        val newState = _uiState.value.copy(showPasteBoxEditor = show)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setShowPasteBoxTextEditor(show: Boolean) {
        val newState = _uiState.value.copy(showPasteBoxTextEditor = show)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setDialogueEditOffsetY(offsetDp: Float) {
        val newState = _uiState.value.copy(dialogueEditOffsetY = offsetDp.coerceIn(-300f, 300f))
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setColorPickerOffsetY(offsetDp: Float) {
        val newState = _uiState.value.copy(colorPickerOffsetY = offsetDp.coerceIn(-300f, 300f))
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setShowUploadSectionTextEditor(show: Boolean) {
        val newState = _uiState.value.copy(showUploadSectionTextEditor = show)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setTextEditorFontSizeSp(sizeSp: Float) {
        val newState = _uiState.value.copy(textEditorFontSizeSp = sizeSp.coerceIn(10f, 28f))
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setShowLineNumbersTextEditor(show: Boolean) {
        val newState = _uiState.value.copy(showLineNumbersTextEditor = show)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setTextEditorWordWrap(wrap: Boolean) {
        val newState = _uiState.value.copy(textEditorWordWrap = wrap)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setShowBottomToolbarTextEditor(show: Boolean) {
        val newState = _uiState.value.copy(showBottomToolbarTextEditor = show)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setShowSearchPanelTextEditor(show: Boolean) {
        val newState = _uiState.value.copy(showSearchPanelTextEditor = show)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setTextEditorNavStep(step: TextEditorNavStep) {
        val newState = _uiState.value.copy(textEditorNavStep = step)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setShowPlayerNotifications(show: Boolean) {
        val newState = _uiState.value.copy(showPlayerNotifications = show)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setPlayerLayoutConfig(config: PlayerLayoutConfig) {
        val sanitized = config.sanitized()
        val newState = _uiState.value.copy(playerLayoutConfig = sanitized)
        _uiState.value = newState
        SettingsPreferencesManager.savePlayerLayoutConfig(appContext, sanitized)
    }

    fun resetPlayerLayoutConfig() {
        setPlayerLayoutConfig(PlayerLayoutConfig.default)
    }

    fun applyImportedState(newState: UiState) {
        _uiState.value = newState
        triggerLibraryRefresh()
    }

    fun reloadSettings() {
        val reloaded = SettingsPreferencesManager.loadSettings(appContext)
        _uiState.value = reloaded
        triggerLibraryRefresh()
    }

    fun setPlayerSettings(settings: PlayerSettings) {
        val newState = _uiState.value.copy(playerSettings = settings)
        _uiState.value = newState
        PlayerSettings.save(appContext, settings)
    }

    /**
     * Updates the in-memory [UiState.playerSettings] ONLY - no disk write. Callers (e.g. the
     * video player's subtitle style editor) that already persist [PlayerSettings] themselves,
     * on their own debounced/IO-dispatched schedule, must still call this on every change so
     * this ViewModel's cached copy never goes stale. Without it, [uiState] keeps serving the
     * OLD snapshot that was current when the player screen was entered; leaving the player and
     * re-entering (or any other screen that reads/re-saves [UiState.playerSettings] via
     * [setPlayerSettings] in the meantime) would then silently revert the subtitle changes,
     * because [setPlayerSettings] always writes whatever it is currently holding - stale or not
     * - back over the newer values already on disk. Cheap enough (a plain StateFlow assignment,
     * no I/O) to call on every keystroke, slider tick, or gesture frame.
     */
    fun syncPlayerSettingsInMemory(settings: PlayerSettings) {
        _uiState.value = _uiState.value.copy(playerSettings = settings)
    }
    // Audio & Sound Preferences Updaters
    fun setPreferredAudioLanguages(languages: String) {
        val newState = _uiState.value.copy(preferredAudioLanguages = languages)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setApplyAudioLanguageToSelectedContentOnly(enabled: Boolean) {
        val newState = _uiState.value.copy(applyAudioLanguageToSelectedContentOnly = enabled)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setSelectedAudioFolders(folders: Set<String>) {
        val newState = _uiState.value.copy(selectedAudioFolders = folders)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setSelectedAudioVideos(videos: Set<String>) {
        val newState = _uiState.value.copy(selectedAudioVideos = videos)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setEnableAudioPitchCorrection(enabled: Boolean) {
        val newState = _uiState.value.copy(enableAudioPitchCorrection = enabled)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setVolumeNormalization(enabled: Boolean) {
        val newState = _uiState.value.copy(volumeNormalization = enabled)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setBackgroundPlayback(enabled: Boolean) {
        val newState = _uiState.value.copy(backgroundPlayback = enabled)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setAudioChannelMode(mode: AudioChannelMode) {
        val newState = _uiState.value.copy(audioChannelMode = mode)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setVolumeBoostCap(cap: Int) {
        val newState = _uiState.value.copy(volumeBoostCap = cap.coerceIn(100, 200))
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setRememberSelectedAudioTrack(enabled: Boolean) {
        val newState = _uiState.value.copy(rememberSelectedAudioTrack = enabled)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    // Appearance Setters
    fun setAppTheme(theme: AppThemeVariant) {
        val newState = _uiState.value.copy(appTheme = theme)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setAmoledBlackMode(enabled: Boolean) {
        val newState = _uiState.value.copy(amoledBlackMode = enabled)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setUseSystemFont(enabled: Boolean) {
        val newState = _uiState.value.copy(useSystemFont = enabled)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setSystemFontEnabled(enabled: Boolean) = setUseSystemFont(enabled)

    fun setHapticFeedback(enabled: Boolean) {
        com.example.util.AppHaptics.isEnabled = enabled
        val newState = _uiState.value.copy(hapticFeedback = enabled)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    // File Browser Setters
    fun setShowFullName(show: Boolean) {
        val newState = _uiState.value.copy(showFullName = show)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setShowNewVideoLabel(show: Boolean) {
        val newState = _uiState.value.copy(showNewVideoLabel = show)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setNewVideoDaysThreshold(days: Int) {
        val newState = _uiState.value.copy(newVideoDaysThreshold = days.coerceIn(1, 30))
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setShowFolderUnplayedBadge(show: Boolean) {
        val newState = _uiState.value.copy(showFolderUnplayedBadge = show)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setAutoScrollToLastPlayed(enabled: Boolean) {
        val newState = _uiState.value.copy(autoScrollToLastPlayed = enabled)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setTreePathCompression(enabled: Boolean) {
        val newState = _uiState.value.copy(treePathCompression = enabled)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setDualPaneView(enabled: Boolean) {
        val newState = _uiState.value.copy(dualPaneView = enabled)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setWatchedThresholdPercent(percent: Int) {
        val newState = _uiState.value.copy(watchedThresholdPercent = percent.coerceIn(50, 100))
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    // Thumbnails Setters
    fun setShowVideoThumbnails(show: Boolean) {
        val newState = _uiState.value.copy(showVideoThumbnails = show)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setThumbnailStrategy(strategy: ThumbnailStrategy) {
        val newState = _uiState.value.copy(thumbnailStrategy = strategy)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setThumbnailQuality(quality: ThumbnailQuality) {
        val newState = _uiState.value.copy(thumbnailQuality = quality)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setThumbnailFallbackSecond(second: Int) {
        val newState = _uiState.value.copy(thumbnailFallbackSecond = second.coerceIn(1, 10))
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setTapThumbnailToSelect(enabled: Boolean) {
        val newState = _uiState.value.copy(tapThumbnailToSelect = enabled)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setShowNetworkThumbnails(show: Boolean) {
        val newState = _uiState.value.copy(showNetworkThumbnails = show)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    // Navigation Setters
    fun setShowHomeTab(show: Boolean) {
        val newState = _uiState.value.copy(showHomeTab = show)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setShowMusicTab(show: Boolean) {
        val newState = _uiState.value.copy(showMusicTab = show)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setShowRecentsTab(show: Boolean) {
        val newState = _uiState.value.copy(showRecentsTab = show)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setShowPlaylistsTab(show: Boolean) {
        val newState = _uiState.value.copy(showPlaylistsTab = show)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    // Animations Setters
    fun setControlsAnimationStyle(style: ControlsAnimationStyle) {
        val newState = _uiState.value.copy(controlsAnimationStyle = style)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setVideoOpeningAnimation(anim: VideoOpeningAnimation) {
        val newState = _uiState.value.copy(videoOpeningAnimation = anim)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setScreenNavigationStyle(style: ScreenNavigationStyle) {
        val newState = _uiState.value.copy(screenNavigationStyle = style)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setTabNavigationStyle(style: TabNavigationStyle) {
        val newState = _uiState.value.copy(tabNavigationStyle = style)
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    fun setAnimationSpeed(speed: Float) {
        val newState = _uiState.value.copy(animationSpeed = speed.coerceIn(0.25f, 2.0f))
        _uiState.value = newState
        SettingsPreferencesManager.saveSettings(appContext, newState)
    }

    // Active player & playback state preserved across rotation
    var activeVideoItem: com.example.ui.screens.VideoItem? = null
    var activeVideoPlaylist: List<com.example.ui.screens.VideoItem> = emptyList()
    var savedPlaybackPositionMs: Long = 0L
    var isPlayerPlaying: Boolean = true
    var isPlayerInLandscape: Boolean = false
    val currentScreenState = MutableStateFlow(com.example.Screen.HOME)

    // Which screen to return to when the player is closed. Defaults to HOME, but is set
    // to CHANNEL_DETAIL when a video was opened from inside a channel's video list, so
    // Back correctly returns to that channel instead of always jumping to Home.
    private var screenBeforePlayer: com.example.Screen = com.example.Screen.HOME

    // Channel currently shown on the Channel Detail screen (set by openChannel()).
    var activeChannelResult: com.example.util.OnlineChannelResult? = null
    var isChannelAudioMode: Boolean = false

    // Shared playlist currently shown on the Shared Playlist screen (set by openSharedPlaylist()).
    var activeSharedPlaylist: com.example.util.OnlinePlaylistDetail? = null

    // Folder navigation stack preserved across playing videos and screen switches
    val folderNavigationStack = mutableListOf<com.example.ui.screens.VideoFolder>()

    fun startPlaying(video: com.example.ui.screens.VideoItem, playlist: List<com.example.ui.screens.VideoItem>) {
        activeVideoItem = video
        activeVideoPlaylist = playlist
        screenBeforePlayer = currentScreenState.value.takeIf { it != com.example.Screen.PLAYER }
            ?: com.example.Screen.HOME
        currentScreenState.value = com.example.Screen.PLAYER
    }

    fun openChannel(channel: com.example.util.OnlineChannelResult, isAudioMode: Boolean = false) {
        activeChannelResult = channel
        isChannelAudioMode = isAudioMode
        currentScreenState.value = com.example.Screen.CHANNEL_DETAIL
    }

    fun exitChannel() {
        currentScreenState.value = com.example.Screen.HOME
    }

    fun openSharedPlaylist(playlist: com.example.util.OnlinePlaylistDetail) {
        activeSharedPlaylist = playlist
        currentScreenState.value = com.example.Screen.SHARED_PLAYLIST
    }

    fun exitSharedPlaylist() {
        currentScreenState.value = com.example.Screen.HOME
    }

    fun playNext(videos: List<com.example.ui.screens.VideoItem>) {
        if (videos.isEmpty()) return
        val current = activeVideoPlaylist.toMutableList()
        val activeIdx = activeVideoItem?.let { cur -> current.indexOfFirst { it.path == cur.path } } ?: -1
        val newVideos = videos.filter { v -> !current.any { it.path == v.path } }
        if (activeIdx >= 0 && activeIdx < current.size) {
            current.addAll(activeIdx + 1, newVideos)
        } else {
            current.addAll(newVideos)
        }
        activeVideoPlaylist = current
    }

    fun playLast(videos: List<com.example.ui.screens.VideoItem>) {
        if (videos.isEmpty()) return
        val current = activeVideoPlaylist.toMutableList()
        val newVideos = videos.filter { v -> !current.any { it.path == v.path } }
        current.addAll(newVideos)
        activeVideoPlaylist = current
    }

    fun exitPlayer() {
        // Return to wherever the player was actually opened from (Home or Channel Detail)
        // instead of always forcing Home, so channel/search state survives Search Results
        // -> Channel Detail -> Video Player -> Back -> Channel Detail.
        currentScreenState.value = screenBeforePlayer
        com.example.util.AppStorageManager.performBackgroundCleanup(appContext)
    }
}

object AppStrings {
    fun getTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH, AppLanguage.HINGLISH, AppLanguage.HINDI -> "Lumora"
    }

    fun getCard1Title(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Subtitle Format Converter"
        AppLanguage.HINGLISH -> "Subtitle Format Converter"
        AppLanguage.HINDI -> "सबटाइटल्स फॉर्मेट कनवर्टर"
    }

    fun getCard1Desc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Upload or Paste Subtitle Content"
        AppLanguage.HINGLISH -> "Subtitle Content Upload ya Paste karein"
        AppLanguage.HINDI -> "सबटाइटल्स सामग्री अपलोड या पेस्ट करें"
    }

    fun getCard2Title(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Subtitle Shifter"
        AppLanguage.HINGLISH -> "Subtitle Shifter"
        AppLanguage.HINDI -> "सबटाइटल्स शिफ्टर"
    }

    fun getCard2Desc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Shift entire subtitle cues or specify custom ranges forward/backward to fix timing delays."
        AppLanguage.HINGLISH -> "Timing delays fix karne ke liye poore subtitle cues ya custom ranges ko aage/peeche shift karein."
        AppLanguage.HINDI -> "टाइमिँग देरी को ठीक करने के लिए पूरे सबटाइटल क्यूज़ या कस्टम रेंज को आगे/पीछे शिफ्ट करें।"
    }

    fun getCard3Title(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Subtitle Cleaner"
        AppLanguage.HINGLISH -> "Subtitle Cleaner"
        AppLanguage.HINDI -> "सबटाइटल्स क्लीनर"
    }

    fun getCard3Desc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Scrub and clean watermarks, brackets, SDH descriptions, or merge identical lines instantly."
        AppLanguage.HINGLISH -> "Watermarks, brackets, SDH descriptions scrub karein aur identical lines ko instantly merge karein."
        AppLanguage.HINDI -> "वाटरमार्क, ब्रैकेट, SDH विवरण साफ करें और समान पंक्तियों को तुरंत मर्ज करें।"
    }

    fun getCard4Title(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Subtitle Edit"
        AppLanguage.HINGLISH -> "Subtitle Edit"
        AppLanguage.HINDI -> "सबटाइटल्स एडिट"
    }

    fun getCard4Desc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Import ASS/SRT/SUB files, edit subtitle cues, timestamps & styling with live text preview."
        AppLanguage.HINGLISH -> "ASS/SRT/SUB files import karein, subtitle cues, timestamps aur styling ko edit karein."
        AppLanguage.HINDI -> "ASS/SRT/SUB फ़ाइलें आयात करें, सबटाइटल क्यूज़, समय और स्टाइलिंग को संपादित करें।"
    }

    fun getCard5Title(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Text Editor"
        AppLanguage.HINGLISH -> "Text Editor"
        AppLanguage.HINDI -> "टेक्स्ट एडिटर"
    }

    fun getCard5Desc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Raw text editing and formatting tool."
        AppLanguage.HINGLISH -> "Raw text editing aur formatting tool."
        AppLanguage.HINDI -> "रॉ टेक्स्ट एडिटिंग और फॉर्मेटिंग टूल।"
    }

    fun getPoweredBy(lang: AppLanguage): String = "POWERED BY GOD KARTIK"

    fun getSettingsDashboard(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Settings Dashboard"
        AppLanguage.HINGLISH -> "Settings Dashboard"
        AppLanguage.HINDI -> "सेटिंग्स डैशबोर्ड"
    }

    fun getDone(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Done"
        AppLanguage.HINGLISH -> "Done"
        AppLanguage.HINDI -> "संपन्न"
    }

    fun getTabGeneral(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "General"
        AppLanguage.HINGLISH -> "General"
        AppLanguage.HINDI -> "सामान्य"
    }

    fun getTabPlayer(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Player"
        AppLanguage.HINGLISH -> "Player"
        AppLanguage.HINDI -> "प्लेयर"
    }

    fun getTabAudio(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Audio"
        AppLanguage.HINGLISH -> "Audio"
        AppLanguage.HINDI -> "ऑडियो"
    }

    fun getTabDecoder(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Decoder"
        AppLanguage.HINGLISH -> "Decoder"
        AppLanguage.HINDI -> "डिकोडर"
    }

    fun getTabSubtitles(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Subtitles"
        AppLanguage.HINGLISH -> "Subtitles"
        AppLanguage.HINDI -> "सबटाइटल"
    }

    fun getTabAbout(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "About"
        AppLanguage.HINGLISH -> "About"
        AppLanguage.HINDI -> "के बारे में"
    }

    fun getAboutCategory(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "About & Device Info"
        AppLanguage.HINGLISH -> "About & Device Info"
        AppLanguage.HINDI -> "के बारे में और डिवाइस जानकारी"
    }

    fun getTabPlayerLayout(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Layout"
        AppLanguage.HINGLISH -> "Layout"
        AppLanguage.HINDI -> "लेआउट"
    }

    fun getTabGestures(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Gesture"
        AppLanguage.HINGLISH -> "Gesture"
        AppLanguage.HINDI -> "जेस्चर"
    }

    fun getPlayerLayoutTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Player Layout"
        AppLanguage.HINGLISH -> "Player Layout"
        AppLanguage.HINDI -> "प्लेयर लेआउट"
    }

    fun getSeekbarStyleTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Seekbar Style"
        AppLanguage.HINGLISH -> "Seekbar Style"
        AppLanguage.HINDI -> "सीकबार स्टाइल"
    }

    fun getGeneralCategory(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "General & UI"
        AppLanguage.HINGLISH -> "General & UI"
        AppLanguage.HINDI -> "सामान्य और UI"
    }

    fun getAppearanceCategory(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Appearance & Themes"
        AppLanguage.HINGLISH -> "Appearance & Themes"
        AppLanguage.HINDI -> "दिखावट और थीम्स"
    }

    fun getFoldersCategory(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Folders Management"
        AppLanguage.HINGLISH -> "Folders Management"
        AppLanguage.HINDI -> "फ़ोल्डर प्रबंधन"
    }

    fun getPlayerCategory(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Player & Controls"
        AppLanguage.HINGLISH -> "Player & Controls"
        AppLanguage.HINDI -> "प्लेयर और कंट्रोल्स"
    }

    fun getAudioCategory(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Audio & Sound"
        AppLanguage.HINGLISH -> "Audio & Sound"
        AppLanguage.HINDI -> "ऑडियो और ध्वनि"
    }

    fun getDecoderCategory(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Decoder & Hardware"
        AppLanguage.HINGLISH -> "Decoder & Hardware"
        AppLanguage.HINDI -> "डिकोडर और हार्डवेयर"
    }

    fun getSubtitlesCategory(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Subtitles & Custom Fonts"
        AppLanguage.HINGLISH -> "Subtitles & Custom Fonts"
        AppLanguage.HINDI -> "सबटाइटल और कस्टम फ़ॉन्ट"
    }

    fun getSectionAppearance(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "INTERFACE APPEARANCE"
        AppLanguage.HINGLISH -> "INTERFACE APPEARANCE"
        AppLanguage.HINDI -> "इंटरफ़ेस थीम"
    }

    fun getLightMode(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Light Mode"
        AppLanguage.HINGLISH -> "Light Mode"
        AppLanguage.HINDI -> "लाइट मोड"
    }

    fun getDarkMode(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Dark Mode"
        AppLanguage.HINGLISH -> "Dark Mode"
        AppLanguage.HINDI -> "डार्क मोड"
    }

    fun getSectionLanguage(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "INTERFACE LANGUAGE"
        AppLanguage.HINGLISH -> "INTERFACE LANGUAGE"
        AppLanguage.HINDI -> "इंटरफ़ेस भाषा"
    }

    fun getSectionScaling(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "DISPLAY ZOOM & SCALE"
        AppLanguage.HINGLISH -> "DISPLAY ZOOM & SCALE"
        AppLanguage.HINDI -> "स्क्रीन ज़ूम और स्केल"
    }

    fun getAppScaleZoom(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Application Scale / Zoom:"
        AppLanguage.HINGLISH -> "Application Scale / Zoom:"
        AppLanguage.HINDI -> "एप्लिकेशन स्केल / ज़ूम:"
    }

    fun getSectionGlassBlur(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "CONTROLS UI GLASS BLUR & TRANSLUCENCY"
        AppLanguage.HINGLISH -> "CONTROLS UI GLASS BLUR & TRANSLUCENCY"
        AppLanguage.HINDI -> "कंट्रोल्स UI ग्लास ब्लर और ट्रांसपेरेंसी"
    }

    fun getGlassBlurTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "UI Glass Blur & Opacity:"
        AppLanguage.HINGLISH -> "UI Glass Blur & Opacity:"
        AppLanguage.HINDI -> "UI ग्लास ब्लर और पारदर्शिता:"
    }

    fun getGlassBlurSubtitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Controls player buttons, menus, popups & panels"
        AppLanguage.HINGLISH -> "Player buttons, menus, popups & panels control karta hai"
        AppLanguage.HINDI -> "प्लेयर बटन, मेनू, पॉपअप और पैनल को नियंत्रित करता है"
    }

    fun getGlassBlurLow(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "10% (High Transparent)"
        AppLanguage.HINGLISH -> "10% (High Transparent)"
        AppLanguage.HINDI -> "10% (अत्यधिक पारदर्शी)"
    }

    fun getGlassBlurHigh(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "100% (Solid Frost)"
        AppLanguage.HINGLISH -> "100% (Solid Frost)"
        AppLanguage.HINDI -> "100% (ठोस फ्रॉस्ट)"
    }

    fun getSectionPlayback(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "PLAYBACK SETTINGS"
        AppLanguage.HINGLISH -> "PLAYBACK SETTINGS"
        AppLanguage.HINDI -> "प्लेबैक सेटिंग्स"
    }

    fun getCopyTimestamp(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Copy Timestamp on Double Tap"
        AppLanguage.HINGLISH -> "Double Tap par Timestamp Copy karein"
        AppLanguage.HINDI -> "डबल टैप पर टाइमस्टैम्प कॉपी करें"
    }

    fun getCopyTimestampDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Quickly copy current playback time to clipboard"
        AppLanguage.HINGLISH -> "Current playback time ko clipboard par jaldi copy karein"
        AppLanguage.HINDI -> "वर्तमान प्लेबैक समय को क्लिपबोर्ड पर तुरंत कॉपी करें"
    }

    fun getForceSideBySide(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Side-by-Side Dual View"
        AppLanguage.HINGLISH -> "Side-by-Side Dual View"
        AppLanguage.HINDI -> "साइड-बाय-साइड ड्यूल व्यू"
    }

    fun getForceSideBySideDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Force split view layout on landscape screens"
        AppLanguage.HINGLISH -> "Landscape screen par split view layout force karein"
        AppLanguage.HINDI -> "लैंडस्केप स्क्रीन पर स्प्लिट व्यू लेआउट फ़ोर्स करें"
    }

    fun getSectionFastSeek(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "FAST SEEK STEP"
        AppLanguage.HINGLISH -> "FAST SEEK STEP"
        AppLanguage.HINDI -> "फ़ास्ट सीक स्टेप"
    }

    fun getSectionGestureSensitivity(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "GESTURE SENSITIVITY & SCALING"
        AppLanguage.HINGLISH -> "GESTURE SENSITIVITY & SCALING"
        AppLanguage.HINDI -> "जेस्चर संवेदनशीलता और स्केलिंग"
    }

    fun getGestureLinearTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Linear (1:1 Uniform)"
        AppLanguage.HINGLISH -> "Linear (1:1 Uniform)"
        AppLanguage.HINDI -> "लीनियर (1:1 एकसमान)"
    }

    fun getGestureLinearDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Direct 1:1 proportional swipe response for uniform volume & brightness steps"
        AppLanguage.HINGLISH -> "Uniform volume & brightness steps ke liye direct 1:1 swipe response"
        AppLanguage.HINDI -> "एकसमान वॉल्यूम और ब्राइटनेस स्टेप्स के लिए सीधा 1:1 स्वाइप रिस्पॉन्स"
    }

    fun getGestureExponentialTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Exponential (Perceptual Curve)"
        AppLanguage.HINGLISH -> "Exponential (Perceptual Curve)"
        AppLanguage.HINDI -> "एक्सपोनेंशियल (परसेप्चुअल कर्व)"
    }

    fun getGestureExponentialDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Fine-grain precision at lower levels with smooth dynamic curve to prevent lag & jumps"
        AppLanguage.HINGLISH -> "Lag aur jumps rokne ke liye smooth dynamic curve ke saath precise control"
        AppLanguage.HINDI -> "लैग और जंप्स को रोकने के लिए सहज गतिशील वक्र के साथ निचले स्तरों पर सूक्ष्म सटीकता"
    }

    fun getRecommended(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "RECOMMENDED"
        AppLanguage.HINGLISH -> "RECOMMENDED"
        AppLanguage.HINDI -> "अनुशंसित"
    }

    // Audio & Sound Strings
    fun getSectionPreferredLanguages(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "PREFERRED AUDIO LANGUAGES"
        AppLanguage.HINGLISH -> "PREFERRED AUDIO LANGUAGES"
        AppLanguage.HINDI -> "प्राथमिकता वाले ऑडियो भाषाएँ"
    }

    fun getPreferredLanguagesTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Preferred languages"
        AppLanguage.HINGLISH -> "Preferred languages"
        AppLanguage.HINDI -> "प्राथमिकता वाले भाषाएँ"
    }

    fun getPreferredLanguagesDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Comma-separated codes (e.g. hin,eng,jpn). The first code has the highest priority and falls back sequentially."
        AppLanguage.HINGLISH -> "Comma-separated codes (jaise hin,eng,jpn). Pehla code highest priority rakhta hai."
        AppLanguage.HINDI -> "अल्पविराम से अलग किए गए कोड (जैसे hin,eng,jpn)। पहले कोड की सर्वोच्च प्राथमिकता है।"
    }

    fun getApplyToSelectedContentTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Apply to selected folder/videos only"
        AppLanguage.HINGLISH -> "Selected folder/videos par hi apply karein"
        AppLanguage.HINDI -> "केवल चयनित फ़ोल्डर/वीडियो पर लागू करें"
    }

    fun getApplyToSelectedContentDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Restrict auto-selection rule to chosen directories instead of applying globally to all videos"
        AppLanguage.HINGLISH -> "Saare videos ke bajaye chune huye folders ya videos par hi language auto-select rule lagayein"
        AppLanguage.HINDI -> "सभी वीडियो के बजाय केवल चुने गए फ़ोल्डरों या वीडियो पर भाषा नियम लागू करें"
    }

    fun getSelectedFoldersTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Scoped Content Targets"
        AppLanguage.HINGLISH -> "Scoped Content Targets"
        AppLanguage.HINDI -> "लक्षित फ़ोल्डर व वीडियो"
    }

    fun getSectionSoundProcessing(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "PLAYBACK & SOUND PROCESSING"
        AppLanguage.HINGLISH -> "PLAYBACK & SOUND PROCESSING"
        AppLanguage.HINDI -> "ध्वनि प्रसंस्करण और प्लेबैक"
    }

    fun getAudioPitchCorrectionTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Enable audio pitch correction"
        AppLanguage.HINGLISH -> "Audio pitch correction enable karein"
        AppLanguage.HINDI -> "ऑडियो पिच सुधार सक्षम करें"
    }

    fun getAudioPitchCorrectionDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Preserves natural human vocal pitch when playing at high (0.25x - 4.0x) or slow speeds"
        AppLanguage.HINGLISH -> "Speed change karne par voice pitch ko naturally maintain rakhta hai"
        AppLanguage.HINDI -> "गति बदलने पर स्वाभाविक आवाज़ की पिच को बनाए रखता है"
    }

    fun getVolumeNormalizationTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Volume normalization"
        AppLanguage.HINGLISH -> "Volume normalization"
        AppLanguage.HINDI -> "वॉल्यूम सामान्यीकरण"
    }

    fun getVolumeNormalizationDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Maintains consistent perceived loudness between different media and audio tracks dynamically"
        AppLanguage.HINGLISH -> "Alag-alag videos aur tracks ke beech sound level ko balance aur consistent rakhta hai"
        AppLanguage.HINDI -> "विभिन्न मीडिया और ऑडियो ट्रैकों के बीच लगातार समान ध्वनि स्तर बनाए रखता है"
    }

    fun getRememberAudioTrackTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Remember selected audio track"
        AppLanguage.HINGLISH -> "Remember selected audio track"
        AppLanguage.HINDI -> "चयनित ऑडियो ट्रैक याद रखें"
    }

    fun getRememberAudioTrackDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Saves manual audio track changes per video so your selection is restored next time"
        AppLanguage.HINGLISH -> "Manual audio track selection ko save karta hai taki agli baar wahi track chale"
        AppLanguage.HINDI -> "प्रत्येक वीडियो के लिए मैन्युअल ऑडियो ट्रैक को सहेजता है ताकि अगली बार वही चले"
    }

    fun getSectionBackgroundPlayback(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "BACKGROUND AUDIO"
        AppLanguage.HINGLISH -> "BACKGROUND AUDIO"
        AppLanguage.HINDI -> "पृष्ठभूमि ऑडियो"
    }

    fun getBackgroundPlaybackTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Background playback"
        AppLanguage.HINGLISH -> "Background playback"
        AppLanguage.HINDI -> "बैकग्राउंड प्लेबैक"
    }

    fun getBackgroundPlaybackDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Continue playing audio when app is minimized or the screen is turned off"
        AppLanguage.HINGLISH -> "App minimize hone par ya screen band hone par bhi audio play hota rahega"
        AppLanguage.HINDI -> "ऐप छोटा होने पर या स्क्रीन बंद होने पर भी ऑडियो चलता रहेगा"
    }

    fun getSectionAudioChannels(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "AUDIO CHANNELS & ACOUSTICS"
        AppLanguage.HINGLISH -> "AUDIO CHANNELS & ACOUSTICS"
        AppLanguage.HINDI -> "ऑडियो चैनल और एकाउस्टिक्स"
    }

    fun getAudioChannelsTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Audio channels"
        AppLanguage.HINGLISH -> "Audio channels"
        AppLanguage.HINDI -> "ऑडियो चैनल"
    }

    fun getSectionVolumeBoost(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "VOLUME AMPLIFICATION & BOOST"
        AppLanguage.HINGLISH -> "VOLUME AMPLIFICATION & BOOST"
        AppLanguage.HINDI -> "वॉल्यूम प्रवर्धन और बूस्ट"
    }

    fun getVolumeBoostTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Volume boost cap"
        AppLanguage.HINGLISH -> "Volume boost cap"
        AppLanguage.HINDI -> "वॉल्यूम बूस्ट सीमा"
    }

    fun getVolumeBoostDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Maximum software amplification limit when swiping beyond standard volume"
        AppLanguage.HINGLISH -> "Standard volume se aage swipe karne par maximum boost limit"
        AppLanguage.HINDI -> "मानक वॉल्यूम से आगे स्वाइप करने पर अधिकतम सॉफ़्टवेयर प्रवर्धन सीमा"
    }

    fun getSectionHwAccel(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "HARDWARE ACCELERATION"
        AppLanguage.HINGLISH -> "HARDWARE ACCELERATION"
        AppLanguage.HINDI -> "हार्डवेयर एक्सेलेरेशन"
    }

    fun getHwForceTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Force MediaCodec"
        AppLanguage.HINGLISH -> "Force MediaCodec"
        AppLanguage.HINDI -> "फ़ोर्स मीडियाकोडेक"
    }

    fun getHwForceDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Direct MediaCodec HW acceleration for max frame sync & smoothness"
        AppLanguage.HINGLISH -> "Max frame sync & smoothness ke liye direct MediaCodec HW acceleration"
        AppLanguage.HINDI -> "अधिकतम फ़्रेम सिंक और सहजता के लिए सीधा MediaCodec HW एक्सेलेरेशन"
    }

    fun getHwPreferTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Prefer (Auto Copy)"
        AppLanguage.HINGLISH -> "Prefer (Auto Copy)"
        AppLanguage.HINDI -> "प्राथमिकता (ऑटो कॉपी)"
    }

    fun getHwPreferDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Hardware decoding with auto copyback buffer fallback"
        AppLanguage.HINGLISH -> "Auto copyback buffer fallback ke saath hardware decoding"
        AppLanguage.HINDI -> "ऑटो कॉपीबैक बफ़र फ़ॉलबैक के साथ हार्डवेयर डिकोडिंग"
    }

    fun getHwDisableTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Disable (Software CPU)"
        AppLanguage.HINGLISH -> "Disable (Software CPU)"
        AppLanguage.HINDI -> "अक्षम (सॉफ्टवेयर CPU)"
    }

    fun getHwDisableDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Software CPU decoding to troubleshoot persistent GPU/rendering glitches"
        AppLanguage.HINGLISH -> "GPU glitches troubleshoot karne ke liye software CPU decoding"
        AppLanguage.HINDI -> "GPU/रेंडरिंग समस्याओं को हल करने के लिए सॉफ़्टवेयर CPU डिकोडिंग"
    }

    fun getSectionRenderEngine(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "RENDER ENGINE"
        AppLanguage.HINGLISH -> "RENDER ENGINE"
        AppLanguage.HINDI -> "रेंडर इंजन"
    }

    fun getRenderLibassTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "libass Subtitle Renderer"
        AppLanguage.HINGLISH -> "libass Subtitle Renderer"
        AppLanguage.HINDI -> "libass सबटाइटल रेंडरर"
    }

    fun getRenderLibassDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Pixel-perfect Aegisub styles, transforms & karaoke effects"
        AppLanguage.HINGLISH -> "Pixel-perfect Aegisub styles, transforms & karaoke effects"
        AppLanguage.HINDI -> "पिक्सेल-सटीक Aegisub शैलियाँ, रूपांतरण और कराओके प्रभाव"
    }

    fun getRenderEmbeddedFontsTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Embedded Font Extraction"
        AppLanguage.HINGLISH -> "Embedded Font Extraction"
        AppLanguage.HINDI -> "एंबेडेड फ़ॉन्ट निष्कर्षण"
    }

    fun getRenderEmbeddedFontsDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Loads custom fonts packed directly in MKV / MP4 containers"
        AppLanguage.HINGLISH -> "MKV / MP4 containers mein packed custom fonts load karta hai"
        AppLanguage.HINDI -> "MKV / MP4 कंटेनर में सीधे पैक किए गए कस्टम फ़ॉन्ट लोड करता है"
    }

    fun getRenderHwEqTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Video Setting"
        AppLanguage.HINGLISH -> "Video Setting"
        AppLanguage.HINDI -> "वीडियो सेटिंग"
    }

    fun getRenderHwEqDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Live filters, color presets and video adjustments"
        AppLanguage.HINGLISH -> "Live filters, color presets aur video adjustments"
        AppLanguage.HINDI -> "लाइव फिल्टर, रंग प्रीसेट और वीडियो समायोजन"
    }

    fun getActiveBadge(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Active"
        AppLanguage.HINGLISH -> "Active"
        AppLanguage.HINDI -> "सक्रिय"
    }

    fun getSectionCustomFont(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "CUSTOM FONT UPLOADER"
        AppLanguage.HINGLISH -> "CUSTOM FONT UPLOADER"
        AppLanguage.HINDI -> "कस्टम फ़ॉन्ट अपलोडर"
    }

    fun getUploadSubtitleFontsTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Upload Subtitle Fonts"
        AppLanguage.HINGLISH -> "Subtitle Fonts Upload karein"
        AppLanguage.HINDI -> "सबटाइटल फ़ॉन्ट अपलोड करें"
    }

    fun getUploadSubtitleFontsDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Upload .ttf, .otf, .ttc, .woff font files for advanced ASS/SSA subtitles when fonts are not embedded in the video."
        AppLanguage.HINGLISH -> "Jab fonts video mein embedded na hon toh advanced ASS/SSA subtitles ke liye font files upload karein."
        AppLanguage.HINDI -> "जब फ़ॉन्ट वीडियो में एम्बेडेड न हों तो उन्नत ASS/SSA सबटाइटल के लिए .ttf, .otf, .ttc, .woff फ़ॉन्ट फ़ाइलें अपलोड करें।"
    }

    fun getUploadFontButton(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Upload Font Files (.ttf / .otf / .ttc)"
        AppLanguage.HINGLISH -> "Font Files Upload karein (.ttf / .otf / .ttc)"
        AppLanguage.HINDI -> "फ़ॉन्ट फ़ाइलें अपलोड करें (.ttf / .otf / .ttc)"
    }

    fun getActiveInstalledFonts(lang: AppLanguage, count: Int): String = when (lang) {
        AppLanguage.ENGLISH -> "Active Installed Fonts ($count)"
        AppLanguage.HINGLISH -> "Active Installed Fonts ($count)"
        AppLanguage.HINDI -> "सक्रिय इंस्टॉल किए गए फ़ॉन्ट ($count)"
    }

    fun getSectionSupportedFormats(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "SUPPORTED SUBTITLE FORMATS (13 TYPES)"
        AppLanguage.HINGLISH -> "SUPPORTED SUBTITLE FORMATS (13 TYPES)"
        AppLanguage.HINDI -> "समर्थित सबटाइटल प्रारूप (13 प्रकार)"
    }

    fun getSupportedFormatsTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "All 13 Major Subtitle Formats Supported:"
        AppLanguage.HINGLISH -> "All 13 Major Subtitle Formats Supported:"
        AppLanguage.HINDI -> "सभी 13 प्रमुख सबटाइटल प्रारूप समर्थित हैं:"
    }

    fun getAssColorRenderTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Full ASS Color & Style Rendering"
        AppLanguage.HINGLISH -> "Full ASS Color & Style Rendering"
        AppLanguage.HINDI -> "पूर्ण ASS रंग और शैली रेंडरिंग"
    }

    fun getAssColorRenderDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Custom margins, positioning, colors & karaoke preserved"
        AppLanguage.HINGLISH -> "Custom margins, positioning, colors & karaoke preserved"
        AppLanguage.HINDI -> "कस्टम मार्जिन, स्थिति, रंग और कराओके सुरक्षित"
    }

    fun getStdSubtitleEngineTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Standard Subtitle High-Contrast Engine"
        AppLanguage.HINGLISH -> "Standard Subtitle High-Contrast Engine"
        AppLanguage.HINDI -> "मानक सबटाइटल उच्च-कंट्रास्ट इंजन"
    }

    fun getStdSubtitleEngineDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Crystal-clear rendering for SRT, VTT, SUB with crisp outlines"
        AppLanguage.HINGLISH -> "Crisp outlines ke saath SRT, VTT, SUB ke liye crystal-clear rendering"
        AppLanguage.HINDI -> "स्पष्ट आउटलाइन के साथ SRT, VTT, SUB के लिए क्रिस्टल-क्लियर रेंडरिंग"
    }

    fun getHwAccelDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Select MediaCodec mode to resolve frame drops, video stuttering, or subtitle sync issues."
        AppLanguage.HINGLISH -> "Frame drops, video stuttering ya subtitle sync issues ko resolve karne ke liye MediaCodec mode chunein."
        AppLanguage.HINDI -> "फ़्रेम ड्रॉप, वीडियो हकलाना या सबटाइटल सिंक समस्याओं को हल करने के लिए मीडियाकोडेक मोड चुनें।"
    }

    fun getSectionPlayerNotification(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "PLAYER ACTION NOTIFICATIONS"
        AppLanguage.HINGLISH -> "PLAYER ACTION NOTIFICATIONS"
        AppLanguage.HINDI -> "प्लेयर एक्शन सूचनाएं"
    }

    fun getPlayerNotificationTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Player Action Notifications"
        AppLanguage.HINGLISH -> "Player Action Notifications"
        AppLanguage.HINDI -> "प्लेयर एक्शन सूचनाएं"
    }

    fun getPlayerNotificationDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Show sleek system glass notifications when changing tracks, looping, or seeking"
        AppLanguage.HINGLISH -> "Track badalne, loop karne ya seek karte waqt sleek system glass notification dikhayein"
        AppLanguage.HINDI -> "ट्रैक बदलने, लूप करने या सीक करते समय चिकनी सिस्टम ग्लास सूचनाएं दिखाएं"
    }

    fun getTabAdvanced(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Advanced"
        AppLanguage.HINGLISH -> "Advanced"
        AppLanguage.HINDI -> "उन्नत"
    }

    fun getAdvancedCategory(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Advanced Settings"
        AppLanguage.HINGLISH -> "Advanced Settings"
        AppLanguage.HINDI -> "उन्नत सेटिंग्स"
    }

    fun getExportSettingsTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Export Settings"
        AppLanguage.HINGLISH -> "Export Settings"
        AppLanguage.HINDI -> "Export Settings"
    }

    fun getExportSettingsDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Export settings to an XML file"
        AppLanguage.HINGLISH -> "Export settings to an XML file"
        AppLanguage.HINDI -> "सेटिंग्स को एक XML फ़ाइल में निर्यात करें"
    }

    fun getImportSettingsTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Import Settings"
        AppLanguage.HINGLISH -> "Import Settings"
        AppLanguage.HINDI -> "Import Settings"
    }

    fun getImportSettingsDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Import settings from an XML file"
        AppLanguage.HINGLISH -> "Import settings from an XML file"
        AppLanguage.HINDI -> "एक XML फ़ाइल से सेटिंग्स आयात करें"
    }

    fun getImportConfirmMessage(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "Do you want to apply these settings?"
        AppLanguage.HINGLISH -> "Kya aap is setting ko laagu karna chahte hain?"
        AppLanguage.HINDI -> "क्या आप इस सेटिंग को लागू करना चाहते हैं?"
    }

    fun getImportConfirmDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "All current Lumora player, appearance, audio, and subtitle configurations will be replaced."
        AppLanguage.HINGLISH -> "Current Lumora player, appearance, audio, aur subtitle configurations replace ho jayenge."
        AppLanguage.HINDI -> "सभी वर्तमान Lumora प्लेयर, दिखावट, ऑडियो और सबटाइटल कॉन्फ़िगरेशन बदल दिए जाएंगे।"
    }

    fun getCancelButton(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "CANCEL"
        AppLanguage.HINGLISH -> "CANCEL"
        AppLanguage.HINDI -> "रद्द करें"
    }

    fun getConfirmButton(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> "CONFIRM"
        AppLanguage.HINGLISH -> "CONFIRM"
        AppLanguage.HINDI -> "पुष्टि करें"
    }
}
