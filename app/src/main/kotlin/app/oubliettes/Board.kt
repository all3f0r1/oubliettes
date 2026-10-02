package app.oubliettes

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import app.oubliettes.game.Puzzle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

internal val Bg = Color(0xFF1B1A22)
internal val Ink = Color(0xFFF2F0FA)
internal val Dim = Color(0xFF8D8AA3)
internal val Gold = Color(0xFFE0B84C)
internal val Red = Color(0xFFFF6B57)
private val Green = Color(0xFF7CC47A)
private val Unknown = Color(0xFF24222D)
internal val OpenFloor = Color(0xFF3D3A4E)

// Mark values for a cell.
internal const val WALL = 1
internal const val KNOWN_OPEN = 2

/**
 * A monster sprite: two body [frames] drawn without eyes, and where its [eyes] go, in the 24-unit
 * viewport of the drawables. The eyes are drawn on top so they can blink and look around.
 * [bob] is how far down the eyes sit on the second frame.
 */
internal class Monster(
    val frames: List<Int>,
    val eyes: List<Offset>,
    val radius: Float,
    val ball: Color,
    val pupil: Color,
    val lid: Color,
    val bob: Float = 0f,
)

internal val MONSTERS = listOf(
    Monster(
        listOf(R.drawable.monster_slime_0, R.drawable.monster_slime_1), listOf(Offset(9f, 12.5f), Offset(15f, 12.5f)), 1.8f,
        ball = Color(0xFFF4FFD0), pupil = Color(0xFF16301A), lid = Color(0xFF5FBF4A), bob = 1.6f,
    ),
    Monster(
        listOf(R.drawable.monster_ghost_0, R.drawable.monster_ghost_1), listOf(Offset(9.3f, 10.3f), Offset(14.7f, 10.3f)), 1.9f,
        ball = Color(0xFF24222D), pupil = Color(0xFF7CE8FF), lid = Color(0xFFDCD8F0),
    ),
    Monster(
        listOf(R.drawable.monster_imp_0, R.drawable.monster_imp_1), listOf(Offset(9.6f, 10.2f), Offset(14.4f, 10.2f)), 1.5f,
        ball = Color(0xFFFFE066), pupil = Bg, lid = Color(0xFFD9483B),
    ),
    Monster(
        listOf(R.drawable.monster_skull_0, R.drawable.monster_skull_1), listOf(Offset(8.8f, 10.3f), Offset(15.2f, 10.3f)), 2.2f,
        ball = Bg, pupil = Color(0xFFFF3B2F), lid = Color(0xFFE8E4D4),
    ),
    Monster(
        listOf(R.drawable.monster_bat_0, R.drawable.monster_bat_1), listOf(Offset(10.6f, 12.3f), Offset(13.4f, 12.3f)), 1.1f,
        ball = Color(0xFFFFE066), pupil = Bg, lid = Color(0xFF3F6FD0),
    ),
    Monster(
        listOf(R.drawable.monster_eye_0, R.drawable.monster_eye_1), listOf(Offset(12f, 12.5f)), 4.5f,
        ball = Color.White, pupil = Color(0xFF8A1020), lid = Color(0xFFF09A3A),
    ),
)

@Composable
internal fun monsterBodies(): List<List<Painter>> = MONSTERS.map { monster -> monster.frames.map { painterResource(it) } }

@Composable
internal fun torchFrames(): List<Painter> =
    listOf(R.drawable.torch_0, R.drawable.torch_1, R.drawable.torch_2).map { painterResource(it) }

/** The first frame is the bare chest; a glint crosses it on the next three. */
@Composable
internal fun chestFrames(): List<Painter> =
    listOf(R.drawable.chest_0, R.drawable.chest_1, R.drawable.chest_2, R.drawable.chest_3).map { painterResource(it) }

internal enum class Eye { OPEN, BLINK, LEFT, RIGHT }

/** What a monster's eyes do at [tick]: every 7 seconds a glance left then right, and two blinks. [index] staggers the monsters. */
internal fun eyeState(tick: Int, index: Int) = when ((tick + index * 13) % 47) {
    in 0..3 -> Eye.LEFT
    in 4..7 -> Eye.RIGHT
    20, 33 -> Eye.BLINK
    else -> Eye.OPEN
}

