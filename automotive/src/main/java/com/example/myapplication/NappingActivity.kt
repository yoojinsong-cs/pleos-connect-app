package com.example.myapplication

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import java.util.Locale

class NappingActivity : FragmentActivity() {

    private var totalSeconds = 20 * 60
    private var elapsedSeconds = 0
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    private var mediaPlayer: MediaPlayer? = null
    private var isMuted = false
    private var currentAsmr = R.raw.asmr1

    private var currentPhaseIndex = -1

    data class Phase(
        val label: String,
        val title: String,
        val subtitle: String,
        val phaseInfo: String,
        val startPercent: Int,
        val endPercent: Int
    )

    private val phases = listOf(
        Phase("NAPPING · WIND DOWN", "눈을 감고 편안하게 쉬세요", "안내 음성은 잠시 후 종료돼요.", "수면 진입", 0, 10),
        Phase("NAPPING · SLEEP", "휴식 중이에요", "편안하게 쉬고 계세요.", "숙면 중", 10, 85),
        Phase("NAPPING · WAKE UP", "천천히 깨어날 시간이에요", "부드럽게 일어날 준비를 해요.", "각성 1", 85, 95),
        Phase("NAPPING · GET READY", "마지막 1분이에요", "곧 완전히 깨어날 준비를 해주세요.", "각성 2", 95, 100)
    )

    private val ticker = object : Runnable {
        override fun run() {
            elapsedSeconds++
            updateUI()
            if (elapsedSeconds < totalSeconds) {
                handler.postDelayed(this, 1000)
            } else {
                stopAsmr()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_napping_progress)

        totalSeconds = intent.getIntExtra("TOTAL_SECONDS", 20 * 60)

        findViewById<TextView>(R.id.tvBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnClose).setOnClickListener {
            finishAffinity()
            System.exit(0)
        }

        val btnMute = findViewById<ImageButton>(R.id.btnMute)
        btnMute.setOnClickListener {
            isMuted = !isMuted
            if (isMuted) {
                mediaPlayer?.setVolume(0f, 0f)
                btnMute.setImageResource(R.drawable.ic_volume_off)
            } else {
                mediaPlayer?.setVolume(1f, 1f)
                btnMute.setImageResource(R.drawable.ic_volume_on)
            }
        }

        val btnAsmrSelect = findViewById<Button>(R.id.btnAsmrSelect)
        btnAsmrSelect.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add(0, R.raw.asmr1, 0, "♪  ASMR 1")
            popup.menu.add(0, R.raw.asmr2, 1, "♪  ASMR 2")
            popup.setOnMenuItemClickListener { item ->
                currentAsmr = item.itemId
                btnAsmrSelect.text = item.title
                switchAsmr(currentAsmr)
                true
            }
            popup.show()
        }

        initTts()
        startAsmr(currentAsmr)
        handler.postDelayed(ticker, 1000)
        updateUI()
    }

    private fun startAsmr(resId: Int) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(this, resId).apply {
            isLooping = true
            if (!isMuted) setVolume(1f, 1f) else setVolume(0f, 0f)
            start()
        }
    }

    private fun switchAsmr(resId: Int) {
        startAsmr(resId)
    }

    private fun stopAsmr() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun initTts() {
        tts = TextToSpeech(applicationContext, Mode.ON_DEVICE)
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                tts.addEventListener(object : EventListener {
                    override fun onReady() = Unit
                    override fun onStart() = Unit
                    override fun onUpdatedRms(rms: Double) = Unit
                    override fun onDone() = Unit
                    override fun onStop() = Unit
                    override fun onError(errorType: ErrorType) = Unit
                })
                tts.setLocale(Locale.KOREA)
                ttsReady = true
                speak("눈을 감고 편안하게 쉬세요")
            }
            override fun onFailure(errorType: ErrorType) = Unit
        })
    }

    private fun speak(text: String) {
        if (ttsReady) tts.speak(text)
    }

    private fun setScreenBrightness(brightness: Float) {
        val params = window.attributes
        params.screenBrightness = brightness
        window.attributes = params
        val alpha = if (brightness == WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
            0f
        } else {
            (1f - brightness).coerceIn(0f, 0.95f)
        }
        runOnUiThread {
            findViewById<View>(R.id.brightnessOverlay)?.alpha = alpha
        }
    }

    private fun updateUI() {
        val percent = (elapsedSeconds * 100 / totalSeconds)
        val phaseIndex = phases.indexOfLast { percent >= it.startPercent }.coerceAtLeast(0)
        val phase = phases[phaseIndex]

        if (phaseIndex != currentPhaseIndex) {
            currentPhaseIndex = phaseIndex
            when (phaseIndex) {
                0 -> { setScreenBrightness(0.3f);  speak("눈을 감고 편안하게 쉬세요") }
                1 -> { setScreenBrightness(0.1f);  }
                2 -> { setScreenBrightness(0.3f);  speak("천천히 깨어날 시간이에요") }
                3 -> {
                    setScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                    speak("곧 완전히 깨어납니다")
                }
            }
        }

        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60
        val totalMin = totalSeconds / 60

        findViewById<TextView>(R.id.tvPhaseLabel).text = phase.label
        findViewById<TextView>(R.id.tvTitle).text = phase.title
        findViewById<TextView>(R.id.tvSubtitle).text = phase.subtitle
        findViewById<TextView>(R.id.tvPhaseInfo).text = phase.phaseInfo
        findViewById<TextView>(R.id.tvElapsed).text = "%02d:%02d".format(minutes, seconds)
        findViewById<TextView>(R.id.tvTotalTime).text = " / %02d:00".format(totalMin)
        findViewById<TextView>(R.id.tvPhasePercent).text = "${percent}%"
        findViewById<ProgressBar>(R.id.progressBar).progress = percent
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(ticker)
        stopAsmr()
        if (ttsReady) {
            tts.stop()
            tts.release()
        }
        setScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
    }
}