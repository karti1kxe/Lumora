package com.example.ui.screens

import com.example.util.MkvTrackInfo
import com.example.util.MkvTrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MediaExtractionNamingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createTrack(
        id: Int,
        trackNumber: Long,
        type: MkvTrackType,
        codecId: String,
        codecName: String = "",
        language: String = "",
        name: String = ""
    ): MkvTrackInfo {
        return MkvTrackInfo(
            id = id,
            trackNumber = trackNumber,
            trackType = type,
            codecId = codecId,
            codecName = codecName,
            language = language,
            name = name
        )
    }

    @Test
    fun testAudioExtensionDetection() {
        val aacTrack = createTrack(1, 1, MkvTrackType.AUDIO, "A_AAC", "AAC")
        assertEquals("m4a", audioExtension(aacTrack))

        val opusTrack = createTrack(2, 2, MkvTrackType.AUDIO, "A_OPUS", "Opus")
        assertEquals("opus", audioExtension(opusTrack))

        val ac3Track = createTrack(3, 3, MkvTrackType.AUDIO, "A_AC3", "AC3")
        assertEquals("ac3", audioExtension(ac3Track))

        val eac3Track = createTrack(4, 4, MkvTrackType.AUDIO, "A_EAC3", "E-AC3")
        assertEquals("ac3", audioExtension(eac3Track))

        val mp3Track = createTrack(5, 5, MkvTrackType.AUDIO, "A_MPEG/L3", "MP3")
        assertEquals("mp3", audioExtension(mp3Track))

        val flacTrack = createTrack(6, 6, MkvTrackType.AUDIO, "A_FLAC", "FLAC")
        assertEquals("flac", audioExtension(flacTrack))
    }

    @Test
    fun testSubtitleExtensionDetection() {
        val srtTrack = createTrack(1, 1, MkvTrackType.SUBTITLE, "S_TEXT/UTF8", "SubRip")
        assertEquals("srt", subtitleExtension(srtTrack))

        val assTrack = createTrack(2, 2, MkvTrackType.SUBTITLE, "S_TEXT/ASS", "SubStation Alpha")
        assertEquals("ass", subtitleExtension(assTrack))

        val vttTrack = createTrack(3, 3, MkvTrackType.SUBTITLE, "S_TEXT/WEBVTT", "WebVTT")
        assertEquals("vtt", subtitleExtension(vttTrack))

        val pgsTrack = createTrack(4, 4, MkvTrackType.SUBTITLE, "S_HDMV/PGS", "PGS")
        assertEquals("sup", subtitleExtension(pgsTrack))
    }

    @Test
    fun testLanguageNameResolution() {
        val hindiTrack = createTrack(1, 1, MkvTrackType.AUDIO, "A_AAC", language = "hin")
        assertEquals("hindi", resolveTrackLanguageName(hindiTrack, "audio"))

        val englishTrack = createTrack(2, 2, MkvTrackType.AUDIO, "A_OPUS", language = "eng")
        assertEquals("english", resolveTrackLanguageName(englishTrack, "audio"))

        val tamilTrack = createTrack(3, 3, MkvTrackType.AUDIO, "A_AAC", language = "ta")
        assertEquals("tamil", resolveTrackLanguageName(tamilTrack, "audio"))

        val namedTrack = createTrack(4, 4, MkvTrackType.AUDIO, "A_AAC", language = "", name = "Hindi Commentary")
        assertEquals("hindi", resolveTrackLanguageName(namedTrack, "audio"))
    }

    @Test
    fun testOutputFileNameRules() {
        val hindiAudio = createTrack(1, 1, MkvTrackType.AUDIO, "A_AAC", language = "hin")
        val englishAudio = createTrack(2, 2, MkvTrackType.AUDIO, "A_OPUS", language = "eng")
        val audioTracks = listOf(hindiAudio, englishAudio)

        val hindiName = buildTrackOutputFileName("Movie.mkv", hindiAudio, audioTracks, "m4a", "audio")
        assertEquals("Movie-hindi.m4a", hindiName)

        val englishName = buildTrackOutputFileName("Movie.mkv", englishAudio, audioTracks, "opus", "audio")
        assertEquals("Movie-english.opus", englishName)

        val hindiSub = createTrack(3, 1, MkvTrackType.SUBTITLE, "S_TEXT/UTF8", language = "hin")
        val englishSub = createTrack(4, 2, MkvTrackType.SUBTITLE, "S_TEXT/ASS", language = "eng")
        val subTracks = listOf(hindiSub, englishSub)

        val hindiSubName = buildTrackOutputFileName("Movie.mkv", hindiSub, subTracks, "srt", "subtitle")
        assertEquals("Movie-hindi.srt", hindiSubName)

        val englishSubName = buildTrackOutputFileName("Movie.mkv", englishSub, subTracks, "ass", "subtitle")
        assertEquals("Movie-english.ass", englishSubName)
    }

    @Test
    fun testDuplicateLanguageNumberedSuffix() {
        val hindi1 = createTrack(1, 1, MkvTrackType.AUDIO, "A_AAC", language = "hin")
        val hindi2 = createTrack(2, 2, MkvTrackType.AUDIO, "A_AAC", language = "hin")
        val hindi3 = createTrack(3, 3, MkvTrackType.AUDIO, "A_OPUS", language = "hin")
        val tracks = listOf(hindi1, hindi2, hindi3)

        assertEquals("Movie-hindi.m4a", buildTrackOutputFileName("Movie.mkv", hindi1, tracks, "m4a", "audio"))
        assertEquals("Movie-hindi2.m4a", buildTrackOutputFileName("Movie.mkv", hindi2, tracks, "m4a", "audio"))
        assertEquals("Movie-hindi3.opus", buildTrackOutputFileName("Movie.mkv", hindi3, tracks, "opus", "audio"))
    }

    @Test
    fun testPreservesOriginalVideoNameCaseSpacesAndSpecialChars() {
        val complexVideoName = "[SubGroup] Action Movie (2024) [1080p, 10-bit].mkv"
        val hindiAudio = createTrack(1, 1, MkvTrackType.AUDIO, "A_AAC", language = "hin")
        val fileName = buildTrackOutputFileName(complexVideoName, hindiAudio, listOf(hindiAudio), "m4a", "audio")

        assertEquals("[SubGroup] Action Movie (2024) [1080p, 10-bit]-hindi.m4a", fileName)
    }

    @Test
    fun testUniqueDestinationFilePreventsOverwriting() {
        val dir = tempFolder.newFolder("extracted")

        // First extraction: target file doesn't exist yet
        val file1 = getUniqueDestinationFile(dir, "Movie-hindi.m4a")
        assertEquals("Movie-hindi.m4a", file1.name)
        assertFalse(file1.exists())
        file1.writeText("audio track 1")

        // Second extraction of same name: should not overwrite, should produce Movie-hindi2.m4a
        val file2 = getUniqueDestinationFile(dir, "Movie-hindi.m4a")
        assertEquals("Movie-hindi2.m4a", file2.name)
        assertFalse(file2.exists())
        file2.writeText("audio track 2")

        // Third extraction of same name: should produce Movie-hindi3.m4a
        val file3 = getUniqueDestinationFile(dir, "Movie-hindi.m4a")
        assertEquals("Movie-hindi3.m4a", file3.name)
        assertFalse(file3.exists())
        file3.writeText("audio track 3")

        // Subtitle check
        val sub1 = getUniqueDestinationFile(dir, "Movie-english.srt")
        assertEquals("Movie-english.srt", sub1.name)
        sub1.writeText("sub 1")

        val sub2 = getUniqueDestinationFile(dir, "Movie-english.srt")
        assertEquals("Movie-english2.srt", sub2.name)
        sub2.writeText("sub 2")

        // Verify original files are untouched
        assertEquals("audio track 1", file1.readText())
        assertEquals("audio track 2", file2.readText())
        assertEquals("audio track 3", file3.readText())
        assertEquals("sub 1", sub1.readText())
        assertEquals("sub 2", sub2.readText())
    }
}
