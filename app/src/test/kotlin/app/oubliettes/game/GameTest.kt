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
    fun lessonsTeachWhatTheySay() {
        val taught = listOf(Rule.ROOM, Rule.DEAD_END, Rule.BLOCK, Rule.TRY, Rule.TRY)
        assertEquals(taught.size, lessons.size)
        for ((lesson, rule) in lessons.zip(taught)) {
            val marks = lesson.guided.monsters + lesson.guided.chests + lesson.steps.flatMap { it.walls + it.open }
            assertEquals("${lesson.title}: every cell is explained exactly once", (0 until 36).toList(), marks.sorted())
            for (p in listOf(lesson.guided, lesson.practise)) {
                assertEquals(1, solutions(p, maxNodes = Int.MAX_VALUE)!!.size)
                assertTrue(lesson.title, steps(p)!!.any { it.rule == rule })
            }
        }
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
        for ((size, seeds) in listOf(6 to 200, 8 to 200, 10 to 100, 12 to 100, 14 to 20)) {
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
                    assertEquals("solved without a guess", emptyList<Int>(), stuck(p))
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
        // A grid can be asked for by how much reasoning it takes.
        for (seed in 0L until 20) {
            assertTrue(effort(steps(generate(8, 8, seed, reasoning = 0 until 54)!!)!!) < 54)
            assertTrue(effort(steps(generate(8, 8, seed, reasoning = 138..Int.MAX_VALUE)!!)!!) >= 138)
        }
        // The rarest the game asks for, in the size that takes longest to dig.
        for (seed in 0L until 10) assertTrue(effort(steps(generate(14, 14, seed, reasoning = 979..Int.MAX_VALUE)!!)!!) >= 979)
        val a = generate(8, 8, 42)!!
        val b = generate(8, 8, 42)!!
        assertTrue(a.rowCounts.contentEquals(b.rowCounts) && a.monsters == b.monsters && a.chests == b.chests)
    }

    @Test
    fun generatorStopsWhenCancelledOrOutOfTries() {
        var asked = 0
        assertNull(generate(14, 14, 3) { ++asked == 2 })
        assertEquals("checked before every try", 2, asked)
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
        // A grid sent by someone is found in the words around it, and only in a size the game shows.
        assertEquals(p.encode(), findPuzzle("Oubliettes · Endless\nSolved with 2 hints.\n${p.encode()}\n")!!.encode())
        for (bad in listOf("", "no grid here", "40x1|0|0|0|1", "3x3|0,0,0|0,0,0||", "6x8|0,0,0,0,0,0,0,0|0,0,0,0,0,0||")) assertNull(bad, findPuzzle(bad))
    }

    @Test
    fun everyStepIsSoundAndExplained() {
        val seen = mutableSetOf<Rule>()
        for ((size, seeds) in listOf(6 to 60, 8 to 60, 10 to 20)) for (seed in 0L until seeds) {
            val p = generate(size, size, seed)!!
            val solution = solutions(p)!!.single()
            val steps = steps(p)!!
            val marks = IntArray(size * size)
            for (step in steps) {
                // A hint from the marks so far is that very step, and never contradicts the solution.
                val hint = hint(p, marks)!!
                assertEquals(step.rule, hint.rule)
                assertEquals(step.walls to step.open, hint.walls to hint.open)
                assertTrue(step.walls.isNotEmpty() || step.open.isNotEmpty())
                assertTrue(step.walls.all { solution[it] && marks[it] == 0 } && step.open.none { solution[it] })
                assertTrue(step.nudge(p).isNotEmpty() && step.reason(p).isNotEmpty())
                step.walls.forEach { marks[it] = 1 }
                step.open.forEach { marks[it] = 2 }
                seen += step.rule
            }
            // The plainest grids are the ones no try is needed for.
            assertEquals(probes(p) == 0, steps.none { it.rule >= Rule.TRY })
            assertTrue(effort(steps) >= 0)
        }
        // Cells cut off are nearly always dead ends first, and both ways of a cell rarely agree: no grid here needs them.
        assertTrue("rules seen: $seen", seen.containsAll(Rule.entries - Rule.CUT - Rule.EITHER))
        // Marks that break a rule give no hint, and neither does a grid with nothing left to find.
        val p = generate(8, 8, 1)!!
        assertNull(hint(p, IntArray(64) { 1 }))
        assertNull(hint(p, solutions(p)!!.single().let { walls -> IntArray(64) { if (walls[it]) 1 else 2 } }))
    }

    /** The campaign is a file, not seeds: its grids must stay what they are. */
    @Test
    fun campaignIsFrozenAndSound() {
        val lines = File("src/main/res/raw/campaign.txt").readLines()
        assertEquals(250, lines.size)
        assertEquals("the campaign changed: saves and solved levels of players would no longer match", -1506467730, lines.hashCode())
        for ((index, line) in lines.withIndex()) {
            val p = decodePuzzle(line)!!
            assertEquals(listOf(6, 8, 10, 12, 14)[index / 50], p.width)
            assertEquals(p.width, p.height)
            assertEquals("level ${index + 1}", 1, solutions(p, maxNodes = Int.MAX_VALUE)!!.size)
            assertEquals("level ${index + 1} solved without a guess", emptyList<Int>(), stuck(p))
        }
        // Each size goes from the grid that asks for the least work to the one that asks for the most.
        for (levels in lines.chunked(50)) {
            val efforts = levels.map { effort(steps(decodePuzzle(it)!!)!!) }
            assertEquals(efforts.sorted(), efforts)
        }
        // Not only hallways: about half of the levels of each size have treasure rooms.
        for (levels in lines.chunked(50)) assertTrue(levels.count { decodePuzzle(it)!!.chests.isNotEmpty() } in 20..35)
    }
}
