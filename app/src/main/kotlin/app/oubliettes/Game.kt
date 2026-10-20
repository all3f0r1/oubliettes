package app.oubliettes

import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.oubliettes.game.Puzzle
import app.oubliettes.game.Step
import app.oubliettes.game.nudge
import app.oubliettes.game.reason
import kotlinx.coroutines.delay

/**
 * Under a grid: what a tap lays. [brush] is [WALL] or [KNOWN_OPEN], or 0 when a tap cycles through
 * the marks; it is kept from one grid to the next.
 */
@Composable
internal fun Brushes(prefs: SharedPreferences, brush: MutableIntState) {
    val wall = painterResource(R.drawable.wall)
    val bricks: Glyph = { with(wall) { draw(size) } }
    for ((value, name, glyph) in listOf(Triple(WALL, "Wall", bricks), Triple(KNOWN_OPEN, "Hallway", HallwayGlyph))) {
        PlankButton(name, selected = brush.intValue == value, glyph = glyph) {
            brush.intValue = if (brush.intValue == value) 0 else value
            prefs.edit().putInt("brush", brush.intValue).apply()
        }
    }
}

/** Over a grid, opposite the way back: the rules, on parchment. */
@Composable
internal fun Rules() {
    var show by rememberSaveable { mutableStateOf(false) }
    PlankButton("?", matter = Matter.PARCHMENT, description = "Rules") { show = true }
    if (show) {
        Notice("Rules", { show = false }) {
            for (rule in RULES) Text(rule, Modifier.fillMaxWidth(), color = Ink, fontSize = 16.sp, lineHeight = 20.sp)
            PlankButton("Close") { show = false }
        }
    }
}

/**
 * The grid written down before a try, and the way back to it: there once a checkpoint is laid, and
 * of use once the grid has changed since. [act] wraps what each plank does.
 */
@Composable
internal fun Checkpoints(session: Session, act: (() -> Unit) -> () -> Unit = { it }) {
    PlankButton("Checkpoint", enabled = session.canCheckpoint, matter = Matter.PARCHMENT, onClick = act(session::checkpoint))
    if (session.hasCheckpoint) PlankButton("Return", enabled = session.canReturn, matter = Matter.PARCHMENT, onClick = act(session::toCheckpoint))
}

/**
 * One grid being played. Marks and their history are saved under [saveKey] after every action;
 * [legacyId] is what a save of 0.4 was tagged with. [onSolved] receives the picture of the finished
 * dungeon, the moment it is solved and again whenever a solved grid is reopened. [onNext] is only
 * offered once solved; [skip], when given, names a plank over the grid that leaves it unsolved for
 * the next one. [status] is shown above the grid, and [solvedStatus] under the announcement once it is solved.
 *
 * With the help turned on in Options, a Check plank crosses the marks that are wrong for three seconds,
 * then rests for a minute.
 *
 * A Hint plank, there unless turned off in Options and always when the grid is a [guide]d one, gives
 * a deduction from the puzzle clues, a word at a time: where to look, then why, then which cells.
 * It never checks for mistakes. It stays until those cells are marked. A non-null [share] enables
 * sharing and names the grid to whoever a solved one is sent to. Only Daily and Endless opt in.
 */
