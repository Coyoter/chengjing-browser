package tw.techtarian.browser

import android.webkit.WebView

private fun WebView.historyUrls():Pair<List<String>,Int> {
    val history=copyBackForwardList()
    return (0 until history.size).map{history.getItemAtIndex(it).url.orEmpty()} to history.currentIndex
}

private fun WebView.previousPageOffset():Int? {
    val (urls,index)=historyUrls()
    return BrowserBackHistory.previousOffset(urls,index)
}

internal fun BrowserTab.updateNavigationState(){
    canBack=web.previousPageOffset()!=null
    canForward=web.canGoForward()
}

internal fun BrowserController.goBackInPage():Boolean {
    val tab=active?:return false
    val offset=tab.web.previousPageOffset()?:return false
    stopEye();tab.web.goBackOrForward(offset)
    return true
}

internal fun BrowserTab.canReceiveExternalLink():Boolean =
    !incognito&&imageContent==null&&url.isEmpty()&&pendingUrl.isEmpty()&&web.historyUrls().first.all{BrowserBackHistory.isHome(it)}

/** Close only the externally opened tab or the current unused homepage before leaving. */
internal fun BrowserController.finishBackNavigation(){
    val tab=active?:return
    if(tab.openedExternally||tab.canReceiveExternalLink())closeTab(tab.id,replaceLast=false)
}
