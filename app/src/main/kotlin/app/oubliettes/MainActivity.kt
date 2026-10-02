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
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.oubliettes.game.Puzzle
import app.oubliettes.game.drawing
import app.oubliettes.game.generate
import app.oubliettes.game.isSolved
import app.oubliettes.game.tutorialPuzzle
import app.oubliettes.game.tutorialSteps
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val SIZES = listOf(8, 10, 12)
private val DIFFICULTIES = listOf("Easy", "Medium", "Hard") // one per entry of SIZES

/** Campaign levels per difficulty. Level n of a size is always the same grid (its seed is n). */
private const val LEVELS = 50

private val StoneShade = ColorFilter.tint(Color(0xFF4A475C), BlendMode.Modulate)

private enum class Screen { MENU, CAMPAIGN, ENDLESS, TUTORIAL, OPTIONS }

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
                Surface(Modifier.fillMaxSize()) {
                    ProvideTextStyle(TextStyle(fontFamily = Almendra, fontSize = 17.sp)) { App(prefs, audio) }
                }
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
    var level by rememberSaveable { mutableIntStateOf(0) } // campaign level on screen, 0 for the table of levels
    val toMenu = { screen = Screen.MENU }
    val inGrid = screen == Screen.ENDLESS || screen == Screen.TUTORIAL || (screen == Screen.CAMPAIGN && level != 0)
    LaunchedEffect(inGrid) { audio.setMenu(!inGrid) }
    BackHandler(enabled = screen != Screen.MENU) { if (screen == Screen.CAMPAIGN && level != 0) level = 0 else toMenu() }
    when (screen) {
        Screen.MENU -> Menu(prefs.all.keys.count { it.startsWith("done") }) { screen = it }
        Screen.CAMPAIGN -> Campaign(prefs, audio, level, { level = it }, toMenu)
        Screen.ENDLESS -> Endless(prefs, audio, toMenu)
        Screen.TUTORIAL -> Tutorial(toMenu) { level = 0; screen = Screen.CAMPAIGN }
        Screen.OPTIONS -> Options(prefs, audio, toMenu)
    }
}

@Composable
private fun Menu(solved: Int, open: (Screen) -> Unit) {
    val wall = painterResource(R.drawable.wall)
    val torch = torchFrames()
    val chest = chestFrames()
    val bodies = monsterBodies()
    val tick = rememberTick()
    val flicker by rememberInfiniteTransition(label = "torch").animateFloat(
        0.7f, 1f, infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse), label = "flicker",
    )
    Box(
        Modifier.fillMaxSize().drawBehind {
            // A dungeon wall in the dark: staggered stone blocks, lit by two flickering torches.
            val block = size.width / 5
            for (rowIndex in 0..(size.height / block).toInt()) {
                for (column in -1..5) {
                    translate(column * block + if (rowIndex % 2 == 0) 0f else block / 2, rowIndex * block) {
                        with(wall) { draw(Size(block, block), colorFilter = StoneShade) }
                    }
                }
            }
            val torchSize = size.width * 0.16f
            for ((index, x) in listOf(size.width * 0.14f, size.width * 0.86f).withIndex()) {
                val flame = Offset(x, size.height * 0.13f)
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFFF08A2A).copy(alpha = 0.5f * flicker), Color.Transparent), flame, size.width * 0.6f),
                    size.width * 0.6f, flame,
                )
                translate(x - torchSize / 2, flame.y - torchSize * 0.25f) {
                    with(torch[(tick.value + index) % torch.size]) { draw(Size(torchSize, torchSize)) }
                }
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
                "Oubliettes",
                color = Gold,
                style = TextStyle(fontFamily = Fraktur, fontSize = 58.sp, shadow = Shadow(Color.Black, Offset(0f, 6f), 10f)),
            )
            Text("Find the dungeon walls", color = Ink, fontStyle = FontStyle.Italic, fontSize = 19.sp)
            Spacer(Modifier.height(12.dp))
            Plank("Campaign", "$solved / ${LEVELS * SIZES.size} solved") { open(Screen.CAMPAIGN) }
            Plank("Endless", "Random grids") { open(Screen.ENDLESS) }
            Plank("Tutorial", "A step-by-step solve") { open(Screen.TUTORIAL) }
            Plank("Options", "Sounds, music, comfort") { open(Screen.OPTIONS) }
            Spacer(Modifier.height(12.dp))
            // The dungeon's dwellers: skull, imp, chest, slime, bat.
            val dwellers = listOf(3, 2, null, 0, 4)
            Canvas(Modifier.size(width = 264.dp, height = 44.dp)) {
                val cell = size.height
                val gap = (size.width - cell * dwellers.size) / (dwellers.size - 1)
                for ((index, kind) in dwellers.withIndex()) {
                    translate(index * (cell + gap), 0f) {
                        if (kind == null) {
                            with(chest[(tick.value / 2) % chest.size]) { draw(Size(cell, cell)) }
                        } else {
                            drawMonster(kind, bodies, tick.value, index, cell)
                        }
                    }
                }
            }
        }
    }
}

