package tw.techtarian.browser

import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.json.JSONArray

/** Per-device snapshots avoid two phones overwriting the same Drive file. Deletions are retained. */
class BookmarkDrive internal constructor(private val token:String,private val transport:DriveTransport,private val base:String){
    constructor(token:String):this(token,DriveTransport(),"https://www.googleapis.com/")
    private val appTag="chengjing-browser-bookmarks-v1"
    fun cancel(){transport.cancel()}
    private fun request(url:String,method:String="GET",body:RequestBody?=null):String =
        transport.request(Request.Builder().url(base+url.removePrefix("https://www.googleapis.com/"))
            .header("Authorization","Bearer $token").method(method,body).build())
    fun account():Pair<String,String>{
        val j=JSONObject(request("https://www.googleapis.com/drive/v3/about?fields=user(permissionId,displayName,emailAddress)")).getJSONObject("user")
        return j.getString("permissionId") to j.optString("emailAddress",j.optString("displayName","Google 帳戶"))
    }
    fun files(tag:String=appTag):List<JSONObject>{
        val result=mutableListOf<JSONObject>();var cursor=""
        do{
            val url="https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder().addQueryParameter("spaces","appDataFolder").addQueryParameter("q","trashed=false and appProperties has { key='app' and value='$tag' }").addQueryParameter("fields","files(id,appProperties),nextPageToken").addQueryParameter("pageSize","100").apply{if(cursor.isNotEmpty())addQueryParameter("pageToken",cursor)}.build().toString()
            val j=JSONObject(request(url));val rows=j.getJSONArray("files");for(i in 0 until rows.length())result.add(rows.getJSONObject(i));cursor=j.optString("nextPageToken")
            require(result.size<=200){"同步裝置快照過多，請聯絡支援"}
        }while(cursor.isNotEmpty())
        return result
    }
    fun read(id:String)=BookmarkFormat.readSnapshot(request("https://www.googleapis.com/drive/v3/files/$id?alt=media"))
    fun write(device:String,id:String?,rows:List<Bookmark>):String = writeRaw(device,id,BookmarkFormat.snapshot(rows))
    fun readRaw(id:String)=request("https://www.googleapis.com/drive/v3/files/$id?alt=media")
    fun writeRaw(device:String,id:String?,raw:String,tag:String=appTag):String{
        val response=if(id!=null)request("https://www.googleapis.com/upload/drive/v3/files/$id?uploadType=media","PATCH",raw.toRequestBody("application/json; charset=UTF-8".toMediaType()))
        else{
            val boundary="cj_browser_bookmarks_v1"
            val metadata=JSONObject().put("name","ChengJing-Browser-$tag-$device.json").put("parents",JSONArray().put("appDataFolder")).put("appProperties",JSONObject().put("app",tag).put("device",device))
            val multipart="--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$raw\r\n--$boundary--\r\n"
            request("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id","POST",multipart.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
        }
        return JSONObject(response).getString("id")
    }
}
