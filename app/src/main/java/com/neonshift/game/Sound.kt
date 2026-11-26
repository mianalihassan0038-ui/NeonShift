package com.neonshift.game

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.neonshift.game.core.SaveData
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

enum class Sfx { MOVE, BLOCKED, WIN, FAIL, CLICK, UNDO }

/**
 * Sound effects and a soft ambient music loop, all synthesised in code (no audio files in the app).
 * The sounds are written once as WAV files in the cache folder and played with SoundPool / MediaPlayer.
 */
class Sound(private val context: Context, private val save: SaveData) {
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val pool = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build()
    private val ids = IntArray(Sfx.entries.size)
    private var music: MediaPlayer? = null
    @Volatile private var ready = false
    private var paused = false

    init {
        thread(name = "neon-audio") {
            try {
                val dir = File(context.cacheDir, "sfx-v1").apply { mkdirs() }
                fun file(name: String, make: () -> FloatArray): String {
                    val f = File(dir, "$name.wav")
                    if (!f.exists() || f.length() < 100) writeWav(f, make())
                    return f.absolutePath
                }
                ids[Sfx.MOVE.ordinal] = pool.load(file("move") { tone(0.07f, 660f, 880f, 0.35f, false) }, 1)
                ids[Sfx.BLOCKED.ordinal] = pool.load(file("blocked") { tone(0.12f, 140f, 110f, 0.4f, true) }, 1)
                ids[Sfx.CLICK.ordinal] = pool.load(file("click") { tone(0.05f, 1200f, 1000f, 0.25f, false) }, 1)
                ids[Sfx.UNDO.ordinal] = pool.load(file("undo") { tone(0.08f, 700f, 450f, 0.3f, false) }, 1)
                ids[Sfx.WIN.ordinal] = pool.load(file("win") { arpeggio(floatArrayOf(523.25f, 659.25f, 783.99f, 1046.5f), 0.09f, 0.35f) }, 1)
                ids[Sfx.FAIL.ordinal] = pool.load(file("fail") { arpeggio(floatArrayOf(392f, 311.13f, 246.94f), 0.12f, 0.3f) }, 1)
                val musicPath = file("ambient") { ambient() }
                val mp = MediaPlayer()
                mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                mp.setDataSource(musicPath)
                mp.isLooping = true
                mp.prepare()
                synchronized(this) { music = mp }
                ready = true
                applySettings()
            } catch (e: Exception) {
                android.util.Log.w("NeonShift", "Audio setup failed", e)
            }
        }
    }

    fun play(s: Sfx) {
        if (!ready || !save.sound) return
        val v = save.soundVolume
        if (v > 0.01f) pool.play(ids[s.ordinal], v, v, 1, 0, 1f)
    }

    @Synchronized
    fun applySettings() {
        val mp = music ?: return
        val v = 0.3f * save.musicVolume
        mp.setVolume(v, v)
        val want = save.music && save.musicVolume > 0.01f && !paused
        if (want && !mp.isPlaying) mp.start() else if (!want && mp.isPlaying) mp.pause()
    }

    fun onPause() { paused = true; applySettings() }
    fun onResume() { paused = false; applySettings() }

    fun release() {
        synchronized(this) { music?.release(); music = null }
        pool.release()
    }

    companion object {
        const val RATE = 22050

        fun tone(seconds: Float, f0: Float, f1: Float, volume: Float, square: Boolean): FloatArray {
            val n = (seconds * RATE).toInt() + 1
            val data = FloatArray(n)
            var phase = 0.0
            for (i in 0 until n) {
                val t = i.toFloat() / n
                val f = f0 + (f1 - f0) * t
                phase += 2.0 * PI * f / RATE
                var s = sin(phase).toFloat()
                if (square) s = sign(s) * 0.5f
                val env = min(1f, i / (RATE * 0.004f)) * (1f - t).pow(2)
                data[i] = s * env * volume
            }
            return data
        }

        fun arpeggio(notes: FloatArray, noteSeconds: Float, volume: Float): FloatArray {
            val per = (noteSeconds * RATE).toInt()
            val tail = RATE / 4
            val n = per * notes.size + tail
            val data = FloatArray(n)
            for (k in notes.indices) {
                val start = k * per
                val len = per + tail
                for (i in 0 until len) {
                    if (start + i >= n) break
                    val t = i.toFloat() / len
                    val env = min(1f, i / (RATE * 0.005f)) * exp(-5f * t)
                    val s = sin(2 * PI * notes[k] * i / RATE) + 0.3 * sin(4 * PI * notes[k] * i / RATE)
                    data[start + i] += (s * env * volume * 0.6f).toFloat()
                }
            }
            return data
        }

        /** 16-second loop of slow, soft chords (A minor, F, C, G). */
        fun ambient(): FloatArray {
            val chords = arrayOf(floatArrayOf(220f, 261.63f, 329.63f), floatArrayOf(174.61f, 220f, 261.63f), floatArrayOf(130.81f, 196f, 261.63f), floatArrayOf(196f, 246.94f, 293.66f))
            val per = 4 * RATE
            val data = FloatArray(per * chords.size)
            for (c in chords.indices) for (i in 0 until per) {
                val env = sin(i.toDouble() / per * PI)
                val time = (c * per + i).toDouble() / RATE
                var s = 0.0
                for (f in chords[c]) s += sin(2 * PI * f * time) + 0.15 * sin(2 * PI * f * 2.003 * time)
                data[c * per + i] = (s / chords[c].size * env * 0.35).toFloat()
            }
            return data
        }

        fun writeWav(file: File, samples: FloatArray) {
            val dataBytes = samples.size * 2
            val b = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
            b.put("RIFF".toByteArray()).putInt(36 + dataBytes).put("WAVE".toByteArray())
            b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
            b.put("data".toByteArray()).putInt(dataBytes)
            for (s in samples) b.putShort((s.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            val tmp = File(file.path + ".tmp")
            FileOutputStream(tmp).use { it.write(b.array()) }
            tmp.renameTo(file)
        }
    }
}

/** Short vibration pulses. Respects the Vibration setting. */
class Haptics(context: Context, private val save: SaveData) {
    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= 31) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
    } catch (e: Exception) { null }

    fun pulse(ms: Long) {
        if (!save.vibration) return
        try {
            val v = vibrator ?: return
            if (v.hasVibrator()) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) { }
    }
}
