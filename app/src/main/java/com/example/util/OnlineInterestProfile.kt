package com.example.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * What the user is into, learned locally from what they open / play and what they search (Online Video and Online Audio
 * each keep their own profile, see [KIND_VIDEO] / [KIND_AUDIO]).
 *
 * A "topic" is a short label such as "minecraft" or "iphone review". Known topics (games, tech, cricket, ...) carry a
 * category and a list of related topics (Minecraft -> Terraria, Roblox, ...), unknown ones fall back to the first
 * significant words of a title. The recommenders ask for the most recent / strongest topics and build their
 * "more of this / something you may also like / something different / popular channels" blocks from them.
 */
object OnlineInterestProfile {
    const val KIND_VIDEO = "video"
    const val KIND_AUDIO = "audio"

    private const val PREFS = "lumora_online_interest_profile"
    private const val MAX_TOPICS = 40
    private const val MAX_RECENT = 12
    private const val DECAY = 0.97

    data class Topic(val name: String, val category: String)

    private class Known(
        val keywords: List<String>,
        val name: String,
        val category: String,
        val related: List<String>
    )

    private const val GAMING = "gaming"

    private val KNOWN = listOf(
        Known(listOf("minecraft"), "minecraft", GAMING, listOf("terraria", "roblox", "stardew valley", "fortnite", "lego fortnite")),
        Known(listOf("roblox"), "roblox", GAMING, listOf("minecraft", "fortnite", "brookhaven", "among us")),
        Known(listOf("gta", "grand theft auto"), "gta 5", GAMING, listOf("red dead redemption 2", "watch dogs", "saints row", "cyberpunk 2077")),
        Known(listOf("bgmi", "pubg"), "bgmi", GAMING, listOf("free fire", "call of duty mobile", "fortnite", "apex legends")),
        Known(listOf("free fire"), "free fire", GAMING, listOf("bgmi", "call of duty mobile", "fortnite")),
        Known(listOf("valorant"), "valorant", GAMING, listOf("counter strike 2", "apex legends", "overwatch 2")),
        Known(listOf("counter strike", "cs2", "csgo"), "counter strike 2", GAMING, listOf("valorant", "call of duty", "apex legends")),
        Known(listOf("fortnite"), "fortnite", GAMING, listOf("apex legends", "roblox", "minecraft", "call of duty warzone")),
        Known(listOf("among us"), "among us", GAMING, listOf("roblox", "fall guys", "minecraft")),
        Known(listOf("terraria"), "terraria", GAMING, listOf("minecraft", "stardew valley", "starbound")),
        Known(listOf("call of duty", "warzone"), "call of duty", GAMING, listOf("battlefield", "valorant", "bgmi")),
        Known(listOf("clash of clans", "clash royale"), "clash of clans", GAMING, listOf("clash royale", "brawl stars", "boom beach")),
        Known(listOf("pokemon"), "pokemon", GAMING, listOf("zelda", "palworld", "digimon")),
        Known(listOf("fifa", "ea fc", "efootball"), "football game", GAMING, listOf("efootball", "ea fc 25", "dream league soccer")),
        Known(listOf("stardew"), "stardew valley", GAMING, listOf("minecraft", "terraria", "animal crossing")),
        Known(listOf("cricket", "ipl", "t20"), "cricket", "sports", listOf("ipl highlights", "world cup cricket", "virat kohli")),
        Known(listOf("football", "premier league", "messi", "ronaldo"), "football", "sports", listOf("premier league highlights", "messi", "champions league")),
        Known(listOf("iphone", "android", "smartphone", "laptop", "unboxing"), "tech review", "tech", listOf("smartphone review", "laptop review", "gadgets")),
        Known(listOf("python", "kotlin", "javascript", "coding", "programming"), "programming", "tech", listOf("web development", "android development", "ai tools")),
        Known(listOf("recipe", "cooking", "street food"), "cooking", "food", listOf("street food", "dessert recipe", "indian recipes")),
        Known(listOf("vlog", "travel"), "travel vlog", "travel", listOf("travel india", "world travel", "mountain trip")),
        Known(listOf("anime", "naruto", "one piece", "demon slayer"), "anime", "anime", listOf("anime recap", "best anime", "manga")),
        Known(listOf("trailer"), "movie trailer", "movies", listOf("movie review", "web series trailer", "movie explained")),
        Known(listOf("workout", "gym", "fitness"), "fitness", "fitness", listOf("home workout", "diet plan", "yoga")),
        Known(listOf("podcast"), "podcast", "talk", listOf("interview", "motivation", "story")),
        Known(listOf("comedy", "standup", "stand up"), "comedy", "comedy", listOf("stand up comedy", "funny sketch", "roast"))
    )

