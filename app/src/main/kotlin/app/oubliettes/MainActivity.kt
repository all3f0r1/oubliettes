package app.oubliettes

import android.app.Activity
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.oubliettes.game.Puzzle
import app.oubliettes.game.TutorialStep
import app.oubliettes.game.decodePuzzle
import app.oubliettes.game.generate
import app.oubliettes.game.tutorialPuzzle
import app.oubliettes.game.tutorialSteps
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private val SIZES = listOf(6, 8, 10, 12, 14)
private val DIFFICULTIES = listOf("Baby", "Easy", "Medium", "Hard", "Leeroy") // one per entry of SIZES

private fun difficulty(size: Int) = DIFFICULTIES[SIZES.indexOf(size)]

/** Campaign levels of this [size] solved so far. */
private fun SharedPreferences.solved(size: Int) = all.keys.count { it.startsWith("done${size}_") }

/**
 * Campaign levels per difficulty. The grids are frozen in res/raw/campaign.txt, one per line, the
 * 50 of each size in turn (8, 10 and 12, then 6 and 14, which came later): they stay the same
 * whatever becomes of the generator.
 */
private const val LEVELS = 50

private val RULES = listOf(
    "The numbers give how many walls their row or column holds.",
    "A number turns green and underlined once its count is reached, which does not prove the walls " +
        "are the right ones. It turns red and struck through when there are too many.",
    "Monsters and chests are never walls.",
    "Every monster sits in a dead end, and every dead end holds a monster.",
    "Every chest is in a treasure room: 3×3 open cells, a single chest (anywhere in the room) and a single opening.",
    "Outside treasure rooms, hallways are one cell wide: no open 2×2 block.",
    "All open cells are connected.",
    "Walls are never more than two cells thick: no 3×3 block of walls.",
    "Tap a cell to change it: wall, dot (known open), blank. Pick Wall or Dot under the grid to lay " +
        "that mark at the first tap. Drag to fill a row or a column with what the first cell became; a " +
        "drag only fills blank cells and those of your previous action.",
    "When deduction runs dry and you must try something out, lay a Checkpoint first: Return brings " +
        "the grid back to it if the try leads nowhere.",
)

private val StoneShade = ColorFilter.tint(Color(0xFF4A475C), BlendMode.Modulate)

private enum class Screen { MENU, CAMPAIGN, ENDLESS, TUTORIAL, OPTIONS }

