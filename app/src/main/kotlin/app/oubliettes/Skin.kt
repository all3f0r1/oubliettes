package app.oubliettes

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

/** Blackletter, for titles. Mixed case only: its capitals are unreadable in a row. */
internal val Fraktur = FontFamily(Font(R.font.unifraktur_cook))

/** Calligraphic hand, for everything else. */
internal val Almendra = FontFamily(
    Font(R.font.almendra_regular),
    Font(R.font.almendra_bold, FontWeight.Bold),
    Font(R.font.almendra_italic, style = FontStyle.Italic),
)

internal val Cream = Color(0xFFFFE9A8)
internal val DarkWood = Color(0xFF24170C)
internal val Iron = Color(0xFF3A3846)
internal val IronEdge = Color(0xFF8E8CAA)

/** Display settings, read while drawing. Loaded from the preferences on launch, changed in Options. */
internal object Comfort {
    /** Nothing moves on its own: no sprite animation, no flicker, no hopping. */
    var calm by mutableStateOf(false)
    var plainDigits by mutableStateOf(false)
    var magnifier by mutableStateOf(true)
}

/** Frame counter of the sprite animations: one step every 150 ms, none in calm mode. Read it while drawing. */
@Composable
internal fun rememberTick(): State<Int> = produceState(0, Comfort.calm) {
    while (!Comfort.calm) {
        delay(150)
        value++
    }
}

/** What a sign is made of, which tells what it is for: [ink] is its lettering. */
internal enum class Matter(val fill: List<Color>, val rim: Color, val ink: Color) {
    /** Somewhere to go. */
    WOOD(listOf(Color(0xFF8A5C34), Color(0xFF5A391D)), DarkWood, Cream),

    /** The way back into the grid left open: the sign that stands out. */
    GILDED(listOf(Color(0xFF9A6A3A), Color(0xFF6A4322)), Gold, Cream),

    /** Something to read. */
    PARCHMENT(listOf(Color(0xFFEAD9B0), Color(0xFFC9B383)), Color(0xFF6B5330), DarkWood),

    /** Machinery: the settings. */
    IRON(listOf(Color(0xFF4C4A5C), Iron), IronEdge, Ink),
}

/**
 * A sign with iron rivets [inset] from its corners, a wooden plank unless made of another [matter].
 * Greyed when not [enabled]. When [selected] it is rimmed in gold and a gold stroke is painted under
 * its lettering: the colour is not alone to say it.
 */
internal fun Modifier.plank(enabled: Boolean = true, selected: Boolean = false, inset: Dp = 12.dp, matter: Matter = Matter.WOOD): Modifier {
    val shape = RoundedCornerShape(6.dp)
    val wood = if (enabled) matter.fill else listOf(Color(0xFF453C36), Color(0xFF332C28))
    return clip(shape)
        .background(Brush.verticalGradient(wood))
        .border(3.dp, if (selected) Gold else matter.rim, shape)
        .drawBehind {
            val at = inset.toPx()
            val radius = at / 3
            for (x in listOf(at, size.width - at)) for (y in listOf(at, size.height - at)) {
                drawCircle(DarkWood, radius, Offset(x, y))
                drawCircle(if (enabled) Color(0xFFB9B4C8) else Dim, radius / 2, Offset(x - 1, y - 1))
            }
            if (selected) {
                val y = size.height - 6.5.dp.toPx()
                drawLine(Gold, Offset(size.width * 0.3f, y), Offset(size.width * 0.7f, y), 2.dp.toPx(), StrokeCap.Round)
            }
        }
}

/** [selected] is for a plank that is one of several choices: null when it is not, and screen readers are told which one is chosen. */
@Composable
internal fun PlankButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .plank(enabled, selected == true, inset = 7.dp)
            .then(if (selected != null) Modifier.semantics { this.selected = selected } else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .defaultMinSize(minWidth = 72.dp, minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) Cream else Dim, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
    }
}

/** A gold seal struck with a tick: done, finished. */
@Composable
internal fun Seal(size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width
        drawCircle(DarkWood)
        drawCircle(Gold, s * 0.42f)
        val tick = s * 0.13f
        drawLine(DarkWood, Offset(s * 0.28f, s * 0.52f), Offset(s * 0.44f, s * 0.68f), tick, StrokeCap.Round)
        drawLine(DarkWood, Offset(s * 0.44f, s * 0.68f), Offset(s * 0.72f, s * 0.34f), tick, StrokeCap.Round)
    }
}

