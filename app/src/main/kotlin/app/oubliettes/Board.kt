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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
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
private val OpenFloor = Color(0xFF3D3A4E)

// Mark values for a cell.
internal const val WALL = 1
internal const val KNOWN_OPEN = 2

/**
 * Tap cycles a cell: unknown, wall, known open. Dragging paints the value set by the first cell, along
 * its row or column, into unknown cells only. [onStroke] is called once before each gesture's first
 * change. [variety] picks which monsters and wall tiles a grid shows; [focus] cells get a gold outline.
 * [celebrate] shows the finished dungeon: no dots, monsters and chest moving.
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
    onPaint: (cell: Int, value: Int) -> Unit = { _, _ -> },
) {
    // Mostly plain bricks, so the odd cracked or mossy one reads as detail rather than noise.
    val plain = painterResource(R.drawable.wall)
    val walls = listOf(plain, plain, plain, painterResource(R.drawable.wall_cracked), plain, plain, painterResource(R.drawable.wall_mossy))
    val chest = painterResource(R.drawable.chest)
    val monsters = listOf(
        painterResource(R.drawable.monster_slime),
        painterResource(R.drawable.monster_ghost),
        painterResource(R.drawable.monster_imp),
        painterResource(R.drawable.monster_skull),
        painterResource(R.drawable.monster_bat),
        painterResource(R.drawable.monster_eye),
    )
    val monsterOrder = p.monsters.sorted() // neighbouring monsters get different faces
    val measurer = rememberTextMeasurer()
    val currentMarks by rememberUpdatedState(liveMarks)
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
                                // Only unknown cells: a drag never overwrites what is already marked.
                                if (value != 0 && currentMarks()[target] == 0 && target !in p.monsters && target !in p.chests) {
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
        val style = TextStyle(fontSize = (cell * 0.52f).toSp(), fontWeight = FontWeight.Black)

        fun count(want: Int, have: Int, centerX: Float, centerY: Float) {
            val text = measurer.measure(want.toString(), style)
            val color = if (have == want) Green else if (have > want) Red else Ink
            val topLeft = Offset(centerX - text.size.width / 2f, centerY - text.size.height / 2f)
            // Drawn four times, slightly apart: heavy digits even when the system font has no black weight.
            val spread = cell * 0.012f
            for (dx in listOf(-spread, spread)) for (dy in listOf(-spread, spread)) {
                drawText(text, color, topLeft + Offset(dx, dy))
            }
        }
        for (x in 0 until p.width) {
            count(p.colCounts[x], (0 until p.height).count { marks[it * p.width + x] == WALL }, (x + 1.5f) * cell, cell / 2)
        }
        for (y in 0 until p.height) {
            count(p.rowCounts[y], (0 until p.width).count { marks[y * p.width + it] == WALL }, cell / 2, (y + 1.5f) * cell)
        }

        val t = beat?.value ?: 0f
        for (i in marks.indices) {
            val left = (i % p.width + 1) * cell
            val top = (i / p.width + 1) * cell
            val given = i in p.chests || i in p.monsters
            val floor = if (given || marks[i] == KNOWN_OPEN || (celebrate && marks[i] != WALL)) OpenFloor else Unknown
            drawRect(floor, Offset(left + 1, top + 1), Size(cell - 2, cell - 2))
            val painter = when {
                marks[i] == WALL -> walls[(i * 5 + variety).mod(walls.size)]
                i in p.chests -> chest
                i in p.monsters -> monsters[(monsterOrder.indexOf(i) + variety).mod(monsters.size)]
                else -> null
            }
            // Celebration: monsters hop one after the other, chests pulse.
            val moving = celebrate && marks[i] != WALL
            val hop = if (moving && i in p.monsters) abs(sin(PI.toFloat() * (t + monsterOrder.indexOf(i) * 0.37f))) * cell * 0.16f else 0f
            val pulse = if (moving && i in p.chests) 1f + 0.1f * sin(2 * PI.toFloat() * t) else 1f
            if (painter != null) {
                translate(left, top - hop) {
                    scale(pulse, Offset(cell / 2, cell / 2)) { with(painter) { draw(Size(cell, cell)) } }
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
