package com.example.hyundai_first_project

// ─────────────────────────────────────────────
// [릴렉스 담당] SingingBowlView.kt  ─ 점으로 그린 싱잉볼 그림 (SAI 목업 모양)
// strike() 를 부르면 채가 볼을 치고 물결이 퍼져요.
// 색은 강조색 DCC6AB 하나만 써요. 크기·점 개수는 아래 숫자로 바꿀 수 있어요.
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class SingingBowlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ===== 바꿔도 되는 값 =====
    private val accent = Color.parseColor("#DCC6AB")   // 강조색
    private val bowlRows = 12                           // 볼 몸통 점 줄 수
    private val ringCount = 4                           // 바닥 물결 개수

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val dp = resources.displayMetrics.density
    private var pulse = 0f        // 칠 때 1 → 0 으로 줄어요
    private var drift = 0f        // 천천히 도는 반짝임
    private var strikeAnim: ValueAnimator? = null

    private val idle = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 12000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            drift = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        idle.start()
    }

    override fun onDetachedFromWindow() {
        idle.cancel()
        strikeAnim?.cancel()
        super.onDetachedFromWindow()
    }

    // 싱잉볼 치기 (그림만. 소리는 host.sounds.strikeBowl())
    fun strike() {
        strikeAnim?.cancel()
        strikeAnim = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 3500
            addUpdateListener {
                pulse = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun dot(canvas: Canvas, x: Float, y: Float, radius: Float, alpha: Float) {
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        canvas.drawCircle(x, y, radius, paint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val r = min(w * 0.22f, h * 0.40f)     // 볼 반지름
        val rimY = h * 0.40f                   // 볼 입구 높이
        val depth = r * 0.80f                  // 볼 깊이
        val tilt = 0.22f                       // 위에서 내려다보는 기울기
        val baseY = rimY + depth * 0.80f       // 물결이 퍼지는 바닥
        val spin = drift * 2 * PI

        // ① 바닥 물결 (점선 타원)
        for (k in 0 until ringCount) {
            val wave = ((drift * 2 + k * 0.25f) % 1f) * 0.06f
            val rx = r * (1.30f + k * 0.40f) * (1f + 0.10f * pulse + wave)
            val ry = rx * 0.26f
            val n = 70 + k * 26
            val base = 0.50f - k * 0.10f + 0.35f * pulse
            for (i in 0 until n) {
                val p = 2 * PI * i / n + spin * 0.3
                val front = sin(p).toFloat()
                dot(canvas, cx + rx * cos(p).toFloat(), baseY + ry * front, 1.3f * dp, base * (0.55f + 0.45f * max(front, 0f)))
            }
        }

        // ② 볼 몸통 (입구에서 바닥까지 점 줄)
        for (j in 0..bowlRows) {
            val t = j.toFloat() / bowlRows
            val theta = t * PI / 2
            val rowR = r * cos(theta).toFloat()
            val rowY = rimY + depth * sin(theta).toFloat()
            val n = max(10, (64 * cos(theta)).toInt())
            for (i in 0 until n) {
                val p = 2 * PI * i / n + spin / n
                val front = sin(p).toFloat()
                if (j > 0 && front < -0.05f) continue          // 볼 안쪽(뒷면)은 입구 줄만 그려요
                val glow = 0.35f + 0.65f * (1f - t)
                val size = (1.2f + 1.6f * (1f - t)) * dp * (1f + 0.2f * pulse)
                val alpha = glow * (0.45f + 0.55f * max(front, 0f)) + 0.25f * pulse
                dot(canvas, cx + rowR * cos(p).toFloat(), rowY + rowR * tilt * front, size, alpha)
            }
        }

        // ③ 채 (점선 + 둥근 머리). 칠 때 볼 쪽으로 다가가요
        val hit = pulse * pulse * pulse
        val topX = cx + r * 1.30f
        val topY = rimY - r * 1.05f
        val headX = cx + r * (0.92f - 0.05f * hit)
        val headY = rimY - r * (0.48f - 0.38f * hit)
        val steps = 12
        for (i in 0 until steps) {
            val f = i.toFloat() / steps
            dot(canvas, topX + (headX - topX) * f, topY + (headY - topY) * f, 1.6f * dp, 0.35f + 0.5f * f)
        }
        dot(canvas, headX, headY, 6f * dp, 1f)
    }
}
