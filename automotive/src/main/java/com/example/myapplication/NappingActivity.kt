package com.example.myapplication

import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import ai.pleos.playground.vehicle.Vehicle
import java.util.Locale

class NappingActivity : FragmentActivity(), TextToSpeech.OnInitListener {

    private lateinit var vehicle: Vehicle
    private lateinit var tvStatus: TextView
    private lateinit var tvTimer: TextView
    private lateinit var btnClose: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnMute: ImageButton
    private lateinit var btnAsmrSelect: Button

    private var timer: CountDownTimer? = null
    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private var ttsPlayer: MediaPlayer? = null

    private var isMuted = false
    private var currentAsmrIndex = 0

    private val ASMR_SOUNDS = listOf(R.raw.asmr1, R.raw.asmr2)
    private val ASMR_NAMES  = listOf("ASMR 1", "ASMR 2")

    private var envDone     = false
    private var descentDone = false
    private var wake1Done   = false
    private var wake2Done   = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_napping)

        tvStatus     = findViewById(R.id.tvStatus)
        tvTimer      = findViewById(R.id.tvTimer)
        btnClose     = findViewById(R.id.btnClose)
        btnBack      = findViewById(R.id.btnBack)
        btnMute      = findViewById(R.id.btnMute)
        btnAsmrSelect = findViewById(R.id.btnAsmrSelect)

        btnClose.setOnClickListener {
            cleanupAndExit()
            finishAffinity()
            System.exit(0)
        }

        btnBack.setOnClickListener {
            cleanupAndExit()
            finish()
        }

        btnMute.setOnClickListener {
            isMuted = !isMuted
            if (isMuted) {
                mediaPlayer?.setVolume(0f, 0f)
                btnMute.setImageResource(android.R.drawable.ic_audio_vol_mute)
            } else {
                mediaPlayer?.setVolume(1f, 1f)
                btnMute.setImageResource(android.R.drawable.ic_audio_vol)
            }
        }

        // 드롭다운 메뉴
        btnAsmrSelect.setOnClickListener {
            val popup = PopupMenu(this, btnAsmrSelect)
            ASMR_NAMES.forEachIndexed { index, name ->
                popup.menu.add(0, index, index, name)
            }
            popup.setOnMenuItemClickListener { item ->
                currentAsmrIndex = item.itemId
                btnAsmrSelect.text = "🎵 ${ASMR_NAMES[currentAsmrIndex]}"
                playNextAsmr()
                true
            }
            popup.show()
        }

        tts = TextToSpeech(this, this)
        vehicle = Vehicle(this)
        vehicle.initialize()

        applyNappingStart()
        startNappingMode()
    }

    private fun cleanupAndExit() {
        timer?.cancel()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        ttsPlayer?.stop()
        ttsPlayer?.release()
        ttsPlayer = null
        tts?.shutdown()
        tts = null
        try { vehicle.release() } catch (e: Exception) { }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.KOREAN
        }
    }

    private fun playNextAsmr() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(this, ASMR_SOUNDS[currentAsmrIndex])
        if (isMuted) mediaPlayer?.setVolume(0f, 0f)
        mediaPlayer?.setOnCompletionListener {
            currentAsmrIndex = (currentAsmrIndex + 1) % ASMR_SOUNDS.size
            btnAsmrSelect.text = "🎵 ${ASMR_NAMES[currentAsmrIndex]}"
            playNextAsmr()
        }
        mediaPlayer?.start()
    }

    private fun applyNappingStart() {
        tvStatus.text = "냅핑 모드 시작"
        try {
            val door = vehicle.getDoor()
            // TODO: door.setDoorLock(전체, true)
        } catch (e: Exception) { }
        playNextAsmr()
    }

    private fun startNappingMode() {
        timer = object : CountDownTimer(1_200_000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val s = (millisUntilFinished / 1000).toInt()
                tvTimer.text = String.format(Locale.getDefault(), "%02d:%02d", s / 60, s % 60)
                handlePhase(1_200_000L - millisUntilFinished)
            }
            override fun onFinish() {
                tvTimer.text = "00:00"
                handleFinish()
            }
        }.start()
    }

    private fun handlePhase(elapsedMs: Long) {
        val minutes = elapsedMs / 60_000L
        when {
            minutes < 1  -> { if (!envDone)     { envDone = true;     applyEnvironment() } }
            minutes < 2  -> { if (!descentDone) { descentDone = true; applyDescent() } }
            minutes < 17 -> { }
            minutes < 19 -> {
                if (!wake1Done) { wake1Done = true; applyWake1() }
                applyLightRamp(elapsedMs)
            }
            else         -> { if (!wake2Done) { wake2Done = true; applyWake2() } }
        }
    }

    private fun applyEnvironment() {
        tvStatus.text = "환경 설정 중..."
        try {
            val seat = vehicle.getSeat()
            // TODO: seat.setReclineAngle(ROW_1_LEFT, 130)
            // TODO: seat.setLumbarSupport(ROW_1_LEFT, MAX)
        } catch (e: Exception) { }
        try {
            val light = vehicle.getLight()
            // TODO: light.setAmbientLightOff()
        } catch (e: Exception) { }
        try {
            val hvac = vehicle.getHVAC()
            // TODO: hvac.setFanSpeed(1)
            // TODO: hvac.setAirCirculation(INTERNAL)
        } catch (e: Exception) { }
    }

    private fun applyDescent() {
        tvStatus.text = "호흡을 고르세요..."
        mediaPlayer?.setVolume(0.2f, 0.2f)
        ttsPlayer = MediaPlayer.create(this, R.raw.tts_sleep)
        ttsPlayer?.setOnCompletionListener {
            if (!isMuted) mediaPlayer?.setVolume(1f, 1f)
            ttsPlayer?.release()
            ttsPlayer = null
        }
        ttsPlayer?.start()
    }

    private fun applyLightRamp(elapsedMs: Long) {
        val phaseMs = elapsedMs - 17 * 60_000L
        if (phaseMs % 30_000L > 1_000L) return
        val ratio = phaseMs.toFloat() / 120_000f
        val colorTemp = (2500 + 4000 * ratio).toInt()
        try {
            val light = vehicle.getLight()
            // TODO: light.setAmbientLightColorTemp(colorTemp)
        } catch (e: Exception) { }
    }

    private fun applyWake1() {
        tvStatus.text = "기상 준비 중..."
        try {
            val hvac = vehicle.getHVAC()
            // TODO: hvac.setTargetTemperature(현재온도 - 2)
            // TODO: hvac.setAirCirculation(EXTERNAL)
        } catch (e: Exception) { }
    }

    private fun applyWake2() {
        tvStatus.text = "20분이 지났어요!"
        try {
            val seat = vehicle.getSeat()
            // TODO: seat.setReclineAngle(ROW_1_LEFT, 115)
            // TODO: seat.setEarMassage(ON)
        } catch (e: Exception) { }
        try {
            val hvac = vehicle.getHVAC()
            // TODO: hvac.setFanSpeed(2)
        } catch (e: Exception) { }
        mediaPlayer?.setVolume(0.2f, 0.2f)
        ttsPlayer = MediaPlayer.create(this, R.raw.tts_wake)
        ttsPlayer?.setOnCompletionListener {
            if (!isMuted) mediaPlayer?.setVolume(1f, 1f)
            ttsPlayer?.release()
            ttsPlayer = null
        }
        ttsPlayer?.start()
    }

    private fun handleFinish() {
        tvStatus.text = "냅핑 모드 종료"
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        try {
            val door = vehicle.getDoor()
            // TODO: door.setDoorLock(전체, false)
        } catch (e: Exception) { }
        vehicle.release()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        tts?.shutdown()
        mediaPlayer?.release()
        ttsPlayer?.release()
        try { vehicle.release() } catch (e: Exception) { }
    }
}