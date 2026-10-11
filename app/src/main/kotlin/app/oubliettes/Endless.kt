package app.oubliettes

import android.content.SharedPreferences
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import app.oubliettes.game.decodePuzzle
import app.oubliettes.game.generate
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private fun endlessSeed(prefs: SharedPreferences, slot: String) =
    if (prefs.contains("seed$slot")) prefs.getLong("seed$slot", 0) else Random.nextLong()

/** Random grids of one [size] and one [reasoning] of REASONINGS, one after the other until the player walks out. */
@Composable
internal fun Endless(prefs: SharedPreferences, audio: Audio, size: Int, reasoning: Int, onBack: () -> Unit) {
    // What the grid under way is saved as. The middle one keeps the name grids had before there was a choice, and the grid begun then.
    val slot = if (reasoning == 1) "$size" else "${size}r$reasoning"
    var seed by remember { mutableLongStateOf(endlessSeed(prefs, slot)) }
    var solvedCount by remember { mutableIntStateOf(prefs.getInt("solved", 0)) }
    val status = "$size×$size · ${REASONINGS[reasoning]} · $solvedCount solved"
    // One grid: leaving it (the next grid, the way back) cancels its digging.
    key(seed) {
        // The grid itself is saved next to its seed, so a save never depends on the generator giving it again.
        var puzzle by remember {
            mutableStateOf(prefs.getString("puzzleE$slot", "")!!.takeIf { it.startsWith("$seed;") }?.let { decodePuzzle(it.substringAfter(';')) })
        }
        var failed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            prefs.edit().putLong("seed$slot", seed).putString("last", "E:$size").apply()
            if (puzzle != null) return@LaunchedEffect
            val dug = withContext(Dispatchers.Default) { generate(size, size, seed, reasoning = effortOf(size, reasoning)) { !isActive } }
            if (dug == null) {
                failed = true
            } else {
                prefs.edit().putString("puzzleE$slot", "$seed;${dug.encode()}").apply()
                puzzle = dug
            }
        }
        val p = puzzle
        if (p != null) {
            Game(
                prefs, audio, "Endless", onBack, back = "Choose",
                puzzle = p, saveKey = "marksE$slot", legacyId = "$seed",
                variety = seed.toInt(), status = status, solvedStatus = { status },
                next = "Next grid", onNext = { seed = Random.nextLong() }, skip = "New grid", auto = true,
                share = "Endless · $size×$size · ${REASONINGS[reasoning]}",
                // A grid counts the moment it is solved, and only once however often it is solved again.
                onSolved = {
                    if (!prefs.contains("counted$slot") || prefs.getLong("counted$slot", 0) != seed) {
                        solvedCount++
                        prefs.edit().putInt("solved", solvedCount).putLong("counted$slot", seed).apply()
                        prefs.keep(listOf(it))
                    }
                },
            )
        } else {
            Digging("Endless", status, onBack, "Choose", failed) { seed = Random.nextLong() }
        }
    }
}

/** In place of a grid while it is dug, or once the diggers [failed]: [again], when there is another to dig, has them start over. */
@Composable
internal fun Digging(title: String, status: String, onBack: () -> Unit, back: String, failed: Boolean, again: (() -> Unit)? = null) {
    Page(title, onBack, back = back, foot = {}) {
        Text(status, color = Dim)
        Column(
            Modifier.fillMaxWidth().aspectRatio(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (failed) {
                Text("The diggers gave up on this one.", color = Dim, fontStyle = FontStyle.Italic)
                if (again != null) PlankButton("Dig another", onClick = again)
            } else {
                Diggers()
                Text("Digging…", Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = Dim, fontStyle = FontStyle.Italic)
            }
        }
    }
}

/** While a grid is dug: a torch, and the dwellers hopping one after the other as they wait for their dungeon. */
@Composable
private fun Diggers() {
    val torch = torchFrames()
    val bodies = monsterBodies()
    val tick = rememberTick()
    val clock = rememberClock()
    val wave = if (Comfort.calm) {
        null
    } else {
        rememberInfiniteTransition(label = "digging")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "wave")
    }
    val dwellers = listOf(2, 0, null, 3, 4) // imp, slime, the torch, skull, bat
    Canvas(Modifier.size(width = 264.dp, height = 64.dp)) {
        val cell = size.width / dwellers.size
        for ((index, kind) in dwellers.withIndex()) {
            // Each one leaves the ground for a fifth of the loop, in turn.
            val turn = wave?.let { (it.value * dwellers.size - index).mod(dwellers.size.toFloat()) } ?: 1f
            val hop = if (kind != null && turn < 1f) sin(PI.toFloat() * turn) * cell * 0.3f else 0f
            translate(index * cell, size.height - cell) {
                if (kind == null) {
                    with(torch[tick.value % torch.size]) { draw(Size(cell, cell)) }
                } else {
                    drawMonster(kind, bodies, tick.value, index, cell, clock.value, hop)
                }
            }
        }
    }
}
