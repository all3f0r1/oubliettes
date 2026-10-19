package app.oubliettes

import android.content.SharedPreferences
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.oubliettes.game.Puzzle
import app.oubliettes.game.TutorialStep
import app.oubliettes.game.lessons
import app.oubliettes.game.tutorialPuzzle
import app.oubliettes.game.tutorialSteps

internal fun Session.show(step: TutorialStep) {
    paint(step.walls, WALL)
    paint(step.open, KNOWN_OPEN)
}

/**
 * The table of lessons, or the one being taken when [lesson] is not 0: the basics first, then a
 * technique each, on a guided grid and then on one to practise alone. A technique is learnt once
 * that second grid is solved. [onPlay] is where the basics lead.
 */
@Composable
internal fun Lessons(prefs: SharedPreferences, audio: Audio, lesson: Int, open: (Int) -> Unit, onMenu: () -> Unit, onPlay: () -> Unit) {
    val toTable = { open(0) }
    if (lesson == 0) {
        Page("Tutorial", onMenu, spider = true) {
            Plank("The basics", "Counts, monsters, checkpoints", matter = Matter.PARCHMENT, done = prefs.getBoolean("tutorialDone", false)) { open(1) }
            for ((index, technique) in lessons.withIndex()) {
                Plank(technique.title, technique.about, done = prefs.getBoolean("lesson$index", false)) { open(index + 2) }
            }
        }
        return
    }
    if (lesson == 1) {
        Guided(prefs, "The basics", tutorialPuzzle, tutorialSteps, toTable, "Play", onPlay) { prefs.edit().putBoolean("tutorialDone", true).apply() }
        return
    }
    val index = lesson - 2
    val technique = lessons[index]
    var practising by rememberSaveable(lesson) { mutableStateOf(false) }
    if (!practising) {
        Guided(prefs, technique.title, technique.guided, technique.steps, toTable, "Practise", { practising = true })
    } else {
        Game(
            prefs, audio, technique.title, toTable, back = "Lessons",
            puzzle = technique.practise, saveKey = "marksL$index", legacyId = "",
            variety = index, status = "On your own", solvedStatus = { "Lesson learnt" },
            next = "Lessons", onNext = toTable, guide = true,
            onSolved = { prefs.edit().putBoolean("lesson$index", true).apply() },
        )
    }
}

/**
 * A fixed solve of [puzzle], one deduction at a time: the player marks the cells of each of the
 * [steps], or asks to be shown. The plank of the last step is named [end] and leads [onEnd];
 * [onLast] is told when that step is reached.
 */
@Composable
private fun Guided(
    prefs: SharedPreferences, title: String, puzzle: Puzzle, steps: List<TutorialStep>,
    onBack: () -> Unit, end: String, onEnd: () -> Unit, onLast: () -> Unit = {},
) {
    var index by rememberSaveable(puzzle) { mutableIntStateOf(0) }
    // Back after the process was killed, the steps already passed are marked again.
    val session = remember(puzzle) { Session(puzzle).also { s -> steps.take(index).forEach { s.show(it) } } }
    val brush = remember { mutableIntStateOf(prefs.getInt("brush", 0)) }
    val step = steps[index]
    val marks = session.marks
    val toMark = (step.walls.filter { marks[it] != WALL } + step.open.filter { marks[it] != KNOWN_OPEN }).toSet()
    val last = index == steps.lastIndex
    LaunchedEffect(last) { if (last) onLast() }

    Page(
        title, onBack, back = "Lessons", end = { Rules() },
        foot = {
            Text(step.text, Modifier.fillMaxWidth().heightIn(min = 130.dp), color = Ink, fontSize = 18.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
            Text(
                if (toMark.isNotEmpty()) "Your turn: ${toMark.size} to mark" else "",
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = Gold, fontStyle = FontStyle.Italic,
            )
            // The step about checkpoints shows them in place of the brushes: a tap still builds its wall.
            Planks { if (step.checkpoints) Checkpoints(session) else Brushes(prefs, brush) }
            Planks {
                PlankButton("Previous", enabled = index > 0) { index-- }
                PlankButton("Show me", enabled = toMark.isNotEmpty()) { session.show(step) }
                PlankButton(if (last) end else "Next", enabled = toMark.isEmpty()) { if (last) onEnd() else index++ }
            }
        },
    ) {
        Text("Step ${index + 1} / ${steps.size}", color = Dim)
        Board(session, locked = last, celebrate = last && session.solved, focus = step.focus, toMark = toMark, brush = brush.intValue)
    }
}
