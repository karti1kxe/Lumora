package com.example.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver for handling media notification actions (Play/Pause, Next, Previous, Stop).
 */
class AudioNotificationReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_PLAY_PAUSE = "com.example.player.ACTION_PLAY_PAUSE"
        const val ACTION_PREV = "com.example.player.ACTION_PREV"
        const val ACTION_NEXT = "com.example.player.ACTION_NEXT"
        const val ACTION_STOP = "com.example.player.ACTION_STOP"
        private const val TAG = "AudioNotifReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d(TAG, "Received notification action: $action")
        when (action) {
            ACTION_PLAY_PAUSE -> {
                AudioPlaybackManager.togglePlayPause(context)
            }
            ACTION_PREV -> {
                AudioPlaybackManager.skipPrevious(context)
            }
            ACTION_NEXT -> {
                AudioPlaybackManager.skipNext(context)
            }
            ACTION_STOP -> {
                AudioPlaybackManager.closeAndStop(context)
            }
        }
    }
}
