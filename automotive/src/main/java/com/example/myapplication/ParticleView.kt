package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint().apply {
        isAntiAlias = true
        color = 0x88DCC6AB.toInt()
    }

    private data class Particle(
        var angle: Float,
        var radius: Float,
        var size: Float,
        var alpha: Float,
        var speed: Float
    )

    private val particles = mutableListOf<Particle>()
    private val handler = Handler(Looper.getMainLooper())
    private var tick = 0

    private val runnable = object : Runnable {
        override fun run() {
            tick++
            updateParticles()
            invalidate()
            handler.postDelayed(this, 50)
        }
    }

    init {
        repeat(80) {
            particles.add(
                Particle(
                    angle = Random.nextFloat() * 360f,
                    radius = Random.nextFloat() * 200f,
                    size = Random.nextFloat() * 6f + 2f,
                    alpha = Random.nextFloat(),
                    speed = Random.nextFloat() * 0.5f + 0.1f
                )
            )
        }
    }

    private fun updateParticles() {
        for (p in particles) {
            p.radius += p.speed
            p.alpha -= 0.01f
            if (p.radius > 220f || p.alpha <= 0f) {
                p.angle = Random.nextFloat() * 360f
                p.radius = Random.nextFloat() * 20f
                p.alpha = Random.nextFloat() * 0.8f + 0.2f
                p.speed = Random.nextFloat() * 0.5f + 0.1f
                p.size = Random.nextFloat() * 6f + 2f
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f

        for (p in particles) {
            val x = cx + cos(Math.toRadians(p.angle.toDouble())).toFloat() * p.radius
            val y = cy + sin(Math.toRadians(p.angle.toDouble())).toFloat() * p.radius
            paint.alpha = (p.alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, y, p.size, paint)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(runnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(runnable)
    }
}