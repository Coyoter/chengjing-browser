package tw.techtarian.browser

import org.json.JSONArray
import org.json.JSONObject

internal enum class BrowsingTimeRange(val label:String,val durationMillis:Long?) {
    LAST_15_MINUTES("過去 15 分鐘",15*60_000L),
    LAST_HOUR("過去 1 小時",60*60_000L),
    LAST_DAY("過去 24 小時",24*60*60_000L),
    LAST_WEEK("過去 7 天",7*24*60*60_000L),
    LAST_FOUR_WEEKS("過去 4 週",28*24*60*60_000L),
    ALL_TIME("不限時間",null);

    fun window(now:Long)=BrowsingWindow(durationMillis?.let{(now-it).coerceAtLeast(1)},now)
}

/** A missing legacy timestamp is unknown, never silently migrated to "now". */
internal data class BrowsingWindow(val since:Long?,val until:Long) {
    fun contains(timestamp:Long)=since==null||(timestamp>0&&timestamp>=since&&timestamp<=until)
}

internal data class BrowsingDataSelection(val history:Boolean,val tabs:Boolean,val siteData:Boolean) {
    val isEmpty get()=!history&&!tabs&&!siteData
}
internal data class HistoryPage(val url:String,val title:String,val visitedAt:Long=0)
internal data class HistorySearch(val query:String,val searchedAt:Long=0)

internal object BrowsingHistoryFormat {
    fun pages(raw:String):List<HistoryPage> = runCatching {
        val rows=JSONArray(raw)
        (0 until rows.length()).mapNotNull { index->
            val row=rows.optJSONObject(index)?:return@mapNotNull null
            val url=row.optString("url").takeIf{it.startsWith("https://")||it.startsWith("http://")}?:return@mapNotNull null
            HistoryPage(url,row.optString("title",url),row.optLong("visitedAt",0).coerceAtLeast(0))
        }.take(250)
    }.getOrDefault(emptyList())
    fun pagesJson(rows:List<HistoryPage>)=JSONArray(rows.map{
        JSONObject().put("url",it.url).put("title",it.title).put("visitedAt",it.visitedAt)
    }).toString()
    fun searches(raw:String):List<HistorySearch> = runCatching {
        val rows=JSONArray(raw)
        (0 until rows.length()).mapNotNull { index->
            when(val row=rows.opt(index)) {
                is String->row.takeIf{it.isNotBlank()}?.let{HistorySearch(it)}
                is JSONObject->row.optString("query").takeIf{it.isNotBlank()}?.let{HistorySearch(it,row.optLong("searchedAt",0).coerceAtLeast(0))}
                else->null
            }
        }.take(100)
    }.getOrDefault(emptyList())
    fun searchesJson(rows:List<HistorySearch>)=JSONArray(rows.map{
        JSONObject().put("query",it.query).put("searchedAt",it.searchedAt)
    }).toString()
}
