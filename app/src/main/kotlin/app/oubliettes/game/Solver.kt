package app.oubliettes.game

/**
 * Returns up to [limit] solutions of [p], or null if the search gave up after [maxNodes] steps.
 * Rows are placed top to bottom as wall bitmasks (bit x = wall at column x).
 */
fun solutions(p: Puzzle, limit: Int = 2, maxNodes: Int = 300_000): List<BooleanArray>? {
    val w = p.width
    val h = p.height
    val full = (1 shl w) - 1

    val keepOpen = IntArray(h)
    val monsterMask = IntArray(h)
    for (i in p.monsters) {
        keepOpen[i / w] = keepOpen[i / w] or (1 shl i % w)
        monsterMask[i / w] = monsterMask[i / w] or (1 shl i % w)
    }
    for (i in p.chests) keepOpen[i / w] = keepOpen[i / w] or (1 shl i % w)

    // roomPossible[y] bit x: a 2x2 open block with top-left (x, y) could lie in a 3x3 room with a chest.
    val roomPossible = IntArray(h)
    for (i in p.chests) {
        val cx = i % w
        val cy = i / w
        for (y in maxOf(0, cy - 2)..minOf(h - 1, cy + 1)) for (x in maxOf(0, cx - 2)..minOf(w - 1, cx + 1)) {
            roomPossible[y] = roomPossible[y] or (1 shl x)
        }
    }

    val patterns = Array(h) { y ->
        (0..full).filter { it.countOneBits() == p.rowCounts[y] && it and keepOpen[y] == 0 }
    }
    val rows = IntArray(h)
    val colLeft = p.colCounts.copyOf()
    val found = mutableListOf<BooleanArray>()
    var nodes = 0

    fun openAt(y: Int) = if (y in 0 until h) rows[y].inv() and full else 0

    // Dead-end rule for row y; only valid once rows y-1, y and y+1 are placed.
    fun deadEndsOk(y: Int): Boolean {
        val open = openAt(y)
        val up = openAt(y - 1)
        val down = if (y + 1 < h) openAt(y + 1) else 0
        for (x in 0 until w) {
            if (open shr x and 1 == 0) continue
            val n = (open shr x + 1 and 1) + (if (x > 0) open shr x - 1 and 1 else 0) +
                (up shr x and 1) + (down shr x and 1)
            if (if (monsterMask[y] shr x and 1 == 1) n != 1 else n < 2) return false
        }
        return true
    }

    // Returns false to stop the whole search (enough solutions, or out of budget).
    fun place(y: Int): Boolean {
        if (y == h) {
            val walls = BooleanArray(w * h) { rows[it / w] shr it % w and 1 == 1 }
            if (isSolved(p, walls)) found += walls
            return found.size < limit
        }
        val rowsLeft = h - y - 1
        var cannot = 0 // columns already full
        var must = 0 // columns that need a wall in every remaining row
        for (x in 0 until w) {
            if (colLeft[x] == 0) cannot = cannot or (1 shl x)
            if (colLeft[x] == rowsLeft + 1) must = must or (1 shl x)
        }
        for (m in patterns[y]) {
            if (++nodes > maxNodes) return false
            if (m and cannot != 0 || m and must != must) continue
            rows[y] = m
            if (y > 0) {
                val both = openAt(y - 1) and openAt(y)
                if (both and (both shr 1) and roomPossible[y - 1].inv() != 0) continue
                if (!deadEndsOk(y - 1)) continue
            }
            if (y == h - 1 && !deadEndsOk(y)) continue
            for (x in 0 until w) colLeft[x] -= m shr x and 1
            val goOn = place(y + 1)
            for (x in 0 until w) colLeft[x] += m shr x and 1
            if (!goOn) return false
        }
        return true
    }

    place(0)
    return if (nodes > maxNodes) null else found
}
