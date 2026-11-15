package com.example.hyundaiappproject

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaPlayer
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView

/** Owns only the temporary nap-to-awaken handoff; cancel always releases its resources. */
object SaiNapAwakenTransition {
    private var snapshot:Bitmap?=null
    private var inheritedCar:CarControl?=null
    private var audio:MediaPlayer?=null
    private var audioFade:ValueAnimator?=null
    private var outputMuted=false
    private var mask:ImageView?=null
    private var owner:ViewGroup?=null
    private var visualFade:ValueAnimator?=null
    private val layoutListener=View.OnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> layoutMask() }

    fun prepare(source:View?,player:MediaPlayer?,car:CarControl?,muted:Boolean) {
        cancel()
        inheritedCar=car; audio=player; outputMuted=muted
        if(source!=null && source.width>0 && source.height>0) runCatching {
            val factor=minOf(1f,1280f/source.width)
            val image=Bitmap.createBitmap((source.width*factor).toInt().coerceAtLeast(1),(source.height*factor).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
            val canvas=Canvas(image); canvas.scale(factor,factor); source.draw(canvas); snapshot=image
        }
    }
    fun setMuted(muted:Boolean) {
        outputMuted=muted
        val level=if(muted) 0f else (audioFade?.animatedValue as? Float ?: .35f)
        audio?.let { runCatching { it.setVolume(level,level) } }
    }
    private fun fadeOutAudio() {
        val player=audio?:return
        audioFade=ValueAnimator.ofFloat(.35f,0f).apply {
                duration=700L
                addUpdateListener { val v=if(outputMuted) 0f else it.animatedValue as Float; runCatching { player.setVolume(v,v) } }
                addListener(object:AnimatorListenerAdapter() { override fun onAnimationEnd(animation:Animator) { releaseAudio() } })
                start()
        }
    }
    fun takeCar():CarControl?=inheritedCar.also { inheritedCar=null }
    fun attach(host:ViewGroup) {
        val image=snapshot?:return
        owner=host
        mask=ImageView(host.context).apply { setImageBitmap(image); scaleType=ImageView.ScaleType.FIT_XY; importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        host.overlay.add(mask!!); host.addOnLayoutChangeListener(layoutListener); layoutMask()
    }
    private fun layoutMask() {
        val host=owner?:return; val image=mask?:return
        image.measure(View.MeasureSpec.makeMeasureSpec(host.width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(host.height,View.MeasureSpec.EXACTLY))
        image.layout(0,0,host.width,host.height)
    }
    fun revealAwaken() {
        fadeOutAudio()
        val image=mask
        if(image==null) { clearVisual(); return }
        visualFade?.cancel()
        visualFade=ValueAnimator.ofFloat(1f,0f).apply {
            duration=500L
            addUpdateListener { image.alpha=it.animatedValue as Float }
            addListener(object:AnimatorListenerAdapter() { override fun onAnimationEnd(animation:Animator) { clearVisual() } })
            start()
        }
    }
    private fun releaseAudio() { audio?.let { runCatching { it.stop() }; runCatching { it.release() } }; audio=null }
    private fun clearVisual() {
        owner?.removeOnLayoutChangeListener(layoutListener)
        mask?.let { owner?.overlay?.remove(it); it.setImageDrawable(null) }
        mask=null; owner=null; snapshot?.recycle(); snapshot=null
    }
    fun detach(host:View?) { if(owner===host) { visualFade?.cancel(); visualFade=null; clearVisual() } }
    fun cancel() {
        visualFade?.cancel(); visualFade=null; clearVisual()
        audioFade?.cancel(); audioFade=null; releaseAudio()
        inheritedCar?.restore(); inheritedCar=null
    }
}
