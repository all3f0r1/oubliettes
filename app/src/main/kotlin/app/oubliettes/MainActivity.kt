package app.oubliettes

import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import app.oubliettes.game.Puzzle
import app.oubliettes.game.decodePuzzle

internal val SIZES = listOf(6, 8, 10, 12, 14)
internal val DIFFICULTIES = listOf("Novice", "Easy", "Medium", "Hard", "Leeroy") // one per entry of SIZES

internal fun difficulty(size: Int) = DIFFICULTIES[SIZES.indexOf(size)]

// How much reasoning an Endless grid asks for. Larger grids take more [effort] whatever they are: each
// entry of SIZES has its own two cuts, where they leave about a third of what the generator digs on either side.
internal val REASONINGS = listOf("Gentle", "Tricky", "Fiendish")
private val CUTS = listOf(3 to 14, 54 to 138, 246 to 382, 477 to 657, 716 to 979)

internal fun effortOf(size: Int, reasoning: Int) = CUTS[SIZES.indexOf(size)].let { (tricky, fiendish) ->
    listOf(0 until tricky, tricky until fiendish, fiendish..Int.MAX_VALUE)[reasoning]
}

/** Campaign levels of this [size] solved so far. */
internal fun SharedPreferences.solved(size: Int) = all.keys.count { it.startsWith("done${size}_") }

/**
 * Campaign levels per difficulty. The grids are frozen in res/raw/campaign.txt, one per line, the
 * 50 of each size in turn, from the one that asks for the least [app.oubliettes.game.effort] to the
 * one that asks for the most, hallways and treasure rooms in turn: they stay the same whatever
 * becomes of the generator. Ten levels make a chapter, named in CHAPTERS for each entry of SIZES.
 * CAMPAIGN counts the campaigns there have been; the levels of a former one are no longer played,
 * and the dungeons solved in it are kept in the gallery.
 */
internal const val LEVELS = 50
internal const val CAMPAIGN = 3
internal val CHAPTERS = listOf(
    listOf("The Gatehouse", "The Guardroom", "The Kennels", "The Kitchens", "The Wine Cellar"),
    listOf("The Dungeons", "The Cells", "The Well", "The Old Sewers", "The Rat Warren"),
    listOf("The Crypts", "The Ossuary", "The Chapel Below", "The Tombs of the Kings", "The Sealed Vault"),
    listOf("The King's Stores", "The Armoury", "The Treasury", "The Counting House", "The Hidden Hoard"),
    listOf("The Forgotten Maze", "The Sunken Halls", "The Roots of the Keep", "The Black Pit", "The Oubliette"),
)

internal val RULES = listOf(
    "The numbers give how many walls their row or column holds.",
    "A number is dimmed and struck through once its count is reached, which does not prove the walls " +
        "are the right ones. It turns red and underlined when there are too many.",
    "Monsters and chests are never walls.",
    "Every monster sits in a dead end, and every dead end holds a monster.",
    "Every chest is in a treasure room: 3×3 open cells, a single chest (anywhere in the room) and a single opening.",
    "Outside treasure rooms, hallways are one cell wide: no open 2×2 block.",
    "All open cells are connected.",
    "Walls are never more than two cells thick: no 3×3 block of walls.",
    "Tap a cell to change it: wall, hallway (a dot: known open), blank. Pick Wall or Hallway under the grid to lay " +
        "that mark at the first tap. Drag to fill a row or a column with what the first cell became; a " +
        "drag only fills blank cells and those of your previous action.",
    "When deduction runs dry and you must try something out, lay a Checkpoint first: Return brings " +
        "the grid back to it if the try leads nowhere. What was laid since bears a parchment corner.",
    "On a grid with small cells, spread two fingers to see it larger, and move it with both. Tap a count for plain digits.",
)

internal val StoneShade = ColorFilter.tint(Color(0xFF4A475C), BlendMode.Modulate)

