package tw.techtarian.browser

import android.Manifest
import android.app.AlertDialog
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

/** Both file and image downloads use the initiating browsing profile's credentials.
 * Only explicit downloads persist outside an incognito session; confirm that first.
 */
internal class BrowserImageDownloads(private val activity:ComponentActivity,private val notice:(String)->Unit) {
    private data class Pending(val url:String,val page:String,val userAgent:String,val cookie:String?,val name:String,val mime:String?)
    private var pending:Pending?=null
    private val permission=activity.registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        val request=pending;pending=null
        if(granted&&request!=null)enqueue(request)
        else if(!granted)notice("未允許儲存權限，檔案沒有下載")
        else notice("請重新選擇要下載的項目")
    }
    fun download(rawUrl:String,page:String,userAgent:String,cookieHeader:String?=null,disposition:String?=null,mimeType:String?=null,privateMode:Boolean=false,isImage:Boolean=true):Long? {
        val url=PageActionPolicy.downloadUrl(rawUrl)
        if(url==null){notice("目前可下載 HTTP／HTTPS 圖片與檔案；內嵌或 blob 暫存圖片尚未支援");return null}
        val extension=MimeTypeMap.getFileExtensionFromUrl(url.substringBefore('?')).lowercase(Locale.ROOT)
        val mime=mimeType?.takeIf{it.isNotBlank()}?:MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.takeIf{!isImage||it.startsWith("image/")}
        val name=PageActionPolicy.safeFilename(URLUtil.guessFileName(url,disposition,mime),UUID.randomUUID().toString().take(8))
        val cookie=if(privateMode)cookieHeader else cookieHeader?:CookieManager.getInstance().getCookie(url)
        val request=Pending(url,page,userAgent,cookie,name,mime)
        if(privateMode||!isImage){
            AlertDialog.Builder(activity).setTitle(if(isImage)"下載圖片？"else"下載檔案？")
                .setMessage(name+if(privateMode)"\n\n這個檔案與系統下載項目會保留在手機，關閉無痕分頁不會刪除。"else"")
                .setNegativeButton("取消",null).setPositiveButton("下載"){_,_->authorize(request)}.show()
            return null
        }
        return authorize(request)
    }
    private fun authorize(request:Pending):Long? {
        if(Build.VERSION.SDK_INT<=28&&ContextCompat.checkSelfPermission(activity,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
            if(pending!=null){notice("請先完成上一個下載的儲存授權");return null}
            pending=request
            try{permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)}catch(_:IllegalStateException){pending=null;notice("無法開啟儲存授權，請重新嘗試")}
            return null
        }
        return enqueue(request)
    }
    private fun enqueue(task:Pending):Long? = try {
        val request=DownloadManager.Request(Uri.parse(task.url)).setTitle(task.name).setDescription("澄境瀏覽器 · 下載")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,task.name)
        if(task.mime!=null)request.setMimeType(task.mime)
        if(task.userAgent.isNotBlank()&&task.userAgent.length<=2048&&task.userAgent.all{it.code in 32..126})request.addRequestHeader("User-Agent",task.userAgent)
        PageActionPolicy.referrer(task.page,task.url)?.let{request.addRequestHeader("Referer",it)}
        task.cookie?.takeIf{it.isNotBlank()&&it.length<=32768&&!it.contains('\r')&&!it.contains('\n')}?.let{request.addRequestHeader("Cookie",it)}
        val id=(activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        notice("已加入系統下載佇列，可在三點選單的「下載」查看結果")
        id
    }catch(_:Exception){notice("無法加入下載，請檢查儲存空間及系統下載服務");null}
}
