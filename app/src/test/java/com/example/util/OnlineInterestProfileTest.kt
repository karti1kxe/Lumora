package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineInterestProfileTest {

    @Test
    fun extractTopics_recognisesKnownGameAndItsCategory() {
        val topics = OnlineInterestProfile.extractTopics("I Survived 100 Days in Minecraft Hardcore!")
        assertEquals("minecraft", topics.first().name)
        assertEquals("gaming", topics.first().category)
    }

    @Test
    fun extractTopics_fallsBackToSignificantWords() {
        val topics = OnlineInterestProfile.extractTopics("Official Video - Zebra Migration Documentary")
        assertEquals(1, topics.size)
        assertEquals("zebra migration", topics.first().name)
    }

    @Test
    fun extractTopics_doesNotMatchInsideOtherWords() {
        assertTrue(OnlineInterestProfile.extractTopics("sagta kaam").none { it.name == "gta 5" })
    }

    @Test
    fun relatedTopics_ofMinecraftAreOtherGames() {
        val related = OnlineInterestProfile.relatedTopics("minecraft")
        assertTrue("terraria" in related)
        assertFalse("minecraft" in related)
    }

    @Test
    fun videoQueryFor_addsGameplayOnlyForGames() {
        assertEquals("minecraft gameplay", OnlineInterestProfile.videoQueryFor("minecraft"))
        assertEquals("cricket", OnlineInterestProfile.videoQueryFor("cricket"))
    }

    @Test
    fun diverseQueries_neverUseTheCategoryTheUserIsIn() {
        val q = OnlineInterestProfile.diverseQueries(
            OnlineInterestProfile.KIND_VIDEO, setOf("tech"), 3
        ) { it.first() }
        assertEquals(3, q.size)
        assertFalse(q.any { it.contains("tech review") || it.contains("smartphone") })
    }
}
