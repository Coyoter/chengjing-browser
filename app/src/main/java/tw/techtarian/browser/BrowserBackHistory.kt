package tw.techtarian.browser

/** The native new-tab page is not a webpage to revisit with Back. */
internal object BrowserBackHistory {
    fun isHome(url:String)=url.isBlank()||url.substringBefore('#').substringBefore('?').equals("about:blank",ignoreCase=true)
    fun previousOffset(urls:List<String>,currentIndex:Int):Int? {
        if(currentIndex !in urls.indices)return null
        for(index in currentIndex-1 downTo 0)if(!isHome(urls[index]))return index-currentIndex
        return null
    }
}
