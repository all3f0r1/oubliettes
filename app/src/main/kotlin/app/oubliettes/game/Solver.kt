package app.oubliettes.game

private const val WALL = 1
private const val OPEN = 2

/**
 * What can be worked out of [p] without guessing. A grid is an array with a cell's state: 0 while
 * unknown, [WALL] or [OPEN].
 *
 * [deduce] applies every rule for as long as that gives something. [probe] then tries each unknown
 * cell both ways: a value that breaks a rule leaves the other, and whatever both values lead to holds.
 */
private class Deducer(val p: Puzzle) {
    val w = p.width
    val h = p.height
    val n = w * h
    val start get() = IntArray(n) { if (it in p.monsters || it in p.chests) OPEN else 0 }

    private val monster = BooleanArray(n) { it in p.monsters }
    private val lines = List(h) { y -> IntArray(w) { y * w + it } } + List(w) { x -> IntArray(h) { it * w + x } }
    private val counts = p.rowCounts + p.colCounts
    private val neighbours = Array(n) { i ->
        listOfNotNull(
            (i - 1).takeIf { i % w > 0 }, (i + 1).takeIf { i % w < w - 1 }, (i - w).takeIf { i >= w }, (i + w).takeIf { i + w < n },
        ).toIntArray()
    }

    // The 2x2 blocks that no 3x3 room around a chest could hold: never all open.
    private val blocks = (0 until n).filter { i ->
        i % w < w - 1 && i / w < h - 1 && p.chests.none { i % w - it % w in -2..1 && i / w - it / w in -2..1 }
    }.map { intArrayOf(it, it + 1, it + w, it + w + 1) }

    /** Where the 3x3 room of a chest may be: the [cells] of the room, and the [ring] around it. */
    private class Place(val cells: IntArray, val ring: IntArray)

    private val rooms = p.chests.map { chest ->
        (0 until 9).map { (chest / w - it / 3) * w + chest % w - it % 3 }.filter { top ->
            val x = top % w
            val y = top / w
            top >= 0 && x <= chest % w && x + 3 <= w && y + 3 <= h &&
                (0 until 9).all { val cell = top + it / 3 * w + it % 3; cell == chest || (cell !in p.chests && !monster[cell]) }
        }.map { top ->
            val x = top % w
            val y = top / w
            val ring = (0 until 3).flatMap { listOf(x + it to y - 1, x + it to y + 3, x - 1 to y + it, x + 3 to y + it) }
                .filter { (rx, ry) -> rx in 0 until w && ry in 0 until h }.map { (rx, ry) -> ry * w + rx }
            Place(IntArray(9) { top + it / 3 * w + it % 3 }, ring.toIntArray())
        }
    }

    // What to look at again when a cell changes.
    private val blocksOf = Array(n) { i -> blocks.indices.filter { i in blocks[it] }.toIntArray() }
    private val roomsOf = Array(n) { i -> rooms.indices.filter { k -> rooms[k].any { i in it.cells || i in it.ring } }.toIntArray() }

    private val inside = IntArray(n)
    private val seen = BooleanArray(n)
    private val queue = IntArray(2 * n) // the cells that changed, whose rules are yet to be looked at again
    private var size = 0
    private var c = IntArray(0)
    private var broken = false
    private var cut = false // a cell changed since the dungeon was last checked to be in one piece

    private fun set(i: Int, value: Int) {
        if (c[i] == value) return
        if (c[i] != 0) {
            broken = true
            return
        }
        c[i] = value
        queue[size++] = i
        cut = true
    }

    private fun fill(cells: IntArray, value: Int) {
        for (i in cells) if (c[i] == 0) set(i, value)
    }

    private fun line(index: Int) {
        val cells = lines[index]
        val walls = cells.count { c[it] == WALL }
        val unknown = cells.count { c[it] == 0 }
        if (walls > counts[index] || walls + unknown < counts[index]) broken = true
        else if (unknown > 0 && walls == counts[index]) fill(cells, OPEN)
        else if (unknown > 0 && walls + unknown == counts[index]) fill(cells, WALL)
    }

    private fun cell(i: Int) {
        if (c[i] == WALL) return
        val around = neighbours[i]
        val open = around.count { c[it] == OPEN }
        val unknown = around.count { c[it] == 0 }
        if (monster[i]) {
            // A dead end: exactly one way out.
            if (open > 1 || open + unknown == 0) broken = true
            else if (unknown > 0 && open == 1) fill(around, WALL)
            else if (open == 0 && unknown == 1) fill(around, OPEN)
        } else if (open + unknown < 2) {
            set(i, WALL) // it would be a dead end without a monster
        } else if (c[i] == OPEN && unknown > 0 && open + unknown == 2) {
            fill(around, OPEN)
        }
    }

    private fun block(index: Int) {
        val block = blocks[index]
        if (block.any { c[it] == WALL }) return
        val unknown = block.count { c[it] == 0 }
        if (unknown == 0) broken = true else if (unknown == 1) fill(block, WALL)
    }

