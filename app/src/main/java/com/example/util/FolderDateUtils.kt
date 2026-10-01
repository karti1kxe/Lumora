package com.example.util

import android.os.Build
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FolderDateUtils {

    /**
     * Resolves the accurate filesystem creation time for a folder.
     * Uses java.nio.file.attribute.BasicFileAttributes.creationTime() on API 26+.
     * If filesystem does not expose a valid creation date, uses the fallback date (e.g. earliest media dateAdded)
     * without falsely claiming arbitrary timestamps.
     */
    fun getFolderCreatedDateMillis(path: String, fallbackDate: Long = 0L): Long {
        if (path.isBlank()) return fallbackDate
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val file = File(path)
                if (file.exists()) {
                    val attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
                    val creationTime = attrs.creationTime()?.toMillis() ?: 0L
                    // If creationTime is valid (greater than Jan 2, 1970)
                    if (creationTime > 86400000L) {
                        return creationTime
                    }
                }
            }
        } catch (_: Throwable) {
            // Filesystem attribute query not supported on this volume
        }
        return if (fallbackDate > 86400000L) fallbackDate else 0L
    }

    /**
     * Formats folder creation date cleanly into standard format (e.g., "12 Aug 2026").
     */
    fun formatCreatedDate(timestampMillis: Long): String {
        if (timestampMillis <= 86400000L) return "Unknown"
        return try {
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            sdf.format(Date(timestampMillis))
        } catch (_: Exception) {
            "Unknown"
        }
    }

    /**
     * Formats byte count into human-readable representation (e.g., "14.2 MB", "1.2 GB").
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        val tb = gb * 1024.0
        return when {
            bytes >= tb -> String.format(Locale.US, "%.1f TB", bytes / tb)
            bytes >= gb -> String.format(Locale.US, "%.1f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}
