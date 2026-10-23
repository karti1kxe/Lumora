/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Lumora addition: network access for installing / updating yt-dlp that survives a broken phone DNS
 * ("Unable to resolve host github.com"): when the system resolver fails the name is looked up over HTTPS
 * (DNS-over-HTTPS on 1.1.1.1 / 8.8.8.8, reached by IP, so no resolver is needed), and when github.com itself is
 * unreachable the same release is taken from PyPI (a different host) and repacked as a runnable yt-dlp zip.
 */

package com.example.ytdlp

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

internal object YtdlpFetch {
  private val resolved = ConcurrentHashMap<String, List<InetAddress>>()

  private val bootstrap: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(5, TimeUnit.SECONDS)
      .readTimeout(5, TimeUnit.SECONDS)
      .callTimeout(8, TimeUnit.SECONDS)
      .build()
  }

  /** The name over HTTPS (JSON DNS API reached by IP address); empty when none of the resolvers answers. */
  private fun dohLookup(host: String): List<InetAddress> {
    for (endpoint in listOf("https://1.1.1.1/dns-query", "https://8.8.8.8/resolve", "https://1.0.0.1/dns-query")) {
      try {
        val request =
          Request.Builder().url("$endpoint?name=$host&type=A").header("accept", "application/dns-json").build()
        bootstrap.newCall(request).execute().use { response ->
          if (!response.isSuccessful) return@use
          val answers = JSONObject(response.body?.string().orEmpty()).optJSONArray("Answer") ?: return@use
          val found = ArrayList<InetAddress>()
          for (i in 0 until answers.length()) {
            val answer = answers.optJSONObject(i) ?: continue
            if (answer.optInt("type") != 1) continue
            // an IP literal: no resolver is involved
            runCatching { InetAddress.getByName(answer.optString("data")) }.getOrNull()?.let(found::add)
          }
          if (found.isNotEmpty()) return found
        }
      } catch (_: Exception) {
      }
    }
    return emptyList()
  }

  private val dns =
    object : Dns {
      override fun lookup(hostname: String): List<InetAddress> =
        try {
          Dns.SYSTEM.lookup(hostname).also { resolved[hostname] = it }
        } catch (e: UnknownHostException) {
          resolved[hostname] ?: dohLookup(hostname).takeIf { it.isNotEmpty() }?.also { resolved[hostname] = it } ?: throw e
        }
    }

  val client: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .dns(dns)
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(20, TimeUnit.SECONDS)
      .followRedirects(true)
      .followSslRedirects(true)
      .build()
  }

  private val noRedirectClient: OkHttpClient by lazy {
    client.newBuilder().followRedirects(false).followSslRedirects(false).build()
  }

  /** Streams [url] into [dest]. */
  fun download(url: String, dest: File) {
    val request = Request.Builder().url(url).header("User-Agent", "Lumora").header("Accept-Encoding", "identity").build()
    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
      val body = response.body ?: throw IOException("empty response")
      body.byteStream().use { input -> FileOutputStream(dest).use { output -> input.copyTo(output, 64 * 1024) } }
    }
  }

  fun text(url: String): String {
    val request = Request.Builder().url(url).header("User-Agent", "Lumora").build()
    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
      return response.body?.string().orEmpty()
    }
  }

  /** Tag of the newest release: the /releases/latest redirect (one tiny request). */
  fun latestTag(releaseUrl: String): String? =
    runCatching {
      val request = Request.Builder().url(releaseUrl).head().header("User-Agent", "Lumora").build()
      noRedirectClient.newCall(request).execute().use { response ->
        response.header("Location")?.substringAfterLast("/tag/", "")?.trim()?.takeIf { it.isNotEmpty() }
      }
    }.getOrNull()

  /**
   * The newest yt-dlp from PyPI (pypi.org / files.pythonhosted.org): its wheel holds the same yt_dlp package, repacked
   * here into the runnable zip layout (package + a root __main__.py) that the app's yt-dlp file has.
   */
  fun downloadFromPypi(dest: File) {
    val meta = JSONObject(text("https://pypi.org/pypi/yt-dlp/json"))
    val files = meta.optJSONArray("urls") ?: throw IOException("PyPI lists no files")
    var wheelUrl: String? = null
    for (i in 0 until files.length()) {
      val file = files.optJSONObject(i) ?: continue
      if (file.optString("packagetype") == "bdist_wheel") {
        wheelUrl = file.optString("url")
        break
      }
    }
    if (wheelUrl.isNullOrBlank()) throw IOException("PyPI lists no wheel")
    val wheel = File(dest.parentFile, dest.name + ".whl")
    try {
      download(wheelUrl, wheel)
      ZipOutputStream(FileOutputStream(dest)).use { out ->
        ZipFile(wheel).use { zip ->
          val entries = zip.entries()
          while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory || !entry.name.startsWith("yt_dlp/")) continue
            out.putNextEntry(ZipEntry(entry.name))
            zip.getInputStream(entry).use { it.copyTo(out, 64 * 1024) }
            out.closeEntry()
          }
        }
        out.putNextEntry(ZipEntry("__main__.py"))
        out.write("import sys\nimport yt_dlp\n\nif __name__ == '__main__':\n    yt_dlp.main()\n".toByteArray())
        out.closeEntry()
      }
    } finally {
      wheel.delete()
    }
  }
}
