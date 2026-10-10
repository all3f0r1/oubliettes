package app.oubliettes

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.magnifier
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

internal val Bg = Color(0xFF1B1A22)
internal val Ink = Color(0xFFF2F0FA)
internal val Dim = Color(0xFF8D8AA3)
internal val Gold = Color(0xFFE0B84C)
internal val Red = Color(0xFFFF6B57)
private val Unknown = Color(0xFF24222D)
internal val OpenFloor = Color(0xFF3D3A4E)
private val Joint = Color(0xFF312E40) // between the flagstones of an open floor
private val Shade = Color(0x47000000) // what stands above the floor darkens it
private val Vignette = Color(0x2E000000)

/**
 * A monster sprite: two body [frames] drawn without eyes, and where its [eyes] go, in the 24-unit
 * viewport of the drawables. The eyes are drawn on top so they can blink and look around.
 * [bob] is how far down the eyes sit on the second frame. One that [flies] drifts over the floor
 * instead of standing on it.
 */
internal class Monster(
    val frames: List<Int>,
    val eyes: List<Offset>,
    val radius: Float,
    val ball: Color,
    val pupil: Color,
    val lid: Color,
    val bob: Float = 0f,
    val flies: Boolean = false,
)

internal val MONSTERS = listOf(
    Monster(
        listOf(R.drawable.monster_slime_0, R.drawable.monster_slime_1), listOf(Offset(9f, 12.5f), Offset(15f, 12.5f)), 1.8f,
        ball = Color(0xFFF4FFD0), pupil = Color(0xFF16301A), lid = Color(0xFF5FBF4A), bob = 1.6f,
    ),
    Monster(
        listOf(R.drawable.monster_ghost_0, R.drawable.monster_ghost_1), listOf(Offset(9.3f, 10.3f), Offset(14.7f, 10.3f)), 1.9f,
        ball = Color(0xFF24222D), pupil = Color(0xFF7CE8FF), lid = Color(0xFFDCD8F0), flies = true,
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
        ball = Color(0xFFFFE066), pupil = Bg, lid = Color(0xFF3F6FD0), flies = true,
    ),
    Monster(
        listOf(R.drawable.monster_eye_0, R.drawable.monster_eye_1), listOf(Offset(12f, 12.5f)), 4.5f,
        ball = Color.White, pupil = Color(0xFF8A1020), lid = Color(0xFFF09A3A), flies = true,
    ),
)

@Composable
internal fun monsterBodies(): List<List<Painter>> = MONSTERS.map { monster -> monster.frames.map { painterResource(it) } }

/** In the order the flame goes through them: it leans one way, stretches, leans the other, shrinks. */
@Composable
internal fun torchFrames(): List<Painter> =
    listOf(R.drawable.torch_0, R.drawable.torch_1, R.drawable.torch_3, R.drawable.torch_2, R.drawable.torch_5, R.drawable.torch_4)
        .map { painterResource(it) }

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

/** The shadow of what stands in a square of side [cell], smaller the further it is [lift]ed off the floor. */
internal fun DrawScope.drawShadow(cell: Float, lift: Float = 0f) {
    val width = cell * 0.58f - lift
    drawOval(Shade, Offset((cell - width) / 2, cell * 0.83f), Size(width, cell * 0.12f))
}

/**
 * Draws monster number [kind] of [MONSTERS] in a square of side [cell], [lift]ed that far off its
 * floor. [index] staggers its animation. As [time] goes by it breathes, and drifts when it flies.
 * On the [alert] it is ready to strike whoever comes near: reared up, shaking, its gesture over and
 * over, lunging now and then, and its eyes never leave you.
 */