    private val STOP_WORDS = setOf(
        "official", "video", "videos", "full", "episode", "part", "the", "and", "for", "with", "from", "this", "that",
        "new", "latest", "best", "top", "hd", "4k", "live", "ep", "vs", "how", "what", "why", "you", "your", "are",
        "was", "will", "not", "all", "can", "now", "out", "more", "most", "just", "into", "http", "https", "www",
        "com", "lyrics", "audio", "song", "songs", "music", "shorts", "short", "trailer", "teaser", "reaction"
    )

    // ---------- topic extraction (pure) ----------

    /** Topics found in [text]: known ones first, otherwise the first two significant words. Never more than [limit]. */
    fun extractTopics(text: String, limit: Int = 2): List<Topic> {
        val lower = text.lowercase(Locale.ROOT)
        if (lower.isBlank()) return emptyList()
        val found = LinkedHashMap<String, Topic>()
        for (k in KNOWN) {
            if (k.keywords.any { containsWord(lower, it) }) found.putIfAbsent(k.name, Topic(k.name, k.category))
            if (found.size >= limit) return found.values.toList()
        }
        if (found.isNotEmpty()) return found.values.toList()
        val words = lower.split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 && it !in STOP_WORDS && !it.all { c -> c.isDigit() } }
            .take(2)
        if (words.isEmpty()) return emptyList()
        val name = words.joinToString(" ")
        return listOf(Topic(name, "other"))
    }

    private fun containsWord(haystack: String, word: String): Boolean {
        var from = 0
        while (true) {
            val i = haystack.indexOf(word, from)
            if (i < 0) return false
            val before = i == 0 || !haystack[i - 1].isLetterOrDigit()
            val end = i + word.length
            val after = end >= haystack.length || !haystack[end].isLetterOrDigit()
            if (before && (after || word.length >= 5)) return true
            from = i + 1
        }
    }

    /** Related things to explore for [topicName] (e.g. other games like Minecraft); a generic pair when unknown. */
    fun relatedTopics(topicName: String): List<String> {
        val k = KNOWN.firstOrNull { it.name.equals(topicName, true) }
        return k?.related ?: listOf("$topicName similar", "best of $topicName")
    }

    fun categoryOf(topicName: String): String =
        KNOWN.firstOrNull { it.name.equals(topicName, true) }?.category ?: "other"

    /** Search words that make a video search return the real thing for the topic (games -> gameplay). */
    fun videoQueryFor(topicName: String): String =
        if (categoryOf(topicName) == GAMING) "$topicName gameplay" else topicName

    // ---------- different categories / popular channels ----------

    private val VIDEO_DIVERSE = listOf(
        "tech" to listOf("latest tech review", "smartphone launch", "ai tools explained", "gadget unboxing"),
        "music" to listOf("top music video", "trending songs official video", "popular songs this week"),
        "trending" to listOf("trending today", "viral video", "most watched this week", "trending topic explained"),
        "talk" to listOf("podcast episode", "documentary", "science explained", "news explained"),
        "sports" to listOf("sports highlights", "cricket highlights"),
        "food" to listOf("street food", "cooking recipe"),
        "comedy" to listOf("stand up comedy", "comedy sketch")
    )

    private val AUDIO_DIVERSE = listOf(
        "pop" to listOf("top hits", "trending songs", "english pop hits"),
        "hindi" to listOf("bollywood hits", "new hindi songs", "arijit singh"),
        "chill" to listOf("lofi chill", "acoustic songs", "instrumental"),
        "talk" to listOf("podcast", "audiobook story", "motivation speech"),
        "classic" to listOf("old hindi songs", "retro 80s", "classical piano"),
        "dance" to listOf("electronic dance", "punjabi hits", "hip hop hits")
    )

    /**
     * One query per different category (never the category the user is in right now), round-robin through the list so
     * the three "something different" videos are tech, popular music and a trending topic rather than three of one kind.
     */
    fun diverseQueries(kind: String, avoidCategories: Set<String>, count: Int, pick: (List<String>) -> String = { it.random() }): List<String> {
        val table = if (kind == KIND_AUDIO) AUDIO_DIVERSE else VIDEO_DIVERSE
        val eligible = table.filter { it.first !in avoidCategories }.ifEmpty { table }
        return eligible.take(count.coerceAtLeast(0)).map { pick(it.second) }
    }

    val POPULAR_VIDEO_CHANNELS = listOf(
        "MrBeast", "T-Series", "Technical Guruji", "CarryMinati", "Marques Brownlee", "Kurzgesagt", "Veritasium",
        "Dude Perfect", "Sony SAB", "TVF", "Mark Rober", "Vox"
    )

    val POPULAR_AUDIO_CHANNELS = listOf(
        "T-Series", "Sony Music India", "Zee Music Company", "Tips Official", "Saregama Music", "Speed Records",
        "Atlantic Records", "Warner Records", "UMG", "Lofi Girl"
    )

    // ---------- storage ----------

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun readObj(c: Context, key: String): JSONObject =
        try { JSONObject(prefs(c).getString(key, "{}") ?: "{}") } catch (_: Throwable) { JSONObject() }

    private fun readArr(c: Context, key: String): JSONArray =
        try { JSONArray(prefs(c).getString(key, "[]") ?: "[]") } catch (_: Throwable) { JSONArray() }

    /**
     * Remembers that the user opened / played / searched [text] (a title or a search query) with the given [weight]
     * (watch ~3, search ~2). [extraTopic] is added as a topic too (for audio: the artist).
     */
    @Synchronized
    fun record(c: Context, kind: String, text: String, weight: Double = 3.0, extraTopic: String? = null) {
        val topics = extractTopics(text).toMutableList()
        extraTopic?.trim()?.takeIf { it.length >= 3 }?.let { topics.add(Topic(it.lowercase(Locale.ROOT), "artist")) }
        if (topics.isEmpty()) return
        val scores = readObj(c, "scores_$kind")
        val recent = readArr(c, "recent_$kind").let { a -> (0 until a.length()).map { a.optString(it) } }.toMutableList()
        // older interests slowly fade, so what the user watches now outweighs what they watched months ago
        scores.keys().asSequence().toList().forEach { scores.put(it, scores.optDouble(it, 0.0) * DECAY) }
        for (t in topics) {
            scores.put(t.name, scores.optDouble(t.name, 0.0) + weight)
            recent.remove(t.name)
            recent.add(0, t.name)
        }
        if (scores.length() > MAX_TOPICS) {
            val weakest = scores.keys().asSequence().map { it to scores.optDouble(it, 0.0) }
                .sortedBy { it.second }.take(scores.length() - MAX_TOPICS).map { it.first }.toList()
            weakest.forEach { scores.remove(it) }
        }
        prefs(c).edit()
            .putString("scores_$kind", scores.toString())
            .putString("recent_$kind", JSONArray(recent.take(MAX_RECENT)).toString())
            .apply()
    }

    /**
     * The topics to build the next batch around: what the user touched last comes first (watch a Minecraft video ->
     * the next batch starts with Minecraft), then their strongest long-term interests.
     */
    fun focus(c: Context, kind: String, limit: Int = 2): List<Topic> {
        val scores = readObj(c, "scores_$kind")
        val recent = readArr(c, "recent_$kind").let { a -> (0 until a.length()).map { a.optString(it) } }
        val strongest = scores.keys().asSequence().map { it to scores.optDouble(it, 0.0) }
            .sortedByDescending { it.second }.map { it.first }.toList()
        val ordered = (recent.take(1) + strongest + recent).distinct()
        return ordered.take(limit).map { Topic(it, categoryOf(it)) }
    }

    fun clear(c: Context, kind: String) {
        prefs(c).edit().remove("scores_$kind").remove("recent_$kind").apply()
    }
}
