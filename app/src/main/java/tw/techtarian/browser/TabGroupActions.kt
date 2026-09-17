package tw.techtarian.browser

/**
 * Snapshot IDs before removing items: closing the last tab can create a fresh homepage.
 * Reuse the existing close path so each WebView, persisted tab and thumbnail is disposed
 * exactly as with an individual close. The other collection is never included.
 */
internal fun BrowserController.closeAllTabs(incognito:Boolean):Int {
    val ids=tabs.filter{it.incognito==incognito}.map{it.id}
    if(ids.isEmpty())return 0
    ids.forEach{closeTab(it)}
    return ids.size
}
