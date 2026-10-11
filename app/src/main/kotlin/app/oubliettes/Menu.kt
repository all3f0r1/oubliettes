package app.oubliettes

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The way into the dungeon: its sign, the dark entrance and what watches from it, then the planks.
 * [finished] is how many difficulties of the campaign are solved through; [daily] is what today's
 * dungeon is like, and [dailyDone] whether it is solved.
 */
@Composable
internal fun Menu(
    solved: Int, finished: Int, tutorialDone: Boolean, resume: String?, daily: String, dailyDone: Boolean,
    onResume: () -> Unit, open: (Screen) -> Unit,
) {
    val wall = painterResource(R.drawable.wall)
    // How far the sign comes down to hang right over the arch, whatever room the entrance was given.
    var drop by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val activity = LocalActivity.current
    Box(
        Modifier.fillMaxSize().drawBehind {
            // A dungeon wall in the dark: staggered stone blocks.
            val block = size.width / 5
            for (rowIndex in 0..(size.height / block).toInt()) {
                for (column in -1..5) {
                    translate(column * block + if (rowIndex % 2 == 0) 0f else block / 2, rowIndex * block) {
                        with(wall) { draw(Size(block, block), colorFilter = StoneShade) }
                    }
                }
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Bg), startY = size.height * 0.45f))
        },
    ) {
        BoxWithConstraints(Modifier.safeDrawingPadding().fillMaxSize()) {
            Column(
                // The entrance takes the room the planks leave; the whole scrolls when even they do not fit: short screens, large fonts.
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.offset { IntOffset(0, drop) }.semantics { heading() }.signboard().padding(horizontal = 34.dp, vertical = 4.dp)) {
                    Text(
                        "Oubliettes",
                        color = Gold,
                        style = TextStyle(fontFamily = Fraktur, fontSize = 50.sp, shadow = Shadow(Color.Black, Offset(0f, 4f), 6f)),
                    )
                }
                Entrance(
                    Modifier.weight(1f).fillMaxWidth().onSizeChanged {
                        val arch = arch(it.width.toFloat(), it.height.toFloat())
                        // Clear of the stones around the arch, less the gap the column already leaves.
                        drop = (it.height - arch.height - arch.width * STONES + with(density) { 6.dp.toPx() }).toInt()
                    },
                )
                Plank("Tutorial", matter = Matter.PARCHMENT, done = tutorialDone) { open(Screen.TUTORIAL) }
                if (resume != null) Plank("Continue", resume, matter = Matter.GILDED, onClick = onResume)
                Row(Modifier.widthIn(max = 320.dp).fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Plank("Campaign", "$solved / ${LEVELS * SIZES.size} solved", Modifier.weight(1f).fillMaxHeight(), seals = finished) {
                        open(Screen.CAMPAIGN)
                    }
                    Plank("Endless", modifier = Modifier.weight(1f).fillMaxHeight()) { open(Screen.ENDLESS) }
                }
                Plank("Daily", daily, done = dailyDone) { open(Screen.DAILY) }
                Row(Modifier.widthIn(max = 320.dp).fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Plank("Options", modifier = Modifier.weight(1f).fillMaxHeight(), matter = Matter.IRON) { open(Screen.OPTIONS) }
                    Plank("Quit", modifier = Modifier.weight(1f).fillMaxHeight(), matter = Matter.IRON) { activity?.finish() }
                }
            }
            Spider()
        }
    }
}

/** The sign over the entrance: one old board, split at both ends, nailed askew. */
private fun Modifier.signboard() = rotate(-3f).drawBehind {
    val outline = listOf(
        0.03f to 0.1f, 0.5f to 0.03f, 0.985f to 0.09f, // the top
        0.955f to 0.3f, 1f to 0.44f, 0.965f to 0.6f, 0.99f to 0.93f, // the right end, split
        0.55f to 0.98f, 0.1f to 0.94f, // the bottom
        0.075f to 0.7f, 0f to 0.64f, 0.04f to 0.4f, 0.01f to 0.24f, // the left end, a corner gone
    )
    val board = Path().apply {
        for ((index, point) in outline.withIndex()) {
            if (index == 0) moveTo(point.first * size.width, point.second * size.height) else lineTo(point.first * size.width, point.second * size.height)
        }
        close()
    }
    fun at(x: Float, y: Float) = Offset(x * size.width, y * size.height)
    drawPath(board, Brush.verticalGradient(Matter.WOOD.fill))
    clipPath(board) {
        // The grain, then a crack running in from each end.
        for (k in 1..4) drawLine(DarkWood.copy(alpha = 0.22f), at(0f, k / 5f), at(1f, k / 5f + if (k % 2 == 0) 0.05f else -0.04f), 1.dp.toPx())
        drawLine(DarkWood, at(1f, 0.44f), at(0.84f, 0.5f), 1.5.dp.toPx())
        drawLine(DarkWood, at(0f, 0.64f), at(0.13f, 0.57f), 1.5.dp.toPx())
    }
    drawPath(board, DarkWood, style = Stroke(3.dp.toPx(), join = StrokeJoin.Round))
    for (nail in listOf(at(0.085f, 0.3f), at(0.93f, 0.74f))) {
        drawCircle(DarkWood, 4.dp.toPx(), nail)
        drawCircle(Color(0xFFB9B4C8), 2.dp.toPx(), nail - Offset(1f, 1f))
    }
}