internal fun DrawScope.drawMonster(
    kind: Int, bodies: List<List<Painter>>, tick: Int, index: Int, cell: Float, time: Float = 0f, lift: Float = 0f, alert: Boolean = false,
) {
    val monster = MONSTERS[kind]
    // Each one at its own pace, so that no two keep moving together. Its second frame is a gesture
    // of half a second, every three seconds or so.
    val pace = 0.75f + (index * 5 % 7) * 0.09f
    val frame = if (alert) (tick + index) % 2 else if ((tick + index * 11) % (17 + index * 5 % 9) < 3) 1 else 0
    val drift = if (monster.flies) cell * 0.025f * (1f + sin(time * 1.5f * pace + index * 2.3f)) else 0f
    // Wider as it gets shorter, its feet where they were: barely, it only has to look alive.
    val swell = 0.018f * sin(time * 1.9f * pace + index * 1.7f)
    // A lunge is short and comes back slowly; the shaking never stops.
    val lunge = if (alert) 0.12f + 0.14f * sin(time * 2.6f * pace + index * 1.9f).coerceAtLeast(0f).pow(6) else 0f
    val shake = if (alert) cell * 0.025f * sin(time * 31f + index * 2.1f) else 0f
    drawShadow(cell, lift + drift)
    translate(shake, -lift - drift) {
        scale(1f + swell + lunge, 1f - swell + lunge, Offset(cell / 2, cell * 0.92f)) {
            with(bodies[kind][frame]) { draw(Size(cell, cell)) }
            drawEyes(monster, tick, index, cell, frame, alert = alert)
        }
    }
}

/**
 * The flagstones of an open cell at [left], [top]: one of four layings, by its [seed]. Then the shade
 * of the walls standing right [above] it and on its left ([beside]), which is what gives them height.
 */
private fun DrawScope.drawFloor(seed: Int, left: Float, top: Float, cell: Float, paved: Boolean, above: Boolean, beside: Boolean) {
    val joint = cell * 0.035f
    fun across(y: Float, from: Float = 0f, to: Float = 1f) =
        drawLine(Joint, Offset(left + 1 + (cell - 2) * from, top + cell * y), Offset(left + 1 + (cell - 2) * to, top + cell * y), joint)
    fun down(x: Float, from: Float = 0f, to: Float = 1f) =
        drawLine(Joint, Offset(left + cell * x, top + 1 + (cell - 2) * from), Offset(left + cell * x, top + 1 + (cell - 2) * to), joint)
    if (paved) when (seed.mod(4)) {
        0 -> { across(0.5f); down(0.36f, to = 0.5f); down(0.66f, from = 0.5f) }
        1 -> { down(0.5f); across(0.38f, to = 0.5f); across(0.64f, from = 0.5f) }
        2 -> { across(0.62f); down(0.55f, from = 0.62f) }
        else -> { across(0.34f); down(0.42f, to = 0.34f) }
    }
    if (above) {
        drawRect(Shade, Offset(left + 1, top + 1), Size(cell - 2, cell * 0.12f))
        drawRect(Shade, Offset(left + 1, top + 1), Size(cell - 2, cell * 0.26f))
    }
    if (beside) drawRect(Shade, Offset(left + 1, top + 1), Size(cell * 0.1f, cell - 2))
}

/**
 * The eyes of a [monster] where its body would have them. [lurking] in the dark, there is no body: a blink shows nothing.
 * On the [alert] they stare without a blink, the pupils thin and the brows low.
 */
internal fun DrawScope.drawEyes(monster: Monster, tick: Int, index: Int, cell: Float, frame: Int = 0, lurking: Boolean = false, alert: Boolean = false) {
    val unit = cell / 24
    val r = monster.radius * unit
    val state = if (alert) Eye.OPEN else eyeState(tick, index)
    val slit = if (alert) 0.4f else 0.7f
    if (lurking && state == Eye.BLINK) return
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
            drawOval(monster.pupil, centre + Offset(look - r * slit / 2, -r * 0.8f), Size(r * slit, r * 1.6f))
        }
        // A frown: each brow slants down towards the middle of the face and cuts into the eye.
        if (monster.eyes.size > 1) {
            val inward = if (eye.x < 12) 1f else -1f
            drawLine(
                Bg, centre + Offset(-inward * r * 1.3f, -r * 1.7f), centre + Offset(inward * r * 1.1f, -r * if (alert) 0.2f else 0.6f),
                unit * if (alert) 1.4f else 0.9f, StrokeCap.Round,
            )
        }
    }
}

