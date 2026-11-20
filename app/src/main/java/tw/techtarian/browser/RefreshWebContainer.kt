package tw.techtarian.browser

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.math.abs

/**
 * Pull-to-refresh activates only when the page (including nested scrollable containers)
 * is genuinely at the top and cannot scroll further, and the user performs a deliberate,
 * unidirectional vertical downward pull.
 */
class RefreshWebContainer(context: Context, private val web: SelectionWebView) : SwipeRefreshLayout(context) {
    private val density = context.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val upwardCancelPx = maxOf(touchSlop * 0.5f, 4f * density)
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var gestureEligible = false

    init {
        addView(web, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setDistanceToTriggerSync((96f * density).toInt())
        setOnChildScrollUpCallback { _, _ ->
            !gestureEligible || web.canScrollUpNow()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                downX = event.x
                downY = event.y
                lastY = event.y
                web.onTouchGestureStart()
                val settledAtTop = SystemClock.elapsedRealtime() - web.lastTouchScrollAt >= SCROLL_SETTLE_MS
                gestureEligible = !web.canScrollUpNow() && settledAtTop
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                gestureEligible = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (gestureEligible) {
                    if (web.canScrollUpNow()) {
                        gestureEligible = false
                    } else {
                        val index = event.findPointerIndex(activePointerId)
                        if (index < 0) {
                            gestureEligible = false
                        } else {
                            val x = event.getX(index)
                            val y = event.getY(index)
                            val dx = x - downX
                            val dy = y - downY
                            val stepDy = y - lastY
                            lastY = y
                            if (dy < -upwardCancelPx || stepDy < -upwardCancelPx || (abs(dx) > touchSlop && abs(dx) > abs(dy) * 0.55f)) {
                                gestureEligible = false
                            }
                        }
                    }
                }
            }
        }
        return try {
            super.dispatchTouchEvent(event)
        } finally {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                web.onTouchGestureEnd()
                activePointerId = MotionEvent.INVALID_POINTER_ID
                gestureEligible = false
            }
        }
    }

    companion object {
        private const val SCROLL_SETTLE_MS = 180L
    }
}
