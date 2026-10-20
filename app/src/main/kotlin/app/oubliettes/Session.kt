package app.oubliettes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.oubliettes.game.Puzzle
import app.oubliettes.game.Step
import app.oubliettes.game.drawing
import app.oubliettes.game.isSolved
import app.oubliettes.game.solutions
import kotlin.math.abs

// Mark values for a cell.
internal const val WALL = 1
internal const val KNOWN_OPEN = 2

/** Actions kept for Undo. Older ones are forgotten. */
private const val HISTORY = 50

/**
 * The marks of one grid being played, and their history. A stroke is one action: [begin], any number
 * of [dragTo], then [end]. [saved] is what [save] returned earlier; it is ignored unless it was made
 * for this exact puzzle. [legacyId] is what saves of 0.4 were tagged with instead: the seed.
 *
 * With [completes], a drag finishes only its own line; a tap can finish its row and column
 * independently once their unknown cells can only be filled one way: all open when the line has
 * its walls, all walls when it needs every one of them. Automatic marks never trigger other lines.
 *
 * A [checkpoint] remembers the marks before the player tries something out, when deduction runs dry;
 * [toCheckpoint] brings back the latest one. They pile up, one per try within a try.
 *
 * A [hint] follows deductions from the puzzle clues, without checking the player's mistakes;
 * how many were asked for is kept with the grid.
 */
internal class Session(val puzzle: Puzzle, saved: String? = null, legacyId: String? = null, private val completes: Boolean = false) {
    private val width = puzzle.width
    private val cells = width * puzzle.height

    /** Fingerprint of the clues: a save never lands on another grid, even if a seed gives a new one someday. */
    private val id = puzzle.encode().hashCode().toString()

    var marks by mutableStateOf(IntArray(cells))
        private set
    var solved by mutableStateOf(false)
        private set

    /** Every row and column holds its number of walls, and yet the grid is not solved. */
    var ruleBroken by mutableStateOf(false)
        private set
    private val undone = mutableStateListOf<IntArray>()
    private val redone = mutableStateListOf<IntArray>()
    private val checkpoints = mutableStateListOf<IntArray>()
    val canUndo get() = undone.isNotEmpty()
    val canRedo get() = redone.isNotEmpty()
    val canCheckpoint get() = !solved && marks.any { it != 0 } && checkpoints.lastOrNull()?.contentEquals(marks) != true
    val hasCheckpoint get() = checkpoints.isNotEmpty()

    /** Only once the grid has changed since the latest checkpoint: there is something to come back from. */
    val canReturn get() = checkpoints.lastOrNull()?.contentEquals(marks) == false

    /** The cells that changed since the latest checkpoint: the try under way. */
    val tried get() = checkpoints.lastOrNull()?.let { then -> marks.indices.filter { then[it] != marks[it] }.toSet() } ?: emptySet()

    /** Hints asked for on this grid so far. */
    var hints by mutableIntStateOf(0)
        private set

    private val solution by lazy { solutions(puzzle, limit = 1)?.firstOrNull() }

    // The stroke in progress.
    private var first = 0
    private var value = 0
    private var alongRow: Boolean? = null
    private var last = 0
    private var repaint = emptySet<Int>()
    private var redoable = emptyList<IntArray>() // what Redo held before the stroke, for [cancel]

    /** The cell the player marked last: its row and its column are the ones [complete] may finish. */
    private var latest = 0

    /** The cells the latest action filled by itself, see [complete]. */
    private var filled = emptySet<Int>()

    init {
        fun snapshot(text: String) = text.takeIf { it.length == cells && it.all { c -> c in '0'..'2' } }
            ?.let { IntArray(cells) { i -> text[i] - '0' } }
        fun snapshots(text: String?) = text.orEmpty().split(',').mapNotNull(::snapshot)
        val parts = saved.orEmpty().split(':')
        if (parts[0] == id || parts[0] == legacyId) parts.getOrNull(1)?.let(::snapshot)?.let {
            marks = it
            undone += snapshots(parts.getOrNull(2))
            redone += snapshots(parts.getOrNull(3))
            checkpoints += snapshots(parts.getOrNull(4))
            hints = parts.getOrNull(5)?.toIntOrNull() ?: 0
            settle()
        }
    }

