package com.example.hyundai_first_project

// ─────────────────────────────────────────────
// [릴렉스 담당] SingingBowlView.kt  ─ 점으로 그린 싱잉볼 (v11, 레퍼런스 그림처럼)
// · 반구 모양 그릇을 점 줄(가로 줄 + 세로 줄)로 그려요
// · 오른쪽 위에 점으로 된 채(말렛), 바닥에 점 물결이 퍼져요
// · strike() 를 부르면 채가 그릇을 "탁" 치고, 그릇이 밝아지며 물결이 밖으로 퍼져요
// 소리는 host.sounds.strikeBowl() 이 따로 내요.
// ─────────────────────────────────────────────
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
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
    private val gold = Color.parseColor("#DCC6AB")        // 점 색 (강조색)
    private val bright = Color.parseColor("#FFF1DC")      // 가장 밝은 점
    private val rows = 13                                 // 그릇 가로 줄 수
    private val columns = 64                              // 그릇 세로 줄 수 (한 바퀴 점 개수)
    private val ripples = 5                               // 바닥 물결 개수
    private val tilt = 0.26f                              // 위에서 내려다보는 정도 (0 = 옆에서)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dp = resources.displayMetrics.density
    private val startTime = SystemClock.uptimeMillis()
    private var strikeAt = -100000L                       // 마지막으로 친 시각

    private val ticker = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { invalidate() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ticker.start()
    }

    override fun onDetachedFromWindow() {
        ticker.cancel()
        super.onDetachedFromWindow()
    }

    // 싱잉볼 치기 (그림만)
    fun strike() {
        strikeAt = SystemClock.uptimeMillis()
    }

    private fun dot(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int, alpha: Float) {
        if (alpha <= 0.01f) return
        paint.color = color
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        canvas.drawCircle(x, y, radius, paint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val now = SystemClock.uptimeMillis()
        val seconds = (now - startTime) / 1000f
        val sinceStrike = (now - strikeAt) / 1000f
        val ring = if (sinceStrike in 0f..6f) exp01(sinceStrike, 1.6f) else 0f      // 1 → 0 울림 세기

        // 그릇 크기와 위치
        val radius = min(w * 0.30f, h * 0.44f)            // 그릇 반지름
        val depth = radius * 0.78f                         // 그릇 깊이
        val cx = w * 0.47f
        val rimY = h * 0.42f
        val baseY = rimY + depth

        // ① 바닥 빛 (은은한 원)
        val glowRadius = radius * (1.5f + 0.2f * ring)
        glowPaint.shader = RadialGradient(cx, baseY, glowRadius,
            Color.argb((60 + 70 * ring).toInt(), 220, 198, 171), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.save()
        canvas.scale(1f, tilt * 1.6f, cx, baseY)
        canvas.drawCircle(cx, baseY, glowRadius, glowPaint)
        canvas.restore()

        // ② 바닥 물결: 점으로 된 타원 여러 겹. 치면 밖으로 퍼져요.
        val spread = if (sinceStrike in 0f..6f) sinceStrike / 6f else 0f
        for (r in 0 until ripples) {
            val base = (r + 1f) / ripples                                  // 0.2 ~ 1
            val move = (base + spread * 0.6f) % 1.2f
            val rx = radius * (1.05f + move * 1.35f)
            val ry = rx * tilt
            val count = (90 + r * 30)
            val fade = (1f - move / 1.2f) * (0.35f + 0.5f * ring)
            for (i in 0 until count) {
                val a = i * (2 * PI / count).toFloat() + seconds * 0.05f
                val x = cx + rx * cos(a)
                val y = baseY + ry * sin(a)
                // 그릇 뒤쪽(가려지는 곳)은 그리지 않아요
                if (sin(a) < 0 && abs1(cos(a)) * rx < radius * 0.95f) continue
                dot(canvas, x, y, 1.3f * dp, gold, fade * (0.5f + 0.5f * sin(a * 3 + seconds)))
            }
        }

        // ③ 그릇: 가로 줄(위 테두리 → 바닥) × 세로 줄
        for (j in 0..rows) {
            val phi = (j.toFloat() / rows) * (PI / 2).toFloat()          // 0 = 테두리, 90° = 바닥
            val rx = radius * cos(phi)
            val ry = rx * tilt
            val y0 = rimY + depth * sin(phi)
            val shimmer = 0.5f + 0.5f * sin(seconds * 1.2f - j * 0.5f)
            for (i in 0 until columns) {
                val a = i * (2 * PI / columns).toFloat()
                val front = sin(a)                                         // + 앞쪽, - 뒤쪽
                if (j > 0 && front < 0) continue                           // 몸통은 앞쪽만 보여요
                val x = cx + rx * cos(a)
                val y = y0 + ry * front
                val edge = 0.35f + 0.65f * max(0f, front)                  // 가운데가 더 밝게
                val bottomFade = 1f - j.toFloat() / (rows + 2)
                var alpha = edge * bottomFade * (0.55f + 0.25f * shimmer + 0.35f * ring)
                var size = (1.6f + 1.8f * edge * bottomFade) * dp
                var color = gold
                if (j == 0) {                                              // 테두리: 가장 밝고 크게
                    val wave = 0.5f + 0.5f * sin(a * 2 - seconds * 2.5f)  // 테두리를 따라 도는 반짝임
                    alpha = if (front < 0) 0.45f + 0.2f * wave else 0.85f + 0.15f * wave
                    alpha += 0.3f * ring
                    size = (if (front < 0) 1.8f else 2.8f) * dp * (1f + 0.25f * ring)
                    color = bright
                    if (front >= 0) dot(canvas, x, y, size * 2.2f, gold, 0.12f + 0.15f * ring)   // 테두리 빛번짐
                }
                dot(canvas, x, y, size, color, alpha)
            }
        }

        // ④ 채(말렛): 오른쪽 위에서 비스듬히. 치는 순간 그릇 쪽으로 다가가요.
        val hit = when {
            sinceStrike < 0f -> 0f
            sinceStrike < 0.15f -> sinceStrike / 0.15f                  // 다가가기
            sinceStrike < 1.2f -> 1f - (sinceStrike - 0.15f) / 1.05f   // 돌아오기
            else -> 0f
        }
        val sway = 0.015f * sin(seconds * 0.8f)
        val ballX = cx + radius * (1.15f - 0.13f * hit + sway)
        val ballY = rimY - radius * (0.36f - 0.33f * hit)
        val angle = (-55 * PI / 180).toFloat()
        val stick = radius * 0.55f
        for (k in 1..16) {
            val f = k / 16f
            dot(canvas, ballX + stick * f * cos(angle), ballY + stick * f * sin(angle), 1.6f * dp, gold, 0.9f - 0.5f * f)
        }
        dot(canvas, ballX, ballY, 14f * dp, gold, 0.15f)
        dot(canvas, ballX, ballY, 8f * dp, bright, 0.95f)
    }

    private fun exp01(t: Float, speed: Float) = kotlin.math.exp(-speed * t)
    private fun abs1(v: Float) = if (v < 0) -v else v
}
