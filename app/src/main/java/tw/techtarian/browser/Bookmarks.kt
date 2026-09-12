package tw.techtarian.browser

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.security.MessageDigest
import java.util.UUID

data class Bookmark(val id:String,val url:String,val title:String,val folder:String,val updated:Long,val deleted:Boolean=false){
    fun json()=JSONObject().put("id",id).put("url",url).put("title",title).put("folder",folder).put("updated",updated).put("deleted",deleted)
    companion object {
        fun id(url:String,folder:String)=MessageDigest.getInstance("SHA-256").digest((url+"\u0000"+folder).toByteArray()).joinToString(""){"%02x".format(it)}
        fun from(j:JSONObject):Bookmark {
            val url=j.getString("url");val folder=j.optString("folder").take(2000)
            require(url.toHttpUrlOrNull()!=null && url.length<=16000)
            require(j.getString("id")==id(url,folder)) { "書籤識別碼不正確" }
            return Bookmark(j.getString("id"),url,j.optString("title",url).take(2000),folder,j.getLong("updated"),j.optBoolean("deleted"))
        }
    }
}
object BookmarkFormat {
    fun merge(vararg sources:List<Bookmark>):List<Bookmark> = sources.flatMap{it}.groupBy{it.id}.values.map { rows ->
        rows.maxWith(compareBy<Bookmark>{it.updated}.thenBy{it.deleted}.thenBy{it.title})
    }.sortedBy{it.id}
    fun parseHtml(html:String,now:Long=System.currentTimeMillis()):List<Bookmark>{
        require(html.length<=8_000_000){"書籤檔案太大，請分批匯入（上限 8 MB）"}
        val stack=mutableListOf<String>();var pending="";val entries=mutableListOf<Bookmark>()
        val token=Regex("<h3\\b[^>]*>[\\s\\S]*?</h3\\s*>|<a\\b[^>]*>[\\s\\S]*?</a\\s*>|</?dl\\b[^>]*>",RegexOption.IGNORE_CASE)
        for(m in token.findAll(html)){
            val text=m.value
            when {
                text.startsWith("<h3",true)->pending=Jsoup.parse(text).text().take(300)
                text.startsWith("</dl",true)->{if(stack.isNotEmpty())stack.removeAt(stack.lastIndex)}
                text.startsWith("<dl",true)->{stack.add(pending);pending=""}
                else->{val a=Jsoup.parse(text).selectFirst("a[href]")?:continue;val url=a.attr("href").toHttpUrlOrNull()?.toString()?:continue
                    val folder=stack.filter{it.isNotBlank()}.joinToString(" / ").take(2000)
                    entries.add(Bookmark(Bookmark.id(url,folder),url,a.text().ifBlank{url}.take(2000),folder,now))
                }
            }
            require(entries.size<=20000){"一次最多匯入 20,000 個書籤"}
        }
        return merge(entries)
    }
    fun html(rows:List<Bookmark>):String {
        fun esc(s:String)=s.replace("&","&amp;").replace("\"","&quot;").replace("<","&lt;").replace(">","&gt;")
        return buildString{
            append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n<TITLE>澄境書籤</TITLE><H1>澄境書籤</H1>\n<DL><p>\n")
            rows.filterNot{it.deleted}.groupBy{it.folder}.forEach{(folder,items)->
                val parts=folder.split(" / ").filter{it.isNotEmpty()}
                parts.forEach{append("<DT><H3>${esc(it)}</H3>\n<DL><p>\n")}
                items.forEach{append("<DT><A HREF=\"${esc(it.url)}\">${esc(it.title)}</A>\n")}
                parts.forEach{append("</DL><p>\n")}
            }
            append("</DL><p>\n")
        }
    }
    fun snapshot(rows:List<Bookmark>)=JSONObject().put("schema",1).put("app","chengjing-browser-bookmarks-v1").put("bookmarks",JSONArray(rows.map{it.json()})).toString()
    fun readSnapshot(raw:String):List<Bookmark>{
        require(raw.length<=12_000_000){"雲端書籤檔案過大"};val j=JSONObject(raw)
        require(j.optInt("schema")==1&&j.optString("app")=="chengjing-browser-bookmarks-v1"){"雲端書籤格式不相容"}
        val rows=j.getJSONArray("bookmarks");require(rows.length()<=40000)
        return (0 until rows.length()).map{Bookmark.from(rows.getJSONObject(it))}
    }
}
class BookmarkStore(context:Context){
    private val prefs=context.getSharedPreferences("bookmarks-v1",Context.MODE_PRIVATE)
    var onChange:(()->Unit)?=null
    val deviceId:String get()=prefs.getString("device",null)?:UUID.randomUUID().toString().also{prefs.edit().putString("device",it).commit()}
    @Synchronized fun all():List<Bookmark> = prefs.getString("data",null)?.let{BookmarkFormat.readSnapshot(it)}?:emptyList()
    fun visible()=all().filterNot{it.deleted}.sortedByDescending{it.updated}
    private fun tick()=maxOf(System.currentTimeMillis(),(all().maxOfOrNull{it.updated}?:0)+1)
    @Synchronized fun replace(rows:List<Bookmark>){check(prefs.edit().putString("data",BookmarkFormat.snapshot(rows)).commit()){ "書籤儲存失敗" }}
    @Synchronized fun toggle(url:String,title:String){
        val existing=visible().filter{it.url==url};val now=tick()
        val changes=if(existing.isEmpty())listOf(Bookmark(Bookmark.id(url,""),url,title,"",now))else existing.map{it.copy(updated=now,deleted=true)}
        replace(BookmarkFormat.merge(all(),changes));onChange?.invoke()
    }
    @Synchronized fun add(url:String,title:String,folder:String=""):Int{
        require(url.toHttpUrlOrNull()!=null)
        val clean=folder.trim().take(2000)
        return importRows(listOf(Bookmark(Bookmark.id(url,clean),url,title.ifBlank{url}.take(2000),clean,tick())))
    }
    @Synchronized fun remove(id:String){replace(all().map{if(it.id==id)it.copy(deleted=true,updated=tick())else it});onChange?.invoke()}
    @Synchronized fun edit(id:String,title:String,folder:String){
        val entry=all().find{it.id==id}?:return;val now=tick();val newId=Bookmark.id(entry.url,folder.take(2000))
        val changes=listOf(entry.copy(deleted=true,updated=now),entry.copy(id=newId,title=title.ifBlank{entry.url}.take(2000),folder=folder.take(2000),updated=now+1,deleted=false))
        replace(BookmarkFormat.merge(all(),changes));onChange?.invoke()
    }
    @Synchronized fun importRows(rows:List<Bookmark>):Int{
        val existing=visible().map{it.id}.toSet();val added=rows.filter{it.id !in existing}.map{it.copy(updated=tick())}
        replace(BookmarkFormat.merge(all(),added));onChange?.invoke();return added.size
    }
    @Synchronized fun mergeRemote(rows:List<Bookmark>){replace(BookmarkFormat.merge(all(),rows))}
    var connected:Boolean get()=prefs.getBoolean("connected",false);set(v){prefs.edit().putBoolean("connected",v).commit()}
    var accountId:String get()=prefs.getString("account","").orEmpty();set(v){prefs.edit().putString("account",v).commit()}
    var accountLabel:String get()=prefs.getString("label","").orEmpty();set(v){prefs.edit().putString("label",v).commit()}
    var lastSync:Long get()=prefs.getLong("last-sync",0);set(v){prefs.edit().putLong("last-sync",v).commit()}
}
