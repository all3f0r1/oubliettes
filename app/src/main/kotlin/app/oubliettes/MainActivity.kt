package app.oubliettes

import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.oubliettes.game.Puzzle
import app.oubliettes.game.generate
import app.oubliettes.game.isSolved
import app.oubliettes.game.tutorialPuzzle
import app.oubliettes.game.tutorialSteps
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SIZES = listOf(8, 10, 12)

private enum class Screen { MENU, CAMPAIGN, ENDLESS, TUTORIAL, OPTIONS }

/** Campaign grids grow with the level; level n is always the same grid (its seed is n). */
private fun campaignSize(level: Int) = if (level <= 10) 8 else if (level <= 25) 10 else 12

class MainActivity : ComponentActivity() {
    private lateinit var audio: Audio

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        volumeControlStream = AudioManager.STREAM_MUSIC
        val prefs = getPreferences(MODE_PRIVATE)
        audio = Audio(applicationContext, prefs)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(primary = Gold, onPrimary = Bg, background = Bg, surface = Bg, onSurface = Ink),
            ) {
                Surface(Modifier.fillMaxSize()) { App(prefs, audio) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        audio.setForeground(true)
    }

    override fun onStop() {
        audio.setForeground(false)
        super.onStop()
    }

    override fun onDestroy() {
        audio.release()
        super.onDestroy()
    }
}

@Composable
private fun App(prefs: SharedPreferences, audio: Audio) {
    var screen by rememberSaveable { mutableStateOf(Screen.MENU) }
    val toMenu = { screen = Screen.MENU }
    BackHandler(enabled = screen != Screen.MENU, onBack = toMenu)
    when (screen) {
        Screen.MENU -> Menu(prefs.getInt("level", 1)) { screen = it }
        Screen.CAMPAIGN -> Game(prefs, audio, endless = false, toMenu)
        Screen.ENDLESS -> Game(prefs, audio, endless = true, toMenu)
        Screen.TUTORIAL -> Tutorial(toMenu) { screen = Screen.CAMPAIGN }
        Screen.OPTIONS -> Options(audio, toMenu)
    }
}

@Composable
private fun Menu(level: Int, open: (Screen) -> Unit) {
    val wall = painterResource(R.drawable.wall)
    val torch = painterResource(R.drawable.torch)
    val flicker by rememberInfiniteTransition(label = "torch").animateFloat(
        0.7f, 1f, infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse), label = "flicker",
    )
    Box(
        Modifier.fillMaxSize().drawBehind {
            // A dungeon wall in the dark: staggered stone blocks, lit by two flickering torches.
            val block = size.width / 5
            val shade = ColorFilter.tint(Color(0xFF4A475C), BlendMode.Modulate)
            for (rowIndex in 0..(size.height / block).toInt()) {
                for (column in -1..5) {
                    translate(column * block + if (rowIndex % 2 == 0) 0f else block / 2, rowIndex * block) {
                        with(wall) { draw(Size(block, block), colorFilter = shade) }
                    }
                }
            }
            val torchSize = size.width * 0.16f
            for (x in listOf(size.width * 0.14f, size.width * 0.86f)) {
                val flame = Offset(x, size.height * 0.13f)
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFFF08A2A).copy(alpha = 0.5f * flicker), Color.Transparent), flame, size.width * 0.6f),
                    size.width * 0.6f, flame,
                )
                translate(x - torchSize / 2, flame.y - torchSize * 0.25f) { with(torch) { draw(Size(torchSize, torchSize)) } }
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Bg), startY = size.height * 0.45f))
        },
    ) {
        Column(
            Modifier.safeDrawingPadding().fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "OUBLIETTES",
                color = Gold,
                style = TextStyle(
                    fontFamily = FontFamily.Serif, fontSize = 38.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp,
                    shadow = Shadow(Color.Black, Offset(0f, 6f), 10f),
                ),
            )
            Text("Find the dungeon walls", color = Ink, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
            Spacer(Modifier.height(12.dp))
            Plank("Campaign", "Level $level") { open(Screen.CAMPAIGN) }
            Plank("Endless", "Random grids") { open(Screen.ENDLESS) }
            Plank("Tutorial", "A step-by-step solve") { open(Screen.TUTORIAL) }
            Plank("Options", "Sounds, music, radio") { open(Screen.OPTIONS) }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (id in listOf(R.drawable.monster_skull, R.drawable.monster_imp, R.drawable.chest, R.drawable.monster_slime, R.drawable.monster_bat)) {
                    Image(painterResource(id), null, Modifier.size(40.dp))
                }
            }
        }
    }
}

