package com.example.util

import androidx.compose.runtime.mutableStateOf

/**
 * Lightweight global holder so any Composable can react to Picture-in-Picture mode
 * changes without threading a parameter through every screen. Updated from
 * MainActivity and VideoPlayerScreen.
 */
object PipModeState {
    val isInPictureInPicture = mutableStateOf(false)
    var isPlayerActive: Boolean = false
    var isCurrentlyPlaying: Boolean = false
    var autoPipEnabled: Boolean = false

    var onPlayPauseToggle: (() -> Unit)? = null
    var requestPipUpdate: ((isPlaying: Boolean, autoPip: Boolean) -> Unit)? = null

    fun onPipPlayPauseTriggered() {
        onPlayPauseToggle?.invoke()
    }
}

