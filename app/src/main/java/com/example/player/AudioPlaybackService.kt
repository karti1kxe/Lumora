package com.example.player

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Foreground Service for persistent audio playback and media notification controls.
 * Ensures the app process is granted foreground priority so online stream resolution,
 * buffering, audio decoding, and thumbnail loading continue uninterrupted when the
 * user minimizes the app or navigates to the home screen.
 */
class AudioPlaybackService : Service() {

    companion object {
        private const val TAG = "AudioPlaybackService"
        const val ACTION_START_OR_UPDATE = "com.example.player.ACTION_START_OR_UPDATE"
        const val ACTION_STOP = "com.example.player.ACTION_STOP"

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        @Volatile
        var activeService: AudioPlaybackService? = null
            private set

        fun startService(context: Context) {
            try {
                val intent = Intent(context.applicationContext, AudioPlaybackService::class.java).apply {
                    action = ACTION_START_OR_UPDATE
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context.applicationContext, intent)
                } else {
                    context.applicationContext.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting AudioPlaybackService: ${e.message}", e)
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context.applicationContext, AudioPlaybackService::class.java).apply {
                    action = ACTION_STOP
                }
                context.applicationContext.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping AudioPlaybackService: ${e.message}", e)
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        activeService = this
        isServiceRunning = true
        acquireWakeLock()
        // Immediately start foreground with notification so Android OS guarantees foreground state within 5 seconds
        val notification = AudioNotificationManager.buildCurrentNotification(this)
        startForegroundWithNotification(notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activeService = this
        isServiceRunning = true

        val action = intent?.action
        if (action == ACTION_STOP) {
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }

        acquireWakeLock()
        val notification = AudioNotificationManager.buildCurrentNotification(this)
        startForegroundWithNotification(notification)
        return START_STICKY
    }

    fun updateForegroundNotification(notification: Notification) {
        try {
            startForegroundWithNotification(notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating foreground notification: ${e.message}", e)
        }
    }

    private fun startForegroundWithNotification(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    AudioNotificationManager.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(AudioNotificationManager.NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to startForeground: ${e.message}", e)
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Lumora:AudioPlaybackServiceWakeLock"
                )?.apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.let {
                if (!it.isHeld) {
                    it.acquire(45 * 60 * 1000L) // 45-minute safety timeout
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire partial wake lock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.w(TAG, "Could not release wake lock: ${e.message}")
        }
    }

    fun stopForegroundAndSelf() {
        isServiceRunning = false
        activeService = null
        releaseWakeLock()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in stopForeground: ${e.message}", e)
        }
        stopSelf()
    }

    override fun onDestroy() {
        isServiceRunning = false
        if (activeService === this) {
            activeService = null
        }
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
