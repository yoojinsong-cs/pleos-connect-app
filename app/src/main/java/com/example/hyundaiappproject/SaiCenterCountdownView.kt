package com.example.hyundaiappproject

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.*

/** Full-host overlay: the numeric center is the center of the app display, including its header. */
class SaiCenterCountdownView(context:Context):View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring=RectF()
    private var remainingMs=3000L
    private var updatedAt=0L
    private var playing=false
    private var lastAnnounced=0
    private val accent=Color.rgb(151,198,169)
    private val density get()=resources.displayMetrics.density

    init { visibility=GONE; isClickable=false; isFocusable=false; importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun present(remaining:Long,running:Boolean) {
        remainingMs=remaining.coerceIn(0L,3000L)
        updatedAt=SystemClock.elapsedRealtime(); playing=running
        visibility=VISIBLE
        val digit=ceil(remainingMs/1000.0).toInt().coerceIn(1,3)
        if(digit!=lastAnnounced) { lastAnnounced=digit; announceForAccessibility(digit.toString()) }
        invalidate()
    }
    fun hide() { playing=false; visibility=GONE; lastAnnounced=0 }

    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val remaining=(remainingMs-if(playing) SystemClock.elapsedRealtime()-updatedAt else 0L).coerceAtLeast(0L)
        if(remaining==0L || width<=0 || height<=0) return
        val cx=width/2f; val cy=height/2f
        val radius=minOf(68f*density,minOf(width,height)*.16f)
        val fraction=(((remaining-1)%1000)+1)/1000f
        val pulse=1f+.035f*(fraction*fraction)
        // Subtle green light echoes the relax accent without filling the screen.
        paint.style=Paint.Style.FILL
        paint.shader=RadialGradient(cx,cy,radius*1.55f,intArrayOf(Color.argb(23,151,198,169),Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
        canvas.drawCircle(cx,cy,radius*1.55f,paint); paint.shader=null
        paint.style=Paint.Style.STROKE; paint.strokeWidth=2.5f*density
        paint.strokeCap=Paint.Cap.ROUND; paint.color=Color.rgb(53,65,61)
        canvas.drawCircle(cx,cy,radius,paint)
        ring.set(cx-radius,cy-radius,cx+radius,cy+radius)
        paint.color=accent; canvas.drawArc(ring,-90f,360f*fraction,false,paint)
        val angle=(-90f+360f*fraction)*PI/180
        paint.style=Paint.Style.FILL
        canvas.drawCircle(cx+cos(angle).toFloat()*radius,cy+sin(angle).toFloat()*radius,3f*density,paint)
        paint.typeface=Typeface.create("sans-serif-light",Typeface.NORMAL)
        paint.textSize=radius*.84f*pulse; paint.textAlign=Paint.Align.CENTER; paint.color=Color.rgb(242,245,243)
        val fm=paint.fontMetrics
        val digit=ceil(remaining/1000.0).toInt().coerceIn(1,3).toString()
        canvas.drawText(digit,cx,cy-(fm.ascent+fm.descent)/2f,paint)
        paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL)
        paint.textSize=minOf(15f*density,radius*.25f); paint.color=Color.rgb(171,179,175)
        canvas.drawText("잠시 후 시작해요",cx,cy+radius+28f*density,paint)
        if(playing && isShown) postInvalidateOnAnimation()
    }
}
