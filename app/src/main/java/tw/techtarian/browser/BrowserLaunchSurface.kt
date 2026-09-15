package tw.techtarian.browser

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min
import kotlin.math.sin

/**
 * The same quiet wordmark / fine arcs language as ChengJing Notes' LaunchSurface.
 * Shown only while the first browser frame is prepared; never a timed branding delay.
 * No WebView, network request, bitmap allocation, or model download is needed to draw it.
 */
internal class BrowserLaunchSurface(context:Context, private val dark:Boolean):View(context){
    private val density=resources.displayMetrics.density
    private fun dp(value:Float)=value*density
    private fun sp(value:Float)=TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,value,resources.displayMetrics)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc=RectF()
    private val regular=Typeface.create("sans-serif",Typeface.NORMAL)
    private val medium=Typeface.create("sans-serif-medium",Typeface.NORMAL)
    private var phase=0f
    private val pulse=ValueAnimator.ofFloat(0f,1f).apply{
        duration=1300L
        repeatCount=ValueAnimator.INFINITE
        interpolator=LinearInterpolator()
        addUpdateListener{phase=it.animatedValue as Float;invalidate()}
    }
    init{
        contentDescription="正在開啟澄境瀏覽器"
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES
        setBackgroundColor(if(dark)Color.rgb(15,21,19)else Color.rgb(241,238,231))
    }
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas)
        val x=width*.14f
        val baseline=height*.38f
        val available=width-x*2
        paint.style=Paint.Style.STROKE
        paint.strokeWidth=dp(1f)
        paint.color=if(dark)Color.argb(26,105,223,192)else Color.argb(26,20,122,100)
        val unit=min(width,height).toFloat()
        for(ratio in floatArrayOf(.28f,.40f,.53f)){
            val radius=unit*ratio
            val cx=width*.86f
            val cy=height*.82f
            arc.set(cx-radius,cy-radius,cx+radius,cy+radius)
            canvas.drawArc(arc,185f,155f,false,paint)
        }
        paint.style=Paint.Style.FILL
        paint.typeface=medium
        paint.textSize=sp(34f)
        paint.textSize=min(paint.textSize,paint.textSize*available/paint.measureText("澄境"))
        paint.color=if(dark)Color.rgb(242,239,231)else Color.rgb(29,41,37)
        canvas.drawText("澄境",x,baseline,paint)
        val titleBottom=baseline+paint.fontMetrics.descent
        paint.typeface=regular
        paint.textSize=sp(14f)
        paint.color=if(dark)Color.rgb(177,184,176)else Color.rgb(89,102,95)
        val subtitleBaseline=titleBottom+dp(12f)-paint.fontMetrics.ascent
        canvas.drawText("瀏覽器",x,subtitleBaseline,paint)
        val subtitleBottom=subtitleBaseline+paint.fontMetrics.descent
        paint.textSize=sp(9f)
        val caption="CHENGJING BROWSER"
        val naturalWidth=paint.measureText(caption)+dp(1.7f)*(caption.length-1)
        val scale=min(1f,available/naturalWidth)
        paint.textSize*=scale
        var next=x
        val captionBaseline=subtitleBottom+dp(15f)-paint.fontMetrics.ascent
        for(letter in caption){
            val text=letter.toString()
            canvas.drawText(text,next,captionBaseline,paint)
            next+=paint.measureText(text)+dp(1.7f)*scale
        }
        for(index in 0..2){
            val alpha=(100+55*sin(phase*6.283185f-index*.7f)).toInt()
            paint.color=if(dark)Color.argb(alpha,105,223,192)else Color.argb(alpha,20,122,100)
            canvas.drawCircle(x+dp(index*11f),captionBaseline+dp(32f),dp(1.6f),paint)
        }
    }
    private fun updateAnimation(){
        if(isAttachedToWindow&&windowVisibility==VISIBLE&&ValueAnimator.areAnimatorsEnabled()){
            if(!pulse.isStarted)pulse.start()
        }else pulse.cancel()
    }
    override fun onAttachedToWindow(){super.onAttachedToWindow();updateAnimation()}
    override fun onWindowVisibilityChanged(visibility:Int){super.onWindowVisibilityChanged(visibility);updateAnimation()}
    override fun onDetachedFromWindow(){pulse.cancel();super.onDetachedFromWindow()}
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event:MotionEvent)=true
}
