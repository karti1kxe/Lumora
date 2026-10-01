package com.example.util

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.example.ui.screens.AudioFolderItem
import com.example.ui.screens.AudioTrackItem
import com.example.ui.screens.VideoFolder
import com.example.ui.screens.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class PrivateMediaType {
    VIDEO,
    AUDIO
}

data class PrivateFolder(
    val id: String,
    val name: String,
    val mediaType: PrivateMediaType,
    val vaultRelativePath: String,
    val itemCount: Int,
    val totalSizeBytes: Long,
    val createdDate: Long,
    val originalPath: String = ""
) {
    val formattedSize: String
        get() = FolderDateUtils.formatFileSize(totalSizeBytes)

    val formattedDate: String
        get() = FolderDateUtils.formatCreatedDate(createdDate)
}

object PrivateVaultManager {

    private const val PREFS_NAME = "lumora_private_vault_secure"
    private const val KEY_SALT = "vault_salt_hex"
    private const val KEY_PWD_HASH = "vault_pwd_hash_hex"
    private const val KEY_ANS_HASH = "vault_ans_hash_hex"
    private const val KEY_IS_SETUP = "vault_is_setup"
    private const val REGISTRY_FILE = "vault_registry.json"

    const val DEFAULT_SECURITY_QUESTION = "What is your favourite movie, anime, web series or cartoon?"

    private val _privateFolders = mutableStateOf<List<PrivateFolder>>(emptyList())
    val privateFolders: State<List<PrivateFolder>> = _privateFolders

