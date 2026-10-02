package tw.techtarian.browser

import org.json.JSONArray
import org.json.JSONObject

internal enum class BrowsingTimeRange(private val labelResource:Int,val durationMillis:Long?) {
    LAST_15_MINUTES(R.string.msg_6846f5758df9,15*60_000L),
    LAST_HOUR(R.string.msg_243d5a95aaad,60*60_000L),
    LAST_DAY(R.string.msg_322773403765,24*60*60_000L),
    LAST_WEEK(R.string.msg_27905bacccf9,7*24*60*60_000L),
    LAST_FOUR_WEEKS(R.string.msg_7d5941c88cb3,28*24*60*60_000L),
    ALL_TIME(R.string.msg_75d9ba5b232a,null);

    val label:String get()=bt(labelResource)

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
