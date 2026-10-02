package app.oubliettes.game

/** Cells are indexed `y * width + x`. Monsters and chests are given and are never walls. */
class Puzzle(
    val width: Int,
    val height: Int,
    val rowCounts: IntArray,
    val colCounts: IntArray,
    val monsters: Set<Int>,
    val chests: Set<Int>,
) {
    init {
        // The solver holds a row in the bits of an Int.
        require(width in 1..30 && height >= 1) { "size ${width}x$height" }
        require(rowCounts.size == height && rowCounts.all { it in 0..width }) { "row counts" }
        require(colCounts.size == width && colCounts.all { it in 0..height }) { "column counts" }
        require((monsters + chests).all { it in 0 until width * height }) { "given cell outside the grid" }
        require(monsters.none { it in chests }) { "monster on a chest" }
    }

    /** The whole puzzle on one line, read back by [decodePuzzle]. Saves and the campaign file rely on it: keep it stable. */
    fun encode() = listOf(
        "${width}x$height", rowCounts.joinToString(","), colCounts.joinToString(","),
        monsters.sorted().joinToString(","), chests.sorted().joinToString(","),
    ).joinToString("|")
}

/** Null when [text] is not a puzzle written by [Puzzle.encode]. */
fun decodePuzzle(text: String): Puzzle? = runCatching {
    fun numbers(part: String) = part.split(',').filter { it.isNotEmpty() }.map { it.toInt() }
    val parts = text.trim().split('|')
    val (width, height) = parts[0].split('x').map { it.toInt() }
    Puzzle(width, height, numbers(parts[1]).toIntArray(), numbers(parts[2]).toIntArray(), numbers(parts[3]).toSet(), numbers(parts[4]).toSet())
}.getOrNull()

/** The dungeon as one character per cell: '#' wall, '.' open, 'M' monster, 'C' chest. */
fun drawing(p: Puzzle, walls: BooleanArray) = String(
    CharArray(walls.size) { if (walls[it]) '#' else if (it in p.monsters) 'M' else if (it in p.chests) 'C' else '.' },
)

/** True when [walls] satisfies every rule of [p]. */
fun isSolved(p: Puzzle, walls: BooleanArray): Boolean {
    val w = p.width
    val h = p.height
    fun open(x: Int, y: Int) = x in 0 until w && y in 0 until h && !walls[y * w + x]

    for (y in 0 until h) if ((0 until w).count { walls[y * w + it] } != p.rowCounts[y]) return false
    for (x in 0 until w) if ((0 until h).count { walls[it * w + x] } != p.colCounts[x]) return false
    if (p.monsters.any { walls[it] } || p.chests.any { walls[it] }) return false

    // Treasure rooms: 3x3 open block holding exactly one chest, with exactly one opening around it.
    val room = BooleanArray(w * h)
    for (chest in p.chests) {
        val cx = chest % w
        val cy = chest / w
        var found = false
        for (ty in cy - 2..cy) for (tx in cx - 2..cx) {
            if (found || tx < 0 || ty < 0 || tx + 3 > w || ty + 3 > h) continue
            val cells = (0 until 9).map { (ty + it / 3) * w + tx + it % 3 }
            if (cells.any { walls[it] } || cells.count { it in p.chests } != 1) continue
            val exits = (0 until 3).sumOf { i ->
                listOf(open(tx + i, ty - 1), open(tx + i, ty + 3), open(tx - 1, ty + i), open(tx + 3, ty + i))
                    .count { it }
            }
            if (exits != 1) continue
            cells.forEach { room[it] = true }
            found = true
        }
        if (!found) return false
    }

    var openCount = 0
    var start = -1
    for (y in 0 until h) for (x in 0 until w) {
        val i = y * w + x
        if (walls[i]) continue
        openCount++
        start = i
        // Monsters sit in dead ends, and every dead end holds a monster.
        val neighbours = listOf(open(x - 1, y), open(x + 1, y), open(x, y - 1), open(x, y + 1)).count { it }
        if (if (i in p.monsters) neighbours != 1 else neighbours < 2) return false
        // Hallways are one cell wide: a 2x2 open block may only exist inside a treasure room.
        if (open(x + 1, y) && open(x, y + 1) && open(x + 1, y + 1) &&
            !(room[i] && room[i + 1] && room[i + w] && room[i + w + 1])
        ) return false
    }
    if (start < 0) return false

    // All open cells are connected.
    val seen = BooleanArray(w * h)
    val stack = ArrayDeque(listOf(start))
    seen[start] = true
    var reached = 0
    while (stack.isNotEmpty()) {
        val i = stack.removeLast()
        reached++
        val x = i % w
        val y = i / w
        for ((nx, ny) in listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)) {
            if (open(nx, ny) && !seen[ny * w + nx]) {
                seen[ny * w + nx] = true
                stack.add(ny * w + nx)
            }
        }
    }
    return reached == openCount
}
