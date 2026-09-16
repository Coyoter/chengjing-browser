package tw.techtarian.browser

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.UUID

/** Uses the system download queue; never reports a queued transfer as a saved file. */
internal class BrowserImageDownloads(private val activity:ComponentActivity,private val notice:(String)->Unit) {
    private data class Pending(val url:String,val page:String,val userAgent:String)
    private var pending:Pending?=null
    private val permission=activity.registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        val request=pending
        pending=null
        if(granted&&request!=null)enqueue(request)
        else if(!granted)notice("未允許儲存權限，圖片沒有下載")
        else notice("請重新長按圖片後下載")
    }
    fun download(rawUrl:String,page:String,userAgent:String):Long? {
        val url=PageActionPolicy.downloadUrl(rawUrl)
        if(url==null){
            notice("目前可下載 HTTP／HTTPS 圖片；內嵌或 blob 暫存圖片尚未支援")
            return null
        }
        val request=Pending(url,page,userAgent)
        if(Build.VERSION.SDK_INT<=28&&ContextCompat.checkSelfPermission(activity,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
            if(pending!=null){notice("請先完成上一個圖片的儲存授權");return null}
            pending=request
            try{permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)}catch(_:IllegalStateException){pending=null;notice("無法開啟儲存授權，請重新嘗試")}
            return null
        }
        return enqueue(request)
    }
    private fun enqueue(task:Pending):Long? = try {
        val extension=MimeTypeMap.getFileExtensionFromUrl(task.url.substringBefore('?')).lowercase(Locale.ROOT)
        val mime=MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.takeIf{it.startsWith("image/")}
        val guessed=URLUtil.guessFileName(task.url,null,mime)
        val name=PageActionPolicy.safeFilename(guessed,UUID.randomUUID().toString().take(8))
        val request=DownloadManager.Request(Uri.parse(task.url))
            .setTitle(name)
            .setDescription("澄境瀏覽器 · 圖片下載")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,name)
        // When the path has no image extension, let the server's Content-Type decide.
        if(mime!=null)request.setMimeType(mime)
        if(task.userAgent.isNotBlank()&&task.userAgent.length<=2048&&task.userAgent.all{it.code in 32..126})request.addRequestHeader("User-Agent",task.userAgent)
        PageActionPolicy.referrer(task.page,task.url)?.let{request.addRequestHeader("Referer",it)}
        CookieManager.getInstance().getCookie(task.url)?.takeIf{it.isNotBlank()&&it.length<=32768&&!it.contains('\r')&&!it.contains('\n')}?.let{request.addRequestHeader("Cookie",it)}
        val id=(activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        notice("圖片已加入系統下載佇列，可在通知或「下載」查看結果")
        id
    }catch(_:Exception){notice("無法加入圖片下載，請檢查儲存空間及系統下載服務");null}
}
