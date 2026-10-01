package com.example.ui.state

import com.example.R

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Orientation modes for customizing the player controls layout.
 */
enum class LayoutOrientationMode(
    val title: String,
    val shortTitle: String,
    val icon: ImageVector
) {
    PORTRAIT(
        title = "Portrait Mode",
        shortTitle = "Portrait",
        icon = Icons.Outlined.PhoneAndroid
    ),
    LANDSCAPE(
        title = "Landscape Mode",
        shortTitle = "Landscape",
        icon = Icons.Outlined.Tv
    )
}

/**
 * Configurable player control areas categorized by orientation.
 */
enum class ControlArea(
    val title: String,
    val shortTitle: String,
    val orientation: LayoutOrientationMode,
    val description: String
) {
    // Landscape Areas (3 sections)
    TOP_RIGHT(
        title = "Top Right Controls",
        shortTitle = "Top Right",
        orientation = LayoutOrientationMode.LANDSCAPE,
        description = "Header tools in the top right during landscape playback"
    ),
    BOTTOM_LEFT(
        title = "Bottom Left Controls",
        shortTitle = "Bottom Left",
        orientation = LayoutOrientationMode.LANDSCAPE,
        description = "Left floating action tools above the progress bar in landscape"
    ),
    BOTTOM_RIGHT(
        title = "Bottom Right Controls",
        shortTitle = "Bottom Right",
        orientation = LayoutOrientationMode.LANDSCAPE,
        description = "Right floating action tools above the progress bar in landscape"
    ),

    // Portrait Areas (2 sections: Top and Down/Bottom)
    PORTRAIT_TOP(
        title = "Portrait Top Bar",
        shortTitle = "Top Tools",
        orientation = LayoutOrientationMode.PORTRAIT,
        description = "Header buttons in the upper bar next to title in portrait mode"
    ),
    PORTRAIT_BOTTOM(
        title = "Portrait Bottom Bar",
        shortTitle = "Bottom Tools",
        orientation = LayoutOrientationMode.PORTRAIT,
        description = "Action tool buttons directly above the seekbar in portrait mode"
    )
}

/**
 * Identifiers and metadata for every player control item.
 */
