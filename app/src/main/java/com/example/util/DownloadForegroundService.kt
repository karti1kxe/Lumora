package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground Service for Downloads:
 * Shows live download progress, real-time speed, ETA, and interactive control actions (Pause, Resume, Cancel).
 */
class DownloadForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "lumora_download_channel"
        const val CHANNEL_NAME = "Downloads"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.example.action.DOWNLOAD_START"
        const val ACTION_PAUSE = "com.example.action.DOWNLOAD_PAUSE"
        const val ACTION_RESUME = "com.example.action.DOWNLOAD_RESUME"
        const val ACTION_CANCEL = "com.example.action.DOWNLOAD_CANCEL"
        const val EXTRA_ID = "extra_download_id"

        fun start(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun notifyDone(context: Context, item: DownloadItem) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            ensureChannel(context, nm)
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context,
                item.id.hashCode() + 10,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.lumora_download_minimalistic)
                .setContentTitle(item.title)
                .setContentText("Download completed • " + DownloadFormat.bytes(item.totalBytes))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            nm.notify(item.id.hashCode() + 10, notif)
        }

        fun notifyFailed(context: Context, item: DownloadItem) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            ensureChannel(context, nm)
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.lumora_download_minimalistic)
                .setContentTitle(item.title)
                .setContentText(item.note ?: "Download failed")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            nm.notify(item.id.hashCode() + 20, notif)
        }

        private fun ensureChannel(context: Context, nm: NotificationManager) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Shows live download progress, speed and controls"
                        setShowBadge(false)
                    }
                    nm.createNotificationChannel(channel)
                }
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(this, nm)
        startForeground(NOTIFICATION_ID, buildNotification(emptyList()))
        observeDownloads()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val id = intent?.getStringExtra(EXTRA_ID)
        when (action) {
            ACTION_PAUSE -> id?.let { DownloadManager.pause(applicationContext, it) }
            ACTION_RESUME -> id?.let { DownloadManager.resume(applicationContext, it) }
            ACTION_CANCEL -> id?.let { DownloadManager.cancel(applicationContext, it) }
        }
        return START_STICKY
    }

    private fun observeDownloads() {
        observerJob?.cancel()
        observerJob = serviceScope.launch {
            DownloadStore.items.collectLatest { list ->
                val active = list.filter { it.isActive }
                if (active.isEmpty()) {
                    delay(1200L)
                    val stillEmpty = DownloadStore.items.value.none { it.isActive }
                    if (stillEmpty) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                } else {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    nm?.notify(NOTIFICATION_ID, buildNotification(active))
                }
            }
        }
    }

    private fun buildNotification(active: List<DownloadItem>): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.lumora_download_minimalistic)
            .setContentIntent(contentPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (active.isEmpty()) {
            return builder
                .setContentTitle("Downloads")
                .setContentText("Preparing download…")
                .setProgress(0, 0, true)
                .build()
        }

        val primary = active.first()
        val otherCount = active.size - 1
        val title = if (otherCount > 0) "${primary.title} (+${otherCount} more)" else primary.title
        val fraction = primary.fraction
        val percent = (fraction * 100).toInt().coerceIn(0, 100)
        val indeterminate = primary.totalBytes <= 0L

        val speedText = if (primary.speedBps > 0) DownloadFormat.speed(primary.speedBps) else ""
        val etaText = if (primary.etaSec > 0) DownloadFormat.eta(primary.etaSec) + " left" else ""
        val sub = listOfNotNull(
            primary.note?.takeIf { it.isNotBlank() },
            DownloadFormat.sizeLine(primary),
            speedText.takeIf { it.isNotBlank() },
            etaText.takeIf { it.isNotBlank() }
        ).joinToString(" • ")

        builder.setContentTitle(title)
            .setContentText(sub)
            .setProgress(100, percent, indeterminate)

        // Action: Pause
        val pauseIntent = Intent(this, DownloadForegroundService::class.java).apply {
            action = ACTION_PAUSE
            putExtra(EXTRA_ID, primary.id)
        }
        val pausePending = PendingIntent.getService(
            this,
            primary.id.hashCode() + 1,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        builder.addAction(R.drawable.lumora_pause_solid, "Pause", pausePending)

        // Action: Cancel
        val cancelIntent = Intent(this, DownloadForegroundService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_ID, primary.id)
        }
        val cancelPending = PendingIntent.getService(
            this,
            primary.id.hashCode() + 2,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        builder.addAction(R.drawable.lumora_close_circle, "Cancel", cancelPending)

        return builder.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        observerJob?.cancel()
    }
}
