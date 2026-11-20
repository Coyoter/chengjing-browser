package tw.techtarian.browser

import android.content.Context
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.OverScroller
import androidx.annotation.Keep

@Keep
class WebScrollBridge internal constructor(private val web: SelectionWebView) {
    @JavascriptInterface
    fun onDomScrollState(canScrollUp: Boolean, scrolling: Boolean) {
        web.updateDomScrollState(canScrollUp, scrolling)
    }
}

/** Picking is a browser gesture, never a click/touch delivered to a website or its iframes. */
class SelectionWebView(context: Context) : WebView(context) {
    var touchSequence=0L
        private set
    internal var lastTouchAt=0L
        private set
    internal var onPageTouch:(()->Unit)?=null
    internal var lastTouchX=0f
        private set
    internal var lastTouchY=0f
        private set
    @Volatile internal var domCanScrollUp = false
        private set
    @Volatile internal var scrolledInCurrentTouch = false
        private set
    @Volatile internal var lastTouchScrollAt = 0L
        private set
    @Volatile private var touchActive = false

    @Volatile var selecting = false
        set(value) {
            if (field != value) pickerScroller.forceFinished(true)
            field = value
        }
    private val pickerScroller = OverScroller(context)
    private val pickerGestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean {
            pickerScroller.forceFinished(true)
            return true
        }
        override fun onSingleTapUp(event: MotionEvent): Boolean {
            if (width > 0 && height > 0) {
                val x = (event.x / width).coerceIn(0f, 1f)
                val y = (event.y / height).coerceIn(0f, 1f)
                evaluateJavascript("window.__chengjingEye?.pickAt($x,$y)", null)
            }
            return true
        }
        override fun onScroll(first: MotionEvent?, current: MotionEvent, dx: Float, dy: Float): Boolean {
            scrollBy(dx.toInt(), dy.toInt())
            return true
        }
        override fun onFling(first: MotionEvent?, current: MotionEvent, vx: Float, vy: Float): Boolean {
            pickerScroller.fling(scrollX, scrollY, -vx.toInt(), -vy.toInt(), 0,
                (computeHorizontalScrollRange() - width).coerceAtLeast(0), 0,
                (computeVerticalScrollRange() - height).coerceAtLeast(0))
            postInvalidateOnAnimation()
            return true
        }
    }).apply { setIsLongpressEnabled(false); setOnDoubleTapListener(null) }

    init {
        addJavascriptInterface(WebScrollBridge(this), "ChengJingScroll")
    }

    internal fun updateDomScrollState(canScrollUp: Boolean, scrolling: Boolean) {
        domCanScrollUp = canScrollUp
        if (scrolling && touchActive) {
            scrolledInCurrentTouch = true
            lastTouchScrollAt = SystemClock.elapsedRealtime()
        }
    }

    internal fun onTouchGestureStart() {
        touchActive = true
        scrolledInCurrentTouch = false
    }

    internal fun onTouchGestureEnd() {
        touchActive = false
    }

    internal fun resetDomScrollState() {
        domCanScrollUp = false
        scrolledInCurrentTouch = false
        lastTouchScrollAt = 0L
    }

    internal fun canScrollUpNow(): Boolean =
        selecting || scrollY > 0 || canScrollVertically(-1) || domCanScrollUp || scrolledInCurrentTouch

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (touchActive && t != oldt) {
            scrolledInCurrentTouch = true
            lastTouchScrollAt = SystemClock.elapsedRealtime()
        }
    }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
        if (touchActive && scrollY > 0) {
            scrolledInCurrentTouch = true
            lastTouchScrollAt = SystemClock.elapsedRealtime()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if(event.actionMasked==MotionEvent.ACTION_DOWN){touchSequence++;lastTouchAt=SystemClock.elapsedRealtime();lastTouchX=event.x;lastTouchY=event.y;onPageTouch?.invoke()}
        if (!selecting) return super.dispatchTouchEvent(event)
        pickerGestures.onTouchEvent(event)
        return true
    }

    override fun computeScroll() {
        if (selecting && pickerScroller.computeScrollOffset()) {
            scrollTo(pickerScroller.currX, pickerScroller.currY)
            postInvalidateOnAnimation()
        } else super.computeScroll()
    }
}
