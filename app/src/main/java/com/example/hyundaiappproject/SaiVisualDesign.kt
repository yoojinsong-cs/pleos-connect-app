package com.example.hyundaiappproject

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*
import java.util.Random
import android.widget.LinearLayout

/** Shrink card internals together on short landscape displays. */
class SaiResponsiveCard(context:Context):LinearLayout(context) {
    private val originals=java.util.IdentityHashMap<View,IntArray>()
    private var originalPadding:IntArray?=null
    override fun onMeasure(widthSpec:Int,heightSpec:Int) {
        val padding=originalPadding ?: intArrayOf(paddingLeft,paddingTop,paddingRight,paddingBottom).also { originalPadding=it }
        var desired=padding[1]+padding[3]
        for(i in 0 until childCount) {
            val child=getChildAt(i); val lp=child.layoutParams as LinearLayout.LayoutParams
            val initial=originals[child] ?: intArrayOf(lp.width,lp.height,lp.topMargin,lp.bottomMargin).also { originals[child]=it }
            desired+=initial[1].coerceAtLeast(0)+initial[2]+initial[3]
        }
        val limit=MeasureSpec.getSize(heightSpec)
        val factor=if(MeasureSpec.getMode(heightSpec)==MeasureSpec.UNSPECIFIED || desired==0) 1f else minOf(1f,limit.toFloat()/desired)
        setPadding(padding[0],(padding[1]*factor).toInt(),padding[2],(padding[3]*factor).toInt())
        for(i in 0 until childCount) {
            val child=getChildAt(i); val original=originals[child]!!
            val lp=child.layoutParams as LinearLayout.LayoutParams
            if(original[1]>0) lp.height=(original[1]*factor).toInt()
            if(original[0]>0 && child is SaiModeIconView) lp.width=(original[0]*factor).toInt()
            lp.topMargin=(original[2]*factor).toInt(); lp.bottomMargin=(original[3]*factor).toInt()
        }
        super.onMeasure(widthSpec,heightSpec)
    }
}

/** Quiet star field, drawn locally without downloaded images. */
class SaiCosmicBackdrop(context:Context):View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val random=Random(73)
    private val stars=List(110) { floatArrayOf(random.nextFloat(),random.nextFloat(),random.nextFloat()) }
    init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(c:Canvas) {
        super.onDraw(c)
        paint.shader=RadialGradient(width*.5f,height*.48f,maxOf(width,height).coerceAtLeast(1)*.58f,
            intArrayOf(Color.rgb(45,44,76),Color.rgb(26,31,47),Color.rgb(27,28,32)),floatArrayOf(0f,.48f,1f),Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint); paint.shader=null
        for(s in stars) { paint.color=Color.argb((45+110*s[2]).toInt(),214,225,255); c.drawCircle(s[0]*width,s[1]*height,(.45f+s[2]*1.05f)*resources.displayMetrics.density,paint) }
        paint.style=Paint.Style.STROKE; paint.strokeWidth=resources.displayMetrics.density; paint.color=Color.argb(22,175,184,238)
        val r=minOf(width,height)*.48f
        c.save(); c.rotate(-18f,width*.5f,height*.48f); c.drawOval(width*.5f-r*1.6f,height*.48f-r*.4f,width*.5f+r*1.6f,height*.48f+r*.4f,paint)
        c.restore(); paint.style=Paint.Style.FILL
    }
}