@Composable
internal fun Game(
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
    auto: Boolean = false,
    share: String? = null,
    guide: Boolean = false,
    onSolved: (picture: String) -> Unit,
) {
    val session = remember(puzzle) { Session(puzzle, prefs.getString(saveKey, null), legacyId, prefs.getBoolean("complete", true)) }
    val haptics = remember { prefs.getBoolean("haptics", true) }
    val ruleHint = remember { prefs.getBoolean("ruleHint", true) }
    val help = remember { prefs.getBoolean("help", false) }
    val hints = remember { guide || prefs.getBoolean("hints", true) }
    val brush = remember { mutableIntStateOf(prefs.getInt("brush", 0)) }
    // What the player is asked to confirm, by the name of its plank: [skip] or Reset. Empty for nothing.
    var asking by rememberSaveable(puzzle) { mutableStateOf("") }
    // The marks found wrong by the check, while they are shown: null the rest of the time.
    var wrong by remember(session) { mutableStateOf<Set<Int>?>(null) }
    // The Check plank rests after each use.
    var checked by remember(session) { mutableStateOf(false) }
    LaunchedEffect(checked) {
        if (!checked) return@LaunchedEffect
        delay(3_000)
        wrong = null
        delay(57_000)
        checked = false
    }
    // The hint being given, and how far it went: 1 where to look, 2 why, 3 which cells.
    var hint by remember(session) { mutableStateOf<Step?>(null) }
    var hintLevel by remember(session) { mutableIntStateOf(0) }
    val marks = session.marks
    val toMark = hint?.let { given -> (given.walls.filter { marks[it] != WALL } + given.open.filter { marks[it] != KNOWN_OPEN }).toSet() }.orEmpty()
    // A hint is over once its cells are marked.
    val step = hint?.takeIf { toMark.isNotEmpty() }
    val level = if (hint != null && step == null) 0 else hintLevel
    // The lines over the grid keep the room the longest of them took: the grid moves when a hint comes, never under the finger.
    var room by remember(session) { mutableIntStateOf(0) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

    DisposableEffect(view) {
        view.keepScreenOn = prefs.getBoolean("keepScreenOn", true)
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(session) { if (session.solved) onSolved(session.picture()) }
    // The music a solved grid stopped comes back with the next screen, whichever it is.
    DisposableEffect(session) { onDispose { audio.resume() } }
    // The time to see the dungeon finished, then on to the next one. Not in calm mode, where nothing moves on its own.
    LaunchedEffect(session.solved) {
        if (auto && session.solved && !Comfort.calm && prefs.getBoolean("autoNext", true)) {
            delay(2_500)
            onNext()
        }
    }

    /** Wraps a button's action: a click, and the save. */
    fun act(action: () -> Unit) = {
        action()
        wrong = null
        hint = null
        hintLevel = 0
        audio.play(audio.click)
        prefs.edit().putString(saveKey, session.save()).apply()
    }

    Page(
        title, onBack, back,
        end = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!session.solved) {
                    // The marks of a grid left this way are gone for good: ask first when there are any.
                    if (skip != null) {
                        PlankButton("", glyph = SkipGlyph, description = skip) { if (session.marks.any { it != 0 }) asking = skip else onNext() }
                    }
                    PlankButton("", enabled = session.marks.any { it != 0 }, matter = Matter.IRON, glyph = ResetGlyph, description = "Reset") {
                        asking = "Reset"
                    }
                }
                Rules()
            }
        },
        foot = {
            if (session.solved) {
                Planks {
                    PlankButton("Play again", onClick = act { session.reset(); audio.resume() })
                    PlankButton(next, onClick = onNext)
                    if (share != null) PlankButton("Share", matter = Matter.PARCHMENT) {
                        val text = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText(share, session.hints, puzzle))
                        context.startActivity(Intent.createChooser(text, null))
                    }
                }
            } else {
                Planks {
                    Brushes(prefs, brush)
                    PlankButton("", enabled = session.canUndo, matter = Matter.IRON, glyph = UndoGlyph, description = "Undo", onClick = act(session::undo))
                    PlankButton("", enabled = session.canRedo, matter = Matter.IRON, glyph = RedoGlyph, description = "Redo", onClick = act(session::redo))
                }
                // Apart from the marks and their history: what a try is framed with.
                Planks {
                    Checkpoints(session, ::act)
                    if (hints) {
                        PlankButton(
                            if (level == 0) "Hint" else if (level == 1 && step != null) "Why?" else "Show me",
                            enabled = level == 0 || (step != null && level < 3), matter = Matter.PARCHMENT,
                        ) {
                            if (level == 0) {
                                hint = session.hint()
                                hintLevel = 1
                                prefs.edit().putString(saveKey, session.save()).apply()
                            } else {
                                hintLevel = level + 1
                            }
                        }
                    }
                    if (help) {
                        PlankButton("Check", enabled = !checked && session.marks.any { it != 0 }, matter = Matter.IRON) {
                            wrong = session.mistakes()
                            checked = true
                        }
                    }
                }
            }
        },
    ) {
        // One node, so that screen readers announce what it turns into.
        Column(
            Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .onSizeChanged { room = maxOf(room, it.height) }.heightIn(min = with(LocalDensity.current) { room.toDp() }),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (session.solved) {
                Text("Dungeon solved!", color = Gold, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(solvedStatus(), color = Ink)
            } else {
                // What the check found takes the place of the status while it shows: the grid does not move.
                val found = wrong?.let { if (it.isEmpty()) "No mistake so far." else if (it.size == 1) "1 mistake, crossed out." else "${it.size} mistakes, crossed out." }
                val said = found ?: when {
                    level == 0 -> null
                    step == null -> "No further deduction is available. Try a checkpoint and explore a possibility."
                    level == 1 -> step.nudge(puzzle)
                    else -> step.reason(puzzle)
                }
                if (said != null) Text(said, color = Ink, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center) else Text(status, color = Dim)
                if (ruleHint && session.ruleBroken) {
                    Text("Every count is met, yet a rule is broken.", color = Ink, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
                }
            }
        }
        Board(
            session, locked = session.solved, celebrate = session.solved, variety = variety, wrong = wrong.orEmpty(), brush = brush.intValue,
            focus = when (level) {
                2 -> step?.from.orEmpty()
                3 -> toMark.toList()
                else -> emptyList()
            },
            toMark = if (level == 3) toMark else emptySet(),
            onCount = {
                Comfort.plainDigits = !Comfort.plainDigits
                prefs.edit().putBoolean("plainDigits", Comfort.plainDigits).apply()
            },
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

/** What a solved grid is sent as: [what] it was, the [hints] it took, and the grid itself for whoever wants to dig the same. */
internal fun shareText(what: String, hints: Int, puzzle: Puzzle) = listOf(
    "Oubliettes · $what",
    when (hints) {
        0 -> "Solved without a hint."
        1 -> "Solved with 1 hint."
        else -> "Solved with $hints hints."
    },
    "The same dungeon, in Endless › Enter a code:",
    puzzle.encode(),
).joinToString("\n")
