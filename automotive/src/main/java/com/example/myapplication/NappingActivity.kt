package com.example.myapplication

import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.tts.TextToSpeech
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import ai.pleos.playground.vehicle.Vehicle
import java.util.Locale

class NappingActivity : FragmentActivity(), TextToSpeech.OnInitListener {

    private lateinit var vehicle: Vehicle
    private lateinit var tvStatus: TextView
    private lateinit var tvTimer: TextView

    private var timer: CountDownTimer? = null
    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null

    private var envDone    = false
    private var descentDone = false
    private var wake1Done  = false
    private var wake2Done  = false

    private val NAPPING_SOUNDS = listOf(
        R.raw.sample
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_napping)

        tvStatus = findViewById(R.id.tvStatus)
        tvTimer  = findViewById(R.id.tvTimer)

        tts = TextToSpeech(this, this)

        vehicle = Vehicle(this)
        vehicle.initialize()

        applyNappingStart()
        startNappingMode()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.KOREAN
        }
    }

    private fun applyNappingStart() {
        tvStatus.text = "냅핑 모드 시작"
        try {
            val door = vehicle.getDoor()
            // TODO: door.setDoorLock(전체, true)
        } catch (e: Exception) { }

        mediaPlayer = MediaPlayer.create(this, NAPPING_SOUNDS[0])
        mediaPlayer?.isLooping = true
        mediaPlayer?.start()
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
        tts?.speak(
            "편안하게 숨을 들이쉬고, 천천히 내쉬세요. 이제 편히 쉬세요.",
            TextToSpeech.QUEUE_FLUSH, null, null
        )
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
        tts?.speak(
            "20분이 지났어요. 다음 일정 19시 30분입니다.",
            TextToSpeech.QUEUE_FLUSH, null, null
        )
    }

    private fun handleFinish() {
        tvStatus.text = "냅핑 모드 종료"
        mediaPlayer?.stop()
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
        try { vehicle.release() } catch (e: Exception) { }
    }
}