/** Draws monster number [kind] of [MONSTERS] in a square of side [cell]. [index] staggers its animation. */
internal fun DrawScope.drawMonster(kind: Int, bodies: List<List<Painter>>, tick: Int, index: Int, cell: Float) {
    val monster = MONSTERS[kind]
    val frame = (tick / 3 + index) % 2
    with(bodies[kind][frame]) { draw(Size(cell, cell)) }
    val unit = cell / 24
    val r = monster.radius * unit
    val state = eyeState(tick, index)
    for (eye in monster.eyes) {
        val centre = Offset(eye.x, eye.y + frame * monster.bob) * unit
        if (state == Eye.BLINK) {
            drawCircle(monster.lid, r * 1.05f, centre)
            drawLine(Bg, centre - Offset(r, 0f), centre + Offset(r, 0f), unit * 0.6f)
        } else {
            drawCircle(monster.ball, r, centre)
            val look = when (state) {
                Eye.LEFT -> -0.5f * r
                Eye.RIGHT -> 0.5f * r
                else -> 0f
            }
            // Slit pupil, like a snake's.
            drawOval(monster.pupil, centre + Offset(look - r * 0.35f, -r * 0.8f), Size(r * 0.7f, r * 1.6f))
        }
        // A frown: each brow slants down towards the middle of the face and cuts into the eye.
        if (monster.eyes.size > 1) {
            val inward = if (eye.x < 12) 1f else -1f
            drawLine(
                Bg, centre + Offset(-inward * r * 1.3f, -r * 1.7f), centre + Offset(inward * r * 1.1f, -r * 0.6f),
                unit * 0.9f, StrokeCap.Round,
            )
        }
    }
}

/**
 * Tap cycles a cell: unknown, wall, known open. Dragging paints the value set by the first cell, along
 * its row or column, into unknown cells and into the [repaintable] ones (the cells of the previous
 * action, so a drag can be corrected by dragging again). [onStroke] is called once before each
 * gesture's first change. [variety] picks which monsters and wall tiles a grid shows; [focus] cells
 * get a gold outline. [celebrate] shows the finished dungeon: no dots, monsters hopping, chest pulsing.
 */