class MainActivity : ComponentActivity() {
    private lateinit var audio: Audio

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is dark whatever the system theme: light icons in the status and navigation bars.
        enableEdgeToEdge(SystemBarStyle.dark(0), SystemBarStyle.dark(0))
        volumeControlStream = AudioManager.STREAM_MUSIC
        val prefs = getPreferences(MODE_PRIVATE)
        audio = Audio(applicationContext, prefs)
        // Calm by default when the system animations are turned off.
        val still = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        Comfort.calm = prefs.getBoolean("calm", still)
        Comfort.plainDigits = prefs.getBoolean("plainDigits", false)
        Comfort.magnifier = prefs.getBoolean("magnifier", true)
        val campaign = resources.openRawResource(R.raw.campaign).bufferedReader().use { it.readLines() }.map { decodePuzzle(it)!! }
            .groupBy { it.width }
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(primary = Gold, onPrimary = Bg, background = Bg, surface = Bg, onSurface = Ink),
            ) {
                Surface(Modifier.fillMaxSize()) {
                    ProvideTextStyle(TextStyle(fontFamily = Almendra, fontSize = 17.sp)) { App(prefs, audio, campaign) }
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
private fun App(prefs: SharedPreferences, audio: Audio, campaign: Map<Int, List<Puzzle>>) {
    var screen by rememberSaveable { mutableStateOf(Screen.MENU) }
    var size by rememberSaveable { mutableIntStateOf(0) } // difficulty chosen in Campaign or Endless, 0 while choosing
    var level by rememberSaveable { mutableIntStateOf(0) } // campaign level on screen, 0 for the table of levels
    val toMenu = { screen = Screen.MENU }
    val inGrid = screen == Screen.TUTORIAL || (screen == Screen.ENDLESS && size != 0) || (screen == Screen.CAMPAIGN && level != 0)
    LaunchedEffect(inGrid) { audio.setMenu(!inGrid) }
    // One step back at a time: the level, its table, the difficulties, the menu.
    BackHandler(enabled = screen != Screen.MENU) {
        when {
            screen == Screen.CAMPAIGN && level != 0 -> level = 0
            size != 0 && (screen == Screen.CAMPAIGN || screen == Screen.ENDLESS) -> size = 0
            else -> toMenu()
        }
    }
    when (screen) {
        Screen.MENU -> {
            // The grid opened last: "C:size:level" in the campaign, "E:size" in Endless.
            val last = prefs.getString("last", "")!!.split(':')
            val lastSize = last.getOrNull(1)?.toIntOrNull()?.takeIf { it in SIZES }
            val lastLevel = last.getOrNull(2)?.toIntOrNull()?.takeIf { it in 1..LEVELS }
            val resume = when {
                lastSize == null -> null
                last[0] == "E" -> "Endless · ${difficulty(lastSize)}"
                last[0] == "C" && lastLevel != null -> "${difficulty(lastSize)} · Level $lastLevel"
                else -> null
            }
            Menu(
                solved = SIZES.sumOf { prefs.solved(it) }, finished = SIZES.count { prefs.solved(it) >= LEVELS },
                tutorialDone = prefs.getBoolean("tutorialDone", false), resume = resume,
                onResume = {
                    size = lastSize!!
                    level = lastLevel ?: 0
                    screen = if (lastLevel != null) Screen.CAMPAIGN else Screen.ENDLESS
                },
            ) { size = 0; level = 0; screen = it }
        }
        Screen.CAMPAIGN -> if (size == 0) {
            Difficulties("Campaign", toMenu, { "$it×$it · ${prefs.solved(it)} / $LEVELS solved" }, { prefs.solved(it) >= LEVELS }) { size = it }
        } else {
            Campaign(prefs, audio, campaign.getValue(size), size, level, { level = it }) { size = 0 }
        }
        Screen.ENDLESS -> if (size == 0) {
            Difficulties("Endless", toMenu, { "$it×$it" }, { false }) { size = it }
        } else {
            Endless(prefs, audio, size) { size = 0 }
        }
        Screen.TUTORIAL -> Tutorial(prefs, toMenu) { size = 0; level = 0; screen = Screen.CAMPAIGN }
        Screen.OPTIONS -> Options(prefs, audio, toMenu)
    }
}

/**
 * The way into the dungeon: its sign, the dark entrance and what watches from it, then the planks.
 * [finished] is how many difficulties of the campaign are solved through.
 */
@Composable
private fun Menu(solved: Int, finished: Int, tutorialDone: Boolean, resume: String?, onResume: () -> Unit, open: (Screen) -> Unit) {
    val wall = painterResource(R.drawable.wall)
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
                // The sign nailed over the entrance.
                Box(Modifier.semantics { heading() }.plank().padding(horizontal = 30.dp, vertical = 2.dp)) {
                    Text(
                        "Oubliettes",
                        color = Gold,
                        style = TextStyle(fontFamily = Fraktur, fontSize = 50.sp, shadow = Shadow(Color.Black, Offset(0f, 4f), 6f)),
                    )
                }
                Entrance(Modifier.weight(1f).fillMaxWidth())
                Plank("Tutorial", matter = Matter.PARCHMENT, done = tutorialDone) { open(Screen.TUTORIAL) }
                if (resume != null) Plank("Continue", resume, matter = Matter.GILDED, onClick = onResume)
                Row(Modifier.widthIn(max = 320.dp).fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Plank("Campaign", "$solved / ${LEVELS * SIZES.size} solved", Modifier.weight(1f).fillMaxHeight(), seals = finished) {
                        open(Screen.CAMPAIGN)
                    }
                    Plank("Endless", modifier = Modifier.weight(1f).fillMaxHeight()) { open(Screen.ENDLESS) }
                }
                Plank("Options", matter = Matter.IRON) { open(Screen.OPTIONS) }
            }
            Spider()
        }
    }
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
    Canvas(modifier) {
        if (size.height < 1f) return@Canvas
        val width = minOf(size.width * 0.54f, size.height * 0.8f)
        val height = minOf(size.height, width * 1.35f)
        val left = (size.width - width) / 2
        val top = size.height - height
        val spring = top + width / 2 // where the arch starts
        val stones = width * 0.13f
        val opening = Path().apply {
            moveTo(left, size.height)
            lineTo(left, spring)
            arcTo(Rect(left, top, left + width, top + width), 180f, 180f, false)
            lineTo(left + width, size.height)
            close()
        }
        val torchSize = width * 0.34f
        val flames = listOf(left - stones - torchSize * 0.6f, left + width + stones + torchSize * 0.6f).map { Offset(it, spring) }
        // The light follows the flame: each of its frames has its own, barely different, brightness.
        val frames = flames.indices.map { (tick.value / 2 + it) % torch.size }
        for ((index, flame) in flames.withIndex()) {
            val glow = 0.42f * listOf(1f, 0.95f, 0.97f)[frames[index]]
            drawCircle(Brush.radialGradient(listOf(Color(0xFFF08A2A).copy(alpha = glow), Color.Transparent), flame, width * 1.1f), width * 1.1f, flame)
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
        }
    }
}

