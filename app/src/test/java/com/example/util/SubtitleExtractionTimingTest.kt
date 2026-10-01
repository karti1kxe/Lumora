package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleExtractionTimingTest {

    @Test
    fun testAssDialogueOriginalTimingPreserved() {
        val cueText = "Dialogue: 0,0:00:04.86,0:00:05.33,Default,,0,0,0,,{\\k47}First"
        val startMs = MkvChapterInfo.parseTimeStringToMs("0:00:04.86")
        val endMs = MkvChapterInfo.parseTimeStringToMs("0:00:05.33")

        assertEquals(4860L, startMs)
        assertEquals(5330L, endMs)

        val duration = endMs - startMs
        assertEquals(470L, duration) // Exactly 470ms, never 3000ms

        val cue = MkvManagerEngine.ExtractedCue(startMs, endMs, cueText)
        val resolved = MkvManagerEngine.resolveCueDurations(listOf(cue))

        assertEquals(1, resolved.size)
        assertEquals(4860L, resolved[0].startTimeMs)
        assertEquals(5330L, resolved[0].endTimeMs)
    }

    @Test
    fun testChainedAnimationEventsResolvedToNextStart() {
        // When blocks don't contain explicit duration, consecutive animation frames
        // should resolve duration up to the next frame rather than defaulting to 3000ms.
        val cue1 = MkvManagerEngine.ExtractedCue(4860L, -1L, "Dialogue: 0,Default,,0,0,0,,{\\pos(100,200)}Frame 1")
        val cue2 = MkvManagerEngine.ExtractedCue(5330L, -1L, "Dialogue: 0,Default,,0,0,0,,{\\pos(110,200)}Frame 2")
        val cue3 = MkvManagerEngine.ExtractedCue(5760L, -1L, "Dialogue: 0,Default,,0,0,0,,{\\pos(120,200)}Frame 3")

        val resolved = MkvManagerEngine.resolveCueDurations(listOf(cue1, cue2, cue3))

        assertEquals(3, resolved.size)
        // Frame 1 lasts until Frame 2 starts (470ms, not 3000ms)
        assertEquals(4860L, resolved[0].startTimeMs)
        assertEquals(5330L, resolved[0].endTimeMs)

        // Frame 2 lasts until Frame 3 starts (430ms, not 3000ms)
        assertEquals(5330L, resolved[1].startTimeMs)
        assertEquals(5760L, resolved[1].endTimeMs)

        // Frame 3 has no successor, ends shortly after based on text length
        assertEquals(5760L, resolved[2].startTimeMs)
        assertTrue(resolved[2].endTimeMs > resolved[2].startTimeMs)
    }

    @Test
    fun testExplicitBlockDurationPreserved() {
        val cue = MkvManagerEngine.ExtractedCue(1000L, 1470L, "Dialogue: 0,Default,,0,0,0,,Short Flash")
        val resolved = MkvManagerEngine.resolveCueDurations(listOf(cue))

        assertEquals(1, resolved.size)
        assertEquals(1000L, resolved[0].startTimeMs)
        assertEquals(1470L, resolved[0].endTimeMs)
    }

    @Test
    fun testFormatAssTimeConversion() {
        assertEquals("0:00:04.86", MkvManagerEngine.formatAssTime(4860L))
        assertEquals("0:00:05.33", MkvManagerEngine.formatAssTime(5330L))
        assertEquals("1:23:45.67", MkvManagerEngine.formatAssTime(5025670L))
    }
}
