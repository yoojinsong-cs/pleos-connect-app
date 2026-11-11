package com.example.hyundai_first_project

// ─────────────────────────────────────────────
// [릴렉스 담당] ParticleView.kt  ─ 점(입자)으로 그리는 움직이는 그림 (v11)
// SWIRL    : 기본 · 보라색 소용돌이 (10초마다 커졌다 작아짐 = 호흡)
// RAIN     : 비   · 파란 빗줄기가 내리고 바닥에 물방울이 퍼져요
// CAMPFIRE : 캠핑 · 장작 위로 불꽃 점이 피어오르고 불씨가 날려요
// SPACE    : 우주 · 반짝이는 별 + 천천히 도는 은하
// OCEAN    : 바다 · 점으로 된 물결이 출렁여요
// FOREST   : 숲   · 반딧불이가 떠다니며 깜빡여요
// SPHERE   : 세션 완료 · 베이지·보라 점 구슬
// 쓰는 법: particles.style = ParticleView.Style.RAIN
// ─────────────────────────────────────────────
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class ParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Style { SWIRL, RAIN, CAMPFIRE, SPACE, OCEAN, FOREST, SPHERE }

    // ===== 바꿔도 되는 값 (색) =====
    private val lavender = Color.parseColor("#A9A2E8")
    private val beige = Color.parseColor("#DCC6AB")
    private val rainBlue = Color.parseColor("#8FB4FF")
    private val flameYellow = Color.parseColor("#FFD59A")
    private val flameOrange = Color.parseColor("#FF8A4C")
    private val starWhite = Color.parseColor("#F6F6F7")
    private val oceanCyan = Color.parseColor("#7FD6E6")
    private val fireflyGreen = Color.parseColor("#D6EE8A")

    var style = Style.SWIRL
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dp = resources.displayMetrics.density
    private val startTime = SystemClock.uptimeMillis()

    // 입자마다 미리 정해 둔 무작위 값 (매번 같은 모양이 나오게 고정)
    private val seeds = Random(42).let { r -> FloatArray(800) { r.nextFloat() } }
    private fun seed(i: Int) = seeds[i % seeds.size]

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
        if (w <= 0f || h <= 0f) return
        val t = (SystemClock.uptimeMillis() - startTime) / 1000f
        when (style) {
            Style.SWIRL -> drawSwirl(canvas, w, h, t)
            Style.RAIN -> drawRain(canvas, w, h, t)
            Style.CAMPFIRE -> drawCampfire(canvas, w, h, t)
            Style.SPACE -> drawSpace(canvas, w, h, t)
            Style.OCEAN -> drawOcean(canvas, w, h, t)
            Style.FOREST -> drawForest(canvas, w, h, t)
            Style.SPHERE -> drawSphere(canvas, w, h, t)
        }
    }

    // 10초마다 0 → 1 → 0 (들숨·날숨)
    private fun breath(t: Float) = (1 - cos(t / 10f * 2 * PI)).toFloat() / 2f

    // ── 기본: 소용돌이 ──
    private fun drawSwirl(canvas: Canvas, w: Float, h: Float, t: Float) {
        val cx = w / 2; val cy = h / 2
        val size = min(w, h) / 2 * 0.9f
        val scale = 0.88f + 0.12f * breath(t)
        val spin = t * 0.08f
        for (a in 0 until 12) {
            val base = a * (2 * PI / 12).toFloat() + spin
            for (i in 1..11) {
                val f = i / 11f
                val r = size * scale * (0.12f + 0.88f * f)
                val angle = base + f * 1.6f
                dot(canvas, cx + r * cos(angle), cy + r * sin(angle), (1.5f + 3.5f * (1 - f)) * dp, lavender, 0.25f + 0.75f * (1 - f))
            }
        }
        for (i in 0 until 10) {
            val angle = i * (2 * PI / 10).toFloat() - spin * 2
            val r = size * 0.06f * scale
            dot(canvas, cx + r * cos(angle), cy + r * sin(angle), 3f * dp, lavender, 0.9f)
        }
        dot(canvas, cx, cy, 5f * dp, lavender, 1f)
    }

    // ── 비: 점선 빗줄기 + 바닥 물방울 ──
    private fun drawRain(canvas: Canvas, w: Float, h: Float, t: Float) {
        val ground = h * 0.86f
        for (i in 0 until 130) {
            val x = seed(i) * w
            val speed = 0.55f + seed(i + 200) * 0.6f                  // 1초에 화면 몇 번
            val p = (t * speed + seed(i + 400)) % 1f
            val y = -h * 0.1f + p * (ground + h * 0.1f)
            val near = 0.4f + seed(i + 600) * 0.6f                    // 가까운 비는 크고 진하게
            for (k in 0 until 4) {
                dot(canvas, x, y - k * 9f * dp * near, (1.1f + near) * dp, rainBlue, (0.25f + 0.55f * near) * (1f - k * 0.22f))
            }
        }
        // 바닥에 떨어진 물방울이 동그랗게 퍼지는 모습
        for (i in 0 until 14) {
            val period = 1.6f + seed(i + 50) * 1.4f
            val p = ((t + seed(i + 90) * period) % period) / period   // 0 → 1
            val cx = seed(i + 130) * w
            val cy = ground + (seed(i + 170) - 0.5f) * h * 0.08f
            val rx = (10f + 46f * p) * dp
            val ry = rx * 0.25f
            for (k in 0 until 18) {
                val a = k * (2 * PI / 18).toFloat()
                dot(canvas, cx + rx * cos(a), cy + ry * sin(a), 1.2f * dp, rainBlue, 0.55f * (1 - p))
            }
        }
    }

    // ── 캠핑: 장작 + 불꽃 + 불씨 ──
    private fun drawCampfire(canvas: Canvas, w: Float, h: Float, t: Float) {
        val cx = w / 2
        val base = h * 0.78f
        val size = min(w, h)
        // 밤하늘 별 몇 개
        for (i in 0 until 40) {
            val tw = 0.5f + 0.5f * sin(t * (1 + seed(i)) * 2 + i)
            dot(canvas, seed(i + 300) * w, seed(i + 340) * h * 0.45f, 1.2f * dp, starWhite, 0.15f + 0.35f * tw)
        }
        // 장작 두 개 (X자)
        for (k in 0..24) {
            val f = k / 24f - 0.5f
            dot(canvas, cx + f * size * 0.42f, base + f * size * 0.07f, 2.4f * dp, beige, 0.6f)
            dot(canvas, cx + f * size * 0.42f, base - f * size * 0.07f, 2.4f * dp, beige, 0.6f)
        }
        // 불꽃: 아래에서 피어올라 흔들리며 작아져요
        for (i in 0 until 170) {
            val life = 1.2f + seed(i) * 1.0f
            val p = ((t + seed(i + 100) * life) % life) / life        // 0(태어남) → 1(사라짐)
            val spread = (seed(i + 200) - 0.5f) * size * 0.22f * (1 - p)
            val sway = sin(t * 3 + i) * size * 0.015f * p
            val x = cx + spread + sway
            val y = base - p * size * (0.35f + 0.2f * seed(i + 300))
            val color = if (p < 0.45f) flameYellow else flameOrange
            dot(canvas, x, y, (1.5f + 4.5f * (1 - p)) * dp, color, (1 - p) * 0.85f)
        }
        // 불씨: 가끔 높이 날아올라요
        for (i in 0 until 18) {
            val life = 3f + seed(i + 500) * 2f
            val p = ((t + seed(i + 520) * life) % life) / life
            val x = cx + (seed(i + 540) - 0.5f) * size * 0.2f + sin(t * 1.5f + i) * size * 0.06f * p
            val y = base - size * 0.2f - p * size * 0.55f
            dot(canvas, x, y, 1.6f * dp, flameOrange, (1 - p) * 0.9f)
        }
    }

    // ── 우주: 별 + 은하 ──
    private fun drawSpace(canvas: Canvas, w: Float, h: Float, t: Float) {
        for (i in 0 until 160) {
            val tw = 0.5f + 0.5f * sin(t * (0.6f + seed(i) * 1.8f) + i * 1.3f)
            val size = (0.8f + seed(i + 160) * 1.6f) * dp
            dot(canvas, seed(i + 320) * w, seed(i + 480) * h, size, starWhite, 0.15f + 0.7f * tw * seed(i + 640))
        }
        val cx = w / 2; val cy = h / 2
        val size = min(w, h) * 0.42f
        val spin = t * 0.06f
        for (arm in 0 until 2) {
            for (i in 0 until 90) {
                val f = i / 90f
                val angle = spin + arm * PI.toFloat() + f * 4.2f + (seed(i + arm * 90) - 0.5f) * 0.35f
                val r = size * (0.05f + 0.95f * f)
                val x = cx + r * cos(angle)
                val y = cy + r * sin(angle) * 0.55f                      // 비스듬히 누운 은하
                val color = if (i % 3 == 0) starWhite else lavender
                dot(canvas, x, y, (1.2f + 2.6f * (1 - f)) * dp, color, 0.25f + 0.65f * (1 - f))
            }
        }
        dot(canvas, cx, cy, 10f * dp, lavender, 0.25f)
        dot(canvas, cx, cy, 5f * dp, starWhite, 0.9f)
    }

    // ── 바다: 점 물결 (멀리 있는 줄은 작고 흐리게) ──
    private fun drawOcean(canvas: Canvas, w: Float, h: Float, t: Float) {
        // 달
        dot(canvas, w * 0.72f, h * 0.18f, 22f * dp, starWhite, 0.12f)
        dot(canvas, w * 0.72f, h * 0.18f, 12f * dp, starWhite, 0.8f)
        val rows = 14
        val cols = 46
        for (j in 0 until rows) {
            val depth = j / (rows - 1f)                                  // 0 먼 곳 → 1 가까운 곳
            val y0 = h * (0.42f + 0.55f * depth * depth)
            val amp = (4f + 18f * depth) * dp
            for (i in 0 until cols) {
                val fx = i / (cols - 1f)
                val x = w * (0.5f + (fx - 0.5f) * (0.7f + 0.5f * depth))
                val wave = sin(fx * 9f - t * 1.1f + j * 0.6f) + 0.5f * sin(fx * 17f + t * 0.7f)
                val y = y0 + wave * amp
                dot(canvas, x, y, (0.9f + 2.4f * depth) * dp, oceanCyan, 0.2f + 0.6f * depth * (0.6f + 0.4f * (wave + 1.5f) / 3f))
            }
        }
    }

    // ── 숲: 반딧불이 ──
    private fun drawForest(canvas: Canvas, w: Float, h: Float, t: Float) {
        // 나무 기둥 (양쪽에 점으로)
        val trunks = floatArrayOf(0.08f, 0.2f, 0.82f, 0.93f)
        trunks.forEachIndexed { k, fx ->
            for (i in 0 until 30) {
                val y = h * (i / 30f)
                dot(canvas, w * fx + sin(i * 0.7f + k) * 2f * dp, y, (1.6f + k % 2) * dp, beige, 0.18f)
            }
        }
        // 바닥 풀
        for (i in 0 until 90) {
            dot(canvas, seed(i + 700) * w, h * (0.9f + seed(i + 740) * 0.1f), 1.3f * dp, fireflyGreen, 0.15f)
        }
        // 반딧불이: 천천히 떠다니며 깜빡깜빡
        for (i in 0 until 60) {
            val bx = seed(i) * w
            val by = h * (0.2f + seed(i + 60) * 0.65f)
            val x = bx + sin(t * (0.2f + seed(i + 120) * 0.3f) + i) * w * 0.05f
            val y = by + cos(t * (0.15f + seed(i + 180) * 0.25f) + i * 2) * h * 0.06f
            val blink = (0.5f + 0.5f * sin(t * (0.8f + seed(i + 240)) + i * 3)).let { it * it }
            dot(canvas, x, y, 9f * dp, fireflyGreen, 0.12f * blink)
            dot(canvas, x, y, 3f * dp, fireflyGreen, 0.25f + 0.75f * blink)
        }
    }

    // ── 세션 완료: 점 구슬 ──
    private fun drawSphere(canvas: Canvas, w: Float, h: Float, t: Float) {
        val cx = w / 2; val cy = h / 2
        val size = min(w, h) / 2 * 0.9f
        val spin = t * 0.3f
        val tilt = 0.35f
        val golden = PI.toFloat() * (3 - sqrt(5f))
        val points = 140
        for (i in 0 until points) {
            val y0 = 1 - 2 * (i + 0.5f) / points
            val ring = sqrt(1 - y0 * y0)
            val theta = golden * i + spin
            val x0 = ring * cos(theta)
            val z0 = ring * sin(theta)
            val y = y0 * cos(tilt) - z0 * sin(tilt)
            val z = y0 * sin(tilt) + z0 * cos(tilt)
            val depth = (z + 1) / 2
            val color = if (i % 2 == 0) beige else lavender
            dot(canvas, cx + x0 * size, cy + y * size, (1.5f + 3f * depth) * dp, color, 0.15f + 0.85f * depth)
        }
    }
}
