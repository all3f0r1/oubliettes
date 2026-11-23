package app.oubliettes

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.oubliettes.game.Puzzle
import java.io.File
import java.io.IOException

internal class PostcardFileProvider : FileProvider()

@Composable
internal fun ChallengePostcard(what: String, hints: Int, puzzle: Puzzle, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val postcard = remember(context, what, puzzle) { drawPostcard(context, what, puzzle) }
    val message = shareText(what, hints, puzzle)
    Notice("Dungeon challenge", onDismiss) {
        Image(
            postcard.asImageBitmap(),
            contentDescription = "$what, ${puzzle.width} by ${puzzle.height}. Preview of the starting clues.",
            modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth(),
        )
        Planks {
            PlankButton("Back", onClick = onDismiss)
            PlankButton("Send code") {
                context.startActivity(Intent.createChooser(codeIntent(message), null))
                onDismiss()
            }
            PlankButton("Send card") {
                val intent = codeIntent(message)
                try {
                    val directory = File(context.cacheDir, "postcards")
                    directory.mkdirs()
                    val cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
                    directory.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
                    val file = File.createTempFile("oubliettes-", ".png", directory)
                    file.outputStream().use { stream ->
                        if (!postcard.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw IOException()
                    }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.postcards", file)
                    intent.setType("image/png")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.clipData = ClipData.newUri(context.contentResolver, "Dungeon challenge", uri)
                } catch (_: IOException) {
                    // The challenge code can still be sent as text if the cache is unavailable.
                }
                context.startActivity(Intent.createChooser(intent, null))
                onDismiss()
            }
        }
    }
}

private fun codeIntent(message: String) = Intent(Intent.ACTION_SEND)
    .setType("text/plain")
    .putExtra(Intent.EXTRA_TEXT, message)

/** The card draws only the clues supplied at the start of the puzzle. */
private fun drawPostcard(context: Context, what: String, puzzle: Puzzle): Bitmap {
    val bitmap = Bitmap.createBitmap(600, 680, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    val gold = 0xffecc365.toInt()
    val parchment = 0xfff3e5c5.toInt()
    canvas.drawColor(0xff201b25.toInt())
    paint.color = gold
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2f
    canvas.drawRoundRect(16f, 16f, 584f, 664f, 16f, 16f, paint)
    paint.style = Paint.Style.FILL
    paint.typeface = context.resources.getFont(R.font.unifraktur_cook)
    paint.textSize = 66f
    canvas.drawText("Oubliettes", 300f, 92f, paint)
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.color = parchment
    paint.textSize = 25f
    val subtitle = "${what.substringBefore(" · ")} challenge · ${puzzle.width}×${puzzle.height}"
    canvas.drawText(subtitle, 300f, 132f, paint)

    val cell = 432f / (maxOf(puzzle.width, puzzle.height) + 1)
    val left = (600f - cell * (puzzle.width + 1)) / 2
    val top = 154f
    val monster = checkNotNull(context.getDrawable(R.drawable.monster_skull_0))
    val chest = checkNotNull(context.getDrawable(R.drawable.chest_0))
    paint.textSize = cell * 0.55f
    for (column in puzzle.colCounts.indices) {
        canvas.drawText(puzzle.colCounts[column].toString(), left + (column + 1.5f) * cell, top + cell * 0.7f, paint)
    }
    for (row in puzzle.rowCounts.indices) {
        paint.color = parchment
        val y = top + (row + 1) * cell
        canvas.drawText(puzzle.rowCounts[row].toString(), left + cell / 2, y + cell * 0.7f, paint)
        for (column in puzzle.colCounts.indices) {
            val x = left + (column + 1) * cell
            val index = row * puzzle.width + column
            paint.color = 0xff37303e.toInt()
            canvas.drawRect(x + 1, y + 1, x + cell - 1, y + cell - 1, paint)
            val clue = when (index) {
                in puzzle.chests -> chest
                in puzzle.monsters -> monster
                else -> continue
            }
            clue.setBounds(x.toInt(), y.toInt(), (x + cell).toInt(), (y + cell).toInt())
            clue.draw(canvas)
        }
    }
    paint.color = gold
    paint.textSize = 23f
    canvas.drawText("Open Endless → Enter a code", 300f, 632f, paint)
    return bitmap
}
