package app.oubliettes

import org.junit.Assert.assertEquals
import org.junit.Test

class WallMaskTest {
    @Test
    fun joinsMarkedWallsOnly() {
        // 3×3, walls in a plus; corners open or unknown.
        val marks = intArrayOf(
            0, WALL, KNOWN_OPEN,
            WALL, WALL, WALL,
            0, WALL, 0,
        )
        assertEquals(15, wallNeighbourMask(4, 3, 3, marks))
        assertEquals(4, wallNeighbourMask(1, 3, 3, marks)) // top edge: only south
        assertEquals(2, wallNeighbourMask(3, 3, 3, marks)) // left edge: only east
        assertEquals(1, wallNeighbourMask(7, 3, 3, marks))
        assertEquals(6, wallNeighbourMask(0, 3, 3, marks)) // corner: east and south
    }

    @Test
    fun stoneByDifficulty() {
        assertEquals(listOf(Stone.DUNGEON, Stone.CISTERN, Stone.CRYPT, Stone.DEPTHS, Stone.DEPTHS), SIZES.map { Stone.of(it) })
        assertEquals(Stone.DUNGEON, Stone.of(5)) // a lesson or a friend's grid of another size
    }
}
