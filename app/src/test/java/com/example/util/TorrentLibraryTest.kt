package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentLibraryTest {
    @Test
    fun parsePathSplitsHashAndIndex() {
        assertEquals("abc123" to 7, TorrentLibrary.parsePath("torrent://abc123/7"))
        assertEquals("abc123" to 0, TorrentLibrary.pathFor("abc123", 0).let { TorrentLibrary.parsePath(it) })
    }

    @Test
    fun parsePathRejectsOtherPaths() {
        assertNull(TorrentLibrary.parsePath("/storage/emulated/0/a.mp4"))
        assertNull(TorrentLibrary.parsePath("torrent://abc123/x"))
        assertNull(TorrentLibrary.parsePath("torrent:///3"))
    }

    @Test
    fun videoNamesAreDetectedByExtension() {
        assertTrue(TorrentLibrary.isVideoName("Show/Episode 01.MKV"))
        assertTrue(TorrentLibrary.isVideoName("movie.mp4"))
        assertFalse(TorrentLibrary.isVideoName("readme.nfo"))
        assertFalse(TorrentLibrary.isVideoName("subs.srt"))
    }

    @Test
    fun magnetInfoHashIsReadFromBtihLinks() {
        val h = "c8b5ec93ace754eb0f7461a4fcf38c51969ed91e"
        assertEquals(h, TorrentStreamEngine.magnetInfoHash("magnet:?xt=urn:btih:${h.uppercase()}&dn=x"))
        assertNull(TorrentStreamEngine.magnetInfoHash("magnet:?dn=nohash"))
    }
}
