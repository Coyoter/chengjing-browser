package tw.techtarian.browser

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Favorite(val id:String,val title:String,val pageTitle:String,val url:String,val scrollY:Double,val progress:Double,val updated:Long){
    fun json()=JSONObject().put("id",id).put("title",title).put("pageTitle",pageTitle).put("url",url).put("scrollY",scrollY).put("progress",progress).put("updated",updated)
    companion object{
        fun from(j:JSONObject):Favorite{
            val url=j.getString("url");require(url.toHttpUrlOrNull()!=null)
            return Favorite(j.getString("id"),j.getString("title"),j.optString("pageTitle",j.getString("title")),url,j.optDouble("scrollY",0.0).takeIf{it.isFinite()}?.coerceAtLeast(0.0)?:0.0,j.optDouble("progress",0.0).takeIf{it.isFinite()}?.coerceIn(0.0,1.0)?:0.0,j.getLong("updated"))
        }
        fun capture(previous:Favorite?,url:String,pageTitle:String,scrollY:Double,progress:Double,now:Long=System.currentTimeMillis()):Favorite{
            require(url.toHttpUrlOrNull()!=null)
            val name=pageTitle.ifBlank{url}.take(2000)
            return Favorite(previous?.id?:UUID.randomUUID().toString(),if(previous==null||previous.title==previous.pageTitle)name else previous.title,name,url,
                scrollY.takeIf{it.isFinite()}?.coerceAtLeast(0.0)?:0.0,progress.takeIf{it.isFinite()}?.coerceIn(0.0,1.0)?:0.0,now)
        }
    }
}
class FavoriteStore(context:Context){
    private val prefs=context.getSharedPreferences("favorites-v1",Context.MODE_PRIVATE)
    fun all():List<Favorite>{
        val array=JSONArray(prefs.getString("items","[]"))
        return (0 until array.length()).map{Favorite.from(array.getJSONObject(it))}.sortedByDescending{it.updated}
    }
    fun get(id:String)=all().find{it.id==id}
    private fun write(rows:List<Favorite>){check(prefs.edit().putString("items",JSONArray(rows.map{it.json()}).toString()).commit()){ "收藏儲存失敗" }}
    @Synchronized fun save(url:String,title:String,y:Double,progress:Double,updateId:String?=null,forceNew:Boolean=false):Favorite{
        val rows=all()
        val previous=if(forceNew)null else rows.find{it.id==updateId}?:rows.find{it.url==url}
        val item=Favorite.capture(previous,url,title,y,progress)
        write(rows.filterNot{it.id==item.id}+item)
        return item
    }
    @Synchronized fun rename(id:String,title:String){require(title.isNotBlank());write(all().map{if(it.id==id)it.copy(title=title.trim().take(2000))else it})}
    @Synchronized fun remove(id:String){write(all().filterNot{it.id==id})}
}
