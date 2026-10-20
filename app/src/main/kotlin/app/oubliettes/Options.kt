package app.oubliettes

import android.app.Activity
import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
internal fun Options(prefs: SharedPreferences, audio: Audio, onMenu: () -> Unit) {
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
            Spacer(Modifier.height(8.dp))
            PrefToggle(prefs, "haptics", "Vibrate on every mark")
            PrefToggle(prefs, "keepScreenOn", "Keep the screen on while playing")
            PrefToggle(prefs, "complete", "Finish the marked line only: a drag's row or column, both independently for a tap")
            PrefToggle(prefs, "ruleHint", "Say when every count is met but a rule is broken")
            PrefToggle(prefs, "hints", "Hints: a Hint plank under the grid says where to look, then why, then which cells")
            PrefToggle(prefs, "autoNext", "Endless: on to the next grid by itself once one is solved")
            PrefToggle(prefs, "help", "Help: a Check plank under the grid shows the wrong marks for three seconds, once a minute", default = false)
            PrefToggle(prefs, "magnifier", "Magnifier under the finger on large grids") { Comfort.magnifier = it }
            PrefToggle(prefs, "plainDigits", "Plain digits for the wall counts (or tap a count on the grid)", Comfort.plainDigits) { Comfort.plainDigits = it }
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
