package app.oubliettes

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.oubliettes.game.decodePuzzle
import app.oubliettes.game.findPuzzle
import app.oubliettes.game.generate
import app.oubliettes.game.stuck
import java.time.LocalDate
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The daily dungeons that stay open: today's and those of the days before. A day missed costs nothing. */
internal const val DAYS = 14

// What each day of the week digs: a size, and an index in REASONINGS. Small and sharp, or large and gentle.
private val DAILY = listOf(6 to 2, 8 to 1, 8 to 2, 10 to 0, 10 to 1, 12 to 0, 12 to 1)

/** Days are counted from 1970, as [LocalDate.toEpochDay] does: the count is the seed of the day's dungeon. */
internal fun today() = LocalDate.now().toEpochDay()

internal fun dailyName(day: Long) = DAILY[day.mod(7)].let { (size, reasoning) -> "$size×$size · ${REASONINGS[reasoning]}" }

internal fun dayName(day: Long) = LocalDate.ofEpochDay(day).toString()

/** How many dungeons are kept in the gallery; older ones make room. */
private const val KEPT = 300

/** The pictures of the dungeons solved outside the campaign, the latest last. */
internal fun SharedPreferences.gallery() = getString("gallery", "")!!.split(',').filter { it.isNotEmpty() }

/** Keeps the [pictures] of finished dungeons in the gallery. One already there is not kept twice. */
internal fun SharedPreferences.keep(pictures: Collection<String>) {
    if (pictures.isNotEmpty()) edit().putString("gallery", (gallery() + pictures).distinct().takeLast(KEPT).joinToString(",")).apply()
}

/**
 * The table of the last [DAYS] days, or the dungeon of one of them when [ago] is not 0: 1 for
 * [today], 2 for the day before. A day's dungeon is dug from its date, so it is the same for every
 * player on this version of the game, and it is kept once dug: it never changes on this phone.
 */
@Composable
internal fun Daily(prefs: SharedPreferences, audio: Audio, today: Long, ago: Int, open: (Int) -> Unit, onMenu: () -> Unit) {
    val mapped = prefs.all.keys.count { it.startsWith("dailyDone") }
    if (ago == 0) {
        Page("Daily", onMenu, spider = true) {
            Plank("Today", dailyName(today), matter = Matter.GILDED, done = prefs.contains("dailyDone$today")) { open(1) }
            Text("$mapped daily dungeons mapped", color = Dim)
            Text("Earlier days stay open: none is ever lost.", color = Dim, fontSize = 15.sp, textAlign = TextAlign.Center)
            for (week in (1 until DAYS).chunked(7)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (back in week) {
                        val day = today - back
                        val begun = prefs.getString("marksD$day", "")!!.split(':').getOrNull(1)?.any { it != '0' } == true
                        LevelTile(LocalDate.ofEpochDay(day).dayOfMonth, prefs.getString("dailyDone$day", null), begun, Modifier.weight(1f)) { open(back + 1) }
                    }
                    // The last row is one short: today has its own plank.
                    repeat(7 - week.size) { Row(Modifier.weight(1f)) {} }
                }
            }
        }
        return
    }
    val day = today - (ago - 1)
    val (size, reasoning) = DAILY[day.mod(7)]
    val status = "${dayName(day)} · ${dailyName(day)}"
    key(day) {
        var puzzle by remember { mutableStateOf(prefs.getString("daily$day", null)?.let(::decodePuzzle)) }
        var failed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            prefs.edit().putString("last", "D:$day").apply()
            if (puzzle != null) return@LaunchedEffect
            val dug = withContext(Dispatchers.Default) { generate(size, size, day, reasoning = effortOf(size, reasoning)) { !isActive } }
            if (dug == null) {
                failed = true
            } else {
                prefs.edit().putString("daily$day", dug.encode()).apply()
                puzzle = dug
            }
        }
        val p = puzzle
        if (p == null) {
            Digging("Daily", status, { open(0) }, "Days", failed)
        } else {
            Game(
                prefs, audio, "Daily", { open(0) }, back = "Days",
                puzzle = p, saveKey = "marksD$day", legacyId = "",
                variety = day.toInt(), status = status, solvedStatus = { "${prefs.all.keys.count { it.startsWith("dailyDone") }} daily dungeons mapped" },
                next = "Days", onNext = { open(0) }, share = "Daily · $status",
                onSolved = {
                    prefs.edit().putString("dailyDone$day", it).apply()
                    prefs.keep(listOf(it))
                },
            )
        }
    }
}

