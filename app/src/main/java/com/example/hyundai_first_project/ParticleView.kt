package com.example.hyundai_first_project

// ─────────────────────────────────────────────
// [릴렉스 담당] ParticleView.kt  ─ 점(입자)으로 그리는 그림 (새 파일)
// SWIRL  : 호흡 화면의 보라색 소용돌이. 10초마다 커졌다 작아져요 (들숨·날숨).
// SPHERE : 세션 완료 화면의 베이지·보라 점 구슬. 천천히 돌아요.
// 쓰는 법: XML에 com.example.hyundai_first_project.ParticleView 로 넣고
//          코드에서 particles.style = ParticleView.Style.SPHERE 처럼 바꿔요.
// ─────────────────────────────────────────────
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class ParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Style { SWIRL, SPHERE }

    // ===== 바꿔도 되는 값 =====
    private val lavender = Color.parseColor("#A9A2E8")   // 보라
    private val beige = Color.parseColor("#DCC6AB")      // 베이지(강조색)
    private val arms = 12                                // 소용돌이 팔 개수
    private val dotsPerArm = 11                          // 팔 하나의 점 개수
    private val spherePoints = 140                       // 구슬 점 개수
    private val breathMillis = 10000L                    // 한 번 숨 쉬는 시간 (10초)

    var style = Style.SWIRL
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dp = resources.displayMetrics.density
    private var time = 0f        // 0 → 1 이 60초에 한 번

    private val ticker = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 60000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            time = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ticker.start()
    }

    override fun onDetachedFromWindow() {
        ticker.cancel()
        super.onDetachedFromWindow()
    }

    private fun dot(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int, alpha: Float) {
        paint.color = color
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        canvas.drawCircle(x, y, radius, paint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val size = min(width, height) / 2f * 0.9f
        if (size <= 0f) return
        if (style == Style.SWIRL) drawSwirl(canvas, cx, cy, size) else drawSphere(canvas, cx, cy, size)
    }

    // 소용돌이: 가운데서 바깥으로 휘어져 나가는 점 줄
    private fun drawSwirl(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val seconds = time * 60f
        val breath = (1 - cos(seconds / (breathMillis / 1000f) * 2 * PI)).toFloat() / 2f   // 0 → 1 → 0
        val scale = 0.88f + 0.12f * breath
        val spin = seconds * 0.08f
        for (a in 0 until arms) {
            val base = a * (2 * PI / arms).toFloat() + spin
            for (i in 1..dotsPerArm) {
                val t = i / dotsPerArm.toFloat()                 // 0(안) → 1(밖)
                val r = size * scale * (0.12f + 0.88f * t)
                val angle = base + t * 1.6f                      // 바깥으로 갈수록 휘어요
                val x = cx + r * cos(angle)
                val y = cy + r * sin(angle)
                dot(canvas, x, y, (1.5f + 3.5f * (1 - t)) * dp, lavender, 0.25f + 0.75f * (1 - t))
            }
        }
        // 가운데 작은 덩어리
        for (i in 0 until 10) {
            val angle = i * (2 * PI / 10).toFloat() - spin * 2
            val r = size * 0.06f * scale
            dot(canvas, cx + r * cos(angle), cy + r * sin(angle), 3f * dp, lavender, 0.9f)
        }
        dot(canvas, cx, cy, 5f * dp, lavender, 1f)
    }

    // 구슬: 공 표면에 고르게 뿌린 점을 천천히 돌려요. 앞쪽 점은 진하고 크게.
    private fun drawSphere(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val spin = time * 2 * PI.toFloat() * 3      // 60초에 3바퀴
        val tilt = 0.35f
        val golden = PI.toFloat() * (3 - sqrt(5f))
        for (i in 0 until spherePoints) {
            val y0 = 1 - 2 * (i + 0.5f) / spherePoints   // 위(1) → 아래(-1)
            val ring = sqrt(1 - y0 * y0)
            val theta = golden * i + spin
            val x0 = ring * cos(theta)
            val z0 = ring * sin(theta)
            // 살짝 기울여서 입체감
            val y = y0 * cos(tilt) - z0 * sin(tilt)
            val z = y0 * sin(tilt) + z0 * cos(tilt)
            val depth = (z + 1) / 2                       // 0(뒤) → 1(앞)
            val color = if (i % 2 == 0) beige else lavender
            dot(canvas, cx + x0 * size, cy + y * size, (1.5f + 3f * depth) * dp, color, 0.15f + 0.85f * depth)
        }
        // 뒤에서 은은하게 빛나는 원
        paint.color = lavender
        paint.alpha = 18
        canvas.drawCircle(cx, cy, size * 0.75f, paint)
    }
}
