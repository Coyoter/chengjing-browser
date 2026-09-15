package tw.techtarian.browser

import org.json.JSONArray
import org.json.JSONObject

/** Separate from bookmarks so older clients cannot overwrite favorite snapshots. */
object FavoriteFormat {
    const val TAG="chengjing-browser-favorites-v1"
    private const val MAX_BYTES=12_000_000
    private const val MAX_RECORDS=20_000
    private val order=compareBy<Favorite>(
        {it.updated},{it.deleted},{it.url},{it.title},{it.pageTitle},{it.scrollY},{it.progress},
    )

    fun merge(vararg snapshots:List<Favorite>):List<Favorite>{
        val rows=mutableMapOf<String,Favorite>()
        snapshots.forEach{snapshot->snapshot.forEach{item->
            val previous=rows[item.id]
            // Last saved position wins, not the greatest percentage: rereading is valid.
            // Equal clocks resolve deterministically, with deletions winning ties.
            if(previous==null || order.compare(item,previous)>0)rows[item.id]=item
            require(rows.size<=MAX_RECORDS){"收藏同步資料過多，本機資料已保留"}
        }}
        return rows.values.sortedBy{it.id}
    }

    fun write(rows:List<Favorite>):String{
        val result=JSONObject().put("app",TAG).put("schema",1)
            .put("favorites",JSONArray(merge(rows).map{it.json()})).toString()
        require(result.toByteArray(Charsets.UTF_8).size<=MAX_BYTES){"收藏同步資料超過大小限制"}
        return result
    }

    fun read(raw:String):List<Favorite>{
        require(raw.toByteArray(Charsets.UTF_8).size<=MAX_BYTES){"收藏同步資料超過大小限制"}
        val json=JSONObject(raw)
        require(json.optString("app")==TAG && json.optInt("schema",-1)==1){
            "收藏同步格式無法辨識；請更新瀏覽器，本機資料已保留"
        }
        val array=json.getJSONArray("favorites")
        require(array.length()<=MAX_RECORDS){"收藏同步資料過多，本機資料已保留"}
        // Reject invalid snapshots as a whole rather than silently dropping records.
        return merge((0 until array.length()).map{Favorite.from(array.getJSONObject(it))})
    }
}