/**
 * Tap cycles a cell: unknown, wall, known open, or lays the [brush] when there is one (see
 * [Session.begin]). Dragging paints the value set by the first cell, along its row or column (see
 * [Session.dragTo]); only the finger that started a stroke draws it. [onPaint]
 * is called every time cells were painted, [onStroke] once the stroke is over, with whether it solved
 * the grid. [variety] picks which monsters and wall tiles a grid shows; [focus] cells get a gold
 * outline, and those among them still [toMark] are said so to screen readers. [wrong] cells are
 * crossed in red. [celebrate] shows the finished dungeon: no dots, monsters on the alert, chest pulsing.
 *
 * The grid is one drawing, so every count and every cell is doubled by an invisible node that
 * describes it to accessibility services and lets them mark it.
 */
@Composable
internal fun Board(
    session: Session,
    locked: Boolean,
    celebrate: Boolean = false,
    variety: Int = 0,
    focus: List<Int> = emptyList(),
    toMark: Set<Int> = emptySet(),
    wrong: Set<Int> = emptySet(),
    brush: Int = 0,
    onPaint: () -> Unit = {},
    onStroke: (won: Boolean) -> Unit = {},
) {
    val p = session.puzzle
    val marks = session.marks
    // Mostly plain bricks, so the odd cracked or mossy one reads as detail rather than noise.
    val plain = painterResource(R.drawable.wall)
    val walls = listOf(
        plain, painterResource(R.drawable.wall_worn), plain, painterResource(R.drawable.wall_cracked),
        plain, painterResource(R.drawable.wall_chipped), painterResource(R.drawable.wall_mossy),
    )
    val chest = chestFrames()
    val bodies = monsterBodies()
    val tick = rememberTick()
    val clock = rememberClock()
    val monsterOrder = p.monsters.sorted() // neighbouring monsters get different faces
    val measurer = rememberTextMeasurer()
    val currentPaint by rememberUpdatedState(onPaint)
    val currentStroke by rememberUpdatedState(onStroke)
    val currentBrush by rememberUpdatedState(brush)
    val beat = if (celebrate && !Comfort.calm) {
        rememberInfiniteTransition(label = "celebrate")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "beat")
    } else {
        null
    }
    val columns = p.width + 1 // one extra row and column for the wall counts
    // The cell touched last: its row and its column are lit up to their counts.
    var touched by remember(session) { mutableStateOf<Int?>(null) }
    // Where the finger is while it draws, for the magnifier.
    var finger by remember { mutableStateOf(Offset.Unspecified) }
    var boardWidth by remember { mutableIntStateOf(0) }
    // Cells that small hide under a fingertip: 10×10 and 12×12 on a phone.
    val magnify = Comfort.magnifier && boardWidth > 0 && with(LocalDensity.current) { boardWidth.toDp() } / columns < 32.dp

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(columns.toFloat() / (p.height + 1))
            .onSizeChanged { boardWidth = it.width }
            // A row drawn from the last column starts next to the screen edge: not a system back gesture.
            .then(if (locked) Modifier else Modifier.systemGestureExclusion())
            .then(
                if (magnify) {
                    Modifier.magnifier(
                        sourceCenter = { finger },
                        magnifierCenter = { if (finger.isSpecified) finger - Offset(0f, 76.dp.toPx()) else Offset.Unspecified },
                        zoom = 2f,
                        size = DpSize(104.dp, 104.dp),
                        cornerRadius = 52.dp,
                    )
                } else {
                    Modifier
                },
            )
            .pointerInput(session, locked) {
                if (locked) return@pointerInput
                val cell = size.width.toFloat() / columns
                fun column(o: Offset) = ((o.x / cell).toInt() - 1).coerceIn(0, p.width - 1)
                fun row(o: Offset) = ((o.y / cell).toInt() - 1).coerceIn(0, p.height - 1)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (down.position.x < cell || down.position.y < cell) return@awaitEachGesture // on a count
                    val first = row(down.position) * p.width + column(down.position)
                    if (!session.begin(first, currentBrush)) return@awaitEachGesture
                    down.consume()
                    touched = first
                    finger = down.position
                    currentPaint()
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            // Other fingers landing on the grid are ignored: they do not draw.
                            event.changes.forEach { it.consume() }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            // The lift counts too: a quick flick may end far from its last move.
                            finger = change.position
                            touched = row(change.position) * p.width + column(change.position)
                            if (session.dragTo(column(change.position), row(change.position)) > 0) currentPaint()
                            if (!change.pressed) break
                        }
                    } finally {
                        finger = Offset.Unspecified
                        currentStroke(session.end())
                    }
                }
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val cell = size.width / columns
            val style = TextStyle(
                fontSize = (cell * 0.58f).toSp(),
                fontFamily = if (Comfort.plainDigits) FontFamily.SansSerif else Almendra,
                fontWeight = FontWeight.Bold,
            )

            val lit = touched.takeIf { !celebrate }
            val band = Ink.copy(alpha = 0.08f)
            lit?.let {
                drawRect(band, Offset(0f, (it / p.width + 1) * cell), Size(size.width, cell))
                drawRect(band, Offset((it % p.width + 1) * cell, 0f), Size(cell, size.height))
            }

            // A count reached is dimmed and struck through, out of the way; exceeded, it is red and
            // underlined: the line says it without the colour.
            fun count(want: Int, have: Int, centerX: Float, centerY: Float) {
                val text = measurer.measure(want.toString(), style)
                val color = if (have == want) Dim else if (have > want) Red else Ink
                drawText(text, color, Offset(centerX - text.size.width / 2f, centerY - text.size.height / 2f))
                val half = cell * 0.26f
                val line = if (have > want) centerY + cell * 0.36f else centerY
                if (have >= want) drawLine(color, Offset(centerX - half, line), Offset(centerX + half, line), cell * 0.06f, StrokeCap.Round)
            }
            for (x in 0 until p.width) {
                count(p.colCounts[x], (0 until p.height).count { marks[it * p.width + x] == WALL }, (x + 1.5f) * cell, cell / 2)
            }
            for (y in 0 until p.height) {
                count(p.rowCounts[y], (0 until p.width).count { marks[y * p.width + it] == WALL }, cell / 2, (y + 1.5f) * cell)
            }

            val t = beat?.value ?: 0f
            val frame = tick.value
            val time = clock.value
            for (i in marks.indices) {
                val left = (i % p.width + 1) * cell
                val top = (i / p.width + 1) * cell
                val given = i in p.chests || i in p.monsters
                val floor = if (given || marks[i] == KNOWN_OPEN || (celebrate && marks[i] != WALL)) OpenFloor else Unknown
                drawRect(floor, Offset(left + 1, top + 1), Size(cell - 2, cell - 2))
                // The band again, over the floor that has just covered it.
                if (lit != null && (i / p.width == lit / p.width || i % p.width == lit % p.width)) {
                    drawRect(band, Offset(left + 1, top + 1), Size(cell - 2, cell - 2))
                }
                if (marks[i] != WALL) {
                    drawFloor(
                        i * 7 + variety, left, top, cell, paved = floor == OpenFloor,
                        above = i >= p.width && marks[i - p.width] == WALL, beside = i % p.width > 0 && marks[i - 1] == WALL,
                    )
                }
                // Celebration: monsters stand on the alert, chests pulse.
                val moving = beat != null && marks[i] != WALL
                when {
                    marks[i] == WALL -> translate(left, top) { with(walls[(i * 5 + variety).mod(walls.size)]) { draw(Size(cell, cell)) } }
                    i in p.chests -> translate(left, top) {
                        drawShadow(cell)
                        val pulse = if (moving) 1f + 0.1f * sin(2 * PI.toFloat() * t) else 1f
                        // A glint crosses the chest every five seconds, each chest in its turn.
                        val glint = (frame + i * 13) % 34
                        scale(pulse, Offset(cell / 2, cell / 2)) { with(chest[if (glint < 3) glint + 1 else 0]) { draw(Size(cell, cell)) } }
                    }
                    i in p.monsters -> {
                        val order = monsterOrder.indexOf(i)
                        translate(left, top) { drawMonster((order + variety).mod(MONSTERS.size), bodies, frame, order, cell, time, alert = celebrate) }
                    }
                }
                if (marks[i] == KNOWN_OPEN && !celebrate) drawCircle(Ink, cell * 0.09f, Offset(left + cell / 2, top + cell / 2))
            }
            // The light does not reach the far corners of the dungeon.
            val grid = Size(p.width * cell, p.height * cell)
            drawRect(
                Brush.radialGradient(
                    0.55f to Color.Transparent, 1f to Vignette,
                    center = Offset(cell + grid.width / 2, cell + grid.height / 2), radius = grid.maxDimension * 0.72f,
                ),
                Offset(cell, cell), grid,
            )
            for (i in wrong) {
                val a = Offset((i % p.width + 1) * cell, (i / p.width + 1) * cell) + Offset(cell * 0.2f, cell * 0.2f)
                val reach = cell * 0.6f
                drawLine(Red, a, a + Offset(reach, reach), cell * 0.12f, StrokeCap.Round)
                drawLine(Red, a + Offset(reach, 0f), a + Offset(0f, reach), cell * 0.12f, StrokeCap.Round)
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

        // The invisible nodes: column counts, row counts, then the cells.
        Layout(
            content = {
                fun counted(have: Int, want: Int) = "$want walls, $have marked"
                for (x in 0 until p.width) {
                    val have = (0 until p.height).count { marks[it * p.width + x] == WALL }
                    Box(Modifier.semantics { contentDescription = "Column ${x + 1}: ${counted(have, p.colCounts[x])}" })
                }
                for (y in 0 until p.height) {
                    val have = (0 until p.width).count { marks[y * p.width + it] == WALL }
                    Box(Modifier.semantics { contentDescription = "Row ${y + 1}: ${counted(have, p.rowCounts[y])}" })
                }
                for (i in marks.indices) {
                    val what = when {
                        i in p.monsters -> "monster"
                        i in p.chests -> "chest"
                        marks[i] == WALL -> "wall"
                        marks[i] == KNOWN_OPEN -> "open"
                        else -> "unknown"
                    }
                    val given = i in p.monsters || i in p.chests
                    Box(
                        Modifier.semantics {
                            contentDescription = "Row ${i / p.width + 1}, column ${i % p.width + 1}: $what" +
                                (if (i in toMark) ", to mark" else "") + if (i in wrong) ", mistake" else ""
                            if (!given && !locked) {
                                fun mark(label: String, value: Int) = CustomAccessibilityAction(label) {
                                    currentStroke(session.paint(listOf(i), value))
                                    true
                                }
                                onClick("change") {
                                    session.begin(i)
                                    currentStroke(session.end())
                                    true
                                }
                                customActions = listOf(mark("Wall", WALL), mark("Open", KNOWN_OPEN), mark("Unknown", 0))
                            }
                        },
                    )
                }
            },
            modifier = Modifier.matchParentSize(),
        ) { measurables, constraints ->
            val cell = constraints.maxWidth.toFloat() / columns
            val placeables = measurables.map { it.measure(Constraints.fixed(cell.toInt(), cell.toInt())) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEachIndexed { n, placeable ->
                    val i = n - p.width - p.height
                    val x = if (n < p.width) n + 1 else if (i < 0) 0 else i % p.width + 1
                    val y = if (n < p.width) 0 else if (i < 0) n - p.width + 1 else i / p.width + 1
                    placeable.place((x * cell).toInt(), (y * cell).toInt())
                }
            }
        }
    }
}