enum class PlayerControlId(
    val id: String,
    val displayName: String,
    val description: String,
    val icon: ImageVector,
    val defaultArea: ControlArea?,
    val isPermanent: Boolean = false,
    val lumoraDrawableRes: Int? = null
) {
    // TOP RIGHT CONTROLS
    CAST(
        id = "cast",
        displayName = "Cast",
        description = "Screen Cast / Wireless Display",
        icon = Icons.Outlined.Cast,
        lumoraDrawableRes = R.drawable.lumora_screencast,
        defaultArea = ControlArea.TOP_RIGHT
    ),
    CHAPTER(
        id = "chapter",
        displayName = "Chapters",
        description = "Video Chapters & Jump Landmarks",
        icon = Icons.Outlined.BookmarkBorder,
        lumoraDrawableRes = R.drawable.lumora_bookmark,
        defaultArea = ControlArea.TOP_RIGHT
    ),
    DECODER(
        id = "decoder",
        displayName = "Decoder HW+/SW",
        description = "MediaCodec HW+, HW, and Software Decoder Switcher",
        icon = Icons.Outlined.Memory,
        lumoraDrawableRes = R.drawable.lumora_cpu,
        defaultArea = ControlArea.TOP_RIGHT
    ),
    AUDIO_TRACK(
        id = "audio_track",
        displayName = "Audio Track",
        description = "Multi-language Audio & Stream Selector",
        icon = Icons.Outlined.MusicNote,
        lumoraDrawableRes = R.drawable.lumora_song,
        defaultArea = ControlArea.TOP_RIGHT
    ),
    SUBTITLES(
        id = "subtitles",
        displayName = "Subtitles",
        description = "Subtitle Track & File Browser",
        icon = Icons.Outlined.Subtitles,
        lumoraDrawableRes = R.drawable.lumora_subtitles,
        defaultArea = ControlArea.TOP_RIGHT
    ),
    PLAYER_LAYOUT(
        id = "player_layout",
        displayName = "Player Layout",
        description = "Seekbar Styles & Control Layout Customizer",
        icon = Icons.Outlined.DashboardCustomize,
        lumoraDrawableRes = R.drawable.lumora_widget_5,
        defaultArea = ControlArea.TOP_RIGHT
    ),
    MORE_OPTIONS(
        id = "more_options",
        displayName = "More Options",
        description = "3-Dots Quick Playback Menu (Fixed at end of Top Right)",
        icon = Icons.Outlined.MoreVert,
        lumoraDrawableRes = R.drawable.lumora_menu_dots,
        defaultArea = ControlArea.TOP_RIGHT,
        isPermanent = true
    ),

    // BOTTOM LEFT CONTROLS
    AUDIO_ONLY(
        id = "audio_only",
        displayName = "Audio-Only Mode",
        description = "Background / Headphones Audio Playback",
        icon = Icons.Outlined.Headphones,
        lumoraDrawableRes = R.drawable.lumora_headphones_round,
        defaultArea = ControlArea.BOTTOM_LEFT
    ),
    LOCK_SCREEN(
        id = "lock_screen",
        displayName = "Lock Controls",
        description = "Lock Player Touch Screen Against Accidental Taps",
        icon = Icons.Outlined.LockOpen,
        lumoraDrawableRes = R.drawable.lumora_lock_unlocked,
        defaultArea = ControlArea.BOTTOM_LEFT
    ),
    SCREEN_ROTATION(
        id = "screen_rotation",
        displayName = "Screen Rotation",
        description = "Toggle Landscape / Portrait Orientation",
        icon = Icons.Outlined.ScreenRotation,
        lumoraDrawableRes = R.drawable.lumora_smartphone_rotate_orientation,
        defaultArea = ControlArea.BOTTOM_LEFT
    ),
    PLAYBACK_SPEED(
        id = "playback_speed",
        displayName = "Playback Speed",
        description = "Speed Presets (0.25x to 4.0x) & Pitch Control",
        icon = Icons.Outlined.Speed,
        lumoraDrawableRes = R.drawable.lumora_speed,
        defaultArea = ControlArea.BOTTOM_LEFT
    ),
    REPEAT_MODE(
        id = "repeat_mode",
        displayName = "Repeat Mode",
        description = "Repeat Off, Repeat One, Repeat All",
        icon = Icons.Outlined.Repeat,
        lumoraDrawableRes = R.drawable.lumora_repeat,
        defaultArea = ControlArea.BOTTOM_LEFT
    ),
    SHUFFLE(
        id = "shuffle",
        displayName = "Shuffle",
        description = "Toggle Playlist Shuffle Sequence",
        icon = Icons.Outlined.Shuffle,
        lumoraDrawableRes = R.drawable.lumora_shuffle,
        defaultArea = ControlArea.BOTTOM_LEFT
    ),
    AB_LOOP(
        id = "ab_loop",
        displayName = "A-B Loop",
        description = "A-B Point Infinite Loop Interval",
        icon = Icons.Outlined.AllInclusive,
        lumoraDrawableRes = R.drawable.lumora_ab_loop,
        defaultArea = ControlArea.BOTTOM_LEFT
    ),

    // BOTTOM RIGHT CONTROLS
    FRAME_NAVIGATION(
        id = "frame_navigation",
        displayName = "Screenshot & Frames",
        description = "Frame-by-Frame Seek & Instant Screenshot",
        icon = Icons.Outlined.CameraAlt,
        lumoraDrawableRes = R.drawable.lumora_screenshot,
        defaultArea = ControlArea.BOTTOM_RIGHT
    ),
    VIDEO_ZOOM(
        id = "video_zoom",
        displayName = "Video Zoom",
        description = "Pan & Zoom Video Scale (50% to 500%)",
        icon = Icons.Outlined.ZoomIn,
        lumoraDrawableRes = R.drawable.lumora_magnifer_zoom_in,
        defaultArea = ControlArea.BOTTOM_RIGHT
    ),
    PIP(
        id = "pip",
        displayName = "Picture in Picture",
        description = "Floating Pop-up Window",
        icon = Icons.Outlined.PictureInPictureAlt,
        lumoraDrawableRes = R.drawable.lumora_pip,
        defaultArea = ControlArea.BOTTOM_RIGHT
    ),
    ASPECT_RATIO(
        id = "aspect_ratio",
        displayName = "Display Mode",
        description = "Fit, Fill, Zoom In, Full Screen",
        icon = Icons.Outlined.AspectRatio,
        lumoraDrawableRes = R.drawable.lumora_minimize_square_2,
        defaultArea = ControlArea.BOTTOM_RIGHT
    ),
    FLIP_VERTICAL(
        id = "flip_vertical",
        displayName = "Flip Vertical",
        description = "Flip the video vertically (upside down)",
        icon = Icons.Outlined.SwapVert,
        lumoraDrawableRes = R.drawable.lumora_flip_vertical,
        defaultArea = ControlArea.BOTTOM_RIGHT
    ),
    MIRROR_RIGHT(
        id = "mirror_right",
        displayName = "Mirror Right",
        description = "Mirror the video horizontally so the left side moves to the right",
        icon = Icons.Outlined.SwapHoriz,
        lumoraDrawableRes = R.drawable.lumora_mirror_right,
        defaultArea = ControlArea.BOTTOM_RIGHT
    ),
    MIRROR_LEFT(
        id = "mirror_left",
        displayName = "Mirror Left",
        description = "Mirror the video horizontally so the right side moves to the left",
        icon = Icons.Outlined.SwapHoriz,
        lumoraDrawableRes = R.drawable.lumora_mirror_left,
        defaultArea = ControlArea.BOTTOM_RIGHT
    ),

    // OPTIONAL / AVAILABLE POOL CONTROLS
    VIDEO_EQ(
        id = "video_eq",
        displayName = "Video Setting",
        description = "Filters, Color Presets & Video Adjustments",
        icon = Icons.Outlined.Tune,
        lumoraDrawableRes = R.drawable.lumora_video_setting,
        defaultArea = null
    ),
    PITCH_CORRECTION(
        id = "pitch_correction",
        displayName = "Pitch Correction",
        description = "Maintain natural vocal tone at fast/slow playback speeds",
        icon = Icons.Outlined.GraphicEq,
        lumoraDrawableRes = R.drawable.lumora_translation,
        defaultArea = null
    ),
    PLAYLIST(
        id = "playlist",
        displayName = "Playlist & Episodes",
        description = "Episode Queue & Playlist Manager",
        icon = Icons.Outlined.VideoLibrary,
        lumoraDrawableRes = R.drawable.lumora_library,
        defaultArea = null
    );

    companion object {
        fun fromId(id: String): PlayerControlId? = values().firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

/**
 * Holds the exact ordered lists and visibility toggles of active controls for both Portrait and Landscape.
 */
data class PlayerLayoutConfig(
    // Landscape Controls (3 Areas)
    val topRightControls: List<PlayerControlId> = defaultTopRightControls,
    val bottomLeftControls: List<PlayerControlId> = defaultBottomLeftControls,
    val bottomRightControls: List<PlayerControlId> = defaultBottomRightControls,
    val isLandscapeTopRightEnabled: Boolean = true,
    val isLandscapeBottomLeftEnabled: Boolean = true,
    val isLandscapeBottomRightEnabled: Boolean = true,

    // Portrait Controls (2 Areas)
    val portraitTopControls: List<PlayerControlId> = defaultPortraitTopControls,
    val portraitBottomControls: List<PlayerControlId> = defaultPortraitBottomControls,
    val isPortraitTopEnabled: Boolean = true,
    val isPortraitBottomEnabled: Boolean = true
) {
    companion object {
        val defaultTopRightControls = listOf(
            PlayerControlId.CAST,
            PlayerControlId.CHAPTER,
            PlayerControlId.DECODER,
            PlayerControlId.AUDIO_TRACK,
            PlayerControlId.SUBTITLES,
            PlayerControlId.PLAYER_LAYOUT,
            PlayerControlId.MORE_OPTIONS
        )

        val defaultBottomLeftControls = listOf(
            PlayerControlId.AUDIO_ONLY,
            PlayerControlId.LOCK_SCREEN,
            PlayerControlId.SCREEN_ROTATION,
            PlayerControlId.PLAYBACK_SPEED,
            PlayerControlId.REPEAT_MODE,
            PlayerControlId.SHUFFLE,
            PlayerControlId.AB_LOOP
        )

        val defaultBottomRightControls = listOf(
            PlayerControlId.FRAME_NAVIGATION,
            PlayerControlId.VIDEO_ZOOM,
            PlayerControlId.PIP,
            PlayerControlId.ASPECT_RATIO,
            PlayerControlId.FLIP_VERTICAL,
            PlayerControlId.MIRROR_RIGHT,
            PlayerControlId.MIRROR_LEFT
        )

        val defaultPortraitTopControls = listOf(
            PlayerControlId.CAST,
            PlayerControlId.SUBTITLES,
            PlayerControlId.AUDIO_TRACK,
            PlayerControlId.DECODER,
            PlayerControlId.PLAYER_LAYOUT,
            PlayerControlId.MORE_OPTIONS
        )

        val defaultPortraitBottomControls = listOf(
            PlayerControlId.AUDIO_ONLY,
            PlayerControlId.LOCK_SCREEN,
            PlayerControlId.SCREEN_ROTATION,
            PlayerControlId.PLAYBACK_SPEED,
            PlayerControlId.REPEAT_MODE,
            PlayerControlId.FRAME_NAVIGATION,
            PlayerControlId.VIDEO_ZOOM,
            PlayerControlId.PIP,
            PlayerControlId.ASPECT_RATIO,
            PlayerControlId.FLIP_VERTICAL,
            PlayerControlId.MIRROR_RIGHT,
            PlayerControlId.MIRROR_LEFT
        )

        val default = PlayerLayoutConfig(
            topRightControls = defaultTopRightControls,
            bottomLeftControls = defaultBottomLeftControls,
            bottomRightControls = defaultBottomRightControls,
            isLandscapeTopRightEnabled = true,
            isLandscapeBottomLeftEnabled = true,
            isLandscapeBottomRightEnabled = true,
            portraitTopControls = defaultPortraitTopControls,
            portraitBottomControls = defaultPortraitBottomControls,
            isPortraitTopEnabled = true,
            isPortraitBottomEnabled = true
        )
    }

    /**
     * Sanitizes the configuration guaranteeing:
     * 1. No duplicates within each orientation group.
     * 2. MORE_OPTIONS is ALWAYS present in TOP_RIGHT / PORTRAIT_TOP and is strictly the LAST item.
     * 3. MORE_OPTIONS is prevented from appearing in bottom sections.
     */
    fun sanitized(): PlayerLayoutConfig {
        // Landscape sanitization
        val usedLandscape = mutableSetOf<PlayerControlId>()
        val cleanTopRight = topRightControls
            .filter { it != PlayerControlId.MORE_OPTIONS && usedLandscape.add(it) }
            .toMutableList()
        val cleanBottomLeft = bottomLeftControls
            .filter { it != PlayerControlId.MORE_OPTIONS && usedLandscape.add(it) }
        val cleanBottomRight = bottomRightControls
            .filter { it != PlayerControlId.MORE_OPTIONS && usedLandscape.add(it) }
        cleanTopRight.add(PlayerControlId.MORE_OPTIONS)

        // Portrait sanitization
        val usedPortrait = mutableSetOf<PlayerControlId>()
        val cleanPortraitTop = portraitTopControls
            .filter { it != PlayerControlId.MORE_OPTIONS && usedPortrait.add(it) }
            .toMutableList()
        val cleanPortraitBottom = portraitBottomControls
            .filter { it != PlayerControlId.MORE_OPTIONS && usedPortrait.add(it) }
        cleanPortraitTop.add(PlayerControlId.MORE_OPTIONS)

        return copy(
            topRightControls = cleanTopRight,
            bottomLeftControls = cleanBottomLeft,
            bottomRightControls = cleanBottomRight,
            portraitTopControls = cleanPortraitTop,
            portraitBottomControls = cleanPortraitBottom
        )
    }

    /**
     * Returns whether a given area is currently enabled (turned on).
     */
    fun isAreaEnabled(area: ControlArea): Boolean = when (area) {
        ControlArea.TOP_RIGHT -> isLandscapeTopRightEnabled
        ControlArea.BOTTOM_LEFT -> isLandscapeBottomLeftEnabled
        ControlArea.BOTTOM_RIGHT -> isLandscapeBottomRightEnabled
        ControlArea.PORTRAIT_TOP -> isPortraitTopEnabled
        ControlArea.PORTRAIT_BOTTOM -> isPortraitBottomEnabled
    }

    /**
     * Returns a copy with the enabled state of an area updated.
     */
    fun withAreaEnabled(area: ControlArea, enabled: Boolean): PlayerLayoutConfig = when (area) {
        ControlArea.TOP_RIGHT -> copy(isLandscapeTopRightEnabled = enabled)
        ControlArea.BOTTOM_LEFT -> copy(isLandscapeBottomLeftEnabled = enabled)
        ControlArea.BOTTOM_RIGHT -> copy(isLandscapeBottomRightEnabled = enabled)
        ControlArea.PORTRAIT_TOP -> copy(isPortraitTopEnabled = enabled)
        ControlArea.PORTRAIT_BOTTOM -> copy(isPortraitBottomEnabled = enabled)
    }

    /**
     * Returns all controls that are currently unassigned in the given orientation mode.
     */
    fun getAvailableControls(orientation: LayoutOrientationMode): List<PlayerControlId> {
        val activeSet = when (orientation) {
            LayoutOrientationMode.LANDSCAPE -> (topRightControls + bottomLeftControls + bottomRightControls).toSet()
            LayoutOrientationMode.PORTRAIT -> (portraitTopControls + portraitBottomControls).toSet()
        }
        return PlayerControlId.values().filter { it !in activeSet && it != PlayerControlId.MORE_OPTIONS }
    }

    /**
     * Returns the active controls for a specific area.
     */
    fun getControlsForArea(area: ControlArea): List<PlayerControlId> = when (area) {
        ControlArea.TOP_RIGHT -> topRightControls
        ControlArea.BOTTOM_LEFT -> bottomLeftControls
        ControlArea.BOTTOM_RIGHT -> bottomRightControls
        ControlArea.PORTRAIT_TOP -> portraitTopControls
        ControlArea.PORTRAIT_BOTTOM -> portraitBottomControls
    }

    /**
     * Returns a new copy with the specified area updated.
     */
    fun withAreaUpdated(area: ControlArea, newControls: List<PlayerControlId>): PlayerLayoutConfig {
        val newSet = newControls.toSet()
        return when (area) {
            ControlArea.TOP_RIGHT -> copy(
                topRightControls = newControls,
                bottomLeftControls = bottomLeftControls.filter { it !in newSet },
                bottomRightControls = bottomRightControls.filter { it !in newSet }
            ).sanitized()
            ControlArea.BOTTOM_LEFT -> copy(
                topRightControls = topRightControls.filter { it !in newSet || it == PlayerControlId.MORE_OPTIONS },
                bottomLeftControls = newControls,
                bottomRightControls = bottomRightControls.filter { it !in newSet }
            ).sanitized()
            ControlArea.BOTTOM_RIGHT -> copy(
                topRightControls = topRightControls.filter { it !in newSet || it == PlayerControlId.MORE_OPTIONS },
                bottomLeftControls = bottomLeftControls.filter { it !in newSet },
                bottomRightControls = newControls
            ).sanitized()
            ControlArea.PORTRAIT_TOP -> copy(
                portraitTopControls = newControls,
                portraitBottomControls = portraitBottomControls.filter { it !in newSet }
            ).sanitized()
            ControlArea.PORTRAIT_BOTTOM -> copy(
                portraitTopControls = portraitTopControls.filter { it !in newSet || it == PlayerControlId.MORE_OPTIONS },
                portraitBottomControls = newControls
            ).sanitized()
        }
    }
}

