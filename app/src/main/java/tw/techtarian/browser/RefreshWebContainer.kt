package tw.techtarian.browser

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Plain WebView host container without pull-to-refresh gesture interception.
 * Page reloading is triggered explicitly via the address bar reload button.
 */
class RefreshWebContainer(context: Context, web: SelectionWebView) : FrameLayout(context) {
    var isRefreshing: Boolean = false

    init {
        addView(web, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun setOnRefreshListener(@Suppress("UNUSED_PARAMETER") listener: () -> Unit) {}
    fun setColorSchemeColors(@Suppress("UNUSED_PARAMETER") vararg colors: Int) {}
    fun setProgressBackgroundColorSchemeColor(@Suppress("UNUSED_PARAMETER") color: Int) {}
}