/** Menu entry: a wide plank with a title and a line of detail. */
@Composable
private fun Plank(title: String, detail: String, onClick: () -> Unit) {
    Column(
        Modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .plank()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Cream, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Text(detail, color = Color(0xFFE6D3B0), fontSize = 15.sp)
    }
}

/** Shared frame of the inner screens: a way back, a title, then the content. */
@Composable
private fun Page(title: String, onBack: () -> Unit, back: String = "Menu", content: @Composable () -> Unit) {
    Column(
        Modifier.safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PlankButton(back, Modifier.align(Alignment.CenterStart), onClick = onBack)
            Text(title, color = Gold, fontFamily = Fraktur, fontSize = 30.sp)
        }
        content()
    }
}

/** The table of levels of one difficulty, or the level being played when [level] is not 0. */
@Composable
private fun Campaign(prefs: SharedPreferences, audio: Audio, level: Int, open: (Int) -> Unit, onMenu: () -> Unit) {
    var size by rememberSaveable { mutableIntStateOf(prefs.getInt("campaignSize", SIZES[0])) }
    if (level != 0) {
        Game(
            prefs, audio, "Campaign", onBack = { open(0) }, back = "Levels",
            size = size, seed = level.toLong(), marksKey = "marks${size}_$level", doneKey = "done${size}_$level",
            status = "${DIFFICULTIES[SIZES.indexOf(size)]} · Level $level",
            next = "Next level", onNext = { open(if (level < LEVELS) level + 1 else 0) },
        )
        return
    }
    Page("Campaign", onMenu) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((index, s) in SIZES.withIndex()) {
                PlankButton("${DIFFICULTIES[index]}\n$s×$s", Modifier.weight(1f), selected = s == size) {
                    size = s
                    prefs.edit().putInt("campaignSize", s).apply()
                }
            }
        }
        LazyVerticalGrid(
            GridCells.Fixed(5),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(LEVELS) { index ->
                val done = prefs.getString("done${size}_${index + 1}", null)?.takeIf { it.length == size * size }
                LevelTile(index + 1, done, size) { open(index + 1) }
            }
        }
    }
}

/** A stone slab bearing the level number; once solved, a small picture of the finished dungeon. */
@Composable
private fun LevelTile(number: Int, done: String?, size: Int, onClick: () -> Unit) {
    val stone = painterResource(R.drawable.wall)
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .drawBehind {
                if (done == null) {
                    with(stone) { draw(this@drawBehind.size, colorFilter = StoneShade) }
                } else {
                    val cell = this.size.width / size
                    for (i in done.indices) {
                        val color = when (done[i]) {
                            '#' -> Color(0xFFA29EBA)
                            'M' -> Red
                            'C' -> Gold
                            else -> OpenFloor
                        }
                        // One pixel wider than the cell: no hairline gaps between neighbours.
                        drawRect(color, Offset(i % size * cell, i / size * cell), Size(cell + 1, cell + 1))
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$number",
            color = if (done == null) Ink else Color.White,
            style = TextStyle(fontFamily = Almendra, fontWeight = FontWeight.Bold, fontSize = 22.sp, shadow = Shadow(Color.Black, Offset(0f, 2f), 6f)),
        )
    }
}

private fun endlessSeed(prefs: SharedPreferences, size: Int) =
    if (prefs.contains("seed$size")) prefs.getLong("seed$size", 0) else Random.nextLong()

@Composable
private fun Endless(prefs: SharedPreferences, audio: Audio, onMenu: () -> Unit) {
    var size by remember { mutableIntStateOf(prefs.getInt("endlessSize", SIZES[0])) }
    var seed by remember(size) { mutableLongStateOf(endlessSeed(prefs, size)) }
    var solvedCount by remember { mutableIntStateOf(prefs.getInt("solved", 0)) }
    LaunchedEffect(size, seed) { prefs.edit().putInt("endlessSize", size).putLong("seed$size", seed).apply() }
    Game(
        prefs, audio, "Endless", onMenu,
        size = size, seed = seed, marksKey = "marksE$size", doneKey = null,
        status = "$solvedCount solved",
        header = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in SIZES) PlankButton("$s×$s", selected = s == size) { size = s }
            }
        },
        next = "Next grid",
        onNext = {
            solvedCount++
            prefs.edit().putInt("solved", solvedCount).apply()
            seed = Random.nextLong()
        },
    )
}

/**
 * One grid, generated from [size] and [seed]. Marks are saved under [marksKey] after every move; the
 * finished dungeon is saved under [doneKey] when there is one. [onNext] is only offered once solved.
 */
