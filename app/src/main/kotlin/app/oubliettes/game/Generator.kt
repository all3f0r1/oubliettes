package app.oubliettes.game

import kotlin.math.abs
import kotlin.random.Random

/** Dungeons carved before [generate] gives up. */
const val MAX_TRIES = 200

/** Cells changed in one dungeon, to make its grid solvable, before another is carved. */
private const val REPAIRS = 150

/**
 * Deterministic: the same size and seed always give the same puzzle. It has exactly one solution,
 * which can be reached without a guess: see [stuck].
 * Null when [cancelled] answered true, or when [maxTries] dungeons were not enough.
 */
fun generate(width: Int, height: Int, seed: Long, maxTries: Int = MAX_TRIES, cancelled: () -> Boolean = { false }): Puzzle? {
    val rng = Random(seed xor (width.toLong() shl 32) xor (height.toLong() shl 48))
    repeat(maxTries) {
        if (cancelled()) return null
        val (walls, chests) = carve(width, height, rng) ?: return@repeat
        // A dungeon rarely gives a solvable grid as it comes, the less so the bigger it is, but most
        // of it does: rather than throw it away, change it where deduction gets stuck, and keep
        // every change that leaves no more cells stuck than before.
        var p = clues(width, height, walls, chests)
        var left = stuck(p) ?: return@repeat
        var repairs = REPAIRS
        // Opening a wall next to a hallway makes a dead end, whose monster is one more clue: the
        // easy way out. Only so many of those, or big grids end up crowded and easy.
        val cap = p.monsters.size + maxOf(2, p.monsters.size / 3)
        while (left.isNotEmpty() && repairs-- > 0) {
            if (cancelled()) return null
            val cell = left.random(rng)
            walls[cell] = !walls[cell]
            val changed = clues(width, height, walls, chests)
            val now = if (changed.monsters.size <= cap && isSolved(changed, walls) && thickWall(walls, width, height) == null) stuck(changed) else null
            if (now != null && now.size <= left.size) {
                p = changed
                left = now
            } else {
                walls[cell] = !walls[cell]
            }
        }
        if (left.isEmpty()) return p
    }
    return null
}

/** The grid of a dungeon: its counts, and a monster in every dead end. */
internal fun clues(w: Int, h: Int, walls: BooleanArray, chests: Set<Int>): Puzzle {
    fun isOpen(x: Int, y: Int) = x in 0 until w && y in 0 until h && !walls[y * w + x]
    // A cell of a treasure room has two open neighbours; a chest with one is not in a room, which [isSolved] refuses.
    val monsters = walls.indices.filter { i ->
        val x = i % w
        val y = i / w
        !walls[i] && i !in chests && listOf(isOpen(x - 1, y), isOpen(x + 1, y), isOpen(x, y - 1), isOpen(x, y + 1)).count { it } == 1
    }.toSet()
    return Puzzle(
        w, h,
        IntArray(h) { y -> (0 until w).count { walls[y * w + it] } },
        IntArray(w) { x -> (0 until h).count { walls[it * w + x] } },
        monsters, chests,
    )
}

/** One random dungeon as a grid, whatever its number of solutions. Null when the attempt broke a rule. */
internal fun layout(w: Int, h: Int, rng: Random): Puzzle? = carve(w, h, rng)?.let { (walls, chests) -> clues(w, h, walls, chests) }

/** Top-left cell of a 3x3 block made only of walls, or null when no wall is more than two cells thick. */
internal fun thickWall(walls: BooleanArray, w: Int, h: Int): Int? = walls.indices.firstOrNull { i ->
    i % w + 3 <= w && i / w + 3 <= h && (0 until 9).all { walls[i + it / 3 * w + it % 3] }
}

