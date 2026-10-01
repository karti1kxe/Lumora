package com.example.player

import androidx.compose.ui.graphics.Color
import com.example.ui.state.PlayerSettings

enum class SkipMarkerType(
    val defaultPrimaryLabel: String,
    val color: Color,
    val isOpening: Boolean = false,
    val isEnding: Boolean = false
) {
    OPENING("Skip Opening", Color(0xFFF59E0B), isOpening = true),
    ENDING("Skip Ending", Color(0xFF38BDF8), isEnding = true),
    PREVIEW("Skip Preview", Color(0xFF60A5FA), isEnding = true),
    RECAP("Skip Recap", Color(0xFFFBBF24)),
    PROLOGUE("Skip Intro", Color(0xFFF59E0B)),
    POST_CREDITS("Skip Post-Credits", Color(0xFF38BDF8))
}

data class ChapterSkipMarker(
    val chapterIndex: Int,
    val originalTitle: String,
    val displayButtonTitle: String,
    val secondaryTitle: String,
    val type: SkipMarkerType,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val color: Color = type.color
)

object ChapterSkipDetector {

    private val DEFAULT_OPENING_KEYWORDS = listOf(
        "opening", "op", "intro", "main title", "theme song", "ncop", "opening theme", "title sequence"
    )

    private val DEFAULT_ENDING_KEYWORDS = listOf(
        "ending", "ed", "outro", "credits", "credit", "end credits", "closing", "ending theme", "nced", "closing credits", "fin"
    )

    private val PREVIEW_KEYWORDS = listOf(
        "preview", "next episode preview", "next episode", "next preview", "next ep preview",
        "next ep", "teaser", "jikai", "next time on", "coming next"
    )

    private val RECAP_KEYWORDS = listOf(
        "recap", "previously", "previously on", "story so far", "summary"
    )

    private val PROLOGUE_KEYWORDS = listOf(
        "prologue", "cold open", "pre-opening"
    )

    private val POST_CREDITS_KEYWORDS = listOf(
        "post-credits", "post credits", "post credit", "after credits", "epilogue", "omake", "extra"
    )

    private fun matchesKeyword(title: String, keyword: String): Boolean {
        val kw = keyword.trim()
        if (kw.isBlank()) return false
        return if (kw.length <= 2) {
            // Strict token boundary check for short acronyms like "OP", "ED"
            Regex("(?i)(^|[^a-zA-Z0-9])${Regex.escape(kw)}([^a-zA-Z0-9]|\\d|$)").containsMatchIn(title)
        } else {
            title.contains(kw, ignoreCase = true)
        }
    }

    private fun matchesAny(title: String, keywords: List<String>): Boolean {
        return keywords.any { kw -> matchesKeyword(title, kw) }
    }

    /**
     * Accurately detects skippable chapter ranges from real embedded chapter metadata.
     * If detectChapterIntroOutro is disabled or chapter titles do not match configured keywords,
     * no skip marker is created.
     */
    fun detectSkipMarkers(
        chapters: List<PlayerVideoChapter>,
        durationMs: Long,
        settings: PlayerSettings
    ): List<ChapterSkipMarker> {
        if (!settings.detectChapterIntroOutro || chapters.isEmpty() || durationMs <= 0L) {
            return emptyList()
        }

        val sorted = chapters.sortedBy { it.timeMs }
        val userOpeningKeywords = settings.customOpeningKeywords
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val allOpeningKeywords = (userOpeningKeywords + DEFAULT_OPENING_KEYWORDS).distinct()

        val userEndingKeywords = settings.customEndingKeywords
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val allEndingKeywords = (userEndingKeywords + DEFAULT_ENDING_KEYWORDS).distinct()

        val markers = mutableListOf<ChapterSkipMarker>()

        for (i in sorted.indices) {
            val chapter = sorted[i]
            val startMs = chapter.timeMs
            val endMs = if (i + 1 < sorted.size) {
                sorted[i + 1].timeMs
            } else {
                durationMs
            }

            if (endMs <= startMs || startMs >= durationMs) continue

            val title = chapter.title.trim()
            if (title.isBlank()) continue

            // Determine matching type and single-line button label
            val (type, buttonLabel) = when {
                matchesAny(title, allOpeningKeywords) -> {
                    val label = when {
                        title.startsWith("Skip ", ignoreCase = true) -> title
                        title.contains("opening song", ignoreCase = true) -> "Skip Opening Song"
                        title.contains("opening theme", ignoreCase = true) -> "Skip Opening Song"
                        title.contains("intro", ignoreCase = true) -> "Skip Intro"
                        else -> "Skip Opening"
                    }
                    SkipMarkerType.OPENING to label
                }
                matchesAny(title, allEndingKeywords) -> {
                    val label = when {
                        title.startsWith("Skip ", ignoreCase = true) -> title
                        title.contains("credit", ignoreCase = true) -> "Skip Credits"
                        title.contains("outro", ignoreCase = true) -> "Skip Outro"
                        title.contains("ending song", ignoreCase = true) -> "Skip Ending Song"
                        title.contains("ending theme", ignoreCase = true) -> "Skip Ending Song"
                        else -> "Skip Ending"
                    }
                    SkipMarkerType.ENDING to label
                }
                matchesAny(title, PREVIEW_KEYWORDS) -> {
                    val label = when {
                        title.startsWith("Skip ", ignoreCase = true) -> title
                        title.contains("next episode preview", ignoreCase = true) -> "Skip Next Episode Preview"
                        title.contains("next ep preview", ignoreCase = true) -> "Skip Next Episode Preview"
                        title.contains("episode preview", ignoreCase = true) -> "Skip Next Episode Preview"
                        else -> "Skip Preview"
                    }
                    SkipMarkerType.PREVIEW to label
                }
                matchesAny(title, RECAP_KEYWORDS) -> {
                    val label = if (title.startsWith("Skip ", ignoreCase = true)) title else "Skip Recap"
                    SkipMarkerType.RECAP to label
                }
                matchesAny(title, PROLOGUE_KEYWORDS) -> {
                    val label = if (title.startsWith("Skip ", ignoreCase = true)) title else "Skip Intro"
                    SkipMarkerType.PROLOGUE to label
                }
                matchesAny(title, POST_CREDITS_KEYWORDS) -> {
                    val label = if (title.startsWith("Skip ", ignoreCase = true)) title else "Skip Post-Credits"
                    SkipMarkerType.POST_CREDITS to label
                }
                else -> null
            } ?: continue

            markers.add(
                ChapterSkipMarker(
                    chapterIndex = chapter.index,
                    originalTitle = title,
                    displayButtonTitle = buttonLabel,
                    secondaryTitle = "",
                    type = type,
                    startTimeMs = startMs,
                    endTimeMs = endMs,
                    color = type.color
                )
            )
        }

        return markers
    }

    /**
     * Resolves the active skip marker currently encompassing the playback position.
     */
    fun getActiveMarker(
        markers: List<ChapterSkipMarker>,
        currentPositionMs: Long
    ): ChapterSkipMarker? {
        return markers.firstOrNull { currentPositionMs >= it.startTimeMs && currentPositionMs < it.endTimeMs }
    }
}
