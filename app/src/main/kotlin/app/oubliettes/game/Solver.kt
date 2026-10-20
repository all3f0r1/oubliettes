package app.oubliettes.game

private const val WALL = 1
private const val OPEN = 2

/**
 * The rules a deduction is made with, from the plainest to the one that asks for the most. [weight]
 * is what one such deduction adds to the [effort] of a grid.
 */
enum class Rule(val weight: Int) {
    /** A row or a column has all its walls, or needs every cell it has left. */
    COUNT(0),

    /** A monster has its way out, or a single cell left for it. */
    MONSTER(0),

    /** A cell that would be a dead end without a monster. */
    DEAD_END(1),

    /** An open cell with just two ways left, and it needs both. */
    PASSAGE(1),

    /** Three open cells of a 2x2 block. */
    BLOCK(1),

    /** The cells a treasure room covers wherever it still fits. */
    ROOM(2),

    /** The single exit of a treasure room. */
    EXIT(1),

    /** Cells that walls cut off from the rest of the dungeon. */
    CUT(3),

    /** A cell that breaks a rule one of the two ways, once what follows is worked out. */
    TRY(4),

    /** A cell that leads to the same cells both ways. */
    EITHER(8),
}

/**
 * One deduction: its [rule], the cells it is read [from] and those it gives, [walls] and [open].
 * [line] is the row of a [Rule.COUNT], or its column counted on from the last row. A try [assumes]
 * a wall or not on its cell, and [breaks] a rule [depth] cells further.
 */