/**
 * Menu entry: a wide sign with a title and maybe a line of [detail]. Its [matter] tells what it is
 * for. [done] strikes a seal next to the title; [seals] are smaller ones under it, one per thing finished.
 */
@Composable
private fun Plank(
    title: String,
    detail: String? = null,
    modifier: Modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
    matter: Matter = Matter.WOOD,
    done: Boolean = false,
    seals: Int = 0,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .plank(matter = matter)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { if (done) stateDescription = "completed" }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = matter.ink, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            if (done) Seal(22.dp)
        }
        if (detail != null) Text(detail, color = matter.ink.copy(alpha = 0.85f), fontSize = 15.sp, textAlign = TextAlign.Center)
        if (seals > 0) {
            Row(
                Modifier.padding(top = 3.dp).semantics { contentDescription = "$seals of ${SIZES.size} difficulties completed" },
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) { repeat(seals) { Seal(13.dp) } }
        }
    }
}

/** The difficulties of Campaign or Endless, each with its line of [detail] and a seal once [finished]. */
@Composable
private fun Difficulties(title: String, onMenu: () -> Unit, detail: (Int) -> String, finished: (Int) -> Boolean, pick: (Int) -> Unit) {
    Page(title, onMenu, spider = true) {
        for ((index, size) in SIZES.withIndex()) Plank(DIFFICULTIES[index], detail(size), done = finished(size)) { pick(size) }
    }
}

/**
 * Shared frame of the inner screens: a way back, a title, then the content. It scrolls when the
 * content is taller than the screen, unless the content does its own scrolling ([scroll] false).
 * A [low] page rests its content on the bottom of the screen, under the thumb, and leaves the
 * spare room below the title. [end] sits opposite the way back. A [spider] visits the screens where
 * nothing else moves; never a grid.
 */
