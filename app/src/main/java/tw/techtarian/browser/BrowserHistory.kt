package tw.techtarian.browser

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

internal data class VisitEntry(val url:String,val title:String,val visitedAt:Long=0) {
    fun json()=JSONObject().put("url",url).put("title",title).put("visitedAt",visitedAt)
}

/** Reads the old array in place; old rows remain visible without inventing visit dates. */
internal class BrowserHistory(context:Context) {
    private val prefs=context.getSharedPreferences("browser-v1",Context.MODE_PRIVATE)
    fun entries():List<VisitEntry> = runCatching {
        val array=JSONArray(prefs.getString("history","[]"))
        (0 until array.length()).mapNotNull { index->
            val row=array.optJSONObject(index)?:return@mapNotNull null
            val url=row.optString("url").takeIf{it.toHttpUrlOrNull()!=null}?:return@mapNotNull null
            VisitEntry(url,row.optString("title").ifBlank{url},row.optLong("visitedAt",0).coerceAtLeast(0))
        }.take(250)
    }.getOrDefault(emptyList())
    fun visit(url:String,title:String,privateMode:Boolean=false,now:Long=System.currentTimeMillis()) {
        if(privateMode||url.toHttpUrlOrNull()==null)return
        save((listOf(VisitEntry(url,title.ifBlank{url}.take(2000),now))+entries().filterNot{it.url==url}).take(250))
    }
    fun remove(url:String)=save(entries().filterNot{it.url==url})
    private fun save(rows:List<VisitEntry>) {
        check(prefs.edit().putString("history",JSONArray(rows.map{it.json()}).toString()).commit()){"歷史記錄儲存失敗"}
    }
}
