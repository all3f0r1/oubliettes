package app.oubliettes.game

/**
 * One deduction of the guided solve: what it lets the player mark, and which cells it is about.
 * The step about [checkpoints] has them at hand to be tried.
 */
class TutorialStep(
    val text: String,
    val walls: List<Int> = emptyList(),
    val open: List<Int> = emptyList(),
    val focus: List<Int> = walls + open,
    val checkpoints: Boolean = false,
)

// '#' wall, '.' open, 'M' monster, 'C' chest.
private val DRAWING = listOf(
    "...###",
    ".C.#M#",
    ".....#",
    "####.#",
    "M....#",
    "######",
)
private const val W = 6
private val CELLS = DRAWING.joinToString("")

val tutorialSolution = BooleanArray(CELLS.length) { CELLS[it] == '#' }

val tutorialPuzzle = Puzzle(
    W, DRAWING.size,
    IntArray(DRAWING.size) { y -> DRAWING[y].count { it == '#' } },
    IntArray(W) { x -> DRAWING.count { it[x] == '#' } },
    CELLS.indices.filter { CELLS[it] == 'M' }.toSet(),
    CELLS.indices.filter { CELLS[it] == 'C' }.toSet(),
)

private fun c(x: Int, y: Int) = y * W + x
private fun row(y: Int, xs: IntRange) = xs.map { c(it, y) }
private fun col(x: Int, ys: IntRange) = ys.map { c(x, it) }

val tutorialSteps = listOf(
    TutorialStep(
        "The goal: find every wall of the dungeon. Each number tells how many walls its row or " +
            "column holds. Monsters and chests are never walls.",
    ),
    TutorialStep(
        "The last row and the last column ask for 6 walls in 6 cells: all of them are walls. " +
            "Tap a cell to build a wall, or drag along the line to build them all.",
        walls = row(5, 0..5) + col(5, 0..4),
    ),
    TutorialStep(
        "Rows 3 and 5 ask for a single wall, already placed on the right: their other cells are " +
            "open. Pick Hallway under the grid, then tap them or drag along the row. Without it, a " +
            "second tap turns a wall into a hallway.",
        open = row(2, 0..4) + row(4, 1..4),
    ),
    TutorialStep(
        "A monster lives at the end of a dead end: it has exactly one open cell next to it. " +
            "This one already has one below, so its other neighbours are walls.",
        walls = listOf(c(4, 0), c(3, 1)),
        focus = listOf(c(4, 1), c(4, 0), c(3, 1), c(4, 2)),
    ),
    TutorialStep(
        "On harder grids deduction may run dry, and something has to be tried out. Lay a Checkpoint " +
            "first: Return brings the grid back to it if the try leads nowhere. Try it: Checkpoint, " +
            "build a wall anywhere, then Return.",
        checkpoints = true,
    ),
    TutorialStep(
        "Column 5 has its 2 walls: its last unknown cell is open. Row 4 then asks for 5 walls " +
            "and has only 5 possible cells left: all of them are walls.",
        walls = row(3, 0..3),
        open = listOf(c(4, 3)),
    ),
    TutorialStep(
        "Column 4 asks for 4 walls. It has 3 and a single unknown cell: that is the fourth.",
        walls = listOf(c(3, 0)),
    ),
    TutorialStep(
        "Rows 1 and 2 now have all their walls: what is left is open.",
        open = row(0, 0..2) + listOf(c(0, 1), c(2, 1)),
    ),
    TutorialStep(
        "The chest is in a treasure room: 3×3 open cells, a single chest (anywhere in the room) " +
            "and a single exit. Everywhere else hallways are one cell wide, with no open 2×2 block, " +
            "and the whole dungeon is connected. Dungeon solved!",
        focus = (0..2).flatMap { row(it, 0..2) },
    ),
)

/**
 * One technique, first on a [guided] grid solved a deduction at a time, then on one to [practise]
 * alone. Both are frozen here, whatever becomes of the generator that dug them.
 */
class Lesson(val title: String, val about: String, intro: String, guided: String, practise: String) {
    val guided = decodePuzzle(guided)!!
    val practise = decodePuzzle(practise)!!

    /** The guided solve: the introduction, then every deduction [steps] finds, each in the words of its hint. */
    val steps by lazy {
        listOf(TutorialStep(intro)) +
            steps(this.guided)!!.map { TutorialStep(it.reason(this.guided), it.walls, it.open, focus = (it.from + it.walls + it.open).distinct()) } +
            TutorialStep("Dungeon solved! Now one on your own: the Hint plank is there if you get stuck.")
    }
}

val lessons = listOf(
    Lesson(
        "Treasure rooms", "Where the room is, and its exit",
        "A chest sits in a treasure room: 3×3 open cells with a single exit. The room may lie several " +
            "ways around its chest, and the cells it covers every way are open for sure. Once it is " +
            "placed, all the cells around it are walls but one.",
        "6x6|3,3,3,2,0,2|1,2,1,3,4,2|6,35|25", "6x6|2,3,1,2,1,5|3,2,4,2,1,2|5,31|16",
    ),
    Lesson(
        "Dead ends", "No dead end without its monster",
        "Every dead end holds a monster. So a cell with no monster and a single way out cannot be " +
            "open: it is a wall. And an open cell needs two ways out: when just two are left, both are open.",
        "6x6|5,4,4,0,3,0|3,3,3,4,2,1|5,13|", "6x6|4,3,5,2,2,1|3,4,2,2,2,4|5,8,29|",
    ),
    Lesson(
        "Narrow hallways", "No open 2×2 block",
        "Outside treasure rooms, hallways are one cell wide: four cells in a square are never all " +
            "open. Where three of them are, the fourth is a wall.",
        "6x6|1,4,2,3,2,5|2,2,3,1,4,5|5,22,30|", "6x6|3,4,2,3,3,5|5,3,1,2,3,6|18,32|",
    ),
    Lesson(
        "One dungeon", "Nothing is cut off",
        "All open cells are connected: any monster could walk to any other. When a single cell joins " +
            "two parts of the dungeon, it cannot be a wall. To find it, picture a wall there and see what is cut off.",
        "6x6|3,4,1,4,4,5|4,3,3,5,4,2|6,19,28,35|", "6x6|3,4,2,3,3,5|3,3,3,3,5,3|19,23,35|",
    ),
    Lesson(
        "Trying it out", "When no rule gives a cell",
        "When no rule gives a cell, suppose one: a wall here, say. Follow what that forces: if a rule " +
            "breaks, the cell was open after all. On your own grids, lay a Checkpoint before the try " +
            "and Return once it has failed.",
        "6x6|6,4,4,2,2,2|5,2,4,3,4,2|10,13,30|", "6x6|3,3,1,2,3,6|3,3,2,4,3,3|8,10,26,28|",
    ),
)
