package tw.techtarian.browser

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.UUID

/** No file-sized app cache. Pending outputs stay hidden and are removed if interrupted. */
internal class PageFileStorage(private val context:Context) {
    private val prefs=context.getSharedPreferences("page-download-pending-v1",Context.MODE_PRIVATE)
    private val catalog=SavedImageDownloads(context)
    private fun remember(uri:Uri,value:JSONObject){check(prefs.edit().putString(uri.toString(),value.toString()).commit()){"無法保存下載狀態"}}
    private fun forget(uri:Uri){prefs.edit().remove(uri.toString()).commit()}
    suspend fun recover()=withContext(Dispatchers.IO){
        for((key,value) in prefs.all){
            runCatching{
                val uri=Uri.parse(key);val row=JSONObject(value as String)
                if(Build.VERSION.SDK_INT>=29&&uri.scheme=="content"){
                    context.contentResolver.query(uri,arrayOf(MediaStore.MediaColumns.IS_PENDING,MediaStore.MediaColumns.SIZE),null,null,null)?.use{cursor->
                        if(cursor.moveToFirst()){
                            if(cursor.getInt(0)==1){
                                context.contentResolver.delete(uri,null,null)
                                catalog.recordFile(row.getString("name"),row.optString("source"),row.getString("mime"),0,Uri.EMPTY,DownloadManager.STATUS_FAILED,"上次下載已中斷，未保留不完整檔案；請回原頁重新下載")
                            }
                            else catalog.recordFile(row.getString("name"),row.optString("source"),row.getString("mime"),cursor.getLong(1),uri)
                        }
                    }
                }else if(uri.scheme=="file"){
                    val file=File(uri.path.orEmpty())
                    @Suppress("DEPRECATION") val root=File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"ChengJing")
                    if(file.parentFile?.canonicalFile==root.canonicalFile&&file.name.startsWith(".cj-")&&file.extension=="part")file.delete()
                }
                forget(uri)
            }
        }
    }
    inner class Pending(val uri:Uri,val output:OutputStream,private val name:String,private val mime:String,private val source:String,private val size:Long,private val legacy:File?=null){
        private var completed=false
        suspend fun finish():DownloadItem=withContext(Dispatchers.IO){
            output.close()
            val result=if(Build.VERSION.SDK_INT>=29&&legacy==null){
                check(context.contentResolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)},null,null)==1){"下載檔案未能完成儲存"}
                catalog.recordFile(name,source,mime,size,uri)
            }else{
                val file=legacy!!;val target=File(file.parentFile,PageActionPolicy.safeFilename(name,UUID.randomUUID().toString().take(8)))
                check(file.renameTo(target)){"無法完成下載檔案"}
                try{
                    @Suppress("DEPRECATION") val id=context.getSystemService(DownloadManager::class.java).addCompletedDownload(target.name,"澄境瀏覽器 · 檔案下載",true,mime,target.path,size,true)
                    DownloadItem(id,target.name,source,mime,DownloadManager.STATUS_SUCCESSFUL,size,size)
                }catch(error:Exception){target.delete();throw error}
            }
            completed=true;forget(uri);result
        }
        suspend fun abort()=withContext(Dispatchers.IO){
            runCatching{output.close()}
            if(!completed){if(legacy!=null)legacy.delete()else context.contentResolver.delete(uri,null,null);forget(uri)}
        }
    }
    suspend fun create(name:String,mime:String,source:String,size:Long):Pending {
      var created:Pending?=null
      try{return withContext(Dispatchers.IO){
        val meta=JSONObject().put("name",name).put("mime",mime).put("source",source).put("size",size)
        if(Build.VERSION.SDK_INT>=29){
            val uri=context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,ContentValues().apply{
                put(MediaStore.MediaColumns.DISPLAY_NAME,name);put(MediaStore.MediaColumns.MIME_TYPE,mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/ChengJing");put(MediaStore.MediaColumns.IS_PENDING,1)
            })?:error("無法建立下載檔案，請檢查儲存空間")
            try{
                remember(uri,meta)
                val actual=context.contentResolver.query(uri,arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0)else null}?:name
                if(actual!=name)remember(uri,meta.put("name",actual))
                Pending(uri,context.contentResolver.openOutputStream(uri,"w")?:error("無法寫入下載檔案"),actual,mime,source,size).also{created=it}
            }
            catch(error:Exception){context.contentResolver.delete(uri,null,null);forget(uri);throw error}
        }else{
            @Suppress("DEPRECATION") val folder=File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"ChengJing")
            check(folder.isDirectory||folder.mkdirs()){"無法建立下載資料夾"}
            val file=File(folder,".cj-${UUID.randomUUID()}.part");val uri=Uri.fromFile(file)
            try{remember(uri,meta);Pending(uri,file.outputStream(),name,mime,source,size,file).also{created=it}}catch(error:Exception){file.delete();forget(uri);throw error}
        }
      }}catch(error:Exception){withContext(NonCancellable){created?.abort()};throw error}
    }
}
