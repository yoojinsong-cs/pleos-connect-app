package com.example.hyundai_first_project

// ─────────────────────────────────────────────
// [각성 담당] AwakeMode.kt  ─ 각성 모드 (지금은 빈 자리)
// RelaxMode.kt 를 참고해서 start() 안을 채우면 돼요.
// 이 파일과 mode_awake.xml 만 고치면 다른 사람 코드와 부딪히지 않아요.
// ─────────────────────────────────────────────
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View

class AwakeMode : RestMode {

    private lateinit var host: ModeHost
    private val handler = Handler(Looper.getMainLooper())

    override fun start(host: ModeHost) {
        this.host = host
        val root = LayoutInflater.from(host.activity).inflate(R.layout.mode_awake, host.container, true)
        val back = root.findViewById<View>(R.id.awakeBackButton)
        back.round("#00000000", 8f, "#6B7A90")
        back.setOnClickListener { host.finish("각성 모드를 마쳤어요.") }
        host.speak("각성 모드는 아직 준비 중이에요.")

        // 예시) 차량 바꾸기:  host.car.setTemperature(22f)  /  host.car.moveSeatBackrest(100.0)
        // 예시) 소리:        host.sounds.strikeBowl()  /  host.sounds.startLoop(Ambience.RAIN)
        // 예시) 끝내기:      host.finish("각성 모드를 마쳤어요.")
    }

    override fun stop() {
        handler.removeCallbacksAndMessages(null)
        host.sounds.stopLoop()
    }
}
