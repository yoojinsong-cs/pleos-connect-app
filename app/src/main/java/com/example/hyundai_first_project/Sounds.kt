package com.example.hyundai_first_project

// ─────────────────────────────────────────────
// [공용] Sounds.kt  ─ 음원 파일 없이 코드로 만드는 소리 (v11)
// strikeBowl()               싱잉볼 한 번
// startLoop(Ambience.RAIN)   반복 소리 (빗소리·모닥불·명상·우주·파도·숲)
// stopLoop()                 멈춤
//
// v11: 소리가 "지직" 깨지던 문제 고침
//  - 전에는 소리를 듣는 동안 실시간으로 계산해서, 계산이 늦으면 소리가 끊겼어요.
//  - 이제는 10초짜리 소리를 미리 만들어 두고 이어 붙여(반복) 틀어요. 계산이 밀릴 일이 없어요.
//  - 소리 크기를 너무 크지 않게 맞추고(찢어짐 방지), 시작·끝을 부드럽게 해서 "틱" 소리를 없앴어요.
// ─────────────────────────────────────────────
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// 반복 소리 종류 (예전 RAIN·FIRE·MEDITATION 은 그대로 쓸 수 있어요)
enum class Ambience { RAIN, FIRE, MEDITATION, SPACE, OCEAN, FOREST }

class Sounds {
    private val bowlRate = 44100
    private val loopRate = 22050          // 배경 소리는 이 정도면 충분하고 가벼워요
    private val bowlSeconds = 9
    private val loopSeconds = 10
    private val fadeSeconds = 1           // 반복 이음새를 부드럽게 섞는 시간

    private val loops = ConcurrentHashMap<Ambience, ShortArray>()
    private var loopTrack: AudioTrack? = null
    private val lock = Any()
    @Volatile private var loopToken = 0     // 멈춘 뒤에 늦게 켜지는 걸 막는 번호

    @Volatile var volume = 0.7f           // 0.0 ~ 1.0
        set(value) {
            field = value.coerceIn(0f, 1f)
            try { loopTrack?.setVolume(field) } catch (_: Exception) {}
        }

    // ── 싱잉볼: 배음 4개가 천천히 사라지는 소리 ──
    private val bowlPcm: ShortArray by lazy {
        val total = bowlRate * bowlSeconds
        val raw = DoubleArray(total)
        val ratios = doubleArrayOf(1.0, 2.76, 5.40, 8.93)
        val levels = doubleArrayOf(1.0, 0.45, 0.2, 0.08)
        val fades = doubleArrayOf(0.4, 0.65, 1.1, 1.9)
        val base = 196.0
        for (i in 0 until total) {
            val t = i.toDouble() / bowlRate
            var sample = 0.0
            for (k in ratios.indices) {
                val wobble = 1 + 0.12 * sin(2 * PI * 0.8 * (k + 1) * t)           // 웅~웅~ 맥놀이
                sample += levels[k] * exp(-fades[k] * t) * wobble * sin(2 * PI * base * ratios[k] * t)
            }
            val attack = min(1.0, t / 0.008)                                     // 시작 "틱" 방지
            val release = min(1.0, (bowlSeconds - t) / 1.5)                       // 끝 1.5초 동안 사라지기
            raw[i] = sample * attack * release
        }
        toPcm(raw, 0.55)
    }

    fun prepare() {
        Thread {
            bowlPcm.size
            loopFor(Ambience.MEDITATION)
        }.start()
    }

    fun strikeBowl() {
        Thread {
            try {
                val data = bowlPcm
                val track = buildTrack(bowlRate, data.size * 2, AudioTrack.MODE_STATIC)
                track.write(data, 0, data.size)
                track.setVolume(volume)
                track.play()
                Thread.sleep(bowlSeconds * 1000L + 300)
                track.release()
            } catch (_: Exception) {
            }
        }.start()
    }

    fun startLoop(kind: Ambience) {
        val token = synchronized(lock) { ++loopToken }
        Thread {
            val data = loopFor(kind)
            synchronized(lock) {
                if (token != loopToken) return@Thread           // 그 사이 멈춤·다른 소리를 눌렀으면 안 켜요
                stopLoopLocked()
                try {
                    val track = buildTrack(loopRate, data.size * 2, AudioTrack.MODE_STATIC)
                    track.write(data, 0, data.size)
                    track.setLoopPoints(0, data.size, -1)          // 끝나면 처음으로, 계속 반복
                    track.setVolume(volume)
                    track.play()
                    loopTrack = track
                } catch (_: Exception) {
                }
            }
        }.start()
    }

    fun stopLoop() {
        synchronized(lock) {
            loopToken++
            stopLoopLocked()
        }
    }

    private fun stopLoopLocked() {
        val track = loopTrack ?: return
        loopTrack = null
        try { track.stop() } catch (_: Exception) {}
        try { track.release() } catch (_: Exception) {}
    }

    // ── 반복 소리 만들기 (한 번 만들면 기억해 둬요) ──
    private fun loopFor(kind: Ambience): ShortArray = loops.getOrPut(kind) { makeLoop(kind) }

