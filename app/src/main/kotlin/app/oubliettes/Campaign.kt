package app.oubliettes

import android.content.SharedPreferences
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.oubliettes.game.Puzzle
import kotlin.math.sqrt

/** The table of levels of the difficulty [size], or the level being played when [level] is not 0. */
@Composable
internal fun Campaign(prefs: SharedPreferences, audio: Audio, levels: List<Puzzle>, size: Int, level: Int, open: (Int) -> Unit, onBack: () -> Unit) {
    if (level != 0) {
        LaunchedEffect(size, level) { prefs.edit().putString("last", "C:$size:$level").apply() }
        Game(
            prefs, audio, "Campaign", onBack = { open(0) }, back = "Levels",
            puzzle = levels[level - 1], saveKey = "marks${size}_$level", legacyId = "$level",
            variety = level, status = "${difficulty(size)} · Level $level",
            share = "Campaign · ${difficulty(size)} · ${CHAPTERS[SIZES.indexOf(size)][(level - 1) / 10]} · Level $level",
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
            for ((chapter, name) in CHAPTERS[SIZES.indexOf(size)].withIndex()) {
                val mapped = (1..10).count { prefs.contains("done${size}_${chapter * 10 + it}") }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        Modifier.padding(top = 6.dp).semantics(mergeDescendants = true) { heading() },
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, color = Gold, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Text("$mapped / 10 mapped", color = Dim, fontSize = 15.sp)
                        if (mapped == 10) Seal(18.dp)
                    }
                }
                items(10) { k ->
                    val index = chapter * 10 + k
                    val done = prefs.getString("done${size}_${index + 1}", null)?.takeIf { it.length == size * size }
                    // A save holds its marks after the first colon: begun once any cell is marked.
                    val begun = prefs.getString("marks${size}_${index + 1}", "")!!.split(':').getOrNull(1)?.any { it != '0' } == true
                    LevelTile(index + 1, done, begun) { open(index + 1) }
                }
            }
        }
    }
}

/** The [picture] of a finished dungeon, as [app.oubliettes.game.drawing] gives it, over the whole of what is drawn. */
internal fun DrawScope.drawDungeon(picture: String) {
    val side = sqrt(picture.length.toFloat()).toInt()
    val cell = size.width / side
    for (i in picture.indices) {
        val color = when (picture[i]) {
            '#' -> Color(0xFFA29EBA)
            'M' -> Red
            'C' -> Gold
            else -> OpenFloor
        }
        // One pixel wider than the cell: no hairline gaps between neighbours.
        drawRect(color, Offset(i % side * cell, i / side * cell), Size(cell + 1, cell + 1))
    }
}

/**
 * A stone slab bearing a number: a level, or a day of the month. One begun is rimmed in gold and
 * says so; a solved one shows a small picture of the finished dungeon.
 */
@Composable
internal fun LevelTile(number: Int, done: String?, begun: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val stone = painterResource(R.drawable.wall)
    val shape = RoundedCornerShape(4.dp)
    Column(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { stateDescription = if (done != null) "solved" else if (begun) "begun" else "not started" }
            .drawBehind {
                if (done == null) with(stone) { draw(size, colorFilter = StoneShade) } else drawDungeon(done)
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