@Composable
internal fun Board(
    p: Puzzle,
    marks: IntArray,
    locked: Boolean,
    celebrate: Boolean = false,
    variety: Int = 0,
    focus: List<Int> = emptyList(),
    onStroke: () -> Unit = {},
    // Marks as of right now. [marks] is only as fresh as the last recomposition, which is too old
    // for a second tap landing before the next frame.
    liveMarks: () -> IntArray = { marks },
    repaintable: () -> Set<Int> = { emptySet() },
    onPaint: (cell: Int, value: Int) -> Unit = { _, _ -> },
) {
    // Mostly plain bricks, so the odd cracked or mossy one reads as detail rather than noise.
    val plain = painterResource(R.drawable.wall)
    val walls = listOf(plain, plain, plain, painterResource(R.drawable.wall_cracked), plain, plain, painterResource(R.drawable.wall_mossy))
    val chest = chestFrames()
    val bodies = monsterBodies()
    val tick = rememberTick()
    val monsterOrder = p.monsters.sorted() // neighbouring monsters get different faces
    val measurer = rememberTextMeasurer()
    val currentMarks by rememberUpdatedState(liveMarks)
    val currentRepaintable by rememberUpdatedState(repaintable)
    val currentPaint by rememberUpdatedState(onPaint)
    val currentStroke by rememberUpdatedState(onStroke)
    val beat = if (celebrate) {
        rememberInfiniteTransition(label = "celebrate")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "beat")
    } else {
        null
    }
    val columns = p.width + 1 // one extra row and column for the wall counts

    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(columns.toFloat() / (p.height + 1))
            .pointerInput(p, locked) {
                if (locked) return@pointerInput
                val cell = size.width.toFloat() / columns
                fun cellAt(o: Offset): Int? {
                    if (o.x < cell || o.y < cell) return null
                    val x = (o.x / cell).toInt() - 1
                    val y = (o.y / cell).toInt() - 1
                    val i = y * p.width + x
                    return i.takeIf { x < p.width && y < p.height && i !in p.monsters && i !in p.chests }
                }
                awaitEachGesture {
                    val first = cellAt(awaitFirstDown().position) ?: return@awaitEachGesture
                    val firstX = first % p.width
                    val firstY = first / p.width
                    val value = (currentMarks()[first] + 1) % 3
                    val repaint = currentRepaintable() // read before this gesture becomes the last action
                    currentStroke()
                    currentPaint(first, value)
                    // The drag locks onto the row or the column it first moves along: no zig-zag.
                    var alongRow: Boolean? = null
                    do {
                        val event = awaitPointerEvent()
                        for (change in event.changes) {
                            if (change.pressed) {
                                val x = ((change.position.x / cell).toInt() - 1).coerceIn(0, p.width - 1)
                                val y = ((change.position.y / cell).toInt() - 1).coerceIn(0, p.height - 1)
                                if (alongRow == null && (x != firstX || y != firstY)) alongRow = abs(x - firstX) >= abs(y - firstY)
                                val target = when (alongRow) {
                                    true -> firstY * p.width + x
                                    false -> y * p.width + firstX
                                    null -> first
                                }
                                // Unknown cells, plus those of the previous action: a drag never overwrites older marks.
                                val now = currentMarks()[target]
                                if (now != value && (now == 0 || target in repaint) && target !in p.monsters && target !in p.chests) {
                                    currentPaint(target, value)
                                }
                            }
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val cell = size.width / columns
        val style = TextStyle(fontSize = (cell * 0.58f).toSp(), fontFamily = Almendra, fontWeight = FontWeight.Bold)

        fun count(want: Int, have: Int, centerX: Float, centerY: Float) {
            val text = measurer.measure(want.toString(), style)
            val color = if (have == want) Green else if (have > want) Red else Ink
            drawText(text, color, Offset(centerX - text.size.width / 2f, centerY - text.size.height / 2f))
        }
        for (x in 0 until p.width) {
            count(p.colCounts[x], (0 until p.height).count { marks[it * p.width + x] == WALL }, (x + 1.5f) * cell, cell / 2)
        }
        for (y in 0 until p.height) {
            count(p.rowCounts[y], (0 until p.width).count { marks[y * p.width + it] == WALL }, cell / 2, (y + 1.5f) * cell)
        }

        val t = beat?.value ?: 0f
        val frame = tick.value
        for (i in marks.indices) {
            val left = (i % p.width + 1) * cell
            val top = (i / p.width + 1) * cell
            val given = i in p.chests || i in p.monsters
            val floor = if (given || marks[i] == KNOWN_OPEN || (celebrate && marks[i] != WALL)) OpenFloor else Unknown
            drawRect(floor, Offset(left + 1, top + 1), Size(cell - 2, cell - 2))
            // Celebration: monsters hop one after the other, chests pulse.
            val moving = celebrate && marks[i] != WALL
            when {
                marks[i] == WALL -> translate(left, top) { with(walls[(i * 5 + variety).mod(walls.size)]) { draw(Size(cell, cell)) } }
                i in p.chests -> translate(left, top) {
                    val pulse = if (moving) 1f + 0.1f * sin(2 * PI.toFloat() * t) else 1f
                    scale(pulse, Offset(cell / 2, cell / 2)) { with(chest[(frame / 2 + i).mod(chest.size)]) { draw(Size(cell, cell)) } }
                }
                i in p.monsters -> {
                    val order = monsterOrder.indexOf(i)
                    val hop = if (moving) abs(sin(PI.toFloat() * (t + order * 0.37f))) * cell * 0.16f else 0f
                    translate(left, top - hop) { drawMonster((order + variety).mod(MONSTERS.size), bodies, frame, order, cell) }
                }
            }
            if (marks[i] == KNOWN_OPEN && !celebrate) drawCircle(Ink, cell * 0.09f, Offset(left + cell / 2, top + cell / 2))
        }
        for (i in focus) {
            val inset = cell * 0.06f
            drawRect(
                Gold,
                Offset((i % p.width + 1) * cell + inset, (i / p.width + 1) * cell + inset),
                Size(cell - 2 * inset, cell - 2 * inset),
                style = Stroke(cell * 0.07f),
            )
        }
    }
}