/** Carves one random dungeon: its walls and its chests. Null when the attempt broke a rule. */
private fun carve(w: Int, h: Int, rng: Random): Pair<BooleanArray, Set<Int>>? {
    val walls = BooleanArray(w * h) { true }
    val frozen = BooleanArray(w * h) // room surroundings: stay walls, except each room's exit
    val chests = mutableSetOf<Int>()

    fun isOpen(x: Int, y: Int) = x in 0 until w && y in 0 until h && !walls[y * w + x]

    val tops = mutableListOf<Pair<Int, Int>>()
    val exits = mutableListOf<Int>()
    repeat(rng.nextInt(w * h / 32 + 1)) {
        val tx = rng.nextInt(w - 2)
        val ty = rng.nextInt(h - 2)
        // Keep the 5x5 footprints apart so two rooms never share surrounding cells.
        if (tops.any { abs(it.first - tx) < 5 && abs(it.second - ty) < 5 }) return@repeat
        tops += tx to ty
        for (i in 0 until 9) {
            val cell = (ty + i / 3) * w + tx + i % 3
            walls[cell] = false
        }
        chests += (ty + rng.nextInt(3)) * w + tx + rng.nextInt(3)
        val around = (0 until 3).flatMap { listOf(tx + it to ty - 1, tx + it to ty + 3, tx - 1 to ty + it, tx + 3 to ty + it) }
            .filter { (x, y) -> x in 0 until w && y in 0 until h }
            .map { (x, y) -> y * w + x }
        around.forEach { frozen[it] = true }
        exits += around.random(rng).also { walls[it] = false }
    }
    val start = exits.firstOrNull() ?: rng.nextInt(w * h).also { walls[it] = false }

    fun neighbours(i: Int) = listOf(i - 1, i + 1, i - w, i + w)
        .filter { it in walls.indices && abs(it % w - i % w) + abs(it / w - i / w) == 1 }

    /** Open cells connected to [start]. */
    fun reached(): BooleanArray {
        val seen = BooleanArray(w * h)
        val stack = ArrayDeque(listOf(start))
        seen[start] = true
        while (stack.isNotEmpty()) {
            for (n in neighbours(stack.removeLast())) if (!walls[n] && !seen[n]) {
                seen[n] = true
                stack.add(n)
            }
        }
        return seen
    }

    fun canOpen(i: Int, seen: BooleanArray): Boolean {
        if (!walls[i] || frozen[i] || neighbours(i).none { seen[it] }) return false
        val x = i % w
        val y = i / w
        // Refuse to complete any of the four 2x2 blocks containing this cell.
        for (dy in -1..0) for (dx in -1..0) {
            if ((0 until 4).all { val bx = x + dx + it % 2; val by = y + dy + it / 2; (bx == x && by == y) || isOpen(bx, by) }) return false
        }
        return true
    }

    // Grow hallways from the start: mostly extend the last corridor (long hallways, few dead ends),
    // sometimes branch elsewhere, and head for any room that is not connected yet. Once the budget
    // is spent, keep digging only towards walls more than two cells thick, until none is left.
    var budget = (w * h * (0.35 + 0.2 * rng.nextDouble())).toInt()
    var last = -1
    while (true) {
        val seen = reached()
        val goal = exits.firstOrNull { !seen[it] }
        var thick: Int? = null
        if (goal == null && budget-- <= 0) thick = (thickWall(walls, w, h) ?: break) + w + 1 // its centre
        val all = walls.indices.filter { canOpen(it, seen) }
        if (all.isEmpty()) return null // boxed in: would be a degenerate, near-empty dungeon
        val near = all.filter { last in neighbours(it) }
        last = when {
            thick != null -> all.minBy { abs(it % w - thick % w) + abs(it / w - thick / w) }
            goal != null && rng.nextBoolean() -> all.minBy { abs(it % w - goal % w) + abs(it / w - goal / w) }
            near.isNotEmpty() && rng.nextInt(4) != 0 -> near.random(rng)
            else -> all.random(rng)
        }
        walls[last] = false
    }

    return (walls to chests).takeIf { isSolved(clues(w, h, walls, chests), walls) }
}
