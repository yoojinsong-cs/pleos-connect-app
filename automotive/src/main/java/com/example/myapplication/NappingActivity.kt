package com.example.myapplication

import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import ai.pleos.playground.vehicle.Vehicle
import java.util.Locale

class NappingActivity : FragmentActivity() {

    private lateinit var vehicle: Vehicle
    private lateinit var tvBack: TextView
    private lateinit var tvMainMessage: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvPercent: TextView
    private lateinit var tvPhaseLabel: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnClose: ImageButton
    private lateinit var btnMute: ImageButton
    private lateinit var btnAsmrSelect: Button

    private var timer: CountDownTimer? = null
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

        tvBack        = findViewById(R.id.tvBack)
        tvMainMessage = findViewById(R.id.tvMainMessage)
        tvStatus      = findViewById(R.id.tvStatus)
        tvTimer       = findViewById(R.id.tvTimer)
        tvPercent     = findViewById(R.id.tvPercent)
        tvPhaseLabel  = findViewById(R.id.tvPhaseLabel)
        progressBar   = findViewById(R.id.progressBar)
        btnClose      = findViewById(R.id.btnClose)
        btnMute       = findViewById(R.id.btnMute)
        btnAsmrSelect = findViewById(R.id.btnAsmrSelect)

        // 뒤로가기
        tvBack.setOnClickListener {
            cleanupAndExit()
            finish()
        }

        // 앱 종료
        btnClose.setOnClickListener {
            cleanupAndExit()
            finishAffinity()
            System.exit(0)
        }

        // 음소거
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

        // ASMR 드롭다운
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
        try { vehicle.release() } catch (e: Exception) { }
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
        tvMainMessage.text = "잠깐의 휴식으로 에너지를 채워요"
        tvStatus.text = "20분 뒤 부드럽게 깨울게요."
        tvPhaseLabel.text = "냅핑 시작"
        try {
            val door = vehicle.getDoor()
            // TODO: door.setDoorLock(전체, true)
        } catch (e: Exception) { }
        playNextAsmr()
    }

    private fun startNappingMode() {
        timer = object : CountDownTimer(1_200_000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val elapsed = 1_200_000L - millisUntilFinished
                val s = (millisUntilFinished / 1000).toInt()
                val percent = (elapsed / 1_200_000f * 100).toInt()

                tvTimer.text = String.format(Locale.getDefault(), "%02d:%02d", s / 60, s % 60)
                tvPercent.text = "$percent%"
                progressBar.progress = percent

                handlePhase(elapsed)
            }
            override fun onFinish() {
                tvTimer.text = "00:00"
                tvPercent.text = "100%"
                progressBar.progress = 100
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
        tvMainMessage.text = "잠깐의 휴식으로 에너지를 채워요"
        tvStatus.text = "20분 뒤 부드럽게 깨울게요."
        tvPhaseLabel.text = "환경 설정 중"
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
        tvMainMessage.text = "이제 편히 눈을 감아보세요"
        tvStatus.text = "곧 음성 안내가 종료되고 조용한 휴식이 시작됩니다."
        tvPhaseLabel.text = "수면 진입"
        mediaPlayer?.setVolume(0.2f, 0.2f)
        ttsPlayer = MediaPlayer.create(this, R.raw.tts_sleep)
        ttsPlayer?.setOnCompletionListener {
            if (!isMuted) mediaPlayer?.setVolume(1f, 1f)
            ttsPlayer?.release()
            ttsPlayer = null
            // TTS 끝나면 수면 중 텍스트로
            tvMainMessage.text = "잠시 아무것도 하지 않아도 괜찮아요"
            tvStatus.text = "화면은 수면을 위해 최소 밝기로 유지돼요"
            tvPhaseLabel.text = "조용한 휴식 중"
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
        tvMainMessage.text = "빛과 함께 천천히 깨어나요"
        tvStatus.text = "엠비언트 조명이 서서히 밝아져요."
        tvPhaseLabel.text = "각성 1 · 조명 상승"
        try {
            val hvac = vehicle.getHVAC()
            // TODO: hvac.setTargetTemperature(현재온도 - 2)
            // TODO: hvac.setAirCirculation(EXTERNAL)
        } catch (e: Exception) { }
    }

    private fun applyWake2() {
        tvMainMessage.text = "이제 다음 일정을 준비해요"
        tvStatus.text = "등받이가 돌아오고, 공조가 전환됩니다."
        tvPhaseLabel.text = "각성 2 · 자세 복귀"
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
        tvMainMessage.text = "냅핑이 종료됐어요"
        tvStatus.text = "수고하셨어요."
        tvPhaseLabel.text = "완료"
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
        mediaPlayer?.release()
        ttsPlayer?.release()
        try { vehicle.release() } catch (e: Exception) { }
    }
}