    fun save() = (listOf(id, text(marks)) + listOf(undone, redone, checkpoints).map { it.joinToString(",", transform = ::text) } + hints)
        .joinToString(":")

    private fun text(snapshot: IntArray) = snapshot.joinToString("")

    private fun given(cell: Int) = cell in puzzle.monsters || cell in puzzle.chests

    private fun walls() = BooleanArray(cells) { marks[it] == WALL }

    /** The finished dungeon, as [drawing] gives it. */
    fun picture() = drawing(puzzle, walls())

    private fun settle() {
        solved = isSolved(puzzle, walls())
        ruleBroken = !solved &&
            (0 until puzzle.height).all { y -> (0 until width).count { marks[y * width + it] == WALL } == puzzle.rowCounts[y] } &&
            (0 until width).all { x -> (0 until puzzle.height).count { marks[it * width + x] == WALL } == puzzle.colCounts[x] }
    }

    /** Call before changing [marks]: the change becomes one action of the history. */
    private fun record() {
        undone += marks
        if (undone.size > HISTORY) undone.removeAt(0)
        redone.clear()
    }

    /**
     * Starts a stroke on [cell], and the stroke will paint the value the cell got. With no [brush] the
     * cell cycles through unknown, wall, known open; with one ([WALL] or [KNOWN_OPEN]) it takes that
     * mark, or loses it if it already had it. False, and nothing happens, on a monster or a chest.
     */
    fun begin(cell: Int, brush: Int = 0): Boolean {
        if (given(cell)) return false
        // The cells the previous action changed: this stroke may paint over those.
        // Not those it filled by itself, though: the player did not lay them, and a drag leaves them be.
        redoable = redone.toList()
        repaint = (undone.lastOrNull()?.let { before -> marks.indices.filter { before[it] != marks[it] }.toSet() } ?: emptySet()) - filled
        record()
        first = cell
        latest = cell
        value = when {
            brush == 0 -> (marks[cell] + 1) % 3
            marks[cell] == brush -> 0
            else -> brush
        }
        alongRow = null
        // Mending the previous action takes back what it filled: a wall on its way to a dot in the
        // cycle may have finished a line that the dot does not.
        val mended = if (cell in repaint) filled else emptySet()
        marks = marks.copyOf().also { new ->
            mended.forEach { new[it] = 0 }
            new[cell] = value
        }
        return true
    }

    /**
     * Moves the stroke to column [x], row [y], and returns how many cells that painted. The stroke
     * locks onto the row or the column it first moves along (no zig-zag) and paints every cell it
     * crosses on the way, however far the finger jumped: unknown cells and those of the previous
     * action, never older marks.
     */
    fun dragTo(x: Int, y: Int): Int {
        val firstX = first % width
        val firstY = first / width
        if (alongRow == null) {
            if (x == firstX && y == firstY) return 0
            alongRow = abs(x - firstX) >= abs(y - firstY)
            last = if (alongRow == true) firstX else firstY
        }
        val row = alongRow == true
        val to = if (row) x else y
        val new = marks.copyOf()
        var painted = 0
        // The way the finger went: the cell painted last is the one it reached.
        for (step in if (to >= last) last..to else last downTo to) {
            val target = if (row) firstY * width + step else step * width + firstX
            if (new[target] != value && (new[target] == 0 || target in repaint) && !given(target)) {
                new[target] = value
                latest = target
                painted++
            }
        }
        last = to
        if (painted > 0) marks = new
        return painted
    }

    /** Takes back the stroke in progress as if it had never begun: the fingers were after something else. */
    fun cancel() {
        marks = undone.removeAt(undone.lastIndex)
        redone += redoable
    }

    /** Ends the stroke: only now is the grid checked. True when this stroke solved it. */
    fun end(): Boolean {
        val before = solved
        filled = if (completes) complete() else emptySet()
        settle()
        // A solved grid has nothing left to take back, and its history would only weigh on the saves.
        if (solved) {
            undone.clear()
            redone.clear()
            checkpoints.clear()
        }
        return solved && !before
    }

