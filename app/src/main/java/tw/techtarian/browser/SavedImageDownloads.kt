package tw.techtarian.browser

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Verified image bytes go to Downloads only after their format is known. */
internal class SavedImageDownloads(private val context:Context) {
    companion object { private val lock=Any() }
    private val prefs=context.getSharedPreferences("saved-image-downloads-v1",Context.MODE_PRIVATE)
    @Synchronized fun list():List<DownloadItem> = runCatching{
        val data=JSONArray(prefs.getString("items","[]"))
        (0 until data.length()).map{data.getJSONObject(it)}.map{j->
            DownloadItem(j.getLong("id"),j.getString("name"),j.optString("source"),j.getString("mime"),DownloadManager.STATUS_SUCCESSFUL,j.getLong("size"),j.getLong("size"),j.getString("uri"),j.getLong("time"))
        }
    }.getOrDefault(emptyList())
    private fun record(asset:ImageAsset,uri:Uri):DownloadItem = synchronized(lock) {
        val now=System.currentTimeMillis()
        val id=minOf(-now,(list().minOfOrNull{it.id}?:0)-1)
        val source=PageActionPolicy.shareUrl(asset.source)?.let{if(it.length<=8192)it else PageActionPolicy.referrer(it,it)}.orEmpty()
        val row=DownloadItem(id,asset.file.name,source,asset.format.mime,DownloadManager.STATUS_SUCCESSFUL,asset.file.length(),asset.file.length(),uri.toString(),now)
        val rows=(listOf(row)+list()).take(500)
        check(prefs.edit().putString("items",JSONArray(rows.map{JSONObject().put("id",it.id).put("name",it.title).put("source",it.source).put("mime",it.mime).put("size",it.total).put("uri",it.contentUri).put("time",it.createdAt)}).toString()).commit()){"下載記錄未能儲存"}
        row
    }
    suspend fun save(asset:ImageAsset):DownloadItem=withContext(Dispatchers.IO){
        if(Build.VERSION.SDK_INT>=29){
            val values=ContentValues().apply{
                put(MediaStore.MediaColumns.DISPLAY_NAME,asset.file.name);put(MediaStore.MediaColumns.MIME_TYPE,asset.format.mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/ChengJing")
                put(MediaStore.MediaColumns.IS_PENDING,1)
            }
            val resolver=context.contentResolver
            val uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)?:error("無法建立下載檔案")
            try{
                resolver.openOutputStream(uri,"w")!!.use{out->asset.file.inputStream().use{ImageFiles.copyBounded(it,out)}}
                check(resolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)},null,null)==1)
                record(asset,uri)
            }catch(e:Exception){resolver.delete(uri,null,null);throw e}
        }else{
            @Suppress("DEPRECATION")
            val directory=File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"ChengJing").apply{mkdirs()}
            val file=File(directory,asset.file.name)
            try{
                check(file.createNewFile())
                file.outputStream().use{out->asset.file.inputStream().use{ImageFiles.copyBounded(it,out)}}
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).addCompletedDownload(file.name,"澄境瀏覽器 · 圖片下載",true,asset.format.mime,file.path,file.length(),true)
                MediaScannerConnection.scanFile(context,arrayOf(file.path),arrayOf(asset.format.mime),null)
                DownloadItem(0,file.name,asset.source,asset.format.mime,DownloadManager.STATUS_SUCCESSFUL,file.length(),file.length())
            }catch(e:Exception){file.delete();throw e}
        }
    }
}
