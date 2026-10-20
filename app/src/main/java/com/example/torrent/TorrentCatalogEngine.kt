/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Metadata-only part of mpvRx's torrent engine (AGPL-3.0). It resolves a magnet / .torrent source into a file list for
 * the download picker. There is NO playback / streaming here: the chosen file is fetched by [TorrentDownloadEngine].
 */

package com.example.torrent

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.libtorrent4j.AlertListener
import org.libtorrent4j.AnnounceEntry
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionHandle
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.alerts.AddTorrentAlert
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.TorrentErrorAlert
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Process wide holder of the metadata engine (Lumora has no DI container). */
object TorrentCatalogs {
  @Volatile private var instance: TorrentCatalogEngine? = null

  fun engine(context: Context): TorrentCatalogEngine =
    instance ?: synchronized(this) {
      instance ?: TorrentCatalogEngine(context.applicationContext).also { instance = it }
    }
}

class TorrentCatalogEngine(
  context: Context,
) {
  companion object {
    private const val TAG = "TorrentCatalogEngine"
    private const val METADATA_TIMEOUT_MS = 90_000L
    private const val MAX_METADATA_BYTES = 16L * 1024L * 1024L
    private const val MAX_TORRENT_FILES = 100_000

    /** Only these file types are offered for download (same list as before). */
    private val playableMimeTypes =
      mapOf(
        "3g2" to "video/3gpp2",
        "3gp" to "video/3gpp",
        "asf" to "video/x-ms-asf",
        "avi" to "video/x-msvideo",
        "divx" to "video/*",
        "f4v" to "video/mp4",
        "flv" to "video/x-flv",
        "m2ts" to "video/mp2t",
        "m2v" to "video/mpeg",
        "m4s" to "video/iso.segment",
        "m4v" to "video/mp4",
        "mkv" to "video/x-matroska",
        "mov" to "video/quicktime",
        "mp4" to "video/mp4",
        "mpeg" to "video/mpeg",
        "mpg" to "video/mpeg",
        "mts" to "video/mp2t",
        "ogm" to "video/ogg",
        "ogv" to "video/ogg",
        "rm" to "video/*",
        "rmvb" to "video/*",
        "ts" to "video/mp2t",
        "vob" to "video/mpeg",
        "webm" to "video/webm",
        "wmv" to "video/x-ms-wmv",
        "xvid" to "video/*",
        "aac" to "audio/aac",
        "ac3" to "audio/ac3",
        "aif" to "audio/aiff",
        "aiff" to "audio/aiff",
        "alac" to "audio/mp4",
        "ape" to "audio/*",
        "dff" to "audio/*",
        "dsf" to "audio/*",
        "dts" to "audio/vnd.dts",
        "eac3" to "audio/eac3",
        "flac" to "audio/flac",
        "m4a" to "audio/mp4",
        "mka" to "audio/x-matroska",
        "mp1" to "audio/mpeg",
        "mp2" to "audio/mpeg",
        "mp3" to "audio/mpeg",
        "oga" to "audio/ogg",
        "ogg" to "audio/ogg",
        "opus" to "audio/opus",
        "ra" to "audio/*",
        "tak" to "audio/*",
        "tta" to "audio/*",
        "wav" to "audio/wav",
        "wave" to "audio/wav",
        "weba" to "audio/webm",
        "wma" to "audio/x-ms-wma",
      )
  }

  private data class PreparedTorrent(
    val handle: TorrentHandle,
    val info: TorrentInfo,
    val durableSource: String,
    val infoHash: String,
  )

  private data class PreparedSession(
    val id: String,
    val session: SessionManager,
    val handle: TorrentHandle,
    val cacheDir: File,
    val durableSource: String,
    val infoHash: String,
    val torrentName: String,
    val playableFiles: List<TorrentFileItem>,
  ) {
    fun catalog() =
      TorrentCatalog(
        preparationId = id,
        source = durableSource,
        infoHash = infoHash,
        torrentName = torrentName,
        playableFiles = playableFiles,
      )
  }

  private val appContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val lifecycleMutex = Mutex()
  private val generation = AtomicLong(0L)
  private val httpClient =
    OkHttpClient
      .Builder()
      .connectTimeout(20L, TimeUnit.SECONDS)
      .readTimeout(30L, TimeUnit.SECONDS)
      .callTimeout(45L, TimeUnit.SECONDS)
      .build()

  private val _phase = MutableStateFlow("Starting Torrent Engine...")

  /** Text for the picker while the metadata is being fetched. */
  val phase: StateFlow<String> = _phase.asStateFlow()

  @Volatile
  private var prepared: PreparedSession? = null

  @Volatile
  private var closed = false

  /** Resolves and validates torrent metadata (file list) without downloading any media file. */
  suspend fun prepareTorrent(source: String): TorrentCatalog =
    withContext(Dispatchers.IO) {
      val startGeneration = generation.incrementAndGet()
      lifecycleMutex.withLock {
        check(!closed) { "Torrent engine is shut down" }
        cleanupPrepared()
        ensureCurrent(startGeneration)

        val preparedSession = createPreparedSession(source, startGeneration)
        prepared = preparedSession
        preparedSession.catalog()
      }
    }

  /** Releases the metadata session of the picker (native session and its temporary folder). */
  fun discardPreparation(preparationId: String) {
    if (preparationId.isBlank()) return
    scope.launch {
      lifecycleMutex.withLock {
        if (prepared?.id == preparationId) cleanupPrepared()
      }
    }
  }

  fun shutdown() {
    if (closed) return
    closed = true
    val stopGeneration = generation.incrementAndGet()
    scope.launch {
      lifecycleMutex.withLock {
        if (generation.get() == stopGeneration) cleanupPrepared()
      }
      scope.cancel()
    }
  }

  private suspend fun createPreparedSession(
    sourceValue: String,
    startGeneration: Long,
  ): PreparedSession {
    val source = sourceValue.trim()
    if (source.isEmpty()) throw streamError("Torrent source is empty.")
    if (hasV2OnlyMagnet(source)) {
      throw streamError("BitTorrent v2-only torrents are not supported yet.")
    }
    val normalized = normalizeTorrentSource(source)
      ?: if (isMetadataUri(source)) source else throw streamError("Unsupported torrent source.")

    val cacheDir = File(appContext.cacheDir, "torrent_catalog/${UUID.randomUUID()}")
    if (!cacheDir.mkdirs() && !cacheDir.isDirectory) {
      throw streamError("Couldn't create the torrent cache.")
    }

    var session: SessionManager? = null
    var handle: TorrentHandle? = null
    try {
      _phase.value = "Starting Torrent Engine..."
      val startedSession = SessionManager()
      session = startedSession
      val settings =
        SettingsPack().apply {
          setMaxMetadataSize(MAX_METADATA_BYTES.toInt())
          setEnableDht(true)
          setEnableLsd(true)
        }
      startedSession.start(SessionParams(settings))
      ensureCurrent(startGeneration)

      val torrent =
        if (normalized.startsWith("magnet:?", ignoreCase = true)) {
          prepareMagnet(startedSession, cacheDir, normalized, startGeneration)
        } else {
          prepareMetadata(startedSession, cacheDir, normalized, startGeneration)
        }
      handle = torrent.handle

      val files = validateAndEnumerateFiles(torrent.info, cacheDir)
      val playableFiles =
        files
          .filter { it.mimeType.isNotEmpty() }
          .sortedWith { f1, f2 ->
            compareTorrentMediaFiles(f1.name, f1.index, f2.name, f2.index)
          }
      if (playableFiles.isEmpty()) throw streamError("Torrent contains no playable audio or video files.")
      val torrentName = safeDisplayName(torrent.info.name(), playableFiles.first().name)

      return PreparedSession(
        id = UUID.randomUUID().toString(),
        session = startedSession,
        handle = torrent.handle,
        cacheDir = cacheDir,
        durableSource = torrent.durableSource,
        infoHash = torrent.infoHash,
        torrentName = torrentName,
        playableFiles = playableFiles,
      )
    } catch (cancellation: CancellationException) {
      cleanupAfterPreparationFailure(session, handle, cacheDir)
      throw cancellation
    } catch (error: Throwable) {
      cleanupAfterPreparationFailure(session, handle, cacheDir)
      val safe = (error as? TorrentException) ?: streamError("Couldn't read torrent metadata.", error)
      if (error is LinkageError) {
        Log.e(TAG, "Torrent native runtime is unavailable", error)
      } else {
        Log.e(TAG, "Torrent preparation failed (${error::class.java.simpleName})")
      }
      throw safe
    }
  }

  private suspend fun prepareMagnet(
    session: SessionManager,
    cacheDir: File,
    source: String,
    startGeneration: Long,
  ): PreparedTorrent {
    val parsed = parseMagnet(source) ?: throw streamError("Magnet link does not contain a supported v1 info hash.")
    val hash = runCatching { Sha1Hash.parseHex(parsed.infoHash) }.getOrNull()
      ?: throw streamError("Magnet link contains an invalid v1 info hash.")

    _phase.value = "Connecting to peers and fetching torrent metadata..."
    return monitorTorrentErrors(session) { failure ->
      // Upload mode still permits metadata exchange but prevents unselected payload files from
      // racing onto disk before their paths and priorities have been validated.
      session.download(parsed.cleanMagnetUri, cacheDir, TorrentFlags.UPLOAD_MODE)
      val handle = waitForHandle(session, hash, startGeneration, failure)
      // Preserve explicit/private tracker policy. Public fallbacks are only appropriate when the
      // source supplied no tracker at all.
      if (parsed.trackers.isEmpty()) {
        DEFAULT_TORRENT_TRACKERS.forEach { tracker -> handle.addTracker(AnnounceEntry(tracker)) }
        handle.forceReannounce()
      }
      val info = waitForMetadata(handle, startGeneration, failure)
      if (!info.hasV1()) throw streamError("BitTorrent v2-only torrents are not supported yet.")
      if (!info.infoHash().toHex().equals(parsed.infoHash, ignoreCase = true)) {
        throw streamError("Torrent metadata did not match the requested info hash.")
      }
      PreparedTorrent(
        handle = handle,
        info = info,
        durableSource = magnetWithoutFileSelection(parsed.cleanMagnetUri),
        infoHash = parsed.infoHash.lowercase(),
      )
    }
  }

  private suspend fun prepareMetadata(
    session: SessionManager,
    cacheDir: File,
    source: String,
    startGeneration: Long,
  ): PreparedTorrent {
    _phase.value = "Reading torrent metadata..."
    val payload = readMetadata(source)
    val endpoints = runCatching { extractTorrentMetadataEndpoints(payload) }.getOrElse {
      throw streamError("The selected file contains invalid torrent metadata.")
    }
    val info = runCatching { TorrentInfo(payload) }.getOrElse {
      throw streamError("The selected file is not valid torrent metadata.")
    }
    if (!info.isValid) throw streamError("The selected file is not valid torrent metadata.")
    if (!info.hasV1()) throw streamError("BitTorrent v2-only torrents are not supported yet.")
    validateAndEnumerateFiles(info, cacheDir)
    val hashHex = info.infoHash().toHex().lowercase()
    val priorities = Array(info.files().numFiles()) { Priority.IGNORE }

    return monitorTorrentErrors(session) { failure ->
      session.download(info, cacheDir, null, priorities, null, TorrentFlags.UPLOAD_MODE)
      val handle = waitForHandle(session, info.infoHash(), startGeneration, failure)
      endpoints.trackers.forEach { tracker -> handle.addTracker(AnnounceEntry(tracker)) }
      endpoints.webSeeds.forEach(handle::addUrlSeed)
      if (endpoints.trackers.isNotEmpty()) handle.forceReannounce()
      if (info.isPrivate && endpoints.trackers.isEmpty()) {
        throw streamError("Private torrent metadata does not contain a supported tracker.")
      }
      val durableSource =
        buildMagnetUri(
          infoHash = hashHex,
          trackers = endpoints.trackers,
          displayName = safeDisplayName(info.name(), hashHex),
          webSeeds = endpoints.webSeeds,
        )
      PreparedTorrent(handle, info, durableSource, hashHex)
    }
  }

  private suspend fun <T> monitorTorrentErrors(
    session: SessionManager,
    block: suspend (AtomicReference<TorrentException?>) -> T,
  ): T {
    val failure = AtomicReference<TorrentException?>()
    val listener =
      object : AlertListener {
        override fun types(): IntArray =
          intArrayOf(
            AlertType.ADD_TORRENT.swig(),
            AlertType.TORRENT_ERROR.swig(),
          )

        override fun alert(alert: Alert<*>) {
          val errorCode =
            when (alert) {
              is AddTorrentAlert -> alert.error().takeIf { it.isError }
              is TorrentErrorAlert -> alert.error().takeIf { it.isError }
              else -> null
            } ?: return
          Log.w(TAG, "Native torrent operation failed (code ${errorCode.value})")
          failure.compareAndSet(null, streamError("The torrent engine couldn't add this torrent."))
        }
      }
    session.addListener(listener)
    return try {
      block(failure)
    } finally {
      session.removeListener(listener)
    }
  }

  private suspend fun waitForHandle(
    session: SessionManager,
    hash: Sha1Hash,
    startGeneration: Long,
    failure: AtomicReference<TorrentException?>,
  ): TorrentHandle {
    val deadline = System.currentTimeMillis() + METADATA_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
      ensureCurrent(startGeneration)
      failure.get()?.let { throw it }
      session.find(hash)?.takeIf { it.isValid }?.let { return it }
      delay(100L)
    }
    throw streamError("Timed out while adding the torrent.")
  }

  private suspend fun waitForMetadata(
    handle: TorrentHandle,
    startGeneration: Long,
    failure: AtomicReference<TorrentException?>,
  ): TorrentInfo {
    val deadline = System.currentTimeMillis() + METADATA_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
      ensureCurrent(startGeneration)
      failure.get()?.let { throw it }
      if (!handle.isValid) throw streamError("Torrent session stopped before metadata was received.")
      handle.torrentFile()?.takeIf { it.isValid }?.let { return it }
      runCatching {
        val status = handle.status()
        _phase.value =
          if (status.numPeers() > 0) "Downloading torrent metadata..." else "Searching for torrent peers..."
      }
      delay(250L)
    }
    throw streamError("Timed out while fetching torrent metadata. Check that the torrent has active peers.")
  }

  private fun validateAndEnumerateFiles(
    info: TorrentInfo,
    cacheDir: File,
  ): List<TorrentFileItem> {
    val storage = info.files()
    val count = storage.numFiles()
    if (count <= 0) throw streamError("Torrent contains no files.")
    if (count > MAX_TORRENT_FILES) throw streamError("Torrent contains too many files.")
    if (info.pieceLength() <= 0) throw streamError("Torrent metadata has an invalid piece size.")

    return buildList(count) {
      for (index in 0 until count) {
        val path = storage.filePath(index)
        validateMetadataPath(path, storage.fileAbsolutePath(index), cacheDir)
        val size = storage.fileSize(index)
        val offset = storage.fileOffset(index)
        if (size < 0L || offset < 0L || (size > 0L && offset > Long.MAX_VALUE - size)) {
          throw streamError("Torrent metadata contains an invalid file size.")
        }
        val mimeType =
          if (storage.padFileAt(index) || size <= 0L) {
            ""
          } else {
            playableMimeTypes[path.substringAfterLast('.', "").lowercase()].orEmpty()
          }
        add(
          TorrentFileItem(
            index = index,
            path = path,
            name = path.substringAfterLast('/').substringAfterLast('\\'),
            size = size,
            mimeType = mimeType,
          ),
        )
      }
    }
  }

  private fun readMetadata(source: String): ByteArray {
    val uri = Uri.parse(source)
    val input =
      when (uri.scheme?.lowercase()) {
        "content" -> appContext.contentResolver.openInputStream(uri)
          ?: throw streamError("Couldn't open the selected torrent metadata.")
        "file" -> {
          val path = uri.path ?: throw streamError("Torrent metadata path is invalid.")
          FileInputStream(File(path))
        }
        "http", "https" -> return readRemoteMetadata(source)
        else -> throw streamError("Only magnet links and content, file, HTTP or HTTPS torrent metadata are supported.")
      }
    return input.use { readBounded(it) }
  }

  private fun readRemoteMetadata(source: String): ByteArray {
    val request = runCatching { Request.Builder().url(source).get().build() }.getOrElse {
      throw streamError("Torrent metadata URL is invalid.")
    }
    return try {
      httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw streamError("Couldn't download torrent metadata (HTTP ${response.code}).")
        val body = response.body ?: throw streamError("Couldn't download torrent metadata.")
        if (body.contentLength() > MAX_METADATA_BYTES) throw streamError("Torrent metadata is too large.")
        body.byteStream().use { readBounded(it) }
      }
    } catch (error: TorrentException) {
      throw error
    } catch (error: IOException) {
      throw streamError("Couldn't download torrent metadata.", error)
    }
  }

  private fun readBounded(input: InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0L
    while (true) {
      val read = input.read(buffer)
      if (read < 0) break
      total += read
      if (total > MAX_METADATA_BYTES) throw streamError("Torrent metadata is too large.")
      output.write(buffer, 0, read)
    }
    if (total == 0L) throw streamError("Torrent metadata is empty.")
    return output.toByteArray()
  }

  private fun validateMetadataPath(
    path: String,
    absolute: Boolean,
    cacheDir: File,
  ) {
    if (
      path.isBlank() || path.length > 4096 || absolute || File(path).isAbsolute ||
      path.any { it == '\u0000' || it.isISOControl() } ||
      path.replace('\\', '/').split('/').any { it.isEmpty() || it == "." || it == ".." }
    ) {
      throw streamError("Torrent metadata contains an unsafe file path.")
    }
    safeCacheFile(cacheDir, path)
  }

  private fun safeCacheFile(
    cacheDir: File,
    path: String,
  ): File {
    val root = cacheDir.canonicalFile
    val candidate = File(root, path).canonicalFile
    val prefix = root.path + File.separator
    if (!candidate.path.startsWith(prefix)) throw streamError("Torrent metadata contains an unsafe file path.")
    return candidate
  }

  private fun cleanupPrepared() {
    val preparation = prepared ?: return
    prepared = null
    cleanup(preparation.session, preparation.handle, preparation.cacheDir)
  }

  private fun cleanup(
    session: SessionManager,
    handle: TorrentHandle?,
    cacheDir: File,
  ) {
    if (handle?.isValid == true) {
      runCatching { session.remove(handle, SessionHandle.DELETE_FILES) }
    }
    runCatching { session.stop() }
    if (cacheDir.exists() && !cacheDir.deleteRecursively()) {
      Log.w(TAG, "Torrent cache cleanup did not remove every file")
    }
    cacheDir.parentFile?.takeIf { it.isDirectory && it.list().isNullOrEmpty() }?.delete()
  }

  private fun cleanupAfterPreparationFailure(
    session: SessionManager?,
    handle: TorrentHandle?,
    cacheDir: File,
  ) {
    if (session != null) {
      cleanup(session, handle, cacheDir)
      return
    }
    if (cacheDir.exists() && !cacheDir.deleteRecursively()) {
      Log.w(TAG, "Torrent cache cleanup did not remove every file")
    }
  }

  private fun ensureCurrent(expected: Long) {
    if (closed || generation.get() != expected) throw CancellationException("Torrent request superseded")
  }

  private fun safeDisplayName(
    value: String?,
    fallback: String,
  ): String =
    value
      ?.trim()
      ?.filterNot { it.isISOControl() }
      ?.take(240)
      ?.takeIf(String::isNotBlank)
      ?: fallback

  private fun isMetadataUri(source: String): Boolean =
    Uri.parse(source).scheme?.lowercase() in setOf("content", "file", "http", "https")

  private fun streamError(
    message: String,
    cause: Throwable? = null,
  ) = TorrentException(message, cause)
}