/** Menu button: a wooden plank with iron rivets. */
@Composable
private fun Plank(title: String, detail: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Column(
        Modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF8A5C34), Color(0xFF5A391D))))
            .border(3.dp, Color(0xFF24170C), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .drawBehind {
                val inset = 12.dp.toPx()
                for (x in listOf(inset, size.width - inset)) for (y in listOf(inset, size.height - inset)) {
                    drawCircle(Color(0xFF24170C), 4.dp.toPx(), Offset(x, y))
                    drawCircle(Color(0xFFB9B4C8), 2.dp.toPx(), Offset(x - 1, y - 1))
                }
            }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Color(0xFFFFE9A8), fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 21.sp, letterSpacing = 2.sp)
        Text(detail, color = Color(0xFFE6D3B0), fontFamily = FontFamily.Serif, fontSize = 13.sp)
    }
}

/** Shared frame of the inner screens: a way back, a title, then the content. */
@Composable
private fun Page(title: String, onMenu: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            OutlinedButton(onClick = onMenu, Modifier.align(Alignment.CenterStart)) { Text("Menu") }
            Text(title, color = Gold, fontFamily = FontFamily.Serif, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
        content()
    }
}

private fun endlessSeed(prefs: SharedPreferences, size: Int) =
    if (prefs.contains("seed$size")) prefs.getLong("seed$size", 0) else Random.nextLong()

@Composable
private fun Game(prefs: SharedPreferences, audio: Audio, endless: Boolean, onMenu: () -> Unit) {
    var level by remember { mutableIntStateOf(prefs.getInt("level", 1)) }
    var endlessSize by remember { mutableIntStateOf(prefs.getInt("endlessSize", SIZES[0])) }
    var endlessSeed by remember(endlessSize) { mutableLongStateOf(endlessSeed(prefs, endlessSize)) }
    var solvedCount by remember { mutableIntStateOf(prefs.getInt("solved", 0)) }
    var puzzle by remember { mutableStateOf<Puzzle?>(null) }
    var marks by remember { mutableStateOf(IntArray(0)) }

    val size = if (endless) endlessSize else campaignSize(level)
    val seed = if (endless) endlessSeed else level.toLong()
    val marksKey = if (endless) "marksE$size" else "marksC"
    // ponytail: undo history lives in memory only, it is gone after leaving the grid or the app.
    val history = remember(size, seed) { mutableStateListOf<IntArray>() }

    LaunchedEffect(size, seed) {
        puzzle = null
        val generated = withContext(Dispatchers.Default) { generate(size, size, seed) }
        // Saved marks are only reused when they belong to this exact grid.
        val saved = prefs.getString(marksKey, "")!!.removePrefix("$seed:")
        marks = if (saved.length == size * size && saved.all { it in '0'..'2' }) {
            IntArray(saved.length) { saved[it] - '0' }
        } else {
            IntArray(size * size)
        }
        puzzle = generated
        prefs.edit().apply {
            if (endless) putInt("endlessSize", size).putLong("seed$size", seed) else putInt("level", level)
        }.apply()
    }

    fun update(new: IntArray) {
        marks = new
        prefs.edit().putString(marksKey, "$seed:${new.joinToString("")}").apply()
    }

    val p = puzzle
    val solved = p != null && isSolved(p, BooleanArray(marks.size) { marks[it] == WALL })

    Page(if (endless) "Endless" else "Campaign", onMenu) {
        if (endless) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in SIZES) {
                    if (s == size) {
                        Button(onClick = {}) { Text("$s×$s") }
                    } else {
                        OutlinedButton(onClick = { endlessSize = s }) { Text("$s×$s") }
                    }
                }
            }
        }
        Text(if (endless) "$solvedCount solved" else "Level $level · $size×$size", color = Dim)
        if (p == null) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Board(p, marks, locked = solved, celebrate = solved, variety = seed.toInt(), onStroke = { history += marks }, liveMarks = { marks }) { cell, value ->
                val new = marks.copyOf().also { it[cell] = value }
                update(new)
                if (isSolved(p, BooleanArray(new.size) { new[it] == WALL })) audio.victory() else audio.play(audio.click)
            }
            Text(if (solved) "Dungeon solved!" else "", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { update(history.removeAt(history.lastIndex)); audio.play(audio.click) },
                    enabled = history.isNotEmpty() && !solved,
                ) { Text("Undo") }
                OutlinedButton(onClick = { history += marks; update(IntArray(size * size)) }, enabled = !solved) { Text("Clear") }
                Button(onClick = {
                    if (solved && endless) {
                        solvedCount++
                        prefs.edit().putInt("solved", solvedCount).apply()
                    }
                    if (endless) endlessSeed = Random.nextLong() else level++
                }) { Text(if (solved) "Next grid" else "Skip") }
            }
        }
    }
}

