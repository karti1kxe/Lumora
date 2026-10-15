package com.example.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the app alive while videos are downloading and shows ONE ongoing notification with the progress bar, the
 * speed, the size and the time left (several downloads are summarised in one notification). Finished / failed
 * downloads get their own notification. The service stops itself when nothing is running any more.
 */
class DownloadService : Service() {

    companion object {
        private const val CHANNEL_PROGRESS = "lumora_downloads_progress"
        private const val CHANNEL_DONE = "lumora_downloads_done"
        private const val NOTIF_ID = 7301
        private const val ACTION_PAUSE = "com.example.download.PAUSE"
        private const val ACTION_CANCEL = "com.example.download.CANCEL"
        private const val ACTION_PAUSE_ALL = "com.example.download.PAUSE_ALL"
        private const val EXTRA_ID = "download_id"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context.applicationContext, Intent(context.applicationContext, DownloadService::class.java))
            } catch (t: Throwable) {
                android.util.Log.w("DownloadService", "could not start the download service", t)
            }
        }

        private fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_PROGRESS) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_PROGRESS, "Download progress", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Speed and progress of running downloads"
                        setShowBadge(false)
                    }
                )
            }
            if (nm.getNotificationChannel(CHANNEL_DONE) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_DONE, "Download finished", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "A download finished or failed"
                    }
                )
            }
        }

        private fun openAppIntent(context: Context): PendingIntent {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(context, com.example.MainActivity::class.java)
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private fun actionIntent(context: Context, action: String, id: String?): PendingIntent {
            val i = Intent(context, DownloadService::class.java).setAction(action)
            if (id != null) i.putExtra(EXTRA_ID, id)
            val code = (action + (id ?: "")).hashCode()
            return PendingIntent.getService(context, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private fun doneId(item: DownloadItem): Int = 8000 + (item.id.hashCode() and 0xFFFF)

        /** "Download complete" notification (tap = open the app). */
        fun notifyDone(context: Context, item: DownloadItem) {
            try {
                val app = context.applicationContext
                ensureChannels(app)
                val n = NotificationCompat.Builder(app, CHANNEL_DONE)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("Download complete")
                    .setContentText(item.title + " • " + DownloadFormat.bytes(item.totalBytes.coerceAtLeast(item.downloadedBytes)))
                    .setContentIntent(openAppIntent(app))
                    .setAutoCancel(true)
                    .build()
                (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(doneId(item), n)
            } catch (_: Throwable) {
            }
        }

        fun notifyFailed(context: Context, item: DownloadItem) {
            try {
                val app = context.applicationContext
                ensureChannels(app)
                val n = NotificationCompat.Builder(app, CHANNEL_DONE)
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentTitle("Download failed")
                    .setContentText(item.title + (item.note?.let { " • $it" } ?: ""))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(item.title + (item.note?.let { "\n$it" } ?: "")))
                    .setContentIntent(openAppIntent(app))
                    .setAutoCancel(true)
                    .build()
                (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(doneId(item), n)
            } catch (_: Throwable) {
            }
        }

        /** Removes the finished / failed notification of a download that was deleted. */
        fun clearDone(context: Context, id: String) {
            try {
                val nm = context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(8000 + (id.hashCode() and 0xFFFF))
            } catch (_: Throwable) {
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    @Volatile private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        val app = applicationContext
        DownloadStore.init(app)
        ensureChannels(app)

        val active = DownloadStore.items.value.filter { it.isActive }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, buildOngoing(active), type)
        } catch (t: Throwable) {
            android.util.Log.w("DownloadService", "startForeground failed", t)
        }

        when (intent?.action) {
            ACTION_PAUSE -> intent?.getStringExtra(EXTRA_ID)?.let { DownloadManager.pause(app, it) }
            ACTION_CANCEL -> intent?.getStringExtra(EXTRA_ID)?.let { DownloadManager.cancel(app, it) }
            ACTION_PAUSE_ALL -> DownloadManager.pauseAll(app)
        }

        if (loop?.isActive != true) {
            loop = scope.launch {
                var idleTicks = 0
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                while (isActive) {
                    delay(1000L)
                    val now = DownloadStore.items.value.filter { it.isActive }
                    if (now.isEmpty()) {
                        idleTicks++
                        if (idleTicks >= 2) break
                    } else {
                        idleTicks = 0
                        try { nm.notify(NOTIF_ID, buildOngoing(now)) } catch (_: Throwable) {}
                    }
                }
                ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf(lastStartId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildOngoing(active: List<DownloadItem>): Notification {
        val b = NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(this))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        when {
            active.isEmpty() -> {
                b.setContentTitle("Downloads")
                b.setContentText("Finishing up…")
            }
            active.size == 1 -> {
                val d = active[0]
                b.setContentTitle(d.title)
                val line = when {
                    d.status == DownloadStatus.QUEUED -> "Waiting in queue…"
                    d.status == DownloadStatus.PROCESSING -> d.note ?: "Saving to your library…"
                    d.note != null -> d.note
                    else -> DownloadFormat.sizeLine(d) + " • " + DownloadFormat.speedLine(d)
                }
                b.setContentText(line)
                b.setStyle(NotificationCompat.BigTextStyle().bigText(line))
                if (d.totalBytes > 0L && d.note == null) b.setProgress(1000, (d.fraction * 1000).toInt(), false)
                else b.setProgress(0, 0, true)
                b.addAction(0, "Pause", actionIntent(this, ACTION_PAUSE, d.id))
                b.addAction(0, "Cancel", actionIntent(this, ACTION_CANCEL, d.id))
            }
            else -> {
                val speed = active.sumOf { it.speedBps }
                val total = active.sumOf { it.totalBytes }
                val done = active.sumOf { if (it.totalBytes > 0L) minOf(it.downloadedBytes, it.totalBytes) else 0L }
                b.setContentTitle("Downloading ${active.size} videos")
                b.setContentText(DownloadFormat.speed(speed))
                val inbox = NotificationCompat.InboxStyle()
                for (d in active.take(5)) {
                    val pct = if (d.totalBytes > 0L) " • ${(d.fraction * 100).toInt()}%" else ""
                    inbox.addLine(d.title + pct)
                }
                inbox.setSummaryText(DownloadFormat.speed(speed))
                b.setStyle(inbox)
                if (total > 0L) b.setProgress(1000, ((done.toDouble() / total.toDouble()) * 1000).toInt().coerceIn(0, 1000), false)
                else b.setProgress(0, 0, true)
                b.addAction(0, "Pause all", actionIntent(this, ACTION_PAUSE_ALL, null))
            }
        }
        return b.build()
    }
}
