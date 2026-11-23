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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.oubliettes.game.Puzzle
import kotlin.math.sqrt

// These Easy grids all have a single-exit deduction in the solver's trace.
private val TreasureTrail = linkedMapOf(
    6 to "Every treasure room has exactly one exit. Look for the wall counts that leave it open.",
    11 to "Find the room first, then use its wall counts to work out which side can open.",
    20 to "Trace the narrow hallways to see which entrance can connect the room to the dungeon.",
)
private const val TREASURE_TRAIL_SIZE = 8

/** The table of levels of the difficulty [size], or the level being played when [level] is not 0. */
@Composable
internal fun Campaign(prefs: SharedPreferences, audio: Audio, levels: List<Puzzle>, size: Int, level: Int, open: (Int) -> Unit, onBack: () -> Unit) {
    var followingTrail by rememberSaveable(size) { mutableStateOf(false) }
    val trail = TreasureTrail.keys.toList()
    val stage = if (size == TREASURE_TRAIL_SIZE && followingTrail) trail.indexOf(level) else -1
    fun mapped(candidate: Int) = prefs.contains("done${size}_$candidate")
    if (level != 0) {
        val following = if (stage >= 0) trail.getOrNull(stage + 1) ?: 0 else nextCampaignLevel(level, levels.size, ::mapped)
        LaunchedEffect(size, level) { prefs.edit().putString("last", "C:$size:$level").apply() }
        Game(
            prefs, audio, "Campaign", onBack = { open(0) }, back = "Levels",
            puzzle = levels[level - 1], saveKey = "marks${size}_$level", legacyId = "$level",
            variety = level, status = "${difficulty(size)} · Level $level",
            objective = TreasureTrail[level].takeIf { stage >= 0 },
            solvedStatus = {
                if (stage >= 0) {
                    if (trail.all(::mapped)) "Treasure trail complete" else "Treasure trail · ${trail.count(::mapped)} of ${trail.size} mapped"
                } else {
                    "${difficulty(size)} · ${prefs.solved(size)} / $LEVELS solved"
                }
            },
            next = when {
                following == 0 -> "Levels"
                stage >= 0 -> "Next in trail"
                else -> "Next level"
            },
            onNext = { open(following) },
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
            if (size == TREASURE_TRAIL_SIZE) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("The treasure trail", Modifier.semantics { heading() }, color = Gold, fontFamily = Fraktur, fontSize = 22.sp)
                        Text("Three dungeons to master the single exit of a treasure room.", color = Ink, textAlign = TextAlign.Center)
                        Planks {
                            for ((index, candidate) in trail.withIndex()) {
                                val label = listOf("Find it", "Follow it", "Connect it")[index]
                                PlankButton(
                                    if (mapped(candidate)) "$label ✓" else label,
                                    matter = Matter.PARCHMENT,
                                    description = "$label, level $candidate" + if (mapped(candidate)) ", mapped" else "",
                                ) {
                                    followingTrail = true
                                    open(candidate)
                                }
                            }
                        }
                    }
                }
            }
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
                    LevelTile(index + 1, done, begun) {
                        followingTrail = false
                        open(index + 1)
                    }
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
