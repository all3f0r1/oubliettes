package app.oubliettes.game

/** One deduction of the guided solve: what it lets the player mark, and which cells it is about. */
class TutorialStep(val text: String, val walls: List<Int> = emptyList(), val open: List<Int> = emptyList(), val focus: List<Int> = walls + open)

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
        "The last row and the last column ask for 6 walls in 6 cells: all of them are walls.",
        walls = row(5, 0..5) + col(5, 0..4),
    ),
    TutorialStep(
        "Rows 3 and 5 ask for a single wall, and it is already placed on the right. Their other " +
            "cells are therefore open: mark them with a dot.",
        open = row(2, 0..4) + row(4, 1..4),
    ),
    TutorialStep(
        "A monster lives at the end of a dead end: it has exactly one open cell next to it. " +
            "This one already has one below, so its other neighbours are walls.",
        walls = listOf(c(4, 0), c(3, 1)),
        focus = listOf(c(4, 1), c(4, 0), c(3, 1), c(4, 2)),
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
