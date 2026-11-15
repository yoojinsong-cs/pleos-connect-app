package com.example.hyundaiappproject

import android.content.Context
import android.graphics.Canvas
import android.graphics.Movie
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable

/** Shared visual area: native Lottie rendering, clipped between guidance and progress. */
class SaiAwakenVisualView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val animation = LottieDrawable().apply { callback = this@SaiAwakenVisualView }
    private var resourceId = R.raw.sai_awaken_loop
    private var requestSerial = 0
    private var loaded = false
    private var loading = false
    private var loadFailed=false
    private var readyAction:(()->Unit)?=null
    fun whenSceneReady(action:()->Unit) {
        if(loaded || loadFailed) action() else readyAction=action
    }
    private var attached = false
    private var progressUpdatedAt = SystemClock.uptimeMillis()
    private var frozenProgress = 0f

    var playbackDurationSeconds = 180f
    var progress: Float = 0f
        set(value) {
            val next = value.coerceIn(0f, 1f)
            if (next == field) return
            field = next
            frozenProgress = next
            progressUpdatedAt = SystemClock.uptimeMillis()
            invalidate()
        }
    var running = false
        set(value) {
            if (value == field) return
            if (field) frozenProgress = currentProgress()
            progressUpdatedAt = SystemClock.uptimeMillis()
            field = value
            invalidate()
        }
    var completed = false
        set(value) { if (value != field) { field = value; invalidate() } }

    private val completionMovie: Movie? by lazy {
        runCatching {
            resources.openRawResource(R.raw.sai_completion_orb).use { Movie.decodeStream(it) }
        }.getOrNull()
    }

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun setScene(raw: Int) {
        if (resourceId == raw) return
        resourceId = raw
        requestSerial++
        animation.clearComposition()
        loaded = false
        loadFailed=false
        loading = false
        frozenProgress = 0f
        progress = 0f
        progressUpdatedAt = SystemClock.uptimeMillis()
        if (attached) loadScene()
        invalidate()
    }

    private fun loadScene() {
        if (loaded || loading) return
        loading = true
        val serial = ++requestSerial
        // Large halftone compositions are cached in a small LRU, rather than all nine at once.
        LottieCompositionFactory.setMaxCacheSize(2)
        LottieCompositionFactory.fromRawRes(context.applicationContext, resourceId)
            .addListener { composition ->
                if (serial == requestSerial && attached) {
                    animation.setComposition(composition)
                    loaded = true
                    loading = false
                    val action=readyAction; readyAction=null; action?.invoke()
                    contentDescription = null
                    invalidate()
                }
            }
            .addFailureListener { error ->
                if (serial == requestSerial) {
                    loading = false
                    loadFailed=true
                    val action=readyAction; readyAction=null; action?.invoke()
                    contentDescription = "애니메이션을 불러오지 못했어요"
                    Log.e("SAI_GRAPHIC", "Animation load failed", error)
                    invalidate()
                }
            }
    }

    private fun currentProgress(): Float {
        val extra = if (running) (SystemClock.uptimeMillis() - progressUpdatedAt) /
            (playbackDurationSeconds.coerceAtLeast(1f) * 1000f) else 0f
        return (frozenProgress + extra).coerceIn(0f, 1f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        canvas.save()
        canvas.clipRect(0, 0, width, height)
        if (completed) {
            val movie = completionMovie
            if (movie != null && movie.width() > 0 && movie.height() > 0) {
                if (layerType != LAYER_TYPE_SOFTWARE) setLayerType(LAYER_TYPE_SOFTWARE, null)
                movie.setTime((SystemClock.uptimeMillis() % movie.duration().coerceAtLeast(1)).toInt())
                val scale = minOf(width.toFloat() / movie.width(), height.toFloat() / movie.height())
                canvas.translate((width - movie.width() * scale) / 2, (height - movie.height() * scale) / 2)
                canvas.scale(scale, scale)
                movie.draw(canvas, 0f, 0f)
            }
        } else if (loaded) {
            // These eclipse assets have a ~310px subject inside a 640px canvas.
            // Fit the visible subject instead of the canvas's transparent margins.
            // Singing bowl keeps its original canvas fit; completion uses the unchanged GIF path.
            val bounds = animation.composition?.bounds
            if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
                val enlarge = resourceId != R.raw.sai_bowl_loop
                val contentFraction = when(resourceId) { R.raw.sai_bowl_loop -> 1f; R.raw.sai_awaken_loop -> .62f; else -> .66f }
                val gap = if (enlarge) 6f * resources.displayMetrics.density else 0f
                val usableWidth = (width - gap * 2f).coerceAtLeast(0f)
                val usableHeight = (height - gap * 2f).coerceAtLeast(0f)
                val scale = minOf(
                    usableWidth / (bounds.width() * contentFraction),
                    usableHeight / (bounds.height() * contentFraction)
                )
                if (scale <= 0f) { canvas.restore(); return }
                val w = (bounds.width() * scale).toInt()
                val h = (bounds.height() * scale).toInt()
                val left = (width - w) / 2
                val top = (height - h) / 2
                animation.setBounds(left, top, left + w, top + h)
                animation.progress = currentProgress()
                animation.draw(canvas)
            }
        }
        canvas.restore()
        if ((running || completed) && attached && isShown && windowVisibility == VISIBLE) {
            postInvalidateOnAnimation()
        }
    }

    override fun verifyDrawable(who: Drawable) = who === animation || super.verifyDrawable(who)
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        loadScene()
        invalidate()
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) invalidate()
    }
    override fun onDetachedFromWindow() {
        readyAction=null
        attached = false
        requestSerial++
        loading = false
        running = false
        super.onDetachedFromWindow()
    }
}
