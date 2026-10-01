package com.example.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import com.example.R

@Composable
fun StyledIcon(
    drawableRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Icon(
        painter = painterResource(drawableRes),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}

@Composable
fun StyledIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val key = imageVector.name.substringAfterLast('.')
    val resId = when (key) {
        "PhoneAndroid" -> R.drawable.lumora_smartphone
        "Cast" -> R.drawable.lumora_screencast
        "BookmarkBorder" -> R.drawable.lumora_bookmark
        "Memory" -> R.drawable.lumora_cpu
        "MusicNote" -> R.drawable.lumora_song
        "Subtitles" -> R.drawable.lumora_subtitles
        "DashboardCustomize" -> R.drawable.lumora_widget_5
        "MoreVert" -> R.drawable.lumora_menu_dots
        "Headphones" -> R.drawable.lumora_headphones_round
        "LockOpen" -> R.drawable.lumora_lock_unlocked
        "ScreenRotation" -> R.drawable.lumora_smartphone_rotate_orientation
        "Speed" -> R.drawable.lumora_speed
        "Repeat" -> R.drawable.lumora_repeat
        "Shuffle" -> R.drawable.lumora_shuffle
        "AllInclusive" -> R.drawable.lumora_ab_loop
        "CameraAlt" -> R.drawable.lumora_screenshot
        "ZoomIn" -> R.drawable.lumora_magnifer_zoom_in
        "PictureInPictureAlt" -> R.drawable.lumora_pip
        "AspectRatio" -> R.drawable.lumora_minimize_square_2
        "GraphicEq" -> R.drawable.lumora_equalizer
        "VideoLibrary" -> R.drawable.lumora_video_library
        "VideoFile", "Videocam" -> R.drawable.lumora_video
        "Audiotrack" -> R.drawable.lumora_song
        "AccessTime" -> R.drawable.lumora_clock_circle
        "VolumeUp" -> R.drawable.lumora_volume_loud
        "WbSunny", "BrightnessMedium" -> R.drawable.lumora_sun
        "BrightnessLow" -> R.drawable.lumora_sun_2
        "Search" -> R.drawable.lumora_magnifer
        "Settings" -> R.drawable.lumora_settings
        "SortByAlpha" -> R.drawable.lumora_sort_by_alphabet
        "Add" -> R.drawable.lumora_add_circle
        "Check", "CheckCircle", "DoneAll" -> R.drawable.lumora_check_circle
        "ChevronLeft" -> R.drawable.lumora_arrow_left_solid
        "ChevronRight" -> R.drawable.lumora_arrow_right_solid
        "Clear", "Close" -> R.drawable.lumora_close_circle
        "CreateNewFolder" -> R.drawable.lumora_add_folder
        "DarkMode" -> R.drawable.lumora_moon
        "Description" -> R.drawable.lumora_document_text
        "Download" -> R.drawable.lumora_download_minimalistic
        "DragIndicator", "DragHandle" -> R.drawable.lumora_reorder
        "ExpandLess", "KeyboardArrowUp" -> R.drawable.lumora_list_arrow_up
        "ExpandMore", "KeyboardArrowDown", "ArrowDropDown" -> R.drawable.lumora_list_arrow_down
        "Folder" -> R.drawable.lumora_folder
        "FolderOff" -> R.drawable.lumora_folder_error
        "FolderOpen" -> R.drawable.lumora_folder_open
        "FolderSpecial" -> R.drawable.lumora_folder_favourite_star
        "FolderZip" -> R.drawable.lumora_winrar
        "FontDownload" -> R.drawable.lumora_text
        "FormatListBulleted" -> R.drawable.lumora_list
        "GridView" -> R.drawable.lumora_widget_5
        "History" -> R.drawable.lumora_recent
        "Lock" -> R.drawable.lumora_lock
        "Movie" -> R.drawable.lumora_video
        "Palette" -> R.drawable.lumora_pallete_2
        "PlaylistPlay" -> R.drawable.lumora_playlist_video
        "Remove" -> R.drawable.lumora_minus_circle
        "RepeatOne" -> R.drawable.lumora_repeat_one
        "RestartAlt" -> R.drawable.lumora_restart
        "Save" -> R.drawable.lumora_download_square
        "Security" -> R.drawable.lumora_shield_check
        "Storage" -> R.drawable.lumora_folder_with_files
        "SubdirectoryArrowRight", "ArrowForwardIos" -> R.drawable.lumora_arrow_right_solid
        "SurroundSound" -> R.drawable.lumora_audio_circle
        "SwapHoriz" -> R.drawable.lumora_sort_horizontal
        "SwapVert" -> R.drawable.lumora_sort_vertical
        "Timelapse" -> R.drawable.lumora_clock_circle
        "Title" -> R.drawable.lumora_text
        "Translate" -> R.drawable.lumora_translation
        "UploadFile" -> R.drawable.lumora_upload_minimalistic
        "ViewList" -> R.drawable.lumora_list
        "Visibility" -> R.drawable.lumora_eye
        "VisibilityOff" -> R.drawable.lumora_eye_closed
        "VolumeDown" -> R.drawable.lumora_volume_small
        "VolumeOff" -> R.drawable.lumora_volume_cross
        "VolumeMute" -> R.drawable.lumora_volume
        "FastForward" -> R.drawable.lumora_rewind_forward
        "FastRewind" -> R.drawable.lumora_rewind_back
        "Favorite" -> R.drawable.lumora_heart
        "Pause" -> R.drawable.lumora_pause_solid
        "PlayArrow" -> R.drawable.lumora_play_solid
        "SkipNext" -> R.drawable.lumora_arrow_right_solid
        "SkipPrevious" -> R.drawable.lumora_arrow_left_solid
        "ArrowBack" -> R.drawable.lumora_leave
        "ArrowForward" -> R.drawable.lumora_arrow_right_solid
        "SubtitlesOff" -> R.drawable.lumora_subtitles
        "Sort" -> R.drawable.lumora_sort
        "Refresh" -> R.drawable.lumora_refresh
        "SelectAll" -> R.drawable.lumora_checklist
        "Share" -> R.drawable.lumora_share
        "QueueMusic" -> R.drawable.lumora_audio_track
        "Replay" -> R.drawable.lumora_repeat
        "KeyboardArrowRight" -> R.drawable.lumora_arrow_right_solid
        "PhotoCamera" -> R.drawable.lumora_screenshot
        "CalendarToday" -> R.drawable.lumora_clock_circle
        "PlayCircle" -> R.drawable.lumora_play_circle
        "Loop" -> R.drawable.lumora_ab_loop
        "ColorLens" -> R.drawable.lumora_pallete_2
        "ContentCopy" -> R.drawable.lumora_copy
        "DriveFileMove" -> R.drawable.lumora_move_to_folder
        "Info" -> R.drawable.lumora_info_circle
        "AttachFile" -> R.drawable.lumora_document
        "Block" -> R.drawable.lumora_block
        "Bookmarks" -> R.drawable.lumora_bookmark
        "Cloud" -> R.drawable.lumora_cloud
        "FileDownload" -> R.drawable.lumora_download_minimalistic
        "FileUpload", "Upload" -> R.drawable.lumora_upload_minimalistic
        "FormatAlignCenter" -> R.drawable.lumora_align_horizontal_center
        "FormatBold" -> R.drawable.lumora_text_bold
        "FormatColorReset" -> R.drawable.lumora_eraser
        "FormatItalic" -> R.drawable.lumora_text_italic
        "HighQuality" -> R.drawable.lumora_high_quality
        "QueuePlayNext" -> R.drawable.lumora_playlist_video
        "Restore" -> R.drawable.lumora_restart
        "SettingsBackupRestore" -> R.drawable.lumora_settings_minimalistic
        "WatchLater" -> R.drawable.lumora_recent
        "Edit" -> R.drawable.lumora_pen
        "DriveFileRenameOutline" -> R.drawable.lumora_pen_2
        "Delete", "DeleteOutline" -> R.drawable.lumora_trash_bin_2
        "Send" -> R.drawable.lumora_share
        "OpenInNew" -> R.drawable.lumora_maximize_square
        "FolderShared" -> R.drawable.lumora_folder
        "MusicOff" -> R.drawable.lumora_volume_cross
        "NotificationsActive" -> R.drawable.lumora_notification_unread
        "PlaylistAdd" -> R.drawable.lumora_playlist_video
        "TextFields" -> R.drawable.lumora_text_field
        "Android" -> R.drawable.lumora_smartphone
        "AddFolder" -> R.drawable.lumora_add_folder
        "AccountTree" -> R.drawable.lumora_folder_with_files
        "Tv" -> R.drawable.lumora_video
        "Tune" -> R.drawable.lumora_tuning
        "Size" -> R.drawable.lumora_size
        "Payment" -> R.drawable.lumora_document
        "WarningAmber" -> R.drawable.lumora_shield_minimalistic
        "ErrorOutline" -> R.drawable.lumora_folder_error
        "Wifi" -> R.drawable.lumora_wifi
        "BatteryChargingFull", "BatteryStd" -> R.drawable.lumora_battery
        "RadioButtonUnchecked" -> R.drawable.lumora_minus_circle
        "ListCheck" -> R.drawable.lumora_checklist
        else -> 0
    }
    if (resId != 0) {
        Icon(painter = painterResource(resId), contentDescription = contentDescription, modifier = modifier, tint = tint)
    } else {
        Icon(imageVector = imageVector, contentDescription = contentDescription, modifier = modifier, tint = tint)
    }
}