class Step(
    val rule: Rule, val from: List<Int>, val walls: List<Int>, val open: List<Int>,
    val line: Int = -1, val assumes: Boolean = false, val depth: Int = 0, val breaks: Rule = Rule.COUNT,
)

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
    var probes = 0 // how many times [probe] had to try a cell both ways to get further
    private var cut = false // a cell changed since the dungeon was last checked to be in one piece
    private var placed: Place? = null // where [room] last found a room could only be

    private var doing = Rule.COUNT // the rule being applied
    var broke = Rule.COUNT // the one that was when the grid broke
        private set

    private fun fail() {
        if (!broken) broke = doing
        broken = true
    }

    private fun set(i: Int, value: Int) {
        if (c[i] == value) return
        if (c[i] != 0) {
            fail()
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
        doing = Rule.COUNT
        val cells = lines[index]
        val walls = cells.count { c[it] == WALL }
        val unknown = cells.count { c[it] == 0 }
        if (walls > counts[index] || walls + unknown < counts[index]) fail()
        else if (unknown > 0 && walls == counts[index]) fill(cells, OPEN)
        else if (unknown > 0 && walls + unknown == counts[index]) fill(cells, WALL)
    }

    private fun cell(i: Int) {
        if (c[i] == WALL) return
        doing = if (monster[i]) Rule.MONSTER else Rule.DEAD_END
        val around = neighbours[i]
        val open = around.count { c[it] == OPEN }
        val unknown = around.count { c[it] == 0 }
        if (monster[i]) {
            // A dead end: exactly one way out.
            if (open > 1 || open + unknown == 0) fail()
            else if (unknown > 0 && open == 1) fill(around, WALL)
            else if (open == 0 && unknown == 1) fill(around, OPEN)
        } else if (open + unknown < 2) {
            set(i, WALL) // it would be a dead end without a monster
        } else if (c[i] == OPEN && unknown > 0 && open + unknown == 2) {
            fill(around, OPEN)
        }
    }

    private fun block(index: Int) {
        doing = Rule.BLOCK
        val block = blocks[index]
        if (block.any { c[it] == WALL }) return
        val unknown = block.count { c[it] == 0 }
        if (unknown == 0) fail() else if (unknown == 1) fill(block, WALL)
    }

    /** A room is all open, and its ring all walls but one cell. */
    private fun room(index: Int) {
        doing = Rule.ROOM
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
        placed = only.takeIf { possible == 1 }
        if (only == null) {
            fail()
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
        doing = Rule.CUT
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

    /**
     * The plainest deduction [grid] allows, which is left as it is: the first rule of [Rule] that
     * gives a cell, and among tries the one that breaks a rule soonest. Null when there is none,
     * or when [grid] already breaks a rule.
     */
    fun step(grid: IntArray): Step? {
        val base = grid.copyOf()
        c = base
        broken = false

        fun made(rule: Rule, from: List<Int>, cells: List<Int>, line: Int = -1) =
            Step(rule, from, cells.filter { c[it] == WALL }, cells.filter { c[it] == OPEN }, line)

        /** The cells this rule gives, null when none. */
        fun given(rule: () -> Unit): List<Int>? {
            size = 0
            rule()
            return if (broken || size == 0) null else queue.take(size)
        }
        for (index in lines.indices) given { line(index) }?.let { return made(Rule.COUNT, lines[index].toList(), it, index) }
        if (broken) return null
        for (i in 0 until n) given { cell(i) }?.let {
            return made(if (monster[i]) Rule.MONSTER else if (i in it) Rule.DEAD_END else Rule.PASSAGE, listOf(i), it)
        }
        for (block in blocks.indices) given { block(block) }?.let { return made(Rule.BLOCK, blocks[block].toList(), it) }
        for (index in rooms.indices) given { room(index) }?.let { cells ->
            // Where the room is comes before its exit: one thing at a time.
            val place = placed
            val inside = if (place == null) cells else cells.filter { it in place.cells }
            return if (place == null || inside.isNotEmpty()) made(Rule.ROOM, listOf(p.chests.elementAt(index)), inside) else made(Rule.EXIT, place.cells.toList(), cells)
        }
        given { flood() }?.let { return made(Rule.CUT, it, it) }
        if (broken) return null

        // No rule gives anything as the grid stands: what does each unknown cell lead to?
        var best: Step? = null
        for (i in 0 until n) {
            if (base[i] != 0) continue
            val at = intArrayOf(i)
            val wall = base.copyOf().also { it[i] = WALL }
            val open = base.copyOf().also { it[i] = OPEN }
            val wallHolds = deduce(wall, at)
            val wallBroke = broke
            val openHolds = deduce(open, at)
            fun reach(branch: IntArray) = (0 until n).count { branch[it] != base[it] }
            val found = when {
                !wallHolds && !openHolds -> return null
                !wallHolds -> Step(Rule.TRY, at.toList(), emptyList(), at.toList(), assumes = true, depth = reach(wall), breaks = wallBroke)
                !openHolds -> Step(Rule.TRY, at.toList(), at.toList(), emptyList(), assumes = false, depth = reach(open), breaks = broke)
                else -> (0 until n).filter { base[it] == 0 && wall[it] == open[it] && wall[it] != 0 }.takeIf { it.isNotEmpty() }?.let { same ->
                    Step(Rule.EITHER, at.toList(), same.filter { wall[it] == WALL }, same.filter { wall[it] == OPEN }, depth = reach(wall) + reach(open))
                }
            } ?: continue
            if (best == null || found.rule < best.rule || (found.rule == best.rule && found.depth < best.depth)) best = found
        }
        return best
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
                    probes++
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
 * How hard [p] is to reason through: how many times solving it takes trying a cell both ways. None
 * when its rules alone, applied one after the other, give every cell.
 */
internal fun probes(p: Puzzle): Int = with(Deducer(p)) {
    val c = start
    if (deduce(c)) probe(c)
    probes
}

/**
 * The first deduction from the clues of [p] not yet marked by the player: 0 for unknown, 1 for a
 * wall, 2 for open. Player marks only skip completed steps; they are never premises or checked for
 * mistakes. An unrelated wrong mark therefore cannot turn a hint into an error detector.
 */
fun hint(p: Puzzle, marks: IntArray): Step? = with(Deducer(p)) {
    val c = start
    while (0 in c) {
        val next = step(c) ?: return@with null
        if (next.walls.any { marks[it] != WALL } || next.open.any { marks[it] != OPEN }) return@with next
        next.walls.forEach { c[it] = WALL }
        next.open.forEach { c[it] = OPEN }
    }
    null
}

/**
 * How [p] is solved by taking the plainest deduction there is, one after the other. Null when that
 * does not reach the end: a grid with several solutions, or none.
 */
fun steps(p: Puzzle): List<Step>? = with(Deducer(p)) {
    val c = start
    val steps = mutableListOf<Step>()
    while (0 in c) {
        val step = step(c) ?: return null
        step.walls.forEach { c[it] = WALL }
        step.open.forEach { c[it] = OPEN }
        steps += step
    }
    steps.takeIf { isSolved(p, BooleanArray(n) { c[it] == WALL }) }
}

/**
 * How much a player has to work to solve [p]: what each of its [steps] weighs, and for a try how far
 * it has to be followed. Counts and monsters, which are read off the grid, weigh nothing.
 */
// ponytail: the weights are a guess at what a player feels, tune them once people have played with them.
fun effort(steps: List<Step>) = steps.sumOf { it.rule.weight + if (it.rule >= Rule.TRY) minOf(it.depth, 12) else 0 }

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
