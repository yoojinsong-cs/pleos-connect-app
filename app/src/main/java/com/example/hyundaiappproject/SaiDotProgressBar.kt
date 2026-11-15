package com.example.hyundaiappproject

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** 선과 원을 한 Canvas에서 직접 그려 OEM ProgressBar 테마와 무관하게 끝점이 일치한다. */
class SaiDotProgressBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    var accent: Int = Color.rgb(220,198,171)
        set(value) { field = value; invalidate() }
    var max: Int = 180
        set(value) { field = value.coerceAtLeast(1); invalidate() }
    var progress: Int = 0
        set(value) { field = value.coerceIn(0,max); invalidate() }
    init { setWillNotDraw(false); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = minOf(5f*density, height/2f)
        val left = paddingLeft+radius
        val right = (width-paddingRight-radius).coerceAtLeast(left)
        val fraction = (progress.toFloat()/max).coerceIn(0f,1f)
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val start = if (rtl) right else left
        val end = if (rtl) right-(right-left)*fraction else left+(right-left)*fraction
        val y = height/2f
        paint.style = Paint.Style.STROKE; paint.strokeWidth=3f*density; paint.strokeCap=Paint.Cap.ROUND
        paint.color=Color.rgb(61,63,68); canvas.drawLine(left,y,right,y,paint)
        paint.color=accent; canvas.drawLine(start,y,end,y,paint)
        paint.style=Paint.Style.FILL; canvas.drawCircle(end,y,radius,paint)
    }
}