    /** A room is all open, and its ring all walls but one cell. */
    private fun room(index: Int) {
        val places = rooms[index]
        var possible = 0
        var only: Place? = null
        for (place in places) for (cell in place.cells) inside[cell] = 0
        for (place in places) {
            val ways = place.ring.count { c[it] == OPEN }
            if (place.cells.any { c[it] == WALL } || ways > 1 || ways + place.ring.count { c[it] == 0 } == 0) continue
            possible++
            only = place
            for (cell in place.cells) inside[cell]++
        }
        if (only == null) {
            broken = true
            return
        }
        // The cells every place left shares are open.
        for (cell in only.cells) if (inside[cell] == possible) set(cell, OPEN)
        if (possible == 1) {
            val ways = only.ring.count { c[it] == OPEN }
            val unknown = only.ring.count { c[it] == 0 }
            if (unknown > 0 && ways == 1) fill(only.ring, WALL) else if (ways == 0 && unknown == 1) fill(only.ring, OPEN)
        }
    }

    /** One dungeon: what the walls cut off from an open cell cannot be open. */
    private fun flood() {
        val from = c.indexOf(OPEN)
        if (from < 0) return
        seen.fill(false)
        seen[from] = true
        // The far end of the queue is free: it only ever holds as many cells as there are.
        var top = queue.size
        queue[--top] = from
        while (top < queue.size) {
            for (next in neighbours[queue[top++]]) if (c[next] != WALL && !seen[next]) {
                seen[next] = true
                queue[--top] = next
            }
        }
        for (i in 0 until n) if (!seen[i] && c[i] != WALL) set(i, WALL)
    }

    /**
     * Deduces all it can in [grid]. False when a rule is broken. When only the cells [changed] since
     * [grid] was last deduced, rules are looked at from there alone.
     */
    fun deduce(grid: IntArray, changed: IntArray? = null): Boolean {
        c = grid
        broken = false
        cut = true
        size = 0
        if (changed == null) {
            for (index in lines.indices) line(index)
            for (i in 0 until n) cell(i)
            for (index in blocks.indices) block(index)
            for (index in rooms.indices) room(index)
        } else {
            for (i in changed) queue[size++] = i
        }
        var head = 0
        while (!broken) {
            while (head < size && !broken) {
                val i = queue[head++]
                line(i / w)
                line(h + i % w)
                cell(i)
                for (next in neighbours[i]) cell(next)
                for (index in blocksOf[i]) block(index)
                for (index in roomsOf[i]) room(index)
            }
            if (broken || !cut) break
            cut = false
            flood()
        }
        return !broken
    }

    /** Tries every unknown cell of [c] both ways, for as long as that settles cells. False when a rule is broken. */
    fun probe(c: IntArray): Boolean {
        var settled = true
        while (settled) {
            settled = false
            for (i in 0 until n) {
                if (c[i] != 0) continue
                val at = intArrayOf(i)
                val wall = c.copyOf().also { it[i] = WALL }
                val open = c.copyOf().also { it[i] = OPEN }
                val wallHolds = deduce(wall, at)
                val openHolds = deduce(open, at)
                if (!wallHolds && !openHolds) return false
                val found = (0 until n).filter { cell ->
                    val value = if (!wallHolds) open[cell] else if (!openHolds) wall[cell] else if (wall[cell] == open[cell]) wall[cell] else 0
                    (c[cell] == 0 && value != 0).also { if (it) c[cell] = value }
                }
                if (found.isNotEmpty()) {
                    settled = true
                    if (!deduce(c, found.toIntArray())) return false
                }
            }
        }
        return true
    }
}

/**
 * The cells of [p] that deduction leaves unknown: none when the grid can be solved without a guess,
 * which also proves its solution is the only one. Null when [p] has no solution at all.
 */
internal fun stuck(p: Puzzle): List<Int>? = with(Deducer(p)) {
    val c = start
    if (deduce(c) && probe(c)) c.indices.filter { c[it] == 0 } else null
}

/**
 * Returns up to [limit] solutions of [p], or null if the search gave up after [maxNodes] guesses.
 * A cell is only guessed, both ways, when deduction runs dry. [isSolved] has the last word on full grids.
 */
fun solutions(p: Puzzle, limit: Int = 2, maxNodes: Int = 300_000): List<BooleanArray>? = with(Deducer(p)) {
    val found = mutableListOf<BooleanArray>()
    var nodes = 0

    // Returns false to stop the whole search (enough solutions, or out of budget).
    fun search(c: IntArray): Boolean {
        if (!deduce(c) || !probe(c)) return true
        val guess = c.indexOf(0)
        if (guess < 0) {
            val walls = BooleanArray(n) { c[it] == WALL }
            if (isSolved(p, walls)) found += walls
            return found.size < limit
        }
        if (++nodes > maxNodes) return false
        return search(c.copyOf().also { it[guess] = WALL }) && search(c.also { it[guess] = OPEN })
    }

    search(start)
    if (nodes > maxNodes) null else found
}
