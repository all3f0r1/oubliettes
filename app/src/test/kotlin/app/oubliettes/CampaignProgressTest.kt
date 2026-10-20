package app.oubliettes

import org.junit.Assert.assertEquals
import org.junit.Test

class CampaignProgressTest {
    @Test
    fun nextLevelSkipsCompletedRuns() {
        val done = setOf(2, 3, 4, 6)
        assertEquals(5, nextCampaignLevel(1, 8, done::contains))
        assertEquals(7, nextCampaignLevel(5, 8, done::contains))
        assertEquals(8, nextCampaignLevel(7, 8, done::contains))
    }

    @Test
    fun noLaterUnfinishedLevelReturnsToTheList() {
        assertEquals(0, nextCampaignLevel(3, 5) { it >= 4 })
        assertEquals(0, nextCampaignLevel(5, 5) { false })
        assertEquals(0, nextCampaignLevel(1, 5) { true })
    }

    @Test
    fun progressionReadsTheLatestCompletionState() {
        val done = mutableSetOf<Int>()
        assertEquals(2, nextCampaignLevel(1, 5, done::contains))
        done += 2
        assertEquals(3, nextCampaignLevel(1, 5, done::contains))
    }
}
