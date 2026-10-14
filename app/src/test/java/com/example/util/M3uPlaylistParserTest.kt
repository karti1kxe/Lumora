package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uPlaylistParserTest {

    private val sample = """
        #EXTM3U
        #EXTINF:-1 tvg-id="BBCOne.uk" tvg-logo="https://example.com/logos/bbc one.png" group-title="UK, News",BBC One HD
        https://example.com/live/bbc1.m3u8
        #EXTINF:-1 tvg-name="Ch Two" group-title="Movies",
        #EXTVLCOPT:http-user-agent=VLC/3.0
        #EXTVLCOPT:http-referrer=https://ref.example/
        http://example.com/two.ts
        #EXTINF:-1,No Scheme
        relative/line.ts
        #EXTINF:-1,Kodi Style
        https://example.com/k.m3u8|User-Agent=Foo%20Bar&Referer=https://r.example/
    """.trimIndent()

    @Test
    fun parsesNamesLogosGroupsAndUrls() {
        val r = M3uPlaylistParser.parse(sample)
        assertEquals(3, r.entries.size)
        val bbc = r.entries[0]
        assertEquals("BBC One HD", bbc.name)
        assertEquals("https://example.com/live/bbc1.m3u8", bbc.url)
        assertEquals("https://example.com/logos/bbc one.png", bbc.logo)
        assertEquals("UK, News", bbc.group) // comma inside quotes must not end the attributes
        assertEquals("BBCOne.uk", bbc.tvgId)
    }

    @Test
    fun readsPerStreamHeadersAndFallsBackToTvgName() {
        val two = M3uPlaylistParser.parse(sample).entries[1]
        assertEquals("Ch Two", two.name)
        assertEquals("VLC/3.0", two.userAgent)
        assertEquals("https://ref.example/", two.referrer)
        assertEquals("Movies", two.group)
    }

    @Test
    fun readsKodiPipeHeadersAndSkipsRelativeLinesWithoutBase() {
        val r = M3uPlaylistParser.parse(sample)
        assertEquals(1, r.skippedLines)
        val kodi = r.entries[2]
        assertEquals("https://example.com/k.m3u8", kodi.url)
        assertEquals("Foo Bar", kodi.userAgent)
        assertEquals("https://r.example/", kodi.referrer)
    }

    @Test
    fun resolvesRelativeLinesAgainstBaseUrl() {
        val r = M3uPlaylistParser.parse("#EXTM3U\n#EXTINF:-1,Rel\nstreams/a.ts\n", "https://host.example/lists/pl.m3u")
        assertEquals("https://host.example/lists/streams/a.ts", r.entries.single().url)
    }

    @Test
    fun detectsPlaylistsAndHlsManifests() {
        assertTrue(M3uPlaylistParser.looksLikePlaylist("\uFEFF#EXTM3U\n"))
        assertFalse(M3uPlaylistParser.looksLikePlaylist("<html>not a playlist</html>"))
        assertTrue(M3uPlaylistParser.isHlsStreamManifest("#EXTM3U\n#EXT-X-TARGETDURATION:6\n"))
        assertFalse(M3uPlaylistParser.isHlsStreamManifest(sample))
    }

    @Test
    fun emptyOrMalformedContentGivesNoEntries() {
        assertTrue(M3uPlaylistParser.parse("").entries.isEmpty())
        assertTrue(M3uPlaylistParser.parse("#EXTM3U\n#EXTINF:-1,Only a title\n").entries.isEmpty())
        assertNull(M3uPlaylistParser.parse("#EXTM3U\n").title)
    }
}
