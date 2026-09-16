package tw.techtarian.browser

import android.app.DownloadManager
import android.content.ClipData
import android.content.Context
import android.content.Intent

internal data class BrowserDownload(
    val id:Long,val title:String,val status:Int,val done:Long,val total:Long,val modified:Long,val reason:Int,
) {
    val active get()=status in setOf(DownloadManager.STATUS_PENDING,DownloadManager.STATUS_RUNNING,DownloadManager.STATUS_PAUSED)
    val complete get()=status==DownloadManager.STATUS_SUCCESSFUL
    val fraction:Float? get()=if(total>0)(done.toDouble()/total).toFloat().coerceIn(0f,1f)else null
    val label:String get()=when(status){
        DownloadManager.STATUS_SUCCESSFUL->"已完成"
        DownloadManager.STATUS_PENDING->"等待下載"
        DownloadManager.STATUS_RUNNING->"下載中"
        DownloadManager.STATUS_PAUSED->when(reason){
            DownloadManager.PAUSED_WAITING_FOR_NETWORK->"等待網路"
            DownloadManager.PAUSED_QUEUED_FOR_WIFI->"等待 Wi-Fi"
            else->"等待系統重試"
        }
        DownloadManager.STATUS_FAILED->when(reason){
            DownloadManager.ERROR_INSUFFICIENT_SPACE->"儲存空間不足"
            DownloadManager.ERROR_DEVICE_NOT_FOUND->"儲存位置無法使用"
            in 400..599->"來源拒絕或無法提供檔案（$reason）"
            else->"下載未完成"
        }
        else->"項目已移除"
    }
}

/** DownloadManager's default my_downloads query is scoped to this application's UID.
 * It also includes downloads made by older releases; no broad storage permission is needed.
 */
internal class BrowserDownloads(private val context:Context) {
    private val manager=context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    fun entries():List<BrowserDownload> {
        val rows=mutableListOf<BrowserDownload>()
        manager.query(DownloadManager.Query()).use{cursor->
            checkNotNull(cursor){"系統下載服務暫時無法使用"}
            fun number(name:String)=cursor.getLong(cursor.getColumnIndexOrThrow(name))
            while(cursor.moveToNext()) {
                // Internal AI models remain managed by AI settings, not the download library.
                val title=cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)).orEmpty()
                if(title=="澄境 · Gemma 4 本機模型")continue
                rows.add(BrowserDownload(number(DownloadManager.COLUMN_ID),
                    cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)).orEmpty().ifBlank{"下載檔案"},
                    number(DownloadManager.COLUMN_STATUS).toInt(),number(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                    number(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),number(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP),number(DownloadManager.COLUMN_REASON).toInt()))
            }
        }
        return rows.sortedByDescending{it.modified}
    }
    fun remove(id:Long) { check(manager.remove(id)>0){"此下載已不存在"} }
    fun openIntent(id:Long):Intent {
        val uri=manager.getUriForDownloadedFile(id)?:error("檔案尚未完成或已被移除")
        check(uri.scheme=="content"){"系統未提供安全的檔案開啟方式"}
        context.contentResolver.openFileDescriptor(uri,"r")?.use{}?:error("檔案已被移除")
        val intent=Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri,manager.getMimeTypeForDownloadedFile(id)?:"application/octet-stream")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData=ClipData.newRawUri("下載檔案",uri)
        }
        return Intent.createChooser(intent,"開啟下載檔案")
    }
}
