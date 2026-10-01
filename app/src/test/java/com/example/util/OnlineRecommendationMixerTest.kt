package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class OnlineRecommendationMixerTest {

    private val similar = (1..100).toList()
    private val discovery = (101..200).toList()

    @Test
    fun mix_keepsAtLeast65PercentSimilarAndRestDiscovery() {
        val mixed = OnlineRecommendationMixer.mix(similar, discovery, total = 20)
        assertEquals(20, mixed.size)
        assertEquals(13, mixed.count { it in similar })
        assertEquals(7, mixed.count { it in discovery })
    }

    @Test
    fun mix_spreadsDiscoveryInsteadOfBunchingIt() {
        val mixed = OnlineRecommendationMixer.mix(similar, discovery, total = 20)
        assertTrue("first upcoming track should be similar", mixed.first() in similar)
        val isDiscovery = mixed.map { it in discovery }
        for (i in 1 until isDiscovery.size) {
            assertFalse("two discovery tracks back to back at $i", isDiscovery[i] && isDiscovery[i - 1])
        }
    }

    @Test
    fun mix_fillsFromTheOtherPoolWhenOneIsShort() {
        val mixed = OnlineRecommendationMixer.mix((1..5).toList(), discovery, total = 20)
        assertEquals(20, mixed.size)
        assertEquals(5, mixed.count { it in 1..5 })

        val onlySimilar = OnlineRecommendationMixer.mix(similar, emptyList(), total = 20)
        assertEquals(20, onlySimilar.size)
        assertTrue(onlySimilar.all { it in similar })
    }

    @Test
    fun mix_handlesEmptyAndZero() {
        assertTrue(OnlineRecommendationMixer.mix(emptyList<Int>(), emptyList(), total = 20).isEmpty())
        assertTrue(OnlineRecommendationMixer.mix(similar, discovery, total = 0).isEmpty())
    }

    @Test
    fun mix_hasNoDuplicatesWhenPoolsAreDistinct() {
        val mixed = OnlineRecommendationMixer.mix(similar, discovery, total = 24)
        assertEquals(mixed.size, mixed.toSet().size)
    }

    @Test
    fun roundRobin_interleavesLists() {
        val out = OnlineRecommendationMixer.roundRobin(listOf(listOf(1, 2, 3), listOf(10, 20), listOf(100)))
        assertEquals(listOf(1, 10, 100, 2, 20, 3), out)
    }

    @Test
    fun eligibleDiscoveryTopics_skipsGenreAlreadyBeingPlayed() {
        val topics = OnlineRecommendationMixer.eligibleDiscoveryTopics(listOf("Smooth Jazz Night", "Some Artist"))
        assertFalse("jazz" in topics)
        assertTrue(topics.isNotEmpty())
    }

    @Test
    fun eligibleDiscoveryTopics_skipsWholeIndianFamilyForHindiListeners() {
        val topics = OnlineRecommendationMixer.eligibleDiscoveryTopics(listOf("Latest Hindi Songs", "T-Series"))
        assertFalse("bollywood" in topics)
        assertFalse("punjabi" in topics)
        assertFalse("sufi" in topics)
        assertTrue("jazz" in topics)
    }

    @Test
    fun freshQuery_neverRepeatsUntilExhaustedThenKeepsGoing() {
        val used = mutableSetOf<String>()
        val random = Random(42)
        val seen = mutableSetOf<String>()
        repeat(8) {
            val q = OnlineRecommendationMixer.freshQuery(listOf("artist a"), used, random)
            assertNotNull(q)
            assertTrue("repeated query $q", seen.add(q!!))
        }
        // 8 modifiers exist; the 9th call must recycle rather than return null (endless feed)
        assertNotNull(OnlineRecommendationMixer.freshQuery(listOf("artist a"), used, random))
    }

    @Test
    fun freshQuery_returnsNullOnlyWithoutBases() {
        assertEquals(null, OnlineRecommendationMixer.freshQuery(emptyList(), mutableSetOf()))
    }
}
