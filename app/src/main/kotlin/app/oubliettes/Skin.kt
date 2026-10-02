package app.oubliettes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
private val DarkWood = Color(0xFF24170C)
private val Iron = Color(0xFF3A3846)
private val IronEdge = Color(0xFF8E8CAA)

/** Frame counter of the sprite animations: one step every 150 ms. Read it while drawing. */
@Composable
internal fun rememberTick(): State<Int> = produceState(0) {
    while (true) {
        delay(150)
        value++
    }
}

/** A wooden plank with iron rivets [inset] from its corners. Greyed when not [enabled], gold-rimmed when [selected]. */
internal fun Modifier.plank(enabled: Boolean = true, selected: Boolean = false, inset: Dp = 12.dp): Modifier {
    val shape = RoundedCornerShape(6.dp)
    val wood = if (enabled) listOf(Color(0xFF8A5C34), Color(0xFF5A391D)) else listOf(Color(0xFF453C36), Color(0xFF332C28))
    return clip(shape)
        .background(Brush.verticalGradient(wood))
        .border(3.dp, if (selected) Gold else DarkWood, shape)
        .drawBehind {
            val at = inset.toPx()
            val radius = at / 3
            for (x in listOf(at, size.width - at)) for (y in listOf(at, size.height - at)) {
                drawCircle(DarkWood, radius, Offset(x, y))
                drawCircle(if (enabled) Color(0xFFB9B4C8) else Dim, radius / 2, Offset(x - 1, y - 1))
            }
        }
}

@Composable
internal fun PlankButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .plank(enabled, selected, inset = 7.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .defaultMinSize(minWidth = 72.dp, minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) Cream else Dim, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
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

/** Slider from 0 to 1: an iron bar filling with gold, and a riveted knob. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IronSlider(value: Float, onValueChange: (Float) -> Unit, onValueChangeFinished: (() -> Unit)? = null) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        // Sliders reach the screen edge: keep the system back gesture from stealing their drags.
        modifier = Modifier.systemGestureExclusion(),
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
