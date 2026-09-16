package tw.techtarian.browser

import org.json.JSONObject

data class HistoryEntry(val url:String,val title:String,val visited:Long=0){
    fun json()=JSONObject().put("url",url).put("title",title).put("visited",visited)
}