    /**
     * Finishes only the stroke's line. A tap may finish its row and column independently, using the
     * same marks before either fill: automatic marks never trigger a perpendicular fill.
     * Erasing a mark finishes nothing, or the cell would fill again at once.
     * Returns the cells it filled.
     */
    private fun complete(): Set<Int> {
        if (marks[latest] == 0) return emptySet()
        val before = marks
        val new = before.copyOf()
        val done = mutableSetOf<Int>()
        val x = latest % width
        val y = latest / width
        val lines = buildList {
            if (alongRow != false) add((0 until width).map { y * width + it } to puzzle.rowCounts[y])
            if (alongRow != true) add((0 until puzzle.height).map { it * width + x } to puzzle.colCounts[x])
        }
        for ((line, count) in lines) {
            val unknown = line.filter { before[it] == 0 && !given(it) }
            val walls = line.count { before[it] == WALL }
            val value = when (count) {
                walls -> KNOWN_OPEN
                walls + unknown.size -> WALL
                else -> continue
            }
            unknown.forEach { new[it] = value }
            done += unknown
        }
        if (done.isNotEmpty()) marks = new
        return done
    }

    /** Sets [targets] to [value] as one action. For the tutorial and the accessibility actions. */
    fun paint(targets: Collection<Int>, value: Int): Boolean {
        val new = marks.copyOf()
        for (cell in targets) if (!given(cell) && new[cell] != value) {
            new[cell] = value
            latest = cell
        }
        if (new.contentEquals(marks)) return false
        record()
        marks = new
        alongRow = null
        return end()
    }

    fun undo() {
        if (!canUndo) return
        filled = emptySet()
        redone += marks
        marks = undone.removeAt(undone.lastIndex)
        settle()
    }

    fun redo() {
        if (!canRedo) return
        filled = emptySet()
        undone += marks
        marks = redone.removeAt(redone.lastIndex)
        settle()
    }

    fun checkpoint() {
        if (canCheckpoint) checkpoints += marks
    }

    /** Back to the latest checkpoint, which is used up. An action like any other: Undo brings the try back. */
    fun toCheckpoint() {
        if (!canReturn) return
        filled = emptySet()
        record()
        marks = checkpoints.removeAt(checkpoints.lastIndex)
        settle()
    }

    /** The marks that contradict the solution: walls where it is open, dots where it has a wall. */
    fun mistakes(): Set<Int> {
        val walls = solution ?: return emptySet()
        return marks.indices.filter { (marks[it] == WALL && !walls[it]) || (marks[it] == KNOWN_OPEN && walls[it]) }.toSet()
    }

    /**
     * A deduction from the clues, see [app.oubliettes.game.hint]. The optional Check action alone
     * checks mistakes; hints never report whether the grid contains one.
     */
    fun hint(): Step? {
        hints++
        return app.oubliettes.game.hint(puzzle, marks)
    }

    /** Empties the grid. An action like any other: Undo brings the marks back. */
    fun reset() {
        paint(marks.indices.toList(), 0)
    }
}

private const val BACKUP = "oubliettes save 1"

/** Everything the app remembers ([all] the preferences), as a text file: a line per entry, its type first. */
internal fun backupText(all: Map<String, *>) = all.entries.sortedBy { it.key }.mapNotNull { (key, value) ->
    val type = when (value) {
        is Boolean -> 'B'
        is Int -> 'I'
        is Long -> 'L'
        is Float -> 'F'
        is String -> 'S'
        else -> return@mapNotNull null
    }
    "$type$key=$value"
}.joinToString("\n", prefix = "$BACKUP\n")

/** Null when [text] was not written by [backupText]. */
internal fun parseBackup(text: String): Map<String, Any>? = runCatching {
    val lines = text.trim().lines()
    require(lines[0] == BACKUP)
    lines.drop(1).associate { line ->
        val value = line.substringAfter('=')
        line.substring(1).substringBefore('=').also { require(it.isNotEmpty() && '=' in line) } to when (line[0]) {
            'B' -> value.toBooleanStrict()
            'I' -> value.toInt()
            'L' -> value.toLong()
            'F' -> value.toFloat()
            'S' -> value
            else -> error("type")
        }
    }
}.getOrNull()
