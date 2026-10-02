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

/** A blank-looking page with real back/forward history is still an existing browsing tab. */
internal fun BrowserTab.isInitialNewTab():Boolean =
    imageContent==null&&BrowserBackHistory.isHome(url)&&BrowserBackHistory.isHome(pendingUrl)&&
        web.historyUrls().first.all{BrowserBackHistory.isHome(it)}

internal fun BrowserController.backInBrowser(){
    if(!goBackInPage())finishBackNavigation()
}

/** At the history boundary, close the current page and show a usable new-tab page in its profile. */
internal fun BrowserController.finishBackNavigation(){
    val tab=active?:run{newTab(incognito=false);return}
    // Initial-page system Back exits through MainActivity; the toolbar never recreates blank tabs.
    if(tab.isInitialNewTab())return
    val initial=tabs.firstOrNull{it.id!=tab.id&&it.incognito==tab.incognito&&it.isInitialNewTab()}
    val incognito=tab.incognito
    closeTab(tab.id,replaceLast=false)
    if(initial!=null)switchTab(initial.id)else newTab(incognito=incognito)
    sheet=""
}