/** Replays a fixed solve one deduction at a time. */
@Composable
private fun Tutorial(onMenu: () -> Unit, onPlay: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = tutorialSteps[index]
    val p = tutorialPuzzle
    val marks = IntArray(p.width * p.height)
    for (done in tutorialSteps.take(index + 1)) {
        done.walls.forEach { marks[it] = WALL }
        done.open.forEach { marks[it] = KNOWN_OPEN }
    }
    val last = index == tutorialSteps.lastIndex

    Page("Tutorial", onMenu) {
        Text("Step ${index + 1} / ${tutorialSteps.size}", color = Dim)
        Board(p, marks, locked = true, celebrate = last, focus = step.focus)
        Text(step.text, Modifier.fillMaxWidth().height(150.dp), color = Ink, fontSize = 16.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { index-- }, enabled = index > 0) { Text("Back") }
            Button(onClick = { if (last) onPlay() else index++ }) { Text(if (last) "Play" else "Next") }
        }
    }
}

@Composable
private fun Options(audio: Audio, onMenu: () -> Unit) {
    Page("Options", onMenu) {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Sounds", color = Ink, fontWeight = FontWeight.Bold)
            // Sliders reach the screen edge: keep the system back gesture from stealing their drags.
            Slider(
                modifier = Modifier.systemGestureExclusion(),
                value = audio.soundVolume,
                onValueChange = { audio.soundVolume = it; audio.save() },
                onValueChangeFinished = { audio.play(audio.click) },
            )
            Text("Music", color = Ink, fontWeight = FontWeight.Bold)
            Slider(value = audio.musicVolume, onValueChange = { audio.musicVolume = it; audio.save() }, modifier = Modifier.systemGestureExclusion())
            Row(
                Modifier.fillMaxWidth().toggleable(audio.radio, role = Role.Checkbox) { audio.radio = it; audio.save(retryRadio = true) }.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = audio.radio, onCheckedChange = null)
                Text("Replace music loop by radio", color = Ink)
            }
            Text(
                "Ancient FM (ancientfm.com): live medieval and Renaissance music. " +
                    "Needs an Internet connection.",
                color = Dim, fontSize = 13.sp,
            )
            if (audio.radioFailed) Text("Radio unavailable: back to the music loop.", color = Red, fontSize = 13.sp)
        }
    }
}
