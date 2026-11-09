package com.example.hyundai_first_project

// ─────────────────────────────────────────────
// [공용] Sounds.kt  ─ 음원 파일 없이 코드로 만드는 소리
// strikeBowl() 싱잉볼 한 번 / startLoop(Ambience.RAIN) 반복 소리 / stopLoop() 멈춤
// ─────────────────────────────────────────────
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.Random
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class Ambience { RAIN, FIRE, MEDITATION }

class Sounds {
    private val rate = 44100
    private val bowlSeconds = 8
    private var loopFlag: AtomicBoolean? = null

    @Volatile var volume = 0.7f        // 0.0 ~ 1.0

    // 싱잉볼 소리 (배음 4개가 천천히 사라지는 소리)
    private val bowlPcm: ShortArray by lazy {
        val total = rate * bowlSeconds
        val out = ShortArray(total)
        val ratios = doubleArrayOf(1.0, 2.76, 5.40, 8.93)
        val levels = doubleArrayOf(1.0, 0.5, 0.25, 0.12)
        val fades = doubleArrayOf(0.45, 0.7, 1.2, 2.0)
        val base = 196.0
        for (i in 0 until total) {
            val t = i.toDouble() / rate
            var sample = 0.0
            for (k in ratios.indices) {
                val wobble = 1 + 0.15 * sin(2 * PI * 0.8 * (k + 1) * t)
                sample += levels[k] * exp(-fades[k] * t) * wobble * sin(2 * PI * base * ratios[k] * t)
            }
            val attack = min(1.0, t / 0.01)
            out[i] = (sample * attack * 0.35 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        out
    }

    fun prepare() {
        Thread { bowlPcm.size }.start()
    }

    fun strikeBowl() {
        Thread {
            try {
                val data = bowlPcm
                val track = buildTrack(data.size * 2, AudioTrack.MODE_STATIC)
                track.write(data, 0, data.size)
                track.setVolume(volume)
                track.play()
                Thread.sleep(bowlSeconds * 1000L + 200)
                track.release()
            } catch (_: Exception) {
            }
        }.start()
    }

    fun startLoop(kind: Ambience) {
        stopLoop()
        val running = AtomicBoolean(true)
        loopFlag = running
        Thread { playLoop(kind, running) }.start()
    }

    fun stopLoop() {
        loopFlag?.set(false)
        loopFlag = null
    }

    private fun playLoop(kind: Ambience, running: AtomicBoolean) {
        val track = try {
            val minBuffer = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            buildTrack(max(minBuffer, 8192), AudioTrack.MODE_STREAM)
        } catch (_: Exception) {
            return
        }
        val buffer = ShortArray(2048)
        val random = Random()
        val notes = doubleArrayOf(110.0, 164.81, 220.0, 277.18, 329.63)   // 명상 화음
        var n = 0L
        var soft = 0.0
        var brown = 0.0
        var drop = 0.0
        var dropFreq = 2000.0
        var crack = 0.0
        var lastWhite = 0.0
        try {
            track.play()
            while (running.get()) {
                for (i in buffer.indices) {
                    val white = random.nextDouble() * 2 - 1
                    val t = n.toDouble() / rate
                    val s = when (kind) {
                        Ambience.RAIN -> {                     // 쏴아 + 가끔 톡
                            soft += 0.08 * (white - soft)
                            if (random.nextDouble() < 0.0004) {
                                drop = 1.0
                                dropFreq = 1200 + random.nextDouble() * 1800
                            }
                            drop *= 0.9985
                            soft * 1.6 + drop * 0.25 * sin(2 * PI * dropFreq * t)
                        }
                        Ambience.FIRE -> {                     // 웅~ + 타닥
                            brown = (brown + white * 0.02) * 0.998
                            if (random.nextDouble() < 0.0003) crack = 0.6 + random.nextDouble() * 0.4
                            crack *= 0.993
                            val sharp = white - lastWhite
                            lastWhite = white
                            brown * 2.0 + crack * sharp * 0.5
                        }
                        Ambience.MEDITATION -> {               // 천천히 출렁이는 화음
                            var sum = 0.0
                            for (k in notes.indices) {
                                val swell = 0.5 + 0.5 * sin(2 * PI * (0.03 + 0.01 * k) * t + k)
                                sum += swell * sin(2 * PI * notes[k] * t)
                            }
                            sum / notes.size * (0.6 + 0.4 * sin(2 * PI * 0.07 * t))
                        }
                    }
                    buffer[i] = (s * volume * 0.5 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
                    n++
                }
                track.write(buffer, 0, buffer.size)
            }
        } catch (_: Exception) {
        } finally {
            try { track.stop() } catch (_: Exception) {}
            track.release()
        }
    }

    private fun buildTrack(bytes: Int, mode: Int): AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setBufferSizeInBytes(bytes)
        .setTransferMode(mode)
        .build()
}
