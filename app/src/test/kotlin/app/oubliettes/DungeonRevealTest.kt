package app.oubliettes

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class DungeonRevealTest {
    @Test
    fun lightFollowsOpenCellsWithoutCrossingWallsOrEdges() {
        val marks = intArrayOf(
            KNOWN_OPEN, WALL, KNOWN_OPEN,
            KNOWN_OPEN, WALL, KNOWN_OPEN,
            KNOWN_OPEN, KNOWN_OPEN, KNOWN_OPEN,
        )

        assertArrayEquals(
            intArrayOf(0, -1, 6, 1, -1, 5, 2, 3, 4),
            dungeonDistances(marks, width = 3, height = 3),
        )
    }
}
