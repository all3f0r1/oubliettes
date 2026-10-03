package app.oubliettes

import app.oubliettes.game.generate
import app.oubliettes.game.tutorialPuzzle
import app.oubliettes.game.tutorialSolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {
    private val w = tutorialPuzzle.width
    private fun c(x: Int, y: Int) = y * w + x
    private fun Session.row(y: Int) = (0 until w).joinToString("") { marks[c(it, y)].toString() }
    private fun Session.column(x: Int) = (0 until tutorialPuzzle.height).joinToString("") { marks[c(x, it)].toString() }
    private fun Session.tap(cell: Int) = begin(cell).also { end() }

    /** Marks every wall of the solution, the last one with a tap. */
    private fun Session.solve(): Boolean {
        val walls = tutorialSolution.indices.filter { tutorialSolution[it] }
        paint(walls.dropLast(1), WALL)
        begin(walls.last())
        return end()
    }

    @Test
    fun fastDragLeavesNoHole() {
        val s = Session(tutorialPuzzle)
        assertTrue(s.begin(c(0, 5)))
        assertEquals("one event jumping five columns", 5, s.dragTo(5, 5))
        s.end()
        assertEquals("111111", s.row(5))
    }

    @Test
    fun dragKeepsItsLineAndComesBack() {
        val s = Session(tutorialPuzzle)
        s.begin(c(2, 3))
        assertEquals(0, s.dragTo(2, 3)) // still on the first cell: no direction yet
        s.dragTo(4, 4) // further along the row than down the column: the row it is
        assertEquals("001110", s.row(3))
        s.dragTo(4, 0) // moving up changes nothing: the stroke stays on its row
        assertEquals("001110", s.row(3))
        s.dragTo(0, 0) // back past the start: the other side is filled too
        s.end()
        assertEquals("111110", s.row(3))
        assertEquals("only row 3 was touched", 5, s.marks.count { it != 0 })
    }

    @Test
    fun dragSkipsGivensAndOlderMarks() {
        val s = Session(tutorialPuzzle)
        assertFalse("a monster cannot be marked", s.begin(c(4, 1)))
        s.tap(c(4, 2))
        s.tap(c(4, 2)) // a dot, two actions ago by the end of the next tap
        s.tap(c(0, 0))
        s.begin(c(4, 0))
        s.dragTo(4, 5)
        s.end()
        assertEquals("the monster stays, the older dot too", "102111", s.column(4))
    }

    @Test
    fun dragRepaintsThePreviousAction() {
        val s = Session(tutorialPuzzle)
        s.begin(c(0, 3))
        s.dragTo(3, 3)
        s.end()
        s.begin(c(0, 3)) // walls become dots
        s.dragTo(5, 3)
        s.end()
        assertEquals("the four walls, and the two blank cells beyond", "222222", s.row(3))
    }

    @Test
    fun brushLaysItsMarkAtOnce() {
        val s = Session(tutorialPuzzle)
        s.begin(c(0, 2), KNOWN_OPEN) // a dot at the first tap, with no wall on the way
        s.dragTo(4, 2)
        s.end()
        assertEquals("222220", s.row(2))
        s.begin(c(0, 2), KNOWN_OPEN) // already a dot: the brush takes it off
        s.end()
        assertEquals("022220", s.row(2))
        s.begin(c(0, 2), WALL)
        s.end()
        assertEquals("122220", s.row(2))
        s.undo()
        s.undo()
        s.undo() // three strokes, three actions
        assertTrue(s.marks.all { it == 0 })
    }

    @Test
    fun countsMetIsNotSolved() {
        val s = Session(tutorialPuzzle)
        assertFalse("an empty grid breaks no rule yet", s.ruleBroken)
        // The solution with two walls swapped between rows 1 and 4: every count holds, the dungeon does not.
        val walls = tutorialSolution.indices.filter { tutorialSolution[it] } - setOf(c(4, 0), c(0, 3)) + setOf(c(0, 0), c(4, 3))
        assertFalse(s.paint(walls, WALL))
        assertTrue(s.ruleBroken)
        s.reset()
        assertFalse(s.ruleBroken)
        assertTrue(s.solve())
        assertFalse("a solved grid breaks none", s.ruleBroken)
    }

    @Test
    fun undoRedoAndReset() {
        val s = Session(tutorialPuzzle)
        assertFalse(s.canUndo || s.canRedo)
        s.begin(c(0, 5))
        s.dragTo(5, 5)
        s.end()
        s.tap(c(0, 0))
        s.undo()
        assertEquals("000000", s.row(0))
        s.undo() // a whole drag is one action
        assertEquals("000000", s.row(5))
        s.redo()
        s.redo()
        assertEquals("111111", s.row(5))
        assertEquals("100000", s.row(0))
        assertFalse(s.canRedo)
        s.reset()
        assertTrue(s.marks.all { it == 0 })
        s.undo() // a reset is taken back like any action
        assertEquals("111111", s.row(5))
        s.undo()
        s.tap(c(1, 1) + 1)
        assertFalse("a new action forgets what was undone", s.canRedo)
    }

    @Test
    fun solvedIsOnlyAnnouncedOnce() {
        val s = Session(tutorialPuzzle)
        assertTrue("the stroke that solves says so", s.solve())
        assertTrue(s.solved)
        assertFalse("nothing to take back on a solved grid", s.canUndo)
        s.reset()
        assertFalse(s.solved)
        s.undo()
        assertTrue("back to the solved grid, without solving it", s.solved)
        assertEquals("...###.C.#M#.....#####.#M....#######", s.picture())
    }

    @Test
    fun saveSurvivesTheProcess() {
        val s = Session(tutorialPuzzle)
        s.begin(c(0, 5))
        s.dragTo(5, 5)
        s.end()
        s.tap(c(0, 0))
        s.tap(c(1, 0))
        s.undo()

        val back = Session(tutorialPuzzle, s.save())
        assertEquals(s.marks.toList(), back.marks.toList())
        back.redo() // the history came back with the marks
        assertEquals("110000", back.row(0))
        back.undo()
        back.undo()
        back.undo()
        assertTrue(back.marks.all { it == 0 })
        assertFalse(back.canUndo)

        val solved = Session(tutorialPuzzle).also { it.solve() }
        assertTrue(Session(tutorialPuzzle, solved.save()).solved)
    }

    @Test
    fun saveOfAnotherGridIsIgnored() {
        val marks = "1".repeat(64)
        val a = generate(8, 8, 1)!!
        val b = generate(8, 8, 2)!!
        val save = Session(a, "1:$marks", legacyId = "1").save()
        assertEquals("a save of 0.4, tagged with its seed", marks.toList(), Session(a, save).marks.map { '0' + it })
        // Same seed, same size, another grid: what a changed generator would do to an old save.
        assertTrue(Session(b, save, legacyId = "other").marks.all { it == 0 })
        for (broken in listOf(null, "", "1", "1:", "1:123", "1:${"3".repeat(64)}", "x:$marks")) {
            assertTrue("$broken", Session(a, broken, legacyId = "1").marks.all { it == 0 })
        }
    }
}