@Composable
private fun Page(
    title: String,
    onBack: () -> Unit,
    back: String = "Menu",
    scroll: Boolean = true,
    low: Boolean = false,
    spider: Boolean = false,
    end: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) = Box(Modifier.safeDrawingPadding().fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(16.dp),
        verticalArrangement = if (low) Arrangement.SpaceBetween else Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The title between the two planks, or on a line of its own when it does not fit: large fonts.
        Layout(
            content = {
                Text(title, Modifier.semantics { heading() }, color = Gold, fontFamily = Fraktur, fontSize = 30.sp, textAlign = TextAlign.Center)
                PlankButton(back, onClick = onBack)
                Box { end() }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val start = measurables[1].measure(loose)
            val opposite = measurables[2].measure(loose)
            val gap = 8.dp.roundToPx()
            val between = constraints.maxWidth - 2 * (maxOf(start.width, opposite.width) + gap)
            val beside = measurables[0].maxIntrinsicWidth(constraints.maxHeight) <= between
            val name = measurables[0].measure(loose.copy(maxWidth = if (beside) between else constraints.maxWidth))
            val planks = maxOf(start.height, opposite.height)
            val row = if (beside) maxOf(planks, name.height) else planks
            layout(constraints.maxWidth, if (beside) row else planks + gap + name.height) {
                start.placeRelative(0, (row - start.height) / 2)
                opposite.placeRelative(constraints.maxWidth - opposite.width, (row - opposite.height) / 2)
                name.placeRelative((constraints.maxWidth - name.width) / 2, if (beside) (row - name.height) / 2 else planks + gap)
            }
        }
        Column(
            Modifier.padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
    if (spider) Spider()
}

/** A notice nailed over the screen: a blackletter [title], then the content. */
@Composable
private fun Notice(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Dialog(onDismiss) {
        Column(
            Modifier.clip(shape).background(Bg).border(2.dp, Gold, shape).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, Modifier.semantics { heading() }, color = Gold, fontFamily = Fraktur, fontSize = 28.sp)
            content()
        }
    }
}

/** A row of planks, centred, that goes on to a second line when it does not fit: large fonts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Planks(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/**
 * Under a grid: what a tap lays, and the rules. [brush] is [WALL] or [KNOWN_OPEN], or 0 when a tap
 * cycles through the marks; it is kept from one grid to the next.
 */
@Composable
private fun Brushes(prefs: SharedPreferences, brush: MutableIntState) {
    var showRules by rememberSaveable { mutableStateOf(false) }
    Planks {
        for ((value, name) in listOf(WALL to "Wall", KNOWN_OPEN to "Dot")) {
            PlankButton(name, selected = brush.intValue == value) {
                brush.intValue = if (brush.intValue == value) 0 else value
                prefs.edit().putInt("brush", brush.intValue).apply()
            }
        }
        Spacer(Modifier.width(8.dp))
        PlankButton("Rules") { showRules = true }
    }
    if (showRules) {
        Notice("Rules", { showRules = false }) {
            for (rule in RULES) Text(rule, Modifier.fillMaxWidth(), color = Ink, fontSize = 16.sp, lineHeight = 20.sp)
            PlankButton("Close") { showRules = false }
        }
    }
}

/** What a tap and a drag do with this [brush], in one line. */
private fun hint(brush: Int) = when (brush) {
    WALL -> "Tap: wall or blank"
    KNOWN_OPEN -> "Tap: dot or blank"
    else -> "Tap: wall, dot, blank"
} + " · Drag: fill a line"

/** The table of levels of the difficulty [size], or the level being played when [level] is not 0. */
@Composable
private fun Campaign(prefs: SharedPreferences, audio: Audio, levels: List<Puzzle>, size: Int, level: Int, open: (Int) -> Unit, onBack: () -> Unit) {
    if (level != 0) {
        LaunchedEffect(size, level) { prefs.edit().putString("last", "C:$size:$level").apply() }
        Game(
            prefs, audio, "Campaign", onBack = { open(0) }, back = "Levels",
            puzzle = levels[level - 1], saveKey = "marks${size}_$level", legacyId = "$level",
            variety = level, status = "${difficulty(size)} · Level $level",
            solvedStatus = { "${difficulty(size)} · ${prefs.solved(size)} / $LEVELS solved" },
            next = if (level < LEVELS) "Next level" else "Levels", onNext = { open(if (level < LEVELS) level + 1 else 0) },
            onSolved = { prefs.edit().putString("done${size}_$level", it).apply() },
        )
        return
    }
    Page(difficulty(size), onBack, back = "Campaign", scroll = false, spider = true) {
        Text("$size×$size · ${prefs.solved(size)} / $LEVELS solved", color = Dim)
        LazyVerticalGrid(
            GridCells.Fixed(5),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(LEVELS) { index ->
                val done = prefs.getString("done${size}_${index + 1}", null)?.takeIf { it.length == size * size }
                // A save holds its marks after the first colon: begun once any cell is marked.
                val begun = prefs.getString("marks${size}_${index + 1}", "")!!.split(':').getOrNull(1)?.any { it != '0' } == true
                LevelTile(index + 1, done, begun, size) { open(index + 1) }
            }
        }
    }
}

/**
 * A stone slab bearing the level number. A level begun is rimmed in gold and says so; a solved one
 * shows a small picture of the finished dungeon.
 */
@Composable
private fun LevelTile(number: Int, done: String?, begun: Boolean, size: Int, onClick: () -> Unit) {
    val stone = painterResource(R.drawable.wall)
    val shape = RoundedCornerShape(4.dp)
    Column(
        Modifier
            .aspectRatio(1f)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { stateDescription = if (done != null) "solved" else if (begun) "begun" else "not started" }
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
            }
            .then(if (begun && done == null) Modifier.border(2.dp, Gold, shape) else Modifier),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "$number",
            color = if (done == null) Ink else Color.White,
            style = TextStyle(fontFamily = Almendra, fontWeight = FontWeight.Bold, fontSize = 22.sp, shadow = Shadow(Color.Black, Offset(0f, 2f), 6f)),
        )
        // Already said by the state description.
        if (begun && done == null) Text("begun", Modifier.clearAndSetSemantics {}, color = Gold, fontSize = 12.sp, lineHeight = 12.sp)
    }
}

