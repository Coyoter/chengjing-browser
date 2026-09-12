package tw.techtarian.browser

import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/** A drag must start at the top. Reaching the top during an existing scroll is not a refresh. */
class RefreshWebContainer(context: Context, private val web: SelectionWebView) : SwipeRefreshLayout(context) {
    private var startedAtTop = false

    init {
        addView(web, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setOnChildScrollUpCallback { _, _ -> !startedAtTop || web.selecting || web.canScrollVertically(-1) }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            startedAtTop = !web.selecting && !web.canScrollVertically(-1)
        }
        return super.dispatchTouchEvent(event)
    }
}
