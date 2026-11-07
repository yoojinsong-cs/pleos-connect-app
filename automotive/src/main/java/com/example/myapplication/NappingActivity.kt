package com.example.myapplication

import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
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
    private lateinit var btnClose: TextView
    private lateinit var btnMute: TextView
    private lateinit var btnAsmrSelect: Button

    private var timer: CountDownTimer? = null
    private var mediaPlayer: MediaPlayer? = null
    private var ttsPlayer: MediaPlayer? = null

    private var isMuted = false
    private var currentAsmrIndex = 0

    private val ASMR_SOUNDS = listOf(R.raw.asmr1, R.raw.asmr2)
    private val ASMR_NAMES  = listOf("ASMR 1", "ASMR 2")

    private val TOTAL_TIME = 60_000L

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

        tvBack.setOnClickListener {
            cleanupAndExit()
            finish()
        }

        btnClose.setOnClickListener {
            cleanupAndExit()
            finishAffinity()
            System.exit(0)
        }

        btnMute.setOnClickListener {
            isMuted = !isMuted
            if (isMuted) {
                mediaPlayer?.setVolume(0f, 0f)
                btnMute.text = "🔈"
            } else {
                mediaPlayer?.setVolume(1f, 1f)
                btnMute.text = "🔇"
            }
        }

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
        } catch (e: Exception) { }
        playNextAsmr()
    }

    private fun startNappingMode() {
        timer = object : CountDownTimer(TOTAL_TIME, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val elapsed = TOTAL_TIME - millisUntilFinished
                val s = (millisUntilFinished / 1000).toInt()
                val percent = (elapsed / TOTAL_TIME.toFloat() * 100).toInt()

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
        val percent = elapsedMs.toFloat() / TOTAL_TIME
        when {
            percent < 0.05f -> { if (!envDone)     { envDone = true;     applyEnvironment() } }
            percent < 0.10f -> { if (!descentDone) { descentDone = true; applyDescent() } }
            percent < 0.85f -> { }
            percent < 0.95f -> {
                if (!wake1Done) { wake1Done = true; applyWake1() }
                applyLightRamp(elapsedMs)
            }
            else            -> { if (!wake2Done) { wake2Done = true; applyWake2() } }
        }
    }

    private fun applyEnvironment() {
        tvMainMessage.text = "잠깐의 휴식으로 에너지를 채워요"
        tvStatus.text = "20분 뒤 부드럽게 깨울게요."
        tvPhaseLabel.text = "환경 설정 중"
        try { val seat = vehicle.getSeat() } catch (e: Exception) { }
        try { val light = vehicle.getLight() } catch (e: Exception) { }
        try { val hvac = vehicle.getHVAC() } catch (e: Exception) { }
    }

    private fun applyDescent() {
        tvMainMessage.text = "이제 편히 눈을 감아보세요"
        tvStatus.text = "곧 음성 안내가 종료되고 조용한 휴식이 시작됩니다."
        tvPhaseLabel.text = "수면 진입"

        if (!isMuted) mediaPlayer?.setVolume(0.2f, 0.2f)  // 뮤트 중이면 볼륨 건드리지 않음

        ttsPlayer = MediaPlayer.create(this, R.raw.tts_sleep)
        ttsPlayer?.setOnCompletionListener {
            if (!isMuted) mediaPlayer?.setVolume(1f, 1f)  // 뮤트 중이면 복구 안 함
            ttsPlayer?.release()
            ttsPlayer = null
            tvMainMessage.text = "잠시 아무것도 하지 않아도 괜찮아요"
            tvStatus.text = "화면은 수면을 위해 최소 밝기로 유지돼요"
            tvPhaseLabel.text = "조용한 휴식 중"
        }
        ttsPlayer?.start()
    }

    private fun applyLightRamp(elapsedMs: Long) {
        val phaseMs = elapsedMs - (TOTAL_TIME * 0.85f).toLong()
        if (phaseMs % 3_000L > 1_000L) return
        val ratio = phaseMs.toFloat() / (TOTAL_TIME * 0.10f)
        val colorTemp = (2500 + 4000 * ratio).toInt()
        try { val light = vehicle.getLight() } catch (e: Exception) { }
    }

    private fun applyWake1() {
        tvMainMessage.text = "빛과 함께 천천히 깨어나요"
        tvStatus.text = "엠비언트 조명이 서서히 밝아져요."
        tvPhaseLabel.text = "각성 1 · 조명 상승"
        try { val hvac = vehicle.getHVAC() } catch (e: Exception) { }
    }

    private fun applyWake2() {
        tvMainMessage.text = "이제 다음 일정을 준비해요"
        tvStatus.text = "등받이가 돌아오고, 공조가 전환됩니다."
        tvPhaseLabel.text = "각성 2 · 자세 복귀"
        try { val seat = vehicle.getSeat() } catch (e: Exception) { }
        try { val hvac = vehicle.getHVAC() } catch (e: Exception) { }

        if (!isMuted) mediaPlayer?.setVolume(0.2f, 0.2f)  // 뮤트 중이면 볼륨 건드리지 않음

        ttsPlayer = MediaPlayer.create(this, R.raw.tts_wake)
        ttsPlayer?.setOnCompletionListener {
            if (!isMuted) mediaPlayer?.setVolume(1f, 1f)  // 뮤트 중이면 복구 안 함
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
        try { val door = vehicle.getDoor() } catch (e: Exception) { }
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