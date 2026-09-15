package tw.techtarian.browser

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Favorite(
    val id:String,
    val title:String,
    val pageTitle:String,
    val url:String,
    val scrollY:Double,
    val progress:Double,
    val updated:Long,
    val deleted:Boolean=false,
){
    fun json()=JSONObject().put("id",id).put("title",title).put("pageTitle",pageTitle).put("url",url)
        .put("scrollY",scrollY).put("progress",progress).put("updated",updated).put("deleted",deleted)
    companion object{
        fun from(j:JSONObject):Favorite{
            val url=j.getString("url");require(url.toHttpUrlOrNull()!=null)
            return Favorite(j.getString("id"),j.getString("title"),j.optString("pageTitle",j.getString("title")),url,
                j.optDouble("scrollY",0.0).takeIf{it.isFinite()}?.coerceAtLeast(0.0)?:0.0,
                j.optDouble("progress",0.0).takeIf{it.isFinite()}?.coerceIn(0.0,1.0)?:0.0,
                j.getLong("updated"),j.optBoolean("deleted",false))
        }
        fun capture(previous:Favorite?,url:String,pageTitle:String,scrollY:Double,progress:Double,now:Long=System.currentTimeMillis()):Favorite{
            require(url.toHttpUrlOrNull()!=null)
            val name=pageTitle.ifBlank{url}.take(2000)
            return Favorite(previous?.id?:UUID.randomUUID().toString(),if(previous==null||previous.title==previous.pageTitle)name else previous.title,name,url,
                scrollY.takeIf{it.isFinite()}?.coerceAtLeast(0.0)?:0.0,progress.takeIf{it.isFinite()}?.coerceIn(0.0,1.0)?:0.0,now)
        }
    }
}

/** A separate Drive collection lets older bookmark-only clients coexist safely. */
object FavoriteSyncFormat{
    const val TAG="chengjing-browser-favorites-v1"
    private const val MAX_RECORDS=10000
    private const val MAX_BYTES=12_000_000
    private val order=compareBy<Favorite>({it.updated},{it.deleted},{it.url},{it.title},{it.pageTitle},{it.scrollY},{it.progress})

    private fun validate(row:Favorite){
        require(row.id.isNotBlank()&&row.id.length<=200){"收藏識別碼無效"}
        require(row.url.toHttpUrlOrNull()!=null&&row.url.length<=32768){"收藏網址無效"}
        require(row.title.length<=2000&&row.pageTitle.length<=2000){"收藏標題太長"}
        require(row.updated in 0 until Long.MAX_VALUE){"收藏更新時間無效"}
        require(row.scrollY.isFinite()&&row.scrollY>=0&&row.progress.isFinite()&&row.progress in 0.0..1.0){"收藏進度無效"}
    }
    fun merge(vararg snapshots:List<Favorite>):List<Favorite>{
        val merged=mutableMapOf<String,Favorite>()
        snapshots.forEach{rows->rows.forEach{row->
            validate(row)
            val old=merged[row.id]
            // Last edit wins, not the largest percentage: reading backwards is valid.
            // Deletion wins a timestamp tie; remaining ties converge independent of device order.
            if(old==null||order.compare(row,old)>0)merged[row.id]=row
            require(merged.size<=MAX_RECORDS){"收藏同步資料超過上限，本機資料已保留"}
        }}
        // Do not deduplicate by URL: separate saved reading positions can share a page.
        return merged.values.sortedBy{it.id}
    }
    fun write(rows:List<Favorite>):String{
        val raw=JSONObject().put("version",1).put("favorites",JSONArray(merge(rows).map{it.json()})).toString()
        require(raw.toByteArray(Charsets.UTF_8).size<=MAX_BYTES){"收藏同步資料超過大小上限"}
        return raw
    }
    fun read(raw:String):List<Favorite>{
        require(raw.toByteArray(Charsets.UTF_8).size<=MAX_BYTES){"收藏同步資料超過大小上限"}
        val data=JSONObject(raw)
        require(data.getInt("version")==1){"收藏同步格式不支援，請先更新應用程式"}
        val rows=data.getJSONArray("favorites")
        require(rows.length()<=MAX_RECORDS){"收藏同步資料超過筆數上限"}
        return merge((0 until rows.length()).map{Favorite.from(rows.getJSONObject(it))})
    }
}

class FavoriteStore(context:Context){
    private val prefs=context.getSharedPreferences("favorites-v1",Context.MODE_PRIVATE)
    var onChange:(()->Unit)?=null

    /** Existing items migrate in place: a missing deleted field means a live favorite. */
    @Synchronized fun syncRecords():List<Favorite>{
        val array=JSONArray(prefs.getString("items","[]"))
        return FavoriteSyncFormat.merge((0 until array.length()).map{Favorite.from(array.getJSONObject(it))})
    }
    fun all():List<Favorite> = syncRecords().filterNot{it.deleted}.sortedByDescending{it.updated}
    fun get(id:String)=all().find{it.id==id}
    fun forPage(url:String):Favorite?{
        val address=url.toHttpUrlOrNull()?:return null
        return all().firstOrNull{it.url.toHttpUrlOrNull()==address}
    }
    private fun nextTimestamp(rows:List<Favorite>):Long{
        val last=maxOf(prefs.getLong("clock",0),rows.maxOfOrNull{it.updated}?:0)
        check(last<Long.MAX_VALUE-1){"收藏更新時間超過上限"}
        return maxOf(System.currentTimeMillis(),last+1)
    }
    private fun write(rows:List<Favorite>){
        val records=FavoriteSyncFormat.merge(rows)
        check(prefs.edit().putString("items",JSONArray(records.map{it.json()}).toString())
            .putLong("clock",maxOf(prefs.getLong("clock",0),records.maxOfOrNull{it.updated}?:0)).commit()){"收藏儲存失敗"}
    }
    @Synchronized fun save(url:String,title:String,y:Double,progress:Double,updateId:String?=null,forceNew:Boolean=false):Favorite{
        val rows=syncRecords()
        val live=rows.filterNot{it.deleted}.sortedByDescending{it.updated}
        val previous=if(forceNew)null else live.find{it.id==updateId}?:live.find{it.url==url}
        val item=Favorite.capture(previous,url,title,y,progress,nextTimestamp(rows))
        write(rows.filterNot{it.id==item.id}+item)
        onChange?.invoke()
        return item
    }
    @Synchronized fun rename(id:String,title:String){
        require(title.isNotBlank())
        val rows=syncRecords()
        val old=rows.find{it.id==id&&!it.deleted}?:return
        val name=title.trim().take(2000)
        if(name==old.title)return
        val item=old.copy(title=name,updated=nextTimestamp(rows))
        write(rows.filterNot{it.id==id}+item)
        onChange?.invoke()
    }
    @Synchronized fun remove(id:String){
        val rows=syncRecords()
        val old=rows.find{it.id==id&&!it.deleted}?:return
        // Keep the tombstone so an offline device cannot resurrect a deleted favorite.
        write(rows.filterNot{it.id==id}+old.copy(deleted=true,updated=nextTimestamp(rows)))
        onChange?.invoke()
    }
    @Synchronized fun mergeRemote(remote:List<Favorite>):Boolean{
        val local=syncRecords()
        val merged=FavoriteSyncFormat.merge(local,remote)
        if(merged==local)return false
        write(merged)
        // The sync coordinator refreshes UI; do not recursively schedule another sync here.
        return true
    }
}
