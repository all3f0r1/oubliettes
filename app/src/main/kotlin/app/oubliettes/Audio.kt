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
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.math.roundToInt

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
 * Decoded loops stay in [cache]: starting over after a silence decodes nothing.
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
 * Sound effects plus background music: one bundled loop, the same on every screen.
 * The music holds the audio focus while it plays. It stays silent when another app is already
 * playing, stops when another app takes the focus, and comes back when the focus does.
 */
class Audio(private val context: Context, private val prefs: SharedPreferences) {
    var soundVolume by mutableFloatStateOf(prefs.getFloat("soundVolume", 0.7f))
    var musicVolume by mutableFloatStateOf(prefs.getFloat("musicVolume", 0.5f))

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attributes).build()
    val click = pool.load(context, R.raw.sfx_click, 1)
    private val victory = pool.load(context, R.raw.sfx_victory, 1)
    private val roars = listOf(
        R.raw.sfx_roar_0, R.raw.sfx_roar_1, R.raw.sfx_roar_2, R.raw.sfx_roar_3, R.raw.sfx_roar_4,
        R.raw.sfx_roar_5, R.raw.sfx_roar_6, R.raw.sfx_roar_7, R.raw.sfx_roar_8, R.raw.sfx_roar_9,
    ).map { pool.load(context, it, 1) }

    private var loop: Loop? = null // a bundled loop
    private val decoded = ConcurrentHashMap<Int, Pcm>()
    private var foreground = false
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

    /** Plays the victory jingle and the cry of a monster, another one from grid to grid. The music stops until [resume]. */
    fun victory() {
        play(victory)
        play(roars.random())
        hushed = true
        sync()
    }

    /** The music comes back after [victory]: call when the solved grid leaves the screen. */
    fun resume() {
        hushed = false
        sync()
    }

    /** Persists the settings and applies them to the music. Call after changing any of them. */
    fun save() {
        prefs.edit().putFloat("soundVolume", soundVolume).putFloat("musicVolume", musicVolume).apply()
        sync()
    }

    /** Music only plays while the app is on screen. */
    fun setForeground(value: Boolean) {
        foreground = value
        sync()
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        manager.abandonAudioFocusRequest(focusRequest)
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
        val playing = wanted && focused && !paused && !hushed
        if (playing != (loop != null)) {
            loop?.stop()
            loop = if (playing) Loop(context, R.raw.music, attributes, decoded) else null
        }
        loop?.volume = musicVolume * musicVolume
    }
}
