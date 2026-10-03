package app.oubliettes

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.math.roundToInt

private const val RADIO_URL = "https://mediaserv73.live-streams.nl:18058/stream" // Ancient FM, ancientfm.com

private enum class Source { NONE, MENU, LOOP, RADIO }

/** A decoded sound: [size] bytes of 16-bit samples. */
private class Pcm(val bytes: ByteArray, val size: Int, val rate: Int, val channels: Int)

/** Decodes a whole raw resource. Null if the device cannot. */
private fun decode(context: Context, resource: Int): Pcm? = runCatching {
    val extractor = MediaExtractor()
    context.resources.openRawResourceFd(resource).use { extractor.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
    extractor.selectTrack(0)
    val format = extractor.getTrackFormat(0)
    val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    // What the file says it holds. A decoder giving a few samples more would put a click in the loop.
    val expected = (format.getLong(MediaFormat.KEY_DURATION) * rate / 1e6).roundToInt() * channels * 2
    val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
    try {
        codec.configure(format, null, null, 0)
        codec.start()
        var bytes = ByteArray(expected + 16384)
        var size = 0
        val info = MediaCodec.BufferInfo()
        var fed = false
        while (true) {
            if (!fed) {
                val input = codec.dequeueInputBuffer(10_000)
                if (input >= 0) {
                    val read = extractor.readSampleData(codec.getInputBuffer(input)!!, 0)
                    if (read < 0) {
                        codec.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        fed = true
                    } else {
                        codec.queueInputBuffer(input, 0, read, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val output = codec.dequeueOutputBuffer(info, 10_000)
            if (output < 0) continue
            if (size + info.size > bytes.size) bytes = bytes.copyOf(maxOf(bytes.size * 2, size + info.size))
            codec.getOutputBuffer(output)!!.apply { position(info.offset) }.get(bytes, size, info.size)
            size += info.size
            codec.releaseOutputBuffer(output, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
        }
        Pcm(bytes, minOf(size, expected), rate, channels)
    } finally {
        codec.release()
        extractor.release()
    }
}.getOrNull()

/**
 * A bundled loop played without a seam: decoded whole, then written round and round to an audio
 * track by a thread of its own. MediaPlayer's looping leaves a gap every time it starts over.
 * Decoded loops stay in [cache]: going back and forth between the menu and a grid decodes nothing.
 */
private class Loop(context: Context, resource: Int, attributes: AudioAttributes, cache: ConcurrentHashMap<Int, Pcm>) {
    @Volatile private var track: AudioTrack? = null
    @Volatile private var stopped = false
    @Volatile var volume = 0f
        set(value) {
            field = value
            track?.setVolume(value)
        }

    init {
        thread(name = "music") {
            val pcm = cache[resource] ?: decode(context, resource)?.also { cache[resource] = it } ?: return@thread
            if (stopped) return@thread
            val mask = if (pcm.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
            val buffer = AudioTrack.getMinBufferSize(pcm.rate, mask, AudioFormat.ENCODING_PCM_16BIT) * 2
            val out = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(pcm.rate).setChannelMask(mask).build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(buffer)
                .build()
            track = out
            out.setVolume(volume)
            out.play()
            var at = 0
            while (!stopped) {
                // Never more than half the buffer: once stopped and flushed, the last write cannot block.
                val written = out.write(pcm.bytes, at, minOf(buffer / 2, pcm.size - at))
                if (written < 0) break
                at = (at + written) % pcm.size
            }
            out.release()
        }
    }

    /** Silent at once; the thread then lets go of the track. */
    fun stop() {
        stopped = true
        track?.runCatching {
            pause()
            flush()
        }
    }
}

/**
 * Sound effects plus background music: the bundled loops (a calmer one for the menus), or the Ancient FM stream when asked.
 * The music holds the audio focus while it plays. It stays silent when another app is already
 * playing, stops when another app takes the focus, and comes back when the focus does.
 */
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

    private var player: MediaPlayer? = null // the radio
    private var loop: Loop? = null // a bundled loop
    private val decoded = ConcurrentHashMap<Int, Pcm>()
    private var source = Source.NONE
    private var foreground = false
    private var menu = true
    private var hushed = false
    private val handler = Handler(Looper.getMainLooper())

    private val manager = context.getSystemService(AudioManager::class.java)
    private var focused = false // the music may play
    private var paused = false // focus lent for a moment (a call, a notification): it comes back by itself
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener({ change ->
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> paused = false
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> paused = true
                AudioManager.AUDIOFOCUS_LOSS -> {
                    focused = false
                    paused = false
                }
            }
            // Not the moment to ask for the focus again: it was just taken.
            sync(askFocus = false)
        }, handler)
        .build()

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
        handler.removeCallbacksAndMessages(null)
        manager.abandonAudioFocusRequest(focusRequest)
        player?.release()
        loop?.stop()
        pool.release()
    }

    private fun sync(askFocus: Boolean = true) {
        val wanted = foreground && musicVolume > 0f
        if (!wanted && focused) {
            manager.abandonAudioFocusRequest(focusRequest)
            focused = false
            paused = false
        } else if (wanted && !focused && askFocus && !manager.isMusicActive) {
            // Another app playing keeps the floor: asking for the focus would stop it.
            focused = manager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        val want = when {
            !wanted || !focused || paused -> Source.NONE
            radio && !radioFailed -> Source.RADIO
            menu -> Source.MENU
            else -> Source.LOOP
        }
        if (want != source) {
            player?.release()
            loop?.stop()
            source = want
            loop = when (want) {
                Source.MENU -> Loop(context, R.raw.menu, attributes, decoded)
                Source.LOOP -> Loop(context, R.raw.music, attributes, decoded)
                else -> null
            }
            player = when (want) {
                Source.NONE, Source.MENU, Source.LOOP -> null
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
        loop?.volume = v
    }

    private fun radioLost() {
        radioFailed = true
        sync()
    }
}
