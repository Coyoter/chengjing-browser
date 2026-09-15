package tw.techtarian.browser

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Favorite(val id:String,val title:String,val pageTitle:String,val url:String,val scrollY:Double,val progress:Double,val updated:Long,val deleted:Boolean=false){
    fun json()=JSONObject().put("id",id).put("title",title).put("pageTitle",pageTitle).put("url",url).put("scrollY",scrollY).put("progress",progress).put("updated",updated).put("deleted",deleted)
    companion object{
        fun from(j:JSONObject):Favorite{
            val url=j.getString("url");require(url.toHttpUrlOrNull()!=null){"收藏網址格式不正確"}
            val id=j.getString("id");require(id.isNotBlank()&&id.length<=256){"收藏識別碼格式不正確"}
            val updated=j.getLong("updated");require(updated>=0&&updated<Long.MAX_VALUE){"收藏時間格式不正確"}
            return Favorite(id,j.getString("title"),j.optString("pageTitle",j.getString("title")),url,j.optDouble("scrollY",0.0).takeIf{it.isFinite()}?.coerceAtLeast(0.0)?:0.0,j.optDouble("progress",0.0).takeIf{it.isFinite()}?.coerceIn(0.0,1.0)?:0.0,updated,j.optBoolean("deleted",false))
        }
        fun capture(previous:Favorite?,url:String,pageTitle:String,scrollY:Double,progress:Double,now:Long=System.currentTimeMillis()):Favorite{
            require(url.toHttpUrlOrNull()!=null)
            val name=pageTitle.ifBlank{url}.take(2000)
            val timestamp=FavoriteFormat.nextTimestamp(previous?.updated?:0,now)
            return Favorite(previous?.id?:UUID.randomUUID().toString(),if(previous==null||previous.title==previous.pageTitle)name else previous.title,name,url,
                scrollY.takeIf{it.isFinite()}?.coerceAtLeast(0.0)?:0.0,progress.takeIf{it.isFinite()}?.coerceIn(0.0,1.0)?:0.0,timestamp)
        }
    }
}

/** Independent Drive namespace: older clients cannot overwrite favorite snapshots. */
object FavoriteFormat{
    const val TAG="chengjing-browser-favorites-v1"
    const val MAX_RECORDS=10000
    const val MAX_BYTES=12_000_000
    private val conflictOrder=compareBy<Favorite>({it.updated},{it.deleted},{it.url},{it.title},{it.pageTitle},{it.scrollY},{it.progress})

    fun nextTimestamp(previous:Long,now:Long=System.currentTimeMillis()):Long{
        require(previous>=0&&previous<Long.MAX_VALUE-1){"收藏時間超出範圍"}
        return maxOf(now,previous+1)
    }
    // The entire newer record wins, not the maximum progress: reading backwards is valid.
    // Equal clocks converge deterministically; a deletion wins a same-clock edit.
    fun merge(vararg snapshots:List<Favorite>):List<Favorite>{
        val result=linkedMapOf<String,Favorite>()
        snapshots.forEach{rows->rows.forEach{row->
            val old=result[row.id]
            if(old==null||conflictOrder.compare(row,old)>0)result[row.id]=row
            require(result.size<=MAX_RECORDS){"收藏同步資料過多，本機內容已保留"}
        }}
        return result.values.sortedBy{it.id}
    }
    fun snapshot(rows:List<Favorite>):String{
        val raw=JSONObject().put("schema",1).put("favorites",JSONArray(merge(rows).map{it.json()})).toString()
        require(raw.toByteArray(Charsets.UTF_8).size<=MAX_BYTES){"收藏同步資料超過大小限制"}
        return raw
    }
    fun read(raw:String):List<Favorite>{
        require(raw.toByteArray(Charsets.UTF_8).size<=MAX_BYTES){"收藏同步資料超過大小限制"}
        val root=JSONObject(raw)
        require(root.getInt("schema")==1){"此收藏同步格式需要更新 App 才能讀取"}
        val rows=root.getJSONArray("favorites")
        require(rows.length()<=MAX_RECORDS){"收藏同步資料過多，本機內容已保留"}
        return merge((0 until rows.length()).map{Favorite.from(rows.getJSONObject(it))})
    }
}

class FavoriteStore(context:Context){
    private val prefs=context.getSharedPreferences("favorites-v1",Context.MODE_PRIVATE)
    var onChange:(()->Unit)?=null
    // Keep the existing preference and IDs: upgrading must not recreate the library.
    @Synchronized fun records():List<Favorite>{
        val array=JSONArray(prefs.getString("items","[]"))
        return FavoriteFormat.merge((0 until array.length()).map{Favorite.from(array.getJSONObject(it))})
    }
    @Synchronized fun all():List<Favorite> = records().filterNot{it.deleted}.sortedWith(compareByDescending<Favorite>{it.updated}.thenBy{it.id})
    fun get(id:String)=all().find{it.id==id}
    fun forPage(url:String):Favorite?{
        val address=url.toHttpUrlOrNull()?:return null
        return all().firstOrNull{it.url.toHttpUrlOrNull()==address}
    }
    private fun write(rows:List<Favorite>){
        val merged=FavoriteFormat.merge(rows)
        FavoriteFormat.snapshot(merged) // Validate size before changing any local data.
        check(prefs.edit().putString("items",JSONArray(merged.map{it.json()}).toString()).commit()){ "收藏儲存失敗" }
    }
    private fun nextTime(rows:List<Favorite>)=FavoriteFormat.nextTimestamp(rows.maxOfOrNull{it.updated}?:0)
    @Synchronized fun save(url:String,title:String,y:Double,progress:Double,updateId:String?=null,forceNew:Boolean=false):Favorite{
        val rows=records()
        val visible=rows.filterNot{it.deleted}
        val previous=if(forceNew)null else visible.find{it.id==updateId}?:visible.find{it.url==url}
        val item=Favorite.capture(previous,url,title,y,progress,nextTime(rows))
        write(rows.filterNot{it.id==item.id}+item)
        onChange?.invoke()
        return item
    }
    @Synchronized fun rename(id:String,title:String){
        require(title.isNotBlank())
        val rows=records();val old=rows.find{it.id==id&&!it.deleted}?:return
        val name=title.trim().take(2000)
        if(old.title==name)return
        write(rows.map{if(it.id==id)it.copy(title=name,updated=nextTime(rows))else it})
        onChange?.invoke()
    }
    @Synchronized fun remove(id:String){
        val rows=records()
        if(rows.none{it.id==id&&!it.deleted})return
        val timestamp=nextTime(rows)
        // Retain tombstones so an offline phone cannot resurrect a deleted favorite.
        write(rows.map{if(it.id==id)it.copy(deleted=true,updated=timestamp)else it})
        onChange?.invoke()
    }
    @Synchronized fun mergeRemote(remote:List<Favorite>):Boolean{
        val local=records();val merged=FavoriteFormat.merge(local,remote)
        if(merged==local)return false
        write(merged)
        return true // Remote merges do not enqueue another sync by themselves.
    }
}