private fun endlessSeed(prefs: SharedPreferences, size: Int) =
    if (prefs.contains("seed$size")) prefs.getLong("seed$size", 0) else Random.nextLong()

/** Random grids of one [size], one after the other. */
@Composable
private fun Endless(prefs: SharedPreferences, audio: Audio, size: Int, onBack: () -> Unit) {
    var seed by remember { mutableLongStateOf(endlessSeed(prefs, size)) }
    var solvedCount by remember { mutableIntStateOf(prefs.getInt("solved", 0)) }
    val status = "${difficulty(size)} · $solvedCount solved"
    // One grid: leaving it (the next grid, the way back) cancels its digging.
    key(seed) {
        // The grid itself is saved next to its seed, so a save never depends on the generator giving it again.
        var puzzle by remember {
            mutableStateOf(prefs.getString("puzzleE$size", "")!!.takeIf { it.startsWith("$seed;") }?.let { decodePuzzle(it.substringAfter(';')) })
        }
        var failed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            prefs.edit().putLong("seed$size", seed).putString("last", "E:$size").apply()
            if (puzzle != null) return@LaunchedEffect
            val dug = withContext(Dispatchers.Default) { generate(size, size, seed) { !isActive } }
            if (dug == null) {
                failed = true
            } else {
                prefs.edit().putString("puzzleE$size", "$seed;${dug.encode()}").apply()
                puzzle = dug
            }
        }
        val p = puzzle
        if (p != null) {
            Game(
                prefs, audio, "Endless", onBack, back = "Difficulty",
                puzzle = p, saveKey = "marksE$size", legacyId = "$seed",
                variety = seed.toInt(), status = status, solvedStatus = { status },
                next = "Next grid", onNext = { seed = Random.nextLong() }, skip = "New grid",
                // A grid counts the moment it is solved, and only once however often it is solved again.
                onSolved = {
                    if (!prefs.contains("counted$size") || prefs.getLong("counted$size", 0) != seed) {
                        solvedCount++
                        prefs.edit().putInt("solved", solvedCount).putLong("counted$size", seed).apply()
                    }
                },
            )
        } else {
            Page("Endless", onBack, back = "Difficulty", low = true) {
                Text(status, color = Dim)
                Column(
                    Modifier.fillMaxWidth().aspectRatio(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (failed) {
                        Text("The diggers gave up on this one.", color = Dim, fontStyle = FontStyle.Italic)
                        PlankButton("Dig another") { seed = Random.nextLong() }
                    } else {
                        Diggers()
                        Text("Digging…", Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = Dim, fontStyle = FontStyle.Italic)
                    }
                }
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
            translate(index * cell, size.height - cell - hop) {
                if (kind == null) {
                    with(torch[(tick.value / 2) % torch.size]) { draw(Size(cell, cell)) }
                } else {
                    drawMonster(kind, bodies, tick.value, index, cell)
                }
            }
        }
    }
}

/**
 * One grid being played. Marks and their history are saved under [saveKey] after every action;
 * [legacyId] is what a save of 0.4 was tagged with. [onSolved] receives the picture of the finished
 * dungeon, the moment it is solved and again whenever a solved grid is reopened. [onNext] is only
 * offered once solved; [skip], when given, names a plank that leaves an unsolved grid for the next
 * one. [status] is shown above the grid, and [solvedStatus] under the announcement once it is solved.
 *
 * With the help turned on in Options, a Check plank crosses the marks that are wrong, until the next action.
 */
@Composable
private fun Game(
    prefs: SharedPreferences,
    audio: Audio,
    title: String,
    onBack: () -> Unit,
    back: String = "Menu",
    puzzle: Puzzle,
    saveKey: String,
    legacyId: String,
    variety: Int,
    status: String,
    solvedStatus: () -> String,
    next: String,
    onNext: () -> Unit,
    skip: String? = null,
    onSolved: (picture: String) -> Unit,
) {
    val session = remember(puzzle) { Session(puzzle, prefs.getString(saveKey, null), legacyId) }
    val haptics = remember { prefs.getBoolean("haptics", true) }
    val ruleHint = remember { prefs.getBoolean("ruleHint", false) }
    val help = remember { prefs.getBoolean("help", false) }
    val brush = remember { mutableIntStateOf(prefs.getInt("brush", 0)) }
    // What the player is asked to confirm, by the name of its plank: [skip] or Reset. Empty for nothing.
    var asking by rememberSaveable(puzzle) { mutableStateOf("") }
    // The marks found wrong by the last check: null when the grid changed since, or was never checked.
    var wrong by remember(session) { mutableStateOf<Set<Int>?>(null) }
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

    DisposableEffect(view) {
        view.keepScreenOn = prefs.getBoolean("keepScreenOn", true)
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(session) { if (session.solved) onSolved(session.picture()) }

    /** Wraps a button's action: a click, and the save. */
    fun act(action: () -> Unit) = {
        action()
        wrong = null
        audio.play(audio.click)
        prefs.edit().putString(saveKey, session.save()).apply()
    }

    Page(
        title, onBack, back, low = true,
        end = {
            // The marks of a grid left this way are gone for good: ask first when there are any.
            if (skip != null && !session.solved) PlankButton(skip) { if (session.marks.any { it != 0 }) asking = skip else onNext() }
        },
    ) {
        // One node, so that screen readers announce what it turns into.
        Column(
            Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (session.solved) {
                Text("Dungeon solved!", color = Gold, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(solvedStatus(), color = Ink)
            } else {
                Text(status, color = Dim)
                wrong?.let {
                    val found = if (it.isEmpty()) "No mistake so far." else if (it.size == 1) "1 mistake, crossed out." else "${it.size} mistakes, crossed out."
                    Text(found, color = Ink, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
                }
                if (ruleHint && session.ruleBroken) {
                    Text("Every count is met, yet a rule is broken.", color = Ink, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
                }
            }
        }
        Board(
            session, locked = session.solved, celebrate = session.solved, variety = variety, wrong = wrong.orEmpty(), brush = brush.intValue,
            onPaint = {
                wrong = null
                audio.play(audio.click)
                if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onStroke = { won ->
                prefs.edit().putString(saveKey, session.save()).apply()
                if (won) {
                    audio.victory()
                    if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSolved(session.picture())
                }
            },
        )
        if (session.solved) {
            Planks {
                PlankButton("Play again", onClick = act(session::reset))
                PlankButton(next, onClick = onNext)
            }
        } else {
            Brushes(prefs, brush)
            Planks {
                PlankButton("Undo", enabled = session.canUndo, onClick = act(session::undo))
                PlankButton("Redo", enabled = session.canRedo, onClick = act(session::redo))
                PlankButton("Reset", enabled = session.marks.any { it != 0 }) { asking = "Reset" }
            }
            Planks {
                PlankButton("Checkpoint", enabled = session.canCheckpoint, onClick = act(session::checkpoint))
                PlankButton("Return", enabled = session.canReturn, onClick = act(session::toCheckpoint))
                if (help) PlankButton("Check") { wrong = session.mistakes() }
            }
            Text(hint(brush.intValue), color = Dim, fontSize = 15.sp, textAlign = TextAlign.Center)
        }
    }
    if (asking.isNotEmpty()) {
        Notice(asking, { asking = "" }) {
            Text(
                if (asking == skip) "Leave this grid? Its marks will be lost." else "Empty this grid? Undo brings the marks back.",
                color = Ink, textAlign = TextAlign.Center,
            )
            Planks {
                PlankButton("Stay") { asking = "" }
                PlankButton(asking, onClick = if (asking == skip) { { asking = ""; onNext() } } else act { asking = ""; session.reset() })
            }
        }
    }
}

private fun Session.show(step: TutorialStep) {
    paint(step.walls, WALL)
    paint(step.open, KNOWN_OPEN)
}

/** A fixed solve, one deduction at a time: the player marks the cells of each step, or asks to be shown. */
@Composable
private fun Tutorial(prefs: SharedPreferences, onMenu: () -> Unit, onPlay: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    // Back after the process was killed, the steps already passed are marked again.
    val session = remember { Session(tutorialPuzzle).also { s -> tutorialSteps.take(index).forEach { s.show(it) } } }
    val brush = remember { mutableIntStateOf(prefs.getInt("brush", 0)) }
    val step = tutorialSteps[index]
    val marks = session.marks
    val toMark = (step.walls.filter { marks[it] != WALL } + step.open.filter { marks[it] != KNOWN_OPEN }).toSet()
    val last = index == tutorialSteps.lastIndex
    LaunchedEffect(last) { if (last) prefs.edit().putBoolean("tutorialDone", true).apply() }

    Page("Tutorial", onMenu, low = true) {
        Text("Step ${index + 1} / ${tutorialSteps.size}", color = Dim)
        Board(session, locked = last, celebrate = last && session.solved, focus = step.focus, toMark = toMark, brush = brush.intValue)
        Text(step.text, Modifier.fillMaxWidth().heightIn(min = 130.dp), color = Ink, fontSize = 18.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
        Text(
            if (toMark.isNotEmpty()) "Your turn: ${toMark.size} to mark" else "",
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = Gold, fontStyle = FontStyle.Italic,
        )
        Brushes(prefs, brush)
        Planks {
            PlankButton("Previous", enabled = index > 0) { index-- }
            PlankButton("Show me", enabled = toMark.isNotEmpty()) { session.show(step) }
            PlankButton(if (last) "Play" else "Next", enabled = toMark.isEmpty()) { if (last) onPlay() else index++ }
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

/** A setting kept under [key], [default] until changed. Read where it is used with the same key; [onChange] applies it at once. */
@Composable
private fun PrefToggle(prefs: SharedPreferences, key: String, text: String, default: Boolean = true, onChange: (Boolean) -> Unit = {}) {
    var on by remember { mutableStateOf(prefs.getBoolean(key, default)) }
    Toggle(text, on) {
        on = it
        prefs.edit().putBoolean(key, it).apply()
        onChange(it)
    }
}

@Composable
private fun Options(prefs: SharedPreferences, audio: Audio, onMenu: () -> Unit) {
    val context = LocalContext.current
    var said by rememberSaveable { mutableStateOf("") }
    // A save read from a file, waiting for the player to agree to lose what this phone holds.
    var loaded by remember { mutableStateOf<Map<String, Any>?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            said = runCatching { context.contentResolver.openOutputStream(uri)!!.use { it.write(backupText(prefs.all).toByteArray()) } }
                .fold({ "Progress saved to the file." }, { "The file could not be written." })
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            loaded = runCatching { context.contentResolver.openInputStream(uri)!!.use { parseBackup(it.readBytes().decodeToString()) } }.getOrNull()
            said = if (loaded == null) "This file is not an Oubliettes save." else ""
        }
    }
    Page("Options", onMenu, spider = true) {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // The sliders carry these names themselves for screen readers.
            Text("Sounds", Modifier.clearAndSetSemantics {}, color = Ink, fontWeight = FontWeight.Bold)
            IronSlider("Sounds", audio.soundVolume, { audio.soundVolume = it; audio.save() }, onValueChangeFinished = { audio.play(audio.click) })
            Text("Music", Modifier.clearAndSetSemantics {}, color = Ink, fontWeight = FontWeight.Bold)
            IronSlider("Music", audio.musicVolume, { audio.musicVolume = it; audio.save() })
            Toggle("Replace music loops by radio", audio.radio) { audio.radio = it; audio.save(retryRadio = true) }
            Text(
                "Ancient FM (ancientfm.com): live medieval and Renaissance music. " +
                    "Needs an Internet connection.",
                color = Dim, fontSize = 15.sp,
            )
            if (audio.radioFailed) Text("Radio unavailable: back to the music loops.", color = Red, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            PrefToggle(prefs, "haptics", "Vibrate on every mark")
            PrefToggle(prefs, "keepScreenOn", "Keep the screen on while playing")
            PrefToggle(prefs, "ruleHint", "Say when every count is met but a rule is broken", default = false)
            PrefToggle(prefs, "help", "Help: a Check plank under the grid crosses out the wrong marks", default = false)
            PrefToggle(prefs, "magnifier", "Magnifier under the finger on large grids") { Comfort.magnifier = it }
            PrefToggle(prefs, "plainDigits", "Plain digits for the wall counts", Comfort.plainDigits) { Comfort.plainDigits = it }
            PrefToggle(prefs, "calm", "Calm mode: nothing moves on its own", Comfort.calm) { Comfort.calm = it }
            Spacer(Modifier.height(8.dp))
            Text("Progress", color = Ink, fontWeight = FontWeight.Bold)
            Text(
                "Progress stays through updates, but not when the app is removed. A file keeps it: " +
                    "solved levels, grids in progress and settings.",
                color = Dim, fontSize = 15.sp,
            )
            Planks {
                PlankButton("Save to a file") { export.launch("oubliettes-save.txt") }
                PlankButton("Load a file") { import.launch(arrayOf("text/plain")) }
            }
            Text(said, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = Ink, fontStyle = FontStyle.Italic)
        }
    }
    loaded?.let { saved ->
        Notice("Load a file", { loaded = null }) {
            Text("Replace the progress and the settings on this phone by those of the file?", color = Ink, textAlign = TextAlign.Center)
            Planks {
                PlankButton("Keep mine") { loaded = null }
                PlankButton("Load") {
                    val editor = prefs.edit().clear()
                    for ((key, value) in saved) when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        else -> editor.putString(key, value as String)
                    }
                    editor.apply()
                    // Sound, comfort and every screen read the preferences when they start: start again.
                    (context as Activity).recreate()
                }
            }
        }
    }
}