    private val _isSessionAuthenticated = mutableStateOf(false)
    val isSessionAuthenticated: State<Boolean> = _isSessionAuthenticated

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        loadRegistry(context)
        isInitialized = true
    }

    fun isSetup(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_SETUP, false) && prefs.getString(KEY_PWD_HASH, null) != null
    }

    /**
     * Initializes the Private Vault with password and security answer.
     * Uses PBKDF2WithHmacSHA256 with a 16-byte cryptographically secure salt.
     * Plaintext credentials are NEVER saved.
     */
    fun setupVault(context: Context, password: String, securityAnswer: String): Boolean {
        if (password.isBlank() || securityAnswer.isBlank()) return false
        try {
            val random = SecureRandom()
            val salt = ByteArray(16)
            random.nextBytes(salt)
            val saltHex = bytesToHex(salt)

            val pwdHash = hashPbkdf2(password.toCharArray(), salt)
            val ansHash = hashPbkdf2(securityAnswer.trim().lowercase(Locale.ROOT).toCharArray(), salt)

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_SALT, saltHex)
                .putString(KEY_PWD_HASH, bytesToHex(pwdHash))
                .putString(KEY_ANS_HASH, bytesToHex(ansHash))
                .putBoolean(KEY_IS_SETUP, true)
                .apply()

            _isSessionAuthenticated.value = true
            init(context)
            return true
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Securely verifies password and security answer against stored hashes.
     * Compares hashes in constant time to prevent timing attacks.
     */
    fun verifyCredentials(context: Context, passwordCandidate: String, answerCandidate: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saltHex = prefs.getString(KEY_SALT, null) ?: return false
        val storedPwdHashHex = prefs.getString(KEY_PWD_HASH, null) ?: return false
        val storedAnsHashHex = prefs.getString(KEY_ANS_HASH, null) ?: return false

        try {
            val salt = hexToBytes(saltHex)
            val candidatePwdHash = hashPbkdf2(passwordCandidate.toCharArray(), salt)
            val candidateAnsHash = hashPbkdf2(answerCandidate.trim().lowercase(Locale.ROOT).toCharArray(), salt)

            val storedPwdHash = hexToBytes(storedPwdHashHex)
            val storedAnsHash = hexToBytes(storedAnsHashHex)

            val pwdMatches = MessageDigest.isEqual(candidatePwdHash, storedPwdHash)
            val ansMatches = MessageDigest.isEqual(candidateAnsHash, storedAnsHash)

            return pwdMatches && ansMatches
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Authenticates the current in-memory session.
     */
    fun authenticateSession(context: Context, passwordCandidate: String, answerCandidate: String): Boolean {
        val valid = verifyCredentials(context, passwordCandidate, answerCandidate)
        if (valid) {
            _isSessionAuthenticated.value = true
        }
        return valid
    }

    fun setSessionAuthenticated(authenticated: Boolean) {
        _isSessionAuthenticated.value = authenticated
    }

    fun clearSession() {
        _isSessionAuthenticated.value = false
    }

    fun getVaultRootDirectory(context: Context): File {
        val dir = File(context.filesDir, "private_vault")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun isFolderPrivate(folderIdOrPath: String): Boolean {
        val list = _privateFolders.value
        return list.any {
            it.id == folderIdOrPath ||
                    it.originalPath == folderIdOrPath ||
                    it.vaultRelativePath == folderIdOrPath ||
                    it.originalPath == VideoLibraryCache.normalizeCanonicalPath(folderIdOrPath)
        }
    }

    private fun loadRegistry(context: Context) {
        val file = File(getVaultRootDirectory(context), REGISTRY_FILE)
        if (!file.exists()) {
            _privateFolders.value = emptyList()
            return
        }
        try {
            val jsonStr = file.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<PrivateFolder>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    PrivateFolder(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        mediaType = PrivateMediaType.valueOf(obj.optString("mediaType", "VIDEO")),
                        vaultRelativePath = obj.getString("vaultRelativePath"),
                        itemCount = obj.optInt("itemCount", 0),
                        totalSizeBytes = obj.optLong("totalSizeBytes", 0L),
                        createdDate = obj.optLong("createdDate", 0L),
                        originalPath = obj.optString("originalPath", "")
                    )
                )
            }
            _privateFolders.value = list
        } catch (_: Exception) {
            _privateFolders.value = emptyList()
        }
    }

    private fun saveRegistry(context: Context) {
        try {
            val file = File(getVaultRootDirectory(context), REGISTRY_FILE)
            val array = JSONArray()
            for (item in _privateFolders.value) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("name", item.name)
                obj.put("mediaType", item.mediaType.name)
                obj.put("vaultRelativePath", item.vaultRelativePath)
                obj.put("itemCount", item.itemCount)
                obj.put("totalSizeBytes", item.totalSizeBytes)
                obj.put("createdDate", item.createdDate)
                obj.put("originalPath", item.originalPath)
                array.put(obj)
            }
            file.writeText(array.toString(2))
        } catch (_: Exception) {}
    }

    /**
     * Safely migrates a public video folder into the app-private vault.
     * Sequence:
     * 1. Copies all files into app internal storage vault.
     * 2. Verifies destination files exist with exact size.
     * 3. ONLY after successful verification, deletes original public files and cleans MediaStore.
     */
    suspend fun makeVideoFolderPrivate(
        context: Context,
        folder: VideoFolder,
        onProgress: ((fraction: Float, completed: Int, total: Int, name: String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val vaultDir = getVaultRootDirectory(context)
        val safeFolderName = folder.name.replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")
        val targetFolder = File(vaultDir, "vid_${System.currentTimeMillis()}_$safeFolderName")
        targetFolder.mkdirs()

        val allVideos = folder.getAllVideos()
        val total = allVideos.size.coerceAtLeast(1)

        val copiedFiles = mutableListOf<Pair<File, File>>()
        var success = true

        try {
            for (index in allVideos.indices) {
                val vid = allVideos[index]
                val srcFile = File(vid.path)
                val destFile = File(targetFolder, vid.displayName)

                onProgress?.invoke(index.toFloat() / total, index, total, vid.displayName)

                if (srcFile.exists()) {
                    val copied = copyFileWithVerification(srcFile, destFile)
                    if (copied) {
                        copiedFiles.add(Pair(srcFile, destFile))
                    } else {
                        success = false
                        break
                    }
                }
                onProgress?.invoke((index + 1).toFloat() / total, index + 1, total, vid.displayName)
            }
        } catch (_: Exception) {
            success = false
        }

        if (success && copiedFiles.size == allVideos.filter { File(it.path).exists() }.size) {
            // Delete original files now that vault copies are verified
            for ((src, _) in copiedFiles) {
                try {
                    src.delete()
                    cleanMediaStoreVideoRecord(context, src.absolutePath)
                } catch (_: Exception) {}
            }
            // If original folder is empty, remove it
            val originalDir = File(folder.path)
            if (originalDir.exists() && originalDir.isDirectory && originalDir.listFiles()?.isEmpty() == true) {
                try { originalDir.delete() } catch (_: Exception) {}
            }

            val createdDate = FolderDateUtils.getFolderCreatedDateMillis(folder.path, folder.lastModifiedDate)
            val newPrivateFolder = PrivateFolder(
                id = "private_vid_${System.currentTimeMillis()}",
                name = folder.name,
                mediaType = PrivateMediaType.VIDEO,
                vaultRelativePath = targetFolder.name,
                itemCount = copiedFiles.size,
                totalSizeBytes = copiedFiles.sumOf { it.second.length() },
                createdDate = createdDate,
                originalPath = folder.path
            )

            val updated = _privateFolders.value.toMutableList()
            updated.add(newPrivateFolder)
            _privateFolders.value = updated
            saveRegistry(context)

            // Refresh media cache
            VideoLibraryCache.clearCache(context)
            return@withContext true
        } else {
            // Migration failed: delete incomplete vault copies without touching original user files
            targetFolder.deleteRecursively()
            return@withContext false
        }
    }

    /**
     * Safely migrates a public audio folder into the app-private vault.
     */
    suspend fun makeAudioFolderPrivate(
        context: Context,
        folder: AudioFolderItem,
        onProgress: ((fraction: Float, completed: Int, total: Int, name: String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val vaultDir = getVaultRootDirectory(context)
        val safeFolderName = folder.name.replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")
        val targetFolder = File(vaultDir, "aud_${System.currentTimeMillis()}_$safeFolderName")
        targetFolder.mkdirs()

        val srcDir = File(folder.path)
        val audioFiles = if (srcDir.exists() && srcDir.isDirectory) {
            srcDir.walkTopDown().filter { it.isFile && AudioFileManager.isAudioFile(it) }.toList()
        } else emptyList()

        val total = audioFiles.size.coerceAtLeast(1)
        val copiedFiles = mutableListOf<Pair<File, File>>()
        var success = true

        try {
            for (index in audioFiles.indices) {
                val srcFile = audioFiles[index]
                val destFile = File(targetFolder, srcFile.name)

                onProgress?.invoke(index.toFloat() / total, index, total, srcFile.name)

                val copied = copyFileWithVerification(srcFile, destFile)
                if (copied) {
                    copiedFiles.add(Pair(srcFile, destFile))
                } else {
                    success = false
                    break
                }
                onProgress?.invoke((index + 1).toFloat() / total, index + 1, total, srcFile.name)
            }
        } catch (_: Exception) {
            success = false
        }

        if (success && copiedFiles.size == audioFiles.size) {
            for ((src, _) in copiedFiles) {
                try {
                    src.delete()
                    cleanMediaStoreAudioRecord(context, src.absolutePath)
                } catch (_: Exception) {}
            }
            if (srcDir.exists() && srcDir.isDirectory && srcDir.listFiles()?.isEmpty() == true) {
                try { srcDir.delete() } catch (_: Exception) {}
            }

            val createdDate = FolderDateUtils.getFolderCreatedDateMillis(folder.path, folder.dateModified * 1000L)
            val newPrivateFolder = PrivateFolder(
                id = "private_aud_${System.currentTimeMillis()}",
                name = folder.name,
                mediaType = PrivateMediaType.AUDIO,
                vaultRelativePath = targetFolder.name,
                itemCount = copiedFiles.size,
                totalSizeBytes = copiedFiles.sumOf { it.second.length() },
                createdDate = createdDate,
                originalPath = folder.path
            )

            val updated = _privateFolders.value.toMutableList()
            updated.add(newPrivateFolder)
            _privateFolders.value = updated
            saveRegistry(context)

            AudioLibraryCache.invalidate()
            return@withContext true
        } else {
            targetFolder.deleteRecursively()
            return@withContext false
        }
    }

    /**
     * Safely restores a private vault folder back to public storage.
     */
    suspend fun restorePrivateFolder(
        context: Context,
        privateFolder: PrivateFolder,
        onProgress: ((fraction: Float, completed: Int, total: Int, name: String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val vaultDir = getVaultRootDirectory(context)
        val vaultFolder = File(vaultDir, privateFolder.vaultRelativePath)
        if (!vaultFolder.exists() || !vaultFolder.isDirectory) return@withContext false

        val publicRoot = if (privateFolder.mediaType == PrivateMediaType.VIDEO) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                ?: File("/storage/emulated/0/Movies")
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                ?: File("/storage/emulated/0/Music")
        }

        val destDir = File(publicRoot, privateFolder.name)
        destDir.mkdirs()

        val filesToRestore = vaultFolder.listFiles()?.filter { it.isFile } ?: emptyList()
        val total = filesToRestore.size.coerceAtLeast(1)
        val restoredFiles = mutableListOf<Pair<File, File>>()
        var success = true

        for (index in filesToRestore.indices) {
            val vaultFile = filesToRestore[index]
            val destFile = File(destDir, vaultFile.name)

            onProgress?.invoke(index.toFloat() / total, index, total, vaultFile.name)

            val copied = copyFileWithVerification(vaultFile, destFile)
            if (copied) {
                restoredFiles.add(Pair(vaultFile, destFile))
            } else {
                success = false
                break
            }
            onProgress?.invoke((index + 1).toFloat() / total, index + 1, total, vaultFile.name)
        }

        if (success && restoredFiles.size == filesToRestore.size) {
            // Tell MediaScanner about restored files
            val paths = restoredFiles.map { it.second.absolutePath }.toTypedArray()
            MediaScannerConnection.scanFile(context, paths, null, null)

            // Delete vault files
            vaultFolder.deleteRecursively()

            val updated = _privateFolders.value.toMutableList()
            updated.removeAll { it.id == privateFolder.id }
            _privateFolders.value = updated
            saveRegistry(context)

            VideoLibraryCache.clearCache(context)
            AudioLibraryCache.invalidate()
            return@withContext true
        } else {
            return@withContext false
        }
    }

    /**
     * Lists video items inside a private vault folder for browsing and playback.
     */
    fun getPrivateFolderVideos(context: Context, privateFolder: PrivateFolder): List<VideoItem> {
        val vaultDir = getVaultRootDirectory(context)
        val folderDir = File(vaultDir, privateFolder.vaultRelativePath)
        if (!folderDir.exists() || !folderDir.isDirectory) return emptyList()

        val files = folderDir.listFiles()?.filter { it.isFile && VideoFileManager.isVideoFile(it) } ?: emptyList()
        return files.mapIndexed { index, file ->
            VideoItem(
                id = -(index + 1000L),
                uri = Uri.fromFile(file),
                displayName = file.name,
                path = file.absolutePath,
                sizeBytes = file.length(),
                durationMs = 0L,
                dateModified = file.lastModified()
            )
        }
    }

    /**
     * Lists audio track items inside a private vault folder for browsing and playback.
     */
    fun getPrivateFolderAudios(context: Context, privateFolder: PrivateFolder): List<AudioTrackItem> {
        val vaultDir = getVaultRootDirectory(context)
        val folderDir = File(vaultDir, privateFolder.vaultRelativePath)
        if (!folderDir.exists() || !folderDir.isDirectory) return emptyList()

        val files = folderDir.listFiles()?.filter { it.isFile && AudioFileManager.isAudioFile(it) } ?: emptyList()
        return files.mapIndexed { index, file ->
            AudioTrackItem(
                id = -(index + 1000L),
                uri = Uri.fromFile(file),
                title = file.nameWithoutExtension,
                artist = "Private Vault",
                album = privateFolder.name,
                path = file.absolutePath,
                sizeBytes = file.length(),
                durationMs = 0L,
                dateModified = file.lastModified() / 1000L,
                format = file.extension.uppercase(Locale.ROOT)
            )
        }
    }

    private fun copyFileWithVerification(src: File, dest: File): Boolean {
        return try {
            FileInputStream(src).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                    output.flush()
                }
            }
            dest.exists() && dest.length() == src.length()
        } catch (_: Exception) {
            false
        }
    }

    private fun cleanMediaStoreVideoRecord(context: Context, path: String) {
        try {
            context.contentResolver.delete(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                "${MediaStore.Video.Media.DATA} = ?",
                arrayOf(path)
            )
        } catch (_: Exception) {}
    }

    private fun cleanMediaStoreAudioRecord(context: Context, path: String) {
        try {
            context.contentResolver.delete(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                "${MediaStore.Audio.Media.DATA} = ?",
                arrayOf(path)
            )
        } catch (_: Exception) {}
    }

    private fun hashPbkdf2(chars: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(chars, salt, 10000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