/**
 * A spider that lets itself down its thread along the edge of the screen now and then, hangs there
 * and climbs back. It stays in the margin, over nothing that is read or touched. Not in calm mode.
 */
@Composable
internal fun Spider() {
    if (Comfort.calm) return
    val time = rememberInfiniteTransition(label = "spider")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(26_000, easing = LinearEasing)), label = "thread")
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        // Down, hanging, back up, then out of sight for the second half of the loop.
        val down = when {
            t < 0.12f -> t / 0.12f
            t < 0.36f -> 1f
            t < 0.5f -> (0.5f - t) / 0.14f
            else -> return@Canvas
        }
        val r = 3.5.dp.toPx()
        val x = size.width - 8.dp.toPx()
        val y = down * size.height * 0.28f
        // Legs work while it moves, and twitch now and then while it hangs.
        val stride = sin(t * 2 * PI.toFloat() * if (down < 1f) 90 else 6) * r * 0.25f
        drawLine(Dim.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, y), 1.dp.toPx())
        for (side in listOf(-1f, 1f)) for (leg in 0..3) {
            val lift = (leg - 1.5f) * r * 0.55f
            val knee = Offset(x + side * r * 1.5f, y + lift - r * 0.6f + stride * if (leg % 2 == 0) 1 else -1)
            drawLine(IronEdge, Offset(x, y), knee, 1.dp.toPx(), StrokeCap.Round)
            drawLine(IronEdge, knee, Offset(x + side * r * 2f, y + lift * 1.6f + r * 0.5f), 1.dp.toPx(), StrokeCap.Round)
        }
        drawCircle(Iron, r, Offset(x, y))
        drawCircle(IronEdge, r, Offset(x, y), style = Stroke(1.dp.toPx()))
        drawCircle(Iron, r * 0.6f, Offset(x, y + r * 1.2f))
        for (side in listOf(-1f, 1f)) drawCircle(Color(0xFFFF3B2F), r * 0.16f, Offset(x + side * r * 0.25f, y + r * 1.4f))
    }
}

/** Checkbox: an iron plate, crossed in gold when [checked]. */
@Composable
internal fun IronCheck(checked: Boolean) {
    Canvas(Modifier.size(26.dp)) {
        val corner = CornerRadius(size.width * 0.15f)
        drawRoundRect(Iron, cornerRadius = corner)
        drawRoundRect(IronEdge, cornerRadius = corner, style = Stroke(size.width * 0.1f))
        if (checked) {
            val a = size.width * 0.27f
            val b = size.width - a
            drawLine(Gold, Offset(a, a), Offset(b, b), size.width * 0.15f, StrokeCap.Round)
            drawLine(Gold, Offset(b, a), Offset(a, b), size.width * 0.15f, StrokeCap.Round)
        }
    }
}

/** Slider from 0 to 1: an iron bar filling with gold, and a riveted knob. [label] names it to screen readers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IronSlider(label: String, value: Float, onValueChange: (Float) -> Unit, onValueChangeFinished: (() -> Unit)? = null) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        // Sliders reach the screen edge: keep the system back gesture from stealing their drags.
        modifier = Modifier.systemGestureExclusion().semantics { contentDescription = label },
        onValueChangeFinished = onValueChangeFinished,
        thumb = {
            Canvas(Modifier.size(28.dp)) {
                drawCircle(DarkWood)
                drawCircle(Gold, size.width * 0.38f)
                drawCircle(Cream, size.width * 0.12f, center - Offset(size.width * 0.1f, size.width * 0.1f))
            }
        },
        track = { state ->
            Canvas(Modifier.fillMaxWidth().height(12.dp)) {
                val corner = CornerRadius(size.height / 2)
                drawRoundRect(Iron, cornerRadius = corner)
                drawRoundRect(Gold, size = Size(size.width * state.value, size.height), cornerRadius = corner)
                drawRoundRect(IronEdge, cornerRadius = corner, style = Stroke(size.height * 0.16f))
            }
        },
    )
}
