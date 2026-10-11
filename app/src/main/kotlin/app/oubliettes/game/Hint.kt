package app.oubliettes.game

private fun Puzzle.at(cell: Int) = "row ${cell / width + 1}, column ${cell % width + 1}"

private fun Puzzle.lineName(line: Int) = if (line < height) "row ${line + 1}" else "column ${line - height + 1}"

/** The first word of a hint: where to look on [p], and no more. */
fun Step.nudge(p: Puzzle) = when (rule) {
    Rule.COUNT -> "Look at ${p.lineName(line)}."
    Rule.MONSTER -> "Look at the monster in ${p.at(from[0])}."
    Rule.DEAD_END -> "Could the cell in ${p.at(from[0])} be open?"
    Rule.PASSAGE -> "Look at the open cell in ${p.at(from[0])}: how does one leave it?"
    Rule.BLOCK -> "Look at the 2×2 block whose top left is in ${p.at(from[0])}."
    Rule.ROOM -> "Where can the treasure room of the chest in ${p.at(from[0])} be?"
    Rule.EXIT -> "Look around the treasure room whose top left is in ${p.at(from[0])}."
    Rule.CUT -> "Is every cell still within reach of the rest of the dungeon?"
    Rule.TRY, Rule.EITHER -> "No rule gives a cell as the grid stands. Lay a Checkpoint and try the cell in ${p.at(from[0])}."
}

/** The second word of a hint: the rule at work on [p], and what it gives. */
fun Step.reason(p: Puzzle): String {
    val line = if (rule == Rule.COUNT) p.lineName(this.line).replaceFirstChar { it.uppercase() } else ""
    val count = if (rule == Rule.COUNT) (p.rowCounts + p.colCounts)[this.line] else 0
    return when (rule) {
        Rule.COUNT ->
            if (walls.isEmpty()) "$line has its $count walls: its other cells are open."
            else "$line asks for $count walls and has just enough cells left: they are all walls."
        Rule.MONSTER ->
            if (open.isEmpty()) "A monster has exactly one way out, and this one has it: its other neighbours are walls."
            else "A monster has exactly one way out, and this one has a single cell left for it: that cell is open."
        Rule.DEAD_END -> "Open, the cell in ${p.at(from[0])} would be a dead end, and it holds no monster: it is a wall."
        Rule.PASSAGE -> "Only a monster sits in a dead end: the cell in ${p.at(from[0])} needs two ways out, and has just two left. Both are open."
        Rule.BLOCK -> "Hallways are one cell wide: no open 2×2 block. Three cells of this one are open, so the fourth is a wall."
        Rule.ROOM -> "Wherever the 3×3 treasure room of this chest still fits, it covers the same cells: they are open."
        Rule.EXIT ->
            if (open.isEmpty()) "A treasure room has a single exit, and this one has it: every other cell around it is a wall."
            else "A treasure room has a single exit, and this one has a single cell left for it: that cell is open."
        Rule.CUT -> "All open cells are connected. Walls cut some cells off from the rest of the dungeon: they are walls too."
        Rule.TRY -> {
            val (tried, proved) = if (assumes) "a wall" to "open" else "open" to "a wall"
            val end = when (breaks) {
                Rule.COUNT -> "a row or a column ends up with the wrong number of walls"
                Rule.MONSTER -> "a monster ends up with no way out, or with two"
                Rule.BLOCK -> "an open 2×2 block appears"
                Rule.ROOM, Rule.EXIT -> "a treasure room no longer fits, or has no single exit"
                Rule.CUT -> "part of the dungeon is cut off from the rest"
                else -> "a dead end appears where no monster is"
            }
            "Suppose the cell in ${p.at(from[0])} is $tried: follow what that forces, and $end. So it is $proved."
        }
        Rule.EITHER -> "Try the cell in ${p.at(from[0])} both ways: wall or open, some cells come out the same. Those are sure."
    }
}
