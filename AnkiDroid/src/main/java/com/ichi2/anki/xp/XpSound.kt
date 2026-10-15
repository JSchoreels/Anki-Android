// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.xp

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.VibrationEffect
import android.os.Vibrator
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * kuma3: the Anki XP skin's sounds, synthesized like its Web Audio ones (no sound files): 8-bit blips per button
 * (a semitone higher per combo hit), arpeggio chimes for wins, the falling saw when a combo breaks, the startup
 * chord. And its vibration patterns.
 */
object XpSound {
    private const val RATE = 22050
    private val player = Executors.newSingleThreadExecutor()

    enum class Wave { SQUARE, TRIANGLE, SINE, SAW }

    /** 8-bit blips per button: Again a low bonk, Hard one note, Good two up, Easy a quick run */
    private val BLIPS = mapOf(1 to listOf(196.0, 131.0), 2 to listOf(440.0), 3 to listOf(523.0, 784.0), 4 to listOf(659.0, 880.0, 1319.0))

    /** on, off, on… ms: a tap for each button, heavier for Again; a double knock for a break */
    private val BUZZ =
        mapOf(
            "1" to longArrayOf(45),
            "2" to longArrayOf(15),
            "3" to longArrayOf(12),
            "4" to longArrayOf(10, 40, 10),
            "break" to longArrayOf(70, 60, 110),
            "milestone" to longArrayOf(25, 40, 25, 40, 60),
        )

    private fun wave(
        w: Wave,
        phase: Double,
    ): Double {
        val p = phase - Math.floor(phase)
        return when (w) {
            Wave.SQUARE -> if (p < 0.5) 1.0 else -1.0
            Wave.TRIANGLE -> 4 * kotlin.math.abs(p - 0.5) - 1
            Wave.SINE -> sin(2 * PI * p)
            Wave.SAW -> 2 * p - 1
        }
    }

    /** notes starting [step] s apart, each [len] s with an exponential fade (like the skin's gain ramps) */
    private fun notes(
        freqs: List<Double>,
        w: Wave,
        vol: Double,
        step: Double,
        len: Double,
    ) {
        if (!XpGame.sound || freqs.isEmpty()) return
        val total = ((step * (freqs.size - 1) + len) * RATE).toInt()
        val mix = DoubleArray(total)
        freqs.forEachIndexed { i, f ->
            val start = (i * step * RATE).toInt()
            val n = (len * RATE).toInt()
            for (s in 0 until n) {
                if (start + s >= total) break
                val t = s.toDouble() / RATE
                mix[start + s] += wave(w, f * t) * vol * exp(-t * 6.9 / len)
            }
        }
        play(mix)
    }

    private fun play(mix: DoubleArray) {
        val pcm = ShortArray(mix.size) { (mix[it].coerceIn(-1.0, 1.0) * 12000).toInt().toShort() }
        player.execute {
            runCatching {
                val track =
                    AudioTrack
                        .Builder()
                        .setAudioAttributes(
                            AudioAttributes
                                .Builder()
                                .setUsage(
                                    AudioAttributes.USAGE_GAME,
                                ).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build(),
                        ).setAudioFormat(
                            AudioFormat
                                .Builder()
                                .setSampleRate(
                                    RATE,
                                ).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build(),
                        ).setTransferMode(AudioTrack.MODE_STATIC)
                        .setBufferSizeInBytes(pcm.size * 2)
                        .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(pcm.size * 1000L / RATE + 50)
                track.release()
            }
        }
    }

    fun blip(
        ease: Int,
        combo: Int,
    ) {
        val up = 2.0.pow(minOf(combo, 12) / 12.0)
        notes(BLIPS[ease].orEmpty().map { it * up }, Wave.SQUARE, 0.3, 0.05, 0.09)
    }

    fun chime(
        freqs: List<Double>,
        w: Wave = Wave.TRIANGLE,
        vol: Double = 0.35,
    ) = notes(freqs, w, vol, 0.09, 0.3)

    /** the combo-break fall */
    fun slide() {
        if (!XpGame.sound) return
        val secs = 0.4
        val n = (secs * RATE).toInt()
        var phase = 0.0
        play(
            DoubleArray(n) {
                val t = it.toDouble() / RATE
                phase += 440 * (110.0 / 440).pow(t / secs) / RATE
                wave(Wave.SAW, phase) * 0.35 * exp(-t * 6.9 / secs)
            },
        )
    }

    /** opening kuma3: a soft rising chord */
    fun startup() = notes(listOf(311.1, 466.2, 622.3, 932.3, 1244.5), Wave.TRIANGLE, 0.22, 0.12, 1.4)

    fun buzz(
        context: Context,
        kind: String,
    ) {
        if (!XpGame.vibrate) return
        val timings = BUZZ[kind] ?: return
        runCatching {
            val vibrator = context.getSystemService(Vibrator::class.java) ?: return@runCatching
            val pattern = longArrayOf(0) + timings
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        }
    }
}