    private fun makeLoop(kind: Ambience): ShortArray {
        val length = loopRate * loopSeconds
        val fade = loopRate * fadeSeconds
        val raw = DoubleArray(length + fade)
        val random = Random(kind.ordinal * 31L + 7)
        var soft = 0.0
        var soft2 = 0.0
        var brown = 0.0
        var drop = 0.0
        var dropFreq = 2000.0
        var crack = 0.0
        var lastWhite = 0.0
        var chirp = 0.0
        var chirpFreq = 3000.0
        var chirpPhase = 0.0
        val notes = doubleArrayOf(110.0, 164.81, 220.0, 277.18, 329.63)   // 명상 화음
        val spaceNotes = doubleArrayOf(55.0, 82.41, 110.0, 164.81)        // 우주: 낮은 울림
        for (i in raw.indices) {
            val t = i.toDouble() / loopRate
            val white = random.nextDouble() * 2 - 1
            raw[i] = when (kind) {
                Ambience.RAIN -> {                     // 쏴아 + 톡톡 (빗소리 ASMR)
                    soft += 0.25 * (white - soft)
                    soft2 += 0.05 * (soft - soft2)
                    if (random.nextDouble() < 0.0012) {
                        drop = 0.6 + random.nextDouble() * 0.4
                        dropFreq = 1500 + random.nextDouble() * 2500
                    }
                    drop *= 0.996
                    (soft - soft2) * 1.4 + soft2 * 0.8 + drop * 0.18 * sin(2 * PI * dropFreq * t)
                }
                Ambience.FIRE -> {                     // 웅~ + 타닥타닥 (모닥불)
                    brown = (brown + white * 0.02) * 0.997
                    if (random.nextDouble() < 0.0006) crack = 0.5 + random.nextDouble() * 0.5
                    crack *= 0.985
                    val sharp = white - lastWhite
                    lastWhite = white
                    brown * 2.2 + crack * sharp * 0.45
                }
                Ambience.MEDITATION -> {               // 천천히 출렁이는 화음
                    var sum = 0.0
                    for (k in notes.indices) {
                        val swell = 0.5 + 0.5 * sin(2 * PI * (k + 1) / loopSeconds * t + k)
                        sum += swell * sin(2 * PI * notes[k] * t)
                    }
                    sum / notes.size
                }
                Ambience.SPACE -> {                    // 낮게 깔리는 울림 + 반짝이는 높은 소리
                    var sum = 0.0
                    for (k in spaceNotes.indices) {
                        val swell = 0.6 + 0.4 * sin(2 * PI * (k + 1) / loopSeconds * t + k * 1.7)
                        sum += swell * sin(2 * PI * spaceNotes[k] * t + 0.3 * sin(2 * PI * 0.2 * t))
                    }
                    val shimmer = 0.15 * (0.5 + 0.5 * sin(2 * PI * 3.0 / loopSeconds * t)) * sin(2 * PI * 1318.5 * t)
                    sum / spaceNotes.size + shimmer
                }
                Ambience.OCEAN -> {                    // 파도가 밀려왔다 빠지는 소리 (5초에 한 번)
                    brown = (brown + white * 0.03) * 0.995
                    soft += 0.15 * (white - soft)
                    val wave = 0.5 - 0.5 * kotlin.math.cos(2 * PI * 2.0 / loopSeconds * t)
                    brown * 1.6 * (0.35 + 0.65 * wave) + soft * 0.5 * wave * wave
                }
                Ambience.FOREST -> {                   // 산들바람 + 새소리
                    soft += 0.03 * (white - soft)
                    if (chirp < 0.01 && random.nextDouble() < 0.00012) {
                        chirp = 1.0
                        chirpFreq = 2500 + random.nextDouble() * 2000
                    }
                    chirp *= 0.9996
                    chirpPhase += 2 * PI * (chirpFreq + 600 * sin(2 * PI * 14 * t)) / loopRate
                    soft * 2.0 + chirp * 0.12 * sin(chirpPhase)
                }
            }
        }
        // 끝부분 1초를 처음 1초와 섞어서 이음새가 안 들리게
        val out = DoubleArray(length)
        for (i in 0 until length) {
            out[i] = if (i < fade) {
                val a = i.toDouble() / fade
                raw[i] * a + raw[length + i] * (1 - a)
            } else raw[i]
        }
        return toPcm(out, 0.45)
    }

    // 가장 큰 소리가 peak 이 되도록 맞춘 뒤 16비트로 바꾸기 (찢어짐 방지)
    private fun toPcm(raw: DoubleArray, peak: Double): ShortArray {
        var loudest = 1e-9
        for (v in raw) loudest = max(loudest, abs(v))
        val gain = peak / loudest
        return ShortArray(raw.size) { i -> (raw[i] * gain * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort() }
    }

    private fun buildTrack(rate: Int, bytes: Int, mode: Int): AudioTrack = AudioTrack.Builder()
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
