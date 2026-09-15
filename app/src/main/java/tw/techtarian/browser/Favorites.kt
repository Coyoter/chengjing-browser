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
    fun json()=JSONObject().put("id",id).put("title",title).put("pageTitle",pageTitle)
        .put("url",url).put("scrollY",scrollY).put("progress",progress)
        .put("updated",updated).put("deleted",deleted)

    companion object{
        fun from(j:JSONObject):Favorite{
            val url=j.getString("url")
            require(url.toHttpUrlOrNull()!=null){"收藏網址格式錯誤"}
            val id=j.getString("id")
            val updated=j.getLong("updated")
            require(id.isNotBlank() && id.length<=200){"收藏識別碼格式錯誤"}
            require(updated>=0 && updated<Long.MAX_VALUE){"收藏更新時間格式錯誤"}
            return Favorite(
                id,j.getString("title"),j.optString("pageTitle",j.getString("title")),url,
                j.optDouble("scrollY",0.0).takeIf{it.isFinite()}?.coerceAtLeast(0.0)?:0.0,
                j.optDouble("progress",0.0).takeIf{it.isFinite()}?.coerceIn(0.0,1.0)?:0.0,
                updated,j.optBoolean("deleted",false),
            )
        }

        internal fun nextUpdated(previous:Long,now:Long=System.currentTimeMillis()):Long{
            require(previous>=0 && previous<Long.MAX_VALUE){"收藏更新時間格式錯誤"}
            return maxOf(now,previous+1)
        }

        fun capture(previous:Favorite?,url:String,pageTitle:String,scrollY:Double,progress:Double,now:Long=System.currentTimeMillis()):Favorite{
            require(url.toHttpUrlOrNull()!=null)
            val name=pageTitle.ifBlank{url}.take(2000)
            return Favorite(
                previous?.id?:UUID.randomUUID().toString(),
                if(previous==null||previous.title==previous.pageTitle)name else previous.title,
                name,url,
                scrollY.takeIf{it.isFinite()}?.coerceAtLeast(0.0)?:0.0,
                progress.takeIf{it.isFinite()}?.coerceIn(0.0,1.0)?:0.0,
                nextUpdated(previous?.updated?:0,now),
            )
        }
    }
}

class FavoriteStore(context:Context,preferenceName:String="favorites-v1"){
    private val prefs=context.getSharedPreferences(preferenceName,Context.MODE_PRIVATE)
    var onChange:(()->Unit)?=null

    /** Keep the existing local storage key; legacy records default to deleted=false. */
    @Synchronized fun records():List<Favorite>{
        val array=JSONArray(prefs.getString("items","[]"))
        return (0 until array.length()).map{Favorite.from(array.getJSONObject(it))}.sortedBy{it.id}
    }
    fun all():List<Favorite> = records().filterNot{it.deleted}
        .sortedWith(compareByDescending<Favorite>{it.updated}.thenBy{it.id})
    fun get(id:String)=all().find{it.id==id}
    fun forPage(url:String):Favorite?{
        val address=url.toHttpUrlOrNull()?:return null
        return all().firstOrNull{it.url.toHttpUrlOrNull()==address}
    }
    private fun write(rows:List<Favorite>){
        check(prefs.edit().putString("items",JSONArray(rows.sortedBy{it.id}.map{it.json()}).toString()).commit()){
            "收藏儲存失敗，本機資料未覆寫"
        }
    }
    @Synchronized fun save(url:String,title:String,y:Double,progress:Double,updateId:String?=null,forceNew:Boolean=false):Favorite{
        val rows=records()
        val visible=rows.filterNot{it.deleted}
        val previous=if(forceNew)null else visible.find{it.id==updateId}?:visible.find{it.url==url}
        val item=Favorite.capture(previous,url,title,y,progress)
        write(rows.filterNot{it.id==item.id}+item)
        onChange?.invoke()
        return item
    }
    @Synchronized fun rename(id:String,title:String){
        require(title.isNotBlank())
        val rows=records()
        val previous=rows.find{it.id==id && !it.deleted}?:return
        val name=title.trim().take(2000)
        if(previous.title==name)return
        val item=previous.copy(title=name,updated=Favorite.nextUpdated(previous.updated))
        write(rows.map{if(it.id==id)item else it})
        onChange?.invoke()
    }
    @Synchronized fun remove(id:String){
        val rows=records()
        val previous=rows.find{it.id==id && !it.deleted}?:return
        // Do not physically remove the identity: an offline device may still hold it.
        val tombstone=previous.copy(deleted=true,updated=Favorite.nextUpdated(previous.updated))
        write(rows.map{if(it.id==id)tombstone else it})
        onChange?.invoke()
    }
    @Synchronized fun mergeRemote(remote:List<Favorite>):Boolean{
        // Re-read here, after the network request, to retain edits made during sync.
        val local=records()
        val merged=FavoriteFormat.merge(local,remote)
        if(local==merged)return false
        write(merged)
        // Remote merges must not recursively schedule uploads; the sync owns that step.
        return true
    }
}
