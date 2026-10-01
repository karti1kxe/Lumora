package com.example.util

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

/**
 * Returns the shared-storage volume roots that Lumora can enumerate.
 * On Android 11+ StorageVolume exposes both primary and removable volumes.
 * On older releases we derive roots from the app's external-files mount points.
 */
fun accessibleStorageRoots(context: Context): List<File> {
    val roots = linkedSetOf<String>()

    fun addRoot(file: File?) {
        if (file == null) return
        try {
            val canonical = file.canonicalFile
            if (canonical.exists() && canonical.isDirectory) {
                roots += canonical.absolutePath
            }
        } catch (_: Throwable) {
            if (file.exists() && file.isDirectory) roots += file.absolutePath
        }
    }

    addRoot(Environment.getExternalStorageDirectory())

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val storageManager = context.getSystemService(StorageManager::class.java)
            storageManager?.storageVolumes?.forEach { volume ->
                addRoot(volume.directory)
            }
        } catch (_: Throwable) {
            // Keep the primary volume fallback above.
        }
    } else {
        try {
            context.getExternalFilesDirs(null).forEach { appFilesDir ->
                var current: File? = appFilesDir
                repeat(4) {
                    current = current?.parentFile
                }
                addRoot(current)
            }
        } catch (_: Throwable) {
            // Keep the primary volume fallback above.
        }
    }

    return roots.map(::File)
}
