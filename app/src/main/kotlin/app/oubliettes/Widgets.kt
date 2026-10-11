package app.oubliettes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Menu entry: a wide sign with a title and maybe a line of [detail]. Its [matter] tells what it is
 * for. [done] strikes a seal next to the title; [seals] are smaller ones under it, one per thing finished.
 */
@Composable
internal fun Plank(
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
internal fun Difficulties(title: String, onMenu: () -> Unit, detail: (Int) -> String, finished: (Int) -> Boolean, pick: (Int) -> Unit) {
    Page(title, onMenu, spider = true) {
        for ((index, size) in SIZES.withIndex()) Plank(DIFFICULTIES[index], detail(size), done = finished(size)) { pick(size) }
    }
}

/**
 * Shared frame of the inner screens: a way back, a title, then the content. It scrolls when the
 * content is taller than the screen, unless the content does its own scrolling ([scroll] false).
 * A page with a [foot] rests it on the bottom of the screen, under the thumb, and centres its
 * content in the room left under the title. [end] sits opposite the way back. A [spider] visits the screens where
 * nothing else moves; never a grid.
 */
@Composable
internal fun Page(
    title: String,
    onBack: () -> Unit,
    back: String = "Menu",
    scroll: Boolean = true,
    spider: Boolean = false,
    end: @Composable () -> Unit = {},
    foot: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) = Box(Modifier.safeDrawingPadding().fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The title between the two planks, or on a line of its own when it does not fit: large fonts.
        Layout(
            content = {
                Text(title, Modifier.semantics { heading() }, color = Gold, fontFamily = Fraktur, fontSize = 30.sp, textAlign = TextAlign.Center)
                PlankButton(back, glyph = BackGlyph, onClick = onBack)
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
        if (foot != null) Spacer(Modifier.weight(1f))
        for (part in listOfNotNull(content, foot)) {
            Column(
                Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = part,
            )
            if (foot != null && part === content) Spacer(Modifier.weight(1f))
        }
    }
    if (spider) Spider()
}

/** A notice nailed over the screen: a blackletter [title], then the content. */
@Composable
internal fun Notice(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
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
internal fun Planks(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}
