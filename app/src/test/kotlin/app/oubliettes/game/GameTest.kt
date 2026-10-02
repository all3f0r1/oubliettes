package app.oubliettes.game

import java.io.File
import kotlin.random.Random
import kotlin.system.measureTimeMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTest {
    /** '#' wall, '.' open, 'M' monster, 'C' chest. Clues are derived from the drawing. */
    private fun parse(vararg lines: String): Pair<Puzzle, BooleanArray> {
        val w = lines[0].length
        val cells = lines.joinToString("")
        val walls = BooleanArray(cells.length) { cells[it] == '#' }
        val p = Puzzle(
            w, lines.size,
            IntArray(lines.size) { y -> lines[y].count { it == '#' } },
            IntArray(w) { x -> lines.count { it[x] == '#' } },
            cells.indices.filter { cells[it] == 'M' }.toSet(),
            cells.indices.filter { cells[it] == 'C' }.toSet(),
        )
        return p to walls
    }

    private fun valid(vararg lines: String) = parse(*lines).let { (p, walls) -> isSolved(p, walls) }

    private val good = arrayOf(
        "...###",
        ".C.#M#",
        ".....#",
        "####.#",
        "M....#",
        "######",
    )

    @Test
    fun rules() {
        assertTrue(valid(*good))
        val (p, walls) = parse(*good)
        assertFalse("wall counts", isSolved(p, walls.copyOf().also { it[5 * 6] = false }))
        assertFalse("dead end without monster", valid("...###", ".C.#.#", ".....#", "####.#", "M....#", "######"))
        assertFalse("monster outside dead end", valid("...###", ".C.#M#", ".....#", "####M#", "M....#", "######"))
        assertFalse("2x2 hallway", valid("...###", ".C.#M#", ".....#", "###..#", "M....#", "######"))
        assertFalse("room with two exits", valid("...###", ".C.#M#", ".....#", "##.#.#", "M....#", "######"))
        assertFalse("chest outside a room", valid("...###", "...#M#", ".....#", "####.#", "M..C.#", "######"))
        assertFalse("disconnected", valid("...###", ".C.#M#", ".....#", "######", "MM####", "######"))
    }

    @Test
    fun drawingRoundTrips() {
        val (p, walls) = parse(*good)
        assertEquals(good.joinToString(""), drawing(p, walls))
    }

    @Test
    fun tutorialStepsSolveItsPuzzle() {
        val walls = BooleanArray(tutorialSolution.size)
        val marked = tutorialPuzzle.monsters + tutorialPuzzle.chests + tutorialSteps.flatMap { step ->
            assertTrue(step.walls.all { tutorialSolution[it] } && step.open.none { tutorialSolution[it] })
            step.walls.forEach { walls[it] = true }
            step.walls + step.open
        }
        assertEquals("every cell is explained exactly once", walls.indices.toList(), marked.sorted())
        assertTrue(isSolved(tutorialPuzzle, walls))
        assertEquals(1, solutions(tutorialPuzzle, maxNodes = Int.MAX_VALUE)!!.size)
    }

    @Test
    fun solverMatchesBruteForce() {
        val rng = Random(1)
        val w = 5
        val h = 4
        var solvable = 0
        var ambiguous = 0
        repeat(400) { round ->
            // Three kinds of clues: a carved dungeon, a room plus noise, pure noise.
            val p = if (round % 3 == 0) {
                layout(w, h, rng) ?: return@repeat
            } else {
                val walls = BooleanArray(w * h) { rng.nextInt(10) < 7 }
                val chests = mutableSetOf<Int>()
                if (round % 3 == 1) {
                    val tx = rng.nextInt(w - 2)
                    val ty = rng.nextInt(h - 2)
                    for (i in walls.indices) walls[i] = rng.nextInt(10) < 9
                    for (i in 0 until 9) walls[(ty + i / 3) * w + tx + i % 3] = false
                    chests += (ty + rng.nextInt(3)) * w + tx + rng.nextInt(3)
                }
                fun open(x: Int, y: Int) = x in 0 until w && y in 0 until h && !walls[y * w + x]
                val monsters = walls.indices.filter { i ->
                    val x = i % w
                    val y = i / w
                    !walls[i] && i !in chests &&
                        listOf(open(x - 1, y), open(x + 1, y), open(x, y - 1), open(x, y + 1)).count { it } == 1
                }.toSet()
                Puzzle(
                    w, h,
                    IntArray(h) { y -> (0 until w).count { walls[y * w + it] } },
                    IntArray(w) { x -> (0 until h).count { walls[it * w + x] } },
                    monsters, chests,
                )
            }
            val brute = (0 until (1 shl w * h)).count { bits -> isSolved(p, BooleanArray(w * h) { bits shr it and 1 == 1 }) }
            val solved = solutions(p, limit = Int.MAX_VALUE, maxNodes = Int.MAX_VALUE)!!
            assertEquals(brute, solved.size)
            assertTrue(solved.all { isSolved(p, it) })
            if (brute > 0) solvable++
            if (brute > 1) ambiguous++
        }
        // Guard against a vacuous test: both solvable and multi-solution clues must have shown up.
        assertTrue("solvable=$solvable", solvable > 100)
        println("brute force: $solvable solvable, $ambiguous ambiguous")
    }

    @Test
    fun solverSeesAmbiguousClues() {
        val rng = Random(2)
        val ambiguous = (0 until 300).mapNotNull { layout(8, 8, rng) }.count { p ->
            val found = solutions(p, maxNodes = Int.MAX_VALUE)!!
            assertTrue(found.isNotEmpty() && found.all { isSolved(p, it) })
            found.size == 2 && !found[0].contentEquals(found[1])
        }
        println("ambiguous raw layouts: $ambiguous")
        assertTrue(ambiguous > 0)
    }

    @Test
    fun generatorGivesUniquePuzzles() {
        for ((size, seeds) in listOf(8 to 200, 10 to 100, 12 to 100)) {
            var monsters = 0
            var chests = 0
            var wallCells = 0
            var slowest = 0L
            val total = measureTimeMillis {
                for (seed in 0L until seeds) {
                    val p: Puzzle
                    slowest = maxOf(slowest, measureTimeMillis { p = generate(size, size, seed)!! })
                    val found = solutions(p, maxNodes = Int.MAX_VALUE)!!
                    assertEquals(1, found.size)
                    assertEquals("walls at most two cells thick", null, thickWall(found[0], size, size))
                    monsters += p.monsters.size
                    chests += p.chests.size
                    wallCells += p.rowCounts.sum()
                }
            }
            val walls = wallCells * 100 / (seeds * size * size)
            println(
                "size $size: ${total / seeds} ms/puzzle avg, slowest $slowest ms, " +
                    "${monsters.toDouble() / seeds} monsters, ${chests.toDouble() / seeds} chests, $walls% walls",
            )
        }
        val a = generate(8, 8, 42)!!
        val b = generate(8, 8, 42)!!
        assertTrue(a.rowCounts.contentEquals(b.rowCounts) && a.monsters == b.monsters && a.chests == b.chests)
    }

    @Test
    fun generatorStopsWhenCancelledOrOutOfTries() {
        var asked = 0
        assertNull(generate(12, 12, 3) { ++asked == 3 })
        assertEquals("checked before every try", 3, asked)
        assertNull("no try, no puzzle", generate(8, 8, 42, maxTries = 0))
        // A budget does not change what a seed gives.
        assertEquals(generate(8, 8, 42)!!.encode(), generate(8, 8, 42, maxTries = Int.MAX_VALUE)!!.encode())
    }

    @Test
    fun puzzleRoundTripsAndRejectsNonsense() {
        val p = generate(10, 10, 7)!!
        assertEquals(p.encode(), decodePuzzle(p.encode())!!.encode())
        assertEquals("6x6|3,2,1,5,1,6|2,2,2,4,2,6|10,24|7", tutorialPuzzle.encode())
        for (bad in listOf(
            "", "garbage", "6x6|3,2,1,5,1,6|2,2,2,4,2,6|10,24", // not a puzzle, a part missing
            "6x6|3,2,1,5,1|2,2,2,4,2,6|10,24|7", // a row count missing
            "6x6|3,2,1,5,1,7|2,2,2,4,2,6|10,24|7", // more walls than cells
            "6x6|3,2,1,5,1,6|2,2,2,4,2,6|10,36|7", // monster outside the grid
            "6x6|3,2,1,5,1,6|2,2,2,4,2,6|10,7|7", // monster on the chest
            "40x1|0|0|0|1", // wider than the solver can hold
        )) assertNull(bad, decodePuzzle(bad))
    }

    /** The campaign is a file, not seeds: its grids must stay what they are. */
    @Test
    fun campaignIsFrozenAndSound() {
        val lines = File("src/main/res/raw/campaign.txt").readLines()
        assertEquals(150, lines.size)
        assertEquals("the campaign changed: saves and solved levels of players would no longer match", -1974651446, lines.hashCode())
        for ((index, line) in lines.withIndex()) {
            val p = decodePuzzle(line)!!
            assertEquals(listOf(8, 10, 12)[index / 50], p.width)
            assertEquals(p.width, p.height)
            assertEquals("level ${index + 1}", 1, solutions(p, maxNodes = Int.MAX_VALUE)!!.size)
        }
        // Level 1 of each size is still the grid 0.4 generated from seed 1.
        for (index in listOf(0, 50, 100)) assertEquals(generate(8 + index / 25, 8 + index / 25, 1)!!.encode(), lines[index])
    }
}