@Composable
private fun Game(
    prefs: SharedPreferences,
    audio: Audio,
    title: String,
    onBack: () -> Unit,
    back: String = "Menu",
    size: Int,
    seed: Long,
    marksKey: String,
    doneKey: String?,
    status: String,
    header: @Composable () -> Unit = {},
    next: String,
    onNext: () -> Unit,
) {
    var puzzle by remember { mutableStateOf<Puzzle?>(null) }
    var marks by remember { mutableStateOf(IntArray(0)) }
    // ponytail: undo history lives in memory only, it is gone after leaving the grid or the app.
    val history = remember(size, seed) { mutableStateListOf<IntArray>() }
    // Reset cannot be undone, so it asks twice: the first tap arms it for a few seconds.
    var armed by remember(size, seed) { mutableStateOf(false) }
    val confirmReset = remember { prefs.getBoolean("confirmReset", true) }
    val haptics = remember { prefs.getBoolean("haptics", true) }
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

    DisposableEffect(view) {
        view.keepScreenOn = prefs.getBoolean("keepScreenOn", true)
        onDispose { view.keepScreenOn = false }
    }
    if (armed) LaunchedEffect(Unit) { delay(3000); armed = false }

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
    }

    fun update(new: IntArray) {
        marks = new
        prefs.edit().putString(marksKey, "$seed:${new.joinToString("")}").apply()
    }

    val p = puzzle
    val solved = p != null && isSolved(p, BooleanArray(marks.size) { marks[it] == WALL })

    Page(title, onBack, back) {
        header()
        Text(status, color = Dim)
        if (p == null) {
            val torch = torchFrames()
            val tick = rememberTick()
            Column(
                Modifier.fillMaxWidth().aspectRatio(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Canvas(Modifier.size(72.dp)) { with(torch[tick.value % torch.size]) { draw(this@Canvas.size) } }
                Text("Digging…", color = Dim, fontStyle = FontStyle.Italic)
            }
        } else {
            Board(
                p, marks, locked = solved, celebrate = solved, variety = seed.toInt(),
                onStroke = { history += marks; armed = false },
                liveMarks = { marks },
                // The cells the previous action changed: a drag may paint over those.
                repaintable = { history.lastOrNull()?.let { before -> marks.indices.filter { before[it] != marks[it] }.toSet() } ?: emptySet() },
            ) { cell, value ->
                val new = marks.copyOf().also { it[cell] = value }
                update(new)
                val walls = BooleanArray(new.size) { new[it] == WALL }
                val won = isSolved(p, walls)
                if (won) {
                    audio.victory()
                    if (doneKey != null) prefs.edit().putString(doneKey, drawing(p, walls)).apply()
                } else {
                    audio.play(audio.click)
                }
                if (haptics) haptic.performHapticFeedback(if (won) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
            }
            Text(if (solved) "Dungeon solved!" else "", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlankButton("Undo", enabled = history.isNotEmpty() && !solved) {
                    update(history.removeAt(history.lastIndex))
                    audio.play(audio.click)
                }
                PlankButton(if (armed) "Sure?" else "Reset", enabled = marks.any { it != 0 }) {
                    if (confirmReset && !armed) {
                        armed = true
                    } else {
                        armed = false
                        history.clear()
                        update(IntArray(size * size))
                    }
                }
                PlankButton(next, enabled = solved, onClick = onNext)
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
        Text(step.text, Modifier.fillMaxWidth().height(150.dp), color = Ink, fontSize = 18.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlankButton("Back", enabled = index > 0) { index-- }
            PlankButton(if (last) "Play" else "Next") { if (last) onPlay() else index++ }
        }
    }
}

@Composable
private fun Toggle(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox, onValueChange = onChange).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IronCheck(checked)
        Text(text, color = Ink)
    }
}

/** A comfort setting, on unless turned off. Read where it is used with the same [key]. */
@Composable
private fun PrefToggle(prefs: SharedPreferences, key: String, text: String) {
    var on by remember { mutableStateOf(prefs.getBoolean(key, true)) }
    Toggle(text, on) {
        on = it
        prefs.edit().putBoolean(key, it).apply()
    }
}

@Composable
private fun Options(prefs: SharedPreferences, audio: Audio, onMenu: () -> Unit) {
    Page("Options", onMenu) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Sounds", color = Ink, fontWeight = FontWeight.Bold)
            IronSlider(audio.soundVolume, { audio.soundVolume = it; audio.save() }, onValueChangeFinished = { audio.play(audio.click) })
            Text("Music", color = Ink, fontWeight = FontWeight.Bold)
            IronSlider(audio.musicVolume, { audio.musicVolume = it; audio.save() })
            Toggle("Replace music loops by radio", audio.radio) { audio.radio = it; audio.save(retryRadio = true) }
            Text(
                "Ancient FM (ancientfm.com): live medieval and Renaissance music. " +
                    "Needs an Internet connection.",
                color = Dim, fontSize = 15.sp,
            )
            if (audio.radioFailed) Text("Radio unavailable: back to the music loops.", color = Red, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            PrefToggle(prefs, "confirmReset", "Ask twice before a reset")
            PrefToggle(prefs, "haptics", "Vibrate on every mark")
            PrefToggle(prefs, "keepScreenOn", "Keep the screen on while playing")
        }
    }
}
