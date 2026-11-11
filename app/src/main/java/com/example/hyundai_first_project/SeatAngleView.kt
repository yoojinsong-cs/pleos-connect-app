package com.example.hyundai_first_project

// ─────────────────────────────────────────────
// [릴렉스 담당] SeatAngleView.kt  ─ 등받이 각도가 바뀌는 모습을 옆에서 본 그림 (새 파일)
// 점으로 그린 시트(앉는 곳 + 등받이 + 머리받침)가 지금 각도에서 목표 각도로 천천히 눕혀져요.
// 쓰는 법: seatView.animate(105.0, 117.0)   ← 105°에서 117°로 (화면에 숫자는 안 나와요)
// 각도는 앉는 곳과 등받이 사이 각도예요 (90° = 똑바로, 클수록 눕혀짐).
// ─────────────────────────────────────────────
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class SeatAngleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val beige = Color.parseColor("#DCC6AB")
    private val white = Color.parseColor("#F6F6F7")
    private val dp = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = beige
        strokeCap = Paint.Cap.ROUND
    }

    private var from = 105.0
    private var to = 117.0
    private var angle = 105.0               // 지금 그리는 각도
    private var animator: ValueAnimator? = null

    // from° 에서 to° 로 천천히 움직이기
    fun animate(fromDegrees: Double, toDegrees: Double, millis: Long = 2500) {
        from = fromDegrees
        to = toDegrees
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = millis
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                angle = from + (to - from) * (it.animatedValue as Float)
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun dot(canvas: Canvas, x: Float, y: Float, r: Float, color: Int, alpha: Float) {
        paint.color = color
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        canvas.drawCircle(x, y, r, paint)
    }

    // (x1,y1) → (x2,y2) 를 점선처럼 점으로
    private fun dottedLine(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, count: Int, r: Float, color: Int, alpha: Float) {
        for (i in 0..count) {
            val f = i / count.toFloat()
            dot(canvas, x1 + (x2 - x1) * f, y1 + (y2 - y1) * f, r, color, alpha)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val unit = min(w * 0.5f, h * 1.1f) / 100f               // 그림 크기 기준
        val hipX = w * 0.42f                               // 엉덩이(등받이 회전 중심)
        val hipY = h * 0.74f

        // 바닥 · 시트 다리
        dottedLine(canvas, hipX - 30 * unit, hipY + 22 * unit, hipX + 55 * unit, hipY + 22 * unit, 40, 1.2f * dp, white, 0.25f)
        dottedLine(canvas, hipX + 5 * unit, hipY + 6 * unit, hipX + 5 * unit, hipY + 22 * unit, 6, 1.6f * dp, white, 0.35f)

        // 앉는 곳 (앞으로 쭉)
        for (k in 0..2) {
            dottedLine(canvas, hipX, hipY - k * 3 * unit, hipX + 48 * unit, hipY - 4 * unit - k * 3 * unit, 22, 2.2f * dp, beige, 0.9f - k * 0.25f)
        }

        // 처음 각도 (흐린 점선으로 남겨 두기)
        drawBack(canvas, hipX, hipY, unit, from, 0.22f)
        // 지금 각도
        drawBack(canvas, hipX, hipY, unit, angle, 1f)

        // 각도 호 (앉는 곳 ↔ 등받이 사이)
        arcPaint.strokeWidth = 3 * dp
        val r = 22 * unit
        val rect = RectF(hipX - r, hipY - r, hipX + r, hipY + r)
        // 화면 각도: 0° = 오른쪽, 위쪽은 마이너스. 등받이 방향 = -angle°, 앉는 곳 ≈ -5°
        arcPaint.alpha = 200
        canvas.drawArc(rect, (-angle).toFloat(), (angle - 5).toFloat(), false, arcPaint)

        // (각도 숫자는 이용자에게 보여 주지 않아요)
    }

    // 등받이 + 머리받침
    private fun drawBack(canvas: Canvas, hipX: Float, hipY: Float, unit: Float, degrees: Double, alpha: Float) {
        // 90° = 똑바로 위, 클수록 뒤(왼쪽)로 눕혀져요
        val tilt = Math.toRadians(degrees - 90.0)
        val dx = -sin(tilt).toFloat()
        val dy = -cos(tilt).toFloat()
        val length = 50 * unit
        for (k in 0..2) {
            val ox = dy * k * 3 * unit          // 등받이 두께 (뒤쪽으로 줄 3개)
            val oy = -dx * k * 3 * unit
            dottedLine(canvas, hipX + ox, hipY + oy, hipX + ox + dx * length, hipY + oy + dy * length, 26, 2.2f * dp, beige, alpha * (0.9f - k * 0.25f))
        }
        // 머리받침
        val headX = hipX + dx * (length + 8 * unit)
        val headY = hipY + dy * (length + 8 * unit)
        dot(canvas, headX, headY, 8 * dp, beige, alpha * 0.35f)
        dot(canvas, headX, headY, 5 * dp, white, alpha * 0.9f)
    }
}