internal enum class Screen { MENU, CAMPAIGN, ENDLESS, DAILY, SHARED, GALLERY, TUTORIAL, OPTIONS }

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
        if (prefs.getInt("campaign", 1) < CAMPAIGN) {
            // Levels solved or begun in the former campaign would land on grids they were never played on:
            // the dungeons solved go to the gallery.
            val former = prefs.all.filterKeys { Regex("(done|marks)\\d+_\\d+").matches(it) }
            prefs.keep(former.filterKeys { it.startsWith("done") }.values.filterIsInstance<String>())
            val editor = prefs.edit().putInt("campaign", CAMPAIGN)
            for (key in former.keys) editor.remove(key)
            if (prefs.getString("last", "")!!.startsWith("C:")) editor.remove("last")
            editor.apply()
        }
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

    override fun onResume() {
        super.onResume()
        audio.setForeground(hasWindowFocus())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Android 15+ only grants audio focus to the top app. At launch onStart is too early;
        // the first focused window starts the music, without waiting for a tap or another screen.
        if (hasFocus && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            audio.setForeground(true)
        }
    }

    override fun onPause() {
        audio.setForeground(false)
        super.onPause()
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
    // What Endless digs: a size, kept apart from [size] while it is being chosen, and an index in REASONINGS.
    var endlessSize by rememberSaveable { mutableIntStateOf(prefs.getInt("endlessSize", 8).takeIf { it in SIZES } ?: 8) }
    var reasoning by rememberSaveable { mutableIntStateOf(prefs.getInt("reasoning", 1).coerceIn(REASONINGS.indices)) }
    val toMenu = { screen = Screen.MENU }
    val today = remember { today() }
    // One step back at a time: the level, its table, the difficulties, the menu.
    BackHandler(enabled = screen != Screen.MENU) {
        when {
            screen == Screen.SHARED || screen == Screen.GALLERY -> { size = 0; screen = Screen.ENDLESS }
            (screen == Screen.CAMPAIGN || screen == Screen.TUTORIAL) && level != 0 -> level = 0
            size != 0 && (screen == Screen.CAMPAIGN || screen == Screen.ENDLESS) -> size = 0
            else -> toMenu()
        }
    }
    when (screen) {
        Screen.MENU -> {
            // The grid opened last: "C:size:level" in the campaign, "E:size" in Endless, "D:day" for a
            // daily one, "S" for the one a friend sent.
            val last = prefs.getString("last", "")!!.split(':')
            val lastSize = last.getOrNull(1)?.toIntOrNull()?.takeIf { it in SIZES }
            val lastLevel = last.getOrNull(2)?.toIntOrNull()?.takeIf { it in 1..LEVELS }
            val lastDay = last.getOrNull(1)?.toLongOrNull()?.takeIf { last[0] == "D" && it == today }
            val resume = when {
                lastDay != null -> "Daily · ${dayName(lastDay)}"
                last[0] == "S" && prefs.contains("shared") -> "A friend's dungeon"
                lastSize == null -> null
                last[0] == "E" -> "Endless · $lastSize×$lastSize · ${REASONINGS[reasoning]}"
                last[0] == "C" && lastLevel != null -> "${difficulty(lastSize)} · Level $lastLevel"
                else -> null
            }
            Menu(
                solved = SIZES.sumOf { prefs.solved(it) }, finished = SIZES.count { prefs.solved(it) >= LEVELS },
                tutorialDone = prefs.getBoolean("tutorialDone", false), resume = resume,
                daily = dailyName(today), dailyDone = prefs.contains("dailyDone$today"),
                onResume = {
                    size = lastSize ?: 0
                    level = lastLevel ?: 0
                    screen = when {
                        lastDay != null -> Screen.DAILY
                        last[0] == "S" -> Screen.SHARED
                        lastLevel != null -> Screen.CAMPAIGN
                        else -> Screen.ENDLESS
                    }
                },
            ) { size = 0; level = 0; screen = it }
        }
        Screen.CAMPAIGN -> if (size == 0) {
            Difficulties("Campaign", toMenu, { "$it×$it · ${prefs.solved(it)} / $LEVELS solved" }, { prefs.solved(it) >= LEVELS }) { size = it }
        } else {
            Campaign(prefs, audio, campaign.getValue(size), size, level, { level = it }) { size = 0 }
        }
        Screen.ENDLESS -> if (size == 0) {
            Page("Endless", toMenu, spider = true) {
                Text("Size", color = Dim)
                Planks {
                    for (s in SIZES) PlankButton("$s×$s", selected = s == endlessSize) {
                        endlessSize = s
                        prefs.edit().putInt("endlessSize", s).apply()
                    }
                }
                Text("Reasoning", color = Dim)
                Planks {
                    for ((index, name) in REASONINGS.withIndex()) PlankButton(name, selected = index == reasoning) {
                        reasoning = index
                        prefs.edit().putInt("reasoning", index).apply()
                    }
                }
                Plank("Enter", "$endlessSize×$endlessSize · ${REASONINGS[reasoning]}", matter = Matter.GILDED) { size = endlessSize }
                var entering by rememberSaveable { mutableStateOf(false) }
                Planks {
                    PlankButton("Gallery", matter = Matter.PARCHMENT) { screen = Screen.GALLERY }
                    PlankButton("Enter a code", matter = Matter.PARCHMENT) { entering = true }
                }
                if (entering) EnterCode(prefs, { entering = false }) { screen = Screen.SHARED }
            }
        } else {
            Endless(prefs, audio, size, reasoning) { size = 0 }
        }
        Screen.DAILY -> Daily(prefs, audio, today, toMenu)
        Screen.SHARED -> Shared(prefs, audio) { screen = Screen.ENDLESS }
        Screen.GALLERY -> Gallery(prefs) { screen = Screen.ENDLESS }
        Screen.TUTORIAL -> Lessons(prefs, audio, level, { level = it }, toMenu) { size = 0; level = 0; screen = Screen.CAMPAIGN }
        Screen.OPTIONS -> Options(prefs, audio, toMenu)
    }
}
