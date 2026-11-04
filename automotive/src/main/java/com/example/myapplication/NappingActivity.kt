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

    // ── 단계 플래그 (중복 실행 방지) ──────────────────────
    private var envDone    = false  // 0~1분: 환경 설정
    private var descentDone = false // 1~2분: 호흡 안내
    private var wake1Done  = false  // 17~19분: 각성 1
    private var wake2Done  = false  // 19~20분: 각성 2

    // ── 사운드 설정 (나중에 여기서 추가/변경) ─────────────
    private val NAPPING_SOUNDS = listOf(
        R.raw.sample   // 지금은 하나, 나중에 추가 가능
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

    // TTS 초기화 완료 콜백
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.KOREAN
        }
    }

    // ── 0:00 냅핑 시작 ───────────────────────────────────
    private fun applyNappingStart() {
        tvStatus.text = "냅핑 모드 시작"

        // 도어 잠금
        try {
            val door = vehicle.getDoor()
            // TODO: door.setDoorLock(전체, true) — API 준비 중
        } catch (e: Exception) { }

        // ASMR 재생 (루프)
        mediaPlayer = MediaPlayer.create(this, NAPPING_SOUNDS[0])
        mediaPlayer?.isLooping = true
        mediaPlayer?.start()
    }

    // ── 20분 타이머 ──────────────────────────────────────
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

    // ── 단계 분기 ────────────────────────────────────────
    private fun handlePhase(elapsedMs: Long) {
        val minutes = elapsedMs / 60_000L

        when {
            minutes < 1  -> { if (!envDone)     { envDone = true;     applyEnvironment() } }
            minutes < 2  -> { if (!descentDone) { descentDone = true; applyDescent() } }
            minutes < 17 -> { /* 수면 — 아무것도 안 함 */ }
            minutes < 19 -> {
                if (!wake1Done) { wake1Done = true; applyWake1() }
                applyLightRamp(elapsedMs)   // 매 초 조명 색온도 업데이트
            }
            else         -> { if (!wake2Done) { wake2Done = true; applyWake2() } }
        }
    }

    // ── 0~1분: 환경 설정 ─────────────────────────────────
    private fun applyEnvironment() {
        tvStatus.text = "환경 설정 중..."
        try {
            val seat = vehicle.getSeat()
            // TODO: seat.setReclineAngle(ROW_1_LEFT, 130)  — 등받이 130도
            // TODO: seat.setLumbarSupport(ROW_1_LEFT, MAX) — 요추 지지대
        } catch (e: Exception) { }
        try {
            val light = vehicle.getLight()
            // TODO: light.setAmbientLightOff()             — 완전 소등
        } catch (e: Exception) { }
        try {
            val hvac = vehicle.getHVAC()
            // TODO: hvac.setFanSpeed(1)                    — 풍량 1단
            // TODO: hvac.setAirCirculation(INTERNAL)       — 내기 순환
        } catch (e: Exception) { }
    }

    // ── 1~2분: 호흡 안내 후 조용히 ──────────────────────
    private fun applyDescent() {
        tvStatus.text = "호흡을 고르세요..."
        tts?.speak(
            "편안하게 숨을 들이쉬고, 천천히 내쉬세요. 이제 편히 쉬세요.",
            TextToSpeech.QUEUE_FLUSH, null, null
        )
    }

    // ── 17~19분: 조명 색온도 매 초 램프업 ───────────────
    private fun applyLightRamp(elapsedMs: Long) {
        val phaseMs  = elapsedMs - 17 * 60_000L          // 0 ~ 120,000ms
        val ratio    = phaseMs.toFloat() / 120_000f       // 0.0 ~ 1.0
        val colorTemp = (2500 + 4000 * ratio).toInt()     // 2500K ~ 6500K
        try {
            val light = vehicle.getLight()
            // TODO: light.setAmbientLightColorTemp(colorTemp)
        } catch (e: Exception) { }
    }

    // ── 17분: 각성 1 초기 설정 ──────────────────────────
    private fun applyWake1() {
        tvStatus.text = "기상 준비 중..."
        try {
            val hvac = vehicle.getHVAC()
            // TODO: hvac.setTargetTemperature(현재온도 - 2) — 온도 2도 하강
            // TODO: hvac.setAirCirculation(EXTERNAL)        — 외기 유입
        } catch (e: Exception) { }
    }

    // ── 19분: 각성 2 ─────────────────────────────────────
    private fun applyWake2() {
        tvStatus.text = "20분이 지났어요!"

        // 등받이 115도
        try {
            val seat = vehicle.getSeat()
            // TODO: seat.setReclineAngle(ROW_1_LEFT, 115)
            // TODO: seat.setEarMassage(ON)
        } catch (e: Exception) { }

        // 풍량 2단
        try {
            val hvac = vehicle.getHVAC()
            // TODO: hvac.setFanSpeed(2)
        } catch (e: Exception) { }

        // TTS 안내
        tts?.speak(
            "20분이 지났어요. 다음 일정 19시 30분입니다.",
            TextToSpeech.QUEUE_FLUSH, null, null
        )
    }

    // ── 종료 ─────────────────────────────────────────────
    private fun handleFinish() {
        tvStatus.text = "냅핑 모드 종료"
        mediaPlayer?.stop()

        try {
            val door = vehicle.getDoor()
            // TODO: door.setDoorLock(전체, false) — 도어 잠금 해제
        } catch (e: Exception) { }

        // 조명·공조는 원복하지 않음 (스펙대로)
        vehicle.release()
    }

    // ── 생명주기 정리 ────────────────────────────────────
    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        tts?.shutdown()
        mediaPlayer?.release()
        try { vehicle.release() } catch (e: Exception) { }
    }
}