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
        val text = "Accept: text/html,image/png"
        assertEquals("%${text.toByteArray().size}%$text", StreamRequestOptions.mpvListItem(text))
    }

    @Test
    fun masterPlaylistListsItsAudioAndSubtitleRenditions() {
        val text = """#EXTM3U
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="Hindi",LANGUAGE="hi",DEFAULT=YES,URI="audio/hi.m3u8"
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="English",LANGUAGE="en",DEFAULT=NO,URI="https://cdn.example/audio/en.m3u8"
#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="English",LANGUAGE="en",URI="subs/en.m3u8"
#EXT-X-MEDIA:TYPE=CLOSED-CAPTIONS,GROUP-ID="cc",NAME="CC1",INSTREAM-ID="CC1"
#EXT-X-STREAM-INF:BANDWIDTH=2000000,AUDIO="aud",SUBTITLES="subs"
720/index.m3u8
"""
        val r = HlsVariants.parseRenditions(text, "https://a.example/c/master.m3u8")
        assertEquals(listOf("Hindi", "English", "English"), r.map { it.name })
        assertEquals(listOf("AUDIO", "AUDIO", "SUBTITLES"), r.map { it.type })
        assertEquals("https://a.example/c/audio/hi.m3u8", r[0].uri)
        assertEquals("https://cdn.example/audio/en.m3u8", r[1].uri)
        assertEquals("https://a.example/c/subs/en.m3u8", r[2].uri)
        assertTrue(r[0].isDefault)
    }

    @Test
    fun mediaPlaylistGivesItsLengthAndSegments() {
        val text = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6.0,\nseg0.ts\n#EXTINF:4.5,\nhttps://cdn.example/seg1.ts\n#EXT-X-ENDLIST\n"
        val p = HlsVariants.parseMediaPlaylist(text, "https://a.example/c/index.m3u8")!!
        assertEquals(10.5, p.totalSeconds, 0.001)
        assertEquals("https://a.example/c/seg0.ts", p.segments[0])
        assertEquals("https://cdn.example/seg1.ts", p.segments[1])
        assertTrue(p.isVod)
        assertTrue(HlsVariants.parseMediaPlaylist(master, "https://a.example/m.m3u8") == null)
    }
}
