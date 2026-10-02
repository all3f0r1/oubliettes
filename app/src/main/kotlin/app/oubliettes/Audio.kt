package app.oubliettes

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private const val RADIO_URL = "https://mediaserv73.live-streams.nl:18058/stream" // Ancient FM, ancientfm.com

private enum class Source { NONE, MENU, LOOP, RADIO }

/** Sound effects plus background music: the bundled loops (a calmer one for the menus), or the Ancient FM stream when asked. */
class Audio(private val context: Context, private val prefs: SharedPreferences) {
    var soundVolume by mutableFloatStateOf(prefs.getFloat("soundVolume", 0.7f))
    var musicVolume by mutableFloatStateOf(prefs.getFloat("musicVolume", 0.5f))
    var radio by mutableStateOf(prefs.getBoolean("radio", false))
    var radioFailed by mutableStateOf(false)
        private set

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attributes).build()
    val click = pool.load(context, R.raw.sfx_click, 1)
    private val victory = pool.load(context, R.raw.sfx_victory, 1)

    private var player: MediaPlayer? = null
    private var source = Source.NONE
    private var foreground = false
    private var menu = true
    private var hushed = false
    private val handler = Handler(Looper.getMainLooper())

    fun play(sound: Int) {
        val v = soundVolume * soundVolume // squared: closer to perceived loudness
        if (v > 0) pool.play(sound, v, v, 1, 0, 1f)
    }

    /** Plays the victory jingle, with the music silenced while it lasts. */
    fun victory() {
        play(victory)
        hushed = true
        sync()
        handler.removeCallbacks(unhush)
        handler.postDelayed(unhush, 3500)
    }

    private val unhush = Runnable {
        hushed = false
        sync()
    }

    /** Persists the settings and applies them to the music. Call after changing any of them. */
    fun save(retryRadio: Boolean = false) {
        prefs.edit().putFloat("soundVolume", soundVolume).putFloat("musicVolume", musicVolume)
            .putBoolean("radio", radio).apply()
        if (retryRadio) radioFailed = false
        sync()
    }

    /** Music only plays while the app is on screen. */
    fun setForeground(value: Boolean) {
        foreground = value
        if (value) radioFailed = false // the network may be back
        sync()
    }

    /** Menus get their own shorter, calmer loop. */
    fun setMenu(value: Boolean) {
        menu = value
        sync()
    }

    fun release() {
        player?.release()
        pool.release()
    }

    private fun sync() {
        val want = when {
            !foreground || musicVolume == 0f -> Source.NONE
            radio && !radioFailed -> Source.RADIO
            menu -> Source.MENU
            else -> Source.LOOP
        }
        if (want != source) {
            player?.release()
            source = want
            player = when (want) {
                Source.NONE -> null
                Source.MENU, Source.LOOP -> MediaPlayer.create(context, if (want == Source.MENU) R.raw.menu else R.raw.music, attributes, 0)?.apply {
                    isLooping = true
                    start()
                }
                Source.RADIO -> MediaPlayer().apply {
                    setAudioAttributes(attributes)
                    setDataSource(RADIO_URL)
                    var connected = false
                    setOnPreparedListener { connected = true; it.start() }
                    // A live stream only ends or errors when the connection is lost: fall back to the loop.
                    setOnCompletionListener { radioLost() }
                    setOnErrorListener { _, _, _ -> radioLost(); true }
                    prepareAsync()
                    // Offline, the connection attempt can hang for minutes: give up well before that.
                    handler.postDelayed({ if (player === this && !connected) radioLost() }, 15_000)
                }
            }
        }
        val v = if (hushed) 0f else musicVolume * musicVolume
        player?.setVolume(v, v)
    }

    private fun radioLost() {
        radioFailed = true
        sync()
    }
}