/** The dungeon a friend sent, see [EnterCode]: one at a time, kept until another takes its place. */
@Composable
internal fun Shared(prefs: SharedPreferences, audio: Audio, onBack: () -> Unit) {
    val p = remember { prefs.getString("shared", null)?.let(::decodePuzzle) }
    if (p == null) {
        Digging("Shared", "No dungeon here.", onBack, "Endless", failed = true)
        return
    }
    LaunchedEffect(Unit) { prefs.edit().putString("last", "S").apply() }
    val status = "${p.width}×${p.width} · from a friend"
    Game(
        prefs, audio, "Shared", onBack, back = "Endless",
        puzzle = p, saveKey = "marksS", legacyId = "",
        variety = p.encode().hashCode(), status = status, solvedStatus = { status },
        next = "Endless", onNext = onBack, share = "A friend's dungeon · ${p.width}×${p.width}",
        onSolved = { prefs.keep(listOf(it)) },
    )
}

/**
 * Asks for the grid a friend sent: the whole message will do, the grid is found in it. It is taken
 * only if reasoning alone solves it, which is worked out before [onFound].
 */
@Composable
internal fun EnterCode(prefs: SharedPreferences, onDismiss: () -> Unit, onFound: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var said by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Notice("Enter a code", onDismiss) {
        Text("Paste what a friend shared from a solved grid: you get the same dungeon.", color = Ink, textAlign = TextAlign.Center)
        OutlinedTextField(
            // Nothing the game shares is anywhere near that long.
            text, { text = it.take(2_000) }, Modifier.fillMaxWidth().semantics { contentDescription = "Code" },
            maxLines = 4, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
        )
        Text(said, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = Ink, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
        Planks {
            PlankButton("Cancel", onClick = onDismiss)
            PlankButton("Dig", enabled = text.isNotBlank() && !checking) {
                checking = true
                scope.launch {
                    val p = findPuzzle(text)
                    val sound = p != null && withContext(Dispatchers.Default) { stuck(p)?.isEmpty() == true }
                    checking = false
                    if (p != null && sound) {
                        // The marks of the grid sent before go with it.
                        prefs.edit().putString("shared", p.encode()).remove("marksS").apply()
                        onDismiss()
                        onFound()
                    } else {
                        said = if (p == null) "No dungeon in there." else "This one cannot be solved by reasoning alone."
                    }
                }
            }
        }
    }
}

/** Every dungeon kept, see [keep], the latest first. */
@Composable
internal fun Gallery(prefs: SharedPreferences, onBack: () -> Unit) {
    val pictures = remember { prefs.gallery().reversed() }
    Page("Gallery", onBack, back = "Endless", scroll = false, spider = true) {
        Text(
            if (pictures.isEmpty()) "Dungeons solved in Endless, Daily or from a friend are kept here." else "${pictures.size} dungeons mapped",
            color = Dim, textAlign = TextAlign.Center,
        )
        LazyVerticalGrid(
            GridCells.Fixed(4), Modifier.widthIn(max = 480.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(pictures) { picture ->
                val side = sqrt(picture.length.toFloat()).toInt()
                Row(
                    Modifier.aspectRatio(1f).clip(RoundedCornerShape(4.dp)).drawBehind { drawDungeon(picture) }
                        .semantics { contentDescription = "Dungeon, $side by $side" },
                ) {}
            }
        }
    }
}