/** How thick the stones around the arch are, in fractions of its width. */
private const val STONES = 0.13f

/** Width and height of the opening of the entrance, in the room it is given. */
private fun arch(width: Float, height: Float): Size {
    val opening = minOf(width * 0.54f, height * 0.8f)
    return Size(opening, minOf(height, opening * 1.35f))
}

/** Who lurks in the entrance: which of [MONSTERS], where in the opening and how big, in fractions of its width and height. */
private class Lurker(val kind: Int, val x: Float, val y: Float, val size: Float)

private val LURKERS = listOf(
    Lurker(2, 0.31f, 0.46f, 0.44f), Lurker(0, 0.64f, 0.66f, 0.36f), Lurker(5, 0.5f, 0.27f, 0.3f),
    Lurker(1, 0.2f, 0.8f, 0.28f), Lurker(4, 0.74f, 0.4f, 0.42f),
)

/** The dungeon entrance: a stone arch between two torches, opening on the dark. Only eyes show in there. */
@Composable
private fun Entrance(modifier: Modifier) {
    val torch = torchFrames()
    val tick = rememberTick()
    val clock = rememberClock()
    Canvas(modifier) {
        if (size.height < 1f) return@Canvas
        val time = clock.value
        val (width, height) = arch(size.width, size.height)
        val left = (size.width - width) / 2
        val top = size.height - height
        val spring = top + width / 2 // where the arch starts
        val stones = width * STONES
        val opening = Path().apply {
            moveTo(left, size.height)
            lineTo(left, spring)
            arcTo(Rect(left, top, left + width, top + width), 180f, 180f, false)
            lineTo(left + width, size.height)
            close()
        }
        val torchSize = width * 0.34f
        val flames = listOf(left - stones - torchSize * 0.6f, left + width + stones + torchSize * 0.6f).map { Offset(it, spring) }
        val frames = flames.indices.map { (tick.value + it * 2) % torch.size }
        for ((index, flame) in flames.withIndex()) {
            // The light wavers with the flame, never twice the same: two beats that drift apart.
            val waver = 1f + 0.07f * sin(time * 9f + index * 2f) * sin(time * 3.7f + index)
            val reach = width * 1.1f * waver
            drawCircle(Brush.radialGradient(listOf(Color(0xFFF08A2A).copy(alpha = 0.42f * waver), Color.Transparent), flame, reach), reach, flame)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE066).copy(alpha = 0.3f * waver), Color.Transparent), flame, width * 0.3f), width * 0.3f, flame)
        }
        // The stones of the arch, then their joints; the dark covers the inner half of the stroke.
        drawPath(opening, Color(0xFF6E6A86), style = Stroke(stones * 2))
        val centre = Offset(left + width / 2, spring)
        for (k in 0..8) {
            val angle = PI.toFloat() * (1 + k / 8f)
            val towards = Offset(cos(angle), sin(angle))
            drawLine(Bg, centre + towards * (width / 2), centre + towards * (width / 2 + stones), 2.dp.toPx())
        }
        var y = spring + stones
        while (y < size.height) {
            for (x in listOf(left - stones, left + width)) drawLine(Bg, Offset(x, y), Offset(x + stones, y), 2.dp.toPx())
            y += stones * 1.1f
        }
        drawPath(opening, Brush.verticalGradient(listOf(Color(0xFF040308), Color(0xFF12111A)), startY = top, endY = size.height))
        for ((index, lurker) in LURKERS.withIndex()) {
            val cell = lurker.size * width
            translate(left + lurker.x * width - cell / 2, top + lurker.y * height - cell / 2) {
                drawEyes(MONSTERS[lurker.kind], tick.value, index, cell, lurking = true)
            }
        }
        for ((index, flame) in flames.withIndex()) {
            translate(flame.x - torchSize / 2, flame.y - torchSize * 0.25f) { with(torch[frames[index]]) { draw(Size(torchSize, torchSize)) } }
            // Sparks rise from the flame and die out, one after the other.
            if (!Comfort.calm) for (spark in 0..3) {
                val life = (time * 0.4f + spark * 0.27f + index * 0.5f).mod(1f)
                val sway = sin(spark * 7f + life * 6f) * torchSize * 0.16f
                drawCircle(Color(0xFFFFB347).copy(alpha = 1f - life), torchSize * 0.022f, flame + Offset(sway, -torchSize * (0.15f + life * 1.1f)))
            }
        }
    }
}