/** Shared line icons: same tile, weight and palette as the reference cards. */
class SaiModeIconView(context:Context,private val kind:String):View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private val path=Path()
    init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(c:Canvas) {
        super.onDraw(c); c.save(); c.scale(width/100f,height/100f)
        p.style=Paint.Style.FILL; p.color=Color.rgb(51,55,62); c.drawRoundRect(0f,0f,100f,100f,30f,30f,p)
        p.style=Paint.Style.STROKE; p.color=Color.rgb(174,180,192); p.strokeWidth=3f; p.strokeCap=Paint.Cap.ROUND; p.strokeJoin=Paint.Join.ROUND
        fun line(x:Float,y:Float,xx:Float,yy:Float)=c.drawLine(x,y,xx,yy,p)
        fun circle(x:Float,y:Float,r:Float)=c.drawCircle(x,y,r,p)
        when(kind) {
            "relax" -> {
                // A lotus/breath symbol is shared by mode selection and ritual records.
                path.reset(); path.moveTo(50f,66f); path.cubicTo(31f,55f,38f,35f,50f,25f)
                path.cubicTo(62f,35f,69f,55f,50f,66f); c.drawPath(path,p)
                path.reset(); path.moveTo(50f,66f); path.cubicTo(30f,70f,23f,52f,24f,40f)
                path.cubicTo(40f,40f,48f,49f,50f,66f); c.drawPath(path,p)
                path.reset(); path.moveTo(50f,66f); path.cubicTo(70f,70f,77f,52f,76f,40f)
                path.cubicTo(60f,40f,52f,49f,50f,66f); c.drawPath(path,p)
                c.drawArc(27f,62f,73f,81f,10f,160f,false,p)
            }
            "basic" -> {
                circle(50f,50f,21f); c.drawArc(23f,23f,77f,77f,205f,130f,false,p)
                c.drawArc(23f,23f,77f,77f,25f,130f,false,p)
                p.style=Paint.Style.FILL; circle(50f,50f,4f)
            }
            "reminder" -> {
                c.drawRoundRect(28f,29f,72f,75f,6f,6f,p)
                line(28f,42f,72f,42f); line(39f,23f,39f,34f); line(61f,23f,61f,34f)
                line(39f,57f,47f,65f); line(47f,65f,62f,50f)
            }
            "nap" -> {
                path.reset(); path.moveTo(56f,28f); path.cubicTo(24f,26f,22f,72f,54f,72f); path.cubicTo(61f,72f,66f,69f,70f,64f); path.cubicTo(45f,66f,39f,44f,56f,28f); c.drawPath(path,p)
                line(66f,29f,73f,29f); line(73f,29f,66f,37f); line(66f,37f,73f,37f)
            }
            "awaken" -> { circle(50f,50f,10f); for(i in 0..7) { val a=i*Math.PI/4; line((50+18*cos(a)).toFloat(),(50+18*sin(a)).toFloat(),(50+28*cos(a)).toFloat(),(50+28*sin(a)).toFloat()) } }
            "clock" -> { circle(50f,50f,25f); line(50f,50f,50f,34f); line(50f,50f,63f,57f); circle(50f,50f,1.5f) }
            "music" -> { line(42f,65f,42f,31f); line(42f,31f,69f,26f); line(69f,26f,69f,60f); c.drawOval(27f,60f,42f,71f,p); c.drawOval(54f,55f,69f,66f,p); line(43f,39f,69f,34f) }
            "light" -> { c.drawArc(32f,28f,68f,62f,180f,180f,false,p); line(30f,45f,70f,45f); path.reset(); path.moveTo(37f,46f); path.quadTo(39f,64f,50f,66f); path.quadTo(61f,64f,63f,46f); c.drawPath(path,p); line(50f,66f,50f,72f); line(41f,73f,59f,73f) }
            "rain" -> { cloud(c); for(x in listOf(36f,50f,64f)) line(x,61f,x-4,72f) }
            "camp" -> { path.reset(); path.moveTo(24f,73f); path.lineTo(50f,30f); path.lineTo(76f,73f); path.close(); c.drawPath(path,p); line(50f,30f,50f,73f); line(50f,52f,62f,73f) }
            "space" -> { circle(50f,50f,18f); c.save(); c.rotate(-28f,50f,50f); c.drawOval(19f,43f,81f,57f,p); c.restore(); line(73f,22f,73f,30f); line(69f,26f,77f,26f) }
            "sea" -> { for(y in listOf(38f,52f,66f)) { path.reset(); path.moveTo(23f,y); path.cubicTo(33f,y-12,43f,y+12,53f,y); path.cubicTo(63f,y-12,68f,y+6,77f,y); c.drawPath(path,p) } }
            "forest" -> { path.reset(); path.moveTo(50f,24f); path.lineTo(29f,50f); path.lineTo(38f,50f); path.lineTo(24f,68f); path.lineTo(76f,68f); path.lineTo(62f,50f); path.lineTo(71f,50f); path.close(); c.drawPath(path,p); line(50f,68f,50f,78f) }
        }; c.restore()
    }
    private fun cloud(c:Canvas) {
        path.reset(); path.moveTo(31f,53f); path.cubicTo(16f,51f,26f,30f,38f,35f); path.cubicTo(45f,15f,69f,24f,67f,39f); path.cubicTo(83f,41f,77f,55f,67f,53f); path.close(); c.drawPath(path,p)
    }
}
