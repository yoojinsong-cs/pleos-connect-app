package com.example.hyundai_first_project

// ─────────────────────────────────────────────
// [공용] RestMode.kt  ─ 3개 모드가 지키는 "약속"
// 모드 파일은 RestMode를 따르고, start()에서 받은 host로 차·소리·음성을 써요.
// ─────────────────────────────────────────────
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

interface RestMode {
    fun start(host: ModeHost)   // 모드 시작: 화면을 host.container에 넣고 시작
    fun stop()                  // 모드 강제 종료: 타이머·소리 정리 (차량 복원은 MainActivity가 해요)
}

class ModeHost(
    val activity: AppCompatActivity,                    // 화면(레이아웃) 만들 때 필요
    val container: FrameLayout,                         // 모드 화면이 들어갈 자리
    val car: CarControl,                                // 시트·조명·공조 바꾸기
    val sounds: Sounds,                                 // 싱잉볼·자연 소리
    val driverName: String,                             // 운전자 이름 (붕붕이)
    private val speaker: (String, (() -> Unit)?) -> Unit,
    private val finisher: (String) -> Unit
) {
    // 말하기. then을 주면 말이 끝난 뒤 실행돼요.  예) host.speak("안녕") { 다음단계() }
    fun speak(text: String, then: (() -> Unit)? = null) = speaker(text, then)

    // 모드 끝내기: 차량 설정을 되돌리고 첫 화면으로 가서 message를 말해요.
    fun finish(message: String) = finisher(message)
}

// 둥근 모서리 + 배경색.  예) view.round("#FFFFFF", 16f)
fun View.round(color: String, radiusDp: Float, stroke: String? = null) {
    val density = resources.displayMetrics.density
    background = GradientDrawable().apply {
        setColor(Color.parseColor(color))
        cornerRadius = radiusDp * density
        if (stroke != null) setStroke((1 * density).toInt(), Color.parseColor(stroke))
    }
}
