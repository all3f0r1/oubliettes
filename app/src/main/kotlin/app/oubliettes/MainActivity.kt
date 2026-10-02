package app.oubliettes

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.oubliettes.game.Puzzle
import app.oubliettes.game.generate
import app.oubliettes.game.isSolved
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF1B1A22)
private val Floor = Color(0xFF2A2833)
private val Ink = Color(0xFFE8E6F0)
private val Dim = Color(0xFF77758A)
private val Gold = Color(0xFFE0B84C)
private val Red = Color(0xFFE06C5A)

private val SIZES = listOf(8, 10, 12)

// Mark values for a cell.
private const val WALL = 1
private const val KNOWN_OPEN = 2

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getPreferences(MODE_PRIVATE)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(primary = Gold, onPrimary = Bg, background = Bg, surface = Bg, onSurface = Ink),
            ) {
                Surface(Modifier.fillMaxSize()) { Game(prefs) }
            }
        }
    }
}

@Composable
private fun Game(prefs: SharedPreferences) {
    var size by remember { mutableIntStateOf(prefs.getInt("size", SIZES[0])) }
    var level by remember { mutableIntStateOf(prefs.getInt("level$size", 1)) }
    var solvedCount by remember { mutableIntStateOf(prefs.getInt("solved", 0)) }
    var puzzle by remember { mutableStateOf<Puzzle?>(null) }
    var marks by remember { mutableStateOf(IntArray(0)) }

    LaunchedEffect(size, level) {
        puzzle = null
        val generated = withContext(Dispatchers.Default) { generate(size, size, level.toLong()) }
        // Saved marks are only reused when they belong to this exact grid.
        val saved = prefs.getString("marks", "")!!.removePrefix("$size:$level:")
        marks = if (saved.length == size * size && saved.all { it in '0'..'2' }) {
            IntArray(saved.length) { saved[it] - '0' }
        } else {
            IntArray(size * size)
        }
        puzzle = generated
        prefs.edit().putInt("size", size).putInt("level$size", level).apply()
    }

    fun update(new: IntArray) {
        marks = new
        prefs.edit().putString("marks", "$size:$level:${new.joinToString("")}").apply()
    }

    val p = puzzle
    val solved = p != null && isSolved(p, BooleanArray(marks.size) { marks[it] == WALL })

    Column(
        Modifier.safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("OUBLIETTES", color = Gold, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (s in SIZES) {
                if (s == size) {
                    Button(onClick = {}) { Text("$s×$s") }
                } else {
                    OutlinedButton(onClick = { size = s; level = prefs.getInt("level$s", 1) }) { Text("$s×$s") }
                }
            }
        }
        Text("Grille n° $level · $solvedCount résolues", color = Dim)
        if (p == null) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Board(p, marks, locked = solved) { cell, value -> update(marks.copyOf().also { it[cell] = value }) }
            Text(if (solved) "Donjon résolu !" else "", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { update(IntArray(size * size)) }, enabled = !solved) { Text("Effacer") }
                Button(onClick = {
                    if (solved) {
                        solvedCount++
                        prefs.edit().putInt("solved", solvedCount).apply()
                    }
                    level++
                }) { Text(if (solved) "Grille suivante" else "Passer") }
            }
        }
    }
}

/** Tap cycles a cell: unknown, wall, known open. Dragging paints the value set by the first cell. */
@Composable
private fun Board(p: Puzzle, marks: IntArray, locked: Boolean, onPaint: (cell: Int, value: Int) -> Unit) {
    val wall = painterResource(R.drawable.wall)
    val chest = painterResource(R.drawable.chest)
    val monsters = listOf(
        painterResource(R.drawable.monster_slime),
        painterResource(R.drawable.monster_ghost),
        painterResource(R.drawable.monster_imp),
    )
    val measurer = rememberTextMeasurer()
    val currentMarks by rememberUpdatedState(marks)
    val currentPaint by rememberUpdatedState(onPaint)
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
                    val value = (currentMarks[first] + 1) % 3
                    currentPaint(first, value)
                    do {
                        val event = awaitPointerEvent()
                        for (change in event.changes) {
                            if (change.pressed) {
                                cellAt(change.position)?.let { if (currentMarks[it] != value) currentPaint(it, value) }
                            }
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val cell = size.width / columns
        val style = TextStyle(fontSize = (cell * 0.45f).toSp(), fontWeight = FontWeight.Bold)

        fun count(want: Int, have: Int, centerX: Float, centerY: Float) {
            val text = measurer.measure(want.toString(), style)
            val color = if (have == want) Dim else if (have > want) Red else Ink
            drawText(text, color, Offset(centerX - text.size.width / 2f, centerY - text.size.height / 2f))
        }
        for (x in 0 until p.width) {
            count(p.colCounts[x], (0 until p.height).count { marks[it * p.width + x] == WALL }, (x + 1.5f) * cell, cell / 2)
        }
        for (y in 0 until p.height) {
            count(p.rowCounts[y], (0 until p.width).count { marks[y * p.width + it] == WALL }, cell / 2, (y + 1.5f) * cell)
        }

        for (i in marks.indices) {
            val left = (i % p.width + 1) * cell
            val top = (i / p.width + 1) * cell
            drawRect(Floor, Offset(left + 1, top + 1), Size(cell - 2, cell - 2))
            val painter = when {
                marks[i] == WALL -> wall
                i in p.chests -> chest
                i in p.monsters -> monsters[i % monsters.size]
                else -> null
            }
            if (painter != null) translate(left, top) { with(painter) { draw(Size(cell, cell)) } }
            if (marks[i] == KNOWN_OPEN) drawCircle(Dim, cell * 0.1f, Offset(left + cell / 2, top + cell / 2))
        }
    }
}
