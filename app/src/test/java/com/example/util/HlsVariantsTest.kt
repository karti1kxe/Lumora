package com.example.util

import com.example.util.media.HlsVariants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsVariantsTest {
    private val master = """#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=750000,RESOLUTION=842x480,CODECS="avc1.4D401E,mp4a.40.2"
480/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=4100000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2",FRAME-RATE=23.976
https://cdn.example/1080/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1280x720,CODECS="avc1.4D401F,mp4a.40.2"

720/index.m3u8
"""

    @Test
    fun masterPlaylistGivesTheQualityListBestFirst() {
        val v = HlsVariants.parseMaster(master, "https://a.example/c/master.m3u8")
        assertEquals(listOf("1080p", "720p", "480p"), v.map { it.label })
        assertEquals(listOf("HD", "HD", "SD"), v.map { it.badge })
        assertEquals("4.1 Mbps · 1920x1080 · avc1.640028", v[0].info)
        assertEquals("2.0 Mbps · 1280x720 · avc1.4D401F", v[1].info)
        assertEquals("750 kbps · 842x480 · avc1.4D401E", v[2].info)
        assertEquals("https://cdn.example/1080/index.m3u8", v[0].uri)
        assertEquals("https://a.example/c/720/index.m3u8", v[1].uri)
    }

    @Test
    fun mediaPlaylistIsNotAQualityList() {
        assertTrue(HlsVariants.parseMaster("#EXTM3U\n#EXTINF:4,\nseg.ts", "https://a.example/b.m3u8").isEmpty())
    }

    @Test
    fun headerValuesWithCommasKeepTheirCommas() {
        assertEquals("Accept: a/b", StreamRequestOptions.mpvListItem("Accept: a/b"))
        assertEquals("%24%Accept: text/html,image/png", StreamRequestOptions.mpvListItem("Accept: text/html,image/png"))
    }
}
